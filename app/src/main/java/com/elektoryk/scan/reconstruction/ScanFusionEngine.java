package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.CloudPoint;
import com.elektoryk.scan.model.ScanPathPoint;
import java.util.*;

/**
 * Multi-pass scan fusion for independently started ARCore sessions.
 *
 * v0.20.6: partial-room passes no longer rely on a single cloud median. A rescan of a doorway,
 * corridor or one side of a room can have a very different median from the stored apartment even
 * when both scans are perfectly compatible. We generate robust X/Z quantile translation hypotheses,
 * score them cheaply, refine the best candidates, then accept only geometrically meaningful overlap.
 */
public class ScanFusionEngine {
    public static class Result {
        public final List<CloudPoint> points=new ArrayList<>();
        public final List<ScanPathPoint> path=new ArrayList<>();
        public double dx,dy,dz;
        public int rotationQuarterTurns;
        public double overlapScore;
        public boolean fused;
        public boolean rejected;
    }

    public Result fuse(List<CloudPoint> previous,List<ScanPathPoint> previousPath,List<CloudPoint> current,List<ScanPathPoint> currentPath){
        Result r=new Result();
        if(previous==null||previous.size()<300){if(current!=null)r.points.addAll(current);if(currentPath!=null)r.path.addAll(currentPath);return r;}
        if(current==null||current.size()<300){r.points.addAll(previous);if(previousPath!=null)r.path.addAll(previousPath);return r;}

        AxisStats pa=stats(previous,0);
        Alignment best=null;
        for(int q=0;q<4;q++){
            AxisStats ca=stats(current,q);
            if(pa==null||ca==null)continue;
            double dy=pa.floor-ca.floor;
            if(Math.abs(dy)>4)continue;

            // The median remains the first hypothesis, but partial scans also try low/mid/high
            // structural quantiles. Quantiles are intentionally sparse to keep phone CPU bounded.
            ArrayList<Alignment> coarse=new ArrayList<>();
            for(double px:pa.qx)for(double cx:ca.qx)for(double pz:pa.qz)for(double cz:ca.qz){
                Alignment a=new Alignment();a.q=q;a.dx=px-cx;a.dy=dy;a.dz=pz-cz;
                if(Math.abs(a.dx)>20||Math.abs(a.dz)>20)continue;
                a.score=overlap(previous,current,a,6500,1800);
                coarse.add(a);
            }
            Collections.sort(coarse,new Comparator<Alignment>(){@Override public int compare(Alignment a,Alignment b){return Double.compare(b.score,a.score);}});
            int refine=Math.min(4,coarse.size());
            for(int i=0;i<refine;i++){
                Alignment a=coarse.get(i);
                // Small metric refinement compensates voxel/quantile discretization without ICP's
                // memory/CPU cost. 12.5 cm is below the coarse overlap voxel and is safe on-device.
                Alignment refined=refineTranslation(previous,current,a);
                if(best==null||refined.score>best.score)best=refined;
            }
        }
        // 2% with a +/-1 neighborhood was too permissive: unrelated rooms could fuse by chance.
        // 5% still permits a doorway/corridor overlap while rejecting most incompatible sessions.
        if(best==null||best.score<0.05){
            r.points.addAll(previous);if(previousPath!=null)r.path.addAll(previousPath);
            r.overlapScore=best==null?0:best.score;r.rejected=true;return r;
        }

        r.dx=best.dx;r.dy=best.dy;r.dz=best.dz;r.rotationQuarterTurns=best.q;r.overlapScore=best.score;
        final double voxel=.045;
        // Final cloud fusion used to fall back to LinkedHashMap<Long,CloudPoint>. That reintroduced
        // one boxed Long plus a linked hash node for almost every 4.5 cm voxel, precisely after the
        // overlap stage had been made primitive. Dense apartment scans can contain hundreds of
        // thousands of voxels, so finishing/resuming a scan could still trigger a large heap spike.
        LongCloudMap map=new LongCloudMap(previous.size()+current.size());
        for(CloudPoint p:previous)put(map,p,voxel);
        for(CloudPoint p:current){double rx=rotateX(p.x,p.z,best.q), rz=rotateZ(p.x,p.z,best.q);put(map,new CloudPoint((float)(rx+best.dx),(float)(p.y+best.dy),(float)(rz+best.dz),p.confidence),voxel);}
        map.appendValues(r.points);
        if(previousPath!=null)r.path.addAll(previousPath);
        if(currentPath!=null)for(ScanPathPoint p:currentPath){double rx=rotateX(p.x,p.z,best.q), rz=rotateZ(p.x,p.z,best.q);r.path.add(new ScanPathPoint(rx+best.dx,rz+best.dz,p.timestampNs));}
        r.fused=true;return r;
    }

