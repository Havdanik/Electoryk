package com.elektoryk.scan.device;

public class ScannerProfile {
    public enum Mode {
        CAMERA2_DEPTH_OUTPUT,
        ARCORE_DEPTH_CANDIDATE,
        CAMERA_IMU,
        UNSUPPORTED
    }

    public Mode mode;
    public String title;
    public String description;

    public static ScannerProfile choose(DeviceCapabilities c) {
        ScannerProfile p = new ScannerProfile();

        if (!c.hasCamera || !c.hasAccelerometer) {
            p.mode = Mode.UNSUPPORTED;
            p.title = "Недостатньо сенсорів";
            p.description = "Потрібна камера та базові датчики руху.";
            return p;
        }

        if (c.hasCamera2DepthOutput) {
            p.mode = Mode.CAMERA2_DEPTH_OUTPUT;
            p.title = "Camera2 + Depth Output";
            p.description = "Android Camera2 експонує depth output. Тип depth-сенсора уточнюється окремою діагностикою; камера й IMU використовуються разом.";
            return p;
        }

        if (c.hasArCorePackage) {
            p.mode = Mode.ARCORE_DEPTH_CANDIDATE;
            p.title = "Camera + AR depth";
            p.description = "Профіль для ARCore Depth. Підтримка API перевірятиметься ARCore-адаптером.";
            return p;
        }

        p.mode = Mode.CAMERA_IMU;
        p.title = "Camera + IMU";
        p.description = "Базовий профіль без depth-сенсора. Потрібна фотограмметрія/AR-модуль.";
        return p;
    }
}
