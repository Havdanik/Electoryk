package com.elektoryk.scan.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.elektoryk.scan.model.*;
import com.elektoryk.scan.storage.*;
import java.text.DateFormat;
import java.util.*;

public class RevisionActivity extends Activity {
    private String projectId;private RevisionRepository repo;private RevisionHistory history;private ChoiceStrip reason;private TextView status;private EditText changeNote;private volatile boolean busy;
    @Override protected void onCreate(Bundle b){super.onCreate(b);projectId=getIntent().getStringExtra("projectId");repo=new RevisionRepository(this);buildUi();}
    private void buildUi(){history=repo.load(projectId);CommercialPlan cp=new CommercialRepository(this).load(projectId);ScrollView s=new ScrollView(this);s.setBackgroundColor(Ui.BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));s.addView(root);root.addView(Ui.brandHeader(this,"ELEKTORYK • Версії","Фіксуй V1/V2/V3 перед змінами клієнта або монтажними відхиленнями.","REVISION",true));
        reason=new ChoiceStrip(this);reason.setItems(new String[]{"BASELINE","CLIENT_CHANGE","SITE_CHANGE","DESIGN_CHANGE","AS_BUILT"},new String[]{"Базова","Зміна клієнта","Зміна на об'єкті","Зміна проєкту","As-built"},"CLIENT_CHANGE");root.addView(reason,Ui.lpMatchWrap(this,10));changeNote=new EditText(this);changeNote.setHint("Що саме змінилось: було → стало, причина, хто погодив");changeNote.setMinLines(2);changeNote.setTextColor(Ui.TEXT);changeNote.setHintTextColor(Ui.MUTED);changeNote.setBackgroundColor(Ui.CARD_SOFT);changeNote.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10));root.addView(changeNote,Ui.lpMatchWrap(this,8));
        RevisionSummary last=history.revisions.isEmpty()?null:history.revisions.get(history.revisions.size()-1);LinearLayout delta=Ui.card(this);delta.addView(Ui.title(this,"Поточна дельта від останньої ревізії",18));if(last==null)delta.addView(Ui.muted(this,"Ще немає V1. Збережи базову ревізію перед першими змінами."));else{ElectricalProject p=new FileProjectRepository(this).get(projectId);FloorPlan f=new FloorPlanRepository(this).load(projectId);FieldWorkPlan field=new FieldWorkRepository(this).loadPlan(projectId);double area=p==null?0:p.approximateAreaM2;int points=f==null?0:f.points.size();double estimate=cp.total;delta.addView(Ui.muted(this,String.format(Locale.US,"Точки %+d • площа %+.1f м² • групи %+d • кошторис %+.0f грн",points-last.pointCount,area-last.areaM2,field.circuits.size()-last.circuitCount,estimate-last.estimateTotal)));}root.addView(delta,Ui.lpMatchWrap(this,10));
        Button create=Ui.button(this,"Зберегти нову ревізію + FULL snapshot");create.setOnClickListener(v->create(cp.total));root.addView(create,Ui.lpMatchWrap(this,12));status=Ui.muted(this,"");root.addView(status,Ui.lpMatchWrap(this,6));
        root.addView(Ui.title(this,"Історія",20),Ui.lpMatchWrap(this,16));DateFormat df=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT);for(RevisionSummary r:history.revisions){LinearLayout c=Ui.card(this);c.addView(Ui.chip(this,"V"+r.versionNo));c.addView(Ui.title(this,r.reason.name(),17),Ui.lpMatchWrap(this,4));c.addView(Ui.muted(this,df.format(new Date(r.createdAt))));c.addView(Ui.muted(this,String.format(Locale.US,"%.1f м² • %d точок • %d груп • %.1f м кабелю • %.0f грн",r.areaM2,r.pointCount,r.circuitCount,r.cablePurchaseM,r.estimateTotal)),Ui.lpMatchWrap(this,4));if(r.note!=null&&!r.note.isEmpty())c.addView(Ui.muted(this,r.note),Ui.lpMatchWrap(this,4));root.addView(c,Ui.lpMatchWrap(this,7));}root.addView(Ui.bottomNav(this,projectId,"HOME"),Ui.lpMatchWrap(this,18));setContentView(s);}
    private void create(double total){if(busy)return;busy=true;status.setText("Створюю FULL snapshot і ревізію…");RevisionSummary.Reason rr;try{rr=RevisionSummary.Reason.valueOf(reason.getSelectedKey());}catch(Exception e){rr=RevisionSummary.Reason.CLIENT_CHANGE;}final RevisionSummary.Reason fin=rr;final String note=changeNote==null?"":changeNote.getText().toString().trim();StableTaskRunner.run(this,"REVISION",()->repo.create(projectId,fin,total,note),r->{busy=false;status.setText("Готово: V"+r.versionNo);buildUi();},e->{busy=false;status.setText("Не вдалося: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));});}
}
