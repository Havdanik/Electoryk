package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.elektoryk.scan.model.*;
import com.elektoryk.scan.reconstruction.ProjectGapAuditEngine;
import com.elektoryk.scan.storage.*;

public class GapAuditActivity extends Activity {
    private String projectId;
    @Override protected void onCreate(Bundle b){super.onCreate(b);projectId=getIntent().getStringExtra("projectId");buildUi();}
    private void buildUi(){
        ElectricalProject p=new FileProjectRepository(this).get(projectId);SceneSnapshot s=new SceneRepository(this).loadScene(projectId);FloorPlan f=new FloorPlanRepository(this).load(projectId);FieldWorkRepository fw=new FieldWorkRepository(this);CableCutList cuts=fw.loadCuts(projectId);FieldWorkPlan field=fw.loadPlan(projectId);CommercialPlan commercial=new CommercialRepository(this).load(projectId);EvidenceIndex evidence=new EvidenceRepository(this).load(projectId);RevisionHistory revisions=new RevisionRepository(this).load(projectId);ClientApproval approval=new ApprovalRepository(this).load(projectId);
        ProjectGapAuditReport report=new ProjectGapAuditEngine().audit(p,s,f,cuts,field,commercial,evidence,revisions,approval);
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(Ui.BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));sc.addView(root);
        root.addView(Ui.brandHeader(this,"ELEKTORYK • Самоаудит","Що ще не вистачає в цьому конкретному об'єкті — по всьому циклу.","AUDIT",true));
        LinearLayout sum=Ui.card(this);sum.addView(Ui.title(this,report.score+"/100",26));sum.addView(Ui.muted(this,"Критично: "+report.count(GapAuditItem.Severity.CRITICAL)+" • доробити: "+report.count(GapAuditItem.Severity.WARNING)+" • optional: "+report.count(GapAuditItem.Severity.OPTIONAL)));root.addView(sum,Ui.lpMatchWrap(this,10));
        for(GapAuditItem i:report.items){LinearLayout c=Ui.card(this);c.addView(Ui.chip(this,label(i.severity)+" • "+i.area));c.addView(Ui.title(this,i.title,17),Ui.lpMatchWrap(this,5));c.addView(Ui.muted(this,i.detail));c.addView(Ui.muted(this,"→ "+i.action),Ui.lpMatchWrap(this,5));Button b=buttonFor(i);if(b!=null)c.addView(b,Ui.lpMatchWrap(this,8));root.addView(c,Ui.lpMatchWrap(this,7));}
        root.addView(Ui.bottomNav(this,projectId,"HOME"),Ui.lpMatchWrap(this,18));setContentView(sc);
    }
    private String label(GapAuditItem.Severity s){switch(s){case CRITICAL:return "КРИТИЧНО";case WARNING:return "ДОРОБИТИ";case OPTIONAL:return "БАЖАНО";default:return "OK";}}
    private Button buttonFor(GapAuditItem i){Class<?> c=null;if("Скан".equals(i.area))c=ArCoreScanActivity.class;else if("План".equals(i.area)||"Точність".equals(i.area))c=ApartmentPlanActivity.class;else if("Електрика".equals(i.area)||"Щит".equals(i.area))c=ApartmentEditorActivity.class;else if("Кабель".equals(i.area)||"Маркування".equals(i.area)||"Монтаж".equals(i.area))c=FieldWorkActivity.class;else if("Commissioning".equals(i.area))c=CommissioningActivity.class;else if("Кошторис".equals(i.area)||"Закупівля".equals(i.area))c=EstimateActivity.class;else if("As-built".equals(i.area))c=EvidenceActivity.class;else if("Версії".equals(i.area))c=RevisionActivity.class;else if("Клієнт".equals(i.area))c=ClientApprovalActivity.class;if(c==null)return null;Button b=Ui.secondaryButton(this,"Відкрити");final Class<?> target=c;b.setOnClickListener(v->{Intent x=new Intent(this,target);x.putExtra("projectId",projectId);startActivity(x);});return b;}
}
