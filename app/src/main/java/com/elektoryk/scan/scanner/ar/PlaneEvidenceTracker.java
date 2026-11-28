package com.elektoryk.scan.scanner.ar;

import com.elektoryk.scan.scanner.LiveScanCoverage;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.nio.FloatBuffer;

/**
 * Lightweight architectural plane evidence built from ARCore Plane trackables.
 *
 * Depth remains the metric source of geometry. Plane tracking is only used to:
 *  - classify confirmed depth points as floor / wall / ceiling;
 *  - provide an independent structural cross-check;
 *  - expose useful perpendicular distances to stable planes;
 *  - prevent furniture / isolated depth noise from inflating structural coverage.
 */
public final class PlaneEvidenceTracker {
    private static final int MAX_PLANES = 28;
    private static final long UPDATE_INTERVAL_NS = 420_000_000L; // ~2.4 Hz
    private static final float MIN_ARCH_AREA_M2 = 0.32f;

    public static final class Match {
        public int band = -1;
        public float planeDistanceM = Float.NaN;
        public float support;
        public boolean matched;
    }

    public static final class Snapshot {
        public int trackedPlanes;
        public int verticalPlanes;
        public boolean floorLocked;
        public boolean ceilingLocked;
        public float floorY = Float.NaN;
        public float ceilingY = Float.NaN;
        public float floorDistanceM = Float.NaN;
        public float ceilingDistanceM = Float.NaN;
        public float nearestWallDistanceM = Float.NaN;
        public float wallYawHintRad = Float.NaN;
        public float wallYawConfidence;
    }

    private static final class Model {
        int band = -1;
        float cx, cy, cz;
        float nx, ny, nz;
        float ux, uy, uz;
        float vx, vy, vz;
        float extentX, extentZ;
        float area;
        int planeKind; // 1 vertical, 2 upward-facing horizontal, 3 downward-facing horizontal
        float[] polygonXZ;
    }

    private final List<Model> models = new ArrayList<>();
    private long lastUpdateNs;
    private float floorY = Float.NaN;
    private float ceilingY = Float.NaN;
    private int floorLockFrames;
    private int ceilingLockFrames;
    // A mature architectural level must not jump to a table, stair landing or transient ARCore
    // horizontal plane. Large height changes are staged as challengers and must repeat across
    // several tracker updates before replacing the locked floor/ceiling.
    private float pendingFloorY = Float.NaN;
    private float pendingCeilingY = Float.NaN;
    private int pendingFloorFrames;
    private int pendingCeilingFrames;
    private float cameraX, cameraY, cameraZ;
    private static final float[] AXIS_N={0f,1f,0f}, AXIS_U={1f,0f,0f}, AXIS_V={0f,0f,1f};
    private final float[] normalScratch=new float[3],uScratch=new float[3],vScratch=new float[3];

    public synchronized void reset() {
        models.clear();
        lastUpdateNs = 0;
        floorY = Float.NaN;
        ceilingY = Float.NaN;
        floorLockFrames = 0;
        ceilingLockFrames = 0;
        pendingFloorY = pendingCeilingY = Float.NaN;
        pendingFloorFrames = pendingCeilingFrames = 0;
        cameraX = cameraY = cameraZ = 0;
    }

    public synchronized boolean shouldUpdate(long frameTimestampNs) {
        return lastUpdateNs == 0 || frameTimestampNs - lastUpdateNs >= UPDATE_INTERVAL_NS;
    }

