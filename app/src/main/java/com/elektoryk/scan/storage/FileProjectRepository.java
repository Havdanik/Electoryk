package com.elektoryk.scan.storage;

import android.content.Context;
import com.elektoryk.scan.model.ElectricalProject;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileProjectRepository implements ProjectRepository {
    private final File dir;
    private final StabilityReporter reporter;

    public FileProjectRepository(Context context) {
        dir = new File(context.getFilesDir(), "projects");
        if (!dir.exists()) dir.mkdirs();
        reporter = new StabilityReporter(context);
    }

    private File fileFor(String id) { return new File(dir, ProjectIdGuard.requireSafe(id) + ".json"); }

    @Override public synchronized List<ElectricalProject> list() {
        List<ElectricalProject> out = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            if (!f.getName().endsWith(".json")) continue;
            ElectricalProject p = read(f);
            if (p != null) out.add(p);
        }
        Collections.sort(out,new Comparator<ElectricalProject>(){
            @Override public int compare(ElectricalProject a,ElectricalProject b){return Long.compare(b.updatedAt,a.updatedAt);}
        });
        return out;
    }

    @Override public synchronized ElectricalProject get(String id) {
        if(!ProjectIdGuard.isSafe(id))return null;
        return read(fileFor(id));
    }

    @Override public synchronized void save(ElectricalProject project) {
        if(project==null)return;
        if(!ProjectIdGuard.isSafe(project.id))throw new IllegalArgumentException("Unsafe project id");
        project.updatedAt=System.currentTimeMillis();
        try{
            AtomicFileStore.writeText(fileFor(project.id),project.toJson().toString(2));
        }catch(Exception e){
            reporter.error("PROJECT_SAVE","Failed "+project.id,e);
            throw new RuntimeException("Project save failed",e);
        }
    }

    @Override public synchronized void delete(String id) {
        if(!ProjectIdGuard.isSafe(id))return;
        File f=fileFor(id);if(f.exists())f.delete();
        File bak=new File(dir,id+".json.bak");if(bak.exists())bak.delete();
    }

    public synchronized boolean restoreBackup(String id){
        if(!ProjectIdGuard.isSafe(id))return false;
        boolean ok=AtomicFileStore.restoreBackup(fileFor(id));
        if(ok)reporter.breadcrumb("PROJECT_RECOVERY","Restored "+id);
        return ok;
    }

    private ElectricalProject read(File f) {
        if(f==null||!f.exists())return null;
        try{
            ElectricalProject p=ElectricalProject.fromJson(new JSONObject(AtomicFileStore.readText(f)));
            String expected=f.getName().substring(0,f.getName().length()-5);
            if(p==null||!ProjectIdGuard.isSafe(p.id)||!expected.equals(p.id))throw new IllegalStateException("Project id/file mismatch");
            return p;
        }catch(Exception primary){
            File bak=new File(f.getParentFile(),f.getName()+".bak");
            if(bak.exists()){
                try{
                    ElectricalProject p=ElectricalProject.fromJson(new JSONObject(AtomicFileStore.readText(bak)));
                    String expected=f.getName().substring(0,f.getName().length()-5);
                    if(p==null||!ProjectIdGuard.isSafe(p.id)||!expected.equals(p.id))throw new IllegalStateException("Backup project id/file mismatch");
                    if(AtomicFileStore.restoreBackup(f))reporter.breadcrumb("PROJECT_RECOVERY","Restored backup for "+f.getName());
                    else reporter.breadcrumb("PROJECT_RECOVERY","Loaded backup; main restore failed for "+f.getName());
                    return p;
                }catch(Exception ignored){}
            }
            reporter.error("PROJECT_READ","Corrupt project "+f.getName(),primary);
            return null;
        }
    }
}
