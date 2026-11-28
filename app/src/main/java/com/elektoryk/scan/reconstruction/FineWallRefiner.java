package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.CloudPoint;
import com.elektoryk.scan.model.PlanWallSegment;
import com.elektoryk.scan.model.RoomBounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Refines coarse wall-grid coordinates against the metric point cloud.
 * Topology still comes from the occupancy grid; the metric cloud then refines both
 * the wall plane coordinate and locally observed endpoints, removing 10 cm grid quantization.
 */
public class FineWallRefiner {

    public void refine(ApartmentPlan plan,List<CloudPoint> points,RoomBounds bounds){
        if(plan==null||points==null||points.isEmpty()||bounds==null)return;
        double yMin=bounds.floorY+0.18;
        double yMax=bounds.ceilingY-0.15;

        for(PlanWallSegment w:plan.walls){
            boolean horizontal=Math.abs(w.x2-w.x1)>=Math.abs(w.z2-w.z1);
            double alongMin=horizontal?Math.min(w.x1,w.x2):Math.min(w.z1,w.z2);
            double alongMax=horizontal?Math.max(w.x1,w.x2):Math.max(w.z1,w.z2);
            double nominal=horizontal?w.z1:w.x1;

            List<Value> support=new ArrayList<>();
            for(CloudPoint p:points){
                if(p.y<yMin||p.y>yMax)continue;
                double along=horizontal?p.x:p.z;
                double perp=horizontal?p.z:p.x;
                if(along<alongMin-0.18||along>alongMax+0.18)continue;
                double d=Math.abs(perp-nominal);
                if(d>0.22)continue;
                double weight=Math.max(0.05,p.confidence)*(1.0-Math.min(0.85,d/0.26));
                support.add(new Value(perp,weight));
            }
            if(support.size()<18)continue;

            double refined=weightedMedian(support);
            List<Double> residuals=new ArrayList<>();
            for(Value v:support)residuals.add(Math.abs(v.value-refined));
            Collections.sort(residuals);
            double mad=residuals.get(residuals.size()/2);

            // Prevent a bad cluster from pulling the wall far from the topological solution.
            double shift=refined-nominal;
            if(Math.abs(shift)>0.16)continue;

            if(horizontal){w.z1=w.z2=refined;}
            else{w.x1=w.x2=refined;}

            // The 10 cm occupancy grid is only topology. Refine wall endpoints from the
            // original metric cloud as well, otherwise wall lengths remain grid-quantized.
            List<Value> alongSupport=new ArrayList<>();
            for(CloudPoint p:points){
                if(p.y<yMin||p.y>yMax)continue;
                double along=horizontal?p.x:p.z;
                double perp=horizontal?p.z:p.x;
                if(along<alongMin-0.22||along>alongMax+0.22)continue;
                double d=Math.abs(perp-refined);
                if(d>0.10)continue;
                double c=Math.max(0.03,Math.min(1.0,p.confidence));
                double weight=c*c*(1.0-Math.min(0.90,d/0.11));
                alongSupport.add(new Value(along,Math.max(0.01,weight)));
            }
            if(alongSupport.size()>=28){
                double candidateLo=weightedPercentile(alongSupport,0.005);
                double candidateHi=weightedPercentile(alongSupport,0.995);
                // Never shrink a poorly observed wall dramatically. Endpoint evidence is used
                // only to remove the local grid quantization near the topological endpoint.
                double newLo=(candidateLo>=alongMin-0.18&&candidateLo<=alongMin+0.18)?candidateLo:alongMin;
                double newHi=(candidateHi>=alongMax-0.18&&candidateHi<=alongMax+0.18)?candidateHi:alongMax;
                if(newHi-newLo>=0.35){
                    if(horizontal){w.x1=newLo;w.x2=newHi;}
                    else{w.z1=newLo;w.z2=newHi;}
                }
            }

            double supportScore=Math.min(1.0,support.size()/160.0);
            double residualScore=Math.max(0.0,1.0-mad/0.08);
            w.confidence=Math.max(w.confidence,
                    Math.min(0.98,0.48+0.28*supportScore+0.24*residualScore));
        }
    }


    private double weightedPercentile(List<Value> src,double q){
        List<Value> v=new ArrayList<>(src);
        Collections.sort(v,new Comparator<Value>(){
            @Override public int compare(Value a,Value b){return Double.compare(a.value,b.value);}
        });
        double total=0;for(Value x:v)total+=Math.max(0,x.weight);
        if(v.isEmpty()||total<=1e-12)return 0;
        double target=Math.max(0,Math.min(1,q))*total,acc=0;
        for(Value x:v){acc+=Math.max(0,x.weight);if(acc>=target)return x.value;}
        return v.get(v.size()-1).value;
    }
    private double weightedMedian(List<Value> src){
        List<Value> v=new ArrayList<>(src);
        Collections.sort(v,new Comparator<Value>(){
            @Override public int compare(Value a,Value b){return Double.compare(a.value,b.value);}
        });
        double total=0;for(Value x:v)total+=x.weight;
        double acc=0;
        for(Value x:v){acc+=x.weight;if(acc>=total/2.0)return x.value;}
        return v.get(v.size()-1).value;
    }

    private static class Value{
        final double value,weight;
        Value(double value,double weight){this.value=value;this.weight=weight;}
    }
}
