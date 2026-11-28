package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

public class RoomBounds {
    public double minX;
    public double maxX;
    public double floorY;
    public double ceilingY;
    public double minZ;
    public double maxZ;
    public double confidence;

    public double widthM() { return Math.max(0, maxX - minX); }
    public double lengthM() { return Math.max(0, maxZ - minZ); }
    public double heightM() { return Math.max(0, ceilingY - floorY); }

    public boolean isUsable() {
        return widthM() > 0.7 && lengthM() > 0.7 && heightM() > 1.8;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("minX", minX);
        o.put("maxX", maxX);
        o.put("floorY", floorY);
        o.put("ceilingY", ceilingY);
        o.put("minZ", minZ);
        o.put("maxZ", maxZ);
        o.put("confidence", confidence);
        return o;
    }

    public static RoomBounds fromJson(JSONObject o) {
        RoomBounds b = new RoomBounds();
        if (o == null) return b;
        b.minX = o.optDouble("minX", 0);
        b.maxX = o.optDouble("maxX", 0);
        b.floorY = o.optDouble("floorY", 0);
        b.ceilingY = o.optDouble("ceilingY", 0);
        b.minZ = o.optDouble("minZ", 0);
        b.maxZ = o.optDouble("maxZ", 0);
        b.confidence = o.optDouble("confidence", 0);
        return b;
    }
}
