package com.elektoryk.scan.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.elektoryk.scan.R;

public final class Ui {
    private Ui() {}

    public static final int BG = Color.rgb(11,11,13);
    public static final int CARD = Color.rgb(23,23,27);
    public static final int CARD_SOFT = Color.rgb(18,18,22);
    public static final int GOLD = Color.rgb(226,177,54);
    public static final int GOLD_SOFT = Color.rgb(190,132,35);
    public static final int TEXT = Color.rgb(244,244,245);
    public static final int MUTED = Color.rgb(164,164,173);
    public static final int STROKE = Color.rgb(58,58,66);

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /**
     * Float overload for metric / procedural drawing code.
     * Keeps all pixel conversion in one place and avoids lossy-conversion
     * compile errors when a calculated dp value is fractional.
     */
    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static TextView title(Context c, String text, float sp) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextColor(TEXT);
        v.setTextSize(sp);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(c, 6), 0, dp(c, 6));
        return v;
    }

    public static TextView muted(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextColor(MUTED);
        v.setTextSize(14);
        return v;
    }

    public static TextView chip(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextColor(GOLD);
        v.setTextSize(12);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(dp(c,10), dp(c,5), dp(c,10), dp(c,5));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(35,226,177,54));
        bg.setStroke(dp(c,1), Color.argb(85,226,177,54));
        bg.setCornerRadius(dp(c,999));
        v.setBackground(bg);
        return v;
    }

    public static Button button(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextColor(BG);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(244,198,80), Color.rgb(190,132,35)});
        bg.setCornerRadius(dp(c, 14));
        b.setBackground(bg);
        b.setPadding(dp(c,16), dp(c,12), dp(c,16), dp(c,12));
        return b;
    }

    public static Button secondaryButton(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextColor(TEXT);
        b.setTextSize(14);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD_SOFT);
        bg.setStroke(dp(c,1), STROKE);
        bg.setCornerRadius(dp(c, 14));
        b.setBackground(bg);
        b.setPadding(dp(c,16), dp(c,12), dp(c,16), dp(c,12));
        return b;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c,16), dp(c,14), dp(c,16), dp(c,14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(c,18));
        bg.setStroke(dp(c,1), STROKE);
        l.setBackground(bg);
        return l;
    }

    public static LinearLayout softCard(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c,14), dp(c,12), dp(c,14), dp(c,12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD_SOFT);
        bg.setCornerRadius(dp(c,16));
        bg.setStroke(dp(c,1), Color.rgb(42,42,48));
        l.setBackground(bg);
        return l;
    }

    public static LinearLayout brandHeader(Context c, String title, String subtitle, String badge, boolean compact) {
        LinearLayout wrap = card(c);
        wrap.setPadding(dp(c,16), dp(c,16), dp(c,16), dp(c,16));
        GradientDrawable headerBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(25,23,19), Color.rgb(16,16,19)});
        headerBg.setStroke(dp(c,1), Color.argb(110,226,177,54));
        headerBg.setCornerRadius(dp(c,18));
        wrap.setBackground(headerBg);

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(c);
        icon.setImageResource(R.drawable.brand_icon);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(c, compact ? 44 : 56), dp(c, compact ? 44 : 56));
        top.addView(icon, ip);

        LinearLayout textCol = new LinearLayout(c);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(c,12);

        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(compact ? 20 : 24);
        textCol.addView(t);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = muted(c, subtitle);
            s.setTextSize(compact ? 12 : 14);
            textCol.addView(s);
        }
        top.addView(textCol, tp);

        if (badge != null && !badge.isEmpty()) {
            top.addView(chip(c, badge));
        }
        wrap.addView(top);

        if (!compact) {
            ImageView logo = new ImageView(c);
            logo.setImageResource(R.drawable.brand_wordmark);
            logo.setAdjustViewBounds(true);
            LinearLayout.LayoutParams lp = lpMatchWrap(c, 12);
            wrap.addView(logo, lp);
        }
        return wrap;
    }

    public static LinearLayout statCard(Context c, String value, String label) {
        LinearLayout card = softCard(c);
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextColor(GOLD);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextSize(20);
        card.addView(v);
        TextView l = muted(c, label);
        l.setTextSize(12);
        card.addView(l);
        return card;
    }

    public static LinearLayout actionCard(Context c, String title, String desc, String tag) {
        LinearLayout card = softCard(c);
        if (tag != null && !tag.isEmpty()) card.addView(chip(c, tag));
        TextView t = title(c, title, 17);
        card.addView(t);
        TextView d = muted(c, desc);
        d.setTextSize(13);
        card.addView(d);
        return card;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Color.rgb(36,36,42));
        return v;
    }

    public static LinearLayout.LayoutParams lpMatchWrap(Context c, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, top);
        return p;
    }

    public static LinearLayout.LayoutParams lpWeight(Context c, float weight, int rightMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        p.rightMargin = dp(c, rightMargin);
        return p;
    }
    public static LinearLayout bottomNav(Activity a, String projectId, String active) {
        LinearLayout nav = new LinearLayout(a);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(a,6), dp(a,6), dp(a,6), dp(a,6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(15,15,18));
        bg.setStroke(dp(a,1), Color.rgb(48,48,56));
        bg.setCornerRadius(dp(a,18));
        nav.setBackground(bg);

        addNavItem(a, nav, "⌂ Головна", "HOME", active, projectId, MainActivity.class);
        addNavItem(a, nav, "◉ Скан", "SCAN", active, projectId, ArCoreScanActivity.class);
        addNavItem(a, nav, "▦ План", "PLAN", active, projectId, ApartmentPlanActivity.class);
        addNavItem(a, nav, "⚡ Електрика", "ELECTRIC", active, projectId, ApartmentEditorActivity.class);
        addNavItem(a, nav, "◈ Клієнту", "CLIENT", active, projectId, ClientPresentationActivity.class);
        return nav;
    }

    private static void addNavItem(Activity a, LinearLayout nav, String label, String key,
                                   String active, String projectId, Class<?> target) {
        TextView item = new TextView(a);
        item.setText(label);
        item.setGravity(Gravity.CENTER);
        item.setTextSize(11);
        item.setTypeface(Typeface.DEFAULT_BOLD);
        boolean selected = key.equals(active);
        item.setTextColor(selected ? GOLD : MUTED);
        item.setPadding(dp(a,6), dp(a,10), dp(a,6), dp(a,10));
        if (selected) {
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(Color.argb(34,226,177,54));
            pill.setCornerRadius(dp(a,12));
            item.setBackground(pill);
        }
        item.setOnClickListener(v -> {
            if (target == MainActivity.class) {
                Intent i = new Intent(a, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                a.startActivity(i);
                return;
            }
            if (projectId == null || projectId.isEmpty()) return;
            if (a.getClass() == target) return;
            Intent i = new Intent(a, target);
            i.putExtra("projectId", projectId);
            a.startActivity(i);
        });
        nav.addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }

    public static LinearLayout screenIntro(Context c, String tag, String title, String desc) {
        LinearLayout card = softCard(c);
        card.addView(chip(c, tag));
        card.addView(title(c, title, 20), lpMatchWrap(c, 5));
        TextView d = muted(c, desc);
        d.setTextSize(13);
        card.addView(d, lpMatchWrap(c, 2));
        return card;
    }

}
