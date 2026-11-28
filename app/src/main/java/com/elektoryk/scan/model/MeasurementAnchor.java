package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class MeasurementAnchor {
    public enum Kind { WALL_LENGTH, ROOM_DIMENSION, CEILING_HEIGHT, OPENING_WIDTH, CUSTOM }
    public enum Axis { X, Y, Z, OTHER }
    public enum Source { LASER, TAPE, DOCUMENT, KNOWN_OBJECT, OTHER }

    public String id=UUID.randomUUID().toString();
    public Kind kind=Kind.CUSTOM;
    public Axis axis=Axis.OTHER;
    public Source source=Source.LASER;
    public String referenceId="";
    public String label="";
    public double scannedM;
    public double measuredM;
    public double weight=1.0;
    public boolean enabled=true;
    public long createdAt=System.currentTimeMillis();

    public double factor(){
        return scannedM>1e-6&&measuredM>0?measuredM/scannedM:1.0;
    }

    public double errorBeforeCm(){
        return (measuredM-scannedM)*100.0;
    }

    public JSONObject toJson() throws JSONException{
        JSONObject o=new JSONObject();
        o.put("id",id);o.put("kind",kind.name());o.put("axis",axis.name());o.put("source",source.name());
        o.put("referenceId",referenceId);o.put("label",label);
        o.put("scannedM",scannedM);o.put("measuredM",measuredM);o.put("weight",weight);
        o.put("enabled",enabled);o.put("createdAt",createdAt);
        return o;
    }

    public static MeasurementAnchor fromJson(JSONObject o){
        if(o==null)return null;
        MeasurementAnchor a=new MeasurementAnchor();
        a.id=o.optString("id",a.id);
        try{a.kind=Kind.valueOf(o.optString("kind",Kind.CUSTOM.name()));}catch(Exception ignored){}
        try{a.axis=Axis.valueOf(o.optString("axis",Axis.OTHER.name()));}catch(Exception ignored){}
        try{a.source=Source.valueOf(o.optString("source",Source.LASER.name()));}catch(Exception ignored){}
        a.referenceId=o.optString("referenceId","");
        a.label=o.optString("label","");
        a.scannedM=o.optDouble("scannedM",0);
        a.measuredM=o.optDouble("measuredM",0);
        a.weight=o.optDouble("weight",1.0);
        a.enabled=o.optBoolean("enabled",true);
        a.createdAt=o.optLong("createdAt",System.currentTimeMillis());
        return a;
    }
}
