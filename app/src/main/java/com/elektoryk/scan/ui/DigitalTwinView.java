package com.elektoryk.scan.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import com.elektoryk.scan.model.ApartmentOpening;
import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.ElectricalPoint;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.FurnitureItem;
import com.elektoryk.scan.model.PlanGridRun;
import com.elektoryk.scan.model.PlanWallSegment;
import com.elektoryk.scan.model.WallFinishCatalog;
import com.elektoryk.scan.model.RoomZone;
import com.elektoryk.scan.model.RoomFinishCatalog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Field-safe 3D digital twin editor.  The renderer intentionally remains dependency-free
 * (Canvas software projection) so it is reliable in AIDE, but behaves like a real 3D editor:
 * orbit / walk / top camera, pinch zoom, two-finger pan, cutaway, object selection, drag-to-move,
 * photo/solid/wireframe rendering and persistent selection.
 */
public class DigitalTwinView extends View {
    public interface Listener {
        void onWallSelected(PlanWallSegment wall);
        void onPointSelected(ElectricalPoint point);
        void onOpeningSelected(ApartmentOpening opening);
        void onFurnitureSelected(FurnitureItem furniture);
        void onPlacePoint(PlanWallSegment wall, double wallFraction, double heightM);
        void onPlaceFreePoint(double x, double z, RoomZone room, boolean ceiling);
        void onDragPoint(ElectricalPoint point, PlanWallSegment wall, double wallFraction, double heightM, boolean finished);
        void onDragFurniture(FurnitureItem furniture, double x, double z, boolean finished);
        void onFinishWall(PlanWallSegment wall);
        void onPlaceFurniture(double x, double z, RoomZone room);
        void onCameraMessage(String text);
        void onEditGestureStart();
        void onEditGestureFinish();
    }

    private static class P2 {
        float x,y; double depth;
        P2(float x,float y,double depth){this.x=x;this.y=y;this.depth=depth;}
    }
    private static class WallFace {
        PlanWallSegment wall;
        P2 b1,b2,t2,t1;
        double depth;
    }
    private static class FurnitureProjection {
        FurnitureItem item;
        P2[] lo=new P2[4],hi=new P2[4];
        double depth;
        float minX,maxX,minY,maxY;
    }
    private static class OpeningProjection {
        ApartmentOpening opening;
        P2 a,b,c,d;
        double depth;
    }

    private ApartmentPlan plan;
    private FloorPlan floor;
    private String projectId="";
    private static final long MAX_TEXTURE_CACHE_BYTES=32L*1024L*1024L;
    private static final int MAX_TEXTURE_DECODE_DIM=2048;
    private static final long MAX_TEXTURE_SOURCE_PIXELS=32L*1024L*1024L;
    private final Map<String, Bitmap> scannedWallTextures=new LinkedHashMap<String, Bitmap>(16,0.75f,true);
    private long scannedWallTextureBytes;
    private Listener listener;

    /** Editor tool: SELECT / PLACE / FINISH / FURNITURE. */
    private String mode="SELECT";
    /** Camera: ORBIT / WALK / TOP. */
    private String cameraMode="ORBIT";
    /** Visual: PHOTO / SOLID / WIRE. */
    private String renderStyle="PHOTO";

    private String selectedWallId="",selectedPointId="",selectedFurnitureId="",selectedOpeningId="";
    private boolean placeOnCeiling=false;
    private boolean cutaway=true;
    private boolean showCeiling=false;
    private boolean showFurniture=true;
    private boolean showElectrical=true;
    private boolean showOpenings=true;
    private boolean showGrid=true;

    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<WallFace> faces=new ArrayList<>();
    private final List<FurnitureProjection> furnitureProjections=new ArrayList<>();
    private final List<OpeningProjection> openingProjections=new ArrayList<>();

    private double camX=0,camY=1.60,camZ=0;
    private double yawRad=0,pitchRad=Math.toRadians(-8);
    private boolean cameraInitialized=false;

    // Orbit camera state.  Focus is the stable center of what the user is editing.
    private double focusX=0,focusY=1.2,focusZ=0;
    private double orbitDistance=5.0,orbitAzimuth=Math.toRadians(35),orbitElevation=Math.toRadians(18);

    private float downX,downY,lastX,lastY;
    private float lastPinchDistance=0,lastMidX=0,lastMidY=0;
    private boolean moved=false,gestureEditing=false;
    private ElectricalPoint draggingPoint;
    private PlanWallSegment draggingWall;
    private FurnitureItem draggingFurniture;

    public DigitalTwinView(Context c){
        super(c);
        setBackgroundColor(Color.rgb(12,12,15));
        setFocusable(true);
    }

    public void setListener(Listener l){listener=l;}
    public void setProjectId(String id){projectId=id==null?"":id;clearTextureCache();invalidate();}
    public void setData(ApartmentPlan plan,FloorPlan floor){
        this.plan=plan;this.floor=floor;
        if(plan!=null&&!plan.rooms.isEmpty()&&!cameraInitialized){
            computeSceneFocus();cameraInitialized=true;frameAll();
        }
        invalidate();
    }
    public void setMode(String m){mode=m==null?"SELECT":m;draggingPoint=null;draggingWall=null;draggingFurniture=null;invalidate();}
    public String getMode(){return mode;}
    public void setCameraMode(String m){
        cameraMode=m==null?"ORBIT":m;
        if("TOP".equals(cameraMode)){computeSceneFocus();orbitElevation=Math.toRadians(82);orbitDistance=Math.max(3.0,sceneSpan()*1.10);applyOrbitCamera();}
        else if("ORBIT".equals(cameraMode)){computeSceneFocus();orbitElevation=Math.toRadians(18);orbitDistance=Math.max(3.0,sceneSpan()*1.15);applyOrbitCamera();}
        else {resetWalkCamera();}
        invalidate();
    }
    public String getCameraMode(){return cameraMode;}
    public void setRenderStyle(String style){renderStyle=style==null?"PHOTO":style;invalidate();}
    public String getRenderStyle(){return renderStyle;}
    public void setCutaway(boolean v){cutaway=v;invalidate();}
    public boolean isCutaway(){return cutaway;}
    public void setShowCeiling(boolean v){showCeiling=v;invalidate();}
    public void setShowFurniture(boolean v){showFurniture=v;invalidate();}
    public void setShowElectrical(boolean v){showElectrical=v;invalidate();}
    public void setShowOpenings(boolean v){showOpenings=v;invalidate();}
    public void setShowGrid(boolean v){showGrid=v;invalidate();}
    public void setPlaceOnCeiling(boolean value){placeOnCeiling=value;invalidate();}

    public void setSelection(String wallId,String pointId){
        selectedWallId=wallId==null?"":wallId;selectedPointId=pointId==null?"":pointId;
        selectedFurnitureId="";selectedOpeningId="";invalidate();
    }
    public void setFurnitureSelection(String id){selectedFurnitureId=id==null?"":id;selectedWallId="";selectedPointId="";selectedOpeningId="";invalidate();}
    public void setOpeningSelection(String id){selectedOpeningId=id==null?"":id;selectedWallId="";selectedPointId="";selectedFurnitureId="";invalidate();}
    public void clearSelection(){selectedWallId="";selectedPointId="";selectedFurnitureId="";selectedOpeningId="";invalidate();}

    public void frameAll(){
        if(plan==null)return;
        computeSceneFocus();
        orbitDistance=Math.max(2.8,sceneSpan()*1.18);
        if("TOP".equals(cameraMode))orbitElevation=Math.toRadians(82);
        else orbitElevation=Math.toRadians(20);
        applyOrbitCamera();invalidate();
    }
    public void resetCamera(){if("WALK".equals(cameraMode))resetWalkCamera();else frameAll();}
    public void focusWall(PlanWallSegment w){
        if(w==null)return;focusX=w.centerX();focusZ=w.centerZ();focusY=plan.floorY+height()*0.48;
        orbitDistance=Math.max(2.0,w.lengthM()*1.25);orbitElevation=Math.toRadians(12);applyOrbitCamera();invalidate();
    }
    public void focusFurniture(FurnitureItem f){
        if(f==null)return;focusX=f.x;focusZ=f.z;focusY=plan.floorY+f.y+Math.max(0.3,f.heightM*0.5);
        orbitDistance=Math.max(1.6,Math.max(f.widthM,f.depthM)*3.0);orbitElevation=Math.toRadians(22);applyOrbitCamera();invalidate();
    }
    public void focusOpening(ApartmentOpening o){
        if(o==null)return;focusX=o.centerX();focusZ=o.centerZ();focusY=plan.floorY+o.bottomY+Math.max(0.5,o.heightM*0.5);
        orbitDistance=Math.max(1.8,o.widthM*2.5);orbitElevation=Math.toRadians(10);applyOrbitCamera();invalidate();
    }

