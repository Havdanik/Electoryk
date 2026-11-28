package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.ApartmentOpening;
import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.CableRoute;
import com.elektoryk.scan.model.CableRoutingResult;
import com.elektoryk.scan.model.ElectricalPoint;
import com.elektoryk.scan.model.ElectricalProject;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.ProjectSettings;
import com.elektoryk.scan.model.RoomZone;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * First professional routing foundation.
 *
 * It uses the room graph + door centers so a route between rooms follows known
 * transitions instead of drawing a straight line through arbitrary walls.
 * Horizontal portions are orthogonalized. Vertical rises/drops are accounted
 * separately according to the project's mounting strategy.
 */
public class CableRoutingEngine {

    public CableRoutingResult build(ElectricalProject project,ApartmentPlan apartment,FloorPlan floor){
        CableRoutingResult result=new CableRoutingResult();
        if(project==null||apartment==null||floor==null)return result;

        ElectricalPoint panel=null;
        for(ElectricalPoint p:floor.points)if(p.type==ElectricalPoint.Type.PANEL){panel=p;break;}
        if(panel==null){
            result.warnings.add("Не вказаний щит. Додай точку «Щит» на плані.");
            return result;
        }

        Map<Integer,List<ElectricalPoint>> circuits=new LinkedHashMap<>();
        for(ElectricalPoint p:floor.points){
            if(p==panel||p.type==ElectricalPoint.Type.PANEL)continue;
            int no=p.circuitNo;
            List<ElectricalPoint> list=circuits.get(no);
            if(list==null){list=new ArrayList<>();circuits.put(no,list);}
            list.add(p);
        }
        if(circuits.isEmpty()){
            result.warnings.add("Ще немає електроточок для розрахунку трас.");
            return result;
        }
        if(circuits.containsKey(0))result.warnings.add("Є точки без номера групи: вони рахуються як група 0.");

        for(Map.Entry<Integer,List<ElectricalPoint>> e:circuits.entrySet()){
            int circuit=e.getKey();
            List<ElectricalPoint> points=e.getValue();
            if(points.isEmpty())continue;

            CableRoute route=new CableRoute();
            route.circuitNo=circuit;
            route.reservePercent=project.settings.cableReservePercent;
            route.cableKey=dominantCable(points);

            if(project.settings.junctionMethod==ProjectSettings.JunctionMethod.HOME_RUN){
                buildHomeRuns(route,panel,points,apartment);
            }else{
                buildLoop(route,panel,points,apartment,project.settings.junctionMethod);
            }

            route.purchaseLengthM=route.rawLengthM*(1.0+route.reservePercent/100.0);
            result.routes.add(route);
            result.totalRawLengthM+=route.rawLengthM;
            result.totalPurchaseLengthM+=route.purchaseLengthM;
            Double old=result.purchaseByCable.get(route.cableKey);
            result.purchaseByCable.put(route.cableKey,(old==null?0:old)+route.purchaseLengthM);
        }

        Collections.sort(result.routes,new Comparator<CableRoute>(){
            @Override public int compare(CableRoute a,CableRoute b){return Integer.compare(a.circuitNo,b.circuitNo);}
        });
        return result;
    }

    private void buildHomeRuns(CableRoute out,ElectricalPoint panel,List<ElectricalPoint> points,ApartmentPlan apartment){
        for(ElectricalPoint target:points){
            List<double[]> waypoints=roomWaypoints(panel,target,apartment,out);
            appendOrthogonalPath(out,waypoints,"HOME_RUN");
            out.rawLengthM+=verticalToCeiling(panel,apartment)+verticalToCeiling(target,apartment);
            out.pointIds.add(target.id);
        }
    }

    private void buildLoop(CableRoute out,ElectricalPoint panel,List<ElectricalPoint> points,
                           ApartmentPlan apartment,ProjectSettings.JunctionMethod method){
        List<ElectricalPoint> remaining=new ArrayList<>(points);
        ElectricalPoint current=panel;
        boolean first=true;
        while(!remaining.isEmpty()){
            ElectricalPoint next=nearest(current,remaining);
            List<double[]> waypoints=roomWaypoints(current,next,apartment,out);
            appendOrthogonalPath(out,waypoints,"LOOP");
            if(first){
                out.rawLengthM+=verticalToCeiling(panel,apartment);
                first=false;
            }

            // For deep socket boxes / classic junction logic, cable normally drops from
            // the ceiling to the point. A following leg may require going back up.
            double drop=verticalToCeiling(next,apartment);
            out.rawLengthM+=drop;
            if(remaining.size()>1 && (method==ProjectSettings.JunctionMethod.DEEP_SOCKET_BOXES
                    ||method==ProjectSettings.JunctionMethod.JUNCTION_BOXES
                    ||method==ProjectSettings.JunctionMethod.COMBINED)){
                out.rawLengthM+=drop;
            }

            out.pointIds.add(next.id);
            current=next;
            remaining.remove(next);
        }
    }

    private ElectricalPoint nearest(ElectricalPoint from,List<ElectricalPoint> candidates){
        ElectricalPoint best=candidates.get(0);double dBest=Double.POSITIVE_INFINITY;
        for(ElectricalPoint p:candidates){
            double d=Math.abs(p.position.x-from.position.x)+Math.abs(p.position.z-from.position.z);
            if(d<dBest){dBest=d;best=p;}
        }
        return best;
    }

