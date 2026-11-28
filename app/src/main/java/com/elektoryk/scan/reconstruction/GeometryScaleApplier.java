package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.*;

public final class GeometryScaleApplier {
    private GeometryScaleApplier(){}

    public static void apply(SceneSnapshot scene, FloorPlan floor, double factor){
        if(scene==null||factor<=0||Math.abs(factor-1.0)<1e-9)return;

        if(scene.roomBounds!=null)scaleBounds(scene.roomBounds,factor);
        if(scene.apartmentPlan!=null)scalePlan(scene.apartmentPlan,factor);
        if(scene.latestAutoApartmentPlan!=null)scalePlan(scene.latestAutoApartmentPlan,factor);

        for(ReviewIssue r:scene.reviewIssues){
            r.minX*=factor;r.maxX*=factor;r.minY*=factor;r.maxY*=factor;r.minZ*=factor;r.maxZ*=factor;
        }
        for(ScanGap g:scene.scanGaps){
            g.minX*=factor;g.maxX*=factor;g.minZ*=factor;g.maxZ*=factor;
            g.estimatedAreaM2*=factor*factor;
        }
        for(ScanPathPoint s:scene.scanPath){s.x*=factor;s.z*=factor;}
        scene.observedFloorAreaM2*=factor*factor;
        scene.rawPointScale*=factor;

        if(floor!=null){
            for(RoomRect r:floor.rooms){
                r.xM*=factor;r.zM*=factor;r.widthM*=factor;r.lengthM*=factor;r.heightM*=factor;
            }
            for(ElectricalPoint e:floor.points){
                // X/Z live in scan geometry and must follow metric calibration.
                // Mounting height is a user-entered real-world value (e.g. 0.30 m)
                // and must NOT be silently scaled.
                e.position.x*=factor;e.position.z*=factor;
                e.position.y=e.heightFromFinishedFloorM;
                if(e.wallOffsetM>=0)e.wallOffsetM*=factor;
            }
            floor.scaleCorrection=1.0;
            floor.updatedAt=System.currentTimeMillis();
        }

        // Existing anchors remain as historical evidence: scanned side is moved into the corrected metric space.
        for(MeasurementAnchor a:scene.measurementAnchors)a.scannedM*=factor;
        scene.updatedAt=System.currentTimeMillis();
    }

    private static void scalePlan(ApartmentPlan p,double f){
        p.cellM*=f;p.originX*=f;p.originZ*=f;p.floorY*=f;p.ceilingY*=f;
        p.directObservedAreaM2*=f*f;p.inferredFloorAreaM2*=f*f;p.totalObservedAreaM2*=f*f;p.totalRoomAreaM2*=f*f;

        for(PlanWallSegment w:p.walls){
            w.x1*=f;w.z1*=f;w.x2*=f;w.z2*=f;
            if(!w.manual)w.thicknessM*=f;
        }
        for(RoomZone r:p.rooms){
            r.areaM2*=f*f;r.centroidX*=f;r.centroidZ*=f;
            r.minX*=f;r.maxX*=f;r.minZ*=f;r.maxZ*=f;
        }
        for(ApartmentOpening o:p.openings){
            o.x1*=f;o.z1*=f;o.x2*=f;o.z2*=f;
            o.bottomY*=f;o.topY*=f;o.widthM*=f;o.heightM*=f;
        }
        for(StructuralFeature s:p.features){
            s.minX*=f;s.maxX*=f;s.minZ*=f;s.maxZ*=f;s.minY*=f;s.maxY*=f;
        }
        for(MissingZoneHint h:p.missingZoneHints){
            h.x*=f;h.z*=f;h.estimatedAreaMinM2*=f*f;h.estimatedAreaMaxM2*=f*f;
        }
        for(ForbiddenZone z:p.forbiddenZones){
            z.minX*=f;z.maxX*=f;z.minZ*=f;z.maxZ*=f;z.minY*=f;z.maxY*=f;
        }
        for(FurnitureItem item:p.furniture){
            // Placement follows calibrated scan coordinates. Preset object sizes are user-entered real dimensions.
            item.x*=f;item.z*=f;
        }
        p.lastManualEditAt=System.currentTimeMillis();
    }

    private static void scaleBounds(RoomBounds b,double f){
        b.minX*=f;b.maxX*=f;b.floorY*=f;b.ceilingY*=f;b.minZ*=f;b.maxZ*=f;
    }
}
