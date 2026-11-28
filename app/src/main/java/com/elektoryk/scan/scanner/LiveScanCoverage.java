package com.elektoryk.scan.scanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Live coverage model backed by real 3D Depth endpoints.
 *
 * v0.16.9: the old live map flattened every hit to X/Z. That was useful for navigation,
 * but it could never show the user whether a wall, floor or upper wall/ceiling was actually
 * observed. We now keep two sparse structures:
 *  - a light X/Z map only for gap/target guidance;
 *  - a 3D voxel map for coverage/quality and the live isometric preview.
 *
 * Empty-space ray traversal is deliberately NOT stored as scanned geometry.
 */
public class LiveScanCoverage {
    public static final double CELL_M = 0.45;
    public static final double VOXEL_M = 0.28;

    private static final int MAX_2D_CELLS = 2600;
    private static final int MAX_3D_VOXELS = 28000;
    private static final int MAX_SNAPSHOT_VOXELS = 2200;

    private static final int N_X_NEG=1, N_X_POS=2, N_Y_NEG=4, N_Y_POS=8, N_Z_NEG=16, N_Z_POS=32;

    public static final int BAND_FLOOR = 0;
    public static final int BAND_WALL = 1;
    public static final int BAND_UPPER = 2;

    public static final class CellView {
        public int ix;
        public int iz;
        /** 0 = hole candidate, 1 = weak surface, 2 = usable surface, 3 = strong surface. */
        public int state;
        public float score;

        CellView(int ix, int iz, int state, float score) {
            this.ix = ix; this.iz = iz; this.state = state; this.score = score;
        }
    }

    /** One displayed 3D surface voxel. */
    public static final class VoxelView {
        public int ix, iy, iz;
        public int state;
        public int band;
        public float score;

        VoxelView(int ix, int iy, int iz, int state, int band, float score) {
            this.ix=ix; this.iy=iy; this.iz=iz;
            this.state=state; this.band=band; this.score=score;
        }
    }

    public static final class Snapshot {
        /** Kept for 2D gap/target logic and compatibility. */
        public final List<CellView> cells = new ArrayList<>();
        /** Real 3D endpoint voxels for the live preview. */
        public final List<VoxelView> voxels = new ArrayList<>();

        public int coveragePercent;
        public int verticalCoveragePercent;
        public int lowBandPercent;
        public int midBandPercent;
        public int highBandPercent;
        public int strongCells;
        public int weakCells;
        public int missingCells;
        public int strongVoxels;
        public int weakVoxels;
        public int observedVoxelCount;
        public int floorVoxelCount;
        public int wallVoxelCount;
        public int upperVoxelCount;
        public int architecturalVoxelCount;
        public int planeSupportedVoxels;
        public int multiViewPercent;
        public int liveVoxelCapacityPercent;

        public float cameraX;
        public float cameraY;
        public float cameraZ;
        public float yawRad;
        public float speedMps;
        public float cameraTravelM;
        public boolean hasTarget;
        public float targetX;
        public float targetZ;
        public float targetDistanceM;
        public String targetDirection = "";
        public String targetHint = "";

        public int minIx, maxIx, minIz, maxIz;
        public int minVx, maxVx, minVy, maxVy, minVz, maxVz;
    }

    private static final class Cell {
        int ix, iz;
        int surfaceHits;
        int surfaceFrames;
        int lastSurfaceFrame = -1;
        int cameraVisits;
        int lowHits;
        int midHits;
        int highHits;
        float confidenceSum;

        int verticalBands() {
            int n=0;
            if(lowHits>=2)n++;
            if(midHits>=2)n++;
            if(highHits>=2)n++;
            return n;
        }

        float score() {
            if(surfaceHits<=0)return 0f;
            float repeat=Math.min(1f,surfaceFrames/4f);
            float conf=clamp01(confidenceSum/Math.max(1f,surfaceHits));
            float vertical=verticalBands()/3f;
            return clamp01(repeat*0.58f+conf*0.34f+vertical*0.08f);
        }
    }

    private static final class Voxel {
        int ix, iy, iz;
        int hits;
        int frames;
        int lastFrame=-1;
        int floorHits;
        int wallHits;
        int upperHits;
        int planeFloorHits;
        int planeWallHits;
        int planeUpperHits;
        int neighborMask;
        float confidenceSum;
        boolean hasFirstObserver;
        float firstObserverX,firstObserverY,firstObserverZ;
        float maxObserverBaselineM;
        int viewMask;

