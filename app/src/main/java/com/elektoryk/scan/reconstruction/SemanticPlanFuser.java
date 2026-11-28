package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.ApartmentOpening;
import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.PlanWallSegment;
import com.elektoryk.scan.model.SemanticEntity;
import com.elektoryk.scan.semantic.SemanticLabel;
import com.elektoryk.scan.semantic.SemanticSceneTracker;
import com.google.ar.core.Pose;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Uses RGB semantic masks to name geometric openings and, when possible, add an opening that
 * geometry alone missed (e.g. glass window with sparse Depth).
 */
public final class SemanticPlanFuser {
    private static final int MIN_MASK_PIXELS=18;

    // Reused analysis workspace. SemanticPlanFuser is intentionally serialized because a final
    // reconstruction pass can walk dozens of retained semantic frames; allocating two full-frame
    // component buffers for every record caused large avoidable GC bursts on long scans.
    private boolean[] maskSeenWorkspace=new boolean[0];
    private int[] maskQueueWorkspace=new int[0];
    private final float[] pointIn={0f,0f,0f};
    private final float[] pointCam={0f,0f,0f};
    private final float[] rayLocal={0f,0f,-1f};
    private final float[] rayWorld={0f,0f,0f};

    public synchronized void apply(ApartmentPlan plan,List<SemanticEntity> entities,List<SemanticSceneTracker.Record> records,double yawRad){
        if(plan==null)return;
        if(records!=null){
            for(ApartmentOpening o:plan.openings)classifyExisting(o,records,yawRad,plan);
            addVisualOpenings(plan,records,yawRad,SemanticLabel.DOOR);
            addVisualOpenings(plan,records,yawRad,SemanticLabel.WINDOW);
        }
        if(entities!=null){
            for(SemanticEntity e:entities){
                rotateEntityInPlace(e,yawRad);
                if(e.type==SemanticEntity.Type.DOOR||e.type==SemanticEntity.Type.WINDOW)addEntityOpening(plan,e);
            }
        }
        // A geometry gap crossed by the user but not classified as a door/window is a passage.
        for(ApartmentOpening o:plan.openings){
            if(o.type==ApartmentOpening.Type.UNKNOWN&&o.crossedByScanPath&&o.widthM>=0.55&&o.widthM<=2.8)
                o.type=ApartmentOpening.Type.PASSAGE;
        }
        dedupe(plan);
    }

    private void classifyExisting(ApartmentOpening o,List<SemanticSceneTracker.Record> records,double yaw,ApartmentPlan plan){
        if(o==null)return;int doors=0,windows=0,total=0;
        double floor=plan.floorY,cy=(o.bottomY+o.topY)*0.5;
        if(o.topY<=o.bottomY)cy=floor+1.1;
        double[][] samples={{o.centerX(),cy,o.centerZ()},{o.centerX(),floor+0.5,o.centerZ()},{o.centerX(),floor+1.7,o.centerZ()}};
        for(SemanticSceneTracker.Record r:records){
            if(r==null||r.frame==null||r.meta==null)continue;
            for(double[] p:samples){SemanticLabel lab=labelAtNormalizedPoint(r,p[0],p[1],p[2],yaw);if(lab==SemanticLabel.UNKNOWN)continue;total++;if(lab==SemanticLabel.DOOR)doors++;if(lab==SemanticLabel.WINDOW)windows++;}
        }
        if(doors>=2&&doors>windows){o.type=ApartmentOpening.Type.DOOR;o.confidence=Math.max(o.confidence,Math.min(.96,.55+doors*.05));}
        else if(windows>=2&&windows>doors){o.type=ApartmentOpening.Type.WINDOW;o.external=true;o.roomBId="";o.confidence=Math.max(o.confidence,Math.min(.96,.55+windows*.05));}
    }

    private SemanticLabel labelAtNormalizedPoint(SemanticSceneTracker.Record r,double nx,double ny,double nz,double yaw){
        double c=Math.cos(yaw),s=Math.sin(yaw);float lx=(float)(c*nx-s*nz),lz=(float)(s*nx+c*nz);float ly=(float)ny;
        Pose inv=r.meta.pose().inverse();pointIn[0]=lx;pointIn[1]=ly;pointIn[2]=lz;inv.transformPoint(pointIn,0,pointCam,0);float depth=-pointCam[2];if(depth<.2f||depth>7f)return SemanticLabel.UNKNOWN;
        float u=r.meta.cx+r.meta.fx*pointCam[0]/depth,v=r.meta.cy-r.meta.fy*pointCam[1]/depth;
        if(u<0||v<0||u>=r.meta.width||v>=r.meta.height)return SemanticLabel.UNKNOWN;return r.frame.labelAtSource(u,v);
    }

