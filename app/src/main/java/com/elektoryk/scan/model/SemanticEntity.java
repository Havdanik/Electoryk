package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** Persistent object/surface recognized by RGB semantics and located by Depth. */
public class SemanticEntity {
    public enum Type {
        WALL,FLOOR,CEILING,DOOR,WINDOW,PASSAGE,MIRROR,
        CABINET,DRAWERS,WARDROBE,TABLE,DESK,CHAIR,ARMCHAIR,SOFA,SHELF,BED,CURTAIN,
        COLUMN,SINK,REFRIGERATOR,TOILET,SHOWER,BATHTUB,STAIRS,LAMP,COMPUTER,TELEVISION,WASHER,OVEN,MICROWAVE,STOVE,DISHWASHER,RADIATOR,BOOKCASE,BENCH,STOOL,
        BICYCLE,PERSON,OBJECT,UNKNOWN
    }
    public String id=UUID.randomUUID().toString();
    public Type type=Type.UNKNOWN;
    public String label="";
    public double minX,maxX,minY,maxY,minZ,maxZ;
    public double confidence;
    public int observations;
    public int supportPoints;
    public long lastSeenNs;
    public boolean visualOnly;
    public String parentWallId="";
    /** Dominant horizontal orientation learned from fused Depth support; 180-degree symmetric. */
    public double rotationDeg;
    /** Robust dimensions in the entity's own horizontal frame. Zero means legacy/world-AABB fallback. */
    public double orientedWidthM,orientedDepthM;
    /** Robust fused centroid. Unlike the historical union-AABB center it may move back after a bad/partial view. */
    public double fusedCenterX,fusedCenterY,fusedCenterZ;
    public boolean fusedCenterValid;

    public double widthM(){return orientedWidthM>0.01?orientedWidthM:Math.max(0,maxX-minX);}
    public double heightM(){return Math.max(0,maxY-minY);}
    public double depthM(){return orientedDepthM>0.01?orientedDepthM:Math.max(0,maxZ-minZ);}
    public double centerX(){return fusedCenterValid?fusedCenterX:(minX+maxX)*0.5;}
    public double centerY(){return fusedCenterValid?fusedCenterY:(minY+maxY)*0.5;}
    public double centerZ(){return fusedCenterValid?fusedCenterZ:(minZ+maxZ)*0.5;}

    public JSONObject toJson() throws JSONException{
        JSONObject o=new JSONObject();o.put("id",id).put("type",type.name()).put("label",label);
        o.put("minX",minX).put("maxX",maxX).put("minY",minY).put("maxY",maxY).put("minZ",minZ).put("maxZ",maxZ);
        o.put("confidence",confidence).put("observations",observations).put("supportPoints",supportPoints).put("lastSeenNs",lastSeenNs);
        o.put("visualOnly",visualOnly).put("parentWallId",parentWallId).put("rotationDeg",rotationDeg).put("orientedWidthM",orientedWidthM).put("orientedDepthM",orientedDepthM).put("fusedCenterX",fusedCenterX).put("fusedCenterY",fusedCenterY).put("fusedCenterZ",fusedCenterZ).put("fusedCenterValid",fusedCenterValid);return o;
    }
    public static SemanticEntity fromJson(JSONObject o){
        if(o==null)return null;SemanticEntity e=new SemanticEntity();e.id=o.optString("id",e.id);e.label=o.optString("label","");
        try{e.type=Type.valueOf(o.optString("type",Type.UNKNOWN.name()));}catch(Exception ignored){}
        e.minX=o.optDouble("minX",0);e.maxX=o.optDouble("maxX",0);e.minY=o.optDouble("minY",0);e.maxY=o.optDouble("maxY",0);e.minZ=o.optDouble("minZ",0);e.maxZ=o.optDouble("maxZ",0);
        e.confidence=o.optDouble("confidence",0);e.observations=o.optInt("observations",0);e.supportPoints=o.optInt("supportPoints",0);e.lastSeenNs=o.optLong("lastSeenNs",0);e.visualOnly=o.optBoolean("visualOnly",false);e.parentWallId=o.optString("parentWallId","");e.rotationDeg=o.optDouble("rotationDeg",0);e.orientedWidthM=o.optDouble("orientedWidthM",0);e.orientedDepthM=o.optDouble("orientedDepthM",0);e.fusedCenterValid=o.optBoolean("fusedCenterValid",false);e.fusedCenterX=o.optDouble("fusedCenterX",0);e.fusedCenterY=o.optDouble("fusedCenterY",0);e.fusedCenterZ=o.optDouble("fusedCenterZ",0);return e;
    }
}