        float score(){
            if(hits<=0)return 0f;
            float repeat=Math.min(1f,frames/4f);
            float conf=clamp01(confidenceSum/Math.max(1f,hits));
            float baseline=Math.min(1f,maxObserverBaselineM/0.28f);
            float views=Math.min(1f,Integer.bitCount(viewMask)/3f);
            float plane=Math.min(1f,(planeFloorHits+planeWallHits+planeUpperHits)/3f);
            // Repeated observations from the same spot are deliberately capped. High structural
            // confidence requires parallax or independent ARCore Plane evidence.
            return clamp01(repeat*0.26f+conf*0.25f+baseline*0.28f+views*0.11f+plane*0.10f);
        }

        boolean isArchitectural(){
            int planeHits=planeFloorHits+planeWallHits+planeUpperHits;
            if(planeHits>=1 && frames>=2)return true;
            boolean vertical=(neighborMask&(N_Y_NEG|N_Y_POS))!=0;
            int horizontal=Integer.bitCount(neighborMask&(N_X_NEG|N_X_POS|N_Z_NEG|N_Z_POS));
            return frames>=2 && maxObserverBaselineM>=0.07f && (vertical||horizontal>=2);
        }

        int dominantBand(){
            if(planeWallHits>=planeFloorHits && planeWallHits>=planeUpperHits && planeWallHits>0)return BAND_WALL;
            if(planeFloorHits>=planeWallHits && planeFloorHits>=planeUpperHits && planeFloorHits>0)return BAND_FLOOR;
            if(planeUpperHits>=planeWallHits && planeUpperHits>=planeFloorHits && planeUpperHits>0)return BAND_UPPER;
            // Vertical continuity is strong evidence for a wall even when the point itself is
            // below/above camera height. This prevents the lower half of a wall being called floor.
            boolean vertical=(neighborMask&(N_Y_NEG|N_Y_POS))!=0;
            int horizontal=Integer.bitCount(neighborMask&(N_X_NEG|N_X_POS|N_Z_NEG|N_Z_POS));
            if(vertical && horizontal>=1)return BAND_WALL;
            if(vertical && wallHits>0)return BAND_WALL;
            if(!vertical && horizontal>=1){
                if(floorHits>wallHits && floorHits>=upperHits)return BAND_FLOOR;
                if(upperHits>wallHits && upperHits>=floorHits)return BAND_UPPER;
            }
            if(floorHits>=wallHits && floorHits>=upperHits)return BAND_FLOOR;
            if(upperHits>=wallHits && upperHits>=floorHits)return BAND_UPPER;
            return BAND_WALL;
        }
    }

    // The navigation grid is touched for every camera/depth update. Avoid Long boxing and
    // HashMap node churn; capacity is bounded by MAX_2D_CELLS.
    private final CellLookup cells=new CellLookup(MAX_2D_CELLS);
    private final VoxelLookup voxelLookup=new VoxelLookup(65536);
    private final List<Voxel> voxelList=new ArrayList<>(MAX_3D_VOXELS);
    // Interior guidance is bounded to 1200 cells. Keep hole coordinates primitive so snapshot()
    // does not allocate one int[] object per missing cell while the live UI polls coverage.
    private final int[] holeX=new int[1200];
    private final int[] holeZ=new int[1200];
    private int surfaceSerial;

    private float cameraX,cameraY,cameraZ,yawRad;
    private float speedMps;
    private float cameraTravelM;
    private float lastCameraX,lastCameraZ;
    private long lastCameraTs;
    private boolean hasCamera;
    private float architecturalFloorY=Float.NaN;
    private float architecturalCeilingY=Float.NaN;
    private boolean floorLocked;
    private boolean ceilingLocked;

    public void reset(){
        cells.clear();
        voxelLookup.clear();
        voxelList.clear();
        surfaceSerial=0;
        cameraX=cameraY=cameraZ=yawRad=speedMps=cameraTravelM=0;
        lastCameraX=lastCameraZ=0;
        lastCameraTs=0;
        hasCamera=false;
        architecturalFloorY=Float.NaN;architecturalCeilingY=Float.NaN;
        floorLocked=false;ceilingLocked=false;
    }

