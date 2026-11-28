package com.elektoryk.scan.scanner;

import android.content.Context;

import com.elektoryk.scan.device.DeviceCapabilities;
import com.elektoryk.scan.device.DeviceCapabilityDetector;
import com.elektoryk.scan.device.ScannerProfile;

/**
 * State machine used by UI now.
 * Camera/AR frame acquisition will be plugged in behind this contract.
 */
public class FoundationScanEngine implements ScanEngine {
    private final Context context;
    private Listener listener;
    private ScanSession session = new ScanSession();

    public FoundationScanEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void start(String projectId) {
        DeviceCapabilities caps = new DeviceCapabilityDetector().detect(context);
        ScannerProfile profile = ScannerProfile.choose(caps);

        session = new ScanSession();
        session.projectId = projectId;
        session.startedAt = System.currentTimeMillis();
        session.state = ScanSession.State.SCANNING;
        session.scannerProfile = profile.mode.name();

        emitState();
        emitQuality(10, "Діагностика Camera2 + IMU. Цей fallback не створює 3D-сцену.");
    }

    @Override
    public void pause() {
        if (session.state == ScanSession.State.SCANNING) {
            session.state = ScanSession.State.PAUSED;
            emitState();
        }
    }

    @Override
    public void resume() {
        if (session.state == ScanSession.State.PAUSED) {
            session.state = ScanSession.State.SCANNING;
            emitState();
        }
    }

    @Override
    public void stop() {
        if (session.state == ScanSession.State.SCANNING
                || session.state == ScanSession.State.PAUSED) {
            session.endedAt = System.currentTimeMillis();
            session.state = ScanSession.State.PROCESSING;
            emitState();

            // Diagnostic fallback only: intentionally does not pretend to produce geometry.
            session.state = ScanSession.State.READY;
            emitQuality(0, "Діагностичну сесію збережено. 3D-сцена НЕ створена; для плану потрібен Smart Scan з ARCore Depth.");
            emitState();
        }
    }

    @Override
    public ScanSession getSession() {
        return session;
    }

    private void emitState() {
        if (listener != null) listener.onStateChanged(session);
    }

    private void emitQuality(int p, String m) {
        if (listener != null) listener.onQualityChanged(p, m);
    }
}
