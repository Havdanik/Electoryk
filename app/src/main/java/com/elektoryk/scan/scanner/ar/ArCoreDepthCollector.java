package com.elektoryk.scan.scanner.ar;

import android.media.Image;

import com.elektoryk.scan.model.ScanPathPoint;
import com.elektoryk.scan.reconstruction.VoxelPointCloud;
import com.elektoryk.scan.scanner.LiveScanCoverage;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Plane;
import com.google.ar.core.TrackingState;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;

public class ArCoreDepthCollector {
    private static final int MIN_ACCEPTED_FRAME_POINTS = 10;
    // Depth processing runs on the GL thread. Sampling every render frame can stall the camera.
    private static final long MIN_DEPTH_ATTEMPT_INTERVAL_NS = 140_000_000L; // ~7 Hz
    private static final long STATS_CACHE_NS = 280_000_000L;                // ~3.5 Hz UI model
    private static final int RAW_TARGET_SAMPLES = 900;
    private static final int SMOOTH_TARGET_SAMPLES = 520;
    private static final int[] SUPPORT_DX={-2,2,0,0,-1,1,-1,1};
    private static final int[] SUPPORT_DY={0,0,-2,2,-1,-1,1,1};

    public static class Stats {
        public int depthFrames;
        public int rawDepthFrames;
        public int fallbackDepthFrames;
        public int rawSamples;
        public int acceptedHighConfidenceSamples;
        public int voxelPoints;
        public int confirmedVoxelPoints;
        public int directionCoveragePercent;
        public int pathSamples;
        public int meanRawConfidence;
        public long lastDepthTimestampNs;
        public int spatialCoveragePercent;
        public int verticalCoveragePercent;
        public int lowBandPercent;
        public int midBandPercent;
        public int highBandPercent;
        public int scanQualityPercent;
        public float currentMeanDistanceM;
        public int currentNearPercent;
        public int currentOptimalPercent;
        public int currentFarPercent;
        public float currentSpeedMps;
        public int staleRawFrames;
        public int staleSmoothFrames;
        public int rawTimestampSkewFrames;
        public int confidenceTimestampSkewFrames;
        public float lastRawFrameSkewMs;
        public int rejectedDepthOutliers;
        public int poseJumpRejects;
        public int planeCount;
        public int verticalPlaneCount;
        public int planeSupportedSamples;
        public int planeRescuedSamples;
        public int multiViewPercent;
        public boolean floorPlaneLocked;
        public boolean ceilingPlaneLocked;
        public float floorDistanceM=Float.NaN;
        public float nearestWallDistanceM=Float.NaN;
        public float ceilingDistanceM=Float.NaN;
        public float floorPlaneY=Float.NaN;
        public float ceilingPlaneY=Float.NaN;
        public float wallYawHintRad=Float.NaN;
        public float wallYawConfidence;
        public boolean rawPrecisionMode;
        public boolean finishRecommended;
        public String liveHint = "";
        public LiveScanCoverage.Snapshot coverageSnapshot;
    }

    /** Immutable accepted Depth evidence from the newest real depth frame. */
    public static final class DepthEvidenceFrame {
        public final long timestampNs;
        public final int frameId;
        /** Camera and points in ARCore world coordinates (rendering/debug compatibility). */
        public final float cameraX, cameraY, cameraZ;
        public final float[] worldXyz;
        /** Camera and points in the immutable scan-local coordinate system. */
        public final float localCameraX, localCameraY, localCameraZ;
        public final float[] localXyz;
        public final int pointCount;

        private DepthEvidenceFrame(long timestampNs,int frameId,float cameraX,float cameraY,float cameraZ,
                                   float[] worldXyz,float localCameraX,float localCameraY,float localCameraZ,
                                   float[] localXyz,int pointCount){
            this.timestampNs=timestampNs;this.frameId=frameId;
            this.cameraX=cameraX;this.cameraY=cameraY;this.cameraZ=cameraZ;
            this.worldXyz=worldXyz;
            this.localCameraX=localCameraX;this.localCameraY=localCameraY;this.localCameraZ=localCameraZ;
            this.localXyz=localXyz;this.pointCount=pointCount;
        }
    }

