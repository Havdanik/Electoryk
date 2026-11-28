package com.elektoryk.scan.model;
import org.json.*;
public class CatalogItem {
    public String stableKey="";public String name="";public String unit="шт";public double unitCost;public double packSize=1;public double stockQty;public long updatedAt=System.currentTimeMillis();
    public JSONObject toJson() throws JSONException{JSONObject o=new JSONObject();o.put("stableKey",stableKey);o.put("name",name);o.put("unit",unit);o.put("unitCost",unitCost);o.put("packSize",packSize);o.put("stockQty",stockQty);o.put("updatedAt",updatedAt);return o;}
    public static CatalogItem fromJson(JSONObject o){if(o==null)return null;CatalogItem i=new CatalogItem();i.stableKey=o.optString("stableKey","");i.name=o.optString("name","");i.unit=o.optString("unit","шт");i.unitCost=o.optDouble("unitCost",0);i.packSize=Math.max(.0001,o.optDouble("packSize",1));i.stockQty=o.optDouble("stockQty",0);i.updatedAt=o.optLong("updatedAt",System.currentTimeMillis());return i;}
}
