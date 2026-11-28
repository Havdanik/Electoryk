package com.elektoryk.scan.storage;

import java.io.*;

public final class AtomicFileStore {
    private AtomicFileStore() {}

    public static void writeText(File target, String text) throws IOException {
        ensureParent(target);
        File tmp = tempFor(target);
        try (FileOutputStream fos = new FileOutputStream(tmp, false);
             OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
             BufferedWriter w = new BufferedWriter(osw)) {
            w.write(text == null ? "" : text);
            w.flush();
            fos.getFD().sync();
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw e;
        }
        commit(target, tmp, backupFor(target));
    }

    public static String readTextWithBackup(File target) throws IOException {
        try { return readText(target); }
        catch (IOException primary) {
            File bak = backupFor(target);
            if (bak.exists()) return readText(bak);
            throw primary;
        }
    }

    public static String readText(File file) throws IOException {
        if (file == null || !file.isFile()) throw new FileNotFoundException(String.valueOf(file));
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            char[] buf = new char[8192];int n;
            while ((n = br.read(buf)) > 0) sb.append(buf,0,n);
        }
        return sb.toString();
    }

    public static void writeBytes(File target, byte[] data) throws IOException {
        if(data==null)throw new IllegalArgumentException("Data missing");
        ensureParent(target);File tmp=tempFor(target);
        try (FileOutputStream fos = new FileOutputStream(tmp, false);BufferedOutputStream out = new BufferedOutputStream(fos)) {
            out.write(data);out.flush();fos.getFD().sync();
        } catch(IOException e){deleteQuietly(tmp);throw e;}
        commit(target,tmp,backupFor(target));
    }

    public static long writeStream(File target,InputStream in,long maxBytes)throws IOException{
        if(in==null)throw new IllegalArgumentException("Input stream missing");ensureParent(target);File tmp=tempFor(target);long total=0;
        try(FileOutputStream fos=new FileOutputStream(tmp,false);BufferedOutputStream out=new BufferedOutputStream(fos)){
            byte[] buf=new byte[16384];int n;while((n=in.read(buf))>0){total+=n;if(maxBytes>0&&total>maxBytes)throw new IOException("File too large");out.write(buf,0,n);}out.flush();fos.getFD().sync();
        }catch(IOException e){deleteQuietly(tmp);throw e;}
        if(total<=0){deleteQuietly(tmp);throw new IOException("File is empty");}
        commit(target,tmp,backupFor(target));return total;
    }

    public static void replaceBinary(File target, File preparedTmp) throws IOException {
        if(preparedTmp==null||!preparedTmp.isFile())throw new IOException("Prepared temporary file missing");ensureParent(target);commit(target, preparedTmp, backupFor(target));
    }

    public static boolean restoreBackup(File target) {
        try {
            File bak = backupFor(target);if (!bak.isFile()) return false;
            File bad = new File(target.getParentFile(), target.getName()+".corrupt_"+System.currentTimeMillis());
            if (target.exists() && !target.renameTo(bad)) { copy(target,bad); if(!target.delete())return false; }
            try{copy(bak,target);return target.isFile()&&target.length()>0;}
            catch(Exception e){if(!target.exists()&&bad.isFile())copy(bad,target);return false;}
        } catch (Exception e) { return false; }
    }

    private static void commit(File target, File tmp, File bak) throws IOException {
        if (!tmp.isFile()) throw new IOException("Temporary file missing: "+tmp);
        boolean hadTarget=target.isFile();boolean originalRemoved=false;
        if(hadTarget)copy(target,bak);
        try{
            if(target.exists()){if(!target.delete())throw new IOException("Cannot replace "+target);originalRemoved=true;}
            if(!tmp.renameTo(target))copy(tmp,target);
            if(!target.isFile()||target.length()==0)throw new IOException("Atomic commit failed: "+target);
        }catch(IOException failure){
            if(target.exists())deleteQuietly(target);
            if(hadTarget&&originalRemoved&&bak.isFile()){
                try{copy(bak,target);}catch(IOException restore){failure.addSuppressed(restore);}
            }
            throw failure;
        }finally{deleteQuietly(tmp);}
    }

    private static File tempFor(File target){return new File(target.getParentFile(),target.getName()+".tmp");}
    private static File backupFor(File target){return new File(target.getParentFile(),target.getName()+".bak");}

    private static void copy(File from, File to) throws IOException {
        ensureParent(to);
        try (InputStream in = new BufferedInputStream(new FileInputStream(from));FileOutputStream fos = new FileOutputStream(to,false);OutputStream out = new BufferedOutputStream(fos)) {
            byte[] buf = new byte[16384];int n;while ((n=in.read(buf))>0) out.write(buf,0,n);out.flush();fos.getFD().sync();
        }catch(IOException e){deleteQuietly(to);throw e;}
    }

    private static void ensureParent(File f) throws IOException {File p=f.getParentFile();if (p!=null && !p.exists() && !p.mkdirs()) throw new IOException("Cannot create directory "+p);}
    private static void deleteQuietly(File f){if(f!=null&&f.exists()&&!f.delete())f.deleteOnExit();}
}
