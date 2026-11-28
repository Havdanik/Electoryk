package com.elektoryk.scan.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class CableCutList {
    public String projectId="";
    public long updatedAt=System.currentTimeMillis();
    public final List<CableCutPiece> pieces=new ArrayList<>();
    public double totalCutM;
    public double sourceRollM=100.0;
    public double estimatedWasteM;

    public JSONObject toJson() throws JSONException{
        JSONObject o=new JSONObject();o.put("projectId",projectId);o.put("updatedAt",updatedAt);o.put("totalCutM",totalCutM);o.put("sourceRollM",sourceRollM);o.put("estimatedWasteM",estimatedWasteM);
        JSONArray a=new JSONArray();for(CableCutPiece p:pieces)a.put(p.toJson());o.put("pieces",a);return o;
    }
    public static CableCutList fromJson(JSONObject o){
        CableCutList l=new CableCutList();if(o==null)return l;l.projectId=o.optString("projectId","");l.updatedAt=o.optLong("updatedAt",System.currentTimeMillis());l.totalCutM=o.optDouble("totalCutM",0);l.sourceRollM=o.optDouble("sourceRollM",100.0);l.estimatedWasteM=o.optDouble("estimatedWasteM",0);
        JSONArray a=o.optJSONArray("pieces");if(a!=null)for(int i=0;i<a.length();i++){CableCutPiece p=CableCutPiece.fromJson(a.optJSONObject(i));if(p!=null)l.pieces.add(p);}return l;
    }
}
