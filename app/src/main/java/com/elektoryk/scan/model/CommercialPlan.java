package com.elektoryk.scan.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class CommercialPlan {
    public String projectId="";
    public long updatedAt=System.currentTimeMillis();
    public double materialMarkupPercent=30.0;
    public double laborMarkupPercent=0.0;
    public double discountPercent=0.0;
    public final List<CommercialLine> lines=new ArrayList<>();
    public double materialCost,materialClient,laborCost,laborClient,subtotal,discount,total;

    public void recalc(){
        materialCost=materialClient=laborCost=laborClient=0;
        for(CommercialLine l:lines){if(!l.includeInEstimate)continue;if(l.category==CommercialLine.Category.MATERIAL){materialCost+=l.costTotal();materialClient+=l.clientTotal();}else if(l.category==CommercialLine.Category.LABOR){laborCost+=l.costTotal();laborClient+=l.clientTotal();}}
        subtotal=materialClient+laborClient;discount=subtotal*Math.max(0,discountPercent)/100.0;total=Math.max(0,subtotal-discount);
    }
    public JSONObject toJson() throws JSONException{recalc();JSONObject o=new JSONObject();o.put("projectId",projectId);o.put("updatedAt",updatedAt);o.put("materialMarkupPercent",materialMarkupPercent);o.put("laborMarkupPercent",laborMarkupPercent);o.put("discountPercent",discountPercent);JSONArray a=new JSONArray();for(CommercialLine l:lines)a.put(l.toJson());o.put("lines",a);return o;}
    public static CommercialPlan fromJson(JSONObject o){CommercialPlan p=new CommercialPlan();if(o==null)return p;p.projectId=o.optString("projectId","");p.updatedAt=o.optLong("updatedAt",System.currentTimeMillis());p.materialMarkupPercent=o.optDouble("materialMarkupPercent",30);p.laborMarkupPercent=o.optDouble("laborMarkupPercent",0);p.discountPercent=o.optDouble("discountPercent",0);JSONArray a=o.optJSONArray("lines");if(a!=null)for(int i=0;i<a.length();i++){CommercialLine l=CommercialLine.fromJson(a.optJSONObject(i));if(l!=null)p.lines.add(l);}p.recalc();return p;}
}
