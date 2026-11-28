package com.elektoryk.scan.device;

import android.content.Context;
import android.content.SharedPreferences;

public class RuntimeProfile {
    public enum Mode { STABLE, BALANCED, MAX_QUALITY }

    public Mode mode=Mode.BALANCED;
    public int maxAnalysisPoints=150000;
    public boolean keepScanCheckpoint=true;
    public boolean preferRawDepth=true;

    public static RuntimeProfile load(Context c){
        RuntimeProfile p=new RuntimeProfile();
        SharedPreferences sp=c.getSharedPreferences("runtime_profile",Context.MODE_PRIVATE);
        try{p.mode=Mode.valueOf(sp.getString("mode",Mode.BALANCED.name()));}catch(Exception ignored){}
        p.applyDefaults();
        return p;
    }

    public static void save(Context c,Mode mode){
        c.getSharedPreferences("runtime_profile",Context.MODE_PRIVATE).edit().putString("mode",mode.name()).apply();
    }

    private void applyDefaults(){
        switch(mode){
            case STABLE:maxAnalysisPoints=85000;keepScanCheckpoint=true;preferRawDepth=true;break;
            case MAX_QUALITY:maxAnalysisPoints=220000;keepScanCheckpoint=true;preferRawDepth=true;break;
            case BALANCED:
            default:maxAnalysisPoints=150000;keepScanCheckpoint=true;preferRawDepth=true;break;
        }
    }

    public String label(){
        switch(mode){case STABLE:return "Стабільний";case MAX_QUALITY:return "Максимальна якість";default:return "Збалансований";}
    }
}
