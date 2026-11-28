package com.elektoryk.scan.scanner.ar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Persistent structural-surface tracker built from accepted ARCore Depth samples.
 *
 * The previous fallback stored independent 10 cm XYZ voxels and rendered every voxel as a
 * camera-facing square. That is useful for debugging a point cloud, but it is NOT a room model:
 * one wall becomes hundreds of floating squares and relocalization makes extra layers.
 *
 * This tracker fits actual planes, assigns them stable IDs, and keeps their coverage tiles in the
 * immutable scan-local coordinate system. Re-visiting a wall strengthens the same SurfaceTrack.
 */
public final class DepthSurfaceTracker {
    public enum Kind { WALL, FLOOR, CEILING }
    public static final float TILE_M=0.10f;

    private static final float INLIER_M=0.055f;
    private static final int RANSAC_ITERS=96;
    private static final int MAX_PLANES_PER_FRAME=4;
    private static final int MIN_FRAME_INLIERS=26;
    private static final float MERGE_PLANE_M=0.16f;
    private static final float MERGE_DOT=0.955f; // tolerate early wobble, then consolidate geometrically
    private static final long RECENT_VISIBLE_NS=1_100_000_000L;
    private static final long WEAK_TRACK_KEEP_NS=5_000_000_000L;
    private static final int MAX_TRACKS=10;
    private static final float MAX_EVIDENCE_JUMP_M=0.48f;
    private static final float MAX_EVIDENCE_SPEED_MPS=2.8f;
    private static final int MATURE_TRACK_FRAMES=8;
    private static final int MATURE_TRACK_SUPPORT=320;
    private static final float MATURE_MAX_EXPAND_PER_FRAME_M=0.30f;
    private static final long STALE_RED_TILE_NS=12_000_000_000L;
    private static final float REACQUIRE_DOT=0.940f;
    private static final float REACQUIRE_PLANE_M=0.28f;

    public static final class TileSnapshot {
        public int state;
        public long lastSeenNs;
        public float centerU,centerV;
    }

    public static final class SurfaceSnapshot {
        public int id;
        public Kind kind;
        public float ox,oy,oz;
        public float nx,ny,nz;
        public float ux,uy,uz;
        public float vx,vy,vz;
        public float minU,maxU,minV,maxV;
        public int independentFrames;
        public int supportPoints;
        public long lastSeenNs;
        public boolean architectural;
        public final List<TileSnapshot> tiles=new ArrayList<>();

        public float widthM(){return Math.max(0f,maxU-minU);}
        public float heightM(){return Math.max(0f,maxV-minV);}
        public float areaM2(){return widthM()*heightM();}
        private boolean geometryStable(){
            // A wardrobe side / jamb can easily look like a vertical plane for a couple frames.
            // Requiring a room-scale span keeps the live "wall count" architectural rather than
            // turning every tall object into another wall.
            if(kind==Kind.WALL)return independentFrames>=5&&supportPoints>=250&&widthM()>=1.15f&&heightM()>=1.90f&&areaM2()>=2.15f;
            return independentFrames>=5&&supportPoints>=150&&areaM2()>=0.95f;
        }
        public boolean stable(){return architectural;}
    }

    public static final class Snapshot {
        public final List<SurfaceSnapshot> surfaces=new ArrayList<>();
        public int redTiles,yellowTiles,greenTiles;
        public int recentRed,recentYellow,recentGreen;
        public int stableWalls,stableHorizontals;
        public int totalTracks;
        public int confirmedPercent(){
            int total=redTiles+yellowTiles+greenTiles;
            return total<=0?0:Math.max(0,Math.min(100,Math.round((yellowTiles+greenTiles)*100f/total)));
        }
        public boolean ready(){
            return stableWalls>=3 && stableWalls<=7 && stableHorizontals>=1 && (yellowTiles+greenTiles)>=56 && greenTiles>=18 && confirmedPercent()>=60;
        }
    }

    private static final class Tile {
        int lastFrameId=-1;
        int frames;
        int viewMask;
        boolean firstCamera;
        float firstCamX,firstCamY,firstCamZ;
        float maxBaseline;
        long lastSeenNs;
        int state(){
            if(frames>=3&&maxBaseline>=0.18f&&Integer.bitCount(viewMask)>=2)return 3;
            if(frames>=2&&(maxBaseline>=0.055f||Integer.bitCount(viewMask)>=2))return 2;
            return 1;
        }
    }

    private static final class Track {
        int id;
        Kind kind;
        float ox,oy,oz;
        float nx,ny,nz;
        float ux,uy,uz;
        float vx,vy,vz;
        float minU=Float.POSITIVE_INFINITY,maxU=Float.NEGATIVE_INFINITY;
        float minV=Float.POSITIVE_INFINITY,maxV=Float.NEGATIVE_INFINITY;
        int lastFrameId=-1;
        int frames;
        int supportPoints;
        long lastSeenNs;
        final TileMap tiles=new TileMap(256);
    }


