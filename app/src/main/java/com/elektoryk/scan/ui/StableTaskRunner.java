package com.elektoryk.scan.ui;

import android.app.Activity;
import com.elektoryk.scan.storage.StabilityReporter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StableTaskRunner {
    public interface Task<T>{T run()throws Exception;}
    public interface Success<T>{void onSuccess(T value);}
    public interface Failure{void onFailure(Throwable error);}

    private static final ExecutorService IO=Executors.newFixedThreadPool(2);
    private StableTaskRunner(){}

    public static <T> void run(Activity a,String tag,Task<T> task,Success<T> success,Failure failure){
        IO.execute(()->{
            try{
                T value=task.run();
                if(a.isFinishing()||a.isDestroyed())return;
                a.runOnUiThread(()->{if(!a.isFinishing()&&!a.isDestroyed())success.onSuccess(value);});
            }catch(Throwable e){
                try{new StabilityReporter(a).error(tag,"Background task failed",e);}catch(Exception ignored){}
                if(a.isFinishing()||a.isDestroyed())return;
                a.runOnUiThread(()->{if(!a.isFinishing()&&!a.isDestroyed())failure.onFailure(e);});
            }
        });
    }
}