    public void setArchitecturalLevels(float floorY,boolean floorIsLocked,float ceilingY,boolean ceilingIsLocked){
        architecturalFloorY=floorY;architecturalCeilingY=ceilingY;
        floorLocked=floorIsLocked&&Float.isFinite(floorY);
        ceilingLocked=ceilingIsLocked&&Float.isFinite(ceilingY);
    }

    public void updateCamera(float x,float y,float z,float yaw,long timestampNs){
        cameraX=x;cameraY=y;cameraZ=z;yawRad=yaw;
        if(hasCamera && timestampNs>lastCameraTs){
            double dt=(timestampNs-lastCameraTs)/1_000_000_000.0;
            float step=(float)Math.hypot(x-lastCameraX,z-lastCameraZ);
            if(dt>0.03 && dt<2.5){
                float instant=(float)(step/dt);
                if(Float.isFinite(instant))speedMps=speedMps*0.72f+instant*0.28f;
                if(step<=1.20f && Float.isFinite(step))cameraTravelM+=step;
            }
        }
        lastCameraX=x;lastCameraZ=z;lastCameraTs=timestampNs;hasCamera=true;
        Cell c=cellFor(x,z,true);
        if(c!=null && c.cameraVisits<100000)c.cameraVisits++;
    }

    /** Observe one accepted Depth endpoint. Empty ray space is never counted as geometry. */
    public void observeRay(float hitX,float hitY,float hitZ,float confidence,float distanceM,int frameId){
        observeSurface(cameraX,cameraY,cameraZ,hitX,hitY,hitZ,confidence,distanceM,frameId,-1,0f);
    }

    /**
     * Precision observation with camera baseline and optional ARCore Plane evidence.
     * preferredBand < 0 means no trusted plane classification.
     */
    public void observeSurface(float observerX,float observerY,float observerZ,
                               float hitX,float hitY,float hitZ,float confidence,float distanceM,int frameId,
                               int preferredBand,float planeSupport){
        if(!hasCamera || !Float.isFinite(distanceM) || distanceM<0.30f || distanceM>6.2f)return;
        if(!Float.isFinite(hitX)||!Float.isFinite(hitY)||!Float.isFinite(hitZ))return;

        int band=preferredBand>=0?preferredBand:bandForPoint(hitY);
        float clamped=clamp01(confidence);

        if((surfaceSerial++&1)==0){
            Cell end=cellFor(hitX,hitZ,true);
            if(end!=null){
                if(end.surfaceHits<100000)end.surfaceHits++;
                if(end.lastSurfaceFrame!=frameId){
                    end.lastSurfaceFrame=frameId;
                    if(end.surfaceFrames<100000)end.surfaceFrames++;
                }
                end.confidenceSum+=clamped;
                if(band==BAND_FLOOR)end.lowHits++;
                else if(band==BAND_UPPER)end.highHits++;
                else end.midHits++;
            }
        }

        Voxel v=voxelFor(hitX,hitY,hitZ,true);
        if(v!=null){
            if(v.hits<100000)v.hits++;
            if(v.lastFrame!=frameId){
                v.lastFrame=frameId;
                if(v.frames<100000)v.frames++;
                if(!v.hasFirstObserver){
                    v.hasFirstObserver=true;v.firstObserverX=observerX;v.firstObserverY=observerY;v.firstObserverZ=observerZ;
                }else{
                    float dx=observerX-v.firstObserverX,dy=observerY-v.firstObserverY,dz=observerZ-v.firstObserverZ;
                    float baseline=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                    if(Float.isFinite(baseline))v.maxObserverBaselineM=Math.max(v.maxObserverBaselineM,baseline);
                }
                float vx=hitX-observerX,vz=hitZ-observerZ;
                float yaw=(float)Math.atan2(vx,-vz);
                int bin=(int)Math.floor((yaw+Math.PI)/(2*Math.PI)*16.0);
                bin=Math.max(0,Math.min(15,bin));v.viewMask|=(1<<bin);
            }
            v.confidenceSum+=clamped;
            if(band==BAND_FLOOR)v.floorHits++;
            else if(band==BAND_UPPER)v.upperHits++;
            else v.wallHits++;
            if(planeSupport>0.12f){
                if(preferredBand==BAND_FLOOR)v.planeFloorHits++;
                else if(preferredBand==BAND_UPPER)v.planeUpperHits++;
                else if(preferredBand==BAND_WALL)v.planeWallHits++;
            }
        }
    }

