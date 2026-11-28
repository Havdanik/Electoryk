package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.elektoryk.scan.model.*;
import com.elektoryk.scan.storage.*;
import java.io.File;

public class HandoverActivity extends Activity {
    private String projectId;
    private ElectricalProject project;
    private CableCutList cuts;
    private FieldWorkPlan field;
    private SceneSnapshot scene;
    private CommercialPlan commercial;
    private EvidenceIndex evidence;
    private RevisionHistory revisions;
    private ClientApproval approval;
    private TextView status;
    private boolean busy;

    @Override protected void onCreate(Bundle b){super.onCreate(b);projectId=getIntent().getStringExtra("projectId");load();buildUi();}

    private void load(){
        project=new FileProjectRepository(this).get(projectId);
        FieldWorkRepository r=new FieldWorkRepository(this);cuts=r.loadCuts(projectId);field=r.loadPlan(projectId);scene=new SceneRepository(this).loadScene(projectId);
        commercial=new CommercialRepository(this).load(projectId);evidence=new EvidenceRepository(this).load(projectId);revisions=new RevisionRepository(this).load(projectId);approval=new ApprovalRepository(this).load(projectId);
        if(cuts==null){cuts=new CableCutList();cuts.projectId=projectId;}if(field==null){field=new FieldWorkPlan();field.projectId=projectId;}
    }

    private void buildUi(){
        ScrollView s=new ScrollView(this);s.setBackgroundColor(Ui.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));s.addView(root);
        root.addView(Ui.brandHeader(this,"ELEKTORYK • Handover","Фінальна здача об'єкта як дорогого цифрового продукту.","VIP",true));
        root.addView(Ui.screenIntro(this,"ЗДАЧА","Паспорт готового об'єкта","Клієнт отримує презентацію й технічний паспорт, а ти — повний as-built snapshot для гарантії, сервісу і майбутніх змін."),Ui.lpMatchWrap(this,10));

        int verified=0,installed=0,printed=0,testPass=0,testFail=0;
        for(CircuitProtectionPlan c:field.circuits){if(c.verifiedByElectrician)verified++;if("PASS".equals(c.testStatus))testPass++;if("FAIL".equals(c.testStatus))testFail++;}
        for(CableCutPiece p:cuts.pieces){if(p.installed)installed++;if(p.labelsPrinted)printed++;}
        int checks=0,total=9;
        if(!field.circuits.isEmpty()&&verified==field.circuits.size())checks++;
        if(!cuts.pieces.isEmpty()&&installed==cuts.pieces.size())checks++;
        if(!cuts.pieces.isEmpty()&&printed==cuts.pieces.size())checks++;
        if(scene!=null&&scene.precisionReport!=null&&scene.precisionReport.score>=72)checks++;
        if(!field.circuits.isEmpty()&&testPass==field.circuits.size()&&testFail==0)checks++;
        boolean commercialReady=false;if(commercial!=null&&!commercial.lines.isEmpty()){int missing=0;for(CommercialLine l:commercial.lines)if(l.includeInEstimate&&l.unitCost<=0)missing++;commercialReady=missing==0;}if(commercialReady)checks++;
        boolean asBuiltReady=false;if(evidence!=null){boolean hidden=false,panelPhoto=false,finishedPhoto=false;for(EvidenceItem e:evidence.items){if(e.category==EvidenceItem.Category.HIDDEN_WORK)hidden=true;if(e.category==EvidenceItem.Category.PANEL)panelPhoto=true;if(e.category==EvidenceItem.Category.FINISHED)finishedPhoto=true;}asBuiltReady=hidden&&panelPhoto&&finishedPhoto;}if(asBuiltReady)checks++;
        if(revisions!=null&&!revisions.revisions.isEmpty())checks++;
        if(approval!=null&&approval.planApproved&&approval.estimateApproved)checks++;

        LinearLayout score=Ui.card(this);score.addView(Ui.chip(this,checks==total?"READY TO HANDOVER":"CHECK BEFORE HANDOVER"));
        score.addView(Ui.title(this,checks+" / "+total+" блоків готові",22),Ui.lpMatchWrap(this,6));
        score.addView(Ui.muted(this,"Групи підтверджені: "+verified+"/"+field.circuits.size()+" • монтаж: "+installed+"/"+cuts.pieces.size()+" • етикетки: "+printed+"/"+cuts.pieces.size()),Ui.lpMatchWrap(this,4));
        score.addView(Ui.muted(this,"Commissioning: OK "+testPass+" / "+field.circuits.size()+(testFail>0?" • проблема "+testFail:"")),Ui.lpMatchWrap(this,4));
        score.addView(Ui.muted(this,"Кошторис: "+(commercialReady?"готовий":"є незаповнені ціни")+" • as-built: "+(asBuiltReady?"готовий":"неповний")+" • ревізії: "+(revisions==null?0:revisions.revisions.size())),Ui.lpMatchWrap(this,4));
        score.addView(Ui.muted(this,"Погодження: план "+(approval!=null&&approval.planApproved?"✓":"—")+" • кошторис "+(approval!=null&&approval.estimateApproved?"✓":"—")+" • приймання "+(approval!=null&&approval.handoverAccepted?"✓":"—")),Ui.lpMatchWrap(this,4));
        if(scene!=null&&scene.precisionReport!=null)score.addView(Ui.muted(this,"Точність: "+scene.precisionReport.score+"/100 • очікувана похибка ≈ "+String.format(java.util.Locale.US,"%.1f см",scene.precisionReport.estimatedPlanErrorCm)),Ui.lpMatchWrap(this,4));
        root.addView(score,Ui.lpMatchWrap(this,12));

