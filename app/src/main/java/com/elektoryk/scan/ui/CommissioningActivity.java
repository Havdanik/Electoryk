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

public class CommissioningActivity extends Activity {
    private String projectId;
    private FieldWorkRepository repo;
    private FieldWorkPlan plan;
    private ChoiceStrip circuit,status;
    private NumberStepper voltage,insulation,rcd,loop;
    private TextView summary,currentInfo;

    @Override protected void onCreate(Bundle b){super.onCreate(b);projectId=getIntent().getStringExtra("projectId");repo=new FieldWorkRepository(this);plan=repo.loadPlan(projectId);buildUi();}

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));scroll.addView(root);
        root.addView(Ui.brandHeader(this,"ELEKTORYK • Commissioning","Фактична перевірка груп перед фінальною здачею.","TEST",true));
        root.addView(Ui.screenIntro(this,"КОНТРОЛЬ","Вимірювання після монтажу","Записує фактичні результати по кожній групі. Це журнал вимірювань, а не автоматична сертифікація чи заміна вимог до офіційних випробувань."),Ui.lpMatchWrap(this,10));

        summary=Ui.muted(this,"");root.addView(summary,Ui.lpMatchWrap(this,8));
        if(plan==null||plan.circuits.isEmpty()){root.addView(Ui.muted(this,"Спочатку сформуй групи та захист у монтажній карті."),Ui.lpMatchWrap(this,10));setContentView(scroll);return;}

        String[] keys=new String[plan.circuits.size()],labels=new String[plan.circuits.size()];
        for(int i=0;i<plan.circuits.size();i++){CircuitProtectionPlan c=plan.circuits.get(i);keys[i]=String.valueOf(c.circuitNo);labels[i]="G"+c.circuitNo+" • "+c.name;}
        circuit=new ChoiceStrip(this);circuit.setItems(keys,labels,keys[0]);circuit.setListener(k->loadCurrent());
        root.addView(Ui.title(this,"Група",18),Ui.lpMatchWrap(this,12));root.addView(circuit);

        currentInfo=Ui.muted(this,"");root.addView(currentInfo,Ui.lpMatchWrap(this,7));
        status=new ChoiceStrip(this);status.setItems(new String[]{"PENDING","PASS","FAIL"},new String[]{"Не перевірено","OK","Проблема"},"PENDING");
        root.addView(Ui.title(this,"Результат",18),Ui.lpMatchWrap(this,12));root.addView(status);

        voltage=new NumberStepper(this,"V фактична напруга");voltage.configure(0,500,1,0);root.addView(voltage,Ui.lpMatchWrap(this,10));
        insulation=new NumberStepper(this,"MΩ ізоляція");insulation.configure(0,2000,0.1,1);root.addView(insulation,Ui.lpMatchWrap(this,7));
        rcd=new NumberStepper(this,"ms спрацювання УЗО/RCBO");rcd.configure(0,2000,1,0);root.addView(rcd,Ui.lpMatchWrap(this,7));
        loop=new NumberStepper(this,"Ω петля/імпеданс");loop.configure(0,50,0.01,2);root.addView(loop,Ui.lpMatchWrap(this,7));

        Button save=Ui.button(this,"Зберегти результат групи");save.setOnClickListener(v->saveCurrent());root.addView(save,Ui.lpMatchWrap(this,14));
        Button markPass=Ui.secondaryButton(this,"Швидко: позначити OK");markPass.setOnClickListener(v->{status.setSelectedKey("PASS");saveCurrent();});root.addView(markPass,Ui.lpMatchWrap(this,7));
        Button clear=Ui.secondaryButton(this,"Скинути вимірювання цієї групи");clear.setOnClickListener(v->clearCurrent());root.addView(clear,Ui.lpMatchWrap(this,7));

        root.addView(Ui.bottomNav(this,projectId,"ELECTRIC"),Ui.lpMatchWrap(this,18));setContentView(scroll);loadCurrent();refreshSummary();
    }

    private CircuitProtectionPlan current(){
        if(plan==null||circuit==null)return null;int no=0;try{no=Integer.parseInt(circuit.getSelectedKey());}catch(Exception ignored){}
        for(CircuitProtectionPlan c:plan.circuits)if(c.circuitNo==no)return c;return null;
    }

    private void loadCurrent(){
        CircuitProtectionPlan c=current();if(c==null)return;
        status.setSelectedKey(c.testStatus==null?"PENDING":c.testStatus);
        voltage.setValue(c.measuredVoltageV);insulation.setValue(c.insulationMohm);rcd.setValue(c.rcdTripMs);loop.setValue(c.loopImpedanceOhm);
        currentInfo.setText(String.format(Locale.US,"%s • %s%dA • %dP • %s • %s%s",c.cableKey,c.suggestedCurve,c.suggestedBreakerA,c.poles,c.phase,c.protection,c.verifiedByElectrician?" • захист підтверджено":" • захист ще чернетка"));
    }

    private void saveCurrent(){
        CircuitProtectionPlan c=current();if(c==null)return;
        c.testStatus=status.getSelectedKey();c.measuredVoltageV=voltage.getValue();c.insulationMohm=insulation.getValue();c.rcdTripMs=rcd.getValue();c.loopImpedanceOhm=loop.getValue();c.testVerifiedAt=System.currentTimeMillis();
        repo.savePlan(plan);refreshSummary();Toast.makeText(this,"Результат G"+c.circuitNo+" збережено",Toast.LENGTH_SHORT).show();
    }

    private void clearCurrent(){
        CircuitProtectionPlan c=current();if(c==null)return;c.testStatus="PENDING";c.measuredVoltageV=0;c.insulationMohm=0;c.rcdTripMs=0;c.loopImpedanceOhm=0;c.testVerifiedAt=0;repo.savePlan(plan);loadCurrent();refreshSummary();
    }

    private void refreshSummary(){
        if(summary==null||plan==null)return;int pass=0,fail=0,pending=0;
        for(CircuitProtectionPlan c:plan.circuits){if("PASS".equals(c.testStatus))pass++;else if("FAIL".equals(c.testStatus))fail++;else pending++;}
        summary.setText("Груп: "+plan.circuits.size()+" • OK "+pass+" • проблема "+fail+" • не перевірено "+pending);
    }
}
