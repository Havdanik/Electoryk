package com.elektoryk.scan.storage;

import android.content.Context;
import com.elektoryk.scan.model.ObjectFlowState;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ObjectFlowRepository {
    private final File root;
    private final StabilityReporter reporter;
    public ObjectFlowRepository(Context c){root=new File(c.getFilesDir(),"object_flow");if(!root.exists())root.mkdirs();reporter=new StabilityReporter(c);}
    public File dir(String id){File d=new File(root,ProjectIdGuard.requireSafe(id));if(!d.exists())d.mkdirs();return d;}
    private File stateFile(String id){return new File(dir(id),"state.json");}
    public synchronized ObjectFlowState load(String id){
        ObjectFlowState empty=new ObjectFlowState();empty.projectId=ProjectIdGuard.isSafe(id)?id:"";if(!ProjectIdGuard.isSafe(id))return empty;
        File f=stateFile(id);if(!f.exists())return empty;
        ObjectFlowState s;
        try{s=ObjectFlowState.fromJson(new JSONObject(AtomicFileStore.readText(f)));s.projectId=id;}
        catch(Exception e){
            File bak=new File(f.getParentFile(),f.getName()+".bak");
            if(bak.exists())try{s=ObjectFlowState.fromJson(new JSONObject(AtomicFileStore.readText(bak)));s.projectId=id;if(AtomicFileStore.restoreBackup(f))reporter.breadcrumb("FLOW_RECOVERY","Restored backup "+id);else reporter.breadcrumb("FLOW_RECOVERY","Loaded backup; main restore failed "+id);}catch(Exception ignored){reporter.error("FLOW_READ","Corrupt object flow "+id,e);return empty;}
            else{reporter.error("FLOW_READ","Corrupt object flow "+id,e);return empty;}
        }
        reconcileAudioRefs(s);
        return s;
    }
    public synchronized void save(ObjectFlowState s){if(s==null||!ProjectIdGuard.isSafe(s.projectId))throw new IllegalArgumentException("Unsafe/missing project id");sanitizeAudioNames(s);s.updatedAt=System.currentTimeMillis();try{AtomicFileStore.writeText(stateFile(s.projectId),s.toJson().toString(2));}catch(Exception e){reporter.error("FLOW_SAVE","Failed "+s.projectId,e);throw new RuntimeException(e);}}
    public File audioDir(String id){File d=new File(dir(id),"audio");if(!d.exists())d.mkdirs();return d;}

    private void sanitizeAudioNames(ObjectFlowState s){
        List<String> clean=new ArrayList<>();
        for(String raw:s.consultationAudioFiles){String n=StoredFileNameGuard.sanitizeLeaf(raw);if(!n.isEmpty()&&!clean.contains(n))clean.add(n);}
        s.consultationAudioFiles.clear();s.consultationAudioFiles.addAll(clean);
    }
    private void reconcileAudioRefs(ObjectFlowState s){
        List<String> clean=new ArrayList<>();File d=audioDir(s.projectId);
        for(String raw:s.consultationAudioFiles){String n=StoredFileNameGuard.sanitizeLeaf(raw);if(n.isEmpty()||clean.contains(n))continue;File f=new File(d,n);if(f.isFile()&&f.length()>1024)clean.add(n);}
        if(clean.size()!=s.consultationAudioFiles.size()){s.consultationAudioFiles.clear();s.consultationAudioFiles.addAll(clean);reporter.breadcrumb("FLOW_REPAIR","Removed stale/unsafe audio refs "+s.projectId);}
    }
}
