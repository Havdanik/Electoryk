package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.ApartmentOpening;
import com.elektoryk.scan.model.ScanPathPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Finds door gaps between collinear wall runs and conservative window candidates. */
class OpeningDetector {

    List<ApartmentOpening> detect(ApartmentGrid g,
                                  List<WallLineDetector.Segment> walls,
                                  List<ScanPathPoint> path){
        List<ApartmentOpening> out=new ArrayList<>();
        if(g==null||walls==null)return out;

        // Door: two collinear wall pieces with a 0.55-1.55m gap.
        for(int i=0;i<walls.size();i++){
            WallLineDetector.Segment a=walls.get(i);
            for(int j=i+1;j<walls.size();j++){
                WallLineDetector.Segment b=walls.get(j);
                if(a.horizontal!=b.horizontal)continue;
                if(Math.abs(a.fixed-b.fixed)>1)continue;

                WallLineDetector.Segment left=a.start<=b.start?a:b;
                WallLineDetector.Segment right=left==a?b:a;
                int gapStart=left.end+1;
                int gapEnd=right.start-1;
                int gap=gapEnd-gapStart+1;
                if(gap<=0)continue;
                double width=gap*g.cellM;
                if(width<0.55||width>1.55)continue;
                if(left.lengthCells()*g.cellM<0.55||right.lengthCells()*g.cellM<0.55)continue;
                if((right.end-left.start+1)*g.cellM<2.0)continue;

                int fixed=(a.fixed+b.fixed)/2;
                int center=(gapStart+gapEnd)/2;

                // Use directly observed floor for opening evidence. Filled floor is useful for
                // room topology, but an inpainted patch behind furniture must never manufacture
                // traversability for a doorway.
                boolean sideA,sideB;
                if(a.horizontal){
                    sideA=hasRawFloorNear(g,fixed-1,center,true);
                    sideB=hasRawFloorNear(g,fixed+1,center,true);
                }else{
                    sideA=hasRawFloorNear(g,center,fixed-1,false);
                    sideB=hasRawFloorNear(g,center,fixed+1,false);
                }
                if(!sideA&&!sideB)continue;

                ApartmentOpening d=new ApartmentOpening();
                d.orientation=a.horizontal?ApartmentOpening.Orientation.HORIZONTAL_X
                        :ApartmentOpening.Orientation.VERTICAL_Z;
                if(a.horizontal){
                    d.x1=g.originX+gapStart*g.cellM; d.x2=g.originX+(gapEnd+1)*g.cellM;
                    d.z1=d.z2=g.zCenter(fixed);
                }else{
                    d.z1=g.originZ+gapStart*g.cellM; d.z2=g.originZ+(gapEnd+1)*g.cellM;
                    d.x1=d.x2=g.xCenter(fixed);
                }
                d.widthM=width;

                boolean nearOuter=a.horizontal
                        ? fixed<=2||fixed>=g.rows-3
                        : fixed<=2||fixed>=g.cols-3;
                d.external=nearOuter;
                d.crossedByScanPath=pathCrosses(d,path);

                boolean windowProfile=windowLikeGap(g,a.horizontal,fixed,gapStart,gapEnd);
                boolean bothSides=sideA&&sideB;
                boolean oneSide=sideA^sideB;

                // Never promote a random occupancy gap to a named opening.  A doorway must
                // either have actually been crossed by the scan path, or have convincing floor
                // support on both sides plus a normal doorway width.  A window needs the
                // characteristic low+top / missing-middle vertical profile on an exterior wall.
                // Everything else stays UNKNOWN for manual/semantic review.
                boolean doorwayWidth=width>=0.62 && width<=1.35;
                boolean strongWallPair=Math.min(a.support,b.support)>=0.58;
                boolean exteriorEdge=nearExteriorFloorEdge(g,left) || nearExteriorFloorEdge(g,right);
                // Keep the pair-gap detector consistent with the continuous-wall detector below:
                // crossing an estimated wall line is not sufficient on its own. Interior doors
                // need observed floor on both sides; an exterior door may have only the scanned
                // interior side. This closes a second false-door path that survived v0.27.8.
                boolean traversableCrossing=d.crossedByScanPath
                        && (bothSides || (exteriorEdge && (sideA||sideB)));
                boolean doorEvidence=doorwayWidth && strongWallPair
                        && (traversableCrossing || bothSides);
                boolean windowEvidence=windowProfile && exteriorEdge && oneSide && !d.crossedByScanPath
                        && width>=0.50 && width<=2.80 && strongWallPair;

                if(windowEvidence){
                    d.type=ApartmentOpening.Type.WINDOW;
                    d.bottomY=Math.min(1.05,Math.max(0.55,(g.ceilingY-g.floorY)*0.28));
                    d.topY=(g.ceilingY-g.floorY)-Math.min(0.55,Math.max(0.30,(g.ceilingY-g.floorY)*0.16));
                    d.heightM=Math.max(0.40,d.topY-d.bottomY);
                    d.confidence=Math.min(0.91,0.60+0.10*Math.min(a.support,b.support));
                }else if(doorEvidence){
                    d.type=ApartmentOpening.Type.DOOR;
                    d.bottomY=0;
                    d.topY=Math.min(g.ceilingY-g.floorY,2.15);
                    d.heightM=d.topY-d.bottomY;
                    double conf=0.54+0.08*Math.min(a.support,b.support);
                    if(bothSides)conf+=0.12;
                    if(d.crossedByScanPath)conf+=0.22;
                    // Crossing is a confidence boost only after traversability has been proven.
                    d.external=exteriorEdge && !bothSides;
                    d.confidence=Math.min(0.95,conf);
                }else{
                    // Exterior gap with insufficient evidence: keep it visible for review,
                    // but do not silently call a window or a door.
                    d.type=ApartmentOpening.Type.UNKNOWN;
                    d.bottomY=0;
                    d.topY=g.ceilingY-g.floorY;
                    d.heightM=g.ceilingY-g.floorY;
                    d.confidence=0.42;
                }
                if(!duplicateOpening(out,d,g.cellM*2.6))out.add(d);
            }
        }

        // Window: on a long exterior wall, wall evidence below/above with a missing middle band.
        for(WallLineDetector.Segment s:walls){
            if(!nearExteriorFloorEdge(g,s))continue;
            int runStart=-1;
            for(int pos=s.start;pos<=s.end+1;pos++){
                boolean like=false;
                if(pos<=s.end){
                    int r=s.horizontal?s.fixed:pos;
                    int c=s.horizontal?pos:s.fixed;
                    int m=g.mask(r,c);
                    boolean low=(m&(1<<0))!=0 || (m&(1<<1))!=0;
                    boolean mid=(m&(1<<2))!=0 || (m&(1<<3))!=0;
                    boolean top=(m&(1<<4))!=0 || (m&(1<<5))!=0;
                    like=low && top && !mid;
                }
                if(like && runStart<0)runStart=pos;
                if((!like||pos==s.end+1)&&runStart>=0){
                    int end=pos-1;
                    double width=(end-runStart+1)*g.cellM;
                    if(width>=0.50&&width<=3.20){
                        ApartmentOpening w=new ApartmentOpening();
                        w.type=ApartmentOpening.Type.WINDOW;
                        w.orientation=s.horizontal?ApartmentOpening.Orientation.HORIZONTAL_X
                                :ApartmentOpening.Orientation.VERTICAL_Z;
                        if(s.horizontal){
                            w.x1=g.originX+runStart*g.cellM;w.x2=g.originX+(end+1)*g.cellM;
                            w.z1=w.z2=g.zCenter(s.fixed);
                        }else{
                            w.z1=g.originZ+runStart*g.cellM;w.z2=g.originZ+(end+1)*g.cellM;
                            w.x1=w.x2=g.xCenter(s.fixed);
                        }
                        w.widthM=width;
                        w.bottomY=Math.min(1.0,Math.max(0.55,(g.ceilingY-g.floorY)*0.28));
                        w.topY=(g.ceilingY-g.floorY)-Math.min(0.55,Math.max(0.30,(g.ceilingY-g.floorY)*0.16));
                        w.heightM=Math.max(0.4,w.topY-w.bottomY);
                        w.external=true;
                        // Geometry-only candidate: keep confidence below the level of an
                        // RGB/semantic-confirmed window and require a long structural wall.
                        w.confidence=(s.lengthCells()*g.cellM>=1.80 && s.support>=0.60)?0.58:0.44;
                        if(w.confidence>=0.55 && !overlapsDoor(out,w))out.add(w);
                    }
                    runStart=-1;
                }
            }
        }

        // After perimeter sanitization a physical wall is one continuous architectural segment.
        // Door/window openings therefore cannot rely only on "two wall segments with a gap".
        // Detect vertical-evidence gaps directly along each wall and require structural shoulders.
        detectOpeningsInsideContinuousWalls(g,walls,path,out);

        Collections.sort(out,new Comparator<ApartmentOpening>(){
            @Override public int compare(ApartmentOpening a,ApartmentOpening b){
                return Double.compare(b.confidence,a.confidence);
            }
        });
        return out;
    }

