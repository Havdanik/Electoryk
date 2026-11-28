package com.elektoryk.scan.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SceneSnapshot {
    public String projectId = "";
    public long createdAt = System.currentTimeMillis();
    public long updatedAt = createdAt;

    public int depthFrameCount;
    public int rawPointSamples;
    public int voxelPointCount;
    public int directionCoveragePercent;
    public double roomYawRad;
    public double observedFloorAreaM2;
    public double floorObservationRatio;
    public double rawPointScale = 1.0;

    public RoomBounds roomBounds;
    public AreaCheckResult areaCheck;
    public ApartmentPlan apartmentPlan;
    public ApartmentPlan latestAutoApartmentPlan;
    public PrecisionReport precisionReport;
    public final List<MeasurementAnchor> measurementAnchors = new ArrayList<>();

    public final List<SurfaceObservation> surfaces = new ArrayList<>();
    public final List<SemanticEntity> semanticEntities = new ArrayList<>();
    public final List<ReviewIssue> reviewIssues = new ArrayList<>();
    public final List<ScanGap> scanGaps = new ArrayList<>();
    public final List<ScanPathPoint> scanPath = new ArrayList<>();

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("projectId", projectId);
        o.put("createdAt", createdAt);
        o.put("updatedAt", updatedAt);
        o.put("depthFrameCount", depthFrameCount);
        o.put("rawPointSamples", rawPointSamples);
        o.put("voxelPointCount", voxelPointCount);
        o.put("directionCoveragePercent", directionCoveragePercent);
        o.put("roomYawRad", roomYawRad);
        o.put("observedFloorAreaM2", observedFloorAreaM2);
        o.put("floorObservationRatio", floorObservationRatio);
        o.put("rawPointScale", rawPointScale);
        if (roomBounds != null) o.put("roomBounds", roomBounds.toJson());
        if (areaCheck != null) o.put("areaCheck", areaCheck.toJson());
        if (apartmentPlan != null) o.put("apartmentPlan", apartmentPlan.toJson());
        if (latestAutoApartmentPlan != null) o.put("latestAutoApartmentPlan", latestAutoApartmentPlan.toJson());
        if (precisionReport != null) o.put("precisionReport", precisionReport.toJson());
        JSONArray aa = new JSONArray();
        for (MeasurementAnchor a : measurementAnchors) aa.put(a.toJson());
        o.put("measurementAnchors", aa);

        JSONArray ss = new JSONArray();
        for (SurfaceObservation s : surfaces) ss.put(s.toJson());
        o.put("surfaces", ss);

        JSONArray se = new JSONArray();
        for (SemanticEntity e : semanticEntities) se.put(e.toJson());
        o.put("semanticEntities", se);

        JSONArray rr = new JSONArray();
        for (ReviewIssue r : reviewIssues) rr.put(r.toJson());
        o.put("reviewIssues", rr);

        JSONArray gg = new JSONArray();
        for (ScanGap g : scanGaps) gg.put(g.toJson());
        o.put("scanGaps", gg);

        JSONArray pp = new JSONArray();
        for (ScanPathPoint p : scanPath) pp.put(p.toJson());
        o.put("scanPath", pp);
        return o;
    }

    public static SceneSnapshot fromJson(JSONObject o) {
        SceneSnapshot s = new SceneSnapshot();
        if (o == null) return s;
        s.projectId = o.optString("projectId", "");
        s.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        s.updatedAt = o.optLong("updatedAt", s.createdAt);
        s.depthFrameCount = o.optInt("depthFrameCount", 0);
        s.rawPointSamples = o.optInt("rawPointSamples", 0);
        s.voxelPointCount = o.optInt("voxelPointCount", 0);
        s.directionCoveragePercent = o.optInt("directionCoveragePercent", 0);
        s.roomYawRad = o.optDouble("roomYawRad", 0.0);
        s.observedFloorAreaM2 = o.optDouble("observedFloorAreaM2", 0.0);
        s.floorObservationRatio = o.optDouble("floorObservationRatio", 0.0);
        s.rawPointScale = o.optDouble("rawPointScale", 1.0);
        s.roomBounds = RoomBounds.fromJson(o.optJSONObject("roomBounds"));
        s.areaCheck = AreaCheckResult.fromJson(o.optJSONObject("areaCheck"));
        s.apartmentPlan = ApartmentPlan.fromJson(o.optJSONObject("apartmentPlan"));
        s.latestAutoApartmentPlan = ApartmentPlan.fromJson(o.optJSONObject("latestAutoApartmentPlan"));
        s.precisionReport = PrecisionReport.fromJson(o.optJSONObject("precisionReport"));
        JSONArray aa = o.optJSONArray("measurementAnchors");
        if (aa != null) for (int i=0;i<aa.length();i++) {
            MeasurementAnchor a=MeasurementAnchor.fromJson(aa.optJSONObject(i));
            if(a!=null)s.measurementAnchors.add(a);
        }

        JSONArray ss = o.optJSONArray("surfaces");
        if (ss != null) for (int i=0;i<ss.length();i++) {
            JSONObject x=ss.optJSONObject(i); if(x!=null)s.surfaces.add(SurfaceObservation.fromJson(x));
        }
        JSONArray se = o.optJSONArray("semanticEntities");
        if (se != null) for (int i=0;i<se.length();i++) {
            SemanticEntity e=SemanticEntity.fromJson(se.optJSONObject(i)); if(e!=null)s.semanticEntities.add(e);
        }
        JSONArray rr = o.optJSONArray("reviewIssues");
        if (rr != null) for (int i=0;i<rr.length();i++) {
            JSONObject x=rr.optJSONObject(i); if(x!=null)s.reviewIssues.add(ReviewIssue.fromJson(x));
        }
        JSONArray gg = o.optJSONArray("scanGaps");
        if (gg != null) for (int i=0;i<gg.length();i++) {
            JSONObject x=gg.optJSONObject(i); if(x!=null)s.scanGaps.add(ScanGap.fromJson(x));
        }
        JSONArray pp = o.optJSONArray("scanPath");
        if (pp != null) for (int i=0;i<pp.length();i++) {
            ScanPathPoint x=ScanPathPoint.fromJson(pp.optJSONObject(i)); if(x!=null)s.scanPath.add(x);
        }
        return s;
    }
}
