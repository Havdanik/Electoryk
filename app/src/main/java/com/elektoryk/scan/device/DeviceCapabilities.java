package com.elektoryk.scan.device;

public class DeviceCapabilities {
    public String manufacturer = "";
    public String model = "";
    public String androidVersion = "";

    public boolean hasCamera;
    public boolean hasGyroscope;
    public boolean hasAccelerometer;
    public boolean hasRotationVector;
    public boolean hasCamera2DepthOutput;
    public boolean hasArCorePackage;
    public boolean hasGooglePlayServices;

    public String cameraHardwareSummary = "";
}
