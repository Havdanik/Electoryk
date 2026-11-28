package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.ApartmentOpening;
import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.CableRoute;
import com.elektoryk.scan.model.CableRoutingResult;
import com.elektoryk.scan.model.ElectricalPoint;
import com.elektoryk.scan.model.ElectricalProject;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.ForbiddenZone;
import com.elektoryk.scan.model.InstallationMaterialEstimate;
import com.elektoryk.scan.model.InstallationRoutingResult;
import com.elektoryk.scan.model.MountingSegment;
import com.elektoryk.scan.model.PlanWallSegment;
import com.elektoryk.scan.model.ProjectSettings;
import com.elektoryk.scan.model.WallPenetration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * v0.7 installation routing layer.
 *
 * Converts logical circuit routes into actual installation corridors:
 * - ceiling / wall / floor mounting plane
 * - vertical rises/drops
 * - forbidden-zone detours
 * - wall passages near known door/wall crossings
 * - fixed reserves at panel/endpoints/passages
 * - shared-bundle aggregation
 * - conduit/tray/chase/fastener material estimates
 *
 * This is still a planning engine, not a substitute for on-site engineering judgement.
 */
public class InstallationRoutingEngine {

    public InstallationRoutingResult build(ElectricalProject project, ApartmentPlan apartment, FloorPlan floor) {
        InstallationRoutingResult out=new InstallationRoutingResult();
        if(project==null||apartment==null||floor==null)return out;

        CableRoutingResult logical=new CableRoutingEngine().build(project,apartment,floor);
        out.warnings.addAll(logical.warnings);
        if(logical.routes.isEmpty())return out;

        ElectricalPoint panel=findPanel(floor);
        if(panel==null){
            out.warnings.add("Немає щита — монтажні траси неможливо прив'язати.");
            return out;
        }

        double planeY=mountingPlaneY(project.settings,apartment);
        Map<String,ElectricalPoint> pointById=new HashMap<>();
        for(ElectricalPoint e:floor.points)pointById.put(e.id,e);

        for(CableRoute logicalRoute:logical.routes){
            CableRoute route=new CableRoute();
            route.circuitNo=logicalRoute.circuitNo;
            route.cableKey=logicalRoute.cableKey;
            route.reservePercent=project.settings.cableReservePercent;
            route.pointIds.addAll(logicalRoute.pointIds);
            route.warning=logicalRoute.warning;

            // Horizontal logical route -> installation plane segments with obstacle detours.
            List<CableRoute.RouteNode> elevated=elevateAndDetour(
                    logicalRoute.nodes, planeY, apartment.forbiddenZones, route.warning, out.warnings);

            if(!elevated.isEmpty()){
                route.nodes.addAll(elevated);
                for(int i=1;i<elevated.size();i++){
                    CableRoute.RouteNode a=elevated.get(i-1),b=elevated.get(i);
                    double len=distance(a,b);
                    if(len<1e-6)continue;
                    MountingSegment s=new MountingSegment();
                    s.kind=horizontalKind(project.settings);
                    s.x1=a.x;s.y1=planeY;s.z1=a.z;s.x2=b.x;s.y2=planeY;s.z2=b.z;
                    s.circuits.add(route.circuitNo);s.cableCount=1;
                    s.protection=project.settings.cableProtection.name();
                    out.mountingSegments.add(s);
                    route.rawLengthM+=len;
                }
            }

            // Vertical panel rise.
            double panelH=height(panel);
            double panelVertical=Math.abs(planeY-panelH);
            if(panelVertical>0.02){
                MountingSegment s=verticalSegment(panel,planeY,route.circuitNo);
                out.mountingSegments.add(s);
                route.rawLengthM+=s.lengthM();
            }
            route.rawLengthM+=Math.max(0,project.settings.panelReserveM);

            // Vertical drops/rises and endpoint reserves.
            for(String id:route.pointIds){
                ElectricalPoint ep=pointById.get(id);
                if(ep==null)continue;
                double v=Math.abs(planeY-height(ep));
                if(v>0.02){
                    MountingSegment s=verticalSegment(ep,planeY,route.circuitNo);
                    out.mountingSegments.add(s);
                    route.rawLengthM+=s.lengthM();
                }
                route.rawLengthM+=Math.max(0,project.settings.endpointReserveM);
            }

            // Known wall crossings near door transitions.
            List<WallPenetration> routePassages=findPassages(
                    route, apartment, project.settings, planeY);
            for(WallPenetration wp:routePassages){
                out.penetrations.add(wp);
                route.rawLengthM+=wp.wallThicknessM+wp.extraCableM;
                MountingSegment s=new MountingSegment();
                s.kind=MountingSegment.Kind.PENETRATION;
                s.x1=wp.x;s.x2=wp.x;s.z1=wp.z;s.z2=wp.z;
                s.y1=planeY;s.y2=planeY;
                s.wallId=wp.wallId;s.circuits.add(route.circuitNo);s.cableCount=1;
                s.protection=project.settings.cableProtection.name();
                out.mountingSegments.add(s);
            }

            route.purchaseLengthM=route.rawLengthM*(1.0+Math.max(0,route.reservePercent)/100.0);
            out.circuitRoutes.add(route);
            out.totalRawCableM+=route.rawLengthM;
            out.totalPurchaseCableM+=route.purchaseLengthM;
            addCable(out.materials,route.cableKey,route.purchaseLengthM);
        }

        aggregatePhysicalPenetrations(out);
        if(project.settings.bundleSharedSegments) aggregateSharedSegments(out,project.settings.maxBundleCables);
        estimateMaterials(out,project,apartment,floor);

        Collections.sort(out.circuitRoutes,new Comparator<CableRoute>(){
            @Override public int compare(CableRoute a,CableRoute b){
                return Integer.compare(a.circuitNo,b.circuitNo);
            }
        });
        return out;
    }

