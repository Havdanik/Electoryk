package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.CloudPoint;
import com.elektoryk.scan.model.ScanPathPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * Estimates dominant horizontal architecture axes.
 *
 * v0.5: PCA is used only as a coarse hint. We then search nearby rotations and
 * prefer the angle where X/Z coordinate histograms become sharpest. Long vertical
 * walls create strong histogram peaks when the apartment is axis-aligned, which is
 * more robust for L-shaped / multi-room apartments than raw footprint PCA alone.
 */
public class RoomAxisEstimator {
    public static class Result {
        public double yawRad;
        public final List<CloudPoint> points = new ArrayList<>();
    }

    public Result normalize(List<CloudPoint> input) {
        return normalize(input,Double.NaN,0.0);
    }

    /**
     * Optional ARCore vertical-plane yaw is an independent architectural hint. It may resolve
     * near-ties in noisy Depth clouds, but it never overrides a clearly stronger point-cloud fit.
     */
    public Result normalize(List<CloudPoint> input,double wallYawHintRad,double wallYawConfidence) {
        Result out = new Result();
        if (input == null || input.size() < 50) {
            if (input != null) out.points.addAll(input);
            return out;
        }

        double coarse=pcaAngle(input);
        double best=refineArchitectureAngle(input,coarse,wallYawHintRad,wallYawConfidence);
        out.yawRad=best;

        double c=Math.cos(best), s=Math.sin(best);
        for (CloudPoint p: input) {
            float x=(float)( c*p.x + s*p.z);
            float z=(float)(-s*p.x + c*p.z);
            out.points.add(new CloudPoint(x,p.y,z,p.confidence));
        }
        return out;
    }

    public List<ScanPathPoint> normalizePath(List<ScanPathPoint> path,double yawRad) {
        List<ScanPathPoint> out=new ArrayList<>();
        if(path==null)return out;
        double c=Math.cos(yawRad),s=Math.sin(yawRad);
        for(ScanPathPoint p:path){
            double x=c*p.x+s*p.z;
            double z=-s*p.x+c*p.z;
            out.add(new ScanPathPoint(x,z,p.timestampNs));
        }
        return out;
    }

    private double pcaAngle(List<CloudPoint> input){
        double mx=0,mz=0,totalW=0;int n=0;
        for(CloudPoint p:input){
            if(!finite(p.x)||!finite(p.z))continue;
            double w=confidenceWeight(p);
            mx+=p.x*w;mz+=p.z*w;totalW+=w;n++;
        }
        if(n<50||totalW<=1e-9)return 0;
        mx/=totalW;mz/=totalW;
        double xx=0,zz=0,xz=0;
        for(CloudPoint p:input){
            if(!finite(p.x)||!finite(p.z))continue;
            double w=confidenceWeight(p);
            double x=p.x-mx,z=p.z-mz;
            xx+=x*x*w;zz+=z*z*w;xz+=x*z*w;
        }
        double theta=0.5*Math.atan2(2.0*xz,xx-zz);
        return normalizeQuarterTurn(theta);
    }

    private double refineArchitectureAngle(List<CloudPoint> points,double coarse,double planeHint,double planeConfidence){
        PrimitiveHistogram hx=new PrimitiveHistogram(512),hz=new PrimitiveHistogram(512);
        double best=coarse;
        double bestScore=alignmentScore(points,coarse,hx,hz);

        // Coarse global search: footprint PCA can be misleading for L-shaped apartments.
        for(double deg=-45.0;deg<=45.0001;deg+=1.0){
            double a=Math.toRadians(deg);
            double score=alignmentScore(points,a,hx,hz);
            // Tiny prior only breaks near-ties; architectural wall concentration dominates.
            double delta=Math.abs(normalizeQuarterTurn(a-coarse));
            score-=delta*0.002;
            if(score>bestScore){bestScore=score;best=a;}
        }
        // Independent ARCore vertical planes are useful when the depth histogram has a near-tie.
        // Search around their dominant yaw, then accept it only if its point-cloud score remains
        // close to the global optimum. Strong point-cloud evidence always wins.
        double pcBestScore=bestScore;
        if(Double.isFinite(planeHint) && planeConfidence>=0.22){
            double h=normalizeQuarterTurn(planeHint);
            double hintBest=h,hintScore=Double.NEGATIVE_INFINITY;
            for(double deg=-3.0;deg<=3.0001;deg+=0.25){
                double a=normalizeQuarterTurn(h+Math.toRadians(deg));
                double score=alignmentScore(points,a,hx,hz);
                if(score>hintScore){hintScore=score;hintBest=a;}
            }
            double allowance=0.008+0.045*Math.min(1.0,planeConfidence);
            if(hintScore>=pcBestScore*(1.0-allowance)){
                best=hintBest;bestScore=hintScore;
            }
        }

        // Fine sub-degree refinement improves long-wall dimensions without expensive full RANSAC.
        double coarseBest=best;
        for(double deg=-1.0;deg<=1.0001;deg+=0.10){
            double a=normalizeQuarterTurn(coarseBest+Math.toRadians(deg));
            double score=alignmentScore(points,a,hx,hz);
            double delta=Math.abs(normalizeQuarterTurn(a-coarse));
            score-=delta*0.002;
            if(score>bestScore){bestScore=score;best=a;}
        }
        return normalizeQuarterTurn(best);
    }

