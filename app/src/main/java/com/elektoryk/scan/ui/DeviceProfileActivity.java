package com.elektoryk.scan.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.elektoryk.scan.device.DeviceCapabilities;
import com.elektoryk.scan.device.DeviceCapabilityDetector;
import com.elektoryk.scan.device.ScannerProfile;
import com.elektoryk.scan.device.RuntimeProfile;

public class DeviceProfileActivity extends Activity {
    private String projectId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectId=getIntent().getStringExtra("projectId");

        DeviceCapabilities c = new DeviceCapabilityDetector().detect(this);
        ScannerProfile p = ScannerProfile.choose(c);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(11,11,13));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,18), Ui.dp(this,18), Ui.dp(this,18), Ui.dp(this,30));
        scroll.addView(root);

        root.addView(Ui.brandHeader(this,"ELEKTORYK • Пристрій","Що саме цей телефон дає Smart Scan.","ПРИСТРІЙ",true));
        root.addView(Ui.screenIntro(this,"СКАНЕР",""+c.manufacturer+" "+c.model,"Android "+c.androidVersion+" • тут видно, які сенсори та AR/Depth можливості реально доступні."),Ui.lpMatchWrap(this,10));

        LinearLayout card = Ui.card(this);
        card.addView(Ui.title(this, p.title, 19));
        card.addView(Ui.muted(this, p.description));
        root.addView(card, Ui.lpMatchWrap(this, 16));

        root.addView(row("Камера", c.hasCamera));
        root.addView(row("Гіроскоп", c.hasGyroscope));
        root.addView(row("Акселерометр", c.hasAccelerometer));
        root.addView(row("Rotation Vector", c.hasRotationVector));
        root.addView(row("Camera2 DEPTH_OUTPUT", c.hasCamera2DepthOutput));
        root.addView(row("ARCore встановлено", c.hasArCorePackage));
        root.addView(row("Google Play Services", c.hasGooglePlayServices));

        root.addView(Ui.title(this, "Профіль продуктивності", 18), Ui.lpMatchWrap(this, 16));
        RuntimeProfile runtime=RuntimeProfile.load(this);
        ChoiceStrip perf=new ChoiceStrip(this);
        perf.setItems(new String[]{"STABLE","BALANCED","MAX_QUALITY"},
                new String[]{"Стабільний","Збалансований","Макс. якість"},runtime.mode.name());
        perf.setListener(k->{try{RuntimeProfile.save(this,RuntimeProfile.Mode.valueOf(k));}catch(Exception ignored){}});
        root.addView(perf);
        root.addView(Ui.muted(this,"Стабільний — менше точок для аналізу, менший ризик OOM. Збалансований — рекомендовано. Макс. якість — повний point cloud для потужного телефона."),Ui.lpMatchWrap(this,6));
        long maxMem=Runtime.getRuntime().maxMemory()/1024/1024;
        root.addView(Ui.muted(this,"Ліміт Java memory: ≈ "+maxMem+" MB • поточний профіль: "+runtime.label()+" • аналіз до "+runtime.maxAnalysisPoints+" точок."),Ui.lpMatchWrap(this,5));

        root.addView(Ui.title(this, "Camera2 діагностика", 18), Ui.lpMatchWrap(this, 16));
        root.addView(Ui.muted(this,
                c.cameraHardwareSummary.isEmpty() ? "Немає даних" : c.cameraHardwareSummary));

        root.addView(Ui.title(this, "Важливо", 18), Ui.lpMatchWrap(this, 16));
        root.addView(Ui.muted(this,
                "Ця версія визначає апаратні можливості без зовнішніх бібліотек. "
                        + "Фактичну підтримку ARCore Depth API перевірятиме окремий ARCore-модуль."));

        root.addView(Ui.bottomNav(this,projectId,"SCAN"),Ui.lpMatchWrap(this,18));
        setContentView(scroll);
    }

    private TextView row(String label, boolean yes) {
        TextView t = Ui.title(this, (yes ? "✓  " : "—  ") + label, 16);
        if (!yes) t.setTextColor(Color.rgb(164,164,173));
        return t;
    }
}
