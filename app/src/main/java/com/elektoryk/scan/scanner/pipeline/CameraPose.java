package com.elektoryk.scan.scanner.pipeline;

public class CameraPose {
    public long timestampNs;
    public double x;
    public double y;
    public double z;
    public double qx;
    public double qy;
    public double qz;
    public double qw = 1.0;
    public float trackingConfidence;
}
