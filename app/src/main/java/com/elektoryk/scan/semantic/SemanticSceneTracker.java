package com.elektoryk.scan.semantic;

import com.elektoryk.scan.model.SemanticEntity;
import com.elektoryk.scan.scanner.ar.ArCoreDepthCollector;
import com.elektoryk.scan.scanner.ar.ScanVisualKeyframeRecorder;
import com.google.ar.core.Pose;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.Iterator;

/**
 * Fuses per-pixel RGB semantics with metric Depth points in scan-local coordinates.
 * This is the scene-memory layer: the same wardrobe/wall is updated instead of re-created per frame.
 */
public final class SemanticSceneTracker {
    public static final class Record {
        public final SemanticFrame frame;
        public final ScanVisualKeyframeRecorder.Meta meta;
        Record(SemanticFrame f,ScanVisualKeyframeRecorder.Meta m){frame=f;meta=m;}
    }
    public static final class Snapshot {
        public SemanticLabel centerLabel=SemanticLabel.UNKNOWN;
        public float centerShare;
        public int entityCount;
        public int wallCount,doorCount,windowCount,objectCount;
        public double targetWidthM,targetHeightM,targetDepthM,targetConfidence;
        public long lastSemanticTimestampNs;
    }
    private static final class Candidate {
        SemanticLabel label;int rawClass;
        int n;double sx,sy,sz,sxx,szz,sxz;
        // Robust paired moments are rebuilt from the trimmed reservoir. Raw moments above remain
        // useful for support accounting, but must not steer pose after boundary-depth outliers.
        int robustN;double robustSx,robustSy,robustSz,robustSxx,robustSzz,robustSxz;
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY,minZ=Double.POSITIVE_INFINITY;
        double maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        Candidate(SemanticLabel label,int rawClass){reset(label,rawClass);}
        void reset(SemanticLabel newLabel,int newRawClass){label=newLabel;rawClass=newRawClass;n=0;sx=sy=sz=sxx=szz=sxz=0;robustN=0;robustSx=robustSy=robustSz=robustSxx=robustSzz=robustSxz=0;minX=minY=minZ=Double.POSITIVE_INFINITY;maxX=maxY=maxZ=Double.NEGATIVE_INFINITY;sampleN=0;}
        private static final int SAMPLE_CAP=256;
        final float[] sampleX=new float[SAMPLE_CAP],sampleY=new float[SAMPLE_CAP],sampleZ=new float[SAMPLE_CAP];
        // Reusable robust-statistics scratch. Candidate pooling removed the reservoir allocations in
        // v0.25.8, but finalizeRobustBounds() still copied three arrays and extentAtYaw() allocated a
        // projection array on every call. Those hidden allocations were now the dominant semantic hot
        // path. Keep scratch alongside the pooled candidate: bounded pool memory, zero per-candidate
        // quantile/projection arrays, and (critically) preserve X/Z sample pairing for PCA projections.
        final float[] scratchX=new float[SAMPLE_CAP],scratchY=new float[SAMPLE_CAP],scratchZ=new float[SAMPLE_CAP],scratchProjection=new float[SAMPLE_CAP];int sampleN;
        void add(float x,float y,float z){n++;sx+=x;sy+=y;sz+=z;sxx+=x*x;szz+=z*z;sxz+=x*z;
            // Fixed-memory deterministic reservoir. Robust quantiles below stop one leaking Depth
            // point at a semantic boundary from turning a chair/door into a room-sized AABB.
            int slot;if(sampleN<SAMPLE_CAP){slot=sampleN++;}else{long h=(n*1103515245L+rawClass*12345L)&0x7fffffffL;int pick=(int)(h%n);if(pick>=SAMPLE_CAP)return;slot=pick;}
            sampleX[slot]=x;sampleY[slot]=y;sampleZ[slot]=z;
        }
        void finalizeRobustBounds(){
            if(sampleN<=0)return;
            System.arraycopy(sampleX,0,scratchX,0,sampleN);System.arraycopy(sampleY,0,scratchY,0,sampleN);System.arraycopy(sampleZ,0,scratchZ,0,sampleN);
            java.util.Arrays.sort(scratchX,0,sampleN);java.util.Arrays.sort(scratchY,0,sampleN);java.util.Arrays.sort(scratchZ,0,sampleN);int trim=sampleN>=24?Math.max(1,(int)(sampleN*.035f)):0;int hi=Math.max(trim,sampleN-1-trim);
            minX=scratchX[trim];maxX=scratchX[hi];minY=scratchY[trim];maxY=scratchY[hi];minZ=scratchZ[trim];maxZ=scratchZ[hi];
            // Quantile bounds alone were not enough: centroid/PCA still used raw moments accumulated
            // before trimming, so a handful of leaking boundary Depth points could rotate or drag a
            // sofa/bed/table even though their AABB had already rejected those points. Rebuild paired
            // moments from the SAME reservoir samples that survive all three robust bounds. This keeps
            // X/Z pairing intact and makes geometry, center and orientation share one evidence set.
            robustN=0;robustSx=robustSy=robustSz=robustSxx=robustSzz=robustSxz=0;
            for(int i=0;i<sampleN;i++){float x=sampleX[i],y=sampleY[i],z=sampleZ[i];
                if(x<minX||x>maxX||y<minY||y>maxY||z<minZ||z>maxZ)continue;
                robustN++;robustSx+=x;robustSy+=y;robustSz+=z;robustSxx+=x*x;robustSzz+=z*z;robustSxz+=x*z;
            }
            // Tiny components do not have enough samples for a stable trimmed estimator. Fall back to
            // raw moments rather than producing a degenerate pose; normal semantic thresholds still apply.
            if(robustN<4){robustN=n;robustSx=sx;robustSy=sy;robustSz=sz;robustSxx=sxx;robustSzz=szz;robustSxz=sxz;}
        }
        double yawDeg(){int q=robustN>0?robustN:n;double ax=robustN>0?robustSx:sx,az=robustN>0?robustSz:sz,axx=robustN>0?robustSxx:sxx,azz=robustN>0?robustSzz:szz,axz=robustN>0?robustSxz:sxz;if(q<4)return 0;double mx=ax/q,mz=az/q,a=Math.max(0,axx/q-mx*mx),d=Math.max(0,azz/q-mz*mz),b=axz/q-mx*mz;return Math.toDegrees(.5*Math.atan2(2*b,a-d));}
        double orientationReliability(){
            int q=robustN>0?robustN:n;double ax=robustN>0?robustSx:sx,az=robustN>0?robustSz:sz,axx=robustN>0?robustSxx:sxx,azz=robustN>0?robustSzz:szz,axz=robustN>0?robustSxz:sxz;if(q<6)return 0;double mx=ax/q,mz=az/q,a=Math.max(0,axx/q-mx*mx),d=Math.max(0,azz/q-mz*mz),b=axz/q-mx*mz;
            double tr=a+d,disc=Math.sqrt(Math.max(0,(a-d)*(a-d)+4*b*b));
            return tr<1e-8?0:Math.max(0,Math.min(1,disc/tr));
        }
        double orientedWidth(){return extentAtYaw(yawDeg(),true);}
        double orientedDepth(){return extentAtYaw(yawDeg(),false);}
        double extentAtYaw(double yawDeg,boolean along){
            if(sampleN<4)return along?Math.max(0,maxX-minX):Math.max(0,maxZ-minZ);
            double yaw=Math.toRadians(yawDeg),ux=Math.cos(yaw),uz=Math.sin(yaw),vx=-uz,vz=ux;
            // v0.28.8 rebuilt center/PCA from the robust 3D survivor set, but oriented dimensions
            // still projected EVERY reservoir sample. A boundary leak rejected by the robust AABB
            // could therefore no longer rotate a sofa/bed, yet could still make its oriented width
            // or depth room-sized. Project exactly the same paired survivor set used by the pose.
            float[] q=scratchProjection;int qn=0;
            for(int i=0;i<sampleN;i++){float x=sampleX[i],y=sampleY[i],z=sampleZ[i];
                if(robustN>=4&&(x<minX||x>maxX||y<minY||y>maxY||z<minZ||z>maxZ))continue;
                q[qn++]=(float)(x*(along?ux:vx)+z*(along?uz:vz));
            }
            if(qn<4)return along?Math.max(.01,maxX-minX):Math.max(.01,maxZ-minZ);
            java.util.Arrays.sort(q,0,qn);
            // The 3D survivor set is already quantile-trimmed. A second 3.5% projection trim would
            // systematically shrink legitimate corners, especially on partial views, so use the
            // full robust projection envelope here.
            return Math.max(.01,q[qn-1]-q[0]);
        }
        double cx(){return robustN>0?robustSx/robustN:(n<=0?0:sx/n);}double cy(){return robustN>0?robustSy/robustN:(n<=0?0:sy/n);}double cz(){return robustN>0?robustSz/robustN:(n<=0?0:sz/n);}
        double horizontalPlanarityRatio(){
            int q=robustN>0?robustN:n;double ax=robustN>0?robustSx:sx,az=robustN>0?robustSz:sz,axx=robustN>0?robustSxx:sxx,azz=robustN>0?robustSzz:szz,axz=robustN>0?robustSxz:sxz;if(q<3)return 1;double mx=ax/q,mz=az/q;double a=Math.max(0,axx/q-mx*mx),d=Math.max(0,azz/q-mz*mz),b=axz/q-mx*mz;
            double tr=a+d,disc=Math.sqrt(Math.max(0,(a-d)*(a-d)+4*b*b));double l1=(tr+disc)*0.5,l2=(tr-disc)*0.5;return l1<1e-8?1:Math.max(0,l2)/l1;
        }
    }