    private static class Alignment{int q;double dx,dy,dz,score;}
    private static class AxisStats{double[] qx,qz;double floor;}

    private AxisStats stats(List<CloudPoint> points,int q){
        int stride=Math.max(1,points.size()/24000);
        // This runs up to four times for every resumed scan. ArrayList<Float> used to box tens of
        // thousands of coordinates per orientation (plus backing-array growth), causing a large
        // stop-the-world GC spike exactly when the user taps Finish. Keep the same robust quantiles
        // but collect into primitive arrays whose capacity is known from the sampling stride.
        int cap=Math.max(1,(points.size()+stride-1)/stride);
        float[] xs=new float[cap],ys=new float[cap],zs=new float[cap];int n=0;
        for(int i=0;i<points.size();i+=stride){CloudPoint p=points.get(i);if(!finite(p))continue;xs[n]=(float)rotateX(p.x,p.z,q);ys[n]=p.y;zs[n]=(float)rotateZ(p.x,p.z,q);n++;}
        if(n<50)return null;Arrays.sort(xs,0,n);Arrays.sort(ys,0,n);Arrays.sort(zs,0,n);
        AxisStats s=new AxisStats();s.qx=new double[]{quant(xs,n,.15),quant(xs,n,.50),quant(xs,n,.85)};s.qz=new double[]{quant(zs,n,.15),quant(zs,n,.50),quant(zs,n,.85)};s.floor=quant(ys,n,.08);return s;
    }

    private Alignment refineTranslation(List<CloudPoint> previous,List<CloudPoint> current,Alignment seed){
        Alignment best=copy(seed);best.score=overlap(previous,current,best,24000,12000);
        final double d=.125;
        for(int ix=-1;ix<=1;ix++)for(int iz=-1;iz<=1;iz++){
            if(ix==0&&iz==0)continue;Alignment a=copy(seed);a.dx+=ix*d;a.dz+=iz*d;
            a.score=overlap(previous,current,a,16000,7000);if(a.score>best.score){best=a;best.score=overlap(previous,current,best,24000,12000);}
        }
        return best;
    }
    private Alignment copy(Alignment x){Alignment a=new Alignment();a.q=x.q;a.dx=x.dx;a.dy=x.dy;a.dz=x.dz;a.score=x.score;return a;}

    private double overlap(List<CloudPoint> previous,List<CloudPoint> current,Alignment a,int maxPrev,int maxCur){
        // Score both directions. A one-way score can be fooled by a small noisy cloud placed inside
        // a large old model: nearly every new point is "near something" although the old structure
        // has no reciprocal support. Geometric mean keeps legitimate partial rescans possible while
        // strongly penalizing this containment false-positive.
        double forward=directedOverlap(previous,current,a,maxPrev,maxCur,false);
        double reverse=directedOverlap(previous,current,a,maxPrev,maxCur,true);
        return Math.sqrt(Math.max(0,forward)*Math.max(0,reverse));
    }

    private double directedOverlap(List<CloudPoint> previous,List<CloudPoint> current,Alignment a,
                                   int maxPrev,int maxCur,boolean reverse){
        final double v=.22;
        List<CloudPoint> target=reverse?current:previous, query=reverse?previous:current;
        int targetMax=reverse?maxCur:maxPrev,queryMax=reverse?maxPrev:maxCur;
        int st=Math.max(1,target.size()/Math.max(1,targetMax)),sq=Math.max(1,query.size()/Math.max(1,queryMax));
        // Primitive occupancy set avoids boxing one Long per sampled voxel. Fusion evaluates many
        // hypotheses, so the old HashSet<Long> could allocate hundreds of thousands of objects.
        LongVoxelSet occ=new LongVoxelSet(Math.min(target.size(),Math.max(16,targetMax)));
        for(int i=0;i<target.size();i+=st){CloudPoint p=target.get(i);if(!finite(p))continue;
            double x=p.x,y=p.y,z=p.z;
            if(reverse){double ox=x,oz=z;x=rotateX(ox,oz,a.q)+a.dx;y+=a.dy;z=rotateZ(ox,oz,a.q)+a.dz;}
            occ.add(hash((int)Math.floor(x/v),(int)Math.floor(y/v),(int)Math.floor(z/v)));
        }
        int hit=0,total=0;
        for(int i=0;i<query.size();i+=sq){CloudPoint p=query.get(i);if(!finite(p))continue;
            double x=p.x,y=p.y,z=p.z;
            if(!reverse){double ox=x,oz=z;x=rotateX(ox,oz,a.q)+a.dx;y+=a.dy;z=rotateZ(ox,oz,a.q)+a.dz;}
            total++;int gx=(int)Math.floor(x/v),gy=(int)Math.floor(y/v),gz=(int)Math.floor(z/v);boolean ok=false;
            for(int yy=-1;yy<=1&&!ok;yy++)for(int xx=-1;xx<=1&&!ok;xx++)for(int zz=-1;zz<=1&&!ok;zz++)if(occ.contains(hash(gx+xx,gy+yy,gz+zz)))ok=true;
            if(ok)hit++;
        }
        return total==0?0:hit/(double)total;
    }

