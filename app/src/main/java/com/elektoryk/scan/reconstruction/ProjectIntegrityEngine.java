package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.*;
import java.util.HashSet;
import java.util.Set;

public class ProjectIntegrityEngine {
    public ProjectIntegrityReport check(ElectricalProject project, SceneSnapshot scene, FloorPlan floor,
                                        CableCutList cuts, FieldWorkPlan field) {
        ProjectIntegrityReport r=new ProjectIntegrityReport();
        if(project==null){r.error("Основний файл об'єкта відсутній або пошкоджений.");return r;}
        String id=project.id;
        if(id==null||id.trim().isEmpty())r.error("У об'єкта відсутній ID.");

        if(scene!=null){
            if(scene.projectId!=null&&!scene.projectId.isEmpty()&&!scene.projectId.equals(id))r.error("Scene належить іншому projectId.");
            if(scene.roomBounds!=null&&scene.roomBounds.isUsable()){
                if(!finite(scene.roomBounds.minX)||!finite(scene.roomBounds.maxX)||!finite(scene.roomBounds.floorY)||!finite(scene.roomBounds.ceilingY))r.error("Некоректні числові координати scan bounds.");
            }
            if(scene.apartmentPlan!=null){
                ApartmentPlan ap=scene.apartmentPlan;
                if(!finite(ap.cellM)||ap.cellM<=0||ap.cellM>2)r.error("Некоректний масштаб grid у плані.");
                if(!finite(ap.originX)||!finite(ap.originZ)||!finite(ap.floorY)||!finite(ap.ceilingY))r.error("План має NaN/Infinity у базовій системі координат.");
                if(ap.rows<0||ap.cols<0||ap.rows>10000||ap.cols>10000)r.error("Некоректний розмір footprint grid.");
                if(finite(ap.floorY)&&finite(ap.ceilingY)&&ap.ceilingY<=ap.floorY)r.warning("Стеля плану не вище підлоги.");
                Set<String> roomIds=new HashSet<>();
                for(RoomZone room:ap.rooms){
                    if(room==null){r.error("У списку кімнат є null-запис.");continue;}
                    if(room.id==null||room.id.isEmpty())r.warning("Є кімната без ID.");
                    else if(!roomIds.add(room.id))r.error("Дубльований room ID: "+room.id);
                    if(room.areaM2<0||!finite(room.areaM2))r.error("Некоректна площа кімнати "+room.displayName()+".");
                    if(!finite(room.minX)||!finite(room.maxX)||!finite(room.minZ)||!finite(room.maxZ)||room.minX>room.maxX||room.minZ>room.maxZ)r.error("Некоректний envelope кімнати "+room.displayName()+".");
                    if(!finite(room.centroidX)||!finite(room.centroidZ))r.error("Некоректний centroid кімнати "+room.displayName()+".");
                    for(PlanGridRun run:room.floorRuns){
                        if(run==null){r.error("Footprint кімнати містить null-run: "+room.displayName()+".");continue;}
                        if(run.startCol>run.endCol)r.error("Footprint кімнати має перевернутий run: "+room.displayName()+".");
                        if(ap.rows>0&&(run.row<0||run.row>=ap.rows))r.error("Footprint кімнати виходить за межі grid по рядку: "+room.displayName()+".");
                        if(ap.cols>0&&(run.startCol<0||run.endCol>=ap.cols))r.error("Footprint кімнати виходить за межі grid по колонці: "+room.displayName()+".");
                    }
                }
                Set<String> wallIds=new HashSet<>();
                for(PlanWallSegment w:ap.walls){
                    if(w==null){r.error("У списку стін є null-запис.");continue;}
                    if(w.id==null||w.id.isEmpty())r.warning("Є стіна без ID.");
                    else if(!wallIds.add(w.id))r.error("Дубльований wall ID: "+w.id);
                    if(!finite(w.x1)||!finite(w.z1)||!finite(w.x2)||!finite(w.z2)||!finite(w.thicknessM)||!finite(w.confidence))r.error("Стіна має NaN/Infinity.");
                    if(w.thicknessM<0||w.thicknessM>2)r.warning("Підозріла товщина стіни: "+w.id);
                }
                for(ApartmentOpening o:ap.openings){
                    if(o==null){r.error("У списку отворів є null-запис.");continue;}
                    if(o.wallId!=null&&!o.wallId.isEmpty()&&!wallIds.contains(o.wallId))r.warning("Отвір посилається на відсутню стіну: "+o.id);
                    if(o.roomAId!=null&&!o.roomAId.isEmpty()&&!roomIds.contains(o.roomAId))r.error("Отвір має осиротілий roomAId: "+o.id);
                    if(o.roomBId!=null&&!o.roomBId.isEmpty()&&!roomIds.contains(o.roomBId))r.error("Отвір має осиротілий roomBId: "+o.id);
                    if(!finite(o.x1)||!finite(o.z1)||!finite(o.x2)||!finite(o.z2)||!finite(o.bottomY)||!finite(o.topY)||!finite(o.widthM)||!finite(o.heightM)||!finite(o.confidence)||o.widthM<0||o.heightM<0||o.topY<o.bottomY)r.error("Отвір має некоректну геометрію: "+o.id);
                }
                for(FurnitureItem f:ap.furniture){
                    if(f==null){r.error("У списку меблів є null-запис.");continue;}
                    if(f.roomId!=null&&!f.roomId.isEmpty()&&!roomIds.contains(f.roomId))r.error("Меблі/об'єкт мають осиротілий roomId: "+f.name);
                    if(!finite(f.x)||!finite(f.y)||!finite(f.z)||!finite(f.widthM)||!finite(f.depthM)||!finite(f.heightM)||f.widthM<0||f.depthM<0||f.heightM<0)r.error("Меблі/об'єкт мають некоректну геометрію: "+f.name);
                    RoomZone owner=null;for(RoomZone room:ap.rooms)if(room!=null&&room.id!=null&&room.id.equals(f.roomId)){owner=room;break;}
                    if(owner!=null&&!RoomFootprint.contains(ap,owner,f.x,f.z,1))r.warning("Меблі/об'єкт лежать поза footprint своєї кімнати: "+f.name);
                }
            }
        } else r.warning("Скан ще відсутній.");

        if(floor!=null){
            if(floor.projectId!=null&&!floor.projectId.isEmpty()&&!floor.projectId.equals(id))r.error("FloorPlan належить іншому projectId.");
            Set<String> pointIds=new HashSet<>();
            for(ElectricalPoint p:floor.points){
                if(p==null){r.error("У списку електроточок є null-запис.");continue;}
                if(p.id==null||p.id.isEmpty())r.warning("Є електроточка без ID.");
                else if(!pointIds.add(p.id))r.error("Дубльований point ID: "+p.id);
                if(p.position==null||!finite(p.position.x)||!finite(p.position.y)||!finite(p.position.z))r.error("Електроточка має некоректні координати.");
                if(p.circuitNo<0||p.circuitNo>999)r.warning("Підозрілий номер групи: "+p.circuitNo);
            }
        }

        if(cuts!=null){
            if(cuts.projectId!=null&&!cuts.projectId.isEmpty()&&!cuts.projectId.equals(id))r.error("CUT-лист належить іншому projectId.");
            Set<String> codes=new HashSet<>();
            for(CableCutPiece p:cuts.pieces){
                if(p==null){r.error("У CUT-листі є null-запис.");continue;}
                if(p.code!=null&&!p.code.isEmpty()&&!codes.add(p.code))r.warning("Дубльований CUT-код: "+p.code);
                if(p.cutLengthM<0||!finite(p.cutLengthM))r.error("Некоректна довжина CUT у "+p.code);
                if(p.cutLengthM>500)r.warning("Незвично довгий відрізок "+p.code+": "+p.cutLengthM+" м.");
            }
        }

        if(field!=null){
            if(field.projectId!=null&&!field.projectId.isEmpty()&&!field.projectId.equals(id))r.error("FieldWorkPlan належить іншому projectId.");
            Set<Integer> circuits=new HashSet<>();
            for(CircuitProtectionPlan c:field.circuits){
                if(c==null){r.error("У плані захисту є null-запис.");continue;}
                if(c.circuitNo>0&&!circuits.add(c.circuitNo))r.warning("Дубльована група захисту: "+c.circuitNo);
                if(c.suggestedBreakerA<0||c.suggestedBreakerA>125)r.warning("Підозрілий номінал автомата у групі "+c.circuitNo+".");
            }
        }
        return r;
    }

    private boolean finite(double v){return !Double.isNaN(v)&&!Double.isInfinite(v);}
}