    // Reused connected-component workspaces. v0.25.6 removed Integer boxing, but still allocated
    // two mask-sized int[] arrays for every semantic inference (~512 KiB at 256x256). During a long
    // ARCore scan those arrays become steady young-generation churn and can still produce periodic
    // GC stalls. integrate() is synchronized, so one reusable workspace is safe and deterministic.
    private static final int[] COMPONENT_DX={1,-1,0,0};
    private static final int[] COMPONENT_DY={0,0,1,-1};
    private int[] componentIds=new int[0];
    private int[] componentQueue=new int[0];
    // Candidate objects contain three fixed reservoir arrays. Reallocating them for every connected
    // semantic component created tens/hundreds of KiB of young-gen garbage per inference even after
    // the component workspace itself became reusable. Keep a bounded pool across frames.
    private static final int MAX_CANDIDATE_POOL=64;
    private final ArrayDeque<Candidate> candidatePool=new ArrayDeque<>();
    // Per-frame candidate lookup used to be HashMap<Long,Candidate>. Every Depth sample performed a
    // Long autobox on get(), and new semantic components boxed again on put(). At tens of thousands
    // of Depth points per inference this was one of the last high-frequency allocation sources in
    // semantic fusion. Keep a reusable primitive long->Candidate table; integrate() is synchronized.
    private long[] candidateKeys=new long[128];
    private Candidate[] candidateValues=new Candidate[128];
    private byte[] candidateUsed=new byte[128];
    private int[] candidateTouched=new int[128];
    private int candidateTouchedCount;
    private final float[] transformSrc=new float[3],transformCam=new float[3];

    private Candidate obtainCandidate(SemanticLabel label,int rawClass){
        Candidate c=candidatePool.pollFirst();if(c==null)return new Candidate(label,rawClass);c.reset(label,rawClass);return c;
    }
    private void clearCandidateTable(){
        for(int i=0;i<candidateTouchedCount;i++){int slot=candidateTouched[i];Candidate c=candidateValues[slot];if(c!=null&&candidatePool.size()<MAX_CANDIDATE_POOL)candidatePool.addLast(c);candidateValues[slot]=null;candidateUsed[slot]=0;}
        candidateTouchedCount=0;
    }
    private Candidate candidateFor(long key,SemanticLabel label,int rawClass){
        // Load factor is bounded by growing before insertion. Component count is normally tiny, but
        // pathological noisy masks should not turn linear probing into a frame-time spike.
        if((candidateTouchedCount+1)*10>=candidateKeys.length*7)growCandidateTable();
        int mask=candidateKeys.length-1,slot=mixKey(key)&mask;
        while(candidateUsed[slot]!=0){if(candidateKeys[slot]==key)return candidateValues[slot];slot=(slot+1)&mask;}
        Candidate c=obtainCandidate(label,rawClass);candidateUsed[slot]=1;candidateKeys[slot]=key;candidateValues[slot]=c;
        if(candidateTouchedCount>=candidateTouched.length)candidateTouched=java.util.Arrays.copyOf(candidateTouched,candidateTouched.length<<1);
        candidateTouched[candidateTouchedCount++]=slot;return c;
    }
    private void growCandidateTable(){
        long[] oldK=candidateKeys;Candidate[] oldV=candidateValues;byte[] oldU=candidateUsed;
        int cap=oldK.length<<1;candidateKeys=new long[cap];candidateValues=new Candidate[cap];candidateUsed=new byte[cap];candidateTouched=new int[cap];candidateTouchedCount=0;int mask=cap-1;
        for(int i=0;i<oldK.length;i++)if(oldU[i]!=0){long k=oldK[i];int slot=mixKey(k)&mask;while(candidateUsed[slot]!=0)slot=(slot+1)&mask;candidateUsed[slot]=1;candidateKeys[slot]=k;candidateValues[slot]=oldV[i];candidateTouched[candidateTouchedCount++]=slot;}
    }
    private static int mixKey(long k){long z=k;z^=z>>>33;z*=0xff51afd7ed558ccdL;z^=z>>>33;z*=0xc4ceb9fe1a85ec53L;z^=z>>>33;return (int)z;}

