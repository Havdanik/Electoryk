package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.*;
import java.util.*;

/** Builds an editable bill of materials + labor from the same points/routes used by 2D/3D. */
public class CommercialPlanEngine {
    public CommercialPlan build(ElectricalProject project, InstallationRoutingResult routing,
                                FieldWorkPlan field, FloorPlan floor, CommercialPlan previous){
        CommercialPlan out=new CommercialPlan();
        out.projectId=project==null?"":project.id;
        if(previous!=null){out.materialMarkupPercent=previous.materialMarkupPercent;out.laborMarkupPercent=previous.laborMarkupPercent;out.discountPercent=previous.discountPercent;}
        Map<String,CommercialLine> old=new HashMap<>();
        if(previous!=null)for(CommercialLine l:previous.lines)old.put(l.stableKey,l);

        if(routing!=null){
            for(Map.Entry<String,Double> e:routing.materials.cableM.entrySet())
                add(out,old,material("MAT:CABLE:"+e.getKey(),"Кабель "+e.getKey(),"м",e.getValue()));
            if(routing.materials.corrugatedConduitM>0)add(out,old,material("MAT:CORR","Гофра","м",routing.materials.corrugatedConduitM));
            if(routing.materials.trayM>0)add(out,old,material("MAT:TRAY","Лоток / кабельний канал","м",routing.materials.trayM));
            if(routing.materials.fasteners>0)add(out,old,material("MAT:FAST","Кріплення кабелю","шт",routing.materials.fasteners));
            // Standard/deep wall boxes are derived from the actual electrical mechanisms below.
            if(floor==null&&routing.materials.deepSocketBoxes>0)add(out,old,material("MAT:DEEP_BOX","Глибокий підрозетник","шт",routing.materials.deepSocketBoxes));
            if(routing.materials.junctionBoxes>0)add(out,old,material("MAT:JBOX","Розподільча коробка","шт",routing.materials.junctionBoxes));
            if(routing.materials.wallPenetrations>0)add(out,old,labor("LAB:PASS","Проходка через стіну","шт",routing.materials.wallPenetrations));
            if(routing.materials.wallChaseM>0)add(out,old,labor("LAB:CHASE","Штроблення стін","м",routing.materials.wallChaseM));
            if(routing.materials.floorRouteM>0)add(out,old,labor("LAB:FLOOR","Прокладання по підлозі","м",routing.materials.floorRouteM));
        }

        if(floor!=null) addPointMaterialsAndLabor(out,old,project,floor);

        if(field!=null){
            for(CircuitProtectionPlan c:field.circuits){
                String device=c.protection+" "+c.suggestedCurve+c.suggestedBreakerA+"A "+c.poles+"P";
                add(out,old,material("MAT:PROTECT:"+c.circuitNo,"Захист групи "+c.circuitNo+" • "+device,"шт",1));
            }
            if(!field.circuits.isEmpty())add(out,old,labor("LAB:PANEL_GROUP","Збірка / маркування групи щита","група",field.circuits.size()));
        }
        applyPrices(out);out.recalc();return out;
    }

    private void addPointMaterialsAndLabor(CommercialPlan out,Map<String,CommercialLine> old,ElectricalProject project,FloorPlan floor){
        Map<String,Double> materials=new LinkedHashMap<>();Map<String,String> names=new LinkedHashMap<>();
        int sockets=0,switches=0,lights=0,other=0,panels=0,wallBoxes=0;
        for(ElectricalPoint p:floor.points){
            int modules=Math.max(1,p.moduleCount);String asset=p.assetKey==null?"":p.assetKey;
            switch(p.type){
                case SOCKET:
                    sockets+=modules;
                    if("SOCKET_DOUBLE_BLOCK".equals(asset)){
                        inc(materials,names,"MAT:POINT:SOCKET_DOUBLE","Розетка подвійна • механізм",1);wallBoxes+=1;
                    }else{
                        inc(materials,names,"MAT:POINT:SOCKET_MECH","Механізм розетки",modules);wallBoxes+=modules;addFrame(materials,names,modules);
                    }
                    break;
                case SWITCH:
                    switches+=modules;inc(materials,names,"MAT:POINT:SWITCH:"+switchVariant(asset),switchName(asset),modules);wallBoxes+=modules;addFrame(materials,names,modules);break;
                case LIGHT:
                    lights++;inc(materials,names,"MAT:POINT:LIGHT:"+safeAsset(asset,"LIGHT_BASIC"),lightName(asset),1);break;
                case PANEL:
                    panels++;inc(materials,names,"MAT:POINT:PANEL:"+safeAsset(asset,"PANEL_BASIC"),panelName(asset),1);break;
                case TV:
                    other++;inc(materials,names,"MAT:POINT:TV","Механізм TV",modules);wallBoxes+=modules;addFrame(materials,names,modules);break;
                case ETHERNET:
                    other++;inc(materials,names,"MAT:POINT:LAN","Механізм RJ45 / LAN",modules);wallBoxes+=modules;addFrame(materials,names,modules);break;
                case SMART_HOME:
                    other++;inc(materials,names,"MAT:POINT:SMART","Модуль / клавіша Smart Home",modules);wallBoxes+=modules;addFrame(materials,names,modules);break;
                case SENSOR:
                    other++;inc(materials,names,"MAT:POINT:SENSOR","Датчик",modules);if(p.wallId!=null&&!p.wallId.isEmpty())wallBoxes+=modules;break;
                case CAMERA:
                    other++;inc(materials,names,"MAT:POINT:CAMERA","Камера / монтажна точка",1);break;
                case APPLIANCE:
                default:
                    other++;inc(materials,names,"MAT:POINT:APPLIANCE","Підключення техніки / силова точка",modules);if(p.wallId!=null&&!p.wallId.isEmpty())wallBoxes+=modules;break;
            }
        }
        boolean deep=project!=null&&project.settings!=null&&project.settings.junctionMethod==ProjectSettings.JunctionMethod.DEEP_SOCKET_BOXES;
        if(wallBoxes>0)inc(materials,names,deep?"MAT:BOX:DEEP":"MAT:BOX:STD",deep?"Підрозетник глибокий":"Підрозетник стандартний",wallBoxes);
        for(Map.Entry<String,Double> e:materials.entrySet())add(out,old,material(e.getKey(),names.get(e.getKey()),"шт",e.getValue()));
        if(sockets>0)add(out,old,labor("LAB:SOCKET","Монтаж розеткового модуля","шт",sockets));
        if(switches>0)add(out,old,labor("LAB:SWITCH","Монтаж вимикача","шт",switches));
        if(lights>0)add(out,old,labor("LAB:LIGHT","Монтаж точки / світильника","шт",lights));
        if(other>0)add(out,old,labor("LAB:POINT_OTHER","Монтаж спеціальної точки","шт",other));
        if(wallBoxes>0)add(out,old,labor("LAB:BOX","Встановлення підрозетника","шт",wallBoxes));
        if(panels>0)add(out,old,labor("LAB:PANEL_INSTALL","Монтаж корпусу щита","шт",panels));
    }

