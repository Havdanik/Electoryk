package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.CloudPoint;
import com.elektoryk.scan.model.RoomBounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Final architectural guard between noisy vertical-plane candidates and the editable room model.
 *
 * v0.20.8: this guard is deliberately topology-aware. Older builds treated more than six wall
 * segments as an invalid "single room" and replaced the complete result with one rectangular
 * outer envelope. That was safe for a bedroom scan but destructive for a real apartment: valid
 * interior partitions disappeared before MultiRoomSegmenter ever saw them. We now keep credible
 * floor-to-ceiling interior partitions and only use the rectangular rescue path when there is no
 * credible partition evidence at all.
 */
final class RoomPerimeterSanitizer {
    private static final int MAX_SINGLE_ROOM_WALLS=6;
    private static final int MAX_ARCHITECTURAL_WALLS=18;
    private static final int MAX_IRREGULAR_PERIMETER_WALLS=12;
    private static final double BOUNDARY_TOL_M=0.30;
    private static final double STRONG_INTERIOR_SUPPORT=0.82;
    private static final double MIN_INTERIOR_LENGTH_M=1.55;

    List<WallLineDetector.Segment> sanitize(List<WallLineDetector.Segment> raw,
                                            List<CloudPoint> points,
                                            RoomBounds b,
                                            ApartmentGrid g){
        if(b==null||g==null)return raw==null?new ArrayList<WallLineDetector.Segment>():raw;
        List<WallLineDetector.Segment> src=raw==null?new ArrayList<WallLineDetector.Segment>():raw;

        List<WallLineDetector.Segment> boundary=new ArrayList<>();
        List<WallLineDetector.Segment> interior=new ArrayList<>();
        for(WallLineDetector.Segment s:src){
            if(s==null||s.lengthCells()<5)continue;
            double coord=s.horizontal?g.zCenter(s.fixed):g.xCenter(s.fixed);
            double d=s.horizontal
                    ?Math.min(Math.abs(coord-b.minZ),Math.abs(coord-b.maxZ))
                    :Math.min(Math.abs(coord-b.minX),Math.abs(coord-b.maxX));
            // A real facade is not necessarily on the global min/max bounds. L-shaped rooms,
            // alcoves and apartment notches have exterior wall runs inset from the bounding box.
            // Treat a strong wall as perimeter when observed floor consistently exists on only
            // one side of it; otherwise a valid L-shape can be destroyed as a "fake interior".
            boolean floorEdge=isFloorEdgeBoundary(g,s);
            if(d<=BOUNDARY_TOL_M || floorEdge){
                boundary.add(s);
            }else if(s.support>=STRONG_INTERIOR_SUPPORT && s.lengthCells()*g.cellM>=MIN_INTERIOR_LENGTH_M){
                interior.add(s);
            }
        }

        // Credible interior partitions mean this may be a multi-room / L-shaped apartment.
        // Never collapse that topology to the global bounding rectangle merely because the wall
        // count exceeds the old single-room heuristic.
        if(!interior.isEmpty()){
            List<WallLineDetector.Segment> architectural=new ArrayList<>();
            architectural.addAll(boundary);
            architectural.addAll(interior);
            architectural=dedupe(architectural);
            Collections.sort(architectural,new Comparator<WallLineDetector.Segment>(){
                @Override public int compare(WallLineDetector.Segment a,WallLineDetector.Segment z){
                    double qa=a.lengthCells()*Math.max(.25,a.support);
                    double qz=z.lengthCells()*Math.max(.25,z.support);
                    return Double.compare(qz,qa);
                }
            });
            if(architectural.size()>MAX_ARCHITECTURAL_WALLS)
                architectural=new ArrayList<>(architectural.subList(0,MAX_ARCHITECTURAL_WALLS));
            // Keep a topology-bearing result only if it still contains at least one interior wall.
            if(hasInterior(architectural,b,g))return architectural;
        }

        List<WallLineDetector.Segment> sane=dedupe(boundary);
        // Single-room rescue remains intentionally conservative. With no trustworthy partition
        // evidence a simple coherent perimeter is better than a forest of furniture planes.
        if(sane.size()<3)return outerEnvelope(points,b,g);

        // Preserve observed non-rectangular perimeters (L/U shapes, alcoves). The previous
        // single-room >6 rule could replace them with a rectangle, inventing floor area outside
        // the scanned room. Only allow the larger perimeter budget when most runs independently
        // look like a floor edge; otherwise keep the conservative rectangular rescue.
        int edgeRuns=0;
        for(WallLineDetector.Segment s:sane)if(isFloorEdgeBoundary(g,s))edgeRuns++;
        boolean observedIrregular=sane.size()<=MAX_IRREGULAR_PERIMETER_WALLS
                && edgeRuns>=Math.max(3,(int)Math.ceil(sane.size()*0.70));
        if((sane.size()>MAX_SINGLE_ROOM_WALLS && !observedIrregular) || hasTooManyParallelLayers(sane))
            return outerEnvelope(points,b,g);
        return sane;
    }

