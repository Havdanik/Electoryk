package com.elektoryk.scan.storage;

import android.content.Context;
import com.elektoryk.scan.model.FloorPlan;
import org.json.JSONObject;
import java.io.File;

public class FloorPlanRepository {
    private final File dir;
    private final StabilityReporter reporter;

    public FloorPlanRepository(Context context) {
        dir=new File(context.getFilesDir(),"floorplans");
        if(!dir.exists())dir.mkdirs();
        reporter=new StabilityReporter(context);
    }
    private File fileFor(String projectId){return new File(dir,ProjectIdGuard.requireSafe(projectId)+".json");}

    public synchronized FloorPlan load(String projectId){
        FloorPlan empty=new FloorPlan();empty.projectId=ProjectIdGuard.isSafe(projectId)?projectId:"";
        if(!ProjectIdGuard.isSafe(projectId))return empty;
        File f=fileFor(projectId);if(!f.exists())return empty;
        try{
            FloorPlan p=FloorPlan.fromJson(new JSONObject(AtomicFileStore.readText(f)));p.projectId=projectId;return p;
        }catch(Exception e){
            File bak=new File(f.getParentFile(),f.getName()+".bak");
            if(bak.exists())try{FloorPlan p=FloorPlan.fromJson(new JSONObject(AtomicFileStore.readText(bak)));p.projectId=projectId;if(AtomicFileStore.restoreBackup(f))reporter.breadcrumb("FLOOR_RECOVERY","Restored backup "+projectId);else reporter.breadcrumb("FLOOR_RECOVERY","Loaded backup; main restore failed "+projectId);return p;}catch(Exception ignored){}
            reporter.error("FLOOR_READ","Corrupt floor plan "+projectId,e);return empty;
        }
    }

    public synchronized void save(FloorPlan plan){
        if(plan==null||!ProjectIdGuard.isSafe(plan.projectId))throw new IllegalArgumentException("Unsafe/missing project id");
        plan.updatedAt=System.currentTimeMillis();
        try{AtomicFileStore.writeText(fileFor(plan.projectId),plan.toJson().toString(2));}
        catch(Exception e){reporter.error("FLOOR_SAVE","Failed "+plan.projectId,e);throw new RuntimeException("Floor plan save failed",e);}
    }

    public synchronized boolean restoreBackup(String id){return ProjectIdGuard.isSafe(id)&&AtomicFileStore.restoreBackup(fileFor(id));}
}