    private final VoxelPointCloud cloud = new VoxelPointCloud(0.040, 260000);
    private final PlaneEvidenceTracker planeTracker = new PlaneEvidenceTracker();
    private final BitSet directionBins = new BitSet(24);
    private final List<ScanPathPoint> cameraPath = new ArrayList<>();
    private final LiveScanCoverage liveCoverage = new LiveScanCoverage();
    private long lastDepthNs;
    private long lastCollectAttemptNs;
    private long lastRawImageTimestampNs;
    private long lastSmoothedImageTimestampNs;
    private int depthFrames;
    private int rawDepthFrames;
    private int fallbackDepthFrames;
    private long confidenceSum;
    private long confidenceCount;
    private int acceptedHighConfidenceSamples;
    private int sampleFrameSerial;
    private float currentMeanDistanceM;
    private int currentNearPercent;
    private int currentOptimalPercent;
    private int currentFarPercent;
    private Stats cachedStats;
    private long cachedStatsAtNs;
    private boolean statsDirty=true;
    private boolean smoothedFallbackEnabled=true;
    private int staleRawFrames;
    private int staleSmoothFrames;
    private int rawTimestampSkewFrames;
    private int confidenceTimestampSkewFrames;
    private float lastRawFrameSkewMs;
    private int rejectedDepthOutliers;
    private int poseJumpRejects;
    private int planeSupportedSamples;
    private int planeRescuedSamples;
    private boolean hasAcceptedCameraPose;
    private float lastAcceptedCameraX,lastAcceptedCameraY,lastAcceptedCameraZ;
    private long lastAcceptedCameraTs;
    private float[] pendingEvidenceWorldXyz=new float[0];
    private float[] pendingEvidenceLocalXyz=new float[0];
    private int pendingEvidencePointCount;
    private int pendingEvidenceFrameId;
    private float pendingEvidenceCameraX,pendingEvidenceCameraY,pendingEvidenceCameraZ;
    private float pendingEvidenceLocalCameraX,pendingEvidenceLocalCameraY,pendingEvidenceLocalCameraZ;
    private volatile DepthEvidenceFrame latestDepthEvidence;

    // Reusable GL-thread workspaces. Depth collection is synchronized, so these buffers never race.
    // Keeping them here avoids several large int/float allocations every ~140 ms depth pass.
    private int[] sampleXs=new int[0],sampleYs=new int[0],sampleDepths=new int[0],sampleConfs=new int[0];
    private float[] projectLocalX=new float[0],projectLocalY=new float[0],projectLocalZ=new float[0],projectLocalC=new float[0];
    private float[] projectWorldXyz=new float[0];
    private final float[] pointCamera=new float[3],pointWorld=new float[3],pointLocal=new float[3];
    // Reused for every accepted Depth sample. PlaneEvidenceTracker.match() used to allocate a
    // Match object per point, causing thousands of short-lived objects per collected frame.
    private final PlaneEvidenceTracker.Match planeMatchScratch=new PlaneEvidenceTracker.Match();
    private final float[] forwardAxis=new float[]{0f,0f,-1f},forwardScratch=new float[3];

    private void ensureDepthWorkspace(int cap){
        if(cap<=sampleXs.length)return;
        int grown=Math.max(cap,Math.max(256,sampleXs.length+(sampleXs.length>>1)+1));
        sampleXs=new int[grown];sampleYs=new int[grown];sampleDepths=new int[grown];sampleConfs=new int[grown];
    }
    private void ensureProjectionWorkspace(int cap){
        if(cap<=projectLocalX.length)return;
        int grown=Math.max(cap,Math.max(256,projectLocalX.length+(projectLocalX.length>>1)+1));
        projectLocalX=new float[grown];projectLocalY=new float[grown];projectLocalZ=new float[grown];projectLocalC=new float[grown];
        projectWorldXyz=new float[grown*3];
    }

    public synchronized void reset() {
        cloud.clear();
        directionBins.clear();
        cameraPath.clear();
        lastDepthNs=0;
        lastCollectAttemptNs=0;
        lastRawImageTimestampNs=0;
        lastSmoothedImageTimestampNs=0;
        depthFrames=0;
        rawDepthFrames=0;
        fallbackDepthFrames=0;
        confidenceSum=0;
        confidenceCount=0;
        acceptedHighConfidenceSamples=0;
        sampleFrameSerial=0;
        currentMeanDistanceM=0;
        currentNearPercent=0;
        currentOptimalPercent=0;
        currentFarPercent=0;
        liveCoverage.reset();
        planeTracker.reset();
        staleRawFrames=0;staleSmoothFrames=0;rawTimestampSkewFrames=0;confidenceTimestampSkewFrames=0;lastRawFrameSkewMs=0f;
        rejectedDepthOutliers=0;poseJumpRejects=0;planeSupportedSamples=0;planeRescuedSamples=0;
        hasAcceptedCameraPose=false;lastAcceptedCameraX=lastAcceptedCameraY=lastAcceptedCameraZ=0;lastAcceptedCameraTs=0;
        pendingEvidenceWorldXyz=new float[0];pendingEvidenceLocalXyz=new float[0];pendingEvidencePointCount=0;pendingEvidenceFrameId=0;
        pendingEvidenceCameraX=pendingEvidenceCameraY=pendingEvidenceCameraZ=0f;
        pendingEvidenceLocalCameraX=pendingEvidenceLocalCameraY=pendingEvidenceLocalCameraZ=0f;latestDepthEvidence=null;
        cachedStats=null;
        cachedStatsAtNs=0;
        statsDirty=true;
    }

    public synchronized void setSmoothedFallbackEnabled(boolean enabled){
        smoothedFallbackEnabled=enabled;statsDirty=true;
    }

