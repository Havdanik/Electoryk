package com.elektoryk.scan.storage;

import android.content.Context;
import java.io.*;
import java.util.*;

public class ProjectRecoveryManager {
    private final Context context;
    private final File dir;

    public ProjectRecoveryManager(Context context){this.context=context.getApplicationContext();dir=new File(context.getFilesDir(),"recovery_packages");if(!dir.exists())dir.mkdirs();}

    public File createSnapshot(String projectId,String reason)throws Exception{
        ProjectIdGuard.requireSafe(projectId);File src=new ProjectPackageManager().createPackage(context,projectId,true);String safeReason=(reason==null?"snapshot":reason).replaceAll("[^a-zA-Z0-9_-]+","_");if(safeReason.length()>48)safeReason=safeReason.substring(0,48);
        File dst=new File(dir,projectId+"_"+System.currentTimeMillis()+"_"+safeReason+".elektoryk");File tmp=new File(dst.getAbsolutePath()+".tmp");copy(src,tmp);AtomicFileStore.replaceBinary(dst,tmp);trim(projectId,8);new StabilityReporter(context).breadcrumb("SNAPSHOT","Created "+dst.getName());return dst;
    }

    public List<File> list(String projectId){
        List<File> out=new ArrayList<>();if(!ProjectIdGuard.isSafe(projectId))return out;File[] files=dir.listFiles();if(files==null)return out;String prefix=projectId+"_";
        for(File f:files)if(f.isFile()&&f.getName().startsWith(prefix)&&f.getName().endsWith(".elektoryk"))out.add(f);Collections.sort(out,new Comparator<File>(){@Override public int compare(File a,File b){return Long.compare(b.lastModified(),a.lastModified());}});return out;
    }

    public ProjectPackageManager.ImportResult restoreAsCopy(File snapshot)throws Exception{
        if(snapshot==null||!snapshot.isFile())throw new FileNotFoundException("Snapshot missing");String n=snapshot.getName();if(!StoredFileNameGuard.isSafeLeaf(n)||!n.endsWith(".elektoryk"))throw new IOException("Unsafe snapshot file");
        File canonicalDir=dir.getCanonicalFile(),canonical=snapshot.getCanonicalFile();if(!canonical.getParentFile().equals(canonicalDir))throw new IOException("Snapshot outside recovery directory");
        try(InputStream in=new BufferedInputStream(new FileInputStream(canonical))){return new ProjectPackageManager().importPackage(context,in,true);}
    }

    private void trim(String projectId,int keep){List<File> all=list(projectId);for(int i=keep;i<all.size();i++)all.get(i).delete();}
    private void copy(File a,File b)throws Exception{try(InputStream in=new BufferedInputStream(new FileInputStream(a));FileOutputStream fos=new FileOutputStream(b);OutputStream out=new BufferedOutputStream(fos)){byte[] buf=new byte[16384];int n;while((n=in.read(buf))>0)out.write(buf,0,n);out.flush();fos.getFD().sync();}}
}
