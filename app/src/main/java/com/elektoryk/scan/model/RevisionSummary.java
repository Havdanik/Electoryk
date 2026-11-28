package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class RevisionSummary {
    public enum Reason { BASELINE, CLIENT_CHANGE, SITE_CHANGE, DESIGN_CHANGE, AS_BUILT }
    public String id=UUID.randomUUID().toString();
    public int versionNo;
    public long createdAt=System.currentTimeMillis();
    public Reason reason=Reason.BASELINE;
    public String snapshotFile="";
    public double areaM2;
    public int pointCount;
    public int circuitCount;
    public double cablePurchaseM;
    public double estimateTotal;
    public String note="";
    public JSONObject toJson() throws JSONException{JSONObject o=new JSONObject();o.put("id",id);o.put("versionNo",versionNo);o.put("createdAt",createdAt);o.put("reason",reason.name());o.put("snapshotFile",snapshotFile);o.put("areaM2",areaM2);o.put("pointCount",pointCount);o.put("circuitCount",circuitCount);o.put("cablePurchaseM",cablePurchaseM);o.put("estimateTotal",estimateTotal);o.put("note",note);return o;}
    public static RevisionSummary fromJson(JSONObject o){if(o==null)return null;RevisionSummary r=new RevisionSummary();r.id=o.optString("id",r.id);r.versionNo=o.optInt("versionNo",0);r.createdAt=o.optLong("createdAt",System.currentTimeMillis());try{r.reason=Reason.valueOf(o.optString("reason",Reason.BASELINE.name()));}catch(Exception ignored){}r.snapshotFile=o.optString("snapshotFile","");r.areaM2=o.optDouble("areaM2",0);r.pointCount=o.optInt("pointCount",0);r.circuitCount=o.optInt("circuitCount",0);r.cablePurchaseM=o.optDouble("cablePurchaseM",0);r.estimateTotal=o.optDouble("estimateTotal",0);r.note=o.optString("note","");return r;}
}
