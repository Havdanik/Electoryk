package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class ForbiddenZone {
    public enum Type { NO_ROUTE, BEAM, VENTILATION, WET_ZONE, STRUCTURAL, CUSTOM }

    public String id=UUID.randomUUID().toString();
    public Type type=Type.NO_ROUTE;
    public double minX,maxX,minZ,maxZ;
    public double minY=0,maxY=4;
    public String note="";

    public boolean contains(double x,double y,double z){
        return x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ&&y>=minY&&y<=maxY;
    }

    public JSONObject toJson() throws JSONException{
        JSONObject o=new JSONObject();
        o.put("id",id);o.put("type",type.name());
        o.put("minX",minX);o.put("maxX",maxX);o.put("minZ",minZ);o.put("maxZ",maxZ);
        o.put("minY",minY);o.put("maxY",maxY);o.put("note",note);
        return o;
    }

    public static ForbiddenZone fromJson(JSONObject o){
        if(o==null)return null;
        ForbiddenZone z=new ForbiddenZone();
        z.id=o.optString("id",z.id);
        try{z.type=Type.valueOf(o.optString("type",Type.NO_ROUTE.name()));}catch(Exception ignored){}
        z.minX=o.optDouble("minX",0);z.maxX=o.optDouble("maxX",0);
        z.minZ=o.optDouble("minZ",0);z.maxZ=o.optDouble("maxZ",0);
        z.minY=o.optDouble("minY",0);z.maxY=o.optDouble("maxY",4);
        z.note=o.optString("note","");
        return z;
    }
}
