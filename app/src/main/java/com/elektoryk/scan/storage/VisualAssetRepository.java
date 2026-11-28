package com.elektoryk.scan.storage;

import android.content.Context;
import com.elektoryk.scan.model.VisualAssetPreset;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** Small installable asset catalog. Packs are JSON so AIDE/offline installs remain deterministic. */
public class VisualAssetRepository {
    private static final long MAX_PACK_BYTES=2L*1024L*1024L;
    private static final int MAX_IMPORTED_ASSETS=1000;
    private final File file;
    private final StabilityReporter reporter;
    public VisualAssetRepository(Context c){File d=new File(c.getFilesDir(),"visual_assets");if(!d.exists())d.mkdirs();file=new File(d,"assets.json");reporter=new StabilityReporter(c);}

    public synchronized List<VisualAssetPreset> load(){
        ArrayList<VisualAssetPreset> out=new ArrayList<>();boolean loaded=false;
        if(file.exists())try{readCatalog(file,out);loaded=true;}catch(Exception e){
            File bak=new File(file.getParentFile(),file.getName()+".bak");
            if(bak.isFile())try{out.clear();readCatalog(bak,out);loaded=true;if(AtomicFileStore.restoreBackup(file))reporter.breadcrumb("ASSET_RECOVERY","Restored visual asset backup");else reporter.breadcrumb("ASSET_RECOVERY","Loaded visual asset backup; main restore failed");}catch(Exception backupError){reporter.error("ASSET_READ","Main and backup visual asset catalogs are corrupt",backupError);}
            if(!loaded)reporter.error("ASSET_READ","Could not read visual asset catalog",e);
        }
        ensureBuiltins(out);save(out);return out;
    }

    private void readCatalog(File source,List<VisualAssetPreset> out)throws Exception{JSONArray a=new JSONArray(AtomicFileStore.readText(source));if(a.length()>MAX_IMPORTED_ASSETS+100)throw new IOException("Asset catalog has unreasonable size");for(int i=0;i<a.length();i++){VisualAssetPreset x=sanitize(VisualAssetPreset.fromJson(a.optJSONObject(i)));if(x!=null&&!x.id.isEmpty())out.add(x);}}

    public synchronized List<VisualAssetPreset> forPointType(String t){
        ArrayList<VisualAssetPreset> out=new ArrayList<>();for(VisualAssetPreset a:load())if("ELECTRICAL".equals(a.category)&&t.equals(a.pointType))out.add(a);return out;
    }
    public synchronized List<VisualAssetPreset> forFurnitureType(String t){
        ArrayList<VisualAssetPreset> out=new ArrayList<>();for(VisualAssetPreset a:load())if("FURNITURE".equals(a.category)&&t.equals(a.furnitureType))out.add(a);return out;
    }
    public synchronized VisualAssetPreset find(String id){if(id==null)return null;for(VisualAssetPreset a:load())if(id.equals(a.id))return a;return null;}

    /** Case-insensitive local search across name/id/type/group/tags. */
    public synchronized List<VisualAssetPreset> search(String query,String category){
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        ArrayList<VisualAssetPreset> out=new ArrayList<>();
        for(VisualAssetPreset a:load()){
            if(category!=null&&!category.isEmpty()&&!category.equals(a.category))continue;
            String hay=(a.label+" "+a.id+" "+a.group+" "+a.tags+" "+a.pointType+" "+a.furnitureType).toLowerCase(Locale.ROOT);
            if(q.isEmpty()||hay.contains(q))out.add(a);
        }
        return out;
    }

    public synchronized List<String> groups(String category){
        LinkedHashSet<String> set=new LinkedHashSet<>();
        for(VisualAssetPreset a:load())if(category==null||category.isEmpty()||category.equals(a.category))set.add(a.group==null||a.group.isEmpty()?"Інше":a.group);
        return new ArrayList<>(set);
    }

