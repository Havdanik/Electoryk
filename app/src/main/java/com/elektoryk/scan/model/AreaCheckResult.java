package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

public class AreaCheckResult {
    public enum Status {
        NO_REFERENCE,
        MATCH,
        REVIEW,
        RESCAN_REQUIRED
    }

    public Status status = Status.NO_REFERENCE;
    public double referenceAreaM2;
    public double scannedAreaM2;
    public double deltaM2;
    public double deltaPercent;
    public double tolerancePercent;
    public double likelyMissingAreaM2;
    public String message = "";

    public boolean isAcceptable() { return status == Status.MATCH || status == Status.NO_REFERENCE; }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("status", status.name());
        o.put("referenceAreaM2", referenceAreaM2);
        o.put("scannedAreaM2", scannedAreaM2);
        o.put("deltaM2", deltaM2);
        o.put("deltaPercent", deltaPercent);
        o.put("tolerancePercent", tolerancePercent);
        o.put("likelyMissingAreaM2", likelyMissingAreaM2);
        o.put("message", message);
        return o;
    }

    public static AreaCheckResult fromJson(JSONObject o) {
        AreaCheckResult r = new AreaCheckResult();
        if (o == null) return r;
        try { r.status = Status.valueOf(o.optString("status", r.status.name())); } catch (Exception ignored) {}
        r.referenceAreaM2 = o.optDouble("referenceAreaM2", 0);
        r.scannedAreaM2 = o.optDouble("scannedAreaM2", 0);
        r.deltaM2 = o.optDouble("deltaM2", 0);
        r.deltaPercent = o.optDouble("deltaPercent", 0);
        r.tolerancePercent = o.optDouble("tolerancePercent", 0);
        r.likelyMissingAreaM2 = o.optDouble("likelyMissingAreaM2", 0);
        r.message = o.optString("message", "");
        return r;
    }
}
