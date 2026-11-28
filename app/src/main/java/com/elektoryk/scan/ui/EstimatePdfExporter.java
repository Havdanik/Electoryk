package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.widget.Toast;

import com.elektoryk.scan.model.CommercialLine;
import com.elektoryk.scan.model.CommercialPlan;
import com.elektoryk.scan.storage.ExportFileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

public final class EstimatePdfExporter{
    private EstimatePdfExporter(){}

    public static void share(Activity a,CommercialPlan plan,String projectName){
        if(plan==null){Toast.makeText(a,"Кошторис ще не готовий.",Toast.LENGTH_SHORT).show();return;}
        Toast.makeText(a,"Готую PDF…",Toast.LENGTH_SHORT).show();
        StableTaskRunner.run(a,"ESTIMATE_PDF",()->create(a,plan,projectName),f->{
            Uri uri=new Uri.Builder().scheme("content").authority(ExportFileProvider.AUTHORITY).appendPath(f.getName()).build();
            Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/pdf");i.putExtra(Intent.EXTRA_STREAM,uri);i.setClipData(ClipData.newRawUri("ELEKTORYK estimate",uri));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(i,"Кошторис PDF"));
        },e->Toast.makeText(a,"PDF: "+safeMessage(e),Toast.LENGTH_LONG).show());
    }

    private static File create(Activity a,CommercialPlan plan,String projectName)throws Exception{
        plan.recalc();
        File d=new File(a.getCacheDir(),"exports");if(!d.exists()&&!d.mkdirs())throw new IllegalStateException("Cannot create export dir");
        File f=new File(d,"estimate_"+System.currentTimeMillis()+".pdf");
        PdfDocument pdf=new PdfDocument();
        try{
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);int pageW=595,pageH=842,y=55,pageNo=1;
            PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(pageW,pageH,pageNo).create());Canvas c=page.getCanvas();
            p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(22);c.drawText("ELEKTORYK • КОШТОРИС",36,y,p);y+=28;
            p.setTypeface(Typeface.DEFAULT);p.setTextSize(12);c.drawText(projectName==null?"Об'єкт":projectName,36,y,p);y+=28;
            for(CommercialLine l:plan.lines){
                if(!l.includeInEstimate)continue;
                if(y>790){pdf.finishPage(page);pageNo++;page=pdf.startPage(new PdfDocument.PageInfo.Builder(pageW,pageH,pageNo).create());c=page.getCanvas();y=45;}
                p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(11);c.drawText(shorten(l.name,38),36,y,p);
                p.setTypeface(Typeface.DEFAULT);String line=String.format(Locale.US,"%.2f %s × %.2f = %.2f грн",l.requiredQty,l.unit,l.unitClientPrice,l.clientTotal());c.drawText(shorten(line,44),285,y,p);y+=18;
            }
            if(y>735){pdf.finishPage(page);pageNo++;page=pdf.startPage(new PdfDocument.PageInfo.Builder(pageW,pageH,pageNo).create());c=page.getCanvas();y=55;}
            y+=12;p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(14);c.drawText(String.format(Locale.US,"Матеріали: %.2f грн",plan.materialClient),36,y,p);y+=20;c.drawText(String.format(Locale.US,"Роботи: %.2f грн",plan.laborClient),36,y,p);y+=20;c.drawText(String.format(Locale.US,"РАЗОМ: %.2f грн",plan.total),36,y,p);y+=24;
            p.setTypeface(Typeface.DEFAULT);p.setTextSize(9);c.drawText("Розрахунок сформований ELEKTORYK. Фактична ціна може змінитись при погоджених правках.",36,y,p);
            pdf.finishPage(page);
            try(FileOutputStream out=new FileOutputStream(f)){pdf.writeTo(out);}
            if(!f.exists()||f.length()<100)throw new IllegalStateException("PDF file is empty");
            return f;
        }finally{pdf.close();}
    }
    private static String shorten(String s,int max){if(s==null)return "";return s.length()<=max?s:s.substring(0,Math.max(1,max-1))+"…";}
    private static String safeMessage(Throwable e){String m=e==null?null:e.getMessage();return m==null||m.trim().isEmpty()?(e==null?"невідома помилка":e.getClass().getSimpleName()):m;}
}
