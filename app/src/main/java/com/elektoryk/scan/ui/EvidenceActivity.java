package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.elektoryk.scan.model.EvidenceIndex;
import com.elektoryk.scan.model.EvidenceItem;
import com.elektoryk.scan.model.CableCutList;
import com.elektoryk.scan.model.CableCutPiece;
import com.elektoryk.scan.storage.EvidenceRepository;
import com.elektoryk.scan.storage.FieldWorkRepository;

import java.io.InputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

public class EvidenceActivity extends Activity {
    private static final int PICK=701;
    private String projectId;private EvidenceRepository repo;private EvidenceIndex index;private ChoiceStrip category,cut;private NumberStepper circuit;private TextView status;private String statusMessage="Можна вибрати кілька фото. За потреби прив'яжи їх до групи або конкретного CUT-відрізка.";
    @Override protected void onCreate(Bundle b){super.onCreate(b);projectId=getIntent().getStringExtra("projectId");repo=new EvidenceRepository(this);buildUi();}
    private void buildUi(){index=repo.load(projectId);ScrollView s=new ScrollView(this);s.setBackgroundColor(Ui.BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));s.addView(root);root.addView(Ui.brandHeader(this,"ELEKTORYK • As-built","Фото прихованих робіт і доказ того, що реально змонтовано.","AS-BUILT",true));root.addView(Ui.screenIntro(this,"ФОТО","Не покладайся на пам'ять","Додай фото перед закриттям штроб, щита, тестів і готового результату. У FULL .elektoryk вони переносяться разом з проєктом."),Ui.lpMatchWrap(this,10));
        category=new ChoiceStrip(this);category.setItems(new String[]{"BEFORE","HIDDEN_WORK","PANEL","TEST","FINISHED","OTHER"},new String[]{"До робіт","Приховані роботи","Щит","Тести","Готово","Інше"},"HIDDEN_WORK");root.addView(category,Ui.lpMatchWrap(this,10));
        circuit=new NumberStepper(this,"група");circuit.configure(0,99,1,0);circuit.setValue(0);root.addView(circuit,Ui.lpMatchWrap(this,8));
        CableCutList cuts=new FieldWorkRepository(this).loadCuts(projectId);String[] ck=new String[cuts.pieces.size()+1],cl=new String[cuts.pieces.size()+1];ck[0]="";cl[0]="Без прив'язки до CUT";for(int i=0;i<cuts.pieces.size();i++){CableCutPiece cp=cuts.pieces.get(i);ck[i+1]=cp.stableKey;cl[i+1]=cp.code+" • "+cp.cableKey+" • "+cp.fromLabel+" → "+cp.toLabel;}cut=new ChoiceStrip(this);cut.setItems(ck,cl,"");root.addView(cut,Ui.lpMatchWrap(this,8));
        Button add=Ui.button(this,"+ Додати фото / файл");add.setOnClickListener(v->pick());root.addView(add,Ui.lpMatchWrap(this,10));status=Ui.muted(this,statusMessage);root.addView(status,Ui.lpMatchWrap(this,6));
        root.addView(Ui.title(this,"Докази на об'єкті: "+index.items.size(),20),Ui.lpMatchWrap(this,16));DateFormat df=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT);for(EvidenceItem e:index.items){LinearLayout c=Ui.card(this);c.addView(Ui.chip(this,e.category.name()));c.addView(Ui.title(this,e.fileName,16),Ui.lpMatchWrap(this,4));c.addView(Ui.muted(this,df.format(new Date(e.createdAt))+(e.circuitNo>0?" • група "+e.circuitNo:"")+(e.cutKey!=null&&!e.cutKey.isEmpty()?" • CUT "+e.cutKey:"")));Button del=Ui.secondaryButton(this,"Видалити");del.setOnClickListener(v->{repo.delete(projectId,e.id);buildUi();});c.addView(del,Ui.lpMatchWrap(this,7));root.addView(c,Ui.lpMatchWrap(this,7));}root.addView(Ui.bottomNav(this,projectId,"CLIENT"),Ui.lpMatchWrap(this,18));setContentView(s);}
    private void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=PICK||result!=RESULT_OK||data==null)return;
        final List<Uri> uris=new ArrayList<>();
        if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}
        else if(data.getData()!=null)uris.add(data.getData());
        if(uris.isEmpty())return;
        final EvidenceItem.Category cat;try{cat=EvidenceItem.Category.valueOf(category.getSelectedKey());}catch(Exception e){statusMessage="Невідома категорія доказу";status.setText(statusMessage);return;}
        final int circuitNo=circuit.getIntValue();final String cutKey=cut.getSelectedKey();
        statusMessage="Імпортую файли: "+uris.size()+"…";status.setText(statusMessage);
        StableTaskRunner.run(this,"EVIDENCE_IMPORT",()->{int added=0;for(Uri uri:uris)if(importUri(uri,cat,circuitNo,cutKey))added++;return added;},added->{statusMessage="Додано: "+added;buildUi();},e->{statusMessage="Помилка імпорту: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());if(status!=null)status.setText(statusMessage);});
    }
    private boolean importUri(Uri uri,EvidenceItem.Category cat,int circuitNo,String cutKey)throws Exception{String mime=getContentResolver().getType(uri);String name=name(uri);try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)return false;repo.add(projectId,in,name,mime,cat,circuitNo,cutKey);return true;}}
    private String name(Uri uri){Cursor c=null;try{c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}finally{if(c!=null)c.close();}return "photo.jpg";}
}
