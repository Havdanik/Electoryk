package com.elektoryk.scan.scanner.ar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.Image;

import com.elektoryk.scan.storage.AtomicFileStore;
import com.elektoryk.scan.storage.ProjectIdGuard;
import com.elektoryk.scan.semantic.IndoorSemanticSystem;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Low-rate RGB keyframes for photo-texturing structural walls after reconstruction.
 *
 * Geometry remains Depth-driven. RGB is sampled independently so a RAW wall in Digital Twin can
 * look like the actual wallpaper/brick/paint instead of an invented translucent material.
 */
public final class ScanVisualKeyframeRecorder implements AutoCloseable {
    private static final long MIN_INTERVAL_NS=900_000_000L;
    private static final long MIN_SEMANTIC_INTERVAL_NS=360_000_000L;
    private static final int MAX_KEYFRAMES=80;
    // JPEG compression is slower than the GL camera callback on some devices. Keep the executor
    // queue bounded indirectly so RGB int[] frames cannot accumulate ~100 MB during a long scan.
    private static final int MAX_PENDING_WRITES=3;
    private static final int MAX_DIM=640;
    private static final int DEPTH_W=80,DEPTH_H=60;

    private final File dir;
    private final File metaFile;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final List<Meta> metas=new ArrayList<>();
    private final IndoorSemanticSystem semantic;
    private long lastCaptureNs;
    private long lastSemanticNs;
    private int serial;
    private volatile boolean closed;
    private volatile boolean resetting;
    // Generation of the visual scan coordinate system. A camera callback can spend tens of ms
    // converting YUV before it reaches the recorder; without an epoch it could resume after
    // resetPass() and inject a pose from the previous ARCore origin into the new pass.
    private long passEpoch;
    private final AtomicInteger pendingWrites=new AtomicInteger();

    public static final class Meta {
        public String file="";
        public String depthFile="";
        public int depthW,depthH;
        public int width,height;
        public float fx,fy,cx,cy;
        public float tx,ty,tz,qx,qy,qz,qw;
        public long timestampNs;

        JSONObject json() throws Exception{
            JSONObject o=new JSONObject();o.put("file",file);o.put("depthFile",depthFile).put("depthW",depthW).put("depthH",depthH);o.put("width",width);o.put("height",height);
            o.put("fx",fx);o.put("fy",fy);o.put("cx",cx);o.put("cy",cy);
            o.put("tx",tx);o.put("ty",ty);o.put("tz",tz);o.put("qx",qx);o.put("qy",qy);o.put("qz",qz);o.put("qw",qw);o.put("timestampNs",timestampNs);return o;
        }
        static Meta from(JSONObject o){if(o==null)return null;Meta m=new Meta();m.file=o.optString("file","");m.depthFile=o.optString("depthFile","");m.depthW=o.optInt("depthW",0);m.depthH=o.optInt("depthH",0);m.width=o.optInt("width",0);m.height=o.optInt("height",0);m.fx=(float)o.optDouble("fx",0);m.fy=(float)o.optDouble("fy",0);m.cx=(float)o.optDouble("cx",0);m.cy=(float)o.optDouble("cy",0);m.tx=(float)o.optDouble("tx",0);m.ty=(float)o.optDouble("ty",0);m.tz=(float)o.optDouble("tz",0);m.qx=(float)o.optDouble("qx",0);m.qy=(float)o.optDouble("qy",0);m.qz=(float)o.optDouble("qz",0);m.qw=(float)o.optDouble("qw",1);m.timestampNs=o.optLong("timestampNs",0);return m;}
        public Pose pose(){return new Pose(new float[]{tx,ty,tz},new float[]{qx,qy,qz,qw});}
    }

    public ScanVisualKeyframeRecorder(Context c,String projectId){this(c,projectId,null);}

    public ScanVisualKeyframeRecorder(Context c,String projectId,IndoorSemanticSystem semantic){
        ProjectIdGuard.requireSafe(projectId);
        this.semantic=semantic;
        dir=new File(new File(new File(c.getFilesDir(),"scenes"),projectId),"visual_pass");
        if(!dir.exists())dir.mkdirs();metaFile=new File(dir,"keyframes.json");
        // A recovery checkpoint is useful only if its photo evidence survives the same process
        // death. Reload the last fully committed keyframes; a fresh Smart Scan calls resetPass()
        // immediately and therefore still starts from a clean AR origin.
        restoreCommittedMetadata();
    }

