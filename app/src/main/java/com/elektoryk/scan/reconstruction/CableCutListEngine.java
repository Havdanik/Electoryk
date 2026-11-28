package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.*;
import java.util.*;

public class CableCutListEngine {
    public CableCutList build(ElectricalProject project,ApartmentPlan apartment,FloorPlan floor,CableCutList previous){
        CableCutList out=new CableCutList();if(project==null||apartment==null||floor==null)return out;out.projectId=project.id;if(previous!=null){out.sourceRollM=previous.sourceRollM;out.estimatedWasteM=previous.estimatedWasteM;}
        Map<String,CableCutPiece> old=new HashMap<>();if(previous!=null)for(CableCutPiece p:previous.pieces)old.put(p.stableKey,p);
        ElectricalPoint panel=findPanel(floor);if(panel==null)return out;
        Map<Integer,List<ElectricalPoint>> groups=new TreeMap<>();for(ElectricalPoint p:floor.points){if(p==panel||p.type==ElectricalPoint.Type.PANEL)continue;List<ElectricalPoint> l=groups.get(p.circuitNo);if(l==null){l=new ArrayList<>();groups.put(p.circuitNo,l);}l.add(p);}
        Map<String,String> labels=buildPointLabels(apartment,floor);
        for(Map.Entry<Integer,List<ElectricalPoint>> e:groups.entrySet()){
            int circuit=e.getKey();List<ElectricalPoint> pts=order(panel,e.getValue());int piece=1;
            if(project.settings.junctionMethod==ProjectSettings.JunctionMethod.HOME_RUN){
                for(ElectricalPoint target:pts){CableCutPiece cp=create(project,apartment,floor,panel,target,circuit,piece++,true,labels);mergeState(cp,old.get(cp.stableKey));out.pieces.add(cp);out.totalCutM+=cp.cutLengthM;}
            }else{
                ElectricalPoint from=panel;for(ElectricalPoint target:pts){CableCutPiece cp=create(project,apartment,floor,from,target,circuit,piece++,from==panel,labels);mergeState(cp,old.get(cp.stableKey));out.pieces.add(cp);out.totalCutM+=cp.cutLengthM;from=target;}
            }
        }
        return out;
    }

    private CableCutPiece create(ElectricalProject project,ApartmentPlan a,FloorPlan floor,ElectricalPoint from,ElectricalPoint to,int circuit,int index,boolean fromPanel,Map<String,String> labels){
        CableCutPiece p=new CableCutPiece();p.circuitNo=circuit;p.code=String.format(java.util.Locale.US,"G%02d-%02d",Math.max(0,circuit),index);p.fromPointId=from.id;p.toPointId=to.id;p.stableKey=circuit+":"+from.id+":"+to.id;
        p.cableKey=(to.cableKey==null||to.cableKey.isEmpty())?defaultCable(to.type):to.cableKey;p.fromRoom=roomName(a,from.roomId);p.toRoom=roomName(a,to.roomId);
        p.fromLabel=fromPanel?"ЩИТ":labelFor(labels,from);p.toLabel=labelFor(labels,to);
        RouteMeasure m=measure(project.settings,a,from,to);p.routeLengthM=m.route;p.wallPassages=m.passages;p.warning=m.warning;
        p.fixedReserveM=(fromPanel?Math.max(0,project.settings.panelReserveM):Math.max(0,project.settings.endpointReserveM))+Math.max(0,project.settings.endpointReserveM)+m.passages*Math.max(0,project.settings.passageReserveM);
        p.percentReserve=Math.max(0,project.settings.cableReservePercent);p.cutLengthM=roundUp((p.routeLengthM+p.fixedReserveM)*(1.0+p.percentReserve/100.0),0.1);
        String cut=String.format(java.util.Locale.US,"%.1fm",p.cutLengthM);p.startSticker=p.code+" A | "+p.fromLabel+" > "+p.toLabel+" | "+p.cableKey+" | "+cut;p.endSticker=p.code+" B | "+p.toLabel+" < "+p.fromLabel+" | "+p.cableKey+" | "+cut;return p;
    }

    private RouteMeasure measure(ProjectSettings s,ApartmentPlan a,ElectricalPoint from,ElectricalPoint to){
        RouteMeasure m=new RouteMeasure();double plane=planeY(s,a);List<double[]> way=new ArrayList<>();way.add(new double[]{from.position.x,from.position.z});
        List<ApartmentOpening> doors=new ArrayList<>();
        if(from.roomId!=null&&to.roomId!=null&&!from.roomId.isEmpty()&&!to.roomId.isEmpty()&&!from.roomId.equals(to.roomId)){
            doors=doorPath(a,from.roomId,to.roomId);m.passages=doors.size();
            if(doors.isEmpty())m.warning="Між кімнатами немає підтвердженого дверного маршруту — CUT є оцінкою, перевір трасу перед нарізкою.";
            for(ApartmentOpening d:doors)way.add(new double[]{d.centerX(),d.centerZ()});
        }
        way.add(new double[]{to.position.x,to.position.z});for(int i=1;i<way.size();i++)m.route+=Math.abs(way.get(i)[0]-way.get(i-1)[0])+Math.abs(way.get(i)[1]-way.get(i-1)[1]);
        m.route+=Math.abs(plane-height(from))+Math.abs(plane-height(to));
        for(ApartmentOpening d:doors){PlanWallSegment w=findWall(a,d.wallId);if(w!=null)m.route+=Math.max(.05,w.thicknessM);}return m;
    }