    public synchronized int importPack(InputStream in)throws Exception{
        String text=read(in);JSONArray a;String trim=text.trim();if(trim.startsWith("["))a=new JSONArray(trim);else a=new JSONObject(trim).optJSONArray("assets");if(a==null)return 0;
        if(a.length()>MAX_IMPORTED_ASSETS)throw new IOException("Забагато моделей у пакеті: "+a.length());
        List<VisualAssetPreset> all=load();Map<String,VisualAssetPreset> map=new LinkedHashMap<>();for(VisualAssetPreset x:all)map.put(x.id,x);int n=0;
        for(int i=0;i<a.length();i++){VisualAssetPreset x=sanitize(VisualAssetPreset.fromJson(a.optJSONObject(i)));if(x==null||x.id.isEmpty())continue;x.builtIn=false;map.put(x.id,x);n++;}
        save(new ArrayList<>(map.values()));return n;
    }

    private VisualAssetPreset sanitize(VisualAssetPreset x){
        if(x==null)return null;
        x.id=safeId(x.id);if(x.id.isEmpty())return null;
        if(x.label==null||x.label.trim().isEmpty())x.label=x.id;else x.label=x.label.trim();
        if(!"ELECTRICAL".equals(x.category)&&!"FURNITURE".equals(x.category))x.category="FURNITURE";
        if(!"WALL".equals(x.placement)&&!"CEILING".equals(x.placement)&&!"FLOOR".equals(x.placement))x.placement="FLOOR";
        x.widthM=clampDim(x.widthM,.08);x.depthM=clampDim(x.depthM,.03);x.heightM=clampDim(x.heightM,.08);
        if(x.group==null||x.group.trim().isEmpty())x.group="Інше";else x.group=x.group.trim();
        if(x.tags==null)x.tags="";if(x.styleKey==null||x.styleKey.isEmpty())x.styleKey="BASIC";
        x.defaultMountHeightM=Math.max(0,Math.min(5.0,x.defaultMountHeightM));
        x.snapStepM=Math.max(.01,Math.min(.50,x.snapStepM));
        return x;
    }
    private double clampDim(double v,double fallback){if(Double.isNaN(v)||Double.isInfinite(v)||v<=0)return fallback;return Math.max(.01,Math.min(20.0,v));}
    private String safeId(String id){if(id==null)return "";String s=id.trim().replaceAll("[^A-Za-z0-9_.-]+","_");return s.length()>80?s.substring(0,80):s;}

