package com.elektoryk.scan.storage;

import android.content.Context;
import com.elektoryk.scan.model.CommercialPlan;
import org.json.JSONObject;
import java.io.File;

public class CommercialRepository {
    private final File root;
    private final StabilityReporter reporter;
    public CommercialRepository(Context c){root=new File(c.getFilesDir(),"commercial");if(!root.exists())root.mkdirs();reporter=new StabilityReporter(c);}
    private File file(String id){return new File(root,ProjectIdGuard.requireSafe(id)+".json");}
    public synchronized CommercialPlan load(String id){
        CommercialPlan e=new CommercialPlan();e.projectId=ProjectIdGuard.isSafe(id)?id:"";if(!ProjectIdGuard.isSafe(id))return e;
        File f=file(id);if(!f.exists())return e;
        try{CommercialPlan p=CommercialPlan.fromJson(new JSONObject(AtomicFileStore.readText(f)));p.projectId=id;return p;}
        catch(Exception x){File b=new File(f.getParentFile(),f.getName()+".bak");if(b.exists())try{CommercialPlan p=CommercialPlan.fromJson(new JSONObject(AtomicFileStore.readText(b)));p.projectId=id;if(AtomicFileStore.restoreBackup(f))reporter.breadcrumb("COMMERCIAL_RECOVERY","Restored backup "+id);else reporter.breadcrumb("COMMERCIAL_RECOVERY","Loaded backup; main restore failed "+id);return p;}catch(Exception ignored){}reporter.error("COMMERCIAL_READ","Corrupt commercial plan "+id,x);return e;}
    }
    public synchronized void save(CommercialPlan p){
        if(p==null||!ProjectIdGuard.isSafe(p.projectId))throw new IllegalArgumentException("Unsafe/missing project id");
        p.updatedAt=System.currentTimeMillis();p.recalc();
        try{AtomicFileStore.writeText(file(p.projectId),p.toJson().toString(2));}
        catch(Exception e){reporter.error("COMMERCIAL_SAVE","Failed "+p.projectId,e);throw new RuntimeException(e);}
    }
}
