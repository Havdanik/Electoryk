package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class MissingZoneHint {
    public String id = UUID.randomUUID().toString();
    public double x;
    public double z;
    public String relatedRoomId = "";
    public double confidence;
    public double estimatedAreaMinM2;
    public double estimatedAreaMaxM2;
    public String reason = "";

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id",id);
        o.put("x",x); o.put("z",z);
        o.put("relatedRoomId",relatedRoomId);
        o.put("confidence",confidence);
        o.put("estimatedAreaMinM2",estimatedAreaMinM2);
        o.put("estimatedAreaMaxM2",estimatedAreaMaxM2);
        o.put("reason",reason);
        return o;
    }

    public static MissingZoneHint fromJson(JSONObject o) {
        if(o==null)return null;
        MissingZoneHint h=new MissingZoneHint();
        h.id=o.optString("id",h.id);
        h.x=o.optDouble("x",0); h.z=o.optDouble("z",0);
        h.relatedRoomId=o.optString("relatedRoomId","");
        h.confidence=o.optDouble("confidence",0);
        h.estimatedAreaMinM2=o.optDouble("estimatedAreaMinM2",0);
        h.estimatedAreaMaxM2=o.optDouble("estimatedAreaMaxM2",0);
        h.reason=o.optString("reason","");
        return h;
    }
}
