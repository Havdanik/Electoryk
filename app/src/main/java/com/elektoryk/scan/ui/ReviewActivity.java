package com.elektoryk.scan.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.elektoryk.scan.model.ReviewIssue;
import com.elektoryk.scan.model.SceneSnapshot;
import com.elektoryk.scan.storage.SceneRepository;

import java.util.Locale;

public class ReviewActivity extends Activity {
    private String projectId;
    private SceneRepository repo;
    private SceneSnapshot scene;
    private LinearLayout list;
    private TextView summary;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        projectId=getIntent().getStringExtra("projectId");
        repo=new SceneRepository(this);
        scene=repo.loadScene(projectId);

        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(11,11,13));
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,24));
        scroll.addView(root);

        root.addView(Ui.brandHeader(this,"ELEKTORYK • Clean","Перевірка сумнівної геометрії після скану.","CLEAN",true));
        root.addView(Ui.screenIntro(this,"RAW → CLEAN","Smart Cleanup","Алгоритм лише пропонує. Ти підтверджуєш: конструкція, предмет перед стіною або помилка скану."),Ui.lpMatchWrap(this,10));
        summary=Ui.muted(this,"");
        root.addView(summary,Ui.lpMatchWrap(this,8));

        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list,Ui.lpMatchWrap(this,8));

        root.addView(Ui.bottomNav(this,projectId,"SCAN"),Ui.lpMatchWrap(this,18));
        setContentView(scroll);
        render();
    }

    private void render(){
        list.removeAllViews();
        if(scene==null){
            summary.setText("Немає Smart Scan сцени.");
            return;
        }
        int pending=0;
        for(ReviewIssue r:scene.reviewIssues)
            if(r.decision==ReviewIssue.Decision.UNREVIEWED) pending++;
        summary.setText("Всього: "+scene.reviewIssues.size()+" • не перевірено: "+pending);

        if(scene.reviewIssues.isEmpty()){
            list.addView(Ui.muted(this,"Сумнівних внутрішніх кластерів не знайдено."));
            return;
        }

        for(ReviewIssue r:scene.reviewIssues){
            LinearLayout card=Ui.card(this);
            String title=r.suggestedType.name()+" • "
                    +String.format(Locale.US,"%.0f%%",r.confidence*100);
            card.addView(Ui.title(this,title,17));
            card.addView(Ui.muted(this,r.reason));
            card.addView(Ui.muted(this,String.format(Locale.US,
                    "%.2f × %.2f × %.2f м • %d точок • рішення: %s",
                    r.widthM(),r.heightM(),r.depthM(),r.supportPoints,r.decision.name())),
                    Ui.lpMatchWrap(this,5));

            Button keep=Ui.button(this,"Це конструкція — залишити");
            keep.setOnClickListener(v -> decide(r,ReviewIssue.Decision.KEEP_ARCHITECTURE));
            card.addView(keep,Ui.lpMatchWrap(this,8));

            Button obj=Ui.button(this,"Це предмет — прибрати з CLEAN");
            obj.setOnClickListener(v -> decide(r,ReviewIssue.Decision.REMOVE_AS_OBJECT));
            card.addView(obj,Ui.lpMatchWrap(this,6));

            Button artifact=Ui.button(this,"Це помилка скану — прибрати");
            artifact.setOnClickListener(v -> decide(r,ReviewIssue.Decision.REMOVE_AS_ARTIFACT));
            card.addView(artifact,Ui.lpMatchWrap(this,6));

            list.addView(card,Ui.lpMatchWrap(this,10));
        }
    }

    private void decide(ReviewIssue r,ReviewIssue.Decision d){
        r.decision=d;
        repo.save(projectId,scene,null);
        render();
    }
}
