package com.elektoryk.scan.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class FieldWorkPlan {
    public String projectId="";
    public long updatedAt=System.currentTimeMillis();
    public final List<CircuitProtectionPlan> circuits=new ArrayList<>();
    public JSONObject toJson() throws JSONException{
        JSONObject o=new JSONObject();o.put("projectId",projectId);o.put("updatedAt",updatedAt);JSONArray a=new JSONArray();for(CircuitProtectionPlan c:circuits)a.put(c.toJson());o.put("circuits",a);return o;
    }
    public static FieldWorkPlan fromJson(JSONObject o){FieldWorkPlan p=new FieldWorkPlan();if(o==null)return p;p.projectId=o.optString("projectId","");p.updatedAt=o.optLong("updatedAt",System.currentTimeMillis());JSONArray a=o.optJSONArray("circuits");if(a!=null)for(int i=0;i<a.length();i++){CircuitProtectionPlan c=CircuitProtectionPlan.fromJson(a.optJSONObject(i));if(c!=null)p.circuits.add(c);}return p;}
}