    public Snapshot snapshot(){
        Snapshot out=new Snapshot();
        out.cameraX=cameraX;out.cameraY=cameraY;out.cameraZ=cameraZ;out.yawRad=yawRad;
        out.speedMps=speedMps;out.cameraTravelM=cameraTravelM;

        build2DGuidance(out);
        build3DCoverage(out);
        return out;
    }

    private void build2DGuidance(Snapshot out){
        int minIx=(int)Math.floor(cameraX/CELL_M),maxIx=minIx;
        int minIz=(int)Math.floor(cameraZ/CELL_M),maxIz=minIz;
        int observedSurfaceCells=0;
        for(int ci=0;ci<cells.capacity();ci++){
            Cell c=cells.valueAt(ci);if(c==null)continue;
            if(c.surfaceHits<=0)continue;
            observedSurfaceCells++;
            minIx=Math.min(minIx,c.ix);maxIx=Math.max(maxIx,c.ix);
            minIz=Math.min(minIz,c.iz);maxIz=Math.max(maxIz,c.iz);
        }
        out.minIx=minIx;out.maxIx=maxIx;out.minIz=minIz;out.maxIz=maxIz;
        if(observedSurfaceCells==0)return;

        int good=0,weak=0;
        for(int ci=0;ci<cells.capacity();ci++){
            Cell c=cells.valueAt(ci);if(c==null)continue;
            if(c.surfaceHits<=0)continue;
            float score=c.score();
            int state;
            if(score>=0.70f){state=3;good++;}
            else if(score>=0.44f){state=2;good++;}
            else{state=1;weak++;}
            out.cells.add(new CellView(c.ix,c.iz,state,score));
        }

        int holeCount=0;
        int marginMinX=minIx+1,marginMaxX=maxIx-1;
        int marginMinZ=minIz+1,marginMaxZ=maxIz-1;
        int width=Math.max(0,marginMaxX-marginMinX+1);
        int height=Math.max(0,marginMaxZ-marginMinZ+1);
        if((long)width*(long)height<=1200){
            for(int iz=marginMinZ;iz<=marginMaxZ;iz++){
                for(int ix=marginMinX;ix<=marginMaxX;ix++){
                    Cell existing=cells.get(key2(ix,iz));
                    if(existing!=null&&existing.surfaceHits>0)continue;
                    int n=0;
                    if(surfaceActive(ix+1,iz))n++;
                    if(surfaceActive(ix-1,iz))n++;
                    if(surfaceActive(ix,iz+1))n++;
                    if(surfaceActive(ix,iz-1))n++;
                    if(surfaceActive(ix+1,iz+1))n++;
                    if(surfaceActive(ix-1,iz-1))n++;
                    if(surfaceActive(ix-1,iz+1))n++;
                    if(surfaceActive(ix+1,iz-1))n++;
                    if(n>=5 && holeCount<holeX.length){holeX[holeCount]=ix;holeZ[holeCount]=iz;holeCount++;}
                }
            }
        }
        int holeLimit=Math.min(50,holeCount);
        for(int i=0;i<holeLimit;i++) out.cells.add(new CellView(holeX[i],holeZ[i],0,0f));
        out.missingCells=holeCount;
        out.strongCells=good;out.weakCells=weak;
        chooseTarget(out,holeCount);
    }

