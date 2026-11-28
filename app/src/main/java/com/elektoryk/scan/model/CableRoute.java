package com.elektoryk.scan.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CableRoute {
    public String id=UUID.randomUUID().toString();
    public int circuitNo;
    public String cableKey="3x2.5";
    public double rawLengthM;
    public double reservePercent;
    public double purchaseLengthM;
    public final List<RouteNode> nodes=new ArrayList<>();
    public final List<String> pointIds=new ArrayList<>();
    public String warning="";

    public JSONObject toJson() throws JSONException{
        JSONObject o=new JSONObject();
        o.put("id",id);o.put("circuitNo",circuitNo);o.put("cableKey",cableKey);
        o.put("rawLengthM",rawLengthM);o.put("reservePercent",reservePercent);
        o.put("purchaseLengthM",purchaseLengthM);o.put("warning",warning);
        JSONArray n=new JSONArray();for(RouteNode x:nodes)n.put(x.toJson());o.put("nodes",n);
        JSONArray p=new JSONArray();for(String x:pointIds)p.put(x);o.put("pointIds",p);
        return o;
    }

    public static class RouteNode{
        public double x,z,y;
        public String kind="";
        public RouteNode(){}
        public RouteNode(double x,double y,double z,String kind){this.x=x;this.y=y;this.z=z;this.kind=kind;}
        public JSONObject toJson() throws JSONException{
            JSONObject o=new JSONObject();o.put("x",x);o.put("y",y);o.put("z",z);o.put("kind",kind);return o;
        }
    }
}