    /** Project coherent semantic door/window components onto the nearest reconstructed wall. */
    private void addVisualOpenings(ApartmentPlan plan,List<SemanticSceneTracker.Record> records,double yaw,SemanticLabel target){
        for(SemanticSceneTracker.Record r:records){
            int rawTarget=SemanticLabel.modelIndexFor(target,r.frame.flavor);
            if(rawTarget<0)continue;
            for(MaskBox b:maskBoxes(r,rawTarget)){
                if(b==null||b.count<MIN_MASK_PIXELS)continue;
                Hit hc=projectPixelToWall(plan,r,(b.x0+b.x1)*.5f,(b.y0+b.y1)*.5f,yaw);
                if(hc==null)continue;
                PlanWallSegment wall=wallById(plan,hc.wallId);if(wall==null)continue;
                Hit hl=projectPixelToSpecificWall(wall,r,b.x0,(b.y0+b.y1)*.5f,yaw),hr=projectPixelToSpecificWall(wall,r,b.x1,(b.y0+b.y1)*.5f,yaw);
                Hit ht=projectPixelToSpecificWall(wall,r,(b.x0+b.x1)*.5f,b.y0,yaw),hb=projectPixelToSpecificWall(wall,r,(b.x0+b.x1)*.5f,b.y1,yaw);
                if(hl==null||hr==null||ht==null||hb==null)continue;
                double width=Math.hypot(hr.x-hl.x,hr.z-hl.z),height=Math.abs(ht.y-hb.y);
                if(target==SemanticLabel.DOOR){if(width<.45||width>2.3||height<1.25||height>3.0)continue;}
                else {if(width<.30||width>3.8||height<.25||height>2.8)continue;}
                ApartmentOpening.Type type=target==SemanticLabel.DOOR?ApartmentOpening.Type.DOOR:ApartmentOpening.Type.WINDOW;
                if(hasOpeningNear(plan,hc.x,hc.z,type,.65))continue;
                ApartmentOpening o=new ApartmentOpening();o.wallId=wall.id;o.type=type;
                o.x1=hl.x;o.z1=hl.z;o.x2=hr.x;o.z2=hr.z;o.widthM=width;o.bottomY=Math.min(ht.y,hb.y);o.topY=Math.max(ht.y,hb.y);o.heightM=height;o.confidence=.78;
                o.orientation=orientationOf(wall);
                if(o.type==ApartmentOpening.Type.DOOR&&o.bottomY>plan.floorY+.35)o.bottomY=plan.floorY;
                bindOpeningToRooms(plan,o,wall);
                if(o.type==ApartmentOpening.Type.WINDOW){o.external=true;o.roomBId="";}
                plan.openings.add(o);
            }
        }
    }

    /** ADE20K is semantic rather than instance segmentation, so split masks into components. */
    private List<MaskBox> maskBoxes(SemanticSceneTracker.Record r,int label){
        List<MaskBox> out=new ArrayList<>();if(r==null||r.frame==null||r.frame.labels==null)return out;
        int w=r.frame.width,h=r.frame.height;byte[] m=r.frame.labels;int pixels=w*h;
        ensureMaskWorkspace(pixels);boolean[] seen=maskSeenWorkspace;int[] q=maskQueueWorkspace;
        // Only the active prefix belongs to this frame; capacity can be larger after a high-res frame.
        Arrays.fill(seen,0,pixels,false);
        for(int sy=0;sy<h;sy++)for(int sx=0;sx<w;sx++){
            int start=sy*w+sx;if(seen[start]||(m[start]&255)!=label)continue;
            int qs=0,qe=0;q[qe++]=start;seen[start]=true;int minX=sx,maxX=sx,minY=sy,maxY=sy,n=0;
            while(qs<qe){int idx=q[qs++],x=idx%w,y=idx/w;n++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                if(x>0)qe=push(m,seen,q,qe,idx-1,label);if(x+1<w)qe=push(m,seen,q,qe,idx+1,label);if(y>0)qe=push(m,seen,q,qe,idx-w,label);if(y+1<h)qe=push(m,seen,q,qe,idx+w,label);
            }
            if(n<MIN_MASK_PIXELS||maxX<=minX||maxY<=minY)continue;
            MaskBox b=new MaskBox();b.count=n;b.x0=(minX/(float)w)*r.meta.width;b.x1=((maxX+1)/(float)w)*r.meta.width;b.y0=(minY/(float)h)*r.meta.height;b.y1=((maxY+1)/(float)h)*r.meta.height;out.add(b);
        }
        return out;
    }

