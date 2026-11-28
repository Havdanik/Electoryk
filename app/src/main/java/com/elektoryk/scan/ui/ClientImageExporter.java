package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import com.elektoryk.scan.storage.ExportFileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public final class ClientImageExporter {
    private ClientImageExporter(){}

    public static void saveAndShare(Activity activity,View view,String baseName){
        if(view==null||view.getWidth()<=0||view.getHeight()<=0){
            Toast.makeText(activity,"Візуалізація ще не готова.",Toast.LENGTH_SHORT).show();return;
        }
        Bitmap bitmap=null;
        try{
            bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bitmap);view.draw(canvas);
            String fileName=safe(baseName)+"_"+System.currentTimeMillis()+".png";
            Uri uri=save(activity,bitmap,fileName);
            if(uri==null)throw new IllegalStateException("Не вдалося отримати URI PNG");
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM,uri);
            send.setClipData(ClipData.newRawUri("ELEKTORYK image",uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(send,"Поділитися планом"));
        }catch(Exception e){
            Toast.makeText(activity,"Не вдалося зберегти PNG: "+safeMessage(e),Toast.LENGTH_LONG).show();
        }finally{
            if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();
        }
    }

    private static Uri save(Activity a,Bitmap bitmap,String name) throws Exception{
        if(Build.VERSION.SDK_INT>=29){
            ContentResolver resolver=a.getContentResolver();
            ContentValues values=new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,name);
            values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/Elektoryk");
            values.put(MediaStore.Images.Media.IS_PENDING,1);
            Uri uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            if(uri==null)throw new IllegalStateException("MediaStore insert failed");
            boolean committed=false;
            try{
                try(OutputStream out=resolver.openOutputStream(uri)){
                    if(out==null)throw new IllegalStateException("No output stream");
                    if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IllegalStateException("PNG encode failed");
                }
                values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);
                resolver.update(uri,values,null,null);committed=true;return uri;
            }finally{
                if(!committed)try{resolver.delete(uri,null,null);}catch(Exception ignored){}
            }
        }

        // Android 8/9: keep export in cache/exports so ExportFileProvider can grant access to other apps.
        File dir=new File(a.getCacheDir(),"exports");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Cannot create export dir");
        File f=new File(dir,name);
        try(FileOutputStream out=new FileOutputStream(f)){
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IllegalStateException("PNG encode failed");
        }
        return new Uri.Builder().scheme("content").authority(ExportFileProvider.AUTHORITY).appendPath(f.getName()).build();
    }

    private static String safe(String s){
        if(s==null||s.trim().isEmpty())return "elektoryk_plan";
        return s.replaceAll("[^a-zA-Z0-9А-Яа-яІіЇїЄє_-]+","_");
    }
    private static String safeMessage(Throwable e){String m=e==null?null:e.getMessage();return m==null||m.trim().isEmpty()?(e==null?"невідома помилка":e.getClass().getSimpleName()):m;}
}
