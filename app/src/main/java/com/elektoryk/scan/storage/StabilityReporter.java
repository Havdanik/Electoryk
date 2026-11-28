package com.elektoryk.scan.storage;

import android.content.Context;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StabilityReporter {
    private final File log;

    public StabilityReporter(Context c) {
        File d = new File(c.getFilesDir(), "diagnostics");
        if (!d.exists()) d.mkdirs();
        log = new File(d, "stability.log");
    }

    public synchronized void breadcrumb(String tag, String message) {
        append("INFO",tag,message,null);
    }

    public synchronized void error(String tag, String message, Throwable error) {
        append("ERROR",tag,message,error);
    }

    public synchronized String readTail(int maxChars) {
        try {
            if (!log.exists()) return "";
            String s=AtomicFileStore.readText(log);
            if (s.length()<=maxChars) return s;
            return s.substring(s.length()-maxChars);
        } catch(Exception e) { return ""; }
    }

    public synchronized void clear() {
        if (log.exists()) log.delete();
    }

    private void append(String level,String tag,String message,Throwable error) {
        try {
            String old = log.exists() ? AtomicFileStore.readText(log) : "";
            String ts=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date());
            StringBuilder line=new StringBuilder();
            line.append(ts).append(" ").append(level).append(" [").append(tag).append("] ").append(message==null?"":message).append("\n");
            if(error!=null){
                line.append(error.getClass().getName()).append(": ").append(String.valueOf(error.getMessage())).append("\n");
                StackTraceElement[] stack=error.getStackTrace();
                for(int i=0;i<Math.min(12,stack.length);i++) line.append("  at ").append(stack[i]).append("\n");
            }
            String combined=old+line;
            int cap=180000;
            if(combined.length()>cap) combined=combined.substring(combined.length()-cap);
            AtomicFileStore.writeText(log,combined);
        }catch(Exception ignored){}
    }
}