    private double mountingPlaneY(ProjectSettings s,ApartmentPlan a){
        double height=(a.ceilingY>a.floorY+1.5)?a.ceilingY-a.floorY:2.7;
        switch(s.wiringMethod){
            case FLOOR:return a.floorY+0.05;
            case WALLS:return a.floorY+Math.min(height-0.20,2.30);
            case COMBINED:
            case CEILING_VERTICAL_DROPS:
            default:return a.floorY+Math.max(1.8,height-Math.max(0.05,s.ceilingOffsetM));
        }
    }

    private MountingSegment.Kind horizontalKind(ProjectSettings s){
        switch(s.wiringMethod){
            case FLOOR:return MountingSegment.Kind.FLOOR;
            case WALLS:return MountingSegment.Kind.WALL_HORIZONTAL;
            default:return MountingSegment.Kind.CEILING;
        }
    }

    private MountingSegment verticalSegment(ElectricalPoint p,double planeY,int circuit){
        MountingSegment s=new MountingSegment();
        s.kind=MountingSegment.Kind.WALL_VERTICAL;
        s.x1=s.x2=p.position.x;s.z1=s.z2=p.position.z;
        s.y1=height(p);s.y2=planeY;s.wallId=p.wallId==null?"":p.wallId;
        s.circuits.add(circuit);s.cableCount=1;
        return s;
    }

    private List<CableRoute.RouteNode> elevateAndDetour(List<CableRoute.RouteNode> src,double y,
                                                        List<ForbiddenZone> zones,String routeWarning,
                                                        List<String> warnings){
        List<CableRoute.RouteNode> out=new ArrayList<>();
        if(src==null||src.isEmpty())return out;
        out.add(new CableRoute.RouteNode(src.get(0).x,y,src.get(0).z,"MOUNT"));
        for(int i=1;i<src.size();i++){
            CableRoute.RouteNode a=out.get(out.size()-1);
            CableRoute.RouteNode b=new CableRoute.RouteNode(src.get(i).x,y,src.get(i).z,"MOUNT");

            ForbiddenZone hit=firstHit(a,b,zones,y);
            if(hit==null){
                appendIfDifferent(out,b);
                continue;
            }

            List<CableRoute.RouteNode> detour=detourAround(a,b,hit,y);
            if(detour==null||detour.isEmpty()){
                appendIfDifferent(out,b);
                warnings.add("Траса перетинає заборонену зону "+hit.type.name()+" — автоматичний обхід не вдалось побудувати.");
            }else{
                for(CableRoute.RouteNode n:detour)appendIfDifferent(out,n);
                appendIfDifferent(out,b);
                warnings.add("Автоматично додано обхід забороненої зони "+hit.type.name()+".");
            }
        }
        return out;
    }

    private ForbiddenZone firstHit(CableRoute.RouteNode a,CableRoute.RouteNode b,
                                   List<ForbiddenZone> zones,double y){
        if(zones==null)return null;
        for(ForbiddenZone z:zones){
            if(y<z.minY||y>z.maxY)continue;
            if(segmentIntersectsRect(a.x,a.z,b.x,b.z,z.minX,z.minZ,z.maxX,z.maxZ))return z;
        }
        return null;
    }

