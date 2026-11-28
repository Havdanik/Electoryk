package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.CloudPoint;
import com.elektoryk.scan.model.SceneSnapshot;
import com.elektoryk.scan.model.SurfaceObservation;

import java.util.List;

public class SceneAnalysisPipeline {
    public SceneSnapshot analyze(String projectId,
                                 List<CloudPoint> points,
                                 int depthFrames,
                                 int rawSamples,
                                 int directionCoveragePercent) {
        return analyze(projectId,points,depthFrames,rawSamples,directionCoveragePercent,
                false,Float.NaN,false,Float.NaN);
    }

    /**
     * Precision overload: independent ARCore horizontal-plane evidence may refine floor/ceiling
     * height, but only when it agrees with the Raw-Depth-derived envelope. This keeps tables and
     * shelves from becoming a false floor while reducing vertical dimension noise.
     */
    public SceneSnapshot analyze(String projectId,
                                 List<CloudPoint> points,
                                 int depthFrames,
                                 int rawSamples,
                                 int directionCoveragePercent,
                                 boolean floorPlaneLocked,float floorPlaneY,
                                 boolean ceilingPlaneLocked,float ceilingPlaneY) {
        // One quarantine boundary for the complete reconstruction stack. Most scans take the
        // zero-allocation fast path and reuse the original list. Only a cloud containing a corrupt
        // coordinate/confidence is copied, preventing NaN/Infinity from reaching footprint, wall,
        // grid, opening or object algorithms that reasonably assume metric finite input.
        List<CloudPoint> safePoints = sanitizeCloud(points);

        SceneSnapshot s = new SceneSnapshot();
        s.projectId = projectId;
        s.depthFrameCount = depthFrames;
        s.rawPointSamples = rawSamples;
        s.voxelPointCount = safePoints == null ? 0 : safePoints.size();
        s.directionCoveragePercent = directionCoveragePercent;

        RoomAnalyzer.Result room = new RoomAnalyzer().analyze(safePoints);
        s.roomBounds = room.bounds;
        if(s.roomBounds!=null){
            if(floorPlaneLocked && Float.isFinite(floorPlaneY)
                    && Math.abs(s.roomBounds.floorY-floorPlaneY)<=0.22){
                s.roomBounds.floorY=s.roomBounds.floorY*0.35+floorPlaneY*0.65;
            }
            if(ceilingPlaneLocked && Float.isFinite(ceilingPlaneY)
                    && Math.abs(s.roomBounds.ceilingY-ceilingPlaneY)<=0.25){
                s.roomBounds.ceilingY=s.roomBounds.ceilingY*0.35+ceilingPlaneY*0.65;
            }
            syncSurfaceHeights(room.surfaces,s.roomBounds);
        }
        s.surfaces.addAll(room.surfaces);

        if (s.roomBounds != null) {
            FloorFootprintEstimator.Result footprint = new FloorFootprintEstimator().estimate(safePoints, s.roomBounds);
            s.observedFloorAreaM2 = footprint.areaM2;
            s.floorObservationRatio = footprint.observedRatio;
            s.reviewIssues.addAll(
                    new InteriorObjectDetector(0.18).detect(safePoints, s.roomBounds));
        }
        return s;
    }

    /**
     * Validate once at the pipeline boundary. Returns the exact original list when every sample is
     * healthy, so normal long scans pay only a linear validation pass and no extra cloud memory.
     * If corruption is present, invalid coordinates are dropped and non-finite confidence is
     * normalized to zero evidence while preserving the valid metric samples.
     */
    private List<CloudPoint> sanitizeCloud(List<CloudPoint> points) {
        if (points == null || points.isEmpty()) return points;
        boolean dirty=false;
        for (CloudPoint p:points) {
            if (p==null || !Float.isFinite(p.x) || !Float.isFinite(p.y) || !Float.isFinite(p.z)
                    || !Float.isFinite(p.confidence)) { dirty=true; break; }
        }
        if (!dirty) return points;
        java.util.ArrayList<CloudPoint> clean=new java.util.ArrayList<>(points.size());
        for (CloudPoint p:points) {
            if (p==null || !Float.isFinite(p.x) || !Float.isFinite(p.y) || !Float.isFinite(p.z)) continue;
            float confidence=Float.isFinite(p.confidence) ? Math.max(0f,Math.min(1f,p.confidence)) : 0f;
            clean.add(new CloudPoint(p.x,p.y,p.z,confidence));
        }
        return clean;
    }

    private void syncSurfaceHeights(List<SurfaceObservation> surfaces,com.elektoryk.scan.model.RoomBounds b){
        if(surfaces==null||b==null)return;
        for(SurfaceObservation x:surfaces){
            if(x==null||x.center==null)continue;
            if(x.kind==SurfaceObservation.Kind.FLOOR){
                x.center.y=b.floorY;x.planeDistanceM=b.floorY;
            }else if(x.kind==SurfaceObservation.Kind.CEILING){
                x.center.y=b.ceilingY;x.planeDistanceM=b.ceilingY;
            }else if(x.kind==SurfaceObservation.Kind.WALL){
                x.center.y=(b.floorY+b.ceilingY)/2.0;x.heightM=b.heightM();
            }
        }
    }
}
