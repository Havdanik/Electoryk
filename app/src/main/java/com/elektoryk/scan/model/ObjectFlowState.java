package com.elektoryk.scan.model;

import org.json.*;
import java.util.*;

/** Persistent state for the whole object lifecycle: brief -> scan -> approval -> work -> handover. */
public class ObjectFlowState {
    public enum WorkClass { ECONOMY, STANDARD, PREMIUM }
    public String projectId="";
    public String clientName="";
    public String clientPhone="";
    public String addressNote="";
    public int requestedSockets=0,requestedSwitches=0,requestedLights=0,requestedWallLights=0,requestedPanels=1,requestedEthernet=0,requestedCameras=0;
    public double budgetMin=0,budgetMax=0;
    public WorkClass workClass=WorkClass.STANDARD;
    public boolean wantsRepairVisualization=false;
    public boolean briefConfirmed=false;
    public String briefNote="";
    public int desiredScanPasses=2;
    public int completedScanPasses=0;
    public boolean analysisConfirmed=false;
    public boolean clientRecordingConsent=false;
    public boolean autoRecordConsultation=true;
    public long updatedAt=System.currentTimeMillis();
    public final List<WorkerShift> shifts=new ArrayList<>();
    public final List<ProjectExpense> expenses=new ArrayList<>();
    public final List<String> consultationAudioFiles=new ArrayList<>();

    public JSONObject toJson() throws JSONException{
        JSONObject o=new JSONObject();o.put("projectId",projectId);o.put("clientName",clientName);o.put("clientPhone",clientPhone);o.put("addressNote",addressNote);
        o.put("requestedSockets",requestedSockets);o.put("requestedSwitches",requestedSwitches);o.put("requestedLights",requestedLights);o.put("requestedWallLights",requestedWallLights);o.put("requestedPanels",requestedPanels);o.put("requestedEthernet",requestedEthernet);o.put("requestedCameras",requestedCameras);
        o.put("budgetMin",budgetMin);o.put("budgetMax",budgetMax);o.put("workClass",workClass.name());o.put("wantsRepairVisualization",wantsRepairVisualization);o.put("briefConfirmed",briefConfirmed);o.put("briefNote",briefNote);
        o.put("desiredScanPasses",desiredScanPasses);o.put("completedScanPasses",completedScanPasses);o.put("analysisConfirmed",analysisConfirmed);o.put("clientRecordingConsent",clientRecordingConsent);o.put("autoRecordConsultation",autoRecordConsultation);o.put("updatedAt",updatedAt);
        JSONArray a=new JSONArray();for(WorkerShift x:shifts)a.put(x.toJson());o.put("shifts",a);a=new JSONArray();for(ProjectExpense x:expenses)a.put(x.toJson());o.put("expenses",a);a=new JSONArray();for(String x:consultationAudioFiles)a.put(x);o.put("consultationAudioFiles",a);return o;
    }
    public static ObjectFlowState fromJson(JSONObject o){ObjectFlowState s=new ObjectFlowState();if(o==null)return s;s.projectId=o.optString("projectId","");s.clientName=o.optString("clientName","");s.clientPhone=o.optString("clientPhone","");s.addressNote=o.optString("addressNote","");s.requestedSockets=o.optInt("requestedSockets",0);s.requestedSwitches=o.optInt("requestedSwitches",0);s.requestedLights=o.optInt("requestedLights",0);s.requestedWallLights=o.optInt("requestedWallLights",0);s.requestedPanels=o.optInt("requestedPanels",1);s.requestedEthernet=o.optInt("requestedEthernet",0);s.requestedCameras=o.optInt("requestedCameras",0);s.budgetMin=o.optDouble("budgetMin",0);s.budgetMax=o.optDouble("budgetMax",0);try{s.workClass=WorkClass.valueOf(o.optString("workClass",WorkClass.STANDARD.name()));}catch(Exception ignored){}s.wantsRepairVisualization=o.optBoolean("wantsRepairVisualization",false);s.briefConfirmed=o.optBoolean("briefConfirmed",false);s.briefNote=o.optString("briefNote","");s.desiredScanPasses=Math.max(1,o.optInt("desiredScanPasses",2));s.completedScanPasses=Math.max(0,o.optInt("completedScanPasses",0));s.analysisConfirmed=o.optBoolean("analysisConfirmed",false);s.clientRecordingConsent=o.optBoolean("clientRecordingConsent",false);s.autoRecordConsultation=o.optBoolean("autoRecordConsultation",true);s.updatedAt=o.optLong("updatedAt",System.currentTimeMillis());JSONArray a=o.optJSONArray("shifts");if(a!=null)for(int i=0;i<a.length();i++){WorkerShift x=WorkerShift.fromJson(a.optJSONObject(i));if(x!=null)s.shifts.add(x);}a=o.optJSONArray("expenses");if(a!=null)for(int i=0;i<a.length();i++){ProjectExpense x=ProjectExpense.fromJson(a.optJSONObject(i));if(x!=null)s.expenses.add(x);}a=o.optJSONArray("consultationAudioFiles");if(a!=null)for(int i=0;i<a.length();i++)s.consultationAudioFiles.add(a.optString(i,""));return s;}
}
