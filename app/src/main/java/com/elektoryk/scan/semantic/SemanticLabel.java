package com.elektoryk.scan.semantic;

/** Canonical indoor subset of ADE20K's 150 semantic categories. */
public enum SemanticLabel {
    WALL(0,"стіна",true,false),
    FLOOR(3,"підлога",true,false),
    CEILING(5,"стеля",true,false),
    BED(7,"ліжко",false,true),
    WINDOW(8,"вікно",true,true),
    CABINET(10,"шафа/тумба",false,true),
    PERSON(12,"людина",false,true),
    DOOR(14,"двері",true,true),
    TABLE(15,"стіл",false,true),
    CURTAIN(18,"штора",false,true),
    CHAIR(19,"стілець",false,true),
    SOFA(23,"диван",false,true),
    SHELF(24,"полиця",false,true),
    MIRROR(27,"дзеркало",false,true),
    ARMCHAIR(30,"крісло",false,true),
    SEAT(31,"сидіння",false,true),
    DESK(33,"письмовий стіл",false,true),
    WARDROBE(35,"гардероб/шафа",false,true),
    LAMP(36,"лампа",false,true),
    BATHTUB(37,"ванна",false,true),
    COLUMN(42,"колона",true,true),
    DRAWERS(44,"комод",false,true),
    COUNTER(45,"стільниця/прилавок",false,true),
    SINK(47,"мийка",false,true),
    REFRIGERATOR(50,"холодильник",false,true),
    STAIRS(53,"сходи",true,true),
    BOOKCASE(62,"книжкова шафа",false,true),
    TOILET(65,"унітаз",false,true),
    BENCH(69,"лава",false,true),
    STOVE(71,"плита",false,true),
    COMPUTER(74,"комп'ютер",false,true),
    SWIVEL_CHAIR(75,"офісне крісло",false,true),
    TELEVISION(89,"телевізор",false,true),
    WASHER(107,"пральна машина",false,true),
    STOOL(110,"табурет",false,true),
    OVEN(118,"духовка",false,true),
    MICROWAVE(124,"мікрохвильовка",false,true),
    BICYCLE(127,"велосипед",false,true),
    DISHWASHER(129,"посудомийка",false,true),
    SHOWER(145,"душ",false,true),
    RADIATOR(146,"радіатор",false,true),
    OBJECT(-1,"об'єкт",false,true),
    UNKNOWN(-2,"невідомо",false,false);

    public final int adeIndex;public final String ua;public final boolean architectural;public final boolean objectLike;
    SemanticLabel(int i,String ua,boolean architectural,boolean objectLike){this.adeIndex=i;this.ua=ua;this.architectural=architectural;this.objectLike=objectLike;}

    /** Canonical ADE20K class IDs used by class-name lists: 0..149. */
    public static SemanticLabel fromAde150Index(int i){
        for(SemanticLabel x:values())if(x.adeIndex==i)return x;
        return i>=0&&i<150?OBJECT:UNKNOWN;
    }

    /** MLPerf Mobile ADE20K model: outputs 0..30 canonical frequent classes, 31=OTHER. */
    public static SemanticLabel fromMlPerf32Index(int i){
        if(i==31)return OBJECT;
        if(i>=0&&i<=30)return fromAde150Index(i);
        return UNKNOWN;
    }

    /** TensorFlow DeepLab ADE20K checkpoint exports 151 IDs: 0=ignore, 1..150=canonical classes 0..149. */
    public static SemanticLabel fromTfDeepLab151Index(int i){
        if(i<=0||i>150)return UNKNOWN;
        return fromAde150Index(i-1);
    }

    public static SemanticLabel fromModelIndex(int i,SemanticModelManager.Flavor f){
        if(f==SemanticModelManager.Flavor.ML_PERF_32)return fromMlPerf32Index(i);
        if(f==SemanticModelManager.Flavor.TF_DEEPLAB_ADE151)return fromTfDeepLab151Index(i);
        return UNKNOWN;
    }

    /** Raw model output ID corresponding to a canonical label, or -1 when the model cannot name it. */
    public static int modelIndexFor(SemanticLabel label,SemanticModelManager.Flavor f){
        if(label==null||label.adeIndex<0)return -1;
        if(f==SemanticModelManager.Flavor.TF_DEEPLAB_ADE151)return label.adeIndex+1;
        if(f==SemanticModelManager.Flavor.ML_PERF_32)return label.adeIndex<=30?label.adeIndex:-1;
        return -1;
    }
    public boolean isRoomEnvelope(){return this==WALL||this==FLOOR||this==CEILING;}
    public boolean isOpening(){return this==DOOR||this==WINDOW;}
}
