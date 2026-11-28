package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.ApartmentOpening;
import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.PlanGridRun;
import com.elektoryk.scan.model.PlanWallSegment;
import com.elektoryk.scan.model.RoomZone;
import com.elektoryk.scan.model.StructuralFeature;
import com.elektoryk.scan.model.FurnitureItem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ApartmentPlanEditorEngine {
    public enum SplitAxis { X, Z }

    public RoomZone findRoomAt(ApartmentPlan plan,double x,double z){
        if(plan==null)return null;
        int c=(int)Math.floor((x-plan.originX)/plan.cellM);
        int r=(int)Math.floor((z-plan.originZ)/plan.cellM);
        for(RoomZone room:plan.rooms){
            for(PlanGridRun run:room.floorRuns){
                if(run.row==r&&c>=run.startCol&&c<=run.endCol)return room;
            }
        }
        return null;
    }

    public boolean mergeRooms(ApartmentPlan plan,String aId,String bId){
        if(plan==null||aId==null||bId==null||aId.equals(bId))return false;
        RoomZone a=find(plan,aId),b=find(plan,bId);
        if(a==null||b==null)return false;

        boolean[][] cells=new boolean[Math.max(1,plan.rows)][Math.max(1,plan.cols)];
        addRoomCells(cells,a);addRoomCells(cells,b);

        RoomZone merged=fromCells(plan,cells,a.id);
        if(merged==null)return false;
        merged.type=a.type!=RoomZone.RoomType.UNKNOWN?a.type:b.type;
        merged.autoLabel=a.displayName();
        merged.confidence=Math.max(a.confidence,b.confidence);
        merged.narrowPassageLike=a.narrowPassageLike&&b.narrowPassageLike;
        // A topology edit must not silently reset the finishes the user already chose.
        copyFinishes(a,merged);

        List<ApartmentOpening> removeOpenings=new ArrayList<>();
        for(ApartmentOpening o:plan.openings){
            boolean connects=(aId.equals(o.roomAId)&&bId.equals(o.roomBId))
                    ||(bId.equals(o.roomAId)&&aId.equals(o.roomBId));
            if(connects){
                removeOpenings.add(o);
                continue;
            }
            if(bId.equals(o.roomAId))o.roomAId=aId;
            if(bId.equals(o.roomBId))o.roomBId=aId;
        }
        plan.openings.removeAll(removeOpenings);

        for(StructuralFeature f:plan.features)if(bId.equals(f.roomId))f.roomId=aId;
        // Furniture belongs to a room semantically as well as geometrically. Without this,
        // merging rooms left items pointing at a deleted room id and later room filters/kits
        // could no longer find them.
        for(FurnitureItem item:plan.furniture)if(bId.equals(item.roomId))item.roomId=aId;

        int ia=plan.rooms.indexOf(a),ib=plan.rooms.indexOf(b);
        int insert=Math.min(ia,ib);
        plan.rooms.remove(a);plan.rooms.remove(b);
        plan.rooms.add(Math.min(insert,plan.rooms.size()),merged);
        markEdited(plan);
        return true;
    }

    public boolean splitRoom(ApartmentPlan plan,String roomId,SplitAxis axis,double coordinate){
        if(plan==null)return false;
        RoomZone room=find(plan,roomId);
        if(room==null)return false;

        boolean[][] left=new boolean[Math.max(1,plan.rows)][Math.max(1,plan.cols)];
        boolean[][] right=new boolean[Math.max(1,plan.rows)][Math.max(1,plan.cols)];
        int splitIndex=axis==SplitAxis.X
                ?(int)Math.floor((coordinate-plan.originX)/plan.cellM)
                :(int)Math.floor((coordinate-plan.originZ)/plan.cellM);

        for(PlanGridRun run:room.floorRuns){
            for(int c=run.startCol;c<=run.endCol;c++){
                if(run.row<0||run.row>=plan.rows||c<0||c>=plan.cols)continue;
                boolean first=axis==SplitAxis.X?c<splitIndex:run.row<splitIndex;
                (first?left:right)[run.row][c]=true;
            }
        }
        RoomZone a=fromCells(plan,left,room.id);
        RoomZone b=fromCells(plan,right,UUID.randomUUID().toString());
        if(a==null||b==null||a.areaM2<0.8||b.areaM2<0.8)return false;

        a.type=room.type;a.autoLabel=room.autoLabel;a.confidence=room.confidence*0.95;
        b.type=RoomZone.RoomType.UNKNOWN;b.autoLabel="Нова зона";b.confidence=room.confidence*0.85;
        // Both children start with the parent's visual finishes. The user can then change
        // either side explicitly; a split must not unexpectedly revert half the room to raw.
        copyFinishes(room,a);copyFinishes(room,b);

        int idx=plan.rooms.indexOf(room);
        plan.rooms.remove(room);
        plan.rooms.add(idx,a);plan.rooms.add(idx+1,b);

        PlanWallSegment wall=new PlanWallSegment();
        wall.manual=true;wall.confidence=1.0;
        if(axis==SplitAxis.X){
            wall.x1=wall.x2=coordinate;
            wall.z1=Math.max(a.minZ,b.minZ);
            wall.z2=Math.min(a.maxZ,b.maxZ);
        }else{
            wall.z1=wall.z2=coordinate;
            wall.x1=Math.max(a.minX,b.minX);
            wall.x2=Math.min(a.maxX,b.maxX);
        }
        if(wall.lengthM()>0.4)plan.walls.add(wall);

        // Re-evaluate both sides of every affected opening. An opening center lies on its wall,
        // so assigning by the center alone can choose the wrong child after a split.
        for(ApartmentOpening o:plan.openings){
            if(!roomId.equals(o.roomAId)&&!roomId.equals(o.roomBId))continue;
            assignOpeningRooms(plan,o);
            if(o.type==ApartmentOpening.Type.WINDOW)o.external=true;
        }
        for(StructuralFeature f:plan.features){
            if(roomId.equals(f.roomId)){
                double x=(f.minX+f.maxX)/2.0,z=(f.minZ+f.maxZ)/2.0;
                RoomZone target=findRoomAt(plan,x,z);
                if(target==null)target=nearestRoom(plan,x,z,a,b);
                f.roomId=target==null?a.id:target.id;
            }
        }
        for(FurnitureItem item:plan.furniture){
            if(!roomId.equals(item.roomId))continue;
            RoomZone target=findRoomAt(plan,item.x,item.z);
            if(target==null)target=nearestRoom(plan,item.x,item.z,a,b);
            item.roomId=target==null?a.id:target.id;
            // Keep the object physically inside its newly assigned irregular footprint.
            double[] inside=com.elektoryk.scan.model.RoomFootprint.nearestInside(plan,target,item.x,item.z);
            item.x=inside[0];item.z=inside[1];
        }
        markEdited(plan);
        return true;
    }

    public PlanWallSegment addWall(ApartmentPlan plan,double x1,double z1,double x2,double z2,double thicknessM,String materialKey){
        if(plan==null||Math.hypot(x2-x1,z2-z1)<0.25)return null;
        // Force orthogonal wall: choose the dominant requested direction.
        PlanWallSegment w=new PlanWallSegment();
        if(Math.abs(x2-x1)>=Math.abs(z2-z1)){
            double z=(z1+z2)/2.0;w.x1=Math.min(x1,x2);w.x2=Math.max(x1,x2);w.z1=w.z2=z;
        }else{
            double x=(x1+x2)/2.0;w.z1=Math.min(z1,z2);w.z2=Math.max(z1,z2);w.x1=w.x2=x;
        }
        w.thicknessM=Math.max(0.05,Math.min(0.60,thicknessM));
        w.materialKey=materialKey==null?"UNKNOWN":materialKey;
        w.manual=true;w.confidence=1.0;
        plan.walls.add(w);markEdited(plan);return w;
    }

    public boolean removeWall(ApartmentPlan plan,String wallId){
        if(plan==null||wallId==null)return false;
        PlanWallSegment found=null;
        for(PlanWallSegment w:plan.walls)if(wallId.equals(w.id)){found=w;break;}
        if(found==null)return false;
        plan.walls.remove(found);
        for(ApartmentOpening o:plan.openings)if(wallId.equals(o.wallId))o.wallId="";
        markEdited(plan);return true;
    }

    public boolean moveWall(ApartmentPlan plan,String wallId,double perpendicularDeltaM){
        PlanWallSegment w=findWall(plan,wallId);if(w==null)return false;
        boolean horizontal=Math.abs(w.x2-w.x1)>=Math.abs(w.z2-w.z1);
        if(horizontal){w.z1+=perpendicularDeltaM;w.z2+=perpendicularDeltaM;}
        else{w.x1+=perpendicularDeltaM;w.x2+=perpendicularDeltaM;}
        w.manual=true;w.confidence=1.0;
        for(ApartmentOpening o:plan.openings){
            if(!wallId.equals(o.wallId))continue;
            if(horizontal){o.z1+=perpendicularDeltaM;o.z2+=perpendicularDeltaM;}
            else{o.x1+=perpendicularDeltaM;o.x2+=perpendicularDeltaM;}
            o.manualEdited=true;
        }
        markEdited(plan);return true;
    }

    public ApartmentOpening addOpening(ApartmentPlan plan,String wallId,ApartmentOpening.Type type,
                                       double centerFraction,double widthM,double sillM,double heightM){
        PlanWallSegment w=findWall(plan,wallId);
        if(w==null||type==ApartmentOpening.Type.UNKNOWN)return null;
        centerFraction=Math.max(0.05,Math.min(0.95,centerFraction));
        widthM=Math.max(0.45,Math.min(w.lengthM()*0.85,widthM));
        double cx=w.x1+(w.x2-w.x1)*centerFraction;
        double cz=w.z1+(w.z2-w.z1)*centerFraction;
        double half=widthM/2.0;
        double dx=w.x2-w.x1,dz=w.z2-w.z1,len=Math.max(1e-9,Math.hypot(dx,dz));
        dx/=len;dz/=len;

        ApartmentOpening o=new ApartmentOpening();
        o.type=type;o.wallId=w.id;o.manualEdited=true;o.confidence=1.0;
        o.x1=cx-dx*half;o.z1=cz-dz*half;o.x2=cx+dx*half;o.z2=cz+dz*half;
        o.widthM=widthM;
        o.orientation=Math.abs(dx)>=Math.abs(dz)
                ?ApartmentOpening.Orientation.HORIZONTAL_X:ApartmentOpening.Orientation.VERTICAL_Z;
        if(type==ApartmentOpening.Type.DOOR){
            o.bottomY=0;o.heightM=Math.max(1.8,heightM);o.topY=o.heightM;
        }else{
            o.bottomY=Math.max(0.2,sillM);o.heightM=Math.max(0.4,heightM);o.topY=o.bottomY+o.heightM;o.external=true;
        }
        assignOpeningRooms(plan,o);
        plan.openings.add(o);markEdited(plan);return o;
    }

    public boolean editOpening(ApartmentPlan plan,String openingId,ApartmentOpening.Type type,
                               double widthM,double bottomM,double heightM){
        ApartmentOpening o=findOpening(plan,openingId);if(o==null)return false;
        PlanWallSegment w=findWall(plan,o.wallId);
        double cx=o.centerX(),cz=o.centerZ();
        double dx=o.x2-o.x1,dz=o.z2-o.z1,len=Math.max(1e-9,Math.hypot(dx,dz));
        if(w!=null){dx=w.x2-w.x1;dz=w.z2-w.z1;len=Math.max(1e-9,Math.hypot(dx,dz));}
        dx/=len;dz/=len;
        widthM=Math.max(0.40,Math.min(w==null?4.0:w.lengthM()*0.90,widthM));
        o.x1=cx-dx*widthM/2;o.z1=cz-dz*widthM/2;
        o.x2=cx+dx*widthM/2;o.z2=cz+dz*widthM/2;
        o.widthM=widthM;o.type=type;o.manualEdited=true;o.confidence=1.0;
        if(type==ApartmentOpening.Type.DOOR){o.bottomY=0;o.heightM=Math.max(1.8,heightM);o.topY=o.heightM;}
        else{o.bottomY=Math.max(0.15,bottomM);o.heightM=Math.max(0.4,heightM);o.topY=o.bottomY+o.heightM;}
        assignOpeningRooms(plan,o);
        if(type==ApartmentOpening.Type.WINDOW)o.external=true;
        markEdited(plan);return true;
    }

    public boolean removeOpening(ApartmentPlan plan,String openingId){
        ApartmentOpening o=findOpening(plan,openingId);if(o==null)return false;
        plan.openings.remove(o);markEdited(plan);return true;
    }

    public void markEdited(ApartmentPlan plan){
        if(plan==null)return;
        plan.manualEdited=true;
        plan.manualRevision++;
        plan.lastManualEditAt=System.currentTimeMillis();
    }

    private void assignOpeningRooms(ApartmentPlan plan,ApartmentOpening o){
        PlanWallSegment w=findWall(plan,o.wallId);
        if(w==null)return;
        double dx=w.x2-w.x1,dz=w.z2-w.z1,len=Math.max(1e-9,Math.hypot(dx,dz));
        double nx=-dz/len,nz=dx/len;
        RoomZone a=findRoomAt(plan,o.centerX()+nx*0.35,o.centerZ()+nz*0.35);
        RoomZone b=findRoomAt(plan,o.centerX()-nx*0.35,o.centerZ()-nz*0.35);
        o.roomAId=a==null?"":a.id;o.roomBId=b==null?"":b.id;
        o.external=(a==null)^(b==null);
    }

    private void copyFinishes(RoomZone from,RoomZone to){
        if(from==null||to==null)return;
        to.floorFinishPresetKey=from.floorFinishPresetKey;
        to.floorFinishColorArgb=from.floorFinishColorArgb;
        to.floorPatternKey=from.floorPatternKey;
        to.floorPatternScale=from.floorPatternScale;
        to.floorPatternRotationDeg=from.floorPatternRotationDeg;
        to.ceilingFinishPresetKey=from.ceilingFinishPresetKey;
        to.ceilingFinishColorArgb=from.ceilingFinishColorArgb;
        to.ceilingPatternKey=from.ceilingPatternKey;
        to.ceilingPatternScale=from.ceilingPatternScale;
        to.skirtingPresetKey=from.skirtingPresetKey;
    }

    private RoomZone fromCells(ApartmentPlan plan,boolean[][] cells,String id){
        int count=0,minR=Integer.MAX_VALUE,maxR=Integer.MIN_VALUE,minC=Integer.MAX_VALUE,maxC=Integer.MIN_VALUE;
        double sx=0,sz=0;
        RoomZone r=new RoomZone();r.id=id;
        for(int row=0;row<cells.length;row++){
            int c=0;
            while(c<cells[row].length){
                while(c<cells[row].length&&!cells[row][c])c++;
                if(c>=cells[row].length)break;
                int start=c;
                while(c<cells[row].length&&cells[row][c]){count++;sx+=plan.originX+(c+0.5)*plan.cellM;sz+=plan.originZ+(row+0.5)*plan.cellM;c++;}
                int end=c-1;r.floorRuns.add(new PlanGridRun(row,start,end));
                minR=Math.min(minR,row);maxR=Math.max(maxR,row);minC=Math.min(minC,start);maxC=Math.max(maxC,end);
            }
        }
        if(count==0)return null;
        r.areaM2=count*plan.cellM*plan.cellM;r.centroidX=sx/count;r.centroidZ=sz/count;
        r.minX=plan.originX+minC*plan.cellM;r.maxX=plan.originX+(maxC+1)*plan.cellM;
        r.minZ=plan.originZ+minR*plan.cellM;r.maxZ=plan.originZ+(maxR+1)*plan.cellM;
        return r;
    }

    private void addRoomCells(boolean[][] cells,RoomZone r){
        for(PlanGridRun run:r.floorRuns)for(int c=run.startCol;c<=run.endCol;c++)
            if(run.row>=0&&run.row<cells.length&&c>=0&&c<cells[run.row].length)cells[run.row][c]=true;
    }

    private RoomZone nearestRoom(ApartmentPlan plan,double x,double z,RoomZone a,RoomZone b){
        double da=Math.hypot(a.centroidX-x,a.centroidZ-z),db=Math.hypot(b.centroidX-x,b.centroidZ-z);
        return da<=db?a:b;
    }

    public RoomZone find(ApartmentPlan p,String id){if(p!=null)for(RoomZone r:p.rooms)if(id.equals(r.id))return r;return null;}
    public PlanWallSegment findWall(ApartmentPlan p,String id){if(p!=null)for(PlanWallSegment w:p.walls)if(id.equals(w.id))return w;return null;}
    public ApartmentOpening findOpening(ApartmentPlan p,String id){if(p!=null)for(ApartmentOpening o:p.openings)if(id.equals(o.id))return o;return null;}
}
