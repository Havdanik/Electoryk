package com.elektoryk.scan.model;
import org.json.*;
public class ClientApproval {
    public String projectId="";
    public boolean planApproved;
    public boolean estimateApproved;
    public boolean handoverAccepted;
    public long planApprovedAt;
    public long estimateApprovedAt;
    public long handoverAcceptedAt;
    public String revisionId="";
    public String signatureFile="";
    public long signatureAt;
    public String note="";
    public boolean hasAnyApproval(){return planApproved||estimateApproved||handoverAccepted;}
    public JSONObject toJson() throws JSONException{JSONObject o=new JSONObject();o.put("projectId",projectId);o.put("planApproved",planApproved);o.put("estimateApproved",estimateApproved);o.put("handoverAccepted",handoverAccepted);o.put("planApprovedAt",planApprovedAt);o.put("estimateApprovedAt",estimateApprovedAt);o.put("handoverAcceptedAt",handoverAcceptedAt);o.put("revisionId",revisionId);o.put("signatureFile",signatureFile);o.put("signatureAt",signatureAt);o.put("note",note);return o;}
    public static ClientApproval fromJson(JSONObject o){ClientApproval a=new ClientApproval();if(o==null)return a;a.projectId=o.optString("projectId","");a.planApproved=o.optBoolean("planApproved",false);a.estimateApproved=o.optBoolean("estimateApproved",false);a.handoverAccepted=o.optBoolean("handoverAccepted",false);a.planApprovedAt=o.optLong("planApprovedAt",0);a.estimateApprovedAt=o.optLong("estimateApprovedAt",0);a.handoverAcceptedAt=o.optLong("handoverAcceptedAt",0);a.revisionId=o.optString("revisionId","");a.signatureFile=o.optString("signatureFile","");a.signatureAt=o.optLong("signatureAt",0);a.note=o.optString("note","");return a;}
}
