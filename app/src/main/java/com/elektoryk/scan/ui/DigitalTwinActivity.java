package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.elektoryk.scan.model.ApartmentOpening;
import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.ElectricalPoint;
import com.elektoryk.scan.model.ElectricalProject;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.FurnitureItem;
import com.elektoryk.scan.model.InstallationRoutingResult;
import com.elektoryk.scan.model.PlanWallSegment;
import com.elektoryk.scan.model.RoomZone;
import com.elektoryk.scan.model.RoomFootprint;
import com.elektoryk.scan.model.RoomFinishCatalog;
import com.elektoryk.scan.model.RoomKitCatalog;
import com.elektoryk.scan.model.SceneSnapshot;
import com.elektoryk.scan.model.VisualAssetPreset;
import com.elektoryk.scan.model.WallFinishCatalog;
import com.elektoryk.scan.reconstruction.InstallationRoutingEngine;
import com.elektoryk.scan.reconstruction.WallPlacementEngine;
import com.elektoryk.scan.storage.FileProjectRepository;
import com.elektoryk.scan.storage.FloorPlanRepository;
import com.elektoryk.scan.storage.SceneRepository;
import com.elektoryk.scan.storage.StabilityReporter;
import com.elektoryk.scan.storage.VisualAssetRepository;

import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Full 3D workspace over the same ApartmentPlan used by 2D/routing.
 * The goal is a field editor, not a decorative preview: select -> inspect -> edit -> undo/redo.
 */
public class DigitalTwinActivity extends Activity implements DigitalTwinView.Listener {
    private String projectId;
    private SceneRepository sceneRepo;
    private FloorPlanRepository floorRepo;
    private SceneSnapshot scene;
    private ApartmentPlan plan;
    private FloorPlan floor;
    private DigitalTwinView twin;
    private final WallPlacementEngine placement=new WallPlacementEngine();

    private ChoiceStrip editMode,cameraMode,renderStyle,pointType,visualStyle,finishType,finishPreset,furnitureType,furnitureStyle,moduleStrip,cableType;
    private ChoiceStrip wallMaterial,wallSurfaceState,wallFinish,wallFinishPreset,openingType;
    private ChoiceStrip roomChoice,roomFloorFinish,roomCeilingFinish,roomSkirting,roomKit,furnitureInspectorStyle;
    private NumberStepper circuitNo,finishThickness,plannedLoad,heightCm,offsetCm;
    private NumberStepper wallThicknessCm,wallFinishMm,wallPatternScale,wallPatternRotation;
    private NumberStepper furnitureWcm,furnitureDcm,furnitureHcm,furnitureRotation;
    private NumberStepper openingWidthCm,openingHeightCm,openingBottomCm,pointArrayCount,pointArraySpacingCm;

    private TextView info,syncInfo,routingInfo,selectionTitle,selectionDetails;
    private LinearLayout placeTools,finishTools,furnitureTools,roomTools,pointPanel,inspector,wallPanel,furniturePanel,openingPanel;
    private Button cutawayButton,ceilingButton,gridButton,furnitureButton,pointsButton,undoButton,redoButton,furnitureLockButton;
    private EditText wallColorHex;

    private ElectricalPoint selectedPoint;
    private PlanWallSegment selectedWall;
    private FurnitureItem selectedFurniture;
    private ApartmentOpening selectedOpening;