    private void detectOpeningsInsideContinuousWalls(ApartmentGrid g,List<WallLineDetector.Segment> walls,
                                                    List<ScanPathPoint> path,List<ApartmentOpening> out){
        for(WallLineDetector.Segment s:walls){
            if(s==null||s.lengthCells()*g.cellM<1.8)continue;
            int start=-1;
            for(int pos=s.start;pos<=s.end+1;pos++){
                boolean weak=false;
                if(pos<=s.end){
                    int r=s.horizontal?s.fixed:pos,c=s.horizontal?pos:s.fixed;
                    int m=combinedMask(g,s.horizontal,r,c);
                    weak=Integer.bitCount(m)<=1;
                }
                if(weak&&start<0)start=pos;
                if((!weak||pos==s.end+1)&&start>=0){
                    int end=pos-1;double width=(end-start+1)*g.cellM;
                    if(width>=0.50&&width<=2.80 && strongShoulders(g,s,start,end)){
                        ApartmentOpening o=openingForRun(g,s,start,end,width);
                        boolean crossed=pathCrosses(o,path);
                        boolean window=windowLikeGap(g,s.horizontal,s.fixed,start,end);
                        int floorSides=floorSidesForRun(g,s,start,end);
                        boolean exteriorEdge=nearExteriorFloorEdge(g,s);
                        // A camera trajectory crossing an estimated wall line is useful evidence,
                        // but by itself it is not enough: a slightly misplaced/diagonal wall can
                        // intersect the walking path and turn a furniture occlusion into a door.
                        // Interior doors must connect observed floor on both sides. Exterior doors
                        // may legitimately have only the scanned interior side.
                        boolean traversableDoor=crossed && width<=1.55
                                && (floorSides==2 || (exteriorEdge && floorSides>=1));
                        if(traversableDoor){
                            o.type=ApartmentOpening.Type.DOOR;o.bottomY=0;o.topY=Math.min(g.ceilingY-g.floorY,2.15);
                            o.heightM=o.topY;o.crossedByScanPath=true;
                            o.external=exteriorEdge && floorSides<2;
                            o.confidence=floorSides==2?0.90:0.82;
                        }else if(window && exteriorEdge){
                            o.type=ApartmentOpening.Type.WINDOW;
                            o.bottomY=Math.min(1.05,Math.max(0.55,(g.ceilingY-g.floorY)*0.28));
                            o.topY=(g.ceilingY-g.floorY)-Math.min(0.55,Math.max(0.30,(g.ceilingY-g.floorY)*0.16));
                            o.heightM=Math.max(0.40,o.topY-o.bottomY);o.external=true;o.confidence=0.66;
                        }else{o=null;}
                        if(o!=null&&!duplicateOpening(out,o,g.cellM*2.6))out.add(o);
                    }
                    start=-1;
                }
            }
        }
    }