    /** Primitive long -> Tile table used by structural coverage. Avoids Long boxing and
     * HashMap nodes on every accepted depth sample. Tombstones keep deletes cheap during
     * stale-red cleanup; tables compact themselves when tombstones accumulate. */
    private static final class TileMap {
        private long[] keys; private Tile[] values; private byte[] states; private int mask,size,tombs;
        TileMap(int requested){init(capacity(requested));}
        private static int capacity(int n){int c=16,target=Math.max(16,(int)(n/0.62f)+1);while(c<target)c<<=1;return c;}
        private void init(int c){keys=new long[c];values=new Tile[c];states=new byte[c];mask=c-1;size=tombs=0;}
        int size(){return size;}
        long keyAt(int i){return keys[i];}
        Tile valueAt(int i){return states[i]==1?values[i]:null;}
        int slots(){return states.length;}
        Tile get(long k){int i=find(k);return i>=0?values[i]:null;}
        Tile getOrCreate(long k){
            if((size+tombs+1)*10>=states.length*7)rehash(states.length<<1);
            int i=slot(k),firstTomb=-1;
            while(states[i]!=0){if(states[i]==1&&keys[i]==k)return values[i];if(states[i]==2&&firstTomb<0)firstTomb=i;i=(i+1)&mask;}
            if(firstTomb>=0){i=firstTomb;tombs--;}
            states[i]=1;keys[i]=k;Tile v=new Tile();values[i]=v;size++;return v;
        }
        void removeAt(int i){if(i<0||i>=states.length||states[i]!=1)return;states[i]=2;values[i]=null;size--;tombs++;if(tombs>size&&states.length>16)rehash(states.length);}
        private int find(long k){int i=slot(k);while(states[i]!=0){if(states[i]==1&&keys[i]==k)return i;i=(i+1)&mask;}return -1;}
        private int slot(long k){k^=k>>>33;k*=0xff51afd7ed558ccdl;k^=k>>>33;k*=0xc4ceb9fe1a85ec53l;k^=k>>>33;return ((int)k)&mask;}
        private void rehash(int cap){long[] ok=keys;Tile[] ov=values;byte[] os=states;init(cap);for(int i=0;i<os.length;i++)if(os[i]==1){long k=ok[i];Tile v=ov[i];int j=slot(k);while(states[j]==1)j=(j+1)&mask;states[j]=1;keys[j]=k;values[j]=v;size++;}}
    }

    private static final class Fit {
        Kind kind;
        float ox,oy,oz,nx,ny,nz,ux,uy,uz,vx,vy,vz;
        float minU,maxU,minV,maxV;
        int count;
        int[] indices;
    }

    private final List<Track> tracks=new ArrayList<>();
    private int nextId=1;
    private long lastEvidenceTs;
    private float lastEvidenceCamX,lastEvidenceCamY,lastEvidenceCamZ;
    private boolean hasEvidencePose;
    // Reused under synchronized integrate()/matching paths: avoid per-frame/per-track tiny arrays
    // that otherwise accumulate into visible GC churn during long room scans.
    private boolean[] availableWorkspace=new boolean[0];
    private int[] availableIndexWorkspace=new int[0];
    private int[] fitIndexWorkspace=new int[0];
    private final float[] gapWorkspace=new float[2];
    private final float[] boundsWorkspace=new float[4];
    // Reused robust-bound workspaces. integrateFit() runs under synchronized integrate(), so one
    // set is sufficient for every plane pass and avoids four medium float[] allocations per fit.
    private float[] fitUWorkspace=new float[0];
    private float[] fitVWorkspace=new float[0];
    private float[] fitSortUWorkspace=new float[0];
    private float[] fitSortVWorkspace=new float[0];

    private boolean[] ensureAvailableWorkspace(int n){
        if(availableWorkspace.length<n){
            int grown=Math.max(n,Math.max(256,availableWorkspace.length+(availableWorkspace.length>>1)+1));
            availableWorkspace=new boolean[grown];
        }
        return availableWorkspace;
    }
    private int[] ensureAvailableIndexWorkspace(int n){
        if(availableIndexWorkspace.length<n){
            int grown=Math.max(n,Math.max(256,availableIndexWorkspace.length+(availableIndexWorkspace.length>>1)+1));
            availableIndexWorkspace=new int[grown];
        }
        return availableIndexWorkspace;
    }
    private int[] ensureFitIndexWorkspace(int n){
        if(fitIndexWorkspace.length<n){
            int grown=Math.max(n,Math.max(256,fitIndexWorkspace.length+(fitIndexWorkspace.length>>1)+1));
            fitIndexWorkspace=new int[grown];
        }
        return fitIndexWorkspace;
    }
    private void ensureFitWorkspace(int n){
        if(fitUWorkspace.length>=n)return;
        int grown=Math.max(n,Math.max(256,fitUWorkspace.length+(fitUWorkspace.length>>1)+1));
        fitUWorkspace=new float[grown];fitVWorkspace=new float[grown];
        fitSortUWorkspace=new float[grown];fitSortVWorkspace=new float[grown];
    }