    private int[] connectedComponents(SemanticFrame f){
        int n=f.width*f.height;if(componentIds.length<n)componentIds=new int[n];if(componentQueue.length<n)componentQueue=new int[n];
        int[] ids=componentIds,q=componentQueue;java.util.Arrays.fill(ids,0,n,-1);int next=0;
        // Primitive flood-fill queue: no boxing and no per-inference mask-sized queue allocation.
        for(int y=0;y<f.height;y++)for(int x=0;x<f.width;x++){int i=y*f.width+x;if(ids[i]>=0)continue;int raw=f.labels[i]&0xff;SemanticLabel lab=SemanticLabel.fromModelIndex(raw,f.flavor);
            if(lab==SemanticLabel.UNKNOWN||lab==SemanticLabel.PERSON){ids[i]=-2;continue;}ids[i]=next;int qh=0,qt=0;q[qt++]=i;
            while(qh<qt){int v=q[qh++],vx=v%f.width,vy=v/f.width;for(int k=0;k<4;k++){int nx=vx+COMPONENT_DX[k],ny=vy+COMPONENT_DY[k];if(nx<0||ny<0||nx>=f.width||ny>=f.height)continue;int ni=ny*f.width+nx;if(ids[ni]!=-1||(f.labels[ni]&0xff)!=raw)continue;ids[ni]=next;q[qt++]=ni;}}next++;}
        return ids;
    }

    private static final class CenterVote {
        final SemanticLabel label; final float share; final long ts;
        CenterVote(SemanticLabel label,float share,long ts){this.label=label;this.share=share;this.ts=ts;}
    }

    private static final class PendingExpansion {
        double minX,maxX,minY,maxY,minZ,maxZ;int votes;long lastSeenNs;
        PendingExpansion(Candidate c,long ts){set(c);votes=1;lastSeenNs=ts;}
        void set(Candidate c){minX=c.minX;maxX=c.maxX;minY=c.minY;maxY=c.maxY;minZ=c.minZ;maxZ=c.maxZ;}
        boolean similar(Candidate c){
            double acx=(minX+maxX)*.5,acy=(minY+maxY)*.5,acz=(minZ+maxZ)*.5;
            double bcx=(c.minX+c.maxX)*.5,bcy=(c.minY+c.maxY)*.5,bcz=(c.minZ+c.maxZ)*.5;
            double d=Math.sqrt((acx-bcx)*(acx-bcx)+(acy-bcy)*(acy-bcy)+(acz-bcz)*(acz-bcz));
            double ad=Math.max(Math.max(maxX-minX,maxY-minY),maxZ-minZ);
            double bd=Math.max(Math.max(c.maxX-c.minX,c.maxY-c.minY),c.maxZ-c.minZ);
            return d<=0.24&&Math.abs(ad-bd)<=0.35;
        }
    }

    private final List<SemanticEntity> entities=new ArrayList<>();
    private final ArrayDeque<Record> records=new ArrayDeque<>();
    // Keep semantic history useful for diagnostics without pinning hundreds of full label masks.
    // The previous count-only cap (240) could retain ~60 MiB at 512x512 before object overhead.
    private static final int MAX_RECORDS=96;
    private static final long MAX_RECORD_PIXELS=3_000_000L;
    private long recordPixels;
    private final Map<String,EnumMap<SemanticEntity.Type,Integer>> typeVotes=new HashMap<>();
    private final Map<String,PendingExpansion> pendingExpansions=new HashMap<>();
    private final ArrayDeque<CenterVote> centerVotes=new ArrayDeque<>();
    private SemanticFrame latest;
    private SemanticLabel stableCenterLabel=SemanticLabel.UNKNOWN;
    private float stableCenterShare;
    private long stableCenterTimestampNs;
    private long lastIntegratedTimestampNs;

    public synchronized void reset(){entities.clear();records.clear();recordPixels=0;clearCandidateTable();candidatePool.clear();typeVotes.clear();pendingExpansions.clear();centerVotes.clear();latest=null;stableCenterLabel=SemanticLabel.UNKNOWN;stableCenterShare=0;stableCenterTimestampNs=0;lastIntegratedTimestampNs=0;}

    /** Clear only transient "what am I looking at now" votes after tracking loss.
     * Persistent room/object entities remain intact and will be re-associated on recovery. */
    public synchronized void invalidateLiveFocus(){
        centerVotes.clear();stableCenterLabel=SemanticLabel.UNKNOWN;stableCenterShare=0;stableCenterTimestampNs=0;latest=null;
    }

    public synchronized void integrate(SemanticFrame frame,ScanVisualKeyframeRecorder.Meta meta,ArCoreDepthCollector.DepthEvidenceFrame ev){
        if(frame==null||meta==null)return;
        if(frame.timestampNs>0&&lastIntegratedTimestampNs>0&&frame.timestampNs<=lastIntegratedTimestampNs)return;
        lastIntegratedTimestampNs=Math.max(lastIntegratedTimestampNs,frame.timestampNs);
        latest=frame;updateCenterConsensus(frame);
        records.add(new Record(frame,copyMeta(meta)));recordPixels+=framePixels(frame);
        while(records.size()>MAX_RECORDS||recordPixels>MAX_RECORD_PIXELS){Record old=records.pollFirst();if(old==null)break;recordPixels=Math.max(0,recordPixels-framePixels(old.frame));}
        if(ev==null||ev.localXyz==null||ev.pointCount<=0)return;
        if(ev.timestampNs>0&&meta.timestampNs>0&&Math.abs(ev.timestampNs-meta.timestampNs)>350_000_000L)return;

        Pose inv=meta.pose().inverse();float[] src=transformSrc,cam=transformCam;int[] comps=connectedComponents(frame);
        clearCandidateTable();
        int n=Math.min(ev.pointCount,ev.localXyz.length/3);
        for(int i=0;i<n;i++){
            int o=i*3;float x=ev.localXyz[o],y=ev.localXyz[o+1],z=ev.localXyz[o+2];if(!finite(x)||!finite(y)||!finite(z))continue;
            src[0]=x;src[1]=y;src[2]=z;inv.transformPoint(src,0,cam,0);float depth=-cam[2];if(depth<0.20f||depth>6.5f)continue;
            float u=meta.cx+meta.fx*cam[0]/depth,v=meta.cy-meta.fy*cam[1]/depth;if(u<0||v<0||u>=meta.width||v>=meta.height)continue;
            int mx=frame.maskX(u),my=frame.maskY(v),raw=frame.labels[my*frame.width+mx]&0xff,comp=comps[my*frame.width+mx];if(comp<0)continue;
            // Erode one semantic-mask pixel at class boundaries. Depth pixels on silhouettes often
            // belong to the wall behind an object and were the main source of giant false 3D boxes.
            if(mx>0&&my>0&&mx+1<frame.width&&my+1<frame.height){int ii=my*frame.width+mx;if((frame.labels[ii-1]&255)!=raw||(frame.labels[ii+1]&255)!=raw||(frame.labels[ii-frame.width]&255)!=raw||(frame.labels[ii+frame.width]&255)!=raw)continue;}
            SemanticLabel lab=SemanticLabel.fromModelIndex(raw,frame.flavor);if(lab==SemanticLabel.UNKNOWN||lab==SemanticLabel.PERSON)continue;
            long key=(((long)raw)<<32)|(comp&0xffffffffL);Candidate c=candidateFor(key,lab,raw);c.add(x,y,z);
        }
        for(int ci=0;ci<candidateTouchedCount;ci++){Candidate c=candidateValues[candidateTouched[ci]];
            c.finalizeRobustBounds();
            SemanticLabel lab=c.label;int min=lab.isRoomEnvelope()?18:(lab.isOpening()?4:6);if(c.n<min)continue;
            // A semantic WALL region can wrap around a room corner in 2D. Do not turn an L-shaped cloud
            // into one giant wall entity; structural DepthSurfaceTracker remains authoritative for geometry.
            if(lab==SemanticLabel.WALL&&c.n>=30&&c.horizontalPlanarityRatio()>0.20)continue;
            mergeCandidate(lab,c,frame.timestampNs);
        }
        clearCandidateTable();
        mergeStableDuplicates(frame.timestampNs);
        pruneWeakEntities(frame.timestampNs);
    }

