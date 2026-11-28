package com.elektoryk.scan.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ApartmentPlan {
    public double cellM = 0.20;
    public double originX;
    public double originZ;
    public int rows;
    public int cols;
    public double floorY;
    public double ceilingY;

    public double directObservedAreaM2;
    public double inferredFloorAreaM2;
    public double totalObservedAreaM2;
    public double totalRoomAreaM2;
    public double confidence;
    public boolean manualEdited;
    public long manualRevision;
    public long lastManualEditAt;

    public final List<PlanWallSegment> walls = new ArrayList<>();
    public final List<RoomZone> rooms = new ArrayList<>();
    public final List<ApartmentOpening> openings = new ArrayList<>();
    public final List<StructuralFeature> features = new ArrayList<>();
    public final List<MissingZoneHint> missingZoneHints = new ArrayList<>();
    public final List<ForbiddenZone> forbiddenZones = new ArrayList<>();
    public final List<FurnitureItem> furniture = new ArrayList<>();

    public JSONObject toJson() throws JSONException {
        JSONObject o=new JSONObject();
        o.put("cellM",cellM);
        o.put("originX",originX); o.put("originZ",originZ);
        o.put("rows",rows); o.put("cols",cols);
        o.put("floorY",floorY); o.put("ceilingY",ceilingY);
        o.put("directObservedAreaM2",directObservedAreaM2);
        o.put("inferredFloorAreaM2",inferredFloorAreaM2);
        o.put("totalObservedAreaM2",totalObservedAreaM2);
        o.put("totalRoomAreaM2",totalRoomAreaM2);
        o.put("confidence",confidence);
        o.put("manualEdited",manualEdited);
        o.put("manualRevision",manualRevision);
        o.put("lastManualEditAt",lastManualEditAt);

        JSONArray ww=new JSONArray(); for(PlanWallSegment w:walls) ww.put(w.toJson()); o.put("walls",ww);
        JSONArray rr=new JSONArray(); for(RoomZone r:rooms) rr.put(r.toJson()); o.put("rooms",rr);
        JSONArray oo=new JSONArray(); for(ApartmentOpening a:openings) oo.put(a.toJson()); o.put("openings",oo);
        JSONArray ff=new JSONArray(); for(StructuralFeature f:features) ff.put(f.toJson()); o.put("features",ff);
        JSONArray mm=new JSONArray(); for(MissingZoneHint h:missingZoneHints) mm.put(h.toJson()); o.put("missingZoneHints",mm);
        JSONArray zz=new JSONArray(); for(ForbiddenZone z:forbiddenZones) zz.put(z.toJson()); o.put("forbiddenZones",zz);
        JSONArray ii=new JSONArray(); for(FurnitureItem f:furniture) ii.put(f.toJson()); o.put("furniture",ii);
        return o;
    }

    public static ApartmentPlan fromJson(JSONObject o){
        if(o==null)return null;
        ApartmentPlan p=new ApartmentPlan();
        p.cellM=o.optDouble("cellM",0.20);
        p.originX=o.optDouble("originX",0); p.originZ=o.optDouble("originZ",0);
        p.rows=o.optInt("rows",0); p.cols=o.optInt("cols",0);
        p.floorY=o.optDouble("floorY",0); p.ceilingY=o.optDouble("ceilingY",0);
        p.directObservedAreaM2=o.optDouble("directObservedAreaM2",0);
        p.inferredFloorAreaM2=o.optDouble("inferredFloorAreaM2",0);
        p.totalObservedAreaM2=o.optDouble("totalObservedAreaM2",0);
        p.totalRoomAreaM2=o.optDouble("totalRoomAreaM2",0);
        p.confidence=o.optDouble("confidence",0);
        p.manualEdited=o.optBoolean("manualEdited",false);
        p.manualRevision=o.optLong("manualRevision",0);
        p.lastManualEditAt=o.optLong("lastManualEditAt",0);

        JSONArray ww=o.optJSONArray("walls"); if(ww!=null)for(int i=0;i<ww.length();i++){PlanWallSegment w=PlanWallSegment.fromJson(ww.optJSONObject(i));if(w!=null)p.walls.add(w);}
        JSONArray rr=o.optJSONArray("rooms"); if(rr!=null)for(int i=0;i<rr.length();i++){RoomZone r=RoomZone.fromJson(rr.optJSONObject(i));if(r!=null)p.rooms.add(r);}
        JSONArray oo=o.optJSONArray("openings"); if(oo!=null)for(int i=0;i<oo.length();i++){ApartmentOpening a=ApartmentOpening.fromJson(oo.optJSONObject(i));if(a!=null)p.openings.add(a);}
        JSONArray ff=o.optJSONArray("features"); if(ff!=null)for(int i=0;i<ff.length();i++){StructuralFeature f=StructuralFeature.fromJson(ff.optJSONObject(i));if(f!=null)p.features.add(f);}
        JSONArray mm=o.optJSONArray("missingZoneHints"); if(mm!=null)for(int i=0;i<mm.length();i++){MissingZoneHint h=MissingZoneHint.fromJson(mm.optJSONObject(i));if(h!=null)p.missingZoneHints.add(h);}
        JSONArray zz=o.optJSONArray("forbiddenZones"); if(zz!=null)for(int i=0;i<zz.length();i++){ForbiddenZone z=ForbiddenZone.fromJson(zz.optJSONObject(i));if(z!=null)p.forbiddenZones.add(z);}
        JSONArray ii=o.optJSONArray("furniture"); if(ii!=null)for(int i=0;i<ii.length();i++){FurnitureItem f=FurnitureItem.fromJson(ii.optJSONObject(i));if(f!=null)p.furniture.add(f);}
        return p;
    }
}