    private double alignmentScore(List<CloudPoint> points,double angle,PrimitiveHistogram hx,PrimitiveHistogram hz){
        final double bin=0.08;
        final int maxSamples=14000;
        int stride=Math.max(1,points.size()/maxSamples);
        double c=Math.cos(angle),s=Math.sin(angle);

        hx.clear();hz.clear();
        int n=0; double totalW=0;
        for(int i=0;i<points.size();i+=stride){
            CloudPoint p=points.get(i);
            if(p==null||!finite(p.x)||!finite(p.z))continue;
            double x=c*p.x+s*p.z;
            double z=-s*p.x+c*p.z;
            int ix=(int)Math.floor(x/bin);
            int iz=(int)Math.floor(z/bin);
            double w=confidenceWeight(p);
            hx.add(ix,w);hz.add(iz,w);
            totalW+=w;n++;
        }
        if(n<50||totalW<=1e-9)return 0;
        double score=hx.sharpness()+hz.sharpness();
        return score/(totalW*totalW);
    }

    /** Tiny primitive histogram avoids millions of Integer/Double allocations during the
     *  90+ candidate-angle search.  The number of occupied 8 cm bins in room-scale scans is
     *  small, so open addressing is both predictable and faster than boxed HashMap traffic. */
    private static final class PrimitiveHistogram{
        private int[] keys;private double[] values;private byte[] used;private int size,mask;
        PrimitiveHistogram(int requested){int cap=1;while(cap<Math.max(16,requested))cap<<=1;keys=new int[cap];values=new double[cap];used=new byte[cap];mask=cap-1;}
        void clear(){Arrays.fill(used,(byte)0);size=0;}
        void add(int key,double value){if((size+1)*10>=keys.length*7)grow();put(key,value);}
        private void put(int key,double value){int slot=mix(key)&mask;while(used[slot]!=0){if(keys[slot]==key){values[slot]+=value;return;}slot=(slot+1)&mask;}used[slot]=1;keys[slot]=key;values[slot]=value;size++;}
        private void grow(){int[] oldK=keys;double[] oldV=values;byte[] oldU=used;keys=new int[oldK.length<<1];values=new double[keys.length];used=new byte[keys.length];mask=keys.length-1;size=0;for(int i=0;i<oldK.length;i++)if(oldU[i]!=0)put(oldK[i],oldV[i]);}
        double sharpness(){
            double[] top=new double[18];int count=0;
            for(int i=0;i<values.length;i++)if(used[i]!=0&&values[i]>=2.2){double v=values[i];if(count>=18&&v<=top[17])continue;int pos=Math.min(count,17);if(count<18)count++;while(pos>0&&top[pos-1]<v){top[pos]=top[pos-1];pos--;}top[pos]=v;}
            double sum=0;for(int i=0;i<count;i++)sum+=top[i]*top[i];return sum;
        }
        private static int mix(int x){x^=x>>>16;x*=0x7feb352d;x^=x>>>15;x*=0x846ca68b;x^=x>>>16;return x;}
    }

    private double normalizeQuarterTurn(double theta){
        while(theta>Math.PI/4)theta-=Math.PI/2;
        while(theta<-Math.PI/4)theta+=Math.PI/2;
        return theta;
    }


    private double confidenceWeight(CloudPoint p){
        double c=p==null?0:Math.max(0,Math.min(1,p.confidence));
        return 0.12+0.88*c*c;
    }
    private boolean finite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }
}
