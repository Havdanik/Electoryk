package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.CloudPoint;
import com.elektoryk.scan.model.RoomBounds;
import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.RoomZone;
import com.elektoryk.scan.model.PlanGridRun;
import com.elektoryk.scan.model.ScanGap;
import com.elektoryk.scan.model.ScanPathPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds low-observation floor zones inside reconstructed room bounds.
 * This does not magically locate an entirely missing room outside the current bounds;
 * it identifies holes inside the known footprint and reports the remaining area deficit separately.
 */
public class ScanGapDetector {
    private final double cellM;

    public ScanGapDetector() { this(0.45); }
    public ScanGapDetector(double cellM) { this.cellM = Math.max(0.25, cellM); }

    public List<ScanGap> detect(List<CloudPoint> points, RoomBounds b) {
        return detect(points,b,null);
    }

    public List<ScanGap> detect(List<CloudPoint> points, RoomBounds b, List<ScanPathPoint> path) {
        return detect(points,b,path,null);
    }

    /**
     * Detect gaps only inside the reconstructed apartment footprint when a topology plan exists.
     * This is important for L/U-shaped rooms: the empty quadrant of their bounding box is outside
     * the apartment, not a missing scan zone. Legacy callers without a plan retain the old behavior.
     */
    public List<ScanGap> detect(List<CloudPoint> points, RoomBounds b, List<ScanPathPoint> path, ApartmentPlan plan) {
        List<ScanGap> out = new ArrayList<>();
        if (points == null || points.isEmpty() || b == null || !b.isUsable()) return out;

        int cols = Math.max(1, (int)Math.ceil(b.widthM() / cellM));
        int rows = Math.max(1, (int)Math.ceil(b.lengthM() / cellM));
        int[][] hits = new int[rows][cols];

        double floorBand = Math.min(0.35, Math.max(0.18, b.heightM() * 0.12));
        for (CloudPoint p : points) {
            if (p.y < b.floorY - 0.08 || p.y > b.floorY + floorBand) continue;
            int c = (int)((p.x - b.minX) / cellM);
            int r = (int)((p.z - b.minZ) / cellM);
            if (r >= 0 && r < rows && c >= 0 && c < cols) hits[r][c]++;
        }

        boolean[][] empty = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                // A little tolerance: one hit can be noise; two means the floor zone was observed.
                // Never report space outside an irregular reconstructed footprint as a scan gap.
                double x=b.minX+(c+0.5)*cellM, z=b.minZ+(r+0.5)*cellM;
                empty[r][c] = expectedFloorAt(plan,x,z) && hits[r][c] < 2;
            }
        }

        boolean[][] seen = new boolean[rows][cols];
        final int[] dr = {1,-1,0,0};
        final int[] dc = {0,0,1,-1};
        // Primitive queue: a gap may contain hundreds/thousands of cells. Avoid one int[] object
        // per visited cell, which previously caused a visible GC spike when finishing large scans.
        final int[] queue = new int[Math.max(1, rows*cols)];

        for (int sr = 0; sr < rows; sr++) {
            for (int sc = 0; sc < cols; sc++) {
                if (!empty[sr][sc] || seen[sr][sc]) continue;
                int head=0,tail=0;
                queue[tail++]=sr*cols+sc; seen[sr][sc] = true;
                int minR=sr,maxR=sr,minC=sc,maxC=sc,count=0;
                while(head<tail) {
                    int packed=queue[head++]; int r=packed/cols,c=packed-r*cols; count++;
                    minR=Math.min(minR,r); maxR=Math.max(maxR,r); minC=Math.min(minC,c); maxC=Math.max(maxC,c);
                    for(int i=0;i<4;i++){
                        int nr=r+dr[i], nc=c+dc[i];
                        if(nr>=0&&nr<rows&&nc>=0&&nc<cols&&empty[nr][nc]&&!seen[nr][nc]){
                            seen[nr][nc]=true; queue[tail++]=nr*cols+nc;
                        }
                    }
                }
                double area = count * cellM * cellM;
                if (area < 0.45) continue;
                ScanGap g=new ScanGap();
                g.emptyCells=count;
                g.estimatedAreaM2=area;
                g.minX=b.minX + minC*cellM;
                g.maxX=Math.min(b.maxX,b.minX+(maxC+1)*cellM);
                g.minZ=b.minZ + minR*cellM;
                g.maxZ=Math.min(b.maxZ,b.minZ+(maxR+1)*cellM);
                g.nearestPathDistanceM=nearestPathDistance(g.centerX(),g.centerZ(),path);
                if(area>=2.5){
                    g.hint=g.nearestPathDistanceM>1.8
                            ?"Велика зона майже поза траєкторією — зайди ближче до її центру й зроби повільний прохід по периметру."
                            :"Велика зона з низьким покриттям — пройди її ще раз і покажи підлогу, кути та верх стін.";
                }else{
                    g.hint=g.nearestPathDistanceM>1.3
                            ?"Ти проходив далеко від цієї ділянки — підійди ближче й додай 2–3 ракурси."
                            :"Додай ракурс підлоги/кута в цій зоні з іншого боку.";
                }
                out.add(g);
            }
        }
        out.sort(new Comparator<ScanGap>() {
            @Override public int compare(ScanGap a, ScanGap b) { return Double.compare(b.estimatedAreaM2, a.estimatedAreaM2); }
        });
        if (out.size() > 8) return new ArrayList<>(out.subList(0,8));
        return out;
    }

    private boolean expectedFloorAt(ApartmentPlan plan,double x,double z){
        if(plan==null || plan.rooms==null || plan.rooms.isEmpty() || plan.cellM<=0 || plan.rows<=0 || plan.cols<=0) return true;
        int c=(int)Math.floor((x-plan.originX)/plan.cellM);
        int r=(int)Math.floor((z-plan.originZ)/plan.cellM);
        if(r<0||r>=plan.rows||c<0||c>=plan.cols)return false;
        for(RoomZone room:plan.rooms){
            if(room==null)continue;
            // Fast reject avoids scanning runs for unrelated rooms.
            if(x<room.minX-plan.cellM||x>room.maxX+plan.cellM||z<room.minZ-plan.cellM||z>room.maxZ+plan.cellM)continue;
            for(PlanGridRun run:room.floorRuns){
                if(run!=null&&run.row==r&&c>=run.startCol&&c<=run.endCol)return true;
            }
        }
        return false;
    }

    private double nearestPathDistance(double x,double z,List<ScanPathPoint> path){
        if(path==null||path.isEmpty())return 0;
        double best=Double.POSITIVE_INFINITY;
        for(ScanPathPoint p:path){
            double d=Math.hypot(x-p.x,z-p.z);
            if(d<best)best=d;
        }
        return Double.isFinite(best)?best:0;
    }

}