    public synchronized void update(Collection<Plane> planes, Pose originPose, Pose cameraWorldPose,
                                    long frameTimestampNs) {
        if (planes == null || originPose == null || cameraWorldPose == null) return;
        if (!shouldUpdate(frameTimestampNs)) return;
        lastUpdateNs = frameTimestampNs;

        Pose originInverse = originPose.inverse();
        Pose cameraLocal = originInverse.compose(cameraWorldPose);
        cameraX = cameraLocal.tx();
        cameraY = cameraLocal.ty();
        cameraZ = cameraLocal.tz();

        List<Model> fresh = new ArrayList<>();
        float maxFloorArea = 0f, maxCeilingArea = 0f;
        for (Plane p : planes) {
            if (p == null || p.getTrackingState() != TrackingState.TRACKING || p.getSubsumedBy() != null) continue;
            float ex = Math.max(0f, p.getExtentX());
            float ez = Math.max(0f, p.getExtentZ());
            FloatBuffer polygonBuffer = p.getPolygon();
            float polygonArea = polygonArea(polygonBuffer);
            float area = polygonArea > 0.02f ? polygonArea : ex * ez;
            if (area < 0.08f) continue;

            Pose local = originInverse.compose(p.getCenterPose());
            local.rotateVector(AXIS_N,0,normalScratch,0);
            local.rotateVector(AXIS_U,0,uScratch,0);
            local.rotateVector(AXIS_V,0,vScratch,0);

            Model m = new Model();
            m.cx = local.tx(); m.cy = local.ty(); m.cz = local.tz();
            m.nx = normalScratch[0]; m.ny = normalScratch[1]; m.nz = normalScratch[2];
            m.ux = uScratch[0]; m.uy = uScratch[1]; m.uz = uScratch[2];
            m.vx = vScratch[0]; m.vy = vScratch[1]; m.vz = vScratch[2];
            m.extentX = ex; m.extentZ = ez; m.area = area;

            if (p.getType() == Plane.Type.VERTICAL) { m.band = LiveScanCoverage.BAND_WALL; m.planeKind=1; }
            else if (p.getType() == Plane.Type.HORIZONTAL_UPWARD_FACING) m.planeKind=2;
            else if (p.getType() == Plane.Type.HORIZONTAL_DOWNWARD_FACING) m.planeKind=3;
            // Plane collections are not ordered by architectural importance. Keeping the first
            // MAX_PLANES made tracking quality depend on ARCore iteration order: many small table,
            // shelf or fragmented planes could crowd out a large wall/floor discovered later.
            // Retain the strongest architectural candidates by observed area instead.
            if (retainArchitectural(fresh,m)) m.polygonXZ = copyPolygon(polygonBuffer);
        }

        // Compute horizontal reference strength only from the retained structural candidates.
        // This also prevents a discarded transient plane from raising the relative area gate.
        maxFloorArea=0f;maxCeilingArea=0f;
        for(Model m:fresh){
            if(m.ny>0.55f && cameraY-m.cy>=0.75f && cameraY-m.cy<=2.15f)
                maxFloorArea=Math.max(maxFloorArea,m.area);
            else if(m.ny<-0.55f && m.cy-cameraY>=0.70f && m.cy-cameraY<=2.30f)
                maxCeilingArea=Math.max(maxCeilingArea,m.area);
        }

        // Pick a structural floor/ceiling, not just any horizontal plane. Once a level has been
        // learned, continuity outranks the naive "lowest floor / highest ceiling" heuristic.
        // Otherwise a visible stair landing, lowered room, mezzanine or ceiling recess can win on
        // every update and eventually pass the challenger hysteresis even while the original room
        // level is still clearly tracked. A different level is allowed to challenge only when no
        // credible incumbent-level plane remains visible in this update.
        Model floorCandidate = null, ceilingCandidate = null;
        Model floorChallenger = null, ceilingChallenger = null;
        float floorIncumbentDelta=Float.POSITIVE_INFINITY,ceilingIncumbentDelta=Float.POSITIVE_INFINITY;
        for (Model m : fresh) {
            if (Math.abs(m.ny) < 0.78f || m.area < MIN_ARCH_AREA_M2) continue;
            if (m.ny > 0.55f && cameraY - m.cy >= 0.75f && cameraY - m.cy <= 2.15f &&
                    m.area >= Math.max(MIN_ARCH_AREA_M2, maxFloorArea * 0.30f)) {
                float d=Float.isFinite(floorY)?Math.abs(m.cy-floorY):Float.POSITIVE_INFINITY;
                if(Float.isFinite(floorY) && d<=0.18f){
                    if(floorCandidate==null || d<floorIncumbentDelta ||
                            (Math.abs(d-floorIncumbentDelta)<0.015f && m.area>floorCandidate.area)){
                        floorCandidate=m;floorIncumbentDelta=d;
                    }
                }else if(floorChallenger==null || m.cy<floorChallenger.cy){
                    floorChallenger=m;
                }
            }
            if (m.ny < -0.55f && m.cy - cameraY >= 0.70f && m.cy - cameraY <= 2.30f &&
                    m.area >= Math.max(MIN_ARCH_AREA_M2, maxCeilingArea * 0.30f)) {
                float d=Float.isFinite(ceilingY)?Math.abs(m.cy-ceilingY):Float.POSITIVE_INFINITY;
                if(Float.isFinite(ceilingY) && d<=0.20f){
                    if(ceilingCandidate==null || d<ceilingIncumbentDelta ||
                            (Math.abs(d-ceilingIncumbentDelta)<0.015f && m.area>ceilingCandidate.area)){
                        ceilingCandidate=m;ceilingIncumbentDelta=d;
                    }
                }else if(ceilingChallenger==null || m.cy>ceilingChallenger.cy){
                    ceilingChallenger=m;
                }
            }
        }
        if(floorCandidate==null)floorCandidate=floorChallenger;
        if(ceilingCandidate==null)ceilingCandidate=ceilingChallenger;

        if (floorCandidate != null) updateFloorLevel(floorCandidate.cy, floorCandidate.area);
        else {
            pendingFloorFrames=0; pendingFloorY=Float.NaN;
            if (floorLockFrames > 0) floorLockFrames--;
        }
        if (ceilingCandidate != null) updateCeilingLevel(ceilingCandidate.cy, ceilingCandidate.area);
        else {
            pendingCeilingFrames=0; pendingCeilingY=Float.NaN;
            if (ceilingLockFrames > 0) ceilingLockFrames--;
        }

        boolean floorLocked = isFloorLocked();
        boolean ceilingLocked = isCeilingLocked();
        for (Model m : fresh) {
            if (m.band == LiveScanCoverage.BAND_WALL) continue;
            if (floorLocked && m.ny > 0.55f && Math.abs(m.cy - floorY) <= 0.20f)
                m.band = LiveScanCoverage.BAND_FLOOR;
            else if (ceilingLocked && m.ny < -0.55f && Math.abs(m.cy - ceilingY) <= 0.22f)
                m.band = LiveScanCoverage.BAND_UPPER;
            else
                m.band = -1; // tables / shelves / other horizontal planes are not floor/ceiling.
        }

        models.clear();
        models.addAll(fresh);
    }