    /** Lightweight GL-thread signal used by the AR surface overlay. */
    public synchronized long lastAcceptedDepthTimestampNs(){
        return lastDepthNs;
    }

    /** Lightweight immutable snapshot; the backing array is never mutated after publication. */
    public DepthEvidenceFrame latestDepthEvidence(){
        return latestDepthEvidence;
    }

    public synchronized PlaneEvidenceTracker.Snapshot planeSnapshot(){
        return planeTracker.snapshot();
    }

    public synchronized boolean shouldCollectPlanes(long frameTimestampNs){
        return planeTracker.shouldUpdate(frameTimestampNs);
    }

    public synchronized void collectPlanes(Collection<Plane> planes,Pose originPose,Pose cameraWorldPose,long frameTimestampNs){
        planeTracker.update(planes,originPose,cameraWorldPose,frameTimestampNs);
        PlaneEvidenceTracker.Snapshot ps=planeTracker.snapshot();
        liveCoverage.setArchitecturalLevels(ps.floorY,ps.floorLocked,ps.ceilingY,ps.ceilingLocked);
        statsDirty=true;
    }

    public synchronized void collect(Frame frame, Pose originPose) {
        Camera camera = frame.getCamera();
        if (camera.getTrackingState() != TrackingState.TRACKING || originPose == null) return;
        long ts=frame.getTimestamp();
        if(ts==0 || (lastCollectAttemptNs!=0 && ts-lastCollectAttemptNs<MIN_DEPTH_ATTEMPT_INTERVAL_NS))return;
        // Throttle acquisition attempts too, not only successful frames. Otherwise a temporarily
        // unavailable Raw Depth stream can be queried at the full 60 FPS and freeze the preview.
        lastCollectAttemptNs=ts;

        Pose cameraInOrigin = originPose.inverse().compose(camera.getPose());
        if(hasAcceptedCameraPose && lastAcceptedCameraTs>0){
            double dt=(ts-lastAcceptedCameraTs)/1_000_000_000.0;
            if(dt>0 && dt<1.2){
                float dx=cameraInOrigin.tx()-lastAcceptedCameraX;
                float dy=cameraInOrigin.ty()-lastAcceptedCameraY;
                float dz=cameraInOrigin.tz()-lastAcceptedCameraZ;
                float jump=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                // At the 7 Hz depth cadence, a >0.75 m step is almost certainly relocalization / pose shock.
                if(jump>0.75f || Math.abs(dy)>0.42f){
                    poseJumpRejects++;statsDirty=true;return;
                }
            }
        }
        cameraInOrigin.rotateVector(forwardAxis,0,forwardScratch,0);
        float yaw=(float)Math.atan2(forwardScratch[0],-forwardScratch[2]);
        // Camera motion is useful for speed guidance even before a usable depth image arrives.
        liveCoverage.updateCamera(cameraInOrigin.tx(),cameraInOrigin.ty(),cameraInOrigin.tz(),yaw,ts);
        statsDirty=true;

        boolean collected=false;
        int accepted=0;
        Image rawDepth=null;
        Image confidence=null;
        try {
            rawDepth=frame.acquireRawDepthImage16Bits();
            confidence=frame.acquireRawDepthConfidenceImage();
            long rawTs=rawDepth.getTimestamp();
            long confidenceTs=confidence.getTimestamp();
            if(rawTs!=0){
                if(rawTs==lastRawImageTimestampNs){
                    // Same raw-depth timestamp means ARCore is reprojecting the previous depth
                    // estimate. Never integrate it as another view: that would fake multi-view
                    // confirmation and can thicken/duplicate walls while the phone moves.
                    staleRawFrames++;
                }else{
                    // IMPORTANT: Samsung/ARCore builds do not always expose the raw-depth image
                    // timestamp as bit-identical to Frame.getTimestamp(). ARCore's API contract
                    // for detecting genuinely NEW raw depth is the raw image timestamp changing
                    // relative to the previous raw image. Treat frame-vs-depth skew as telemetry,
                    // not as a reason to throw away all depth.
                    lastRawImageTimestampNs=rawTs;
                    long skewNs=rawTs-ts;
                    lastRawFrameSkewMs=skewNs/1_000_000f;
                    if(skewNs!=0)rawTimestampSkewFrames++;
                    if(confidenceTs!=0 && confidenceTs!=rawTs)confidenceTimestampSkewFrames++;
                    // acquireRawDepthConfidenceImage() is defined as the confidence image
                    // corresponding to the raw depth image for this Frame. Validate dimensions
                    // inside sampleRawDepth(), but do not reject a vendor build solely because
                    // its Image timestamp metadata differs.
                    accepted=sampleRawDepth(camera,rawDepth,confidence,originPose,cameraInOrigin);
                    if(accepted>=MIN_ACCEPTED_FRAME_POINTS){
                        rawDepthFrames++;
                        depthFrames++;
                        collected=true;
                    }
                }
            }
        } catch (Exception ignored) {
            // Raw Depth is sparse and may be temporarily unavailable.
        } finally {
            if(rawDepth!=null)rawDepth.close();
            if(confidence!=null)confidence.close();
        }

        // Smoothed depth is a robustness fallback only. De-duplicate by image timestamp so a
        // reprojected/old depth image cannot inflate coverage and quality counters.
        if(!collected && smoothedFallbackEnabled){
            Image depth=null;
            try{
                depth=frame.acquireDepthImage16Bits();
                long depthTs=depth.getTimestamp();
                if(depthTs!=0){
                    if(depthTs==lastSmoothedImageTimestampNs){
                        staleSmoothFrames++;
                    }else{
                        // Full/smoothed depth may legally be the newest depth image from an
                        // earlier camera frame when an up-to-date estimate is not ready yet.
                        // De-duplicate by the depth image's own timestamp instead of requiring
                        // exact equality with Frame.getTimestamp().
                        lastSmoothedImageTimestampNs=depthTs;
                        accepted=sampleSmoothedDepth(camera,depth,originPose,cameraInOrigin);
                        if(accepted>=MIN_ACCEPTED_FRAME_POINTS){
                            fallbackDepthFrames++;
                            depthFrames++;
                            collected=true;
                        }
                    }
                }
            }catch(Exception ignored){
            }finally{
                if(depth!=null)depth.close();
            }
        }

        // A direction/path sample is valid only when this frame contributed actual geometry.
        if(collected){
            updateDirectionCoverage(cameraInOrigin);
            updatePath(cameraInOrigin,ts);
            lastDepthNs=ts;
            if(pendingEvidencePointCount>=MIN_ACCEPTED_FRAME_POINTS && pendingEvidenceWorldXyz.length>=pendingEvidencePointCount*3){
                latestDepthEvidence=new DepthEvidenceFrame(ts,pendingEvidenceFrameId,
                        pendingEvidenceCameraX,pendingEvidenceCameraY,pendingEvidenceCameraZ,
                        pendingEvidenceWorldXyz,
                        pendingEvidenceLocalCameraX,pendingEvidenceLocalCameraY,pendingEvidenceLocalCameraZ,
                        pendingEvidenceLocalXyz,pendingEvidencePointCount);
            }
            hasAcceptedCameraPose=true;
            lastAcceptedCameraX=cameraInOrigin.tx();lastAcceptedCameraY=cameraInOrigin.ty();lastAcceptedCameraZ=cameraInOrigin.tz();
            lastAcceptedCameraTs=ts;
        }
    }