    // Kept for old buttons / accessibility.
    public void rotateLeft(){if("WALK".equals(cameraMode))yawRad-=Math.toRadians(15);else orbitAzimuth-=Math.toRadians(15);updateCameraAfterControl();}
    public void rotateRight(){if("WALK".equals(cameraMode))yawRad+=Math.toRadians(15);else orbitAzimuth+=Math.toRadians(15);updateCameraAfterControl();}
    public void moveForward(){if("WALK".equals(cameraMode))moveWalk(0.35,0);else zoomBy(0.82);}
    public void moveBack(){if("WALK".equals(cameraMode))moveWalk(-0.35,0);else zoomBy(1.22);}
    public void strafeLeft(){if("WALK".equals(cameraMode))moveWalk(0,-0.30);else panFocus(Ui.dp(getContext(),45),0);}
    public void strafeRight(){if("WALK".equals(cameraMode))moveWalk(0,0.30);else panFocus(-Ui.dp(getContext(),45),0);}

    private void updateCameraAfterControl(){if(!"WALK".equals(cameraMode))applyOrbitCamera();invalidate();}
    private void zoomBy(double factor){orbitDistance=clamp(orbitDistance*factor,0.8,Math.max(25,sceneSpan()*5));applyOrbitCamera();invalidate();}

    private void computeSceneFocus(){
        if(plan==null)return;
        if(!plan.rooms.isEmpty()){
            double minX=Double.POSITIVE_INFINITY,minZ=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
            for(RoomZone r:plan.rooms){minX=Math.min(minX,r.minX);minZ=Math.min(minZ,r.minZ);maxX=Math.max(maxX,r.maxX);maxZ=Math.max(maxZ,r.maxZ);}
            if(finite(minX)&&finite(maxX)){focusX=(minX+maxX)/2;focusZ=(minZ+maxZ)/2;}
        }else if(!plan.walls.isEmpty()){
            double sx=0,sz=0;for(PlanWallSegment w:plan.walls){sx+=w.centerX();sz+=w.centerZ();}focusX=sx/plan.walls.size();focusZ=sz/plan.walls.size();
        }
        focusY=plan.floorY+Math.max(0.8,height()*0.45);
    }
    private double sceneSpan(){
        if(plan==null)return 4;
        double minX=Double.POSITIVE_INFINITY,minZ=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        for(PlanWallSegment w:plan.walls){minX=Math.min(minX,Math.min(w.x1,w.x2));minZ=Math.min(minZ,Math.min(w.z1,w.z2));maxX=Math.max(maxX,Math.max(w.x1,w.x2));maxZ=Math.max(maxZ,Math.max(w.z1,w.z2));}
        if(!finite(minX)||!finite(maxX))return 4;return Math.max(2.0,Math.hypot(maxX-minX,maxZ-minZ));
    }
    private void applyOrbitCamera(){
        double ce=Math.cos(orbitElevation);
        camX=focusX-Math.sin(orbitAzimuth)*orbitDistance*ce;
        camZ=focusZ-Math.cos(orbitAzimuth)*orbitDistance*ce;
        camY=focusY+Math.sin(orbitElevation)*orbitDistance;
        double dx=focusX-camX,dz=focusZ-camZ,dy=focusY-camY;
        yawRad=Math.atan2(dx,dz);pitchRad=Math.atan2(dy,Math.max(0.01,Math.hypot(dx,dz)));
    }
    private void resetWalkCamera(){
        if(plan==null)return;computeSceneFocus();camX=focusX;camZ=focusZ;camY=plan.floorY+1.60;pitchRad=Math.toRadians(-4);faceNearestWall();invalidate();
    }
    private void moveWalk(double forward,double strafe){
        double nx=camX+Math.sin(yawRad)*forward+Math.cos(yawRad)*strafe;
        double nz=camZ+Math.cos(yawRad)*forward-Math.sin(yawRad)*strafe;
        if(plan==null||roomAt(nx,nz)!=null){camX=nx;camZ=nz;invalidate();}
        else if(listener!=null)listener.onCameraMessage("Далі межа відомої геометрії. Виправ план або перескануй цю зону.");
    }
    private void panFocus(float dxPx,float dyPx){
        double scale=Math.max(0.0015,orbitDistance/Math.max(300.0,getWidth())*1.15);
        double rightX=Math.cos(yawRad),rightZ=-Math.sin(yawRad),forwardX=Math.sin(yawRad),forwardZ=Math.cos(yawRad);
        focusX+=(-dxPx*rightX+dyPx*forwardX)*scale;focusZ+=(-dxPx*rightZ+dyPx*forwardZ)*scale;applyOrbitCamera();invalidate();
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(plan==null||plan.walls.isEmpty()){
            p.setColor(Color.LTGRAY);p.setTextSize(Ui.dp(getContext(),14));c.drawText("Немає геометрії Digital Twin",Ui.dp(getContext(),18),Ui.dp(getContext(),36),p);return;
        }
        faces.clear();furnitureProjections.clear();openingProjections.clear();
        drawBackground(c);
        drawFloor(c);
        if(showCeiling)drawCeiling(c);
        buildWallFaces();
        Collections.sort(faces,new Comparator<WallFace>(){@Override public int compare(WallFace a,WallFace b){return Double.compare(b.depth,a.depth);}});
        String hidden=cutaway&&!"WALK".equals(cameraMode)?cutawayWallId():"";
        for(WallFace f:faces)if(!f.wall.id.equals(hidden))drawWall(c,f);
        if(showOpenings)drawOpenings(c);
        if(showFurniture)drawFurniture(c);
        if(showElectrical)drawElectrical(c);
        drawSelectionGizmo(c);
        drawHud(c,hidden);
    }

