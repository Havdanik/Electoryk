package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.elektoryk.scan.model.CableRoute;
import com.elektoryk.scan.model.ElectricalProject;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.InstallationMaterialEstimate;
import com.elektoryk.scan.model.InstallationRoutingResult;
import com.elektoryk.scan.model.MountingSegment;
import com.elektoryk.scan.model.SceneSnapshot;
import com.elektoryk.scan.reconstruction.InstallationRoutingEngine;
import com.elektoryk.scan.storage.FileProjectRepository;
import com.elektoryk.scan.storage.FloorPlanRepository;
import com.elektoryk.scan.storage.SceneRepository;

import java.util.Locale;
import java.util.Map;

public class RoutingActivity extends Activity {
    private String projectId;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        projectId=getIntent().getStringExtra("projectId");

        ElectricalProject project=new FileProjectRepository(this).get(projectId);
        SceneSnapshot scene=new SceneRepository(this).loadScene(projectId);
        FloorPlan floor=new FloorPlanRepository(this).load(projectId);

        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(11,11,13));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,28));scroll.addView(root);

        root.addView(Ui.brandHeader(this,"ELEKTORYK • Траси","Кабель, проходки, пучки та монтажні матеріали.","ТРАСИ",true));
        root.addView(Ui.screenIntro(this,"МОНТАЖ","Монтажні траси + матеріали","Маршрут від щита до точок, заборонені зони, проходки через стіни, спільні пучки, запаси і підсумкова відомість."),Ui.lpMatchWrap(this,10));

        if(project==null||scene==null||scene.apartmentPlan==null){
            root.addView(Ui.muted(this,"Потрібні об'єкт, Multi-room план і електроточки."));
            setContentView(scroll);return;
        }

        InstallationRoutingResult result=new InstallationRoutingEngine()
                .build(project,scene.apartmentPlan,floor);

        LinearLayout summary=Ui.card(this);
        summary.addView(Ui.title(this,String.format(Locale.US,
                "%.1f м розрахункового кабелю → %.1f м до закупівлі",
                result.totalRawCableM,result.totalPurchaseCableM),20));
        summary.addView(Ui.muted(this,
                "Прокладання: "+project.settings.wiringMethod.name()
                +" • захист: "+project.settings.cableProtection.name()
                +" • проходи: "+project.settings.wallPassageMode.name()));
        summary.addView(Ui.muted(this,String.format(Locale.US,
                "Запаси: щит %.2f м • точка %.2f м • проходка %.2f м • +%.0f%%",
                project.settings.panelReserveM,project.settings.endpointReserveM,
                project.settings.passageReserveM,project.settings.cableReservePercent)),Ui.lpMatchWrap(this,5));
        root.addView(summary,Ui.lpMatchWrap(this,12));

        LinearLayout routeStats=new LinearLayout(this);routeStats.setOrientation(LinearLayout.HORIZONTAL);
        routeStats.addView(Ui.statCard(this,String.format(Locale.US,"%.0f",result.totalPurchaseCableM),"м кабелю"),Ui.lpWeight(this,1f,7));
        routeStats.addView(Ui.statCard(this,String.valueOf(result.penetrations.size()),"проходки"),Ui.lpWeight(this,1f,7));
        routeStats.addView(Ui.statCard(this,String.valueOf(result.materials.fasteners),"кріплення"),Ui.lpWeight(this,1f,7));
        routeStats.addView(Ui.statCard(this,String.valueOf(result.circuitRoutes.size()),"групи"),Ui.lpWeight(this,1f,0));
        root.addView(routeStats,Ui.lpMatchWrap(this,8));

        ApartmentEditView map=new ApartmentEditView(this);
        map.setData(scene.apartmentPlan,floor,result.circuitRoutes);
        map.setInteractionMode("SELECT");
        root.addView(map,new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,440)));
        root.addView(Ui.muted(this,"Легенда: світлі лінії — монтажні траси • червоне — зона, де не вести • точки — електрика • золотий акцент — вибране/важливе"),Ui.lpMatchWrap(this,7));

        InstallationMaterialEstimate m=result.materials;

        root.addView(Ui.title(this,"Кабель",19),Ui.lpMatchWrap(this,16));
        if(m.cableM.isEmpty())root.addView(Ui.muted(this,"Поки немає розрахованого кабелю."));
        for(Map.Entry<String,Double> e:m.cableM.entrySet()){
            root.addView(Ui.muted(this,String.format(Locale.US,
                    "%s → %.1f м",e.getKey(),e.getValue())),Ui.lpMatchWrap(this,5));
        }

        root.addView(Ui.title(this,"Монтажні матеріали / роботи",19),Ui.lpMatchWrap(this,16));
        addMetric(root,"Гофра",m.corrugatedConduitM,"м");
        addMetric(root,"Лоток / спільний коридор",m.trayM,"м");
        addMetric(root,"Штроба по стінах",m.wallChaseM,"м");
        addMetric(root,"Прокладання по підлозі",m.floorRouteM,"м");
        addMetric(root,"Спільні пучки",m.bundledCorridorM,"м");
        root.addView(Ui.muted(this,"Проходок через стіни: "+m.wallPenetrations),Ui.lpMatchWrap(this,5));
        root.addView(Ui.muted(this,"Орієнтовно кріплень: "+m.fasteners),Ui.lpMatchWrap(this,5));
        if(m.deepSocketBoxes>0)root.addView(Ui.muted(this,"Глибоких підрозетників/місць з'єднань: "+m.deepSocketBoxes),Ui.lpMatchWrap(this,5));
        if(m.junctionBoxes>0)root.addView(Ui.muted(this,"Розподільчих коробок: "+m.junctionBoxes),Ui.lpMatchWrap(this,5));

        root.addView(Ui.title(this,"Групи",19),Ui.lpMatchWrap(this,16));
        if(result.circuitRoutes.isEmpty())root.addView(Ui.muted(this,"Маршрутів поки немає."));
        for(CableRoute r:result.circuitRoutes){
            LinearLayout card=Ui.card(this);
            card.addView(Ui.title(this,"Група "+r.circuitNo+" • "+r.cableKey,17));
            card.addView(Ui.muted(this,String.format(Locale.US,
                    "Точок %d • з фіксованими запасами %.1f м • +%.0f%% → %.1f м",
                    r.pointIds.size(),r.rawLengthM,r.reservePercent,r.purchaseLengthM)));
            if(r.warning!=null&&!r.warning.isEmpty())card.addView(Ui.muted(this,"! "+r.warning),Ui.lpMatchWrap(this,4));
            root.addView(card,Ui.lpMatchWrap(this,7));
        }

        if(!result.penetrations.isEmpty()){
            root.addView(Ui.title(this,"Проходки",19),Ui.lpMatchWrap(this,16));
            int i=1;
            for(com.elektoryk.scan.model.WallPenetration wp:result.penetrations){
                root.addView(Ui.muted(this,String.format(Locale.US,
                        "%d. %s • стіна %.0f мм • X %.2f / Z %.2f",
                        i++,wp.method,wp.wallThicknessM*1000,wp.x,wp.z)),Ui.lpMatchWrap(this,5));
            }
        }

        int shared=0,maxBundle=0;
        for(MountingSegment s:result.mountingSegments){
            if(s.shared)shared++;
            maxBundle=Math.max(maxBundle,s.cableCount);
        }
        root.addView(Ui.title(this,"Пучки / коридори",19),Ui.lpMatchWrap(this,16));
        root.addView(Ui.muted(this,"Спільних сегментів: "+shared+" • максимальний пучок: "+maxBundle+" каб."),Ui.lpMatchWrap(this,5));

        if(!result.warnings.isEmpty()){
            root.addView(Ui.title(this,"Що треба перевірити",19),Ui.lpMatchWrap(this,16));
            for(String w:result.warnings)root.addView(Ui.muted(this,"• "+w),Ui.lpMatchWrap(this,5));
        }

        Button ceiling=Ui.button(this,"⌂ Розмітка трас на стелі");ceiling.setOnClickListener(v->{Intent i=new Intent(this,CeilingMarkingActivity.class);i.putExtra("projectId",projectId);startActivity(i);});root.addView(ceiling,Ui.lpMatchWrap(this,18));

        Button field=Ui.button(this,"✂ Кабельна карта / CUT / етикетки");
        field.setOnClickListener(v->{Intent i=new Intent(this,FieldWorkActivity.class);i.putExtra("projectId",projectId);startActivity(i);});
        root.addView(field,Ui.lpMatchWrap(this,18));

        Button transfer=Ui.secondaryButton(this,"⇄ Передати об'єкт на інший пристрій");
        transfer.setOnClickListener(v->{Intent i=new Intent(this,ProjectTransferActivity.class);i.putExtra("projectId",projectId);startActivity(i);});
        root.addView(transfer,Ui.lpMatchWrap(this,8));

        Button edit=Ui.button(this,"← Редагувати план / точки / ⛔ зони");
        edit.setOnClickListener(v->{
            Intent i=new Intent(this,ApartmentEditorActivity.class);i.putExtra("projectId",projectId);startActivity(i);
        });
        root.addView(edit,Ui.lpMatchWrap(this,18));

        Button setup=Ui.button(this,"⚙ Змінити монтажні правила");
        setup.setOnClickListener(v->{
            Intent i=new Intent(this,ProjectActivity.class);i.putExtra("projectId",projectId);startActivity(i);
        });
        root.addView(setup,Ui.lpMatchWrap(this,8));

        root.addView(Ui.bottomNav(this,projectId,"ELECTRIC"),Ui.lpMatchWrap(this,18));
        setContentView(scroll);
    }

    private void addMetric(LinearLayout root,String label,double value,String unit){
        if(value<=0.01)return;
        root.addView(Ui.muted(this,String.format(Locale.US,"%s: %.1f %s",label,value,unit)),Ui.lpMatchWrap(this,5));
    }
}
