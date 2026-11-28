package com.elektoryk.scan.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/** Fast one-tap selector used instead of free-text inputs. */
public class ChoiceStrip extends HorizontalScrollView {
    public interface Listener { void onChanged(String key); }

    private final LinearLayout row;
    private final List<Button> buttons = new ArrayList<>();
    private String[] keys = new String[0];
    private String selected = "";
    private Listener listener;

    public ChoiceStrip(Context context) {
        super(context);
        setHorizontalScrollBarEnabled(false);
        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    public void setItems(String[] keys, String[] labels, String selectedKey) {
        this.keys = keys == null ? new String[0] : keys;
        selected = selectedKey == null ? "" : selectedKey;
        buttons.clear(); row.removeAllViews();
        if (labels == null) labels = new String[0];
        int n = Math.min(this.keys.length, labels.length);
        for (int i = 0; i < n; i++) {
            final String key = this.keys[i];
            Button b = new Button(getContext());
            b.setText(labels[i]);
            b.setAllCaps(false);
            b.setTextSize(14);
            b.setMinHeight(0); b.setMinimumHeight(0);
            b.setMinWidth(0); b.setMinimumWidth(0);
            b.setPadding(Ui.dp(getContext(),14), Ui.dp(getContext(),9), Ui.dp(getContext(),14), Ui.dp(getContext(),9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(getContext(),7);
            row.addView(b, lp);
            buttons.add(b);
            b.setOnClickListener(v -> {
                selected = key;
                refresh();
                if (listener != null) listener.onChanged(selected);
            });
        }
        refresh();
    }

    public void setListener(Listener l) { listener = l; }
    public String getSelectedKey() { return selected; }

    public void setSelectedKey(String key) {
        selected = key == null ? "" : key;
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            boolean active = i < keys.length && keys[i].equals(selected);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.dp(getContext(), 14));
            if(active){
                bg.setColors(new int[]{Color.rgb(244,198,80),Color.rgb(190,132,35)});
                bg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            }else{
                bg.setColor(Color.rgb(27,27,32));
            }
            if (!active) bg.setStroke(Ui.dp(getContext(),1), Color.rgb(58,58,66));
            else bg.setStroke(Ui.dp(getContext(),1), Color.argb(130,255,220,120));
            b.setBackground(bg);
            b.setTextColor(active ? Color.rgb(11,11,13) : Color.rgb(230,230,233));
        }
    }
}
