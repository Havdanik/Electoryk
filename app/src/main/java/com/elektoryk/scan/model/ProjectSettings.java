package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

public class ProjectSettings {
    public enum WiringMethod {
        CEILING_VERTICAL_DROPS,
        WALLS,
        FLOOR,
        COMBINED
    }

    public enum JunctionMethod {
        DEEP_SOCKET_BOXES,
        JUNCTION_BOXES,
        LOOP,
        HOME_RUN,
        COMBINED
    }

    public enum CableProtection {
        NONE,
        CORRUGATED,
        TRAY,
        MIXED
    }

    public enum WallPassageMode {
        AUTO,
        ABOVE_DOOR,
        DIRECT_WALL
    }

    public int phases = 1;
    public int plannedGroups = 0;
    public double cableReservePercent = 10.0;
    public WiringMethod wiringMethod = WiringMethod.CEILING_VERTICAL_DROPS;
    public JunctionMethod junctionMethod = JunctionMethod.DEEP_SOCKET_BOXES;
    public CableProtection cableProtection = CableProtection.NONE;
    public WallPassageMode wallPassageMode = WallPassageMode.AUTO;

    public double panelReserveM = 1.5;
    public double endpointReserveM = 0.25;
    public double passageReserveM = 0.20;
    public double ceilingOffsetM = 0.15;
    public double fastenerSpacingM = 0.40;
    public boolean bundleSharedSegments = true;
    public int maxBundleCables = 12;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("phases", phases);
        o.put("plannedGroups", plannedGroups);
        o.put("cableReservePercent", cableReservePercent);
        o.put("wiringMethod", wiringMethod.name());
        o.put("junctionMethod", junctionMethod.name());
        o.put("cableProtection", cableProtection.name());
        o.put("wallPassageMode", wallPassageMode.name());
        o.put("panelReserveM", panelReserveM);
        o.put("endpointReserveM", endpointReserveM);
        o.put("passageReserveM", passageReserveM);
        o.put("ceilingOffsetM", ceilingOffsetM);
        o.put("fastenerSpacingM", fastenerSpacingM);
        o.put("bundleSharedSegments", bundleSharedSegments);
        o.put("maxBundleCables", maxBundleCables);
        return o;
    }

    public static ProjectSettings fromJson(JSONObject o) {
        ProjectSettings s = new ProjectSettings();
        if (o == null) return s;
        s.phases = o.optInt("phases", 1);
        s.plannedGroups = o.optInt("plannedGroups", 0);
        s.cableReservePercent = o.optDouble("cableReservePercent", 10.0);
        try {
            s.wiringMethod = WiringMethod.valueOf(
                    o.optString("wiringMethod", WiringMethod.CEILING_VERTICAL_DROPS.name()));
        } catch (Exception ignored) {}
        try {
            s.junctionMethod = JunctionMethod.valueOf(
                    o.optString("junctionMethod", JunctionMethod.DEEP_SOCKET_BOXES.name()));
        } catch (Exception ignored) {}
        try {
            s.cableProtection = CableProtection.valueOf(
                    o.optString("cableProtection", CableProtection.NONE.name()));
        } catch (Exception ignored) {}
        try {
            s.wallPassageMode = WallPassageMode.valueOf(
                    o.optString("wallPassageMode", WallPassageMode.AUTO.name()));
        } catch (Exception ignored) {}
        s.panelReserveM = o.optDouble("panelReserveM", 1.5);
        s.endpointReserveM = o.optDouble("endpointReserveM", 0.25);
        s.passageReserveM = o.optDouble("passageReserveM", 0.20);
        s.ceilingOffsetM = o.optDouble("ceilingOffsetM", 0.15);
        s.fastenerSpacingM = o.optDouble("fastenerSpacingM", 0.40);
        s.bundleSharedSegments = o.optBoolean("bundleSharedSegments", true);
        s.maxBundleCables = Math.max(1, o.optInt("maxBundleCables", 12));
        return s;
    }
}
