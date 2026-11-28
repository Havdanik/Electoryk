package com.elektoryk.scan.scanner.pipeline;

import com.elektoryk.scan.model.SceneModel;

public interface ReconstructionEngine {
    void reset();
    void push(ScanFrameBundle frame);
    SceneModel snapshot();
    int getAcceptedFrameCount();
    int getRejectedFrameCount();
}