    private List<ApartmentOpening> doorPath(ApartmentPlan p,String start,String goal){List<ApartmentOpening> none=new ArrayList<>();if(start==null||goal==null||start.isEmpty()||goal.isEmpty()||start.equals(goal))return none;Map<String,List<Edge>> g=new HashMap<>();for(ApartmentOpening o:p.openings){if(!o.isWalkable()||o.roomAId.isEmpty()||o.roomBId.isEmpty())continue;add(g,o.roomAId,o.roomBId,o);add(g,o.roomBId,o.roomAId,o);}ArrayDeque<String> q=new ArrayDeque<>();Map<String,String> prev=new HashMap<>();Map<String,ApartmentOpening> pd=new HashMap<>();q.add(start);prev.put(start,"");while(!q.isEmpty()){String r=q.removeFirst();if(goal.equals(r))break;List<Edge> es=g.get(r);if(es==null)continue;for(Edge e:es)if(!prev.containsKey(e.to)){prev.put(e.to,r);pd.put(e.to,e.door);q.add(e.to);}}if(!prev.containsKey(goal))return none;List<ApartmentOpening> rev=new ArrayList<>();String cur=goal;while(!cur.equals(start)){ApartmentOpening d=pd.get(cur);if(d==null)break;rev.add(d);cur=prev.get(cur);}Collections.reverse(rev);return rev;}
    private void add(Map<String,List<Edge>> g,String a,String b,ApartmentOpening d){List<Edge> l=g.get(a);if(l==null){l=new ArrayList<>();g.put(a,l);}l.add(new Edge(b,d));}
    private List<ElectricalPoint> order(ElectricalPoint start,List<ElectricalPoint> src){List<ElectricalPoint> rem=new ArrayList<>(src),out=new ArrayList<>();ElectricalPoint cur=start;while(!rem.isEmpty()){ElectricalPoint best=rem.get(0);double bd=Double.POSITIVE_INFINITY;for(ElectricalPoint p:rem){double d=Math.abs(p.position.x-cur.position.x)+Math.abs(p.position.z-cur.position.z);if(d<bd){bd=d;best=p;}}out.add(best);rem.remove(best);cur=best;}return out;}
    private Map<String,String> buildPointLabels(ApartmentPlan a,FloorPlan f){Map<String,String> out=new HashMap<>(),seq=new HashMap<>();for(ElectricalPoint p:f.points){if(p.type==ElectricalPoint.Type.PANEL){out.put(p.id,"ЩИТ");continue;}String room=roomName(a,p.roomId),base=typeShort(p.type),key=p.roomId+":"+base;int n=seq.containsKey(key)?Integer.parseInt(seq.get(key))+1:1;seq.put(key,String.valueOf(n));out.put(p.id,(room.isEmpty()?"":room+" ")+base+n);}return out;}
    private String labelFor(Map<String,String> labels,ElectricalPoint p){String s=labels.get(p.id);return s==null?typeShort(p.type):s;}
    private String roomName(ApartmentPlan a,String id){if(a==null||id==null)return "";for(RoomZone r:a.rooms)if(id.equals(r.id))return r.displayName();return "";}
    private String typeShort(ElectricalPoint.Type t){switch(t){case SOCKET:return "R";case SWITCH:return "SW";case LIGHT:return "L";case TV:return "TV";case ETHERNET:return "LAN";case APPLIANCE:return "A";case CAMERA:return "CAM";case SENSOR:return "S";case SMART_HOME:return "SMART";default:return "P";}}
    private String defaultCable(ElectricalPoint.Type t){return (t==ElectricalPoint.Type.LIGHT||t==ElectricalPoint.Type.SWITCH)?"3x1.5":"3x2.5";}
    private ElectricalPoint findPanel(FloorPlan f){for(ElectricalPoint p:f.points)if(p.type==ElectricalPoint.Type.PANEL)return p;return null;}
    private PlanWallSegment findWall(ApartmentPlan a,String id){if(id==null)return null;for(PlanWallSegment w:a.walls)if(id.equals(w.id))return w;return null;}
    private double height(ElectricalPoint e){return e.heightFromFinishedFloorM>0?e.heightFromFinishedFloorM:e.position.y;}
    private double planeY(ProjectSettings s,ApartmentPlan a){double h=(a.ceilingY>a.floorY+1.5)?a.ceilingY-a.floorY:2.7;switch(s.wiringMethod){case FLOOR:return a.floorY+.05;case WALLS:return a.floorY+Math.min(h-.2,2.3);default:return a.floorY+Math.max(1.8,h-Math.max(.05,s.ceilingOffsetM));}}
    private double roundUp(double v,double step){return Math.ceil(v/step)*step;}
    private void mergeState(CableCutPiece dst,CableCutPiece old){if(old==null)return;dst.cutDone=old.cutDone;dst.labelsPrinted=old.labelsPrinted;dst.installed=old.installed;dst.spool=old.spool;dst.note=old.note;}
    private static class RouteMeasure{double route;int passages;String warning="";}
    private static class Edge{String to;ApartmentOpening door;Edge(String t,ApartmentOpening d){to=t;door=d;}}
}
