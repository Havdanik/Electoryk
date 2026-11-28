package com.elektoryk.scan.model;

public class CloudPoint {
    public float x;
    public float y;
    public float z;
    public float confidence = 1f;

    public CloudPoint() {}

    public CloudPoint(float x, float y, float z, float confidence) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.confidence = confidence;
    }
}
