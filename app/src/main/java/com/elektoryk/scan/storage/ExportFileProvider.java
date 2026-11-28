package com.elektoryk.scan.storage;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public class ExportFileProvider extends ContentProvider {
    public static final String AUTHORITY="com.elektoryk.scan.exports";
    @Override public boolean onCreate(){return true;}
    private File resolve(Uri uri) throws FileNotFoundException{
        String name=uri==null?null:uri.getLastPathSegment();
        if(!StoredFileNameGuard.isSafeLeaf(name))throw new FileNotFoundException("Unsafe export path");
        File f=new File(new File(getContext().getCacheDir(),"exports"),name);if(!f.isFile())throw new FileNotFoundException(name);return f;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(mode!=null&&!"r".equals(mode))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri){String n=uri==null?null:uri.getLastPathSegment();if(n!=null&&n.endsWith(".elektoryk"))return ProjectPackageManager.MIME;if(n!=null&&n.endsWith(".html"))return "text/html";if(n!=null&&n.endsWith(".png"))return "image/png";if(n!=null&&n.endsWith(".pdf"))return "application/pdf";return "application/octet-stream";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){
        try{File f=resolve(uri);String[] cols=(projection==null||projection.length==0)?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]=f.getName();else if(OpenableColumns.SIZE.equals(cols[i]))row[i]=f.length();else row[i]=null;}c.addRow(row);return c;}catch(Exception e){return null;}
    }
    @Override public int delete(Uri uri,String s,String[] a){return 0;}
    @Override public Uri insert(Uri uri,ContentValues v){return null;}
    @Override public int update(Uri uri,ContentValues v,String s,String[] a){return 0;}
}
