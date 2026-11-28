package com.elektoryk.scan.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RoomZone {
    public enum RoomType {
        UNKNOWN("Не визначено"),
        LIVING_ROOM("Вітальня"),
        KITCHEN("Кухня"),
        BEDROOM("Спальня"),
        BATHROOM("Санвузол"),
        HALL("Коридор"),
        WARDROBE("Гардероб"),
        BALCONY("Балкон"),
        TECHNICAL("Технічна"),
        OFFICE("Кабінет");

        public final String label;
        RoomType(String label) { this.label = label; }
    }

    public String id = UUID.randomUUID().toString();
    public String autoLabel = "";
    public RoomType type = RoomType.UNKNOWN;
    public double areaM2;
    public double centroidX;
    public double centroidZ;
    public double minX, maxX, minZ, maxZ;
    public double confidence;
    public boolean narrowPassageLike;

    // Interior-editor layers. Structural room geometry stays unchanged; these are visual/planning finishes.
    public String floorFinishPresetKey = "FLOOR_RAW";
    public int floorFinishColorArgb = 0;
    public String floorPatternKey = "";
    public double floorPatternScale = 1.0;
    public double floorPatternRotationDeg = 0.0;
    public String ceilingFinishPresetKey = "CEILING_RAW";
    public int ceilingFinishColorArgb = 0;
    public String ceilingPatternKey = "";
    public double ceilingPatternScale = 1.0;
    public String skirtingPresetKey = "SKIRT_NONE";

    public final List<PlanGridRun> floorRuns = new ArrayList<>();

    public String displayName() {
        if (type != RoomType.UNKNOWN) return type.label;
        return autoLabel == null || autoLabel.isEmpty() ? "Зона" : autoLabel;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("autoLabel", autoLabel);
        o.put("type", type.name());
        o.put("areaM2", areaM2);
        o.put("centroidX", centroidX);
        o.put("centroidZ", centroidZ);
        o.put("minX", minX); o.put("maxX", maxX);
        o.put("minZ", minZ); o.put("maxZ", maxZ);
        o.put("confidence", confidence);
        o.put("narrowPassageLike", narrowPassageLike);
        o.put("floorFinishPresetKey", floorFinishPresetKey);
        o.put("floorFinishColorArgb", floorFinishColorArgb);
        o.put("floorPatternKey", floorPatternKey);
        o.put("floorPatternScale", floorPatternScale);
        o.put("floorPatternRotationDeg", floorPatternRotationDeg);
        o.put("ceilingFinishPresetKey", ceilingFinishPresetKey);
        o.put("ceilingFinishColorArgb", ceilingFinishColorArgb);
        o.put("ceilingPatternKey", ceilingPatternKey);
        o.put("ceilingPatternScale", ceilingPatternScale);
        o.put("skirtingPresetKey", skirtingPresetKey);
        JSONArray a = new JSONArray();
        for (PlanGridRun r : floorRuns) a.put(r.toJson());
        o.put("floorRuns", a);
        return o;
    }

    public static RoomZone fromJson(JSONObject o) {
        if (o == null) return null;
        RoomZone r = new RoomZone();
        r.id = o.optString("id", r.id);
        r.autoLabel = o.optString("autoLabel", "");
        try { r.type = RoomType.valueOf(o.optString("type", RoomType.UNKNOWN.name())); }
        catch (Exception ignored) {}
        r.areaM2 = o.optDouble("areaM2", 0);
        r.centroidX = o.optDouble("centroidX", 0);
        r.centroidZ = o.optDouble("centroidZ", 0);
        r.minX = o.optDouble("minX", 0); r.maxX = o.optDouble("maxX", 0);
        r.minZ = o.optDouble("minZ", 0); r.maxZ = o.optDouble("maxZ", 0);
        r.confidence = o.optDouble("confidence", 0);
        r.narrowPassageLike = o.optBoolean("narrowPassageLike", false);
        r.floorFinishPresetKey = o.optString("floorFinishPresetKey", "FLOOR_RAW");
        r.floorFinishColorArgb = o.optInt("floorFinishColorArgb", 0);
        r.floorPatternKey = o.optString("floorPatternKey", "");
        r.floorPatternScale = clampScale(o.optDouble("floorPatternScale", 1.0));
        r.floorPatternRotationDeg = o.optDouble("floorPatternRotationDeg", 0.0);
        r.ceilingFinishPresetKey = o.optString("ceilingFinishPresetKey", "CEILING_RAW");
        r.ceilingFinishColorArgb = o.optInt("ceilingFinishColorArgb", 0);
        r.ceilingPatternKey = o.optString("ceilingPatternKey", "");
        r.ceilingPatternScale = clampScale(o.optDouble("ceilingPatternScale", 1.0));
        r.skirtingPresetKey = o.optString("skirtingPresetKey", "SKIRT_NONE");
        JSONArray a = o.optJSONArray("floorRuns");
        if (a != null) for (int i=0;i<a.length();i++) {
            PlanGridRun run = PlanGridRun.fromJson(a.optJSONObject(i));
            if (run != null) r.floorRuns.add(run);
        }
        return r;
    }

    private static double clampScale(double v){
        if(Double.isNaN(v)||Double.isInfinite(v)) return 1.0;
        return Math.max(0.20,Math.min(6.0,v));
    }
}
