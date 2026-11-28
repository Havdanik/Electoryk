package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class CommercialLine {
    public enum Category { MATERIAL, LABOR, OTHER }
    public enum ProcurementOwner { MASTER, CLIENT }
    public String id=UUID.randomUUID().toString();
    public String stableKey="";
    public Category category=Category.MATERIAL;
    public String name="";
    public String unit="шт";
    public double requiredQty;
    public double stockQty;
    public double purchaseQty;
    public double packSize=1.0;
    public double unitCost;
    public double unitClientPrice;
    public ProcurementOwner owner=ProcurementOwner.MASTER;
    public boolean purchased;
    public boolean received;
    public boolean unavailable;
    public boolean includeInEstimate=true;
    public String supplier="";
    public String replacementName="";
    public String note="";
    public double costTotal(){return Math.max(0,purchaseQty)*Math.max(0,unitCost);}    
    public double clientTotal(){return Math.max(0,requiredQty)*Math.max(0,unitClientPrice);}    
    public JSONObject toJson() throws JSONException{
        JSONObject o=new JSONObject();o.put("id",id);o.put("stableKey",stableKey);o.put("category",category.name());o.put("name",name);o.put("unit",unit);
        o.put("requiredQty",requiredQty);o.put("stockQty",stockQty);o.put("purchaseQty",purchaseQty);o.put("packSize",packSize);o.put("unitCost",unitCost);o.put("unitClientPrice",unitClientPrice);
        o.put("owner",owner.name());o.put("purchased",purchased);o.put("received",received);o.put("unavailable",unavailable);o.put("includeInEstimate",includeInEstimate);o.put("supplier",supplier);o.put("replacementName",replacementName);o.put("note",note);return o;
    }
    public static CommercialLine fromJson(JSONObject o){
        if(o==null)return null;CommercialLine x=new CommercialLine();x.id=o.optString("id",x.id);x.stableKey=o.optString("stableKey","");
        try{x.category=Category.valueOf(o.optString("category",Category.MATERIAL.name()));}catch(Exception ignored){}
        x.name=o.optString("name","");x.unit=o.optString("unit","шт");x.requiredQty=o.optDouble("requiredQty",0);x.stockQty=o.optDouble("stockQty",0);x.purchaseQty=o.optDouble("purchaseQty",0);x.packSize=Math.max(0.0001,o.optDouble("packSize",1));x.unitCost=o.optDouble("unitCost",0);x.unitClientPrice=o.optDouble("unitClientPrice",0);
        try{x.owner=ProcurementOwner.valueOf(o.optString("owner",ProcurementOwner.MASTER.name()));}catch(Exception ignored){}
        x.purchased=o.optBoolean("purchased",false);x.received=o.optBoolean("received",false);x.unavailable=o.optBoolean("unavailable",false);x.includeInEstimate=o.optBoolean("includeInEstimate",true);x.supplier=o.optString("supplier","");x.replacementName=o.optString("replacementName","");x.note=o.optString("note","");return x;
    }
}
