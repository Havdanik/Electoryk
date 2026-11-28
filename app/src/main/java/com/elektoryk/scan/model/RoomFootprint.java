package com.elektoryk.scan.model;

/**
 * Single source of truth for room-footprint membership.
 * Room min/max bounds are only a coarse envelope; irregular rooms must use floorRuns.
 *
 * The helpers are intentionally defensive because plans can also arrive through import/recovery.
 * A malformed grid must never propagate NaN coordinates or crash the 2D/3D editors.
 */
public final class RoomFootprint {
    private RoomFootprint() {}

    public static boolean contains(ApartmentPlan plan, RoomZone room, double x, double z) {
        return contains(plan, room, x, z, 0);
    }

    public static boolean contains(ApartmentPlan plan, RoomZone room, double x, double z, int toleranceCells) {
        if (room == null || !finite(x) || !finite(z)) return false;
        if (room.floorRuns.isEmpty() || !validGrid(plan)) {
            double cell=(plan!=null&&finite(plan.cellM)&&plan.cellM>0)?Math.max(.01,plan.cellM):0;
            double tol=Math.max(0,toleranceCells)*cell;
            return envelopeContains(room,x,z,tol);
        }
        double cell=Math.max(.01,plan.cellM);
        int col=safeCellIndex((x-plan.originX)/cell), row=safeCellIndex((z-plan.originZ)/cell);
        int t=Math.max(0,toleranceCells);
        for(PlanGridRun run:room.floorRuns){
            if(!validRun(run))continue;
            if(Math.abs((long)run.row-(long)row)>t)continue;
            long lo=(long)run.startCol-t,hi=(long)run.endCol+t;
            if((long)col>=lo&&(long)col<=hi)return true;
        }
        return false;
    }

    /** Returns the requested point if valid, otherwise the centre of the nearest footprint cell. */
    public static double[] nearestInside(ApartmentPlan plan, RoomZone room, double x, double z) {
        if (!finite(x)||!finite(z)) {
            x=room!=null&&finite(room.centroidX)?room.centroidX:0;
            z=room!=null&&finite(room.centroidZ)?room.centroidZ:0;
        }
        if (contains(plan,room,x,z)) return new double[]{x,z};
        if(room==null||room.floorRuns.isEmpty()||!validGrid(plan))return envelopeFallback(room,x,z);
        double cell=Math.max(.01,plan.cellM),best=Double.POSITIVE_INFINITY,bx=x,bz=z;
        boolean found=false;
        for(PlanGridRun run:room.floorRuns){
            if(!validRun(run))continue;
            double zc=plan.originZ+((double)run.row+.5)*cell;
            int target=safeCellIndex((x-plan.originX)/cell);
            int col=Math.max(run.startCol,Math.min(run.endCol,target));
            double xc=plan.originX+((double)col+.5)*cell,d=(xc-x)*(xc-x)+(zc-z)*(zc-z);
            if(finite(xc)&&finite(zc)&&finite(d)&&d<best){best=d;bx=xc;bz=zc;found=true;}
        }
        return found?new double[]{bx,bz}:envelopeFallback(room,x,z);
    }

    private static boolean validGrid(ApartmentPlan plan){
        return plan!=null&&finite(plan.cellM)&&plan.cellM>0&&plan.cellM<=2&&finite(plan.originX)&&finite(plan.originZ);
    }
    private static boolean validRun(PlanGridRun run){return run!=null&&run.startCol<=run.endCol;}
    private static boolean envelopeContains(RoomZone room,double x,double z,double tol){
        return finite(room.minX)&&finite(room.maxX)&&finite(room.minZ)&&finite(room.maxZ)
                &&room.minX<=room.maxX&&room.minZ<=room.maxZ
                &&x>=room.minX-tol&&x<=room.maxX+tol&&z>=room.minZ-tol&&z<=room.maxZ+tol;
    }
    private static double[] envelopeFallback(RoomZone room,double x,double z){
        if(room==null||!finite(room.minX)||!finite(room.maxX)||!finite(room.minZ)||!finite(room.maxZ)
                ||room.minX>room.maxX||room.minZ>room.maxZ)return new double[]{x,z};
        return new double[]{clamp(x,room.minX,room.maxX),clamp(z,room.minZ,room.maxZ)};
    }
    private static int safeCellIndex(double v){
        if(Double.isNaN(v))return 0;if(v>=Integer.MAX_VALUE)return Integer.MAX_VALUE;if(v<=Integer.MIN_VALUE)return Integer.MIN_VALUE;return (int)Math.floor(v);
    }
    private static boolean finite(double v){return !Double.isNaN(v)&&!Double.isInfinite(v);}
    private static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
}