    private void mergeCandidate(SemanticLabel label,Candidate c,long ts){
        SemanticEntity.Type type=toType(label);double cx=c.cx(),cy=c.cy(),cz=c.cz();
        SemanticEntity best=null;double bestD=Double.POSITIVE_INFINITY;
        for(SemanticEntity x:entities){
            if(!compatibleType(x.type,type))continue;
            // Instance-separation guard: two disconnected semantic components observed in the SAME
            // RGB frame are direct evidence that they are distinct physical instances. Previously
            // two nearby chairs/nightstands could be folded into one persistent entity simply because
            // their centroids were inside the association gate. Keep them separate unless their metric
            // Depth boxes genuinely overlap/touch (fragmented mask of one object).
            if(x.lastSeenNs==ts&&ts>0){double hg=horizontalCandidateGap(x,c),hi=horizontalCandidateIoU(x,c);if(hg>0.06&&hi<0.04)continue;}
            double dx=x.centerX()-cx,dy=x.centerY()-cy,dz=x.centerZ()-cz;double d=Math.sqrt(dx*dx+dy*dy+dz*dz);
            double gate=type==SemanticEntity.Type.WALL?0.90:(label.isOpening()?0.65:(isLargeFurniture(type)?0.72:0.48));
            // Partial views of a large object can move the observed centroid by much more than the
            // old fixed gate (e.g. scanning the left then right half of a sofa/bed).  Use geometric
            // continuity as a second association cue, but only when the 3D boxes touch/overlap and
            // their vertical spans agree. This keeps one physical object stable across viewpoints
            // without merging two neighbouring chairs/cabinets merely because they are close.
            double gap=candidateGap(x,c);double vo=verticalOverlapRatio(x,c);
            boolean continuous=(isLargeFurniture(type)||label.isOpening())&&gap<=0.16&&vo>=0.35;
            double score=continuous?Math.min(d,gap*1.8+Math.abs(x.centerY()-cy)*0.25):d;
            if((d<gate||continuous)&&score<bestD){best=x;bestD=score;}
        }
        if(best==null){
            best=new SemanticEntity();best.type=type;best.label=label.ua;best.minX=c.minX;best.maxX=c.maxX;best.minY=c.minY;best.maxY=c.maxY;best.minZ=c.minZ;best.maxZ=c.maxZ;best.fusedCenterX=cx;best.fusedCenterY=cy;best.fusedCenterZ=cz;best.fusedCenterValid=true;double rel=c.orientationReliability();best.rotationDeg=rel>=0.16?c.yawDeg():0;if(!isEnvelope(type)){best.orientedWidthM=c.extentAtYaw(best.rotationDeg,true);best.orientedDepthM=c.extentAtYaw(best.rotationDeg,false);}entities.add(best);
            vote(best,type,2);
        }else{
            // A single bad semantic/depth frame must never permanently inflate an entity.
            // Early entities may grow quickly; mature entities need repeated confirmation before
            // accepting a large new extent. This prevents slow "semantic ballooning" over a long scan.
            boolean mature=best.observations>=9&&best.confidence>=0.68;
            double extension=Math.max(Math.max(best.minX-c.minX,c.maxX-best.maxX),
                    Math.max(Math.max(best.minY-c.minY,c.maxY-best.maxY),Math.max(best.minZ-c.minZ,c.maxZ-best.maxZ)));
            boolean allowLargeExpansion=true;
            if(mature&&extension>0.12){
                PendingExpansion pe=pendingExpansions.get(best.id);
                if(pe==null||ts-pe.lastSeenNs>2_600_000_000L||!pe.similar(c)){pe=new PendingExpansion(c,ts);pendingExpansions.put(best.id,pe);}
                else{pe.votes++;pe.lastSeenNs=ts;pe.set(c);}
                allowLargeExpansion=pe.votes>=3;
                if(allowLargeExpansion)pendingExpansions.remove(best.id);
            }else if(extension<=0.12)pendingExpansions.remove(best.id);

            double grow=best.observations<3?0.42:(best.observations<8?0.26:(allowLargeExpansion?0.16:0.035));
            double nMinX=Math.max(c.minX,best.minX-grow),nMaxX=Math.min(c.maxX,best.maxX+grow);
            double nMinY=Math.max(c.minY,best.minY-grow),nMaxY=Math.min(c.maxY,best.maxY+grow);
            double nMinZ=Math.max(c.minZ,best.minZ-grow),nMaxZ=Math.min(c.maxZ,best.maxZ+grow);
            best.minX=Math.min(best.minX,nMinX);best.maxX=Math.max(best.maxX,nMaxX);
            best.minY=Math.min(best.minY,nMinY);best.maxY=Math.max(best.maxY,nMaxY);
            best.minZ=Math.min(best.minZ,nMinZ);best.maxZ=Math.max(best.maxZ,nMaxZ);
            if(!isEnvelope(type)&&!isOpeningType(type)){
                // PCA orientation is undefined for near-square/noisy footprints. Updating yaw from those
                // frames made furniture slowly spin and could swap width/depth by ~90 degrees. Only a
                // sufficiently anisotropic point cloud may steer persistent orientation; dimensions are
                // always measured in the persistent frame so partial views cannot silently swap axes.
                double rel=c.orientationReliability();
                if(rel>=0.16){double y=c.yawDeg();double delta=((y-best.rotationDeg+90)%180+180)%180-90;double yawAlpha=Math.min(.28,.10+.20*rel);best.rotationDeg+=delta*yawAlpha;}
                double ow=c.extentAtYaw(best.rotationDeg,true),od=c.extentAtYaw(best.rotationDeg,false);
                if(best.orientedWidthM<=.01){best.orientedWidthM=ow;best.orientedDepthM=od;}
                else{
                    // A partial view is very common during a room walk (e.g. only half of a sofa is
                    // visible behind a table). Symmetric EMA used to make the persistent object slowly
                    // SHRINK toward whichever fragment happened to be visible most often. Preserve the
                    // learned physical envelope: accept newly revealed extent relatively quickly, but
                    // require sustained evidence before reducing a mature dimension. This still lets an
                    // early noisy estimate recover instead of turning dimensions into a permanent max().
                    best.orientedWidthM=fuseExtent(best.orientedWidthM,ow,best.observations);
                    best.orientedDepthM=fuseExtent(best.orientedDepthM,od,best.observations);
                }
            }
            // Track position from robust per-view centroids instead of the historical union AABB.
            // Union bounds intentionally preserve scanned coverage, but their center can drift forever after
            // one partial/outlier view. A bounded EMA gives association/promotion a recoverable physical center.
            if(!best.fusedCenterValid){best.fusedCenterX=cx;best.fusedCenterY=cy;best.fusedCenterZ=cz;best.fusedCenterValid=true;}
            else{
                double alpha=best.observations<4?0.34:(best.observations<10?0.22:0.12);
                double jump=Math.sqrt((best.fusedCenterX-cx)*(best.fusedCenterX-cx)+(best.fusedCenterY-cy)*(best.fusedCenterY-cy)+(best.fusedCenterZ-cz)*(best.fusedCenterZ-cz));
                if(jump>0.55)alpha*=0.35;
                // A robust centroid is still biased when only one end of a large sofa/bed/table is
                // visible. v0.25.0 protected dimensions from partial-view shrinkage, but position could
                // still "walk" toward the visible half. Estimate how much of the learned footprint this
                // observation covers in the persistent local frame and strongly down-weight centroid
                // motion for partial views. Full views and early tracks remain responsive.
                if(best.observations>=5&&!isEnvelope(type)&&best.orientedWidthM>.08&&best.orientedDepthM>.08){
                    double viewW=c.extentAtYaw(best.rotationDeg,true),viewD=c.extentAtYaw(best.rotationDeg,false);
                    double coverW=Math.min(1.0,viewW/Math.max(.08,best.orientedWidthM));
                    double coverD=Math.min(1.0,viewD/Math.max(.08,best.orientedDepthM));
                    double footprintCoverage=Math.sqrt(Math.max(.02,coverW*coverD));
                    if(footprintCoverage<.72)alpha*=Math.max(.16,footprintCoverage*footprintCoverage);
                }
                best.fusedCenterX=best.fusedCenterX*(1-alpha)+cx*alpha;best.fusedCenterY=best.fusedCenterY*(1-alpha)+cy*alpha;best.fusedCenterZ=best.fusedCenterZ*(1-alpha)+cz*alpha;
            }
            vote(best,type,1);maybeSwitchType(best);
        }
        best.observations++;best.supportPoints=Math.min(1_000_000,best.supportPoints+c.n);best.lastSeenNs=ts;best.confidence=Math.min(0.985,0.28+best.observations*0.075+Math.min(0.36,best.supportPoints/1100.0));
    }


