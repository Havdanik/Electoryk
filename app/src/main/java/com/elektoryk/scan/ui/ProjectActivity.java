package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.elektoryk.scan.model.ElectricalProject;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.ProjectProfile;
import com.elektoryk.scan.model.ProjectSettings;
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
import com.elektoryk.scan.reconstruction.AutoFloorPlanBuilder;
import com.elektoryk.scan.reconstruction.ProjectWorkflowEngine;

public class ProjectActivity extends Activity {
    private ProjectRepository repo;
    private ElectricalProject project;

    private ChoiceStrip objectType, areaReference, areaAccuracy, wallMaterial, phases, wiring, junction, reserve, protection, passage, bundle;
    private NumberStepper area, rooms, groups, panelReserve, endpointReserve, passageReserve, fastenerSpacing, maxBundle;
    private TextView generatedName;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        repo=new FileProjectRepository(this);
        String id=getIntent().getStringExtra("projectId");
        project=repo.get(id);
        if(project==null){finish();return;}
        buildUi();
    }

    private void section(LinearLayout root,String title,String help){
        root.addView(Ui.title(this,title,18),Ui.lpMatchWrap(this,14));
        if(help!=null&&!help.isEmpty()) root.addView(Ui.muted(this,help));
    }

    private LinearLayout actionBlock(String tag, String title, String desc, Button action){
        LinearLayout card = Ui.card(this);
        card.addView(Ui.chip(this, tag));
        card.addView(Ui.title(this, title, 18), Ui.lpMatchWrap(this, 6));
        TextView d = Ui.muted(this, desc);
        d.setTextSize(13);
        card.addView(d, Ui.lpMatchWrap(this, 4));
        card.addView(action, Ui.lpMatchWrap(this, 12));
        return card;
    }

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,34));
        scroll.addView(root);

        root.addView(Ui.brandHeader(this,
                "ELEKTORYK • Об'єкт",
                "Швидко задаєш параметри, а далі йдеш по зрозумілих кроках — від скану до клієнта.",
                "ПРОЄКТ",
                true));

        generatedName=Ui.title(this,project.displayName(),22);
        root.addView(generatedName,Ui.lpMatchWrap(this,12));
        root.addView(Ui.muted(this,"Менше ручного тексту: більшість речей вибираються. Вручну вводиш в основному тільки числа."));

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.addView(Ui.statCard(this, project.approximateAreaM2>0?trim(project.approximateAreaM2):"—", "м²"), Ui.lpWeight(this,1f,8));
        summary.addView(Ui.statCard(this, project.roomCount>0?String.valueOf(project.roomCount):"—", "кімнати"), Ui.lpWeight(this,1f,8));
        summary.addView(Ui.statCard(this, project.settings.plannedGroups>0?String.valueOf(project.settings.plannedGroups):"—", "групи"), Ui.lpWeight(this,1f,0));
        root.addView(summary, Ui.lpMatchWrap(this,12));

        SceneSnapshot workflowScene=new SceneRepository(this).loadScene(project.id);
        FloorPlan workflowFloor=new FloorPlanRepository(this).load(project.id);
        FieldWorkRepository workflowFieldRepo=new FieldWorkRepository(this);
        CableCutList workflowCuts=workflowFieldRepo.loadCuts(project.id);
        FieldWorkPlan workflowField=workflowFieldRepo.loadPlan(project.id);
        CommercialPlan workflowCommercial=new CommercialRepository(this).load(project.id);
        EvidenceIndex workflowEvidence=new EvidenceRepository(this).load(project.id);
        ClientApproval workflowApproval=new ApprovalRepository(this).load(project.id);
        ProjectWorkflowSnapshot workflow=new ProjectWorkflowEngine().build(project,workflowScene,workflowFloor,workflowCuts,workflowField,workflowCommercial,workflowEvidence,workflowApproval);
        LinearLayout workflowCard=Ui.card(this);
        workflowCard.addView(Ui.chip(this,"WORKFLOW"));
        workflowCard.addView(Ui.title(this,"Готовність об'єкта • "+workflow.overallProgress+"%",20),Ui.lpMatchWrap(this,5));
        android.widget.ProgressBar workflowBar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        workflowBar.setMax(100);workflowBar.setProgress(workflow.overallProgress);workflowCard.addView(workflowBar,Ui.lpMatchWrap(this,6));
        workflowCard.addView(Ui.muted(this,"Наступне: "+workflow.nextStage.label+" — "+workflow.nextStage.goal),Ui.lpMatchWrap(this,6));
        Button workflowButton=Ui.button(this,"Відкрити всі етапи об'єкта");
        workflowButton.setOnClickListener(v->{saveProject();open(WorkflowActivity.class);});
        workflowCard.addView(workflowButton,Ui.lpMatchWrap(this,10));
        Button auditButton=Ui.secondaryButton(this,"Перевірити, чого ще не вистачає");
        auditButton.setOnClickListener(v->{saveProject();open(GapAuditActivity.class);});
        workflowCard.addView(auditButton,Ui.lpMatchWrap(this,8));
        root.addView(workflowCard,Ui.lpMatchWrap(this,12));

        LinearLayout settingsCard = Ui.card(this);
        settingsCard.addView(Ui.title(this,"Базові параметри",20));
        settingsCard.addView(Ui.muted(this,"Це основа для перевірки площі, трасування і подальшого кошторису."));

        section(settingsCard,"Тип об'єкта",null);
        objectType=new ChoiceStrip(this);
        objectType.setItems(keysObject(),labelsObject(),project.profile.objectType.name());
        objectType.setListener(k->{ project.profile.objectType=ProjectProfile.ObjectType.valueOf(k); refreshName(); });
        settingsCard.addView(objectType);

        section(settingsCard,"Контрольна площа","Це число допомагає зрозуміти: скан зійшовся чи частина квартири загубилась.");
        area=new NumberStepper(this,"м²"); area.configure(0,10000,1,1); area.setValue(project.approximateAreaM2);
        settingsCard.addView(area);

        areaReference=new ChoiceStrip(this);
        areaReference.setItems(new String[]{"NET_FLOOR","DOCUMENT_TOTAL","UNKNOWN"},
                new String[]{"Чиста підлога","За документами","Не знаю"},
                project.profile.areaReference.name());
        areaReference.setListener(k->{
            project.profile.areaReference=ProjectProfile.AreaReference.valueOf(k);
            project.profile.applyReferenceDefaults();
        });
        settingsCard.addView(areaReference,Ui.lpMatchWrap(this,8));

        areaAccuracy=new ChoiceStrip(this);
        areaAccuracy.setItems(new String[]{"EXACT","APPROXIMATE","UNKNOWN"},
                new String[]{"Точна","Приблизна","Не знаю"},
                project.profile.areaAccuracy.name());
        areaAccuracy.setListener(k->{
            project.profile.areaAccuracy=ProjectProfile.AreaAccuracy.valueOf(k);
            project.profile.applyReferenceDefaults();
        });
        settingsCard.addView(areaAccuracy,Ui.lpMatchWrap(this,7));
        settingsCard.addView(Ui.muted(this,"Для приблизної або документної площі програма автоматично допускає більшу похибку, ніж для точної чистої площі."),Ui.lpMatchWrap(this,5));

        section(settingsCard,"Кімнати","Якщо ще не знаєш — залиш 0. Це не блокує роботу.");
        rooms=new NumberStepper(this,"кімн."); rooms.configure(0,30,1,0); rooms.setValue(project.roomCount);
        settingsCard.addView(rooms);

        section(settingsCard,"Матеріал стін",null);
        wallMaterial=new ChoiceStrip(this);
        wallMaterial.setItems(keysWall(),labelsWall(),project.profile.wallMaterial.name());
        settingsCard.addView(wallMaterial);

        section(settingsCard,"Живлення",null);
        phases=new ChoiceStrip(this);
        phases.setItems(new String[]{"1","3"},new String[]{"1 фаза","3 фази"},String.valueOf(project.settings.phases));
        settingsCard.addView(phases);

        section(settingsCard,"Орієнтовно груп","Можеш лишити 0, а потім уже вручну чи з підказок скоригувати.");
        groups=new NumberStepper(this,"груп"); groups.configure(0,80,1,0); groups.setValue(project.settings.plannedGroups);
        settingsCard.addView(groups);
        root.addView(settingsCard, Ui.lpMatchWrap(this,16));

        LinearLayout routingCard = Ui.card(this);
        routingCard.addView(Ui.title(this,"Маршрути і монтаж",20));
        routingCard.addView(Ui.muted(this,"Ці налаштування впливають на підрахунок кабелю, проходок, кріплення і спільних коридорів."));

        section(routingCard,"Прокладання",null);
        wiring=new ChoiceStrip(this);
        wiring.setItems(
                new String[]{"CEILING_VERTICAL_DROPS","WALLS","FLOOR","COMBINED"},
                new String[]{"Стеля ↓","По стінах","По підлозі","Комбіновано"},
                project.settings.wiringMethod.name());
        routingCard.addView(wiring);

        section(routingCard,"З'єднання",null);
        junction=new ChoiceStrip(this);
        junction.setItems(
                new String[]{"DEEP_SOCKET_BOXES","JUNCTION_BOXES","LOOP","HOME_RUN","COMBINED"},
                new String[]{"У підрозетниках","Коробки","Шлейф","Кожна в щит","Комбіновано"},
                project.settings.junctionMethod.name());
        routingCard.addView(junction);

        section(routingCard,"Запас кабелю",null);
        reserve=new ChoiceStrip(this);
        reserve.setItems(new String[]{"5","10","15","20"},new String[]{"5%","10%","15%","20%"},
                String.valueOf((int)Math.round(project.settings.cableReservePercent)));
        routingCard.addView(reserve);

        section(routingCard,"Захист / спосіб укладки",null);
        protection=new ChoiceStrip(this);
        protection.setItems(
                new String[]{"NONE","CORRUGATED","TRAY","MIXED"},
                new String[]{"Без гофри","Гофра","Лоток","Змішано"},
                project.settings.cableProtection.name());
        routingCard.addView(protection);

        section(routingCard,"Прохід через стіни",null);
        passage=new ChoiceStrip(this);
        passage.setItems(
                new String[]{"AUTO","ABOVE_DOOR","DIRECT_WALL"},
                new String[]{"Авто","Над дверима","Пряма проходка"},
                project.settings.wallPassageMode.name());
        routingCard.addView(passage);

        section(routingCard,"Монтажні запаси","Фіксовані запаси додаються окремо від загального % запасу кабелю.");
        panelReserve=new NumberStepper(this,"м у щиті");
        panelReserve.configure(0,5,0.25,2); panelReserve.setValue(project.settings.panelReserveM);
        routingCard.addView(panelReserve);

        endpointReserve=new NumberStepper(this,"м у точці");
        endpointReserve.configure(0,1.5,0.05,2); endpointReserve.setValue(project.settings.endpointReserveM);
        routingCard.addView(endpointReserve,Ui.lpMatchWrap(this,6));

        passageReserve=new NumberStepper(this,"м на проходку");
        passageReserve.configure(0,1.0,0.05,2); passageReserve.setValue(project.settings.passageReserveM);
        routingCard.addView(passageReserve,Ui.lpMatchWrap(this,6));

        fastenerSpacing=new NumberStepper(this,"м між кріпленням");
        fastenerSpacing.configure(0.15,1.0,0.05,2); fastenerSpacing.setValue(project.settings.fastenerSpacingM);
        routingCard.addView(fastenerSpacing,Ui.lpMatchWrap(this,6));

        section(routingCard,"Спільні пучки / коридори","Якщо кілька груп ідуть однаково, програма може рахувати спільний коридор окремо від окремих кінцевих відгалужень.");
        bundle=new ChoiceStrip(this);
        bundle.setItems(new String[]{"YES","NO"},new String[]{"Об'єднувати","Не об'єднувати"},
                project.settings.bundleSharedSegments?"YES":"NO");
        routingCard.addView(bundle);

        maxBundle=new NumberStepper(this,"каб. максимум у пучку");
        maxBundle.configure(1,40,1,0); maxBundle.setValue(project.settings.maxBundleCables);
        routingCard.addView(maxBundle,Ui.lpMatchWrap(this,6));
        root.addView(routingCard, Ui.lpMatchWrap(this,16));

        root.addView(Ui.title(this,"Маршрут роботи",20),Ui.lpMatchWrap(this,18));
        root.addView(Ui.muted(this,"Тут усе вже поділено по етапах, щоб було легше зрозуміти, що для чого служить."));

        Button smart=Ui.button(this,"Почати Smart Scan");
        smart.setOnClickListener(v->{saveProject();open(ArCoreScanActivity.class);});
        root.addView(actionBlock("СКАН","1. Зібрати приміщення","Проходиш квартиру телефоном, збираєш 3D, depth і базову геометрію.",smart),Ui.lpMatchWrap(this,12));

        Button areaCheck=Ui.secondaryButton(this,"Перевірка площі / де втрачено");
        areaCheck.setOnClickListener(v->{saveProject();open(AreaCheckActivity.class);});
        root.addView(actionBlock("КОНТРОЛЬ","2. Перевірити, чи зійшлася площа","Після скану програма покаже, де може бути втрачений метраж або яка зона недосканована.",areaCheck),Ui.lpMatchWrap(this,8));

        Button apartment=Ui.secondaryButton(this,"Єдиний Multi-room план квартири");
        apartment.setOnClickListener(v->{saveProject();open(ApartmentPlanActivity.class);});
        root.addView(actionBlock("ПЛАН","3. Побудувати квартиру","Один спільний план: кімнати, дверні проходи, вікна, колони, ніші та цілісна геометрія.",apartment),Ui.lpMatchWrap(this,8));

        Button plan=Ui.secondaryButton(this,"2D план + електроточки");
        plan.setOnClickListener(v->{saveProject();buildOrOpenPlan();});
        root.addView(actionBlock("2D","4. Швидке 2D редагування","Якщо треба, відкриваєш 2D-план та коригуєш логіку розміщення точок і зон.",plan),Ui.lpMatchWrap(this,8));

        Button editApartment=Ui.secondaryButton(this,"Редактор квартири + електрика");
        editApartment.setOnClickListener(v->{saveProject();open(ApartmentEditorActivity.class);});
        root.addView(actionBlock("ЕЛЕКТРИКА","5. Розетки, вимикачі, світло, щит","Основний робочий екран, де ти прив'язуєш електрику до стін і кімнат.",editApartment),Ui.lpMatchWrap(this,8));

        Button routing=Ui.secondaryButton(this,"Траси кабелю / метраж");
        routing.setOnClickListener(v->{saveProject();open(RoutingActivity.class);});
        root.addView(actionBlock("ТРАСИ","6. Порахувати кабель і монтаж","Програма будує маршрути, проходки, кріплення та монтажні коридори.",routing),Ui.lpMatchWrap(this,8));

        Button estimate=Ui.button(this,"Кошторис / склад / закупівля");
        estimate.setOnClickListener(v->{saveProject();open(EstimateActivity.class);});
        root.addView(actionBlock("КОШТОРИС","7. Порахувати гроші й закупівлю","Технічні кількості → склад → що докупити → ціна → націнка → роботи → сума клієнту.",estimate),Ui.lpMatchWrap(this,8));

        Button field=Ui.button(this,"Монтажна карта / відрізки / етикетки");
        field.setOnClickListener(v->{saveProject();open(FieldWorkActivity.class);});
        root.addView(actionBlock("FIELD","8. Підготувати кабель до монтажу","Кожен фізичний відрізок: код, звідки → куди, CUT-довжина, дві етикетки A/B, стан нарізки та монтажу.",field),Ui.lpMatchWrap(this,8));

        Button revision=Ui.secondaryButton(this,"Версії V1 / V2 / зміни клієнта");
        revision.setOnClickListener(v->{saveProject();open(RevisionActivity.class);});
        root.addView(actionBlock("REVISION","9. Зафіксувати зміни","Перед зміною клієнта або монтажною правкою збережи FULL snapshot і побач дельту по точках, кабелю та кошторису.",revision),Ui.lpMatchWrap(this,8));

        Button transfer=Ui.secondaryButton(this,"Передати на планшет / другий телефон");
        transfer.setOnClickListener(v->{saveProject();open(ProjectTransferActivity.class);});
        root.addView(actionBlock("TRANSFER","10. Перекинути весь об'єкт","LIGHT — швидко для роботи; FULL — разом із 3D point cloud. Отриманий .elektoryk відкривається назад у програмі.",transfer),Ui.lpMatchWrap(this,8));

        Button accuracy=Ui.secondaryButton(this,"Точність / лазерні контрольні заміри");
        accuracy.setOnClickListener(v->{saveProject();open(AccuracyActivity.class);});
        root.addView(actionBlock("ТОЧНІСТЬ","11. Підтвердити точність","Задаєш контрольні лазерні заміри і бачиш, наскільки скан збігається з реальністю.",accuracy),Ui.lpMatchWrap(this,8));

        Button electricalCheck=Ui.secondaryButton(this,"Перевірка електроплану");
        electricalCheck.setOnClickListener(v->{saveProject();open(ElectricalCheckActivity.class);});
        root.addView(actionBlock("QA","12. Перевірити логіку електроплану","Швидко бачиш точки без групи, дублікати, підозрілі місця та інші технічні проблеми.",electricalCheck),Ui.lpMatchWrap(this,8));

        Button qualityHub=Ui.button(this,"Quality Hub / commissioning");
        qualityHub.setOnClickListener(v->{saveProject();open(QualityHubActivity.class);});
        root.addView(actionBlock("QUALITY","13. Фінальна технічна перевірка","В одному місці: точність, QA електроплану, підтвердження захисту та фактичні вимірювання після монтажу.",qualityHub),Ui.lpMatchWrap(this,8));

        Button evidence=Ui.secondaryButton(this,"Фото прихованих робіт / as-built");
        evidence.setOnClickListener(v->{saveProject();open(EvidenceActivity.class);});
        root.addView(actionBlock("AS-BUILT","14. Фото фактичного монтажу","Збережи приховані траси, щит, тести й готовий результат. FULL пакет переносить ці фото разом з проєктом.",evidence),Ui.lpMatchWrap(this,8));

        Button client=Ui.button(this,"Презентація клієнту");
        client.setOnClickListener(v->{saveProject();open(ClientPresentationActivity.class);});
        root.addView(actionBlock("КЛІЄНТ","15. Показати результат красиво","План, розгортки стін, PNG та підсумок — щоб клієнт одразу зрозумів, що і де буде.",client),Ui.lpMatchWrap(this,8));

        Button approval=Ui.secondaryButton(this,"Погодження клієнта / підпис");
        approval.setOnClickListener(v->{saveProject();open(ClientApprovalActivity.class);});
        root.addView(actionBlock("APPROVAL","16. Зафіксувати погодження","Внутрішнє підтвердження плану, кошторису або приймання робіт з прив'язкою до останньої ревізії.",approval),Ui.lpMatchWrap(this,8));

        Button handover=Ui.button(this,"VIP здача готового об'єкта");
        handover.setOnClickListener(v->{saveProject();open(HandoverActivity.class);});
        root.addView(actionBlock("VIP","17. Передати об'єкт як продукт","Паспорт об'єкта, підтверджені групи, кабельна карта, точність, монтажний статус і FULL as-built резервна копія.",handover),Ui.lpMatchWrap(this,8));

        Button review=Ui.secondaryButton(this,"RAW / CLEAN та сумнівні об'єкти");
        review.setOnClickListener(v->{saveProject();open(ReviewActivity.class);});
        root.addView(actionBlock("ЧИСТКА","Інструмент: прибрати шум і сміття","Контроль сумнівних зон, сирого point cloud і очищеної моделі.",review),Ui.lpMatchWrap(this,8));

        Button more=Ui.secondaryButton(this,"Інструменти сканера");
        more.setOnClickListener(v->{saveProject();open(DeviceProfileActivity.class);});
        root.addView(actionBlock("ПРИСТРІЙ","Інструмент: можливості телефона","Перевіряєш, що саме вміє S22 Ultra або інший пристрій: depth, ARCore, fallback і т.д.",more),Ui.lpMatchWrap(this,8));
        root.addView(Ui.bottomNav(this,project.id,"HOME"),Ui.lpMatchWrap(this,18));

        setContentView(scroll);
    }

    private String trim(double v){
        if(Math.abs(v-Math.round(v))<0.01) return String.valueOf((int)Math.round(v));
        return String.format(java.util.Locale.US,"%.1f",v);
    }

    private void refreshName(){ generatedName.setText(project.displayName()); }

    private void buildOrOpenPlan(){
        FloorPlanRepository pr=new FloorPlanRepository(this);
        FloorPlan existing=pr.load(project.id);
        if(existing==null || existing.rooms.isEmpty()){
            SceneSnapshot scene=new SceneRepository(this).loadScene(project.id);
            if(scene!=null && scene.roomBounds!=null && scene.roomBounds.isUsable()){
                pr.save(new AutoFloorPlanBuilder().build(scene));
            }
        }
        open(PlanEditorActivity.class);
    }

    private void open(Class<?> c){Intent i=new Intent(this,c);i.putExtra("projectId",project.id);startActivity(i);}

    private void saveProject(){
        try { project.profile.objectType=ProjectProfile.ObjectType.valueOf(objectType.getSelectedKey()); } catch(Exception ignored){}
        try { project.profile.areaReference=ProjectProfile.AreaReference.valueOf(areaReference.getSelectedKey()); } catch(Exception ignored){}
        try { project.profile.areaAccuracy=ProjectProfile.AreaAccuracy.valueOf(areaAccuracy.getSelectedKey()); } catch(Exception ignored){}
        try { project.profile.wallMaterial=ProjectProfile.WallMaterial.valueOf(wallMaterial.getSelectedKey()); } catch(Exception ignored){}
        project.profile.applyReferenceDefaults();
        project.approximateAreaM2=area.getValue();
        project.roomCount=rooms.getIntValue();
        try { project.settings.phases=Integer.parseInt(phases.getSelectedKey()); } catch(Exception ignored){}
        project.settings.plannedGroups=groups.getIntValue();
        try { project.settings.wiringMethod=ProjectSettings.WiringMethod.valueOf(wiring.getSelectedKey()); } catch(Exception ignored){}
        try { project.settings.junctionMethod=ProjectSettings.JunctionMethod.valueOf(junction.getSelectedKey()); } catch(Exception ignored){}
        try { project.settings.cableReservePercent=Double.parseDouble(reserve.getSelectedKey()); } catch(Exception ignored){}
        try { project.settings.cableProtection=ProjectSettings.CableProtection.valueOf(protection.getSelectedKey()); } catch(Exception ignored){}
        try { project.settings.wallPassageMode=ProjectSettings.WallPassageMode.valueOf(passage.getSelectedKey()); } catch(Exception ignored){}
        project.settings.panelReserveM=panelReserve.getValue();
        project.settings.endpointReserveM=endpointReserve.getValue();
        project.settings.passageReserveM=passageReserve.getValue();
        project.settings.fastenerSpacingM=fastenerSpacing.getValue();
        project.settings.bundleSharedSegments="YES".equals(bundle.getSelectedKey());
        project.settings.maxBundleCables=maxBundle.getIntValue();
        if(project.name==null || "Новий об'єкт".equals(project.name)) project.name="";
        repo.save(project);
        refreshName();
    }

    private String[] keysObject(){ProjectProfile.ObjectType[] a=ProjectProfile.ObjectType.values();String[] r=new String[a.length];for(int i=0;i<a.length;i++)r[i]=a[i].name();return r;}
    private String[] labelsObject(){ProjectProfile.ObjectType[] a=ProjectProfile.ObjectType.values();String[] r=new String[a.length];for(int i=0;i<a.length;i++)r[i]=a[i].label;return r;}
    private String[] keysWall(){ProjectProfile.WallMaterial[] a=ProjectProfile.WallMaterial.values();String[] r=new String[a.length];for(int i=0;i<a.length;i++)r[i]=a[i].name();return r;}
    private String[] labelsWall(){ProjectProfile.WallMaterial[] a=ProjectProfile.WallMaterial.values();String[] r=new String[a.length];for(int i=0;i<a.length;i++)r[i]=a[i].label;return r;}

    @Override protected void onPause(){super.onPause();if(project!=null&&area!=null)saveProject();}
}
