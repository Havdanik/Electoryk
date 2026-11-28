package com.elektoryk.scan.model;

/** Offline floor/ceiling/skirting finish presets used by the 3D interior editor. */
public final class RoomFinishCatalog {
    private RoomFinishCatalog(){}
    public static final class Preset{
        public final String key,label,patternKey;
        public final int colorArgb;
        public final double scale;
        public Preset(String key,String label,int colorArgb,String patternKey,double scale){this.key=key;this.label=label;this.colorArgb=colorArgb;this.patternKey=patternKey;this.scale=scale;}
    }
    private static int rgb(int r,int g,int b){return 0xFF000000|(r<<16)|(g<<8)|b;}

    public static final Preset[] FLOORS={
        new Preset("FLOOR_RAW","Як відскановано",rgb(82,82,88),"RAW",1.0),
        new Preset("FLOOR_CONCRETE","Бетон / стяжка",rgb(126,126,122),"CONCRETE",1.0),
        new Preset("FLOOR_LAMINATE_OAK","Ламінат • дуб світлий",rgb(174,139,96),"PLANK",1.0),
        new Preset("FLOOR_LAMINATE_DARK","Ламінат • дуб темний",rgb(92,68,48),"PLANK",1.0),
        new Preset("FLOOR_PARQUET","Паркет • ялинка",rgb(150,105,68),"HERRINGBONE",0.9),
        new Preset("FLOOR_VINYL","Вініл • теплий дуб",rgb(160,125,86),"PLANK",1.15),
        new Preset("FLOOR_TILE_WHITE","Плитка • біла 60×60",rgb(218,218,214),"TILE",1.0),
        new Preset("FLOOR_TILE_GRAY","Плитка • сіра 60×60",rgb(145,149,150),"TILE",1.0),
        new Preset("FLOOR_TILE_GRAPHITE","Плитка • графіт",rgb(68,72,75),"TILE",1.0),
        new Preset("FLOOR_MARBLE","Керамограніт • мармур",rgb(210,209,204),"MARBLE",1.0),
        new Preset("FLOOR_TERRAZZO","Тераццо",rgb(178,174,164),"TERRAZZO",0.8),
        new Preset("FLOOR_CARPET","Ковролін",rgb(116,110,105),"CARPET",1.0)
    };

    public static final Preset[] CEILINGS={
        new Preset("CEILING_RAW","Як відскановано",rgb(105,105,108),"RAW",1.0),
        new Preset("CEILING_WHITE","Біла матова",rgb(238,238,235),"NONE",1.0),
        new Preset("CEILING_WARM","Теплий білий",rgb(235,229,216),"NONE",1.0),
        new Preset("CEILING_CONCRETE","Відкритий бетон",rgb(126,126,122),"CONCRETE",1.0),
        new Preset("CEILING_DRYWALL","Гіпсокартон",rgb(225,225,220),"NONE",1.0),
        new Preset("CEILING_SLAT","Дерев'яні рейки",rgb(126,88,58),"SLAT",1.0),
        new Preset("CEILING_BLACK","Чорна технічна",rgb(34,35,38),"NONE",1.0)
    };

    public static final String[] SKIRT_KEYS={"SKIRT_NONE","SKIRT_WHITE_60","SKIRT_WHITE_100","SKIRT_BLACK_60","SKIRT_WOOD_60","SKIRT_SHADOW"};
    public static final String[] SKIRT_LABELS={"Без плінтуса","Білий 60 мм","Білий 100 мм","Чорний 60 мм","Дерево 60 мм","Тіньовий профіль"};

    public static String[] floorKeys(){String[] a=new String[FLOORS.length];for(int i=0;i<a.length;i++)a[i]=FLOORS[i].key;return a;}
    public static String[] floorLabels(){String[] a=new String[FLOORS.length];for(int i=0;i<a.length;i++)a[i]=FLOORS[i].label;return a;}
    public static String[] ceilingKeys(){String[] a=new String[CEILINGS.length];for(int i=0;i<a.length;i++)a[i]=CEILINGS[i].key;return a;}
    public static String[] ceilingLabels(){String[] a=new String[CEILINGS.length];for(int i=0;i<a.length;i++)a[i]=CEILINGS[i].label;return a;}
    public static Preset floor(String key){if(key!=null)for(Preset p:FLOORS)if(p.key.equals(key))return p;return FLOORS[0];}
    public static Preset ceiling(String key){if(key!=null)for(Preset p:CEILINGS)if(p.key.equals(key))return p;return CEILINGS[0];}
}
