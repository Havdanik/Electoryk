package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import com.elektoryk.scan.model.*;
import com.elektoryk.scan.reconstruction.ProjectWorkflowEngine;
import com.elektoryk.scan.storage.*;

public class WorkflowActivity extends Activity {
    private String projectId;

    @Override protected void onCreate(Bundle b){super.onCreate(b);projectId=getIntent().getStringExtra("projectId");buildUi();}

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));scroll.addView(root);

        root.addView(Ui.brandHeader(this,"ELEKTORYK • Етапи об'єкта","Один маршрут від обміру до VIP здачі. Кожен етап показує, що готово і що блокує далі.","WORKFLOW",true));

        ElectricalProject project=new FileProjectRepository(this).get(projectId);
        SceneSnapshot scene=new SceneRepository(this).loadScene(projectId);
        FloorPlan floor=new FloorPlanRepository(this).load(projectId);
        FieldWorkRepository fw=new FieldWorkRepository(this);
        CableCutList cuts=fw.loadCuts(projectId);
        FieldWorkPlan field=fw.loadPlan(projectId);
        CommercialPlan commercial=new CommercialRepository(this).load(projectId);
        EvidenceIndex evidence=new EvidenceRepository(this).load(projectId);
        ClientApproval approval=new ApprovalRepository(this).load(projectId);
        ProjectWorkflowSnapshot flow=new ProjectWorkflowEngine().build(project,scene,floor,cuts,field,commercial,evidence,approval);

        LinearLayout summary=Ui.card(this);
        summary.addView(Ui.title(this,flow.overallProgress+"% готовності",24));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(flow.overallProgress);summary.addView(bar,Ui.lpMatchWrap(this,8));
        summary.addView(Ui.muted(this,"Наступний етап: "+flow.nextStage.label+" — "+flow.nextStage.goal),Ui.lpMatchWrap(this,7));
        root.addView(summary,Ui.lpMatchWrap(this,10));

        int n=1;
        for(WorkflowStageState s:flow.stages){
            LinearLayout card=Ui.card(this);
            card.addView(Ui.chip(this,statusLabel(s.status)));
            card.addView(Ui.title(this,n+". "+s.stage.label,19),Ui.lpMatchWrap(this,5));
            card.addView(Ui.muted(this,s.stage.goal));
            ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(100);pb.setProgress(s.progress);card.addView(pb,Ui.lpMatchWrap(this,8));
            TextView pct=Ui.muted(this,s.progress+"%");card.addView(pct,Ui.lpMatchWrap(this,3));
            for(String x:s.blockers)card.addView(Ui.muted(this,"⛔ "+x),Ui.lpMatchWrap(this,5));
            for(String x:s.warnings)card.addView(Ui.muted(this,"• "+x),Ui.lpMatchWrap(this,4));
            Button action=(s.status==WorkflowStageState.Status.BLOCKED)?Ui.secondaryButton(this,"Відкрити попередній етап"):Ui.button(this,s.primaryAction.isEmpty()?"Відкрити":s.primaryAction);
            final ProjectWorkflowStage stage=(s.status==WorkflowStageState.Status.BLOCKED)?previousStage(s.stage):s.stage;action.setOnClickListener(v->openStage(stage));card.addView(action,Ui.lpMatchWrap(this,10));
            root.addView(card,Ui.lpMatchWrap(this,8));n++;
        }

        Button audit=Ui.button(this,"Самоаудит: що ще не вистачає");audit.setOnClickListener(v->open(GapAuditActivity.class));root.addView(audit,Ui.lpMatchWrap(this,14));
        Button diagnostics=Ui.secondaryButton(this,"Стабільність / відновлення / діагностика");
        diagnostics.setOnClickListener(v->open(DiagnosticsActivity.class));root.addView(diagnostics,Ui.lpMatchWrap(this,8));
        root.addView(Ui.bottomNav(this,projectId,"HOME"),Ui.lpMatchWrap(this,18));
        setContentView(scroll);
    }

    private ProjectWorkflowStage previousStage(ProjectWorkflowStage stage){
        ProjectWorkflowStage[] all=ProjectWorkflowStage.values();int i=stage.ordinal();return i<=0?ProjectWorkflowStage.SETUP:all[i-1];
    }

    private String statusLabel(WorkflowStageState.Status s){
        switch(s){case COMPLETE:return "ГОТОВО";case READY:return "МОЖНА ПОЧИНАТИ";case IN_PROGRESS:return "В РОБОТІ";default:return "ЗАБЛОКОВАНО";}
    }

    private void openStage(ProjectWorkflowStage s){
        switch(s){
            case SETUP:open(ProjectActivity.class);break;
            case SCAN:open(ArCoreScanActivity.class);break;
            case PLAN:open(ApartmentPlanActivity.class);break;
            case ELECTRICAL:open(ApartmentEditorActivity.class);break;
            case ROUTING:open(RoutingActivity.class);break;
            case MATERIALS:open(FieldWorkActivity.class);break;
            case COMMERCIAL:open(EstimateActivity.class);break;
            case INSTALLATION:open(FieldWorkActivity.class);break;
            case QUALITY:open(QualityHubActivity.class);break;
            case AS_BUILT:open(EvidenceActivity.class);break;
            case HANDOVER:open(HandoverActivity.class);break;
        }
    }
    private void open(Class<?> c){Intent i=new Intent(this,c);i.putExtra("projectId",projectId);startActivity(i);}
}