    private static double fuseExtent(double oldV,double observed,int observations){
        if(!Double.isFinite(observed)||observed<.01)return oldV;
        if(!Double.isFinite(oldV)||oldV<.01)return observed;
        double ratio=observed/Math.max(.01,oldV);
        // Cap a single-frame jump; the persistent AABB/pending-expansion path independently protects
        // against giant silhouette/depth leaks. This guard is for oriented dimensions specifically.
        double bounded=Math.max(oldV*.55,Math.min(oldV*1.45,observed));
        boolean growth=bounded>oldV;
        double alpha=growth?(observations<5?.34:.24):(observations<5?.16:.055);
        // Tiny changes are measurement jitter, not a reason to continuously breathe the model.
        if(ratio>.94&&ratio<1.06)alpha*=.45;
        return Math.max(.01,oldV*(1-alpha)+bounded*alpha);
    }

    private void vote(SemanticEntity e,SemanticEntity.Type t,int weight){
        EnumMap<SemanticEntity.Type,Integer> m=typeVotes.get(e.id);if(m==null){m=new EnumMap<>(SemanticEntity.Type.class);typeVotes.put(e.id,m);}
        Integer old=m.get(t);m.put(t,(old==null?0:old)+Math.max(1,weight));
    }

    private void maybeSwitchType(SemanticEntity e){
        EnumMap<SemanticEntity.Type,Integer> m=typeVotes.get(e.id);if(m==null||m.isEmpty())return;
        SemanticEntity.Type current=e.type,best=current;int cur=m.get(current)==null?0:m.get(current),bestVotes=cur;
        for(Map.Entry<SemanticEntity.Type,Integer> x:m.entrySet())if(x.getValue()>bestVotes){best=x.getKey();bestVotes=x.getValue();}
        // Hysteresis: one or two flickering frames cannot rename a persistent object.
        // Once an entity has been observed repeatedly it becomes much harder to rename; this
        // avoids a wardrobe oscillating between cabinet/door as the camera angle changes.
        boolean mature=e.observations>=8&&e.confidence>=0.72;
        int margin=mature?6:3;int absolute=mature?9:4;
        if(best!=current&&compatibleType(current,best)&&bestVotes>=cur+margin&&bestVotes>=absolute){e.type=best;e.label=uaFor(best);}
    }

    private void mergeStableDuplicates(long nowNs){
        boolean changed=true;int guard=0;
        while(changed&&guard++<32){
            changed=false;
            outer:for(int i=0;i<entities.size();i++){
                SemanticEntity a=entities.get(i);if(a==null||isEnvelope(a.type))continue;
                for(int j=i+1;j<entities.size();j++){
                    SemanticEntity b=entities.get(j);if(b==null||isEnvelope(b.type))continue;
                    if(!compatibleType(a.type,b.type)&&a.type!=b.type)continue;
                    double gate=(isOpeningType(a.type)||isOpeningType(b.type))?0.34:(isLargeFurniture(a.type)||isLargeFurniture(b.type)?0.48:0.30);
                    double dx=a.centerX()-b.centerX(),dy=a.centerY()-b.centerY(),dz=a.centerZ()-b.centerZ();
                    double center=Math.sqrt(dx*dx+dy*dy+dz*dz);
                    if(center>gate)continue;
                    double gap=aabbGap(a,b);if(gap>0.22)continue;
                    // Do not undo the same-frame instance separation during duplicate cleanup. If two
                    // entities were independently supported by the current frame and have no meaningful
                    // horizontal overlap, they are neighbours, not stale duplicate tracks.
                    double hGap=horizontalEntityGap(a,b),hIou=horizontalEntityIoU(a,b);
                    if(a.lastSeenNs==nowNs&&b.lastSeenNs==nowNs&&nowNs>0&&hGap>0.06&&hIou<0.04)continue;
                    // Mature tracks represent identity accumulated across many viewpoints. The old cleanup
                    // could still merge two real neighbouring chairs/cabinets when they happened to be seen
                    // in alternating frames: the same-frame guard above no longer applied, while their
                    // centres/gap remained inside the generic duplicate gate. Be deliberately conservative
                    // once BOTH tracks are established. Real duplicate tracks normally share metric volume;
                    // separate physical instances usually do not. Openings are exempt because two partial
                    // observations of one wide door/window can legitimately have little footprint IoU.
                    boolean bothMature=a.observations>=4&&b.observations>=4&&a.confidence>=0.58&&b.confidence>=0.58;
                    if(bothMature&&!isOpeningType(a.type)&&!isOpeningType(b.type)){
                        double vOverlap=verticalEntityOverlapRatio(a,b);
                        if(hIou<0.08&&(hGap>0.025||vOverlap<0.55))continue;
                        // Even overlapping AABBs can belong to adjacent objects when noisy depth envelopes
                        // touch. A large centre separation relative to their learned oriented footprint is
                        // another strong identity-separation cue.
                        double as=Math.max(.08,Math.min(validExtent(a.orientedWidthM,a.maxX-a.minX),validExtent(a.orientedDepthM,a.maxZ-a.minZ)));
                        double bs=Math.max(.08,Math.min(validExtent(b.orientedWidthM,b.maxX-b.minX),validExtent(b.orientedDepthM,b.maxZ-b.minZ)));
                        double horizontalCenter=Math.hypot(a.centerX()-b.centerX(),a.centerZ()-b.centerZ());
                        if(hIou<0.18&&horizontalCenter>0.70*(as+bs))continue;
                    }
                    SemanticEntity keep=a.observations>=b.observations?a:b,drop=keep==a?b:a;
                    mergeEntityInto(keep,drop);entities.remove(drop);changed=true;break outer;
                }
            }
        }
    }

