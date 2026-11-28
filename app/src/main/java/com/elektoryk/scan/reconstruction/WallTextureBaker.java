package com.elektoryk.scan.reconstruction;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;

import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.PlanWallSegment;
import com.elektoryk.scan.scanner.ar.ScanVisualKeyframeRecorder;
import com.elektoryk.scan.storage.ProjectIdGuard;
import com.elektoryk.scan.storage.AtomicFileStore;
import com.elektoryk.scan.storage.StoredFileNameGuard;
import com.google.ar.core.Pose;

import java.io.File;
import java.io.FileOutputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Builds a non-repeating photo mosaic for every reconstructed wall.
 *
 * v0.18.0 baked one ~0.8 m camera patch and DigitalTwinView repeated it like wallpaper across
 * the whole wall.  That produced duplicated wardrobes/doors and was not a scanned texture.
 * This baker divides the physical wall into metric tiles and projects each tile into the best RGB
 * keyframe.  Every piece is placed exactly once in wall UV space; missing evidence stays neutral.
 */
public final class WallTextureBaker {
    private static final double TILE_M=0.52;
    private static final int PX_PER_M=180;
    private static final int MIN_W=240,MAX_W=1400,MIN_H=360,MAX_H=900;
    // Recorder keyframes are intentionally modest. Never let a corrupt/replaced JPEG turn the
    // reconstruction stage into an unbounded Bitmap allocation.
    private static final long MAX_KEYFRAME_PIXELS=8L*1024L*1024L;
    // D16 companions are small compared with RGB bitmaps, but keeping every decoded grid from a
    // large apartment could still retain tens of MB until reconstruction ends. Keep a bounded
    // access-order cache; neighboring wall tiles usually reuse the same recent keyframes.
    private static final int MAX_DEPTH_GRIDS=12;
    private final LinkedHashMap<String,DepthGrid> depthCache=new LinkedHashMap<String,DepthGrid>(16,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,DepthGrid> eldest){
            return size()>MAX_DEPTH_GRIDS;
        }
    };

    public int bake(Context c,String projectId,ApartmentPlan plan,List<ScanVisualKeyframeRecorder.Meta> frames,
                    double normalizationYawRad,ScanFusionEngine.Result fusion){
        if(c==null||plan==null||frames==null||frames.isEmpty())return 0;
        ProjectIdGuard.requireSafe(projectId);
        File projectDir=new File(new File(c.getFilesDir(),"scenes"),projectId);
        File srcDir=new File(projectDir,"visual_pass");
        File outDir=new File(projectDir,"textures");if(!outDir.exists())outDir.mkdirs();
        int done=0;
        depthCache.clear();
        try{
            for(PlanWallSegment w:plan.walls){
                if(w==null||w.lengthM()<0.35)continue;
                BakeResult r;
                try{r=bakeWall(srcDir,frames,w,plan,normalizationYawRad,fusion);}
                catch(OutOfMemoryError oom){continue;}
                if(r==null||r.bitmap==null)continue;
                File out=new File(outDir,"wall_"+safe(w.id)+".png");
                try{
                    writePngAtomic(out,r.bitmap);
                    // Publish metadata only after the PNG is fully synced and committed. A failed
                    // encode must never make scene.json point at a partial/new texture.
                    w.scannedTextureFile=out.getName();w.scannedTextureCoverage=r.coverage;done++;
                }catch(Exception ignored){}finally{r.bitmap.recycle();}
            }
            return done;
        }finally{
            // Do not let a long-lived baker retain decoded Depth evidence after texture generation.
            depthCache.clear();
        }
    }

    private void writePngAtomic(File target,Bitmap bitmap)throws Exception{
        if(target==null||bitmap==null||bitmap.isRecycled())throw new java.io.IOException("Texture missing");
        File parent=target.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new java.io.IOException("Cannot create texture directory");
        File staged=new File(parent,target.getName()+".part");if(staged.exists()&&!staged.delete())throw new java.io.IOException("Cannot clear stale texture staging file");
        try{
            try(FileOutputStream fos=new FileOutputStream(staged,false)){
                if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,fos))throw new java.io.IOException("PNG encoder rejected texture");
                fos.flush();fos.getFD().sync();
            }
            if(!staged.isFile()||staged.length()<=0)throw new java.io.IOException("Empty staged texture");
            AtomicFileStore.replaceBinary(target,staged);
        }finally{if(staged.exists())staged.delete();}
    }

    private BakeResult bakeWall(File srcDir,List<ScanVisualKeyframeRecorder.Meta> frames,
                                PlanWallSegment w,ApartmentPlan plan,double yaw,ScanFusionEngine.Result fusion){
        double len=w.lengthM();double height=Math.max(1.8,plan.ceilingY-plan.floorY);
        int tw=clamp((int)Math.round(len*PX_PER_M),MIN_W,MAX_W);
        int th=clamp((int)Math.round(height*PX_PER_M),MIN_H,MAX_H);
        Bitmap tex=Bitmap.createBitmap(tw,th,Bitmap.Config.ARGB_8888);
        boolean textureOwnershipTransferred=false;
        Canvas canvas=new Canvas(tex);canvas.drawColor(Color.TRANSPARENT);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        double dx=(w.x2-w.x1)/Math.max(.001,len),dz=(w.z2-w.z1)/Math.max(.001,len);
        int nx=Math.max(1,(int)Math.ceil(len/TILE_M));int ny=Math.max(1,(int)Math.ceil(height/TILE_M));
        int success=0,total=nx*ny;
        String[][] chosen=new String[ny][nx];
        // A wall can contain dozens of metric tiles. Decoding the same 640px JPEG separately for
        // every tile made finalization CPU/GC heavy and could look like a frozen scan on large
        // rooms. Keep only a tiny per-wall LRU: enough to exploit neighboring-tile continuity
        // without retaining all (up to 80) keyframes in native Bitmap memory.
        final LinkedHashMap<String,Bitmap> bitmapCache=new LinkedHashMap<String,Bitmap>(4,0.75f,true);
        // Reuse transform/destination scratch across tiles. Matrix + float[8] per tile is small,
        // but a large apartment can bake hundreds of tiles in one burst while Bitmap pressure is
        // already high. Keeping these objects wall-local removes avoidable finalization GC churn.
        final Matrix tileMatrix=new Matrix();
        final float[] dstQuad=new float[8];
        // Texture baking can evaluate hundreds of wall tiles against dozens of keyframes. Keep
        // all geometric/projection scratch wall-local so finalization does not manufacture arrays
        // and Choice objects for every tile while large Bitmaps are resident.
        final TileProjectionScratch projectionScratch=new TileProjectionScratch();

        try{
        try{for(int iy=0;iy<ny;iy++){
            double y0=height*iy/ny,y1=height*(iy+1)/ny;
            for(int ix=0;ix<nx;ix++){
                double s0=len*ix/nx,s1=len*(ix+1)/nx;
                double[] finalPts=projectionScratch.finalPts;
                finalPts[0]=w.x1+dx*s0;finalPts[1]=plan.floorY+y0;finalPts[2]=w.z1+dz*s0;
                finalPts[3]=w.x1+dx*s1;finalPts[4]=plan.floorY+y0;finalPts[5]=w.z1+dz*s1;
                finalPts[6]=w.x1+dx*s1;finalPts[7]=plan.floorY+y1;finalPts[8]=w.z1+dz*s1;
                finalPts[9]=w.x1+dx*s0;finalPts[10]=plan.floorY+y1;finalPts[11]=w.z1+dz*s0;
                String preferred=ix>0?chosen[iy][ix-1]:(iy>0?chosen[iy-1][ix]:null);
                Choice choice=bestFrame(srcDir,frames,finalPts,yaw,fusion,preferred,projectionScratch);if(choice==null)continue;
                Bitmap src=bitmapCache.get(choice.file.getAbsolutePath());
                if(src==null||src.isRecycled()){
                    src=decodeKeyframeSafe(choice.file);if(src==null)continue;
                    bitmapCache.put(choice.file.getAbsolutePath(),src);
                    if(bitmapCache.size()>3){
                        String eldest=bitmapCache.keySet().iterator().next();
                        Bitmap evicted=bitmapCache.remove(eldest);if(evicted!=null&&!evicted.isRecycled())evicted.recycle();
                    }
                }
                float x0=tw*ix/(float)nx,x1=tw*(ix+1)/(float)nx;
                // Texture Y=0 is top of wall, while physical y0 starts at floor.
                float tyBottom=th*(1f-iy/(float)ny),tyTop=th*(1f-(iy+1)/(float)ny);
                dstQuad[0]=x0;dstQuad[1]=tyBottom;dstQuad[2]=x1;dstQuad[3]=tyBottom;
                dstQuad[4]=x1;dstQuad[5]=tyTop;dstQuad[6]=x0;dstQuad[7]=tyTop;
                if(tileMatrix.setPolyToPoly(choice.srcQuad,0,dstQuad,0,4)){
                    int save=canvas.save();canvas.clipRect(new RectF(x0,tyTop,x1,tyBottom));
                    canvas.drawBitmap(src,tileMatrix,paint);canvas.restoreToCount(save);success++;chosen[iy][ix]=choice.file.getName();
                }
            }
        }}finally{
            for(Bitmap b:bitmapCache.values())if(b!=null&&!b.isRecycled())b.recycle();
            bitmapCache.clear();
        }
        }catch(OutOfMemoryError failure){
            // Bitmap native memory is most valuable exactly when allocation pressure is already
            // high. Never rely on a later GC to reclaim an unfinished wall texture after OOM.
            if(!textureOwnershipTransferred&&!tex.isRecycled())tex.recycle();
            throw failure;
        }catch(RuntimeException failure){
            if(!textureOwnershipTransferred&&!tex.isRecycled())tex.recycle();
            throw failure;
        }
        if(success==0){tex.recycle();return null;}
        BakeResult r=new BakeResult();r.bitmap=tex;r.coverage=success/(double)Math.max(1,total);
        textureOwnershipTransferred=true;
        return r;
    }

    private Bitmap decodeKeyframeSafe(File file){
        if(file==null||!file.isFile()||file.length()<=0)return null;
        try{
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0)return null;
            long pixels=(long)bounds.outWidth*(long)bounds.outHeight;
            if(pixels<=0||pixels>MAX_KEYFRAME_PIXELS)return null;
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        }catch(OutOfMemoryError oom){
            return null;
        }catch(Throwable badImage){
            return null;
        }
    }

    private Choice bestFrame(File srcDir,List<ScanVisualKeyframeRecorder.Meta> frames,double[] finalPts,
                             double yaw,ScanFusionEngine.Result fusion,String preferredFile,TileProjectionScratch scratch){
        // final->raw transform and all projection buffers are reused for the whole wall. This is
        // intentionally wall-local rather than a baker field so separate baker instances/calls do
        // not share mutable state.
        double[] rawPts=scratch.rawPts;
        for(int i=0;i<4;i++)inverseFinal(finalPts[i*3],finalPts[i*3+1],finalPts[i*3+2],yaw,fusion,rawPts,i*3);
        double ex=rawPts[3]-rawPts[0],ey=rawPts[4]-rawPts[1],ez=rawPts[5]-rawPts[2];
        double vx=rawPts[9]-rawPts[0],vy=rawPts[10]-rawPts[1],vz=rawPts[11]-rawPts[2];
        double nx=ey*vz-ez*vy,ny=ez*vx-ex*vz,nz=ex*vy-ey*vx,nl=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(nl<1e-6)return null;nx/=nl;ny/=nl;nz/=nl;
        double cx=(rawPts[0]+rawPts[3]+rawPts[6]+rawPts[9])*.25;
        double cy=(rawPts[1]+rawPts[4]+rawPts[7]+rawPts[10])*.25;
        double cz=(rawPts[2]+rawPts[5]+rawPts[8]+rawPts[11])*.25;

        Choice best=scratch.choice;best.file=null;best.score=-1;
        float[] quad=scratch.quad;double[] cornerDepth=scratch.cornerDepth;
        float[] srcPoint=scratch.srcPoint,cam=scratch.cam;
        for(ScanVisualKeyframeRecorder.Meta k:frames){
            if(k==null||!StoredFileNameGuard.isSafeLeaf(k.file))continue;
            File file=new File(srcDir,k.file);if(!file.isFile()||k.width<=0||k.height<=0||k.fx<=0||k.fy<=0)continue;
            Pose inv=k.pose().inverse();boolean ok=true;double minMargin=1,meanDepth=0;
            double cvx=k.tx-cx,cvy=k.ty-cy,cvz=k.tz-cz,cl=Math.sqrt(cvx*cvx+cvy*cvy+cvz*cvz);
            if(cl<1e-6)continue;double incidence=Math.abs((nx*cvx+ny*cvy+nz*cvz)/cl);
            if(incidence<0.50)continue;
            for(int i=0;i<4;i++){
                int o=i*3;srcPoint[0]=(float)rawPts[o];srcPoint[1]=(float)rawPts[o+1];srcPoint[2]=(float)rawPts[o+2];
                inv.transformPoint(srcPoint,0,cam,0);
                double depth=-cam[2];cornerDepth[i]=depth;meanDepth+=depth;if(depth<0.30||depth>5.2){ok=false;break;}
                float u=(float)(k.cx+k.fx*cam[0]/depth);float v=(float)(k.cy-k.fy*cam[1]/depth);
                quad[i*2]=u;quad[i*2+1]=v;
                double mx=Math.min(u,k.width-u)/k.width,my=Math.min(v,k.height-v)/k.height;minMargin=Math.min(minMargin,Math.min(mx,my));
                if(u<3||u>k.width-4||v<3||v>k.height-4){ok=false;break;}
            }
            if(!ok)continue;
            double area=quadArea(quad);if(area<520)continue;
            meanDepth/=4.0;
            double depthEvidence=depthConsistency(srcDir,k,quad,cornerDepth,meanDepth);if(depthEvidence<0)continue;
            double areaScore=area/(k.width*(double)k.height);
            double distanceScore=1.0/(1.0+Math.abs(meanDepth-1.5)*0.45);
            double continuity=(preferredFile!=null&&preferredFile.equals(k.file))?0.30:0.0;
            double score=areaScore*1.8+Math.max(0,minMargin)*0.45+distanceScore*0.12+incidence*0.42+depthEvidence*0.32+continuity;
            if(score>best.score){
                best.score=score;best.file=file;System.arraycopy(quad,0,scratch.bestQuad,0,8);
            }
        }
        if(best.file==null)return null;best.srcQuad=scratch.bestQuad;return best;
    }

    /**
     * Returns -1 when the RGB tile is occluded/mismatched, otherwise 0..1 evidence strength.
     * The coarse map was projected from the same accepted scan-local Depth points as geometry.
     */
    private double depthConsistency(File srcDir,ScanVisualKeyframeRecorder.Meta k,float[] quad,double[] cornerDepth,double meanDepth){
        DepthGrid g=loadDepth(srcDir,k);if(g==null)return 0.15; // older/Depth-less frame: usable, but not preferred
        int valid=0,agree=0;
        for(int i=0;i<4;i++){
            int mm=g.sample(quad[i*2],quad[i*2+1],k.width,k.height);if(mm<=0)continue;valid++;
            double observed=mm/1000.0,expected=cornerDepth[i];
            double nearTol=Math.max(0.13,expected*0.055),farTol=Math.max(0.28,expected*0.12);
            if(expected-observed>nearTol)return -1; // furniture/curtain/person is in front of the wall
            if(observed-expected>farTol)return -1; // projected geometry does not match the photographed surface
            agree++;
        }
        float cu=(quad[0]+quad[2]+quad[4]+quad[6])*0.25f,cv=(quad[1]+quad[3]+quad[5]+quad[7])*0.25f;
        int center=g.sample(cu,cv,k.width,k.height);if(center>0){valid++;double observed=center/1000.0;
            double nearTol=Math.max(0.13,meanDepth*0.055),farTol=Math.max(0.28,meanDepth*0.12);
            if(meanDepth-observed>nearTol||observed-meanDepth>farTol)return -1;agree++;}
        if(valid==0)return 0.08;
        return Math.min(1.0,agree/(double)valid);
    }

    private DepthGrid loadDepth(File srcDir,ScanVisualKeyframeRecorder.Meta k){
        if(k==null||!StoredFileNameGuard.isSafeLeaf(k.depthFile))return null;
        DepthGrid cached=depthCache.get(k.depthFile);if(cached!=null)return cached;
        File f=new File(srcDir,k.depthFile);if(!f.isFile())return null;
        try(DataInputStream in=new DataInputStream(new FileInputStream(f))){
            if(in.readInt()!=0x44505448)return null;
            int w=in.readInt(),h=in.readInt();
            // Never multiply untrusted dimensions as int: a corrupt/imported D16 header can
            // overflow w*h, bypass the old 200k-cell guard and request a gigantic short[].
            long cells=(long)w*(long)h;
            if(w<8||h<6||cells<=0||cells>200000L)return null;
            // Header + exactly two bytes per cell must fit in the file. Reject truncation before
            // allocating the grid so malformed evidence cannot churn heap/IO during wall scoring.
            long expected=12L+cells*2L;if(f.length()<expected)return null;
            short[] mm=new short[(int)cells];for(int i=0;i<mm.length;i++)mm[i]=(short)in.readUnsignedShort();
            DepthGrid g=new DepthGrid(w,h,mm);depthCache.put(k.depthFile,g);return g;
        }catch(OutOfMemoryError oom){return null;}
        catch(Exception ignored){return null;}
    }

    /** final fused architectural coordinates -> raw scan-local coordinates of this pass. */
    private void inverseFinal(double x,double y,double z,double yaw,ScanFusionEngine.Result f,double[] out,int offset){
        double dx=f==null?0:f.dx,dy=f==null?0:f.dy,dz=f==null?0:f.dz;int q=f==null?0:f.rotationQuarterTurns;
        double X=x-dx,Y=y-dy,Z=z-dz;double nx,nz;switch((q%4+4)%4){case 1:nx=-Z;nz=X;break;case 2:nx=-X;nz=-Z;break;case 3:nx=Z;nz=-X;break;default:nx=X;nz=Z;}
        double c=Math.cos(yaw),s=Math.sin(yaw);out[offset]=c*nx-s*nz;out[offset+1]=Y;out[offset+2]=s*nx+c*nz;
    }
    private double quadArea(float[] q){double a=0;for(int i=0;i<4;i++){int j=(i+1)%4;a+=q[i*2]*q[j*2+1]-q[j*2]*q[i*2+1];}return Math.abs(a)*.5;}
    private int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    private String safe(String s){return s==null?"wall":s.replaceAll("[^A-Za-z0-9_.-]","_");}
    private static final class Choice{File file;float[] srcQuad;double score;}
    private static final class TileProjectionScratch{
        final double[] finalPts=new double[12],rawPts=new double[12],cornerDepth=new double[4];
        final float[] quad=new float[8],bestQuad=new float[8],srcPoint=new float[3],cam=new float[3];
        final Choice choice=new Choice();
    }
    private static final class BakeResult{Bitmap bitmap;double coverage;}
    private static final class DepthGrid{
        final int w,h;final short[] mm;DepthGrid(int w,int h,short[] mm){this.w=w;this.h=h;this.mm=mm;}
        int sample(float u,float v,int iw,int ih){
            if(iw<=0||ih<=0||u<0||v<0||u>=iw||v>=ih)return 0;
            int x=Math.max(0,Math.min(w-1,(int)(u/iw*w))),y=Math.max(0,Math.min(h-1,(int)(v/ih*h)));
            // Sparse accepted Depth points: search a tiny 3x3 neighborhood for the nearest sample.
            int best=0;for(int yy=Math.max(0,y-1);yy<=Math.min(h-1,y+1);yy++)for(int xx=Math.max(0,x-1);xx<=Math.min(w-1,x+1);xx++){
                int q=mm[yy*w+xx]&0xffff;if(q>0&&(best==0||q<best))best=q;
            }return best;
        }
    }
}