    private void ensureMaskWorkspace(int pixels){
        if(pixels<=maskSeenWorkspace.length)return;
        int cap=Math.max(pixels,Math.max(1024,maskSeenWorkspace.length*2));
        maskSeenWorkspace=new boolean[cap];
        maskQueueWorkspace=new int[cap];
    }
    private int push(byte[] m,boolean[] seen,int[] q,int qe,int idx,int label){if(!seen[idx]&&(m[idx]&255)==label){seen[idx]=true;q[qe++]=idx;}return qe;}
    private static final class MaskBox{float x0,x1,y0,y1;int count;}
    private static final class Hit{String wallId;double x,y,z,t,u;}

    private Hit projectPixelToWall(ApartmentPlan plan,SemanticSceneTracker.Record r,float px,float py,double yaw){
        Hit best=null;for(PlanWallSegment w:plan.walls){Hit h=projectPixelToSpecificWall(w,r,px,py,yaw);if(h!=null&&(best==null||h.t<best.t))best=h;}return best;
    }
    private Hit projectPixelToSpecificWall(PlanWallSegment w,SemanticSceneTracker.Record r,float px,float py,double yaw){
        float dx=(px-r.meta.cx)/Math.max(1f,r.meta.fx),dy=-(py-r.meta.cy)/Math.max(1f,r.meta.fy);rayLocal[0]=dx;rayLocal[1]=dy;rayLocal[2]=-1f;r.meta.pose().rotateVector(rayLocal,0,rayWorld,0);
        double c=Math.cos(yaw),s=Math.sin(yaw);double ox=c*r.meta.tx+s*r.meta.tz,oz=-s*r.meta.tx+c*r.meta.tz,oy=r.meta.ty;double rx=c*rayWorld[0]+s*rayWorld[2],rz=-s*rayWorld[0]+c*rayWorld[2],ry=rayWorld[1];
        double sx=w.x2-w.x1,sz=w.z2-w.z1;double den=cross(rx,rz,sx,sz);if(Math.abs(den)<1e-7)return null;
        double ax=w.x1-ox,az=w.z1-oz;double t=cross(ax,az,sx,sz)/den,u=cross(ax,az,rx,rz)/den;if(t<.15||t>7.0||u<-.08||u>1.08)return null;
        Hit h=new Hit();h.wallId=w.id;h.t=t;h.u=u;h.x=ox+t*rx;h.z=oz+t*rz;h.y=oy+t*ry;return h;
    }
    private static double cross(double ax,double az,double bx,double bz){return ax*bz-az*bx;}