    private int sampleRawDepth(Camera camera,Image depth,Image confidence,Pose originPose,Pose cameraInOrigin){
        int w=depth.getWidth(),h=depth.getHeight();
        if(w<=0||h<=0||confidence.getWidth()!=w||confidence.getHeight()!=h)return 0;

        Image.Plane dp=depth.getPlanes()[0];
        Image.Plane cp=confidence.getPlanes()[0];
        ByteBuffer db=dp.getBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer cb=cp.getBuffer().duplicate();

        int dRow=dp.getRowStride(),dPix=dp.getPixelStride();
        int cRow=cp.getRowStride(),cPix=cp.getPixelStride();

        int step=sampleStepFor(w,h,RAW_TARGET_SAMPLES,4);
        int cap=((w+step-1)/step)*((h+step-1)/step);
        ensureDepthWorkspace(cap);
        int[] xs=sampleXs,ys=sampleYs,depths=sampleDepths,confs=sampleConfs;
        int n=0;

        // Stagger the sampling lattice between depth frames. This increases spatial density
        // over time without increasing per-frame CPU load.
        int phase=sampleFrameSerial%9;
        int offX=Math.min(step-1,Math.max(0,step/6+(phase%3)*step/3));
        int offY=Math.min(step-1,Math.max(0,step/6+((phase/3)%3)*step/3));
        for(int y=offY;y<h;y+=step){
            for(int x=offX;x<w;x+=step){
                int di=y*dRow+x*dPix;
                int ci=y*cRow+x*cPix;
                if(di<0||di+1>=db.limit()||ci<0||ci>=cb.limit())continue;
                int mm=Short.toUnsignedInt(db.getShort(di));
                int conf=Byte.toUnsignedInt(cb.get(ci));
                if(mm<450||mm>6000)continue;
                int strongMinConf=mm>5000?176:(mm>3500?148:128);
                // Never trust extremely weak raw depth. The 96..strong threshold band is only a
                // candidate: it must have local support here and later independent plane support.
                if(conf<96)continue;
                if(conf<218 && !hasLocalRawSupport(db,cb,dRow,dPix,cRow,cPix,w,h,x,y,mm,conf)){
                    rejectedDepthOutliers++;continue;
                }
                // Strong pixels continue normally. We also keep weaker candidates so
                // projectSamples can check them against ARCore's structural plane model in XYZ.
                xs[n]=x;ys[n]=y;depths[n]=mm;confs[n]=conf;n++;
            }
        }
        if(n<MIN_ACCEPTED_FRAME_POINTS)return 0;
        return projectSamples(camera,originPose,cameraInOrigin,w,h,xs,ys,depths,confs,n,true,++sampleFrameSerial);
    }

