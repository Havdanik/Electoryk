package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.elektoryk.scan.model.*;
import com.elektoryk.scan.reconstruction.CommercialPlanEngine;
import com.elektoryk.scan.reconstruction.InstallationRoutingEngine;
import com.elektoryk.scan.storage.*;

import java.util.*;

public class EstimateActivity extends Activity {
    private String projectId;
    private CommercialRepository repo;
    private CommercialPlan plan;
    private NumberStepper materialMarkup,laborMarkup,discount;
    private final Map<String,Row> rows=new LinkedHashMap<>();
    private TextView totals;

    @Override protected void onCreate(Bundle b){super.onCreate(b);projectId=getIntent().getStringExtra("projectId");repo=new CommercialRepository(this);rebuild();}

    private void rebuild(){
        ElectricalProject project=new FileProjectRepository(this).get(projectId);
        SceneSnapshot scene=new SceneRepository(this).loadScene(projectId);
        FloorPlan floor=new FloorPlanRepository(this).load(projectId);
        FieldWorkPlan field=new FieldWorkRepository(this).loadPlan(projectId);
        CommercialPlan previous=repo.load(projectId);
        InstallationRoutingResult routing=(project!=null&&scene!=null&&scene.apartmentPlan!=null)?new InstallationRoutingEngine().build(project,scene.apartmentPlan,floor):new InstallationRoutingResult();
        plan=new CommercialPlanEngine().build(project,routing,field,floor,previous);
        CatalogState catalog=new CatalogRepository(this).load();
        for(CommercialLine l:plan.lines){CatalogItem ci=catalog.find(l.stableKey);if(ci==null)continue;if(l.unitCost<=0)l.unitCost=ci.unitCost;if(l.stockQty<=0)l.stockQty=ci.stockQty;if((l.packSize<=1.0||l.packSize<=0)&&ci.packSize>0)l.packSize=ci.packSize;double need=Math.max(0,l.requiredQty-l.stockQty);l.purchaseQty=l.owner==CommercialLine.ProcurementOwner.CLIENT?0:Math.ceil(need/Math.max(.0001,l.packSize))*Math.max(.0001,l.packSize);if(l.unitClientPrice<=0&&l.unitCost>0)l.unitClientPrice=l.unitCost*(1.0+(l.category==CommercialLine.Category.MATERIAL?plan.materialMarkupPercent:plan.laborMarkupPercent)/100.0);}
        repo.save(plan);buildUi();
    }

    private void buildUi(){
        ScrollView s=new ScrollView(this);s.setBackgroundColor(Ui.BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));s.addView(root);
        root.addView(Ui.brandHeader(this,"ELEKTORYK • Кошторис","Технічні кількості вже підтягуються з проєкту. Ти задаєш ціни, склад і хто закуповує.","КОШТОРИС",true));
        root.addView(Ui.screenIntro(this,"ЗАКУПІВЛЯ","Матеріали + роботи + запас","Програма рахує, що потрібно, віднімає склад, округляє до упаковки та окремо показує собівартість і клієнтську суму."),Ui.lpMatchWrap(this,10));

        LinearLayout cfg=Ui.card(this);cfg.addView(Ui.title(this,"Націнки / знижка",18));
        materialMarkup=new NumberStepper(this,"% матеріали");materialMarkup.configure(0,200,1,0);materialMarkup.setValue(plan.materialMarkupPercent);cfg.addView(materialMarkup,Ui.lpMatchWrap(this,6));
        laborMarkup=new NumberStepper(this,"% роботи");laborMarkup.configure(0,200,1,0);laborMarkup.setValue(plan.laborMarkupPercent);cfg.addView(laborMarkup,Ui.lpMatchWrap(this,6));
        discount=new NumberStepper(this,"% знижка");discount.configure(0,50,1,0);discount.setValue(plan.discountPercent);cfg.addView(discount,Ui.lpMatchWrap(this,6));root.addView(cfg,Ui.lpMatchWrap(this,10));

        rows.clear();root.addView(Ui.title(this,"Матеріали",20),Ui.lpMatchWrap(this,16));
        for(CommercialLine l:plan.lines)if(l.category==CommercialLine.Category.MATERIAL)addLine(root,l);
        root.addView(Ui.title(this,"Роботи",20),Ui.lpMatchWrap(this,16));
        for(CommercialLine l:plan.lines)if(l.category==CommercialLine.Category.LABOR)addLine(root,l);

