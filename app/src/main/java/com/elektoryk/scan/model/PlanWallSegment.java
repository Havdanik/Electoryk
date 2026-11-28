package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class PlanWallSegment {
    public String id = UUID.randomUUID().toString();
    public double x1, z1, x2, z2;
    public double thicknessM = 0.12;
    public double confidence = 0.5;
    public boolean manual;
    public String materialKey = "UNKNOWN";
    /** What physically exists at scan time; independent from planned decorative finish. */
    public String surfaceStateKey = "AS_SCANNED";
    // Client-facing finish layer. It does not destroy the scanned structural geometry.
    public String finishKey = "RAW";
    public double finishThicknessM = 0.0;
    /** Built-in finish/material preset used by the 3D editor. */
    public String finishPresetKey = "RAW_SCAN";
    /** Optional override color (ARGB). 0 means derive from preset/state. */
    public int finishColorArgb = 0;
    /** Procedural pattern key: NONE/WALLPAPER/TILE/BRICK/WOOD/CONCRETE. */
    public String finishPatternKey = "";
    /** Pattern controls used by the 3D editor; independent from structural wall coordinates. */
    public double finishPatternScale = 1.0;
    public double finishPatternRotationDeg = 0.0;
    public double finishPatternOffsetXM = 0.0;
    public double finishPatternOffsetYM = 0.0;
    // Baked photo texture captured during Smart Scan. Stored relative to app files/scenes/<project>/textures.
    public String scannedTextureFile = "";
    public double scannedTextureCoverage = 0.0;

    public double lengthM() { return Math.hypot(x2-x1, z2-z1); }
    public double centerX(){ return (x1+x2)/2.0; }
    public double centerZ(){ return (z1+z2)/2.0; }

    public JSONObject toJson() throws JSONException {
        JSONObject o=new JSONObject();
        o.put("id",id);
        o.put("x1",x1); o.put("z1",z1); o.put("x2",x2); o.put("z2",z2);
        o.put("thicknessM",thicknessM);
        o.put("confidence",confidence);
        o.put("manual",manual);
        o.put("materialKey",materialKey);
        o.put("surfaceStateKey",surfaceStateKey);
        o.put("finishKey",finishKey);
        o.put("finishThicknessM",finishThicknessM);
        o.put("finishPresetKey",finishPresetKey);
        o.put("finishColorArgb",finishColorArgb);
        o.put("finishPatternKey",finishPatternKey);
        o.put("finishPatternScale",finishPatternScale);
        o.put("finishPatternRotationDeg",finishPatternRotationDeg);
        o.put("finishPatternOffsetXM",finishPatternOffsetXM);
        o.put("finishPatternOffsetYM",finishPatternOffsetYM);
        o.put("scannedTextureFile",scannedTextureFile);
        o.put("scannedTextureCoverage",scannedTextureCoverage);
        return o;
    }

    public static PlanWallSegment fromJson(JSONObject o){
        if(o==null)return null;
        PlanWallSegment w=new PlanWallSegment();
        w.id=o.optString("id",w.id);
        w.x1=o.optDouble("x1",0); w.z1=o.optDouble("z1",0);
        w.x2=o.optDouble("x2",0); w.z2=o.optDouble("z2",0);
        w.thicknessM=o.optDouble("thicknessM",0.12);
        w.confidence=o.optDouble("confidence",0.5);
        w.manual=o.optBoolean("manual",false);
        w.materialKey=o.optString("materialKey","UNKNOWN");
        w.surfaceStateKey=o.optString("surfaceStateKey","AS_SCANNED");
        w.finishKey=o.optString("finishKey","RAW");
        w.finishThicknessM=Math.max(0,o.optDouble("finishThicknessM",0.0));
        w.finishPresetKey=o.optString("finishPresetKey", "RAW_SCAN");
        w.finishColorArgb=o.optInt("finishColorArgb",0);
        w.finishPatternKey=o.optString("finishPatternKey","");
        w.finishPatternScale=clampScale(o.optDouble("finishPatternScale",1.0));
        w.finishPatternRotationDeg=o.optDouble("finishPatternRotationDeg",0.0);
        w.finishPatternOffsetXM=o.optDouble("finishPatternOffsetXM",0.0);
        w.finishPatternOffsetYM=o.optDouble("finishPatternOffsetYM",0.0);
        w.scannedTextureFile=o.optString("scannedTextureFile","");
        w.scannedTextureCoverage=Math.max(0,Math.min(1,o.optDouble("scannedTextureCoverage",0.0)));
        return w;
    }

    private static double clampScale(double v){
        if(Double.isNaN(v)||Double.isInfinite(v))return 1.0;
        return Math.max(0.20,Math.min(8.0,v));
    }
}
