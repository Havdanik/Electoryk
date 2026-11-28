package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class ReviewIssue {
    public enum SuggestedType {
        TEMPORARY_OBJECT,
        STRUCTURAL_PROTRUSION,
        SCAN_ARTIFACT,
        UNKNOWN
    }

    public enum Decision {
        UNREVIEWED,
        KEEP_ARCHITECTURE,
        REMOVE_AS_OBJECT,
        REMOVE_AS_ARTIFACT
    }

    public String id = UUID.randomUUID().toString();
    public SuggestedType suggestedType = SuggestedType.UNKNOWN;
    public Decision decision = Decision.UNREVIEWED;
    public double confidence;
    public String reason = "";
    public int supportPoints;

    public double minX, maxX;
    public double minY, maxY;
    public double minZ, maxZ;

    public double widthM() { return maxX - minX; }
    public double heightM() { return maxY - minY; }
    public double depthM() { return maxZ - minZ; }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("suggestedType", suggestedType.name());
        o.put("decision", decision.name());
        o.put("confidence", confidence);
        o.put("reason", reason);
        o.put("supportPoints", supportPoints);
        o.put("minX", minX); o.put("maxX", maxX);
        o.put("minY", minY); o.put("maxY", maxY);
        o.put("minZ", minZ); o.put("maxZ", maxZ);
        return o;
    }

    public static ReviewIssue fromJson(JSONObject o) {
        ReviewIssue r = new ReviewIssue();
        if (o == null) return r;
        r.id = o.optString("id", r.id);
        try {
            r.suggestedType = SuggestedType.valueOf(
                    o.optString("suggestedType", SuggestedType.UNKNOWN.name()));
        } catch (Exception ignored) {}
        try {
            r.decision = Decision.valueOf(
                    o.optString("decision", Decision.UNREVIEWED.name()));
        } catch (Exception ignored) {}
        r.confidence = o.optDouble("confidence", 0);
        r.reason = o.optString("reason", "");
        r.supportPoints = o.optInt("supportPoints", 0);
        r.minX = o.optDouble("minX", 0); r.maxX = o.optDouble("maxX", 0);
        r.minY = o.optDouble("minY", 0); r.maxY = o.optDouble("maxY", 0);
        r.minZ = o.optDouble("minZ", 0); r.maxZ = o.optDouble("maxZ", 0);
        return r;
    }
}
