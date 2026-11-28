package com.elektoryk.scan.scanner;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import com.elektoryk.scan.storage.StabilityReporter;

public class ScanRecorder {
    private final File dir;
    private FileWriter writer;
    private File currentFile;
    private final StabilityReporter reporter;

    public ScanRecorder(Context context) {
        dir = new File(context.getFilesDir(), "scan_sessions");
        reporter = new StabilityReporter(context);
        if (!dir.exists()) dir.mkdirs();
    }

    public File start(ScanSession session) {
        close();
        try {
            String project=safe(session.projectId),sid=safe(session.id);
            if(project.isEmpty()||sid.isEmpty())throw new IllegalArgumentException("Invalid scan session id");
            currentFile = new File(dir, project + "_" + sid + ".jsonl");
            writer = new FileWriter(currentFile, false);

            JSONObject header = new JSONObject();
            header.put("recordType", "session");
            header.put("sessionId", session.id);
            header.put("projectId", session.projectId);
            header.put("startedAt", session.startedAt);
            header.put("scannerProfile", session.scannerProfile);
            writeLine(header);
            return currentFile;
        } catch (Exception e) {
            reporter.error("SCAN_RECORDER","Could not start scan session log",e);
            close();
            return null;
        }
    }

    public synchronized void recordPose(PoseSample sample) {
        if (writer == null || sample == null) return;
        try {
            JSONObject o = sample.toJson();
            o.put("recordType", "pose");
            writeLine(o);
        } catch (Exception e) { reporter.error("SCAN_RECORDER","Could not write pose",e); }
    }

    public synchronized void recordEvent(String type, String message) {
        if (writer == null) return;
        try {
            JSONObject o = new JSONObject();
            o.put("recordType", "event");
            o.put("type", type);
            o.put("message", message == null ? "" : message);
            o.put("timeMs", System.currentTimeMillis());
            writeLine(o);
        } catch (Exception e) { reporter.error("SCAN_RECORDER","Could not write event",e); }
    }

    public synchronized void finish(ScanSession session) {
        if (writer != null && session != null) {
            try {
                JSONObject o = new JSONObject();
                o.put("recordType", "end");
                o.put("endedAt", session.endedAt);
                o.put("state", session.state.name());
                writeLine(o);
            } catch (Exception e) { reporter.error("SCAN_RECORDER","Could not finish scan log",e); }
        }
        close();
    }

    private void writeLine(JSONObject o) throws Exception {
        writer.write(o.toString());
        writer.write("\n");
        writer.flush();
    }

    public synchronized void close() {
        try {
            if (writer != null) writer.close();
        } catch (Exception e) { reporter.error("SCAN_RECORDER","Could not close scan log",e); }
        writer = null;
    }

    public File getCurrentFile() {
        return currentFile;
    }

    private String safe(String value){return value==null?"":value.replaceAll("[^A-Za-z0-9_-]","_");}
}
