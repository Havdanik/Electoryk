package com.elektoryk.scan.audio;

import android.media.MediaRecorder;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Visible, consent-gated consultation recorder. Never starts without caller permission/consent checks. */
public class ConsultationRecorder {
    private MediaRecorder recorder;
    private File file;
    public boolean isRecording(){return recorder!=null;}
    public File currentFile(){return file;}
    public File start(File dir) throws Exception{
        if(isRecording())return file;
        if(dir==null)throw new IllegalArgumentException("Audio directory missing");
        if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Cannot create audio directory");
        String ts=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date());file=new File(dir,"consultation_"+ts+".m4a");
        MediaRecorder r=new MediaRecorder();
        try{
            r.setAudioSource(MediaRecorder.AudioSource.MIC);r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);r.setAudioEncodingBitRate(96000);r.setAudioSamplingRate(44100);r.setOutputFile(file.getAbsolutePath());r.prepare();r.start();recorder=r;return file;
        }catch(Exception e){try{r.reset();}catch(Exception ignored){}try{r.release();}catch(Exception ignored){}if(file!=null&&file.exists())file.delete();file=null;throw e;}
    }
    public File stop(){
        MediaRecorder r=recorder;recorder=null;boolean stopped=r==null;
        if(r!=null){try{r.stop();stopped=true;}catch(Exception ignored){stopped=false;}try{r.reset();}catch(Exception ignored){}try{r.release();}catch(Exception ignored){}}
        File out=file;file=null;
        if(!stopped||out==null||!out.isFile()||out.length()<=1024){if(out!=null&&out.exists())out.delete();return null;}
        return out;
    }
    public void cancel(){File f=stop();if(f!=null&&f.exists())f.delete();}
}
