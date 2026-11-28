package com.elektoryk.scan.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.MeasurementAnchor;
import com.elektoryk.scan.model.PlanWallSegment;
import com.elektoryk.scan.model.PrecisionReport;
import com.elektoryk.scan.model.SceneSnapshot;
import com.elektoryk.scan.reconstruction.GeometryScaleApplier;
import com.elektoryk.scan.reconstruction.PrecisionCalibrationEngine;
import com.elektoryk.scan.storage.FloorPlanRepository;
import com.elektoryk.scan.storage.SceneRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AccuracyActivity extends Activity {
    private String projectId;
    private SceneRepository sceneRepo;
    private FloorPlanRepository floorRepo;
    private SceneSnapshot scene;
    private FloorPlan floor;
    private ApartmentPlan plan;

    private ChoiceStrip reference;
    private ChoiceStrip source;
    private NumberStepper measured;
    private TextView scannedValue;
    private LinearLayout anchorList;
    private LinearLayout reportBox;
    private List<Ref> refs=new ArrayList<>();

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        projectId=getIntent().getStringExtra("projectId");
        sceneRepo=new SceneRepository(this);
        floorRepo=new FloorPlanRepository(this);
        scene=sceneRepo.loadScene(projectId);
        floor=floorRepo.load(projectId);
        plan=scene==null?null:scene.apartmentPlan;
        buildUi();
    }

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(11,11,13));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));scroll.addView(root);

        root.addView(Ui.brandHeader(this,"ELEKTORYK • Точність","Перевірка масштабу реальними замірами.","ТОЧНІСТЬ",true));
        root.addView(Ui.screenIntro(this,"КОНТРОЛЬ","Контрольні заміри","Додай 2–4 реальні відстані лазером або рулеткою. Програма перевірить масштаб і покаже очікувану похибку без фальшивої «міліметрової точності»."),Ui.lpMatchWrap(this,10));

        if(scene==null||plan==null){
            root.addView(Ui.muted(this,"Потрібен Smart Scan / Multi-room план."));
            setContentView(scroll);return;
        }

        buildRefs();

        root.addView(Ui.title(this,"1. Вибери контрольний розмір",18),Ui.lpMatchWrap(this,14));
        reference=new ChoiceStrip(this);
        String[] keys=new String[refs.size()],labels=new String[refs.size()];
        for(int i=0;i<refs.size();i++){keys[i]=String.valueOf(i);labels[i]=refs.get(i).label;}
        reference.setItems(keys,labels,refs.isEmpty()?"":"0");
        reference.setListener(k->refreshSelectedReference());
        root.addView(reference);

        scannedValue=Ui.muted(this,"");
        root.addView(scannedValue,Ui.lpMatchWrap(this,6));

        root.addView(Ui.title(this,"2. Чим виміряно",18),Ui.lpMatchWrap(this,12));
        source=new ChoiceStrip(this);
        source.setItems(
                new String[]{"LASER","TAPE","DOCUMENT","KNOWN_OBJECT"},
                new String[]{"Лазер","Рулетка","Документ","Відомий розмір"},
                "LASER");
        root.addView(source);

        root.addView(Ui.title(this,"3. Реальний розмір",18),Ui.lpMatchWrap(this,12));
        measured=new NumberStepper(this,"м");
        measured.configure(0.10,50.0,0.01,2);
        measured.setValue(refs.isEmpty()?1.0:refs.get(0).scannedM);
        root.addView(measured);

        Button add=Ui.button(this,"+ Додати контрольний замір");
        add.setOnClickListener(v->addAnchor());
        root.addView(add,Ui.lpMatchWrap(this,12));

        anchorList=new LinearLayout(this);anchorList.setOrientation(LinearLayout.VERTICAL);
        root.addView(anchorList,Ui.lpMatchWrap(this,12));

        reportBox=Ui.card(this);
        root.addView(reportBox,Ui.lpMatchWrap(this,14));

        Button apply=Ui.button(this,"Застосувати рекомендовану корекцію масштабу");
        apply.setOnClickListener(v->confirmApply());
        root.addView(apply,Ui.lpMatchWrap(this,12));

        Button clear=Ui.button(this,"Очистити контрольні заміри");
        clear.setOnClickListener(v->{
            scene.measurementAnchors.clear();scene.precisionReport=null;save();render();
        });
        root.addView(clear,Ui.lpMatchWrap(this,8));

        refreshSelectedReference();
        render();
        root.addView(Ui.bottomNav(this,projectId,"PLAN"),Ui.lpMatchWrap(this,18));
        setContentView(scroll);
    }

    private void buildRefs(){
        refs.clear();
        if(plan==null)return;

        List<PlanWallSegment> walls=new ArrayList<>(plan.walls);
        Collections.sort(walls,new Comparator<PlanWallSegment>(){
            @Override public int compare(PlanWallSegment a,PlanWallSegment b){
                return Double.compare(b.lengthM(),a.lengthM());
            }
        });
        int max=Math.min(12,walls.size());
        for(int i=0;i<max;i++){
            PlanWallSegment w=walls.get(i);
            MeasurementAnchor.Axis axis=Math.abs(w.x2-w.x1)>=Math.abs(w.z2-w.z1)
                    ?MeasurementAnchor.Axis.X:MeasurementAnchor.Axis.Z;
            refs.add(new Ref("W"+(i+1)+" • "+String.format(Locale.US,"%.2f м",w.lengthM()),
                    w.lengthM(),MeasurementAnchor.Kind.WALL_LENGTH,axis,w.id));
        }

        if(plan.ceilingY-plan.floorY>1.5){
            refs.add(new Ref("Висота стелі • "+String.format(Locale.US,"%.2f м",plan.ceilingY-plan.floorY),
                    plan.ceilingY-plan.floorY,MeasurementAnchor.Kind.CEILING_HEIGHT,
                    MeasurementAnchor.Axis.Y,"CEILING"));
        }

        if(refs.isEmpty()){
            refs.add(new Ref("Власний контрольний розмір",1.0,
                    MeasurementAnchor.Kind.CUSTOM,MeasurementAnchor.Axis.OTHER,"CUSTOM"));
        }
    }

    private void refreshSelectedReference(){
        Ref r=currentRef();
        if(r==null)return;
        scannedValue.setText("Скан показує: "+String.format(Locale.US,"%.3f м",r.scannedM)
                +" • тип "+r.kind.name());
        measured.setValue(r.scannedM);
    }

    private Ref currentRef(){
        if(refs.isEmpty())return null;
        int idx=0;
        try{idx=Integer.parseInt(reference==null?"0":reference.getSelectedKey());}catch(Exception ignored){}
        idx=Math.max(0,Math.min(refs.size()-1,idx));
        return refs.get(idx);
    }

    private void addAnchor(){
        Ref r=currentRef();if(r==null)return;
        double actual=measured.getValue();
        if(actual<=0.05)return;

        MeasurementAnchor a=new MeasurementAnchor();
        a.kind=r.kind;a.axis=r.axis;a.referenceId=r.referenceId;a.label=r.label;
        a.scannedM=r.scannedM;a.measuredM=actual;
        try{a.source=MeasurementAnchor.Source.valueOf(source.getSelectedKey());}catch(Exception ignored){}
        a.weight=r.kind==MeasurementAnchor.Kind.WALL_LENGTH?1.15:1.0;
        scene.measurementAnchors.add(a);
        scene.precisionReport=new PrecisionCalibrationEngine().analyze(scene);
        save();render();
    }

    private void render(){
        if(anchorList==null)return;
        anchorList.removeAllViews();
        if(scene.measurementAnchors.isEmpty()){
            anchorList.addView(Ui.muted(this,"Контрольних замірів ще немає."));
        }else{
            anchorList.addView(Ui.title(this,"Контрольні заміри",18));
            int n=1;
            for(MeasurementAnchor a:scene.measurementAnchors){
                LinearLayout card=Ui.card(this);
                card.addView(Ui.title(this,n+". "+a.label,16));
                card.addView(Ui.muted(this,String.format(Locale.US,
                        "скан %.3f м → реально %.3f м • %+,.1f см • ×%.6f",
                        a.scannedM,a.measuredM,a.errorBeforeCm(),a.factor()).replace(",", ".")));
                final MeasurementAnchor target=a;
                Button del=Ui.button(this,"Видалити");
                del.setOnClickListener(v->{
                    scene.measurementAnchors.remove(target);
                    scene.precisionReport=new PrecisionCalibrationEngine().analyze(scene);
                    save();render();
                });
                card.addView(del,Ui.lpMatchWrap(this,6));
                anchorList.addView(card,Ui.lpMatchWrap(this,6));n++;
            }
        }

        PrecisionReport r=new PrecisionCalibrationEngine().analyze(scene);
        scene.precisionReport=r;save();
        reportBox.removeAllViews();
        reportBox.addView(Ui.title(this,gradeLabel(r.grade)+" • "+r.score+"/100",21));
        reportBox.addView(Ui.muted(this,String.format(Locale.US,
                "Очікувана похибка плану ≈ %.1f см • RMS контрольних замірів %.1f см • max %.1f см",
                r.estimatedPlanErrorCm,r.rmsResidualCm,r.maxResidualCm)));
        reportBox.addView(Ui.muted(this,String.format(Locale.US,
                "Масштаб: ×%.6f • X ×%.6f • Z ×%.6f • X/Z Δ %.2f%% • використано %d • відхилено %d",
                r.suggestedScale,r.xScale,r.zScale,r.axisMismatchPercent,r.anchorsUsed,r.anchorsRejected)),Ui.lpMatchWrap(this,5));
        reportBox.addView(Ui.muted(this,String.format(Locale.US,
                "Scan %.0f%% • Geometry %.0f%% • Calibration %.0f%%",
                r.scanConfidence*100,r.geometryConfidence*100,r.calibrationConfidence*100)),Ui.lpMatchWrap(this,5));
        for(String w:r.warnings)reportBox.addView(Ui.muted(this,"• "+w),Ui.lpMatchWrap(this,5));
    }

    private void confirmApply(){
        PrecisionReport r=new PrecisionCalibrationEngine().analyze(scene);
        if(r.anchorsUsed==0){
            new AlertDialog.Builder(this).setTitle("Немає контрольних замірів")
                    .setMessage("Спочатку додай хоча б один реальний замір.").setPositiveButton("OK",null).show();
            return;
        }
        if(Math.abs(r.suggestedScale-1.0)<0.0005){
            new AlertDialog.Builder(this).setTitle("Корекція майже не потрібна")
                    .setMessage("Рекомендований масштаб уже близький до 1.000.").setPositiveButton("OK",null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Застосувати масштаб ×"+String.format(Locale.US,"%.6f",r.suggestedScale)+"?")
                .setMessage("Буде метрично скориговано робочий план, стіни, отвори, електроточки й маршрути. RAW point cloud залишиться оригінальним, але в перегляді масштабуватиметься тим самим коефіцієнтом.")
                .setNegativeButton("Ні",null)
                .setPositiveButton("Застосувати",(d,w)->{
                    GeometryScaleApplier.apply(scene,floor,r.suggestedScale);
                    scene.precisionReport=new PrecisionCalibrationEngine().analyze(scene);
                    sceneRepo.save(projectId,scene,null);floorRepo.save(floor);
                    plan=scene.apartmentPlan;
                    buildRefs();
                    if(!refs.isEmpty()){
                        String[] keys=new String[refs.size()],labels=new String[refs.size()];
                        for(int i=0;i<refs.size();i++){keys[i]=String.valueOf(i);labels[i]=refs.get(i).label;}
                        reference.setItems(keys,labels,"0");
                    }
                    refreshSelectedReference();render();
                }).show();
    }

    private void save(){
        sceneRepo.save(projectId,scene,null);
        floorRepo.save(floor);
    }

    private String gradeLabel(PrecisionReport.Grade g){
        switch(g){
            case HIGH:return "Висока впевненість";
            case GOOD:return "Добра";
            case CHECK:return "Треба перевірити";
            default:return "Низька";
        }
    }

    private static class Ref{
        String label,referenceId;double scannedM;MeasurementAnchor.Kind kind;MeasurementAnchor.Axis axis;
        Ref(String l,double s,MeasurementAnchor.Kind k,MeasurementAnchor.Axis a,String id){
            label=l;scannedM=s;kind=k;axis=a;referenceId=id;
        }
    }
}
