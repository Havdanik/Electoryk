package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.ApartmentOpening;
import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.CloudPoint;
import com.elektoryk.scan.model.RoomBounds;
import com.elektoryk.scan.model.RoomZone;
import com.elektoryk.scan.model.PlanWallSegment;
import com.elektoryk.scan.model.ScanPathPoint;
import com.elektoryk.scan.model.StructuralFeature;

import java.util.List;

public class MultiRoomReconstructionEngine {
    public ApartmentPlan analyze(List<CloudPoint> points,
                                 RoomBounds bounds,
                                 List<ScanPathPoint> path){
        ApartmentPlan plan=new ApartmentPlan();
        if(points==null||points.isEmpty()||bounds==null||!bounds.isUsable())return plan;

        ApartmentGrid grid=ApartmentGrid.build(points,bounds,0.10);
        if(grid==null)return plan;

        plan.cellM=grid.cellM;
        plan.originX=grid.originX;plan.originZ=grid.originZ;
        plan.rows=grid.rows;plan.cols=grid.cols;
        plan.floorY=grid.floorY;plan.ceilingY=grid.ceilingY;
        plan.directObservedAreaM2=grid.directObservedFloorCells()*grid.cellM*grid.cellM;
        plan.inferredFloorAreaM2=grid.inferredFloorCells()*grid.cellM*grid.cellM;
        plan.totalObservedAreaM2=grid.observedFloorCells()*grid.cellM*grid.cellM;

        WallLineDetector wallDetector=new WallLineDetector();
        // v0.18: detect persistent vertical planes first. The old cell-by-cell wall detector
        // happily promoted furniture edges and depth noise into dozens of fake walls.
        List<WallLineDetector.Segment> walls=new StructuralWallExtractor().extract(points,bounds,grid);
        // Final topology guard: a single room must not expose furniture/jambs as 7-30 walls.
        walls=new RoomPerimeterSanitizer().sanitize(walls,points,bounds,grid);
        // Never fall back to cell-edge walls. If structural evidence is weak, return an incomplete
        // model and ask for more scan evidence instead of inventing dozens of walls from furniture.
        boolean[][] barriers=wallDetector.barrierMap(grid,walls);

        // Persist wall geometry as first-class plan objects. This becomes the base for
        // manual corrections, wall-mounted electrical points and future cable routing.
        for(WallLineDetector.Segment s:walls){
            PlanWallSegment w=new PlanWallSegment();
            if(s.horizontal){
                w.x1=grid.originX+s.start*grid.cellM;
                w.x2=grid.originX+(s.end+1)*grid.cellM;
                w.z1=w.z2=grid.zCenter(s.fixed);
            }else{
                w.z1=grid.originZ+s.start*grid.cellM;
                w.z2=grid.originZ+(s.end+1)*grid.cellM;
                w.x1=w.x2=grid.xCenter(s.fixed);
            }
            w.thicknessM=Math.max(0.08,grid.cellM);
            w.confidence=Math.max(0.35,Math.min(0.98,s.support));
            plan.walls.add(w);
        }

        new FineWallRefiner().refine(plan,points,bounds);

        List<ApartmentOpening> openings=new OpeningDetector().detect(grid,walls,path);
        for(ApartmentOpening o:openings) attachOpeningToNearestWall(plan,o);

        MultiRoomSegmenter.Result segmented=new MultiRoomSegmenter()
                .segment(grid,barriers,openings);
        plan.rooms.addAll(segmented.rooms);
        plan.openings.addAll(openings);

        List<StructuralFeature> features=new StructuralFeatureDetector()
                .detect(grid,barriers,segmented.rooms);
        plan.features.addAll(features);

        double roomsArea=0;
        for(RoomZone r:plan.rooms)roomsArea+=r.areaM2;
        plan.totalRoomAreaM2=roomsArea;

        // Coverage must be measured against the reconstructed footprint, not the rectangular
        // grid envelope. Otherwise a perfectly scanned L-shaped apartment is permanently
        // penalized for the intentionally empty quadrant of its bounding box.
        double coverage;
        if(roomsArea>0.5){
            coverage=Math.max(0.0,Math.min(1.0,plan.directObservedAreaM2/roomsArea));
        }else{
            coverage=grid.rows*grid.cols==0?0:
                    grid.observedFloorCells()/(double)(grid.rows*grid.cols);
        }
        double avgWallSupport=0;for(WallLineDetector.Segment x:walls)avgWallSupport+=Math.max(0,Math.min(1,x.support));
        if(!walls.isEmpty())avgWallSupport/=walls.size();
        double wallEvidence=Math.min(1.0,walls.size()/4.0)*avgWallSupport;
        double roomEvidence=plan.rooms.isEmpty()?0:Math.min(1.0,0.55+plan.rooms.size()*0.08);
        plan.confidence=Math.max(0.15,Math.min(0.96,
                0.35+coverage*0.20+wallEvidence*0.18+roomEvidence*0.22));

        return plan;
    }

    private void attachOpeningToNearestWall(ApartmentPlan plan,ApartmentOpening o){
        PlanWallSegment best=null;
        double bestD=Double.POSITIVE_INFINITY;
        boolean openingHorizontal=o.orientation==ApartmentOpening.Orientation.HORIZONTAL_X;
        for(PlanWallSegment w:plan.walls){
            boolean wallHorizontal=Math.abs(w.x2-w.x1)>=Math.abs(w.z2-w.z1);
            if(wallHorizontal!=openingHorizontal)continue;
            double d;
            if(wallHorizontal){
                d=Math.abs(w.z1-o.centerZ());
                double ox=o.centerX();
                double min=Math.min(w.x1,w.x2)-1.2,max=Math.max(w.x1,w.x2)+1.2;
                if(ox<min||ox>max)continue;
            }else{
                d=Math.abs(w.x1-o.centerX());
                double oz=o.centerZ();
                double min=Math.min(w.z1,w.z2)-1.2,max=Math.max(w.z1,w.z2)+1.2;
                if(oz<min||oz>max)continue;
            }
            if(d<bestD){bestD=d;best=w;}
        }
        if(best!=null&&bestD<0.45)o.wallId=best.id;
    }
}