    private void addEntityOpening(ApartmentPlan plan,SemanticEntity e){
        double cx=e.centerX(),cz=e.centerZ();PlanWallSegment w=nearestWall(plan,cx,cz);if(w==null)return;double d=distancePointSegment(cx,cz,w);if(d>.55)return;
        ApartmentOpening.Type type=e.type==SemanticEntity.Type.DOOR?ApartmentOpening.Type.DOOR:ApartmentOpening.Type.WINDOW;if(hasOpeningNear(plan,cx,cz,type,.65))return;
        double len=w.lengthM();if(len<.2)return;double ux=(w.x2-w.x1)/len,uz=(w.z2-w.z1)/len;double along=(cx-w.x1)*ux+(cz-w.z1)*uz;along=Math.max(.15,Math.min(len-.15,along));
        // Semantic Depth bounds are oriented in the object's own frame. Using max(width,depth)
        // made a door/window too wide whenever wall-depth contamination survived the mask edge.
        // Project the oriented rectangle onto the actual wall tangent instead: only the extent
        // physically running along this wall becomes opening width.
        double a=Math.toRadians(e.rotationDeg),mx=Math.cos(a),mz=Math.sin(a),nx=-mz,nz=mx;
        double projectedWidth=Math.abs(ux*mx+uz*mz)*e.widthM()+Math.abs(ux*nx+uz*nz)*e.depthM();
        if(!Double.isFinite(projectedWidth)||projectedWidth<.12)projectedWidth=Math.max(e.widthM(),e.depthM());
        double maxByWall=Math.max(.30,2.0*Math.max(.15,Math.min(along,len-along))-.10);
        double width=Math.max(.45,Math.min(Math.min(type==ApartmentOpening.Type.DOOR?2.2:3.5,maxByWall),projectedWidth));
        ApartmentOpening o=new ApartmentOpening();o.type=type;o.wallId=w.id;o.widthM=width;o.heightM=Math.max(.3,e.heightM());
        // Doors are floor-connected architectural voids. Sparse Depth often misses the lowest
        // pixels because of threshold erosion, so never leave a floating semantic door.
        if(type==ApartmentOpening.Type.DOOR){o.bottomY=plan.floorY;o.heightM=Math.max(1.65,Math.min(2.8,o.heightM));o.topY=o.bottomY+o.heightM;}
        else{o.bottomY=Math.max(plan.floorY,e.minY);o.topY=Math.min(plan.ceilingY,e.maxY);if(o.topY-o.bottomY<.30)o.topY=Math.min(plan.ceilingY,o.bottomY+Math.max(.30,o.heightM));o.heightM=Math.max(.30,o.topY-o.bottomY);}
        o.confidence=Math.max(.65,e.confidence);o.orientation=orientationOf(w);
        double ccx=w.x1+along*ux,ccz=w.z1+along*uz;o.x1=ccx-ux*width*.5;o.z1=ccz-uz*width*.5;o.x2=ccx+ux*width*.5;o.z2=ccz+uz*width*.5;bindOpeningToRooms(plan,o,w);if(type==ApartmentOpening.Type.WINDOW){o.external=true;o.roomBId="";}plan.openings.add(o);
    }

