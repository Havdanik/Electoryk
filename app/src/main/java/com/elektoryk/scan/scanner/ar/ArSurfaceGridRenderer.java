package com.elektoryk.scan.scanner.ar;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.google.ar.core.Camera;
import com.elektoryk.scan.semantic.SemanticLabel;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Persistent structural coverage overlay.
 *
 * v0.17.8 rendered each raw XYZ voxel as a camera-facing billboard. It looked like thousands of
 * floating squares and could never represent a wall. v0.18.0 renders only tiles belonging to a
 * fitted persistent surface (WALL/FLOOR/CEILING). The surface map lives in scan-local coordinates,
 * so returning to an already scanned wall strengthens the same tiles instead of creating a new
 * layer after ARCore relocalization.
 */
public final class ArSurfaceGridRenderer {
    public static final float TILE_M=DepthSurfaceTracker.TILE_M;
    private static final int MAX_DRAW_TILES=1200;
    /**
     * Room memory is persistent, but the camera overlay must not render the whole room at once.
     * A surface becomes "active" again as soon as current Depth evidence hits it; at that point
     * its already-scanned tiles are shown again.  This removes the x-ray / spaghetti effect while
     * keeping the user's progress when they return to a wall.
     */
    private static final long ACTIVE_SURFACE_NS=850_000_000L;
    private static final long ACTIVE_RED_TILE_NS=1_350_000_000L;
    private static final long RECENT_TILE_NS=1_450_000_000L;
    private static final int MAX_ACTIVE_SURFACES=1;
    private static final long ACTIVE_LOCK_NS=1_250_000_000L;

    public static final class Stats {
        public int visiblePlanes;
        public int coveredPlanes;
        public int redTiles;
        public int yellowTiles;
        public int greenTiles;
        public boolean depthFallback;
        public int provisionalTiles;
        public int stableWalls;
        public int stableHorizontals;
        public boolean freshEvidence;
        public int activeRedTiles,activeYellowTiles,activeGreenTiles;
        public String activeKind="";
        public float activeWidthM,activeHeightM;
        /** Coarse, quantized architectural fingerprint used only for readiness hysteresis. */
        public long topologySignature;

        public int observedTiles(){return redTiles+yellowTiles+greenTiles;}
        public int coveredPercent(){
            int total=observedTiles();
            return total<=0?0:Math.max(0,Math.min(100,Math.round((yellowTiles+greenTiles)*100f/total)));
        }
        public boolean visuallyReady(){
            // Do not call the scan ready merely because thousands of noisy tiles were revisited.
            // We need a plausible architectural set plus at least one horizontal reference.
            return stableWalls>=3&&stableWalls<=6&&stableHorizontals>=1
                    &&(yellowTiles+greenTiles)>=80&&greenTiles>=36&&coveredPercent()>=70;
        }
    }