    private boolean hasLocalRawSupport(ByteBuffer depth,ByteBuffer conf,
                                       int dRow,int dPix,int cRow,int cPix,int w,int h,
                                       int x,int y,int centerMm,int centerConf){
        int support=0;
        int tol=Math.max(55,(int)(centerMm*0.028f));
        for(int k=0;k<SUPPORT_DX.length;k++){
            int xx=x+SUPPORT_DX[k],yy=y+SUPPORT_DY[k];
            if(xx<0||yy<0||xx>=w||yy>=h)continue;
            int di=yy*dRow+xx*dPix,ci=yy*cRow+xx*cPix;
            if(di<0||di+1>=depth.limit()||ci<0||ci>=conf.limit())continue;
            int mm=Short.toUnsignedInt(depth.getShort(di));
            int cc=Byte.toUnsignedInt(conf.get(ci));
            if(mm==0||cc<96)continue;
            if(Math.abs(mm-centerMm)<=tol) support++;
            if(support>=(centerConf>=170?1:2))return true;
        }
        return false;
    }

    private int sampleSmoothedDepth(Camera camera,Image image,Pose originPose,Pose cameraInOrigin){
        int w=image.getWidth(),h=image.getHeight();
        if(w<=0||h<=0)return 0;
        Image.Plane plane=image.getPlanes()[0];
        ByteBuffer buf=plane.getBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int row=plane.getRowStride(),pix=plane.getPixelStride();

        int step=sampleStepFor(w,h,SMOOTH_TARGET_SAMPLES,6);
        int cap=((w+step-1)/step)*((h+step-1)/step);
        ensureDepthWorkspace(cap);
        int[] xs=sampleXs,ys=sampleYs,depths=sampleDepths,confs=sampleConfs;
        int n=0;
        int smoothPhase=(sampleFrameSerial+4)%9;
        int offX=Math.min(step-1,Math.max(0,step/6+(smoothPhase%3)*step/3));
        int offY=Math.min(step-1,Math.max(0,step/6+((smoothPhase/3)%3)*step/3));
        for(int y=offY;y<h;y+=step){
            for(int x=offX;x<w;x+=step){
                int i=y*row+x*pix;
                if(i<0||i+1>=buf.limit())continue;
                int mm=Short.toUnsignedInt(buf.getShort(i));
                if(mm<500||mm>5000)continue;
                xs[n]=x;ys[n]=y;depths[n]=mm;confs[n]=92;n++;
            }
        }
        if(n<MIN_ACCEPTED_FRAME_POINTS)return 0;
        return projectSamples(camera,originPose,cameraInOrigin,w,h,xs,ys,depths,confs,n,false,++sampleFrameSerial);
    }

