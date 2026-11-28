package com.elektoryk.scan.storage;

import android.content.Context;
import com.elektoryk.scan.model.*;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

public class EvidenceRepository {
    private final File root;private final StabilityReporter reporter;
    public EvidenceRepository(Context c){root=new File(c.getFilesDir(),"evidence");if(!root.exists())root.mkdirs();reporter=new StabilityReporter(c);}
    private File dir(String id){File d=new File(root,ProjectIdGuard.requireSafe(id));if(!d.exists())d.mkdirs();return d;}
    private File indexFile(String id){return new File(dir(id),"index.json");}
    public synchronized EvidenceIndex load(String id){
        EvidenceIndex empty=new EvidenceIndex();empty.projectId=ProjectIdGuard.isSafe(id)?id:"";if(!ProjectIdGuard.isSafe(id))return empty;
        File f=indexFile(id);if(!f.exists())return empty;EvidenceIndex y;
        try{y=EvidenceIndex.fromJson(new JSONObject(AtomicFileStore.readText(f)));y.projectId=id;}
        catch(Exception e){File bak=new File(f.getParentFile(),f.getName()+".bak");if(bak.exists())try{y=EvidenceIndex.fromJson(new JSONObject(AtomicFileStore.readText(bak)));y.projectId=id;if(AtomicFileStore.restoreBackup(f))reporter.breadcrumb("EVIDENCE_RECOVERY","Restored backup "+id);else reporter.breadcrumb("EVIDENCE_RECOVERY","Loaded backup; main restore failed "+id);}catch(Exception ignored){reporter.error("EVIDENCE_READ","Corrupt evidence index "+id,e);return empty;}else{reporter.error("EVIDENCE_READ","Corrupt evidence index "+id,e);return empty;}}
        reconcile(y);return y;
    }
    public synchronized EvidenceItem add(String projectId,InputStream in,String originalName,String mime,EvidenceItem.Category category)throws Exception{return add(projectId,in,originalName,mime,category,0,"");}
    public synchronized EvidenceItem add(String projectId,InputStream in,String originalName,String mime,EvidenceItem.Category category,int circuitNo,String cutKey)throws Exception{
        ProjectIdGuard.requireSafe(projectId);if(in==null)throw new IllegalArgumentException("Evidence stream missing");
        EvidenceIndex idx=load(projectId);EvidenceItem item=new EvidenceItem();item.category=category==null?EvidenceItem.Category.OTHER:category;item.mime=mime==null?"application/octet-stream":mime;item.circuitNo=Math.max(0,circuitNo);item.cutKey=cutKey==null?"":cutKey;
        String ext=extension(originalName,item.mime);item.fileName=item.id+ext;File out=new File(dir(projectId),item.fileName);
        AtomicFileStore.writeStream(out,in,25L*1024L*1024L);
        try{idx.items.add(item);save(idx);return item;}catch(Exception e){if(out.exists())out.delete();throw e;}
    }
    public synchronized void delete(String projectId,String itemId){
        if(!ProjectIdGuard.isSafe(projectId)||itemId==null)return;EvidenceIndex idx=load(projectId);File victim=null;
        Iterator<EvidenceItem> it=idx.items.iterator();while(it.hasNext()){EvidenceItem e=it.next();if(itemId.equals(e.id)){String n=StoredFileNameGuard.sanitizeLeaf(e.fileName);if(!n.isEmpty())victim=new File(dir(projectId),n);it.remove();break;}}
        save(idx);if(victim!=null&&victim.exists()&&!victim.delete())reporter.breadcrumb("EVIDENCE_DELETE","Could not remove orphan "+victim.getName());
        try{save(idx);}catch(Exception ignored){}
    }
    public File file(String projectId,EvidenceItem e){ProjectIdGuard.requireSafe(projectId);String n=StoredFileNameGuard.sanitizeLeaf(e==null?"":e.fileName);return new File(dir(projectId),n.isEmpty()?"__invalid__":n);}
    public File projectDir(String projectId){return dir(projectId);}
    public synchronized void save(EvidenceIndex i){if(i==null||!ProjectIdGuard.isSafe(i.projectId))throw new IllegalArgumentException("Unsafe/missing project id");sanitize(i);try{AtomicFileStore.writeText(indexFile(i.projectId),i.toJson().toString(2));}catch(Exception e){reporter.error("EVIDENCE_SAVE","Failed "+i.projectId,e);throw new RuntimeException(e);}}
    private void sanitize(EvidenceIndex idx){Iterator<EvidenceItem> it=idx.items.iterator();Set<String> ids=new HashSet<>();while(it.hasNext()){EvidenceItem e=it.next();if(e==null||e.id==null||e.id.isEmpty()||!ids.add(e.id)){it.remove();continue;}String n=StoredFileNameGuard.sanitizeLeaf(e.fileName);if(n.isEmpty()){it.remove();continue;}e.fileName=n;}}
    private void reconcile(EvidenceIndex idx){sanitize(idx);Iterator<EvidenceItem> it=idx.items.iterator();int removed=0;while(it.hasNext()){EvidenceItem e=it.next();File f=new File(dir(idx.projectId),e.fileName);if(!f.isFile()||f.length()<=0){it.remove();removed++;}}if(removed>0)reporter.breadcrumb("EVIDENCE_REPAIR","Removed "+removed+" stale refs "+idx.projectId);}
    private String extension(String n,String mime){if(n!=null){int i=n.lastIndexOf('.');if(i>=0&&i>n.length()-8){String x=n.substring(i).replaceAll("[^A-Za-z0-9.]","");if(x.length()>1&&x.length()<=7)return x;}}if(mime!=null&&mime.contains("png"))return ".png";if(mime!=null&&mime.contains("jpeg"))return ".jpg";return ".bin";}
}
