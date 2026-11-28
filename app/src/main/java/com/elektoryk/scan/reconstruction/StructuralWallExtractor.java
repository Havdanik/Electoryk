package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.CloudPoint;
import com.elektoryk.scan.model.RoomBounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Extracts persistent wall center-lines from the full normalized point cloud.
 * Unlike WallLineDetector this does not turn every occupied 10 cm cell into a possible wall.
 * It first finds long vertical plane peaks, suppresses nearby duplicates, then converts only the
 * surviving planes into grid segments. This is deliberately conservative: missing a weak wall is
 * preferable to inventing dozens of transparent sheets in the Digital Twin.
 */
final class StructuralWallExtractor {
    private static final double HIST_M=0.05;
    private static final double INLIER_M=0.075;
    private static final double NMS_M=0.18;
    private static final int MAX_PER_AXIS=14;

    private static final class Candidate {
        boolean xPlane;
        double coord;
        int count;
        double tangentMin,tangentMax;
        double verticalSpan;
        double support;
    }

    List<WallLineDetector.Segment> extract(List<CloudPoint> points,RoomBounds b,ApartmentGrid g){
        List<WallLineDetector.Segment> out=new ArrayList<>();
        if(points==null||points.size()<180||b==null||g==null)return out;
        List<Candidate> cands=new ArrayList<>();
        cands.addAll(axis(points,b,g,true));
        cands.addAll(axis(points,b,g,false));
        Collections.sort(cands,new Comparator<Candidate>(){
            @Override public int compare(Candidate a,Candidate z){return Integer.compare(z.count,a.count);}
        });
        for(Candidate c:cands){
            WallLineDetector.Segment s=toSegment(c,g);if(s==null)continue;
            boolean dup=false;
            for(WallLineDetector.Segment k:out){
                if(k.horizontal!=s.horizontal)continue;
                if(Math.abs(k.fixed-s.fixed)>2)continue;
                int overlap=Math.min(k.end,s.end)-Math.max(k.start,s.start)+1;
                int shorter=Math.min(k.lengthCells(),s.lengthCells());
                if(overlap>0&&overlap>=shorter*0.50){dup=true;break;}
            }
            if(!dup)out.add(s);
        }
        Collections.sort(out,new Comparator<WallLineDetector.Segment>(){
            @Override public int compare(WallLineDetector.Segment a,WallLineDetector.Segment z){return Integer.compare(z.lengthCells(),a.lengthCells());}
        });
        if(out.size()>24)return new ArrayList<>(out.subList(0,24));
        return out;
    }

    private List<Candidate> axis(List<CloudPoint> points,RoomBounds b,ApartmentGrid g,boolean xPlane){
        double lo=xPlane?b.minX:b.minZ,hi=xPlane?b.maxX:b.maxZ;
        int bins=Math.max(1,(int)Math.ceil((hi-lo)/HIST_M)+1);int[] hist=new int[bins];
        double y0=b.floorY+0.12,y1=b.ceilingY-0.10;
        int valid=0;
        for(CloudPoint p:points){
            if(p==null||p.confidence<0.12f||p.y<y0||p.y>y1)continue;
            double q=xPlane?p.x:p.z;int bi=(int)Math.floor((q-lo)/HIST_M);if(bi<0||bi>=bins)continue;
            hist[bi]++;valid++;
        }
        int threshold=Math.max(34,(int)Math.round(valid*0.0035));
        List<Integer> peaks=new ArrayList<>();
        // Room walls often sit exactly at min/max bounds, i.e. histogram bin 0 or bins-1.
        // Do not silently drop those edge peaks.
        for(int i=0;i<bins;i++){
            int left=i>0?hist[i-1]:-1,right=i+1<bins?hist[i+1]:-1;
            if(hist[i]>=threshold&&hist[i]>=left&&hist[i]>=right)peaks.add(i);
        }
        Collections.sort(peaks,new Comparator<Integer>(){@Override public int compare(Integer a,Integer z){return Integer.compare(hist[z],hist[a]);}});
        List<Candidate> out=new ArrayList<>();
        for(int bi:peaks){
            double coord=lo+(bi+0.5)*HIST_M;boolean near=false;
            for(Candidate e:out)if(Math.abs(e.coord-coord)<NMS_M){near=true;break;}
            if(near)continue;
            Candidate c=evaluate(points,b,g,xPlane,coord);if(c==null)continue;
            out.add(c);if(out.size()>=MAX_PER_AXIS)break;
        }
        return out;
    }

