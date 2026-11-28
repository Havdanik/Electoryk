package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.RoomBounds;
import com.elektoryk.scan.model.RoomRect;
import com.elektoryk.scan.model.RoomZone;
import com.elektoryk.scan.model.SceneSnapshot;

public class AutoFloorPlanBuilder {
    public FloorPlan build(SceneSnapshot scene) {
        FloorPlan plan = new FloorPlan();
        if (scene == null) return plan;
        plan.projectId = scene.projectId;

        if (scene.apartmentPlan != null && !scene.apartmentPlan.rooms.isEmpty()) {
            for (RoomZone zone : scene.apartmentPlan.rooms) {
                RoomRect r = new RoomRect();
                r.id = zone.id;
                r.name = zone.displayName();
                r.xM = zone.minX;
                r.zM = zone.minZ;
                r.widthM = Math.max(0.1, zone.maxX-zone.minX);
                r.lengthM = Math.max(0.1, zone.maxZ-zone.minZ);
                r.heightM = scene.roomBounds != null && scene.roomBounds.isUsable()
                        ? scene.roomBounds.heightM() : 2.7;
                plan.rooms.add(r);
            }
            return plan;
        }

        RoomBounds b = scene.roomBounds;
        if (b != null && b.isUsable()) {
            RoomRect r = new RoomRect();
            r.name = "Авто кімната 1";
            r.xM = b.minX;
            r.zM = b.minZ;
            r.widthM = b.widthM();
            r.lengthM = b.lengthM();
            r.heightM = b.heightM();
            plan.rooms.add(r);
        }
        return plan;
    }
}