    /** Small primitive long set dedicated to one overlap score; no Long boxing or node objects. */
    private static final class LongVoxelSet {
        private final long[] keys;
        private final byte[] used;
        private final int mask;
        LongVoxelSet(int expected){
            int cap=16, need=Math.max(16,expected*2);
            while(cap<need && cap<(1<<25))cap<<=1;
            keys=new long[cap];used=new byte[cap];mask=cap-1;
        }
        void add(long k){int i=slot(k);while(used[i]!=0){if(keys[i]==k)return;i=(i+1)&mask;}used[i]=1;keys[i]=k;}
        boolean contains(long k){int i=slot(k);while(used[i]!=0){if(keys[i]==k)return true;i=(i+1)&mask;}return false;}
        private int slot(long k){long z=k;z^=z>>>33;z*=0xff51afd7ed558ccdl;z^=z>>>33;z*=0xc4ceb9fe1a85ec53l;z^=z>>>33;return ((int)z)&mask;}
    }

    /** Primitive long -> CloudPoint map for the final fused cloud; highest-confidence sample wins. */
    private static final class LongCloudMap {
        private long[] keys; private CloudPoint[] values; private byte[] used; private int mask,size,threshold;
        LongCloudMap(int expected){int cap=16;long need=Math.max(16L,(long)expected*10L/7L);while(cap<need&&cap<(1<<26))cap<<=1;init(cap);}
        private void init(int cap){keys=new long[cap];values=new CloudPoint[cap];used=new byte[cap];mask=cap-1;threshold=(int)(cap*.70f);}
        CloudPoint get(long k){int i=slot(k);while(used[i]!=0){if(keys[i]==k)return values[i];i=(i+1)&mask;}return null;}
        void put(long k,CloudPoint v){if(size>=threshold)grow();int i=slot(k);while(used[i]!=0){if(keys[i]==k){values[i]=v;return;}i=(i+1)&mask;}used[i]=1;keys[i]=k;values[i]=v;size++;}
        void appendValues(List<CloudPoint> out){for(int i=0;i<used.length;i++)if(used[i]!=0)out.add(values[i]);}
        private void grow(){long[] ok=keys;CloudPoint[] ov=values;byte[] ou=used;int next=ok.length<<1;if(next<=0||next>(1<<27))return;init(next);size=0;for(int i=0;i<ou.length;i++)if(ou[i]!=0)put(ok[i],ov[i]);}
        private int slot(long k){long z=k;z^=z>>>33;z*=0xff51afd7ed558ccdl;z^=z>>>33;z*=0xc4ceb9fe1a85ec53l;z^=z>>>33;return ((int)z)&mask;}
    }

    private void put(LongCloudMap m,CloudPoint p,double v){if(!finite(p))return;int x=(int)Math.floor(p.x/v),y=(int)Math.floor(p.y/v),z=(int)Math.floor(p.z/v);long k=hash(x,y,z);CloudPoint old=m.get(k);if(old==null||p.confidence>old.confidence)m.put(k,p);}
    private long hash(int x,int y,int z){long h=1469598103934665603L;h=(h^x)*1099511628211L;h=(h^y)*1099511628211L;h=(h^z)*1099511628211L;return h;}
    private double rotateX(double x,double z,int q){switch((q%4+4)%4){case 1:return z;case 2:return -x;case 3:return -z;default:return x;}}
    private double rotateZ(double x,double z,int q){switch((q%4+4)%4){case 1:return -x;case 2:return -z;case 3:return x;default:return z;}}
    private boolean finite(CloudPoint p){return p!=null&&Float.isFinite(p.x)&&Float.isFinite(p.y)&&Float.isFinite(p.z);}
    private double quant(float[] a,int n,double q){if(n<=0)return 0;int i=(int)Math.round((n-1)*q);return a[Math.max(0,Math.min(n-1,i))];}
}