        totals=Ui.title(this,"",20);root.addView(totals,Ui.lpMatchWrap(this,18));refreshTotalsPreview();
        Button save=Ui.button(this,"Зберегти кошторис і закупівлю");save.setOnClickListener(v->{save();rebuild();});root.addView(save,Ui.lpMatchWrap(this,12));
        Button pdf=Ui.secondaryButton(this,"PDF • зберегти / скинути клієнту");pdf.setOnClickListener(v->{save();ElectricalProject ep=new FileProjectRepository(this).get(projectId);EstimatePdfExporter.share(this,plan,ep==null?"Об'єкт":ep.displayName());});root.addView(pdf,Ui.lpMatchWrap(this,8));
        Button receipt=Ui.secondaryButton(this,"Термопринтер • надрукувати кошторис");receipt.setOnClickListener(v->{save();open(EstimateThermalActivity.class);});root.addView(receipt,Ui.lpMatchWrap(this,8));
        Button revisions=Ui.secondaryButton(this,"Версії / зміни V1 V2 V3");revisions.setOnClickListener(v->open(RevisionActivity.class));root.addView(revisions,Ui.lpMatchWrap(this,8));
        root.addView(Ui.bottomNav(this,projectId,"ELECTRIC"),Ui.lpMatchWrap(this,18));setContentView(s);
    }

    private void addLine(LinearLayout root,CommercialLine l){
        LinearLayout c=Ui.card(this);c.addView(Ui.chip(this,l.category==CommercialLine.Category.MATERIAL?"МАТЕРІАЛ":"РОБОТА"));c.addView(Ui.title(this,l.name,17),Ui.lpMatchWrap(this,5));
        c.addView(Ui.muted(this,String.format(Locale.US,"Потрібно %.2f %s",l.requiredQty,l.unit)));
        Row r=new Row();r.line=l;
        if(l.category==CommercialLine.Category.MATERIAL){
            r.stock=new NumberStepper(this,l.unit+" на складі");r.stock.configure(0,100000,0.5,2);r.stock.setValue(l.stockQty);c.addView(r.stock,Ui.lpMatchWrap(this,6));
            r.pack=new NumberStepper(this,l.unit+" упаковка");r.pack.configure(0.01,10000,0.5,2);r.pack.setValue(l.packSize);c.addView(r.pack,Ui.lpMatchWrap(this,6));
            r.owner=new ChoiceStrip(this);r.owner.setItems(new String[]{"MASTER","CLIENT"},new String[]{"Закуповую я","Матеріал клієнта"},l.owner.name());c.addView(r.owner,Ui.lpMatchWrap(this,6));
            r.state=new ChoiceStrip(this);String st=l.received?"RECEIVED":l.purchased?"PURCHASED":l.unavailable?"MISSING":"NEED";r.state.setItems(new String[]{"NEED","MISSING","PURCHASED","RECEIVED"},new String[]{"Треба купити","Немає / заміна","Куплено","Отримано"},st);c.addView(r.state,Ui.lpMatchWrap(this,6));
            r.supplier=textInput("Магазин / постачальник",l.supplier);c.addView(r.supplier,Ui.lpMatchWrap(this,6));
            r.replacement=textInput("Фактично куплено / заміна (якщо інша позиція)",l.replacementName);c.addView(r.replacement,Ui.lpMatchWrap(this,6));
        }
        r.cost=new NumberStepper(this,l.category==CommercialLine.Category.MATERIAL?"грн / "+l.unit:"грн / "+l.unit);r.cost.configure(0,1000000,1,2);r.cost.setValue(l.unitCost);c.addView(r.cost,Ui.lpMatchWrap(this,6));
        rows.put(l.stableKey,r);root.addView(c,Ui.lpMatchWrap(this,8));
    }

    private void save(){
        plan.materialMarkupPercent=materialMarkup.getValue();plan.laborMarkupPercent=laborMarkup.getValue();plan.discountPercent=discount.getValue();
        for(Row r:rows.values()){
            CommercialLine l=r.line;l.unitCost=r.cost.getValue();
            if(l.category==CommercialLine.Category.MATERIAL){l.stockQty=r.stock.getValue();l.packSize=Math.max(0.01,r.pack.getValue());try{l.owner=CommercialLine.ProcurementOwner.valueOf(r.owner.getSelectedKey());}catch(Exception ignored){}String st=r.state.getSelectedKey();l.purchased="PURCHASED".equals(st)||"RECEIVED".equals(st);l.received="RECEIVED".equals(st);l.unavailable="MISSING".equals(st);l.supplier=r.supplier.getText().toString().trim();l.replacementName=r.replacement.getText().toString().trim();double need=Math.max(0,l.requiredQty-l.stockQty);l.purchaseQty=l.owner==CommercialLine.ProcurementOwner.CLIENT?0:Math.ceil(need/l.packSize)*l.packSize;l.unitClientPrice=l.owner==CommercialLine.ProcurementOwner.CLIENT?0:l.unitCost*(1+plan.materialMarkupPercent/100.0);}else{l.purchaseQty=l.requiredQty;l.unitClientPrice=l.unitCost*(1+plan.laborMarkupPercent/100.0);}
        }
        plan.recalc();repo.save(plan);CatalogRepository catalogRepo=new CatalogRepository(this);for(CommercialLine l:plan.lines)catalogRepo.updateFrom(l);
    }

    private void refreshTotalsPreview(){plan.recalc();totals.setText(String.format(Locale.US,"Собівартість матеріалів %.0f грн • клієнту %.0f грн\nРоботи %.0f грн • Разом ≈ %.0f грн",plan.materialCost,plan.materialClient,plan.laborClient,plan.total));}
    private EditText textInput(String hint,String value){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setText(value==null?"":value);e.setTextColor(Ui.TEXT);e.setHintTextColor(Ui.MUTED);e.setBackgroundColor(Ui.CARD_SOFT);e.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10));return e;}
    private void open(Class<?> c){Intent i=new Intent(this,c);i.putExtra("projectId",projectId);startActivity(i);}
    private static class Row{CommercialLine line;NumberStepper stock,pack,cost;ChoiceStrip owner,state;EditText supplier,replacement;}
}