    private Candidate evaluate(List<CloudPoint> points,RoomBounds b,ApartmentGrid g,boolean xPlane,double coord){
        ArrayList<Double> ys=new ArrayList<>(),ts=new ArrayList<>(),coords=new ArrayList<>();
        double y0=b.floorY+0.08,y1=b.ceilingY-0.06;
        for(CloudPoint p:points){
            if(p==null||p.confidence<0.12f||p.y<y0||p.y>y1)continue;
            double q=xPlane?p.x:p.z;if(Math.abs(q-coord)>INLIER_M)continue;
            ys.add((double)p.y);ts.add(xPlane?(double)p.z:(double)p.x);coords.add(q);
        }
        if(ys.size()<42)return null;
        Collections.sort(ys);Collections.sort(ts);Collections.sort(coords);
        double yMin=pct(ys,.03),yMax=pct(ys,.97),tMin=pct(ts,.02),tMax=pct(ts,.98);
        double height=yMax-yMin,length=tMax-tMin;
        // Short interior vertical planes are much more likely to be wardrobe/cabinet sides than
        // architectural walls.  Boundary walls may be visible only in a short run because of a
        // doorway/occlusion, but an interior wall needs a longer continuous footprint.
        double boundaryDistance=xPlane
                ?Math.min(Math.abs(coord-b.minX),Math.abs(coord-b.maxX))
                :Math.min(Math.abs(coord-b.minZ),Math.abs(coord-b.maxZ));
        // Global min/max bounds only identify the four outermost facade planes.  In an
        // L/U-shaped apartment a perfectly real exterior wall can sit well inside that box.
        // Treat a plane as a footprint boundary as well when observed floor consistently exists
        // on one side of it but not the other.  This keeps concave facade/alcove walls from being
        // rejected by the stricter interior-partition thresholds.
        boolean footprintBoundary=isFootprintBoundary(g,xPlane,coord,tMin,tMax);
        boolean boundaryWall=boundaryDistance<=0.28 || footprintBoundary;
        double minLength=boundaryWall?0.85:1.40;
        if(height<1.35||length<minLength)return null;
        // Geometry alone cannot name a tall cabinet. For an interior plane demand floor-to-ceiling
        // structural continuity; otherwise keep it as residual/object evidence, not a wall.
        if(!boundaryWall && (yMin>b.floorY+0.24 || yMax<b.ceilingY-0.32))return null;

        int vb=12;boolean[] bands=new boolean[vb];int good=0;
        double denom=Math.max(.1,b.ceilingY-b.floorY);
        for(double y:ys){int k=(int)Math.floor((y-b.floorY)/denom*vb);k=Math.max(0,Math.min(vb-1,k));bands[k]=true;}
        for(boolean z:bands)if(z)good++;
        if(good<7)return null;

        // Tangential structural support: a true wall stays vertically tall across many adjacent
        // 10 cm slices.  A cupboard/bed plus one remote wall intersection can have a large global
        // height and length, but only one or two slices are actually tall.  Re-scan while keeping
        // the tangent/Y pairing and require a long contiguous run of vertically supported slices.
        int tb=Math.max(1,(int)Math.ceil(length/0.10));
        int[] occ=new int[tb];
        double[] sliceMinY=new double[tb],sliceMaxY=new double[tb];
        java.util.Arrays.fill(sliceMinY,Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(sliceMaxY,Double.NEGATIVE_INFINITY);
        for(CloudPoint p:points){
            if(p==null||p.confidence<0.12f||p.y<y0||p.y>y1)continue;
            double q=xPlane?p.x:p.z;if(Math.abs(q-coord)>INLIER_M)continue;
            double t=xPlane?p.z:p.x;int k=(int)Math.floor((t-tMin)/0.10);if(k<0||k>=tb)continue;
            occ[k]++;sliceMinY[k]=Math.min(sliceMinY[k],p.y);sliceMaxY[k]=Math.max(sliceMaxY[k],p.y);
        }
        int occupied=0,strong=0,longestStrong=0,run=0;
        for(int k=0;k<tb;k++){
            if(occ[k]>=2)occupied++;
            boolean vertical=occ[k]>=5 && Double.isFinite(sliceMinY[k]) && (sliceMaxY[k]-sliceMinY[k])>=0.95;
            if(vertical){strong++;run++;longestStrong=Math.max(longestStrong,run);}else run=0;
        }
        double tangentSupport=occupied/(double)Math.max(1,tb);
        double strongSupport=strong/(double)Math.max(1,tb);
        if(tangentSupport<0.32||strongSupport<0.24||longestStrong*0.10<0.65)return null;

        Candidate c=new Candidate();c.xPlane=xPlane;c.coord=pct(coords,.50);c.count=ys.size();
        c.tangentMin=tMin;c.tangentMax=tMax;c.verticalSpan=height;
        c.support=Math.max(.35,Math.min(.99,0.38+tangentSupport*.22+strongSupport*.24+Math.min(1,good/10.0)*.16));
        return c;
    }

    private WallLineDetector.Segment toSegment(Candidate c,ApartmentGrid g){
        WallLineDetector.Segment s=new WallLineDetector.Segment();
        if(c.xPlane){
            s.horizontal=false;s.fixed=(int)Math.round((c.coord-g.originX)/g.cellM-0.5);
            s.start=(int)Math.floor((c.tangentMin-g.originZ)/g.cellM);
            s.end=(int)Math.ceil((c.tangentMax-g.originZ)/g.cellM)-1;
            s.start=Math.max(0,s.start);s.end=Math.min(g.rows-1,s.end);
            if(s.fixed<0||s.fixed>=g.cols)return null;
        }else{
            s.horizontal=true;s.fixed=(int)Math.round((c.coord-g.originZ)/g.cellM-0.5);
            s.start=(int)Math.floor((c.tangentMin-g.originX)/g.cellM);
            s.end=(int)Math.ceil((c.tangentMax-g.originX)/g.cellM)-1;
            s.start=Math.max(0,s.start);s.end=Math.min(g.cols-1,s.end);
            if(s.fixed<0||s.fixed>=g.rows)return null;
        }
        if(s.end-s.start+1<5)return null;s.support=c.support;return s;
    }

    /**
     * Independent floor-side evidence for irregular footprints.  Samples 10-30 cm away from the
     * candidate plane and requires a strong one-sided floor asymmetry across a useful fraction of
     * its tangent run.  Furniture inside a room normally has floor on both sides, so it does not
     * gain facade privileges merely by being tall.
     */
    private boolean isFootprintBoundary(ApartmentGrid g,boolean xPlane,double coord,double tMin,double tMax){
        if(g==null||tMax<=tMin)return false;
        int samples=Math.max(6,Math.min(48,(int)Math.ceil((tMax-tMin)/0.12)));
        int sideA=0,sideB=0,both=0,usable=0;
        for(int i=0;i<samples;i++){
            double t=tMin+(i+0.5)*(tMax-tMin)/samples;
            boolean a=false,b=false;
            for(int d=1;d<=3;d++){
                if(xPlane){
                    int r=g.rowFor(t),c=g.colFor(coord);
                    a|=g.isFloor(r,c-d); b|=g.isFloor(r,c+d);
                }else{
                    int r=g.rowFor(coord),c=g.colFor(t);
                    a|=g.isFloor(r-d,c); b|=g.isFloor(r+d,c);
                }
            }
            if(a||b)usable++;
            if(a&&b)both++;
            else if(a)sideA++;
            else if(b)sideB++;
        }
        if(usable<Math.max(4,samples/3))return false;
        int one=Math.max(sideA,sideB),other=Math.min(sideA,sideB);
        double oneSided=one/(double)usable;
        double opposite=other/(double)usable;
        double twoSided=both/(double)usable;
        return oneSided>=0.55 && opposite<=0.18 && twoSided<=0.35;
    }

    private double pct(List<Double> a,double p){if(a.isEmpty())return 0;int i=(int)Math.round((a.size()-1)*p);return a.get(Math.max(0,Math.min(a.size()-1,i)));}
}
