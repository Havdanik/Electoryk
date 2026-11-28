package com.elektoryk.scan.device;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;

public class DeviceCapabilityDetector {

    public DeviceCapabilities detect(Context context) {
        DeviceCapabilities out = new DeviceCapabilities();
        out.manufacturer = Build.MANUFACTURER;
        out.model = Build.MODEL;
        out.androidVersion = Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";

        PackageManager pm = context.getPackageManager();
        out.hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        out.hasGooglePlayServices = isPackageInstalled(pm, "com.google.android.gms");
        out.hasArCorePackage = isPackageInstalled(pm, "com.google.ar.core");

        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sm != null) {
            out.hasGyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null;
            out.hasAccelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null;
            out.hasRotationVector = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null;
        }

        detectCameraDepth(context, out);
        return out;
    }

    private void detectCameraDepth(Context context, DeviceCapabilities out) {
        StringBuilder summary = new StringBuilder();
        try {
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return;

            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(id);
                int[] caps = cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                boolean depth = false;
                if (caps != null) {
                    for (int cap : caps) {
                        if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) {
                            depth = true;
                            out.hasCamera2DepthOutput = true;
                        }
                    }
                }

                Integer level = cc.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                summary.append("Camera ").append(id)
                        .append(": depth=").append(depth)
                        .append(", level=").append(level == null ? "?" : level)
                        .append("\n");
            }
        } catch (Exception e) {
            summary.append("Camera2 diagnostic error: ").append(e.getClass().getSimpleName());
        }
        out.cameraHardwareSummary = summary.toString().trim();
    }

    private boolean isPackageInstalled(PackageManager pm, String packageName) {
        try {
            pm.getPackageInfo(packageName, 0);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
