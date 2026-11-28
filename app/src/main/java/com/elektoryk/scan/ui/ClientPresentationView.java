package com.elektoryk.scan.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import com.elektoryk.scan.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ClientPresentationView extends View {
    public interface Listener{
        void onWallSelected(PlanWallSegment wall);
        void onRoomSelected(RoomZone room);
    }

    public enum Mode { CLEAN, ELECTRICAL, LIGHTING, TECHNICAL, ACCURACY }

    private ApartmentPlan plan;
    private FloorPlan floor;
    private List<CableRoute> routes=new ArrayList<>();
    private PrecisionReport precision;
    private Listener listener;
    private Mode mode=Mode.CLEAN;
    private String selectedWallId="";
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float ox,oy,scale;

    public ClientPresentationView(Context c){super(c);setBackgroundColor(Color.rgb(14,14,17));}

    public void setData(ApartmentPlan plan,FloorPlan floor,List<CableRoute> routes,PrecisionReport precision){
        this.plan=plan;this.floor=floor;this.routes=routes==null?new ArrayList<>():routes;this.precision=precision;invalidate();
    }
    public void setMode(Mode m){mode=m==null?Mode.CLEAN:m;invalidate();}
    public void setListener(Listener l){listener=l;}
    public void setSelectedWall(String id){selectedWallId=id==null?"":id;invalidate();}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(plan==null||plan.rows<=0||plan.cols<=0){
            p.setColor(Color.LTGRAY);p.setTextSize(Ui.dp(getContext(),15));
            c.drawText("Немає плану",Ui.dp(getContext(),16),Ui.dp(getContext(),36),p);return;
        }
        float pad=Ui.dp(getContext(),24);
        double ww=plan.cols*plan.cellM,hh=plan.rows*plan.cellM;
        scale=(float)Math.min((getWidth()-pad*2)/Math.max(.1,ww),(getHeight()-pad*2)/Math.max(.1,hh));
        float dw=(float)(ww*scale),dh=(float)(hh*scale);
        ox=(getWidth()-dw)/2f;oy=(getHeight()-dh)/2f;

        drawRooms(c);
        drawFurniture(c);
        if(mode==Mode.TECHNICAL)drawTechnical(c);
        drawWalls(c);
        drawOpenings(c);
        if(mode==Mode.ELECTRICAL||mode==Mode.LIGHTING||mode==Mode.TECHNICAL)drawPoints(c);
        drawRoomLabels(c);
        if(mode==Mode.CLEAN||mode==Mode.ELECTRICAL)drawDimensions(c);
        if(mode==Mode.ACCURACY)drawAccuracy(c);
        drawBrandWatermark(c);
    }

    private void drawRooms(Canvas c){
        int[] fills={Color.rgb(34,36,42),Color.rgb(39,35,43),Color.rgb(32,41,39),
                Color.rgb(43,40,32),Color.rgb(34,34,47),Color.rgb(43,34,34)};
        for(int i=0;i<plan.rooms.size();i++){
            RoomZone r=plan.rooms.get(i);
            p.setStyle(Paint.Style.FILL);p.setColor(fills[i%fills.length]);
            for(PlanGridRun run:r.floorRuns){
                c.drawRect(ox+(float)(run.startCol*plan.cellM*scale),
                        oy+(float)(run.row*plan.cellM*scale),
                        ox+(float)((run.endCol+1)*plan.cellM*scale),
                        oy+(float)((run.row+1)*plan.cellM*scale),p);
            }
        }
    }


    private void drawFurniture(Canvas c){
        if(plan==null||plan.furniture==null)return;
        for(FurnitureItem f:plan.furniture){
            double hw=f.widthM/2.0,hd=f.depthM/2.0,a=Math.toRadians(f.rotationDeg),ca=Math.cos(a),sa=Math.sin(a);
            double[][] q={{-hw,-hd},{hw,-hd},{hw,hd},{-hw,hd}};
            Path path=new Path();
            for(int i=0;i<4;i++){
                double x=f.x+q[i][0]*ca-q[i][1]*sa,z=f.z+q[i][0]*sa+q[i][1]*ca;
                if(i==0)path.moveTo(wx(x),wz(z));else path.lineTo(wx(x),wz(z));
            }
            path.close();p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(45,210,178,112));c.drawPath(path,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),1));p.setColor(Color.rgb(170,140,95));c.drawPath(path,p);
        }
    }

    private void drawWalls(Canvas c){
        for(PlanWallSegment w:plan.walls){
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(getContext(),w.id.equals(selectedWallId)?6:4));
            if(mode==Mode.ACCURACY){
                if(w.confidence>=0.82)p.setColor(Color.rgb(57,217,138));
                else if(w.confidence>=0.60)p.setColor(Color.rgb(255,196,0));
                else p.setColor(Color.rgb(255,90,95));
            }else{
                p.setColor(w.id.equals(selectedWallId)?Color.rgb(255,196,0):Color.rgb(235,235,238));
            }
            c.drawLine(wx(w.x1),wz(w.z1),wx(w.x2),wz(w.z2),p);
        }
    }

    private void drawOpenings(Canvas c){
        for(ApartmentOpening o:plan.openings){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),6));
            p.setColor(o.type==ApartmentOpening.Type.DOOR?Color.rgb(57,217,138):
                    o.type==ApartmentOpening.Type.PASSAGE?Color.rgb(70,220,190):
                    o.type==ApartmentOpening.Type.WINDOW?Color.rgb(80,160,255):Color.rgb(255,130,90));
            c.drawLine(wx(o.x1),wz(o.z1),wx(o.x2),wz(o.z2),p);

            if(mode==Mode.CLEAN||mode==Mode.ELECTRICAL){
                p.setStyle(Paint.Style.FILL);p.setTextSize(Ui.dp(getContext(),9));p.setTextAlign(Paint.Align.CENTER);
                p.setColor(Color.rgb(210,210,216));
                String t=o.type==ApartmentOpening.Type.DOOR?"Д "+fmt(o.widthM):o.type==ApartmentOpening.Type.PASSAGE?"П "+fmt(o.widthM):"В "+fmt(o.widthM);
                c.drawText(t,wx(o.centerX()),wz(o.centerZ())-Ui.dp(getContext(),7),p);
                p.setTextAlign(Paint.Align.LEFT);
            }
        }
    }

    private void drawPoints(Canvas c){
        if(floor==null)return;
        for(ElectricalPoint e:floor.points){
            if(mode==Mode.LIGHTING && e.type!=ElectricalPoint.Type.LIGHT && e.type!=ElectricalPoint.Type.SWITCH)continue;
            if(mode==Mode.ELECTRICAL && e.type==ElectricalPoint.Type.LIGHT){} // keep all points
            float x=wx(e.position.x),z=wz(e.position.z);
            p.setStyle(Paint.Style.FILL);
            p.setColor(pointColor(e.type));
            float r=Ui.dp(getContext(),7);
            c.drawCircle(x,z,r,p);
            p.setColor(Color.rgb(10,10,12));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Ui.dp(getContext(),8));
            c.drawText(symbol(e.type),x,z+Ui.dp(getContext(),3),p);
            p.setTextAlign(Paint.Align.LEFT);

            if(mode==Mode.ELECTRICAL||mode==Mode.LIGHTING){
                p.setColor(Color.WHITE);p.setTextSize(Ui.dp(getContext(),9));
                String label=clientLabel(e);
                c.drawText(label,x+Ui.dp(getContext(),9),z-Ui.dp(getContext(),4),p);
            }
        }
    }

    private void drawTechnical(Canvas c){
        // Forbidden zones.
        for(ForbiddenZone z:plan.forbiddenZones){
            p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(55,255,90,95));
            c.drawRect(wx(z.minX),wz(z.minZ),wx(z.maxX),wz(z.maxZ),p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),1));p.setColor(Color.rgb(255,90,95));
            c.drawRect(wx(z.minX),wz(z.minZ),wx(z.maxX),wz(z.maxZ),p);
        }
        // Routes.
        for(CableRoute route:routes){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),2));
            p.setColor(Color.rgb(150,150,165));
            for(int i=1;i<route.nodes.size();i++){
                CableRoute.RouteNode a=route.nodes.get(i-1),b=route.nodes.get(i);
                c.drawLine(wx(a.x),wz(a.z),wx(b.x),wz(b.z),p);
            }
        }
    }

    private void drawRoomLabels(Canvas c){
        p.setTextAlign(Paint.Align.CENTER);
        for(RoomZone r:plan.rooms){
            p.setColor(Color.WHITE);p.setTextSize(Ui.dp(getContext(),12));
            c.drawText(r.displayName(),wx(r.centroidX),wz(r.centroidZ),p);
            p.setTextSize(Ui.dp(getContext(),10));p.setColor(Color.rgb(180,180,188));
            c.drawText(String.format(Locale.US,"%.1f м²",r.areaM2),wx(r.centroidX),wz(r.centroidZ)+Ui.dp(getContext(),14),p);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawDimensions(Canvas c){
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Ui.dp(getContext(),9));p.setColor(Color.rgb(190,190,198));
        for(PlanWallSegment w:plan.walls){
            if(w.lengthM()<0.75)continue;
            double cx=w.centerX(),cz=w.centerZ();
            c.drawText(fmt(w.lengthM())+" м",wx(cx),wz(cz)-Ui.dp(getContext(),6),p);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawAccuracy(Canvas c){
        if(precision==null)return;
        p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(210,20,20,24));
        float l=Ui.dp(getContext(),10),t=Ui.dp(getContext(),10),r=getWidth()-Ui.dp(getContext(),10),b=t+Ui.dp(getContext(),54);
        c.drawRoundRect(l,t,r,b,Ui.dp(getContext(),12),Ui.dp(getContext(),12),p);
        p.setColor(Color.WHITE);p.setTextSize(Ui.dp(getContext(),13));
        c.drawText("Точність "+precision.score+"/100 • очікувана похибка ≈ "+String.format(Locale.US,"%.1f см",precision.estimatedPlanErrorCm),
                l+Ui.dp(getContext(),12),t+Ui.dp(getContext(),22),p);
        p.setTextSize(Ui.dp(getContext(),10));p.setColor(Color.rgb(180,180,188));
        c.drawText("Зелений = впевнено • жовтий = перевірити • червоний = слабка геометрія",
                l+Ui.dp(getContext(),12),t+Ui.dp(getContext(),41),p);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()!=MotionEvent.ACTION_UP||plan==null)return true;
        double x=worldX(e.getX()),z=worldZ(e.getY());
        PlanWallSegment wall=nearestWall(x,z,0.35);
        if(wall!=null){
            selectedWallId=wall.id;invalidate();
            if(listener!=null)listener.onWallSelected(wall);
            return true;
        }
        RoomZone room=roomAt(x,z);
        if(room!=null&&listener!=null)listener.onRoomSelected(room);
        return true;
    }

    private RoomZone roomAt(double x,double z){
        int c=(int)Math.floor((x-plan.originX)/plan.cellM),r=(int)Math.floor((z-plan.originZ)/plan.cellM);
        for(RoomZone room:plan.rooms)for(PlanGridRun run:room.floorRuns)
            if(run.row==r&&c>=run.startCol&&c<=run.endCol)return room;
        return null;
    }

    private PlanWallSegment nearestWall(double x,double z,double max){
        PlanWallSegment best=null;double bd=max;
        for(PlanWallSegment w:plan.walls){
            double d=distance(x,z,w.x1,w.z1,w.x2,w.z2);
            if(d<bd){bd=d;best=w;}
        }
        return best;
    }

    private double distance(double px,double pz,double ax,double az,double bx,double bz){
        double dx=bx-ax,dz=bz-az,den=dx*dx+dz*dz;
        if(den<1e-9)return Math.hypot(px-ax,pz-az);
        double t=((px-ax)*dx+(pz-az)*dz)/den;t=Math.max(0,Math.min(1,t));
        return Math.hypot(px-(ax+t*dx),pz-(az+t*dz));
    }

    private int pointColor(ElectricalPoint.Type t){
        switch(t){
            case SOCKET:return Color.rgb(255,196,0);
            case SWITCH:return Color.rgb(80,190,255);
            case LIGHT:return Color.rgb(255,240,150);
            case PANEL:return Color.rgb(255,105,95);
            case ETHERNET:return Color.rgb(110,210,180);
            case CAMERA:return Color.rgb(190,130,255);
            default:return Color.rgb(230,230,235);
        }
    }
    private String symbol(ElectricalPoint.Type t){
        switch(t){
            case SOCKET:return "R";case SWITCH:return "S";case LIGHT:return "L";case PANEL:return "Щ";
            case TV:return "TV";case ETHERNET:return "N";case APPLIANCE:return "A";case CAMERA:return "C";
            case SENSOR:return "D";case SMART_HOME:return "M";default:return "•";
        }
    }
    private String clientLabel(ElectricalPoint e){
        String h=e.heightFromFinishedFloorM>0?String.format(Locale.US," • %.2fм",e.heightFromFinishedFloorM):"";
        String modules=e.moduleCount>1?" ×"+e.moduleCount:"";
        return friendly(e.type)+modules+h;
    }
    private String friendly(ElectricalPoint.Type t){
        switch(t){
            case SOCKET:return "Розетка";case SWITCH:return "Вимикач";case LIGHT:return "Світло";case PANEL:return "Щит";
            case TV:return "TV";case ETHERNET:return "LAN";case APPLIANCE:return "Техніка";case CAMERA:return "Камера";
            case SENSOR:return "Датчик";case SMART_HOME:return "Smart";default:return t.name();
        }
    }

    private String fmt(double v){return String.format(Locale.US,"%.2f",v);}
    private float wx(double x){return ox+(float)((x-plan.originX)*scale);}
    private float wz(double z){return oy+(float)((z-plan.originZ)*scale);}
    private double worldX(float x){return plan.originX+(x-ox)/Math.max(.0001,scale);}
    private double worldZ(float z){return plan.originZ+(z-oy)/Math.max(.0001,scale);}
    private void drawBrandWatermark(Canvas c){
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(Ui.dp(getContext(),12));
        p.setColor(Color.argb(210,226,177,54));
        float x=getWidth()-Ui.dp(getContext(),12);
        float y=getHeight()-Ui.dp(getContext(),14);
        c.drawText("ELEKTORYK",x,y,p);
        p.setStrokeWidth(Ui.dp(getContext(),2));
        p.setColor(Color.argb(160,226,177,54));
        c.drawLine(x-Ui.dp(getContext(),86),y+Ui.dp(getContext(),5),x,y+Ui.dp(getContext(),5),p);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextAlign(Paint.Align.LEFT);
    }

}
