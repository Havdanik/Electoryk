package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.CloudPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Metric point-cloud fusion with multi-frame / multi-view evidence.
 *
 * v0.17.0 precision rules:
 *  - raw depth has much more influence than smoothed fallback;
 *  - final analysis prefers voxels observed from more than one depth frame;
 *  - repeated samples from the same camera position cannot by themselves become high quality;
 *  - points keep a sub-voxel weighted mean, so voxelization does not quantize output coordinates.
 */
public class VoxelPointCloud {
    private static class Acc {
        double x, y, z, weight;
        double qualitySum, qualityWeight;
        int n;
        int rawN;
        int smoothN;
        int frames;
        int lastFrame = Integer.MIN_VALUE;
        boolean hasFirstCamera;
        float firstCameraX, firstCameraY, firstCameraZ;
        float maxBaselineM;
    }

    private final double voxelM;
    private final int maxVoxels;
    // Depth ingestion is a per-sample hot path. A HashMap<Long,Acc> boxed every voxel key and
    // allocated HashMap nodes. Keep the sparse cloud primitive while preserving Acc objects.
    private final AccLookup cells;
    private int rawSamples;
    private int confirmedVoxels;

    public VoxelPointCloud(double voxelM, int maxVoxels) {
        this.voxelM = Math.max(0.01, voxelM);
        this.maxVoxels = Math.max(1000, maxVoxels);
        this.cells = new AccLookup(this.maxVoxels);
    }

    public synchronized boolean add(float x, float y, float z, float confidence) {
        return addLocked(x,y,z,confidence,-1,0,0,0,true);
    }

    /** Compatibility overload. */
    public synchronized int addBatch(float[] xs,float[] ys,float[] zs,float[] confidence,int count) {
        return addBatch(xs,ys,zs,confidence,count,-1,0,0,0,true);
    }

    /**
     * Add one complete Depth frame with one monitor acquisition.
     * frameId/camera position let the voxel estimate independent observations instead of just hit count.
     */
    public synchronized int addBatch(float[] xs,float[] ys,float[] zs,float[] confidence,int count,
                                     int frameId,float cameraX,float cameraY,float cameraZ,boolean raw) {
        if(xs==null||ys==null||zs==null||confidence==null)return 0;
        int n=Math.min(count,Math.min(Math.min(xs.length,ys.length),Math.min(zs.length,confidence.length)));
        int added=0;
        for(int i=0;i<n;i++)if(addLocked(xs[i],ys[i],zs[i],confidence[i],frameId,cameraX,cameraY,cameraZ,raw))added++;
        return added;
    }

    private boolean addLocked(float x,float y,float z,float confidence,
                              int frameId,float cameraX,float cameraY,float cameraZ,boolean raw) {
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||!Float.isFinite(confidence))return false;
        rawSamples++;
        int qx=(int)Math.floor(x/voxelM);
        int qy=(int)Math.floor(y/voxelM);
        int qz=(int)Math.floor(z/voxelM);
        long key=pack(qx,qy,qz);
        Acc a=cells.get(key);
        boolean wasConfirmed=a!=null && confirmed(a);
        if(a==null){
            if(cells.size()>=maxVoxels)return false;
            a=new Acc();
            cells.put(key,a);
        }

        // Raw Depth is the measurement source. Smoothed Depth is useful only as a weak fallback.
        double c=Math.max(0.02,Math.min(1.0,confidence));
        double sourceWeight=raw?1.0:0.20;
        double w=Math.max(0.01,c*c*sourceWeight);
        a.x+=x*w;a.y+=y*w;a.z+=z*w;a.weight+=w;
        a.qualitySum+=c*sourceWeight;a.qualityWeight+=sourceWeight;
        a.n++;
        if(raw)a.rawN++;else a.smoothN++;