    private List<CableRoute.RouteNode> detourAround(CableRoute.RouteNode a,CableRoute.RouteNode b,
                                                   ForbiddenZone z,double y){
        double gap=0.15;
        double minX=z.minX-gap,maxX=z.maxX+gap,minZ=z.minZ-gap,maxZ=z.maxZ+gap;
        List<List<CableRoute.RouteNode>> candidates=new ArrayList<>();

        // Four orthogonal detour options around rectangle.
        candidates.add(path(y,new double[][]{{a.x,a.z},{a.x,minZ},{b.x,minZ},{b.x,b.z}}));
        candidates.add(path(y,new double[][]{{a.x,a.z},{a.x,maxZ},{b.x,maxZ},{b.x,b.z}}));
        candidates.add(path(y,new double[][]{{a.x,a.z},{minX,a.z},{minX,b.z},{b.x,b.z}}));
        candidates.add(path(y,new double[][]{{a.x,a.z},{maxX,a.z},{maxX,b.z},{b.x,b.z}}));

        List<CableRoute.RouteNode> best=null;double bestLen=Double.POSITIVE_INFINITY;
        for(List<CableRoute.RouteNode> c:candidates){
            boolean bad=false;
            for(int i=1;i<c.size();i++){
                CableRoute.RouteNode u=c.get(i-1),v=c.get(i);
                if(segmentIntersectsRect(u.x,u.z,v.x,v.z,z.minX,z.minZ,z.maxX,z.maxZ)){bad=true;break;}
            }
            if(bad)continue;
            double len=0;for(int i=1;i<c.size();i++)len+=distance(c.get(i-1),c.get(i));
            if(len<bestLen){bestLen=len;best=c;}
        }
        if(best==null)return null;
        // Skip first node, caller already has it.
        return new ArrayList<>(best.subList(1,best.size()));
    }

    private List<CableRoute.RouteNode> path(double y,double[][] pts){
        List<CableRoute.RouteNode> out=new ArrayList<>();
        for(double[] p:pts)out.add(new CableRoute.RouteNode(p[0],y,p[1],"DETOUR"));
        return out;
    }

    private boolean segmentIntersectsRect(double x1,double z1,double x2,double z2,
                                          double minX,double minZ,double maxX,double maxZ){
        // Axis-aligned routing segments in v0.7.
        if(Math.abs(z1-z2)<1e-6){
            double lo=Math.min(x1,x2),hi=Math.max(x1,x2);
            return z1>=minZ&&z1<=maxZ&&hi>=minX&&lo<=maxX;
        }
        if(Math.abs(x1-x2)<1e-6){
            double lo=Math.min(z1,z2),hi=Math.max(z1,z2);
            return x1>=minX&&x1<=maxX&&hi>=minZ&&lo<=maxZ;
        }
        // Conservative bounding-box fallback.
        return Math.max(Math.min(x1,x2),minX)<=Math.min(Math.max(x1,x2),maxX)
                &&Math.max(Math.min(z1,z2),minZ)<=Math.min(Math.max(z1,z2),maxZ);
    }

    private List<WallPenetration> findPassages(CableRoute route,ApartmentPlan a,
                                               ProjectSettings s,double y){
        List<WallPenetration> out=new ArrayList<>();
        Set<String> used=new LinkedHashSet<>();
        if(route.nodes.size()<2)return out;
        for(ApartmentOpening o:a.openings){
            if(!o.isWalkable())continue;
            if(o.wallId==null||o.wallId.isEmpty())continue;
            boolean near=false;
            for(int i=1;i<route.nodes.size();i++){
                CableRoute.RouteNode n1=route.nodes.get(i-1),n2=route.nodes.get(i);
                if(distancePointToSegment(o.centerX(),o.centerZ(),n1.x,n1.z,n2.x,n2.z)<0.25){
                    near=true;break;
                }
            }
            if(!near||used.contains(o.wallId))continue;
            PlanWallSegment wall=findWall(a,o.wallId);
            if(wall==null)continue;

            WallPenetration p=new WallPenetration();
            p.wallId=wall.id;p.x=o.centerX();p.z=o.centerZ();p.y=y;
            p.wallThicknessM=Math.max(0.05,wall.thicknessM);
            p.extraCableM=Math.max(0,s.passageReserveM);
            p.method=passageMethod(s,o).name();
            p.circuits.add(route.circuitNo);
            out.add(p);used.add(o.wallId);
        }
        return out;
    }