    /**
     * Unprojects pixels exactly in the coordinate system expected by ARCore Raw Depth:
     * texture/GPU intrinsics scaled to the current depth-image resolution.
     */
    private int projectSamples(Camera camera,Pose originPose,Pose cameraInOrigin,int depthWidth,int depthHeight,
                               int[] xs,int[] ys,int[] depths,int[] confs,int n,boolean raw,int frameId){
        if(n<=0)return 0;

        CameraIntrinsics intr=camera.getTextureIntrinsics();
        float[] focal=intr.getFocalLength();
        float[] principal=intr.getPrincipalPoint();
        int[] dimensions=intr.getImageDimensions();
        if(focal==null||principal==null||dimensions==null||focal.length<2||principal.length<2||dimensions.length<2)
            return 0;

        if(dimensions[0]<=0||dimensions[1]<=0||depthWidth<=0||depthHeight<=0)return 0;
        // Compute scaled texture intrinsics as scalars. The previous helper returned a new
        // Intrinsics object for every collected Depth frame.
        float intrSx=depthWidth/(float)dimensions[0],intrSy=depthHeight/(float)dimensions[1];
        float depthFx=focal[0]*intrSx,depthFy=focal[1]*intrSy;
        float depthCx=principal[0]*intrSx,depthCy=principal[1]*intrSy;
        if(depthFx==0f||depthFy==0f)return 0;

        Pose pose=camera.getPose();
        Pose originInverse=originPose.inverse();
        float distanceSum=0f;
        int distanceCount=0,nearCount=0,optimalCount=0,farCount=0;
        int accepted=0;
        ensureProjectionWorkspace(n);
        float[] localX=projectLocalX,localY=projectLocalY,localZ=projectLocalZ,localC=projectLocalC;
        float[] acceptedWorldXyz=projectWorldXyz;

        for(int i=0;i<n;i++){
            float d=depths[i]/1000f;
            if(!DepthProjectionMath.unprojectInto(xs[i],ys[i],d,depthFx,depthFy,depthCx,depthCy,pointCamera,0))continue;
            pose.transformPoint(pointCamera,0,pointWorld,0);
            originInverse.transformPoint(pointWorld,0,pointLocal,0);
            float[] world=pointWorld,local=pointLocal;
            if(!Float.isFinite(local[0])||!Float.isFinite(local[1])||!Float.isFinite(local[2]))continue;

            float confidence=Math.max(0.02f,Math.min(1f,confs[i]/255f));
            // ARCore is most accurate around 0.5-5 m. De-weight the long tail aggressively instead
            // of allowing distant estimates to pull structural planes.
            float distanceWeight=d<=3.0f?1f:(d<=5.0f?(1f-(d-3f)*0.18f):0.58f-(d-5f)*0.20f);
            distanceWeight=Math.max(0.20f,Math.min(1f,distanceWeight));
            float sourceWeight=raw?1f:0.42f;
            float weightedConfidence=confidence*distanceWeight*sourceWeight;
            PlaneEvidenceTracker.Match pm=planeTracker.matchInto(local[0],local[1],local[2],planeMatchScratch);
            if(raw){
                int strongMinConf=depths[i]>5000?176:(depths[i]>3500?148:128);
                if(confs[i]<strongMinConf){
                    if(!pm.matched || pm.support<0.30f){rejectedDepthOutliers++;continue;}
                    planeRescuedSamples++;
                }
            }
            if(pm.matched){
                // Independent ARCore plane support raises structural authority.
                weightedConfidence=Math.min(1f,weightedConfidence*(1f+0.22f*pm.support));
                planeSupportedSamples++;
            }else{
                // Keep unsupported geometry for furniture/niches, but let confirmed architectural
                // surfaces dominate room-axis and boundary estimation downstream.
                weightedConfidence*=raw?0.82f:0.68f;
            }
            liveCoverage.observeSurface(cameraInOrigin.tx(),cameraInOrigin.ty(),cameraInOrigin.tz(),
                    local[0],local[1],local[2],weightedConfidence,d,frameId,
                    pm.matched?pm.band:-1,pm.matched?pm.support:0f);
            localX[accepted]=local[0];localY[accepted]=local[1];localZ[accepted]=local[2];localC[accepted]=weightedConfidence;
            int wi=accepted*3;acceptedWorldXyz[wi]=world[0];acceptedWorldXyz[wi+1]=world[1];acceptedWorldXyz[wi+2]=world[2];
            accepted++;
            distanceSum+=d;distanceCount++;
            if(d<0.65f)nearCount++;
            else if(d<=3.60f)optimalCount++;
            else farCount++;

            if(raw){
                confidenceSum+=confs[i];
                confidenceCount++;
                acceptedHighConfidenceSamples++;
            }
        }
        if(accepted>=MIN_ACCEPTED_FRAME_POINTS){
            float[] exactWorld=new float[accepted*3];
            float[] exactLocal=new float[accepted*3];
            System.arraycopy(acceptedWorldXyz,0,exactWorld,0,accepted*3);
            for(int q=0;q<accepted;q++){
                int qi=q*3;exactLocal[qi]=localX[q];exactLocal[qi+1]=localY[q];exactLocal[qi+2]=localZ[q];
            }
            pendingEvidenceWorldXyz=exactWorld;pendingEvidenceLocalXyz=exactLocal;
            pendingEvidencePointCount=accepted;pendingEvidenceFrameId=frameId;
            pendingEvidenceCameraX=pose.tx();pendingEvidenceCameraY=pose.ty();pendingEvidenceCameraZ=pose.tz();
            pendingEvidenceLocalCameraX=cameraInOrigin.tx();pendingEvidenceLocalCameraY=cameraInOrigin.ty();pendingEvidenceLocalCameraZ=cameraInOrigin.tz();
        }
        if(accepted>0)cloud.addBatch(localX,localY,localZ,localC,accepted,frameId,
                cameraInOrigin.tx(),cameraInOrigin.ty(),cameraInOrigin.tz(),raw);
        if(distanceCount>0){
            currentMeanDistanceM=distanceSum/distanceCount;
            currentNearPercent=Math.round(nearCount*100f/distanceCount);
            currentOptimalPercent=Math.round(optimalCount*100f/distanceCount);
            currentFarPercent=Math.round(farCount*100f/distanceCount);
        }
        return accepted;
    }

    private static int sampleStepFor(int w,int h,int target,int minStep){
        if(w<=0||h<=0)return minStep;
        double area=(double)w*(double)h;
        int step=(int)Math.ceil(Math.sqrt(area/Math.max(1,target)));
        return Math.max(minStep,step);
    }

    private void updateDirectionCoverage(Pose pose) {
        pose.rotateVector(forwardAxis,0,forwardScratch,0);
        double yaw=Math.atan2(forwardScratch[0],-forwardScratch[2]);
        int bin=(int)Math.floor((yaw+Math.PI)/(2*Math.PI)*24.0);
        bin=Math.max(0,Math.min(23,bin));
        directionBins.set(bin);
    }

