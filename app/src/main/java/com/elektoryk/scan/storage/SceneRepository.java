package com.elektoryk.scan.storage;

import android.content.Context;
import com.elektoryk.scan.model.CloudPoint;
import com.elektoryk.scan.model.ScanPathPoint;
import com.elektoryk.scan.model.SceneSnapshot;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class SceneRepository {
    private static final int MAGIC=0x4553434E;
    private static final int VERSION=1;
    private static final int MAX_STORED_POINTS=2000000;
    static final Object IO_LOCK=new Object();
    private final File root;
    private final StabilityReporter reporter;

    public SceneRepository(Context context){root=new File(context.getFilesDir(),"scenes");if(!root.exists())root.mkdirs();reporter=new StabilityReporter(context);}
    private File dir(String projectId){File d=new File(root,ProjectIdGuard.requireSafe(projectId));if(!d.exists())d.mkdirs();return d;}

    // One process-wide lock coordinates scanner, editor, diagnostics and export, which each create
    // their own SceneRepository instance. Instance-level synchronized methods were not enough:
    // a second repository could otherwise observe and disturb another instance's PREPARING journal.
    public void save(String projectId,SceneSnapshot scene,List<CloudPoint> points){synchronized(IO_LOCK){saveLocked(projectId,scene,points);}}
    public SceneSnapshot loadScene(String projectId){synchronized(IO_LOCK){return loadSceneLocked(projectId);}}
    public List<CloudPoint> loadPoints(String projectId,int maxPoints){synchronized(IO_LOCK){return loadPointsLocked(projectId,maxPoints);}}
    public boolean hasScene(String projectId){synchronized(IO_LOCK){return hasSceneLocked(projectId);}}
    public boolean hasPointCloud(String projectId){synchronized(IO_LOCK){return hasPointCloudLocked(projectId);}}
    public void saveScanCheckpoint(String projectId,List<CloudPoint> points,List<ScanPathPoint> path,boolean replacePrevious){synchronized(IO_LOCK){saveScanCheckpointLocked(projectId,points,path,replacePrevious);}}
    public boolean hasScanCheckpoint(String projectId){synchronized(IO_LOCK){return hasScanCheckpointLocked(projectId);}}
    public List<CloudPoint> loadCheckpointPoints(String projectId){synchronized(IO_LOCK){return loadCheckpointPointsLocked(projectId);}}
    public List<ScanPathPoint> loadCheckpointPath(String projectId){synchronized(IO_LOCK){return loadCheckpointPathLocked(projectId);}}
    public boolean loadCheckpointReplacePrevious(String projectId){synchronized(IO_LOCK){return loadCheckpointReplacePreviousLocked(projectId);}}
    public void clearScanCheckpoint(String projectId){synchronized(IO_LOCK){clearScanCheckpointLocked(projectId);}}
    public boolean restoreSceneBackup(String projectId){synchronized(IO_LOCK){return restoreSceneBackupLocked(projectId);}}
    public boolean restorePointsBackup(String projectId){synchronized(IO_LOCK){return restorePointsBackupLocked(projectId);}}

    /**
     * Applies scene state received from a package without decoding a potentially large point cloud
     * into Java objects. FULL imports are authoritative: missing scene/points clear stale local scan
     * state instead of silently mixing generations from two projects.
     */
    public void importSceneState(String projectId,SceneSnapshot scene,File pointSource,boolean authoritativeFull)throws IOException{
        synchronized(IO_LOCK){importSceneStateLocked(projectId,scene,pointSource,authoritativeFull);}
    }

    private void importSceneStateLocked(String projectId,SceneSnapshot scene,File pointSource,boolean authoritativeFull)throws IOException{
        ProjectIdGuard.requireSafe(projectId);File d=dir(projectId);File sceneFile=new File(d,"scene.json"),pointsFile=new File(d,"points.bin"),journal=new File(d,"scene_commit.json");
        File scenePrepared=new File(d,"scene.json.pairnew"),pointsPrepared=new File(d,"points.bin.pairnew");
        recoverPairIfNeeded(journal,sceneFile,scenePrepared,pointsFile,pointsPrepared,"SCENE_TX_RECOVERY");
        if(scene==null){
            if(authoritativeFull){
                try{JSONObject o=new JSONObject();o.put("state","PREPARED_CLEAR_ALL");o.put("preparedAt",System.currentTimeMillis());AtomicFileStore.writeText(journal,o.toString());recoverPairIfNeeded(journal,sceneFile,scenePrepared,pointsFile,pointsPrepared,"SCENE_IMPORT_CLEAR");}
                catch(Exception e){throw new IOException("Could not clear authoritative scene state",e);}
            }
            return;
        }
        scene.projectId=projectId;scene.updatedAt=System.currentTimeMillis();
        if(pointSource==null){
            if(!authoritativeFull){
                try{AtomicFileStore.writeText(sceneFile,scene.toJson().toString(2));}
                catch(Exception e){throw new IOException("Could not import scene metadata",e);}
                return;
            }
            try{
                deleteQuietly(scenePrepared);deleteQuietly(pointsPrepared);JSONObject preparing=new JSONObject();preparing.put("state","PREPARING_CLEAR_SECOND");preparing.put("startedAt",System.currentTimeMillis());AtomicFileStore.writeText(journal,preparing.toString());
                writeTextPrepared(scenePrepared,scene.toJson().toString(2));JSONObject prepared=new JSONObject();prepared.put("state","PREPARED_CLEAR_SECOND");prepared.put("preparedAt",System.currentTimeMillis());AtomicFileStore.writeText(journal,prepared.toString());
                recoverPairIfNeeded(journal,sceneFile,scenePrepared,pointsFile,pointsPrepared,"SCENE_IMPORT_CLEAR_POINTS");deleteRecursive(new File(d,"checkpoint"));deleteRecursive(new File(d,"visual_pass"));
            }catch(Exception e){recoverPairIfNeeded(journal,sceneFile,scenePrepared,pointsFile,pointsPrepared,"SCENE_TX_RECOVERY");if(e instanceof IOException)throw (IOException)e;throw new IOException("Could not import scene without point cloud",e);}
            return;
        }
        validatePointFile(pointSource);
        try{
            beginPairPrepare(journal,scenePrepared,pointsPrepared);
            writeTextPrepared(scenePrepared,scene.toJson().toString(2));
            copyPrepared(pointSource,pointsPrepared);
            validatePointFile(pointsPrepared);
            markPairPrepared(journal);
            finishPreparedPair(journal,sceneFile,scenePrepared,pointsFile,pointsPrepared,"SCENE_IMPORT_COMMIT");
            if(authoritativeFull){deleteRecursive(new File(d,"checkpoint"));deleteRecursive(new File(d,"visual_pass"));}
        }catch(Exception e){
            recoverPairIfNeeded(journal,sceneFile,scenePrepared,pointsFile,pointsPrepared,"SCENE_TX_RECOVERY");
            if(e instanceof IOException)throw (IOException)e;throw new IOException("Could not import paired scene state",e);
        }
    }

    private void validatePointFile(File f)throws IOException{
        if(f==null||!f.isFile())throw new IOException("Point cloud file missing");
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(f)))){
            if(in.readInt()!=MAGIC)throw new IOException("Invalid point cloud magic");int version=in.readInt();if(version!=VERSION)throw new IOException("Unsupported point cloud version: "+version);int count=in.readInt();
            if(count<=0||count>MAX_STORED_POINTS)throw new IOException("Invalid point cloud count: "+count);long expected=12L+16L*count;if(f.length()<expected)throw new IOException("Truncated point cloud");
        }catch(EOFException e){throw new IOException("Truncated point cloud header",e);}
    }
    private void copyPrepared(File source,File target)throws IOException{
        File parent=target.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("Cannot create "+parent);
        try(InputStream in=new BufferedInputStream(new FileInputStream(source));FileOutputStream fos=new FileOutputStream(target,false);OutputStream out=new BufferedOutputStream(fos)){byte[] b=new byte[16384];int n;while((n=in.read(b))>0)out.write(b,0,n);out.flush();fos.getFD().sync();}
        catch(IOException e){deleteQuietly(target);throw e;}
    }
    private void deleteFileFamily(File f){if(f==null)return;deleteQuietly(f);File parent=f.getParentFile();if(parent==null)return;deleteQuietly(new File(parent,f.getName()+".bak"));deleteQuietly(new File(parent,f.getName()+".tmp"));deleteQuietly(new File(parent,f.getName()+".pairnew"));}

    private void saveLocked(String projectId,SceneSnapshot scene,List<CloudPoint> points){
        if(scene==null)throw new IllegalArgumentException("Scene missing");ProjectIdGuard.requireSafe(projectId);
        File d=dir(projectId);File sceneFile=new File(d,"scene.json"),pointsFile=new File(d,"points.bin"),journal=new File(d,"scene_commit.json");
        File scenePrepared=new File(d,"scene.json.pairnew"),pointsPrepared=new File(d,"points.bin.pairnew");
        recoverPairIfNeeded(journal,sceneFile,scenePrepared,pointsFile,pointsPrepared,"SCENE_TX_RECOVERY");
        try{
            scene.projectId=projectId;scene.updatedAt=System.currentTimeMillis();
            if(points==null){AtomicFileStore.writeText(sceneFile,scene.toJson().toString(2));return;}
            beginPairPrepare(journal,scenePrepared,pointsPrepared);
            writeTextPrepared(scenePrepared,scene.toJson().toString(2));
            writePointsPrepared(pointsPrepared,points);
            markPairPrepared(journal);
            finishPreparedPair(journal,sceneFile,scenePrepared,pointsFile,pointsPrepared,"SCENE_TX_COMMIT");
        }catch(Exception e){
            recoverPairIfNeeded(journal,sceneFile,scenePrepared,pointsFile,pointsPrepared,"SCENE_TX_RECOVERY");
            reporter.error("SCENE_SAVE","Failed "+projectId,e);throw new RuntimeException("Scene save failed",e);
        }
    }

    private SceneSnapshot loadSceneLocked(String projectId){
        if(!ProjectIdGuard.isSafe(projectId))return null;File d=dir(projectId);File f=new File(d,"scene.json");recoverScenePairIfNeeded(d,"SCENE_TX_RECOVERY");if(!f.exists())return null;
        try{SceneSnapshot s=SceneSnapshot.fromJson(new JSONObject(AtomicFileStore.readText(f)));s.projectId=projectId;return s;}
        catch(Exception e){File bak=new File(f.getParentFile(),f.getName()+".bak");if(bak.exists())try{SceneSnapshot s=SceneSnapshot.fromJson(new JSONObject(AtomicFileStore.readText(bak)));s.projectId=projectId;if(AtomicFileStore.restoreBackup(f))reporter.breadcrumb("SCENE_RECOVERY","Restored backup "+projectId);else reporter.breadcrumb("SCENE_RECOVERY","Loaded backup; main restore failed "+projectId);return s;}catch(Exception ignored){}reporter.error("SCENE_READ","Corrupt scene "+projectId,e);return null;}
    }

    private List<CloudPoint> loadPointsLocked(String projectId,int maxPoints){
        List<CloudPoint> empty=new ArrayList<>();if(!ProjectIdGuard.isSafe(projectId))return empty;File d=dir(projectId);File f=new File(d,"points.bin");recoverScenePairIfNeeded(d,"SCENE_TX_RECOVERY");List<CloudPoint> out=readPoints(f,maxPoints);
        if(out.isEmpty()&&f.exists()){File bak=new File(f.getParentFile(),f.getName()+".bak");if(bak.exists()){out=readPoints(bak,maxPoints);if(!out.isEmpty()){if(AtomicFileStore.restoreBackup(f))reporter.breadcrumb("POINT_RECOVERY","Restored point backup "+projectId);else reporter.breadcrumb("POINT_RECOVERY","Loaded point backup; main restore failed "+projectId);}}}
        return out;
    }

    private boolean hasSceneLocked(String projectId){if(!ProjectIdGuard.isSafe(projectId))return false;File d=dir(projectId);recoverScenePairIfNeeded(d,"SCENE_TX_RECOVERY");return new File(d,"scene.json").isFile();}
    private boolean hasPointCloudLocked(String projectId){if(!ProjectIdGuard.isSafe(projectId))return false;File d=dir(projectId);recoverScenePairIfNeeded(d,"SCENE_TX_RECOVERY");return validPointFile(new File(d,"points.bin"));}

    public void saveScanCheckpoint(String projectId,List<CloudPoint> points,List<ScanPathPoint> path){
        saveScanCheckpoint(projectId,points,path,false);
    }
    private void saveScanCheckpointLocked(String projectId,List<CloudPoint> points,List<ScanPathPoint> path,boolean replacePrevious){
        if(!ProjectIdGuard.isSafe(projectId)||points==null||points.isEmpty())return;
        File d=new File(dir(projectId),"checkpoint");if(!d.exists()&&!d.mkdirs()){reporter.error("SCAN_CHECKPOINT","Cannot create checkpoint directory "+projectId,new IOException("mkdir failed"));return;}
        File metaFile=new File(d,"checkpoint.json"),pointsFile=new File(d,"points.bin"),journal=new File(d,"checkpoint_commit.json");
        File metaPrepared=new File(d,"checkpoint.json.pairnew"),pointsPrepared=new File(d,"points.bin.pairnew");
        recoverPairIfNeeded(journal,metaFile,metaPrepared,pointsFile,pointsPrepared,"CHECKPOINT_TX_RECOVERY");
        try{
            beginPairPrepare(journal,metaPrepared,pointsPrepared);
            int valid=writePointsPrepared(pointsPrepared,points);
            JSONArray a=new JSONArray();if(path!=null)for(ScanPathPoint p:path)if(p!=null)a.put(p.toJson());JSONObject meta=new JSONObject();meta.put("createdAt",System.currentTimeMillis());meta.put("pointCount",valid);meta.put("replacePrevious",replacePrevious);meta.put("path",a);writeTextPrepared(metaPrepared,meta.toString(2));
            markPairPrepared(journal);
            finishPreparedPair(journal,metaFile,metaPrepared,pointsFile,pointsPrepared,"CHECKPOINT_TX_COMMIT");
            reporter.breadcrumb("SCAN_CHECKPOINT","Saved "+projectId+" points="+valid);
        }catch(Exception e){recoverPairIfNeeded(journal,metaFile,metaPrepared,pointsFile,pointsPrepared,"CHECKPOINT_TX_RECOVERY");reporter.error("SCAN_CHECKPOINT","Save failed "+projectId,e);}
    }

    private boolean hasScanCheckpointLocked(String projectId){if(!ProjectIdGuard.isSafe(projectId))return false;File d=new File(dir(projectId),"checkpoint"),meta=new File(d,"checkpoint.json"),points=new File(d,"points.bin");recoverCheckpointPairIfNeeded(d,"CHECKPOINT_TX_RECOVERY");if(!meta.isFile()||!validPointFile(points))return false;try{new JSONObject(AtomicFileStore.readTextWithBackup(meta));return true;}catch(Exception e){return false;}}
    private List<CloudPoint> loadCheckpointPointsLocked(String projectId){if(!ProjectIdGuard.isSafe(projectId))return new ArrayList<>();File d=new File(dir(projectId),"checkpoint"),meta=new File(d,"checkpoint.json"),points=new File(d,"points.bin");recoverCheckpointPairIfNeeded(d,"CHECKPOINT_TX_RECOVERY");return readPoints(points,0);}
    private List<ScanPathPoint> loadCheckpointPathLocked(String projectId){
        List<ScanPathPoint> out=new ArrayList<>();if(!ProjectIdGuard.isSafe(projectId))return out;
        try{File d=new File(dir(projectId),"checkpoint"),f=new File(d,"checkpoint.json"),points=new File(d,"points.bin");recoverCheckpointPairIfNeeded(d,"CHECKPOINT_TX_RECOVERY");JSONObject o=new JSONObject(AtomicFileStore.readTextWithBackup(f));JSONArray a=o.optJSONArray("path");if(a!=null)for(int i=0;i<a.length();i++){ScanPathPoint p=ScanPathPoint.fromJson(a.optJSONObject(i));if(p!=null)out.add(p);}}
        catch(Exception ignored){}return out;
    }
    private boolean loadCheckpointReplacePreviousLocked(String projectId){
        if(!ProjectIdGuard.isSafe(projectId))return false;
        try{
            File d=new File(dir(projectId),"checkpoint"),f=new File(d,"checkpoint.json");
            recoverCheckpointPairIfNeeded(d,"CHECKPOINT_TX_RECOVERY");
            return new JSONObject(AtomicFileStore.readTextWithBackup(f)).optBoolean("replacePrevious",false);
        }catch(Exception ignored){return false;}
    }
    private void clearScanCheckpointLocked(String projectId){if(!ProjectIdGuard.isSafe(projectId))return;File d=new File(dir(projectId),"checkpoint");deleteRecursive(d);reporter.breadcrumb("SCAN_CHECKPOINT","Cleared "+projectId);}
    private boolean restoreSceneBackupLocked(String projectId){return ProjectIdGuard.isSafe(projectId)&&AtomicFileStore.restoreBackup(new File(dir(projectId),"scene.json"));}
    private boolean restorePointsBackupLocked(String projectId){return ProjectIdGuard.isSafe(projectId)&&AtomicFileStore.restoreBackup(new File(dir(projectId),"points.bin"));}

    private void recoverScenePairIfNeeded(File d,String tag){
        recoverPairIfNeeded(new File(d,"scene_commit.json"),new File(d,"scene.json"),new File(d,"scene.json.pairnew"),new File(d,"points.bin"),new File(d,"points.bin.pairnew"),tag);
    }
    private void recoverCheckpointPairIfNeeded(File d,String tag){
        recoverPairIfNeeded(new File(d,"checkpoint_commit.json"),new File(d,"checkpoint.json"),new File(d,"checkpoint.json.pairnew"),new File(d,"points.bin"),new File(d,"points.bin.pairnew"),tag);
    }
    private void beginPairPrepare(File journal,File firstPrepared,File secondPrepared)throws Exception{
        deleteQuietly(firstPrepared);deleteQuietly(secondPrepared);
        JSONObject o=new JSONObject();o.put("state","PREPARING");o.put("startedAt",System.currentTimeMillis());AtomicFileStore.writeText(journal,o.toString());
    }
    private void markPairPrepared(File journal)throws Exception{
        JSONObject o=new JSONObject(AtomicFileStore.readText(journal));o.put("state","PREPARED");o.put("preparedAt",System.currentTimeMillis());AtomicFileStore.writeText(journal,o.toString());
    }
    private void finishPreparedPair(File journal,File first,File firstPrepared,File second,File secondPrepared,String tag)throws Exception{
        JSONObject o=new JSONObject(AtomicFileStore.readTextWithBackup(journal));
        if(!"PREPARED".equals(o.optString("state","")))throw new IOException("Pair transaction is not prepared");
        installPrepared(first,firstPrepared);installPrepared(second,secondPrepared);
        if(!first.isFile()||!second.isFile())throw new IOException("Pair commit incomplete");
        clearPairJournal(journal);reporter.breadcrumb(tag,"Committed paired files in "+journal.getParentFile().getName());
    }
    /**
     * Crash recovery is deliberately roll-forward. Both replacement files are fully fsynced before
     * PREPARED is journaled, so a process death can never expose half-generated data as authoritative.
     * If one rename already happened, its .pairnew is absent and the other half is simply completed.
     */
    private void recoverPairIfNeeded(File journal,File first,File firstPrepared,File second,File secondPrepared,String tag){
        if(journal==null||!journal.isFile()){
            // Orphan staging files can only come from a crash before the durable journal appeared.
            deleteQuietly(firstPrepared);deleteQuietly(secondPrepared);return;
        }
        try{
            JSONObject o=new JSONObject(AtomicFileStore.readTextWithBackup(journal));String state=o.optString("state","");
            if("PREPARED_CLEAR_ALL".equals(state)){
                deleteFileFamily(first);deleteFileFamily(second);deleteQuietly(firstPrepared);deleteQuietly(secondPrepared);File base=journal.getParentFile();deleteRecursive(new File(base,"checkpoint"));deleteRecursive(new File(base,"visual_pass"));deleteRecursive(new File(base,"textures"));deleteRecursive(new File(base,"textures.importnew"));clearPairJournal(journal);reporter.breadcrumb(tag,"Completed authoritative scene clear in "+base.getName());return;
            }
            if("PREPARING_CLEAR_SECOND".equals(state)){deleteQuietly(firstPrepared);deleteQuietly(secondPrepared);clearPairJournal(journal);reporter.breadcrumb(tag,"Discarded unprepared scene-clear transaction in "+journal.getParentFile().getName());return;}
            if("PREPARED_CLEAR_SECOND".equals(state)){
                if(firstPrepared.isFile())installPrepared(first,firstPrepared);else if(!first.isFile())throw new IOException("Prepared scene metadata missing");deleteFileFamily(second);deleteQuietly(secondPrepared);clearPairJournal(journal);reporter.breadcrumb(tag,"Completed scene import with cleared point cloud in "+journal.getParentFile().getName());return;
            }
            if("PREPARING".equals(state)){
                deleteQuietly(firstPrepared);deleteQuietly(secondPrepared);clearPairJournal(journal);reporter.breadcrumb(tag,"Discarded unprepared pair transaction in "+journal.getParentFile().getName());return;
            }
            if(!"PREPARED".equals(state))throw new IOException("Unknown pair transaction state: "+state);
            if(firstPrepared.isFile())installPrepared(first,firstPrepared);else if(!first.isFile())throw new IOException("First paired file missing after prepared transaction");
            if(secondPrepared.isFile())installPrepared(second,secondPrepared);else if(!second.isFile())throw new IOException("Second paired file missing after prepared transaction");
            if(!first.isFile()||!second.isFile())throw new IOException("Recovered pair is incomplete");
            clearPairJournal(journal);reporter.breadcrumb(tag,"Completed interrupted pair transaction in "+journal.getParentFile().getName());
        }catch(Exception e){reporter.error(tag,"Could not reconcile pair transaction; journal retained",e);}
    }
    private void installPrepared(File target,File prepared)throws IOException{
        if(prepared==null||!prepared.isFile())return;AtomicFileStore.replaceBinary(target,prepared);
    }
    private void clearPairJournal(File journal){
        if(journal==null)return;deleteQuietly(journal);deleteQuietly(new File(journal.getParentFile(),journal.getName()+".bak"));deleteQuietly(new File(journal.getParentFile(),journal.getName()+".tmp"));
    }
    private void deleteQuietly(File f){if(f!=null&&f.exists()&&!f.delete())f.deleteOnExit();}

    private void writeTextPrepared(File target,String text)throws IOException{
        File parent=target.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("Cannot create "+parent);
        try(FileOutputStream fos=new FileOutputStream(target,false);OutputStreamWriter osw=new OutputStreamWriter(fos,"UTF-8");BufferedWriter out=new BufferedWriter(osw)){
            out.write(text==null?"":text);out.flush();fos.getFD().sync();
        }catch(IOException e){deleteQuietly(target);throw e;}
        if(!target.isFile()||target.length()<=0)throw new IOException("Prepared text file is empty");
    }
    private int writePointsPrepared(File target,List<CloudPoint> points)throws Exception{
        if(points.size()>MAX_STORED_POINTS)throw new IOException("Too many point-cloud samples");int valid=0;
        File parent=target.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("Cannot create "+parent);
        try(FileOutputStream fos=new FileOutputStream(target,false);DataOutputStream out=new DataOutputStream(new BufferedOutputStream(fos))){
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(0);
            for(CloudPoint p:points){if(p==null||!finite(p.x)||!finite(p.y)||!finite(p.z)||!finite(p.confidence))continue;if(Math.abs(p.x)>10000f||Math.abs(p.y)>10000f||Math.abs(p.z)>10000f)continue;out.writeFloat(p.x);out.writeFloat(p.y);out.writeFloat(p.z);out.writeFloat(Math.max(0f,Math.min(1f,p.confidence)));valid++;}
            out.flush();fos.getFD().sync();
        }catch(Exception e){deleteQuietly(target);throw e;}
        if(valid<=0){deleteQuietly(target);throw new IOException("Point cloud contains no valid points");}
        try(RandomAccessFile raf=new RandomAccessFile(target,"rw")){raf.seek(8);raf.writeInt(valid);raf.getFD().sync();}
        return valid;
    }

    private boolean validPointFile(File f){try{validatePointFile(f);return true;}catch(Exception ignored){return false;}}
    private boolean finite(float v){return !Float.isNaN(v)&&!Float.isInfinite(v);}
    private List<CloudPoint> readPoints(File f,int maxPoints){
        List<CloudPoint> out=new ArrayList<>();if(f==null||!f.isFile())return out;
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(f)))){
            if(in.readInt()!=MAGIC)return out;int version=in.readInt();if(version!=VERSION)return out;int count=in.readInt();if(count<0||count>MAX_STORED_POINTS)return out;long minBytes=12L+16L*count;if(f.length()<minBytes)return out;
            int safeMax=maxPoints>0?Math.min(maxPoints,300000):300000;int stride=count>safeMax?Math.max(1,(count+safeMax-1)/safeMax):1;
            for(int i=0;i<count;i++){float x=in.readFloat(),y=in.readFloat(),z=in.readFloat(),c=in.readFloat();if(i%stride!=0)continue;if(!finite(x)||!finite(y)||!finite(z)||!finite(c))continue;if(Math.abs(x)>10000f||Math.abs(y)>10000f||Math.abs(z)>10000f)continue;out.add(new CloudPoint(x,y,z,Math.max(0f,Math.min(1f,c))));}
        }catch(Exception e){reporter.error("POINT_READ","Failed "+f.getName(),e);out.clear();}
        return out;
    }
    private void deleteRecursive(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] c=f.listFiles();if(c!=null)for(File x:c)deleteRecursive(x);}f.delete();}
}