    /** Convenience API for cold callers. Live Depth ingestion should use matchInto(). */
    public synchronized Match match(float x, float y, float z) {
        return matchInto(x,y,z,new Match());
    }

    /**
     * Allocation-free point-to-plane query for the Depth hot path. A scan can evaluate tens of
     * thousands of samples, so allocating one Match per sample creates avoidable GC stalls.
     * The supplied object is fully reset before use and may safely be reused by one collector.
     */
    public synchronized Match matchInto(float x,float y,float z,Match best) {
        if(best==null)best=new Match();
        best.band=-1;best.planeDistanceM=Float.NaN;best.support=0f;best.matched=false;
        float bestScore = 0f;
        for (Model m : models) {
            if (m.band < 0) continue;
            float dx=x-m.cx, dy=y-m.cy, dz=z-m.cz;
            float normal=Math.abs(dx*m.nx+dy*m.ny+dz*m.nz);
            float su=dx*m.ux+dy*m.uy+dz*m.uz;
            float sv=dx*m.vx+dy*m.vy+dz*m.vz;
            float u=Math.abs(su);
            float v=Math.abs(sv);
            float margin=0.10f;
            if (u > m.extentX*0.5f+margin || v > m.extentZ*0.5f+margin) continue;
            // ARCore extent is only a bounding rectangle. The actual polygon is a much tighter
            // structural gate. Points just outside an evolving polygon may still be retained as
            // strong Raw Depth geometry, but they receive too little plane support to rescue a
            // weak depth sample. This prevents wall inflation around doors/corners.
            float polygonSupport=1f;
            if (m.polygonXZ != null && m.polygonXZ.length >= 6) {
                polygonSupport=polygonSupport(su,sv,m.polygonXZ,margin);
                if(polygonSupport<=0f) continue;
            }
            float tol=m.band==LiveScanCoverage.BAND_WALL?0.090f:0.075f;
            if (normal > tol) continue;
            float areaScore=Math.min(1f,m.area/3.0f);
            float score=(1f-normal/tol)*(0.72f+0.28f*areaScore)*polygonSupport;
            if (score>bestScore) {
                bestScore=score;
                best.matched=true;
                best.band=m.band;
                best.planeDistanceM=normal;
                best.support=Math.max(0f,Math.min(1f,score));
            }
        }
        return best;
    }

