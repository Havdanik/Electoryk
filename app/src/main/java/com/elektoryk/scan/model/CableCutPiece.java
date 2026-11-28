package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

public class CableCutPiece {
    public String stableKey="";
    public String code="";
    public int circuitNo;
    public String cableKey="";
    public String fromPointId="";
    public String toPointId="";
    public String fromLabel="";
    public String toLabel="";
    public String fromRoom="";
    public String toRoom="";
    public double routeLengthM;
    public double fixedReserveM;
    public double percentReserve;
    public double cutLengthM;
    public int wallPassages;
    public String startSticker="";
    public String endSticker="";
    public String protectionTag="";
    public boolean cutDone;
    public boolean labelsPrinted;
    public boolean installed;
    public String spool="";
    public String warning="";
    public String note="";

    public JSONObject toJson() throws JSONException {
        JSONObject o=new JSONObject();
        o.put("stableKey",stableKey);o.put("code",code);o.put("circuitNo",circuitNo);o.put("cableKey",cableKey);
        o.put("fromPointId",fromPointId);o.put("toPointId",toPointId);o.put("fromLabel",fromLabel);o.put("toLabel",toLabel);
        o.put("fromRoom",fromRoom);o.put("toRoom",toRoom);o.put("routeLengthM",routeLengthM);o.put("fixedReserveM",fixedReserveM);
        o.put("percentReserve",percentReserve);o.put("cutLengthM",cutLengthM);o.put("wallPassages",wallPassages);
        o.put("startSticker",startSticker);o.put("endSticker",endSticker);o.put("protectionTag",protectionTag);o.put("cutDone",cutDone);o.put("labelsPrinted",labelsPrinted);
        o.put("installed",installed);o.put("spool",spool);o.put("warning",warning);o.put("note",note);return o;
    }

    public static CableCutPiece fromJson(JSONObject o){
        if(o==null)return null;CableCutPiece p=new CableCutPiece();
        p.stableKey=o.optString("stableKey","");p.code=o.optString("code","");p.circuitNo=o.optInt("circuitNo",0);p.cableKey=o.optString("cableKey","");
        p.fromPointId=o.optString("fromPointId","");p.toPointId=o.optString("toPointId","");p.fromLabel=o.optString("fromLabel","");p.toLabel=o.optString("toLabel","");
        p.fromRoom=o.optString("fromRoom","");p.toRoom=o.optString("toRoom","");p.routeLengthM=o.optDouble("routeLengthM",0);p.fixedReserveM=o.optDouble("fixedReserveM",0);
        p.percentReserve=o.optDouble("percentReserve",0);p.cutLengthM=o.optDouble("cutLengthM",0);p.wallPassages=o.optInt("wallPassages",0);
        p.startSticker=o.optString("startSticker","");p.endSticker=o.optString("endSticker","");p.protectionTag=o.optString("protectionTag","");p.cutDone=o.optBoolean("cutDone",false);p.labelsPrinted=o.optBoolean("labelsPrinted",false);
        p.installed=o.optBoolean("installed",false);p.spool=o.optString("spool","");p.warning=o.optString("warning","");p.note=o.optString("note","");return p;
    }
}
