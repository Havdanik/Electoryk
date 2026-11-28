package com.elektoryk.scan.storage;

import android.content.Context;
import com.elektoryk.scan.model.*;
import org.json.JSONObject;
import java.io.*;

public class RevisionRepository {
    private final Context c;
    private final File root;
    private final File packages;
    private final StabilityReporter reporter;

    public RevisionRepository(Context c){
        this.c=c.getApplicationContext();root=new File(c.getFilesDir(),"revisions");if(!root.exists())root.mkdirs();packages=new File(root,"packages");if(!packages.exists())packages.mkdirs();reporter=new StabilityReporter(c);
    }
    private File file(String id){return new File(root,ProjectIdGuard.requireSafe(id)+".json");}
    public synchronized RevisionHistory load(String id){
        RevisionHistory h=new RevisionHistory();h.projectId=ProjectIdGuard.isSafe(id)?id:"";if(!ProjectIdGuard.isSafe(id))return h;File f=file(id);if(!f.exists())return h;
        try{RevisionHistory x=RevisionHistory.fromJson(new JSONObject(AtomicFileStore.readText(f)));x.projectId=id;sanitize(x);return x;}
        catch(Exception e){File bak=new File(f.getParentFile(),f.getName()+".bak");if(bak.exists())try{RevisionHistory x=RevisionHistory.fromJson(new JSONObject(AtomicFileStore.readText(bak)));x.projectId=id;sanitize(x);if(AtomicFileStore.restoreBackup(f))reporter.breadcrumb("REVISION_RECOVERY","Restored backup "+id);else reporter.breadcrumb("REVISION_RECOVERY","Loaded backup; main restore failed "+id);return x;}catch(Exception ignored){}reporter.error("REVISION_READ","Corrupt revision history "+id,e);return h;}
    }
    public synchronized void save(RevisionHistory h){if(h==null||!ProjectIdGuard.isSafe(h.projectId))throw new IllegalArgumentException("Unsafe/missing project id");sanitize(h);try{AtomicFileStore.writeText(file(h.projectId),h.toJson().toString(2));}catch(Exception e){reporter.error("REVISION_SAVE","Failed "+h.projectId,e);throw new RuntimeException(e);}}

    public synchronized RevisionSummary create(String projectId,RevisionSummary.Reason reason,double estimateTotal)throws Exception{return create(projectId,reason,estimateTotal,"");}
    public synchronized RevisionSummary create(String projectId,RevisionSummary.Reason reason,double estimateTotal,String note)throws Exception{
        ProjectIdGuard.requireSafe(projectId);ElectricalProject p=new FileProjectRepository(c).get(projectId);if(p==null)throw new IllegalArgumentException("Project not found");
        SceneSnapshot s=new SceneRepository(c).loadScene(projectId);FloorPlan f=new FloorPlanRepository(c).load(projectId);FieldWorkRepository fw=new FieldWorkRepository(c);FieldWorkPlan field=fw.loadPlan(projectId);
        InstallationRoutingResult route=(s!=null&&s.apartmentPlan!=null)?new com.elektoryk.scan.reconstruction.InstallationRoutingEngine().build(p,s.apartmentPlan,f):new InstallationRoutingResult();RevisionHistory h=load(projectId);
        int version=1;for(RevisionSummary old:h.revisions)if(old!=null)version=Math.max(version,old.versionNo+1);
        File src=new ProjectPackageManager().createPackage(c,projectId,true);String snapshotName=projectId+"_V"+version+"_"+System.currentTimeMillis()+".elektoryk";File dst=new File(packages,snapshotName);File tmp=new File(packages,snapshotName+".tmp");copy(src,tmp);AtomicFileStore.replaceBinary(dst,tmp);
        try{RevisionSummary r=new RevisionSummary();r.versionNo=version;r.reason=reason==null?RevisionSummary.Reason.BASELINE:reason;r.snapshotFile=dst.getName();r.areaM2=p.approximateAreaM2;r.pointCount=f==null?0:f.points.size();r.circuitCount=field.circuits.size();r.cablePurchaseM=route.totalPurchaseCableM;r.estimateTotal=estimateTotal;r.note=note==null?"":note.trim();h.revisions.add(r);save(h);return r;}
        catch(Exception e){if(dst.exists())dst.delete();throw e;}
    }
    public File packageFile(RevisionSummary r){if(r==null)return null;String n=StoredFileNameGuard.sanitizeLeaf(r.snapshotFile);if(n.isEmpty()||!n.equals(r.snapshotFile)||!n.endsWith(".elektoryk"))return null;File f=new File(packages,n);return f.isFile()?f:null;}
    private void sanitize(RevisionHistory h){if(h==null)return;for(RevisionSummary r:h.revisions){if(r==null)continue;String n=StoredFileNameGuard.sanitizeLeaf(r.snapshotFile);r.snapshotFile=(n.endsWith(".elektoryk")?n:"");}}
    private void copy(File a,File b)throws Exception{try(InputStream in=new BufferedInputStream(new FileInputStream(a));FileOutputStream fos=new FileOutputStream(b);OutputStream out=new BufferedOutputStream(fos)){byte[] x=new byte[16384];int n;while((n=in.read(x))>0)out.write(x,0,n);out.flush();fos.getFD().sync();}}
}
