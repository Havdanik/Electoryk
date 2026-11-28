package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.CloudPoint;
import com.elektoryk.scan.model.RoomBounds;

import java.util.List;

/** Metric 2D evidence grid used for apartment-wide segmentation. */
class ApartmentGrid {
    final double cellM;
    final double originX;
    final double originZ;
    final int rows;
    final int cols;
    final double floorY;
    final double ceilingY;

    final boolean[][] floor;
    final boolean[][] rawFloor;
    final boolean[][] wall;
    final int[][] floorHits;
    final int[][] verticalHits;
    final int[][] heightMask;

    private ApartmentGrid(double cellM, RoomBounds b, int rows, int cols) {
        this.cellM=cellM;
        this.originX=b.minX;
        this.originZ=b.minZ;
        this.rows=rows;
        this.cols=cols;
        this.floorY=b.floorY;
        this.ceilingY=b.ceilingY;
        floor=new boolean[rows][cols];
        rawFloor=new boolean[rows][cols];
        wall=new boolean[rows][cols];
        floorHits=new int[rows][cols];
        verticalHits=new int[rows][cols];
        heightMask=new int[rows][cols];
    }

    static ApartmentGrid build(List<CloudPoint> points, RoomBounds b, double requestedCellM) {
        if(points==null || points.isEmpty() || b==null || !b.isUsable()) return null;
        double cell=Math.max(0.08,Math.min(0.20,requestedCellM));
        int cols=Math.max(1,(int)Math.ceil(b.widthM()/cell));
        int rows=Math.max(1,(int)Math.ceil(b.lengthM()/cell));
        ApartmentGrid g=new ApartmentGrid(cell,b,rows,cols);

        double h=Math.max(1.8,b.heightM());
        double floorLo=b.floorY-0.08;
        double floorHi=b.floorY+Math.min(0.24,h*0.09);
        double verticalLo=b.floorY+0.12;
        double verticalHi=b.ceilingY-0.10;

        for(CloudPoint p:points){
            int c=g.colFor(p.x), r=g.rowFor(p.z);
            if(!g.inside(r,c)) continue;

            if(p.y>=floorLo && p.y<=floorHi) g.floorHits[r][c]++;

            if(p.y>=verticalLo && p.y<=verticalHi){
                g.verticalHits[r][c]++;
                double t=(p.y-b.floorY)/h;
                int bin=(int)Math.floor(t*6.0);
                bin=Math.max(0,Math.min(5,bin));
                g.heightMask[r][c] |= 1<<bin;
            }
        }

        for(int r=0;r<rows;r++)for(int c=0;c<cols;c++){
            g.floor[r][c]=g.floorHits[r][c]>=2;
            g.rawFloor[r][c]=g.floor[r][c];
            int bins=Integer.bitCount(g.heightMask[r][c]);
            g.wall[r][c]=g.verticalHits[r][c]>=4 && bins>=3;
        }

        // Recover tiny floor holes caused by furniture/shadows, but never invent large rooms.
        for(int pass=0;pass<2;pass++){
            boolean[][] next=copy(g.floor);
            for(int r=0;r<rows;r++)for(int c=0;c<cols;c++){
                if(g.floor[r][c]) continue;
                int n=0;
                for(int dr=-1;dr<=1;dr++)for(int dc=-1;dc<=1;dc++){
                    if(dr==0&&dc==0)continue;
                    if(g.isFloor(r+dr,c+dc))n++;
                }
                if(n>=6)next[r][c]=true;
            }
            for(int r=0;r<rows;r++)System.arraycopy(next[r],0,g.floor[r],0,cols);
        }

        // Fill enclosed occlusion holes (e.g. sofa/wardrobe) only when the missing
        // component is fully surrounded by observed footprint and remains reasonably small.
        g.fillEnclosedHoles(3.5);

        // Remove isolated one-cell floor speckles.
        boolean[][] clean=copy(g.floor);
        for(int r=0;r<rows;r++)for(int c=0;c<cols;c++){
            if(!g.floor[r][c])continue;
            int n=0;
            if(g.isFloor(r-1,c))n++;
            if(g.isFloor(r+1,c))n++;
            if(g.isFloor(r,c-1))n++;
            if(g.isFloor(r,c+1))n++;
            if(n==0)clean[r][c]=false;
        }
        for(int r=0;r<rows;r++)System.arraycopy(clean[r],0,g.floor[r],0,cols);
        return g;
    }

    int colFor(double x){ return (int)Math.floor((x-originX)/cellM); }
    int rowFor(double z){ return (int)Math.floor((z-originZ)/cellM); }
    double xCenter(int c){ return originX+(c+0.5)*cellM; }
    double zCenter(int r){ return originZ+(r+0.5)*cellM; }

    boolean inside(int r,int c){ return r>=0&&r<rows&&c>=0&&c<cols; }
    boolean isFloor(int r,int c){ return inside(r,c)&&floor[r][c]; }
    boolean isWall(int r,int c){ return inside(r,c)&&wall[r][c]; }
    int mask(int r,int c){ return inside(r,c)?heightMask[r][c]:0; }

    int observedFloorCells(){
        int n=0;for(int r=0;r<rows;r++)for(int c=0;c<cols;c++)if(floor[r][c])n++;return n;
    }

    int directObservedFloorCells(){
        int n=0;for(int r=0;r<rows;r++)for(int c=0;c<cols;c++)if(rawFloor[r][c])n++;return n;
    }

    int inferredFloorCells(){
        return Math.max(0,observedFloorCells()-directObservedFloorCells());
    }

    private void fillEnclosedHoles(double maxAreaM2){
        boolean[][] seen=new boolean[rows][cols];
        final int[] dr={1,-1,0,0},dc={0,0,1,-1};
        // The queue doubles as the retained component list, so enclosed-hole filling needs no
        // ArrayDeque<int[]> and no ArrayList<int[]> allocation per component.
        final int[] queue=new int[Math.max(1,rows*cols)];
        for(int sr=0;sr<rows;sr++)for(int sc=0;sc<cols;sc++){
            if(floor[sr][sc]||seen[sr][sc])continue;
            int head=0,tail=0;queue[tail++]=sr*cols+sc;seen[sr][sc]=true;
            boolean touchesEdge=false;
            while(head<tail){
                int packed=queue[head++],r0=packed/cols,c0=packed-r0*cols;
                if(r0==0||r0==rows-1||c0==0||c0==cols-1)touchesEdge=true;
                for(int k=0;k<4;k++){
                    int r=r0+dr[k],c=c0+dc[k];
                    if(inside(r,c)&&!floor[r][c]&&!seen[r][c]){
                        seen[r][c]=true;queue[tail++]=r*cols+c;
                    }
                }
            }
            double area=tail*cellM*cellM;
            if(!touchesEdge && area<=maxAreaM2){
                for(int i=0;i<tail;i++){int packed=queue[i],r=packed/cols,c=packed-r*cols;floor[r][c]=true;}
            }
        }
    }

    static boolean[][] copy(boolean[][] a){
        boolean[][] b=new boolean[a.length][];
        for(int i=0;i<a.length;i++)b[i]=a[i].clone();
        return b;
    }
}