    private void build3DCoverage(Snapshot out){
        int cvx=(int)Math.floor(cameraX/VOXEL_M);
        int cvy=(int)Math.floor(cameraY/VOXEL_M);
        int cvz=(int)Math.floor(cameraZ/VOXEL_M);
        int minX=cvx,maxX=cvx,minY=cvy,maxY=cvy,minZ=cvz,maxZ=cvz;

        int observed=0,strong=0,weak=0,architectural=0,planeSupported=0,multiView=0;
        int floorN=0,wallN=0,upperN=0;
        int floorGood=0,wallGood=0,upperGood=0;
        float floorScore=0,wallScore=0,upperScore=0;
        int displayStride=Math.max(1,(int)Math.ceil(voxelList.size()/(double)MAX_SNAPSHOT_VOXELS));
        int displayIndex=0;

        for(Voxel v:voxelList){
            if(v.hits<=0)continue;
            observed++;
            boolean structural=v.isArchitectural();
            if(structural)architectural++;
            if(v.planeFloorHits+v.planeWallHits+v.planeUpperHits>0)planeSupported++;
            if(v.maxObserverBaselineM>=0.12f && v.frames>=2)multiView++;
            minX=Math.min(minX,v.ix);maxX=Math.max(maxX,v.ix);
            minY=Math.min(minY,v.iy);maxY=Math.max(maxY,v.iy);
            minZ=Math.min(minZ,v.iz);maxZ=Math.max(maxZ,v.iz);
            float score=v.score();
            int state;
            if(!structural){state=1;weak++;}
            else if(score>=0.72f){state=3;strong++;}
            else if(score>=0.44f){state=2;strong++;}
            else{state=1;weak++;}
            int band=v.dominantBand();
            if(structural){
                if(band==BAND_FLOOR){floorN++;floorScore+=score;if(state>=2)floorGood++;}
                else if(band==BAND_UPPER){upperN++;upperScore+=score;if(state>=2)upperGood++;}
                else{wallN++;wallScore+=score;if(state>=2)wallGood++;}
            }

            if((displayIndex++%displayStride)==0 && out.voxels.size()<MAX_SNAPSHOT_VOXELS){
                out.voxels.add(new VoxelView(v.ix,v.iy,v.iz,state,band,score));
            }
        }

        out.minVx=minX;out.maxVx=maxX;out.minVy=minY;out.maxVy=maxY;out.minVz=minZ;out.maxVz=maxZ;
        out.observedVoxelCount=observed;
        out.architecturalVoxelCount=architectural;
        out.planeSupportedVoxels=planeSupported;
        out.multiViewPercent=architectural==0?0:Math.min(100,Math.round(multiView*100f/architectural));
        out.strongVoxels=strong;out.weakVoxels=weak;
        out.floorVoxelCount=floorN;out.wallVoxelCount=wallN;out.upperVoxelCount=upperN;
        out.liveVoxelCapacityPercent=Math.min(100,Math.round(voxelList.size()*100f/MAX_3D_VOXELS));

        out.lowBandPercent=bandEvidencePercent(floorN,floorGood,floorScore,42);
        out.midBandPercent=bandEvidencePercent(wallN,wallGood,wallScore,95);
        out.highBandPercent=bandEvidencePercent(upperN,upperGood,upperScore,34);
        out.verticalCoveragePercent=Math.round((out.lowBandPercent+out.midBandPercent+out.highBandPercent)/3f);

        // Full 3D coverage intentionally rewards a balanced scan. A wall-only pass can no longer
        // report a high percentage while the floor or upper wall/ceiling are absent.
        float surfaceBalance=out.lowBandPercent*0.26f+out.midBandPercent*0.54f+out.highBandPercent*0.20f;
        float travelGate=Math.min(1f,cameraTravelM/1.6f);
        float motionGate=0.20f+0.80f*(float)Math.sqrt(Math.max(0f,travelGate));
        out.coveragePercent=Math.max(0,Math.min(100,Math.round(surfaceBalance*motionGate)));
    }

    private int bandEvidencePercent(int n,int good,float scoreSum,int targetVoxels){
        if(n<=0)return 0;
        float mean=clamp01(scoreSum/n);
        float goodRatio=good/(float)n;
        float quality=mean*0.55f+goodRatio*0.45f;
        float countGate=(float)Math.sqrt(Math.min(1f,n/(float)Math.max(1,targetVoxels)));
        return Math.max(0,Math.min(100,Math.round(quality*countGate*100f)));
    }

