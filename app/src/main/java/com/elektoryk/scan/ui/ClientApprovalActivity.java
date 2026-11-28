package com.elektoryk.scan.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.elektoryk.scan.model.ClientApproval;
import com.elektoryk.scan.model.RevisionHistory;
import com.elektoryk.scan.model.RevisionSummary;
import com.elektoryk.scan.storage.ApprovalRepository;
import com.elektoryk.scan.storage.RevisionRepository;

import java.io.FileOutputStream;

public class ClientApprovalActivity extends Activity {
    private String projectId;
    private ApprovalRepository repo;
    private ClientApproval approval;
    private ChoiceStrip planState,estimateState,handoverState;
    private SignatureView signature;
    private TextView status;
    private EditText note;
    private boolean clearStoredSignature;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        projectId=getIntent().getStringExtra("projectId");
        repo=new ApprovalRepository(this);
        approval=repo.load(projectId);
        buildUi();
    }

    private void buildUi(){
        ScrollView s=new ScrollView(this);s.setBackgroundColor(Ui.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));s.addView(root);
        root.addView(Ui.brandHeader(this,"ELEKTORYK • Погодження","Внутрішнє цифрове підтвердження плану, кошторису та здачі.","APPROVAL",true));
        root.addView(Ui.screenIntro(this,"ВАЖЛИВО","Це не КЕП/кваліфікований електронний підпис","Підпис тут — зручне внутрішнє підтвердження домовленостей і версії проєкту. Юридичний статус залежить від ваших окремих документів."),Ui.lpMatchWrap(this,10));
        planState=toggle("План погоджено",approval.planApproved);root.addView(Ui.title(this,"План",18),Ui.lpMatchWrap(this,12));root.addView(planState);
        estimateState=toggle("Кошторис погоджено",approval.estimateApproved);root.addView(Ui.title(this,"Кошторис",18),Ui.lpMatchWrap(this,12));root.addView(estimateState);
        handoverState=toggle("Роботи прийнято",approval.handoverAccepted);root.addView(Ui.title(this,"Здача",18),Ui.lpMatchWrap(this,12));root.addView(handoverState);
        note=new EditText(this);note.setHint("Коротко зафіксуй, що саме погоджено / особливі умови");note.setMinLines(2);note.setText(approval.note);note.setTextColor(Ui.TEXT);note.setHintTextColor(Ui.MUTED);note.setBackgroundColor(Ui.CARD_SOFT);note.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10));root.addView(note,Ui.lpMatchWrap(this,10));
        root.addView(Ui.title(this,"Підпис",18),Ui.lpMatchWrap(this,14));
        signature=new SignatureView(this);root.addView(signature,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,220)));
        Button clear=Ui.secondaryButton(this,"Очистити підпис");
        clear.setOnClickListener(v->{
            signature.clear();
            clearStoredSignature=true;
            if(status!=null)status.setText(approval.signatureFile.isEmpty()?"Полотно очищено.":"Збережений підпис буде видалено після «Зберегти погодження».");
        });
        root.addView(clear,Ui.lpMatchWrap(this,8));
        Button save=Ui.button(this,"Зберегти погодження");save.setOnClickListener(v->save());root.addView(save,Ui.lpMatchWrap(this,12));
        status=Ui.muted(this,approval.signatureFile.isEmpty()?"Підпис ще не збережено.":"Є збережений підпис і попереднє погодження.");root.addView(status,Ui.lpMatchWrap(this,8));
        root.addView(Ui.bottomNav(this,projectId,"CLIENT"),Ui.lpMatchWrap(this,18));setContentView(s);
    }

    private ChoiceStrip toggle(String label,boolean on){ChoiceStrip c=new ChoiceStrip(this);c.setItems(new String[]{"NO","YES"},new String[]{"Ні",label},on?"YES":"NO");return c;}

    private void save(){
        long now=System.currentTimeMillis();
        approval.note=note==null?approval.note:note.getText().toString().trim();
        boolean oldPlan=approval.planApproved,oldEstimate=approval.estimateApproved,oldHandover=approval.handoverAccepted;
        approval.planApproved="YES".equals(planState.getSelectedKey());
        approval.estimateApproved="YES".equals(estimateState.getSelectedKey());
        approval.handoverAccepted="YES".equals(handoverState.getSelectedKey());
        approval.planApprovedAt=approval.planApproved?(oldPlan&&approval.planApprovedAt>0?approval.planApprovedAt:now):0;
        approval.estimateApprovedAt=approval.estimateApproved?(oldEstimate&&approval.estimateApprovedAt>0?approval.estimateApprovedAt:now):0;
        approval.handoverAcceptedAt=approval.handoverAccepted?(oldHandover&&approval.handoverAcceptedAt>0?approval.handoverAcceptedAt:now):0;
        RevisionHistory h=new RevisionRepository(this).load(projectId);
        if(!h.revisions.isEmpty()){RevisionSummary r=h.revisions.get(h.revisions.size()-1);approval.revisionId=r.id;}
        if(clearStoredSignature&&signature.isEmpty()){
            try{java.io.File old=repo.signatureFile(projectId);if(old.exists()&&!old.delete())throw new IllegalStateException("Не вдалося видалити старий файл підпису");}catch(Exception e){status.setText("Не вдалося видалити старий підпис: "+e.getMessage());return;}
            approval.signatureFile="";approval.signatureAt=0;clearStoredSignature=false;
        }
        if(!signature.isEmpty()){
            try{
                Bitmap b=signature.bitmap();
                try(FileOutputStream out=new FileOutputStream(repo.signatureFile(projectId))){if(!b.compress(Bitmap.CompressFormat.PNG,100,out))throw new IllegalStateException("PNG encode failed");}
                b.recycle();approval.signatureFile="signature.png";approval.signatureAt=now;clearStoredSignature=false;
            }catch(Exception e){status.setText("Не вдалося зберегти підпис: "+e.getMessage());return;}
        }
        repo.save(approval);status.setText("Погодження збережено.");
    }
}
