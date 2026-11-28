package com.elektoryk.scan.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import com.elektoryk.scan.model.ApartmentOpening;
import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.CableRoute;
import com.elektoryk.scan.model.ElectricalPoint;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.FurnitureItem;
import com.elektoryk.scan.model.ForbiddenZone;
import com.elektoryk.scan.model.PlanGridRun;
import com.elektoryk.scan.model.PlanWallSegment;
import com.elektoryk.scan.model.RoomZone;

import java.util.ArrayList;
import java.util.List;

public class ApartmentEditView extends View {
    public interface Listener {
        void onWorldTap(double x,double z);
        void onRoomSelected(RoomZone room);
        void onWallSelected(PlanWallSegment wall);
        void onOpeningSelected(ApartmentOpening opening);
        void onPointSelected(ElectricalPoint point);
    }

    private ApartmentPlan plan;
    private FloorPlan floor;
    private List<CableRoute> routes=new ArrayList<>();
    private Listener listener;
    private String interactionMode="SELECT";

    private String selectedRoomId="",selectedWallId="",selectedOpeningId="",selectedPointId="";
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float ox,oy,scale;

    public ApartmentEditView(Context c){super(c);setBackgroundColor(Color.rgb(18,18,22));}

    public void setData(ApartmentPlan plan,FloorPlan floor,List<CableRoute> routes){
        this.plan=plan;this.floor=floor;
        this.routes=routes==null?new ArrayList<>():routes;
        invalidate();
    }
    public void setListener(Listener l){listener=l;}
    public void setInteractionMode(String mode){interactionMode=mode==null?"SELECT":mode;}
    public void setSelection(String room,String wall,String opening,String point){
        selectedRoomId=room==null?"":room;selectedWallId=wall==null?"":wall;
        selectedOpeningId=opening==null?"":opening;selectedPointId=point==null?"":point;
        invalidate();
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(plan==null||plan.rows<=0||plan.cols<=0){
            p.setColor(Color.LTGRAY);p.setTextSize(Ui.dp(getContext(),15));
            c.drawText("Немає Multi-room плану",Ui.dp(getContext(),18),Ui.dp(getContext(),38),p);return;
        }
        float pad=Ui.dp(getContext(),18);
        double ww=plan.cols*plan.cellM,hh=plan.rows*plan.cellM;
        scale=(float)Math.min((getWidth()-pad*2)/Math.max(.1,ww),(getHeight()-pad*2)/Math.max(.1,hh));
        float dw=(float)(ww*scale),dh=(float)(hh*scale);
        ox=(getWidth()-dw)/2f;oy=(getHeight()-dh)/2f;

        int[] palette={Color.rgb(45,51,59),Color.rgb(52,46,59),Color.rgb(42,58,55),
                Color.rgb(59,54,42),Color.rgb(48,48,64),Color.rgb(58,45,45)};

        for(int i=0;i<plan.rooms.size();i++){
            RoomZone r=plan.rooms.get(i);
            p.setStyle(Paint.Style.FILL);
            p.setColor(r.id.equals(selectedRoomId)?Color.rgb(100,83,20):palette[i%palette.length]);
            for(PlanGridRun run:r.floorRuns){
                c.drawRect(ox+(float)(run.startCol*plan.cellM*scale),
                        oy+(float)(run.row*plan.cellM*scale),
                        ox+(float)((run.endCol+1)*plan.cellM*scale),
                        oy+(float)((run.row+1)*plan.cellM*scale),p);
            }
        }

        // Forbidden/no-route zones.
        if(plan.forbiddenZones!=null){
            for(ForbiddenZone z:plan.forbiddenZones){
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(70,255,90,95));
                c.drawRect(wx(z.minX),wz(z.minZ),wx(z.maxX),wz(z.maxZ),p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(),2));
                p.setColor(Color.rgb(255,90,95));
                c.drawRect(wx(z.minX),wz(z.minZ),wx(z.maxX),wz(z.maxZ),p);
            }
        }

        // Optional furniture footprint from Digital Twin. It is reference-only.
        if(plan.furniture!=null){
            for(FurnitureItem f:plan.furniture)drawFurniture(c,f);
        }

        // Draft cable routes under walls/points.
        if(routes!=null){
            for(CableRoute route:routes){
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),2));
                p.setColor(Color.rgb(130,130,145));
                for(int i=1;i<route.nodes.size();i++){
                    CableRoute.RouteNode a=route.nodes.get(i-1),b=route.nodes.get(i);
                    c.drawLine(wx(a.x),wz(a.z),wx(b.x),wz(b.z),p);
                }
            }
        }

        // Walls.
        for(PlanWallSegment w:plan.walls){
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(getContext(),w.id.equals(selectedWallId)?5:3));
            p.setColor(w.manual?Color.rgb(255,196,0):Color.rgb(225,225,230));
            c.drawLine(wx(w.x1),wz(w.z1),wx(w.x2),wz(w.z2),p);
        }

        // Openings.
        for(ApartmentOpening o:plan.openings){
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(getContext(),o.id.equals(selectedOpeningId)?7:5));
            p.setColor(o.type==ApartmentOpening.Type.DOOR?Color.rgb(57,217,138):
                    o.type==ApartmentOpening.Type.PASSAGE?Color.rgb(70,220,190):
                    o.type==ApartmentOpening.Type.WINDOW?Color.rgb(90,170,255):Color.rgb(255,128,90));
            c.drawLine(wx(o.x1),wz(o.z1),wx(o.x2),wz(o.z2),p);
        }

        // Electrical points.
        if(floor!=null){
            for(ElectricalPoint e:floor.points){
                boolean sel=e.id.equals(selectedPointId);
                p.setStyle(Paint.Style.FILL);
                p.setColor(sel?Color.rgb(255,196,0):Color.WHITE);
                c.drawCircle(wx(e.position.x),wz(e.position.z),Ui.dp(getContext(),sel?8:6),p);
                p.setTextSize(Ui.dp(getContext(),10));
                p.setColor(sel?Color.rgb(255,196,0):Color.WHITE);
                c.drawText(shortName(e),wx(e.position.x)+Ui.dp(getContext(),7),
                        wz(e.position.z)-Ui.dp(getContext(),5),p);
            }
        }

        // Labels.
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(Ui.dp(getContext(),11));p.setColor(Color.WHITE);
        for(RoomZone r:plan.rooms)c.drawText(r.displayName()+" "+String.format(java.util.Locale.US,"%.1f",r.areaM2),
                wx(r.centroidX),wz(r.centroidZ),p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()!=MotionEvent.ACTION_UP||plan==null)return true;
        double x=worldX(e.getX()),z=worldZ(e.getY());
        if(!"SELECT".equals(interactionMode)){
            if(listener!=null)listener.onWorldTap(x,z);
            return true;
        }

        ElectricalPoint ep=nearestPoint(x,z,0.28);
        if(ep!=null){if(listener!=null)listener.onPointSelected(ep);return true;}
        ApartmentOpening op=nearestOpening(x,z,0.30);
        if(op!=null){if(listener!=null)listener.onOpeningSelected(op);return true;}
        PlanWallSegment wall=nearestWall(x,z,0.28);
        if(wall!=null){if(listener!=null)listener.onWallSelected(wall);return true;}
        RoomZone room=roomAt(x,z);
        if(room!=null&&listener!=null)listener.onRoomSelected(room);
        else if(listener!=null)listener.onWorldTap(x,z);
        return true;
    }

    public RoomZone roomAt(double x,double z){
        int c=(int)Math.floor((x-plan.originX)/plan.cellM),r=(int)Math.floor((z-plan.originZ)/plan.cellM);
        for(RoomZone room:plan.rooms)for(PlanGridRun run:room.floorRuns)
            if(run.row==r&&c>=run.startCol&&c<=run.endCol)return room;
        return null;
    }

    public PlanWallSegment nearestWall(double x,double z,double max){
        PlanWallSegment best=null;double bd=max;
        for(PlanWallSegment w:plan.walls){
            double d=distance(x,z,w.x1,w.z1,w.x2,w.z2);
            if(d<=bd){bd=d;best=w;}
        }return best;
    }
    private ApartmentOpening nearestOpening(double x,double z,double max){
        ApartmentOpening best=null;double bd=max;
        for(ApartmentOpening o:plan.openings){
            double d=distance(x,z,o.x1,o.z1,o.x2,o.z2);
            if(d<=bd){bd=d;best=o;}
        }return best;
    }
    private ElectricalPoint nearestPoint(double x,double z,double max){
        if(floor==null)return null;ElectricalPoint best=null;double bd=max;
        for(ElectricalPoint p:floor.points){
            double d=Math.hypot(x-p.position.x,z-p.position.z);
            if(d<=bd){bd=d;best=p;}
        }return best;
    }

    private double distance(double px,double pz,double ax,double az,double bx,double bz){
        double dx=bx-ax,dz=bz-az,den=dx*dx+dz*dz;
        if(den<1e-9)return Math.hypot(px-ax,pz-az);
        double t=((px-ax)*dx+(pz-az)*dz)/den;t=Math.max(0,Math.min(1,t));
        return Math.hypot(px-(ax+t*dx),pz-(az+t*dz));
    }
    private float wx(double x){return ox+(float)((x-plan.originX)*scale);}
    private float wz(double z){return oy+(float)((z-plan.originZ)*scale);}
    private double worldX(float px){return plan.originX+(px-ox)/Math.max(0.0001,scale);}
    private double worldZ(float py){return plan.originZ+(py-oy)/Math.max(0.0001,scale);}


    private void drawFurniture(Canvas c,FurnitureItem f){
        double hw=f.widthM/2.0,hd=f.depthM/2.0,a=Math.toRadians(f.rotationDeg),ca=Math.cos(a),sa=Math.sin(a);
        double[][] q={{-hw,-hd},{hw,-hd},{hw,hd},{-hw,hd}};
        android.graphics.Path path=new android.graphics.Path();
        for(int i=0;i<4;i++){
            double x=f.x+q[i][0]*ca-q[i][1]*sa,z=f.z+q[i][0]*sa+q[i][1]*ca;
            if(i==0)path.moveTo(wx(x),wz(z));else path.lineTo(wx(x),wz(z));
        }
        path.close();p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(55,205,170,110));c.drawPath(path,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),1));p.setColor(Color.rgb(170,140,95));c.drawPath(path,p);
    }

    private String shortName(ElectricalPoint e){
        String base;
        switch(e.type){
            case SOCKET:base="R";break;case SWITCH:base="SW";break;case LIGHT:base="L";break;
            case PANEL:base="Щ";break;case ETHERNET:base="LAN";break;case APPLIANCE:base="A";break;
            case SENSOR:base="S";break;case CAMERA:base="CAM";break;case SMART_HOME:base="SMART";break;
            default:base="TV";
        }
        return e.circuitNo>0?base+"·"+e.circuitNo:base;
    }
}
