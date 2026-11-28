package com.elektoryk.scan.storage;

import android.content.Context;
import com.elektoryk.scan.model.ClientApproval;
import org.json.JSONObject;
import java.io.File;

public class ApprovalRepository {
    private final File root;
    private final StabilityReporter reporter;
    public ApprovalRepository(Context c){root=new File(c.getFilesDir(),"approvals");if(!root.exists())root.mkdirs();reporter=new StabilityReporter(c);}
    private File dir(String id){File d=new File(root,ProjectIdGuard.requireSafe(id));if(!d.exists())d.mkdirs();return d;}
    private File file(String id){return new File(dir(id),"approval.json");}
    public synchronized ClientApproval load(String id){
        ClientApproval empty=new ClientApproval();empty.projectId=ProjectIdGuard.isSafe(id)?id:"";if(!ProjectIdGuard.isSafe(id))return empty;
        File f=file(id);if(!f.exists())return empty;
        try{ClientApproval a=ClientApproval.fromJson(new JSONObject(AtomicFileStore.readText(f)));a.projectId=id;return a;}
        catch(Exception e){
            File bak=new File(f.getParentFile(),f.getName()+".bak");
            if(bak.exists())try{ClientApproval a=ClientApproval.fromJson(new JSONObject(AtomicFileStore.readText(bak)));a.projectId=id;if(AtomicFileStore.restoreBackup(f))reporter.breadcrumb("APPROVAL_RECOVERY","Restored backup "+id);else reporter.breadcrumb("APPROVAL_RECOVERY","Loaded backup; main restore failed "+id);return a;}catch(Exception ignored){}
            reporter.error("APPROVAL_READ","Corrupt approval "+id,e);return empty;
        }
    }
    public synchronized void save(ClientApproval a){if(a==null||!ProjectIdGuard.isSafe(a.projectId))throw new IllegalArgumentException("Unsafe/missing project id");try{AtomicFileStore.writeText(file(a.projectId),a.toJson().toString(2));}catch(Exception e){reporter.error("APPROVAL_SAVE","Failed "+a.projectId,e);throw new RuntimeException(e);}}
    public File signatureFile(String id){return new File(dir(id),"signature.png");}
}
