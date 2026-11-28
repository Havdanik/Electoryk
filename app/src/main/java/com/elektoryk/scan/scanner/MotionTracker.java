package com.elektoryk.scan.scanner;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class MotionTracker implements SensorEventListener {
    public interface Listener {
        void onMotionQuality(int percent, String message);
        void onPoseSample(PoseSample sample);
    }

    private final SensorManager manager;
    private final Sensor gyro;
    private final Sensor accel;
    private final Sensor rotation;
    private final Listener listener;
    private final CoverageGrid coverage = new CoverageGrid();

    private float lastAccelMagnitude = 9.81f;
    private float gyroMagnitude;
    private float yawDeg;
    private float pitchDeg;
    private float rollDeg;
    private int accelSamples;
    private double shakeAccumulator;
    private long lastPoseEmitNs;
    private boolean running;
    // Rotation-vector events arrive at sensor rate. Reuse these buffers instead of allocating two
    // arrays on every event; this keeps motion guidance from contributing periodic young-gen GC.
    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];

    public MotionTracker(Context context, Listener listener) {
        this.listener = listener;
        manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        gyro = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        accel = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        rotation = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void start() {
        reset();
        register();
    }

    /** Resume after a user/lifecycle pause without losing accumulated coverage. */
    public void resume() {
        register();
    }

    private void register(){
        if(running||manager==null)return;
        if (gyro != null) manager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME);
        if (accel != null) manager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
        if (rotation != null) manager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_GAME);
        running=true;
    }

    public void stop() {
        if (manager != null && running) manager.unregisterListener(this);
        running=false;
    }

    private void reset() {
        coverage.reset();
        lastAccelMagnitude = 9.81f;
        gyroMagnitude = 0f;
        yawDeg = pitchDeg = rollDeg = 0f;
        accelSamples = 0;
        shakeAccumulator = 0.0;
        lastPoseEmitNs = 0L;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();

        if (type == Sensor.TYPE_GYROSCOPE) {
            gyroMagnitude = (float)Math.sqrt(
                    event.values[0]*event.values[0] +
                    event.values[1]*event.values[1] +
                    event.values[2]*event.values[2]);
        } else if (type == Sensor.TYPE_ACCELEROMETER) {
            float m = (float)Math.sqrt(
                    event.values[0]*event.values[0] +
                    event.values[1]*event.values[1] +
                    event.values[2]*event.values[2]);
            float d = m - lastAccelMagnitude;
            shakeAccumulator += d*d;
            accelSamples++;
            lastAccelMagnitude = m;
        } else if (type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientation);
            yawDeg = (float)Math.toDegrees(orientation[0]);
            pitchDeg = (float)Math.toDegrees(orientation[1]);
            rollDeg = (float)Math.toDegrees(orientation[2]);
            int c = coverage.add(yawDeg, pitchDeg);

            if (listener != null) {
                double shake = accelSamples > 0
                        ? Math.sqrt(shakeAccumulator / accelSamples) : 0.0;
                String msg;
                if (gyroMagnitude > 3.2f || shake > 2.8) {
                    msg = "Рух занадто різкий — веди телефон плавніше";
                } else if (c < 20) {
                    msg = "Покриття низьке — покажи більше стін і кутів";
                } else if (c < 55) {
                    msg = "Добре. Продовжуй обходити кімнату та перекривати ракурси";
                } else {
                    msg = "Покриття хороше — перевір кути, двері та зони за предметами";
                }
                listener.onMotionQuality(c, msg);
            }
        }

        if (listener != null && event.timestamp - lastPoseEmitNs > 100_000_000L) {
            lastPoseEmitNs = event.timestamp;
            PoseSample s = new PoseSample();
            s.timestampNs = event.timestamp;
            s.yawDeg = yawDeg;
            s.pitchDeg = pitchDeg;
            s.rollDeg = rollDeg;
            s.accelMagnitude = lastAccelMagnitude;
            s.gyroMagnitude = gyroMagnitude;
            listener.onPoseSample(s);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