    private void drawBackground(Canvas c){
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(12,12,15));c.drawRect(0,0,getWidth(),getHeight(),p);
        // Subtle horizon gradient substitute using three bands; cheap on old phones.
        p.setColor(Color.rgb(16,16,21));c.drawRect(0,0,getWidth(),getHeight()*0.55f,p);
        p.setColor(Color.rgb(10,10,13));c.drawRect(0,getHeight()*0.55f,getWidth(),getHeight(),p);
    }

    private void drawFloor(Canvas c){
        for(RoomZone r:plan.rooms){
            Path path=roomSurfacePath(r,plan.floorY);if(path==null)continue;
            RoomFinishCatalog.Preset fp=RoomFinishCatalog.floor(r.floorFinishPresetKey);
            int fill=r.floorFinishColorArgb!=0?r.floorFinishColorArgb:fp.colorArgb;String pattern=r.floorPatternKey==null||r.floorPatternKey.isEmpty()?fp.patternKey:r.floorPatternKey;
            p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(fill);c.drawPath(path,p);
            drawRoomSurfacePattern(c,path,pattern,r.floorPatternScale,r.floorPatternRotationDeg,false);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),1));p.setColor(Color.argb(120,104,104,114));c.drawPath(path,p);
            if(showGrid)drawFloorGrid(c,r,path);
        }
    }
    /** Builds the visible surface from the actual reconstructed footprint, never its bounding box. */
    private Path roomSurfacePath(RoomZone r,double y){
        Path out=new Path();boolean any=false;
        if(r!=null&&!r.floorRuns.isEmpty()&&plan.cellM>0){
            double cell=Math.max(.01,plan.cellM);
            for(PlanGridRun run:r.floorRuns){
                double x1=plan.originX+run.startCol*cell,x2=plan.originX+(run.endCol+1)*cell;
                double z1=plan.originZ+run.row*cell,z2=z1+cell;
                P2 a=project(x1,y,z1),b=project(x2,y,z1),d=project(x2,y,z2),e=project(x1,y,z2);
                if(a==null||b==null||d==null||e==null)continue;out.addPath(quad(a,b,d,e));any=true;
            }
        }else if(r!=null){
            P2 a=project(r.minX,y,r.minZ),b=project(r.maxX,y,r.minZ),d=project(r.maxX,y,r.maxZ),e=project(r.minX,y,r.maxZ);
            if(a!=null&&b!=null&&d!=null&&e!=null){out.addPath(quad(a,b,d,e));any=true;}
        }
        return any?out:null;
    }
    private void drawFloorGrid(Canvas c,RoomZone r,Path footprint){
        double step=0.5;int maxLines=80,lines=0,save=c.save();c.clipPath(footprint);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,Ui.dp(getContext(),1)));p.setColor(Color.argb(65,210,210,220));
        double sx=Math.ceil(r.minX/step)*step;for(double x=sx;x<=r.maxX+1e-6&&lines<maxLines;x+=step,lines++){P2 a=project(x,plan.floorY+0.002,r.minZ),b=project(x,plan.floorY+0.002,r.maxZ);if(a!=null&&b!=null)c.drawLine(a.x,a.y,b.x,b.y,p);}
        double sz=Math.ceil(r.minZ/step)*step;for(double z=sz;z<=r.maxZ+1e-6&&lines<maxLines*2;z+=step,lines++){P2 a=project(r.minX,plan.floorY+0.002,z),b=project(r.maxX,plan.floorY+0.002,z);if(a!=null&&b!=null)c.drawLine(a.x,a.y,b.x,b.y,p);}c.restoreToCount(save);
    }
    private void drawCeiling(Canvas c){
        double y=plan.ceilingY>plan.floorY+1.5?plan.ceilingY:plan.floorY+2.7;
        for(RoomZone r:plan.rooms){
            Path path=roomSurfacePath(r,y);if(path==null)continue;
            RoomFinishCatalog.Preset cp=RoomFinishCatalog.ceiling(r.ceilingFinishPresetKey);int fill=r.ceilingFinishColorArgb!=0?r.ceilingFinishColorArgb:cp.colorArgb;String pattern=r.ceilingPatternKey==null||r.ceilingPatternKey.isEmpty()?cp.patternKey:r.ceilingPatternKey;
            p.setStyle(Paint.Style.FILL);p.setColor(fill);c.drawPath(path,p);drawRoomSurfacePattern(c,path,pattern,r.ceilingPatternScale,0,true);
        }
    }

    private void buildWallFaces(){
        double y0=plan.floorY,y1=plan.ceilingY>plan.floorY+1.5?plan.ceilingY:plan.floorY+2.7;
        for(PlanWallSegment w:plan.walls){
            P2 b1=project(w.x1,y0,w.z1),b2=project(w.x2,y0,w.z2),t2=project(w.x2,y1,w.z2),t1=project(w.x1,y1,w.z1);
            if(b1==null||b2==null||t1==null||t2==null)continue;
            WallFace f=new WallFace();f.wall=w;f.b1=b1;f.b2=b2;f.t2=t2;f.t1=t1;f.depth=(b1.depth+b2.depth+t1.depth+t2.depth)/4.0;faces.add(f);
        }
    }
    private String cutawayWallId(){
        // Prefer the wall actually crossed by the camera->focus sight line.  This behaves like
        // architectural section view: hide the facade in front of the edited room, not merely
        // whichever segment happens to be numerically closest to the camera.
        PlanWallSegment best=null;double bestT=Double.POSITIVE_INFINITY;
        for(PlanWallSegment w:plan.walls){double t=lineIntersectionT(camX,camZ,focusX,focusZ,w.x1,w.z1,w.x2,w.z2);if(t>=0&&t<=1&&t<bestT){bestT=t;best=w;}}
        if(best!=null)return best.id;
        double bd=Double.POSITIVE_INFINITY;for(PlanWallSegment w:plan.walls){double d=distanceToSegment(camX,camZ,w.x1,w.z1,w.x2,w.z2);if(d<bd){bd=d;best=w;}}
        return best==null?"":best.id;
    }

    private void drawWall(Canvas c,WallFace f){
        Path path=quad(f.b1,f.b2,f.t2,f.t1);boolean selected=f.wall.id.equals(selectedWallId);
        if(!"WIRE".equals(renderStyle)){
            p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(wallFill(f.wall,false));c.drawPath(path,p);
            if("PHOTO".equals(renderStyle)&&"RAW".equals(f.wall.finishKey)&&"RAW_SCAN".equals(f.wall.finishPresetKey))drawScannedTexture(c,f,path);
            drawWallPattern(c,f.wall,path);
            if(selected){p.setColor(Color.argb(65,226,177,54));c.drawPath(path,p);}
        }
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),selected?3:1));p.setColor(selected?Ui.GOLD:Color.rgb(118,118,132));c.drawPath(path,p);
        if(selected){
            P2 mid=lerp2(lerp2(f.b1,f.b2,0.5),lerp2(f.t1,f.t2,0.5),0.5);
            p.setStyle(Paint.Style.FILL);p.setTextSize(Ui.dp(getContext(),11));p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);
            c.drawText(String.format(java.util.Locale.US,"%.2f м • %.0f мм • %s",f.wall.lengthM(),f.wall.thicknessM*1000,finishLabel(f.wall.finishKey)),mid.x,mid.y,p);p.setTextAlign(Paint.Align.LEFT);
        }
    }
    private boolean drawScannedTexture(Canvas c,WallFace f,Path clip){
        Bitmap material=wallMaterialBitmap(f.wall);if(material==null||material.isRecycled())return false;
        float[] src={0,material.getHeight(),material.getWidth(),material.getHeight(),material.getWidth(),0,0,0};
        float[] dst={f.b1.x,f.b1.y,f.b2.x,f.b2.y,f.t2.x,f.t2.y,f.t1.x,f.t1.y};
        Matrix m=new Matrix();if(!m.setPolyToPoly(src,0,dst,0,4))return false;
        int save=c.save();c.clipPath(clip);p.setShader(null);p.setAlpha(255);c.drawBitmap(material,m,p);p.setAlpha(255);c.restoreToCount(save);return true;
    }
    private Bitmap wallMaterialBitmap(PlanWallSegment w){
        if(w==null||w.scannedTextureCoverage<0.48||w.scannedTextureFile==null||w.scannedTextureFile.isEmpty()||projectId.isEmpty())return null;
        File file=new File(new File(new File(new File(getContext().getFilesDir(),"scenes"),projectId),"textures"),w.scannedTextureFile);
        // Include the persisted file identity in the cache key. A re-bake may keep the same wall id
        // and filename while replacing the PNG; wall-id-only caching would then show stale scan
        // evidence until the whole view/project was recreated.
        String cacheKey=w.id+"|"+w.scannedTextureFile+"|"+file.length()+"|"+file.lastModified();
        Bitmap cached=scannedWallTextures.get(cacheKey);if(cached!=null&&!cached.isRecycled())return cached;
        Bitmap src=decodeWallTextureSafe(file);if(src==null)return null;
        long bytes=bitmapBytes(src);
        // Never let a single unexpected/imported texture defeat the cache budget. Generated wall
        // textures are <=1400x900, but FULL packages are external input and compressed PNG size is
        // not a reliable bound on decoded heap size.
        if(bytes>MAX_TEXTURE_CACHE_BYTES){if(!src.isRecycled())src.recycle();return null;}
        evictTextureCacheFor(bytes);scannedWallTextures.put(cacheKey,src);scannedWallTextureBytes+=bytes;return src;
    }
    private Bitmap decodeWallTextureSafe(File file){
        if(file==null||!file.isFile()||file.length()<=0)return null;
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)return null;
        long pixels=(long)bounds.outWidth*(long)bounds.outHeight;
        if(pixels<=0||pixels>MAX_TEXTURE_SOURCE_PIXELS)return null;
        int sample=1;while(bounds.outWidth/sample>MAX_TEXTURE_DECODE_DIM||bounds.outHeight/sample>MAX_TEXTURE_DECODE_DIM)sample<<=1;
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=Math.max(1,sample);opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        try{return BitmapFactory.decodeFile(file.getAbsolutePath(),opts);}
        catch(OutOfMemoryError oom){return null;}
        catch(RuntimeException badImage){return null;}
    }
    private long bitmapBytes(Bitmap b){if(b==null||b.isRecycled())return 0;try{return b.getAllocationByteCount();}catch(Throwable ignored){return (long)b.getRowBytes()*b.getHeight();}}
    private void evictTextureCacheFor(long incoming){
        java.util.Iterator<Map.Entry<String,Bitmap>> it=scannedWallTextures.entrySet().iterator();
        while(it.hasNext()&&scannedWallTextureBytes+incoming>MAX_TEXTURE_CACHE_BYTES){Map.Entry<String,Bitmap> e=it.next();Bitmap b=e.getValue();scannedWallTextureBytes=Math.max(0,scannedWallTextureBytes-bitmapBytes(b));if(b!=null&&!b.isRecycled())b.recycle();it.remove();}
    }
    private void clearTextureCache(){for(Bitmap b:scannedWallTextures.values())if(b!=null&&!b.isRecycled())b.recycle();scannedWallTextures.clear();scannedWallTextureBytes=0;}
    @Override protected void onDetachedFromWindow(){clearTextureCache();super.onDetachedFromWindow();}

    private void drawOpenings(Canvas c){
        if(plan.openings==null)return;
        for(ApartmentOpening o:plan.openings){
            double y0=plan.floorY+o.bottomY,y1=y0+Math.max(0.2,o.heightM);
            P2 a=project(o.x1,y0,o.z1),b=project(o.x2,y0,o.z2),cc=project(o.x2,y1,o.z2),d=project(o.x1,y1,o.z1);if(a==null||b==null||cc==null||d==null)continue;
            OpeningProjection op=new OpeningProjection();op.opening=o;op.a=a;op.b=b;op.c=cc;op.d=d;op.depth=(a.depth+b.depth+cc.depth+d.depth)/4;openingProjections.add(op);
            Path path=quad(a,b,cc,d);boolean sel=o.id.equals(selectedOpeningId);
            p.setStyle(Paint.Style.FILL);
            if(o.type==ApartmentOpening.Type.WINDOW)p.setColor(Color.argb("WIRE".equals(renderStyle)?20:105,70,145,190));
            else p.setColor(Color.rgb(12,12,15));
            c.drawPath(path,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),sel?4:2));p.setColor(sel?Ui.GOLD:o.type==ApartmentOpening.Type.DOOR?Color.rgb(70,220,145):o.type==ApartmentOpening.Type.PASSAGE?Color.rgb(75,220,200):Color.rgb(90,170,255));c.drawPath(path,p);
            if(sel){p.setStyle(Paint.Style.FILL);p.setTextSize(Ui.dp(getContext(),10));p.setColor(Color.WHITE);P2 mid=lerp2(a,cc,0.5);c.drawText(openingName(o)+String.format(java.util.Locale.US," %.2f×%.2f м",o.widthM,o.heightM),mid.x+5,mid.y-5,p);}
        }
    }

    private void drawFurniture(Canvas c){
        if(plan.furniture==null)return;
        List<FurnitureProjection> pp=new ArrayList<>();for(FurnitureItem f:plan.furniture){FurnitureProjection fp=projectFurniture(f);if(fp!=null)pp.add(fp);}Collections.sort(pp,new Comparator<FurnitureProjection>(){@Override public int compare(FurnitureProjection a,FurnitureProjection b){return Double.compare(b.depth,a.depth);}});
        for(FurnitureProjection fp:pp){furnitureProjections.add(fp);if(isSemanticStairs(fp.item))drawStaircase(c,fp.item);else drawBox(c,fp);}
    }
    private boolean isSemanticStairs(FurnitureItem f){return f!=null&&"SEMANTIC_STAIRS".equals(f.assetKey);}
    /** Shape-aware stair primitive: render a flight as individual treads instead of one opaque box. */
    private void drawStaircase(Canvas c,FurnitureItem src){
        int steps=(int)Math.round(src.heightM/0.18);steps=Math.max(2,Math.min(18,steps));
        double run=Math.max(.25,src.depthM),stepD=run/steps;
        // Preserve the scanned oriented footprint. Each tread occupies its slice of the run and rises monotonically.
        double a=Math.toRadians(src.rotationDeg),sa=Math.sin(a),ca=Math.cos(a);
        for(int i=0;i<steps;i++){
            double localZ=-run*.5+stepD*(i+.5);
            FurnitureItem q=new FurnitureItem();q.type=FurnitureItem.Type.CUSTOM;q.assetKey="SEMANTIC_STAIR_STEP";q.name="";
            q.widthM=src.widthM;q.depthM=stepD*1.03;q.heightM=Math.max(.06,src.heightM*(i+1)/(double)steps);q.rotationDeg=src.rotationDeg;
            q.x=src.x-localZ*sa;q.z=src.z+localZ*ca;q.y=src.y;
            FurnitureProjection fp=projectFurniture(q);if(fp!=null)drawPrimitiveBox(c,fp,Color.rgb(132,116,94));
        }
        if(src.id.equals(selectedFurnitureId)){FurnitureProjection outline=projectFurniture(src);if(outline!=null)drawBoxOutline(c,outline);}
    }
    private void drawPrimitiveBox(Canvas c,FurnitureProjection fp,int fill){
        if(!"WIRE".equals(renderStyle)){for(int i=0;i<4;i++){int j=(i+1)%4;p.setStyle(Paint.Style.FILL);p.setColor(shade(fill,i));c.drawPath(quad(fp.lo[i],fp.lo[j],fp.hi[j],fp.hi[i]),p);}p.setColor(shade(fill,5));c.drawPath(quad(fp.hi[0],fp.hi[1],fp.hi[2],fp.hi[3]),p);}
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),1));p.setColor(Color.rgb(175,155,120));for(int i=0;i<4;i++){int j=(i+1)%4;c.drawLine(fp.lo[i].x,fp.lo[i].y,fp.lo[j].x,fp.lo[j].y,p);c.drawLine(fp.hi[i].x,fp.hi[i].y,fp.hi[j].x,fp.hi[j].y,p);c.drawLine(fp.lo[i].x,fp.lo[i].y,fp.hi[i].x,fp.hi[i].y,p);}
    }
    private void drawBoxOutline(Canvas c,FurnitureProjection fp){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),3));p.setColor(Ui.GOLD);for(int i=0;i<4;i++){int j=(i+1)%4;c.drawLine(fp.lo[i].x,fp.lo[i].y,fp.lo[j].x,fp.lo[j].y,p);c.drawLine(fp.hi[i].x,fp.hi[i].y,fp.hi[j].x,fp.hi[j].y,p);c.drawLine(fp.lo[i].x,fp.lo[i].y,fp.hi[i].x,fp.hi[i].y,p);}}

    private FurnitureProjection projectFurniture(FurnitureItem f){
        double hw=f.widthM/2.0,hd=f.depthM/2.0,ang=Math.toRadians(f.rotationDeg),ca=Math.cos(ang),sa=Math.sin(ang);double[][] local={{-hw,-hd},{hw,-hd},{hw,hd},{-hw,hd}};
        FurnitureProjection fp=new FurnitureProjection();fp.item=f;fp.minX=Float.POSITIVE_INFINITY;fp.minY=Float.POSITIVE_INFINITY;fp.maxX=Float.NEGATIVE_INFINITY;fp.maxY=Float.NEGATIVE_INFINITY;double dep=0;
        for(int i=0;i<4;i++){double x=f.x+local[i][0]*ca-local[i][1]*sa,z=f.z+local[i][0]*sa+local[i][1]*ca;fp.lo[i]=project(x,plan.floorY+f.y,z);fp.hi[i]=project(x,plan.floorY+f.y+f.heightM,z);if(fp.lo[i]==null||fp.hi[i]==null)return null;dep+=fp.lo[i].depth+fp.hi[i].depth;fp.minX=Math.min(fp.minX,Math.min(fp.lo[i].x,fp.hi[i].x));fp.maxX=Math.max(fp.maxX,Math.max(fp.lo[i].x,fp.hi[i].x));fp.minY=Math.min(fp.minY,Math.min(fp.lo[i].y,fp.hi[i].y));fp.maxY=Math.max(fp.maxY,Math.max(fp.lo[i].y,fp.hi[i].y));}fp.depth=dep/8.0;return fp;
    }
    private void drawBox(Canvas c,FurnitureProjection fp){
        FurnitureItem f=fp.item;boolean sel=f.id.equals(selectedFurnitureId);int fill=f.colorArgb!=0?f.colorArgb:furnitureColor(f.type);
        if(!"WIRE".equals(renderStyle)){
            // Four sides + top.  Draw as opaque solids so furniture reads as objects, not wire cages.
            for(int i=0;i<4;i++){int j=(i+1)%4;Path side=quad(fp.lo[i],fp.lo[j],fp.hi[j],fp.hi[i]);p.setStyle(Paint.Style.FILL);p.setColor(shade(fill,i));c.drawPath(side,p);}
            p.setStyle(Paint.Style.FILL);p.setColor(shade(fill,5));c.drawPath(quad(fp.hi[0],fp.hi[1],fp.hi[2],fp.hi[3]),p);
            if(sel){p.setColor(Color.argb(55,226,177,54));c.drawPath(quad(fp.hi[0],fp.hi[1],fp.hi[2],fp.hi[3]),p);}
        }
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),sel?3:1));p.setColor(sel?Ui.GOLD:Color.rgb(175,155,120));
        for(int i=0;i<4;i++){int j=(i+1)%4;c.drawLine(fp.lo[i].x,fp.lo[i].y,fp.lo[j].x,fp.lo[j].y,p);c.drawLine(fp.hi[i].x,fp.hi[i].y,fp.hi[j].x,fp.hi[j].y,p);c.drawLine(fp.lo[i].x,fp.lo[i].y,fp.hi[i].x,fp.hi[i].y,p);}
        float tx=0,ty=0;for(P2 q:fp.hi){tx+=q.x;ty+=q.y;}tx/=4f;ty/=4f;p.setStyle(Paint.Style.FILL);p.setTextSize(Ui.dp(getContext(),sel?11:9));p.setColor(sel?Color.WHITE:Color.rgb(225,210,180));p.setTextAlign(Paint.Align.CENTER);c.drawText(furnitureName(f),tx,ty-5,p);p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawElectrical(Canvas c){
        if(floor==null)return;
        for(ElectricalPoint e:floor.points){double y=plan.floorY+Math.max(0,e.heightFromFinishedFloorM);P2 q=project(e.position.x,y,e.position.z);if(q==null)continue;boolean sel=e.id.equals(selectedPointId);drawElectricalSymbol(c,e,q,sel);}
    }
    private void drawElectricalSymbol(Canvas c,ElectricalPoint e,P2 q,boolean sel){String a=e.assetKey==null?"":e.assetKey;float scale=sel?1.18f:1f,unit=Ui.dp(getContext(),6)*scale;p.setShader(null);p.setStrokeWidth(Ui.dp(getContext(),sel?3:2));p.setStyle(Paint.Style.FILL);p.setColor(sel?Ui.GOLD:pointColor(e.type));
        if(e.type==ElectricalPoint.Type.SOCKET||e.type==ElectricalPoint.Type.SWITCH||e.type==ElectricalPoint.Type.TV||e.type==ElectricalPoint.Type.ETHERNET){int modules=Math.max(1,Math.min(4,e.moduleCount));float w=unit*(1.25f+modules*.72f),h=unit*1.55f;android.graphics.RectF r=new android.graphics.RectF(q.x-w/2,q.y-h/2,q.x+w/2,q.y+h/2);c.drawRoundRect(r,Ui.dp(getContext(),3),Ui.dp(getContext(),3),p);p.setStyle(Paint.Style.STROKE);p.setColor(Color.rgb(22,22,25));c.drawRoundRect(r,Ui.dp(getContext(),3),Ui.dp(getContext(),3),p);p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(25,25,28));if(e.type==ElectricalPoint.Type.SOCKET){for(int i=0;i<modules;i++){float cx=q.x+(i-(modules-1)/2f)*unit*.72f;c.drawCircle(cx-unit*.16f,q.y,unit*.11f,p);c.drawCircle(cx+unit*.16f,q.y,unit*.11f,p);}}else if(e.type==ElectricalPoint.Type.SWITCH){c.drawRect(q.x-unit*.35f,q.y-unit*.32f,q.x+unit*.35f,q.y+unit*.32f,p);if(a.contains("DOUBLE"))c.drawLine(q.x,q.y-unit*.32f,q.x,q.y+unit*.32f,p);}else{p.setTextSize(Ui.dp(getContext(),7));p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.WHITE);c.drawText(e.type==ElectricalPoint.Type.TV?"TV":"LAN",q.x,q.y+Ui.dp(getContext(),2),p);p.setTextAlign(Paint.Align.LEFT);}}
        else if(e.type==ElectricalPoint.Type.LIGHT){p.setStyle(Paint.Style.STROKE);p.setColor(sel?Ui.GOLD:pointColor(e.type));if(a.contains("TRACK")||a.contains("LINEAR")){c.drawLine(q.x-unit*1.6f,q.y,q.x+unit*1.6f,q.y,p);for(int i=-1;i<=1;i++)c.drawCircle(q.x+i*unit,q.y,unit*.22f,p);}else{c.drawCircle(q.x,q.y,unit*.65f,p);c.drawLine(q.x-unit,q.y,q.x+unit,q.y,p);c.drawLine(q.x,q.y-unit,q.x,q.y+unit,p);}}
        else if(e.type==ElectricalPoint.Type.PANEL){android.graphics.RectF r=new android.graphics.RectF(q.x-unit,q.y-unit*1.2f,q.x+unit,q.y+unit*1.2f);p.setStyle(Paint.Style.FILL);p.setColor(sel?Ui.GOLD:pointColor(e.type));c.drawRect(r,p);p.setStyle(Paint.Style.STROKE);p.setColor(Color.rgb(25,25,28));c.drawRect(r,p);for(int i=-1;i<=1;i++)c.drawLine(q.x-unit*.65f,q.y+i*unit*.42f,q.x+unit*.65f,q.y+i*unit*.42f,p);}
        else {float r=unit*.75f;c.drawCircle(q.x,q.y,r,p);p.setStyle(Paint.Style.STROKE);p.setColor(Color.rgb(15,15,18));c.drawCircle(q.x,q.y,r,p);}if(sel){p.setStyle(Paint.Style.FILL);p.setTextSize(Ui.dp(getContext(),10));p.setColor(Color.WHITE);c.drawText(shortName(e),q.x+unit*1.4f,q.y-unit,p);}}


    private void drawSelectionGizmo(Canvas c){
        FurnitureItem f=findFurniture(selectedFurnitureId);if(f!=null){P2 q=project(f.x,plan.floorY+f.y+f.heightM,f.z);if(q!=null){float s=Ui.dp(getContext(),30);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),2));p.setColor(Color.rgb(230,80,80));c.drawLine(q.x,q.y,q.x+s,q.y,p);p.setColor(Color.rgb(80,220,120));c.drawLine(q.x,q.y,q.x,q.y-s,p);p.setColor(Color.rgb(80,150,255));c.drawLine(q.x,q.y,q.x-s*0.65f,q.y+s*0.45f,p);}}
    }

    private void drawHud(Canvas c,String hiddenWall){
        p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(185,7,7,10));c.drawRoundRect(Ui.dp(getContext(),8),Ui.dp(getContext(),8),getWidth()-Ui.dp(getContext(),8),Ui.dp(getContext(),48),Ui.dp(getContext(),12),Ui.dp(getContext(),12),p);
        p.setColor(Color.WHITE);p.setTextSize(Ui.dp(getContext(),11));String cam="ORBIT".equals(cameraMode)?"Orbit":"TOP".equals(cameraMode)?"Top":"Walk";String style="PHOTO".equals(renderStyle)?"Photo":"WIRE".equals(renderStyle)?"Wire":"Solid";
        c.drawText(cam+" • "+style+(cutaway&&!"WALK".equals(cameraMode)?" • зріз":"")+"    "+gestureHint(),Ui.dp(getContext(),16),Ui.dp(getContext(),30),p);
    }
    private String gestureHint(){
        if("WALK".equals(cameraMode))return "1 палець: огляд • кнопки: рух";
        if("TOP".equals(cameraMode))return "1 палець: pan • 2 пальці: pan/zoom";
        return "1 палець: orbit • pinch: zoom • 2 пальці: pan";
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(plan==null)return true;float x=e.getX(),y=e.getY();int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            downX=lastX=x;downY=lastY=y;moved=false;lastPinchDistance=0;draggingPoint=null;draggingWall=null;draggingFurniture=null;
            if("PLACE".equals(mode)){ElectricalPoint hit=nearestPointOnScreen(x,y,Ui.dp(getContext(),28));if(hit!=null&&hit.wallId!=null&&!hit.wallId.isEmpty()){draggingPoint=hit;draggingWall=findWall(hit.wallId);selectedPointId=hit.id;selectedWallId=hit.wallId;selectedFurnitureId="";selectedOpeningId="";beginEditGesture();if(listener!=null)listener.onPointSelected(hit);}}
            if("FURNITURE".equals(mode)){FurnitureItem hit=hitFurniture(x,y);if(hit!=null&&!hit.locked){draggingFurniture=hit;selectedFurnitureId=hit.id;selectedWallId="";selectedPointId="";selectedOpeningId="";beginEditGesture();if(listener!=null)listener.onFurnitureSelected(hit);}}
            return true;
        }
        if(action==MotionEvent.ACTION_POINTER_DOWN){if(e.getPointerCount()>=2){lastPinchDistance=pointerDistance(e);lastMidX=(e.getX(0)+e.getX(1))/2f;lastMidY=(e.getY(0)+e.getY(1))/2f;}return true;}
        if(action==MotionEvent.ACTION_MOVE){
            if(e.getPointerCount()>=2&&!"WALK".equals(cameraMode)){
                float dist=pointerDistance(e),mx=(e.getX(0)+e.getX(1))/2f,my=(e.getY(0)+e.getY(1))/2f;if(lastPinchDistance>10&&dist>10){double scale=dist/lastPinchDistance;orbitDistance=clamp(orbitDistance/scale,0.8,Math.max(25,sceneSpan()*5));}panFocus(mx-lastMidX,my-lastMidY);lastPinchDistance=dist;lastMidX=mx;lastMidY=my;moved=true;return true;
            }
            float dx=x-lastX,dy=y-lastY;if(Math.hypot(x-downX,y-downY)>Ui.dp(getContext(),5))moved=true;
            if(draggingPoint!=null&&draggingWall!=null){WallFace face=findFace(draggingWall.id);double[] uv=mapOnWall(face,x,y);if(uv!=null&&listener!=null)listener.onDragPoint(draggingPoint,draggingWall,uv[0],uv[1]*height(),false);invalidate();}
            else if(draggingFurniture!=null){double[] hit=rayToFloor(x,y);if(hit!=null&&roomAt(hit[0],hit[1])!=null&&listener!=null)listener.onDragFurniture(draggingFurniture,snap5cm(hit[0]),snap5cm(hit[1]),false);invalidate();}
            else if(!"PLACE".equals(mode)&&!"FURNITURE".equals(mode)){
                if("WALK".equals(cameraMode)){yawRad+=dx*0.0065;pitchRad=clamp(pitchRad+dy*0.0040,Math.toRadians(-55),Math.toRadians(45));invalidate();}
                else if("TOP".equals(cameraMode)){panFocus(dx,dy);}
                else {orbitAzimuth+=dx*0.0070;orbitElevation=clamp(orbitElevation-dy*0.0045,Math.toRadians(-5),Math.toRadians(72));applyOrbitCamera();invalidate();}
            }
            lastX=x;lastY=y;return true;
        }
        if(action==MotionEvent.ACTION_UP){
            if(draggingPoint!=null&&draggingWall!=null){WallFace face=findFace(draggingWall.id);double[] uv=mapOnWall(face,x,y);if(uv!=null&&listener!=null)listener.onDragPoint(draggingPoint,draggingWall,uv[0],uv[1]*height(),true);draggingPoint=null;draggingWall=null;finishEditGesture();invalidate();return true;}
            if(draggingFurniture!=null){double[] hit=rayToFloor(x,y);if(hit!=null&&roomAt(hit[0],hit[1])!=null&&listener!=null)listener.onDragFurniture(draggingFurniture,snap5cm(hit[0]),snap5cm(hit[1]),true);draggingFurniture=null;finishEditGesture();invalidate();return true;}
            if(!moved)handleTap(x,y);return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){draggingPoint=null;draggingWall=null;draggingFurniture=null;finishEditGesture();return true;}
        return true;
    }
    private void beginEditGesture(){if(!gestureEditing){gestureEditing=true;if(listener!=null)listener.onEditGestureStart();}}
    private void finishEditGesture(){if(gestureEditing){gestureEditing=false;if(listener!=null)listener.onEditGestureFinish();}}

    private void handleTap(float x,float y){
        ElectricalPoint ep=showElectrical?nearestPointOnScreen(x,y,Ui.dp(getContext(),24)):null;
        if(ep!=null&&!"FURNITURE".equals(mode)&&!"FINISH".equals(mode)){selectedPointId=ep.id;selectedWallId=ep.wallId==null?"":ep.wallId;selectedFurnitureId="";selectedOpeningId="";if(listener!=null)listener.onPointSelected(ep);invalidate();return;}
        FurnitureItem furniture=showFurniture?hitFurniture(x,y):null;
        if(furniture!=null){selectedFurnitureId=furniture.id;selectedWallId="";selectedPointId="";selectedOpeningId="";if(listener!=null)listener.onFurnitureSelected(furniture);invalidate();return;}
        ApartmentOpening opening=showOpenings?hitOpening(x,y):null;
        if(opening!=null&&!"FINISH".equals(mode)&&!"PLACE".equals(mode)){selectedOpeningId=opening.id;selectedWallId="";selectedPointId="";selectedFurnitureId="";if(listener!=null)listener.onOpeningSelected(opening);invalidate();return;}
        if("FURNITURE".equals(mode)){double[] floorHit=rayToFloor(x,y);if(floorHit!=null){RoomZone r=roomAt(floorHit[0],floorHit[1]);if(r!=null&&listener!=null)listener.onPlaceFurniture(snap5cm(floorHit[0]),snap5cm(floorHit[1]),r);}return;}
        if("PLACE".equals(mode)&&placeOnCeiling){double yPlane=plan.ceilingY>plan.floorY+1.5?plan.ceilingY:plan.floorY+2.7;double[] hit=rayToPlane(x,y,yPlane);if(hit!=null){RoomZone r=roomAt(hit[0],hit[1]);if(r!=null&&listener!=null)listener.onPlaceFreePoint(hit[0],hit[1],r,true);invalidate();}return;}
        WallFace f=hitWall(x,y);if(f==null){clearSelection();return;}selectedWallId=f.wall.id;selectedPointId="";selectedFurnitureId="";selectedOpeningId="";
        if("FINISH".equals(mode)){if(listener!=null)listener.onFinishWall(f.wall);invalidate();return;}
        if("PLACE".equals(mode)){double[] uv=mapOnWall(f,x,y);if(uv!=null&&listener!=null)listener.onPlacePoint(f.wall,uv[0],uv[1]*height());invalidate();return;}
        if(listener!=null)listener.onWallSelected(f.wall);invalidate();
    }

    private WallFace hitWall(float x,float y){WallFace best=null;double bd=Double.POSITIVE_INFINITY;for(WallFace f:faces){if(pointInQuad(x,y,new P2[]{f.b1,f.b2,f.t2,f.t1})&&f.depth<bd){bd=f.depth;best=f;}}return best;}
    private FurnitureItem hitFurniture(float x,float y){FurnitureProjection best=null;for(FurnitureProjection fp:furnitureProjections)if(x>=fp.minX-8&&x<=fp.maxX+8&&y>=fp.minY-8&&y<=fp.maxY+8&&(best==null||fp.depth<best.depth))best=fp;return best==null?null:best.item;}
    private ApartmentOpening hitOpening(float x,float y){OpeningProjection best=null;for(OpeningProjection op:openingProjections)if(pointInQuad(x,y,new P2[]{op.a,op.b,op.c,op.d})&&(best==null||op.depth<best.depth))best=op;return best==null?null:best.opening;}
    private boolean pointInQuad(float x,float y,P2[] q){boolean sign=false,init=false;for(int i=0;i<4;i++){P2 a=q[i],b=q[(i+1)%4];double cross=(b.x-a.x)*(y-a.y)-(b.y-a.y)*(x-a.x);if(Math.abs(cross)<1e-3)continue;if(!init){sign=cross>0;init=true;}else if(sign!=(cross>0))return false;}return init;}
    private double[] mapOnWall(WallFace f,float x,float y){if(f==null)return null;P2 midA=lerp2(f.b1,f.t1,0.5),midB=lerp2(f.b2,f.t2,0.5);double t=clamp(projectionT(x,y,midA,midB),0.015,0.985);P2 bottom=lerp2(f.b1,f.b2,t),top=lerp2(f.t1,f.t2,t);double v=clamp(projectionT(x,y,bottom,top),0,1);return new double[]{t,v};}
    private ElectricalPoint nearestPointOnScreen(float x,float y,float maxPx){if(floor==null)return null;ElectricalPoint best=null;double bd=maxPx;for(ElectricalPoint e:floor.points){P2 q=project(e.position.x,plan.floorY+e.heightFromFinishedFloorM,e.position.z);if(q==null)continue;double d=Math.hypot(x-q.x,y-q.y);if(d<=bd){bd=d;best=e;}}return best;}

    private WallFace findFace(String wallId){if(wallId==null)return null;for(WallFace f:faces)if(wallId.equals(f.wall.id))return f;return null;}
    private PlanWallSegment findWall(String id){if(id==null||plan==null)return null;for(PlanWallSegment w:plan.walls)if(id.equals(w.id))return w;return null;}
    private FurnitureItem findFurniture(String id){if(id==null||id.isEmpty()||plan==null)return null;for(FurnitureItem f:plan.furniture)if(id.equals(f.id))return f;return null;}

    private double[] rayToFloor(float sx,float sy){return rayToPlane(sx,sy,plan.floorY);}
    private double[] rayToPlane(float sx,float sy,double planeY){double f=focal(),right=(sx-getWidth()/2.0)/f,camVertical=-(sy-getHeight()/2.0)/f,camForward=1.0,cp=Math.cos(pitchRad),sp=Math.sin(pitchRad),vertical=camVertical*cp+camForward*sp,forward=-camVertical*sp+camForward*cp,cy=Math.cos(yawRad),syaw=Math.sin(yawRad),dx=right*cy+forward*syaw,dz=-right*syaw+forward*cy;if(Math.abs(vertical)<1e-5)return null;double t=(planeY-camY)/vertical;if(t<=0||t>80)return null;return new double[]{camX+dx*t,camZ+dz*t};}
    private P2 project(double x,double y,double z){double dx=x-camX,dy=y-camY,dz=z-camZ,cy=Math.cos(yawRad),sy=Math.sin(yawRad),right=dx*cy-dz*sy,forward=dx*sy+dz*cy,cp=Math.cos(pitchRad),sp=Math.sin(pitchRad),vertical=dy*cp-forward*sp,depth=dy*sp+forward*cp;if(depth<0.08)return null;double f=focal();return new P2((float)(getWidth()/2.0+right/depth*f),(float)(getHeight()/2.0-vertical/depth*f),depth);}
    private double focal(){return Math.max(220,getWidth()*0.88);}
    private double height(){double h=plan.ceilingY-plan.floorY;return h>1.5?h:2.7;}

    private RoomZone roomAt(double x,double z){if(plan==null)return null;int col=(int)Math.floor((x-plan.originX)/Math.max(0.01,plan.cellM)),row=(int)Math.floor((z-plan.originZ)/Math.max(0.01,plan.cellM));for(RoomZone room:plan.rooms){for(PlanGridRun run:room.floorRuns)if(run.row==row&&col>=run.startCol&&col<=run.endCol)return room;if(room.floorRuns.isEmpty()&&x>=room.minX&&x<=room.maxX&&z>=room.minZ&&z<=room.maxZ)return room;}return null;}
    private void faceNearestWall(){if(plan==null||plan.walls.isEmpty())return;PlanWallSegment best=null;double bd=Double.POSITIVE_INFINITY;for(PlanWallSegment w:plan.walls){double d=Math.hypot(w.centerX()-camX,w.centerZ()-camZ);if(d<bd){bd=d;best=w;}}if(best!=null)yawRad=Math.atan2(best.centerX()-camX,best.centerZ()-camZ);}

    private int wallFill(PlanWallSegment w,boolean selected){if(w.finishColorArgb!=0)return w.finishColorArgb;WallFinishCatalog.Preset fp=WallFinishCatalog.find(w.finishPresetKey);if(!"RAW_SCAN".equals(fp.key)&&fp.colorArgb!=0)return fp.colorArgb;return WallFinishCatalog.surfaceColor(w.surfaceStateKey,w.materialKey);}
    private void drawWallPattern(Canvas c,PlanWallSegment w,Path clip){String k=w.finishPatternKey;if(k==null||k.isEmpty())k=WallFinishCatalog.find(w.finishPresetKey).patternKey;if((k==null||k.isEmpty())&&"RAW".equals(w.finishKey))k=WallFinishCatalog.surfacePattern(w.surfaceStateKey,w.materialKey);if(k==null||k.isEmpty()||"NONE".equals(k))return;android.graphics.RectF r=new android.graphics.RectF();clip.computeBounds(r,true);if(r.width()<8||r.height()<8)return;int save=c.save();c.clipPath(clip);c.rotate((float)w.finishPatternRotationDeg,r.centerX(),r.centerY());p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,Ui.dp(getContext(),1)));p.setColor(Color.argb(75,35,35,38));float scale=(float)Math.max(.20,Math.min(8.0,w.finishPatternScale));float step=Ui.dp(getContext(),"TILE".equals(k)?26:"BRICK".equals(k)?22:"SLAT".equals(k)?18:32)*scale;float offX=Ui.dp(getContext(),(float)(w.finishPatternOffsetXM*20.0)),offY=Ui.dp(getContext(),(float)(w.finishPatternOffsetYM*20.0));r.offset(offX,offY);if("WOOD".equals(k)||"SLAT".equals(k)){for(float x=r.left;x<r.right;x+=step)c.drawLine(x,r.top,x,r.bottom,p);}else if("TILE".equals(k)||"BLOCK".equals(k)){for(float x=r.left;x<r.right;x+=step)c.drawLine(x,r.top,x,r.bottom,p);for(float y=r.top;y<r.bottom;y+=step)c.drawLine(r.left,y,r.right,y,p);}else if("BRICK".equals(k)){for(float y=r.top;y<r.bottom;y+=step){c.drawLine(r.left,y,r.right,y,p);float off=((int)((y-r.top)/step)%2==0)?0:step*.5f;for(float x=r.left+off;x<r.right;x+=step)c.drawLine(x,y,x,y+step,p);}}else if("WALLPAPER_GEOM".equals(k)){for(float x=r.left-step;x<r.right+step;x+=step)for(float y=r.top-step;y<r.bottom+step;y+=step){c.drawLine(x,y,x+step*.5f,y+step*.5f,p);c.drawLine(x+step*.5f,y+step*.5f,x+step,y,p);}}else if("WALLPAPER".equals(k)){p.setStyle(Paint.Style.FILL);for(float x=r.left+step*.5f;x<r.right;x+=step)for(float y=r.top+step*.5f;y<r.bottom;y+=step)c.drawCircle(x,y,Ui.dp(getContext(),2),p);}else if("CONCRETE".equals(k)||"PLASTER".equals(k)||"MARBLE".equals(k)){for(float y=r.top;y<r.bottom;y+=step)c.drawLine(r.left,y,r.right,y-step*.22f,p);}c.restoreToCount(save);}
    private void drawRoomSurfacePattern(Canvas c,Path clip,String k,double scaleValue,double rotationDeg,boolean ceiling){
        if(k==null||k.isEmpty()||"NONE".equals(k)||"RAW".equals(k))return;android.graphics.RectF r=new android.graphics.RectF();clip.computeBounds(r,true);if(r.width()<8||r.height()<8)return;int save=c.save();c.clipPath(clip);c.rotate((float)rotationDeg,r.centerX(),r.centerY());p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,Ui.dp(getContext(),1)));p.setColor(Color.argb(ceiling?45:80,36,36,40));float sc=(float)Math.max(.20,Math.min(6.0,scaleValue));float step=Ui.dp(getContext(),"TILE".equals(k)?34:"PLANK".equals(k)?24:"HERRINGBONE".equals(k)?28:"SLAT".equals(k)?18:32)*sc;
        if("PLANK".equals(k)){for(float y=r.top;y<r.bottom;y+=step*.45f){c.drawLine(r.left,y,r.right,y,p);float off=((int)((y-r.top)/(step*.45f))%2==0)?0:step*.45f;for(float x=r.left+off;x<r.right;x+=step)c.drawLine(x,y,x,y+step*.45f,p);}}
        else if("HERRINGBONE".equals(k)){for(float x=r.left-step;x<r.right+step;x+=step)for(float y=r.top-step;y<r.bottom+step;y+=step){c.drawLine(x,y,x+step*.5f,y+step*.5f,p);c.drawLine(x+step*.5f,y+step*.5f,x+step,y,p);}}
        else if("TILE".equals(k)){for(float x=r.left;x<r.right;x+=step)c.drawLine(x,r.top,x,r.bottom,p);for(float y=r.top;y<r.bottom;y+=step)c.drawLine(r.left,y,r.right,y,p);}
        else if("SLAT".equals(k)){for(float x=r.left;x<r.right;x+=step)c.drawLine(x,r.top,x,r.bottom,p);}
        else if("TERRAZZO".equals(k)){p.setStyle(Paint.Style.FILL);for(float x=r.left+step*.4f;x<r.right;x+=step)for(float y=r.top+step*.35f;y<r.bottom;y+=step*.8f)c.drawCircle(x,y,Math.max(1,step*.08f),p);}
        else if("CARPET".equals(k)||"CONCRETE".equals(k)||"MARBLE".equals(k)){for(float y=r.top;y<r.bottom;y+=step*.7f)c.drawLine(r.left,y,r.right,y-step*.18f,p);}
        c.restoreToCount(save);
    }

    private int furnitureColor(FurnitureItem.Type t){switch(t){case WARDROBE:case NIGHTSTAND:case CABINET:case SHELF:return Color.rgb(126,104,82);case KITCHEN:return Color.rgb(112,116,106);case BED:return Color.rgb(108,104,130);case SOFA:case CHAIR:return Color.rgb(104,118,132);case TABLE:case DESK:return Color.rgb(132,104,72);case TV:return Color.rgb(32,34,38);case FRIDGE:case WASHER:case OVEN:case APPLIANCE:return Color.rgb(145,150,154);case SINK:case TOILET:case BATHTUB:case SHOWER:case SANITARY:return Color.rgb(188,194,198);case RADIATOR:return Color.rgb(202,202,198);case MIRROR:return Color.rgb(105,145,165);default:return Color.rgb(118,102,86);}}
    private int shade(int c,int side){float f=side==5?1.08f:(side%2==0?0.84f:0.96f);return Color.rgb((int)clamp(Color.red(c)*f,0,255),(int)clamp(Color.green(c)*f,0,255),(int)clamp(Color.blue(c)*f,0,255));}
    private String finishLabel(String k){if("PLASTER".equals(k))return "штукатурка";if("PUTTY".equals(k))return "шпаклівка";if("PAINT".equals(k))return "фарба";if("TILE".equals(k))return "плитка";if("DRYWALL".equals(k))return "гіпсокартон";if("DECOR".equals(k))return "шпалери/декор";return "як є";}
    private int pointColor(ElectricalPoint.Type t){switch(t){case SOCKET:return Color.rgb(255,196,0);case SWITCH:return Color.rgb(80,190,255);case LIGHT:return Color.rgb(255,240,150);case PANEL:return Color.rgb(255,105,95);case ETHERNET:return Color.rgb(110,210,180);case CAMERA:return Color.rgb(190,135,255);default:return Color.WHITE;}}
    private String shortName(ElectricalPoint e){String a=e.assetKey==null?"":e.assetKey;switch(e.type){case SOCKET:return "R×"+Math.max(1,e.moduleCount);case SWITCH:return a.contains("DOUBLE")?"SW2":a.contains("PASS")?"SW↔":a.contains("DIMMER")?"DIM":"SW";case LIGHT:return a.contains("SCONCE")?"BRA":a.contains("TRACK")?"TRK":a.contains("PENDANT")?"PEND":a.contains("SURFACE")?"SURF":"SPOT";case PANEL:return "Щ";case ETHERNET:return "LAN";case CAMERA:return "CAM";case SENSOR:return "S";case SMART_HOME:return "SMART";case APPLIANCE:return "A";default:return "TV";}}
    private String furnitureName(FurnitureItem f){if(f.name!=null&&!f.name.trim().isEmpty())return f.name;String a=f.assetKey==null?"":f.assetKey;if(a.contains("SOFA_CORNER"))return "Диван L";switch(f.type){case SOFA:return "Диван";case BED:return "Ліжко";case WARDROBE:return "Шафа";case KITCHEN:return "Кухня";case TABLE:return "Стіл";case CHAIR:return "Стілець";case DESK:return "Робочий стіл";case NIGHTSTAND:return "Тумба";case TV:return "TV";case FRIDGE:return "Холодильник";case WASHER:return "Пральна";case OVEN:return "Духовка";case SINK:return "Умивальник";case TOILET:return "Унітаз";case BATHTUB:return "Ванна";case SHOWER:return "Душ";case RADIATOR:return "Радіатор";case MIRROR:return "Дзеркало";case SHELF:return "Полиця";case APPLIANCE:return "Техніка";case SANITARY:return "Сантехніка";case CUSTOM:return "Об'єкт";default:return "Тумба";}}
    private String openingName(ApartmentOpening o){if(o.type==ApartmentOpening.Type.DOOR)return "Двері";if(o.type==ApartmentOpening.Type.WINDOW)return "Вікно";if(o.type==ApartmentOpening.Type.PASSAGE)return "Прохід";return "Отвір";}

    private Path quad(P2 a,P2 b,P2 c,P2 d){Path path=new Path();path.moveTo(a.x,a.y);path.lineTo(b.x,b.y);path.lineTo(c.x,c.y);path.lineTo(d.x,d.y);path.close();return path;}
    private P2 lerp2(P2 a,P2 b,double t){return new P2((float)(a.x+(b.x-a.x)*t),(float)(a.y+(b.y-a.y)*t),a.depth+(b.depth-a.depth)*t);}
    private double projectionT(float x,float y,P2 a,P2 b){double dx=b.x-a.x,dy=b.y-a.y,den=dx*dx+dy*dy;if(den<1e-9)return 0.5;return ((x-a.x)*dx+(y-a.y)*dy)/den;}
    private float pointerDistance(MotionEvent e){if(e.getPointerCount()<2)return 0;return (float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));}
    private double lineIntersectionT(double ax,double az,double bx,double bz,double cx,double cz,double dx,double dz){
        double rX=bx-ax,rZ=bz-az,sX=dx-cx,sZ=dz-cz,den=rX*sZ-rZ*sX;if(Math.abs(den)<1e-9)return -1;double qx=cx-ax,qz=cz-az,t=(qx*sZ-qz*sX)/den,u=(qx*rZ-qz*rX)/den;return u>=0&&u<=1?t:-1;
    }
    private double distanceToSegment(double px,double pz,double x1,double z1,double x2,double z2){double dx=x2-x1,dz=z2-z1,den=dx*dx+dz*dz;if(den<1e-9)return Math.hypot(px-x1,pz-z1);double t=clamp(((px-x1)*dx+(pz-z1)*dz)/den,0,1);return Math.hypot(px-(x1+t*dx),pz-(z1+t*dz));}
    private double snap5cm(double v){return Math.round(v/0.05)*0.05;}
    private double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
    private boolean finite(double v){return !Double.isNaN(v)&&!Double.isInfinite(v);}
}