    private void addFrame(Map<String,Double> m,Map<String,String> names,int modules){
        int n=Math.max(1,Math.min(6,modules));String k="MAT:FRAME:"+n;inc(m,names,k,"Рамка "+n+"-постова",1);
    }
    private String switchVariant(String asset){if(asset==null||asset.isEmpty())return "BASIC";if(asset.contains("DOUBLE"))return "DOUBLE";if(asset.contains("PASS"))return "PASS";if(asset.contains("DIMMER"))return "DIMMER";return "SINGLE";}
    private String switchName(String asset){String v=switchVariant(asset);if("DOUBLE".equals(v))return "Вимикач двоклавішний • механізм";if("PASS".equals(v))return "Вимикач прохідний • механізм";if("DIMMER".equals(v))return "Димер • механізм";return "Вимикач одноклавішний • механізм";}
    private String lightName(String asset){if(asset!=null){if(asset.contains("PENDANT"))return "Світильник підвісний";if(asset.contains("SURFACE"))return "Світильник накладний";if(asset.contains("TRACK"))return "Трек / трекове світло";if(asset.contains("SCONCE"))return "Бра";if(asset.contains("SPOT"))return "Світильник точковий";}return "Світильник / точка освітлення";}
    private String panelName(String asset){if(asset!=null){if(asset.contains("FLUSH_12"))return "Щит вбудований 12М";if(asset.contains("FLUSH_24"))return "Щит вбудований 24М";if(asset.contains("FLUSH_36"))return "Щит вбудований 36М";if(asset.contains("SURFACE_24"))return "Щит накладний 24М";}return "Корпус електрощита";}
    private String safeAsset(String asset,String fallback){return asset==null||asset.isEmpty()?fallback:asset.replaceAll("[^A-Za-z0-9_-]","_");}
    private void inc(Map<String,Double> m,Map<String,String> names,String key,String name,double qty){m.put(key,m.containsKey(key)?m.get(key)+qty:qty);names.put(key,name);}

    private void add(CommercialPlan out,Map<String,CommercialLine> old,CommercialLine fresh){
        CommercialLine p=old.get(fresh.stableKey);
        if(p!=null){fresh.id=p.id;fresh.stockQty=p.stockQty;fresh.packSize=p.packSize;fresh.unitCost=p.unitCost;fresh.unitClientPrice=p.unitClientPrice;fresh.owner=p.owner;fresh.purchased=p.purchased;fresh.received=p.received;fresh.unavailable=p.unavailable;fresh.includeInEstimate=p.includeInEstimate;fresh.supplier=p.supplier;fresh.replacementName=p.replacementName;fresh.note=p.note;}
        fresh.purchaseQty=roundedPurchase(Math.max(0,fresh.requiredQty-fresh.stockQty),fresh.packSize);out.lines.add(fresh);
    }
    private void applyPrices(CommercialPlan p){for(CommercialLine l:p.lines){if(l.category==CommercialLine.Category.MATERIAL){if(l.unitClientPrice<=0&&l.unitCost>0)l.unitClientPrice=l.unitCost*(1.0+p.materialMarkupPercent/100.0);}else if(l.category==CommercialLine.Category.LABOR){if(l.unitClientPrice<=0&&l.unitCost>0)l.unitClientPrice=l.unitCost*(1.0+p.laborMarkupPercent/100.0);}}}
    private double roundedPurchase(double need,double pack){if(need<=0)return 0;if(pack<=0)return need;return Math.ceil(need/pack)*pack;}
    private CommercialLine material(String key,String name,String unit,double qty){CommercialLine l=new CommercialLine();l.stableKey=key;l.category=CommercialLine.Category.MATERIAL;l.name=name;l.unit=unit;l.requiredQty=Math.max(0,qty);return l;}
    private CommercialLine labor(String key,String name,String unit,double qty){CommercialLine l=new CommercialLine();l.stableKey=key;l.category=CommercialLine.Category.LABOR;l.name=name;l.unit=unit;l.requiredQty=Math.max(0,qty);l.owner=CommercialLine.ProcurementOwner.MASTER;return l;}
}
