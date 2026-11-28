package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.RoomZone;
import com.elektoryk.scan.model.StructuralFeature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Conservative structural candidates: columns + small room-boundary deviations. */
class StructuralFeatureDetector {

    List<StructuralFeature> detect(ApartmentGrid g,
                                   boolean[][] wallBarrier,
                                   List<RoomZone> rooms){
        List<StructuralFeature> out=new ArrayList<>();
        if(g==null)return out;
        out.addAll(columns(g,wallBarrier,rooms));
        out.addAll(boundaryDeviations(g,rooms));
        return out;
    }

    private List<StructuralFeature> columns(ApartmentGrid g,boolean[][] wallBarrier,List<RoomZone> rooms){
        List<StructuralFeature> out=new ArrayList<>();
        boolean[][] candidate=new boolean[g.rows][g.cols];
        for(int r=0;r<g.rows;r++)for(int c=0;c<g.cols;c++){
            candidate[r][c]=g.wall[r][c]&&!wallBarrier[r][c]&&Integer.bitCount(g.heightMask[r][c])>=4;
        }
        boolean[][] seen=new boolean[g.rows][g.cols];
        final int[] dr={1,-1,0,0,1,1,-1,-1},dc={0,0,1,-1,1,-1,1,-1};
        final int[] queue=new int[Math.max(1,g.rows*g.cols)];

        for(int sr=0;sr<g.rows;sr++)for(int sc=0;sc<g.cols;sc++){
            if(!candidate[sr][sc]||seen[sr][sc])continue;
            int head=0,tail=0;queue[tail++]=sr*g.cols+sc;seen[sr][sc]=true;
            int minR=sr,maxR=sr,minC=sc,maxC=sc,count=0;
            while(head<tail){
                int packed=queue[head++],r0=packed/g.cols,c0=packed-r0*g.cols;count++;
                minR=Math.min(minR,r0);maxR=Math.max(maxR,r0);
                minC=Math.min(minC,c0);maxC=Math.max(maxC,c0);
                for(int k=0;k<8;k++){
                    int r=r0+dr[k],c=c0+dc[k];
                    if(g.inside(r,c)&&candidate[r][c]&&!seen[r][c]){
                        seen[r][c]=true;queue[tail++]=r*g.cols+c;
                    }
                }
            }
            double w=(maxC-minC+1)*g.cellM,d=(maxR-minR+1)*g.cellM;
            if(count<2||count>36||w>1.25||d>1.25||Math.max(w,d)/Math.max(0.05,Math.min(w,d))>3.5)continue;
            double cx=g.xCenter((minC+maxC)/2),cz=g.zCenter((minR+maxR)/2);
            String roomId=findRoom(g,rooms,cx,cz);
            if(roomId.isEmpty())continue;

            StructuralFeature f=new StructuralFeature();
            f.type=StructuralFeature.Type.COLUMN;
            f.roomId=roomId;
            f.minX=g.originX+minC*g.cellM;f.maxX=g.originX+(maxC+1)*g.cellM;
            f.minZ=g.originZ+minR*g.cellM;f.maxZ=g.originZ+(maxR+1)*g.cellM;
            f.minY=g.floorY;f.maxY=g.ceilingY;
            f.confidence=Math.min(0.82,0.56+count*0.025);
            f.reason="Компактний вертикальний кластер з підтримкою на кількох висотах, не належить довгій стіні.";
            out.add(f);
        }
        return out;
    }

    private List<StructuralFeature> boundaryDeviations(ApartmentGrid g,List<RoomZone> rooms){
        List<StructuralFeature> out=new ArrayList<>();
        for(RoomZone room:rooms){
            if(room.floorRuns.isEmpty())continue;

            int minRow=Integer.MAX_VALUE,maxRow=Integer.MIN_VALUE;
            for(com.elektoryk.scan.model.PlanGridRun run:room.floorRuns){
                minRow=Math.min(minRow,run.row);maxRow=Math.max(maxRow,run.row);
            }
            List<Integer> mins=new ArrayList<>(),maxs=new ArrayList<>();
            int[] rowMin=new int[maxRow-minRow+1],rowMax=new int[maxRow-minRow+1];
            java.util.Arrays.fill(rowMin,Integer.MAX_VALUE);
            java.util.Arrays.fill(rowMax,Integer.MIN_VALUE);
            for(com.elektoryk.scan.model.PlanGridRun run:room.floorRuns){
                int i=run.row-minRow;
                rowMin[i]=Math.min(rowMin[i],run.startCol);
                rowMax[i]=Math.max(rowMax[i],run.endCol);
            }
            for(int i=0;i<rowMin.length;i++)if(rowMin[i]!=Integer.MAX_VALUE){mins.add(rowMin[i]);maxs.add(rowMax[i]);}
            if(mins.size()<5)continue;
            Collections.sort(mins);Collections.sort(maxs);
            int medMin=mins.get(mins.size()/2),medMax=maxs.get(maxs.size()/2);

            detectSide(out,g,room,minRow,rowMin,medMin,true,true);
            detectSide(out,g,room,minRow,rowMax,medMax,false,true);
        }
        return out;
    }

    private void detectSide(List<StructuralFeature> out,ApartmentGrid g,RoomZone room,int baseRow,
                            int[] values,int median,boolean minSide,boolean verticalSpan){
        int i=0;
        while(i<values.length){
            if(values[i]==Integer.MAX_VALUE||values[i]==Integer.MIN_VALUE){i++;continue;}
            int delta=minSide?median-values[i]:values[i]-median;
            boolean deviation=delta>=1&&delta<=4;
            if(!deviation){i++;continue;}
            int start=i,best=delta;
            while(i+1<values.length){
                int v=values[i+1];
                if(v==Integer.MAX_VALUE||v==Integer.MIN_VALUE)break;
                int d=minSide?median-v:v-median;
                if(d<1||d>4)break;
                best=Math.max(best,d);i++;
            }
            int end=i;
            int len=end-start+1;
            double length=len*g.cellM,depth=best*g.cellM;
            if(length>=0.35&&length<=2.0&&depth>=0.16&&depth<=0.85){
                StructuralFeature f=new StructuralFeature();
                f.type=StructuralFeature.Type.NICHE;
                f.roomId=room.id;
                f.minZ=g.originZ+(baseRow+start)*g.cellM;
                f.maxZ=g.originZ+(baseRow+end+1)*g.cellM;
                if(minSide){
                    f.minX=g.originX+(median-best)*g.cellM;
                    f.maxX=g.originX+median*g.cellM;
                }else{
                    f.minX=g.originX+(median+1)*g.cellM;
                    f.maxX=f.minX+depth;
                }
                f.minY=g.floorY;f.maxY=g.ceilingY;
                f.confidence=0.52;
                f.reason="Невелике локальне відхилення контуру від домінантної лінії стіни. Потрібне підтвердження як ніша/архітектура.";
                out.add(f);
            }
            i++;
        }
    }

    private String findRoom(ApartmentGrid g,List<RoomZone> rooms,double x,double z){
        if(g==null)return "";
        int col=(int)Math.floor((x-g.originX)/Math.max(.01,g.cellM));
        int row=(int)Math.floor((z-g.originZ)/Math.max(.01,g.cellM));
        for(RoomZone room:rooms){
            if(room.floorRuns.isEmpty()){
                if(x>=room.minX&&x<=room.maxX&&z>=room.minZ&&z<=room.maxZ)return room.id;
                continue;
            }
            for(com.elektoryk.scan.model.PlanGridRun run:room.floorRuns)
                if(run.row==row&&col>=run.startCol&&col<=run.endCol)return room.id;
        }
        return "";
    }
}