    private void updatePath(Pose pose,long ts){
        float x=pose.tx(),z=pose.tz();
        if(cameraPath.isEmpty()){
            cameraPath.add(new ScanPathPoint(x,z,ts));return;
        }
        ScanPathPoint last=cameraPath.get(cameraPath.size()-1);
        double d=Math.hypot(x-last.x,z-last.z);
        // Ignore impossible relocalization jumps in the visible scan path.
        if(d>2.5)return;
        if(d>=0.10||ts-last.timestampNs>=800_000_000L){
            if(cameraPath.size()<8000)cameraPath.add(new ScanPathPoint(x,z,ts));
        }
    }

    public List<com.elektoryk.scan.model.CloudPoint> snapshotPoints(){return cloud.snapshot();}

    /** Smaller recovery snapshot so background checkpointing cannot block the GL thread for long. */
    public List<com.elektoryk.scan.model.CloudPoint> snapshotPointsForCheckpoint(int maxPoints){
        return cloud.snapshotLimited(Math.max(1000,maxPoints));
    }

    public synchronized List<ScanPathPoint> snapshotCameraPath(){
        List<ScanPathPoint> out=new ArrayList<>();
        for(ScanPathPoint p:cameraPath)out.add(new ScanPathPoint(p.x,p.z,p.timestampNs));
        return out;
    }

