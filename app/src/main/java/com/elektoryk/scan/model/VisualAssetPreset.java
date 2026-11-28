package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

/** Lightweight client-facing asset preset. Technical electrical meaning is stored separately. */
public class VisualAssetPreset {
    public String id="";
    public String label="";
    /** ELECTRICAL or FURNITURE. */
    public String category="ELECTRICAL";
    public String pointType="SOCKET";
    public String furnitureType="";
    /** WALL / CEILING / FLOOR. */
    public String placement="WALL";
    public double widthM=.08,depthM=.03,heightM=.08;
    public boolean builtIn=false;
    /** Search/filter metadata for the in-app library. */
    public String group="Інше";
    public String tags="";
    public String styleKey="BASIC";
    public double defaultMountHeightM=0.0;
    public double snapStepM=0.05;
    public int colorArgb=0;

    public JSONObject toJson() throws JSONException {
        JSONObject o=new JSONObject();
        o.put("id",id);o.put("label",label);o.put("category",category);
        o.put("pointType",pointType);o.put("furnitureType",furnitureType);o.put("placement",placement);
        o.put("widthM",widthM);o.put("depthM",depthM);o.put("heightM",heightM);o.put("builtIn",builtIn);
        o.put("group",group);o.put("tags",tags);o.put("styleKey",styleKey);o.put("defaultMountHeightM",defaultMountHeightM);o.put("snapStepM",snapStepM);o.put("colorArgb",colorArgb);
        return o;
    }

    public static VisualAssetPreset fromJson(JSONObject o){
        if(o==null)return null;
        VisualAssetPreset a=new VisualAssetPreset();
        a.id=o.optString("id","");a.label=o.optString("label",a.id);a.category=o.optString("category","ELECTRICAL");
        a.pointType=o.optString("pointType","SOCKET");a.furnitureType=o.optString("furnitureType","");a.placement=o.optString("placement","WALL");
        a.widthM=o.optDouble("widthM",.08);a.depthM=o.optDouble("depthM",.03);a.heightM=o.optDouble("heightM",.08);a.builtIn=o.optBoolean("builtIn",false);
        a.group=o.optString("group","Інше");a.tags=o.optString("tags","");a.styleKey=o.optString("styleKey","BASIC");
        a.defaultMountHeightM=o.optDouble("defaultMountHeightM",0.0);a.snapStepM=o.optDouble("snapStepM",0.05);a.colorArgb=o.optInt("colorArgb",0);
        return a;
    }
}