        if(frameId!=Integer.MIN_VALUE && frameId!=a.lastFrame){
            a.lastFrame=frameId;
            a.frames++;
            if(!a.hasFirstCamera){
                a.hasFirstCamera=true;
                a.firstCameraX=cameraX;a.firstCameraY=cameraY;a.firstCameraZ=cameraZ;
            }else{
                float dx=cameraX-a.firstCameraX,dy=cameraY-a.firstCameraY,dz=cameraZ-a.firstCameraZ;
                float baseline=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                if(Float.isFinite(baseline))a.maxBaselineM=Math.max(a.maxBaselineM,baseline);
            }
        }
        if(!wasConfirmed && confirmed(a))confirmedVoxels++;
        return true;
    }

    /** Final analysis snapshot: precision-confirmed voxels only. */
    public synchronized List<CloudPoint> snapshot() {
        return snapshotLocked(Integer.MAX_VALUE,true);
    }

    /** Recovery/checkpoint snapshot also prefers confirmed points. */
    public synchronized List<CloudPoint> snapshotLimited(int maxPoints) {
        return snapshotLocked(Math.max(1,maxPoints),true);
    }

    /** Diagnostic snapshot may include every voxel, including unconfirmed first-view points. */
    public synchronized List<CloudPoint> snapshotAllLimited(int maxPoints) {
        return snapshotLocked(Math.max(1,maxPoints),false);
    }

    private List<CloudPoint> snapshotLocked(int maxPoints,boolean confirmedOnly) {
        int cap=Math.min(cells.size(),maxPoints);
        List<CloudPoint> out = new ArrayList<>(cap);
        int emitted=0;
        for (int slot=0; slot<cells.capacity(); slot++) {
            Acc a=cells.valueAt(slot);
            if(a==null)continue;
            if(emitted>=maxPoints)break;
            if(confirmedOnly && !confirmed(a))continue;
            double w = Math.max(1e-9, a.weight);
            float rawRatio=a.n<=0?0f:a.rawN/(float)a.n;
            float frameScore=Math.min(1f,a.frames/4f);
            float baselineScore=Math.min(1f,a.maxBaselineM/0.28f);
            float sourceScore=0.35f+0.65f*rawRatio;
            float measurementScore=(float)Math.max(0,Math.min(1,
                    a.qualityWeight<=1e-9?0.25:a.qualitySum/a.qualityWeight));
            float conf=(float)Math.max(0.05,Math.min(1.0,
                    0.06+0.18*frameScore+0.23*baselineScore+0.13*sourceScore+0.40*measurementScore));
            out.add(new CloudPoint(
                    (float)(a.x / w),
                    (float)(a.y / w),
                    (float)(a.z / w),
                    conf));
            emitted++;
        }
        return out;
    }

    private boolean confirmed(Acc a){
        if(a==null||a.n<=0)return false;
        // Raw observations need at least two distinct depth frames and a modest parallax baseline.
        if(a.rawN>0 && a.frames>=2 && a.maxBaselineM>=0.080f)return true;
        // A very stable raw voxel may survive with more frames, but still needs real movement;
        // phone-pose jitter is not independent geometry evidence.
        if(a.rawN>=4 && a.frames>=4 && a.maxBaselineM>=0.050f)return true;
        // Smoothed-only geometry is accepted only after substantially stronger evidence.
        return a.rawN==0 && a.smoothN>=6 && a.frames>=5 && a.maxBaselineM>=0.16f;
    }

    public synchronized int size() { return cells.size(); }
    public synchronized int confirmedSize() { return confirmedVoxels; }
    public synchronized int getRawSamples() { return rawSamples; }
    public synchronized void clear() {
        cells.clear();
        rawSamples = 0;
        confirmedVoxels = 0;
    }

    private static final class AccLookup {
        private long[] keys;
        private Acc[] values;
        private int mask,size,threshold;
        AccLookup(int maxEntries){
            int target=Math.max(2048,(int)Math.ceil(Math.max(1,maxEntries)/0.68));
            int cap=1;while(cap<target && cap<(1<<30))cap<<=1;
            keys=new long[cap];values=new Acc[cap];mask=cap-1;threshold=(int)(cap*0.72f);
        }
        Acc get(long key){
            int i=slot(key,mask);
            while(true){Acc v=values[i];if(v==null)return null;if(keys[i]==key)return v;i=(i+1)&mask;}
        }
        void put(long key,Acc value){
            if(size>=threshold)grow();
            int i=slot(key,mask);
            while(true){Acc v=values[i];if(v==null){keys[i]=key;values[i]=value;size++;return;}if(keys[i]==key){values[i]=value;return;}i=(i+1)&mask;}
        }
        int size(){return size;}
        int capacity(){return values.length;}
        Acc valueAt(int i){return values[i];}
        void clear(){java.util.Arrays.fill(values,null);size=0;}
        private void grow(){
            long[] oldK=keys;Acc[] oldV=values;int cap=oldV.length<<1;
            if(cap<=0)throw new IllegalStateException("Voxel accumulator table overflow");
            keys=new long[cap];values=new Acc[cap];mask=cap-1;threshold=(int)(cap*0.72f);size=0;
            for(int i=0;i<oldV.length;i++)if(oldV[i]!=null)put(oldK[i],oldV[i]);
        }
        private static int slot(long key,int mask){long z=key;z^=z>>>33;z*=0xff51afd7ed558ccdl;z^=z>>>33;z*=0xc4ceb9fe1a85ec53l;z^=z>>>33;return ((int)z)&mask;}
    }

    private long pack(int x, int y, int z) {
        long mask = 0x1FFFFFL;
        return ((long)x & mask) << 42
                | (((long)y & mask) << 21)
                | ((long)z & mask);
    }
}
