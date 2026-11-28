package com.elektoryk.scan.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import com.elektoryk.scan.model.*;

import java.util.Locale;

public class WallElevationView extends View {
    private ApartmentPlan plan;
    private FloorPlan floor;
    private PlanWallSegment wall;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);

    public WallElevationView(Context c){super(c);setBackgroundColor(Color.rgb(17,17,21));}

    public void setData(ApartmentPlan plan,FloorPlan floor,PlanWallSegment wall){
        this.plan=plan;this.floor=floor;this.wall=wall;invalidate();
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(plan==null||wall==null){
            p.setColor(Color.LTGRAY);p.setTextSize(Ui.dp(getContext(),13));
            c.drawText("Натисни на стіну на плані — тут буде її розгортка.",Ui.dp(getContext(),14),Ui.dp(getContext(),32),p);return;
        }

        double wallLen=Math.max(0.1,wall.lengthM());
        double height=Math.max(2.0,plan.ceilingY-plan.floorY);
        float pad=Ui.dp(getContext(),28);
        float sx=(float)((getWidth()-pad*2)/wallLen);
        float sy=(float)((getHeight()-pad*2)/height);
        float sc=Math.min(sx,sy);
        float left=(getWidth()-(float)(wallLen*sc))/2f;
        float bottom=getHeight()-pad;
        float top=bottom-(float)(height*sc);

        p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(40,40,46));
        c.drawRect(left,top,left+(float)(wallLen*sc),bottom,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),2));p.setColor(Color.rgb(235,235,238));
        c.drawRect(left,top,left+(float)(wallLen*sc),bottom,p);

        // Openings on this wall.
        for(ApartmentOpening o:plan.openings){
            if(!wall.id.equals(o.wallId))continue;
            double t=projectionFraction(o.centerX(),o.centerZ());
            float cx=left+(float)(t*wallLen*sc);
            float ow=(float)(o.widthM*sc);
            float yBottom=bottom-(float)(o.bottomY*sc);
            float yTop=bottom-(float)(o.topY*sc);
            p.setStyle(Paint.Style.FILL);
            p.setColor(o.type==ApartmentOpening.Type.DOOR?Color.rgb(35,75,58):o.type==ApartmentOpening.Type.PASSAGE?Color.rgb(30,78,70):Color.rgb(34,65,95));
            c.drawRect(cx-ow/2,yTop,cx+ow/2,yBottom,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),2));
            p.setColor(o.type==ApartmentOpening.Type.DOOR?Color.rgb(57,217,138):o.type==ApartmentOpening.Type.PASSAGE?Color.rgb(70,220,190):Color.rgb(80,160,255));
            c.drawRect(cx-ow/2,yTop,cx+ow/2,yBottom,p);
        }

        if(floor!=null){
            for(ElectricalPoint e:floor.points){
                if(!wall.id.equals(e.wallId))continue;
                double t=projectionFraction(e.position.x,e.position.z);
                float x=left+(float)(t*wallLen*sc);
                float y=bottom-(float)(e.heightFromFinishedFloorM*sc);
                p.setStyle(Paint.Style.FILL);p.setColor(pointColor(e.type));
                c.drawCircle(x,y,Ui.dp(getContext(),7),p);
                p.setColor(Color.WHITE);p.setTextSize(Ui.dp(getContext(),9));p.setTextAlign(Paint.Align.CENTER);
                c.drawText(label(e),x,y-Ui.dp(getContext(),11),p);
                p.setTextSize(Ui.dp(getContext(),8));p.setColor(Color.rgb(190,190,198));
                c.drawText(String.format(Locale.US,"%.2f м",e.heightFromFinishedFloorM),x,y+Ui.dp(getContext(),18),p);
            }
        }

        p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.rgb(220,220,225));p.setTextSize(Ui.dp(getContext(),11));
        c.drawText(String.format(Locale.US,"Стіна %.2f м • висота %.2f м • %s %.0f мм",wallLen,height,finishLabel(wall.finishKey),wall.finishThicknessM*1000),
                getWidth()/2f,Ui.dp(getContext(),18),p);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(Ui.dp(getContext(),10));
        p.setColor(Color.argb(210,226,177,54));
        c.drawText("ELEKTORYK",getWidth()-Ui.dp(getContext(),10),getHeight()-Ui.dp(getContext(),10),p);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private double projectionFraction(double x,double z){
        double dx=wall.x2-wall.x1,dz=wall.z2-wall.z1,den=dx*dx+dz*dz;
        if(den<1e-9)return 0.5;
        return Math.max(0,Math.min(1,((x-wall.x1)*dx+(z-wall.z1)*dz)/den));
    }


    private String finishLabel(String k){
        if("PLASTER".equals(k))return "штукатурка";if("PUTTY".equals(k))return "шпаклівка";
        if("PAINT".equals(k))return "фарба";if("TILE".equals(k))return "плитка";
        if("DRYWALL".equals(k))return "ГКЛ";if("DECOR".equals(k))return "декор";return "чернова";
    }

    private int pointColor(ElectricalPoint.Type t){
        switch(t){
            case SOCKET:return Color.rgb(255,196,0);
            case SWITCH:return Color.rgb(80,190,255);
            case LIGHT:return Color.rgb(255,240,150);
            case PANEL:return Color.rgb(255,105,95);
            case ETHERNET:return Color.rgb(110,210,180);
            default:return Color.WHITE;
        }
    }
    private String label(ElectricalPoint e){
        String m=e.moduleCount>1?" ×"+e.moduleCount:"";
        switch(e.type){
            case SOCKET:return "Розетка"+m;case SWITCH:return "Вимикач"+m;case TV:return "TV";
            case ETHERNET:return "LAN";case APPLIANCE:return "Техніка";case PANEL:return "Щит";
            case CAMERA:return "Камера";default:return e.type.name();
        }
    }
}