    private ApartmentOpening openingForRun(ApartmentGrid g,WallLineDetector.Segment s,int start,int end,double width){
        ApartmentOpening o=new ApartmentOpening();o.orientation=s.horizontal?ApartmentOpening.Orientation.HORIZONTAL_X:ApartmentOpening.Orientation.VERTICAL_Z;o.widthM=width;
        if(s.horizontal){o.x1=g.originX+start*g.cellM;o.x2=g.originX+(end+1)*g.cellM;o.z1=o.z2=g.zCenter(s.fixed);}
        else{o.z1=g.originZ+start*g.cellM;o.z2=g.originZ+(end+1)*g.cellM;o.x1=o.x2=g.xCenter(s.fixed);}
        return o;
    }

    private int combinedMask(ApartmentGrid g,boolean horizontal,int r,int c){
        int m=0;for(int off=-1;off<=1;off++){int rr=horizontal?r+off:r,cc=horizontal?c:c+off;m|=g.mask(rr,cc);}return m;
    }

    private boolean strongShoulders(ApartmentGrid g,WallLineDetector.Segment s,int start,int end){
        int left=0,right=0;
        for(int d=1;d<=3;d++){
            int p=start-d;if(p>=s.start){int r=s.horizontal?s.fixed:p,c=s.horizontal?p:s.fixed;if(Integer.bitCount(combinedMask(g,s.horizontal,r,c))>=3)left++;}
            p=end+d;if(p<=s.end){int r=s.horizontal?s.fixed:p,c=s.horizontal?p:s.fixed;if(Integer.bitCount(combinedMask(g,s.horizontal,r,c))>=3)right++;}
        }
        return left>=1&&right>=1;
    }


