package com.elektoryk.scan.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Numeric-only input with +/- controls for fast field use. */
public class NumberStepper extends LinearLayout {
    private final EditText valueView;
    private double min = 0, max = 999, step = 1, value = 0;
    private int decimals = 0;

    public NumberStepper(Context c, String unit) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        Button minus = smallButton("−");
        Button plus = smallButton("+");
        valueView = new EditText(c);
        valueView.setSingleLine(true);
        valueView.setGravity(Gravity.CENTER);
        valueView.setTextColor(Color.WHITE);
        valueView.setTextSize(19);
        valueView.setSelectAllOnFocus(true);
        valueView.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(23,23,27));
        bg.setCornerRadius(Ui.dp(c,12));
        bg.setStroke(Ui.dp(c,1), Color.rgb(57,57,66));
        valueView.setBackground(bg);
        valueView.setPadding(Ui.dp(c,10), Ui.dp(c,8), Ui.dp(c,10), Ui.dp(c,8));

        TextView unitView = Ui.muted(c, unit == null ? "" : unit);
        unitView.setPadding(Ui.dp(c,8),0,0,0);

        addView(minus, new LinearLayout.LayoutParams(Ui.dp(c,48), Ui.dp(c,44)));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(Ui.dp(c,105), Ui.dp(c,48));
        vp.leftMargin=Ui.dp(c,8); vp.rightMargin=Ui.dp(c,8);
        addView(valueView, vp);
        addView(plus, new LinearLayout.LayoutParams(Ui.dp(c,48), Ui.dp(c,44)));
        addView(unitView);

        minus.setOnClickListener(v -> { commitText(); setValue(value-step); });
        plus.setOnClickListener(v -> { commitText(); setValue(value+step); });
        valueView.setOnFocusChangeListener((v, has) -> { if (!has) commitText(); });
    }

    private Button smallButton(String t) {
        Button b = new Button(getContext());
        b.setText(t); b.setTextSize(20); b.setAllCaps(false);
        b.setTextColor(Color.rgb(11,11,13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColors(new int[]{Color.rgb(244,198,80),Color.rgb(190,132,35)});
        bg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        bg.setCornerRadius(Ui.dp(getContext(),12));
        b.setBackground(bg);
        return b;
    }

    public void configure(double min, double max, double step, int decimals) {
        this.min=min; this.max=max; this.step=step; this.decimals=Math.max(0,decimals);
        setValue(value);
    }

    public void setValue(double v) {
        value=Math.max(min,Math.min(max,v));
        valueView.setText(decimals==0
                ? String.valueOf((int)Math.round(value))
                : String.format(Locale.US,"%."+decimals+"f",value));
    }

    public double getValue() { commitText(); return value; }
    public int getIntValue() { return (int)Math.round(getValue()); }

    private void commitText() {
        try {
            String s=valueView.getText().toString().trim().replace(',','.');
            if (!s.isEmpty()) value=Math.max(min,Math.min(max,Double.parseDouble(s)));
        } catch(Exception ignored) {}
        setValue(value);
    }
}
