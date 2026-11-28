package com.elektoryk.scan.semantic;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Manages an indoor semantic model without blocking the camera thread.
 *
 * Primary: TensorFlow DeepLab ADE20K export (151 output IDs: 0=ignore, 1..150=ADE20K classes).
 * Fallback: MLPerf Mobile 32-class ADE20K model (31 frequent classes + OTHER).
 *
 * The fallback deliberately uses a different label mapping. Treating its output 31 as
 * ADE20K class 31 is wrong: it is the catch-all OTHER class in the MLPerf model.
 */
public final class SemanticModelManager implements AutoCloseable {
    public enum State { MISSING, DOWNLOADING, READY, ERROR }
    public enum Flavor { TF_DEEPLAB_ADE151, ML_PERF_32, NONE }
    public static final class Active {
        public final File file;
        public final Flavor flavor;
        private Active(File file,Flavor flavor){this.file=file;this.flavor=flavor;}
    }

    public static final String FULL_MODEL_URL=
            "https://tfhub.dev/sayakpaul/deeplabv3-mobilenetv2-ade20k/1/default/2?lite-format=tflite";
    public static final String FALLBACK_MODEL_URL=
            "https://raw.githubusercontent.com/mlcommons/mobile_models/main/v0_7/tflite/deeplabv3_mnv2_ade20k_float.tflite";

    private static final long MIN_MODEL_BYTES=2_000_000L;
    private static final long MAX_MODEL_BYTES=40_000_000L;

    private final File fullFile;
    private final File fallbackFile;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private volatile State state;
    private volatile Flavor flavor=Flavor.NONE;
    private volatile String error="";
    private volatile long lastAttemptMs;
    private volatile boolean downloading;
    private volatile boolean closed;
    // A file may have a valid TFL3 header but still be incompatible with this device/runtime.
    // Quarantine it for this process so a bad primary cannot shadow a usable fallback forever.
    private boolean fullUnusable;
    private volatile boolean fallbackUnusable;

    public SemanticModelManager(Context c){
        File d=new File(c.getFilesDir(),"models");if(!d.exists())d.mkdirs();
        fullFile=new File(d,"deeplabv3_mnv2_ade20k_151.tflite");
        // Keep the historical filename so users upgrading from v0.19 prereleases can reuse it.
        fallbackFile=new File(d,"deeplabv3_mnv2_ade20k_float.tflite");
        refreshActive();
    }

    public synchronized File file(){refreshActive();return flavor==Flavor.TF_DEEPLAB_ADE151?fullFile:(flavor==Flavor.ML_PERF_32?fallbackFile:null);}
    public synchronized Active active(){refreshActive();return new Active(flavor==Flavor.TF_DEEPLAB_ADE151?fullFile:(flavor==Flavor.ML_PERF_32?fallbackFile:null),flavor);}
    public State state(){return state;}
    public Flavor flavor(){return flavor;}
    public String error(){return error;}
    public synchronized boolean ready(){refreshActive();return flavor!=Flavor.NONE;}

    public synchronized void markUnusable(Flavor failed){
        if(closed||failed==Flavor.NONE)return;
        if(failed==Flavor.TF_DEEPLAB_ADE151)fullUnusable=true;
        else if(failed==Flavor.ML_PERF_32)fallbackUnusable=true;
        error=failed==Flavor.TF_DEEPLAB_ADE151
                ?"full ADE20K model incompatible; trying 32-class fallback"
                :"32-class fallback model incompatible";
        refreshActive();
        if(flavor==Flavor.NONE)state=State.ERROR;
        // A failed full model must not delay the fallback behind the normal retry cooldown.
        lastAttemptMs=0;
    }