    public synchronized Snapshot snapshot() {
        Snapshot s = new Snapshot();
        s.trackedPlanes=models.size();
        for (Model m:models) if (m.band==LiveScanCoverage.BAND_WALL) s.verticalPlanes++;
        s.floorLocked=isFloorLocked();
        s.ceilingLocked=isCeilingLocked();
        s.floorY=floorY; s.ceilingY=ceilingY;
        if (s.floorLocked) s.floorDistanceM=Math.abs(cameraY-floorY);
        if (s.ceilingLocked) s.ceilingDistanceM=Math.abs(ceilingY-cameraY);

        float bestWall=Float.POSITIVE_INFINITY;
        double yawCos4=0,yawSin4=0,yawWeight=0,totalWallArea=0;
        int yawPlanes=0;
        for (Model m:models) {
            if (m.band!=LiveScanCoverage.BAND_WALL) continue;
            // Wall normal orientation has 90-degree symmetry for Manhattan architecture.
            // Circular mean on 4*theta makes parallel and perpendicular walls reinforce the
            // same room-axis hypothesis while arbitrary furniture planes cancel out.
            double theta=Math.atan2(m.nz,m.nx);
            double w=Math.max(0.05,Math.min(4.0,m.area));
            yawCos4+=w*Math.cos(4.0*theta);
            yawSin4+=w*Math.sin(4.0*theta);
            yawWeight+=w;totalWallArea+=m.area;yawPlanes++;
            float dx=cameraX-m.cx,dy=cameraY-m.cy,dz=cameraZ-m.cz;
            float normal=Math.abs(dx*m.nx+dy*m.ny+dz*m.nz);
            float su=dx*m.ux+dy*m.uy+dz*m.uz;
            float sv=dx*m.vx+dy*m.vy+dz*m.vz;
            float u=Math.abs(su);
            float v=Math.abs(sv);
            boolean inExtent=u<=m.extentX*0.5f+0.45f && v<=m.extentZ*0.5f+0.45f;
            boolean inPolygon=m.polygonXZ==null || m.polygonXZ.length<6 ||
                    insideOrNearPolygon(su,sv,m.polygonXZ,0.45f);
            if (inExtent && inPolygon) bestWall=Math.min(bestWall,normal);
        }
        if (bestWall<Float.POSITIVE_INFINITY) s.nearestWallDistanceM=bestWall;
        if(yawWeight>1e-6){
            double resultant=Math.sqrt(yawCos4*yawCos4+yawSin4*yawSin4)/yawWeight;
            float areaFactor=(float)Math.min(1.0,totalWallArea/1.8);
            float countFactor=0.60f+0.40f*Math.min(1f,yawPlanes/3f);
            s.wallYawHintRad=(float)normalizeQuarter(Math.atan2(yawSin4,yawCos4)/4.0);
            s.wallYawConfidence=(float)Math.max(0,Math.min(1,resultant*areaFactor*countFactor));
        }
        return s;
    }

    public synchronized boolean isFloorLocked(){ return floorLockFrames>=3 && Float.isFinite(floorY); }
    public synchronized boolean isCeilingLocked(){ return ceilingLockFrames>=3 && Float.isFinite(ceilingY); }
    public synchronized float getFloorY(){ return floorY; }
    public synchronized float getCeilingY(){ return ceilingY; }

    private static float[] copyPolygon(FloatBuffer polygon) {
        if (polygon == null) return null;
        FloatBuffer dup = polygon.duplicate();
        if (dup.remaining() < 6) return null;
        float[] out = new float[dup.remaining()];
        dup.get(out);
        return out;
    }

    private static float polygonArea(FloatBuffer polygon) {
        if(polygon==null)return 0f;
        FloatBuffer poly=polygon.duplicate();int count=poly.remaining();
        if(count<6)return 0f;
        int base=poly.position(),n=count/2;double a=0.0;
        for(int i=0,j=n-1;i<n;j=i++){
            double xi=poly.get(base+2*i),zi=poly.get(base+2*i+1);
            double xj=poly.get(base+2*j),zj=poly.get(base+2*j+1);
            a+=xj*zi-xi*zj;
        }
        return (float)Math.abs(a*0.5);
    }

    private static boolean insideOrNearPolygon(float x, float z, float[] poly, float margin) {
        return polygonSupport(x,z,poly,margin)>0f;
    }