    private ProjectSettings.WallPassageMode passageMethod(ProjectSettings s,ApartmentOpening o){
        if(s.wallPassageMode!=ProjectSettings.WallPassageMode.AUTO)return s.wallPassageMode;
        return o.isWalkable()
                ?ProjectSettings.WallPassageMode.ABOVE_DOOR
                :ProjectSettings.WallPassageMode.DIRECT_WALL;
    }

    private void aggregatePhysicalPenetrations(InstallationRoutingResult out){
        Map<String,WallPenetration> merged=new LinkedHashMap<>();
        for(WallPenetration p:out.penetrations){
            String key=p.wallId+":"+Math.round(p.x/0.08)+":"+Math.round(p.z/0.08)+":"+Math.round(p.y/0.10);
            WallPenetration m=merged.get(key);
            if(m==null){
                m=new WallPenetration();
                m.wallId=p.wallId;m.x=p.x;m.y=p.y;m.z=p.z;
                m.wallThicknessM=p.wallThicknessM;
                m.extraCableM=p.extraCableM;
                m.method=p.method;
                m.circuits.addAll(p.circuits);
                merged.put(key,m);
            }else{
                m.circuits.addAll(p.circuits);
                m.wallThicknessM=Math.max(m.wallThicknessM,p.wallThicknessM);
                m.extraCableM=Math.max(m.extraCableM,p.extraCableM);
            }
        }
        out.penetrations.clear();
        out.penetrations.addAll(merged.values());
    }

    private void aggregateSharedSegments(InstallationRoutingResult out,int maxBundle){
        Map<String,MountingSegment> agg=new LinkedHashMap<>();
        List<MountingSegment> verticalAndPenetration=new ArrayList<>();

        for(MountingSegment s:out.mountingSegments){
            if(s.kind==MountingSegment.Kind.WALL_VERTICAL||s.kind==MountingSegment.Kind.PENETRATION){
                verticalAndPenetration.add(s);continue;
            }
            String key=segmentKey(s);
            MountingSegment a=agg.get(key);
            if(a==null){
                a=copySegment(s);agg.put(key,a);
            }else{
                a.circuits.addAll(s.circuits);
                a.cableCount+=Math.max(1,s.cableCount);
                a.shared=a.circuits.size()>1||a.cableCount>1;
            }
        }

        out.mountingSegments.clear();
        for(MountingSegment s:agg.values()){
            if(s.cableCount>maxBundle){
                out.warnings.add("На одному коридорі виходить "+s.cableCount
                        +" кабелів — перевір допустимий пучок/лоток.");
            }
            out.mountingSegments.add(s);
        }
        out.mountingSegments.addAll(verticalAndPenetration);
    }

    private String segmentKey(MountingSegment s){
        double q=0.05;
        long x1=Math.round(s.x1/q),z1=Math.round(s.z1/q),x2=Math.round(s.x2/q),z2=Math.round(s.z2/q);
        if(x1>x2||(x1==x2&&z1>z2)){
            long t=x1;x1=x2;x2=t;t=z1;z1=z2;z2=t;
        }
        return s.kind.name()+":"+x1+":"+z1+":"+x2+":"+z2+":"+Math.round(s.y1/q);
    }

    private MountingSegment copySegment(MountingSegment s){
        MountingSegment n=new MountingSegment();
        n.kind=s.kind;n.x1=s.x1;n.y1=s.y1;n.z1=s.z1;n.x2=s.x2;n.y2=s.y2;n.z2=s.z2;
        n.wallId=s.wallId;n.circuits.addAll(s.circuits);n.cableCount=Math.max(1,s.cableCount);
        n.protection=s.protection;n.shared=s.shared;return n;
    }

