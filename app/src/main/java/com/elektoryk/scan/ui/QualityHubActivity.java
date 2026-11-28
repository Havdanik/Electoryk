package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import com.elektoryk.scan.model.*;
import com.elektoryk.scan.reconstruction.ElectricalDesignAnalyzer;
import com.elektoryk.scan.storage.*;

public class QualityHubActivity extends Activity {
    private String projectId;

    @Override protected void onCreate(Bundle b){super.onCreate(b);projectId=getIntent().getStringExtra("projectId");buildUi();}

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));scroll.addView(root);
        root.addView(Ui.brandHeader(this,"ELEKTORYK • Quality Hub","Перед здачею: геометрія, електроплан, захист і фактичний запуск.","QA",true));

        SceneSnapshot scene=new SceneRepository(this).loadScene(projectId);
        FloorPlan floor=new FloorPlanRepository(this).load(projectId);
        FieldWorkPlan field=new FieldWorkRepository(this).loadPlan(projectId);
        ApartmentPlan plan=scene==null?null:scene.apartmentPlan;
        ElectricalDesignReport electrical=new ElectricalDesignAnalyzer().analyze(plan,floor);

        int precisionScore=scene!=null&&scene.precisionReport!=null?scene.precisionReport.score:0;
        int confirmed=0,pass=0,fail=0,pending=0;
        if(field!=null)for(CircuitProtectionPlan c:field.circuits){
            if(c.verifiedByElectrician)confirmed++;
            if("PASS".equals(c.testStatus))pass++;else if("FAIL".equals(c.testStatus))fail++;else pending++;
        }
        int circuits=field==null?0:field.circuits.size();
        int protectionScore=circuits==0?0:(int)Math.round(confirmed*100.0/circuits);
        int commissioningScore=circuits==0?0:(int)Math.round(pass*100.0/circuits);
        int overall=(precisionScore+electrical.score+protectionScore+commissioningScore)/4;
        if(fail>0)overall=Math.min(overall,55);

        LinearLayout summary=Ui.card(this);summary.addView(Ui.title(this,"Quality readiness • "+overall+"%",23));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(overall);summary.addView(bar,Ui.lpMatchWrap(this,7));
        summary.addView(Ui.muted(this,fail>0?"Є групи зі статусом «Проблема». VIP здачу краще не закривати до виправлення.":pending>0?"Не всі групи пройшли commissioning.":"Критичних незавершених commissioning-пунктів не видно."),Ui.lpMatchWrap(this,6));root.addView(summary,Ui.lpMatchWrap(this,10));

        root.addView(card("ТОЧНІСТЬ","Геометрія • "+precisionScore+"/100",scene!=null&&scene.precisionReport!=null?"Очікувана похибка ≈ "+String.format(java.util.Locale.US,"%.1f см",scene.precisionReport.estimatedPlanErrorCm):"Ще немає контрольних замірів.","Контрольні заміри",AccuracyActivity.class),Ui.lpMatchWrap(this,8));
        root.addView(card("ЕЛЕКТРОПЛАН","QA • "+electrical.score+"/100","Точок "+electrical.totalPoints+" • без групи "+electrical.unassignedCircuitPoints+" • дублікати "+electrical.duplicateCandidates,"Перевірити електроплан",ElectricalCheckActivity.class),Ui.lpMatchWrap(this,8));
        root.addView(card("ЗАХИСТ","Підтверджено "+confirmed+" / "+circuits,"Номінали, характеристика, фаза і дифзахист мають бути підтверджені електриком.","Щит / групи",FieldWorkActivity.class),Ui.lpMatchWrap(this,8));
        root.addView(card("COMMISSIONING","OK "+pass+" • проблема "+fail+" • pending "+pending,"Фактична напруга, ізоляція, час RCD/RCBO та інші вимірювання по групах.","Вимірювання після монтажу",CommissioningActivity.class),Ui.lpMatchWrap(this,8));

        Button handover=Ui.button(this,"Перейти до VIP здачі");handover.setEnabled(overall>=75&&fail==0);handover.setOnClickListener(v->open(HandoverActivity.class));root.addView(handover,Ui.lpMatchWrap(this,14));
        root.addView(Ui.bottomNav(this,projectId,"CLIENT"),Ui.lpMatchWrap(this,18));setContentView(scroll);
    }

    private LinearLayout card(String tag,String title,String desc,String action,Class<?> target){
        LinearLayout c=Ui.card(this);c.addView(Ui.chip(this,tag));c.addView(Ui.title(this,title,19),Ui.lpMatchWrap(this,5));c.addView(Ui.muted(this,desc),Ui.lpMatchWrap(this,3));Button b=Ui.secondaryButton(this,action);b.setOnClickListener(v->open(target));c.addView(b,Ui.lpMatchWrap(this,9));return c;
    }
    private void open(Class<?> c){Intent i=new Intent(this,c);i.putExtra("projectId",projectId);startActivity(i);}
    @Override protected void onResume(){super.onResume();}
}
