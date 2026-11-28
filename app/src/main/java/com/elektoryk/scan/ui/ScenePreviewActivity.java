package com.elektoryk.scan.ui;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.elektoryk.scan.model.CloudPoint;
import com.elektoryk.scan.model.ReviewIssue;
import com.elektoryk.scan.model.RoomBounds;
import com.elektoryk.scan.model.SceneSnapshot;
import com.elektoryk.scan.model.PlanWallSegment;
import com.elektoryk.scan.storage.SceneRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScenePreviewActivity extends Activity {
    private String projectId;
    private SceneRepository repo;
    private SceneSnapshot scene;
    private List<CloudPoint> points;
    private PreviewView preview;
    private TextView info;

    @Override
    protected void onCreate(Bundle b){
        super.onCreate(b);
        projectId=getIntent().getStringExtra("projectId");
        repo=new SceneRepository(this);
        scene=repo.loadScene(projectId);
        points=repo.loadPoints(projectId,12000);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,16));
        root.setBackgroundColor(Color.rgb(11,11,13));

        root.addView(Ui.brandHeader(this,"ELEKTORYK • 3D","Сирий point cloud та очищена геометрія.","3D",true));
        root.addView(Ui.screenIntro(this,"ПЕРЕГЛЯД","RAW / CLEAN","RAW показує сирі вимірювання. CLEAN — очищену архітектурну модель. Тягни пальцем, щоб обертати."),Ui.lpMatchWrap(this,8));
        info=Ui.muted(this,"");
        root.addView(info);

        preview=new PreviewView();
        LinearLayout.LayoutParams pv=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,0,1f);
        pv.topMargin=Ui.dp(this,10);
        root.addView(preview,pv);

        Button mode=Ui.button(this,"Перемкнути RAW / CLEAN");
        mode.setOnClickListener(v -> { preview.clean=!preview.clean; preview.invalidate(); update(); });
        root.addView(mode,Ui.lpMatchWrap(this,10));

        Button reset=Ui.button(this,"Скинути кут огляду");
        reset.setOnClickListener(v -> { preview.yaw=-35; preview.pitch=20; preview.invalidate(); });
        root.addView(reset,Ui.lpMatchWrap(this,8));

        root.addView(Ui.bottomNav(this,projectId,"SCAN"),Ui.lpMatchWrap(this,8));
        setContentView(root);
        update();
    }

    private void update(){
        if(scene==null){ info.setText("Немає збереженої Smart Scan сцени."); return; }
        RoomBounds r=scene.roomBounds;
        String dims=r!=null&&r.isUsable()
                ? String.format(Locale.US,"%.2f × %.2f × %.2f м",r.widthM(),r.lengthM(),r.heightM())
                : "межі не визначені";
        info.setText((preview.clean?"CLEAN":"RAW")+" • "+dims+" • points "+points.size()
                +" • review "+scene.reviewIssues.size()+" • тягни пальцем для обертання");
    }

    private class PreviewView extends View {
        Paint rawPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint wallPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint issuePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean clean=false;
        float yaw=-35f,pitch=20f,lastX,lastY;

        PreviewView(){
            super(ScenePreviewActivity.this);
            setBackgroundColor(Color.rgb(18,18,22));
            rawPaint.setColor(Color.rgb(180,180,186));
            rawPaint.setStrokeWidth(2f);
            wallPaint.setColor(Color.rgb(255,196,0));
            wallPaint.setStrokeWidth(Ui.dp(ScenePreviewActivity.this,2));
            wallPaint.setStyle(Paint.Style.STROKE);
            issuePaint.setColor(Color.rgb(255,90,95));
            issuePaint.setStyle(Paint.Style.STROKE);
            issuePaint.setStrokeWidth(Ui.dp(ScenePreviewActivity.this,2));
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            if(scene==null) return;

            RoomBounds b=scene.roomBounds;
            double cx=0,cy=0,cz=0,span=5;
            if(b!=null&&b.isUsable()){
                cx=(b.minX+b.maxX)/2; cy=(b.floorY+b.ceilingY)/2; cz=(b.minZ+b.maxZ)/2;
                span=Math.max(b.widthM(),Math.max(b.lengthM(),b.heightM()));
            }else if(!points.isEmpty()){
                for(CloudPoint p:points){cx+=p.x;cy+=p.y;cz+=p.z;}
                cx/=points.size();cy/=points.size();cz/=points.size();
            }
            float scale=(float)(Math.min(getWidth(),getHeight())/(Math.max(1.0,span)*1.6));

            if(!clean){
                int stride=Math.max(1,points.size()/7000);
                for(int i=0;i<points.size();i+=stride){
                    CloudPoint p=points.get(i);
                    double ps=scene.rawPointScale<=0?1.0:scene.rawPointScale;
                    float[] q=project(p.x*ps-cx,p.y*ps-cy,p.z*ps-cz,scale);
                    c.drawPoint(q[0],q[1],rawPaint);
                }
            }

            // Prefer the reconstructed apartment topology. RoomBounds is only an envelope and
            // drawing it as a box invents walls for L/U-shaped rooms and multi-room apartments.
            if(scene.apartmentPlan!=null&&!scene.apartmentPlan.walls.isEmpty()){
                double floorY=Double.isFinite(scene.apartmentPlan.floorY)?scene.apartmentPlan.floorY:(b!=null?b.floorY:0);
                double ceilY=Double.isFinite(scene.apartmentPlan.ceilingY)?scene.apartmentPlan.ceilingY:(b!=null?b.ceilingY:floorY+2.7);
                for(PlanWallSegment w:scene.apartmentPlan.walls){
                    drawWallPrism(c,w,floorY,ceilY,cx,cy,cz,scale);
                }
            }else if(b!=null&&b.isUsable()){
                drawBoundsFallback(c,b,cx,cy,cz,scale);
            }

            for(ReviewIssue r:scene.reviewIssues){
                if(clean && (r.decision==ReviewIssue.Decision.REMOVE_AS_OBJECT
                        || r.decision==ReviewIssue.Decision.REMOVE_AS_ARTIFACT)) continue;
                drawBox(c,r,cx,cy,cz,scale);
            }
        }


        private void drawWallPrism(Canvas c,PlanWallSegment w,double floorY,double ceilY,double cx,double cy,double cz,float scale){
            double[][] v={{w.x1,floorY,w.z1},{w.x2,floorY,w.z2},{w.x1,ceilY,w.z1},{w.x2,ceilY,w.z2}};
            int[][] e={{0,1},{2,3},{0,2},{1,3}};
            for(int[] edge:e)drawProjectedLine(c,v[edge[0]],v[edge[1]],cx,cy,cz,scale);
        }

        private void drawBoundsFallback(Canvas c,RoomBounds b,double cx,double cy,double cz,float scale){
            double[][] v={{b.minX,b.floorY,b.minZ},{b.maxX,b.floorY,b.minZ},{b.maxX,b.floorY,b.maxZ},{b.minX,b.floorY,b.maxZ},
                    {b.minX,b.ceilingY,b.minZ},{b.maxX,b.ceilingY,b.minZ},{b.maxX,b.ceilingY,b.maxZ},{b.minX,b.ceilingY,b.maxZ}};
            int[][] e={{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
            for(int[] edge:e)drawProjectedLine(c,v[edge[0]],v[edge[1]],cx,cy,cz,scale);
        }

        private void drawProjectedLine(Canvas c,double[] a,double[] b,double cx,double cy,double cz,float scale){
            float[] p1=project(a[0]-cx,a[1]-cy,a[2]-cz,scale),p2=project(b[0]-cx,b[1]-cy,b[2]-cz,scale);
            c.drawLine(p1[0],p1[1],p2[0],p2[1],wallPaint);
        }

        private void drawBox(Canvas c,ReviewIssue r,double cx,double cy,double cz,float scale){
            double[][] v={
                    {r.minX,r.minY,r.minZ},{r.maxX,r.minY,r.minZ},
                    {r.maxX,r.minY,r.maxZ},{r.minX,r.minY,r.maxZ},
                    {r.minX,r.maxY,r.minZ},{r.maxX,r.maxY,r.minZ},
                    {r.maxX,r.maxY,r.maxZ},{r.minX,r.maxY,r.maxZ}
            };
            int[][] e={{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},
                    {0,4},{1,5},{2,6},{3,7}};
            for(int[] edge:e){
                float[] a=project(v[edge[0]][0]-cx,v[edge[0]][1]-cy,v[edge[0]][2]-cz,scale);
                float[] d=project(v[edge[1]][0]-cx,v[edge[1]][1]-cy,v[edge[1]][2]-cz,scale);
                c.drawLine(a[0],a[1],d[0],d[1],issuePaint);
            }
        }

        private float[] project(double x,double y,double z,float scale){
            double yr=Math.toRadians(yaw), pr=Math.toRadians(pitch);
            double x1=x*Math.cos(yr)-z*Math.sin(yr);
            double z1=x*Math.sin(yr)+z*Math.cos(yr);
            double y1=y*Math.cos(pr)-z1*Math.sin(pr);
            return new float[]{getWidth()/2f+(float)x1*scale,
                    getHeight()/2f-(float)y1*scale};
        }

        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                lastX=e.getX();lastY=e.getY();return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                yaw+=(e.getX()-lastX)*0.35f;
                pitch+=(e.getY()-lastY)*0.25f;
                pitch=Math.max(-75,Math.min(75,pitch));
                lastX=e.getX();lastY=e.getY();
                invalidate();return true;
            }
            return true;
        }
    }
}