    private void mergeEntityInto(SemanticEntity keep,SemanticEntity drop){
        int ko=Math.max(1,keep.observations),doo=Math.max(1,drop.observations);double total=ko+doo;
        if(drop.fusedCenterValid){if(!keep.fusedCenterValid){keep.fusedCenterX=drop.fusedCenterX;keep.fusedCenterY=drop.fusedCenterY;keep.fusedCenterZ=drop.fusedCenterZ;keep.fusedCenterValid=true;}else{keep.fusedCenterX=(keep.fusedCenterX*ko+drop.fusedCenterX*doo)/total;keep.fusedCenterY=(keep.fusedCenterY*ko+drop.fusedCenterY*doo)/total;keep.fusedCenterZ=(keep.fusedCenterZ*ko+drop.fusedCenterZ*doo)/total;}}
        // Duplicate tracks can have different local PCA axes even when they describe the same object.
        // Averaging width with width and depth with depth before aligning those axes can collapse a long
        // sofa/desk into an almost square footprint (especially after an old ~90 degree PCA flip). Project
        // the drop rectangle into the keep coordinate frame first, then fuse dimensions. This keeps the
        // physical footprint coherent across track consolidation and avoids width/depth swaps.
        double rd=axialDeltaDeg(drop.rotationDeg,keep.rotationDeg);
        // Choose the FINAL merged axis first. v0.25.4 projected the duplicate into the old keep axis and
        // then rotated keep.rotationDeg afterwards. That left the fused width/depth expressed in a stale
        // coordinate frame: repeated duplicate consolidation could slowly inflate a long object and make
        // its dimensions inconsistent with its yaw. Keep the established track dominant, then re-project
        // BOTH rectangles into that final axis before fusing them.
        double orientationWeight=Math.min(0.35,doo/total);
        double finalYaw=normalizeAxialDeg(keep.rotationDeg+rd*orientationWeight);
        double[] kd=projectOrientedExtents(keep.orientedWidthM,keep.orientedDepthM,keep.rotationDeg,finalYaw);
        double[] dd=projectOrientedExtents(drop.orientedWidthM,drop.orientedDepthM,drop.rotationDeg,finalYaw);
        if(dd!=null){
            if(kd==null){keep.orientedWidthM=dd[0];keep.orientedDepthM=dd[1];}
            else{
                keep.orientedWidthM=(kd[0]*ko+dd[0]*doo)/total;
                keep.orientedDepthM=(kd[1]*ko+dd[1]*doo)/total;
            }
        }else if(kd!=null){keep.orientedWidthM=kd[0];keep.orientedDepthM=kd[1];}
        keep.rotationDeg=finalYaw;
        keep.minX=Math.min(keep.minX,drop.minX);keep.maxX=Math.max(keep.maxX,drop.maxX);
        keep.minY=Math.min(keep.minY,drop.minY);keep.maxY=Math.max(keep.maxY,drop.maxY);
        keep.minZ=Math.min(keep.minZ,drop.minZ);keep.maxZ=Math.max(keep.maxZ,drop.maxZ);
        keep.observations=Math.min(100000,keep.observations+drop.observations);
        keep.supportPoints=Math.min(1_000_000,keep.supportPoints+drop.supportPoints);
        keep.confidence=Math.max(keep.confidence,drop.confidence);keep.lastSeenNs=Math.max(keep.lastSeenNs,drop.lastSeenNs);
        EnumMap<SemanticEntity.Type,Integer> km=typeVotes.get(keep.id),dm=typeVotes.get(drop.id);
        if(km==null){km=new EnumMap<>(SemanticEntity.Type.class);typeVotes.put(keep.id,km);}
        if(dm!=null)for(Map.Entry<SemanticEntity.Type,Integer> x:dm.entrySet()){Integer old=km.get(x.getKey());km.put(x.getKey(),(old==null?0:old)+x.getValue());}
        typeVotes.remove(drop.id);pendingExpansions.remove(drop.id);maybeSwitchType(keep);
    }


    private static double axialDeltaDeg(double from,double to){
        return ((from-to+90)%180+180)%180-90;
    }

    private static double normalizeAxialDeg(double yaw){
        double v=((yaw+90)%180+180)%180-90;
        return Math.abs(v)<1e-9?0:v;
    }

    private static double[] projectOrientedExtents(double width,double depth,double sourceYaw,double targetYaw){
        if(!Double.isFinite(width)||!Double.isFinite(depth)||width<=.01||depth<=.01)return null;
        double r=Math.toRadians(axialDeltaDeg(sourceYaw,targetYaw));
        double ac=Math.abs(Math.cos(r)),as=Math.abs(Math.sin(r));
        return new double[]{width*ac+depth*as,width*as+depth*ac};
    }

    private static double verticalEntityOverlapRatio(SemanticEntity a,SemanticEntity b){
        double inter=Math.max(0,Math.min(a.maxY,b.maxY)-Math.max(a.minY,b.minY));
        double ah=Math.max(.01,a.maxY-a.minY),bh=Math.max(.01,b.maxY-b.minY);
        return inter/Math.min(ah,bh);
    }

    private static double validExtent(double oriented,double fallback){
        return Double.isFinite(oriented)&&oriented>.01?oriented:Math.max(.01,fallback);
    }

    private void pruneWeakEntities(long nowNs){
        Iterator<SemanticEntity> it=entities.iterator();
        while(it.hasNext()){SemanticEntity e=it.next();if(e==null){it.remove();continue;}
            long age=Math.max(0,nowNs-e.lastSeenNs);
            boolean weak=e.observations<=1&&e.confidence<0.46;
            boolean weak2=e.observations<=2&&e.confidence<0.52;
            if(!isOpeningType(e.type)&&!isEnvelope(e.type)&&((weak&&age>8_000_000_000L)||(weak2&&age>18_000_000_000L))){typeVotes.remove(e.id);pendingExpansions.remove(e.id);it.remove();}
        }
    }

