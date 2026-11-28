package com.elektoryk.scan.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.CircuitAnalysis;
import com.elektoryk.scan.model.ElectricalDesignReport;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.SceneSnapshot;
import com.elektoryk.scan.reconstruction.ElectricalDesignAnalyzer;
import com.elektoryk.scan.storage.FloorPlanRepository;
import com.elektoryk.scan.storage.SceneRepository;

import java.util.Locale;

public class ElectricalCheckActivity extends Activity {
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        String projectId=getIntent().getStringExtra("projectId");
        SceneSnapshot scene=new SceneRepository(this).loadScene(projectId);
        FloorPlan floor=new FloorPlanRepository(this).load(projectId);
        ApartmentPlan plan=scene==null?null:scene.apartmentPlan;
        ElectricalDesignReport report=new ElectricalDesignAnalyzer().analyze(plan,floor);

        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(11,11,13));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,28));scroll.addView(root);

        root.addView(Ui.brandHeader(this,"ELEKTORYK • QA","Швидка перевірка електроплану перед розрахунком.","QA",true));
        root.addView(Ui.screenIntro(this,"ПЕРЕВІРКА","Перевірка електроплану","Шукає точки без груп, прив'язок, можливі дублікати та суперечності. Це контроль даних, а не заміна професійного рішення електрика."),Ui.lpMatchWrap(this,10));

        LinearLayout summary=Ui.card(this);
        summary.addView(Ui.title(this,report.score+"/100",24));
        summary.addView(Ui.muted(this,String.format(Locale.US,
                "Точок %d • щитів %d • груп %d • задане навантаження %.1f кВт",
                report.totalPoints,report.panelCount,report.circuits.size(),report.totalPlannedLoadW/1000.0)));
        summary.addView(Ui.muted(this,
                "Без групи "+report.unassignedCircuitPoints
                +" • без кімнати "+report.unboundRoomPoints
                +" • без стіни "+report.unboundWallPoints
                +" • дублікати "+report.duplicateCandidates),Ui.lpMatchWrap(this,5));
        root.addView(summary,Ui.lpMatchWrap(this,12));

        if(!report.warnings.isEmpty()){
            root.addView(Ui.title(this,"Треба перевірити",19),Ui.lpMatchWrap(this,16));
            for(String w:report.warnings)root.addView(Ui.muted(this,"• "+w),Ui.lpMatchWrap(this,5));
        }

        root.addView(Ui.title(this,"Групи",19),Ui.lpMatchWrap(this,16));
        for(CircuitAnalysis c:report.circuits){
            LinearLayout card=Ui.card(this);
            card.addView(Ui.title(this,"Група "+c.circuitNo,17));
            card.addView(Ui.muted(this,String.format(Locale.US,
                    "Точок %d • модулів %d • навантаження %.1f кВт • кімнат %d",
                    c.pointCount,c.moduleCount,c.plannedLoadW/1000.0,c.roomIds.size())));
            if(!c.cableKeys.isEmpty())card.addView(Ui.muted(this,"Кабель: "+android.text.TextUtils.join(", ",c.cableKeys)),Ui.lpMatchWrap(this,4));
            for(String w:c.warnings)card.addView(Ui.muted(this,"! "+w),Ui.lpMatchWrap(this,4));
            root.addView(card,Ui.lpMatchWrap(this,7));
        }

        root.addView(Ui.bottomNav(this,projectId,"ELECTRIC"),Ui.lpMatchWrap(this,18));
        setContentView(scroll);
    }
}