    private double verticalToCeiling(ElectricalPoint p,ApartmentPlan apartment){
        double ceiling=apartment.ceilingY>apartment.floorY+1.5?apartment.ceilingY-apartment.floorY:2.7;
        double h=p.heightFromFinishedFloorM>0?p.heightFromFinishedFloorM:p.position.y;
        return Math.max(0,ceiling-h);
    }

    private List<double[]> roomWaypoints(ElectricalPoint from,ElectricalPoint to,
                                         ApartmentPlan apartment,CableRoute route){
        List<double[]> pts=new ArrayList<>();
        pts.add(new double[]{from.position.x,from.position.z});

        if(from.roomId!=null&&to.roomId!=null&&!from.roomId.isEmpty()&&!to.roomId.isEmpty()
                &&!from.roomId.equals(to.roomId)){
            List<ApartmentOpening> doors=doorPath(apartment,from.roomId,to.roomId);
            if(doors.isEmpty()){
                addRouteWarning(route,"Немає підтвердженого дверного шляху між частиною точок. Чернеткова лінія потребує ручної перевірки — не використовуй її як трасу крізь стіну.");
            }else{
                for(ApartmentOpening d:doors)pts.add(new double[]{d.centerX(),d.centerZ()});
            }
        }else if(from.roomId==null||to.roomId==null||from.roomId.isEmpty()||to.roomId.isEmpty()){
            addRouteWarning(route,"Є електроточка без прив'язки до кімнати. Перевір її положення.");
        }

        pts.add(new double[]{to.position.x,to.position.z});
        return pts;
    }

    private void addRouteWarning(CableRoute route,String message){
        if(route==null||message==null||message.isEmpty())return;
        if(route.warning==null||route.warning.isEmpty())route.warning=message;
        else if(!route.warning.contains(message))route.warning=route.warning+" "+message;
    }

    private List<ApartmentOpening> doorPath(ApartmentPlan p,String start,String goal){
        if(start.equals(goal))return new ArrayList<>();
        Map<String,List<Edge>> graph=new HashMap<>();
        for(ApartmentOpening o:p.openings){
            if(!o.isWalkable()||o.roomAId.isEmpty()||o.roomBId.isEmpty())continue;
            addEdge(graph,o.roomAId,o.roomBId,o);addEdge(graph,o.roomBId,o.roomAId,o);
        }

        ArrayDeque<String> q=new ArrayDeque<>();q.add(start);
        Map<String,String> prevRoom=new HashMap<>();
        Map<String,ApartmentOpening> prevDoor=new HashMap<>();
        prevRoom.put(start,"");
        while(!q.isEmpty()){
            String r=q.removeFirst();
            if(goal.equals(r))break;
            List<Edge> edges=graph.get(r);if(edges==null)continue;
            for(Edge e:edges)if(!prevRoom.containsKey(e.to)){
                prevRoom.put(e.to,r);prevDoor.put(e.to,e.door);q.add(e.to);
            }
        }
        if(!prevRoom.containsKey(goal))return new ArrayList<>();

        ArrayList<ApartmentOpening> reverse=new ArrayList<>();
        String cur=goal;
        while(!cur.equals(start)){
            ApartmentOpening d=prevDoor.get(cur);if(d==null)break;
            reverse.add(d);cur=prevRoom.get(cur);
        }
        Collections.reverse(reverse);return reverse;
    }

    private void addEdge(Map<String,List<Edge>> g,String a,String b,ApartmentOpening door){
        List<Edge> l=g.get(a);if(l==null){l=new ArrayList<>();g.put(a,l);}l.add(new Edge(b,door));
    }

    private void appendOrthogonalPath(CableRoute out,List<double[]> pts,String kind){
        if(pts==null||pts.size()<2)return;
        double y=0;
        double[] first=pts.get(0);
        if(out.nodes.isEmpty())out.nodes.add(new CableRoute.RouteNode(first[0],y,first[1],kind));
        for(int i=1;i<pts.size();i++){
            double[] a=pts.get(i-1),b=pts.get(i);
            double dx=b[0]-a[0],dz=b[1]-a[1];
            // Pick the shorter-looking dogleg orientation based on continuity.
            double mx=b[0],mz=a[1];
            if(Math.abs(dx)>1e-6){
                out.nodes.add(new CableRoute.RouteNode(mx,y,mz,kind));
                out.rawLengthM+=Math.abs(dx);
            }
            if(Math.abs(dz)>1e-6){
                out.nodes.add(new CableRoute.RouteNode(b[0],y,b[1],kind));
                out.rawLengthM+=Math.abs(dz);
            }
        }
    }

    private String dominantCable(List<ElectricalPoint> pts){
        Map<String,Integer> count=new HashMap<>();
        for(ElectricalPoint p:pts){
            String k=p.cableKey==null||p.cableKey.isEmpty()?defaultCable(p.type):p.cableKey;
            Integer n=count.get(k);count.put(k,n==null?1:n+1);
        }
        String best="3x2.5";int nBest=-1;
        for(Map.Entry<String,Integer> e:count.entrySet())if(e.getValue()>nBest){best=e.getKey();nBest=e.getValue();}
        return best;
    }

    private String defaultCable(ElectricalPoint.Type t){
        if(t==ElectricalPoint.Type.LIGHT||t==ElectricalPoint.Type.SWITCH)return "3x1.5";
        return "3x2.5";
    }

    private static class Edge{
        final String to;final ApartmentOpening door;
        Edge(String to,ApartmentOpening door){this.to=to;this.door=door;}
    }
}
