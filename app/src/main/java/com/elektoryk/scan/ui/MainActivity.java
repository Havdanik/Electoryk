package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.elektoryk.scan.model.ElectricalProject;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.SceneSnapshot;
import com.elektoryk.scan.model.CableCutList;
import com.elektoryk.scan.model.FieldWorkPlan;
import com.elektoryk.scan.model.ProjectWorkflowSnapshot;
import com.elektoryk.scan.model.CommercialPlan;
import com.elektoryk.scan.model.EvidenceIndex;
import com.elektoryk.scan.model.ClientApproval;
import com.elektoryk.scan.storage.FileProjectRepository;
import com.elektoryk.scan.storage.FloorPlanRepository;
import com.elektoryk.scan.storage.ProjectRepository;
import com.elektoryk.scan.storage.SceneRepository;
import com.elektoryk.scan.storage.FieldWorkRepository;
import com.elektoryk.scan.storage.CommercialRepository;
import com.elektoryk.scan.storage.EvidenceRepository;
import com.elektoryk.scan.storage.ApprovalRepository;
import com.elektoryk.scan.reconstruction.ProjectWorkflowEngine;

import java.text.DateFormat;
import java.util.List;

public class MainActivity extends Activity {
    private ProjectRepository repo;
    private SceneRepository sceneRepo;
    private FloorPlanRepository floorRepo;
    private LinearLayout projectList;
    private LinearLayout statsRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = new FileProjectRepository(this);
        sceneRepo = new SceneRepository(this);
        floorRepo = new FloorPlanRepository(this);
        buildUi();

        if (!getSharedPreferences("startup", MODE_PRIVATE).getBoolean("device_profile_seen", false)) {
            getSharedPreferences("startup", MODE_PRIVATE).edit()
                    .putBoolean("device_profile_seen", true)
                    .apply();
            startActivity(new Intent(this, DeviceProfileActivity.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderProjects();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,16), Ui.dp(this,16), Ui.dp(this,16), Ui.dp(this,30));
        scroll.addView(root);

        root.addView(Ui.brandHeader(this,
                "ELEKTORYK",
                "Плануй • рахуй • презентуй. Вся електрика в одному додатку.",
                "BRANDED UI",
                false));

        LinearLayout ctaRow = new LinearLayout(this);
        ctaRow.setOrientation(LinearLayout.HORIZONTAL);
        Button add = Ui.button(this, "+ Новий об'єкт");
        add.setOnClickListener(v -> createAndOpenProject());
        ctaRow.addView(add, Ui.lpWeight(this, 1f, 8));

        Button device = Ui.secondaryButton(this, "Профіль телефона");
        device.setOnClickListener(v -> startActivity(new Intent(this, DeviceProfileActivity.class)));
        ctaRow.addView(device, Ui.lpWeight(this, 1f, 0));
        root.addView(ctaRow, Ui.lpMatchWrap(this, 14));
        Button receive = Ui.secondaryButton(this, "⇄ Імпорт / отримати .elektoryk");
        receive.setOnClickListener(v -> startActivity(new Intent(this, ProjectTransferActivity.class)));
        root.addView(receive, Ui.lpMatchWrap(this, 8));

        root.addView(Ui.title(this, "Що ти тут робиш", 20), Ui.lpMatchWrap(this, 18));
        root.addView(Ui.muted(this, "Маршрут простий: скан → план квартири → електрика → траси → клієнтський результат."));

        LinearLayout flow1 = Ui.actionCard(this, "1. Smart Scan", "Швидко скануєш квартиру, збираєш 3D та перевіряєш площу.", "СКАН");
        flow1.setOnClickListener(v -> createAndOpenProject());
        root.addView(flow1, Ui.lpMatchWrap(this, 10));

        LinearLayout flow2 = Ui.actionCard(this, "2. План квартири", "Програма будує єдиний Multi-room план, кімнати, двері, вікна та зони.", "ПЛАН");
        flow2.setOnClickListener(v -> openLatestProjectOrCreate());
        root.addView(flow2, Ui.lpMatchWrap(this, 8));

        LinearLayout flow3 = Ui.actionCard(this, "3. Електрика і траси", "Розставляєш точки, перевіряєш групи, рахуєш метраж і монтаж.", "ЕЛЕКТРИКА");
        flow3.setOnClickListener(v -> openLatestProjectOrCreate());
        root.addView(flow3, Ui.lpMatchWrap(this, 8));

        LinearLayout flow4 = Ui.actionCard(this, "4. Презентація клієнту", "Показуєш план, розгортки стін, PNG і кошторис більш зрозуміло.", "КЛІЄНТ");
        flow4.setOnClickListener(v -> openLatestProjectOrCreate());
        root.addView(flow4, Ui.lpMatchWrap(this, 8));

        root.addView(Ui.title(this, "Підсумок", 20), Ui.lpMatchWrap(this, 18));
        statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(statsRow, Ui.lpMatchWrap(this, 10));

        root.addView(Ui.title(this, "Об'єкти", 20), Ui.lpMatchWrap(this, 18));
        root.addView(Ui.muted(this, "Кожна карточка нижче показує не просто назву, а ще й що вже зроблено і який логічний наступний крок."));

        projectList = new LinearLayout(this);
        projectList.setOrientation(LinearLayout.VERTICAL);
        root.addView(projectList);

        setContentView(scroll);
    }

