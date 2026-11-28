package com.elektoryk.scan.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.elektoryk.scan.scanner.LiveScanCoverage;

/**
 * Real 3D live coverage preview. v0.16.8 still rendered only an X/Z floor-plan heatmap,
 * which hid whether floor, wall or upper geometry had actually been captured.
 */
public class LiveScanCoverageView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private LiveScanCoverage.Snapshot snapshot;

    public LiveScanCoverageView(Context context){
        super(context);
        setMinimumWidth(Ui.dp(context,180));
        setMinimumHeight(Ui.dp(context,214));
    }

    public void setSnapshot(LiveScanCoverage.Snapshot s){snapshot=s;invalidate();}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float pad=Ui.dp(getContext(),10);
        RectF card=new RectF(0,0,getWidth(),getHeight());
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(214,7,7,9));
        c.drawRoundRect(card,Ui.dp(getContext(),14),Ui.dp(getContext(),14),p);

        p.setTextSize(Ui.dp(getContext(),11));
        p.setColor(Color.rgb(245,245,246));
        p.setFakeBoldText(true);
        c.drawText("3D LIVE SCAN",pad,Ui.dp(getContext(),18),p);
        p.setFakeBoldText(false);

        if(snapshot==null||snapshot.voxels.isEmpty()){
            p.setTextSize(Ui.dp(getContext(),10));
            p.setColor(Color.rgb(160,160,168));
            c.drawText("Потрібен рух + Depth",pad,Ui.dp(getContext(),39),p);
            c.drawText("Низ/підлога • стіни • верх",pad,Ui.dp(getContext(),54),p);
            return;
        }

        p.setTextSize(Ui.dp(getContext(),10));
        p.setColor(Color.rgb(255,196,0));
        c.drawText("3D підтверджено "+snapshot.coveragePercent+"%",pad,Ui.dp(getContext(),34),p);
        p.setTextSize(Ui.dp(getContext(),9));
        p.setColor(Color.rgb(186,186,194));
        c.drawText("низ "+snapshot.lowBandPercent+" • стіни "+snapshot.midBandPercent+" • верх "+snapshot.highBandPercent,pad,Ui.dp(getContext(),48),p);
        c.drawText("multi-view "+snapshot.multiViewPercent+"% • struct "+snapshot.architecturalVoxelCount+" • plane "+snapshot.planeSupportedVoxels,pad,Ui.dp(getContext(),60),p);

        float left=pad;
        float top=Ui.dp(getContext(),69);
        float right=getWidth()-pad;
        float bottom=getHeight()-Ui.dp(getContext(),27);

        // Isometric projection of actual X/Y/Z endpoint voxels. Y stays vertical on screen,
        // so a scanned wall rises above the floor instead of collapsing into the same 2D cell.
        float minPX=Float.POSITIVE_INFINITY,maxPX=Float.NEGATIVE_INFINITY;
        float minPY=Float.POSITIVE_INFINITY,maxPY=Float.NEGATIVE_INFINITY;
        for(LiveScanCoverage.VoxelView v:snapshot.voxels){
            float vx=v.ix+0.5f,vy=v.iy+0.5f,vz=v.iz+0.5f;
            float px=isoX(vx,vz),py=isoY(vx,vy,vz);
            minPX=Math.min(minPX,px);maxPX=Math.max(maxPX,px);
            minPY=Math.min(minPY,py);maxPY=Math.max(maxPY,py);
        }
        float camX=snapshot.cameraX/(float)LiveScanCoverage.VOXEL_M;
        float camY=snapshot.cameraY/(float)LiveScanCoverage.VOXEL_M;
        float camZ=snapshot.cameraZ/(float)LiveScanCoverage.VOXEL_M;
        float camPX=isoX(camX,camZ),camPY=isoY(camX,camY,camZ);
        minPX=Math.min(minPX,camPX);maxPX=Math.max(maxPX,camPX);
        minPY=Math.min(minPY,camPY);maxPY=Math.max(maxPY,camPY);

        float spanX=Math.max(1f,maxPX-minPX),spanY=Math.max(1f,maxPY-minPY);
        float scale=Math.min((right-left)/spanX,(bottom-top)/spanY);
        scale=Math.min(scale,Ui.dp(getContext(),11));
        float drawW=spanX*scale,drawH=spanY*scale;
        float ox=left+((right-left)-drawW)/2f-minPX*scale;
        float oy=top+((bottom-top)-drawH)/2f-minPY*scale;

        float pointR=Math.max(1.2f,Math.min(Ui.dp(getContext(),3),scale*0.20f));
        for(LiveScanCoverage.VoxelView v:snapshot.voxels){
            float vx=v.ix+0.5f,vy=v.iy+0.5f,vz=v.iz+0.5f;
            float x=ox+isoX(vx,vz)*scale,y=oy+isoY(vx,vy,vz)*scale;
            if(v.state<=1)p.setColor(Color.argb(205,255,126,40));
            else if(v.state==2)p.setColor(Color.argb(220,255,196,0));
            else p.setColor(Color.argb(235,232,232,236));
            c.drawCircle(x,y,pointR,p);
        }

        // Camera marker inside the same 3D projection.
        float cx=ox+camPX*scale,cy=oy+camPY*scale;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Ui.dp(getContext(),2));
        p.setColor(Color.rgb(255,72,82));
        c.drawCircle(cx,cy,Ui.dp(getContext(),5),p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx,cy,Ui.dp(getContext(),2),p);

        p.setTextSize(Ui.dp(getContext(),9));
        p.setColor(Color.rgb(160,160,168));
        c.drawText("● добре   ● слабко   ○ камера",pad,getHeight()-Ui.dp(getContext(),9),p);
    }

    private static float isoX(float x,float z){ return (x-z)*0.72f; }
    private static float isoY(float x,float y,float z){
        // A stable fixed isometric view: X/Z form the floor plane; Y is real scan height.
        return -(y*1.05f)+(x+z)*0.34f;
    }
}
