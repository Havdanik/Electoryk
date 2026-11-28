package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

public class ProjectProfile {
    public enum ObjectType {
        APARTMENT("Квартира"),
        HOUSE("Будинок"),
        OFFICE("Офіс"),
        SHOP("Магазин"),
        COMMERCIAL("Комерція"),
        GARAGE("Гараж"),
        OTHER("Інше");

        public final String label;
        ObjectType(String label) { this.label = label; }
    }

    public enum AreaReference {
        NET_FLOOR("Чиста площа підлоги"),
        DOCUMENT_TOTAL("Загальна за документами"),
        APPROXIMATE("Приблизна (старий профіль)"),
        UNKNOWN("Не знаю");

        public final String label;
        AreaReference(String label) { this.label = label; }
    }

    public enum AreaAccuracy {
        EXACT("Точна"),
        APPROXIMATE("Приблизна"),
        UNKNOWN("Не знаю");
        public final String label;
        AreaAccuracy(String label) { this.label = label; }
    }

    public enum WallMaterial {
        MIXED("Змішані"),
        CONCRETE("Бетон"),
        BRICK("Цегла"),
        AERATED_CONCRETE("Газоблок"),
        DRYWALL("Гіпсокартон"),
        UNKNOWN("Не знаю");

        public final String label;
        WallMaterial(String label) { this.label = label; }
    }

    public ObjectType objectType = ObjectType.APARTMENT;
    public AreaReference areaReference = AreaReference.NET_FLOOR;
    public AreaAccuracy areaAccuracy = AreaAccuracy.APPROXIMATE;
    public WallMaterial wallMaterial = WallMaterial.MIXED;
    public double areaTolerancePercent = 12.0;
    public boolean autoAreaTolerance = true;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("objectType", objectType.name());
        o.put("areaReference", areaReference.name());
        o.put("areaAccuracy", areaAccuracy.name());
        o.put("wallMaterial", wallMaterial.name());
        o.put("areaTolerancePercent", areaTolerancePercent);
        o.put("autoAreaTolerance", autoAreaTolerance);
        return o;
    }

    public static ProjectProfile fromJson(JSONObject o) {
        ProjectProfile p = new ProjectProfile();
        if (o == null) return p;
        try { p.objectType = ObjectType.valueOf(o.optString("objectType", p.objectType.name())); } catch (Exception ignored) {}
        try { p.areaReference = AreaReference.valueOf(o.optString("areaReference", p.areaReference.name())); } catch (Exception ignored) {}
        try { p.areaAccuracy = AreaAccuracy.valueOf(o.optString("areaAccuracy", p.areaAccuracy.name())); } catch (Exception ignored) {}
        // Backward compatibility with v0.3/v0.4 early profile where APPROXIMATE was the source itself.
        if (p.areaReference == AreaReference.APPROXIMATE) {
            p.areaReference = AreaReference.NET_FLOOR;
            p.areaAccuracy = AreaAccuracy.APPROXIMATE;
        }
        try { p.wallMaterial = WallMaterial.valueOf(o.optString("wallMaterial", p.wallMaterial.name())); } catch (Exception ignored) {}
        p.autoAreaTolerance = o.optBoolean("autoAreaTolerance", true);
        p.areaTolerancePercent = o.optDouble("areaTolerancePercent", p.recommendedTolerancePercent());
        if (p.autoAreaTolerance) p.areaTolerancePercent = p.recommendedTolerancePercent();
        return p;
    }

    public double recommendedTolerancePercent() {
        if (areaReference == AreaReference.UNKNOWN || areaAccuracy == AreaAccuracy.UNKNOWN) return 0.0;
        if (areaReference == AreaReference.DOCUMENT_TOTAL)
            return areaAccuracy == AreaAccuracy.EXACT ? 12.0 : 18.0;
        return areaAccuracy == AreaAccuracy.EXACT ? 5.0 : 12.0;
    }

    public void applyReferenceDefaults() {
        if (autoAreaTolerance) areaTolerancePercent = recommendedTolerancePercent();
    }
}