    private void renderProjects() {
        if (projectList == null) return;
        projectList.removeAllViews();
        if (statsRow != null) statsRow.removeAllViews();

        List<ElectricalProject> projects = repo.list();
        int scanned = 0;
        int planned = 0;
        int electrical = 0;
        for (ElectricalProject p : projects) {
            Status st = statusFor(p);
            if (st.stage >= 2) scanned++;
            if (st.stage >= 3) planned++;
            if (st.stage >= 4) electrical++;
        }

        if (statsRow != null) {
            statsRow.addView(Ui.statCard(this, String.valueOf(projects.size()), "Об'єкти"), Ui.lpWeight(this, 1f, 8));
            statsRow.addView(Ui.statCard(this, String.valueOf(scanned), "Є скан"), Ui.lpWeight(this, 1f, 8));
            statsRow.addView(Ui.statCard(this, String.valueOf(planned), "Є план"), Ui.lpWeight(this, 1f, 8));
            statsRow.addView(Ui.statCard(this, String.valueOf(electrical), "Є електрика"), Ui.lpWeight(this, 1f, 0));
        }

        if (projects.isEmpty()) {
            LinearLayout empty = Ui.card(this);
            empty.addView(Ui.title(this, "Ще немає об'єктів", 18));
            empty.addView(Ui.muted(this, "Створи перший об'єкт. Далі інтерфейс поведе тебе по кроках — від скану до презентації клієнту."));
            Button add = Ui.button(this, "Створити перший об'єкт");
            add.setOnClickListener(v -> createAndOpenProject());
            empty.addView(add, Ui.lpMatchWrap(this, 12));
            projectList.addView(empty, Ui.lpMatchWrap(this, 10));
            return;
        }

        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (ElectricalProject p : projects) {
            Status st = statusFor(p);
            LinearLayout card = Ui.card(this);
            card.addView(Ui.chip(this, st.badge));
            TextView name = Ui.title(this, p.displayName(), 18);
            card.addView(name, Ui.lpMatchWrap(this, 6));

            String meta = (p.approximateAreaM2 > 0 ? trimArea(p.approximateAreaM2) + " м² • " : "")
                    + (p.roomCount > 0 ? p.roomCount + " кімн. • " : "")
                    + "оновлено " + df.format(p.updatedAt);
            card.addView(Ui.muted(this, meta));

            android.widget.ProgressBar flowBar = new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            flowBar.setMax(100); flowBar.setProgress(st.progress);
            card.addView(flowBar, Ui.lpMatchWrap(this, 8));
            card.addView(Ui.muted(this, "Готовність об'єкта: " + st.progress + "%"), Ui.lpMatchWrap(this, 3));

            TextView next = new TextView(this);
            next.setText("Наступний крок: " + st.nextStep);
            next.setTextColor(Ui.GOLD);
            next.setTextSize(14);
            card.addView(next, Ui.lpMatchWrap(this, 8));

            TextView help = Ui.muted(this, st.description);
            help.setTextSize(13);
            card.addView(help, Ui.lpMatchWrap(this, 4));

            card.setOnClickListener(v -> openProject(p.id));
            projectList.addView(card, Ui.lpMatchWrap(this, 10));
        }
    }

    private String trimArea(double v) {
        if (Math.abs(v - Math.round(v)) < 0.01) return String.valueOf((int) Math.round(v));
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    private Status statusFor(ElectricalProject p) {
        SceneSnapshot scene = sceneRepo.loadScene(p.id);
        FloorPlan floor = floorRepo.load(p.id);
        FieldWorkRepository fw = new FieldWorkRepository(this);
        CableCutList cuts = fw.loadCuts(p.id);
        FieldWorkPlan field = fw.loadPlan(p.id);
        CommercialPlan commercial=new CommercialRepository(this).load(p.id);
        EvidenceIndex evidence=new EvidenceRepository(this).load(p.id);
        ClientApproval approval=new ApprovalRepository(this).load(p.id);
        ProjectWorkflowSnapshot flow = new ProjectWorkflowEngine().build(p,scene,floor,cuts,field,commercial,evidence,approval);
        String badge = flow.nextStage == null ? "ГОТОВО" : flow.nextStage.label.toUpperCase();
        String next = flow.nextStage == null ? "Готово" : flow.nextStage.goal;
        String desc = "Етапи перевіряються по реальних даних: скан, план, групи, траси, монтаж і QA.";
        int stage = 1;
        if(flow.overallProgress>=20)stage=2;if(flow.overallProgress>=40)stage=3;if(flow.overallProgress>=60)stage=4;
        return new Status(stage,badge,next,desc,flow.overallProgress);
    }

    private void createAndOpenProject() {
        ElectricalProject p = new ElectricalProject();
        repo.save(p);
        openProject(p.id);
    }

    private void openLatestProjectOrCreate() {
        List<ElectricalProject> projects = repo.list();
        if (projects.isEmpty()) createAndOpenProject();
        else openProject(projects.get(0).id);
    }

    private void openProject(String id) {
        Intent i = new Intent(this, ObjectFlowActivity.class);
        i.putExtra("projectId", id);
        startActivity(i);
    }

    private static class Status {
        final int stage;
        final String badge;
        final String nextStep;
        final String description;
        final int progress;

        Status(int stage, String badge, String nextStep, String description, int progress) {
            this.stage = stage;
            this.badge = badge;
            this.nextStep = nextStep;
            this.description = description;
            this.progress = progress;
        }
    }
}
