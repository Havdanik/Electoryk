package com.elektoryk.scan.audio;

import android.app.*;
import android.content.*;
import android.os.IBinder;
import com.elektoryk.scan.model.ObjectFlowState;
import com.elektoryk.scan.storage.ObjectFlowRepository;
import com.elektoryk.scan.storage.StabilityReporter;
import java.io.File;

/** Foreground microphone recorder for a client consultation. Recording survives navigation between project screens. */
public class ConsultationRecordingService extends Service {
    public static final String ACTION_START="com.elektoryk.scan.RECORD_START",ACTION_STOP="com.elektoryk.scan.RECORD_STOP",EXTRA_PROJECT="projectId";
    private static final String CH="consultation_recording";
    private ConsultationRecorder recorder;
    private String projectId="";

    @Override public void onCreate(){super.onCreate();createChannel();}
    @Override public int onStartCommand(Intent i,int flags,int id){String a=i==null?"":i.getAction();if(ACTION_STOP.equals(a)){stopRecording();stopSelf();return START_NOT_STICKY;}if(ACTION_START.equals(a)){String p=i.getStringExtra(EXTRA_PROJECT);if(p!=null&&!p.isEmpty())startRecording(p);}return START_NOT_STICKY;}

    private void startRecording(String p){
        if(recorder!=null&&recorder.isRecording()){
            if(projectId.equals(p))return;
            String msg="Вже триває запис іншого об'єкта. Спочатку зупини його.";
            getSharedPreferences("consultation_recording",MODE_PRIVATE).edit().putString("errorProject",p).putString("lastError",msg).putLong("errorAt",System.currentTimeMillis()).apply();
            new StabilityReporter(this).breadcrumb("AUDIO_RECORD","Start rejected: another project is recording");
            return;
        }
        projectId=p;
        try{
            startForeground(701,notification("Запис консультації • ELEKTORYK"));
            recorder=new ConsultationRecorder();
            recorder.start(new ObjectFlowRepository(this).audioDir(projectId));
            getSharedPreferences("consultation_recording",MODE_PRIVATE).edit().clear().putString("projectId",projectId).putLong("startedAt",System.currentTimeMillis()).apply();
        }catch(Exception e){
            String msg=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
            getSharedPreferences("consultation_recording",MODE_PRIVATE).edit().clear().putString("errorProject",projectId).putString("lastError",msg).putLong("errorAt",System.currentTimeMillis()).apply();
            new StabilityReporter(this).error("AUDIO_RECORD","Consultation recorder failed",e);
            recorder=null;
            try{stopForeground(true);}catch(Exception ignored){}
            stopSelf();
        }
    }

    private void stopRecording(){
        File completed=null;
        try{
            if(recorder!=null){completed=recorder.stop();recorder=null;}
            if(completed!=null&&completed.isFile()&&completed.length()>1024&&!projectId.isEmpty()){
                ObjectFlowRepository repo=new ObjectFlowRepository(this);ObjectFlowState s=repo.load(projectId);
                if(!s.consultationAudioFiles.contains(completed.getName()))s.consultationAudioFiles.add(completed.getName());
                repo.save(s);
            }
        }catch(Exception e){
            new StabilityReporter(this).error("AUDIO_SAVE","Could not index completed consultation recording",e);
        }finally{
            getSharedPreferences("consultation_recording",MODE_PRIVATE).edit().clear().apply();
            try{stopForeground(true);}catch(Exception ignored){}
            projectId="";
        }
    }
    @Override public void onDestroy(){if(recorder!=null)stopRecording();super.onDestroy();}
    private void createChannel(){if(android.os.Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CH,"Запис консультації",NotificationManager.IMPORTANCE_LOW);c.setDescription("Видимий запис домовленостей з клієнтом");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
    private Notification notification(String t){Intent stop=new Intent(this,ConsultationRecordingService.class);stop.setAction(ACTION_STOP);PendingIntent pi=PendingIntent.getService(this,702,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=android.os.Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CH):new Notification.Builder(this);b.setContentTitle(t).setContentText("Мікрофон активний. Натисни «Зупинити», коли консультація завершена.").setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).addAction(android.R.drawable.ic_media_pause,"Зупинити",pi);return b.build();}
    @Override public IBinder onBind(Intent intent){return null;}
}