    private synchronized void restoreCommittedMetadata(){
        cleanupPartFiles(dir);
        if(!metaFile.isFile()||metaFile.length()<=2)return;
        try{
            String text=AtomicFileStore.readTextWithBackup(metaFile);
            JSONArray a=new JSONArray(text);int maxId=-1;
            for(int i=0;i<a.length()&&metas.size()<MAX_KEYFRAMES;i++){
                Meta m=Meta.from(a.optJSONObject(i));if(m==null||m.file.isEmpty())continue;
                File jpg=new File(dir,m.file);if(!safeChild(jpg)||!jpg.isFile()||jpg.length()<128)continue;
                if(!m.depthFile.isEmpty()){
                    File depth=new File(dir,m.depthFile);
                    if(!safeChild(depth)||!depth.isFile()||depth.length()<16){m.depthFile="";m.depthW=0;m.depthH=0;}
                }
                metas.add(m);maxId=Math.max(maxId,keyframeId(m.file));
            }
            serial=Math.max(0,maxId+1);
        }catch(Exception ignored){metas.clear();serial=0;}
    }

    private boolean safeChild(File f){
        try{return f.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile());}catch(Exception e){return false;}
    }
    private static int keyframeId(String name){
        try{if(name==null||!name.startsWith("kf_")||!name.endsWith(".jpg"))return -1;return Integer.parseInt(name.substring(3,name.length()-4));}catch(Exception e){return -1;}
    }
    private static void cleanupPartFiles(File d){
        File[] files=d==null?null:d.listFiles();if(files==null)return;
        for(File f:files)if(f!=null&&f.isFile()&&f.getName().endsWith(".part"))f.delete();
    }

    /** A fresh Smart Scan pass must not reuse RGB poses from a different ARCore origin. */
    public void resetPass(){
        // Do not hold this monitor while waiting for the IO executor. A failed pending write
        // removes its Meta under the same monitor; the old synchronized resetPass()+flush()
        // could therefore deadlock forever exactly when storage failed during a new scan start.
        resetting=true;
        // Invalidate callbacks that already passed the fast resetting check but are still doing
        // camera-image conversion. This increment is serialized with the final submit gate below.
        synchronized(this){passEpoch++;}
        try{
            flushInternal();
            delete(dir);dir.mkdirs();
            synchronized(this){metas.clear();lastCaptureNs=0;lastSemanticNs=0;serial=0;}
            if(semantic!=null)semantic.reset();
        }finally{resetting=false;}
    }

    public synchronized int count(){return metas.size();}

    /** Call on the GL thread. CPU conversion is intentionally throttled and downsampled. */
    public void maybeCapture(Frame frame,Pose scanOriginPose){maybeCapture(frame,scanOriginPose,null);}

    public void maybeCapture(Frame frame,Pose scanOriginPose,ArCoreDepthCollector.DepthEvidenceFrame evidence){
        if(closed||resetting||frame==null||scanOriginPose==null)return;
        final long captureEpoch; synchronized(this){captureEpoch=passEpoch;}
        Camera camera=frame.getCamera();if(camera==null||camera.getTrackingState()!=TrackingState.TRACKING)return;
        long ts=frame.getTimestamp();if(ts==0)return;
        final boolean semanticDue,keyframeDue;
        synchronized(this){
            semanticDue=semantic!=null&&(lastSemanticNs==0||ts-lastSemanticNs>=MIN_SEMANTIC_INTERVAL_NS);
            keyframeDue=metas.size()<MAX_KEYFRAMES&&pendingWrites.get()<MAX_PENDING_WRITES&&(lastCaptureNs==0||ts-lastCaptureNs>=MIN_INTERVAL_NS);
            if(!semanticDue&&!keyframeDue)return;
            if(semanticDue)lastSemanticNs=ts;if(keyframeDue)lastCaptureNs=ts;
        }
        Image image=null;
        try{
            image=frame.acquireCameraImage();int sw=image.getWidth(),sh=image.getHeight();if(sw<8||sh<8)return;
            int targetMax=keyframeDue?MAX_DIM:384;int step=Math.max(1,(int)Math.ceil(Math.max(sw,sh)/(double)targetMax));
            int w=Math.max(1,sw/step),h=Math.max(1,sh/step);int[] rgb=downsampleYuv(image,w,h,step);
            CameraIntrinsics intr=camera.getImageIntrinsics();float[] f=intr.getFocalLength(),p=intr.getPrincipalPoint();
            Pose local=scanOriginPose.inverse().compose(camera.getPose());float[] q=local.getRotationQuaternion();
            final Meta m=new Meta();m.width=w;m.height=h;m.fx=f[0]/step;m.fy=f[1]/step;m.cx=p[0]/step;m.cy=p[1]/step;
            m.tx=local.tx();m.ty=local.ty();m.tz=local.tz();m.qx=q[0];m.qy=q[1];m.qz=q[2];m.qw=q[3];m.timestampNs=ts;
            // Final generation gate. resetPass() increments passEpoch under the same monitor.
            // Therefore an old callback either submits before semantic.reset() (and is rejected by
            // the semantic epoch) or is discarded here; it can never become a new-pass sample.
            synchronized(this){
                if(closed||resetting||captureEpoch!=passEpoch)return;
                if(semanticDue&&semantic!=null)semantic.submit(rgb,w,h,m,evidence);
            }
            if(!keyframeDue)return;
            short[] coarseDepth=buildCoarseDepth(evidence,local,m,ts,DEPTH_W,DEPTH_H);
            synchronized(this){
                if(closed||resetting||captureEpoch!=passEpoch)return;
                int id=serial++;m.file=String.format(Locale.US,"kf_%03d.jpg",id);if(coarseDepth!=null){m.depthFile=String.format(Locale.US,"kf_%03d.d16",id);m.depthW=DEPTH_W;m.depthH=DEPTH_H;}metas.add(m);
            }
            final File out=new File(dir,m.file);final File depthOut=m.depthFile.isEmpty()?null:new File(dir,m.depthFile);
            pendingWrites.incrementAndGet();
            try{
                io.submit(()->{
                    Bitmap b=null;
                    try{
                        synchronized(ScanVisualKeyframeRecorder.this){
                            if(captureEpoch!=passEpoch||resetting){metas.remove(m);return;}
                        }
                        b=Bitmap.createBitmap(rgb,w,h,Bitmap.Config.ARGB_8888);
                        File jpgTmp=new File(out.getParentFile(),out.getName()+".part");
                        try(FileOutputStream fos=new FileOutputStream(jpgTmp,false)){
                            if(!b.compress(Bitmap.CompressFormat.JPEG,84,fos))throw new java.io.IOException("JPEG encode failed");
                            fos.flush();fos.getFD().sync();
                        }
                        replaceAtomic(jpgTmp,out);
                        if(depthOut!=null&&coarseDepth!=null){
                            File depthTmp=new File(depthOut.getParentFile(),depthOut.getName()+".part");
                            try(FileOutputStream dfos=new FileOutputStream(depthTmp,false);DataOutputStream dos=new DataOutputStream(dfos)){
                                dos.writeInt(0x44505448);dos.writeInt(DEPTH_W);dos.writeInt(DEPTH_H);for(short v:coarseDepth)dos.writeShort(v&0xffff);dos.flush();dfos.getFD().sync();
                            }
                            replaceAtomic(depthTmp,depthOut);
                        }
                        // Make every fully committed keyframe independently recoverable. Before
                        // this, keyframes.json was normally updated only on pause/finish, so an
                        // OS process kill could leave valid JPEG/D16 evidence orphaned on disk.
                        persistMetadata();
                    }catch(Exception ignored){
                        // Never keep metadata that points at a partial/missing JPEG or depth file.
                        // A killed process or ENOSPC used to leave such ghost keyframes behind and
                        // later texture baking repeatedly tried to decode them.
                        synchronized(ScanVisualKeyframeRecorder.this){metas.remove(m);}
                        if(out.exists())out.delete();if(depthOut!=null&&depthOut.exists())depthOut.delete();
                        new File(out.getParentFile(),out.getName()+".part").delete();
                        if(depthOut!=null)new File(depthOut.getParentFile(),depthOut.getName()+".part").delete();
                    }finally{if(b!=null)b.recycle();pendingWrites.decrementAndGet();}
                });
            }catch(RejectedExecutionException rejected){
                pendingWrites.decrementAndGet();
                synchronized(this){metas.remove(m);}
                if(out.exists())out.delete();if(depthOut!=null&&depthOut.exists())depthOut.delete();
            }
        }catch(Exception ignored){}finally{if(image!=null)try{image.close();}catch(Exception ignored){}}
    }

    public synchronized List<Meta> snapshot(){return new ArrayList<>(metas);}

    public void flush(){
        if(closed)return;
        flushInternal();
    }

    private void flushInternal(){
        try{Future<?> f=io.submit(()->{});f.get();}catch(Exception ignored){}
        persistMetadata();
    }

    /** Persist pending keyframes without ever blocking the Activity/UI thread. */
    public void flushAsync(){
        if(closed)return;
        try{io.submit(this::persistMetadata);}catch(java.util.concurrent.RejectedExecutionException ignored){}
    }

    private void persistMetadata(){
        List<Meta> copy;synchronized(this){copy=new ArrayList<>(metas);}
        try{JSONArray a=new JSONArray();for(Meta m:copy)a.put(m.json());AtomicFileStore.writeText(metaFile,a.toString());}catch(Exception ignored){}
    }

    public File directory(){return dir;}

    /** Flush pending JPEG/depth writes and release the per-scan IO thread. */
    @Override public void close(){
        if(closed)return;
        // `closed` is already a final-submit gate for camera callbacks. Do NOT advance the
        // pass epoch here: queued writes were accepted by this same pass and must be allowed to
        // commit during shutdown. Advancing the epoch used to make close()/closeAsync() silently
        // discard up to MAX_PENDING_WRITES valid final keyframes at Activity teardown.
        closed=true;
        flushInternal();
        io.shutdown();
        try{
            if(!io.awaitTermination(1500,java.util.concurrent.TimeUnit.MILLISECONDS))io.shutdownNow();
        }catch(InterruptedException e){
            io.shutdownNow();Thread.currentThread().interrupt();
        }
    }

    /** Lifecycle-safe close: queued after pending JPEG/depth writes and returns immediately. */
    public void closeAsync(){
        if(closed)return;
        // Reject new camera submissions, but preserve already accepted IO tasks from this pass.
        // They are ordered ahead of persistMetadata on the single-thread executor.
        closed=true;
        try{io.submit(this::persistMetadata);}catch(java.util.concurrent.RejectedExecutionException ignored){}
        io.shutdown();
    }

    /**
     * Project accepted scan-local Depth points into the RGB keyframe and keep a coarse nearest
     * depth buffer.  Later texturing can reject a photo tile if a wardrobe/curtain/person was
     * physically in front of the wall at capture time.
     */
    private static short[] buildCoarseDepth(ArCoreDepthCollector.DepthEvidenceFrame ev,Pose cameraLocal,Meta m,long frameTs,int dw,int dh){
        if(ev==null||ev.localXyz==null||ev.pointCount<20||cameraLocal==null||m.width<=0||m.height<=0)return null;
        if(ev.timestampNs>0&&frameTs>0&&Math.abs(ev.timestampNs-frameTs)>220_000_000L)return null;
        short[] out=new short[dw*dh];Pose inv=cameraLocal.inverse();float[] src=new float[3],cam=new float[3];
        int n=Math.min(ev.pointCount,ev.localXyz.length/3),hits=0;
        for(int i=0;i<n;i++){
            int o=i*3;float x=ev.localXyz[o],y=ev.localXyz[o+1],z=ev.localXyz[o+2];if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))continue;
            src[0]=x;src[1]=y;src[2]=z;inv.transformPoint(src,0,cam,0);float depth=-cam[2];if(depth<0.20f||depth>6.0f)continue;
            float u=m.cx+m.fx*cam[0]/depth,v=m.cy-m.fy*cam[1]/depth;if(u<0||u>=m.width||v<0||v>=m.height)continue;
            int gx=Math.min(dw-1,Math.max(0,(int)(u/m.width*dw))),gy=Math.min(dh-1,Math.max(0,(int)(v/m.height*dh)));
            int mm=Math.min(65535,Math.max(1,Math.round(depth*1000f))),idx=gy*dw+gx,old=out[idx]&0xffff;
            if(old==0||mm<old){out[idx]=(short)mm;hits++;}
        }
        return hits>=12?out:null;
    }

    private static int[] downsampleYuv(Image image,int w,int h,int step){
        Image.Plane[] planes=image.getPlanes();Image.Plane yp=planes[0],up=planes[1],vp=planes[2];
        ByteBuffer yb=yp.getBuffer(),ub=up.getBuffer(),vb=vp.getBuffer();
        int yr=yp.getRowStride(),ur=up.getRowStride(),vr=vp.getRowStride(),upix=up.getPixelStride(),vpix=vp.getPixelStride();
        int[] out=new int[w*h];int k=0;
        for(int oy=0;oy<h;oy++){
            int sy=Math.min(image.getHeight()-1,oy*step);int cy=sy/2;
            for(int ox=0;ox<w;ox++){
                int sx=Math.min(image.getWidth()-1,ox*step);int cx=sx/2;
                int Y=yb.get(sy*yr+sx)&255;int U=(ub.get(cy*ur+cx*upix)&255)-128;int V=(vb.get(cy*vr+cx*vpix)&255)-128;
                int r=clamp((int)(Y+1.402f*V));int g=clamp((int)(Y-0.344136f*U-0.714136f*V));int b=clamp((int)(Y+1.772f*U));
                out[k++]=Color.rgb(r,g,b);
            }
        }
        return out;
    }
    private static int clamp(int v){return v<0?0:(v>255?255:v);}
    private static void replaceAtomic(File tmp,File dst)throws java.io.IOException{
        if(tmp==null||!tmp.isFile()||tmp.length()<=0)throw new java.io.IOException("empty keyframe temp file");
        if(dst.exists()&&!dst.delete()){tmp.delete();throw new java.io.IOException("cannot replace keyframe");}
        if(!tmp.renameTo(dst)){tmp.delete();throw new java.io.IOException("cannot commit keyframe");}
    }

    private static void delete(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File x:a)delete(x);}f.delete();}
}
