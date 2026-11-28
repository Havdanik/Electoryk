package com.elektoryk.scan.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.elektoryk.scan.model.CircuitProtectionPlan;
import com.elektoryk.scan.model.FieldWorkPlan;
import com.elektoryk.scan.storage.FieldWorkRepository;
import java.util.Locale;

public class CircuitProtectionActivity extends Activity {
    private String projectId;private int circuitNo;private FieldWorkRepository repo;private FieldWorkPlan plan;private CircuitProtectionPlan circuit;private ChoiceStrip amps,curve,poles,protection,rcd,phase;
    @Override protected void onCreate(Bundle b){super.onCreate(b);projectId=getIntent().getStringExtra("projectId");circuitNo=getIntent().getIntExtra("circuitNo",0);repo=new FieldWorkRepository(this);plan=repo.loadPlan(projectId);for(CircuitProtectionPlan c:plan.circuits)if(c.circuitNo==circuitNo){circuit=c;break;}if(circuit==null){finish();return;}buildUi();}
    private void buildUi(){ScrollView s=new ScrollView(this);s.setBackgroundColor(Ui.BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));s.addView(root);root.addView(Ui.brandHeader(this,"ELEKTORYK • G"+String.format(Locale.US,"%02d",circuitNo),"Захист і маркування групи.","ЩИТ",true));root.addView(Ui.screenIntro(this,"ЗАХИСТ",circuit.name,"Автоматична цифра — лише стартова підказка. Тут ти задаєш фінальний автомат/дифзахист і тільки після цього підтверджуєш групу."),Ui.lpMatchWrap(this,10));
        LinearLayout info=Ui.card(this);info.addView(Ui.title(this,circuit.cableKey+" • "+String.format(Locale.US,"%.1f kW",circuit.plannedLoadW/1000.0),19));info.addView(Ui.muted(this,String.format(Locale.US,"Розрахунковий струм ≈ %.1f A",circuit.estimatedCurrentA)));if(circuit.note!=null&&!circuit.note.isEmpty())info.addView(Ui.muted(this,"! "+circuit.note),Ui.lpMatchWrap(this,5));root.addView(info,Ui.lpMatchWrap(this,12));
        root.addView(Ui.title(this,"Номінал автомата",18),Ui.lpMatchWrap(this,14));amps=new ChoiceStrip(this);amps.setItems(new String[]{"6","10","13","16","20","25","32","40","50","63"},new String[]{"6A","10A","13A","16A","20A","25A","32A","40A","50A","63A"},String.valueOf(circuit.suggestedBreakerA));root.addView(amps);
        root.addView(Ui.title(this,"Характеристика",18),Ui.lpMatchWrap(this,12));curve=new ChoiceStrip(this);curve.setItems(new String[]{"B","C","D"},new String[]{"B","C","D"},circuit.suggestedCurve);root.addView(curve);
        root.addView(Ui.title(this,"Полюси",18),Ui.lpMatchWrap(this,12));poles=new ChoiceStrip(this);poles.setItems(new String[]{"1","2","3","4"},new String[]{"1P","2P","3P","4P"},String.valueOf(circuit.poles));root.addView(poles);
        root.addView(Ui.title(this,"Фаза",18),Ui.lpMatchWrap(this,12));phase=new ChoiceStrip(this);phase.setItems(new String[]{"AUTO","L1","L2","L3"},new String[]{"Авто","L1","L2","L3"},circuit.phase);root.addView(phase);
        root.addView(Ui.title(this,"Тип захисту",18),Ui.lpMatchWrap(this,12));protection=new ChoiceStrip(this);protection.setItems(new String[]{"VERIFY","MCB","RCBO","RCD_MCB","NONE"},new String[]{"Ще визначити","Автомат MCB","Дифавтомат RCBO","УЗО + автомат","Без окремого"},circuit.protection);root.addView(protection);
        root.addView(Ui.title(this,"Диференційний струм",18),Ui.lpMatchWrap(this,12));rcd=new ChoiceStrip(this);rcd.setItems(new String[]{"10","30","100","300"},new String[]{"10 mA","30 mA","100 mA","300 mA"},String.valueOf(circuit.residualCurrentMa));root.addView(rcd);
        Button save=Ui.button(this,"Зберегти й підтвердити групу");save.setOnClickListener(v->save(true));root.addView(save,Ui.lpMatchWrap(this,16));Button draft=Ui.secondaryButton(this,"Зберегти як чернетку");draft.setOnClickListener(v->save(false));root.addView(draft,Ui.lpMatchWrap(this,8));root.addView(Ui.muted(this,"Після підтвердження номінал піде в кабельні етикетки та VIP-паспорт об'єкта."),Ui.lpMatchWrap(this,12));setContentView(s);}
    private void save(boolean verified){try{circuit.suggestedBreakerA=Integer.parseInt(amps.getSelectedKey());}catch(Exception ignored){}circuit.suggestedCurve=curve.getSelectedKey();try{circuit.poles=Integer.parseInt(poles.getSelectedKey());}catch(Exception ignored){}circuit.phase=phase.getSelectedKey();circuit.protection=protection.getSelectedKey();try{circuit.residualCurrentMa=Integer.parseInt(rcd.getSelectedKey());}catch(Exception ignored){}circuit.verifiedByElectrician=verified;circuit.manualProtectionEdited=true;repo.savePlan(plan);Toast.makeText(this,verified?"Групу підтверджено":"Чернетку збережено",Toast.LENGTH_SHORT).show();finish();}
}
