package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class RoomRect {
    public String id = UUID.randomUUID().toString();
    public String name = "Кімната";
    public double xM = 0.0;
    public double zM = 0.0;
    public double widthM = 4.0;
    public double lengthM = 4.0;
    public double heightM = 2.7;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("xM", xM);
        o.put("zM", zM);
        o.put("widthM", widthM);
        o.put("lengthM", lengthM);
        o.put("heightM", heightM);
        return o;
    }

    public static RoomRect fromJson(JSONObject o) {
        RoomRect r = new RoomRect();
        if (o == null) return r;
        r.id = o.optString("id", r.id);
        r.name = o.optString("name", "Кімната");
        r.xM = o.optDouble("xM", 0.0);
        r.zM = o.optDouble("zM", 0.0);
        r.widthM = Math.max(0.1, o.optDouble("widthM", 4.0));
        r.lengthM = Math.max(0.1, o.optDouble("lengthM", 4.0));
        r.heightM = Math.max(1.8, o.optDouble("heightM", 2.7));
        return r;
    }

    public boolean contains(double x, double z) {
        return x >= xM && x <= xM + widthM && z >= zM && z <= zM + lengthM;
    }
}
