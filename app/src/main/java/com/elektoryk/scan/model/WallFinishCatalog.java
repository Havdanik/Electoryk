package com.elektoryk.scan.model;

/** Built-in offline finish catalog for the Digital Twin editor. */
public final class WallFinishCatalog {
    private WallFinishCatalog() {}
    public static final class Preset {
        public final String key,label,finishKey,patternKey;
        public final double thicknessM;
        public final int colorArgb;
        Preset(String key,String label,String finishKey,double thicknessM,int colorArgb,String patternKey){
            this.key=key;this.label=label;this.finishKey=finishKey;this.thicknessM=thicknessM;this.colorArgb=colorArgb;this.patternKey=patternKey;
        }
    }
    private static int rgb(int r,int g,int b){return 0xFF000000|(r<<16)|(g<<8)|b;}
    public static final Preset[] PRESETS = new Preset[]{
        new Preset("RAW_SCAN","Як відскановано","RAW",0,0,""),
        new Preset("CONCRETE_RAW","Бетон • чорновий","RAW",0,rgb(118,118,116),"CONCRETE"),
        new Preset("BRICK_RAW","Цегла • відкрита","RAW",0,rgb(151,91,66),"BRICK"),
        new Preset("BLOCK_RAW","Газоблок / блок","RAW",0,rgb(184,181,169),"BLOCK"),
        new Preset("PLASTER_GRAY","Штукатурка • сіра","PLASTER",0.015,rgb(174,170,160),"PLASTER"),
        new Preset("PLASTER_LIGHT","Штукатурка • світла","PLASTER",0.015,rgb(201,197,188),"PLASTER"),
        new Preset("PUTTY_WHITE","Шпаклівка • біла","PUTTY",0.003,rgb(229,227,220),"NONE"),
        new Preset("PAINT_WHITE","Фарба • біла","PAINT",0.001,rgb(238,238,234),"NONE"),
        new Preset("PAINT_WARM","Фарба • теплий білий","PAINT",0.001,rgb(232,225,210),"NONE"),
        new Preset("PAINT_GRAY","Фарба • світло-сіра","PAINT",0.001,rgb(190,193,196),"NONE"),
        new Preset("PAINT_GRAPHITE","Фарба • графіт","PAINT",0.001,rgb(73,77,82),"NONE"),
        new Preset("PAINT_BEIGE","Фарба • беж","PAINT",0.001,rgb(205,190,166),"NONE"),
        new Preset("PAINT_GREEN","Фарба • оливкова","PAINT",0.001,rgb(128,139,111),"NONE"),
        new Preset("WALLPAPER_LIGHT","Шпалери • світлі","DECOR",0.002,rgb(225,218,204),"WALLPAPER"),
        new Preset("WALLPAPER_GRAY","Шпалери • сірі","DECOR",0.002,rgb(165,166,162),"WALLPAPER"),
        new Preset("WALLPAPER_GEOM","Шпалери • геометрія","DECOR",0.002,rgb(194,188,176),"WALLPAPER_GEOM"),
        new Preset("TILE_WHITE","Плитка • біла","TILE",0.012,rgb(221,224,224),"TILE"),
        new Preset("TILE_GRAY","Плитка • сіра","TILE",0.012,rgb(145,151,154),"TILE"),
        new Preset("TILE_MARBLE","Плитка • мармур","TILE",0.012,rgb(215,214,208),"MARBLE"),
        new Preset("DECOR_CONCRETE","Декор • бетон","DECOR",0.004,rgb(129,128,124),"CONCRETE"),
        new Preset("DECOR_WOOD","Панелі • дерево","DECOR",0.012,rgb(139,102,69),"WOOD"),
        new Preset("DECOR_SLAT","Рейки • дерево","DECOR",0.018,rgb(125,86,55),"SLAT"),
        new Preset("PAINT_BLACK","Фарба • чорна","PAINT",0.001,rgb(28,30,32),"NONE"),
        new Preset("PAINT_BLUE","Фарба • пилова синя","PAINT",0.001,rgb(98,120,137),"NONE"),
        new Preset("PAINT_TERRACOTTA","Фарба • теракота","PAINT",0.001,rgb(170,105,80),"NONE"),
        new Preset("PAINT_SAGE","Фарба • шавлія","PAINT",0.001,rgb(150,160,139),"NONE"),
        new Preset("WALLPAPER_LINEN","Шпалери • льон","DECOR",0.002,rgb(207,198,181),"WALLPAPER"),
        new Preset("WALLPAPER_DARK","Шпалери • темні","DECOR",0.002,rgb(74,72,69),"WALLPAPER"),
        new Preset("WALLPAPER_STRIPE","Шпалери • смуга","DECOR",0.002,rgb(190,185,176),"SLAT"),
        new Preset("TILE_BEIGE","Плитка • бежева","TILE",0.012,rgb(198,181,157),"TILE"),
        new Preset("TILE_GRAPHITE","Плитка • графіт","TILE",0.012,rgb(75,79,82),"TILE"),
        new Preset("TILE_GREEN","Плитка • зелена","TILE",0.012,rgb(101,132,116),"TILE"),
        new Preset("TILE_METRO_WHITE","Плитка • метро біла","TILE",0.010,rgb(224,224,220),"BRICK"),
        new Preset("TILE_METRO_GREEN","Плитка • метро зелена","TILE",0.010,rgb(83,116,94),"BRICK"),
        new Preset("DECOR_MICROCEMENT","Мікроцемент","DECOR",0.003,rgb(142,140,135),"CONCRETE"),
        new Preset("DECOR_STONE_LIGHT","Камінь • світлий","DECOR",0.020,rgb(185,178,164),"BLOCK"),
        new Preset("DECOR_STONE_DARK","Камінь • темний","DECOR",0.020,rgb(88,84,78),"BLOCK"),
        new Preset("DECOR_BRICK_WHITE","Декоративна цегла • біла","DECOR",0.015,rgb(218,215,207),"BRICK"),
        new Preset("DECOR_BRICK_RED","Декоративна цегла • червона","DECOR",0.015,rgb(154,83,58),"BRICK"),
        new Preset("DECOR_PANEL_GRAY","Панелі • сірі","DECOR",0.012,rgb(115,116,118),"SLAT"),
        new Preset("DECOR_PANEL_BLACK","Панелі • чорні","DECOR",0.012,rgb(42,43,45),"SLAT"),
        new Preset("DECOR_ACOUSTIC_WOOD","Акустичні рейки • дерево","DECOR",0.022,rgb(113,76,49),"SLAT")
    };
    public static final String[] SURFACE_KEYS={"AS_SCANNED","RAW_CONCRETE","RAW_BRICK","RAW_BLOCK","PLASTERED","PUTTIED","PAINTED","TILED"};
    public static final String[] SURFACE_LABELS={"Як відскановано","Бетон","Цегла","Блок","Є штукатурка","Є шпаклівка","Пофарбовано","Є плитка"};
    public static String[] keys(){String[] a=new String[PRESETS.length];for(int i=0;i<a.length;i++)a[i]=PRESETS[i].key;return a;}
    public static String[] labels(){String[] a=new String[PRESETS.length];for(int i=0;i<a.length;i++)a[i]=PRESETS[i].label;return a;}
    public static Preset find(String key){if(key!=null)for(Preset p:PRESETS)if(p.key.equals(key))return p;return PRESETS[0];}
    public static int surfaceColor(String key,String material){
        if("RAW_CONCRETE".equals(key)||("AS_SCANNED".equals(key)&&"CONCRETE".equals(material)))return rgb(116,116,113);
        if("RAW_BRICK".equals(key)||("AS_SCANNED".equals(key)&&"BRICK".equals(material)))return rgb(154,92,65);
        if("RAW_BLOCK".equals(key)||("AS_SCANNED".equals(key)&&"BLOCK".equals(material)))return rgb(184,181,168);
        if("PLASTERED".equals(key))return rgb(176,171,160);if("PUTTIED".equals(key))return rgb(224,222,216);
        if("PAINTED".equals(key))return rgb(221,221,218);if("TILED".equals(key))return rgb(176,183,186);
        return rgb(78,78,84);
    }
    public static String surfacePattern(String key,String material){
        if("RAW_BRICK".equals(key)||("AS_SCANNED".equals(key)&&"BRICK".equals(material)))return "BRICK";
        if("RAW_CONCRETE".equals(key)||("AS_SCANNED".equals(key)&&"CONCRETE".equals(material)))return "CONCRETE";
        if("RAW_BLOCK".equals(key)||("AS_SCANNED".equals(key)&&"BLOCK".equals(material)))return "BLOCK";
        if("TILED".equals(key))return "TILE";return "";
    }
}
