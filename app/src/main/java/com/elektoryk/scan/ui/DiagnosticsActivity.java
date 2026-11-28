package com.elektoryk.scan.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.elektoryk.scan.model.*;
import com.elektoryk.scan.reconstruction.ProjectIntegrityEngine;
import com.elektoryk.scan.storage.*;

public class DiagnosticsActivity extends Activity {
    private String projectId;
    private LinearLayout root;

    @Override protected void onCreate(Bundle b){super.onCreate(b);projectId=getIntent().getStringExtra("projectId");render();}

    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));scroll.addView(root);
        root.addView(Ui.brandHeader(this,"ELEKTORYK • Стабільність","Перевірка файлів, посилань між модулями, backup-відновлення і журнал помилок.","DIAGNOSTICS",true));

        FileProjectRepository projects=new FileProjectRepository(this);SceneRepository scenes=new SceneRepository(this);FloorPlanRepository floors=new FloorPlanRepository(this);FieldWorkRepository fieldRepo=new FieldWorkRepository(this);
        ElectricalProject project=projects.get(projectId);SceneSnapshot scene=scenes.loadScene(projectId);FloorPlan floor=floors.load(projectId);CableCutList cuts=fieldRepo.loadCuts(projectId);FieldWorkPlan field=fieldRepo.loadPlan(projectId);
        ProjectIntegrityReport report=new ProjectIntegrityEngine().check(project,scene,floor,cuts,field);

        LinearLayout health=Ui.card(this);health.addView(Ui.title(this,"Цілісність: "+report.score+"/100",22));health.addView(Ui.muted(this,"Статус: "+report.severity.name()));
        if(report.issues.isEmpty())health.addView(Ui.muted(this,"Критичних суперечностей у даних не знайдено."),Ui.lpMatchWrap(this,6));
        for(String x:report.issues)health.addView(Ui.muted(this,"• "+x),Ui.lpMatchWrap(this,5));root.addView(health,Ui.lpMatchWrap(this,10));

        LinearLayout recovery=Ui.card(this);recovery.addView(Ui.title(this,"Backup / Recovery",19));recovery.addView(Ui.muted(this,"Кожен важливий JSON тепер зберігається атомарно і тримає попередню .bak версію."));
        Button restoreProject=Ui.secondaryButton(this,"Відновити backup параметрів об'єкта");restoreProject.setOnClickListener(v->confirm("Відновити project.json з .bak?",()->projects.restoreBackup(projectId)));recovery.addView(restoreProject,Ui.lpMatchWrap(this,7));
        Button restoreScene=Ui.secondaryButton(this,"Відновити backup сцени");restoreScene.setOnClickListener(v->confirm("Відновити scene.json з .bak?",()->scenes.restoreSceneBackup(projectId)));recovery.addView(restoreScene,Ui.lpMatchWrap(this,7));
        Button restoreFloor=Ui.secondaryButton(this,"Відновити backup 2D/електроплану");restoreFloor.setOnClickListener(v->confirm("Відновити floorplan з .bak?",()->floors.restoreBackup(projectId)));recovery.addView(restoreFloor,Ui.lpMatchWrap(this,7));
        Button restoreCuts=Ui.secondaryButton(this,"Відновити backup CUT-листа");restoreCuts.setOnClickListener(v->confirm("Відновити cuts.json з .bak?",()->fieldRepo.restoreCutsBackup(projectId)));recovery.addView(restoreCuts,Ui.lpMatchWrap(this,7));
        root.addView(recovery,Ui.lpMatchWrap(this,10));

        ProjectRecoveryManager recoveryManager=new ProjectRecoveryManager(this);
        LinearLayout snapshots=Ui.card(this);snapshots.addView(Ui.title(this,"Повні точки відновлення",19));
        snapshots.addView(Ui.muted(this,"FULL snapshot зберігає план, електрику, field-work і 3D point cloud. Відновлення робиться як окрема копія, щоб не знищити поточну версію."));
        Button makeSnapshot=Ui.button(this,"Створити FULL snapshot зараз");
        makeSnapshot.setOnClickListener(v->{try{java.io.File f=recoveryManager.createSnapshot(projectId,"manual");new AlertDialog.Builder(this).setMessage("Snapshot створено: "+f.getName()).setPositiveButton("OK",(d,w)->render()).show();}catch(Exception e){new AlertDialog.Builder(this).setMessage("Не вдалося створити snapshot: "+e.getMessage()).setPositiveButton("OK",null).show();}});
        snapshots.addView(makeSnapshot,Ui.lpMatchWrap(this,8));
        java.util.List<java.io.File> snapshotFiles=recoveryManager.list(projectId);
        int shown=0;
        for(java.io.File f:snapshotFiles){
            if(shown++>=5)break;
            LinearLayout row=Ui.softCard(this);
            row.addView(Ui.muted(this,f.getName()+" • "+Math.max(1,f.length()/1024)+" KB"));
            Button restoreCopy=Ui.secondaryButton(this,"Відновити як копію");
            restoreCopy.setOnClickListener(v->{try{ProjectPackageManager.ImportResult rr=recoveryManager.restoreAsCopy(f);new AlertDialog.Builder(this).setMessage(rr.message+" ID: "+rr.projectId).setPositiveButton("OK",null).show();}catch(Exception e){new AlertDialog.Builder(this).setMessage("Recovery не вдався: "+e.getMessage()).setPositiveButton("OK",null).show();}});
            row.addView(restoreCopy,Ui.lpMatchWrap(this,6));snapshots.addView(row,Ui.lpMatchWrap(this,6));
        }
        root.addView(snapshots,Ui.lpMatchWrap(this,10));

        LinearLayout checkpoint=Ui.card(this);checkpoint.addView(Ui.title(this,"Scan Recovery",19));
        checkpoint.addView(Ui.muted(this,scenes.hasScanCheckpoint(projectId)?"Є незавершений scan-checkpoint. Його можна повторно обробити з екрана Smart Scan.":"Незавершеного scan-checkpoint немає."));root.addView(checkpoint,Ui.lpMatchWrap(this,10));

        StabilityReporter reporter=new StabilityReporter(this);String log=reporter.readTail(12000);
        LinearLayout logs=Ui.card(this);logs.addView(Ui.title(this,"Останні події / помилки",19));TextView text=Ui.muted(this,log.isEmpty()?"Журнал порожній.":log);text.setTextSize(11);logs.addView(text,Ui.lpMatchWrap(this,6));
        Button clear=Ui.secondaryButton(this,"Очистити журнал");clear.setOnClickListener(v->{reporter.clear();render();});logs.addView(clear,Ui.lpMatchWrap(this,8));root.addView(logs,Ui.lpMatchWrap(this,10));

        setContentView(scroll);
    }

    private void confirm(String message,Action action){new AlertDialog.Builder(this).setTitle("Recovery").setMessage(message+" Поточний файл буде збережений як corrupt-копія, якщо це можливо.").setNegativeButton("Ні",null).setPositiveButton("Відновити",(d,w)->{boolean ok=false;try{ok=action.run();}catch(Exception ignored){}new AlertDialog.Builder(this).setMessage(ok?"Відновлено.":"Backup недоступний або відновлення не вдалося.").setPositiveButton("OK",(x,y)->render()).show();}).show();}
    private interface Action{boolean run();}
}
