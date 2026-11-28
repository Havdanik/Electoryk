package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class ElectricalPoint {
    public enum Type {
        SOCKET,
        SWITCH,
        LIGHT,
        PANEL,
        TV,
        ETHERNET,
        APPLIANCE,
        SENSOR,
        CAMERA,
        SMART_HOME
    }

    public String id = UUID.randomUUID().toString();
    public String roomId = "";
    public Type type = Type.SOCKET;
    public Vec3 position = new Vec3();
    public double heightFromFinishedFloorM = 0.3;
    public String groupId = "";
    public int circuitNo = 0;
    public int moduleCount = 1;
    public String wallId = "";
    public String cableKey = "";
    public double plannedLoadW = 0.0;
    public boolean manualPosition = false;
    // Stable wall-local coordinates used by 3D/2D/wall-elevation editors.
    public boolean lockedToWall = false;
    public double wallFraction = -1.0;
    public double wallOffsetM = -1.0;
    public String placementSource = "";
    // Visual/client-facing variant. Technical type stays stable for routing and estimates.
    public String assetKey = "";
    public String clientLabel = "";
    public String note = "";

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("roomId", roomId);
        o.put("type", type.name());
        o.put("x", position.x);
        o.put("y", position.y);
        o.put("z", position.z);
        o.put("heightFromFinishedFloorM", heightFromFinishedFloorM);
        o.put("groupId", groupId);
        o.put("circuitNo", circuitNo);
        o.put("moduleCount", moduleCount);
        o.put("wallId", wallId);
        o.put("cableKey", cableKey);
        o.put("plannedLoadW", plannedLoadW);
        o.put("manualPosition", manualPosition);
        o.put("lockedToWall", lockedToWall);
        o.put("wallFraction", wallFraction);
        o.put("wallOffsetM", wallOffsetM);
        o.put("placementSource", placementSource);
        o.put("assetKey", assetKey);
        o.put("clientLabel", clientLabel);
        o.put("note", note);
        return o;
    }

    public static ElectricalPoint fromJson(JSONObject o) {
        ElectricalPoint p = new ElectricalPoint();
        if (o == null) return p;
        p.id = o.optString("id", p.id);
        p.roomId = o.optString("roomId", "");
        try {
            p.type = Type.valueOf(o.optString("type", Type.SOCKET.name()));
        } catch (Exception ignored) {}
        p.position = new Vec3(
                o.optDouble("x", 0.0),
                o.optDouble("y", 0.0),
                o.optDouble("z", 0.0));
        p.heightFromFinishedFloorM = o.optDouble("heightFromFinishedFloorM", 0.3);
        p.groupId = o.optString("groupId", "");
        p.circuitNo = o.optInt("circuitNo", 0);
        p.moduleCount = Math.max(1, o.optInt("moduleCount", 1));
        p.wallId = o.optString("wallId", "");
        p.cableKey = o.optString("cableKey", "");
        p.plannedLoadW = o.optDouble("plannedLoadW", 0.0);
        p.manualPosition = o.optBoolean("manualPosition", false);
        p.lockedToWall = o.optBoolean("lockedToWall", !p.wallId.isEmpty());
        p.wallFraction = o.optDouble("wallFraction", -1.0);
        p.wallOffsetM = o.optDouble("wallOffsetM", -1.0);
        p.placementSource = o.optString("placementSource", "");
        p.assetKey = o.optString("assetKey", "");
        p.clientLabel = o.optString("clientLabel", "");
        p.note = o.optString("note", "");
        return p;
    }
}
