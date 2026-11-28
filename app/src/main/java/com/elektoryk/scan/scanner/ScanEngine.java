package com.elektoryk.scan.scanner;

public interface ScanEngine {
    interface Listener {
        void onStateChanged(ScanSession session);
        void onQualityChanged(int percent, String message);
        void onError(String message, Throwable error);
    }

    void setListener(Listener listener);
    void start(String projectId);
    void pause();
    void resume();
    void stop();
    ScanSession getSession();
}