    private void estimateMaterials(InstallationRoutingResult out,ElectricalProject project,
                                   ApartmentPlan apartment,FloorPlan floor){
        InstallationMaterialEstimate m=out.materials;
        double uniqueHorizontal=0,vertical=0,allInstalled=0;
        for(MountingSegment s:out.mountingSegments){
            if(s.kind==MountingSegment.Kind.PENETRATION)continue;
            double len=s.lengthM();allInstalled+=len;
            if(s.kind==MountingSegment.Kind.WALL_VERTICAL){
                vertical+=len;
                PlanWallSegment wall=findWall(apartment,s.wallId);
                if(wall==null||!"DRYWALL".equals(wall.materialKey))m.wallChaseM+=len;
            }else{
                uniqueHorizontal+=len;
                if(s.shared)m.bundledCorridorM+=len;
                if(s.kind==MountingSegment.Kind.FLOOR)m.floorRouteM+=len;
                if(s.kind==MountingSegment.Kind.WALL_HORIZONTAL)m.wallChaseM+=len;
            }
        }

        m.wallPenetrations=out.penetrations.size();
        for(ElectricalPoint e:floor.points){
            if(e.type==ElectricalPoint.Type.PANEL)continue;
            if(project.settings.junctionMethod==ProjectSettings.JunctionMethod.DEEP_SOCKET_BOXES){
                m.deepSocketBoxes+=Math.max(1,e.moduleCount);
            }
        }
        if(project.settings.junctionMethod==ProjectSettings.JunctionMethod.JUNCTION_BOXES){
            Set<String> roomCircuit=new LinkedHashSet<>();
            for(ElectricalPoint e:floor.points)if(e.type!=ElectricalPoint.Type.PANEL)
                roomCircuit.add(e.roomId+":"+e.circuitNo);
            m.junctionBoxes=roomCircuit.size();
        }

        switch(project.settings.cableProtection){
            case CORRUGATED:
                // each cable follows its own protected route
                m.corrugatedConduitM=Math.max(0,out.totalRawCableM-fixedReserveApprox(project,floor,out));
                break;
            case TRAY:
                m.trayM=uniqueHorizontal;
                m.corrugatedConduitM=vertical;
                break;
            case MIXED:
                for(MountingSegment s:out.mountingSegments){
                    if(s.kind==MountingSegment.Kind.PENETRATION)continue;
                    if(s.kind==MountingSegment.Kind.WALL_VERTICAL)m.corrugatedConduitM+=s.lengthM();
                    else if(s.shared)m.trayM+=s.lengthM();
                    else m.corrugatedConduitM+=s.lengthM();
                }
                break;
            case NONE:
            default:break;
        }

        double fastenerRoute=m.trayM+m.corrugatedConduitM;
        if(project.settings.cableProtection==ProjectSettings.CableProtection.NONE)fastenerRoute=allInstalled;
        double spacing=Math.max(0.15,project.settings.fastenerSpacingM);
        m.fasteners=(int)Math.ceil(fastenerRoute/spacing)+m.wallPenetrations*2;
        m.unbundledCablePathM=Math.max(0,out.totalRawCableM-m.bundledCorridorM);
    }

    private double fixedReserveApprox(ElectricalProject project,FloorPlan floor,InstallationRoutingResult out){
        int endpoints=0;for(ElectricalPoint e:floor.points)if(e.type!=ElectricalPoint.Type.PANEL)endpoints++;
        double panel=out.circuitRoutes.size()*Math.max(0,project.settings.panelReserveM);
        double end=endpoints*Math.max(0,project.settings.endpointReserveM);
        double passage=out.penetrations.size()*Math.max(0,project.settings.passageReserveM);
        return panel+end+passage;
    }

    private ElectricalPoint findPanel(FloorPlan f){
        for(ElectricalPoint e:f.points)if(e.type==ElectricalPoint.Type.PANEL)return e;
        return null;
    }
    private PlanWallSegment findWall(ApartmentPlan a,String id){
        if(id==null||id.isEmpty())return null;
        for(PlanWallSegment w:a.walls)if(id.equals(w.id))return w;
        return null;
    }
    private double height(ElectricalPoint e){
        return e.heightFromFinishedFloorM>0?e.heightFromFinishedFloorM:e.position.y;
    }
    private double distance(CableRoute.RouteNode a,CableRoute.RouteNode b){
        return Math.sqrt((b.x-a.x)*(b.x-a.x)+(b.y-a.y)*(b.y-a.y)+(b.z-a.z)*(b.z-a.z));
    }
    private void appendIfDifferent(List<CableRoute.RouteNode> list,CableRoute.RouteNode n){
        if(list.isEmpty()){list.add(n);return;}
        CableRoute.RouteNode a=list.get(list.size()-1);
        if(Math.abs(a.x-n.x)>1e-6||Math.abs(a.z-n.z)>1e-6||Math.abs(a.y-n.y)>1e-6)list.add(n);
    }
    private double distancePointToSegment(double px,double pz,double ax,double az,double bx,double bz){
        double dx=bx-ax,dz=bz-az,den=dx*dx+dz*dz;
        if(den<1e-9)return Math.hypot(px-ax,pz-az);
        double t=((px-ax)*dx+(pz-az)*dz)/den;t=Math.max(0,Math.min(1,t));
        return Math.hypot(px-(ax+t*dx),pz-(az+t*dz));
    }
    private void addCable(InstallationMaterialEstimate m,String cable,double len){
        Double old=m.cableM.get(cable);
        m.cableM.put(cable,(old==null?0:old)+len);
    }
}
