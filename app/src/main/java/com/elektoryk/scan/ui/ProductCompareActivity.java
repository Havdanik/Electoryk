package com.elektoryk.scan.ui;

import android.app.Activity;
import android.graphics.*;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

/** Client-facing visual comparison. Drawings explain the mounting principle, not a specific brand. */
public class ProductCompareActivity extends Activity {
    private ChoiceStrip category;
    private LinearLayout options;

    @Override protected void onCreate(Bundle b){super.onCreate(b);build();}

    private void build(){
        ScrollView s=new ScrollView(this);s.setBackgroundColor(Ui.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));s.addView(root);
        root.addView(Ui.brandHeader(this,"ELEKTORYK • Варіанти клієнту","Поясни різницю візуально, не технічними словами.","COMPARE",true));
        root.addView(Ui.screenIntro(this,"ВИГЛЯД","Схема, а не каталог бренду","Показується принцип: механізм і рамка, клавіші вимикача, тип світильника або розмір щита. Конкретну серію, колір і бренд погоджуєш окремо."),Ui.lpMatchWrap(this,10));
        category=new ChoiceStrip(this);category.setItems(new String[]{"SOCKET","SWITCH","LIGHT","PANEL"},new String[]{"Розетки","Вимикачі","Світло","Щит"},"SOCKET");category.setListener(k->renderOptions());root.addView(category,Ui.lpMatchWrap(this,8));
        options=new LinearLayout(this);options.setOrientation(LinearLayout.VERTICAL);root.addView(options,Ui.lpMatchWrap(this,8));setContentView(s);renderOptions();
    }

    private void renderOptions(){
        if(options==null)return;options.removeAllViews();String k=category.getSelectedKey();
        if("SWITCH".equals(k)){
            add("SWITCH_SINGLE","1 клавіша","Один контур світла з одного місця.");
            add("SWITCH_DOUBLE","2 клавіші","Два контури в одному механізмі — наприклад, основне світло + підсвітка.");
            add("SWITCH_PASS","Прохідний","Керування одним світлом з двох або більше місць; схема й кабель відрізняються.");
            add("SWITCH_DIMMER","Димер","Регулювання яскравості, якщо світильник/драйвер це підтримує.");
        } else if("LIGHT".equals(k)){
            add("LIGHT_SPOT","Точковий","Мінімальний видимий корпус; добре для рівномірного загального світла.");
            add("LIGHT_PENDANT","Підвісний","Помітний декоративний світильник над столом, островом або зоною.");
            add("LIGHT_SURFACE","Накладний","Корпус повністю видно; не потребує глибокої ніші.");
            add("LIGHT_TRACK","Трек","Кілька направлених світильників на одній шині.");
            add("LIGHT_SCONCE","Бра","Настінний світильник; точка живлення ставиться на стіні.");
        } else if("PANEL".equals(k)){
            add("PANEL_FLUSH_12","Вбудований 12М","Компактний, але запас модулів невеликий.");
            add("PANEL_FLUSH_24","Вбудований 24М","Універсальний варіант для невеликої або середньої квартири.");
            add("PANEL_FLUSH_36","Вбудований 36М","Більший запас під автомати, реле, smart-home та майбутні групи.");
            add("PANEL_SURFACE_24","Накладний 24М","Простіший монтаж там, де не хочуть робити нішу.");
        } else {
            add("SOCKET_FRAME_1","1 пост у рамці","Один окремий механізм у декоративній рамці.");
            add("SOCKET_FRAME_2","2 пости у рамці","Два незалежні механізми під однією рамкою — гнучкіше по функціях.");
            add("SOCKET_FRAME_3","3 пости у рамці","Можна комбінувати 230V, USB, LAN/TV залежно від серії.");
            add("SOCKET_DOUBLE_BLOCK","Подвійний механізм","Дві розетки в одному корпусі/механізмі; компактніше, але менше свободи комбінацій.");
        }
    }

    private void add(String id,String title,String desc){
        LinearLayout c=Ui.card(this);c.addView(Ui.chip(this,id));Preview v=new Preview(this,id);c.addView(v,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,150)));c.addView(Ui.title(this,title,18),Ui.lpMatchWrap(this,4));c.addView(Ui.muted(this,desc));options.addView(c,Ui.lpMatchWrap(this,8));
    }

    private static class Preview extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final String id;
        Preview(android.content.Context c,String id){super(c);this.id=id;setBackgroundColor(Color.rgb(18,18,22));}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2,cy=h/2;p.setStrokeWidth(5);p.setStyle(Paint.Style.STROKE);p.setColor(Ui.GOLD);
            if(id.startsWith("SOCKET")){drawSocket(c,cx,cy);return;}
            if(id.startsWith("SWITCH")){drawSwitch(c,cx,cy);return;}
            if(id.startsWith("PANEL")){drawPanel(c,cx,cy);return;}
            drawLight(c,cx,cy);
        }
        private void drawSocket(Canvas c,float cx,float cy){int n=id.contains("FRAME_3")?3:id.contains("FRAME_2")?2:id.contains("DOUBLE")?2:1;float bw=n*70+26,left=cx-bw/2;c.drawRoundRect(left,cy-45,left+bw,cy+45,16,16,p);for(int i=0;i<n;i++){float x=left+48+i*70;c.drawCircle(x,cy,24,p);p.setStrokeWidth(3);c.drawCircle(x-7,cy,3,p);c.drawCircle(x+7,cy,3,p);}}
        private void drawSwitch(Canvas c,float cx,float cy){c.drawRoundRect(cx-55,cy-55,cx+55,cy+55,16,16,p);if(id.contains("DOUBLE")){c.drawLine(cx,cy-45,cx,cy+45,p);}else if(id.contains("DIMMER")){c.drawCircle(cx,cy,27,p);c.drawLine(cx,cy,cx+18,cy-16,p);}else if(id.contains("PASS")){c.drawLine(cx-30,cy+18,cx+28,cy-20,p);c.drawCircle(cx-30,cy+18,5,p);c.drawCircle(cx+28,cy-20,5,p);}else c.drawRoundRect(cx-30,cy-38,cx+30,cy+38,8,8,p);}
        private void drawPanel(Canvas c,float cx,float cy){float pw=id.contains("36")?210:id.contains("24")?170:125;c.drawRoundRect(cx-pw/2,cy-55,cx+pw/2,cy+55,10,10,p);p.setStrokeWidth(2);int cols=id.contains("36")?9:id.contains("24")?6:3;for(int r=0;r<2;r++)for(int i=0;i<cols;i++){float x=cx-pw/2+18+i*18,y=cy-25+r*42;c.drawRect(x,y,x+12,y+25,p);}}
        private void drawLight(Canvas c,float cx,float cy){if(id.contains("TRACK")){c.drawLine(cx-110,cy-35,cx+110,cy-35,p);for(int i=-2;i<=2;i++){c.drawLine(cx+i*45,cy-35,cx+i*45-10,cy+20,p);c.drawCircle(cx+i*45-12,cy+28,14,p);}}else if(id.contains("PENDANT")){c.drawLine(cx,cy-65,cx,cy+10,p);Path q=new Path();q.moveTo(cx-45,cy+35);q.lineTo(cx+45,cy+35);q.lineTo(cx+28,cy+5);q.lineTo(cx-28,cy+5);q.close();c.drawPath(q,p);}else if(id.contains("SCONCE")){c.drawLine(cx-65,cy-10,cx-15,cy-10,p);c.drawCircle(cx+20,cy-10,35,p);}else if(id.contains("SURFACE")){c.drawRoundRect(cx-55,cy-30,cx+55,cy+30,18,18,p);}else{c.drawCircle(cx,cy,38,p);c.drawCircle(cx,cy,14,p);}}
    }
}
