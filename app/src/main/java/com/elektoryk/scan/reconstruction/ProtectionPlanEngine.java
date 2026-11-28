package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.*;
import java.util.*;

/**
 * Generates a FIELD DRAFT, not an electrical-code approval. Ratings remain explicitly
 * unverified until the electrician confirms them on the object.
 */
public class ProtectionPlanEngine {
    public FieldWorkPlan build(String projectId,FloorPlan floor,FieldWorkPlan previous){
        FieldWorkPlan out=new FieldWorkPlan();out.projectId=projectId;
        Map<Integer,CircuitProtectionPlan> old=new HashMap<>();
        if(previous!=null)for(CircuitProtectionPlan c:previous.circuits)old.put(c.circuitNo,c);
        Map<Integer,List<ElectricalPoint>> groups=new TreeMap<>();
        if(floor!=null)for(ElectricalPoint p:floor.points){if(p.type==ElectricalPoint.Type.PANEL)continue;List<ElectricalPoint> l=groups.get(p.circuitNo);if(l==null){l=new ArrayList<>();groups.put(p.circuitNo,l);}l.add(p);}
        for(Map.Entry<Integer,List<ElectricalPoint>> e:groups.entrySet()){
            int no=e.getKey();List<ElectricalPoint> pts=e.getValue();CircuitProtectionPlan c=new CircuitProtectionPlan();c.circuitNo=no;
            c.name=groupName(no,pts);c.cableKey=dominantCable(pts);for(ElectricalPoint p:pts)c.plannedLoadW+=Math.max(0,p.plannedLoadW);
            c.estimatedCurrentA=c.plannedLoadW>0?c.plannedLoadW/230.0:0;c.suggestedBreakerA=suggestBreaker(c.cableKey,c.estimatedCurrentA);c.poles=c.cableKey.startsWith("5x")?3:1;c.protection="VERIFY";if(c.estimatedCurrentA>c.suggestedBreakerA*0.90)c.note="Планове навантаження близьке/вище до чернеткового номіналу — перевір переріз, спосіб прокладки та захист.";
            CircuitProtectionPlan prev=old.get(no);
            if(prev!=null){
                if(prev.manualProtectionEdited){
                    c.suggestedBreakerA=prev.suggestedBreakerA;c.suggestedCurve=prev.suggestedCurve;c.poles=prev.poles;c.phase=prev.phase;c.protection=prev.protection;c.residualCurrentMa=prev.residualCurrentMa;c.verifiedByElectrician=prev.verifiedByElectrician;c.manualProtectionEdited=true;c.note=prev.note;
                }
                // Commissioning is factual field data. Never discard it during recalculation.
                c.testStatus=prev.testStatus;c.measuredVoltageV=prev.measuredVoltageV;c.insulationMohm=prev.insulationMohm;c.rcdTripMs=prev.rcdTripMs;c.loopImpedanceOhm=prev.loopImpedanceOhm;c.testVerifiedAt=prev.testVerifiedAt;c.testNote=prev.testNote;
            }
            out.circuits.add(c);
        }
        return out;
    }
    private String groupName(int no,List<ElectricalPoint> pts){String type="Точки";boolean light=true,socket=true;for(ElectricalPoint p:pts){if(p.type!=ElectricalPoint.Type.LIGHT&&p.type!=ElectricalPoint.Type.SWITCH)light=false;if(p.type!=ElectricalPoint.Type.SOCKET)socket=false;}if(light)type="Освітлення";else if(socket)type="Розетки";return "G"+String.format(java.util.Locale.US,"%02d",no)+" • "+type;}
    private String dominantCable(List<ElectricalPoint> pts){Map<String,Integer> m=new HashMap<>();for(ElectricalPoint p:pts){String k=p.cableKey==null||p.cableKey.isEmpty()?defaultCable(p.type):p.cableKey;Integer n=m.get(k);m.put(k,n==null?1:n+1);}String best="3x2.5";int n=-1;for(Map.Entry<String,Integer> e:m.entrySet())if(e.getValue()>n){n=e.getValue();best=e.getKey();}return best;}
    private String defaultCable(ElectricalPoint.Type t){return (t==ElectricalPoint.Type.LIGHT||t==ElectricalPoint.Type.SWITCH)?"3x1.5":"3x2.5";}
    private int suggestBreaker(String cable,double current){return cable.contains("1.5")?10:cable.contains("2.5")?16:cable.contains("4")?20:cable.contains("6")?32:16;}
}