    /**
     * 0..1 confidence that a local X/Z point belongs to the detected plane polygon.
     * Interior points are strongest away from the uncertain edge. Outside-edge tolerance is
     * deliberately capped below 0.30 so it cannot rescue weak Raw Depth in projectSamples().
     */
    private static float polygonSupport(float x,float z,float[] poly,float margin){
        if(poly==null||poly.length<6)return 1f;
        boolean inside=false;
        float edge=Float.POSITIVE_INFINITY;
        int n=poly.length/2;
        for(int i=0,j=n-1;i<n;j=i++){
            float xi=poly[2*i],zi=poly[2*i+1];
            float xj=poly[2*j],zj=poly[2*j+1];
            boolean cross=((zi>z)!=(zj>z)) &&
                    (x < (xj-xi)*(z-zi)/((zj-zi)==0f?1e-9f:(zj-zi)) + xi);
            if(cross)inside=!inside;
            edge=Math.min(edge,distancePointSegment(x,z,xi,zi,xj,zj));
        }
        if(inside){
            // Plane edges change as ARCore discovers more surface; keep them useful but weaker.
            float t=Math.min(1f,edge/0.12f);
            return 0.58f+0.42f*t;
        }
        if(edge>margin)return 0f;
        // Outside polygon: guidance/classification only, never enough to rescue weak raw depth.
        return Math.max(0.02f,0.24f*(1f-edge/Math.max(0.001f,margin)));
    }

    private static float distancePointSegment(float px,float pz,float ax,float az,float bx,float bz){
        float vx=bx-ax, vz=bz-az;
        float wx=px-ax, wz=pz-az;
        float vv=vx*vx+vz*vz;
        if (vv<=1e-10f) {
            float dx=px-ax,dz=pz-az;
            return (float)Math.sqrt(dx*dx+dz*dz);
        }
        float t=(wx*vx+wz*vz)/vv;
        if(t<0f)t=0f; else if(t>1f)t=1f;
        float qx=ax+t*vx,qz=az+t*vz;
        float dx=px-qx,dz=pz-qz;
        return (float)Math.sqrt(dx*dx+dz*dz);
    }

    private static double normalizeQuarter(double a){
        while(a>Math.PI/4)a-=Math.PI/2;
        while(a<-Math.PI/4)a+=Math.PI/2;
        return a;
    }

    /**
     * Keep a bounded architectural working set without letting one plane family starve another.
     * Pure area ranking is better than ARCore iteration order, but a room with many fragmented
     * vertical planes can still evict every horizontal candidate (or a cluster of tables can
     * crowd out walls).  Floor/ceiling locking and wall guidance are independent signals, so keep
     * at least one representative of each horizontal facing type and a useful wall reserve.
     */
    private static boolean retainArchitectural(List<Model> out,Model candidate){
        if(out.size()<MAX_PLANES){out.add(candidate);return true;}

        int sameKind=0,vertical=0;
        int weakest=-1;float weakestScore=Float.POSITIVE_INFINITY;
        int weakestSame=-1;float weakestSameScore=Float.POSITIVE_INFINITY;
        for(int i=0;i<out.size();i++){
            Model m=out.get(i);
            if(m.planeKind==1)vertical++;
            float score=importance(m);
            if(score<weakestScore){weakestScore=score;weakest=i;}
            if(m.planeKind==candidate.planeKind){
                sameKind++;
                if(score<weakestSameScore){weakestSameScore=score;weakestSame=i;}
            }
        }

        // Never lose the only upward/downward horizontal candidate merely because walls were
        // discovered first.  The strongest such candidate is later filtered by camera height,
        // normal and relative area before it can become floor/ceiling.
        if((candidate.planeKind==2||candidate.planeKind==3) && sameKind==0){
            int replace=weakestUnprotected(out);
            if(replace<0)replace=weakest;
            if(replace>=0){out.set(replace,candidate);return true;}
            return false;
        }
        // Likewise retain a minimum wall population for room axis / nearest-wall guidance.
        if(candidate.planeKind==1 && vertical<8){
            int replace=weakestNonVertical(out);
            if(replace<0)replace=weakest;
            if(replace>=0){out.set(replace,candidate);return true;}
            return false;
        }

        float candidateScore=importance(candidate);
        // Prefer replacing a weaker member of the same family. This preserves cross-family
        // evidence while still allowing a newly discovered large plane to displace fragments.
        if(weakestSame>=0 && candidateScore>weakestSameScore){
            out.set(weakestSame,candidate); return true;
        }else if(weakest>=0 && candidateScore>weakestScore*1.12f){
            // Cross-family eviction requires a meaningful margin to avoid selection thrash.
            out.set(weakest,candidate); return true;
        }
        return false;
    }

