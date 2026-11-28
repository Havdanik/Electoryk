package com.elektoryk.scan.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.elektoryk.scan.model.ElectricalPoint;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.RoomRect;
import com.elektoryk.scan.model.Vec3;
import com.elektoryk.scan.storage.FloorPlanRepository;

import java.util.Locale;

public class PlanEditorActivity extends Activity {
    private FloorPlanRepository repo;
    private FloorPlan plan;
    private String projectId;

    private ChoiceStrip roomType;
    private NumberStepper width;
    private NumberStepper length;
    private NumberStepper height;
    private ChoiceStrip pointType;
    private TextView status;
    private PlanView planView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectId = getIntent().getStringExtra("projectId");
        repo = new FloorPlanRepository(this);
        plan = repo.load(projectId);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(11, 11, 13));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 28));
        scroll.addView(root);

        root.addView(Ui.brandHeader(this,"ELEKTORYK • 2D","Ручний fallback і швидка корекція плану.","2D",true));
        root.addView(Ui.screenIntro(this,"РЕДАКТОР","2D план / ручний fallback","Резерв без 3D та швидкий контроль автоматичного скану. Вибираєш тип точки і ставиш її прямо на план."),Ui.lpMatchWrap(this,10));

        RoomRect r = plan.rooms.isEmpty() ? null : plan.rooms.get(0);
        root.addView(Ui.title(this,"Тип кімнати",18),Ui.lpMatchWrap(this,12));
        roomType=new ChoiceStrip(this);
        roomType.setItems(
                new String[]{"LIVING","KITCHEN","BEDROOM","BATHROOM","CORRIDOR","WARDROBE","BALCONY","TECHNICAL","OTHER"},
                new String[]{"Вітальня","Кухня","Спальня","Санвузол","Коридор","Гардероб","Балкон","Технічна","Інша"},
                roomKey(r==null?"":r.name));
        root.addView(roomType);

        root.addView(Ui.title(this,"Розміри",18),Ui.lpMatchWrap(this,12));
        width=new NumberStepper(this,"м ширина"); width.configure(0.5,50,0.1,2); width.setValue(r==null?4.0:r.widthM);
        length=new NumberStepper(this,"м довжина"); length.configure(0.5,50,0.1,2); length.setValue(r==null?4.0:r.lengthM);
        height=new NumberStepper(this,"м висота"); height.configure(1.8,8,0.05,2); height.setValue(r==null?2.7:r.heightM);
        root.addView(width,Ui.lpMatchWrap(this,6));
        root.addView(length,Ui.lpMatchWrap(this,6));
        root.addView(height,Ui.lpMatchWrap(this,6));

        Button apply = Ui.button(this, "Застосувати геометрію кімнати");
        apply.setOnClickListener(v -> applyRoom());
        root.addView(apply, Ui.lpMatchWrap(this, 10));

        root.addView(Ui.title(this, "Електроточка", 18), Ui.lpMatchWrap(this, 14));

        pointType = new ChoiceStrip(this);
        pointType.setItems(
                new String[]{"SOCKET","SWITCH","LIGHT","PANEL","TV","ETHERNET","APPLIANCE","SENSOR","CAMERA","SMART_HOME"},
                new String[]{"Розетка","Вимикач","Світло","Щит","TV","LAN","Техніка","Датчик","Камера","Smart"},
                "SOCKET");
        root.addView(pointType, Ui.lpMatchWrap(this, 4));

        status = Ui.muted(this, "");
        root.addView(status, Ui.lpMatchWrap(this, 8));

        planView = new PlanView();
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 430));
        cp.topMargin = Ui.dp(this, 10);
        root.addView(planView, cp);

        Button undo = Ui.button(this, "↶ Забрати останню точку");
        undo.setOnClickListener(v -> {
            if (!plan.points.isEmpty()) {
                plan.points.remove(plan.points.size() - 1);
                saveAndRefresh();
            }
        });
        root.addView(undo, Ui.lpMatchWrap(this, 10));

        Button clear = Ui.button(this, "Очистити електроточки");
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Очистити точки?")
                .setMessage("Геометрія кімнати залишиться.")
                .setNegativeButton("Ні", null)
                .setPositiveButton("Так", (d, w) -> {
                    plan.points.clear();
                    saveAndRefresh();
                }).show());
        root.addView(clear, Ui.lpMatchWrap(this, 8));

        updateStatus();
        root.addView(Ui.bottomNav(this,projectId,"PLAN"),Ui.lpMatchWrap(this,18));
        setContentView(scroll);
    }

    private void applyRoom() {
        double w = width.getValue();
        double l = length.getValue();
        double h = height.getValue();

        if (w < 0.5 || l < 0.5 || h < 1.8) {
            status.setText("Перевір розміри: кімната або висота занадто мала.");
            return;
        }

        RoomRect r;
        if (plan.rooms.isEmpty()) {
            r = new RoomRect();
            plan.rooms.add(r);
        } else {
            r = plan.rooms.get(0);
        }
        r.name = roomLabel(roomType.getSelectedKey());
        r.widthM = w;
        r.lengthM = l;
        r.heightM = h;
        repo.save(plan);
        planView.invalidate();
        updateStatus();
    }

    private void addPoint(double xM, double zM) {
        if (plan.rooms.isEmpty()) {
            applyRoom();
            if (plan.rooms.isEmpty()) return;
        }
        RoomRect room = plan.rooms.get(0);
        ElectricalPoint p = new ElectricalPoint();
        p.roomId = room.id;
        try { p.type = ElectricalPoint.Type.valueOf(pointType.getSelectedKey()); }
        catch(Exception ignored) { p.type = ElectricalPoint.Type.SOCKET; }
        p.position = new Vec3(xM, defaultHeight(p.type), zM);
        p.heightFromFinishedFloorM = p.position.y;
        plan.points.add(p);
        saveAndRefresh();
    }

    private double defaultHeight(ElectricalPoint.Type type) {
        switch (type) {
            case SWITCH: return 0.9;
            case PANEL: return 1.5;
            case LIGHT: return 2.7;
            case CAMERA: return 2.5;
            default: return 0.3;
        }
    }

    private void saveAndRefresh() {
        repo.save(plan);
        planView.invalidate();
        updateStatus();
    }

    private void updateStatus() {
        int rooms = plan.rooms.size();
        int points = plan.points.size();
        status.setText("Кімнат: " + rooms + " • точок: " + points
                + " • масштаб ×" + String.format(Locale.US, "%.5f", plan.scaleCorrection));
    }

    private String roomKey(String name) {
        String n=name==null?"":name.toLowerCase(java.util.Locale.ROOT);
        if(n.contains("кух"))return "KITCHEN";
        if(n.contains("спаль"))return "BEDROOM";
        if(n.contains("сан")||n.contains("ван")||n.contains("душ"))return "BATHROOM";
        if(n.contains("корид")||n.contains("хол"))return "CORRIDOR";
        if(n.contains("гардер"))return "WARDROBE";
        if(n.contains("балкон")||n.contains("лодж"))return "BALCONY";
        if(n.contains("тех"))return "TECHNICAL";
        if(n.contains("віталь")||n.contains("зал"))return "LIVING";
        return "OTHER";
    }

    private String roomLabel(String key) {
        if("LIVING".equals(key))return "Вітальня";
        if("KITCHEN".equals(key))return "Кухня";
        if("BEDROOM".equals(key))return "Спальня";
        if("BATHROOM".equals(key))return "Санвузол";
        if("CORRIDOR".equals(key))return "Коридор";
        if("WARDROBE".equals(key))return "Гардероб";
        if("BALCONY".equals(key))return "Балкон";
        if("TECHNICAL".equals(key))return "Технічна";
        return "Кімната";
    }

    private String f(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { repo.save(plan); } catch (Exception ignored) {}
    }

    private class PlanView extends View {
        private final Paint wall = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint point = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RectF roomRectPx = new RectF();

        PlanView() {
            super(PlanEditorActivity.this);
            setBackgroundColor(Color.rgb(20, 20, 24));
            wall.setStyle(Paint.Style.STROKE);
            wall.setStrokeWidth(Ui.dp(PlanEditorActivity.this, 4));
            wall.setColor(Color.rgb(255, 196, 0));

            grid.setStyle(Paint.Style.STROKE);
            grid.setStrokeWidth(1f);
            grid.setColor(Color.rgb(55, 55, 62));

            point.setStyle(Paint.Style.FILL);
            point.setColor(Color.WHITE);

            text.setColor(Color.WHITE);
            text.setTextSize(Ui.dp(PlanEditorActivity.this, 12));
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (plan.rooms.isEmpty()) {
                c.drawText("Додай геометрію кімнати", Ui.dp(PlanEditorActivity.this, 20),
                        Ui.dp(PlanEditorActivity.this, 40), text);
                return;
            }

            RoomRect r = plan.rooms.get(0);
            float pad = Ui.dp(PlanEditorActivity.this, 32);
            float availableW = getWidth() - pad * 2;
            float availableH = getHeight() - pad * 2;

            double worldW = Math.max(0.1, r.widthM);
            double worldH = Math.max(0.1, r.lengthM);
            float scale = (float)Math.min(availableW / worldW, availableH / worldH);

            float rw = (float)(worldW * scale);
            float rh = (float)(worldH * scale);
            float left = (getWidth() - rw) / 2f;
            float top = (getHeight() - rh) / 2f;
            roomRectPx.set(left, top, left + rw, top + rh);

            drawGrid(c, r, scale);
            c.drawRect(roomRectPx, wall);
            c.drawText(r.name + "  " + f(r.widthM) + " × " + f(r.lengthM) + " м",
                    left, Math.max(text.getTextSize() + 4, top - 10), text);

            for (ElectricalPoint p : plan.points) {
                if (!r.id.equals(p.roomId)) continue;
                float px = left + (float)((p.position.x - r.xM) * scale);
                float py = top + (float)((p.position.z - r.zM) * scale);
                c.drawCircle(px, py, Ui.dp(PlanEditorActivity.this, 7), point);
                c.drawText(shortName(p.type), px + Ui.dp(PlanEditorActivity.this, 9),
                        py - Ui.dp(PlanEditorActivity.this, 4), text);
            }
        }

        private void drawGrid(Canvas c, RoomRect r, float scale) {
            if (scale < 10) return;
            float stepM = scale > 80 ? 0.5f : 1.0f;
            for (float x = stepM; x < r.widthM; x += stepM) {
                float px = roomRectPx.left + x * scale;
                c.drawLine(px, roomRectPx.top, px, roomRectPx.bottom, grid);
            }
            for (float z = stepM; z < r.lengthM; z += stepM) {
                float py = roomRectPx.top + z * scale;
                c.drawLine(roomRectPx.left, py, roomRectPx.right, py, grid);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP || plan.rooms.isEmpty()) return true;
            if (!roomRectPx.contains(event.getX(), event.getY())) return true;

            RoomRect r = plan.rooms.get(0);
            double nx = (event.getX() - roomRectPx.left) / roomRectPx.width();
            double nz = (event.getY() - roomRectPx.top) / roomRectPx.height();
            addPoint(r.xM + nx * r.widthM, r.zM + nz * r.lengthM);
            return true;
        }

        private String shortName(ElectricalPoint.Type t) {
            switch (t) {
                case SOCKET: return "R";
                case SWITCH: return "SW";
                case LIGHT: return "L";
                case PANEL: return "Щ";
                case ETHERNET: return "LAN";
                case APPLIANCE: return "A";
                case SENSOR: return "S";
                case CAMERA: return "CAM";
                case SMART_HOME: return "SMART";
                default: return "TV";
            }
        }
    }
}
