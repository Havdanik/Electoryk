package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.CloudPoint;
import com.elektoryk.scan.model.RoomBounds;
import com.elektoryk.scan.model.SurfaceObservation;
import com.elektoryk.scan.model.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RoomAnalyzer {

    public static class Result {
        public RoomBounds bounds;
        public final List<SurfaceObservation> surfaces = new ArrayList<>();
    }

    public Result analyze(List<CloudPoint> points) {
        Result out = new Result();
        if (points == null || points.size() < 120) return out;

        int finiteCount=0;
        for (CloudPoint p : points) {
            if (p!=null && finite(p.x) && finite(p.y) && finite(p.z)) finiteCount++;
        }
        if (finiteCount < 120) return out;

        // Robust envelope is confidence-weighted. A cloud of weak interpolated/ghost points
        // must not move the search window away from the real wall before histogram refinement.
        // Sort each axis once and read both tails from the same weighted distribution. The old
        // implementation sorted the full cloud six times (min+max separately), doubling CPU/GC
        // pressure at the start of reconstruction for no gain in accuracy.
        double[] xRange=weightedRange(points,Axis.X,0.015,0.985);
        double[] yRange=weightedRange(points,Axis.Y,0.015,0.985);
        double[] zRange=weightedRange(points,Axis.Z,0.015,0.985);
        double qMinX=xRange[0],qMaxX=xRange[1];
        double qMinY=yRange[0],qMaxY=yRange[1];
        double qMinZ=zRange[0],qMaxZ=zRange[1];

        // Refine each outer boundary using the strongest 5 cm histogram peak
        // near the robust envelope. This suppresses sparse scan spikes.
        double minX = peakNear(points, Axis.X, qMinX, qMinX + 0.45, 0.05, qMinX);
        double maxX = peakNear(points, Axis.X, qMaxX - 0.45, qMaxX, 0.05, qMaxX);
        double floorY = peakNear(points, Axis.Y, qMinY, qMinY + 0.40, 0.04, qMinY);
        double ceilingY = peakNear(points, Axis.Y, qMaxY - 0.40, qMaxY, 0.04, qMaxY);
        double minZ = peakNear(points, Axis.Z, qMinZ, qMinZ + 0.45, 0.05, qMinZ);
        double maxZ = peakNear(points, Axis.Z, qMaxZ - 0.45, qMaxZ, 0.05, qMaxZ);

        RoomBounds b = new RoomBounds();
        b.minX = minX; b.maxX = maxX;
        b.floorY = floorY; b.ceilingY = ceilingY;
        b.minZ = minZ; b.maxZ = maxZ;

        double support = boundarySupport(points, b, 0.09);
        double shape = (b.widthM() > 1 && b.lengthM() > 1 && b.heightM() > 2)
                ? 1.0 : 0.35;
        b.confidence = clamp(0.2 + support * 1.8, 0, 1) * shape;
        out.bounds = b;

        if (b.isUsable()) {
            out.surfaces.add(horizontal(SurfaceObservation.Kind.FLOOR,
                    (b.floorY), b, new Vec3(0,1,0), b.confidence));
            out.surfaces.add(horizontal(SurfaceObservation.Kind.CEILING,
                    (b.ceilingY), b, new Vec3(0,-1,0), b.confidence));

            out.surfaces.add(wallX(b.minX, b, new Vec3(1,0,0), b.confidence));
            out.surfaces.add(wallX(b.maxX, b, new Vec3(-1,0,0), b.confidence));
            out.surfaces.add(wallZ(b.minZ, b, new Vec3(0,0,1), b.confidence));
            out.surfaces.add(wallZ(b.maxZ, b, new Vec3(0,0,-1), b.confidence));
        }
        return out;
    }

    private SurfaceObservation horizontal(SurfaceObservation.Kind kind, double y,
                                          RoomBounds b, Vec3 normal, double conf) {
        SurfaceObservation s = new SurfaceObservation();
        s.kind = kind;
        s.horizontal = true;
        s.normal = normal;
        s.center = new Vec3((b.minX+b.maxX)/2, y, (b.minZ+b.maxZ)/2);
        s.widthM = b.widthM();
        s.heightM = b.lengthM();
        s.planeDistanceM = y;
        s.confidence = conf;
        return s;
    }

    private SurfaceObservation wallX(double x, RoomBounds b, Vec3 normal, double conf) {
        SurfaceObservation s = new SurfaceObservation();
        s.kind = SurfaceObservation.Kind.WALL;
        s.vertical = true;
        s.normal = normal;
        s.center = new Vec3(x, (b.floorY+b.ceilingY)/2, (b.minZ+b.maxZ)/2);
        s.widthM = b.lengthM();
        s.heightM = b.heightM();
        s.planeDistanceM = x;
        s.confidence = conf;
        return s;
    }

    private SurfaceObservation wallZ(double z, RoomBounds b, Vec3 normal, double conf) {
        SurfaceObservation s = new SurfaceObservation();
        s.kind = SurfaceObservation.Kind.WALL;
        s.vertical = true;
        s.normal = normal;
        s.center = new Vec3((b.minX+b.maxX)/2, (b.floorY+b.ceilingY)/2, z);
        s.widthM = b.widthM();
        s.heightM = b.heightM();
        s.planeDistanceM = z;
        s.confidence = conf;
        return s;
    }

    private enum Axis { X,Y,Z }

    private double peakNear(List<CloudPoint> pts, Axis axis, double lo, double hi,
                            double bin, double fallback) {
        if (!(hi > lo) || bin <= 0) return fallback;
        int n = Math.max(1, (int)Math.ceil((hi-lo)/bin)+1);
        double[] hist = new double[n];
        for (CloudPoint p : pts) {
            if (p == null) continue;
            double v = axis == Axis.X ? p.x : axis == Axis.Y ? p.y : p.z;
            // Raw/Depth pipelines are normally finite, but one corrupt sensor sample must not
            // poison an entire histogram bin with NaN (or overflow the index for infinity).
            // weightedRange() already ignores non-finite coordinates; keep every later refinement
            // stage under the same contract so reconstruction and JSON export remain finite.
            if (!Double.isFinite(v) || v < lo || v > hi) continue;
            int i = (int)Math.floor((v-lo)/bin);
            i = Math.max(0, Math.min(n-1, i));
            // High-confidence Raw Depth observations should define architectural boundaries
            // more strongly than weak/fallback samples. Keep a small floor so sparse but valid
            // geometry is not discarded completely.
            double c=safeConfidence(p.confidence);
            hist[i] += 0.12 + 0.88*c*c;
        }
        int best = 0;
        for (int i=1;i<n;i++) if (hist[i] > hist[best]) best = i;
        if (hist[best] < 5.5) return fallback;

        // Sub-bin centroid of the winning peak and its immediate neighbours reduces the
        // artificial 4–5 cm quantization of the room dimensions.
        double wSum=0, xSum=0;
        for(int j=Math.max(0,best-1);j<=Math.min(n-1,best+1);j++){
            double w=hist[j];
            double center=lo+(j+0.5)*bin;
            wSum+=w; xSum+=center*w;
        }
        return wSum>1e-9?xSum/wSum:lo+(best+0.5)*bin;
    }

    private double boundarySupport(List<CloudPoint> pts, RoomBounds b, double tol) {
        double supported = 0, total = 0;
        for (CloudPoint p : pts) {
            if (p == null || !finite(p.x) || !finite(p.y) || !finite(p.z)) continue;
            double c=safeConfidence(p.confidence);
            double w=0.12+0.88*c*c;
            total+=w;
            double d = Math.min(
                    Math.min(Math.abs(p.x-b.minX), Math.abs(p.x-b.maxX)),
                    Math.min(
                            Math.min(Math.abs(p.y-b.floorY), Math.abs(p.y-b.ceilingY)),
                            Math.min(Math.abs(p.z-b.minZ), Math.abs(p.z-b.maxZ))));
            if (d <= tol) supported+=w;
        }
        return total<=1e-9 ? 0 : supported / total;
    }

    /**
     * Confidence-weighted percentile without allocating one wrapper object per point.
     * Coordinates are metric room-scale values; millimetre quantization is far below the
     * 4–5 cm depth/voxel precision while reducing a 300k-point percentile pass from hundreds
     * of thousands of temporary Java objects to one primitive long[].
     */
    private double[] weightedRange(List<CloudPoint> pts,Axis axis,double qLo,double qHi){
        if(pts==null||pts.isEmpty())return new double[]{0,0};
        final double offset=10000.0,scale=1000.0;
        long[] packed=new long[pts.size()];int n=0;long total=0;
        for(CloudPoint p:pts){
            if(p==null)continue;
            double v=axis==Axis.X?p.x:axis==Axis.Y?p.y:p.z;
            if(!Double.isFinite(v)||v<-10000.0||v>10000.0)continue;
            double c=safeConfidence(p.confidence);
            int w=(int)Math.round((0.04+0.96*c*c)*65535.0);w=Math.max(1,Math.min(65535,w));
            long coord=Math.round((v+offset)*scale); // 1 mm bins, always non-negative
            packed[n++]=(coord<<16)|(w&0xffffL);total+=w;
        }
        if(n==0||total<=0)return new double[]{0,0};
        Arrays.sort(packed,0,n);
        long loTarget=Math.max(1,(long)Math.ceil(clamp(qLo,0,1)*total));
        long hiTarget=Math.max(loTarget,(long)Math.ceil(clamp(qHi,0,1)*total));
        long acc=0,loCoord=packed[0]>>>16,hiCoord=packed[n-1]>>>16;boolean loFound=false;
        for(int i=0;i<n;i++){
            long x=packed[i];acc+=(x&0xffffL);
            if(!loFound&&acc>=loTarget){loCoord=x>>>16;loFound=true;}
            if(acc>=hiTarget){hiCoord=x>>>16;break;}
        }
        return new double[]{loCoord/scale-offset,hiCoord/scale-offset};
    }

    private boolean finite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }

    private double safeConfidence(float v) {
        if (!finite(v)) return 0.0;
        return clamp(v,0,1);
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