    private void chooseTarget(Snapshot out,int holeCount){
        double best=Double.POSITIVE_INFINITY;
        int bestIx=0,bestIz=0;
        boolean found=false;
        for(int i=0;i<holeCount;i++){
            int ix=holeX[i],iz=holeZ[i];
            float x=center2(ix),z=center2(iz);
            double d=Math.hypot(x-cameraX,z-cameraZ);
            if(d>=0.55&&d<best&&d<=8.0){best=d;bestIx=ix;bestIz=iz;found=true;}
        }
        if(!found){
            for(int ci=0;ci<cells.capacity();ci++){
                Cell c=cells.valueAt(ci);if(c==null)continue;
                if(c.surfaceHits<=0||c.score()>=0.44f)continue;
                float x=center2(c.ix),z=center2(c.iz);
                double d=Math.hypot(x-cameraX,z-cameraZ);
                if(d>=0.55&&d<best&&d<=8.0){best=d;bestIx=c.ix;bestIz=c.iz;found=true;}
            }
        }
        if(!found){
            if(out.lowBandPercent+18<out.midBandPercent)
                out.targetHint="Підлога відстає — опусти камеру і пройди нижні кути та край підлоги вздовж стін.";
            else if(out.highBandPercent+18<out.midBandPercent)
                out.targetHint="Верх відстає — покажи верх стін, кути та стелю з кількох позицій.";
            else out.targetHint=out.coveragePercent<35
                    ?"Ще мало 3D-геометрії — рухайся плавно боком і покажи підлогу, стіни та верх кімнати."
                    :"Поточна зона покрита добре. Перевір кути, прорізи й переходь у наступну кімнату.";
            return;
        }
        out.hasTarget=true;
        out.targetX=center2(bestIx);out.targetZ=center2(bestIz);out.targetDistanceM=(float)best;
        float dx=out.targetX-cameraX,dz=out.targetZ-cameraZ;
        float targetYaw=(float)Math.atan2(dx,-dz);
        float delta=normalizeAngle(targetYaw-yawRad);
        out.targetDirection=directionName(delta);
        out.targetHint=String.format(java.util.Locale.US,"%s ~%.1f м — слабка зона геометрії.",out.targetDirection,out.targetDistanceM);
    }

    private boolean surfaceActive(int ix,int iz){
        Cell c=cells.get(key2(ix,iz));
        return c!=null&&c.surfaceFrames>=2;
    }

    private Cell cellFor(float x,float z,boolean create){
        int ix=(int)Math.floor(x/CELL_M),iz=(int)Math.floor(z/CELL_M);
        long k=key2(ix,iz);
        Cell c=cells.get(k);
        if(c==null&&create&&cells.size()<MAX_2D_CELLS){
            c=new Cell();c.ix=ix;c.iz=iz;cells.put(k,c);
        }
        return c;
    }

    private Voxel voxelFor(float x,float y,float z,boolean create){
        int ix=(int)Math.floor(x/VOXEL_M),iy=(int)Math.floor(y/VOXEL_M),iz=(int)Math.floor(z/VOXEL_M);
        long key=pack3(ix,iy,iz);
        Voxel v=voxelLookup.get(key);
        if(v==null&&create&&voxelList.size()<MAX_3D_VOXELS){
            v=new Voxel();v.ix=ix;v.iy=iy;v.iz=iz;
            voxelLookup.put(key,v);
            voxelList.add(v);
            linkNeighbors(v);
        }
        return v;
    }

    private void linkNeighbors(Voxel v){
        link(v,v.ix-1,v.iy,v.iz,N_X_NEG,N_X_POS);
        link(v,v.ix+1,v.iy,v.iz,N_X_POS,N_X_NEG);
        link(v,v.ix,v.iy-1,v.iz,N_Y_NEG,N_Y_POS);
        link(v,v.ix,v.iy+1,v.iz,N_Y_POS,N_Y_NEG);
        link(v,v.ix,v.iy,v.iz-1,N_Z_NEG,N_Z_POS);
        link(v,v.ix,v.iy,v.iz+1,N_Z_POS,N_Z_NEG);
    }

    private void link(Voxel v,int x,int y,int z,int ownBit,int otherBit){
        Voxel other=voxelLookup.get(pack3(x,y,z));
        if(other==null)return;
        v.neighborMask|=ownBit;
        other.neighborMask|=otherBit;
    }

