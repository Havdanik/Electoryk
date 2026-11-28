package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.ElectricalPoint;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.PlanWallSegment;
import com.elektoryk.scan.model.RoomZone;
import com.elektoryk.scan.model.RoomFootprint;
import com.elektoryk.scan.model.Vec3;

/**
 * Keeps wall-mounted points stable in wall-local coordinates.
 * 3D, wall elevation and 2D all use the same ElectricalPoint instance.
 */
public class WallPlacementEngine {
    public static class Metrics {
        public String wallId = "";
        public double wallLengthM;
        public double fromStartM;
        public double fromEndM;
        public double fromNearestCornerM;
        public double heightFromFinishedFloorM;
        public double toCeilingM;
        public double wallFraction;
        public String roomId = "";
    }

    public boolean place(ApartmentPlan plan, FloorPlan floor, ElectricalPoint point,
                         String wallId, double fraction, double heightM) {
        if (plan == null || floor == null || point == null || wallId == null) return false;
        PlanWallSegment w = findWall(plan, wallId);
        if (w == null || w.lengthM() < 0.05) return false;
        double t = clamp(fraction, 0.015, 0.985);
        double h = clamp(heightM, 0.0, usableHeight(plan));
        point.wallId = wallId;
        point.wallFraction = t;
        point.wallOffsetM = t * w.lengthM();
        point.heightFromFinishedFloorM = h;
        point.position = new Vec3(
                w.x1 + (w.x2 - w.x1) * t,
                h,
                w.z1 + (w.z2 - w.z1) * t);
        RoomZone room = findBestRoom(plan, point.position.x, point.position.z);
        point.roomId = room == null ? point.roomId : room.id;
        point.manualPosition = true;
        point.lockedToWall = true;
        point.placementSource = "DIGITAL_TWIN";
        return true;
    }

    public Metrics metrics(ApartmentPlan plan, ElectricalPoint point) {
        Metrics m = new Metrics();
        if (plan == null || point == null) return m;
        m.heightFromFinishedFloorM = point.heightFromFinishedFloorM;
        m.toCeilingM = Math.max(0, usableHeight(plan) - point.heightFromFinishedFloorM);
        m.roomId = point.roomId == null ? "" : point.roomId;
        PlanWallSegment w = findWall(plan, point.wallId);
        if (w == null) return m;
        m.wallId = w.id;
        m.wallLengthM = w.lengthM();
        double t = point.wallFraction;
        if (!(t > 0 && t < 1)) t = fractionFor(w, point.position.x, point.position.z);
        t = clamp(t, 0, 1);
        m.wallFraction = t;
        m.fromStartM = t * m.wallLengthM;
        m.fromEndM = (1.0 - t) * m.wallLengthM;
        m.fromNearestCornerM = Math.min(m.fromStartM, m.fromEndM);
        return m;
    }

    /** Reprojects wall-locked points after a wall was edited, preserving wall-local offset. */
    public void refreshLockedPoints(ApartmentPlan plan, FloorPlan floor) {
        if (plan == null || floor == null) return;
        for (ElectricalPoint p : floor.points) {
            if (!p.lockedToWall || p.wallId == null || p.wallId.isEmpty()) continue;
            PlanWallSegment w = findWall(plan, p.wallId);
            if (w == null) { p.lockedToWall = false; continue; }
            double t = p.wallFraction;
            if (!(t > 0 && t < 1)) t = fractionFor(w, p.position.x, p.position.z);
            place(plan, floor, p, w.id, t, p.heightFromFinishedFloorM);
        }
    }

    public double fractionFor(PlanWallSegment w, double x, double z) {
        if (w == null) return 0.5;
        double dx = w.x2 - w.x1, dz = w.z2 - w.z1;
        double den = dx * dx + dz * dz;
        if (den < 1e-9) return 0.5;
        return clamp(((x - w.x1) * dx + (z - w.z1) * dz) / den, 0, 1);
    }

    public PlanWallSegment findWall(ApartmentPlan plan, String id) {
        if (plan == null || id == null) return null;
        for (PlanWallSegment w : plan.walls) if (id.equals(w.id)) return w;
        return null;
    }

    private RoomZone findBestRoom(ApartmentPlan plan, double x, double z) {
        if (plan == null) return null;
        RoomZone best = null; double bd = Double.POSITIVE_INFINITY;
        for (RoomZone r : plan.rooms) {
            if (RoomFootprint.contains(plan, r, x, z, 1)) {
                double d = Math.hypot(x - r.centroidX, z - r.centroidZ);
                if (d < bd) { bd = d; best = r; }
            }
        }
        return best;
    }

    private double usableHeight(ApartmentPlan p) {
        double h = p.ceilingY - p.floorY;
        return h > 1.5 ? h : 2.7;
    }

    private double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
}
