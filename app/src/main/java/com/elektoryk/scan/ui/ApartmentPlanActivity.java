package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.elektoryk.scan.model.ApartmentOpening;
import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.MissingZoneHint;
import com.elektoryk.scan.model.RoomZone;
import com.elektoryk.scan.model.SceneSnapshot;
import com.elektoryk.scan.model.StructuralFeature;
import com.elektoryk.scan.reconstruction.AutoFloorPlanBuilder;
import com.elektoryk.scan.storage.FloorPlanRepository;
import com.elektoryk.scan.storage.SceneRepository;

import java.util.Locale;

public class ApartmentPlanActivity extends Activity {
    private String projectId;
    private SceneRepository repo;
    private SceneSnapshot scene;
    private ApartmentPlan plan;
    private TextView selectedInfo;
    private ChoiceStrip roomType;
    private RoomZone selectedRoom;
    private ApartmentPlanView map;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        projectId=getIntent().getStringExtra("projectId");
        repo=new SceneRepository(this);
        scene=repo.loadScene(projectId);
        plan=scene==null?null:scene.apartmentPlan;
        buildUi();
    }

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(11,11,13));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,28));scroll.addView(root);

        root.addView(Ui.brandHeader(this,"ELEKTORYK • План","Зрозумілий спільний план квартири після Smart Scan.","ПЛАН",true));
        root.addView(Ui.screenIntro(this,"MULTI-ROOM","Єдиний план квартири","Кімнати, двері, вікна, конструктив і пропущені зони — все в одному місці. Натисни кімнату, щоб уточнити її тип."),Ui.lpMatchWrap(this,10));

        if(plan==null||plan.rooms.isEmpty()){
            root.addView(Ui.title(this,"Multi-room ще не побудований",19),Ui.lpMatchWrap(this,18));
            root.addView(Ui.muted(this,"Зроби новий Smart Scan усієї квартири одним проходом: заходь у кожну кімнату через двері й показуй обидві сторони прорізу."));
            Button scan=Ui.button(this,"Почати Multi-room Smart Scan");
            scan.setOnClickListener(v->open(ArCoreScanActivity.class));
            root.addView(scan,Ui.lpMatchWrap(this,14));
            setContentView(scroll);return;
        }

        int doors=0,windows=0,columns=0,niches=0;
        for(ApartmentOpening o:plan.openings){
            if(o.type==ApartmentOpening.Type.DOOR)doors++;
            if(o.type==ApartmentOpening.Type.WINDOW)windows++;
        }
        for(StructuralFeature f:plan.features){
            if(f.type==StructuralFeature.Type.COLUMN)columns++;
            if(f.type==StructuralFeature.Type.NICHE)niches++;
        }

        LinearLayout summary=Ui.card(this);
        summary.addView(Ui.title(this,String.format(Locale.US,
                "%d зон • %.1f м²",plan.rooms.size(),plan.totalObservedAreaM2),21));
        summary.addView(Ui.muted(this,String.format(Locale.US,
                "Двері %d • вікна %d • колони %d • ніші-кандидати %d • confidence %.0f%%",
                doors,windows,columns,niches,plan.confidence*100)));
        summary.addView(Ui.muted(this,String.format(Locale.US,
                "Підлога: напряму %.1f м² + відновлено під перекриттями %.1f м² = %.1f м²",
                plan.directObservedAreaM2,plan.inferredFloorAreaM2,plan.totalObservedAreaM2)),Ui.lpMatchWrap(this,5));
        if(scene!=null&&scene.areaCheck!=null&&scene.areaCheck.referenceAreaM2>0){
            summary.addView(Ui.muted(this,String.format(Locale.US,
                    "Контроль площі: %.1f / %.1f м² • %s",
                    scene.areaCheck.scannedAreaM2,scene.areaCheck.referenceAreaM2,
                    scene.areaCheck.status.name())),Ui.lpMatchWrap(this,5));
        }
        root.addView(summary,Ui.lpMatchWrap(this,12));

        map=new ApartmentPlanView(this);
        map.setData(plan,scene==null?null:scene.scanPath);
        map.setListener(room->selectRoom(room));
        root.addView(map,new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,430)));
        root.addView(Ui.muted(this,"Легенда: зелений — двері • синій — вікна • червоний круг — можливо пропущена зона • біла лінія — шлях сканування."),Ui.lpMatchWrap(this,7));

        selectedInfo=Ui.muted(this,"Натисни на кімнату/зону на плані.");
        root.addView(selectedInfo,Ui.lpMatchWrap(this,10));

        roomType=new ChoiceStrip(this);
        roomType.setItems(roomKeys(),roomLabels(),RoomZone.RoomType.UNKNOWN.name());
        roomType.setListener(k->{
            if(selectedRoom==null)return;
            try{selectedRoom.type=RoomZone.RoomType.valueOf(k);}catch(Exception ignored){}
            saveScene();
            map.invalidate();
            selectRoom(selectedRoom);
        });
        root.addView(roomType,Ui.lpMatchWrap(this,7));

        if(!plan.missingZoneHints.isEmpty()){
            root.addView(Ui.title(this,"Ймовірно пропущені зони",19),Ui.lpMatchWrap(this,18));
            for(MissingZoneHint h:plan.missingZoneHints){
                String estimate=h.estimatedAreaMaxM2>0
                        ?String.format(Locale.US," • орієнтовно %.1f–%.1f м²",h.estimatedAreaMinM2,h.estimatedAreaMaxM2)
                        :"";
                root.addView(Ui.muted(this,"● "+h.reason+estimate),Ui.lpMatchWrap(this,6));
            }
        }

        Button twin=Ui.button(this,"▣ Digital Twin • пройтись + розставити по стінах");
        twin.setOnClickListener(v->open(DigitalTwinActivity.class));
        root.addView(twin,Ui.lpMatchWrap(this,18));

        Button edit=Ui.button(this,"✎ Виправити план + розставити електрику");
        edit.setOnClickListener(v->open(ApartmentEditorActivity.class));
        root.addView(edit,Ui.lpMatchWrap(this,18));

        Button route=Ui.button(this,"⚡ Чернеткові траси кабелю");
        route.setOnClickListener(v->open(RoutingActivity.class));
        root.addView(route,Ui.lpMatchWrap(this,8));

        Button accuracy=Ui.button(this,"◎ Перевірити точність плану");
        accuracy.setOnClickListener(v->open(AccuracyActivity.class));
        root.addView(accuracy,Ui.lpMatchWrap(this,8));

        Button client=Ui.button(this,"◈ Показати клієнту");
        client.setOnClickListener(v->open(ClientPresentationActivity.class));
        root.addView(client,Ui.lpMatchWrap(this,8));

        Button plan2d=Ui.button(this,"Перенести кімнати в 2D редактор електрики");
        plan2d.setOnClickListener(v->{
            new FloorPlanRepository(this).save(new AutoFloorPlanBuilder().build(scene));
            open(PlanEditorActivity.class);
        });
        root.addView(plan2d,Ui.lpMatchWrap(this,18));

        Button area=Ui.button(this,"Контроль площі / де втрачено");
        area.setOnClickListener(v->open(AreaCheckActivity.class));
        root.addView(area,Ui.lpMatchWrap(this,8));

        Button rescan=Ui.button(this,"Додати / пересканувати квартиру");
        rescan.setOnClickListener(v->open(ArCoreScanActivity.class));
        root.addView(rescan,Ui.lpMatchWrap(this,8));

        root.addView(Ui.bottomNav(this,projectId,"PLAN"),Ui.lpMatchWrap(this,18));
        setContentView(scroll);
    }

    private void selectRoom(RoomZone room){
        selectedRoom=room;
        roomType.setSelectedKey(room.type.name());
        selectedInfo.setText(String.format(Locale.US,
                "%s • %.1f м² • %.2f × %.2f м envelope • confidence %.0f%%%s\n%s",
                room.displayName(),room.areaM2,room.maxX-room.minX,room.maxZ-room.minZ,
                room.confidence*100,room.narrowPassageLike?" • схоже на коридор":"",
                connectionSummary(room)));
    }

    private String connectionSummary(RoomZone room){
        if(plan==null)return "";
        java.util.ArrayList<String> links=new java.util.ArrayList<>();
        int windows=0;
        for(ApartmentOpening o:plan.openings){
            if(o.type==ApartmentOpening.Type.WINDOW){
                if(room.id.equals(o.roomAId)||room.id.equals(o.roomBId))windows++;
                continue;
            }
            if(o.type!=ApartmentOpening.Type.DOOR)continue;
            String other="";
            if(room.id.equals(o.roomAId))other=o.roomBId;
            else if(room.id.equals(o.roomBId))other=o.roomAId;
            else continue;
            if(other==null||other.isEmpty()){
                links.add(o.external?"вхід/назовні":"незавершена зона");
            }else{
                RoomZone target=findRoom(other);
                links.add(target==null?"сусідня зона":target.displayName());
            }
        }
        String doors=links.isEmpty()?"дверні зв'язки не визначені":android.text.TextUtils.join(", ",links);
        return "Зв'язки: "+doors+" • вікон: "+windows;
    }

    private RoomZone findRoom(String id){
        if(plan==null||id==null)return null;
        for(RoomZone r:plan.rooms)if(id.equals(r.id))return r;
        return null;
    }

    private void saveScene(){
        if(scene==null)return;
        repo.save(projectId,scene,null);
    }

    private String[] roomKeys(){
        RoomZone.RoomType[] a=RoomZone.RoomType.values();String[] x=new String[a.length];
        for(int i=0;i<a.length;i++)x[i]=a[i].name();return x;
    }
    private String[] roomLabels(){
        RoomZone.RoomType[] a=RoomZone.RoomType.values();String[] x=new String[a.length];
        for(int i=0;i<a.length;i++)x[i]=a[i].label;return x;
    }

    private void open(Class<?> c){
        Intent i=new Intent(this,c);i.putExtra("projectId",projectId);startActivity(i);
    }
}