    public synchronized Stats stats(){
        long now=System.nanoTime();
        if(cachedStats!=null && (!statsDirty || now-cachedStatsAtNs<STATS_CACHE_NS))return cachedStats;

        Stats s=new Stats();
        s.depthFrames=depthFrames;s.rawDepthFrames=rawDepthFrames;s.fallbackDepthFrames=fallbackDepthFrames;
        s.rawSamples=cloud.getRawSamples();s.acceptedHighConfidenceSamples=acceptedHighConfidenceSamples;
        s.voxelPoints=cloud.size();s.confirmedVoxelPoints=cloud.confirmedSize();
        s.directionCoveragePercent=(int)Math.round(directionBins.cardinality()/24.0*100.0);
        s.pathSamples=cameraPath.size();
        s.meanRawConfidence=confidenceCount==0?0:(int)Math.round(confidenceSum/(double)confidenceCount);
        s.lastDepthTimestampNs=lastDepthNs;
        s.currentMeanDistanceM=currentMeanDistanceM;
        s.currentNearPercent=currentNearPercent;
        s.currentOptimalPercent=currentOptimalPercent;
        s.currentFarPercent=currentFarPercent;
        s.coverageSnapshot=liveCoverage.snapshot();
        s.spatialCoveragePercent=s.coverageSnapshot.coveragePercent;
        s.verticalCoveragePercent=s.coverageSnapshot.verticalCoveragePercent;
        s.lowBandPercent=s.coverageSnapshot.lowBandPercent;
        s.midBandPercent=s.coverageSnapshot.midBandPercent;
        s.highBandPercent=s.coverageSnapshot.highBandPercent;
        s.currentSpeedMps=s.coverageSnapshot.speedMps;
        s.multiViewPercent=s.coverageSnapshot.multiViewPercent;
        s.staleRawFrames=staleRawFrames;s.staleSmoothFrames=staleSmoothFrames;
        s.rawTimestampSkewFrames=rawTimestampSkewFrames;s.confidenceTimestampSkewFrames=confidenceTimestampSkewFrames;s.lastRawFrameSkewMs=lastRawFrameSkewMs;
        s.rejectedDepthOutliers=rejectedDepthOutliers;s.poseJumpRejects=poseJumpRejects;
        s.planeSupportedSamples=planeSupportedSamples;s.planeRescuedSamples=planeRescuedSamples;s.rawPrecisionMode=!smoothedFallbackEnabled;
        PlaneEvidenceTracker.Snapshot ps=planeTracker.snapshot();
        s.planeCount=ps.trackedPlanes;s.verticalPlaneCount=ps.verticalPlanes;
        s.floorPlaneLocked=ps.floorLocked;s.ceilingPlaneLocked=ps.ceilingLocked;
        s.floorDistanceM=ps.floorDistanceM;s.nearestWallDistanceM=ps.nearestWallDistanceM;s.ceilingDistanceM=ps.ceilingDistanceM;
        s.floorPlaneY=ps.floorY;s.ceilingPlaneY=ps.ceilingY;
        s.wallYawHintRad=ps.wallYawHintRad;s.wallYawConfidence=ps.wallYawConfidence;

        int confidenceScore=s.meanRawConfidence<=0?45:Math.max(0,Math.min(100,(s.meanRawConfidence-90)*100/130));
        int motionScore=s.currentSpeedMps<=0.65f?100:(s.currentSpeedMps<=1.0f?65:25);
        int distanceScore=Math.max(10,Math.min(100,s.currentOptimalPercent+(100-s.currentNearPercent-s.currentFarPercent)/2));
        int rawReliability=s.depthFrames==0?0:Math.round(s.rawDepthFrames*100f/s.depthFrames);
        int confirmedRatio=s.voxelPoints==0?0:Math.min(100,Math.round(s.confirmedVoxelPoints*100f/s.voxelPoints));
        int planeScore=Math.min(100,s.verticalPlaneCount*14+(s.floorPlaneLocked?28:0)+(s.ceilingPlaneLocked?16:0));
        // Accuracy score rewards independently confirmed geometry, not just amount of visible depth.
        s.scanQualityPercent=Math.max(0,Math.min(100,Math.round(
                s.spatialCoveragePercent*0.40f+s.multiViewPercent*0.14f+
                s.verticalCoveragePercent*0.10f+s.directionCoveragePercent*0.08f+
                distanceScore*0.07f+confidenceScore*0.05f+motionScore*0.03f+
                rawReliability*0.05f+confirmedRatio*0.04f+planeScore*0.04f)));
        // Precision-ready is intentionally stricter than merely "usable". The UI still allows
        // a forced finish, but the green recommendation only appears after balanced 3D coverage
        // and genuine multi-view confirmation.
        s.finishRecommended=s.depthFrames>=38 && s.confirmedVoxelPoints>=1600 && s.spatialCoveragePercent>=65 &&
                s.midBandPercent>=58 && s.lowBandPercent>=40 && s.highBandPercent>=28 &&
                s.directionCoveragePercent>=50 && s.multiViewPercent>=45 && s.scanQualityPercent>=65 &&
                (s.meanRawConfidence==0 || s.meanRawConfidence>=145);

        if(s.currentSpeedMps>1.0f){
            s.liveHint="Занадто швидко — сповільни рух телефона, інакше кути та краї будуть рваними.";
        }else if(s.depthFrames>8 && s.coverageSnapshot!=null && s.coverageSnapshot.cameraTravelM<0.50f){
            s.liveHint="Не крути телефон на одному місці. Зроби 1–2 кроки боком уздовж стіни — Depth потребує паралаксу.";
        }else if(s.depthFrames>18 && s.rawDepthFrames==0){
            s.liveHint=s.rawPrecisionMode
                    ?"RAW Depth ще не дав достатньо нових точок. Додай світла і зроби повільне бокове зміщення 20–50 см."
                    :"Raw Depth поки не дає валідної геометрії — використовую слабший smooth fallback. Рухай телефон плавно боком.";
        }else if(s.currentNearPercent>=48){
            s.liveHint="Занадто близько до поверхні — відійди приблизно на 0,4–0,8 м і покажи її ще раз.";
        }else if(s.currentFarPercent>=55){
            s.liveHint="Основна геометрія зараз далеко — підійди ближче приблизно на 1–2 м.";
        }else if(depthFrames>12 && s.meanRawConfidence>0 && s.meanRawConfidence<145){
            s.liveHint="Depth невпевнений: рухайся повільніше, додай інший кут і не знімай гладку стіну строго перпендикулярно.";
        }else if(s.depthFrames>12 && s.multiViewPercent<25){
            s.liveHint="Геометрія ще одностороння — змістися на 20–50 см убік і покажи ті самі стіни/кути повторно. Так точність росте значно сильніше, ніж від простого повороту телефона.";
        }else if(s.poseJumpRejects>=3){
            s.liveHint="Було кілька стрибків пози ARCore. Повернись на 2–3 секунди до вже добре баченого кута/дверей, віднови tracking і тільки тоді продовжуй.";
        }else if(s.planeCount<2 && s.depthFrames>18){
            s.liveHint="ARCore ще слабо підтвердив площини. Проведи камерою вздовж стику підлога–стіна та по вертикальних кутах кімнати.";
        }else if(s.lowBandPercent+18<s.midBandPercent){
            s.liveHint="Підлога відсканована слабше — опусти камеру, покажи край підлоги та нижні кути вздовж стін.";
        }else if(s.highBandPercent+18<s.midBandPercent){
            s.liveHint="Верх відсканований слабше — підніми камеру на верх стін, кути та стелю.";
        }else if(s.verticalCoveragePercent<32 && s.spatialCoveragePercent>15){
            s.liveHint="Покриття по висоті слабке — проведи камерою від підлоги через стіну до верхнього кута/стелі.";
        }else if(s.coverageSnapshot!=null && s.coverageSnapshot.targetHint!=null && !s.coverageSnapshot.targetHint.isEmpty()){
            s.liveHint=s.coverageSnapshot.targetHint;
        }else{
            s.liveHint="Рухайся плавно по периметру. 3D-прев’ю має показати підлогу, вертикальні стіни й верх окремо.";
        }
        cachedStats=s;cachedStatsAtNs=now;statsDirty=false;
        return s;
    }
}