    private static double horizontalCandidateGap(SemanticEntity a,Candidate b){
        double gx=Math.max(0,Math.max(a.minX,b.minX)-Math.min(a.maxX,b.maxX));
        double gz=Math.max(0,Math.max(a.minZ,b.minZ)-Math.min(a.maxZ,b.maxZ));return Math.hypot(gx,gz);
    }
    private static double horizontalCandidateIoU(SemanticEntity a,Candidate b){
        double ix=Math.max(0,Math.min(a.maxX,b.maxX)-Math.max(a.minX,b.minX));
        double iz=Math.max(0,Math.min(a.maxZ,b.maxZ)-Math.max(a.minZ,b.minZ));double inter=ix*iz;
        double aa=Math.max(0,a.maxX-a.minX)*Math.max(0,a.maxZ-a.minZ),ba=Math.max(0,b.maxX-b.minX)*Math.max(0,b.maxZ-b.minZ);
        double u=aa+ba-inter;return u>1e-9?inter/u:0;
    }
    private static double horizontalEntityGap(SemanticEntity a,SemanticEntity b){
        double gx=Math.max(0,Math.max(a.minX,b.minX)-Math.min(a.maxX,b.maxX));
        double gz=Math.max(0,Math.max(a.minZ,b.minZ)-Math.min(a.maxZ,b.maxZ));return Math.hypot(gx,gz);
    }
    private static double horizontalEntityIoU(SemanticEntity a,SemanticEntity b){
        double ix=Math.max(0,Math.min(a.maxX,b.maxX)-Math.max(a.minX,b.minX));
        double iz=Math.max(0,Math.min(a.maxZ,b.maxZ)-Math.max(a.minZ,b.minZ));double inter=ix*iz;
        double aa=Math.max(0,a.maxX-a.minX)*Math.max(0,a.maxZ-a.minZ),ba=Math.max(0,b.maxX-b.minX)*Math.max(0,b.maxZ-b.minZ);
        double u=aa+ba-inter;return u>1e-9?inter/u:0;
    }

    private static double candidateGap(SemanticEntity a,Candidate b){
        double gx=Math.max(0,Math.max(a.minX,b.minX)-Math.min(a.maxX,b.maxX));
        double gy=Math.max(0,Math.max(a.minY,b.minY)-Math.min(a.maxY,b.maxY));
        double gz=Math.max(0,Math.max(a.minZ,b.minZ)-Math.min(a.maxZ,b.maxZ));
        return Math.sqrt(gx*gx+gy*gy+gz*gz);
    }
    private static double verticalOverlapRatio(SemanticEntity a,Candidate b){
        double overlap=Math.max(0,Math.min(a.maxY,b.maxY)-Math.max(a.minY,b.minY));
        double ah=Math.max(.01,a.maxY-a.minY),bh=Math.max(.01,b.maxY-b.minY);
        return overlap/Math.min(ah,bh);
    }

    private static double aabbGap(SemanticEntity a,SemanticEntity b){
        double gx=Math.max(0,Math.max(a.minX,b.minX)-Math.min(a.maxX,b.maxX));
        double gy=Math.max(0,Math.max(a.minY,b.minY)-Math.min(a.maxY,b.maxY));
        double gz=Math.max(0,Math.max(a.minZ,b.minZ)-Math.min(a.maxZ,b.maxZ));
        return Math.sqrt(gx*gx+gy*gy+gz*gz);
    }
    private static boolean isEnvelope(SemanticEntity.Type t){return t==SemanticEntity.Type.WALL||t==SemanticEntity.Type.FLOOR||t==SemanticEntity.Type.CEILING;}
    private static boolean isOpeningType(SemanticEntity.Type t){return t==SemanticEntity.Type.DOOR||t==SemanticEntity.Type.WINDOW||t==SemanticEntity.Type.PASSAGE;}
    private static boolean committed(SemanticEntity e){
        if(e==null)return false;if(isEnvelope(e.type))return e.observations>=2&&e.supportPoints>=24;
        if(isOpeningType(e.type))return e.observations>=2&&e.supportPoints>=6;
        return e.observations>=2&&e.supportPoints>=10&&e.confidence>=0.44;
    }

    private static boolean compatibleType(SemanticEntity.Type a,SemanticEntity.Type b){
        if(a==b)return true;
        if(isStorage(a)&&isStorage(b))return true;if(isSeat(a)&&isSeat(b))return true;if(isTable(a)&&isTable(b))return true;
        // Door/window are intentionally NOT compatible: a window must not silently become a door.
        return false;
    }
    private static boolean isStorage(SemanticEntity.Type t){return t==SemanticEntity.Type.CABINET||t==SemanticEntity.Type.DRAWERS||t==SemanticEntity.Type.WARDROBE||t==SemanticEntity.Type.BOOKCASE||t==SemanticEntity.Type.SHELF;}
    private static boolean isSeat(SemanticEntity.Type t){return t==SemanticEntity.Type.CHAIR||t==SemanticEntity.Type.ARMCHAIR||t==SemanticEntity.Type.STOOL||t==SemanticEntity.Type.BENCH;}
    private static boolean isTable(SemanticEntity.Type t){return t==SemanticEntity.Type.TABLE||t==SemanticEntity.Type.DESK;}
    private static boolean isLargeFurniture(SemanticEntity.Type t){return isStorage(t)||t==SemanticEntity.Type.BED||t==SemanticEntity.Type.SOFA||isTable(t);}

    private static String uaFor(SemanticEntity.Type t){
        for(SemanticLabel x:SemanticLabel.values())if(toType(x)==t)return x.ua;return "об'єкт";
    }

