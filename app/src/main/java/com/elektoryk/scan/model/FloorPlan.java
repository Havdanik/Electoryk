package com.elektoryk.scan.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FloorPlan {
    public String projectId = "";
    public final List<RoomRect> rooms = new ArrayList<>();
    public final List<ElectricalPoint> points = new ArrayList<>();
    public double scaleCorrection = 1.0;
    public long updatedAt = System.currentTimeMillis();

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("projectId", projectId);
        o.put("scaleCorrection", scaleCorrection);
        o.put("updatedAt", updatedAt);

        JSONArray rs = new JSONArray();
        for (RoomRect r : rooms) rs.put(r.toJson());
        o.put("rooms", rs);

        JSONArray ps = new JSONArray();
        for (ElectricalPoint p : points) ps.put(p.toJson());
        o.put("points", ps);
        return o;
    }

    public static FloorPlan fromJson(JSONObject o) {
        FloorPlan f = new FloorPlan();
        if (o == null) return f;
        f.projectId = o.optString("projectId", "");
        f.scaleCorrection = o.optDouble("scaleCorrection", 1.0);
        f.updatedAt = o.optLong("updatedAt", System.currentTimeMillis());

        JSONArray rs = o.optJSONArray("rooms");
        if (rs != null) {
            for (int i = 0; i < rs.length(); i++) {
                JSONObject r = rs.optJSONObject(i);
                if (r != null) f.rooms.add(RoomRect.fromJson(r));
            }
        }

        JSONArray ps = o.optJSONArray("points");
        if (ps != null) {
            for (int i = 0; i < ps.length(); i++) {
                JSONObject p = ps.optJSONObject(i);
                if (p != null) f.points.add(ElectricalPoint.fromJson(p));
            }
        }
        return f;
    }
}
