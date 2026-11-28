package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

public class ScanPathPoint {
    public double x;
    public double z;
    public long timestampNs;

    public ScanPathPoint() {}
    public ScanPathPoint(double x,double z,long timestampNs){
        this.x=x; this.z=z; this.timestampNs=timestampNs;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o=new JSONObject();
        o.put("x",x); o.put("z",z); o.put("timestampNs",timestampNs);
        return o;
    }

    public static ScanPathPoint fromJson(JSONObject o){
        if(o==null)return null;
        return new ScanPathPoint(o.optDouble("x",0),o.optDouble("z",0),o.optLong("timestampNs",0));
    }
}
