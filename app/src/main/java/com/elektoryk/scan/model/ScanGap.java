package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class ScanGap {
    public String id = UUID.randomUUID().toString();
    public double minX, maxX, minZ, maxZ;
    public double estimatedAreaM2;
    public int emptyCells;
    public double nearestPathDistanceM;
    public String hint = "";

    public double centerX() { return (minX + maxX) * 0.5; }
    public double centerZ() { return (minZ + maxZ) * 0.5; }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("minX", minX); o.put("maxX", maxX);
        o.put("minZ", minZ); o.put("maxZ", maxZ);
        o.put("estimatedAreaM2", estimatedAreaM2);
        o.put("emptyCells", emptyCells);
        o.put("nearestPathDistanceM", nearestPathDistanceM);
        o.put("hint", hint);
        return o;
    }

    public static ScanGap fromJson(JSONObject o) {
        ScanGap g = new ScanGap();
        if (o == null) return g;
        g.id = o.optString("id", g.id);
        g.minX = o.optDouble("minX", 0); g.maxX = o.optDouble("maxX", 0);
        g.minZ = o.optDouble("minZ", 0); g.maxZ = o.optDouble("maxZ", 0);
        g.estimatedAreaM2 = o.optDouble("estimatedAreaM2", 0);
        g.emptyCells = o.optInt("emptyCells", 0);
        g.nearestPathDistanceM = o.optDouble("nearestPathDistanceM", 0);
        g.hint = o.optString("hint", "");
        return g;
    }
}
