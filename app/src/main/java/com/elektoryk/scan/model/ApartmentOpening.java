package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class ApartmentOpening {
    public enum Type { DOOR, WINDOW, PASSAGE, UNKNOWN }
    public enum Orientation { HORIZONTAL_X, VERTICAL_Z }

    public String id = UUID.randomUUID().toString();
    public Type type = Type.UNKNOWN;
    public Orientation orientation = Orientation.HORIZONTAL_X;

    public double x1, z1, x2, z2;
    public double bottomY;
    public double topY;
    public double widthM;
    public double heightM;
    public double confidence;

    public String roomAId = "";
    public String roomBId = "";
    public boolean external;
    public boolean crossedByScanPath;
    public String wallId = "";
    public boolean manualEdited;

    public double centerX() { return (x1+x2)/2.0; }
    public double centerZ() { return (z1+z2)/2.0; }
    public boolean isWalkable(){ return type==Type.DOOR || type==Type.PASSAGE; }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("type", type.name());
        o.put("orientation", orientation.name());
        o.put("x1", x1); o.put("z1", z1);
        o.put("x2", x2); o.put("z2", z2);
        o.put("bottomY", bottomY); o.put("topY", topY);
        o.put("widthM", widthM); o.put("heightM", heightM);
        o.put("confidence", confidence);
        o.put("roomAId", roomAId); o.put("roomBId", roomBId);
        o.put("external", external);
        o.put("crossedByScanPath", crossedByScanPath);
        o.put("wallId", wallId);
        o.put("manualEdited", manualEdited);
        return o;
    }

    public static ApartmentOpening fromJson(JSONObject o) {
        if (o == null) return null;
        ApartmentOpening a = new ApartmentOpening();
        a.id = o.optString("id", a.id);
        try { a.type = Type.valueOf(o.optString("type", Type.UNKNOWN.name())); } catch (Exception ignored) {}
        try { a.orientation = Orientation.valueOf(o.optString("orientation", Orientation.HORIZONTAL_X.name())); } catch (Exception ignored) {}
        a.x1=o.optDouble("x1",0); a.z1=o.optDouble("z1",0);
        a.x2=o.optDouble("x2",0); a.z2=o.optDouble("z2",0);
        a.bottomY=o.optDouble("bottomY",0); a.topY=o.optDouble("topY",0);
        a.widthM=o.optDouble("widthM",0); a.heightM=o.optDouble("heightM",0);
        a.confidence=o.optDouble("confidence",0);
        a.roomAId=o.optString("roomAId",""); a.roomBId=o.optString("roomBId","");
        a.external=o.optBoolean("external",false);
        a.crossedByScanPath=o.optBoolean("crossedByScanPath",false);
        a.wallId=o.optString("wallId","");
        a.manualEdited=o.optBoolean("manualEdited",false);
        return a;
    }
}
