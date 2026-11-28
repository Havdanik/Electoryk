package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class SurfaceObservation {
    public enum Kind {
        UNKNOWN,
        WALL,
        FLOOR,
        CEILING,
        OPENING,
        STRUCTURAL_PROTRUSION,
        TEMPORARY_OBJECT,
        SCAN_ARTIFACT
    }

    public String id = UUID.randomUUID().toString();
    public Vec3 normal = new Vec3();
    public Vec3 center = new Vec3();
    public double planeDistanceM;
    public double widthM;
    public double heightM;
    public double protrusionDepthM;
    public double confidence;
    public boolean vertical;
    public boolean horizontal;
    public Kind kind = Kind.UNKNOWN;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("kind", kind.name());
        o.put("nx", normal.x); o.put("ny", normal.y); o.put("nz", normal.z);
        o.put("cx", center.x); o.put("cy", center.y); o.put("cz", center.z);
        o.put("planeDistanceM", planeDistanceM);
        o.put("widthM", widthM);
        o.put("heightM", heightM);
        o.put("protrusionDepthM", protrusionDepthM);
        o.put("confidence", confidence);
        o.put("vertical", vertical);
        o.put("horizontal", horizontal);
        return o;
    }

    public static SurfaceObservation fromJson(JSONObject o) {
        SurfaceObservation s = new SurfaceObservation();
        if (o == null) return s;
        s.id = o.optString("id", s.id);
        try { s.kind = Kind.valueOf(o.optString("kind", Kind.UNKNOWN.name())); }
        catch (Exception ignored) {}
        s.normal = new Vec3(o.optDouble("nx",0), o.optDouble("ny",0), o.optDouble("nz",0));
        s.center = new Vec3(o.optDouble("cx",0), o.optDouble("cy",0), o.optDouble("cz",0));
        s.planeDistanceM = o.optDouble("planeDistanceM", 0);
        s.widthM = o.optDouble("widthM", 0);
        s.heightM = o.optDouble("heightM", 0);
        s.protrusionDepthM = o.optDouble("protrusionDepthM", 0);
        s.confidence = o.optDouble("confidence", 0);
        s.vertical = o.optBoolean("vertical", false);
        s.horizontal = o.optBoolean("horizontal", false);
        return s;
    }
}
