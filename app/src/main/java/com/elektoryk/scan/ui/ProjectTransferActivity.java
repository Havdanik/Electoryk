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
import android.widget.Toast;
import com.elektoryk.scan.model.ElectricalProject;
import com.elektoryk.scan.storage.ExportFileProvider;
import com.elektoryk.scan.storage.FileProjectRepository;
import com.elektoryk.scan.storage.ProjectPackageManager;
import java.io.File;
import java.io.InputStream;

public class ProjectTransferActivity extends Activity {
    private static final int PICK=41;
    private String projectId;
    private TextView status;
    private Button light,full,imp;
    private boolean busy;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        projectId=getIntent().getStringExtra("projectId");
        Uri incoming=getIntent().getData();
        if(incoming==null&&Intent.ACTION_SEND.equals(getIntent().getAction())){
            try{incoming=getIntent().getParcelableExtra(Intent.EXTRA_STREAM);}catch(Exception ignored){}
        }
        buildUi();
        if(incoming!=null&&(Intent.ACTION_VIEW.equals(getIntent().getAction())||Intent.ACTION_SEND.equals(getIntent().getAction())))confirmIncoming(incoming);
    }

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,28));scroll.addView(root);
        root.addView(Ui.brandHeader(this,"ELEKTORYK • Перенос","Телефон ↔ планшет ↔ другий телефон. Пакет перевіряється checksum-ами перед імпортом.","TRANSFER",true));
        root.addView(Ui.screenIntro(this,"СИНХРОНІЗАЦІЯ","Передати / імпортувати об'єкт","LIGHT — план, точки, CUT-лист, захист, кошторис, ревізії та погодження. FULL — плюс 3D point cloud і фото/as-built файли. Важкі операції виконуються у фоні, щоб UI не зависав."),Ui.lpMatchWrap(this,10));

        if(projectId!=null){ElectricalProject p=new FileProjectRepository(this).get(projectId);if(p!=null){LinearLayout card=Ui.card(this);card.addView(Ui.title(this,p.displayName(),19));card.addView(Ui.muted(this,"При імпорті існуючого об'єкта перед оновленням створюється FULL recovery-копія."));root.addView(card,Ui.lpMatchWrap(this,12));}}

        if(projectId!=null){
            light=Ui.button(this,"Поділитися • LIGHT");light.setOnClickListener(v->share(false));root.addView(light,Ui.lpMatchWrap(this,14));
            full=Ui.secondaryButton(this,"Поділитися • FULL 3D");full.setOnClickListener(v->share(true));root.addView(full,Ui.lpMatchWrap(this,8));
        }
        imp=Ui.secondaryButton(this,"Імпортувати .elektoryk");imp.setOnClickListener(v->pick());root.addView(imp,Ui.lpMatchWrap(this,projectId!=null?8:14));
        status=Ui.muted(this,projectId!=null?"Готово до переносу.":"Вибери отриманий .elektoryk-файл. Перед записом він буде повністю перевірений.");root.addView(status,Ui.lpMatchWrap(this,12));
        if(projectId!=null)root.addView(Ui.bottomNav(this,projectId,"MORE"),Ui.lpMatchWrap(this,18));
        setContentView(scroll);
    }

    private void setBusy(boolean value,String message){
        busy=value;if(status!=null)status.setText(message);
        if(light!=null)light.setEnabled(!value);if(full!=null)full.setEnabled(!value);if(imp!=null)imp.setEnabled(!value);
    }

    private void share(boolean fullPackage){
        if(projectId==null||busy)return;
        setBusy(true,fullPackage?"Готую FULL пакет і checksum-и…":"Готую LIGHT пакет і checksum-и…");
        StableTaskRunner.run(this,"EXPORT",()->new ProjectPackageManager().createPackage(this,projectId,fullPackage),f->{
            setBusy(false,"Пакет готовий: "+f.getName()+" • "+Math.max(1,f.length()/1024)+" KB");
            Uri uri=new Uri.Builder().scheme("content").authority(ExportFileProvider.AUTHORITY).appendPath(f.getName()).build();
            Intent s=new Intent(Intent.ACTION_SEND);s.setType(ProjectPackageManager.MIME);s.putExtra(Intent.EXTRA_STREAM,uri);s.setClipData(ClipData.newRawUri("ELEKTORYK project",uri));s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(s,fullPackage?"Передати повний ELEKTORYK-проєкт":"Передати ELEKTORYK-проєкт"));
        },e->setBusy(false,"Не вдалося експортувати: "+safeMessage(e)));
    }

    private void pick(){if(busy)return;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,PICK);}

    @Override protected void onActivityResult(int r,int c,Intent data){super.onActivityResult(r,c,data);if(r==PICK&&c==RESULT_OK&&data!=null&&data.getData()!=null)importUri(data.getData());}


    private void confirmIncoming(Uri uri){
        if(uri==null)return;
        if(status!=null)status.setText("Отримано зовнішній .elektoryk-пакет. Імпорт почнеться тільки після підтвердження.");
        new android.app.AlertDialog.Builder(this)
                .setTitle("Імпортувати ELEKTORYK-проєкт?")
                .setMessage("Файл буде перевірено checksum-ами. Якщо він оновлює існуючий об’єкт, перед записом програма створить FULL recovery-копію.")
                .setNegativeButton("Скасувати",(d,w)->{if(projectId==null)finish();else if(status!=null)status.setText("Імпорт скасовано.");})
                .setPositiveButton("Перевірити й імпортувати",(d,w)->importUri(uri))
                .show();
    }

    private void importUri(Uri uri){
        if(uri==null||busy)return;
        setBusy(true,"Перевіряю пакет і checksum-и…");
        StableTaskRunner.run(this,"IMPORT",()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IllegalStateException("Файл недоступний");
                return new ProjectPackageManager().importPackage(this,in);
            }
        },r->{
            setBusy(false,r.message);
            Toast.makeText(this,r.message,Toast.LENGTH_LONG).show();
            Intent p=new Intent(this,WorkflowActivity.class);p.putExtra("projectId",r.projectId);startActivity(p);finish();
        },e->{
            setBusy(false,"Помилка імпорту: "+safeMessage(e));
            Toast.makeText(this,"Імпорт скасовано — поточні дані не повинні бути частково прийняті без перевірки.",Toast.LENGTH_LONG).show();
        });
    }

    @Override public void onBackPressed(){
        if(busy){Toast.makeText(this,"Операція переносу ще виконується. Завершення потрібне для цілісності даних.",Toast.LENGTH_SHORT).show();return;}
        super.onBackPressed();
    }

    private String safeMessage(Throwable e){String m=e==null?"невідома помилка":e.getMessage();return m==null||m.trim().isEmpty()?(e==null?"невідома помилка":e.getClass().getSimpleName()):m;}
}
