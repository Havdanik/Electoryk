package com.elektoryk.scan.scanner.pipeline;

public interface FrameSource {
    interface Listener {
        void onFrame(ScanFrameBundle frame);
        void onTrackingState(boolean tracking, String message);
        void onFrameSourceError(String message, Throwable error);
    }

    void setListener(Listener listener);
    void start();
    void stop();
    String getName();
}
