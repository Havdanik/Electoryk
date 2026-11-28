package com.elektoryk.scan.scanner.pipeline;

public class DepthFrame {
    public enum Source {
        ARCORE_DEPTH,
        CAMERA2_DEPTH,
        EXTERNAL_SENSOR,
        NONE
    }

    public long timestampNs;
    public int width;
    public int height;
    public Source source = Source.NONE;

    // Depth payload intentionally stays adapter-specific in foundation.
    // We do not copy huge depth buffers through the generic model.
    public Object nativeHandle;
}
