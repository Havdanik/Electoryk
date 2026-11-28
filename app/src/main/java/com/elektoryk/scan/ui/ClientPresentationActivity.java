package com.elektoryk.scan.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.elektoryk.scan.model.*;
import com.elektoryk.scan.reconstruction.InstallationRoutingEngine;
import com.elektoryk.scan.storage.FileProjectRepository;
import com.elektoryk.scan.storage.FloorPlanRepository;
import com.elektoryk.scan.storage.SceneRepository;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public class ClientPresentationActivity extends Activity implements ClientPresentationView.Listener {
    private String projectId;
    private ElectricalProject project;
    private SceneSnapshot scene;
    private FloorPlan floor;
    private ApartmentPlan plan;
    private InstallationRoutingResult routing;

    private ClientPresentationView presentation;
    private WallElevationView elevation;
    private TextView selection;
    private ChoiceStrip mode;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        projectId=getIntent().getStringExtra("projectId");
        project=new FileProjectRepository(this).get(projectId);
        scene=new SceneRepository(this).loadScene(projectId);
        floor=new FloorPlanRepository(this).load(projectId);
        plan=scene==null?null:scene.apartmentPlan;
        if(project!=null&&plan!=null)routing=new InstallationRoutingEngine().build(project,plan,floor);
        buildUi();
    }

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(9,9,11));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,30));scroll.addView(root);

        root.addView(Ui.brandHeader(this,"ELEKTORYK","Професійна презентація електропроєкту для клієнта.","КЛІЄНТ",false));
        root.addView(Ui.screenIntro(this,"ПРЕЗЕНТАЦІЯ","План, електрика і стіни — зрозуміло клієнту","Перемикай шари, натискай на кімнату або стіну, показуй розташування точок і одразу зберігай PNG."),Ui.lpMatchWrap(this,10));

        if(plan==null){
            root.addView(Ui.muted(this,"Спочатку потрібен Multi-room план."));
            setContentView(scroll);return;
        }

        mode=new ChoiceStrip(this);
        mode.setItems(
                new String[]{"CLEAN","ELECTRICAL","LIGHTING","TECHNICAL","ACCURACY"},
                new String[]{"План","Електрика","Світло","Технічний","Точність"},
                "CLEAN");
        mode.setListener(k->{
            try{presentation.setMode(ClientPresentationView.Mode.valueOf(k));}catch(Exception ignored){}
        });
        root.addView(mode,Ui.lpMatchWrap(this,10));

        LinearLayout summary=Ui.card(this);
        summary.addView(Ui.title(this,project==null||project.name==null||project.name.isEmpty()
                ?"Електропроєкт":project.name,20));
        summary.addView(Ui.muted(this,String.format(Locale.US,
                "%d зон • %.1f м² • %d електроточок",
                plan.rooms.size(),plan.totalObservedAreaM2,floor==null?0:floor.points.size())));
        if(scene!=null&&scene.precisionReport!=null){
            summary.addView(Ui.muted(this,String.format(Locale.US,
                    "Точність: %d/100 • очікувана похибка ≈ %.1f см",
                    scene.precisionReport.score,scene.precisionReport.estimatedPlanErrorCm)),Ui.lpMatchWrap(this,5));
        }
        root.addView(summary,Ui.lpMatchWrap(this,10));

        LinearLayout clientStats=new LinearLayout(this);clientStats.setOrientation(LinearLayout.HORIZONTAL);
        clientStats.addView(Ui.statCard(this,String.format(Locale.US,"%.1f",plan.totalObservedAreaM2),"м² площа"),Ui.lpWeight(this,1f,7));
        clientStats.addView(Ui.statCard(this,String.valueOf(floor==null?0:floor.points.size()),"точок"),Ui.lpWeight(this,1f,7));
        clientStats.addView(Ui.statCard(this,routing==null?"—":String.format(Locale.US,"%.0f",routing.totalPurchaseCableM),"м кабелю"),Ui.lpWeight(this,1f,7));
        clientStats.addView(Ui.statCard(this,scene!=null&&scene.precisionReport!=null?String.valueOf(scene.precisionReport.score):"—","точність"),Ui.lpWeight(this,1f,0));
        root.addView(clientStats,Ui.lpMatchWrap(this,8));

        presentation=new ClientPresentationView(this);
        presentation.setData(plan,floor,routing==null?null:routing.circuitRoutes,
                scene==null?null:scene.precisionReport);
        presentation.setListener(this);
        root.addView(presentation,new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,500)));

        selection=Ui.muted(this,"Натисни на кімнату або стіну.");
        root.addView(selection,Ui.lpMatchWrap(this,8));

        elevation=new WallElevationView(this);
        elevation.setData(plan,floor,null);
        root.addView(elevation,new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,330)));

        root.addView(Ui.title(this,"Що запроєктовано",19),Ui.lpMatchWrap(this,16));
        addPointSummary(root);

        if(routing!=null&&!routing.materials.cableM.isEmpty()){
            root.addView(Ui.title(this,"Технічна довідка",19),Ui.lpMatchWrap(this,16));
            root.addView(Ui.muted(this,String.format(Locale.US,
                    "Кабель до закупівлі: %.1f м • проходок: %d • орієнтовно кріплень: %d",
                    routing.totalPurchaseCableM,routing.materials.wallPenetrations,routing.materials.fasteners)));
        }

        android.widget.Button share=Ui.button(this,"Зберегти PNG / поділитися з клієнтом");
        share.setOnClickListener(v->ClientImageExporter.saveAndShare(
                this,presentation,project==null?"elektoryk_plan":project.name));
        root.addView(share,Ui.lpMatchWrap(this,16));

        android.widget.Button wallPng=Ui.secondaryButton(this,"Зберегти розгортку вибраної стіни");
        wallPng.setOnClickListener(v->ClientImageExporter.saveAndShare(
                this,elevation,(project==null?"elektoryk":project.name)+"_wall"));
        root.addView(wallPng,Ui.lpMatchWrap(this,8));

        android.widget.Button twin=Ui.button(this,"▣ Пройтись по Digital Twin з клієнтом");
        twin.setOnClickListener(v->{android.content.Intent i=new android.content.Intent(this,DigitalTwinActivity.class);i.putExtra("projectId",projectId);startActivity(i);});
        root.addView(twin,Ui.lpMatchWrap(this,16));

        android.widget.Button view3d=Ui.secondaryButton(this,"3D RAW / CLEAN модель");
        view3d.setOnClickListener(v->{
            android.content.Intent i=new android.content.Intent(this,ScenePreviewActivity.class);
            i.putExtra("projectId",projectId);startActivity(i);
        });
        root.addView(view3d,Ui.lpMatchWrap(this,16));

        android.widget.Button accuracy=Ui.secondaryButton(this,"Показати контроль точності");
        accuracy.setOnClickListener(v->{
            android.content.Intent i=new android.content.Intent(this,AccuracyActivity.class);
            i.putExtra("projectId",projectId);startActivity(i);
        });
        root.addView(accuracy,Ui.lpMatchWrap(this,8));

        root.addView(Ui.muted(this,
                "Візуалізація показує заплановане розташування електрики. Це не фотореалістичний дизайн-проєкт і не замінює фінальні контрольні заміри перед монтажем."),
                Ui.lpMatchWrap(this,18));

        android.widget.Button handover=Ui.button(this,"◈ VIP здача / паспорт об'єкта");
        handover.setOnClickListener(v->{android.content.Intent i=new android.content.Intent(this,HandoverActivity.class);i.putExtra("projectId",projectId);startActivity(i);});
        root.addView(handover,Ui.lpMatchWrap(this,16));

        root.addView(Ui.bottomNav(this,projectId,"CLIENT"),Ui.lpMatchWrap(this,18));
        setContentView(scroll);
    }

    private void addPointSummary(LinearLayout root){
        if(floor==null||floor.points.isEmpty()){
            root.addView(Ui.muted(this,"Електроточки ще не розставлені."));return;
        }
        Map<ElectricalPoint.Type,Integer> counts=new EnumMap<>(ElectricalPoint.Type.class);
        int modules=0;
        for(ElectricalPoint e:floor.points){
            Integer n=counts.get(e.type);counts.put(e.type,n==null?1:n+1);
            if(e.type==ElectricalPoint.Type.SOCKET||e.type==ElectricalPoint.Type.SWITCH)modules+=Math.max(1,e.moduleCount);
        }
        for(ElectricalPoint.Type t:ElectricalPoint.Type.values()){
            Integer n=counts.get(t);if(n==null||n==0)continue;
            root.addView(Ui.muted(this,friendly(t)+": "+n),Ui.lpMatchWrap(this,4));
        }
        root.addView(Ui.muted(this,"Сумарно модулів розеток/вимикачів: "+modules),Ui.lpMatchWrap(this,5));
    }

    @Override public void onWallSelected(PlanWallSegment wall){
        presentation.setSelectedWall(wall.id);
        elevation.setData(plan,floor,wall);
        int points=0;
        if(floor!=null)for(ElectricalPoint e:floor.points)if(wall.id.equals(e.wallId))points++;
        selection.setText(String.format(Locale.US,
                "Стіна %.2f м • %s • електроточок на стіні: %d",
                wall.lengthM(),materialLabel(wall.materialKey),points));
    }

    @Override public void onRoomSelected(RoomZone room){
        int points=0,lights=0,sockets=0;
        if(floor!=null)for(ElectricalPoint e:floor.points)if(room.id.equals(e.roomId)){
            points++;if(e.type==ElectricalPoint.Type.LIGHT)lights++;
            if(e.type==ElectricalPoint.Type.SOCKET)sockets+=Math.max(1,e.moduleCount);
        }
        selection.setText(String.format(Locale.US,
                "%s • %.1f м² • точок %d • розеткових модулів %d • світла %d",
                room.displayName(),room.areaM2,points,sockets,lights));
    }

    private String friendly(ElectricalPoint.Type t){
        switch(t){
            case SOCKET:return "Розетки";case SWITCH:return "Вимикачі";case LIGHT:return "Світло";case PANEL:return "Щит";
            case TV:return "TV";case ETHERNET:return "LAN";case APPLIANCE:return "Техніка";case SENSOR:return "Датчики";
            case CAMERA:return "Камери";case SMART_HOME:return "Smart Home";default:return t.name();
        }
    }
    private String materialLabel(String key){
        if("CONCRETE".equals(key))return "бетон";
        if("BRICK".equals(key))return "цегла";
        if("AERATED".equals(key))return "газоблок";
        if("DRYWALL".equals(key))return "гіпсокартон";
        return "матеріал не задано";
    }
}
