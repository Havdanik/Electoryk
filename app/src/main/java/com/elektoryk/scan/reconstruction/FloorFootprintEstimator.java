package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.CloudPoint;
import com.elektoryk.scan.model.RoomBounds;

import java.util.List;

/** Estimates actually observed floor footprint instead of using a rectangular bounding box. */
public class FloorFootprintEstimator {
    public static class Result {
        public double areaM2;
        public int occupiedCells;
        public int totalCells;
        public double cellM;
        public double observedRatio;
    }

    private final double cellM;
    public FloorFootprintEstimator(){ this(0.18); }
    public FloorFootprintEstimator(double cellM){ this.cellM=Math.max(0.15,cellM); }

    public Result estimate(List<CloudPoint> points, RoomBounds b){
        Result out=new Result(); out.cellM=cellM;
        if(points==null||points.isEmpty()||b==null||!b.isUsable())return out;
        int cols=Math.max(1,(int)Math.ceil(b.widthM()/cellM));
        int rows=Math.max(1,(int)Math.ceil(b.lengthM()/cellM));
        boolean[][] hit=new boolean[rows][cols];
        int[][] count=new int[rows][cols];
        double minY=b.floorY-0.08;
        double maxY=b.floorY+Math.min(0.22,Math.max(0.12,b.heightM()*0.08));
        for(CloudPoint p:points){
            if(p.y<minY||p.y>maxY)continue;
            int c=(int)((p.x-b.minX)/cellM), r=(int)((p.z-b.minZ)/cellM);
            if(r>=0&&r<rows&&c>=0&&c<cols)count[r][c]++;
        }
        for(int r=0;r<rows;r++)for(int c=0;c<cols;c++)hit[r][c]=count[r][c]>=2;

        // Fill only tiny one-cell holes surrounded by observed floor. This recovers occlusion/noise
        // without inventing whole missing rooms.
        boolean[][] clean=new boolean[rows][cols];
        for(int r=0;r<rows;r++)for(int c=0;c<cols;c++){
            if(hit[r][c]){clean[r][c]=true;continue;}
            int neighbours=0;
            for(int rr=Math.max(0,r-1);rr<=Math.min(rows-1,r+1);rr++)
                for(int cc=Math.max(0,c-1);cc<=Math.min(cols-1,c+1);cc++)
                    if(!(rr==r&&cc==c)&&hit[rr][cc])neighbours++;
            clean[r][c]=neighbours>=6;
        }
        int occupied=0;
        for(int r=0;r<rows;r++)for(int c=0;c<cols;c++)if(clean[r][c])occupied++;
        out.occupiedCells=occupied;
        out.totalCells=rows*cols;
        out.areaM2=occupied*cellM*cellM;
        out.observedRatio=out.totalCells==0?0:(double)occupied/out.totalCells;
        return out;
    }
}
