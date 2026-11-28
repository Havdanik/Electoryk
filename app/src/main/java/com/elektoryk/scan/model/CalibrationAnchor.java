package com.elektoryk.scan.model;

/** One trusted measured distance used to correct model scale. */
public class CalibrationAnchor {
    public Vec3 start = new Vec3();
    public Vec3 end = new Vec3();
    public double measuredDistanceM;

    public double scannedDistanceM() {
        return end.sub(start).length();
    }

    public double scaleFactor() {
        double scanned = scannedDistanceM();
        return scanned > 1e-6 && measuredDistanceM > 0.0 ? measuredDistanceM / scanned : 1.0;
    }
}
