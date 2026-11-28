package com.elektoryk.scan.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import com.elektoryk.scan.model.ApartmentOpening;
import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.MissingZoneHint;
import com.elektoryk.scan.model.PlanGridRun;
import com.elektoryk.scan.model.PlanWallSegment;
import com.elektoryk.scan.model.RoomZone;
import com.elektoryk.scan.model.ScanPathPoint;
import com.elektoryk.scan.model.StructuralFeature;

import java.util.ArrayList;
import java.util.List;

public class ApartmentPlanView extends View {
    public interface Listener { void onRoomSelected(RoomZone room); }

    private ApartmentPlan plan;
    private final List<ScanPathPoint> path=new ArrayList<>();
    private RoomZone selected;
    private Listener listener;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);

    private float ox,oy,scale;

    public ApartmentPlanView(Context c){ super(c); setBackgroundColor(Color.rgb(17,17,21)); }

    public void setData(ApartmentPlan plan,List<ScanPathPoint> path){
        this.plan=plan;
        this.path.clear(); if(path!=null)this.path.addAll(path);
        invalidate();
    }

    public void setListener(Listener l){listener=l;}
    public RoomZone getSelected(){return selected;}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(plan==null||plan.rows<=0||plan.cols<=0){
            p.setColor(Color.rgb(164,164,173));p.setTextSize(Ui.dp(getContext(),15));
            c.drawText("Ще немає multi-room плану",Ui.dp(getContext(),18),Ui.dp(getContext(),40),p);
            return;
        }

        float pad=Ui.dp(getContext(),18);
        double worldW=plan.cols*plan.cellM,worldH=plan.rows*plan.cellM;
        scale=(float)Math.min((getWidth()-pad*2)/Math.max(.1,worldW),
                (getHeight()-pad*2)/Math.max(.1,worldH));
        float drawW=(float)(worldW*scale),drawH=(float)(worldH*scale);
        ox=(getWidth()-drawW)/2f; oy=(getHeight()-drawH)/2f;

        int[] palette={
                Color.rgb(54,61,70),Color.rgb(64,56,70),Color.rgb(48,68,65),
                Color.rgb(70,63,48),Color.rgb(55,55,76),Color.rgb(67,52,52),
                Color.rgb(50,65,76),Color.rgb(65,65,65)
        };

        double[] cleanRect=cleanSingleRoomRect();
        if(cleanRect!=null && !plan.rooms.isEmpty()){
            RoomZone room=plan.rooms.get(0);p.setStyle(Paint.Style.FILL);
            p.setColor(room==selected?Color.rgb(255,196,0):palette[0]);
            c.drawRect(wx(cleanRect[0]),wz(cleanRect[2]),wx(cleanRect[1]),wz(cleanRect[3]),p);
        }else{
            for(int i=0;i<plan.rooms.size();i++){
                RoomZone room=plan.rooms.get(i);
                p.setStyle(Paint.Style.FILL);
                p.setColor(room==selected?Color.rgb(255,196,0):palette[i%palette.length]);
                for(PlanGridRun run:room.floorRuns){
                    float l=ox+(float)(run.startCol*plan.cellM*scale);
                    float r=ox+(float)((run.endCol+1)*plan.cellM*scale);
                    float t=oy+(float)(run.row*plan.cellM*scale);
                    float b=oy+(float)((run.row+1)*plan.cellM*scale);
                    c.drawRect(l,t,r,b,p);
                }
            }
        }

        // Scan trajectory.
        if(path.size()>1){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),1));
            p.setColor(Color.argb(85,205,205,212));
            for(int i=1;i<path.size();i++){
                ScanPathPoint a=path.get(i-1),b=path.get(i);
                c.drawLine(wx(a.x),wz(a.z),wx(b.x),wz(b.z),p);
            }
        }

        // Real reconstructed walls.  Older builds forgot to draw plan.walls here, so the bright
        // lines users interpreted as walls were often just the phone trajectory.
        if(plan.walls!=null){
            p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.SQUARE);
            for(PlanWallSegment w:plan.walls){
                if(w==null)continue;
                p.setStrokeWidth(Ui.dp(getContext(),Math.max(3f,(float)(w.thicknessM*scale))));
                int alpha=(int)Math.round(150+Math.max(0,Math.min(1,w.confidence))*105);
                p.setColor(Color.argb(alpha,238,238,244));
                c.drawLine(wx(w.x1),wz(w.z1),wx(w.x2),wz(w.z2),p);
            }
            p.setStrokeCap(Paint.Cap.BUTT);
        }

        // Doors/windows.
        for(ApartmentOpening a:plan.openings){
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(getContext(),a.type==ApartmentOpening.Type.DOOR||a.type==ApartmentOpening.Type.PASSAGE?4:3));
            p.setColor(a.type==ApartmentOpening.Type.DOOR?Color.rgb(57,217,138):
                    a.type==ApartmentOpening.Type.PASSAGE?Color.rgb(70,220,190):
                    a.type==ApartmentOpening.Type.WINDOW?Color.rgb(90,170,255):Color.rgb(255,128,90));
            c.drawLine(wx(a.x1),wz(a.z1),wx(a.x2),wz(a.z2),p);
        }

        // Structural features.
        for(StructuralFeature f:plan.features){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),2));
            p.setColor(f.type==StructuralFeature.Type.COLUMN
                    ?Color.rgb(255,128,90):Color.rgb(220,165,90));
            c.drawRect(wx(f.minX),wz(f.minZ),wx(f.maxX),wz(f.maxZ),p);
        }

        // Missing-zone hints.
        for(MissingZoneHint h:plan.missingZoneHints){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),3));
            p.setColor(Color.rgb(255,90,95));
            c.drawCircle(wx(h.x),wz(h.z),Ui.dp(getContext(),12),p);
        }

        // Labels.
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(Ui.dp(getContext(),12));
        for(RoomZone r:plan.rooms){
            p.setColor(r==selected?Color.rgb(11,11,13):Color.WHITE);
            c.drawText(r.displayName()+" • "+String.format(java.util.Locale.US,"%.1f м²",r.areaM2),
                    wx(r.centroidX),wz(r.centroidZ),p);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()!=MotionEvent.ACTION_UP||plan==null)return true;
        double col=(e.getX()-ox)/(scale*plan.cellM);
        double row=(e.getY()-oy)/(scale*plan.cellM);
        int c=(int)Math.floor(col),r=(int)Math.floor(row);
        RoomZone found=null;
        for(RoomZone room:plan.rooms){
            for(PlanGridRun run:room.floorRuns){
                if(run.row==r&&c>=run.startCol&&c<=run.endCol){found=room;break;}
            }
            if(found!=null)break;
        }
        selected=found;
        invalidate();
        if(listener!=null&&found!=null)listener.onRoomSelected(found);
        return true;
    }

    private float wx(double x){return ox+(float)((x-plan.originX)*scale);}
    private float wz(double z){return oy+(float)((z-plan.originZ)*scale);}

    /** Clean fill for the common one-room rectangular topology. */
    private double[] cleanSingleRoomRect(){
        if(plan==null||plan.rooms.size()!=1||plan.walls==null||plan.walls.size()!=4)return null;
        int h=0,v=0;double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY;
        double minZ=Double.POSITIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        for(PlanWallSegment w:plan.walls){
            if(w==null)return null;double dx=Math.abs(w.x2-w.x1),dz=Math.abs(w.z2-w.z1);
            if(dx>=dz){if(dz>0.18)return null;h++;}else{if(dx>0.18)return null;v++;}
            minX=Math.min(minX,Math.min(w.x1,w.x2));maxX=Math.max(maxX,Math.max(w.x1,w.x2));
            minZ=Math.min(minZ,Math.min(w.z1,w.z2));maxZ=Math.max(maxZ,Math.max(w.z1,w.z2));
        }
        if(h!=2||v!=2||maxX-minX<0.8||maxZ-minZ<0.8)return null;
        return new double[]{minX,maxX,minZ,maxZ};
    }
}
