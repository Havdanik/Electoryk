package com.elektoryk.scan.geometry;

public final class ScaleCalibration {
    private ScaleCalibration() {}

    public static double factor(double scannedMeters, double measuredMeters) {
        if (scannedMeters <= 0.000001 || measuredMeters <= 0.0) return 1.0;
        return measuredMeters / scannedMeters;
    }

    public static double corrected(double rawMeters, double factor) {
        return rawMeters * factor;
    }
}