        Button quality=Ui.secondaryButton(this,"✓ Quality Hub / що ще не готово");quality.setOnClickListener(v->open(QualityHubActivity.class));root.addView(quality,Ui.lpMatchWrap(this,12));
        Button audit=Ui.secondaryButton(this,"Самоаудит всього об'єкта");audit.setOnClickListener(v->open(GapAuditActivity.class));root.addView(audit,Ui.lpMatchWrap(this,8));
        Button client=Ui.button(this,"◈ Відкрити клієнтську презентацію");client.setOnClickListener(v->open(ClientPresentationActivity.class));root.addView(client,Ui.lpMatchWrap(this,8));
        Button estimate=Ui.secondaryButton(this,"Кошторис / закупівля");estimate.setOnClickListener(v->open(EstimateActivity.class));root.addView(estimate,Ui.lpMatchWrap(this,8));
        Button evidence=Ui.secondaryButton(this,"As-built фото / приховані роботи");evidence.setOnClickListener(v->open(EvidenceActivity.class));root.addView(evidence,Ui.lpMatchWrap(this,8));
        Button approval=Ui.secondaryButton(this,"Погодження / підпис клієнта");approval.setOnClickListener(v->open(ClientApprovalActivity.class));root.addView(approval,Ui.lpMatchWrap(this,8));
        Button html=Ui.secondaryButton(this,"Створити VIP паспорт HTML + snapshot");html.setOnClickListener(v->shareReport());root.addView(html,Ui.lpMatchWrap(this,8));
        Button snapshot=Ui.secondaryButton(this,"Створити FULL as-built snapshot");snapshot.setOnClickListener(v->createSnapshot());root.addView(snapshot,Ui.lpMatchWrap(this,8));
        Button backup=Ui.secondaryButton(this,"Передати FULL цифровий об'єкт");backup.setOnClickListener(v->open(ProjectTransferActivity.class));root.addView(backup,Ui.lpMatchWrap(this,8));
        Button fieldWork=Ui.secondaryButton(this,"← Монтажна кабельна карта");fieldWork.setOnClickListener(v->open(FieldWorkActivity.class));root.addView(fieldWork,Ui.lpMatchWrap(this,8));
        status=Ui.muted(this,checks==total?"Об'єкт виглядає готовим до цифрової здачі. Перед фактичною передачею все одно зроби фінальний професійний огляд.":"VIP-паспорт можна згенерувати і раніше, але незавершені блоки будуть видимі як незавершені.");root.addView(status,Ui.lpMatchWrap(this,12));
        root.addView(Ui.bottomNav(this,projectId,"CLIENT"),Ui.lpMatchWrap(this,18));setContentView(s);
    }

    private void createSnapshot(){
        if(busy)return;busy=true;status.setText("Створюю FULL as-built snapshot…");
        StableTaskRunner.run(this,"HANDOVER_SNAPSHOT",()->new ProjectRecoveryManager(this).createSnapshot(projectId,"handover"),f->{busy=false;status.setText("Snapshot готовий: "+f.getName());},e->{busy=false;status.setText("Snapshot не створено: "+safe(e));});
    }

    private void shareReport(){
        if(busy)return;busy=true;status.setText("Створюю recovery snapshot і VIP паспорт…");
        StableTaskRunner.run(this,"HANDOVER_REPORT",()->{
            try{new ProjectRecoveryManager(this).createSnapshot(projectId,"before_handover_report");}catch(Exception ignored){}
            return new HandoverReportGenerator().generate(this,projectId);
        },f->{
            busy=false;Uri uri=new Uri.Builder().scheme("content").authority(ExportFileProvider.AUTHORITY).appendPath(f.getName()).build();Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/html");i.putExtra(Intent.EXTRA_STREAM,uri);i.setClipData(ClipData.newRawUri("ELEKTORYK export",uri));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Передати паспорт ELEKTORYK"));status.setText("VIP паспорт готовий: "+f.getName());
        },e->{busy=false;status.setText("Не вдалося створити паспорт: "+safe(e));});
    }

    private String safe(Throwable e){return e==null?"невідома помилка":(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
    private void open(Class<?> c){Intent i=new Intent(this,c);i.putExtra("projectId",projectId);startActivity(i);}
}