    private static int weakestUnprotected(List<Model> out){
        int up=0,down=0;for(Model m:out){if(m.planeKind==2)up++;else if(m.planeKind==3)down++;}
        int idx=-1;float score=Float.POSITIVE_INFINITY;
        for(int i=0;i<out.size();i++){
            Model m=out.get(i);
            if((m.planeKind==2&&up<=1)||(m.planeKind==3&&down<=1))continue;
            float s=importance(m);if(s<score){score=s;idx=i;}
        }
        return idx;
    }

    private static int weakestNonVertical(List<Model> out){
        int idx=-1;float score=Float.POSITIVE_INFINITY;
        for(int i=0;i<out.size();i++){
            Model m=out.get(i);if(m.planeKind==1)continue;
            float s=importance(m);if(s<score){score=s;idx=i;}
        }
        return idx;
    }

    private static float importance(Model m){
        // Plane.Type is more stable for family reservation; transformed normal remains the metric
        // test used for actual matching/classification.
        float structural=m.planeKind==1?1.18f:1f;
        return m.area*structural;
    }


    private void updateFloorLevel(float candidateY,float area){
        if(!Float.isFinite(floorY)){
            floorY=candidateY; floorLockFrames=1; pendingFloorFrames=0; pendingFloorY=Float.NaN; return;
        }
        float delta=Math.abs(candidateY-floorY);
        if(delta<=0.18f){
            floorY=stabilizeHeight(floorY,candidateY,area);
            floorLockFrames=Math.min(1000,floorLockFrames+1);
            pendingFloorFrames=0; pendingFloorY=Float.NaN; return;
        }
        // Do not let one newly discovered landing/table instantly invalidate a mature room floor.
        if(!Float.isFinite(pendingFloorY)||Math.abs(candidateY-pendingFloorY)>0.12f){
            pendingFloorY=candidateY; pendingFloorFrames=1;
        }else{
            pendingFloorY=pendingFloorY*0.72f+candidateY*0.28f;
            pendingFloorFrames++;
        }
        if(pendingFloorFrames>=4 && area>=0.55f){
            floorY=pendingFloorY; floorLockFrames=1; pendingFloorFrames=0; pendingFloorY=Float.NaN;
        }else if(floorLockFrames>3){
            // Keep a mature level alive while a challenger is being verified, but do not make its
            // confidence grow from evidence that belongs to another height.
            floorLockFrames--;
        }
    }

    private void updateCeilingLevel(float candidateY,float area){
        if(!Float.isFinite(ceilingY)){
            ceilingY=candidateY; ceilingLockFrames=1; pendingCeilingFrames=0; pendingCeilingY=Float.NaN; return;
        }
        float delta=Math.abs(candidateY-ceilingY);
        if(delta<=0.20f){
            ceilingY=stabilizeHeight(ceilingY,candidateY,area);
            ceilingLockFrames=Math.min(1000,ceilingLockFrames+1);
            pendingCeilingFrames=0; pendingCeilingY=Float.NaN; return;
        }
        if(!Float.isFinite(pendingCeilingY)||Math.abs(candidateY-pendingCeilingY)>0.14f){
            pendingCeilingY=candidateY; pendingCeilingFrames=1;
        }else{
            pendingCeilingY=pendingCeilingY*0.72f+candidateY*0.28f;
            pendingCeilingFrames++;
        }
        if(pendingCeilingFrames>=4 && area>=0.55f){
            ceilingY=pendingCeilingY; ceilingLockFrames=1; pendingCeilingFrames=0; pendingCeilingY=Float.NaN;
        }else if(ceilingLockFrames>3){
            ceilingLockFrames--;
        }
    }

    private static float stabilizeHeight(float oldValue,float candidate,float area){
        if (!Float.isFinite(oldValue)) return candidate;
        float delta=Math.abs(candidate-oldValue);
        if (delta>0.35f && area<1.8f) return oldValue;
        float alpha=delta<0.12f?0.22f:0.10f;
        return oldValue*(1f-alpha)+candidate*alpha;
    }
}
