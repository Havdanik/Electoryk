package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

public class CircuitProtectionPlan {
    public int circuitNo;
    public String name="";
    public String cableKey="";
    public double plannedLoadW;
    public double estimatedCurrentA;
    public int suggestedBreakerA;
    public String suggestedCurve="C";
    public int poles=1;
    public String phase="AUTO";
    public String protection="MCB";
    public int residualCurrentMa=30;
    public boolean verifiedByElectrician;
    public boolean manualProtectionEdited;
    public String testStatus="PENDING";
    public double measuredVoltageV;
    public double insulationMohm;
    public double rcdTripMs;
    public double loopImpedanceOhm;
    public long testVerifiedAt;
    public String testNote="";
    public String note="";

    public JSONObject toJson() throws JSONException{
        JSONObject o=new JSONObject();o.put("circuitNo",circuitNo);o.put("name",name);o.put("cableKey",cableKey);o.put("plannedLoadW",plannedLoadW);o.put("estimatedCurrentA",estimatedCurrentA);
        o.put("suggestedBreakerA",suggestedBreakerA);o.put("suggestedCurve",suggestedCurve);o.put("poles",poles);o.put("phase",phase);o.put("protection",protection);o.put("residualCurrentMa",residualCurrentMa);
        o.put("verifiedByElectrician",verifiedByElectrician);o.put("manualProtectionEdited",manualProtectionEdited);
        o.put("testStatus",testStatus);o.put("measuredVoltageV",measuredVoltageV);o.put("insulationMohm",insulationMohm);o.put("rcdTripMs",rcdTripMs);o.put("loopImpedanceOhm",loopImpedanceOhm);o.put("testVerifiedAt",testVerifiedAt);o.put("testNote",testNote);
        o.put("note",note);return o;
    }
    public static CircuitProtectionPlan fromJson(JSONObject o){
        if(o==null)return null;CircuitProtectionPlan p=new CircuitProtectionPlan();p.circuitNo=o.optInt("circuitNo",0);p.name=o.optString("name","");p.cableKey=o.optString("cableKey","");
        p.plannedLoadW=o.optDouble("plannedLoadW",0);p.estimatedCurrentA=o.optDouble("estimatedCurrentA",0);p.suggestedBreakerA=o.optInt("suggestedBreakerA",0);p.suggestedCurve=o.optString("suggestedCurve","C");
        p.poles=o.optInt("poles",1);p.phase=o.optString("phase","AUTO");p.protection=o.optString("protection","MCB");p.residualCurrentMa=o.optInt("residualCurrentMa",30);p.verifiedByElectrician=o.optBoolean("verifiedByElectrician",false);p.manualProtectionEdited=o.optBoolean("manualProtectionEdited",false);
        p.testStatus=o.optString("testStatus","PENDING");p.measuredVoltageV=o.optDouble("measuredVoltageV",0);p.insulationMohm=o.optDouble("insulationMohm",0);p.rcdTripMs=o.optDouble("rcdTripMs",0);p.loopImpedanceOhm=o.optDouble("loopImpedanceOhm",0);p.testVerifiedAt=o.optLong("testVerifiedAt",0);p.testNote=o.optString("testNote","");
        p.note=o.optString("note","");return p;
    }
}
