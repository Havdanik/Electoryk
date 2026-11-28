package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.PlanWallSegment;

public class WallSnapper {
    public static class Result{
        public double x,z,distanceM;
        public String wallId="";
    }

    public Result snap(ApartmentPlan plan,double x,double z,double maxDistanceM){
        if(plan==null)return null;
        Result best=null;
        for(PlanWallSegment w:plan.walls){
            double dx=w.x2-w.x1,dz=w.z2-w.z1,den=dx*dx+dz*dz;
            if(den<1e-9)continue;
            double t=((x-w.x1)*dx+(z-w.z1)*dz)/den;
            t=Math.max(0,Math.min(1,t));
            double sx=w.x1+t*dx,sz=w.z1+t*dz;
            double d=Math.hypot(x-sx,z-sz);
            if(d<=maxDistanceM&&(best==null||d<best.distanceM)){
                best=new Result();best.x=sx;best.z=sz;best.distanceM=d;best.wallId=w.id;
            }
        }
        return best;
    }
}
