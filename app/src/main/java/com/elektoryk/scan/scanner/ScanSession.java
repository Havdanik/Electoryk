package com.elektoryk.scan.scanner;

import java.util.UUID;

public class ScanSession {
    public enum State {
        IDLE,
        SCANNING,
        PAUSED,
        PROCESSING,
        READY,
        FAILED
    }

    public String id = UUID.randomUUID().toString();
    public String projectId;
    public long startedAt;
    public long endedAt;
    public State state = State.IDLE;
    public int frameCount;
    public String scannerProfile = "";
}
