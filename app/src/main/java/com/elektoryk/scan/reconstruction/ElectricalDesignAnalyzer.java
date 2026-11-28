package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.CircuitAnalysis;
import com.elektoryk.scan.model.ElectricalDesignReport;
import com.elektoryk.scan.model.ElectricalPoint;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.PlanWallSegment;
import com.elektoryk.scan.model.RoomZone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ElectricalDesignAnalyzer {

    public ElectricalDesignReport analyze(ApartmentPlan plan,FloorPlan floor){
        ElectricalDesignReport r=new ElectricalDesignReport();
        if(floor==null)return r;
        r.totalPoints=floor.points.size();

        Map<Integer,CircuitAnalysis> groups=new LinkedHashMap<>();
        for(ElectricalPoint p:floor.points){
            if(p.type==ElectricalPoint.Type.PANEL){r.panelCount++;continue;}
            if(p.circuitNo<=0)r.unassignedCircuitPoints++;
            if(p.roomId==null||p.roomId.isEmpty()||findRoom(plan,p.roomId)==null)r.unboundRoomPoints++;
            if(isWallMounted(p.type)&&(p.wallId==null||p.wallId.isEmpty()||findWall(plan,p.wallId)==null))r.unboundWallPoints++;

            CircuitAnalysis c=groups.get(p.circuitNo);
            if(c==null){c=new CircuitAnalysis();c.circuitNo=p.circuitNo;groups.put(p.circuitNo,c);}
            c.pointCount++;c.moduleCount+=Math.max(1,p.moduleCount);
            c.plannedLoadW+=Math.max(0,p.plannedLoadW);
            r.totalPlannedLoadW+=Math.max(0,p.plannedLoadW);
            if(p.cableKey!=null&&!p.cableKey.isEmpty())c.cableKeys.add(p.cableKey);
            if(p.roomId!=null&&!p.roomId.isEmpty())c.roomIds.add(p.roomId);
        }

        // Near-duplicate geometry check.
        List<ElectricalPoint> pts=floor.points;
        for(int i=0;i<pts.size();i++){
            ElectricalPoint a=pts.get(i);if(a.type==ElectricalPoint.Type.PANEL)continue;
            for(int j=i+1;j<pts.size();j++){
                ElectricalPoint b=pts.get(j);if(b.type==ElectricalPoint.Type.PANEL)continue;
                if(a.type!=b.type)continue;
                double d=Math.hypot(a.position.x-b.position.x,a.position.z-b.position.z);
                if(d<0.06&&Math.abs(a.heightFromFinishedFloorM-b.heightFromFinishedFloorM)<0.05){
                    r.duplicateCandidates++;
                }
            }
        }

        for(CircuitAnalysis c:groups.values()){
            if(c.circuitNo==0)c.warnings.add("Точки цієї групи ще не призначені.");
            if(c.cableKeys.size()>1)c.warnings.add("У групі задано кілька різних типів кабелю — перевір задум.");
            if(c.plannedLoadW<=0)c.warnings.add("Планове навантаження не заповнено.");
            if(c.roomIds.size()>4)c.warnings.add("Група охоплює багато кімнат — перевір, чи це навмисно.");
            r.circuits.add(c);
        }
        Collections.sort(r.circuits,new Comparator<CircuitAnalysis>(){
            @Override public int compare(CircuitAnalysis a,CircuitAnalysis b){return Integer.compare(a.circuitNo,b.circuitNo);}
        });

        if(r.panelCount==0)r.warnings.add("Не поставлено щит.");
        if(r.panelCount>1)r.warnings.add("На плані кілька щитів. Якщо це навмисно — розділи маршрути по щитах у наступному проході.");
        if(r.unassignedCircuitPoints>0)r.warnings.add("Точок без номера групи: "+r.unassignedCircuitPoints+".");
        if(r.unboundRoomPoints>0)r.warnings.add("Точок без коректної кімнати: "+r.unboundRoomPoints+".");
        if(r.unboundWallPoints>0)r.warnings.add("Настінних точок без коректної стіни: "+r.unboundWallPoints+".");
        if(r.duplicateCandidates>0)r.warnings.add("Ймовірних дубльованих точок майже в одному місці: "+r.duplicateCandidates+".");

        int penalty=0;
        penalty+=Math.min(25,r.unassignedCircuitPoints*3);
        penalty+=Math.min(15,r.unboundRoomPoints*3);
        penalty+=Math.min(15,r.unboundWallPoints*2);
        penalty+=Math.min(15,r.duplicateCandidates*3);
        if(r.panelCount!=1)penalty+=15;
        for(CircuitAnalysis c:r.circuits)if(c.cableKeys.size()>1)penalty+=3;
        r.score=Math.max(0,100-penalty);
        return r;
    }

    private boolean isWallMounted(ElectricalPoint.Type t){
        return t!=ElectricalPoint.Type.LIGHT&&t!=ElectricalPoint.Type.SENSOR;
    }
    private RoomZone findRoom(ApartmentPlan p,String id){
        if(p==null||id==null)return null;
        for(RoomZone r:p.rooms)if(id.equals(r.id))return r;return null;
    }
    private PlanWallSegment findWall(ApartmentPlan p,String id){
        if(p==null||id==null)return null;
        for(PlanWallSegment w:p.walls)if(id.equals(w.id))return w;return null;
    }
}
