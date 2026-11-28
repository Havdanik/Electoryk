package com.elektoryk.scan.scanner.pipeline;

import com.elektoryk.scan.device.DeviceCapabilities;
import com.elektoryk.scan.device.ScannerProfile;

public final class ScannerBackendSelector {
    private ScannerBackendSelector() {}

    public enum Backend {
        ARCORE_DEPTH,
        CAMERA2_DEPTH,
        CAMERA_IMU,
        NONE
    }

    public static Backend select(DeviceCapabilities c) {
        ScannerProfile p = ScannerProfile.choose(c);
        switch (p.mode) {
            case CAMERA2_DEPTH_OUTPUT:
                return Backend.CAMERA2_DEPTH;
            case ARCORE_DEPTH_CANDIDATE:
                return Backend.ARCORE_DEPTH;
            case CAMERA_IMU:
                return Backend.CAMERA_IMU;
            default:
                return Backend.NONE;
        }
    }
}
