package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** Lightweight optional interior object used by the digital-twin editor. */
public class FurnitureItem {
    public enum Type {
        CABINET, NIGHTSTAND, KITCHEN, WARDROBE, BED, SOFA, TABLE, CHAIR, DESK,
        TV, APPLIANCE, FRIDGE, WASHER, OVEN, SANITARY, SINK, TOILET, BATHTUB, SHOWER,
        RADIATOR, MIRROR, SHELF, CUSTOM
    }

    public String id = UUID.randomUUID().toString();
    public String roomId = "";
    public Type type = Type.CABINET;
    public String name = "";
    public String assetKey = "";
    /** Stable provenance for scanner-promoted objects. Empty for manually placed/catalog objects. */
    public String sourceSemanticId = "";
    public double x;
    public double y;
    public double z;
    public double widthM = 0.8;
    public double depthM = 0.45;
    public double heightM = 0.9;
    public double rotationDeg = 0.0;
    public boolean blocksElectricalPlacement = false;
    /** Interior editor metadata. */
    public boolean locked = false;
    public int colorArgb = 0;
    public String groupKey = "";
    /** FLOOR / WALL / CEILING. Most furniture is FLOOR; TV/mirror/shelf may be WALL. */
    public String placement = "FLOOR";

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("roomId", roomId);
        o.put("type", type.name());
        o.put("name", name);
        o.put("assetKey", assetKey);
        o.put("sourceSemanticId", sourceSemanticId);
        o.put("x", x); o.put("y", y); o.put("z", z);
        o.put("widthM", widthM); o.put("depthM", depthM); o.put("heightM", heightM);
        o.put("rotationDeg", rotationDeg);
        o.put("blocksElectricalPlacement", blocksElectricalPlacement);
        o.put("locked", locked);
        o.put("colorArgb", colorArgb);
        o.put("groupKey", groupKey);
        o.put("placement", placement);
        return o;
    }

    public static FurnitureItem fromJson(JSONObject o) {
        if (o == null) return null;
        FurnitureItem f = new FurnitureItem();
        f.id = o.optString("id", f.id);
        f.roomId = o.optString("roomId", "");
        try { f.type = Type.valueOf(o.optString("type", Type.CABINET.name())); } catch (Exception ignored) {}
        f.name = o.optString("name", "");
        f.assetKey = o.optString("assetKey", "");
        f.sourceSemanticId = o.optString("sourceSemanticId", "");
        f.x = o.optDouble("x", 0); f.y = o.optDouble("y", 0); f.z = o.optDouble("z", 0);
        f.widthM = Math.max(0.1, o.optDouble("widthM", 0.8));
        f.depthM = Math.max(0.1, o.optDouble("depthM", 0.45));
        f.heightM = Math.max(0.1, o.optDouble("heightM", 0.9));
        f.rotationDeg = o.optDouble("rotationDeg", 0.0);
        f.blocksElectricalPlacement = o.optBoolean("blocksElectricalPlacement", false);
        f.locked = o.optBoolean("locked", false);
        f.colorArgb = o.optInt("colorArgb", 0);
        f.groupKey = o.optString("groupKey", "");
        f.placement = o.optString("placement", "FLOOR");
        return f;
    }
}