    private int bandForPoint(float y){
        if(floorLocked && Math.abs(y-architecturalFloorY)<=0.20f)return BAND_FLOOR;
        if(ceilingLocked && Math.abs(y-architecturalCeilingY)<=0.22f)return BAND_UPPER;
        // Once a structural floor/ceiling is locked, points between those planes are not
        // automatically called floor/ceiling merely because the phone is higher/lower.
        if(floorLocked || ceilingLocked)return BAND_WALL;
        float dy=y-cameraY;
        if(dy<-0.55f)return BAND_FLOOR;
        if(dy>0.55f)return BAND_UPPER;
        return BAND_WALL;
    }

    private static long key2(int ix,int iz){return (((long)ix)<<32)^(iz&0xffffffffL);}
    private static float center2(int i){return (float)((i+0.5)*CELL_M);}
    private static float normalizeAngle(float a){
        while(a>Math.PI)a-=(float)(Math.PI*2.0);
        while(a<-Math.PI)a+=(float)(Math.PI*2.0);
        return a;
    }
    private static String directionName(float delta){
        float deg=(float)Math.toDegrees(delta),a=Math.abs(deg);
        if(a<24)return "Прямо попереду";
        if(a<68)return deg>0?"Попереду праворуч":"Попереду ліворуч";
        if(a<118)return deg>0?"Праворуч":"Ліворуч";
        if(a<158)return deg>0?"Позаду праворуч":"Позаду ліворуч";
        return "Позаду";
    }
    private static float clamp01(float v){return Math.max(0f,Math.min(1f,v));}

    private static long pack3(int x,int y,int z){
        long mask=0x1FFFFFL;
        return ((long)x&mask)<<42 | (((long)y&mask)<<21) | ((long)z&mask);
    }

    /** Fixed-capacity primitive long -> Cell table for live X/Z guidance. */
    private static final class CellLookup {
        private final long[] keys;
        private final Cell[] values;
        private final int mask;
        private int size;
        CellLookup(int maxEntries){
            int target=(int)Math.ceil(Math.max(1,maxEntries)/0.62);int cap=1;while(cap<target)cap<<=1;
            keys=new long[cap];values=new Cell[cap];mask=cap-1;
        }
        void clear(){Arrays.fill(values,null);size=0;}
        int size(){return size;}
        int capacity(){return values.length;}
        Cell valueAt(int i){return values[i];}
        Cell get(long key){int i=slot(key,mask);for(int n=0;n<values.length;n++){Cell v=values[i];if(v==null)return null;if(keys[i]==key)return v;i=(i+1)&mask;}return null;}
        void put(long key,Cell value){int i=slot(key,mask);for(int n=0;n<values.length;n++){Cell v=values[i];if(v==null){keys[i]=key;values[i]=value;size++;return;}if(keys[i]==key){values[i]=value;return;}i=(i+1)&mask;}throw new IllegalStateException("Cell lookup full");}
        private static int slot(long key,int mask){long z=key;z^=z>>>33;z*=0xff51afd7ed558ccdl;z^=z>>>33;z*=0xc4ceb9fe1a85ec53l;z^=z>>>33;return ((int)z)&mask;}
    }

    /** Fixed-capacity primitive long -> Voxel table. No per-sample key objects / GC churn. */
    private static final class VoxelLookup {
        private final long[] keys;
        private final Voxel[] values;
        private final int mask;

        VoxelLookup(int requested){
            int cap=1;while(cap<requested)cap<<=1;
            keys=new long[cap];values=new Voxel[cap];mask=cap-1;
        }
        void clear(){Arrays.fill(values,null);}
        Voxel get(long key){
            int i=slot(key,mask);
            for(int n=0;n<values.length;n++){
                Voxel v=values[i];
                if(v==null)return null;
                if(keys[i]==key)return v;
                i=(i+1)&mask;
            }
            return null;
        }
        void put(long key,Voxel value){
            int i=slot(key,mask);
            for(int n=0;n<values.length;n++){
                Voxel v=values[i];
                if(v==null||keys[i]==key){keys[i]=key;values[i]=value;return;}
                i=(i+1)&mask;
            }
            throw new IllegalStateException("Voxel lookup full");
        }
        private static int slot(long key,int mask){
            long z=key;
            z^=(z>>>33);z*=0xff51afd7ed558ccdl;
            z^=(z>>>33);z*=0xc4ceb9fe1a85ec53l;
            z^=(z>>>33);
            return ((int)z)&mask;
        }
    }
}
