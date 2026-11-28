package com.elektoryk.scan;

import android.app.Application;
import com.elektoryk.scan.storage.StabilityReporter;

public class ElektorykApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{new StabilityReporter(this).error("CRASH","Uncaught on "+thread.getName(),error);}catch(Exception ignored){}
            if(previous!=null) previous.uncaughtException(thread,error);
        });
        StabilityReporter reporter=new StabilityReporter(this);
        android.content.SharedPreferences recording=getSharedPreferences("consultation_recording",MODE_PRIVATE);
        if(recording.getLong("startedAt",0)>0){recording.edit().clear().apply();reporter.breadcrumb("AUDIO_RECOVERY","Cleared stale recording state after process restart");}
        reporter.breadcrumb("APP","Application started");
    }
}
