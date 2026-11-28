package com.elektoryk.scan.ui;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.TextureView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.elektoryk.scan.scanner.CameraPreviewController;
import com.elektoryk.scan.scanner.FoundationScanEngine;
import com.elektoryk.scan.scanner.MotionTracker;
import com.elektoryk.scan.scanner.PoseSample;
import com.elektoryk.scan.scanner.ScanEngine;
import com.elektoryk.scan.scanner.ScanRecorder;
import com.elektoryk.scan.scanner.ScanSession;

public class ScanActivity extends Activity implements
        ScanEngine.Listener,
        CameraPreviewController.Listener,
        MotionTracker.Listener {

    private static final int REQ_CAMERA = 1001;

    private ScanEngine engine;
    private CameraPreviewController camera;
    private MotionTracker motionTracker;
    private ScanRecorder recorder;
    private String projectId;

    private TextView state;
    private TextView quality;
    private TextView cameraInfo;
    private TextView poseInfo;
    private ProgressBar coverage;
    private Button action;
    private Button pause;
    private TextureView preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectId = getIntent().getStringExtra("projectId");

        engine = new FoundationScanEngine(this);
        engine.setListener(this);
        motionTracker = new MotionTracker(this, this);
        recorder = new ScanRecorder(this);

        buildUi();
        ensureCameraPermission();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        preview = new TextureView(this);
        root.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(Ui.dp(this,16), Ui.dp(this,18), Ui.dp(this,16), Ui.dp(this,12));
        top.setBackgroundColor(Color.argb(155, 0, 0, 0));
        top.addView(Ui.brandHeader(this,"ELEKTORYK • Diagnostic Fallback","Camera2 + IMU • НЕ створює 3D-план","DIAGNOSTIC",true));

        cameraInfo = Ui.muted(this, "Очікування Camera2…");
        poseInfo = Ui.muted(this, "Pose: —");
        top.addView(cameraInfo);
        top.addView(poseInfo, Ui.lpMatchWrap(this, 4));

        coverage = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        coverage.setMax(100);
        coverage.setProgress(0);
        top.addView(coverage, Ui.lpMatchWrap(this, 8));

        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        root.addView(top, tp);

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(Ui.dp(this,16), Ui.dp(this,16), Ui.dp(this,16), Ui.dp(this,24));
        overlay.setGravity(Gravity.BOTTOM);
        overlay.setBackgroundColor(Color.argb(175, 0, 0, 0));

        state = Ui.title(this, "Діагностична сесія готова", 19);
        quality = Ui.muted(this,
                "Цей режим перевіряє Camera2 + IMU та записує сесію руху. Він НЕ є заміною ARCore Depth і НЕ створює 3D-сцену/план.");

        action = Ui.button(this, "Почати діагностику");
        action.setOnClickListener(v -> toggleSession());

        pause = Ui.secondaryButton(this, "Пауза");
        pause.setEnabled(false);
        pause.setOnClickListener(v -> togglePause());

        overlay.addView(state);
        overlay.addView(quality, Ui.lpMatchWrap(this, 4));
        overlay.addView(action, Ui.lpMatchWrap(this, 12));
        overlay.addView(pause, Ui.lpMatchWrap(this, 8));
        overlay.addView(Ui.bottomNav(this,projectId,"SCAN"),Ui.lpMatchWrap(this,10));

        FrameLayout.LayoutParams op = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(overlay, op);

        setContentView(root);
    }

    private void toggleSession() {
        ScanSession s = engine.getSession();
        if (s.state == ScanSession.State.IDLE || s.state == ScanSession.State.READY
                || s.state == ScanSession.State.FAILED) {
            engine.start(projectId);
            recorder.start(engine.getSession());
            recorder.recordEvent("scan", "session_started");
            motionTracker.start();
            coverage.setProgress(0);
            action.setText("Завершити діагностику");
            pause.setText("Пауза");
            pause.setEnabled(true);
        } else if (s.state == ScanSession.State.SCANNING || s.state == ScanSession.State.PAUSED) {
            motionTracker.stop();
            recorder.recordEvent("scan", "user_finished");
            engine.stop();
            action.setText("Нова сесія");
            pause.setEnabled(false);
        }
    }

    private void togglePause() {
        ScanSession s = engine.getSession();
        if (s.state == ScanSession.State.SCANNING) {
            engine.pause();
            motionTracker.stop();
            recorder.recordEvent("scan", "paused");
            pause.setText("Продовжити");
        } else if (s.state == ScanSession.State.PAUSED) {
            engine.resume();
            motionTracker.resume();
            recorder.recordEvent("scan", "resumed");
            pause.setText("Пауза");
        }
    }

    private void ensureCameraPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        if (camera != null) return;
        camera = new CameraPreviewController(this, preview, this);
        camera.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                quality.setText("Без дозволу CAMERA сканування неможливе.");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && camera == null) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        ScanSession s = engine.getSession();
        if (s.state == ScanSession.State.SCANNING) {
            engine.pause();
            recorder.recordEvent("lifecycle", "activity_paused");
        }
        motionTracker.stop();

        if (camera != null) {
            camera.stop();
            camera = null;
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        recorder.close();
        super.onDestroy();
    }

    @Override
    public void onStateChanged(ScanSession session) {
        runOnUiThread(() -> {
            state.setText("Стан: " + session.state.name() + " • " + session.scannerProfile);
            if (session.state == ScanSession.State.READY || session.state == ScanSession.State.FAILED) {
                recorder.finish(session);
            }
            if (session.state == ScanSession.State.PAUSED) {
                pause.setText("Продовжити");
                pause.setEnabled(true);
            }
        });
    }

    @Override
    public void onQualityChanged(int percent, String message) {
        runOnUiThread(() -> quality.setText(message));
    }

    @Override
    public void onError(String message, Throwable error) {
        runOnUiThread(() -> quality.setText("Помилка: " + message));
        recorder.recordEvent("error", message);
    }

    @Override
    public void onCameraReady(String cameraId, android.util.Size previewSize) {
        String size = previewSize == null ? "?"
                : previewSize.getWidth() + "×" + previewSize.getHeight();
        runOnUiThread(() -> cameraInfo.setText("Camera2: id=" + cameraId + " • preview " + size));
        recorder.recordEvent("camera", "ready id=" + cameraId + " size=" + size);
    }

    @Override
    public void onCameraError(String message, Throwable error) {
        runOnUiThread(() -> cameraInfo.setText("Camera2: " + message));
        recorder.recordEvent("camera_error", message);
    }

    @Override
    public void onMotionQuality(int percent, String message) {
        ScanSession s = engine.getSession();
        if (s.state == ScanSession.State.SCANNING) {
            runOnUiThread(() -> {
                coverage.setProgress(percent);
                quality.setText("Покриття ракурсів ~" + percent + "% • " + message);
            });
        }
    }

    @Override
    public void onPoseSample(PoseSample sample) {
        ScanSession s = engine.getSession();
        if (s.state != ScanSession.State.SCANNING) return;

        recorder.recordPose(sample);
        runOnUiThread(() -> poseInfo.setText(
                String.format(java.util.Locale.US,
                        "yaw %.0f° • pitch %.0f° • roll %.0f°",
                        sample.yawDeg, sample.pitchDeg, sample.rollDeg)));
    }
}
