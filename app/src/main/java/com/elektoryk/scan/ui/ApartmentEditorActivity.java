package com.elektoryk.scan.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.elektoryk.scan.model.ApartmentOpening;
import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.ElectricalPoint;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.ForbiddenZone;
import com.elektoryk.scan.model.PlanWallSegment;
import com.elektoryk.scan.model.RoomRect;
import com.elektoryk.scan.model.RoomZone;
import com.elektoryk.scan.model.SceneSnapshot;
import com.elektoryk.scan.model.Vec3;
import com.elektoryk.scan.reconstruction.ApartmentPlanEditorEngine;
import com.elektoryk.scan.reconstruction.AutoFloorPlanBuilder;
import com.elektoryk.scan.reconstruction.WallSnapper;
import com.elektoryk.scan.reconstruction.WallPlacementEngine;
import com.elektoryk.scan.storage.FloorPlanRepository;
import com.elektoryk.scan.storage.SceneRepository;
import com.elektoryk.scan.storage.StabilityReporter;

import java.util.Locale;

public class ApartmentEditorActivity extends Activity implements ApartmentEditView.Listener {
    private String projectId;
    private SceneRepository sceneRepo;
    private FloorPlanRepository floorRepo;
    private SceneSnapshot scene;
    private ApartmentPlan plan;
    private FloorPlan floor;
    private final ApartmentPlanEditorEngine editor=new ApartmentPlanEditorEngine();
    private final WallPlacementEngine wallPlacement=new WallPlacementEngine();

    private ApartmentEditView map;
    private ChoiceStrip mode;
    private TextView status;
    private TextView selectedInfo;

    private LinearLayout roomPanel,wallPanel,openingPanel,pointPanel;
    private ChoiceStrip roomType,wallMaterial,openingType,pointType,cableType,moduleStrip,forbiddenType,loadPreset;
    private NumberStepper wallThickness,openingWidth,openingBottom,openingHeight,pointHeight,circuitNo,plannedLoad;

    private RoomZone selectedRoom;
    private PlanWallSegment selectedWall;
    private ApartmentOpening selectedOpening;
    private ElectricalPoint selectedPoint;

