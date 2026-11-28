package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class StructuralFeature {
    public enum Type { COLUMN, NICHE, PROTRUSION, UNKNOWN }

    public String id = UUID.randomUUID().toString();
    public Type type = Type.UNKNOWN;
    public String roomId = "";
    public double minX, maxX, minZ, maxZ;
    public double minY, maxY;
    public double confidence;
    public String reason = "";

    public double widthM(){ return Math.max(0,maxX-minX); }
    public double depthM(){ return Math.max(0,maxZ-minZ); }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("type", type.name());
        o.put("roomId", roomId);
        o.put("minX",minX); o.put("maxX",maxX);
        o.put("minZ",minZ); o.put("maxZ",maxZ);
        o.put("minY",minY); o.put("maxY",maxY);
        o.put("confidence",confidence);
        o.put("reason",reason);
        return o;
    }

    public static StructuralFeature fromJson(JSONObject o) {
        if (o == null) return null;
        StructuralFeature f = new StructuralFeature();
        f.id=o.optString("id",f.id);
        try { f.type=Type.valueOf(o.optString("type",Type.UNKNOWN.name())); } catch(Exception ignored){}
        f.roomId=o.optString("roomId","");
        f.minX=o.optDouble("minX",0); f.maxX=o.optDouble("maxX",0);
        f.minZ=o.optDouble("minZ",0); f.maxZ=o.optDouble("maxZ",0);
        f.minY=o.optDouble("minY",0); f.maxY=o.optDouble("maxY",0);
        f.confidence=o.optDouble("confidence",0);
        f.reason=o.optString("reason","");
        return f;
    }
}
