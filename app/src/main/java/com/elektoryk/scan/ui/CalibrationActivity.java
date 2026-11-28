package com.elektoryk.scan.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.elektoryk.scan.geometry.ScaleCalibration;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.storage.FloorPlanRepository;

import java.util.Locale;

public class CalibrationActivity extends Activity {
    private String projectId;
    private FloorPlanRepository repo;
    private FloorPlan plan;
    private EditText scanned;
    private EditText measured;
    private TextView result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectId = getIntent().getStringExtra("projectId");
        repo = new FloorPlanRepository(this);
        plan = repo.load(projectId);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,18), Ui.dp(this,18), Ui.dp(this,18), Ui.dp(this,24));
        root.setBackgroundColor(Color.rgb(11,11,13));

        root.addView(Ui.brandHeader(this,"ELEKTORYK • Калібрування","Швидка одноточкова корекція масштабу.","КАЛІБР",true));
        root.addView(Ui.screenIntro(this,"МАСШТАБ","Один контрольний замір","Виміряй одну надійну відстань лазером. Для серйозної перевірки точності краще використовуй новий екран з кількома контрольними замірами."),Ui.lpMatchWrap(this,10));

        scanned = input("Скан показав, м");
        measured = input("Реально виміряно, м");
        root.addView(scanned, Ui.lpMatchWrap(this, 16));
        root.addView(measured, Ui.lpMatchWrap(this, 8));

        if(plan==null){
            plan=new FloorPlan();
            plan.projectId=projectId;
        }
        result = Ui.muted(this, "Поточний масштаб ×"
                + String.format(Locale.US, "%.6f", plan.scaleCorrection));
        root.addView(result, Ui.lpMatchWrap(this, 12));

        Button apply = Ui.button(this, "Розрахувати і зберегти");
        apply.setOnClickListener(v -> calculate());
        root.addView(apply, Ui.lpMatchWrap(this, 14));
        root.addView(Ui.bottomNav(this,projectId,"PLAN"),Ui.lpMatchWrap(this,14));

        setContentView(root);
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(110,110,118));
        e.setTextColor(Color.WHITE);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setSingleLine(true);
        return e;
    }

    private void calculate() {
        double s = parse(scanned.getText().toString());
        double m = parse(measured.getText().toString());
        if (s <= 0 || m <= 0) {
            result.setText("Введи дві відстані більше нуля.");
            return;
        }
        double factor = ScaleCalibration.factor(s, m);
        plan.scaleCorrection *= factor;
        repo.save(plan);

        double errorPercent = (m - s) / s * 100.0;
        result.setText("Корекція цієї пари ×"
                + String.format(Locale.US, "%.6f", factor)
                + "\nВідхилення: " + String.format(Locale.US, "%+.2f%%", errorPercent)
                + "\nНовий масштаб проєкту ×"
                + String.format(Locale.US, "%.6f", plan.scaleCorrection));
    }

    private double parse(String s) {
        try { return Double.parseDouble(s.trim().replace(',', '.')); }
        catch (Exception e) { return 0; }
    }
}