    private boolean cutaway=true,showCeiling=false,showGrid=true,showFurniture=true,showPoints=true;
    private int changeCounter=0;
    private final ArrayDeque<String> undoStack=new ArrayDeque<>();
    private final ArrayDeque<String> redoStack=new ArrayDeque<>();
    private String gestureSnapshot=null;
    private static final int MAX_HISTORY=30;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        projectId=getIntent().getStringExtra("projectId");
        sceneRepo=new SceneRepository(this);floorRepo=new FloorPlanRepository(this);
        scene=sceneRepo.loadScene(projectId);plan=scene==null?null:scene.apartmentPlan;
        floor=floorRepo.load(projectId);if(floor==null){floor=new FloorPlan();floor.projectId=projectId;}
        migrateWallLocks();buildUi();
    }

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,10),Ui.dp(this,10),Ui.dp(this,10),Ui.dp(this,30));scroll.addView(root);
        root.addView(Ui.brandHeader(this,"ELEKTORYK • 3D Editor","Digital Twin • одна модель з 2D, трасами й кошторисом","3D",true));
        root.addView(Ui.screenIntro(this,"TWIN WORKSPACE","Нормальний 3D перегляд і редактор","Orbit камерою, вибір реальних сутностей, точні параметри, матеріали, меблі, електрика, undo/redo. Ніякого окремого декоративного дубля — редагується сам ApartmentPlan."),Ui.lpMatchWrap(this,8));

        if(plan==null||plan.walls.isEmpty()){
            root.addView(Ui.title(this,"Ще немає придатної 3D-геометрії",19),Ui.lpMatchWrap(this,14));
            root.addView(Ui.muted(this,"Спочатку зроби Smart Scan. 3D Editor працює тільки поверх реконструйованого ApartmentPlan."));
            Button scan=Ui.button(this,"Почати Smart Scan");scan.setOnClickListener(v->open(ArCoreScanActivity.class));root.addView(scan,Ui.lpMatchWrap(this,12));setContentView(scroll);return;
        }

        LinearLayout cameraCard=Ui.softCard(this);cameraCard.addView(Ui.title(this,"Камера",16));
        cameraMode=new ChoiceStrip(this);cameraMode.setItems(new String[]{"ORBIT","WALK","TOP"},new String[]{"Orbit","Walk","Зверху"},"ORBIT");cameraMode.setListener(k->{twin.setCameraMode(k);info.setText(cameraHint(k));});cameraCard.addView(cameraMode,Ui.lpMatchWrap(this,3));
        renderStyle=new ChoiceStrip(this);renderStyle.setItems(new String[]{"PHOTO","SOLID","WIRE"},new String[]{"Фото","Суцільний","Каркас"},"PHOTO");renderStyle.setListener(k->twin.setRenderStyle(k));cameraCard.addView(renderStyle,Ui.lpMatchWrap(this,4));
        LinearLayout toggles=new LinearLayout(this);toggles.setOrientation(LinearLayout.HORIZONTAL);
        cutawayButton=smallToggle("Зріз ✓");ceilingButton=smallToggle("Стеля —");gridButton=smallToggle("Сітка ✓");
        cutawayButton.setOnClickListener(v->{cutaway=!cutaway;twin.setCutaway(cutaway);cutawayButton.setText(cutaway?"Зріз ✓":"Зріз —");});
        ceilingButton.setOnClickListener(v->{showCeiling=!showCeiling;twin.setShowCeiling(showCeiling);ceilingButton.setText(showCeiling?"Стеля ✓":"Стеля —");});
        gridButton.setOnClickListener(v->{showGrid=!showGrid;twin.setShowGrid(showGrid);gridButton.setText(showGrid?"Сітка ✓":"Сітка —");});
        toggles.addView(cutawayButton,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));toggles.addView(ceilingButton,weighted(1,5));toggles.addView(gridButton,weighted(1,5));cameraCard.addView(toggles,Ui.lpMatchWrap(this,5));
        LinearLayout visible=new LinearLayout(this);visible.setOrientation(LinearLayout.HORIZONTAL);
        furnitureButton=smallToggle("Меблі ✓");pointsButton=smallToggle("Точки ✓");Button frame=smallToggle("Показати все");
        furnitureButton.setOnClickListener(v->{showFurniture=!showFurniture;twin.setShowFurniture(showFurniture);furnitureButton.setText(showFurniture?"Меблі ✓":"Меблі —");});
        pointsButton.setOnClickListener(v->{showPoints=!showPoints;twin.setShowElectrical(showPoints);pointsButton.setText(showPoints?"Точки ✓":"Точки —");});
        frame.setOnClickListener(v->twin.frameAll());visible.addView(furnitureButton,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));visible.addView(pointsButton,weighted(1,5));visible.addView(frame,weighted(1,5));cameraCard.addView(visible,Ui.lpMatchWrap(this,5));root.addView(cameraCard,Ui.lpMatchWrap(this,8));

        editMode=new ChoiceStrip(this);editMode.setItems(new String[]{"SELECT","PLACE","FINISH","FURNITURE"},new String[]{"Вибір","Електрика","Оздоблення","Додати об'єкт"},"SELECT");editMode.setListener(k->{twin.setMode(k);showTools(k);info.setText(modeHint(k));});root.addView(editMode,Ui.lpMatchWrap(this,8));
        buildPlaceTools(root);buildFinishTools(root);buildFurnitureTools(root);buildRoomTools(root);

        twin=new DigitalTwinView(this);twin.setProjectId(projectId);twin.setData(plan,floor);twin.setListener(this);twin.setMode("SELECT");twin.setCameraMode("ORBIT");twin.setRenderStyle("PHOTO");twin.setCutaway(true);twin.setPlaceOnCeiling(false);
        root.addView(twin,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,620)));

        LinearLayout viewActions=new LinearLayout(this);viewActions.setOrientation(LinearLayout.HORIZONTAL);
        Button frameAll=Ui.secondaryButton(this,"◎ Все");Button focus=Ui.secondaryButton(this,"⌖ Вибране");Button zoomIn=Ui.secondaryButton(this,"＋");Button zoomOut=Ui.secondaryButton(this,"−");
        frameAll.setOnClickListener(v->twin.frameAll());focus.setOnClickListener(v->focusSelected());zoomIn.setOnClickListener(v->twin.moveForward());zoomOut.setOnClickListener(v->twin.moveBack());
        viewActions.addView(frameAll,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));viewActions.addView(focus,weighted(1,5));viewActions.addView(zoomIn,weighted(0.55f,5));viewActions.addView(zoomOut,weighted(0.55f,5));root.addView(viewActions,Ui.lpMatchWrap(this,6));

        LinearLayout history=new LinearLayout(this);history.setOrientation(LinearLayout.HORIZONTAL);undoButton=Ui.secondaryButton(this,"↶ Undo");redoButton=Ui.secondaryButton(this,"↷ Redo");undoButton.setOnClickListener(v->undo());redoButton.setOnClickListener(v->redo());history.addView(undoButton,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));history.addView(redoButton,weighted(1,6));root.addView(history,Ui.lpMatchWrap(this,6));updateHistoryButtons();

        info=Ui.muted(this,modeHint("SELECT"));root.addView(info,Ui.lpMatchWrap(this,8));
        syncInfo=Ui.muted(this,"");root.addView(syncInfo,Ui.lpMatchWrap(this,4));routingInfo=Ui.muted(this,"");root.addView(routingInfo,Ui.lpMatchWrap(this,4));refreshSyncLabel();refreshRoutingPreview();

        buildInspector(root);showTools("SELECT");showInspectorNone();

        LinearLayout fast=new LinearLayout(this);fast.setOrientation(LinearLayout.HORIZONTAL);Button edit2d=Ui.button(this,"▦ 2D геометрія");Button routes=Ui.button(this,"⚡ Траси");edit2d.setOnClickListener(v->{saveAll();open(ApartmentEditorActivity.class);});routes.setOnClickListener(v->{saveAll();open(RoutingActivity.class);});fast.addView(edit2d,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));fast.addView(routes,weighted(1,7));root.addView(fast,Ui.lpMatchWrap(this,12));
        LinearLayout extra=new LinearLayout(this);extra.setOrientation(LinearLayout.HORIZONTAL);Button raw=Ui.secondaryButton(this,"Point cloud");Button lib=Ui.secondaryButton(this,"▣ Моделі");raw.setOnClickListener(v->open(ScenePreviewActivity.class));lib.setOnClickListener(v->open(AssetLibraryActivity.class));extra.addView(raw,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));extra.addView(lib,weighted(1,7));root.addView(extra,Ui.lpMatchWrap(this,7));
        root.addView(Ui.muted(this,"Жести: Orbit — 1 палець обертання, pinch zoom, 2 пальці pan. У режимі меблів затисни об'єкт і тягни по підлозі. Тап по сутності відкриває точний інспектор."),Ui.lpMatchWrap(this,10));
        root.addView(Ui.bottomNav(this,projectId,"ELECTRIC"),Ui.lpMatchWrap(this,16));setContentView(scroll);
    }

    private Button smallToggle(String text){Button b=Ui.secondaryButton(this,text);b.setTextSize(12);return b;}

    private void buildPlaceTools(LinearLayout root){
        placeTools=Ui.softCard(this);placeTools.addView(Ui.title(this,"Поставити електроточку",16));
        pointType=new ChoiceStrip(this);pointType.setItems(new String[]{"SOCKET","SWITCH","LIGHT","TV","ETHERNET","APPLIANCE","CAMERA","SENSOR","SMART_HOME","PANEL"},new String[]{"Розетка","Вимикач","Світло","TV","LAN","Техніка","Камера","Датчик","Smart","Щит"},"SOCKET");placeTools.addView(pointType,Ui.lpMatchWrap(this,4));
        visualStyle=new ChoiceStrip(this);placeTools.addView(visualStyle,Ui.lpMatchWrap(this,4));pointType.setListener(k->refreshVisualStyles(k,defaultAssetFor(k)));refreshVisualStyles("SOCKET","SOCKET_FRAME_1");
        circuitNo=new NumberStepper(this,"група");circuitNo.configure(0,64,1,0);placeTools.addView(circuitNo,Ui.lpMatchWrap(this,4));root.addView(placeTools,Ui.lpMatchWrap(this,7));
    }
    private void buildFinishTools(LinearLayout root){
        finishTools=Ui.softCard(this);finishTools.addView(Ui.title(this,"Матеріал / оздоблення",16));
        finishPreset=new ChoiceStrip(this);finishPreset.setItems(WallFinishCatalog.keys(),WallFinishCatalog.labels(),"PAINT_WHITE");finishTools.addView(finishPreset,Ui.lpMatchWrap(this,4));
        finishType=new ChoiceStrip(this);finishType.setItems(new String[]{"RAW","PLASTER","PUTTY","PAINT","TILE","DRYWALL","DECOR"},new String[]{"Чернова","Штукатурка","Шпаклівка","Фарба","Плитка","Гіпсокартон","Декор"},"PAINT");finishTools.addView(finishType,Ui.lpMatchWrap(this,4));
        finishThickness=new NumberStepper(this,"м шар");finishThickness.configure(0,0.10,0.001,3);finishThickness.setValue(0.001);finishTools.addView(finishThickness,Ui.lpMatchWrap(this,4));
        finishPreset.setListener(k->{WallFinishCatalog.Preset fp=WallFinishCatalog.find(k);finishType.setSelectedKey(fp.finishKey);finishThickness.setValue(fp.thicknessM);});
        finishType.setListener(k->finishThickness.setValue(defaultFinishThickness(k)));
        LinearLayout quick=new LinearLayout(this);quick.setOrientation(LinearLayout.HORIZONTAL);Button all=Ui.secondaryButton(this,"На всі стіни");Button scan=Ui.secondaryButton(this,"Повернути RAW");all.setOnClickListener(v->applyQuickFinishAll());scan.setOnClickListener(v->{finishPreset.setSelectedKey("RAW_SCAN");finishType.setSelectedKey("RAW");finishThickness.setValue(0);});quick.addView(all,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));quick.addView(scan,weighted(1,6));finishTools.addView(quick,Ui.lpMatchWrap(this,5));
        root.addView(finishTools,Ui.lpMatchWrap(this,7));
    }
    private void buildFurnitureTools(LinearLayout root){
        furnitureTools=Ui.softCard(this);furnitureTools.addView(Ui.title(this,"Додати меблі / об'єкт",16));furnitureType=new ChoiceStrip(this);furnitureType.setItems(new String[]{"CABINET","NIGHTSTAND","KITCHEN","WARDROBE","BED","SOFA","TABLE","CHAIR","DESK","TV","FRIDGE","WASHER","OVEN","SINK","TOILET","BATHTUB","SHOWER","RADIATOR","MIRROR","SHELF","APPLIANCE","SANITARY","CUSTOM"},new String[]{"Тумба","Приліжкова","Кухня","Шафа","Ліжко","Диван","Стіл","Стілець","Робочий стіл","Телевізор","Холодильник","Пральна","Духовка","Умивальник","Унітаз","Ванна","Душ","Радіатор","Дзеркало","Полиця","Техніка","Сантехніка","Об'єкт"},"CABINET");furnitureTools.addView(furnitureType,Ui.lpMatchWrap(this,4));furnitureStyle=new ChoiceStrip(this);furnitureTools.addView(furnitureStyle,Ui.lpMatchWrap(this,4));furnitureType.setListener(k->refreshFurnitureStyles(k,defaultFurnitureAsset(k)));refreshFurnitureStyles("CABINET","CABINET_STD");furnitureTools.addView(Ui.muted(this,"Тап по підлозі — додати. Тап по існуючому — вибрати; затисни й тягни — перемістити з прив'язкою 5 см."),Ui.lpMatchWrap(this,4));root.addView(furnitureTools,Ui.lpMatchWrap(this,7));
    }
    private void buildRoomTools(LinearLayout root){
        roomTools=Ui.softCard(this);roomTools.addView(Ui.title(this,"Приміщення • оздоблення й готові сети",16));
        roomChoice=new ChoiceStrip(this);refreshRoomChoice();roomTools.addView(roomChoice,Ui.lpMatchWrap(this,4));
        roomFloorFinish=new ChoiceStrip(this);roomFloorFinish.setItems(RoomFinishCatalog.floorKeys(),RoomFinishCatalog.floorLabels(),"FLOOR_RAW");roomTools.addView(roomFloorFinish,Ui.lpMatchWrap(this,4));
        roomCeilingFinish=new ChoiceStrip(this);roomCeilingFinish.setItems(RoomFinishCatalog.ceilingKeys(),RoomFinishCatalog.ceilingLabels(),"CEILING_RAW");roomTools.addView(roomCeilingFinish,Ui.lpMatchWrap(this,4));
        roomSkirting=new ChoiceStrip(this);roomSkirting.setItems(RoomFinishCatalog.SKIRT_KEYS,RoomFinishCatalog.SKIRT_LABELS,"SKIRT_NONE");roomTools.addView(roomSkirting,Ui.lpMatchWrap(this,4));
        LinearLayout surf=new LinearLayout(this);surf.setOrientation(LinearLayout.HORIZONTAL);Button applyRoom=Ui.secondaryButton(this,"До кімнати");Button applyAll=Ui.secondaryButton(this,"До всіх кімнат");applyRoom.setOnClickListener(v->applyRoomFinishes(false));applyAll.setOnClickListener(v->applyRoomFinishes(true));surf.addView(applyRoom,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));surf.addView(applyAll,weighted(1,5));roomTools.addView(surf,Ui.lpMatchWrap(this,5));
        roomKit=new ChoiceStrip(this);roomKit.setItems(RoomKitCatalog.keys(),RoomKitCatalog.labels(),RoomKitCatalog.keys()[0]);roomTools.addView(roomKit,Ui.lpMatchWrap(this,6));
        LinearLayout kits=new LinearLayout(this);kits.setOrientation(LinearLayout.HORIZONTAL);Button placeKit=Ui.button(this,"+ Додати сет");Button clear=Ui.secondaryButton(this,"Очистити меблі кімнати");placeKit.setOnClickListener(v->applyRoomKit());clear.setOnClickListener(v->clearRoomFurniture());kits.addView(placeKit,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));kits.addView(clear,weighted(1,5));roomTools.addView(kits,Ui.lpMatchWrap(this,5));
        roomTools.addView(Ui.muted(this,"Сет додається як редаговані легкі об'єкти. Після вставки кожен предмет можна замінити, пересунути, масштабувати або заблокувати."),Ui.lpMatchWrap(this,4));loadRoomFinishControls();root.addView(roomTools,Ui.lpMatchWrap(this,7));
    }

    private void buildInspector(LinearLayout root){
        inspector=Ui.card(this);selectionTitle=Ui.title(this,"Вибрано",18);selectionDetails=Ui.muted(this,"");inspector.addView(selectionTitle);inspector.addView(selectionDetails,Ui.lpMatchWrap(this,3));
        Button focus=Ui.secondaryButton(this,"⌖ Сфокусувати камеру");focus.setOnClickListener(v->focusSelected());inspector.addView(focus,Ui.lpMatchWrap(this,7));
        buildWallPanel();buildFurniturePanel();buildOpeningPanel();buildPointPanel();inspector.addView(wallPanel,Ui.lpMatchWrap(this,6));inspector.addView(furniturePanel,Ui.lpMatchWrap(this,6));inspector.addView(openingPanel,Ui.lpMatchWrap(this,6));inspector.addView(pointPanel,Ui.lpMatchWrap(this,6));root.addView(inspector,Ui.lpMatchWrap(this,10));
    }
    private void buildWallPanel(){
        wallPanel=Ui.softCard(this);wallPanel.addView(Ui.title(this,"Стіна",16));
        wallThicknessCm=new NumberStepper(this,"см товщина");wallThicknessCm.configure(3,100,1,1);wallPanel.addView(wallThicknessCm,Ui.lpMatchWrap(this,4));
        wallMaterial=new ChoiceStrip(this);wallMaterial.setItems(new String[]{"UNKNOWN","BRICK","CONCRETE","BLOCK","DRYWALL","WOOD"},new String[]{"Невідомо","Цегла","Бетон","Блок","Гіпс","Дерево"},"UNKNOWN");wallPanel.addView(wallMaterial,Ui.lpMatchWrap(this,4));
        wallSurfaceState=new ChoiceStrip(this);wallSurfaceState.setItems(WallFinishCatalog.SURFACE_KEYS,WallFinishCatalog.SURFACE_LABELS,"AS_SCANNED");wallPanel.addView(wallSurfaceState,Ui.lpMatchWrap(this,4));
        wallFinishPreset=new ChoiceStrip(this);wallFinishPreset.setItems(WallFinishCatalog.keys(),WallFinishCatalog.labels(),"RAW_SCAN");wallPanel.addView(wallFinishPreset,Ui.lpMatchWrap(this,4));
        wallFinishPreset.setListener(k->{WallFinishCatalog.Preset fp=WallFinishCatalog.find(k);if(wallFinish!=null)wallFinish.setSelectedKey(fp.finishKey);if(wallFinishMm!=null)wallFinishMm.setValue(fp.thicknessM*1000);});
        wallFinish=new ChoiceStrip(this);wallFinish.setItems(new String[]{"RAW","PLASTER","PUTTY","PAINT","TILE","DRYWALL","DECOR"},new String[]{"RAW","Штукатурка","Шпаклівка","Фарба","Плитка","Гіпсокартон","Декор"},"RAW");wallPanel.addView(wallFinish,Ui.lpMatchWrap(this,4));
        wallFinishMm=new NumberStepper(this,"мм шар");wallFinishMm.configure(0,100,1,1);wallPanel.addView(wallFinishMm,Ui.lpMatchWrap(this,4));
        wallPatternScale=new NumberStepper(this,"× масштаб текстури");wallPatternScale.configure(.2,8.0,.1,1);wallPatternScale.setValue(1.0);wallPanel.addView(wallPatternScale,Ui.lpMatchWrap(this,4));
        wallPatternRotation=new NumberStepper(this,"° поворот текстури");wallPatternRotation.configure(-180,180,5,0);wallPanel.addView(wallPatternRotation,Ui.lpMatchWrap(this,4));
        wallColorHex=new EditText(this);wallColorHex.setHint("Колір #RRGGBB (напр. #EAE6DD)");wallColorHex.setSingleLine(true);wallColorHex.setTextColor(Ui.TEXT);wallColorHex.setHintTextColor(Ui.MUTED);wallColorHex.setBackgroundColor(Ui.CARD_SOFT);wallColorHex.setPadding(Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10),Ui.dp(this,8));wallPanel.addView(wallColorHex,Ui.lpMatchWrap(this,4));
        Button customColor=Ui.secondaryButton(this,"Застосувати свій колір");customColor.setOnClickListener(v->applyCustomWallColor());wallPanel.addView(customColor,Ui.lpMatchWrap(this,4));
        Button apply=Ui.button(this,"Застосувати до стіни");apply.setOnClickListener(v->applyWallInspector());wallPanel.addView(apply,Ui.lpMatchWrap(this,6));
        Button applyAll=Ui.secondaryButton(this,"Застосувати це оздоблення до всіх стін");applyAll.setOnClickListener(v->applySelectedWallFinishAll());wallPanel.addView(applyAll,Ui.lpMatchWrap(this,5));
        Button geom=Ui.secondaryButton(this,"Редагувати контур у 2D");geom.setOnClickListener(v->{saveAll();open(ApartmentEditorActivity.class);});wallPanel.addView(geom,Ui.lpMatchWrap(this,5));
    }
    private void buildFurniturePanel(){
        furniturePanel=Ui.softCard(this);furniturePanel.addView(Ui.title(this,"Об'єкт / меблі",16));
        furnitureInspectorStyle=new ChoiceStrip(this);furniturePanel.addView(furnitureInspectorStyle,Ui.lpMatchWrap(this,4));
        furnitureWcm=new NumberStepper(this,"см ширина");furnitureWcm.configure(10,1000,5,0);furniturePanel.addView(furnitureWcm,Ui.lpMatchWrap(this,4));
        furnitureDcm=new NumberStepper(this,"см глибина");furnitureDcm.configure(10,1000,5,0);furniturePanel.addView(furnitureDcm,Ui.lpMatchWrap(this,4));
        furnitureHcm=new NumberStepper(this,"см висота");furnitureHcm.configure(10,500,5,0);furniturePanel.addView(furnitureHcm,Ui.lpMatchWrap(this,4));
        furnitureRotation=new NumberStepper(this,"° поворот");furnitureRotation.configure(0,359,5,0);furniturePanel.addView(furnitureRotation,Ui.lpMatchWrap(this,4));
        LinearLayout moveX=new LinearLayout(this);moveX.setOrientation(LinearLayout.HORIZONTAL);Button xm=Ui.secondaryButton(this,"X −5см");Button xp=Ui.secondaryButton(this,"X +5см");Button zm=Ui.secondaryButton(this,"Z −5см");Button zp=Ui.secondaryButton(this,"Z +5см");xm.setOnClickListener(v->nudgeFurniture(-0.05,0));xp.setOnClickListener(v->nudgeFurniture(0.05,0));zm.setOnClickListener(v->nudgeFurniture(0,-0.05));zp.setOnClickListener(v->nudgeFurniture(0,0.05));moveX.addView(xm,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));moveX.addView(xp,weighted(1,4));moveX.addView(zm,weighted(1,4));moveX.addView(zp,weighted(1,4));furniturePanel.addView(moveX,Ui.lpMatchWrap(this,5));
        LinearLayout rotate=new LinearLayout(this);rotate.setOrientation(LinearLayout.HORIZONTAL);Button rl=Ui.secondaryButton(this,"↶ 15°");Button apply=Ui.button(this,"Застосувати");Button rr=Ui.secondaryButton(this,"15° ↷");rl.setOnClickListener(v->rotateFurniture(-15));rr.setOnClickListener(v->rotateFurniture(15));apply.setOnClickListener(v->applyFurnitureInspector());rotate.addView(rl,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));rotate.addView(apply,weighted(1,5));rotate.addView(rr,weighted(1,5));furniturePanel.addView(rotate,Ui.lpMatchWrap(this,5));
        Button snapWall=Ui.secondaryButton(this,"Притиснути до найближчої стіни");snapWall.setOnClickListener(v->snapSelectedFurnitureToWall());furniturePanel.addView(snapWall,Ui.lpMatchWrap(this,5));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);Button duplicate=Ui.secondaryButton(this,"⧉ Дублювати");furnitureLockButton=Ui.secondaryButton(this,"🔓 Не заблоковано");duplicate.setOnClickListener(v->duplicateSelectedFurniture());furnitureLockButton.setOnClickListener(v->toggleFurnitureLock());actions.addView(duplicate,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));actions.addView(furnitureLockButton,weighted(1,5));furniturePanel.addView(actions,Ui.lpMatchWrap(this,5));
        Button replace=Ui.secondaryButton(this,"Замінити модель без зміни позиції");replace.setOnClickListener(v->replaceSelectedFurnitureAsset());furniturePanel.addView(replace,Ui.lpMatchWrap(this,5));
        Button del=Ui.secondaryButton(this,"Видалити об'єкт");del.setOnClickListener(v->deleteSelectedFurniture());furniturePanel.addView(del,Ui.lpMatchWrap(this,5));
    }
    private void buildOpeningPanel(){
        openingPanel=Ui.softCard(this);openingPanel.addView(Ui.title(this,"Двері / вікно / прохід",16));openingType=new ChoiceStrip(this);openingType.setItems(new String[]{"DOOR","WINDOW","PASSAGE","UNKNOWN"},new String[]{"Двері","Вікно","Прохід","Отвір"},"UNKNOWN");openingPanel.addView(openingType,Ui.lpMatchWrap(this,4));
        openingWidthCm=new NumberStepper(this,"см ширина");openingWidthCm.configure(20,1000,1,0);openingPanel.addView(openingWidthCm,Ui.lpMatchWrap(this,4));openingHeightCm=new NumberStepper(this,"см висота");openingHeightCm.configure(20,500,1,0);openingPanel.addView(openingHeightCm,Ui.lpMatchWrap(this,4));openingBottomCm=new NumberStepper(this,"см від підлоги");openingBottomCm.configure(0,400,1,0);openingPanel.addView(openingBottomCm,Ui.lpMatchWrap(this,4));
        Button apply=Ui.button(this,"Застосувати отвір");apply.setOnClickListener(v->applyOpeningInspector());openingPanel.addView(apply,Ui.lpMatchWrap(this,6));Button del=Ui.secondaryButton(this,"Видалити отвір");del.setOnClickListener(v->deleteSelectedOpening());openingPanel.addView(del,Ui.lpMatchWrap(this,5));
    }
    private void buildPointPanel(){
        pointPanel=Ui.softCard(this);pointPanel.addView(Ui.title(this,"Електроточка",16));moduleStrip=new ChoiceStrip(this);moduleStrip.setItems(new String[]{"1","2","3","4","5","6"},new String[]{"×1","×2","×3","×4","×5","×6"},"1");pointPanel.addView(moduleStrip,Ui.lpMatchWrap(this,4));cableType=new ChoiceStrip(this);cableType.setItems(new String[]{"AUTO","3x1.5","3x2.5","3x4","3x6","5x6"},new String[]{"Авто","3×1.5","3×2.5","3×4","3×6","5×6"},"AUTO");pointPanel.addView(cableType,Ui.lpMatchWrap(this,4));heightCm=new NumberStepper(this,"см від підлоги");heightCm.configure(0,500,1,0);pointPanel.addView(heightCm,Ui.lpMatchWrap(this,4));offsetCm=new NumberStepper(this,"см по стіні");offsetCm.configure(0,3000,1,0);pointPanel.addView(offsetCm,Ui.lpMatchWrap(this,4));plannedLoad=new NumberStepper(this,"W навантаження");plannedLoad.configure(0,20000,100,0);pointPanel.addView(plannedLoad,Ui.lpMatchWrap(this,4));pointArrayCount=new NumberStepper(this,"кількість у масиві");pointArrayCount.configure(2,20,1,0);pointArrayCount.setValue(4);pointPanel.addView(pointArrayCount,Ui.lpMatchWrap(this,4));pointArraySpacingCm=new NumberStepper(this,"см крок масиву");pointArraySpacingCm.configure(5,500,5,0);pointArraySpacingCm.setValue(60);pointPanel.addView(pointArraySpacingCm,Ui.lpMatchWrap(this,4));Button array=Ui.secondaryButton(this,"Створити масив по стіні");array.setOnClickListener(v->createPointArray());pointPanel.addView(array,Ui.lpMatchWrap(this,5));Button apply=Ui.button(this,"Застосувати точку");apply.setOnClickListener(v->applyPointFields());pointPanel.addView(apply,Ui.lpMatchWrap(this,6));Button del=Ui.secondaryButton(this,"Видалити точку");del.setOnClickListener(v->deleteSelectedPoint());pointPanel.addView(del,Ui.lpMatchWrap(this,5));
    }

    private void showTools(String k){if(placeTools==null)return;placeTools.setVisibility("PLACE".equals(k)?View.VISIBLE:View.GONE);finishTools.setVisibility("FINISH".equals(k)?View.VISIBLE:View.GONE);furnitureTools.setVisibility("FURNITURE".equals(k)?View.VISIBLE:View.GONE);}
    private void showInspectorNone(){selectedWall=null;selectedPoint=null;selectedFurniture=null;selectedOpening=null;if(inspector!=null){selectionTitle.setText("Нічого не вибрано");selectionDetails.setText("Тапни стіну, отвір, меблі або електроточку в 3D.");wallPanel.setVisibility(View.GONE);furniturePanel.setVisibility(View.GONE);openingPanel.setVisibility(View.GONE);pointPanel.setVisibility(View.GONE);}if(twin!=null)twin.clearSelection();}
    private void showOnly(LinearLayout panel){wallPanel.setVisibility(panel==wallPanel?View.VISIBLE:View.GONE);furniturePanel.setVisibility(panel==furniturePanel?View.VISIBLE:View.GONE);openingPanel.setVisibility(panel==openingPanel?View.VISIBLE:View.GONE);pointPanel.setVisibility(panel==pointPanel?View.VISIBLE:View.GONE);}

    @Override public void onWallSelected(PlanWallSegment wall){selectedWall=wall;selectedPoint=null;selectedFurniture=null;selectedOpening=null;twin.setSelection(wall.id,"");selectionTitle.setText("Стіна");selectionDetails.setText(String.format(Locale.US,"Довжина %.2f м • товщина %.0f мм • confidence %.0f%% • texture %.0f%%",wall.lengthM(),wall.thicknessM*1000,wall.confidence*100,wall.scannedTextureCoverage*100));wallThicknessCm.setValue(wall.thicknessM*100);wallMaterial.setSelectedKey(wall.materialKey==null?"UNKNOWN":wall.materialKey);wallSurfaceState.setSelectedKey(wall.surfaceStateKey==null?"AS_SCANNED":wall.surfaceStateKey);wallFinishPreset.setSelectedKey(wall.finishPresetKey==null?"RAW_SCAN":wall.finishPresetKey);wallFinish.setSelectedKey(wall.finishKey==null?"RAW":wall.finishKey);wallFinishMm.setValue(wall.finishThicknessM*1000);wallPatternScale.setValue(wall.finishPatternScale);wallPatternRotation.setValue(wall.finishPatternRotationDeg);if(wallColorHex!=null&&wall.finishColorArgb!=0)wallColorHex.setText(String.format(Locale.US,"#%06X",wall.finishColorArgb&0xFFFFFF));else if(wallColorHex!=null)wallColorHex.setText("");showOnly(wallPanel);info.setText("Стіна вибрана. Редагуй властивості в інспекторі; геометричні вершини — у 2D редакторі.");}
    @Override public void onPointSelected(ElectricalPoint point){selectPoint(point);}
    @Override public void onOpeningSelected(ApartmentOpening opening){selectedOpening=opening;selectedWall=null;selectedPoint=null;selectedFurniture=null;twin.setOpeningSelection(opening.id);selectionTitle.setText(openingName(opening));selectionDetails.setText(String.format(Locale.US,"%.2f × %.2f м • низ %.2f м • confidence %.0f%%",opening.widthM,opening.heightM,opening.bottomY,opening.confidence*100));openingType.setSelectedKey(opening.type.name());openingWidthCm.setValue(Math.max(20,opening.widthM*100));openingHeightCm.setValue(Math.max(20,opening.heightM*100));openingBottomCm.setValue(Math.max(0,opening.bottomY*100));showOnly(openingPanel);info.setText("Отвір вибрано. Тип і розміри редагуються без руйнування стіни.");}
    @Override public void onFurnitureSelected(FurnitureItem furniture){selectedFurniture=furniture;selectedWall=null;selectedPoint=null;selectedOpening=null;twin.setFurnitureSelection(furniture.id);selectionTitle.setText(furnitureName(furniture));selectionDetails.setText(String.format(Locale.US,"%.2f × %.2f × %.2f м • X %.2f • Z %.2f • %.0f°",furniture.widthM,furniture.depthM,furniture.heightM,furniture.x,furniture.z,furniture.rotationDeg));refreshFurnitureInspectorStyles(furniture.type.name(),furniture.assetKey);furnitureWcm.setValue(furniture.widthM*100);furnitureDcm.setValue(furniture.depthM*100);furnitureHcm.setValue(furniture.heightM*100);furnitureRotation.setValue(normalizeDeg(furniture.rotationDeg));if(furnitureLockButton!=null)furnitureLockButton.setText(furniture.locked?"🔒 Заблоковано":"🔓 Не заблоковано");showOnly(furniturePanel);info.setText(furniture.locked?"Об'єкт заблокований від випадкового drag. Розблокуй в інспекторі для переміщення.":"Об'єкт вибрано. Його можна перетягувати прямо в 3D або редагувати точно нижче.");}

    @Override public void onPlacePoint(PlanWallSegment wall,double fraction,double heightM){pushUndo();ElectricalPoint e=new ElectricalPoint();try{e.type=ElectricalPoint.Type.valueOf(pointType.getSelectedKey());}catch(Exception ignored){}e.circuitNo=circuitNo.getIntValue();e.groupId=e.circuitNo>0?"G"+e.circuitNo:"";e.assetKey=visualStyle.getSelectedKey();e.moduleCount=modulesForAsset(e.assetKey);double[] snap=snapWall(wall,fraction,heightM);if(!placement.place(plan,floor,e,wall.id,snap[0],snap[1]))return;floor.points.add(e);selectedWall=wall;selectedPoint=e;markChanged(true);selectPoint(e);}
    @Override public void onPlaceFreePoint(double x,double z,RoomZone room,boolean ceiling){pushUndo();ElectricalPoint e=new ElectricalPoint();e.type=ElectricalPoint.Type.LIGHT;e.assetKey=visualStyle.getSelectedKey();e.roomId=room.id;e.position=new com.elektoryk.scan.model.Vec3(x,0,z);e.heightFromFinishedFloorM=Math.max(0,(plan.ceilingY-plan.floorY));e.position.y=e.heightFromFinishedFloorM;e.circuitNo=circuitNo.getIntValue();e.groupId=e.circuitNo>0?"G"+e.circuitNo:"";e.manualPosition=true;e.placementSource="DIGITAL_TWIN_CEILING";floor.points.add(e);selectedPoint=e;markChanged(true);selectPoint(e);}
    @Override public void onDragPoint(ElectricalPoint point,PlanWallSegment wall,double fraction,double heightM,boolean finished){double[] snap=snapWall(wall,fraction,heightM);if(placement.place(plan,floor,point,wall.id,snap[0],snap[1])){selectedPoint=point;selectedWall=wall;showMetrics(point);if(finished)markChanged(true);else twin.setData(plan,floor);}}
    @Override public void onDragFurniture(FurnitureItem furniture,double x,double z,boolean finished){if(furniture.locked)return;placeFurnitureSafely(furniture,x,z);selectedFurniture=furniture;if(finished){markPlanEdited();markChanged(true);onFurnitureSelected(furniture);}else{twin.setData(plan,floor);selectionDetails.setText(String.format(Locale.US,"X %.2f • Z %.2f",furniture.x,furniture.z));}}
    @Override public void onFinishWall(PlanWallSegment wall){pushUndo();applyFinishPreset(wall,finishPreset==null?"RAW_SCAN":finishPreset.getSelectedKey());wall.finishKey=finishType.getSelectedKey();wall.finishThicknessM=finishThickness.getValue();markPlanEdited();selectedWall=wall;markChanged(true);onWallSelected(wall);}
    @Override public void onPlaceFurniture(double x,double z,RoomZone room){pushUndo();FurnitureItem f=new FurnitureItem();try{f.type=FurnitureItem.Type.valueOf(furnitureType.getSelectedKey());}catch(Exception ignored){}f.roomId=room.id;f.x=x;f.z=z;f.y=0;f.assetKey=furnitureStyle==null?defaultFurnitureAsset(f.type.name()):furnitureStyle.getSelectedKey();if(!applyFurnitureAsset(f))applyFurniturePreset(f);applyPlacementDefaults(f);if("WALL".equals(f.placement))snapFurnitureToNearestWall(f);plan.furniture.add(f);markPlanEdited();markChanged(true);onFurnitureSelected(f);}
    @Override public void onCameraMessage(String text){info.setText(text);}
    @Override public void onEditGestureStart(){if(gestureSnapshot==null)gestureSnapshot=snapshotState();}
    @Override public void onEditGestureFinish(){if(gestureSnapshot!=null){pushUndoSnapshot(gestureSnapshot);gestureSnapshot=null;}}

    private void applyWallInspector(){if(selectedWall==null)return;pushUndo();selectedWall.thicknessM=Math.max(0.03,wallThicknessCm.getValue()/100.0);selectedWall.materialKey=wallMaterial.getSelectedKey();selectedWall.surfaceStateKey=wallSurfaceState.getSelectedKey();applyFinishPreset(selectedWall,wallFinishPreset.getSelectedKey());selectedWall.finishKey=wallFinish.getSelectedKey();selectedWall.finishThicknessM=wallFinishMm.getValue()/1000.0;selectedWall.finishPatternScale=wallPatternScale.getValue();selectedWall.finishPatternRotationDeg=wallPatternRotation.getValue();markPlanEdited();markChanged(true);onWallSelected(selectedWall);}
    private void applyFurnitureInspector(){if(selectedFurniture==null)return;pushUndo();selectedFurniture.widthM=furnitureWcm.getValue()/100.0;selectedFurniture.depthM=furnitureDcm.getValue()/100.0;selectedFurniture.heightM=furnitureHcm.getValue()/100.0;selectedFurniture.rotationDeg=normalizeDeg(furnitureRotation.getValue());markPlanEdited();markChanged(true);onFurnitureSelected(selectedFurniture);}
    private void nudgeFurniture(double dx,double dz){if(selectedFurniture==null)return;pushUndo();double x=Math.round((selectedFurniture.x+dx)/0.05)*0.05,z=Math.round((selectedFurniture.z+dz)/0.05)*0.05;placeFurnitureSafely(selectedFurniture,x,z);markPlanEdited();markChanged(true);onFurnitureSelected(selectedFurniture);}
    private void rotateFurniture(double deg){if(selectedFurniture==null)return;pushUndo();selectedFurniture.rotationDeg=normalizeDeg(selectedFurniture.rotationDeg+deg);markPlanEdited();markChanged(true);onFurnitureSelected(selectedFurniture);}
    private void deleteSelectedFurniture(){if(selectedFurniture==null)return;pushUndo();plan.furniture.remove(selectedFurniture);markPlanEdited();markChanged(true);showInspectorNone();info.setText("Об'єкт видалено.");}
    private void applyOpeningInspector(){if(selectedOpening==null)return;pushUndo();try{selectedOpening.type=ApartmentOpening.Type.valueOf(openingType.getSelectedKey());}catch(Exception ignored){}double newW=openingWidthCm.getValue()/100.0,cx=selectedOpening.centerX(),cz=selectedOpening.centerZ(),dx=selectedOpening.x2-selectedOpening.x1,dz=selectedOpening.z2-selectedOpening.z1,len=Math.hypot(dx,dz);if(len<1e-6){if(selectedOpening.orientation==ApartmentOpening.Orientation.HORIZONTAL_X){dx=1;dz=0;}else{dx=0;dz=1;}len=1;}double ux=dx/len,uz=dz/len,h=newW/2;selectedOpening.x1=cx-ux*h;selectedOpening.z1=cz-uz*h;selectedOpening.x2=cx+ux*h;selectedOpening.z2=cz+uz*h;selectedOpening.widthM=newW;selectedOpening.heightM=openingHeightCm.getValue()/100.0;selectedOpening.bottomY=openingBottomCm.getValue()/100.0;selectedOpening.topY=selectedOpening.bottomY+selectedOpening.heightM;selectedOpening.manualEdited=true;selectedOpening.confidence=Math.max(selectedOpening.confidence,0.95);reconcileOpening(selectedOpening);markPlanEdited();markChanged(true);onOpeningSelected(selectedOpening);}
    private void deleteSelectedOpening(){if(selectedOpening==null)return;pushUndo();plan.openings.remove(selectedOpening);markPlanEdited();markChanged(true);showInspectorNone();info.setText("Отвір видалено.");}

    private void selectPoint(ElectricalPoint p){selectedPoint=p;selectedWall=placement.findWall(plan,p.wallId);selectedFurniture=null;selectedOpening=null;selectionTitle.setText(pointName(p.type));showOnly(pointPanel);pointType.setSelectedKey(p.type.name());refreshVisualStyles(p.type.name(),p.assetKey==null||p.assetKey.isEmpty()?defaultAssetFor(p.type.name()):p.assetKey);moduleStrip.setSelectedKey(String.valueOf(Math.max(1,p.moduleCount)));cableType.setSelectedKey(p.cableKey==null||p.cableKey.isEmpty()?"AUTO":p.cableKey);plannedLoad.setValue(p.plannedLoadW);circuitNo.setValue(p.circuitNo);heightCm.setValue(p.heightFromFinishedFloorM*100.0);WallPlacementEngine.Metrics mm=placement.metrics(plan,p);offsetCm.setValue(mm.wallId.isEmpty()?0:mm.fromStartM*100.0);twin.setSelection(p.wallId,p.id);showMetrics(p);}
    private void showMetrics(ElectricalPoint p){WallPlacementEngine.Metrics m=placement.metrics(plan,p);if(m.wallId.isEmpty()){selectionDetails.setText(String.format(Locale.US,"H %.2f м • без прив'язки до стіни",p.heightFromFinishedFloorM));return;}selectionDetails.setText(String.format(Locale.US,"H %.2f м • %.2f м від початку стіни • %.2f м від другого кута • група %d",m.heightFromFinishedFloorM,m.fromStartM,m.fromEndM,p.circuitNo));}
    private void applyPointFields(){if(selectedPoint==null)return;pushUndo();try{selectedPoint.moduleCount=Integer.parseInt(moduleStrip.getSelectedKey());}catch(Exception ignored){}String c=cableType.getSelectedKey();selectedPoint.cableKey="AUTO".equals(c)?"":c;selectedPoint.assetKey=visualStyle.getSelectedKey();selectedPoint.plannedLoadW=plannedLoad.getValue();selectedPoint.circuitNo=circuitNo.getIntValue();selectedPoint.groupId=selectedPoint.circuitNo>0?"G"+selectedPoint.circuitNo:"";if(selectedWall!=null){double f=Math.max(0.015,Math.min(0.985,(offsetCm.getValue()/100.0)/Math.max(0.05,selectedWall.lengthM())));placement.place(plan,floor,selectedPoint,selectedWall.id,f,heightCm.getValue()/100.0);}else selectedPoint.heightFromFinishedFloorM=heightCm.getValue()/100.0;markChanged(true);selectPoint(selectedPoint);}
    private void deleteSelectedPoint(){if(selectedPoint==null)return;pushUndo();floor.points.remove(selectedPoint);selectedPoint=null;selectedWall=null;markChanged(true);showInspectorNone();info.setText("Точку видалено. 2D і траси синхронізовано.");}

    private void focusSelected(){if(selectedFurniture!=null)twin.focusFurniture(selectedFurniture);else if(selectedOpening!=null)twin.focusOpening(selectedOpening);else if(selectedWall!=null)twin.focusWall(selectedWall);else twin.frameAll();}

    private String snapshotState(){try{JSONObject o=new JSONObject();o.put("plan",plan.toJson());o.put("floor",floor.toJson());return o.toString();}catch(Exception e){return null;}}
    private void pushUndo(){String s=snapshotState();if(s!=null)pushUndoSnapshot(s);}
    private void pushUndoSnapshot(String s){if(s==null)return;undoStack.addLast(s);while(undoStack.size()>MAX_HISTORY)undoStack.removeFirst();redoStack.clear();updateHistoryButtons();}
    private void undo(){if(undoStack.isEmpty())return;String current=snapshotState(),prev=undoStack.removeLast();if(current!=null)redoStack.addLast(current);restoreSnapshot(prev);updateHistoryButtons();info.setText("Undo: попередній стан 3D/2D відновлено.");}
    private void redo(){if(redoStack.isEmpty())return;String current=snapshotState(),next=redoStack.removeLast();if(current!=null)undoStack.addLast(current);restoreSnapshot(next);updateHistoryButtons();info.setText("Redo: зміну повернуто.");}
    private void restoreSnapshot(String raw){try{JSONObject o=new JSONObject(raw);plan=ApartmentPlan.fromJson(o.optJSONObject("plan"));floor=FloorPlan.fromJson(o.optJSONObject("floor"));floor.projectId=projectId;if(scene!=null)scene.apartmentPlan=plan;selectedWall=null;selectedPoint=null;selectedFurniture=null;selectedOpening=null;twin.setData(plan,floor);twin.clearSelection();showInspectorNone();saveAll();refreshSyncLabel();refreshRoutingPreview();}catch(Exception e){new StabilityReporter(this).error("TWIN_HISTORY","Cannot restore editor history",e);}}
    private void updateHistoryButtons(){if(undoButton!=null){undoButton.setEnabled(!undoStack.isEmpty());redoButton.setEnabled(!redoStack.isEmpty());}}

    private void markPlanEdited(){if(plan==null)return;plan.manualEdited=true;plan.manualRevision++;plan.lastManualEditAt=System.currentTimeMillis();}
    /** Keeps editor moves inside reconstructed floor space and updates room ownership when crossing rooms. */
    private void placeFurnitureSafely(FurnitureItem f,double x,double z){
        if(f==null||plan==null)return;
        RoomZone target=roomAt(x,z);
        if(target!=null){f.x=x;f.z=z;f.roomId=target.id;return;}
        RoomZone current=roomById(f.roomId);
        if(current==null)current=nearestRoom(x,z);
        if(current!=null){double[] q=RoomFootprint.nearestInside(plan,current,x,z);f.x=q[0];f.z=q[1];f.roomId=current.id;}
        else {f.x=x;f.z=z;}
    }
    private RoomZone roomAt(double x,double z){if(plan==null)return null;for(RoomZone r:plan.rooms)if(RoomFootprint.contains(plan,r,x,z))return r;return null;}
    private RoomZone roomById(String id){if(plan==null||id==null)return null;for(RoomZone r:plan.rooms)if(id.equals(r.id))return r;return null;}
    private RoomZone nearestRoom(double x,double z){RoomZone best=null;double bd=Double.POSITIVE_INFINITY;if(plan==null)return null;for(RoomZone r:plan.rooms){double[] q=RoomFootprint.nearestInside(plan,r,x,z);double d=(q[0]-x)*(q[0]-x)+(q[1]-z)*(q[1]-z);if(d<bd){bd=d;best=r;}}return best;}

    /** Recomputes wall and room topology after a manual door/window resize or type change. */
    private void reconcileOpening(ApartmentOpening o){
        if(o==null||plan==null)return;
        PlanWallSegment wall=null;double best=Double.POSITIVE_INFINITY,cx=o.centerX(),cz=o.centerZ();boolean exactWall=false;
        for(PlanWallSegment w:plan.walls){if(w.id!=null&&w.id.equals(o.wallId)){wall=w;exactWall=true;break;}double d=pointSegmentDistance(cx,cz,w.x1,w.z1,w.x2,w.z2);if(d<best){best=d;wall=w;}}
        if(wall==null||(!exactWall&&best>Math.max(.45,Math.max(.01,plan.cellM)*2)))return;o.wallId=wall.id==null?"":wall.id;
        double dx=wall.x2-wall.x1,dz=wall.z2-wall.z1,len=Math.hypot(dx,dz);if(len<1e-6)return;
        double nx=-dz/len,nz=dx/len,off=Math.max(.12,Math.max(.01,plan.cellM)*.75);
        RoomZone a=roomAt(cx+nx*off,cz+nz*off),b=roomAt(cx-nx*off,cz-nz*off);
        if(a==b)b=null;
        o.roomAId=a==null?"":a.id;o.roomBId=b==null?"":b.id;
        if(o.type==ApartmentOpening.Type.WINDOW){o.external=true;o.roomBId="";if(a==null&&b!=null)o.roomAId=b.id;}
        else o.external=(a==null||b==null);
        o.orientation=Math.abs(dx)>=Math.abs(dz)?ApartmentOpening.Orientation.HORIZONTAL_X:ApartmentOpening.Orientation.VERTICAL_Z;
    }
    private double pointSegmentDistance(double px,double pz,double x1,double z1,double x2,double z2){double dx=x2-x1,dz=z2-z1,l2=dx*dx+dz*dz;if(l2<1e-12)return Math.hypot(px-x1,pz-z1);double t=((px-x1)*dx+(pz-z1)*dz)/l2;t=Math.max(0,Math.min(1,t));return Math.hypot(px-(x1+t*dx),pz-(z1+t*dz));}
    private void markChanged(boolean saveNow){changeCounter++;if(saveNow){saveAll();refreshRoutingPreview();}twin.setData(plan,floor);refreshSyncLabel();}
    private void refreshSyncLabel(){if(syncInfo!=null&&plan!=null&&floor!=null)syncInfo.setText("Одна модель • стіни "+plan.walls.size()+" • отвори "+plan.openings.size()+" • меблі "+plan.furniture.size()+" • електроточки "+floor.points.size()+" • змін "+changeCounter);}
    private void refreshRoutingPreview(){if(routingInfo==null||plan==null||floor==null)return;try{ElectricalProject project=new FileProjectRepository(this).get(projectId);if(project==null){routingInfo.setText("Траси: немає параметрів об'єкта.");return;}InstallationRoutingResult r=new InstallationRoutingEngine().build(project,plan,floor);if(r.circuitRoutes.isEmpty()){routingInfo.setText("Траси: додай щит, точки та групи.");return;}routingInfo.setText(String.format(Locale.US,"Автоперерахунок: кабель %.1f м → закупівля %.1f м • штроба %.1f м • проходки %d",r.totalRawCableM,r.totalPurchaseCableM,r.materials.wallChaseM,r.penetrations.size()));}catch(Exception e){routingInfo.setText("Траси перерахуються на окремому екрані.");}}
    private void saveAll(){if(scene!=null){scene.apartmentPlan=plan;sceneRepo.save(projectId,scene,null);}if(floor!=null){floor.projectId=projectId;floor.updatedAt=System.currentTimeMillis();floorRepo.save(floor);}}

    private void migrateWallLocks(){if(plan==null||floor==null)return;boolean changed=false;for(ElectricalPoint e:floor.points){if(e.wallId==null||e.wallId.isEmpty())continue;PlanWallSegment w=placement.findWall(plan,e.wallId);if(w==null)continue;if(e.wallFraction<=0||e.wallFraction>=1){double t=placement.fractionFor(w,e.position.x,e.position.z);e.wallFraction=t;e.wallOffsetM=t*w.lengthM();e.lockedToWall=true;changed=true;}}if(changed)saveAll();}


    private void applyCustomWallColor(){if(selectedWall==null||wallColorHex==null)return;String raw=wallColorHex.getText().toString().trim();if(raw.startsWith("#"))raw=raw.substring(1);try{if(raw.length()!=6)throw new IllegalArgumentException("hex");int rgb=Integer.parseInt(raw,16);pushUndo();selectedWall.finishColorArgb=0xFF000000|rgb;if("RAW".equals(selectedWall.finishKey))selectedWall.finishKey="PAINT";selectedWall.finishPresetKey="CUSTOM_COLOR";selectedWall.finishPatternKey="";markPlanEdited();markChanged(true);onWallSelected(selectedWall);info.setText("Свій колір застосовано до вибраної стіни.");}catch(Exception e){info.setText("Колір має бути у форматі #RRGGBB, наприклад #EAE6DD.");}}

    private void snapSelectedFurnitureToWall(){if(selectedFurniture==null)return;pushUndo();snapFurnitureToNearestWall(selectedFurniture);markPlanEdited();markChanged(true);onFurnitureSelected(selectedFurniture);}
    private void snapFurnitureToNearestWall(FurnitureItem f){if(f==null||plan==null||plan.walls.isEmpty())return;PlanWallSegment best=null;double bestD=Double.POSITIVE_INFINITY,bestT=.5;for(PlanWallSegment w:plan.walls){double dx=w.x2-w.x1,dz=w.z2-w.z1,l2=dx*dx+dz*dz;if(l2<1e-9)continue;double t=((f.x-w.x1)*dx+(f.z-w.z1)*dz)/l2;t=Math.max(.03,Math.min(.97,t));double px=w.x1+t*dx,pz=w.z1+t*dz,d=Math.hypot(f.x-px,f.z-pz);if(d<bestD){bestD=d;best=w;bestT=t;}}if(best==null)return;double dx=best.x2-best.x1,dz=best.z2-best.z1,len=Math.hypot(dx,dz);f.x=best.x1+(best.x2-best.x1)*bestT;f.z=best.z1+(best.z2-best.z1)*bestT;f.rotationDeg=normalizeDeg(Math.toDegrees(Math.atan2(dz,dx)));f.placement="WALL";}

    private void refreshRoomChoice(){
        if(roomChoice==null)roomChoice=new ChoiceStrip(this);
        if(plan==null||plan.rooms.isEmpty()){roomChoice.setItems(new String[]{"NONE"},new String[]{"Немає кімнат"},"NONE");return;}
        String[] keys=new String[plan.rooms.size()],labels=new String[plan.rooms.size()];for(int i=0;i<plan.rooms.size();i++){RoomZone r=plan.rooms.get(i);keys[i]=r.id;labels[i]=r.displayName()+String.format(Locale.US," • %.1f м²",r.areaM2);}
        roomChoice.setItems(keys,labels,keys[0]);roomChoice.setListener(k->loadRoomFinishControls());loadRoomFinishControls();
    }
    private RoomZone selectedRoom(){if(plan==null||plan.rooms.isEmpty())return null;String id=roomChoice==null?"":roomChoice.getSelectedKey();for(RoomZone r:plan.rooms)if(r.id.equals(id))return r;RoomZone best=plan.rooms.get(0);for(RoomZone r:plan.rooms)if(r.areaM2>best.areaM2)best=r;return best;}
    private void loadRoomFinishControls(){RoomZone r=selectedRoom();if(r==null)return;if(roomFloorFinish!=null)roomFloorFinish.setSelectedKey(r.floorFinishPresetKey);if(roomCeilingFinish!=null)roomCeilingFinish.setSelectedKey(r.ceilingFinishPresetKey);if(roomSkirting!=null)roomSkirting.setSelectedKey(r.skirtingPresetKey);}
    private void applyRoomFinishes(boolean all){if(plan==null)return;pushUndo();RoomZone selected=selectedRoom();for(RoomZone r:plan.rooms){if(!all&&r!=selected)continue;RoomFinishCatalog.Preset fp=RoomFinishCatalog.floor(roomFloorFinish.getSelectedKey()),cp=RoomFinishCatalog.ceiling(roomCeilingFinish.getSelectedKey());r.floorFinishPresetKey=fp.key;r.floorFinishColorArgb=fp.colorArgb;r.floorPatternKey=fp.patternKey;r.floorPatternScale=fp.scale;r.ceilingFinishPresetKey=cp.key;r.ceilingFinishColorArgb=cp.colorArgb;r.ceilingPatternKey=cp.patternKey;r.ceilingPatternScale=cp.scale;r.skirtingPresetKey=roomSkirting.getSelectedKey();}markPlanEdited();markChanged(true);info.setText(all?"Оздоблення підлоги/стелі застосовано до всіх кімнат.":"Оздоблення застосовано до вибраної кімнати.");}
    private void applyRoomKit(){RoomZone r=selectedRoom();if(r==null)return;pushUndo();RoomKitCatalog.Kit kit=RoomKitCatalog.find(roomKit.getSelectedKey());for(RoomKitCatalog.Item it:kit.items){FurnitureItem f=new FurnitureItem();f.roomId=r.id;f.type=it.type;f.assetKey=it.assetKey;f.x=r.minX+Math.max(.05,Math.min(.95,it.nx))*(r.maxX-r.minX);f.z=r.minZ+Math.max(.05,Math.min(.95,it.nz))*(r.maxZ-r.minZ);double[] inside=RoomFootprint.nearestInside(plan,r,f.x,f.z);f.x=inside[0];f.z=inside[1];f.rotationDeg=it.rot;if(!applyFurnitureAsset(f))applyFurniturePreset(f);applyPlacementDefaults(f);if("WALL".equals(f.placement))snapFurnitureToNearestWall(f);f.groupKey=kit.key;plan.furniture.add(f);}if(r.type==RoomZone.RoomType.UNKNOWN)r.type=kit.roomType;markPlanEdited();markChanged(true);info.setText("Додано сет: "+kit.label+". Кожен предмет лишається окремо редагованим.");}
    private void clearRoomFurniture(){RoomZone r=selectedRoom();if(r==null)return;pushUndo();for(int i=plan.furniture.size()-1;i>=0;i--)if(r.id.equals(plan.furniture.get(i).roomId))plan.furniture.remove(i);markPlanEdited();markChanged(true);info.setText("Меблі вибраної кімнати очищено. Структурна геометрія та електрика не змінені.");}

    private void refreshFurnitureInspectorStyles(String type,String selected){if(furnitureInspectorStyle==null)return;java.util.List<VisualAssetPreset> items=new VisualAssetRepository(this).forFurnitureType(type);if(items.isEmpty()){furnitureInspectorStyle.setItems(new String[]{selected==null||selected.isEmpty()?defaultFurnitureAsset(type):selected},new String[]{"Поточна модель"},selected);return;}String[] keys=new String[items.size()],labels=new String[items.size()];for(int i=0;i<items.size();i++){keys[i]=items.get(i).id;labels[i]=items.get(i).label;}String sel=selected==null||selected.isEmpty()?keys[0]:selected;furnitureInspectorStyle.setItems(keys,labels,sel);}
    private void duplicateSelectedFurniture(){if(selectedFurniture==null)return;pushUndo();FurnitureItem n=new FurnitureItem();n.roomId=selectedFurniture.roomId;n.type=selectedFurniture.type;n.name=selectedFurniture.name;n.assetKey=selectedFurniture.assetKey;n.x=selectedFurniture.x+.25;n.y=selectedFurniture.y;n.z=selectedFurniture.z+.25;RoomZone nr=null;for(RoomZone rr:plan.rooms)if(rr.id.equals(n.roomId)){nr=rr;break;}if(nr!=null){double[] inside=RoomFootprint.nearestInside(plan,nr,n.x,n.z);n.x=inside[0];n.z=inside[1];}n.widthM=selectedFurniture.widthM;n.depthM=selectedFurniture.depthM;n.heightM=selectedFurniture.heightM;n.rotationDeg=selectedFurniture.rotationDeg;n.blocksElectricalPlacement=selectedFurniture.blocksElectricalPlacement;n.colorArgb=selectedFurniture.colorArgb;n.groupKey=selectedFurniture.groupKey;n.placement=selectedFurniture.placement;plan.furniture.add(n);markPlanEdited();markChanged(true);onFurnitureSelected(n);info.setText("Об'єкт продубльовано зі зміщенням 25 см.");}
    private void toggleFurnitureLock(){if(selectedFurniture==null)return;pushUndo();selectedFurniture.locked=!selectedFurniture.locked;markPlanEdited();markChanged(true);onFurnitureSelected(selectedFurniture);}
    private void replaceSelectedFurnitureAsset(){if(selectedFurniture==null||furnitureInspectorStyle==null)return;String id=furnitureInspectorStyle.getSelectedKey();VisualAssetPreset a=new VisualAssetRepository(this).find(id);if(a==null)return;pushUndo();double x=selectedFurniture.x,y=selectedFurniture.y,z=selectedFurniture.z,rot=selectedFurniture.rotationDeg;selectedFurniture.assetKey=a.id;selectedFurniture.name=a.label;selectedFurniture.widthM=a.widthM;selectedFurniture.depthM=a.depthM;selectedFurniture.heightM=a.heightM;selectedFurniture.placement=a.placement;selectedFurniture.colorArgb=a.colorArgb;selectedFurniture.x=x;selectedFurniture.y=a.defaultMountHeightM>0?a.defaultMountHeightM:y;selectedFurniture.z=z;selectedFurniture.rotationDeg=rot;markPlanEdited();markChanged(true);onFurnitureSelected(selectedFurniture);info.setText("Модель замінено без зміни X/Z і повороту.");}

    private void createPointArray(){if(selectedPoint==null||selectedWall==null)return;int count=Math.max(2,pointArrayCount.getIntValue());double spacing=Math.max(.05,pointArraySpacingCm.getValue()/100.0),len=Math.max(.05,selectedWall.lengthM()),start=selectedPoint.wallFraction>=0?selectedPoint.wallFraction:placement.fractionFor(selectedWall,selectedPoint.position.x,selectedPoint.position.z);pushUndo();int added=0;for(int i=1;i<count;i++){double f=start+(spacing*i)/len;if(f>=.985)break;ElectricalPoint e=copyPoint(selectedPoint);if(placement.place(plan,floor,e,selectedWall.id,f,selectedPoint.heightFromFinishedFloorM)){floor.points.add(e);added++;}}markChanged(true);info.setText("Масив: додано "+added+" точок • крок "+Math.round(spacing*100)+" см.");}
    private ElectricalPoint copyPoint(ElectricalPoint p){ElectricalPoint e=new ElectricalPoint();e.roomId=p.roomId;e.type=p.type;e.heightFromFinishedFloorM=p.heightFromFinishedFloorM;e.groupId=p.groupId;e.circuitNo=p.circuitNo;e.moduleCount=p.moduleCount;e.cableKey=p.cableKey;e.plannedLoadW=p.plannedLoadW;e.assetKey=p.assetKey;e.clientLabel=p.clientLabel;e.note=p.note;e.manualPosition=true;e.placementSource="DIGITAL_TWIN_ARRAY";return e;}

    private String defaultFurnitureAsset(String type){
        if("SOFA".equals(type))return "SOFA_3";if("BED".equals(type))return "BED_QUEEN";if("WARDROBE".equals(type))return "WARDROBE_3D";if("TABLE".equals(type))return "TABLE_DINING";if("CHAIR".equals(type))return "CHAIR_DINING";if("DESK".equals(type))return "DESK_120";if("NIGHTSTAND".equals(type))return "NIGHTSTAND_1";if("TV".equals(type))return "TV_55";if("KITCHEN".equals(type))return "KITCHEN_240";if("FRIDGE".equals(type))return "FRIDGE_600";if("WASHER".equals(type))return "WASHER_600";if("OVEN".equals(type))return "OVEN_600";if("SINK".equals(type))return "SINK_BASE";if("TOILET".equals(type))return "TOILET_STD";if("BATHTUB".equals(type))return "BATHTUB_170";if("SHOWER".equals(type))return "SHOWER_900";if("RADIATOR".equals(type))return "RADIATOR_600";if("MIRROR".equals(type))return "MIRROR_600";if("SHELF".equals(type))return "SHELF_WALL";if("APPLIANCE".equals(type))return "APPLIANCE_600";if("SANITARY".equals(type))return "SANITARY_STD";return "CABINET_STD";
    }
    private void refreshFurnitureStyles(String type,String selected){if(furnitureStyle==null)return;java.util.List<VisualAssetPreset> items=new VisualAssetRepository(this).forFurnitureType(type);if(items.isEmpty())furnitureStyle.setItems(new String[]{defaultFurnitureAsset(type)},new String[]{"Базовий"},selected);else{String[] keys=new String[items.size()],labels=new String[items.size()];for(int i=0;i<items.size();i++){keys[i]=items.get(i).id;labels[i]=items.get(i).label;}furnitureStyle.setItems(keys,labels,selected==null||selected.isEmpty()?keys[0]:selected);}}
    private boolean applyFurnitureAsset(FurnitureItem f){VisualAssetPreset a=new VisualAssetRepository(this).find(f.assetKey);if(a==null||!"FURNITURE".equals(a.category))return false;f.widthM=a.widthM;f.depthM=a.depthM;f.heightM=a.heightM;f.name=a.label;f.placement=a.placement;f.colorArgb=a.colorArgb;if(a.defaultMountHeightM>0)f.y=a.defaultMountHeightM;return true;}
    private void applyFurniturePreset(FurnitureItem f){
        switch(f.type){case KITCHEN:f.widthM=2.4;f.depthM=.60;f.heightM=.90;break;case WARDROBE:f.widthM=1.5;f.depthM=.60;f.heightM=2.30;break;case BED:f.widthM=1.7;f.depthM=2.1;f.heightM=.55;break;case SOFA:f.widthM=2.2;f.depthM=.92;f.heightM=.86;break;case TABLE:case DESK:f.widthM=1.2;f.depthM=.70;f.heightM=.75;break;case CHAIR:f.widthM=.46;f.depthM=.50;f.heightM=.88;break;case NIGHTSTAND:f.widthM=.48;f.depthM=.42;f.heightM=.48;break;case TV:f.widthM=1.23;f.depthM=.06;f.heightM=.71;f.y=.65;break;case FRIDGE:f.widthM=.60;f.depthM=.68;f.heightM=2.0;break;case WASHER:f.widthM=.60;f.depthM=.60;f.heightM=.85;break;case OVEN:f.widthM=.60;f.depthM=.58;f.heightM=.60;break;case SINK:f.widthM=.60;f.depthM=.50;f.heightM=.86;break;case TOILET:f.widthM=.38;f.depthM=.68;f.heightM=.80;break;case BATHTUB:f.widthM=1.70;f.depthM=.75;f.heightM=.60;break;case SHOWER:f.widthM=.90;f.depthM=.90;f.heightM=2.0;break;case RADIATOR:f.widthM=1.0;f.depthM=.12;f.heightM=.60;break;case MIRROR:f.widthM=.60;f.depthM=.04;f.heightM=.80;f.y=.95;break;case SHELF:f.widthM=1.0;f.depthM=.25;f.heightM=.25;f.y=1.2;break;case APPLIANCE:f.widthM=.60;f.depthM=.65;f.heightM=.90;break;case SANITARY:f.widthM=.70;f.depthM=.70;f.heightM=.85;break;default:f.widthM=.80;f.depthM=.45;f.heightM=.90;}
    }
    private void applyPlacementDefaults(FurnitureItem f){if(f==null)return;if(f.placement==null||f.placement.isEmpty())f.placement="FLOOR";switch(f.type){case TV:if(f.y<=0)f.y=.85;break;case MIRROR:if(f.y<=0)f.y=1.00;break;case SHELF:if(f.y<=0)f.y=1.20;break;case RADIATOR:if(f.y<=0)f.y=.10;break;default:f.y=Math.max(0,f.y);}switch(f.type){case WARDROBE:case KITCHEN:case BED:case SOFA:case CABINET:case FRIDGE:case WASHER:case BATHTUB:case SHOWER:f.blocksElectricalPlacement=true;break;default:break;}}
    private double[] snapWall(PlanWallSegment wall,double fraction,double heightM){double len=Math.max(0.05,wall.lengthM()),cm=Math.round(fraction*len*100.0)/100.0,h=Math.round(heightM*100.0)/100.0;return new double[]{Math.max(0.015,Math.min(0.985,cm/len)),Math.max(0,h)};}
    private int modulesForAsset(String asset){if(asset==null)return 1;if(asset.contains("FRAME_3"))return 3;if(asset.contains("FRAME_2")||asset.contains("DOUBLE"))return 2;return 1;}
    private String defaultAssetFor(String type){if("LIGHT".equals(type))return "LIGHT_SPOT";if("PANEL".equals(type))return "PANEL_FLUSH_24";if("SWITCH".equals(type))return "SWITCH_SINGLE";if("SOCKET".equals(type))return "SOCKET_FRAME_1";return type+"_BASIC";}
    private void refreshVisualStyles(String type,String selected){if(visualStyle==null)return;java.util.List<VisualAssetPreset> items=new VisualAssetRepository(this).forPointType(type);if(items.isEmpty())visualStyle.setItems(new String[]{defaultAssetFor(type)},new String[]{"Базовий"},selected);else{String[] keys=new String[items.size()],labels=new String[items.size()];for(int i=0;i<items.size();i++){keys[i]=items.get(i).id;labels[i]=items.get(i).label;}visualStyle.setItems(keys,labels,selected==null||selected.isEmpty()?keys[0]:selected);}updatePlacementSurface(type,visualStyle.getSelectedKey());visualStyle.setListener(k->updatePlacementSurface(pointType.getSelectedKey(),k));}
    private void updatePlacementSurface(String type,String asset){if(twin==null)return;boolean ceiling=false;for(VisualAssetPreset a:new VisualAssetRepository(this).forPointType(type))if(a.id.equals(asset)){ceiling="CEILING".equals(a.placement);break;}twin.setPlaceOnCeiling(ceiling);}

    private void applyFinishPreset(PlanWallSegment wall,String key){if(wall==null)return;WallFinishCatalog.Preset fp=WallFinishCatalog.find(key);wall.finishPresetKey=fp.key;wall.finishKey=fp.finishKey;wall.finishThicknessM=fp.thicknessM;wall.finishColorArgb=fp.colorArgb;wall.finishPatternKey=fp.patternKey;}
    private void applyQuickFinishAll(){if(plan==null)return;pushUndo();String k=finishPreset==null?"PAINT_WHITE":finishPreset.getSelectedKey();for(PlanWallSegment w:plan.walls){applyFinishPreset(w,k);w.finishKey=finishType.getSelectedKey();w.finishThicknessM=finishThickness.getValue();}markPlanEdited();markChanged(true);info.setText("Оздоблення застосовано до всіх стін. Геометрія скану не змінена.");}
    private void applySelectedWallFinishAll(){if(selectedWall==null||plan==null)return;pushUndo();String preset=wallFinishPreset.getSelectedKey(),surface=wallSurfaceState.getSelectedKey(),material=wallMaterial.getSelectedKey();double mm=wallFinishMm.getValue()/1000.0;String fk=wallFinish.getSelectedKey();for(PlanWallSegment w:plan.walls){w.surfaceStateKey=surface;w.materialKey=material;applyFinishPreset(w,preset);w.finishKey=fk;w.finishThicknessM=mm;w.finishPatternScale=wallPatternScale.getValue();w.finishPatternRotationDeg=wallPatternRotation.getValue();}markPlanEdited();markChanged(true);onWallSelected(selectedWall);info.setText("Матеріал і оздоблення застосовано до всіх стін.");}
    private double defaultFinishThickness(String k){if("PLASTER".equals(k))return 0.015;if("PUTTY".equals(k))return 0.003;if("PAINT".equals(k))return 0.001;if("TILE".equals(k))return 0.012;if("DRYWALL".equals(k))return 0.0125;if("DECOR".equals(k))return 0.010;return 0.0;}
    private String pointName(ElectricalPoint.Type t){switch(t){case SOCKET:return "Розетка";case SWITCH:return "Вимикач";case LIGHT:return "Світло";case PANEL:return "Щит";case ETHERNET:return "LAN";case APPLIANCE:return "Техніка";case CAMERA:return "Камера";case SENSOR:return "Датчик";case SMART_HOME:return "Smart";case TV:return "TV";default:return t.name();}}
    private String openingName(ApartmentOpening o){if(o.type==ApartmentOpening.Type.DOOR)return "Двері";if(o.type==ApartmentOpening.Type.WINDOW)return "Вікно";if(o.type==ApartmentOpening.Type.PASSAGE)return "Прохід";return "Отвір";}
    private String furnitureName(FurnitureItem f){if(f.name!=null&&!f.name.trim().isEmpty())return f.name;switch(f.type){case SOFA:return "Диван";case BED:return "Ліжко";case WARDROBE:return "Шафа";case KITCHEN:return "Кухня";case TABLE:return "Стіл";case CHAIR:return "Стілець";case DESK:return "Робочий стіл";case NIGHTSTAND:return "Приліжкова тумба";case TV:return "Телевізор";case FRIDGE:return "Холодильник";case WASHER:return "Пральна машина";case OVEN:return "Духовка";case SINK:return "Умивальник";case TOILET:return "Унітаз";case BATHTUB:return "Ванна";case SHOWER:return "Душ";case RADIATOR:return "Радіатор";case MIRROR:return "Дзеркало";case SHELF:return "Полиця";case APPLIANCE:return "Техніка";case SANITARY:return "Сантехніка";case CUSTOM:return "Об'єкт";default:return "Тумба";}}
    private String modeHint(String k){if("PLACE".equals(k))return "Електрика: тап по стіні — додати; затисни існуючу точку й тягни по стіні.";if("FINISH".equals(k))return "Оздоблення: вибери шар і тапни потрібну стіну.";if("FURNITURE".equals(k))return "Об'єкти: тап по підлозі — додати; існуючий об'єкт можна вибрати й перетягнути.";return "Вибір: тапни будь-яку сутність. Orbit — 1 палець обертання, pinch zoom, 2 пальці pan.";}
    private String cameraHint(String k){if("WALK".equals(k))return "Walk: камера всередині кімнати; тягни пальцем для огляду.";if("TOP".equals(k))return "Top: огляд зверху; тягни для pan, pinch для масштабу.";return "Orbit: обертай модель одним пальцем; pinch zoom; двома пальцями зміщуй центр.";}
    private double normalizeDeg(double d){d%=360;if(d<0)d+=360;return d;}
    private LinearLayout.LayoutParams weighted(float weight,int margin){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,weight);p.leftMargin=Ui.dp(this,margin);return p;}
    private void open(Class<?> c){Intent i=new Intent(this,c);i.putExtra("projectId",projectId);startActivity(i);}
    @Override protected void onPause(){super.onPause();try{saveAll();}catch(Exception e){new StabilityReporter(this).error("EDITOR_SAVE","Auto-save on pause failed",e);}}
}
