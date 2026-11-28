package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.FurnitureItem;
import com.elektoryk.scan.model.RoomZone;
import com.elektoryk.scan.model.RoomFootprint;
import com.elektoryk.scan.model.SemanticEntity;

import java.util.List;

/** Turns stable semantic 3D entities into editable Digital Twin furniture/objects. */
public final class SemanticFurniturePromoter {
    public void apply(ApartmentPlan plan,List<SemanticEntity> entities){
        if(plan==null||entities==null)return;
        for(SemanticEntity e:entities){
            if(e==null||e.visualOnly||e.confidence<.52||e.observations<2)continue;
            FurnitureItem.Type ft=map(e.type);if(ft==null)continue;
            double w=Math.max(.10,e.widthM()),d=Math.max(.10,e.depthM()),h=Math.max(.10,e.heightM());
            if(w>4.5||d>4.5||h>3.6)continue;
            double x=e.centerX(),z=e.centerZ();
            // Identity must follow the persistent semantic track, not a proximity radius. The old
            // 38 cm de-duplication silently discarded legitimate neighbouring chairs/nightstands.
            // It also made repeated promotion unable to refine the same object deterministically.
            FurnitureItem f=findBySemanticId(plan,e.id);
            if(f==null){
                // Backward compatibility for plans created before sourceSemanticId existed: avoid
                // creating one duplicate beside an old SEMANTIC_* item, but never use proximity to
                // suppress two new scanner tracks.
                if(hasLegacyNearby(plan,x,z,ft,.22))continue;
                f=new FurnitureItem();f.sourceSemanticId=e.id;plan.furniture.add(f);
            }
            // A user can lock an auto-created item in the editor to freeze manual corrections.
            if(f.locked)continue;
            f.type=ft;f.name=e.label;f.assetKey="SEMANTIC_"+e.type.name();
            f.x=x;f.z=z;f.y=Math.max(0,e.minY-plan.floorY);f.widthM=w;f.depthM=d;f.heightM=h;f.rotationDeg=e.rotationDeg;
            f.blocksElectricalPlacement=e.type!=SemanticEntity.Type.CURTAIN&&e.type!=SemanticEntity.Type.LAMP;
            // The track may change class or position after additional views. Recompute these
            // derived fields every time rather than retaining the previous wall/room assignment.
            f.placement=isWallMounted(e.type)?"WALL":"FLOOR";
            RoomZone r=roomAt(plan,x,z);f.roomId=r==null?"":r.id;
        }
    }
    private FurnitureItem.Type map(SemanticEntity.Type t){
        switch(t){
            case CABINET:case DRAWERS:return FurnitureItem.Type.CABINET;
            case WARDROBE:return FurnitureItem.Type.WARDROBE;
            case BED:return FurnitureItem.Type.BED;
            case SOFA:case ARMCHAIR:return FurnitureItem.Type.SOFA;
            case TABLE:return FurnitureItem.Type.TABLE;
            case DESK:return FurnitureItem.Type.DESK;
            case SINK:return FurnitureItem.Type.SINK;
            case TOILET:return FurnitureItem.Type.TOILET;
            case SHOWER:return FurnitureItem.Type.SHOWER;
            case BATHTUB:return FurnitureItem.Type.BATHTUB;
            case REFRIGERATOR:return FurnitureItem.Type.FRIDGE;
            case WASHER:return FurnitureItem.Type.WASHER;
            case OVEN:return FurnitureItem.Type.OVEN;
            case MICROWAVE:case STOVE:case DISHWASHER:return FurnitureItem.Type.APPLIANCE;
            case TELEVISION:return FurnitureItem.Type.TV;
            case CHAIR:case STOOL:return FurnitureItem.Type.CHAIR;
            case RADIATOR:return FurnitureItem.Type.RADIATOR;
            case MIRROR:return FurnitureItem.Type.MIRROR;
            case SHELF:case BOOKCASE:return FurnitureItem.Type.SHELF;
            case BENCH:case BICYCLE:case STAIRS:case COLUMN:
            case CURTAIN:case LAMP:case COMPUTER:case OBJECT:return FurnitureItem.Type.CUSTOM;
            default:return null;
        }
    }
    private boolean isWallMounted(SemanticEntity.Type type){
        return type==SemanticEntity.Type.MIRROR||type==SemanticEntity.Type.SHELF
                ||type==SemanticEntity.Type.TELEVISION||type==SemanticEntity.Type.RADIATOR;
    }
    private FurnitureItem findBySemanticId(ApartmentPlan p,String id){
        if(id==null||id.length()==0)return null;for(FurnitureItem f:p.furniture)if(id.equals(f.sourceSemanticId))return f;return null;
    }
    private boolean hasLegacyNearby(ApartmentPlan p,double x,double z,FurnitureItem.Type t,double r){
        for(FurnitureItem f:p.furniture)if(f.type==t&&(f.sourceSemanticId==null||f.sourceSemanticId.length()==0)&&f.assetKey!=null&&f.assetKey.startsWith("SEMANTIC_")&&Math.hypot(f.x-x,f.z-z)<r)return true;return false;
    }
    private RoomZone roomAt(ApartmentPlan p,double x,double z){for(RoomZone r:p.rooms)if(RoomFootprint.contains(p,r,x,z))return r;return null;}
}