    private void ensureBuiltins(List<VisualAssetPreset> a){
        addElectrical(a,"SOCKET_FRAME_1","Розетка • 1 пост у рамці","SOCKET","WALL",.09,.03,.09);
        addElectrical(a,"SOCKET_FRAME_2","Розетки • 2 пости у рамці","SOCKET","WALL",.16,.03,.09);
        addElectrical(a,"SOCKET_FRAME_3","Розетки • 3 пости у рамці","SOCKET","WALL",.23,.03,.09);
        addElectrical(a,"SOCKET_DOUBLE_BLOCK","Подвійний механізм","SOCKET","WALL",.09,.04,.14);
        addElectrical(a,"SWITCH_SINGLE","Вимикач • 1 клавіша","SWITCH","WALL",.09,.03,.09);
        addElectrical(a,"SWITCH_DOUBLE","Вимикач • 2 клавіші","SWITCH","WALL",.09,.03,.09);
        addElectrical(a,"SWITCH_PASS","Прохідний вимикач","SWITCH","WALL",.09,.03,.09);
        addElectrical(a,"SWITCH_DIMMER","Димер","SWITCH","WALL",.09,.035,.09);
        addElectrical(a,"LIGHT_SPOT","Точковий світильник","LIGHT","CEILING",.09,.09,.04);
        addElectrical(a,"LIGHT_PENDANT","Підвісний світильник","LIGHT","CEILING",.35,.35,.55);
        addElectrical(a,"LIGHT_SURFACE","Накладний світильник","LIGHT","CEILING",.28,.28,.10);
        addElectrical(a,"LIGHT_TRACK","Трекове світло","LIGHT","CEILING",1.0,.08,.12);
        addElectrical(a,"LIGHT_SCONCE","Бра","LIGHT","WALL",.22,.18,.32);
        addElectrical(a,"PANEL_FLUSH_12","Щит вбудований 12М","PANEL","WALL",.30,.10,.35);
        addElectrical(a,"PANEL_FLUSH_24","Щит вбудований 24М","PANEL","WALL",.42,.10,.50);
        addElectrical(a,"PANEL_FLUSH_36","Щит вбудований 36М","PANEL","WALL",.55,.11,.65);
        addElectrical(a,"PANEL_SURFACE_24","Щит накладний 24М","PANEL","WALL",.42,.15,.50);
        addElectrical(a,"SOCKET_USB_C","Розетка • 230V + USB-C","SOCKET","WALL",.09,.035,.09);
        addElectrical(a,"SOCKET_BLACK_1","Розетка • чорна 1 пост","SOCKET","WALL",.09,.03,.09);
        addElectrical(a,"SOCKET_BLACK_2","Розетки • чорні 2 пости","SOCKET","WALL",.16,.03,.09);
        addElectrical(a,"TV_OUTLET","TV розетка","TV","WALL",.09,.03,.09);
        addElectrical(a,"TV_DOUBLE","TV + SAT блок","TV","WALL",.16,.03,.09);
        addElectrical(a,"LAN_CAT6","LAN RJ45 Cat.6","ETHERNET","WALL",.09,.03,.09);
        addElectrical(a,"LAN_DOUBLE","LAN RJ45 ×2","ETHERNET","WALL",.16,.03,.09);
        addElectrical(a,"LIGHT_DOWNLIGHT_75","Даунлайт 75 мм","LIGHT","CEILING",.075,.075,.06);
        addElectrical(a,"LIGHT_DOWNLIGHT_100","Даунлайт 100 мм","LIGHT","CEILING",.10,.10,.07);
        addElectrical(a,"LIGHT_TRACK_2M","Трек 2 м","LIGHT","CEILING",2.0,.08,.12);
        addElectrical(a,"LIGHT_LINEAR","Лінійний підвіс","LIGHT","CEILING",1.2,.08,.12);
        addElectrical(a,"LIGHT_CHANDELIER","Люстра • базова","LIGHT","CEILING",.65,.65,.55);
        addElectrical(a,"LIGHT_WALL_UPDOWN","Бра up/down","LIGHT","WALL",.12,.12,.24);
        addElectrical(a,"APPLIANCE_COOKTOP","Вивід • варильна поверхня","APPLIANCE","WALL",.09,.03,.09);
        addElectrical(a,"APPLIANCE_OVEN","Вивід • духова шафа","APPLIANCE","WALL",.09,.03,.09);
        addElectrical(a,"APPLIANCE_AC","Вивід • кондиціонер","APPLIANCE","WALL",.09,.03,.09);

        addFurniture(a,"SOFA_BASIC","Диван • прямий","SOFA",2.0,.90,.85);
        addFurniture(a,"SOFA_CORNER","Диван • кутовий","SOFA",2.5,1.65,.85);
        addFurniture(a,"BED_DOUBLE","Ліжко • двоспальне","BED",1.70,2.10,.55);
        addFurniture(a,"WARDROBE_STD","Шафа","WARDROBE",1.50,.60,2.30);
        addFurniture(a,"TABLE_DINING","Стіл","TABLE",1.20,.75,.75);
        addFurniture(a,"KITCHEN_LINE","Кухня • лінія","KITCHEN",2.40,.60,.90);
        addFurniture(a,"CABINET_STD","Тумба","CABINET",.80,.45,.90);
        addFurniture(a,"APPLIANCE_600","Техніка 600 мм","APPLIANCE",.60,.65,.90);
        addFurniture(a,"SANITARY_STD","Сантехніка","SANITARY",.70,.70,.85);
        addFurniture(a,"NIGHTSTAND_1","Тумба приліжкова • 1","NIGHTSTAND",.48,.42,.48);
        addFurniture(a,"NIGHTSTAND_2","Тумба приліжкова • 2","NIGHTSTAND",.60,.42,.55);
        addFurniture(a,"CABINET_TV_120","ТВ-тумба 120","CABINET",1.20,.40,.48);
        addFurniture(a,"CABINET_TV_180","ТВ-тумба 180","CABINET",1.80,.42,.50);
        addFurniture(a,"BED_SINGLE","Ліжко • односпальне","BED",.95,2.05,.52);
        addFurniture(a,"BED_QUEEN","Ліжко • 160","BED",1.70,2.10,.55);
        addFurniture(a,"BED_KING","Ліжко • 180","BED",1.90,2.15,.58);
        addFurniture(a,"SOFA_2","Диван • 2 місця","SOFA",1.65,.88,.84);
        addFurniture(a,"SOFA_3","Диван • 3 місця","SOFA",2.20,.92,.86);
        addFurniture(a,"CHAIR_DINING","Стілець","CHAIR",.46,.50,.88);
        addFurniture(a,"CHAIR_ARM","Крісло","CHAIR",.78,.82,.90);
        addFurniture(a,"DESK_120","Стіл робочий 120","DESK",1.20,.60,.75);
        addFurniture(a,"DESK_160","Стіл робочий 160","DESK",1.60,.70,.75);
        addFurniture(a,"TV_43","Телевізор 43\"","TV",.97,.06,.56);
        addFurniture(a,"TV_55","Телевізор 55\"","TV",1.23,.06,.71);
        addFurniture(a,"TV_65","Телевізор 65\"","TV",1.45,.07,.83);
        addFurniture(a,"TV_75","Телевізор 75\"","TV",1.67,.08,.96);
        addFurniture(a,"WARDROBE_2D","Шафа • 2 двері","WARDROBE",1.00,.60,2.30);
        addFurniture(a,"WARDROBE_3D","Шафа • 3 двері","WARDROBE",1.50,.60,2.30);
        addFurniture(a,"WARDROBE_SLIDE","Шафа-купе","WARDROBE",2.00,.65,2.40);
        addFurniture(a,"SHELF_WALL","Полиця","SHELF",1.00,.25,.25);
        addFurniture(a,"SHELF_TALL","Стелаж","SHELF",.90,.35,2.00);
        addFurniture(a,"KITCHEN_180","Кухня 1.8 м","KITCHEN",1.80,.60,.90);
        addFurniture(a,"KITCHEN_240","Кухня 2.4 м","KITCHEN",2.40,.60,.90);
        addFurniture(a,"KITCHEN_300","Кухня 3.0 м","KITCHEN",3.00,.60,.90);
        addFurniture(a,"FRIDGE_600","Холодильник 600","FRIDGE",.60,.68,2.00);
        addFurniture(a,"FRIDGE_SIDE","Холодильник side-by-side","FRIDGE",.91,.72,1.80);
        addFurniture(a,"WASHER_600","Пральна машина","WASHER",.60,.60,.85);
        addFurniture(a,"OVEN_600","Духова шафа","OVEN",.60,.58,.60);
        addFurniture(a,"SINK_BASE","Умивальник","SINK",.60,.50,.86);
        addFurniture(a,"TOILET_STD","Унітаз","TOILET",.38,.68,.80);
        addFurniture(a,"BATHTUB_170","Ванна 170","BATHTUB",1.70,.75,.60);
        addFurniture(a,"SHOWER_900","Душ 90×90","SHOWER",.90,.90,2.00);
        addFurniture(a,"RADIATOR_600","Радіатор 600","RADIATOR",1.00,.12,.60);
        addFurniture(a,"MIRROR_600","Дзеркало 600","MIRROR",.60,.04,.80);

        // v0.20.2 expanded offline interior/electrical set.
        addElectrical(a,"SOCKET_FRAME_4","Розетки • 4 пости у рамці","SOCKET","WALL",.30,.03,.09);
        addElectrical(a,"SOCKET_FRAME_5","Розетки • 5 постів у рамці","SOCKET","WALL",.37,.03,.09);
        addElectrical(a,"SOCKET_USB_DUAL","Розетка • USB-A + USB-C","SOCKET","WALL",.09,.035,.09);
        addElectrical(a,"SOCKET_FLOOR","Розетка • підлогова","SOCKET","FLOOR",.13,.13,.04);
        addElectrical(a,"SWITCH_TRIPLE","Вимикач • 3 клавіші","SWITCH","WALL",.09,.03,.09);
        addElectrical(a,"SWITCH_TOUCH","Вимикач • сенсорний","SWITCH","WALL",.09,.025,.09);
        addElectrical(a,"SWITCH_SMART","Вимикач • smart","SMART_HOME","WALL",.09,.03,.09);
        addElectrical(a,"THERMOSTAT_WALL","Термостат","SMART_HOME","WALL",.09,.035,.09);
        addElectrical(a,"SENSOR_MOTION_WALL","Датчик руху • стіна","SENSOR","WALL",.09,.06,.09);
        addElectrical(a,"SENSOR_MOTION_CEIL","Датчик руху • стеля","SENSOR","CEILING",.10,.10,.05);
        addElectrical(a,"SENSOR_SMOKE","Датчик диму","SENSOR","CEILING",.12,.12,.05);
        addElectrical(a,"CAMERA_DOME","Камера • dome","CAMERA","CEILING",.12,.12,.09);
        addElectrical(a,"CAMERA_BULLET","Камера • bullet","CAMERA","WALL",.10,.20,.10);
        addElectrical(a,"LIGHT_LED_PROFILE_1M","LED профіль 1 м","LIGHT","CEILING",1.0,.035,.025);
        addElectrical(a,"LIGHT_LED_PROFILE_2M","LED профіль 2 м","LIGHT","CEILING",2.0,.035,.025);
        addElectrical(a,"LIGHT_TRACK_MAGNET_1M","Магнітний трек 1 м","LIGHT","CEILING",1.0,.05,.055);
        addElectrical(a,"LIGHT_TRACK_MAGNET_2M","Магнітний трек 2 м","LIGHT","CEILING",2.0,.05,.055);
        addElectrical(a,"LIGHT_TRACK_HEAD","Трековий спот","LIGHT","CEILING",.08,.08,.16);
        addElectrical(a,"LIGHT_PENDANT_SMALL","Підвіс • малий","LIGHT","CEILING",.18,.18,.45);
        addElectrical(a,"LIGHT_PENDANT_TRIPLE","Підвіси • ×3","LIGHT","CEILING",.85,.20,.55);
        addElectrical(a,"LIGHT_RING","Світильник • кільце","LIGHT","CEILING",.60,.60,.18);
        addElectrical(a,"LIGHT_PANEL_600","LED панель 600×600","LIGHT","CEILING",.60,.60,.04);
        addElectrical(a,"APPLIANCE_DISHWASHER","Вивід • посудомийка","APPLIANCE","WALL",.09,.03,.09);
        addElectrical(a,"APPLIANCE_WASHER","Вивід • пральна","APPLIANCE","WALL",.09,.03,.09);
        addElectrical(a,"APPLIANCE_BOILER","Вивід • бойлер","APPLIANCE","WALL",.09,.03,.09);
        addElectrical(a,"APPLIANCE_HOOD","Вивід • витяжка","APPLIANCE","WALL",.09,.03,.09);

        addFurniture(a,"TABLE_COFFEE_100","Журнальний стіл 100","TABLE",1.00,.55,.42);
        addFurniture(a,"TABLE_COFFEE_ROUND","Журнальний стіл • круглий","TABLE",.80,.80,.42);
        addFurniture(a,"TABLE_DINING_160","Обідній стіл 160","TABLE",1.60,.90,.76);
        addFurniture(a,"TABLE_DINING_200","Обідній стіл 200","TABLE",2.00,.95,.76);
        addFurniture(a,"TABLE_BAR","Барний стіл","TABLE",1.20,.55,1.05);
        addFurniture(a,"CHAIR_OFFICE","Крісло офісне","CHAIR",.62,.62,1.10);
        addFurniture(a,"CHAIR_BAR","Стілець барний","CHAIR",.45,.48,1.05);
        addFurniture(a,"SOFA_CORNER_L","Диван кутовий • L","SOFA",2.75,1.75,.88);
        addFurniture(a,"SOFA_CORNER_R","Диван кутовий • R","SOFA",2.75,1.75,.88);
        addFurniture(a,"SOFA_BED","Диван-ліжко","SOFA",2.10,.95,.86);
        addFurniture(a,"BED_BUNK","Ліжко двоярусне","BED",1.00,2.05,1.75);
        addFurniture(a,"BED_KIDS","Ліжко дитяче","BED",.90,1.90,.60);
        addFurniture(a,"WARDROBE_4D","Шафа • 4 двері","WARDROBE",2.00,.62,2.35);
        addFurniture(a,"WARDROBE_CORNER","Шафа • кутова","WARDROBE",1.20,1.20,2.35);
        addFurniture(a,"CABINET_DRAWERS_80","Комод 80","CABINET",.80,.45,.85);
        addFurniture(a,"CABINET_DRAWERS_120","Комод 120","CABINET",1.20,.45,.85);
        addFurniture(a,"CABINET_HALL","Тумба передпокій","CABINET",1.00,.38,.95);
        addFurniture(a,"SHELF_FLOAT_600","Полиця 600","SHELF",.60,.22,.08);
        addFurniture(a,"SHELF_FLOAT_1200","Полиця 1200","SHELF",1.20,.25,.08);
        addFurniture(a,"SHELF_BOOKCASE","Книжковий стелаж","SHELF",1.00,.32,2.10);
        addFurniture(a,"KITCHEN_CORNER_240","Кухня кутова • 2.4×1.8","KITCHEN",2.40,1.80,.90);
        addFurniture(a,"KITCHEN_ISLAND_160","Кухонний острів 160","KITCHEN",1.60,.90,.92);
        addFurniture(a,"KITCHEN_TALL_600","Пенал кухонний 600","KITCHEN",.60,.60,2.20);
        addFurniture(a,"FRIDGE_BUILTIN","Холодильник вбудований","FRIDGE",.60,.58,1.78);
        addFurniture(a,"WASHER_SLIM","Пральна slim","WASHER",.60,.48,.85);
        addFurniture(a,"APPLIANCE_DISHWASHER_600","Посудомийка 600","APPLIANCE",.60,.60,.82);
        addFurniture(a,"APPLIANCE_DISHWASHER_450","Посудомийка 450","APPLIANCE",.45,.60,.82);
        addFurniture(a,"APPLIANCE_MICROWAVE","Мікрохвильова","APPLIANCE",.50,.40,.30);
        addFurniture(a,"APPLIANCE_COOKTOP","Варильна поверхня","APPLIANCE",.60,.52,.06);
        addFurniture(a,"APPLIANCE_HOOD","Витяжка","APPLIANCE",.60,.32,.45);
        addFurniture(a,"APPLIANCE_AC","Кондиціонер","APPLIANCE",.85,.22,.30);
        addFurniture(a,"APPLIANCE_BOILER","Бойлер 80 л","APPLIANCE",.48,.48,.90);
        addFurniture(a,"SINK_800","Тумба з умивальником 800","SINK",.80,.50,.86);
        addFurniture(a,"TOILET_WALL","Унітаз підвісний","TOILET",.38,.55,.42);
        addFurniture(a,"BATHTUB_180","Ванна 180","BATHTUB",1.80,.80,.60);
        addFurniture(a,"SHOWER_1200","Душ 120×90","SHOWER",1.20,.90,2.00);
        addFurniture(a,"MIRROR_800","Дзеркало 800","MIRROR",.80,.04,.90);
        addFurniture(a,"MIRROR_1200","Дзеркало 1200","MIRROR",1.20,.04,.90);
        addFurniture(a,"RADIATOR_800","Радіатор 800","RADIATOR",.80,.12,.60);
        addFurniture(a,"RADIATOR_1200","Радіатор 1200","RADIATOR",1.20,.12,.60);
    }

