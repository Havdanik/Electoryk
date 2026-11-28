package com.elektoryk.scan.geometry;

import com.elektoryk.scan.model.SurfaceObservation;
import com.elektoryk.scan.model.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Foundation heuristic layer.
 *
 * IMPORTANT:
 * This class does not invent geometry behind objects. It produces classifications and
 * snap suggestions. Final destructive cleanup should remain user-reviewable.
 */
public class SmartGeometryEngine {

    public static class CleanupDecision {
        public SurfaceObservation source;
        public SurfaceObservation.Kind suggestedKind;
        public double confidence;
        public boolean canFillFromNeighborPlane;
        public String reason;
    }

    public List<CleanupDecision> classify(List<SurfaceObservation> observations) {
        List<CleanupDecision> out = new ArrayList<>();
        if (observations == null) return out;

        for (SurfaceObservation s : observations) {
            CleanupDecision d = new CleanupDecision();
            d.source = s;
            d.suggestedKind = s.kind;
            d.confidence = s.confidence;
            d.canFillFromNeighborPlane = false;
            d.reason = "No rule matched";

            if (s.vertical && s.widthM > 0.8 && s.heightM > 1.2 && s.confidence > 0.55) {
                d.suggestedKind = SurfaceObservation.Kind.WALL;
                d.confidence = Math.max(d.confidence, 0.72);
                d.reason = "Large stable vertical plane";
            }

            if (s.vertical
                    && s.protrusionDepthM > 0.04
                    && s.protrusionDepthM < 1.2
                    && s.widthM < 1.5
                    && s.heightM < 2.6
                    && s.confidence < 0.78) {
                d.suggestedKind = SurfaceObservation.Kind.TEMPORARY_OBJECT;
                d.confidence = Math.max(d.confidence, 0.62);
                d.canFillFromNeighborPlane = true;
                d.reason = "Local protrusion in front of probable wall plane";
            }

            if (s.confidence < 0.25 && (s.widthM < 0.35 || s.heightM < 0.35)) {
                d.suggestedKind = SurfaceObservation.Kind.SCAN_ARTIFACT;
                d.confidence = 0.80;
                d.canFillFromNeighborPlane = true;
                d.reason = "Small low-confidence isolated geometry";
            }

            out.add(d);
        }
        return out;
    }

    public Vec3 snapWallNormal(Vec3 rawNormal, double toleranceDegrees) {
        if (rawNormal == null) return new Vec3(1, 0, 0);
        Vec3 n = rawNormal.normalized();

        Vec3[] axes = {
                new Vec3(1,0,0), new Vec3(-1,0,0),
                new Vec3(0,0,1), new Vec3(0,0,-1)
        };

        Vec3 best = n;
        double bestAngle = 180.0;
        for (Vec3 axis : axes) {
            double dot = Math.max(-1.0, Math.min(1.0, n.dot(axis)));
            double angle = Math.toDegrees(Math.acos(dot));
            if (angle < bestAngle) {
                bestAngle = angle;
                best = axis;
            }
        }

        return bestAngle <= toleranceDegrees ? best : n;
    }
}
