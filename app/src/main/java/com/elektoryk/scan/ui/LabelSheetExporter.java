package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.widget.Toast;

import com.elektoryk.scan.model.CableCutPiece;
import com.elektoryk.scan.storage.ExportFileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class LabelSheetExporter {
    private static final int PIECES_PER_SHEET=8;
    private LabelSheetExporter(){}

    public static void share(Activity a,List<CableCutPiece> pieces,String base){
        if(pieces==null||pieces.isEmpty()){
            Toast.makeText(a,"Немає кабельних відрізків для етикеток.",Toast.LENGTH_SHORT).show();return;
        }
        final ArrayList<CableCutPiece> snapshot=new ArrayList<>(pieces);
        Toast.makeText(a,"Готую етикетки…",Toast.LENGTH_SHORT).show();
        StableTaskRunner.run(a,"LABEL_EXPORT",()->buildSheets(a,snapshot,base),uris->{
            if(uris==null||uris.isEmpty()){Toast.makeText(a,"Етикетки не створено.",Toast.LENGTH_LONG).show();return;}
            Intent s=new Intent(uris.size()==1?Intent.ACTION_SEND:Intent.ACTION_SEND_MULTIPLE);s.setType("image/png");
            if(uris.size()==1)s.putExtra(Intent.EXTRA_STREAM,uris.get(0));else s.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);
            ClipData clip=ClipData.newRawUri("ELEKTORYK labels",uris.get(0));
            for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));
            s.setClipData(clip);s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(s,"Надрукувати / передати етикетки"));
        },e->Toast.makeText(a,"Етикетки не створено: "+safeMessage(e),Toast.LENGTH_LONG).show());
    }

    private static ArrayList<Uri> buildSheets(Activity a,List<CableCutPiece> pieces,String base)throws Exception{
        File dir=new File(a.getCacheDir(),"exports");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Cannot create export dir");
        ArrayList<Uri> uris=new ArrayList<>();
        int sheetCount=(pieces.size()+PIECES_PER_SHEET-1)/PIECES_PER_SHEET;
        for(int sheet=0;sheet<sheetCount;sheet++){
            int from=sheet*PIECES_PER_SHEET,to=Math.min(pieces.size(),from+PIECES_PER_SHEET);
            File f=renderSheet(dir,pieces.subList(from,to),safe(base)+"_LABELS_"+(sheet+1)+"of"+sheetCount+".png");
            uris.add(new Uri.Builder().scheme("content").authority(ExportFileProvider.AUTHORITY).appendPath(f.getName()).build());
        }
        return uris;
    }

    private static File renderSheet(File dir,List<CableCutPiece> pieces,String name)throws Exception{
        int width=1200,labelH=190,gap=18,height=Math.max(460,pieces.size()*2*(labelH+gap)+40);
        Bitmap b=null;
        try{
            b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.WHITE);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);int y=20;
            for(CableCutPiece piece:pieces){drawLabel(c,p,20,y,width-40,labelH,piece,true);y+=labelH+gap;drawLabel(c,p,20,y,width-40,labelH,piece,false);y+=labelH+gap;}
            File f=new File(dir,name);try(FileOutputStream out=new FileOutputStream(f)){if(!b.compress(Bitmap.CompressFormat.PNG,100,out))throw new IllegalStateException("PNG encode failed");}
            return f;
        }finally{if(b!=null&&!b.isRecycled())b.recycle();}
    }

    private static void drawLabel(Canvas c,Paint p,int x,int y,int w,int h,CableCutPiece piece,boolean start){
        p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(12,12,14));c.drawRoundRect(x,y,x+w,y+h,24,24,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setColor(Color.rgb(210,160,38));c.drawRoundRect(x,y,x+w,y+h,24,24,p);
        String side=start?"A":"B";String from=start?piece.fromLabel:piece.toLabel;String to=start?piece.toLabel:piece.fromLabel;String arrow=start?"→":"←";
        p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(226,177,54));p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(42);c.drawText("ELEKTORYK  "+piece.code+" "+side,x+28,y+52,p);
        p.setColor(Color.WHITE);p.setTextSize(34);c.drawText(piece.cableKey+(piece.protectionTag==null||piece.protectionTag.isEmpty()?"":"  •  "+piece.protectionTag)+"  •  CUT "+String.format(java.util.Locale.US,"%.1f m",piece.cutLengthM),x+28,y+102,p);
        p.setTextSize(28);p.setColor(Color.rgb(220,220,225));String line=from+" "+arrow+" "+to;if(line.length()>56)line=line.substring(0,56);c.drawText(line,x+28,y+148,p);
        p.setTextSize(20);p.setColor(Color.rgb(150,150,158));c.drawText("G"+piece.circuitNo+" • reserve "+String.format(java.util.Locale.US,"%.0f%%",piece.percentReserve),x+w-310,y+52,p);
    }
    private static String safe(String s){return s==null?"elektoryk":s.replaceAll("[^a-zA-Z0-9А-Яа-яІіЇїЄє_-]+","_");}
    private static String safeMessage(Throwable e){String m=e==null?null:e.getMessage();return m==null||m.trim().isEmpty()?(e==null?"невідома помилка":e.getClass().getSimpleName()):m;}
}
