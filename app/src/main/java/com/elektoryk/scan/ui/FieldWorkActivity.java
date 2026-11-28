package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.elektoryk.scan.model.*;
import com.elektoryk.scan.reconstruction.CableCutListEngine;
import com.elektoryk.scan.reconstruction.ProtectionPlanEngine;
import com.elektoryk.scan.reconstruction.SpoolOptimizer;
import com.elektoryk.scan.storage.*;
import java.util.*;

public class FieldWorkActivity extends Activity {
    private String projectId;
    private FieldWorkRepository repo;
    private CableCutList cuts;
    private FieldWorkPlan fieldPlan;
    private ElectricalProject project;
    private SceneSnapshot scene;
    private FloorPlan floor;
    private LinearLayout list,circuitList,stageSummary;
    private TextView summary;
    private ChoiceStrip spoolSize,filter;

    @Override protected void onCreate(Bundle b){super.onCreate(b);projectId=getIntent().getStringExtra("projectId");repo=new FieldWorkRepository(this);load();buildUi();}

    private void load(){
        project=new FileProjectRepository(this).get(projectId);
        scene=new SceneRepository(this).loadScene(projectId);
        floor=new FloorPlanRepository(this).load(projectId);
        CableCutList old=repo.loadCuts(projectId);
        FieldWorkPlan oldPlan=repo.loadPlan(projectId);
        if(project!=null&&scene!=null&&scene.apartmentPlan!=null){
            cuts=new CableCutListEngine().build(project,scene.apartmentPlan,floor,old);
            fieldPlan=new ProtectionPlanEngine().build(projectId,floor,oldPlan);
            decorateProtection();repo.saveCuts(cuts);repo.savePlan(fieldPlan);
        }else{cuts=old;fieldPlan=oldPlan;}
        if(cuts==null){cuts=new CableCutList();cuts.projectId=projectId;}
        if(fieldPlan==null){fieldPlan=new FieldWorkPlan();fieldPlan.projectId=projectId;}
    }

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));scroll.addView(root);
        root.addView(Ui.brandHeader(this,"ELEKTORYK • Монтаж","Підготовка → нарізка → етикетки → монтаж → контроль.","FIELD",true));
        root.addView(Ui.screenIntro(this,"РОБОТА","Кабельна карта об'єкта","Кожен фізичний відрізок має код, початок/кінець, CUT-довжину, бухту, дві етикетки й монтажний статус."),Ui.lpMatchWrap(this,10));

        if(project==null||scene==null||scene.apartmentPlan==null){root.addView(Ui.muted(this,"Потрібен готовий Multi-room план, щит і електроточки."));setContentView(scroll);return;}

        summary=Ui.muted(this,"");root.addView(summary,Ui.lpMatchWrap(this,8));
        stageSummary=new LinearLayout(this);stageSummary.setOrientation(LinearLayout.HORIZONTAL);root.addView(stageSummary,Ui.lpMatchWrap(this,10));

        filter=new ChoiceStrip(this);
        filter.setItems(new String[]{"ALL","CUT","LABEL","INSTALL","DONE"},new String[]{"Усі","Треба нарізати","Треба етикетки","Треба змонтувати","Готові"},"ALL");
        filter.setListener(k->renderPieces());
        root.addView(filter,Ui.lpMatchWrap(this,8));

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button transfer=Ui.secondaryButton(this,"⇄ Перенос");transfer.setOnClickListener(v->open(ProjectTransferActivity.class));actions.addView(transfer,Ui.lpWeight(this,1,6));
        Button print=Ui.button(this,"▣ Етикетки");print.setOnClickListener(v->open(ThermalPrinterActivity.class));actions.addView(print,Ui.lpWeight(this,1,0));root.addView(actions,Ui.lpMatchWrap(this,10));
        Button workflow=Ui.secondaryButton(this,"Етапи всього об'єкта");workflow.setOnClickListener(v->open(WorkflowActivity.class));root.addView(workflow,Ui.lpMatchWrap(this,8));
        Button commissioning=Ui.secondaryButton(this,"✓ Commissioning / фактичні вимірювання");commissioning.setOnClickListener(v->open(CommissioningActivity.class));root.addView(commissioning,Ui.lpMatchWrap(this,8));
        Button handover=Ui.secondaryButton(this,"◈ VIP здача клієнту");handover.setOnClickListener(v->open(HandoverActivity.class));root.addView(handover,Ui.lpMatchWrap(this,8));

        root.addView(Ui.title(this,"Нарізка по бухтах",19),Ui.lpMatchWrap(this,16));
        spoolSize=new ChoiceStrip(this);spoolSize.setItems(new String[]{"50","100","200","305"},new String[]{"50 м","100 м","200 м","305 м"},String.valueOf((int)Math.round(cuts.sourceRollM)));root.addView(spoolSize);
        Button optimize=Ui.secondaryButton(this,"Оптимізувати відрізки по бухтах");
        optimize.setOnClickListener(v->{double m=100;try{m=Double.parseDouble(spoolSize.getSelectedKey());}catch(Exception ignored){}new SpoolOptimizer().optimize(cuts,m);repo.saveCuts(cuts);render();});
        root.addView(optimize,Ui.lpMatchWrap(this,7));

        root.addView(Ui.title(this,"Щит / групи",19),Ui.lpMatchWrap(this,16));
        root.addView(Ui.muted(this,"Автоматичні номінали — лише робоча підказка. Перед монтажем/здачею кожну групу підтверджує електрик."));
        circuitList=new LinearLayout(this);circuitList.setOrientation(LinearLayout.VERTICAL);root.addView(circuitList);

        root.addView(Ui.title(this,"Кабельні відрізки",19),Ui.lpMatchWrap(this,18));
        root.addView(Ui.muted(this,"CUT = скільки реально відрізати з бухти. Монтаж можна відмітити лише після нарізки та етикеток — це захищає від пропущеного маркування."));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);

        root.addView(Ui.bottomNav(this,projectId,"ELECTRIC"),Ui.lpMatchWrap(this,18));setContentView(scroll);render();
    }

    private void render(){
        if(list==null)return;
        int cut=0,printed=0,installed=0;
        for(CableCutPiece p:cuts.pieces){if(p.cutDone)cut++;if(p.labelsPrinted)printed++;if(p.installed)installed++;}
        summary.setText(String.format(Locale.US,"Відрізків %d • %.1f м CUT • нарізано %d • етикетки %d • змонтовано %d%s",cuts.pieces.size(),cuts.totalCutM,cut,printed,installed,cuts.estimatedWasteM>0?String.format(Locale.US," • залишок бухт ≈ %.1f м",cuts.estimatedWasteM):""));
        stageSummary.removeAllViews();
        int n=Math.max(1,cuts.pieces.size());
        stageSummary.addView(Ui.statCard(this,cut+"/"+cuts.pieces.size(),"Нарізка"),Ui.lpWeight(this,1,6));
        stageSummary.addView(Ui.statCard(this,printed+"/"+cuts.pieces.size(),"Етикетки"),Ui.lpWeight(this,1,6));
        stageSummary.addView(Ui.statCard(this,installed+"/"+cuts.pieces.size(),"Монтаж"),Ui.lpWeight(this,1,0));
        renderCircuits();renderPieces();
    }

    private void renderCircuits(){
        circuitList.removeAllViews();
        for(CircuitProtectionPlan c:fieldPlan.circuits){
            LinearLayout card=Ui.card(this);card.addView(Ui.chip(this,c.verifiedByElectrician?"ПІДТВЕРДЖЕНО":"ПІДКАЗКА"));
            card.addView(Ui.title(this,c.name+" • "+c.cableKey,17),Ui.lpMatchWrap(this,5));
            card.addView(Ui.muted(this,String.format(Locale.US,"Навантаження %.1f кВт • ~%.1f A • %s%d A • %dP • %s • %s",c.plannedLoadW/1000.0,c.estimatedCurrentA,c.suggestedCurve,c.suggestedBreakerA,c.poles,c.phase,c.protection)));
            if(!c.verifiedByElectrician)card.addView(Ui.muted(this,"Чернетка: перевір номінал, характеристику, фазу та дифзахист."),Ui.lpMatchWrap(this,4));
            Button edit=Ui.secondaryButton(this,"Редагувати автомат / диф / фазу");edit.setOnClickListener(v->{Intent i=new Intent(this,CircuitProtectionActivity.class);i.putExtra("projectId",projectId);i.putExtra("circuitNo",c.circuitNo);startActivity(i);});card.addView(edit,Ui.lpMatchWrap(this,7));
            Button verify=Ui.secondaryButton(this,c.verifiedByElectrician?"Скасувати підтвердження":"Підтвердити як електрик");verify.setOnClickListener(v->{c.verifiedByElectrician=!c.verifiedByElectrician;c.manualProtectionEdited=true;decorateProtection();save();render();});card.addView(verify,Ui.lpMatchWrap(this,6));
            circuitList.addView(card,Ui.lpMatchWrap(this,7));
        }
    }

    private void renderPieces(){
        list.removeAllViews();
        Map<String,Double> cableM=new LinkedHashMap<>();Map<String,Integer> cableCount=new LinkedHashMap<>();
        for(CableCutPiece p:cuts.pieces){Double m=cableM.get(p.cableKey);cableM.put(p.cableKey,(m==null?0:m)+p.cutLengthM);Integer n=cableCount.get(p.cableKey);cableCount.put(p.cableKey,(n==null?0:n)+1);}
        if(!cableM.isEmpty()){
            LinearLayout card=Ui.card(this);card.addView(Ui.title(this,"Що підготувати з бухт",18));
            for(String key:cableM.keySet())card.addView(Ui.muted(this,String.format(Locale.US,"%s → %d відрізків • %.1f м CUT",key,cableCount.get(key),cableM.get(key))),Ui.lpMatchWrap(this,4));
            list.addView(card,Ui.lpMatchWrap(this,7));
        }

        Map<String,List<CableCutPiece>> spoolMap=new LinkedHashMap<>();
        for(CableCutPiece p:cuts.pieces)if(p.spool!=null&&!p.spool.isEmpty()){List<CableCutPiece> l=spoolMap.get(p.spool);if(l==null){l=new ArrayList<>();spoolMap.put(p.spool,l);}l.add(p);}
        if(!spoolMap.isEmpty()){
            LinearLayout card=Ui.card(this);card.addView(Ui.title(this,"План нарізки по бухтах",18));
            for(String spool:spoolMap.keySet()){
                double used=0;StringBuilder codes=new StringBuilder();
                for(CableCutPiece p:spoolMap.get(spool)){used+=p.cutLengthM;if(codes.length()>0)codes.append(" • ");codes.append(p.code).append(" ").append(String.format(Locale.US,"%.1fм",p.cutLengthM));}
                double left=Math.max(0,cuts.sourceRollM-used);card.addView(Ui.title(this,spool+" • "+String.format(Locale.US,"%.1f/%.0f м",used,cuts.sourceRollM),15),Ui.lpMatchWrap(this,7));
                card.addView(Ui.muted(this,codes+" • залишок ≈ "+String.format(Locale.US,"%.1f м",left)),Ui.lpMatchWrap(this,2));
            }
            list.addView(card,Ui.lpMatchWrap(this,7));
        }

        String f=filter==null?"ALL":filter.getSelectedKey();int visible=0;
        for(CableCutPiece p:cuts.pieces){if(!matches(p,f))continue;visible++;list.addView(pieceCard(p),Ui.lpMatchWrap(this,8));}
        if(visible==0)list.addView(Ui.muted(this,"У цьому фільтрі немає відрізків."),Ui.lpMatchWrap(this,10));
    }

    private boolean matches(CableCutPiece p,String f){
        if("CUT".equals(f))return !p.cutDone;
        if("LABEL".equals(f))return p.cutDone&&!p.labelsPrinted;
        if("INSTALL".equals(f))return p.cutDone&&p.labelsPrinted&&!p.installed;
        if("DONE".equals(f))return p.installed;
        return true;
    }

    private LinearLayout pieceCard(CableCutPiece p){
        LinearLayout card=Ui.card(this);card.addView(Ui.chip(this,p.code+" • "+p.cableKey));
        card.addView(Ui.title(this,p.fromLabel+" → "+p.toLabel,17),Ui.lpMatchWrap(this,5));
        card.addView(Ui.muted(this,String.format(Locale.US,"Траса %.1f м + фікс. запас %.1f м + %.0f%% → CUT %.1f м",p.routeLengthM,p.fixedReserveM,p.percentReserve,p.cutLengthM)));
        if(p.wallPassages>0)card.addView(Ui.muted(this,"Проходок через стіни: "+p.wallPassages),Ui.lpMatchWrap(this,3));
        if(p.warning!=null&&!p.warning.isEmpty())card.addView(Ui.muted(this,"! "+p.warning),Ui.lpMatchWrap(this,4));
        if(p.spool!=null&&!p.spool.isEmpty())card.addView(Ui.muted(this,"Бухта: "+p.spool),Ui.lpMatchWrap(this,4));
        card.addView(Ui.muted(this,"A: "+p.startSticker),Ui.lpMatchWrap(this,5));card.addView(Ui.muted(this,"B: "+p.endSticker),Ui.lpMatchWrap(this,2));

        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        Button b1=Ui.secondaryButton(this,p.cutDone?"✓ Нарізано":"Нарізати");b1.setOnClickListener(v->{if(p.installed){Toast.makeText(this,"Спочатку скасуй монтаж.",Toast.LENGTH_SHORT).show();return;}p.cutDone=!p.cutDone;if(!p.cutDone)p.labelsPrinted=false;save();render();});row.addView(b1,Ui.lpWeight(this,1,5));
        Button b2=Ui.secondaryButton(this,p.labelsPrinted?"✓ Етикетки":"Етикетки");b2.setOnClickListener(v->{if(!p.cutDone){Toast.makeText(this,"Спочатку відміть нарізку кабелю.",Toast.LENGTH_SHORT).show();return;}Intent i=new Intent(this,ThermalPrinterActivity.class);i.putExtra("projectId",projectId);i.putExtra("pieceKey",p.stableKey);startActivity(i);});row.addView(b2,Ui.lpWeight(this,1,5));
        Button b3=Ui.secondaryButton(this,p.installed?"✓ Монтаж":"Монтаж");b3.setOnClickListener(v->{if(!p.cutDone||!p.labelsPrinted){Toast.makeText(this,"Для монтажу спочатку потрібні нарізка та етикетки.",Toast.LENGTH_SHORT).show();return;}p.installed=!p.installed;save();render();});row.addView(b3,Ui.lpWeight(this,1,0));
        card.addView(row,Ui.lpMatchWrap(this,8));return card;
    }

    private void decorateProtection(){
        if(cuts==null||fieldPlan==null)return;Map<Integer,CircuitProtectionPlan> map=new HashMap<>();for(CircuitProtectionPlan c:fieldPlan.circuits)map.put(c.circuitNo,c);
        for(CableCutPiece p:cuts.pieces){CircuitProtectionPlan c=map.get(p.circuitNo);if(c==null)continue;p.protectionTag=c.suggestedCurve+c.suggestedBreakerA+"A "+c.poles+"P"+("AUTO".equals(c.phase)?"":" "+c.phase)+(c.verifiedByElectrician?"":" ?");String cut=String.format(Locale.US,"%.1fm",p.cutLengthM);p.startSticker=p.code+" A | "+p.fromLabel+" > "+p.toLabel+" | "+p.cableKey+" | "+p.protectionTag+" | "+cut;p.endSticker=p.code+" B | "+p.toLabel+" < "+p.fromLabel+" | "+p.cableKey+" | "+p.protectionTag+" | "+cut;}
    }

    private void save(){repo.saveCuts(cuts);repo.savePlan(fieldPlan);}
    private void open(Class<?> c){Intent i=new Intent(this,c);i.putExtra("projectId",projectId);startActivity(i);}
    @Override protected void onResume(){super.onResume();if(projectId!=null&&repo!=null){load();if(list!=null)render();}}
}