    public synchronized void reset(){tracks.clear();nextId=1;lastEvidenceTs=0;lastEvidenceCamX=lastEvidenceCamY=lastEvidenceCamZ=0;hasEvidencePose=false;}

    public synchronized void integrate(ArCoreDepthCollector.DepthEvidenceFrame ev){
        if(ev==null||ev.localXyz==null||ev.pointCount<MIN_FRAME_INLIERS)return;
        if(ev.timestampNs>0&&lastEvidenceTs>0&&ev.timestampNs<=lastEvidenceTs)return;
        if(hasEvidencePose&&lastEvidenceTs>0&&ev.timestampNs>lastEvidenceTs){
            float dx=ev.localCameraX-lastEvidenceCamX,dy=ev.localCameraY-lastEvidenceCamY,dz=ev.localCameraZ-lastEvidenceCamZ;
            float jump=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);double dt=(ev.timestampNs-lastEvidenceTs)/1_000_000_000.0;
            if((dt<0.85&&jump>MAX_EVIDENCE_JUMP_M)||(dt>0.03&&dt<1.2&&jump/dt>MAX_EVIDENCE_SPEED_MPS))return;
        }
        int n=Math.min(ev.pointCount,ev.localXyz.length/3);
        if(n<MIN_FRAME_INLIERS)return;
        boolean[] available=ensureAvailableWorkspace(n);
        for(int i=0;i<n;i++)available[i]=finite3(ev.localXyz,i*3);

