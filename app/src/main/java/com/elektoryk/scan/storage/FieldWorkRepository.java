package com.elektoryk.scan.storage;

import android.content.Context;
import com.elektoryk.scan.model.CableCutList;
import com.elektoryk.scan.model.FieldWorkPlan;
import org.json.JSONObject;
import java.io.File;

public class FieldWorkRepository {
    private final File root;
    private final StabilityReporter reporter;
    public FieldWorkRepository(Context c){root=new File(c.getFilesDir(),"fieldwork");if(!root.exists())root.mkdirs();reporter=new StabilityReporter(c);}
    private File dir(String id){File d=new File(root,ProjectIdGuard.requireSafe(id));if(!d.exists())d.mkdirs();return d;}

    public synchronized CableCutList loadCuts(String id){
        CableCutList empty=new CableCutList();empty.projectId=ProjectIdGuard.isSafe(id)?id:"";if(!ProjectIdGuard.isSafe(id))return empty;
        File f=new File(dir(id),"cuts.json");if(!f.exists())return empty;
        try{CableCutList x=CableCutList.fromJson(new JSONObject(AtomicFileStore.readText(f)));x.projectId=id;return x;}
        catch(Exception e){File bak=new File(f.getParentFile(),f.getName()+".bak");if(bak.exists())try{CableCutList x=CableCutList.fromJson(new JSONObject(AtomicFileStore.readText(bak)));x.projectId=id;if(AtomicFileStore.restoreBackup(f))reporter.breadcrumb("CUT_RECOVERY","Restored backup "+id);else reporter.breadcrumb("CUT_RECOVERY","Loaded backup; main restore failed "+id);return x;}catch(Exception ignored){}reporter.error("CUT_READ","Corrupt cuts "+id,e);return empty;}
    }
    public synchronized void saveCuts(CableCutList l){if(l==null||!ProjectIdGuard.isSafe(l.projectId))throw new IllegalArgumentException("Unsafe/missing project id");l.updatedAt=System.currentTimeMillis();try{AtomicFileStore.writeText(new File(dir(l.projectId),"cuts.json"),l.toJson().toString(2));}catch(Exception e){reporter.error("CUT_SAVE","Failed "+l.projectId,e);throw new RuntimeException(e);}}

    public synchronized FieldWorkPlan loadPlan(String id){
        FieldWorkPlan empty=new FieldWorkPlan();empty.projectId=ProjectIdGuard.isSafe(id)?id:"";if(!ProjectIdGuard.isSafe(id))return empty;
        File f=new File(dir(id),"plan.json");if(!f.exists())return empty;
        try{FieldWorkPlan x=FieldWorkPlan.fromJson(new JSONObject(AtomicFileStore.readText(f)));x.projectId=id;return x;}
        catch(Exception e){File bak=new File(f.getParentFile(),f.getName()+".bak");if(bak.exists())try{FieldWorkPlan x=FieldWorkPlan.fromJson(new JSONObject(AtomicFileStore.readText(bak)));x.projectId=id;if(AtomicFileStore.restoreBackup(f))reporter.breadcrumb("FIELD_RECOVERY","Restored backup "+id);else reporter.breadcrumb("FIELD_RECOVERY","Loaded backup; main restore failed "+id);return x;}catch(Exception ignored){}reporter.error("FIELD_READ","Corrupt field plan "+id,e);return empty;}
    }
    public synchronized void savePlan(FieldWorkPlan p){if(p==null||!ProjectIdGuard.isSafe(p.projectId))throw new IllegalArgumentException("Unsafe/missing project id");p.updatedAt=System.currentTimeMillis();try{AtomicFileStore.writeText(new File(dir(p.projectId),"plan.json"),p.toJson().toString(2));}catch(Exception e){reporter.error("FIELD_SAVE","Failed "+p.projectId,e);throw new RuntimeException(e);}}

    public synchronized boolean restoreCutsBackup(String id){return ProjectIdGuard.isSafe(id)&&AtomicFileStore.restoreBackup(new File(dir(id),"cuts.json"));}
    public synchronized boolean restorePlanBackup(String id){return ProjectIdGuard.isSafe(id)&&AtomicFileStore.restoreBackup(new File(dir(id),"plan.json"));}
}
