package com.elektoryk.scan.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

public class SignatureView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private boolean empty=true;
    public SignatureView(Context c){super(c);setBackgroundColor(Color.rgb(245,245,245));paint.setColor(Color.BLACK);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(c,3));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawPath(path,paint);}
    @Override public boolean onTouchEvent(MotionEvent e){float x=e.getX(),y=e.getY();switch(e.getAction()){case MotionEvent.ACTION_DOWN:path.moveTo(x,y);empty=false;invalidate();return true;case MotionEvent.ACTION_MOVE:path.lineTo(x,y);invalidate();return true;case MotionEvent.ACTION_UP:path.lineTo(x,y);invalidate();return true;}return true;}
    public void clear(){path.reset();empty=true;invalidate();}
    public boolean isEmpty(){return empty;}
    public Bitmap bitmap(){Bitmap b=Bitmap.createBitmap(Math.max(1,getWidth()),Math.max(1,getHeight()),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);draw(c);return b;}
}