    private static ApartmentOpening.Orientation orientationOf(PlanWallSegment w){
        return Math.abs(w.x2-w.x1)>=Math.abs(w.z2-w.z1)
                ?ApartmentOpening.Orientation.HORIZONTAL_X:ApartmentOpening.Orientation.VERTICAL_Z;
    }
    private static void bindOpeningToRooms(ApartmentPlan p,ApartmentOpening o,PlanWallSegment w){
        double dx=w.x2-w.x1,dz=w.z2-w.z1,len=Math.max(1e-9,Math.hypot(dx,dz));
        double nx=-dz/len,nz=dx/len;
        com.elektoryk.scan.model.RoomZone a=roomAt(p,o.centerX()+nx*.35,o.centerZ()+nz*.35);
        com.elektoryk.scan.model.RoomZone b=roomAt(p,o.centerX()-nx*.35,o.centerZ()-nz*.35);
        o.roomAId=a==null?"":a.id;o.roomBId=b==null?"":b.id;
        if(!o.roomAId.isEmpty()&&o.roomAId.equals(o.roomBId))o.roomBId="";
        o.external=o.roomAId.isEmpty() ^ o.roomBId.isEmpty();
    }
    private static com.elektoryk.scan.model.RoomZone roomAt(ApartmentPlan p,double x,double z){
        if(p==null||p.cellM<=0)return null;
        int c=(int)Math.floor((x-p.originX)/p.cellM),r=(int)Math.floor((z-p.originZ)/p.cellM);
        for(com.elektoryk.scan.model.RoomZone room:p.rooms)for(com.elektoryk.scan.model.PlanGridRun run:room.floorRuns)
            if(run.row==r&&c>=run.startCol&&c<=run.endCol)return room;
        return null;
    }
    private static void rotateEntityInPlace(SemanticEntity e,double yaw){
        if(e==null||Math.abs(yaw)<1e-12)return;
        double c=Math.cos(yaw),s=Math.sin(yaw);
        // Rotate the legacy/world AABB into normalized-plan coordinates. This AABB is only an
        // envelope; orientedWidth/Depth stay in the entity's own frame.
        double x0=e.minX,x1=e.maxX,z0=e.minZ,z1=e.maxZ;
        double mnx=Double.POSITIVE_INFINITY,mxx=Double.NEGATIVE_INFINITY,mnz=Double.POSITIVE_INFINITY,mxz=Double.NEGATIVE_INFINITY;
        double nx=c*x0+s*z0,nz=-s*x0+c*z0;mnx=Math.min(mnx,nx);mxx=Math.max(mxx,nx);mnz=Math.min(mnz,nz);mxz=Math.max(mxz,nz);
        nx=c*x0+s*z1;nz=-s*x0+c*z1;mnx=Math.min(mnx,nx);mxx=Math.max(mxx,nx);mnz=Math.min(mnz,nz);mxz=Math.max(mxz,nz);
        nx=c*x1+s*z0;nz=-s*x1+c*z0;mnx=Math.min(mnx,nx);mxx=Math.max(mxx,nx);mnz=Math.min(mnz,nz);mxz=Math.max(mxz,nz);
        nx=c*x1+s*z1;nz=-s*x1+c*z1;mnx=Math.min(mnx,nx);mxx=Math.max(mxx,nx);mnz=Math.min(mnz,nz);mxz=Math.max(mxz,nz);
        e.minX=mnx;e.maxX=mxx;e.minZ=mnz;e.maxZ=mxz;
        // Since v0.24 the semantic tracker uses a robust fused centroid. Leaving that centroid in
        // scan coordinates while rotating only the AABB made centerX()/centerZ() return the wrong
        // position after room-axis normalization, moving promoted furniture and semantic openings.
        if(e.fusedCenterValid){double fx=e.fusedCenterX,fz=e.fusedCenterZ;e.fusedCenterX=c*fx+s*fz;e.fusedCenterZ=-s*fx+c*fz;}
        // The same coordinate transform rotates a horizontal direction by -yaw. Keep the 180°
        // symmetric PCA axis normalized so opening-width projection and furniture promotion use
        // the same frame as walls/rooms.
        e.rotationDeg=normalizeAxialDeg(e.rotationDeg-Math.toDegrees(yaw));
    }
    private static double normalizeAxialDeg(double deg){
        if(!Double.isFinite(deg))return 0;deg%=180.0;if(deg<0)deg+=180.0;return deg;
    }
    private static PlanWallSegment nearestWall(ApartmentPlan p,double x,double z){PlanWallSegment best=null;double bd=Double.POSITIVE_INFINITY;for(PlanWallSegment w:p.walls){double d=distancePointSegment(x,z,w);if(d<bd){bd=d;best=w;}}return best;}
    private static double distancePointSegment(double x,double z,PlanWallSegment w){double dx=w.x2-w.x1,dz=w.z2-w.z1,l2=dx*dx+dz*dz;if(l2<1e-9)return Math.hypot(x-w.x1,z-w.z1);double t=((x-w.x1)*dx+(z-w.z1)*dz)/l2;t=Math.max(0,Math.min(1,t));return Math.hypot(x-(w.x1+t*dx),z-(w.z1+t*dz));}
    private static PlanWallSegment wallById(ApartmentPlan p,String id){for(PlanWallSegment w:p.walls)if(w.id.equals(id))return w;return null;}
    private static boolean hasOpeningNear(ApartmentPlan p,double x,double z,ApartmentOpening.Type t,double r){for(ApartmentOpening o:p.openings)if((o.type==t||o.type==ApartmentOpening.Type.UNKNOWN)&&Math.hypot(o.centerX()-x,o.centerZ()-z)<r)return true;return false;}
    private void dedupe(ApartmentPlan p){
        List<ApartmentOpening> keep=new ArrayList<>();
        for(ApartmentOpening o:p.openings){
            int match=-1;for(int i=0;i<keep.size();i++){ApartmentOpening k=keep.get(i);if(k.type==o.type&&Math.hypot(k.centerX()-o.centerX(),k.centerZ()-o.centerZ())<.35){match=i;break;}}
            if(match<0)keep.add(o);else if(o.confidence>keep.get(match).confidence)keep.set(match,o);
        }
        p.openings.clear();p.openings.addAll(keep);
    }
}
