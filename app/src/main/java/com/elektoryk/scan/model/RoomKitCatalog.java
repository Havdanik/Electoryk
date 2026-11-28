package com.elektoryk.scan.model;

/** Ready-to-place room kits. Geometry is deliberately lightweight and editable after placement. */
public final class RoomKitCatalog {
    private RoomKitCatalog(){}
    public static final class Item{
        public final FurnitureItem.Type type; public final String assetKey; public final double nx,nz,rot;
        Item(FurnitureItem.Type type,String assetKey,double nx,double nz,double rot){this.type=type;this.assetKey=assetKey;this.nx=nx;this.nz=nz;this.rot=rot;}
    }
    public static final class Kit{
        public final String key,label; public final RoomZone.RoomType roomType; public final Item[] items;
        Kit(String key,String label,RoomZone.RoomType roomType,Item...items){this.key=key;this.label=label;this.roomType=roomType;this.items=items;}
    }
    public static final Kit[] KITS={
        new Kit("KIT_BEDROOM","Спальня • базова",RoomZone.RoomType.BEDROOM,
            new Item(FurnitureItem.Type.BED,"BED_QUEEN",.50,.58,0),new Item(FurnitureItem.Type.NIGHTSTAND,"NIGHTSTAND_1",.28,.58,0),new Item(FurnitureItem.Type.NIGHTSTAND,"NIGHTSTAND_1",.72,.58,0),new Item(FurnitureItem.Type.WARDROBE,"WARDROBE_3D",.15,.25,90)),
        new Kit("KIT_LIVING","Вітальня • TV + диван",RoomZone.RoomType.LIVING_ROOM,
            new Item(FurnitureItem.Type.SOFA,"SOFA_3",.50,.72,180),new Item(FurnitureItem.Type.TV,"TV_55",.50,.15,0),new Item(FurnitureItem.Type.CABINET,"CABINET_TV_180",.50,.20,0),new Item(FurnitureItem.Type.TABLE,"TABLE_COFFEE_100",.50,.48,0)),
        new Kit("KIT_OFFICE","Кабінет • робочий",RoomZone.RoomType.OFFICE,
            new Item(FurnitureItem.Type.DESK,"DESK_160",.50,.18,0),new Item(FurnitureItem.Type.CHAIR,"CHAIR_OFFICE",.50,.38,0),new Item(FurnitureItem.Type.SHELF,"SHELF_TALL",.15,.20,90)),
        new Kit("KIT_KITCHEN","Кухня • лінія",RoomZone.RoomType.KITCHEN,
            new Item(FurnitureItem.Type.KITCHEN,"KITCHEN_300",.50,.13,0),new Item(FurnitureItem.Type.FRIDGE,"FRIDGE_600",.10,.13,0),new Item(FurnitureItem.Type.TABLE,"TABLE_DINING_160",.55,.65,0),new Item(FurnitureItem.Type.CHAIR,"CHAIR_DINING",.38,.65,0),new Item(FurnitureItem.Type.CHAIR,"CHAIR_DINING",.72,.65,180)),
        new Kit("KIT_BATH","Санвузол • базовий",RoomZone.RoomType.BATHROOM,
            new Item(FurnitureItem.Type.SHOWER,"SHOWER_900",.18,.18,0),new Item(FurnitureItem.Type.TOILET,"TOILET_STD",.50,.22,0),new Item(FurnitureItem.Type.SINK,"SINK_BASE",.78,.22,0),new Item(FurnitureItem.Type.MIRROR,"MIRROR_800",.78,.18,0)),
        new Kit("KIT_STUDIO","Студія • компакт",RoomZone.RoomType.LIVING_ROOM,
            new Item(FurnitureItem.Type.SOFA,"SOFA_2",.50,.70,180),new Item(FurnitureItem.Type.TV,"TV_43",.50,.14,0),new Item(FurnitureItem.Type.TABLE,"TABLE_DINING",.75,.50,90),new Item(FurnitureItem.Type.KITCHEN,"KITCHEN_180",.15,.20,90))
    };
    public static String[] keys(){String[] a=new String[KITS.length];for(int i=0;i<a.length;i++)a[i]=KITS[i].key;return a;}
    public static String[] labels(){String[] a=new String[KITS.length];for(int i=0;i<a.length;i++)a[i]=KITS[i].label;return a;}
    public static Kit find(String key){if(key!=null)for(Kit k:KITS)if(k.key.equals(key))return k;return KITS[0];}
}
