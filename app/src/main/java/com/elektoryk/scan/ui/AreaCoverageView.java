package com.elektoryk.scan.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import com.elektoryk.scan.model.RoomBounds;
import com.elektoryk.scan.model.ScanGap;
import com.elektoryk.scan.model.ScanPathPoint;

import java.util.ArrayList;
import java.util.List;

/** Field map: reconstructed footprint + actual walk path + low-coverage zones. */
public class AreaCoverageView extends View {
    private RoomBounds bounds;
    private final List<ScanGap> gaps = new ArrayList<>();
    private final List<ScanPathPoint> path = new ArrayList<>();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public AreaCoverageView(Context c) { super(c); setMinimumHeight(Ui.dp(c,240)); }

    public void setData(RoomBounds b, List<ScanGap> g) { setData(b,g,null); }

    public void setData(RoomBounds b, List<ScanGap> g, List<ScanPathPoint> scanPath) {
        bounds=b;
        gaps.clear(); if(g!=null)gaps.addAll(g);
        path.clear(); if(scanPath!=null)path.addAll(scanPath);
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(Color.rgb(17,17,21));
        if(bounds==null || !bounds.isUsable()){
            p.setColor(Color.rgb(164,164,173)); p.setTextSize(Ui.dp(getContext(),15));
            c.drawText("Немає придатного контуру",Ui.dp(getContext(),18),Ui.dp(getContext(),42),p);
            return;
        }
        float pad=Ui.dp(getContext(),22);
        float aw=getWidth()-2*pad, ah=getHeight()-2*pad;
        double rw=bounds.widthM(), rh=bounds.lengthM();
        float scale=(float)Math.min(aw/rw,ah/rh);
        float w=(float)(rw*scale), h=(float)(rh*scale);
        float ox=(getWidth()-w)/2f, oy=(getHeight()-h)/2f;

        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(34,34,41));
        c.drawRect(ox,oy,ox+w,oy+h,p);

        // Actual operator path helps make the next pass intentional.
        if(path.size()>1){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),2));p.setColor(Color.rgb(255,196,0));
            ScanPathPoint prev=null;
            for(ScanPathPoint q:path){
                if(prev!=null){
                    float x1=ox+(float)((prev.x-bounds.minX)*scale);
                    float y1=oy+(float)((prev.z-bounds.minZ)*scale);
                    float x2=ox+(float)((q.x-bounds.minX)*scale);
                    float y2=oy+(float)((q.z-bounds.minZ)*scale);
                    c.drawLine(x1,y1,x2,y2,p);
                }
                prev=q;
            }
        }

        for(ScanGap g:gaps){
            float l=ox+(float)((g.minX-bounds.minX)*scale);
            float r=ox+(float)((g.maxX-bounds.minX)*scale);
            float t=oy+(float)((g.minZ-bounds.minZ)*scale);
            float bb=oy+(float)((g.maxZ-bounds.minZ)*scale);
            p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(180,255,90,95));
            c.drawRect(l,t,r,bb,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),1));p.setColor(Color.rgb(255,190,194));
            c.drawRect(l,t,r,bb,p);
        }

        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Ui.dp(getContext(),2)); p.setColor(Color.rgb(255,196,0));
        c.drawRect(ox,oy,ox+w,oy+h,p);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(244,244,245)); p.setTextSize(Ui.dp(getContext(),13));
        c.drawText(String.format(java.util.Locale.US,"%.2f × %.2f м",rw,rh),ox,Math.max(Ui.dp(getContext(),16),oy-Ui.dp(getContext(),7)),p);
        p.setTextSize(Ui.dp(getContext(),10));p.setColor(Color.rgb(164,164,173));
        c.drawText("жовта лінія — твій прохід • червоне — досканувати",ox,Math.min(getHeight()-Ui.dp(getContext(),5),oy+h+Ui.dp(getContext(),16)),p);
    }
}
