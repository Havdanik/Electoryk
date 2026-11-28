package com.elektoryk.scan.scanner;

import org.json.JSONException;
import org.json.JSONObject;

public class PoseSample {
    public long timestampNs;
    public float yawDeg;
    public float pitchDeg;
    public float rollDeg;
    public float accelMagnitude;
    public float gyroMagnitude;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("timestampNs", timestampNs);
        o.put("yawDeg", yawDeg);
        o.put("pitchDeg", pitchDeg);
        o.put("rollDeg", rollDeg);
        o.put("accelMagnitude", accelMagnitude);
        o.put("gyroMagnitude", gyroMagnitude);
        return o;
    }
}