    /** Returns how many sides of a candidate wall opening have directly observed floor support. */
    private int floorSidesForRun(ApartmentGrid g,WallLineDetector.Segment s,int start,int end){
        int a=0,b=0,samples=0;
        int span=Math.max(1,end-start+1);
        int step=Math.max(1,span/5);
        for(int p=start;p<=end;p+=step){
            samples++;
            if(s.horizontal){
                if(hasRawFloorNear(g,s.fixed-1,p,true))a++;
                if(hasRawFloorNear(g,s.fixed+1,p,true))b++;
            }else{
                if(hasRawFloorNear(g,p,s.fixed-1,false))a++;
                if(hasRawFloorNear(g,p,s.fixed+1,false))b++;
            }
        }
        if(samples==0)return 0;
        int need=Math.max(1,(samples+2)/3);
        return (a>=need?1:0)+(b>=need?1:0);
    }

    private boolean hasRawFloorNear(ApartmentGrid g,int r,int c,boolean horizontal){
        for(int d=-2;d<=2;d++){
            int rr=horizontal?r:r+d,cc=horizontal?c+d:c;
            if(g.inside(rr,cc)&&g.rawFloor[rr][cc])return true;
        }
        return false;
    }

    private boolean windowLikeGap(ApartmentGrid g,boolean horizontal,int fixed,int start,int end){
        int valid=0,windowLike=0;
        for(int p=start;p<=end;p++){
            int r=horizontal?fixed:p;
            int c=horizontal?p:fixed;
            int m=0;
            // Sample the wall thickness around the nominal line.
            for(int off=-1;off<=1;off++){
                int rr=horizontal?r+off:r;
                int cc=horizontal?c:c+off;
                m|=g.mask(rr,cc);
            }
            if(m==0)continue;
            valid++;
            boolean low=(m&(1<<0))!=0 || (m&(1<<1))!=0;
            boolean mid=(m&(1<<2))!=0 || (m&(1<<3))!=0;
            boolean top=(m&(1<<4))!=0 || (m&(1<<5))!=0;
            if(low&&top&&!mid)windowLike++;
        }
        return valid>0 && windowLike>=Math.max(1,(int)Math.ceil(valid*0.45));
    }

    private boolean hasFloorNear(ApartmentGrid g,int r,int c,boolean horizontal){
        for(int d=-2;d<=2;d++){
            int rr=horizontal?r:r+d;
            int cc=horizontal?c+d:c;
            if(g.isFloor(rr,cc))return true;
        }
        return false;
    }

    /**
     * Conservative exterior-wall evidence from directly observed floor only.
     *
     * Room-footprint inpainting is intentionally excluded here.  Using g.isFloor() used to let
     * a filled patch behind a sofa/bed make an interior partition look one-sided; that in turn
     * relaxed doorway traversability to a single side and reopened a false-door path.  Sample a
     * small physical band on both sides instead, because the nominal wall cell can be displaced
     * by one or two 10 cm cells after axis snapping / structural consolidation.
     */
    private boolean nearExteriorFloorEdge(ApartmentGrid g,WallLineDetector.Segment s){
        int votesA=0,votesB=0,samples=0;
        int step=Math.max(1,s.lengthCells()/8);
        for(int p=s.start;p<=s.end;p+=step){
            samples++;
            boolean a=false,b=false;
            for(int off=1;off<=3;off++){
                if(s.horizontal){
                    a|=rawFloor(g,s.fixed-off,p);
                    b|=rawFloor(g,s.fixed+off,p);
                }else{
                    a|=rawFloor(g,p,s.fixed-off);
                    b|=rawFloor(g,p,s.fixed+off);
                }
            }
            if(a)votesA++;if(b)votesB++;
        }
        // Need repeated observations along the wall, not one lucky Depth pixel.  The opposite
        // side may contain an occasional bleed/noise sample, so allow a small minority there.
        if(samples<3)return false;
        int strong=Math.max(2,(int)Math.ceil(samples*0.60));
        int weak=(int)Math.floor(samples*0.18);
        return (votesA>=strong&&votesB<=weak)||(votesB>=strong&&votesA<=weak);
    }