    /**
     * Starts/continues model provisioning. If the 32-class fallback is already installed,
     * scanning remains READY while a best-effort upgrade to the full ADE20K model happens.
     */
    public synchronized void ensureAsync(){
        refreshActive();
        if(closed||flavor==Flavor.TF_DEEPLAB_ADE151||downloading)return;
        if(fullUnusable&&fallbackUnusable){state=State.ERROR;return;}
        if(fullUnusable&&flavor==Flavor.ML_PERF_32)return;
        long now=System.currentTimeMillis();
        if((state==State.ERROR&&now-lastAttemptMs<60_000L)||(flavor==Flavor.ML_PERF_32&&now-lastAttemptMs<3_600_000L))return;
        lastAttemptMs=now;downloading=true;
        final boolean hasFallback=flavor==Flavor.ML_PERF_32;
        final boolean skipFull=fullUnusable;
        if(!hasFallback)state=State.DOWNLOADING;
        if(!skipFull)error="";
        try{io.submit(()->{
            String fullErr=skipFull?"full model rejected in this scan session":"";
            if(!skipFull)try{
                downloadModel(FULL_MODEL_URL,fullFile);
            }catch(Throwable t){fullErr=shortError(t);}
            synchronized(SemanticModelManager.this){refreshActive();}
            if(skipFull||!isValidTflite(fullFile)){
                // If there is no reusable fallback, obtain the known MLPerf one.
                if(!fallbackUnusable&&!isValidTflite(fallbackFile)){
                    try{downloadModel(FALLBACK_MODEL_URL,fallbackFile);}catch(Throwable t){
                        String e=shortError(t);
                        synchronized(SemanticModelManager.this){
                            error="full: "+fullErr+"; fallback: "+e;
                        }
                    }
                }
            }
            synchronized(SemanticModelManager.this){
                downloading=false;refreshActive();
                if(flavor==Flavor.NONE){state=State.ERROR;if(error.length()==0)error="full: "+fullErr;}
                else {state=State.READY;if(flavor==Flavor.ML_PERF_32&&fullErr.length()>0)error="full ADE20K model unavailable; using 32-class fallback";}
            }
        });}catch(RejectedExecutionException rejected){
            downloading=false;
            if(!closed){state=State.ERROR;error="model provisioning executor unavailable";}
        }
    }

    private void refreshActive(){
        if(!fullUnusable&&isValidTflite(fullFile)){flavor=Flavor.TF_DEEPLAB_ADE151;state=State.READY;return;}
        if(!fallbackUnusable&&isValidTflite(fallbackFile)){flavor=Flavor.ML_PERF_32;state=State.READY;return;}
        flavor=Flavor.NONE;if(!downloading&&state!=State.ERROR)state=State.MISSING;
    }

    private static void downloadModel(String url,File dst)throws Exception{
        File tmp=new File(dst.getParentFile(),dst.getName()+".part");if(tmp.exists())tmp.delete();
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(60000);c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent","ElektorykScan/0.19");c.connect();
        int code=c.getResponseCode();if(code<200||code>=300)throw new IllegalStateException("HTTP "+code);
        try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(tmp)){
            byte[] b=new byte[64*1024];int n;long total=0;
            while((n=in.read(b))>0){out.write(b,0,n);total+=n;if(total>MAX_MODEL_BYTES)throw new IllegalStateException("model too large");}
            out.flush();
        }finally{c.disconnect();}
        if(!isValidTflite(tmp)){tmp.delete();throw new IllegalStateException("not a valid TFLite model");}
        if(dst.exists()&&!dst.delete()){tmp.delete();throw new IllegalStateException("cannot replace model");}
        if(!tmp.renameTo(dst)){tmp.delete();throw new IllegalStateException("cannot install model");}
    }

    /** FlatBuffer identifier for a .tflite file is bytes 4..7 = TFL3. */
    private static boolean isValidTflite(File f){
        if(f==null||!f.isFile()||f.length()<MIN_MODEL_BYTES||f.length()>MAX_MODEL_BYTES)return false;
        try(FileInputStream in=new FileInputStream(f)){
            byte[] h=new byte[8];if(in.read(h)!=8)return false;
            return h[4]=='T'&&h[5]=='F'&&h[6]=='L'&&h[7]=='3';
        }catch(Exception e){return false;}
    }
    /** Release the provisioning executor when the owning semantic system is destroyed. */
    @Override public synchronized void close(){
        closed=true;downloading=false;
        io.shutdownNow();
    }

    private static String shortError(Throwable t){return t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage());}
}
