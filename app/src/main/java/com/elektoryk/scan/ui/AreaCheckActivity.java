package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.elektoryk.scan.model.AreaCheckResult;
import com.elektoryk.scan.model.ElectricalProject;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.MissingZoneHint;
import com.elektoryk.scan.model.RoomZone;
import com.elektoryk.scan.model.ScanGap;
import com.elektoryk.scan.model.SceneSnapshot;
import com.elektoryk.scan.reconstruction.AreaReconciliationEngine;
import com.elektoryk.scan.storage.FileProjectRepository;
import com.elektoryk.scan.storage.FloorPlanRepository;
import com.elektoryk.scan.storage.SceneRepository;

import java.util.Locale;

public class AreaCheckActivity extends Activity {
    private String projectId;
    private ElectricalProject project;
    private SceneSnapshot scene;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        projectId=getIntent().getStringExtra("projectId");
        project=new FileProjectRepository(this).get(projectId);
        scene=new SceneRepository(this).loadScene(projectId);
        buildUi();
    }

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(11,11,13));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,30));scroll.addView(root);
        root.addView(Ui.brandHeader(this,"ELEKTORYK • Контроль площі","Чи зійшовся скан з реальною площею об'єкта.","КОНТРОЛЬ",true));
        root.addView(Ui.screenIntro(this,"ПЛОЩА","Де втрачено метраж","Порівнюємо контрольну площу з Multi-room планом і показуємо, де скан міг пропустити кімнату або частину зони."),Ui.lpMatchWrap(this,10));

        if(project==null){root.addView(Ui.muted(this,"Об'єкт не знайдено."));setContentView(scroll);return;}
        FloorPlan plan=new FloorPlanRepository(this).load(projectId);
        AreaCheckResult r=new AreaReconciliationEngine().check(project,scene,plan);

        LinearLayout card=Ui.card(this);
        TextView status=Ui.title(this,statusLabel(r.status),21);
        status.setTextColor(statusColor(r.status)); card.addView(status);
        card.addView(Ui.muted(this,String.format(Locale.US,
                "Контроль: %.1f м² • скан/план: %.1f м² • різниця: %+.1f м² (%+.1f%%)",
                r.referenceAreaM2,r.scannedAreaM2,r.deltaM2,r.deltaPercent)));
        card.addView(Ui.muted(this,"Допуск: «"+project.profile.areaReference.label+" / "+project.profile.areaAccuracy.label+"»: ±"
                +String.format(Locale.US,"%.0f",r.tolerancePercent)+"%"),Ui.lpMatchWrap(this,5));
        card.addView(Ui.title(this,r.message,16),Ui.lpMatchWrap(this,7));
        if(r.likelyMissingAreaM2>0.2){
            card.addView(Ui.title(this,String.format(Locale.US,"Ймовірно не вистачає ≈ %.1f м²",r.likelyMissingAreaM2),18),Ui.lpMatchWrap(this,7));
        }
        root.addView(card,Ui.lpMatchWrap(this,12));

        if(scene!=null && scene.apartmentPlan!=null && !scene.apartmentPlan.rooms.isEmpty()){
            root.addView(Ui.title(this,"Multi-room розклад",19),Ui.lpMatchWrap(this,16));
            double sum=0;
            int i=1;
            for(RoomZone room:scene.apartmentPlan.rooms){
                sum+=room.areaM2;
                root.addView(Ui.muted(this,String.format(Locale.US,
                        "%d. %s • %.1f м²%s",
                        i++,room.displayName(),room.areaM2,
                        room.narrowPassageLike?" • вузька зона/коридор":"")),Ui.lpMatchWrap(this,5));
            }
            root.addView(Ui.muted(this,String.format(Locale.US,
                    "Сума сегментованих зон: %.1f м² • фактично побачена підлога: %.1f м²",
                    sum,scene.apartmentPlan.totalObservedAreaM2)),Ui.lpMatchWrap(this,7));

            if(!scene.apartmentPlan.missingZoneHints.isEmpty()){
                root.addView(Ui.title(this,"Де може бути пропущена кімната",19),Ui.lpMatchWrap(this,16));
                for(MissingZoneHint h:scene.apartmentPlan.missingZoneHints){
                    String area=h.estimatedAreaMaxM2>0
                            ?String.format(Locale.US," • %.1f–%.1f м²",h.estimatedAreaMinM2,h.estimatedAreaMaxM2):"";
                    root.addView(Ui.muted(this,String.format(Locale.US,
                            "● X %.1f / Z %.1f%s • %s",h.x,h.z,area,h.reason)),Ui.lpMatchWrap(this,6));
                }
            }
        }

        if(scene!=null && scene.roomBounds!=null){
            root.addView(Ui.title(this,"Де слабке покриття",19),Ui.lpMatchWrap(this,16));
            AreaCoverageView map=new AreaCoverageView(this); map.setData(scene.roomBounds,scene.scanGaps,scene.scanPath);
            root.addView(map,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,260)));
            root.addView(Ui.muted(this,String.format(Locale.US,
                    "Загальне заповнення підлоги за об’єднаною хмарою: %.0f%% • depth points: %d • слабких зон: %d",
                    Math.max(0,Math.min(1,scene.floorObservationRatio))*100.0,scene.voxelPointCount,scene.scanGaps.size())),Ui.lpMatchWrap(this,7));
            if(scene.scanGaps.isEmpty()){
                root.addView(Ui.muted(this,"Всередині вже відновленого контуру великих дірок не видно. Якщо площі не вистачає, дивись Multi-room підказки біля незавершених дверних проходів."),Ui.lpMatchWrap(this,8));
            }else{
                int i=1;
                for(ScanGap g:scene.scanGaps){
                    root.addView(Ui.muted(this,String.format(Locale.US,
                            "%d. ≈ %.1f м² • центр X %.1f / Z %.1f • від траєкторії %.1f м • %s",
                            i++,g.estimatedAreaM2,g.centerX(),g.centerZ(),g.nearestPathDistanceM,g.hint)),Ui.lpMatchWrap(this,6));
                }
            }
        } else {
            root.addView(Ui.muted(this,"Ще немає Smart Scan для карти покриття."),Ui.lpMatchWrap(this,14));
        }

        Button apartment=Ui.button(this,"Відкрити єдиний план квартири");
        apartment.setOnClickListener(v->{Intent i=new Intent(this,ApartmentPlanActivity.class);i.putExtra("projectId",projectId);startActivity(i);});
        root.addView(apartment,Ui.lpMatchWrap(this,18));

        Button scan=Ui.button(this,r.status==AreaCheckResult.Status.MATCH?"Скан сходиться ✓ • додати контрольний прохід":"Досканувати слабкі зони");
        scan.setOnClickListener(v->{Intent i=new Intent(this,ArCoreScanActivity.class);i.putExtra("projectId",projectId);startActivity(i);});
        if(r.status!=AreaCheckResult.Status.MATCH) root.addView(Ui.muted(this,"Другий прохід починай у вже добре відсканованій кімнаті, затримайся там кілька секунд, а потім іди саме в червоні зони — так fusion надійніше прив’яже новий прохід."),Ui.lpMatchWrap(this,6));
        root.addView(scan,Ui.lpMatchWrap(this,8));

        Button setup=Ui.button(this,"Змінити контрольну площу/тип");
        setup.setOnClickListener(v->{Intent i=new Intent(this,ProjectActivity.class);i.putExtra("projectId",projectId);startActivity(i);});
        root.addView(setup,Ui.lpMatchWrap(this,8));
        root.addView(Ui.bottomNav(this,projectId,"PLAN"),Ui.lpMatchWrap(this,18));
        setContentView(scroll);
    }

    private String statusLabel(AreaCheckResult.Status s){
        switch(s){case MATCH:return "✓ Площа сходиться";case REVIEW:return "! Треба перевірити";case RESCAN_REQUIRED:return "× Скан неповний";default:return "— Немає контрольної площі";}
    }
    private int statusColor(AreaCheckResult.Status s){
        switch(s){case MATCH:return Color.rgb(57,217,138);case REVIEW:return Color.rgb(255,196,0);case RESCAN_REQUIRED:return Color.rgb(255,90,95);default:return Color.rgb(164,164,173);}
    }
}