    private final DepthSurfaceTracker tracker=new DepthSurfaceTracker();
    private final FloatBuffer vertexBuffer=ByteBuffer.allocateDirect(MAX_DRAW_TILES*6*3*4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final FloatBuffer uvBuffer=ByteBuffer.allocateDirect(MAX_DRAW_TILES*6*2*4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final float[] projection=new float[16];
    private final float[] view=new float[16];
    private final float[] vp=new float[16];
    private final Stats stats=new Stats();
    private volatile boolean resetRequested;
    private volatile SemanticLabel semanticFocus=SemanticLabel.UNKNOWN;
    private volatile float semanticFocusShare;
    private volatile long semanticFocusTimestampNs;
    private int lockedActiveSurfaceId=-1;
    private long lockedActiveSinceNs;
    // GL-thread scratch state. Keep the live overlay allocation-free: this path runs every frame.
    private final ArrayList<ScoredSurface> activeScoreWorkspace=new ArrayList<>();
    private final ArrayList<ScoredSurface> activeScorePool=new ArrayList<>();
    private final ArrayList<DepthSurfaceTracker.SurfaceSnapshot> activeSurfaceWorkspace=new ArrayList<>(MAX_ACTIVE_SURFACES);
    private final ArrayList<DepthSurfaceTracker.SurfaceSnapshot> architecturalWorkspace=new ArrayList<>(10);
    private final float[] activeLocal=new float[3],activeWorld=new float[3],activeCamera=new float[3];
    private final float[] activeNormalLocal=new float[3],activeNormalWorld=new float[3],activeCameraWorld=new float[3];
    private final float[] drawLocal=new float[3],drawWorld=new float[3];
    private int program,aPosition,aUv,uMvp,uColor;

    public void createOnGlThread(){
        String vs="uniform mat4 u_Mvp;\nattribute vec3 a_Position;\nattribute vec2 a_Uv;\nvarying vec2 v_Uv;\nvoid main(){v_Uv=a_Uv;gl_Position=u_Mvp*vec4(a_Position,1.0);}";
        String fs="precision mediump float;\nuniform vec4 u_Color;\nvarying vec2 v_Uv;\nvoid main(){float d=min(min(v_Uv.x,1.0-v_Uv.x),min(v_Uv.y,1.0-v_Uv.y));float edge=1.0-smoothstep(0.035,0.075,d);float a=mix(u_Color.a*0.035,u_Color.a,edge);gl_FragColor=vec4(u_Color.rgb,a);}";
        program=link(vs,fs);
        aPosition=GLES20.glGetAttribLocation(program,"a_Position");
        aUv=GLES20.glGetAttribLocation(program,"a_Uv");
        uMvp=GLES20.glGetUniformLocation(program,"u_Mvp");
        uColor=GLES20.glGetUniformLocation(program,"u_Color");
    }

    public void requestReset(){resetRequested=true;lockedActiveSurfaceId=-1;lockedActiveSinceNs=0;}

    public void setSemanticFocus(SemanticLabel label,float share,long timestampNs){
        semanticFocus=label==null?SemanticLabel.UNKNOWN:label;semanticFocusShare=share;semanticFocusTimestampNs=timestampNs;
    }

    public Stats stats(){
        Stats o=new Stats();o.visiblePlanes=stats.visiblePlanes;o.coveredPlanes=stats.coveredPlanes;
        o.redTiles=stats.redTiles;o.yellowTiles=stats.yellowTiles;o.greenTiles=stats.greenTiles;
        o.depthFallback=stats.depthFallback;o.provisionalTiles=stats.provisionalTiles;
        o.stableWalls=stats.stableWalls;o.stableHorizontals=stats.stableHorizontals;o.freshEvidence=stats.freshEvidence;
        o.activeRedTiles=stats.activeRedTiles;o.activeYellowTiles=stats.activeYellowTiles;o.activeGreenTiles=stats.activeGreenTiles;
        o.activeKind=stats.activeKind;o.activeWidthM=stats.activeWidthM;o.activeHeightM=stats.activeHeightM;o.topologySignature=stats.topologySignature;return o;
    }

    public void draw(Camera camera, Collection<Plane> ignoredPlanes, long frameTimestampNs,
                     ArCoreDepthCollector.DepthEvidenceFrame evidence,
                     Pose scanOriginPose, PlaneEvidenceTracker.Snapshot ignoredArchitectural){
        if(program==0||camera==null||scanOriginPose==null)return;
        if(resetRequested){resetRequested=false;tracker.reset();}
        stats.freshEvidence=evidence!=null&&evidence.localXyz!=null&&evidence.pointCount>0;
        if(stats.freshEvidence)tracker.integrate(evidence);

        DepthSurfaceTracker.Snapshot snap=tracker.snapshot(frameTimestampNs);
        List<DepthSurfaceTracker.SurfaceSnapshot> architectural=architecturalSurfaces(snap.surfaces);
        stats.redTiles=stats.yellowTiles=stats.greenTiles=0;
        stats.stableWalls=stats.stableHorizontals=0;
        for(DepthSurfaceTracker.SurfaceSnapshot s:architectural){
            if(s.kind==DepthSurfaceTracker.Kind.WALL)stats.stableWalls++;else stats.stableHorizontals++;
            for(DepthSurfaceTracker.TileSnapshot t:s.tiles){
                if(t==null)continue;if(t.state==1)stats.redTiles++;else if(t.state==2)stats.yellowTiles++;else stats.greenTiles++;
            }
        }
        stats.coveredPlanes=stats.stableWalls+stats.stableHorizontals;
        stats.topologySignature=topologySignature(architectural);
        stats.depthFallback=true;stats.provisionalTiles=0;stats.visiblePlanes=0;

        camera.getProjectionMatrix(projection,0,0.05f,25f);
        camera.getViewMatrix(view,0);
        Matrix.multiplyMM(vp,0,projection,0,view,0);

        List<DepthSurfaceTracker.SurfaceSnapshot> active=activeSurfaces(architectural,camera,scanOriginPose,frameTimestampNs);
        stats.activeRedTiles=stats.activeYellowTiles=stats.activeGreenTiles=0;stats.activeKind="";stats.activeWidthM=stats.activeHeightM=0;
        if(!active.isEmpty()){DepthSurfaceTracker.SurfaceSnapshot a=active.get(0);stats.activeKind=a.kind.name();stats.activeWidthM=a.widthM();stats.activeHeightM=a.heightM();}
        for(DepthSurfaceTracker.SurfaceSnapshot s:active)for(DepthSurfaceTracker.TileSnapshot t:s.tiles){
            if(t==null||frameTimestampNs-t.lastSeenNs>RECENT_TILE_NS)continue;
            if(t.state==1)stats.activeRedTiles++;else if(t.state==2)stats.activeYellowTiles++;else stats.activeGreenTiles++;
        }
        int visible=0;
        for(DepthSurfaceTracker.SurfaceSnapshot s:active){
            int before=visibleTileCount(s,frameTimestampNs);
            if(before<=0)continue;
            int drawn=0;
            // Persistent memory is intentionally faint. Strong color means "Depth confirms this
            // tile in the current view", not merely "we once saw it somewhere in this scan".
            drawn+=drawSurfaceState(s,2,frameTimestampNs,scanOriginPose,RECENT_TILE_NS,Long.MAX_VALUE,1.00f,0.72f,0.06f,0.08f);
            drawn+=drawSurfaceState(s,3,frameTimestampNs,scanOriginPose,RECENT_TILE_NS,Long.MAX_VALUE,0.18f,0.88f,0.42f,0.10f);
            drawn+=drawSurfaceState(s,1,frameTimestampNs,scanOriginPose,0,RECENT_TILE_NS,1.00f,0.22f,0.18f,0.72f);
            drawn+=drawSurfaceState(s,2,frameTimestampNs,scanOriginPose,0,RECENT_TILE_NS,1.00f,0.72f,0.06f,0.70f);
            drawn+=drawSurfaceState(s,3,frameTimestampNs,scanOriginPose,0,RECENT_TILE_NS,0.18f,0.88f,0.42f,0.62f);
            if(drawn>0)visible++;
        }
        stats.visiblePlanes=visible;
    }

    /** Select only the one or two architectural surfaces that are actually being observed now. */
    private List<DepthSurfaceTracker.SurfaceSnapshot> activeSurfaces(List<DepthSurfaceTracker.SurfaceSnapshot> all,
                                                                     Camera camera,Pose scanOrigin,long nowNs){
        activeScoreWorkspace.clear();activeSurfaceWorkspace.clear();
        List<ScoredSurface> scored=activeScoreWorkspace;
        if(all==null||camera==null||scanOrigin==null)return activeSurfaceWorkspace;
        SemanticLabel focus=(nowNs-semanticFocusTimestampNs<=2_500_000_000L&&semanticFocusShare>=0.30f)?semanticFocus:SemanticLabel.UNKNOWN;
        if(focus==SemanticLabel.OBJECT&&semanticFocusShare<0.58f)focus=SemanticLabel.UNKNOWN;
        if(focus.objectLike&&!focus.isOpening()&&!focus.isRoomEnvelope())return activeSurfaceWorkspace;
        Pose cameraPose=camera.getPose(),camInv=cameraPose.inverse();
        float[] local=activeLocal,world=activeWorld,cam=activeCamera,camWorld=activeCameraWorld;
        camWorld[0]=cameraPose.tx();camWorld[1]=cameraPose.ty();camWorld[2]=cameraPose.tz();
        for(DepthSurfaceTracker.SurfaceSnapshot s:all){
            if(s==null||nowNs-s.lastSeenNs>ACTIVE_SURFACE_NS)continue;
            if(focus==SemanticLabel.WALL||focus==SemanticLabel.DOOR||focus==SemanticLabel.WINDOW||focus==SemanticLabel.MIRROR){if(s.kind!=DepthSurfaceTracker.Kind.WALL)continue;}
            else if(focus==SemanticLabel.FLOOR){if(s.kind!=DepthSurfaceTracker.Kind.FLOOR)continue;}
            else if(focus==SemanticLabel.CEILING){if(s.kind!=DepthSurfaceTracker.Kind.CEILING)continue;}
            float mu=(s.minU+s.maxU)*0.5f,mv=(s.minV+s.maxV)*0.5f;
            local[0]=s.ox+s.ux*mu+s.vx*mv;local[1]=s.oy+s.uy*mu+s.vy*mv;local[2]=s.oz+s.uz*mu+s.vz*mv;
            scanOrigin.transformPoint(local,0,world,0);camInv.transformPoint(world,0,cam,0);
            float depth=-cam[2];if(!Float.isFinite(depth)||depth<0.20f||depth>5.5f)continue;

            activeNormalLocal[0]=s.nx;activeNormalLocal[1]=s.ny;activeNormalLocal[2]=s.nz;
            scanOrigin.rotateVector(activeNormalLocal,0,activeNormalWorld,0);float[] nw=activeNormalWorld;
            float vx=camWorld[0]-world[0],vy=camWorld[1]-world[1],vz=camWorld[2]-world[2];
            float vl=(float)Math.sqrt(vx*vx+vy*vy+vz*vz);float incidence=vl<1e-4f?1f:Math.abs((nw[0]*vx+nw[1]*vy+nw[2]*vz)/vl);
            if(s.kind==DepthSurfaceTracker.Kind.WALL&&incidence<0.20f)continue; // grazing wall views flicker and clutter

            float age=(nowNs-s.lastSeenNs)/1_000_000_000f;
            float area=Math.min(6f,Math.max(0.1f,s.areaM2()));
            float nx=cam[0]/Math.max(.2f,depth),ny=cam[1]/Math.max(.2f,depth);
            float centrality=1f/(1f+3.5f*(nx*nx+ny*ny));
            float score=(1.0f-Math.min(1f,age/(ACTIVE_SURFACE_NS/1_000_000_000f)))*4.0f
                    +Math.min(1f,area/3f)*0.8f+1.0f/(0.6f+depth)*0.35f+centrality*1.25f+incidence*0.65f;
            int slot=scored.size();
            ScoredSurface scoredSurface;
            if(slot<activeScorePool.size())scoredSurface=activeScorePool.get(slot);
            else{scoredSurface=new ScoredSurface();activeScorePool.add(scoredSurface);}
            scoredSurface.set(s,score);scored.add(scoredSurface);
        }
        java.util.Collections.sort(scored,new java.util.Comparator<ScoredSurface>(){@Override public int compare(ScoredSurface a,ScoredSurface b){return Float.compare(b.score,a.score);}});
        List<DepthSurfaceTracker.SurfaceSnapshot> out=activeSurfaceWorkspace;if(scored.isEmpty()){lockedActiveSurfaceId=-1;return out;}

        ScoredSurface top=scored.get(0),locked=null;
        for(ScoredSurface x:scored)if(x.surface.id==lockedActiveSurfaceId){locked=x;break;}
        ScoredSurface chosen=top;
        // Hysteresis: keep the current physical surface unless a new one is clearly better.
        if(locked!=null&&nowNs-lockedActiveSinceNs<ACTIVE_LOCK_NS)chosen=locked;
        else if(locked!=null&&locked.score>=top.score*0.82f)chosen=locked;
        if(chosen.surface.id!=lockedActiveSurfaceId){lockedActiveSurfaceId=chosen.surface.id;lockedActiveSinceNs=nowNs;}
        out.add(chosen.surface);return out;
    }

    private static final class ScoredSurface{
        DepthSurfaceTracker.SurfaceSnapshot surface;float score;
        void set(DepthSurfaceTracker.SurfaceSnapshot s,float q){surface=s;score=q;}
    }


    private List<DepthSurfaceTracker.SurfaceSnapshot> architecturalSurfaces(List<DepthSurfaceTracker.SurfaceSnapshot> input){
        architecturalWorkspace.clear();
        List<DepthSurfaceTracker.SurfaceSnapshot> out=architecturalWorkspace;
        int walls=0,horiz=0;
        if(input==null)return out;
        // tracker.snapshot() is already sorted by supportPoints descending, so the strongest
        // representative wins when several fits describe the same physical plane.
        for(DepthSurfaceTracker.SurfaceSnapshot s:input){
            if(s==null||!s.stable())continue;
            boolean dup=false;
            for(DepthSurfaceTracker.SurfaceSnapshot k:out){
                if(k.kind!=s.kind)continue;
                float dot=Math.abs(k.nx*s.nx+k.ny*s.ny+k.nz*s.nz);
                if(dot<0.94f)continue;
                float dx=s.ox-k.ox,dy=s.oy-k.oy,dz=s.oz-k.oz;
                float sep=Math.abs(dx*k.nx+dy*k.ny+dz*k.nz);
                if(sep<=0.20f){dup=true;break;}
            }
            if(dup)continue;
            if(s.kind==DepthSurfaceTracker.Kind.WALL){if(walls>=8)continue;walls++;}
            else{if(horiz>=2)continue;horiz++;}
            out.add(s);
        }
        return out;
    }

    private int visibleTileCount(DepthSurfaceTracker.SurfaceSnapshot s,long now){
        int n=0;for(DepthSurfaceTracker.TileSnapshot t:s.tiles){
            if(t==null)continue;
            if(t.state==1 && now-t.lastSeenNs>ACTIVE_RED_TILE_NS)continue;
            n++;
        }return n;
    }

    private int drawSurfaceState(DepthSurfaceTracker.SurfaceSnapshot s,int desired,long now,Pose scanOrigin,
                                 long minAgeNs,long maxAgeNs,
                                 float r,float g,float b,float a){
        vertexBuffer.clear();uvBuffer.clear();int tiles=0;float half=TILE_M*0.47f;
        float[] local=drawLocal,world=drawWorld;
        for(DepthSurfaceTracker.TileSnapshot t:s.tiles){
            if(tiles>=MAX_DRAW_TILES)break;
            if(t==null||t.state!=desired)continue;
            long age=Math.max(0,now-t.lastSeenNs);if(age<minAgeNs||age>maxAgeNs)continue;
            // Old red tiles are only "we saw this once" noise.  Keep them in memory, but don't
            // paint a permanent red cage over the room.  Yellow/green remain persistent and come
            // back when this physical surface becomes active again.
            if(desired==1 && now-t.lastSeenNs>ACTIVE_RED_TILE_NS)continue;
            float u0=t.centerU-half,u1=t.centerU+half,v0=t.centerV-half,v1=t.centerV+half;
            putCorner(s,u0,v0,scanOrigin,local,world,0,0);
            putCorner(s,u1,v0,scanOrigin,local,world,1,0);
            putCorner(s,u1,v1,scanOrigin,local,world,1,1);
            putCorner(s,u0,v0,scanOrigin,local,world,0,0);
            putCorner(s,u1,v1,scanOrigin,local,world,1,1);
            putCorner(s,u0,v1,scanOrigin,local,world,0,1);
            tiles++;
        }
        if(tiles<=0)return 0;
        vertexBuffer.flip();uvBuffer.flip();
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);GLES20.glUniformMatrix4fv(uMvp,1,false,vp,0);GLES20.glUniform4f(uColor,r,g,b,a);
        GLES20.glEnableVertexAttribArray(aPosition);GLES20.glVertexAttribPointer(aPosition,3,GLES20.GL_FLOAT,false,0,vertexBuffer);
        GLES20.glEnableVertexAttribArray(aUv);GLES20.glVertexAttribPointer(aUv,2,GLES20.GL_FLOAT,false,0,uvBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,tiles*6);
        GLES20.glDisableVertexAttribArray(aPosition);GLES20.glDisableVertexAttribArray(aUv);GLES20.glDisable(GLES20.GL_BLEND);
        return tiles;
    }

    private void putCorner(DepthSurfaceTracker.SurfaceSnapshot s,float u,float v,Pose scanOrigin,
                           float[] local,float[] world,float tu,float tv){
        local[0]=s.ox+s.ux*u+s.vx*v;local[1]=s.oy+s.uy*u+s.vy*v;local[2]=s.oz+s.uz*u+s.vz*v;
        scanOrigin.transformPoint(local,0,world,0);
        vertexBuffer.put(world[0]).put(world[1]).put(world[2]);uvBuffer.put(tu).put(tv);
    }

    /**
     * Coarse architectural fingerprint.  It intentionally ignores centimeter-level refinement,
     * but changes when a wall moves/rotates/grows enough to mean the room topology is still
     * settling.  Readiness must hold the same fingerprint for several seconds.
     */
    private static long topologySignature(List<DepthSurfaceTracker.SurfaceSnapshot> surfaces){
        if(surfaces==null||surfaces.isEmpty())return 0L;
        java.util.ArrayList<Long> parts=new java.util.ArrayList<>();
        for(DepthSurfaceTracker.SurfaceSnapshot s:surfaces){
            if(s==null||!s.stable())continue;
            double angle=Math.atan2(s.nz,s.nx);
            int ang=(int)Math.round(angle/Math.toRadians(12.0));
            double plane=s.ox*s.nx+s.oy*s.ny+s.oz*s.nz;
            int off=(int)Math.round(plane/0.20);
            int w=(int)Math.round(s.widthM()/0.25);
            int h=(int)Math.round(s.heightM()/0.25);
            int kind=s.kind==DepthSurfaceTracker.Kind.WALL?1:(s.kind==DepthSurfaceTracker.Kind.FLOOR?2:3);
            long v=1469598103934665603L;
            v=(v^(kind&0xff))*1099511628211L;v=(v^(ang&0xffff))*1099511628211L;
            v=(v^(off&0xffff))*1099511628211L;v=(v^(w&0xffff))*1099511628211L;v=(v^(h&0xffff))*1099511628211L;
            parts.add(v);
        }
        java.util.Collections.sort(parts);long out=1125899906842597L;
        for(Long x:parts)out=31*out+(x==null?0:x.longValue());
        return out;
    }

    private static int compile(int type,String source){
        int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0){String log=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new RuntimeException("Grid shader compile failed: "+log);}return s;
    }
    private static int link(String vs,String fs){int v=compile(GLES20.GL_VERTEX_SHADER,vs),f=compile(GLES20.GL_FRAGMENT_SHADER,fs);int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0){String log=GLES20.glGetProgramInfoLog(p);GLES20.glDeleteProgram(p);throw new RuntimeException("Grid program link failed: "+log);}return p;}
}