    private boolean rawFloor(ApartmentGrid g,int r,int c){
        return g.inside(r,c)&&g.rawFloor!=null&&g.rawFloor[r]!=null&&g.rawFloor[r][c];
    }

    private boolean pathCrosses(ApartmentOpening o,List<ScanPathPoint> path){
        if(path==null||path.size()<2||o==null)return false;
        // A path merely passing near a gap is not evidence of a door.  Require an actual
        // crossing of the wall line inside (or just beside) the physical opening span.
        // This prevents a walk parallel to a wall from turning window/occlusion gaps into doors.
        final double alongMargin=Math.max(0.18,Math.min(0.35,o.widthM*0.22));
        final double sideEps=0.06;
        for(int i=1;i<path.size();i++){
            ScanPathPoint a=path.get(i-1),b=path.get(i);
            double dx=b.x-a.x,dz=b.z-a.z;
            if(dx*dx+dz*dz<0.01)continue; // ignore tracking jitter / nearly stationary samples

            if(o.orientation==ApartmentOpening.Orientation.HORIZONTAL_X){
                double wallZ=o.centerZ();
                double sa=a.z-wallZ,sb=b.z-wallZ;
                if((sa>sideEps&&sb>sideEps)||(sa<-sideEps&&sb<-sideEps))continue;
                if(Math.abs(dz)<1e-6)continue;
                double t=(wallZ-a.z)/dz;
                if(t<0||t>1)continue;
                double ix=a.x+t*dx;
                double lo=Math.min(o.x1,o.x2)-alongMargin,hi=Math.max(o.x1,o.x2)+alongMargin;
                if(ix>=lo&&ix<=hi)return true;
            }else{
                double wallX=o.centerX();
                double sa=a.x-wallX,sb=b.x-wallX;
                if((sa>sideEps&&sb>sideEps)||(sa<-sideEps&&sb<-sideEps))continue;
                if(Math.abs(dx)<1e-6)continue;
                double t=(wallX-a.x)/dx;
                if(t<0||t>1)continue;
                double iz=a.z+t*dz;
                double lo=Math.min(o.z1,o.z2)-alongMargin,hi=Math.max(o.z1,o.z2)+alongMargin;
                if(iz>=lo&&iz<=hi)return true;
            }
        }
        return false;
    }

    private double distancePointToSegment(double px,double pz,double ax,double az,double bx,double bz){
        double dx=bx-ax,dz=bz-az;
        double den=dx*dx+dz*dz;
        if(den<1e-9)return Math.hypot(px-ax,pz-az);
        double t=((px-ax)*dx+(pz-az)*dz)/den;
        t=Math.max(0,Math.min(1,t));
        return Math.hypot(px-(ax+t*dx),pz-(az+t*dz));
    }

    private boolean duplicateOpening(List<ApartmentOpening> list,ApartmentOpening x,double tol){
        for(ApartmentOpening a:list){
            if(a.type!=x.type||a.orientation!=x.orientation)continue;
            if(Math.hypot(a.centerX()-x.centerX(),a.centerZ()-x.centerZ())<tol)return true;
        }
        return false;
    }

    private boolean overlapsDoor(List<ApartmentOpening> list,ApartmentOpening w){
        for(ApartmentOpening a:list){
            if(a.type!=ApartmentOpening.Type.DOOR)continue;
            if(a.orientation!=w.orientation)continue;
            if(Math.hypot(a.centerX()-w.centerX(),a.centerZ()-w.centerZ())
                    <(a.widthM+w.widthM)*0.55)return true;
        }
        return false;
    }
}
