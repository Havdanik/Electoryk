package com.elektoryk.scan.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class PrecisionReport {
    public enum Grade { HIGH, GOOD, CHECK, LOW }

    public Grade grade=Grade.LOW;
    public int score;
    public int anchorsUsed;
    public int anchorsRejected;
    public double suggestedScale=1.0;
    public double xScale=1.0;
    public double zScale=1.0;
    public double axisMismatchPercent;
    public double rmsResidualCm;
    public double maxResidualCm;
    public double estimatedPlanErrorCm;
    public double scanConfidence;
    public double geometryConfidence;
    public double calibrationConfidence;
    public boolean scaleCorrectionRecommended;
    public final List<String> warnings=new ArrayList<>();

    public JSONObject toJson() throws JSONException{
        JSONObject o=new JSONObject();
        o.put("grade",grade.name());o.put("score",score);
        o.put("anchorsUsed",anchorsUsed);o.put("anchorsRejected",anchorsRejected);
        o.put("suggestedScale",suggestedScale);o.put("xScale",xScale);o.put("zScale",zScale);o.put("axisMismatchPercent",axisMismatchPercent);
        o.put("rmsResidualCm",rmsResidualCm);o.put("maxResidualCm",maxResidualCm);
        o.put("estimatedPlanErrorCm",estimatedPlanErrorCm);
        o.put("scanConfidence",scanConfidence);o.put("geometryConfidence",geometryConfidence);
        o.put("calibrationConfidence",calibrationConfidence);
        o.put("scaleCorrectionRecommended",scaleCorrectionRecommended);
        JSONArray a=new JSONArray();for(String w:warnings)a.put(w);o.put("warnings",a);
        return o;
    }

    public static PrecisionReport fromJson(JSONObject o){
        if(o==null)return null;
        PrecisionReport r=new PrecisionReport();
        try{r.grade=Grade.valueOf(o.optString("grade",Grade.LOW.name()));}catch(Exception ignored){}
        r.score=o.optInt("score",0);r.anchorsUsed=o.optInt("anchorsUsed",0);r.anchorsRejected=o.optInt("anchorsRejected",0);
        r.suggestedScale=o.optDouble("suggestedScale",1.0);r.xScale=o.optDouble("xScale",1.0);r.zScale=o.optDouble("zScale",1.0);r.axisMismatchPercent=o.optDouble("axisMismatchPercent",0);
        r.rmsResidualCm=o.optDouble("rmsResidualCm",0);r.maxResidualCm=o.optDouble("maxResidualCm",0);
        r.estimatedPlanErrorCm=o.optDouble("estimatedPlanErrorCm",0);
        r.scanConfidence=o.optDouble("scanConfidence",0);r.geometryConfidence=o.optDouble("geometryConfidence",0);
        r.calibrationConfidence=o.optDouble("calibrationConfidence",0);
        r.scaleCorrectionRecommended=o.optBoolean("scaleCorrectionRecommended",false);
        JSONArray a=o.optJSONArray("warnings");if(a!=null)for(int i=0;i<a.length();i++)r.warnings.add(a.optString(i,""));
        return r;
    }
}