    private void addElectrical(List<VisualAssetPreset>a,String id,String label,String type,String place,double w,double d,double h){if(has(a,id))return;VisualAssetPreset x=new VisualAssetPreset();x.id=id;x.label=label;x.category="ELECTRICAL";x.pointType=type;x.placement=place;x.widthM=w;x.depthM=d;x.heightM=h;x.builtIn=true;x.group=electricalGroup(type);x.tags=label+" "+type+" електрика";x.defaultMountHeightM=defaultElectricalHeight(type,place);x.snapStepM=.01;a.add(x);}
    private void addFurniture(List<VisualAssetPreset>a,String id,String label,String type,double w,double d,double h){if(has(a,id))return;VisualAssetPreset x=new VisualAssetPreset();x.id=id;x.label=label;x.category="FURNITURE";x.furnitureType=type;x.placement=wallFurniture(type)?"WALL":"FLOOR";x.widthM=w;x.depthM=d;x.heightM=h;x.builtIn=true;x.group=furnitureGroup(type);x.tags=label+" "+type+" інтер'єр";x.defaultMountHeightM=defaultFurnitureHeight(type);x.snapStepM=.05;a.add(x);}
    private String electricalGroup(String t){if("LIGHT".equals(t))return "Освітлення";if("SOCKET".equals(t)||"SWITCH".equals(t)||"TV".equals(t)||"ETHERNET".equals(t))return "Розетки й керування";if("PANEL".equals(t))return "Щити";if("CAMERA".equals(t)||"SENSOR".equals(t)||"SMART_HOME".equals(t))return "Smart / безпека";return "Виводи техніки";}
    private String furnitureGroup(String t){if("BED".equals(t)||"NIGHTSTAND".equals(t)||"WARDROBE".equals(t))return "Спальня";if("SOFA".equals(t)||"TV".equals(t)||"CABINET".equals(t)||"SHELF".equals(t))return "Вітальня";if("KITCHEN".equals(t)||"FRIDGE".equals(t)||"OVEN".equals(t)||"APPLIANCE".equals(t))return "Кухня / техніка";if("SINK".equals(t)||"TOILET".equals(t)||"BATHTUB".equals(t)||"SHOWER".equals(t)||"SANITARY".equals(t)||"MIRROR".equals(t))return "Санвузол";if("TABLE".equals(t)||"CHAIR".equals(t)||"DESK".equals(t))return "Столи / робоче";if("RADIATOR".equals(t))return "Інженерія";return "Інше";}
    private boolean wallFurniture(String t){return "TV".equals(t)||"MIRROR".equals(t)||"SHELF".equals(t)||"RADIATOR".equals(t);}
    private double defaultFurnitureHeight(String t){if("TV".equals(t))return .85;if("MIRROR".equals(t))return 1.0;if("SHELF".equals(t))return 1.2;if("RADIATOR".equals(t))return .10;if("APPLIANCE".equals(t))return 0;return 0;}
    private double defaultElectricalHeight(String t,String place){if("CEILING".equals(place))return 0;if("SWITCH".equals(t)||"SMART_HOME".equals(t))return 1.05;if("PANEL".equals(t))return 1.40;if("TV".equals(t)||"ETHERNET".equals(t))return .30;if("CAMERA".equals(t))return 2.30;if("SENSOR".equals(t))return 2.20;return .30;}
    private boolean has(List<VisualAssetPreset>a,String id){for(VisualAssetPreset x:a)if(id.equals(x.id))return true;return false;}
    private void save(List<VisualAssetPreset>a){try{JSONArray j=new JSONArray();for(VisualAssetPreset x:a)j.put(x.toJson());AtomicFileStore.writeText(file,j.toString(2));}catch(Exception e){reporter.error("ASSET_SAVE","Could not save visual asset catalog",e);throw new RuntimeException(e);}}
    private String read(InputStream in)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;long total=0;while((n=in.read(b))>0){total+=n;if(total>MAX_PACK_BYTES)throw new IOException("Пакет моделей завеликий");o.write(b,0,n);}return o.toString("UTF-8");}
}
