package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.ApartmentOpening;
import com.elektoryk.scan.model.PlanGridRun;
import com.elektoryk.scan.model.RoomZone;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class MultiRoomSegmenter {
    static class Result {
        final List<RoomZone> rooms=new ArrayList<>();
        int[][] labels;
    }

    Result segment(ApartmentGrid g,
                   boolean[][] wallBarrier,
                   List<ApartmentOpening> openings){
        Result out=new Result();
        if(g==null)return out;

        boolean[][] blocked=ApartmentGrid.copy(wallBarrier);
        sealDoors(g,blocked,openings);

        int[][] label=new int[g.rows][g.cols];
        int next=1;
        final int[] dr={1,-1,0,0},dc={0,0,1,-1};
        // Primitive queue: room segmentation can touch tens of thousands of cells.
        // Avoid one int[2] object plus ArrayDeque node/reference churn per floor cell.
        final int capacity=Math.max(1,g.rows*g.cols);
        int[] queue=new int[capacity];

        for(int sr=0;sr<g.rows;sr++)for(int sc=0;sc<g.cols;sc++){
            if(!g.floor[sr][sc]||blocked[sr][sc]||label[sr][sc]!=0)continue;
            int head=0,tail=0;
            queue[tail++]=sr*g.cols+sc; label[sr][sc]=next;
            while(head<tail){
                int packed=queue[head++];
                int cr=packed/g.cols,cc=packed-cr*g.cols;
                for(int k=0;k<4;k++){
                    int r=cr+dr[k],c=cc+dc[k];
                    if(g.inside(r,c)&&g.floor[r][c]&&!blocked[r][c]&&label[r][c]==0){
                        label[r][c]=next; queue[tail++]=r*g.cols+c;
                    }
                }
            }
            double area=tail*g.cellM*g.cellM;
            if(area<0.55){
                for(int i=0;i<tail;i++){int q=queue[i],r=q/g.cols;label[r][q-r*g.cols]=-1;}
                continue;
            }
            RoomZone room=roomFromComponent(g,label,next,queue,tail);
            double minDim=Math.min(room.maxX-room.minX,room.maxZ-room.minZ);
            if(minDim<0.45 && room.areaM2<3.0){
                for(int i=0;i<tail;i++){int q=queue[i],r=q/g.cols;label[r][q-r*g.cols]=-1;}
                continue;
            }
            out.rooms.add(room);
            next++;
        }

        // Convert temporary component numbers to actual room index labels (1..N).
        int[][] normalized=new int[g.rows][g.cols];
        for(int r=0;r<g.rows;r++)for(int c=0;c<g.cols;c++){
            int old=label[r][c];
            if(old<=0)continue;
            int idx=old-1;
            if(idx>=0&&idx<out.rooms.size())normalized[r][c]=idx+1;
        }
        out.labels=normalized;

        for(int i=0;i<out.rooms.size();i++){
            RoomZone r=out.rooms.get(i);
            r.autoLabel="Зона "+(i+1);
            double w=r.maxX-r.minX, l=r.maxZ-r.minZ;
            double min=Math.max(0.01,Math.min(w,l)),max=Math.max(w,l);
            double aspect=max/min;
            r.narrowPassageLike=aspect>2.6&&min<2.25&&r.areaM2<22;
            if(r.narrowPassageLike){
                r.type=RoomZone.RoomType.HALL;
                r.confidence=Math.min(0.92,r.confidence+0.07);
            }
        }

        assignOpenings(g,out,openings);
        return out;
    }

    private RoomZone roomFromComponent(ApartmentGrid g,int[][] labels,int component,int[] cells,int count){
        RoomZone room=new RoomZone();
        room.id=UUID.randomUUID().toString();
        room.areaM2=count*g.cellM*g.cellM;
        int minR=Integer.MAX_VALUE,maxR=Integer.MIN_VALUE,minC=Integer.MAX_VALUE,maxC=Integer.MIN_VALUE;
        double sx=0,sz=0;
        for(int i=0;i<count;i++){
            int q=cells[i],r=q/g.cols,c=q-r*g.cols;
            minR=Math.min(minR,r);maxR=Math.max(maxR,r);minC=Math.min(minC,c);maxC=Math.max(maxC,c);
            sx+=g.xCenter(c);sz+=g.zCenter(r);
        }
        room.centroidX=sx/count;room.centroidZ=sz/count;
        room.minX=g.originX+minC*g.cellM;room.maxX=g.originX+(maxC+1)*g.cellM;
        room.minZ=g.originZ+minR*g.cellM;room.maxZ=g.originZ+(maxR+1)*g.cellM;

        // The component label itself is the membership bitmap; do not allocate a full
        // rows*cols boolean matrix for every room.
        for(int r=minR;r<=maxR;r++){
            int c=minC;
            while(c<=maxC){
                while(c<=maxC&&labels[r][c]!=component)c++;
                if(c>maxC)break;
                int runStart=c;
                while(c<=maxC&&labels[r][c]==component)c++;
                room.floorRuns.add(new PlanGridRun(r,runStart,c-1));
            }
        }

        int perimeter=0;
        for(int i=0;i<count;i++){
            int q=cells[i],r=q/g.cols,c=q-r*g.cols;
            if(r==0||labels[r-1][c]!=component)perimeter++;
            if(r==g.rows-1||labels[r+1][c]!=component)perimeter++;
            if(c==0||labels[r][c-1]!=component)perimeter++;
            if(c==g.cols-1||labels[r][c+1]!=component)perimeter++;
        }
        double compact=count==0?0:Math.min(1.0,count/(double)Math.max(1,perimeter));
        room.confidence=Math.max(0.50,Math.min(0.95,0.62+compact*0.22));
        return room;
    }

    private void sealDoors(ApartmentGrid g,boolean[][] blocked,List<ApartmentOpening> openings){
        if(openings==null)return;
        for(ApartmentOpening o:openings){
            if(!o.isWalkable())continue;
            if(o.orientation==ApartmentOpening.Orientation.HORIZONTAL_X){
                int r=g.rowFor(o.centerZ());
                int c1=g.colFor(Math.min(o.x1,o.x2)),c2=g.colFor(Math.max(o.x1,o.x2)-0.001);
                for(int c=c1;c<=c2;c++)if(g.inside(r,c))blocked[r][c]=true;
            }else{
                int c=g.colFor(o.centerX());
                int r1=g.rowFor(Math.min(o.z1,o.z2)),r2=g.rowFor(Math.max(o.z1,o.z2)-0.001);
                for(int r=r1;r<=r2;r++)if(g.inside(r,c))blocked[r][c]=true;
            }
        }
    }

    private void assignOpenings(ApartmentGrid g,Result result,List<ApartmentOpening> openings){
        if(openings==null||result.labels==null)return;
        for(ApartmentOpening o:openings){
            o.roomAId="";o.roomBId="";
            int r=g.rowFor(o.centerZ()),c=g.colFor(o.centerX());
            int a,b;
            if(o.orientation==ApartmentOpening.Orientation.HORIZONTAL_X){
                // Wall runs along X, therefore the two rooms are on opposite Z sides.
                a=nearestLabelOnSide(result.labels,r,c,-1,0);
                b=nearestLabelOnSide(result.labels,r,c, 1,0);
            }else{
                // Wall runs along Z, therefore the two rooms are on opposite X sides.
                a=nearestLabelOnSide(result.labels,r,c,0,-1);
                b=nearestLabelOnSide(result.labels,r,c,0, 1);
            }
            if(a>0&&a<=result.rooms.size())o.roomAId=result.rooms.get(a-1).id;
            if(b>0&&b<=result.rooms.size())o.roomBId=result.rooms.get(b-1).id;

            // Do not create a fake room-to-room connection when both probes hit the same room
            // around a wall end / noisy barrier. Keep the better side as a single-room opening.
            if(!o.roomAId.isEmpty()&&o.roomAId.equals(o.roomBId))o.roomBId="";

            // Windows are exterior by definition here. Geometry noise can leave floor evidence
            // outside the facade, so never advertise a window as a room-to-room connection.
            if(o.type==ApartmentOpening.Type.WINDOW && !o.roomAId.isEmpty() && !o.roomBId.isEmpty()){
                o.roomBId="";
            }
            o.external=(o.roomAId.isEmpty() ^ o.roomBId.isEmpty()) || o.type==ApartmentOpening.Type.WINDOW;
        }
    }

    private int nearestLabelOnSide(int[][] labels,int r,int c,int dr,int dc){
        // Start outside the nominal wall thickness and walk only along the wall normal.
        // A small tangential fan tolerates grid quantization without jumping through a corner
        // into an unrelated room.
        for(int d=1;d<=7;d++){
            int rr=r+dr*d,cc=c+dc*d;
            for(int tangent=-1;tangent<=1;tangent++){
                int tr=rr+(dc!=0?tangent:0);
                int tc=cc+(dr!=0?tangent:0);
                if(tr>=0&&tr<labels.length&&tc>=0&&tc<labels[0].length&&labels[tr][tc]>0)
                    return labels[tr][tc];
            }
        }
        return 0;
    }

}
