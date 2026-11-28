package com.elektoryk.scan.semantic;

import android.content.Context;

import com.elektoryk.scan.model.SemanticEntity;
import com.elektoryk.scan.scanner.ar.ArCoreDepthCollector;
import com.elektoryk.scan.scanner.ar.ScanVisualKeyframeRecorder;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Owns model download, background inference and persistent semantic scene memory. */
public final class IndoorSemanticSystem implements AutoCloseable {
    public static final class Snapshot {
        public SemanticModelManager.State modelState=SemanticModelManager.State.MISSING;
        public SemanticModelManager.Flavor modelFlavor=SemanticModelManager.Flavor.NONE;
        public String modelError="";
        public SemanticLabel lookingAt=SemanticLabel.UNKNOWN;
        public float lookingAtShare;
        public int entities,walls,doors,windows,objects;
        public long lastSemanticTimestampNs;
        public double targetWidthM,targetHeightM,targetDepthM,targetConfidence;
        public boolean busy;
    }
    private final SemanticModelManager models;
    private final IndoorSemanticSegmenter segmenter=new IndoorSemanticSegmenter();
    private final SemanticSceneTracker tracker=new SemanticSceneTracker();
    private final ExecutorService ai=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private volatile long lastSubmittedTimestampNs;
    private volatile long lastCompletedTimestampNs;
    private volatile int consecutiveInferenceFailures;
    private final AtomicBoolean closed=new AtomicBoolean(false);
    private volatile boolean flushTimedOut;
    // Scan-session epoch. An inference can outlive resetPass(); without an epoch guard its old
    // camera pose/Depth evidence could be integrated into the freshly reset AR coordinate frame.
    private final AtomicLong generation=new AtomicLong(1);

    public IndoorSemanticSystem(Context c){models=new SemanticModelManager(c.getApplicationContext());models.ensureAsync();}
    public void reset(){
        generation.incrementAndGet();
        tracker.reset();lastSubmittedTimestampNs=0;lastCompletedTimestampNs=0;consecutiveInferenceFailures=0;flushTimedOut=false;
    }
    /** Tracking loss must not leave a stale WALL/DOOR label steering the recovered overlay. */
    public void invalidateLiveFocus(){tracker.invalidateLiveFocus();}

    public void submit(int[] argb,int w,int h,ScanVisualKeyframeRecorder.Meta meta,ArCoreDepthCollector.DepthEvidenceFrame ev){
        if(closed.get()||argb==null||meta==null)return;models.ensureAsync();long ts=meta.timestampNs;
        if(ts>0&&lastSubmittedTimestampNs>0&&(ts<=lastSubmittedTimestampNs||ts-lastSubmittedTimestampNs<260_000_000L))return;
        if(!models.ready()||!busy.compareAndSet(false,true))return;
        flushTimedOut=false;
        lastSubmittedTimestampNs=Math.max(lastSubmittedTimestampNs,ts);
        // Ownership contract: ScanVisualKeyframeRecorder creates a fresh RGB array for every camera
        // image and never mutates it after submit(). The JPEG writer is read-only as well. Cloning
        // the full frame here used to create a second ~0.6-1.6 MB int[] for every semantic sample,
        // causing avoidable young-generation GC exactly while Depth/GL scanning was active.
        final int[] inferencePixels=argb;
        final long submitGeneration=generation.get();
        try{
            ai.submit(()->{try{
                if(!segmenter.ensureLoaded(models.file(),models.flavor())){if(submitGeneration==generation.get())consecutiveInferenceFailures++;return;}
                SemanticFrame f=segmenter.segment(inferencePixels,w,h,ts);
                if(f!=null&&submitGeneration==generation.get()&&ts>=lastCompletedTimestampNs){
                    tracker.integrate(f,meta,ev);lastCompletedTimestampNs=ts;consecutiveInferenceFailures=0;
                } else if(f==null&&submitGeneration==generation.get())consecutiveInferenceFailures++;
            }catch(Throwable ignored){
                // A failure from an obsolete scan must not poison the freshly reset scan epoch.
                if(submitGeneration==generation.get()){
                    consecutiveInferenceFailures++;
                    // Recover from a poisoned/interrupted interpreter rather than remaining permanently busy/broken.
                    if(consecutiveInferenceFailures>=3){try{segmenter.close();}catch(Throwable ignored2){}consecutiveInferenceFailures=0;}
                }
            }finally{busy.set(false);}});
        }catch(java.util.concurrent.RejectedExecutionException rejected){
            // close() can win the race after the closed check. Never leave the semantic system
            // permanently "busy" because a lifecycle-time submit was rejected.
            busy.set(false);
        }
    }
    public Snapshot snapshot(){SemanticSceneTracker.Snapshot t=tracker.snapshot();Snapshot s=new Snapshot();s.modelState=models.state();s.modelFlavor=models.flavor();s.modelError=models.error();s.lookingAt=t.centerLabel;s.lookingAtShare=t.centerShare;s.entities=t.entityCount;s.walls=t.wallCount;s.doors=t.doorCount;s.windows=t.windowCount;s.objects=t.objectCount;s.lastSemanticTimestampNs=t.lastSemanticTimestampNs;s.targetWidthM=t.targetWidthM;s.targetHeightM=t.targetHeightM;s.targetDepthM=t.targetDepthM;s.targetConfidence=t.targetConfidence;s.busy=busy.get();return s;}
    public List<SemanticEntity> entities(){flush();return tracker.snapshotEntities();}
    public List<SemanticSceneTracker.Record> records(){flush();return tracker.snapshotRecords();}
    /**
     * Wait briefly for the last accepted inference so final reconstruction sees fresh semantics.
     * Never wait without a deadline: a vendor TFLite/interpreter stall used to block the single
     * reconstruction worker forever when finishScan() called entities()/records(). Geometry and
     * the recovery checkpoint are more important than one final semantic frame.
     */
    public void flush(){
        if(closed.get()||flushTimedOut)return;
        Future<?> barrier=null;
        try{
            barrier=ai.submit(()->{});
            barrier.get(3500,TimeUnit.MILLISECONDS);
        }catch(TimeoutException timeout){
            // Invalidate an inference that may eventually return after the scene has already been
            // finalized. This prevents late evidence from leaking into a subsequent scan/reset.
            generation.incrementAndGet();
            flushTimedOut=true;
            if(barrier!=null)barrier.cancel(false);
        }catch(InterruptedException interrupted){
            if(barrier!=null)barrier.cancel(false);
            Thread.currentThread().interrupt();
        }catch(Exception ignored){
            if(barrier!=null)barrier.cancel(false);
        }
    }
    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;
        models.close();
        try{ai.submit(()->{try{segmenter.close();}catch(Throwable ignored){}});}catch(java.util.concurrent.RejectedExecutionException ignored){try{segmenter.close();}catch(Throwable ignored2){}}
        ai.shutdown();
    }
}
