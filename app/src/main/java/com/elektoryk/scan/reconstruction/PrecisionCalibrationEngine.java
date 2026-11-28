package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.MeasurementAnchor;
import com.elektoryk.scan.model.PlanWallSegment;
import com.elektoryk.scan.model.PrecisionReport;
import com.elektoryk.scan.model.RoomZone;
import com.elektoryk.scan.model.SceneSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PrecisionCalibrationEngine {

    public PrecisionReport analyze(SceneSnapshot scene){
        PrecisionReport out=new PrecisionReport();
        if(scene==null){
            out.warnings.add("Немає Smart Scan сцени.");
            return out;
        }

        List<MeasurementAnchor> valid=new ArrayList<>();
        for(MeasurementAnchor a:scene.measurementAnchors){
            if(a!=null&&a.enabled&&a.scannedM>0.05&&a.measuredM>0.05)valid.add(a);
        }

        double scan=clamp(scene.directionCoveragePercent/100.0,0,1);
        if(scene.roomBounds!=null)scan=(scan+clamp(scene.roomBounds.confidence,0,1))/2.0;
        out.scanConfidence=scan;

        double geometry=geometryConfidence(scene.apartmentPlan);
        out.geometryConfidence=geometry;

        if(valid.isEmpty()){
            out.suggestedScale=1.0;
            out.calibrationConfidence=0;
            out.estimatedPlanErrorCm=Math.max(3.0,(1.0-(scan+geometry)/2.0)*14.0);
            out.warnings.add("Немає контрольних замірів. Додай хоча б 1 довгу стіну лазером.");
            finishScore(out);
            return out;
        }

        List<Double> factors=new ArrayList<>();
        for(MeasurementAnchor a:valid)factors.add(a.factor());
        double median=median(factors);

        List<Double> deviations=new ArrayList<>();
        for(double f:factors)deviations.add(Math.abs(f-median));
        double mad=median(deviations);
        double tolerance=Math.max(0.004,mad*3.5);

        List<MeasurementAnchor> accepted=new ArrayList<>();
        for(MeasurementAnchor a:valid){
            if(Math.abs(a.factor()-median)<=tolerance)accepted.add(a);
            else out.anchorsRejected++;
        }
        if(accepted.isEmpty())accepted.addAll(valid);

        // Weighted least-squares scale: minimize Σw(measured - scale*scanned)^2.
        double num=0,den=0;
        for(MeasurementAnchor a:accepted){
            double w=Math.max(0.1,a.weight)*sourceWeight(a.source);
            num+=w*a.scannedM*a.measuredM;
            den+=w*a.scannedM*a.scannedM;
        }
        double scale=den>1e-9?num/den:median;
        out.suggestedScale=scale;
        out.anchorsUsed=accepted.size();

        List<Double> xFactors=new ArrayList<>(),zFactors=new ArrayList<>();
        for(MeasurementAnchor a:accepted){
            if(a.axis==MeasurementAnchor.Axis.X)xFactors.add(a.factor());
            if(a.axis==MeasurementAnchor.Axis.Z)zFactors.add(a.factor());
        }
        if(!xFactors.isEmpty())out.xScale=median(xFactors);
        if(!zFactors.isEmpty())out.zScale=median(zFactors);
        if(!xFactors.isEmpty()&&!zFactors.isEmpty()){
            out.axisMismatchPercent=Math.abs(out.xScale-out.zScale)/Math.max(1e-9,(out.xScale+out.zScale)/2.0)*100.0;
            if(out.axisMismatchPercent>0.8)
                out.warnings.add("Масштаб X/Z відрізняється на "+String.format(java.util.Locale.US,"%.2f%%",out.axisMismatchPercent)
                        +" — можливий локальний drift; не виправляю нерівномірно автоматично.");
        }

        double sum2=0,max=0,totalSpan=0;
        for(MeasurementAnchor a:accepted){
            double residual=(a.measuredM-a.scannedM*scale)*100.0;
            sum2+=residual*residual;
            max=Math.max(max,Math.abs(residual));
            totalSpan+=a.measuredM;
        }
        out.rmsResidualCm=Math.sqrt(sum2/Math.max(1,accepted.size()));
        out.maxResidualCm=max;

        double anchorCount=Math.min(1.0,accepted.size()/4.0);
        double spanScore=Math.min(1.0,totalSpan/12.0);
        double residualScore=clamp(1.0-out.rmsResidualCm/5.0,0,1);
        out.calibrationConfidence=0.35*anchorCount+0.25*spanScore+0.40*residualScore;

        double baseError=Math.max(out.rmsResidualCm,0.8);
        double confidence=(scan+geometry+out.calibrationConfidence)/3.0;
        out.estimatedPlanErrorCm=baseError+(1.0-confidence)*5.0;
        out.scaleCorrectionRecommended=Math.abs(scale-1.0)>=0.0025;

        if(out.anchorsRejected>0)out.warnings.add("Відхилено контрольних замірів як викиди: "+out.anchorsRejected+".");
        if(accepted.size()==1)out.warnings.add("Один контрольний замір коригує масштаб, але не перевіряє локальний drift.");
        if(totalSpan<6.0)out.warnings.add("Контрольні відстані короткі. Для масштабу краще мати хоча б одну довгу стіну.");
        if(out.rmsResidualCm>2.5)out.warnings.add("Контрольні заміри між собою не дуже сходяться — перевір точки виміру або перескануй проблемну зону.");
        if(scene.directionCoveragePercent<60)out.warnings.add("Мало різних ракурсів під час скану.");
        if(scene.apartmentPlan!=null&&scene.apartmentPlan.missingZoneHints.size()>0)out.warnings.add("Є підозра на пропущену зону — точність загального плану обмежена.");

        finishScore(out);
        return out;
    }

    private void finishScore(PrecisionReport r){
        double c=0.32*r.scanConfidence+0.30*r.geometryConfidence+0.38*r.calibrationConfidence;
        if(r.anchorsUsed==0)c*=0.72;
        if(r.rmsResidualCm>4)c*=0.75;
        r.score=(int)Math.round(clamp(c,0,1)*100.0);
        if(r.score>=88&&r.estimatedPlanErrorCm<=2.0)r.grade=PrecisionReport.Grade.HIGH;
        else if(r.score>=72&&r.estimatedPlanErrorCm<=4.0)r.grade=PrecisionReport.Grade.GOOD;
        else if(r.score>=50)r.grade=PrecisionReport.Grade.CHECK;
        else r.grade=PrecisionReport.Grade.LOW;
    }

    private double geometryConfidence(ApartmentPlan p){
        if(p==null||p.rooms.isEmpty())return 0.2;
        double wall=0;int wn=0;
        for(PlanWallSegment w:p.walls){wall+=clamp(w.confidence,0,1);wn++;}
        double room=0;for(RoomZone r:p.rooms)room+=clamp(r.confidence,0,1);
        double wc=wn==0?0.35:wall/wn;
        double rc=room/p.rooms.size();
        // Manual edit is not automatically "more accurate". It only becomes evidence
        // when supported by a trusted measurement anchor.
        return clamp(0.42*clamp(p.confidence,0,1)+0.30*wc+0.28*rc,0,1);
    }

    private double sourceWeight(MeasurementAnchor.Source s){
        switch(s){
            case LASER:return 1.25;
            case TAPE:return 1.0;
            case DOCUMENT:return 0.75;
            case KNOWN_OBJECT:return 0.55;
            default:return 0.65;
        }
    }

    private double median(List<Double> v){
        if(v.isEmpty())return 1.0;
        List<Double> x=new ArrayList<>(v);Collections.sort(x);
        int n=x.size();return n%2==1?x.get(n/2):(x.get(n/2-1)+x.get(n/2))/2.0;
    }
    private double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
}