    private String mergeFirstRoomId="";
    private Double wallStartX=null,wallStartZ=null;
    private Double forbiddenStartX=null,forbiddenStartZ=null;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        projectId=getIntent().getStringExtra("projectId");
        sceneRepo=new SceneRepository(this);
        floorRepo=new FloorPlanRepository(this);
        scene=sceneRepo.loadScene(projectId);
        plan=scene==null?null:scene.apartmentPlan;
        floor=floorRepo.load(projectId);
        if(plan!=null)syncFloorRooms();
        buildUi();
    }

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(11,11,13));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,30));scroll.addView(root);

        root.addView(Ui.brandHeader(this,"ELEKTORYK • Електрика","Головний робочий екран електрика.","ЕЛЕКТРИКА",true));
        root.addView(Ui.screenIntro(this,"РЕДАКТОР","Квартира + електрика","Виправляй геометрію, розставляй точки, групи, висоти та заборонені зони. Ручні правки зберігаються окремо від автоскана."),Ui.lpMatchWrap(this,10));

        if(plan==null||plan.rooms.isEmpty()){
            root.addView(Ui.title(this,"Немає Multi-room плану",19),Ui.lpMatchWrap(this,16));
            root.addView(Ui.muted(this,"Спочатку зроби Smart Scan усієї квартири."));
            setContentView(scroll);return;
        }

        status=Ui.muted(this,"");
        root.addView(status,Ui.lpMatchWrap(this,10));

        if(scene!=null && scene.latestAutoApartmentPlan!=null){
            LinearLayout candidate=Ui.card(this);
            candidate.addView(Ui.title(this,"Є новий автоплан після повторного скану",18));
            candidate.addView(Ui.muted(this,"Твої ручні правки НЕ були перезаписані. Можеш лишити поточний план або свідомо замінити його новою автоматичною реконструкцією."));
            Button acceptAuto=Ui.button(this,"Переглянути / прийняти новий автоплан");
            acceptAuto.setOnClickListener(v->confirmAcceptAutoPlan());
            candidate.addView(acceptAuto,Ui.lpMatchWrap(this,7));
            root.addView(candidate,Ui.lpMatchWrap(this,10));
        }

        mode=new ChoiceStrip(this);
        mode.setItems(
                new String[]{"SELECT","MERGE","SPLIT_X","SPLIT_Z","WALL","FORBIDDEN","SOCKET","SWITCH","LIGHT","PANEL","TV","ETHERNET","APPLIANCE","CAMERA","SENSOR","SMART_HOME"},
                new String[]{"Вибір","Об'єднати","Розділити ↕","Розділити ↔","+ Стіна","⛔ Не вести","Розетка","Вимикач","Світло","Щит","TV","LAN","Техніка","Камера","Датчик","Smart"},
                "SELECT");
        mode.setListener(k->{
            map.setInteractionMode(k);
            mergeFirstRoomId="";wallStartX=null;wallStartZ=null;
            forbiddenStartX=null;forbiddenStartZ=null;
            clearSelection();
            updateStatus("Режим: "+modeLabel(k));
        });
        root.addView(mode,Ui.lpMatchWrap(this,8));

        forbiddenType=new ChoiceStrip(this);
        forbiddenType.setItems(
                new String[]{"NO_ROUTE","BEAM","VENTILATION","WET_ZONE","STRUCTURAL"},
                new String[]{"Не вести","Балка","Вентиляція","Мокра зона","Конструктив"},
                "NO_ROUTE");
        root.addView(forbiddenType,Ui.lpMatchWrap(this,6));

        map=new ApartmentEditView(this);
        map.setData(plan,floor,null);
        map.setListener(this);
        map.setInteractionMode("SELECT");
        root.addView(map,new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,500)));
        root.addView(Ui.muted(this,"Позначення: R — розетка • SW — вимикач • L — світло • Щ — щит • LAN — мережа • CAM — камера. Золотий = вибране/ручна правка."),Ui.lpMatchWrap(this,7));

        selectedInfo=Ui.muted(this,"Натисни на кімнату, стіну, двері/вікно або електроточку.");
        root.addView(selectedInfo,Ui.lpMatchWrap(this,8));

        buildRoomPanel(root);
        buildWallPanel(root);
        buildOpeningPanel(root);
        buildPointPanel(root);
        showPanel(null);

        Button twin=Ui.button(this,"▣ Digital Twin • 3D розстановка на стінах");
        twin.setOnClickListener(v->{android.content.Intent i=new android.content.Intent(this,DigitalTwinActivity.class);i.putExtra("projectId",projectId);startActivity(i);});
        root.addView(twin,Ui.lpMatchWrap(this,16));

        Button route=Ui.button(this,"⚡ Розрахувати монтажні траси і матеріали");
        route.setOnClickListener(v->{
            android.content.Intent i=new android.content.Intent(this,RoutingActivity.class);
            i.putExtra("projectId",projectId);startActivity(i);
        });
        root.addView(route,Ui.lpMatchWrap(this,16));

        Button fieldWork=Ui.secondaryButton(this,"✂ Монтажна карта / CUT / етикетки");
        fieldWork.setOnClickListener(v->{
            android.content.Intent i=new android.content.Intent(this,FieldWorkActivity.class);
            i.putExtra("projectId",projectId);startActivity(i);
        });
        root.addView(fieldWork,Ui.lpMatchWrap(this,8));

        Button electricalCheck=Ui.button(this,"✓ Перевірити електроплан");
        electricalCheck.setOnClickListener(v->{
            android.content.Intent i=new android.content.Intent(this,ElectricalCheckActivity.class);
            i.putExtra("projectId",projectId);startActivity(i);
        });
        root.addView(electricalCheck,Ui.lpMatchWrap(this,8));

        Button client=Ui.button(this,"◈ Режим презентації клієнту");
        client.setOnClickListener(v->{
            android.content.Intent i=new android.content.Intent(this,ClientPresentationActivity.class);
            i.putExtra("projectId",projectId);startActivity(i);
        });
        root.addView(client,Ui.lpMatchWrap(this,8));

        Button clearForbidden=Ui.button(this,"Очистити всі ⛔ заборонені зони");
        clearForbidden.setOnClickListener(v->{
            if(plan.forbiddenZones.isEmpty())return;
            new AlertDialog.Builder(this).setTitle("Очистити заборонені зони?")
                    .setMessage("Буде видалено "+plan.forbiddenZones.size()+" зон.")
                    .setNegativeButton("Ні",null)
                    .setPositiveButton("Так",(d,w)->{
                        plan.forbiddenZones.clear();editor.markEdited(plan);saveAll();map.invalidate();
                        updateStatus("Заборонені зони очищено.");
                    }).show();
        });
        root.addView(clearForbidden,Ui.lpMatchWrap(this,8));

        Button backPlan=Ui.button(this,"▦ Звичайний Multi-room перегляд");
        backPlan.setOnClickListener(v->finish());
        root.addView(backPlan,Ui.lpMatchWrap(this,8));

        updateStatus("");
        root.addView(Ui.bottomNav(this,projectId,"ELECTRIC"),Ui.lpMatchWrap(this,18));
        setContentView(scroll);
    }

    private void buildRoomPanel(LinearLayout root){
        roomPanel=Ui.card(this);
        roomPanel.addView(Ui.title(this,"Кімната / зона",18));
        roomType=new ChoiceStrip(this);
        roomType.setItems(roomKeys(),roomLabels(),RoomZone.RoomType.UNKNOWN.name());
        roomType.setListener(k->{
            if(selectedRoom==null)return;
            try{selectedRoom.type=RoomZone.RoomType.valueOf(k);}catch(Exception ignored){}
            editor.markEdited(plan);saveAll();map.invalidate();selectRoom(selectedRoom);
        });
        roomPanel.addView(roomType,Ui.lpMatchWrap(this,6));

        Button merge=Ui.button(this,"Об'єднати: вибрати другу кімнату");
        merge.setOnClickListener(v->{
            if(selectedRoom==null)return;
            mergeFirstRoomId=selectedRoom.id;
            mode.setSelectedKey("MERGE");map.setInteractionMode("MERGE");
            updateStatus("Тепер натисни на другу кімнату для об'єднання.");
        });
        roomPanel.addView(merge,Ui.lpMatchWrap(this,8));
        root.addView(roomPanel,Ui.lpMatchWrap(this,10));
    }

    private void buildWallPanel(LinearLayout root){
        wallPanel=Ui.card(this);
        wallPanel.addView(Ui.title(this,"Стіна",18));

        wallMaterial=new ChoiceStrip(this);
        wallMaterial.setItems(
                new String[]{"UNKNOWN","CONCRETE","BRICK","AERATED","DRYWALL"},
                new String[]{"Не знаю","Бетон","Цегла","Газоблок","Гіпсокартон"},"UNKNOWN");
        wallPanel.addView(wallMaterial,Ui.lpMatchWrap(this,5));

        wallThickness=new NumberStepper(this,"м товщина");
        wallThickness.configure(0.05,0.60,0.01,2);
        wallPanel.addView(wallThickness,Ui.lpMatchWrap(this,5));

        Button apply=Ui.button(this,"Застосувати матеріал/товщину");
        apply.setOnClickListener(v->{
            if(selectedWall==null)return;
            selectedWall.materialKey=wallMaterial.getSelectedKey();
            selectedWall.thicknessM=wallThickness.getValue();
            selectedWall.manual=true;selectedWall.confidence=1.0;
            editor.markEdited(plan);saveAll();map.invalidate();selectWall(selectedWall);
        });
        wallPanel.addView(apply,Ui.lpMatchWrap(this,6));

        LinearLayout shift=new LinearLayout(this);shift.setOrientation(LinearLayout.HORIZONTAL);
        Button minus=Ui.button(this,"← / −5 см");Button plus=Ui.button(this,"+5 см / →");
        minus.setOnClickListener(v->moveWall(-0.05));plus.setOnClickListener(v->moveWall(0.05));
        shift.addView(minus,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1);pp.leftMargin=Ui.dp(this,7);
        shift.addView(plus,pp);wallPanel.addView(shift,Ui.lpMatchWrap(this,7));

        LinearLayout addOpen=new LinearLayout(this);addOpen.setOrientation(LinearLayout.HORIZONTAL);
        Button door=Ui.button(this,"+ Двері");Button window=Ui.button(this,"+ Вікно");
        door.setOnClickListener(v->addOpening(ApartmentOpening.Type.DOOR));
        window.setOnClickListener(v->addOpening(ApartmentOpening.Type.WINDOW));
        addOpen.addView(door,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1);wp.leftMargin=Ui.dp(this,7);
        addOpen.addView(window,wp);wallPanel.addView(addOpen,Ui.lpMatchWrap(this,7));

        Button delete=Ui.button(this,"Видалити цю стіну");
        delete.setOnClickListener(v->confirmDeleteWall());
        wallPanel.addView(delete,Ui.lpMatchWrap(this,7));
        root.addView(wallPanel,Ui.lpMatchWrap(this,10));
    }

    private void buildOpeningPanel(LinearLayout root){
        openingPanel=Ui.card(this);
        openingPanel.addView(Ui.title(this,"Двері / вікно",18));

        openingType=new ChoiceStrip(this);
        openingType.setItems(new String[]{"DOOR","WINDOW"},new String[]{"Двері","Вікно"},"DOOR");
        openingPanel.addView(openingType,Ui.lpMatchWrap(this,5));

        openingWidth=new NumberStepper(this,"м ширина");
        openingWidth.configure(0.4,4.0,0.05,2);openingPanel.addView(openingWidth,Ui.lpMatchWrap(this,5));
        openingBottom=new NumberStepper(this,"м від підлоги");
        openingBottom.configure(0,2.5,0.05,2);openingPanel.addView(openingBottom,Ui.lpMatchWrap(this,5));
        openingHeight=new NumberStepper(this,"м висота");
        openingHeight.configure(0.4,3.5,0.05,2);openingPanel.addView(openingHeight,Ui.lpMatchWrap(this,5));

        Button apply=Ui.button(this,"Застосувати розміри");
        apply.setOnClickListener(v->{
            if(selectedOpening==null)return;
            ApartmentOpening.Type t="WINDOW".equals(openingType.getSelectedKey())
                    ?ApartmentOpening.Type.WINDOW:ApartmentOpening.Type.DOOR;
            editor.editOpening(plan,selectedOpening.id,t,openingWidth.getValue(),
                    openingBottom.getValue(),openingHeight.getValue());
            saveAll();map.invalidate();selectOpening(selectedOpening);
        });
        openingPanel.addView(apply,Ui.lpMatchWrap(this,7));

        Button del=Ui.button(this,"Видалити проріз");
        del.setOnClickListener(v->{
            if(selectedOpening==null)return;
            editor.removeOpening(plan,selectedOpening.id);selectedOpening=null;saveAll();clearSelection();
        });
        openingPanel.addView(del,Ui.lpMatchWrap(this,7));
        root.addView(openingPanel,Ui.lpMatchWrap(this,10));
    }

    private void buildPointPanel(LinearLayout root){
        pointPanel=Ui.card(this);
        pointPanel.addView(Ui.title(this,"Електроточка",18));

        pointType=new ChoiceStrip(this);
        pointType.setItems(
                new String[]{"SOCKET","SWITCH","LIGHT","PANEL","TV","ETHERNET","APPLIANCE","SENSOR","CAMERA","SMART_HOME"},
                new String[]{"Розетка","Вимикач","Світло","Щит","TV","LAN","Техніка","Датчик","Камера","Smart"},"SOCKET");
        pointPanel.addView(pointType,Ui.lpMatchWrap(this,5));

        pointHeight=new NumberStepper(this,"м висота");
        pointHeight.configure(0,4.0,0.05,2);pointPanel.addView(pointHeight,Ui.lpMatchWrap(this,5));

        circuitNo=new NumberStepper(this,"група");
        circuitNo.configure(0,64,1,0);pointPanel.addView(circuitNo,Ui.lpMatchWrap(this,5));

        moduleStrip=new ChoiceStrip(this);
        moduleStrip.setItems(new String[]{"1","2","3","4","5","6"},
                new String[]{"×1","×2","×3","×4","×5","×6"},"1");
        pointPanel.addView(moduleStrip,Ui.lpMatchWrap(this,5));

        cableType=new ChoiceStrip(this);
        cableType.setItems(new String[]{"AUTO","3x1.5","3x2.5","3x4","3x6","5x6"},
                new String[]{"Авто","3×1.5","3×2.5","3×4","3×6","5×6"},"AUTO");
        pointPanel.addView(cableType,Ui.lpMatchWrap(this,5));

        loadPreset=new ChoiceStrip(this);
        loadPreset.setItems(
                new String[]{"0","100","500","1000","2000","3500","7000"},
                new String[]{"Не задано","100W","500W","1kW","2kW","3.5kW","7kW"},"0");
        loadPreset.setListener(k->{
            try{plannedLoad.setValue(Double.parseDouble(k));}catch(Exception ignored){}
        });
        pointPanel.addView(loadPreset,Ui.lpMatchWrap(this,5));

        plannedLoad=new NumberStepper(this,"Вт навантаження");
        plannedLoad.configure(0,25000,100,0);
        pointPanel.addView(plannedLoad,Ui.lpMatchWrap(this,5));

        LinearLayout move=new LinearLayout(this);move.setOrientation(LinearLayout.HORIZONTAL);
        Button left=Ui.button(this,"← 10 см");Button right=Ui.button(this,"10 см →");
        left.setOnClickListener(v->movePointAlongWall(-0.10));
        right.setOnClickListener(v->movePointAlongWall(0.10));
        move.addView(left,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1);rp.leftMargin=Ui.dp(this,7);
        move.addView(right,rp);pointPanel.addView(move,Ui.lpMatchWrap(this,7));

        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.HORIZONTAL);
        Button copyL=Ui.button(this,"Копія −30 см");Button copyR=Ui.button(this,"Копія +30 см");
        copyL.setOnClickListener(v->duplicatePointAlongWall(-0.30));
        copyR.setOnClickListener(v->duplicatePointAlongWall(0.30));
        copy.addView(copyL,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1);cp.leftMargin=Ui.dp(this,7);
        copy.addView(copyR,cp);pointPanel.addView(copy,Ui.lpMatchWrap(this,7));

        Button apply=Ui.button(this,"Застосувати до точки");
        apply.setOnClickListener(v->applyPoint());
        pointPanel.addView(apply,Ui.lpMatchWrap(this,7));

        Button del=Ui.button(this,"Видалити точку");
        del.setOnClickListener(v->{
            if(selectedPoint==null)return;
            floor.points.remove(selectedPoint);selectedPoint=null;saveAll();clearSelection();
        });
        pointPanel.addView(del,Ui.lpMatchWrap(this,7));
        root.addView(pointPanel,Ui.lpMatchWrap(this,10));
    }

    @Override public void onWorldTap(double x,double z){
        String m=mode.getSelectedKey();
        if("MERGE".equals(m)){
            RoomZone r=map.roomAt(x,z);if(r==null)return;
            if(mergeFirstRoomId.isEmpty()){mergeFirstRoomId=r.id;updateStatus("Обрано першу кімнату. Натисни другу.");return;}
            if(editor.mergeRooms(plan,mergeFirstRoomId,r.id)){syncFloorRooms();saveAll();updateStatus("Кімнати об'єднано.");}
            mergeFirstRoomId="";mode.setSelectedKey("SELECT");map.setInteractionMode("SELECT");map.invalidate();return;
        }
        if("SPLIT_X".equals(m)||"SPLIT_Z".equals(m)){
            RoomZone r=map.roomAt(x,z);if(r==null){updateStatus("Торкнись всередині кімнати.");return;}
            ApartmentPlanEditorEngine.SplitAxis axis="SPLIT_X".equals(m)
                    ?ApartmentPlanEditorEngine.SplitAxis.X:ApartmentPlanEditorEngine.SplitAxis.Z;
            if(editor.splitRoom(plan,r.id,axis,axis==ApartmentPlanEditorEngine.SplitAxis.X?x:z)){
                syncFloorRooms();saveAll();map.invalidate();updateStatus("Кімнату розділено. За потреби додай двері на нову стіну.");
            }else updateStatus("Не вдалося розділити: одна зі сторін виходить надто мала.");
            return;
        }
        if("WALL".equals(m)){
            if(wallStartX==null){wallStartX=x;wallStartZ=z;updateStatus("Початок стіни задано. Натисни кінцеву точку.");return;}
            PlanWallSegment w=editor.addWall(plan,wallStartX,wallStartZ,x,z,0.12,"UNKNOWN");
            wallStartX=wallStartZ=null;
            if(w!=null){saveAll();map.invalidate();selectWall(w);mode.setSelectedKey("SELECT");map.setInteractionMode("SELECT");}
            return;
        }

        if("FORBIDDEN".equals(m)){
            if(forbiddenStartX==null){
                forbiddenStartX=x;forbiddenStartZ=z;
                updateStatus("Перший кут забороненої зони задано. Натисни протилежний кут.");
                return;
            }
            ForbiddenZone zc=new ForbiddenZone();
            try{zc.type=ForbiddenZone.Type.valueOf(forbiddenType.getSelectedKey());}catch(Exception ignored){}
            zc.minX=Math.min(forbiddenStartX,x);zc.maxX=Math.max(forbiddenStartX,x);
            zc.minZ=Math.min(forbiddenStartZ,z);zc.maxZ=Math.max(forbiddenStartZ,z);
            zc.minY=0;zc.maxY=Math.max(4.0,plan.ceilingY-plan.floorY+0.5);
            if((zc.maxX-zc.minX)>=0.10&&(zc.maxZ-zc.minZ)>=0.10){
                plan.forbiddenZones.add(zc);editor.markEdited(plan);saveAll();map.invalidate();
                updateStatus("Додано зону «"+forbiddenType.getSelectedKey()+"». Роутер її обходитиме.");
            }
            forbiddenStartX=forbiddenStartZ=null;
            mode.setSelectedKey("SELECT");map.setInteractionMode("SELECT");
            return;
        }

        ElectricalPoint.Type type=pointTypeFromMode(m);
        if(type!=null){addElectricalPoint(type,x,z);return;}
    }

    @Override public void onRoomSelected(RoomZone room){selectRoom(room);}
    @Override public void onWallSelected(PlanWallSegment wall){selectWall(wall);}
    @Override public void onOpeningSelected(ApartmentOpening opening){selectOpening(opening);}
    @Override public void onPointSelected(ElectricalPoint point){selectPoint(point);}

    private void addElectricalPoint(ElectricalPoint.Type type,double tapX,double tapZ){
        RoomZone room=map.roomAt(tapX,tapZ);
        if(room==null){updateStatus("Точка має бути всередині відомої кімнати.");return;}
        double x=tapX,z=tapZ;String wallId="";
        if(type!=ElectricalPoint.Type.LIGHT&&type!=ElectricalPoint.Type.SENSOR){
            WallSnapper.Result snap=new WallSnapper().snap(plan,tapX,tapZ,0.70);
            if(snap!=null){x=snap.x;z=snap.z;wallId=snap.wallId;}
        }
        ElectricalPoint ep=new ElectricalPoint();
        ep.roomId=room.id;ep.type=type;ep.wallId=wallId;ep.manualPosition=true;
        ep.heightFromFinishedFloorM=defaultHeight(type);
        ep.position=new Vec3(x,ep.heightFromFinishedFloorM,z);
        ep.circuitNo=circuitNo==null?0:circuitNo.getIntValue();
        ep.moduleCount=1;ep.cableKey="";
        if(!wallId.isEmpty()){
            PlanWallSegment w=wallPlacement.findWall(plan,wallId);
            if(w!=null)wallPlacement.place(plan,floor,ep,wallId,wallPlacement.fractionFor(w,x,z),ep.heightFromFinishedFloorM);
        }
        floor.points.add(ep);saveAll();map.invalidate();selectPoint(ep);
        mode.setSelectedKey("SELECT");map.setInteractionMode("SELECT");
    }

    private void selectRoom(RoomZone r){
        selectedRoom=r;selectedWall=null;selectedOpening=null;selectedPoint=null;
        roomType.setSelectedKey(r.type.name());
        selectedInfo.setText(String.format(Locale.US,"%s • %.1f м² • manual revision %d",
                r.displayName(),r.areaM2,plan.manualRevision));
        showPanel(roomPanel);updateMapSelection();
    }
    private void selectWall(PlanWallSegment w){
        selectedWall=w;selectedRoom=null;selectedOpening=null;selectedPoint=null;
        wallMaterial.setSelectedKey(w.materialKey);wallThickness.setValue(w.thicknessM);
        selectedInfo.setText(String.format(Locale.US,"Стіна %.2f м • товщина %.2f м • %s",
                w.lengthM(),w.thicknessM,w.manual?"ручна/виправлена":"автоскан"));
        showPanel(wallPanel);updateMapSelection();
    }
    private void selectOpening(ApartmentOpening o){
        selectedOpening=o;selectedRoom=null;selectedWall=null;selectedPoint=null;
        openingType.setSelectedKey(o.type==ApartmentOpening.Type.WINDOW?"WINDOW":"DOOR");
        openingWidth.setValue(o.widthM);
        openingBottom.setValue(o.bottomY);
        openingHeight.setValue(o.heightM>0?o.heightM:2.05);
        selectedInfo.setText((o.type==ApartmentOpening.Type.WINDOW?"Вікно":"Двері")
                +" • "+String.format(Locale.US,"%.2f × %.2f м",o.widthM,o.heightM));
        showPanel(openingPanel);updateMapSelection();
    }
    private void selectPoint(ElectricalPoint p){
        selectedPoint=p;selectedRoom=null;selectedWall=null;selectedOpening=null;
        pointType.setSelectedKey(p.type.name());pointHeight.setValue(p.heightFromFinishedFloorM);
        circuitNo.setValue(p.circuitNo);moduleStrip.setSelectedKey(String.valueOf(Math.max(1,p.moduleCount)));
        cableType.setSelectedKey(p.cableKey==null||p.cableKey.isEmpty()?"AUTO":p.cableKey);
        plannedLoad.setValue(p.plannedLoadW);
        loadPreset.setSelectedKey(presetKey(p.plannedLoadW));
        selectedInfo.setText("Електроточка • "+p.type.name()+" • група "+p.circuitNo
                +" • H="+String.format(Locale.US,"%.2f м",p.heightFromFinishedFloorM)
                +" • "+String.format(Locale.US,"%.0f W",p.plannedLoadW));
        showPanel(pointPanel);updateMapSelection();
    }

    private void applyPoint(){
        if(selectedPoint==null)return;
        try{selectedPoint.type=ElectricalPoint.Type.valueOf(pointType.getSelectedKey());}catch(Exception ignored){}
        selectedPoint.heightFromFinishedFloorM=pointHeight.getValue();
        selectedPoint.position.y=selectedPoint.heightFromFinishedFloorM;
        if(selectedPoint.wallId!=null&&!selectedPoint.wallId.isEmpty()){
            PlanWallSegment w=wallPlacement.findWall(plan,selectedPoint.wallId);
            if(w!=null){double t=selectedPoint.wallFraction>0&&selectedPoint.wallFraction<1?selectedPoint.wallFraction:wallPlacement.fractionFor(w,selectedPoint.position.x,selectedPoint.position.z);wallPlacement.place(plan,floor,selectedPoint,w.id,t,selectedPoint.heightFromFinishedFloorM);}
        }
        selectedPoint.circuitNo=circuitNo.getIntValue();
        selectedPoint.groupId=selectedPoint.circuitNo>0?"G"+selectedPoint.circuitNo:"";
        try{selectedPoint.moduleCount=Integer.parseInt(moduleStrip.getSelectedKey());}catch(Exception ignored){}
        String ck=cableType.getSelectedKey();selectedPoint.cableKey="AUTO".equals(ck)?"":ck;
        selectedPoint.plannedLoadW=plannedLoad.getValue();
        saveAll();map.invalidate();selectPoint(selectedPoint);
    }

    private String presetKey(double w){
        int[] vals={0,100,500,1000,2000,3500,7000};
        int best=0;double d=Double.POSITIVE_INFINITY;
        for(int v:vals){double x=Math.abs(w-v);if(x<d){d=x;best=v;}}
        return String.valueOf(best);
    }

    private void movePointAlongWall(double deltaM){
        if(selectedPoint==null||selectedPoint.wallId==null||selectedPoint.wallId.isEmpty())return;
        PlanWallSegment w=editor.findWall(plan,selectedPoint.wallId);if(w==null)return;
        double dx=w.x2-w.x1,dz=w.z2-w.z1,len=Math.hypot(dx,dz);if(len<1e-6)return;
        dx/=len;dz/=len;
        double nx=selectedPoint.position.x+dx*deltaM,nz=selectedPoint.position.z+dz*deltaM;
        double t=((nx-w.x1)*(w.x2-w.x1)+(nz-w.z1)*(w.z2-w.z1))/(len*len);
        t=Math.max(0.02,Math.min(0.98,t));
        wallPlacement.place(plan,floor,selectedPoint,w.id,t,selectedPoint.heightFromFinishedFloorM);
        saveAll();map.invalidate();selectPoint(selectedPoint);
    }

    private void duplicatePointAlongWall(double deltaM){
        if(selectedPoint==null)return;
        ElectricalPoint copy=new ElectricalPoint();
        copy.roomId=selectedPoint.roomId;copy.type=selectedPoint.type;copy.wallId=selectedPoint.wallId;
        copy.heightFromFinishedFloorM=selectedPoint.heightFromFinishedFloorM;
        copy.position=new Vec3(selectedPoint.position.x,selectedPoint.position.y,selectedPoint.position.z);
        copy.circuitNo=selectedPoint.circuitNo;copy.groupId=selectedPoint.groupId;
        copy.moduleCount=selectedPoint.moduleCount;copy.cableKey=selectedPoint.cableKey;
        copy.plannedLoadW=selectedPoint.plannedLoadW;copy.manualPosition=true;
        copy.lockedToWall=selectedPoint.lockedToWall;copy.wallFraction=selectedPoint.wallFraction;
        copy.wallOffsetM=selectedPoint.wallOffsetM;copy.placementSource=selectedPoint.placementSource;
        floor.points.add(copy);selectedPoint=copy;
        if(copy.wallId!=null&&!copy.wallId.isEmpty())movePointAlongWall(deltaM);
        else{saveAll();map.invalidate();selectPoint(copy);}
    }

    private void moveWall(double delta){
        if(selectedWall==null)return;
        editor.moveWall(plan,selectedWall.id,delta);
        wallPlacement.refreshLockedPoints(plan,floor);
        saveAll();map.invalidate();selectWall(selectedWall);
    }
    private void addOpening(ApartmentOpening.Type t){
        if(selectedWall==null)return;
        ApartmentOpening o=editor.addOpening(plan,selectedWall.id,t,0.5,
                t==ApartmentOpening.Type.DOOR?0.9:1.2,
                t==ApartmentOpening.Type.DOOR?0:0.9,
                t==ApartmentOpening.Type.DOOR?2.05:1.3);
        if(o!=null){saveAll();map.invalidate();selectOpening(o);}
    }

    private void confirmDeleteWall(){
        if(selectedWall==null)return;
        new AlertDialog.Builder(this).setTitle("Видалити стіну?")
                .setMessage("Прив'язані двері/вікна залишаться як окремі об'єкти без прив'язки.")
                .setNegativeButton("Ні",null)
                .setPositiveButton("Так",(d,w)->{
                    editor.removeWall(plan,selectedWall.id);selectedWall=null;saveAll();clearSelection();
                }).show();
    }

    private void confirmAcceptAutoPlan(){
        if(scene==null||scene.latestAutoApartmentPlan==null)return;
        new AlertDialog.Builder(this)
                .setTitle("Замінити ручний план новим автосканом?")
                .setMessage("Старий ручний план буде замінений. Електроточки я спробую переприв'язати до нових кімнат і найближчих стін за їх поточними координатами.")
                .setNegativeButton("Лишити ручний",null)
                .setPositiveButton("Прийняти автоплан",(d,w)->acceptAutoPlan())
                .show();
    }

    private void acceptAutoPlan(){
        if(scene==null||scene.latestAutoApartmentPlan==null)return;
        plan=scene.latestAutoApartmentPlan;
        plan.manualEdited=false;
        plan.manualRevision=0;
        plan.lastManualEditAt=0;
        scene.apartmentPlan=plan;
        scene.latestAutoApartmentPlan=null;

        WallSnapper snapper=new WallSnapper();
        for(ElectricalPoint ep:floor.points){
            RoomZone r=editor.findRoomAt(plan,ep.position.x,ep.position.z);
            ep.roomId=r==null?"":r.id;
            if(ep.type!=ElectricalPoint.Type.LIGHT&&ep.type!=ElectricalPoint.Type.SENSOR){
                WallSnapper.Result s=snapper.snap(plan,ep.position.x,ep.position.z,1.0);
                if(s!=null){
                    ep.position.x=s.x;ep.position.z=s.z;ep.wallId=s.wallId;
                    PlanWallSegment nw=wallPlacement.findWall(plan,s.wallId);
                    if(nw!=null)wallPlacement.place(plan,floor,ep,s.wallId,wallPlacement.fractionFor(nw,s.x,s.z),ep.heightFromFinishedFloorM);
                }else {ep.wallId="";ep.lockedToWall=false;ep.wallFraction=-1;ep.wallOffsetM=-1;}
            }
        }
        syncFloorRooms();
        saveAll();
        map.setData(plan,floor,null);
        clearSelection();
        updateStatus("Новий автоплан прийнято. Перевір точки, двері та стіни перед кошторисом.");
    }

    private void clearSelection(){
        selectedRoom=null;selectedWall=null;selectedOpening=null;selectedPoint=null;
        selectedInfo.setText("Натисни на кімнату, стіну, двері/вікно або електроточку.");
        showPanel(null);updateMapSelection();
    }
    private void showPanel(View active){
        roomPanel.setVisibility(active==roomPanel?View.VISIBLE:View.GONE);
        wallPanel.setVisibility(active==wallPanel?View.VISIBLE:View.GONE);
        openingPanel.setVisibility(active==openingPanel?View.VISIBLE:View.GONE);
        pointPanel.setVisibility(active==pointPanel?View.VISIBLE:View.GONE);
    }
    private void updateMapSelection(){
        map.setSelection(selectedRoom==null?"":selectedRoom.id,selectedWall==null?"":selectedWall.id,
                selectedOpening==null?"":selectedOpening.id,selectedPoint==null?"":selectedPoint.id);
    }

    private void syncFloorRooms(){
        if(scene==null||plan==null)return;
        FloorPlan auto=new AutoFloorPlanBuilder().build(scene);
        floor.rooms.clear();
        for(RoomRect r:auto.rooms)floor.rooms.add(r);
        floor.projectId=projectId;
        floorRepo.save(floor);
    }

    private void saveAll(){
        if(scene!=null)sceneRepo.save(projectId,scene,null);
        if(floor!=null)floorRepo.save(floor);
        if(map!=null)map.setData(plan,floor,null);
    }

    private void updateStatus(String extra){
        if(status==null)return;
        status.setText("Стіни: "+plan.walls.size()+" • зони: "+plan.rooms.size()
                +" • точки: "+floor.points.size()+" • manual rev "+plan.manualRevision
                +(extra==null||extra.isEmpty()?"":"\n"+extra));
    }

    private ElectricalPoint.Type pointTypeFromMode(String m){
        try{
            if("SOCKET".equals(m)||"SWITCH".equals(m)||"LIGHT".equals(m)||"PANEL".equals(m)
                    ||"TV".equals(m)||"ETHERNET".equals(m)||"APPLIANCE".equals(m)
                    ||"CAMERA".equals(m)||"SENSOR".equals(m)||"SMART_HOME".equals(m))
                return ElectricalPoint.Type.valueOf(m);
        }catch(Exception ignored){}
        return null;
    }
    private double defaultHeight(ElectricalPoint.Type t){
        switch(t){
            case SWITCH:return 0.90;case PANEL:return 1.50;case LIGHT:return Math.max(2.5,plan.ceilingY-plan.floorY);
            case CAMERA:return 2.50;case TV:return 0.30;case ETHERNET:return 0.30;default:return 0.30;
        }
    }
    private String modeLabel(String k){
        if("SELECT".equals(k))return "вибір/редагування";
        if("MERGE".equals(k))return "об'єднання кімнат";
        if(k.startsWith("SPLIT"))return "розділення кімнати";
        if("WALL".equals(k))return "додати стіну двома тапами";
        if("FORBIDDEN".equals(k))return "позначити заборонену зону двома тапами";
        return "додати "+k;
    }

    private String[] roomKeys(){
        RoomZone.RoomType[] a=RoomZone.RoomType.values();String[] x=new String[a.length];
        for(int i=0;i<a.length;i++)x[i]=a[i].name();return x;
    }
    private String[] roomLabels(){
        RoomZone.RoomType[] a=RoomZone.RoomType.values();String[] x=new String[a.length];
        for(int i=0;i<a.length;i++)x[i]=a[i].label;return x;
    }

    @Override protected void onPause(){super.onPause();try{saveAll();}catch(Exception e){new StabilityReporter(this).error("EDITOR_SAVE","Auto-save on pause failed",e);}}
}
