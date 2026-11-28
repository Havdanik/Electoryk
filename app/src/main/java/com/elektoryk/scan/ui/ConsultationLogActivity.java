package com.elektoryk.scan.ui;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.*;
import com.elektoryk.scan.model.ObjectFlowState;
import com.elektoryk.scan.storage.ObjectFlowRepository;
import java.io.File;
import java.text.DateFormat;
import java.util.*;

/** Read-only in-app journal of consented client consultation recordings. */
public class ConsultationLogActivity extends Activity {
    private String projectId;
    private ObjectFlowRepository repo;
    private MediaPlayer player;
    private Button playingButton;

    @Override protected void onCreate(Bundle b){super.onCreate(b);projectId=getIntent().getStringExtra("projectId");repo=new ObjectFlowRepository(this);build();}

    private void build(){
        stopPlayer();ObjectFlowState state=repo.load(projectId);
        ScrollView s=new ScrollView(this);s.setBackgroundColor(Ui.BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));s.addView(root);
        root.addView(Ui.brandHeader(this,"ELEKTORYK • Консультації","Аудіо домовленостей, записане після згоди клієнта.","AUDIO LOG",true));
        root.addView(Ui.screenIntro(this,"ІСТОРІЯ","Не губи контекст домовленостей","Записи не заміняють письмове погодження. Для ключових рішень використовуй ревізію + примітку + підпис клієнта. FULL-експорт проєкту включає ці аудіофайли."),Ui.lpMatchWrap(this,10));
        if(state.consultationAudioFiles.isEmpty()){root.addView(Ui.muted(this,"Ще немає збережених записів."),Ui.lpMatchWrap(this,12));}
        DateFormat df=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT);
        for(String name:state.consultationAudioFiles){
            File f=new File(repo.audioDir(projectId),new File(name).getName());if(!f.exists())continue;
            LinearLayout c=Ui.card(this);c.addView(Ui.title(this,"Консультація",17));c.addView(Ui.muted(this,df.format(new Date(f.lastModified()))+String.format(Locale.US," • %.1f MB",f.length()/1048576.0)),Ui.lpMatchWrap(this,4));
            Button play=Ui.secondaryButton(this,"▶ Прослухати");play.setOnClickListener(v->toggle(f,play));c.addView(play,Ui.lpMatchWrap(this,7));root.addView(c,Ui.lpMatchWrap(this,8));
        }
        root.addView(Ui.muted(this,"Запис ведеться тільки після зафіксованої згоди. Видимий foreground-індикатор лишається активним, поки мікрофон записує."),Ui.lpMatchWrap(this,12));
        setContentView(s);
    }

    private void toggle(File f,Button b){
        if(player!=null){stopPlayer();return;}
        try{player=new MediaPlayer();player.setDataSource(f.getAbsolutePath());player.setOnCompletionListener(mp->stopPlayer());player.prepare();player.start();playingButton=b;b.setText("■ Зупинити");}
        catch(Exception e){stopPlayer();Toast.makeText(this,"Не вдалося відтворити: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void stopPlayer(){if(player!=null){try{player.stop();}catch(Exception ignored){}try{player.release();}catch(Exception ignored){}player=null;}if(playingButton!=null){playingButton.setText("▶ Прослухати");playingButton=null;}}
    @Override protected void onStop(){stopPlayer();super.onStop();}
}