    /** True when floor evidence lies predominantly on exactly one side of this wall run. */
    private boolean isFloorEdgeBoundary(ApartmentGrid g,WallLineDetector.Segment s){
        if(g==null||s==null)return false;
        int a=0,b=0,samples=0;
        int step=Math.max(1,s.lengthCells()/10);
        for(int p=s.start;p<=s.end;p+=step){
            samples++;
            // Probe 10-30 cm away from the centerline. A one-cell probe is too sensitive to
            // wall thickness and depth quantization; voting across three offsets is steadier.
            boolean fa=false,fb=false;
            for(int off=1;off<=3;off++){
                if(s.horizontal){
                    fa|=g.isFloor(s.fixed-off,p);fb|=g.isFloor(s.fixed+off,p);
                }else{
                    fa|=g.isFloor(p,s.fixed-off);fb|=g.isFloor(p,s.fixed+off);
                }
            }
            if(fa)a++;if(fb)b++;
        }
        if(samples<2)return false;
        int inside=Math.max(a,b),outside=Math.min(a,b);
        return inside>=Math.max(2,(int)Math.ceil(samples*0.55))
                && outside<=Math.floor(samples*0.25);
    }

    private boolean hasInterior(List<WallLineDetector.Segment> a,RoomBounds b,ApartmentGrid g){
        for(WallLineDetector.Segment s:a){
            double coord=s.horizontal?g.zCenter(s.fixed):g.xCenter(s.fixed);
            double d=s.horizontal?Math.min(Math.abs(coord-b.minZ),Math.abs(coord-b.maxZ))
                    :Math.min(Math.abs(coord-b.minX),Math.abs(coord-b.maxX));
            if(d>BOUNDARY_TOL_M)return true;
        }
        return false;
    }

    private List<WallLineDetector.Segment> dedupe(List<WallLineDetector.Segment> src){
        List<WallLineDetector.Segment> sorted=new ArrayList<>(src);
        Collections.sort(sorted,new Comparator<WallLineDetector.Segment>(){
            @Override public int compare(WallLineDetector.Segment a,WallLineDetector.Segment z){
                return Double.compare(z.support,a.support);
            }
        });
        List<WallLineDetector.Segment> out=new ArrayList<>();
        for(WallLineDetector.Segment s:sorted){
            boolean dup=false;
            for(WallLineDetector.Segment k:out){
                if(k.horizontal!=s.horizontal||Math.abs(k.fixed-s.fixed)>2)continue;
                int overlap=Math.min(k.end,s.end)-Math.max(k.start,s.start)+1;
                int shorter=Math.min(k.lengthCells(),s.lengthCells());
                if(overlap>0&&overlap>=shorter*.55){dup=true;break;}
            }
            if(!dup)out.add(s);
        }
        return out;
    }

    private boolean hasTooManyParallelLayers(List<WallLineDetector.Segment> a){
        int h=0,v=0;for(WallLineDetector.Segment s:a){if(s.horizontal)h++;else v++;}
        return h>3||v>3;
    }

    private List<WallLineDetector.Segment> outerEnvelope(List<CloudPoint> points,RoomBounds b,ApartmentGrid g){
        List<WallLineDetector.Segment> out=new ArrayList<>();
        addHorizontal(out,0,0,g.cols-1,boundarySupport(points,b,true,b.minZ));
        addHorizontal(out,g.rows-1,0,g.cols-1,boundarySupport(points,b,true,b.maxZ));
        addVertical(out,0,0,g.rows-1,boundarySupport(points,b,false,b.minX));
        addVertical(out,g.cols-1,0,g.rows-1,boundarySupport(points,b,false,b.maxX));
        return out;
    }

    private void addHorizontal(List<WallLineDetector.Segment> out,int row,int start,int end,double support){
        if(end<start)return;WallLineDetector.Segment s=new WallLineDetector.Segment();
        s.horizontal=true;s.fixed=row;s.start=start;s.end=end;s.support=support;out.add(s);
    }
    private void addVertical(List<WallLineDetector.Segment> out,int col,int start,int end,double support){
        if(end<start)return;WallLineDetector.Segment s=new WallLineDetector.Segment();
        s.horizontal=false;s.fixed=col;s.start=start;s.end=end;s.support=support;out.add(s);
    }

    private double boundarySupport(List<CloudPoint> points,RoomBounds b,boolean zAxis,double value){
        if(points==null||points.isEmpty())return .35;
        int all=0,hit=0;double lo=b.floorY+.20,hi=b.ceilingY-.18;
        for(CloudPoint p:points){
            if(p==null||p.confidence<.12f||p.y<lo||p.y>hi)continue;all++;
            double q=zAxis?p.z:p.x;if(Math.abs(q-value)<=.12)hit++;
        }
        if(all<=0)return .35;
        return Math.max(.35,Math.min(.88,.35+hit/(double)all*7.0));
    }
}