        for(int pass=0;pass<MAX_PLANES_PER_FRAME;pass++){
            Fit fit=fitBest(ev.localXyz,n,available,ev.localCameraY,ev.frameId,pass);
            if(fit==null||fit.count<MIN_FRAME_INLIERS)break;
            Track t=findMatch(fit);
            if(t==null){
                if(tracks.size()>=MAX_TRACKS)dropWeakest(ev.timestampNs);
                if(tracks.size()>=MAX_TRACKS)break;
                t=createTrack(fit);
                tracks.add(t);
            }
            integrateFit(t,fit,ev);
            for(int ii=0;ii<fit.count;ii++){int idx=fit.indices[ii];if(idx>=0&&idx<n)available[idx]=false;}
        }
        consolidateTracks();
        cleanup(ev.timestampNs);
        lastEvidenceTs=Math.max(lastEvidenceTs,ev.timestampNs);lastEvidenceCamX=ev.localCameraX;lastEvidenceCamY=ev.localCameraY;lastEvidenceCamZ=ev.localCameraZ;hasEvidencePose=true;
    }

    public synchronized Snapshot snapshot(long nowNs){
        Snapshot out=new Snapshot();
        out.totalTracks=tracks.size();
        List<Track> sorted=new ArrayList<>(tracks);
        Collections.sort(sorted,new Comparator<Track>(){
            @Override public int compare(Track a,Track b){return Integer.compare(b.supportPoints,a.supportPoints);}
        });
        for(Track t:sorted){
            SurfaceSnapshot s=new SurfaceSnapshot();
            s.id=t.id;s.kind=t.kind;s.ox=t.ox;s.oy=t.oy;s.oz=t.oz;
            s.nx=t.nx;s.ny=t.ny;s.nz=t.nz;s.ux=t.ux;s.uy=t.uy;s.uz=t.uz;s.vx=t.vx;s.vy=t.vy;s.vz=t.vz;
            s.minU=t.minU;s.maxU=t.maxU;s.minV=t.minV;s.maxV=t.maxV;
            s.independentFrames=t.frames;s.supportPoints=t.supportPoints;s.lastSeenNs=t.lastSeenNs;
            for(int slot=0;slot<t.tiles.slots();slot++){
                Tile z=t.tiles.valueAt(slot);if(z==null)continue;long tileKey=t.tiles.keyAt(slot);
                int state=z.state();if(state==1)out.redTiles++;else if(state==2)out.yellowTiles++;else out.greenTiles++;
                if(nowNs-z.lastSeenNs<=RECENT_VISIBLE_NS){
                    if(state==1)out.recentRed++;else if(state==2)out.recentYellow++;else out.recentGreen++;
                }
                TileSnapshot ts=new TileSnapshot();ts.state=state;ts.lastSeenNs=z.lastSeenNs;
                int iu=(int)(tileKey>>32),iv=(int)tileKey;
                ts.centerU=(iu+0.5f)*TILE_M;ts.centerV=(iv+0.5f)*TILE_M;s.tiles.add(ts);
            }
            out.surfaces.add(s);
        }
        classifyArchitectural(out);
        return out;
    }

    private void classifyArchitectural(Snapshot out){
        SurfaceSnapshot floor=null,ceiling=null;
        for(SurfaceSnapshot s:out.surfaces){
            if(s==null||!s.geometryStable())continue;
            if(s.kind==Kind.FLOOR&&(floor==null||s.supportPoints>floor.supportPoints))floor=s;
            if(s.kind==Kind.CEILING&&(ceiling==null||s.supportPoints>ceiling.supportPoints))ceiling=s;
        }
        float floorY=floor==null?Float.NaN:floor.oy,ceilY=ceiling==null?Float.NaN:ceiling.oy;
        for(SurfaceSnapshot s:out.surfaces){
            if(s==null||!s.geometryStable()){if(s!=null)s.architectural=false;continue;}
            boolean ok=true;
            if(s.kind==Kind.WALL){
                float y0=Math.min(s.oy+s.vy*s.minV,s.oy+s.vy*s.maxV);
                float y1=Math.max(s.oy+s.vy*s.minV,s.oy+s.vy*s.maxV);
                if(Float.isFinite(floorY)&&y0>floorY+0.45f)ok=false;
                if(Float.isFinite(ceilY)&&y1<ceilY-0.55f)ok=false;
            }
            s.architectural=ok;
            if(ok){if(s.kind==Kind.WALL)out.stableWalls++;else out.stableHorizontals++;}
        }
    }

    private Fit fitBest(float[] xyz,int n,boolean[] available,float cameraY,int frameId,int pass){
        float frameMinY=Float.POSITIVE_INFINITY,frameMaxY=Float.NEGATIVE_INFINITY;
        int[] availableIdx=ensureAvailableIndexWorkspace(n);int availableCount=0;
        for(int q=0;q<n;q++)if(available[q]){
            availableIdx[availableCount++]=q;int o=q*3;
            frameMinY=Math.min(frameMinY,xyz[o+1]);frameMaxY=Math.max(frameMaxY,xyz[o+1]);
        }
        if(availableCount<MIN_FRAME_INLIERS)return null;
        final float verticalTrim=(frameMaxY-frameMinY)>1.6f?0.14f:0.07f;
        int bestCount=0;float bnx=0,bny=0,bnz=0,bd=0;
        long seed=((long)frameId*1103515245L+12345L+pass*2654435761L)&0x7fffffffL;
        for(int it=0;it<RANSAC_ITERS;it++){
            // Sample only from points that survived earlier plane passes. Sampling from all n and
            // rejecting removed points made later wall/floor passes progressively under-sampled.
            seed=(seed*1664525L+1013904223L)&0x7fffffffL;int i=availableIdx[(int)(seed%availableCount)];
            seed=(seed*1664525L+1013904223L)&0x7fffffffL;int j=availableIdx[(int)(seed%availableCount)];
            seed=(seed*1664525L+1013904223L)&0x7fffffffL;int k=availableIdx[(int)(seed%availableCount)];
            if(i==j||i==k||j==k)continue;
            int ai=i*3,aj=j*3,ak=k*3;
            float ax=xyz[aj]-xyz[ai],ay=xyz[aj+1]-xyz[ai+1],az=xyz[aj+2]-xyz[ai+2];
            float bx=xyz[ak]-xyz[ai],by=xyz[ak+1]-xyz[ai+1],bz=xyz[ak+2]-xyz[ai+2];
            float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
            float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);if(len<0.02f)continue;
            nx/=len;ny/=len;nz/=len;
            float any=Math.abs(ny);
            if(!(any>=0.86f||any<=0.28f))continue; // architectural planes only
            boolean verticalCandidate=any<=0.28f;
            float d=-(nx*xyz[ai]+ny*xyz[ai+1]+nz*xyz[ai+2]);
            int count=0;
            for(int q=0;q<n;q++)if(available[q]){
                int o=q*3;float dist=Math.abs(nx*xyz[o]+ny*xyz[o+1]+nz*xyz[o+2]+d);
                if(dist<=INLIER_M){
                    // Floor/ceiling cross-sections can lie on the same X/Z plane as a wardrobe
                    // side and artificially make it look full-height. Ignore the extreme
                    // horizontal layers while fitting vertical architecture.
                    if(verticalCandidate&&(xyz[o+1]<=frameMinY+verticalTrim||xyz[o+1]>=frameMaxY-verticalTrim))continue;
                    count++;
                }
            }
            if(count>bestCount){bestCount=count;bnx=nx;bny=ny;bnz=nz;bd=d;}
        }
        if(bestCount<MIN_FRAME_INLIERS)return null;
        int[] idx=ensureFitIndexWorkspace(bestCount);int m=0;boolean bestVertical=Math.abs(bny)<=0.28f;
        for(int q=0;q<n;q++)if(available[q]){int o=q*3;if(Math.abs(bnx*xyz[o]+bny*xyz[o+1]+bnz*xyz[o+2]+bd)<=INLIER_M){
            if(bestVertical&&(xyz[o+1]<=frameMinY+verticalTrim||xyz[o+1]>=frameMaxY-verticalTrim))continue;
            idx[m++]=q;
        }}
        if(m<MIN_FRAME_INLIERS)return null;
        return refine(xyz,idx,m,cameraY);
    }

    private Fit refine(float[] xyz,int[] idx,int n,float cameraY){
        double mx=0,my=0,mz=0;
        for(int ii=0;ii<n;ii++){int q=idx[ii],o=q*3;mx+=xyz[o];my+=xyz[o+1];mz+=xyz[o+2];}
        mx/=n;my/=n;mz/=n;
        double cxx=0,czz=0,cxz=0;
        for(int ii=0;ii<n;ii++){int q=idx[ii],o=q*3;double dx=xyz[o]-mx,dz=xyz[o+2]-mz;cxx+=dx*dx;czz+=dz*dz;cxz+=dx*dz;}
        double theta=0.5*Math.atan2(2*cxz,cxx-czz);
        float tx=(float)Math.cos(theta),tz=(float)Math.sin(theta);
        // Tangent points along largest XZ variance. If this is a horizontal surface orientation is cosmetic.
        Fit f=new Fit();f.indices=idx;f.count=n;f.ox=(float)mx;f.oy=(float)my;f.oz=(float)mz;

        float minY=Float.POSITIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        float minU=Float.POSITIVE_INFINITY,maxU=Float.NEGATIVE_INFINITY;
        float minW=Float.POSITIVE_INFINITY,maxW=Float.NEGATIVE_INFINITY;
        for(int ii=0;ii<n;ii++){int q=idx[ii],o=q*3;float x=xyz[o],y=xyz[o+1],z=xyz[o+2];
            minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            float u=(x-f.ox)*tx+(z-f.oz)*tz;float w=-(x-f.ox)*tz+(z-f.oz)*tx;
            minU=Math.min(minU,u);maxU=Math.max(maxU,u);minW=Math.min(minW,w);maxW=Math.max(maxW,w);
        }
        float spanY=maxY-minY,spanU=maxU-minU,spanW=maxW-minW;
        boolean vertical=spanY>=0.58f && spanY>=spanW*1.6f;
        if(vertical){
            f.kind=Kind.WALL;f.ux=tx;f.uy=0;f.uz=tz;f.vx=0;f.vy=1;f.vz=0;
            f.nx=-tz;f.ny=0;f.nz=tx;
            f.minU=minU;f.maxU=maxU;f.minV=minY-f.oy;f.maxV=maxY-f.oy;
            if(spanU<0.42f||spanY<0.62f)return null;
        }else{
            // Horizontal plane requires small vertical thickness.
            if(spanY>0.18f)return null;
            if(f.oy<cameraY-0.95f)f.kind=Kind.FLOOR;
            else if(f.oy>cameraY+0.55f)f.kind=Kind.CEILING;
            else return null; // table/shelf: not an architectural surface
            f.nx=0;f.ny=f.kind==Kind.FLOOR?1f:-1f;f.nz=0;
            f.ux=1;f.uy=0;f.uz=0;f.vx=0;f.vy=0;f.vz=1;
            f.minU=Float.POSITIVE_INFINITY;f.maxU=Float.NEGATIVE_INFINITY;f.minV=Float.POSITIVE_INFINITY;f.maxV=Float.NEGATIVE_INFINITY;
            for(int ii=0;ii<n;ii++){int q=idx[ii],o=q*3;float u=xyz[o]-f.ox,v=xyz[o+2]-f.oz;f.minU=Math.min(f.minU,u);f.maxU=Math.max(f.maxU,u);f.minV=Math.min(f.minV,v);f.maxV=Math.max(f.maxV,v);}
            if((f.maxU-f.minU)*(f.maxV-f.minV)<0.22f)return null;
        }
        return f;
    }

    private Track findMatch(Fit f){
        Track best=null;float bestScore=Float.POSITIVE_INFINITY;
        for(Track t:tracks){
            if(t.kind!=f.kind)continue;
            float dot=Math.abs(t.nx*f.nx+t.ny*f.ny+t.nz*f.nz);
            float dx=f.ox-t.ox,dy=f.oy-t.oy,dz=f.oz-t.oz;
            float sep=Math.abs(dx*t.nx+dy*t.ny+dz*t.nz);

            boolean normalMatch=dot>=MERGE_DOT&&sep<=MERGE_PLANE_M;
            boolean mature=t.frames>=MATURE_TRACK_FRAMES&&t.supportPoints>=MATURE_TRACK_SUPPORT;
            boolean reacquire=false;
            if(!normalMatch&&mature&&dot>=REACQUIRE_DOT&&sep<=REACQUIRE_PLANE_M){
                fitGapToTrack(f,t,gapWorkspace);
                reacquire=gapWorkspace[0]<=0.75f&&gapWorkspace[1]<=0.85f;
            }
            if(!normalMatch&&!reacquire)continue;
            // Prefer the canonical mature wall when ARCore/Depth wobbles after a long scan.
            float score=sep+(1f-dot)*0.85f-(mature?0.025f:0f);
            if(score<bestScore){bestScore=score;best=t;}
        }
        return best;
    }

    private void fitGapToTrack(Fit f,Track t,float[] out){
        float minU=Float.POSITIVE_INFINITY,maxU=Float.NEGATIVE_INFINITY;
        float minV=Float.POSITIVE_INFINITY,maxV=Float.NEGATIVE_INFINITY;
        for(int corner=0;corner<4;corner++){
            float u=(corner&1)==0?f.minU:f.maxU;
            float v=(corner&2)==0?f.minV:f.maxV;
            float x=f.ox+f.ux*u+f.vx*v;
            float y=f.oy+f.uy*u+f.vy*v;
            float z=f.oz+f.uz*u+f.vz*v;
            float rx=x-t.ox,ry=y-t.oy,rz=z-t.oz;
            float tu=rx*t.ux+ry*t.uy+rz*t.uz;
            float tv=rx*t.vx+ry*t.vy+rz*t.vz;
            minU=Math.min(minU,tu);maxU=Math.max(maxU,tu);minV=Math.min(minV,tv);maxV=Math.max(maxV,tv);
        }
        out[0]=intervalGap(t.minU,t.maxU,minU,maxU);
        out[1]=intervalGap(t.minV,t.maxV,minV,maxV);
    }

    /**
     * RANSAC may start the same physical wall as two tracks while the camera pose/depth estimate
     * is still settling.  Merge those tracks in scan-local coordinates instead of letting both
     * survive forever and appear as parallel layers in the overlay/model.
     */
    private void consolidateTracks(){
        boolean changed=true;int guard=0;
        while(changed&&guard++<24){
            changed=false;
            outer:for(int i=0;i<tracks.size();i++){
                Track a=tracks.get(i);
                for(int j=i+1;j<tracks.size();j++){
                    Track b=tracks.get(j);
                    if(!samePhysicalSurface(a,b))continue;
                    Track keep=a.supportPoints>=b.supportPoints?a:b;
                    Track drop=keep==a?b:a;
                    mergeInto(keep,drop);
                    tracks.remove(drop);changed=true;break outer;
                }
            }
        }
    }

    private boolean samePhysicalSurface(Track a,Track b){
        if(a==null||b==null||a.kind!=b.kind)return false;
        float dot=Math.abs(a.nx*b.nx+a.ny*b.ny+a.nz*b.nz);if(dot<0.968f)return false;
        float dx=b.ox-a.ox,dy=b.oy-a.oy,dz=b.oz-a.oz;
        float sep=Math.abs(dx*a.nx+dy*a.ny+dz*a.nz);if(sep>0.13f)return false;

        // Project B's bounding box into A's basis.  Nearby/overlapping pieces of the same plane
        // merge; a parallel opposite wall or cabinet face remains separate.
        projectBounds(b,a,boundsWorkspace);
        float gapU=intervalGap(a.minU,a.maxU,boundsWorkspace[0],boundsWorkspace[1]);
        float gapV=intervalGap(a.minV,a.maxV,boundsWorkspace[2],boundsWorkspace[3]);
        if(a.kind==Kind.WALL)return gapU<=0.55f&&gapV<=0.70f;
        return gapU<=0.65f&&gapV<=0.65f;
    }

    private void projectBounds(Track from,Track to,float[] out){
        float minU=Float.POSITIVE_INFINITY,maxU=Float.NEGATIVE_INFINITY;
        float minV=Float.POSITIVE_INFINITY,maxV=Float.NEGATIVE_INFINITY;
        for(int corner=0;corner<4;corner++){
            float u=(corner&1)==0?from.minU:from.maxU;
            float v=(corner&2)==0?from.minV:from.maxV;
            float x=from.ox+from.ux*u+from.vx*v;
            float y=from.oy+from.uy*u+from.vy*v;
            float z=from.oz+from.uz*u+from.vz*v;
            float rx=x-to.ox,ry=y-to.oy,rz=z-to.oz;
            float tu=rx*to.ux+ry*to.uy+rz*to.uz;
            float tv=rx*to.vx+ry*to.vy+rz*to.vz;
            minU=Math.min(minU,tu);maxU=Math.max(maxU,tu);minV=Math.min(minV,tv);maxV=Math.max(maxV,tv);
        }
        out[0]=minU;out[1]=maxU;out[2]=minV;out[3]=maxV;
    }

    private float intervalGap(float a0,float a1,float b0,float b1){
        if(a1<b0)return b0-a1;if(b1<a0)return a0-b1;return 0f;
    }

    private void mergeInto(Track keep,Track drop){
        for(int slot=0;slot<drop.tiles.slots();slot++){
            Tile src=drop.tiles.valueAt(slot);if(src==null)continue;long dropKey=drop.tiles.keyAt(slot);
            int iu=(int)(dropKey>>32),iv=(int)dropKey;
            float u=(iu+0.5f)*TILE_M,v=(iv+0.5f)*TILE_M;
            float x=drop.ox+drop.ux*u+drop.vx*v;
            float y=drop.oy+drop.uy*u+drop.vy*v;
            float z=drop.oz+drop.uz*u+drop.vz*v;
            float rx=x-keep.ox,ry=y-keep.oy,rz=z-keep.oz;
            float ku=rx*keep.ux+ry*keep.uy+rz*keep.uz;
            float kv=rx*keep.vx+ry*keep.vy+rz*keep.vz;
            int kiu=(int)Math.floor(ku/TILE_M),kiv=(int)Math.floor(kv/TILE_M);long kk=key(kiu,kiv);
            Tile dst=keep.tiles.getOrCreate(kk);
            mergeTile(dst,src);
            keep.minU=Math.min(keep.minU,(kiu)*TILE_M);keep.maxU=Math.max(keep.maxU,(kiu+1)*TILE_M);
            keep.minV=Math.min(keep.minV,(kiv)*TILE_M);keep.maxV=Math.max(keep.maxV,(kiv+1)*TILE_M);
        }
        keep.frames=Math.max(keep.frames,drop.frames);
        keep.supportPoints=Math.min(1_000_000,keep.supportPoints+drop.supportPoints/2);
        keep.lastSeenNs=Math.max(keep.lastSeenNs,drop.lastSeenNs);
        keep.lastFrameId=Math.max(keep.lastFrameId,drop.lastFrameId);
    }

    private void mergeTile(Tile dst,Tile src){
        if(src==null||dst==null)return;
        dst.frames=Math.max(dst.frames,src.frames);dst.viewMask|=src.viewMask;
        dst.maxBaseline=Math.max(dst.maxBaseline,src.maxBaseline);dst.lastSeenNs=Math.max(dst.lastSeenNs,src.lastSeenNs);
        dst.lastFrameId=Math.max(dst.lastFrameId,src.lastFrameId);
        if(!dst.firstCamera&&src.firstCamera){dst.firstCamera=true;dst.firstCamX=src.firstCamX;dst.firstCamY=src.firstCamY;dst.firstCamZ=src.firstCamZ;}
    }

    private Track createTrack(Fit f){
        Track t=new Track();t.id=nextId++;t.kind=f.kind;t.ox=f.ox;t.oy=f.oy;t.oz=f.oz;
        t.nx=f.nx;t.ny=f.ny;t.nz=f.nz;t.ux=f.ux;t.uy=f.uy;t.uz=f.uz;t.vx=f.vx;t.vy=f.vy;t.vz=f.vz;
        return t;
    }

    private void integrateFit(Track t,Fit f,ArCoreDepthCollector.DepthEvidenceFrame ev){
        if(t.lastFrameId!=ev.frameId){t.lastFrameId=ev.frameId;t.frames++;}
        t.lastSeenNs=ev.timestampNs;t.supportPoints=Math.min(1_000_000,t.supportPoints+f.count);
        final boolean mature=t.frames>=MATURE_TRACK_FRAMES&&t.supportPoints>=MATURE_TRACK_SUPPORT
                &&Float.isFinite(t.minU)&&Float.isFinite(t.maxU)&&Float.isFinite(t.minV)&&Float.isFinite(t.maxV);
        final float oldMinU=t.minU,oldMaxU=t.maxU,oldMinV=t.minV,oldMaxV=t.maxV;
        float[] xyz=ev.localXyz;

        // Robust per-frame bounds: isolated Depth spikes must not permanently stretch a wall.
        // Keep unsorted U/V paired for tile integration, and sort only reusable scratch copies for
        // quantiles. The old code allocated four float arrays for every fitted plane/frame.
        ensureFitWorkspace(f.indices.length);
        float[] us=fitUWorkspace,vs=fitVWorkspace;int m=0;
        for(int q:f.indices){
            int o=q*3;float dx=xyz[o]-t.ox,dy=xyz[o+1]-t.oy,dz=xyz[o+2]-t.oz;
            float normal=Math.abs(dx*t.nx+dy*t.ny+dz*t.nz);if(normal>INLIER_M*1.45f)continue;
            us[m]=dx*t.ux+dy*t.uy+dz*t.uz;vs[m]=dx*t.vx+dy*t.vy+dz*t.vz;m++;
        }
        if(m<MIN_FRAME_INLIERS)return;
        System.arraycopy(us,0,fitSortUWorkspace,0,m);System.arraycopy(vs,0,fitSortVWorkspace,0,m);
        Arrays.sort(fitSortUWorkspace,0,m);Arrays.sort(fitSortVWorkspace,0,m);
        int lo=Math.max(0,(int)Math.floor((m-1)*0.055)),hi=Math.min(m-1,(int)Math.ceil((m-1)*0.945));
        float loU=fitSortUWorkspace[lo]-0.08f,hiU=fitSortUWorkspace[hi]+0.08f,loV=fitSortVWorkspace[lo]-0.08f,hiV=fitSortVWorkspace[hi]+0.08f;

        for(int i=0;i<m;i++){
            float u=us[i],v=vs[i];if(u<loU||u>hiU||v<loV||v>hiV)continue;
            int iu=(int)Math.floor(u/TILE_M),iv=(int)Math.floor(v/TILE_M);long key=key(iu,iv);
            Tile tile=t.tiles.getOrCreate(key);tile.lastSeenNs=ev.timestampNs;
            if(tile.lastFrameId!=ev.frameId){
                tile.lastFrameId=ev.frameId;tile.frames=Math.min(100000,tile.frames+1);
                if(!tile.firstCamera){tile.firstCamera=true;tile.firstCamX=ev.localCameraX;tile.firstCamY=ev.localCameraY;tile.firstCamZ=ev.localCameraZ;}
                else{float cx=ev.localCameraX-tile.firstCamX,cy=ev.localCameraY-tile.firstCamY,cz=ev.localCameraZ-tile.firstCamZ;float b=(float)Math.sqrt(cx*cx+cy*cy+cz*cz);if(Float.isFinite(b))tile.maxBaseline=Math.max(tile.maxBaseline,b);}
                float ax=ev.localCameraX-(t.ox+t.ux*u+t.vx*v),az=ev.localCameraZ-(t.oz+t.uz*u+t.vz*v);
                int bin=(int)Math.floor((Math.atan2(ax,az)+Math.PI)/(2*Math.PI)*8.0);bin=Math.max(0,Math.min(7,bin));tile.viewMask|=1<<bin;
            }
            // Mature surfaces are "sticky": a single noisy frame may create a provisional tile,
            // but it cannot permanently stretch a confirmed wall. Expansion must be adjacent and
            // independently confirmed (yellow/green). This keeps long scans from slowly growing
            // walls into furniture, door jambs or relocalization ghosts.
            boolean canGrow=true;
            if(mature){
                float cellMinU=iu*TILE_M,cellMaxU=(iu+1)*TILE_M,cellMinV=iv*TILE_M,cellMaxV=(iv+1)*TILE_M;
                boolean outside=cellMaxU<oldMinU||cellMinU>oldMaxU||cellMaxV<oldMinV||cellMinV>oldMaxV;
                float gapU=intervalGap(oldMinU,oldMaxU,cellMinU,cellMaxU);
                float gapV=intervalGap(oldMinV,oldMaxV,cellMinV,cellMaxV);
                if(outside&&(tile.state()<2||gapU>MATURE_MAX_EXPAND_PER_FRAME_M||gapV>MATURE_MAX_EXPAND_PER_FRAME_M))canGrow=false;
            }
            if(canGrow){
                t.minU=Math.min(t.minU,iu*TILE_M);t.maxU=Math.max(t.maxU,(iu+1)*TILE_M);
                t.minV=Math.min(t.minV,iv*TILE_M);t.maxV=Math.max(t.maxV,(iv+1)*TILE_M);
            }
        }
    }

    private void cleanup(long nowNs){
        Iterator<Track> it=tracks.iterator();while(it.hasNext()){
            Track t=it.next();
            for(int slot=0;slot<t.tiles.slots();slot++){
                Tile z=t.tiles.valueAt(slot);
                if(z!=null&&z.state()==1&&nowNs-z.lastSeenNs>STALE_RED_TILE_NS)t.tiles.removeAt(slot);
            }
            if(t.frames<3&&nowNs-t.lastSeenNs>WEAK_TRACK_KEEP_NS)it.remove();
        }
    }

    private void dropWeakest(long nowNs){
        Track weak=null;double score=Double.POSITIVE_INFINITY;
        for(Track t:tracks){double s=t.frames*20.0+t.supportPoints*0.02-(nowNs-t.lastSeenNs)/1e9;if(s<score){score=s;weak=t;}}
        if(weak!=null)tracks.remove(weak);
    }

    private static boolean finite3(float[] a,int o){return o+2<a.length&&Float.isFinite(a[o])&&Float.isFinite(a[o+1])&&Float.isFinite(a[o+2]);}
    private static long key(int a,int b){return ((long)a<<32)^(b&0xffffffffL);}
}
