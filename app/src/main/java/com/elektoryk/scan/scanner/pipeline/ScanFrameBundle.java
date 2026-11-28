package com.elektoryk.scan.scanner.pipeline;

public class ScanFrameBundle {
    public RgbFrame rgb;
    public DepthFrame depth;
    public CameraPose pose;

    public boolean hasDepth() {
        return depth != null && depth.source != DepthFrame.Source.NONE;
    }

    public boolean hasPose() {
        return pose != null && pose.trackingConfidence > 0f;
    }
}