    private void updateCenterConsensus(SemanticFrame f){
        if(f==null)return;long ts=f.timestampNs;centerVotes.addLast(new CenterVote(f.centerLabel,f.centerShare,ts));
        while(centerVotes.size()>9)centerVotes.removeFirst();
        while(!centerVotes.isEmpty()&&ts-centerVotes.peekFirst().ts>3_000_000_000L)centerVotes.removeFirst();
        EnumMap<SemanticLabel,Float> score=new EnumMap<>(SemanticLabel.class);EnumMap<SemanticLabel,Integer> count=new EnumMap<>(SemanticLabel.class);
        for(CenterVote v:centerVotes){
            if(v.label==SemanticLabel.UNKNOWN||v.label==SemanticLabel.PERSON)continue;
            float age=Math.max(0f,(ts-v.ts)/1_000_000_000f),w=Math.max(0.18f,1f-age/3.2f)*Math.max(0.08f,v.share);
            Float q=score.get(v.label);score.put(v.label,(q==null?0f:q)+w);Integer n=count.get(v.label);count.put(v.label,(n==null?0:n)+1);
        }
        SemanticLabel best=SemanticLabel.UNKNOWN;float bestScore=0,total=0;int bestCount=0;
        for(Map.Entry<SemanticLabel,Float> x:score.entrySet()){total+=x.getValue();int n=count.get(x.getKey())==null?0:count.get(x.getKey());if(x.getValue()>bestScore){best=x.getKey();bestScore=x.getValue();bestCount=n;}}
        float share=total<=0?0:bestScore/total;
        if(stableCenterLabel==SemanticLabel.UNKNOWN){if(bestCount>=2&&share>=0.48f){stableCenterLabel=best;stableCenterShare=share;stableCenterTimestampNs=ts;}}
        else if(best==stableCenterLabel){stableCenterShare=0.65f*stableCenterShare+0.35f*share;stableCenterTimestampNs=ts;}
        else if(best!=SemanticLabel.UNKNOWN){
            // Switching the visible label needs stronger, repeated evidence than keeping it.
            if(bestCount>=4&&share>=0.66f){stableCenterLabel=best;stableCenterShare=share;stableCenterTimestampNs=ts;}
        }
        if(ts-stableCenterTimestampNs>2_200_000_000L&&best==SemanticLabel.UNKNOWN){stableCenterLabel=SemanticLabel.UNKNOWN;stableCenterShare=0;}
    }

    public synchronized Snapshot snapshot(){
        Snapshot s=new Snapshot();if(latest!=null){s.centerLabel=stableCenterLabel;s.centerShare=stableCenterShare;s.lastSemanticTimestampNs=stableCenterTimestampNs;}
        SemanticEntity target=null;SemanticEntity.Type targetType=toType(s.centerLabel);
        for(SemanticEntity e:entities){if(!committed(e))continue;s.entityCount++;if(e.type==SemanticEntity.Type.WALL)s.wallCount++;else if(e.type==SemanticEntity.Type.DOOR)s.doorCount++;else if(e.type==SemanticEntity.Type.WINDOW)s.windowCount++;else s.objectCount++;if(e.type==targetType&&(target==null||e.lastSeenNs>target.lastSeenNs))target=e;}
        if(target!=null){double sx=target.widthM(),sz=target.depthM();s.targetWidthM=Math.max(sx,sz);s.targetDepthM=Math.min(sx,sz);s.targetHeightM=target.heightM();s.targetConfidence=target.confidence;}return s;
    }
    public synchronized List<SemanticEntity> snapshotEntities(){
        List<SemanticEntity> out=new ArrayList<>();for(SemanticEntity x:entities)if(committed(x))out.add(copy(x));return out;
    }
    public synchronized List<Record> snapshotRecords(){return new ArrayList<>(records);}

    private static SemanticEntity copy(SemanticEntity x){SemanticEntity e=new SemanticEntity();e.id=x.id;e.type=x.type;e.label=x.label;e.minX=x.minX;e.maxX=x.maxX;e.minY=x.minY;e.maxY=x.maxY;e.minZ=x.minZ;e.maxZ=x.maxZ;e.confidence=x.confidence;e.observations=x.observations;e.supportPoints=x.supportPoints;e.lastSeenNs=x.lastSeenNs;e.visualOnly=x.visualOnly;e.parentWallId=x.parentWallId;e.rotationDeg=x.rotationDeg;e.orientedWidthM=x.orientedWidthM;e.orientedDepthM=x.orientedDepthM;e.fusedCenterX=x.fusedCenterX;e.fusedCenterY=x.fusedCenterY;e.fusedCenterZ=x.fusedCenterZ;e.fusedCenterValid=x.fusedCenterValid;return e;}
    private static ScanVisualKeyframeRecorder.Meta copyMeta(ScanVisualKeyframeRecorder.Meta m){ScanVisualKeyframeRecorder.Meta x=new ScanVisualKeyframeRecorder.Meta();x.file=m.file;x.depthFile=m.depthFile;x.depthW=m.depthW;x.depthH=m.depthH;x.width=m.width;x.height=m.height;x.fx=m.fx;x.fy=m.fy;x.cx=m.cx;x.cy=m.cy;x.tx=m.tx;x.ty=m.ty;x.tz=m.tz;x.qx=m.qx;x.qy=m.qy;x.qz=m.qz;x.qw=m.qw;x.timestampNs=m.timestampNs;return x;}
    private static long framePixels(SemanticFrame f){return f==null?0L:Math.max(0L,(long)f.width*(long)f.height);}
    private static boolean finite(float v){return !Float.isNaN(v)&&!Float.isInfinite(v);}
    public static SemanticEntity.Type toType(SemanticLabel x){
        switch(x){
            case WALL:return SemanticEntity.Type.WALL;case FLOOR:return SemanticEntity.Type.FLOOR;case CEILING:return SemanticEntity.Type.CEILING;
            case DOOR:return SemanticEntity.Type.DOOR;case WINDOW:return SemanticEntity.Type.WINDOW;case MIRROR:return SemanticEntity.Type.MIRROR;
            case CABINET:return SemanticEntity.Type.CABINET;case DRAWERS:return SemanticEntity.Type.DRAWERS;case WARDROBE:return SemanticEntity.Type.WARDROBE;case TABLE:return SemanticEntity.Type.TABLE;case DESK:return SemanticEntity.Type.DESK;
            case CHAIR:return SemanticEntity.Type.CHAIR;case ARMCHAIR:return SemanticEntity.Type.ARMCHAIR;case SOFA:return SemanticEntity.Type.SOFA;case SHELF:return SemanticEntity.Type.SHELF;case BED:return SemanticEntity.Type.BED;
            case CURTAIN:return SemanticEntity.Type.CURTAIN;case COLUMN:return SemanticEntity.Type.COLUMN;case SINK:return SemanticEntity.Type.SINK;case REFRIGERATOR:return SemanticEntity.Type.REFRIGERATOR;
            case TOILET:return SemanticEntity.Type.TOILET;case SHOWER:return SemanticEntity.Type.SHOWER;case BATHTUB:return SemanticEntity.Type.BATHTUB;case STAIRS:return SemanticEntity.Type.STAIRS;case LAMP:return SemanticEntity.Type.LAMP;case COMPUTER:return SemanticEntity.Type.COMPUTER;case TELEVISION:return SemanticEntity.Type.TELEVISION;
            case WASHER:return SemanticEntity.Type.WASHER;case OVEN:return SemanticEntity.Type.OVEN;case MICROWAVE:return SemanticEntity.Type.MICROWAVE;case STOVE:return SemanticEntity.Type.STOVE;case DISHWASHER:return SemanticEntity.Type.DISHWASHER;case RADIATOR:return SemanticEntity.Type.RADIATOR;case BOOKCASE:return SemanticEntity.Type.BOOKCASE;case BENCH:return SemanticEntity.Type.BENCH;case STOOL:return SemanticEntity.Type.STOOL;case BICYCLE:return SemanticEntity.Type.BICYCLE;case PERSON:return SemanticEntity.Type.PERSON;
            case OBJECT:return SemanticEntity.Type.OBJECT;default:return SemanticEntity.Type.UNKNOWN;
        }
    }
}
