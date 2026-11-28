package com.elektoryk.scan.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.elektoryk.scan.model.AreaCheckResult;
import com.elektoryk.scan.model.CloudPoint;
import com.elektoryk.scan.model.ElectricalProject;
import com.elektoryk.scan.model.SceneSnapshot;
import com.elektoryk.scan.model.ScanPathPoint;
import com.elektoryk.scan.reconstruction.AreaReconciliationEngine;
import com.elektoryk.scan.reconstruction.ScanGapDetector;
import com.elektoryk.scan.reconstruction.SceneAnalysisPipeline;
import com.elektoryk.scan.reconstruction.RoomAxisEstimator;
import com.elektoryk.scan.reconstruction.MultiRoomReconstructionEngine;
import com.elektoryk.scan.reconstruction.MissingZoneAdvisor;
import com.elektoryk.scan.reconstruction.PrecisionCalibrationEngine;
import com.elektoryk.scan.reconstruction.ScanFusionEngine;
import com.elektoryk.scan.reconstruction.SemanticPlanFuser;
import com.elektoryk.scan.reconstruction.SemanticFurniturePromoter;
import com.elektoryk.scan.reconstruction.SemanticRoomClassifier;
import com.elektoryk.scan.semantic.IndoorSemanticSystem;
import com.elektoryk.scan.semantic.SemanticLabel;
import com.elektoryk.scan.storage.ObjectFlowRepository;
import com.elektoryk.scan.model.ObjectFlowState;
import com.elektoryk.scan.scanner.ar.ArCoreBackgroundRenderer;
import com.elektoryk.scan.scanner.ar.ArCoreDepthCollector;
import com.elektoryk.scan.scanner.ar.ArSurfaceGridRenderer;
import com.elektoryk.scan.scanner.ar.ScanVisualKeyframeRecorder;
import com.elektoryk.scan.storage.FileProjectRepository;
import com.elektoryk.scan.storage.SceneRepository;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.Pose;
import com.google.ar.core.Plane;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;

import java.util.List;
import java.util.Collection;
import java.util.EnumSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ArCoreScanActivity extends Activity implements GLSurfaceView.Renderer {
    private static final int REQ_CAMERA=2001;
    private static final int MIN_ANALYSIS_VOXELS=600;

    private String projectId;
    private GLSurfaceView surface;
    private Session session;
    private boolean installRequested;
    private volatile boolean capturing;
    private boolean depthSupported;
    private boolean rawPrecisionMode;
    private boolean automaticDepthMode;
    private Anchor originAnchor;
    // Logical scan origin survives temporary Anchor tracking loss. The Anchor is only a
    // correction source; it must never become a hard gate that stops Depth collection.
    private Pose scanOriginPose;
    private Pose lastLocalCameraPose;
    private volatile boolean originFallbackActive;
    private volatile int originRebaseCount;
    private long lastOriginRebaseNs;

    private final ArCoreBackgroundRenderer background=new ArCoreBackgroundRenderer();
    private final ArCoreDepthCollector collector=new ArCoreDepthCollector();
    private final ArSurfaceGridRenderer surfaceGrid=new ArSurfaceGridRenderer();
    private ScanVisualKeyframeRecorder visualKeyframes;
    private IndoorSemanticSystem semanticSystem;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    // Heavy stats/coverage snapshots must not run on the GL camera thread.
    private final ExecutorService telemetryWorker=Executors.newSingleThreadExecutor();
    private volatile boolean activityDestroyed;

    private SceneRepository sceneRepo;
    private TextView status;
    private TextView metrics;
    private TextView hint;
    private ProgressBar progress;
    private Button scanButton;
    private Button recoverButton;
    private Button freshScanButton;
    private Button previewButton;
    private Button reviewButton;
    private Button areaButton;
    private Button accuracyButton;
    private Button apartmentButton;
    private Button twinButton;
    private Button pauseButton;
    private Button fallbackButton;
    private LiveScanCoverageView liveCoverageView;
    private View bottomNavView;
    private LinearLayout topPanel;
    private LinearLayout bottomPanel;
    private View brandHeaderView;
    private volatile boolean capturePaused;
    private boolean forceFinishArmed;
    private boolean replacePreviousOnFinish;
    private volatile ArCoreDepthCollector.Stats latestStats;
    private int scanPassNumber=1;
    private volatile boolean checkpointScheduled;
    private volatile long lastCheckpointNs;
    private long lastGridDepthTimestampNs;
    private volatile ArSurfaceGridRenderer.Stats latestGridStats=new ArSurfaceGridRenderer.Stats();
    private volatile long readyCandidateSinceNs;
    private volatile int readyCandidateWalls=-1;
    private volatile long readyCandidateTopology;
    private volatile boolean stableReady;
    private volatile long trackingStableSinceNs;
    private volatile boolean trackingCaptureStable;
    private static final long READY_HOLD_NS=3_000_000_000L;
    private static final long TRACKING_RECOVERY_HOLD_NS=950_000_000L;

    private volatile int surfaceWidth;
    private volatile int surfaceHeight;
    private volatile long lastUiUpdateNs;
    private volatile long lastTelemetryScheduleNs;
    private volatile boolean telemetryScheduled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectId=getIntent().getStringExtra("projectId");
        sceneRepo=new SceneRepository(this);
        semanticSystem=new IndoorSemanticSystem(this);
        visualKeyframes=new ScanVisualKeyframeRecorder(this,projectId,semanticSystem);
        ObjectFlowState flow=new ObjectFlowRepository(this).load(projectId);
        scanPassNumber=Math.max(1,flow.completedScanPasses+1);
        buildUi();
    }

    private void buildUi() {
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        surface=new GLSurfaceView(this);
        surface.setEGLContextClientVersion(2);
        surface.setPreserveEGLContextOnPause(true);
        surface.setRenderer(this);
        surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(surface,new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout top=new LinearLayout(this);
        topPanel=top;
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(Ui.dp(this,14),Ui.dp(this,16),Ui.dp(this,14),Ui.dp(this,12));
        top.setBackgroundColor(Color.argb(165,0,0,0));

        brandHeaderView=Ui.brandHeader(this,"ELEKTORYK • Smart Scan","Прохід "+scanPassNumber+" • Depth + Indoor AI • семантичний скан","СКАН",true);
        top.addView(brandHeaderView);
        status=Ui.muted(this,"Підготовка ARCore…");
        metrics=Ui.muted(this,"Depth: —");
        top.addView(status);
        top.addView(metrics,Ui.lpMatchWrap(this,4));

        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        top.addView(progress,Ui.lpMatchWrap(this,7));

        root.addView(top,new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.TOP));

        // v0.17.5: no detached side point-cloud widget. Coverage is rendered directly
        // on the live camera as an AR grid glued to the detected surfaces.
        liveCoverageView=null;

        LinearLayout bottom=new LinearLayout(this);
        bottomPanel=bottom;
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,22));
        bottom.setBackgroundColor(Color.argb(185,0,0,0));

        hint=Ui.muted(this,
                "Обійди ВСЮ квартиру одним сканом. Заходь у кожну кімнату через двері, покажи обидві сторони прорізів, підлогу, стіни, вікна, ніші й колони.");
        bottom.addView(hint);

        boolean hasExistingScene=sceneRepo.hasScene(projectId);
        scanButton=Ui.button(this,hasExistingScene?"Досканувати • додати до моделі":"Почати Smart Scan");
        scanButton.setEnabled(false);
        scanButton.setOnClickListener(v -> {
            if(!capturing) startCapture(!sceneRepo.hasScene(projectId)); else requestFinishCapture();
        });
        bottom.addView(scanButton,Ui.lpMatchWrap(this,10));

        freshScanButton=Ui.secondaryButton(this,"Новий Smart Scan з нуля");
        freshScanButton.setVisibility(hasExistingScene?View.VISIBLE:View.GONE);
        freshScanButton.setOnClickListener(v -> { if(!capturing) startCapture(true); });
        bottom.addView(freshScanButton,Ui.lpMatchWrap(this,8));

        fallbackButton=Ui.secondaryButton(this,"Camera2 / IMU • діагностичний fallback");
        fallbackButton.setVisibility(View.GONE);
        fallbackButton.setOnClickListener(v -> open(ScanActivity.class));
        bottom.addView(fallbackButton,Ui.lpMatchWrap(this,8));

        pauseButton=Ui.secondaryButton(this,"Пауза сканування");
        pauseButton.setVisibility(View.GONE);
        pauseButton.setOnClickListener(v -> toggleCapturePause());
        bottom.addView(pauseButton,Ui.lpMatchWrap(this,8));

        recoverButton=Ui.secondaryButton(this,"Відновити незавершений скан");
        recoverButton.setEnabled(sceneRepo.hasScanCheckpoint(projectId));
        recoverButton.setVisibility(sceneRepo.hasScanCheckpoint(projectId)?View.VISIBLE:View.GONE);
        recoverButton.setOnClickListener(v -> recoverCheckpoint());
        bottom.addView(recoverButton,Ui.lpMatchWrap(this,8));

        previewButton=Ui.secondaryButton(this,"3D RAW / CLEAN перегляд");
        previewButton.setEnabled(sceneRepo.hasScene(projectId));
        previewButton.setOnClickListener(v -> open(ScenePreviewActivity.class));
        bottom.addView(previewButton,Ui.lpMatchWrap(this,8));

        reviewButton=Ui.secondaryButton(this,"Перевірити сумнівні об'єкти");
        reviewButton.setEnabled(sceneRepo.hasScene(projectId));
        reviewButton.setOnClickListener(v -> open(ReviewActivity.class));
        bottom.addView(reviewButton,Ui.lpMatchWrap(this,8));

        areaButton=Ui.secondaryButton(this,"Контроль площі / де втрачено");
        areaButton.setEnabled(sceneRepo.hasScene(projectId));
        areaButton.setOnClickListener(v -> open(AreaCheckActivity.class));
        bottom.addView(areaButton,Ui.lpMatchWrap(this,8));

        accuracyButton=Ui.secondaryButton(this,"Макс. точність • 2–4 контрольні заміри");
        accuracyButton.setEnabled(sceneRepo.hasScene(projectId));
        accuracyButton.setOnClickListener(v -> open(AccuracyActivity.class));
        bottom.addView(accuracyButton,Ui.lpMatchWrap(this,8));

        apartmentButton=Ui.secondaryButton(this,"Єдиний план квартири • Multi-room");
        apartmentButton.setEnabled(sceneRepo.hasScene(projectId));
        apartmentButton.setOnClickListener(v -> open(ApartmentPlanActivity.class));
        bottom.addView(apartmentButton,Ui.lpMatchWrap(this,8));

        twinButton=Ui.button(this,"▣ Digital Twin • пройтись і поставити електрику");
        twinButton.setEnabled(sceneRepo.hasScene(projectId));
        twinButton.setOnClickListener(v -> open(DigitalTwinActivity.class));
        bottom.addView(twinButton,Ui.lpMatchWrap(this,8));
        bottomNavView=Ui.bottomNav(this,projectId,"SCAN");
        bottom.addView(bottomNavView,Ui.lpMatchWrap(this,10));

        root.addView(bottom,new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.BOTTOM));
        setContentView(root);
    }

    private void setScanningUi(boolean scanning){
        // Keep the live camera dominant while scanning: the large branded card and debug wall
        // used to cover almost half of the useful preview.
        if(brandHeaderView!=null)brandHeaderView.setVisibility(scanning?View.GONE:View.VISIBLE);
        if(topPanel!=null){
            topPanel.setPadding(Ui.dp(this,10),Ui.dp(this,scanning?8:16),Ui.dp(this,10),Ui.dp(this,scanning?7:12));
            topPanel.setBackgroundColor(Color.argb(scanning?108:165,0,0,0));
        }
        if(bottomPanel!=null){
            bottomPanel.setPadding(Ui.dp(this,14),Ui.dp(this,scanning?8:12),Ui.dp(this,14),Ui.dp(this,scanning?12:22));
            bottomPanel.setBackgroundColor(Color.argb(scanning?155:185,0,0,0));
        }
        if(status!=null)status.setTextSize(scanning?12f:14f);
        if(metrics!=null)metrics.setTextSize(scanning?13f:14f);
        if(hint!=null)hint.setTextSize(scanning?13f:14f);
        // The old side 3D point preview is intentionally disabled.
        if(liveCoverageView!=null)liveCoverageView.setVisibility(View.GONE);
        if(pauseButton!=null)pauseButton.setVisibility(scanning?View.VISIBLE:View.GONE);
        if(recoverButton!=null)recoverButton.setVisibility(scanning?View.GONE:(sceneRepo.hasScanCheckpoint(projectId)?View.VISIBLE:View.GONE));
        if(freshScanButton!=null)freshScanButton.setVisibility(scanning?View.GONE:(sceneRepo.hasScene(projectId)?View.VISIBLE:View.GONE));
        if(previewButton!=null)previewButton.setVisibility(scanning?View.GONE:View.VISIBLE);
        if(reviewButton!=null)reviewButton.setVisibility(scanning?View.GONE:View.VISIBLE);
        if(areaButton!=null)areaButton.setVisibility(scanning?View.GONE:View.VISIBLE);
        if(accuracyButton!=null)accuracyButton.setVisibility(scanning?View.GONE:View.VISIBLE);
        if(apartmentButton!=null)apartmentButton.setVisibility(scanning?View.GONE:View.VISIBLE);
        if(twinButton!=null)twinButton.setVisibility(scanning?View.GONE:View.VISIBLE);
        if(fallbackButton!=null)fallbackButton.setVisibility(scanning?View.GONE:(!depthSupported?View.VISIBLE:View.GONE));
        if(bottomNavView!=null)bottomNavView.setVisibility(scanning?View.GONE:View.VISIBLE);
    }

    private void startCapture(boolean replacePrevious) {
        if(session==null || !depthSupported) return;
        replacePreviousOnFinish=replacePrevious;
        if(replacePreviousOnFinish)sceneRepo.clearScanCheckpoint(projectId);
        collector.reset();
        surfaceGrid.requestReset();
        if(visualKeyframes!=null)visualKeyframes.resetPass();
        if(originAnchor!=null){ try{originAnchor.detach();}catch(Exception ignored){} originAnchor=null; }
        scanOriginPose=null;
        lastLocalCameraPose=null;
        originFallbackActive=false;
        originRebaseCount=0;
        lastOriginRebaseNs=0;
        telemetryScheduled=false;
        lastTelemetryScheduleNs=0;
        capturing=true;
        capturePaused=false;
        forceFinishArmed=false;
        latestStats=null;
        lastGridDepthTimestampNs=0;
        latestGridStats=new ArSurfaceGridRenderer.Stats();
        readyCandidateSinceNs=0;readyCandidateWalls=-1;readyCandidateTopology=0;stableReady=false;
        trackingStableSinceNs=0;trackingCaptureStable=false;
        setScanningUi(true);
        pauseButton.setText("Пауза сканування");
        lastCheckpointNs=System.nanoTime();
        checkpointScheduled=false;
        scanButton.setText("Завершити скан і проаналізувати");
        previewButton.setEnabled(false);
        reviewButton.setEnabled(false);
        areaButton.setEnabled(false);
        accuracyButton.setEnabled(false);
        apartmentButton.setEnabled(false);
        twinButton.setEnabled(false);
        hint.setText(replacePreviousOnFinish
                ?"ЧИСТИЙ ПРОХІД: попередня point cloud не буде підмішана. Обійди кімнату по периметру, покажи кути, підлогу, стелю та прорізи."
                :"ДОСКАНУВАННЯ: цей прохід буде суміщено з уже збереженою моделлю. Почни з добре відомої стіни, а потім іди в слабкі зони.");
    }

    private void toggleCapturePause(){
        if(!capturing)return;
        capturePaused=!capturePaused;
        pauseButton.setText(capturePaused?"Продовжити сканування":"Пауза сканування");
        if(capturePaused){
            hint.setText("Сканування на паузі. Можеш поговорити з клієнтом або перейти в іншу точку без додавання випадкової геометрії.");
        }else{
            forceFinishArmed=false;
            hint.setText(latestStats!=null?latestStats.liveHint:"Сканування продовжено. Рухайся плавно.");
        }
    }

    private void requestFinishCapture(){
        ArCoreDepthCollector.Stats st=latestStats!=null?latestStats:collector.stats();
        ArSurfaceGridRenderer.Stats gs=latestGridStats!=null?latestGridStats:surfaceGrid.stats();
        boolean gridReady=isStableReady(st,gs,System.nanoTime());
        if(!forceFinishArmed && st.depthFrames>=10 && !gridReady){
            forceFinishArmed=true;
            scanButton.setText("Завершити попри прогалини");
            String target=(st.coverageSnapshot!=null&&st.coverageSnapshot.hasTarget)
                    ? String.format(java.util.Locale.US," Найближча слабка зона: %s ~%.1f м.",st.coverageSnapshot.targetDirection,st.coverageSnapshot.targetDistanceM)
                    : "";
            String grid=" Surface: структурних стін "+gs.stableWalls+", підтверджено "+gs.coveredPercent()+"%, зелених клітинок "+gs.greenTiles+".";
            hint.setText("Скан ще слабкий: 3D "+st.spatialCoveragePercent+"%, низ/підлога "+st.lowBandPercent+"%, стіни "+st.midBandPercent+"%, верх "+st.highBandPercent+"%, ракурси "+st.directionCoveragePercent+"%."+grid+target+" Натисни ще раз, якщо все одно хочеш завершити.");
            return;
        }
        finishCapture();
    }


    private synchronized boolean isStableReady(ArCoreDepthCollector.Stats st,ArSurfaceGridRenderer.Stats gs,long nowNs){
        boolean base=st!=null&&gs!=null&&trackingCaptureStable&&!originFallbackActive&&st.finishRecommended&&gs.visuallyReady();
        if(!base){readyCandidateSinceNs=0;readyCandidateWalls=-1;readyCandidateTopology=0;stableReady=false;return false;}
        // Same wall count is not enough: the actual coarse wall geometry must also stop moving.
        if(readyCandidateWalls!=gs.stableWalls||readyCandidateTopology!=gs.topologySignature){
            readyCandidateWalls=gs.stableWalls;readyCandidateTopology=gs.topologySignature;readyCandidateSinceNs=nowNs;stableReady=false;return false;
        }
        if(readyCandidateSinceNs==0)readyCandidateSinceNs=nowNs;
        stableReady=nowNs-readyCandidateSinceNs>=READY_HOLD_NS;
        return stableReady;
    }

    private void finishCapture() {
        capturing=false;
        capturePaused=false;
        setScanningUi(false);
        scanButton.setEnabled(false);
        scanButton.setText("Обробка…");
        hint.setText("Зберігаю recovery-checkpoint і будую Multi-room модель…");

        if(originAnchor!=null){ try{originAnchor.detach();}catch(Exception ignored){} originAnchor=null; }
        scanOriginPose=null;
        lastLocalCameraPose=null;
        originFallbackActive=false;
        final ArCoreDepthCollector.Stats st=latestStats!=null?latestStats:collector.stats();

        // A full voxel snapshot can be large. Never copy/save it on the UI/GL thread.
        submitWorker(() -> {
            try{
                final List<CloudPoint> points=collector.snapshotPoints();
                final List<ScanPathPoint> rawPath=collector.snapshotCameraPath();
                if(points==null || points.size()<MIN_ANALYSIS_VOXELS){
                    runOnUiThread(() -> {
                        if(activityDestroyed||isFinishing()||isDestroyed())return;
                        hint.setText("Замало depth-точок для надійного аналізу. Проскануй приміщення довше і повільніше.");
                        scanButton.setText("Новий Smart Scan");
                        scanButton.setEnabled(depthSupported);
                    });
                    return;
                }
                sceneRepo.saveScanCheckpoint(projectId,points,rawPath,replacePreviousOnFinish);
                runOnUiThread(() -> {
                    if(activityDestroyed||isFinishing()||isDestroyed())return;
                    recoverButton.setVisibility(View.VISIBLE);
                    recoverButton.setEnabled(false);
                });
                processCaptured(points,rawPath,st,true,replacePreviousOnFinish);
            }catch(Throwable e){
                new com.elektoryk.scan.storage.StabilityReporter(this).error("SCAN_SNAPSHOT","Final scan snapshot failed",e);
                runOnUiThread(() -> {
                    if(activityDestroyed||isFinishing()||isDestroyed())return;
                    hint.setText("Не вдалося безпечно зберегти 3D-скан. Recovery не втрачено — спробуй завершити ще раз.");
                    scanButton.setText("Завершити скан");
                    scanButton.setEnabled(true);
                });
            }
        });
    }

    private void recoverCheckpoint(){
        if(!sceneRepo.hasScanCheckpoint(projectId))return;
        scanButton.setEnabled(false);
        recoverButton.setEnabled(false);
        hint.setText("Відновлюю незавершений скан і повторно запускаю аналіз…");
        // points.bin can contain hundreds of thousands of samples. Reading + decoding it on the
        // main thread caused multi-second ANRs on slower phones exactly when Recovery was needed.
        submitWorker(() -> {
            final List<CloudPoint> points=sceneRepo.loadCheckpointPoints(projectId);
            final List<ScanPathPoint> path=sceneRepo.loadCheckpointPath(projectId);
            final boolean checkpointReplacePrevious=sceneRepo.loadCheckpointReplacePrevious(projectId);
            final ArCoreDepthCollector.Stats st=new ArCoreDepthCollector.Stats();
            st.depthFrames=Math.max(1,points.size()/700);
            st.rawSamples=points.size();
            st.voxelPoints=points.size();
            st.pathSamples=path.size();
            st.directionCoveragePercent=45;
            if(points.size()<MIN_ANALYSIS_VOXELS){
                sceneRepo.clearScanCheckpoint(projectId);
                runOnUiThread(() -> {
                    if(activityDestroyed||isFinishing()||isDestroyed())return;
                    hint.setText("Recovery-checkpoint пошкоджений або надто малий. Почни новий Smart Scan.");
                    recoverButton.setVisibility(View.GONE);
                    scanButton.setEnabled(depthSupported);
                });
                return;
            }
            runOnUiThread(() -> {
                if(activityDestroyed||isFinishing()||isDestroyed())return;
                hint.setText("Checkpoint відновлено. Аналізую геометрію…");
            });
            // We are already on the single reconstruction worker. Run the analysis inline so an
            // Activity destroy/shutdown cannot reject a nested worker.execute() between recovery
            // I/O and the actual reconstruction.
            processCaptured(points,path,st,true,checkpointReplacePrevious);
        });
    }

    // Caller must already be on worker. Keeping snapshot/checkpoint/reconstruction in one queue
    // prevents a shutdown race that used to strand a valid checkpoint without running analysis.
    private void processCaptured(final List<CloudPoint> points,
                                 final List<ScanPathPoint> rawPath,
                                 final ArCoreDepthCollector.Stats st,
                                 final boolean freshCapture,
                                 final boolean replacePrevious){
            try{
                com.elektoryk.scan.device.RuntimeProfile runtimeProfile=com.elektoryk.scan.device.RuntimeProfile.load(this);
                List<CloudPoint> analysisPoints=com.elektoryk.scan.reconstruction.PointCloudGuard.reduce(points,runtimeProfile.maxAnalysisPoints);
                new com.elektoryk.scan.storage.StabilityReporter(this).breadcrumb("SCAN_ANALYSIS","Profile="+runtimeProfile.mode+" input="+points.size()+" analysis="+analysisPoints.size());
                RoomAxisEstimator axisEstimator=new RoomAxisEstimator();
                RoomAxisEstimator.Result normalized=axisEstimator.normalize(analysisPoints,
                        st.wallYawHintRad,st.wallYawConfidence);
                List<ScanPathPoint> normalizedPath=axisEstimator.normalizePath(rawPath,normalized.yawRad);

                // "Новий Smart Scan з нуля" must truly be clean. Older builds reset only the
                // live collector and then silently fused the old stored point cloud back in.
                SceneSnapshot previous=replacePrevious?null:sceneRepo.loadScene(projectId);
                List<CloudPoint> previousPoints=replacePrevious
                        ?new java.util.ArrayList<CloudPoint>()
                        :sceneRepo.loadPoints(projectId,runtimeProfile.maxAnalysisPoints);
                ScanFusionEngine.Result fusion=new ScanFusionEngine().fuse(previousPoints,previous==null?null:previous.scanPath,normalized.points,normalizedPath);
                if(fusion.rejected){sceneRepo.clearScanCheckpoint(projectId);throw new IllegalStateException("SCAN_FUSION_REJECTED: повторний прохід не вдалося надійно прив’язати до попереднього");}
                List<CloudPoint> fusedPoints=com.elektoryk.scan.reconstruction.PointCloudGuard.reduce(fusion.points,runtimeProfile.maxAnalysisPoints);
                List<ScanPathPoint> fusedPath=fusion.path;

                SceneSnapshot scene=new SceneAnalysisPipeline().analyze(
                        projectId, fusedPoints, st.depthFrames, st.rawSamples, st.directionCoveragePercent,
                        st.floorPlaneLocked,st.floorPlaneY,st.ceilingPlaneLocked,st.ceilingPlaneY);
                scene.roomYawRad=normalized.yawRad;
                scene.scanPath.addAll(fusedPath);

                if(scene.roomBounds!=null && scene.roomBounds.isUsable()){
                    com.elektoryk.scan.model.ApartmentPlan autoPlan=new MultiRoomReconstructionEngine()
                            .analyze(fusedPoints,scene.roomBounds,fusedPath);
                    // Gap detection must use the actual reconstructed footprint, not the rectangular
                    // RoomBounds envelope; otherwise L/U-shaped apartments produce giant false gaps.
                    scene.scanGaps.addAll(new ScanGapDetector().detect(fusedPoints,scene.roomBounds,fusedPath,autoPlan));
                    // RGB semantics name surfaces/objects; Depth remains the metric source.
                    java.util.List<com.elektoryk.scan.model.SemanticEntity> semanticEntities=semanticSystem==null?new java.util.ArrayList<com.elektoryk.scan.model.SemanticEntity>():semanticSystem.entities();
                    java.util.List<com.elektoryk.scan.semantic.SemanticSceneTracker.Record> semanticRecords=semanticSystem==null?new java.util.ArrayList<com.elektoryk.scan.semantic.SemanticSceneTracker.Record>():semanticSystem.records();
                    new SemanticPlanFuser().apply(autoPlan,semanticEntities,semanticRecords,normalized.yawRad);
                    scene.semanticEntities.addAll(semanticEntities);
                    new SemanticFurniturePromoter().apply(autoPlan,semanticEntities);
                    new SemanticRoomClassifier().apply(autoPlan,semanticEntities);
                    // Preserve interior geometry as objects instead of allowing it to pollute the wall model.
                    new com.elektoryk.scan.reconstruction.ObjectGeometryPromoter().apply(autoPlan,scene.reviewIssues);
                    // RAW Digital Twin walls use real RGB material patches from this scan pass.
                    if(visualKeyframes!=null){
                        visualKeyframes.flush();
                        new com.elektoryk.scan.reconstruction.WallTextureBaker().bake(this,projectId,autoPlan,visualKeyframes.snapshot(),normalized.yawRad,fusion);
                    }

                    if(previous!=null && previous.apartmentPlan!=null && previous.apartmentPlan.manualEdited){
                        scene.apartmentPlan=previous.apartmentPlan;
                        scene.latestAutoApartmentPlan=autoPlan;
                    }else{
                        scene.apartmentPlan=autoPlan;
                        scene.latestAutoApartmentPlan=null;
                    }
                    if(scene.apartmentPlan!=null && scene.apartmentPlan.totalObservedAreaM2>0.5){
                        scene.observedFloorAreaM2=scene.apartmentPlan.totalObservedAreaM2;
                        // Bounding-box occupancy is misleading for irregular plans. Prefer coverage
                        // against the reconstructed room footprint once topology is available.
                        if(scene.apartmentPlan.totalRoomAreaM2>0.5)
                            scene.floorObservationRatio=Math.max(0.0,Math.min(1.0,
                                    scene.apartmentPlan.directObservedAreaM2/scene.apartmentPlan.totalRoomAreaM2));
                    }
                }

                ElectricalProject project=new FileProjectRepository(this).get(projectId);
                scene.areaCheck=new AreaReconciliationEngine().check(project,scene,null);
                new MissingZoneAdvisor().apply(scene);
                scene.precisionReport=new PrecisionCalibrationEngine().analyze(scene);
                sceneRepo.save(projectId,scene,fusedPoints);
                sceneRepo.clearScanCheckpoint(projectId);
                if(freshCapture){ObjectFlowRepository fr=new ObjectFlowRepository(this);ObjectFlowState fs=fr.load(projectId);fs.completedScanPasses=Math.max(0,fs.completedScanPasses)+1;fs.analysisConfirmed=false;fr.save(fs);}
                new com.elektoryk.scan.storage.StabilityReporter(this).breadcrumb("SCAN_FUSION","fused="+fusion.fused+" q="+fusion.rotationQuarterTurns+" overlap="+String.format(java.util.Locale.US,"%.3f",fusion.overlapScore)+" old="+previousPoints.size()+" new="+normalized.points.size()+" out="+fusedPoints.size());

                runOnUiThread(() -> {
                    if(activityDestroyed||isFinishing()||isDestroyed())return;
                    scanButton.setText("Новий Smart Scan");
                    scanButton.setEnabled(depthSupported);
                    recoverButton.setVisibility(View.GONE);
                    previewButton.setEnabled(true);
                    reviewButton.setEnabled(true);
                    areaButton.setEnabled(true);
                    accuracyButton.setEnabled(scene.apartmentPlan!=null);
                    apartmentButton.setEnabled(scene.apartmentPlan!=null);
                    twinButton.setEnabled(scene.apartmentPlan!=null && !scene.apartmentPlan.walls.isEmpty());
                    showAnalysisSummary(scene);
                });
            }catch(Throwable e){
                new com.elektoryk.scan.storage.StabilityReporter(this).error("SCAN_ANALYSIS","Analysis failed; checkpoint retained",e);
                runOnUiThread(() -> {
                    if(activityDestroyed||isFinishing()||isDestroyed())return;
                    scanButton.setText("Новий Smart Scan");
                    scanButton.setEnabled(depthSupported);
                    boolean checkpoint=sceneRepo.hasScanCheckpoint(projectId);
                    recoverButton.setVisibility(checkpoint?View.VISIBLE:View.GONE);
                    recoverButton.setEnabled(checkpoint);
                    boolean fusionRejected=e.getMessage()!=null&&e.getMessage().startsWith("SCAN_FUSION_REJECTED");
                    hint.setText(fusionRejected?"Цей повторний прохід не вдалося безпечно сумістити з попереднім. Старий скан не пошкоджено. Зроби новий прохід, починаючи з уже добре відсканованої кімнати, а потім іди в сліпі зони.":"Аналіз не завершився, але сирий скан збережений. Натисни «Відновити незавершений скан» замість повторного сканування.");
                    status.setText(fusionRejected?"Multi-pass: прохід відхилено для захисту геометрії":"Помилка аналізу: "+e.getClass().getSimpleName());
                });
            }
    }

    private void showAnalysisSummary(SceneSnapshot scene){
        if(scene.roomBounds!=null && scene.roomBounds.isUsable()){
            boolean wallArtifacts=hasParallelWallArtifacts(scene.apartmentPlan);
            String areaText="";
            if(scene.areaCheck!=null && scene.areaCheck.referenceAreaM2>0){
                areaText=String.format(java.util.Locale.US," • площа %.1f/%.1f м² • %s",
                        scene.areaCheck.scannedAreaM2,scene.areaCheck.referenceAreaM2,
                        scene.areaCheck.status==AreaCheckResult.Status.MATCH?"OK":"ПЕРЕВІРИТИ");
            }
            boolean autoCandidate=scene.latestAutoApartmentPlan!=null;
            int roomCount=scene.apartmentPlan==null?0:scene.apartmentPlan.rooms.size();
            int doorCount=0,windowCount=0;
            if(scene.apartmentPlan!=null)for(com.elektoryk.scan.model.ApartmentOpening o:scene.apartmentPlan.openings){
                if(o.type==com.elektoryk.scan.model.ApartmentOpening.Type.DOOR)doorCount++;
                if(o.type==com.elektoryk.scan.model.ApartmentOpening.Type.WINDOW)windowCount++;
            }
            boolean tooFewWalls=scene.apartmentPlan==null || scene.apartmentPlan.walls.size()<3;
            int weakWalls=0;if(scene.apartmentPlan!=null)for(com.elektoryk.scan.model.PlanWallSegment w:scene.apartmentPlan.walls)if(w.confidence<0.50)weakWalls++;
            boolean weakPerimeter=scene.apartmentPlan!=null && weakWalls>=2;
            if(tooFewWalls){
                hint.setText(String.format(java.util.Locale.US,
                        "СКАН НЕ ЗАВЕРШЕНИЙ: надійно підтверджено лише %d структурних стін. Дані збережені, але я не домальовую відсутні стіни з меблів/шуму. Доскануй кімнату, особливо кути й протилежні стіни.",
                        scene.apartmentPlan==null?0:scene.apartmentPlan.walls.size()));
            }else if(weakPerimeter){
                hint.setText(String.format(java.util.Locale.US,
                        "СКАН НЕ ЗАВЕРШЕНИЙ: %d стіни периметра поки лише припущені з меж хмари, а не підтверджені достатньою кількістю Depth-даних. Доскануй кути/протилежні стіни; я не видаю прямокутний fallback за готову кімнату.",weakWalls));
            }else if(wallArtifacts){
                hint.setText(String.format(java.util.Locale.US,
                        "ПЕРЕВІРИТИ ГЕОМЕТРІЮ: знайдено накладені паралельні стіни. Не вважаю цей прохід готовим. Межі %.2f × %.2f × %.2f м • floor %.1f м² • gaps %d. Відкрий RAW/CLEAN або зроби чистий повторний прохід.",
                        scene.roomBounds.widthM(),scene.roomBounds.lengthM(),scene.roomBounds.heightM(),scene.observedFloorAreaM2,scene.scanGaps.size()));
            }else{
                hint.setText(String.format(java.util.Locale.US,
                        "Готово: envelope %.2f × %.2f × %.2f м • реальна підлога %.1f м² • зон %d • дверей %d • вікон %d • gaps %d%s",
                        scene.roomBounds.widthM(),scene.roomBounds.lengthM(),scene.roomBounds.heightM(),scene.observedFloorAreaM2,
                        roomCount,doorCount,windowCount,scene.scanGaps.size(),
                        areaText+(autoCandidate?" • ручний план збережено, новий автоплан чекає підтвердження":"")
                                +" • Для максимальної метричної точності: «Макс. точність» → 2–4 довгі заміри лазером/рулеткою."));
            }
        }else hint.setText("Point cloud збережено, але межі квартири недостатньо надійні. Проскануй слабкі зони ще раз.");
    }

    /**
     * Final-output sanity guard. A structural wall in the plan is represented by one centerline.
     * Two long, strongly-overlapping parallel centerlines only 8-32 cm apart are therefore an
     * artifact (typically duplicated depth/grid evidence), not a trustworthy finished model.
     */
    private boolean hasParallelWallArtifacts(com.elektoryk.scan.model.ApartmentPlan plan){
        if(plan==null||plan.walls==null||plan.walls.size()<2)return false;
        for(int i=0;i<plan.walls.size();i++){
            com.elektoryk.scan.model.PlanWallSegment a=plan.walls.get(i);
            if(a==null||a.lengthM()<0.45)continue;
            boolean ah=Math.abs(a.x2-a.x1)>=Math.abs(a.z2-a.z1);
            for(int j=i+1;j<plan.walls.size();j++){
                com.elektoryk.scan.model.PlanWallSegment b=plan.walls.get(j);
                if(b==null||b.lengthM()<0.45)continue;
                boolean bh=Math.abs(b.x2-b.x1)>=Math.abs(b.z2-b.z1);
                if(ah!=bh)continue;
                double sep=ah?Math.abs(a.centerZ()-b.centerZ()):Math.abs(a.centerX()-b.centerX());
                if(sep<0.08||sep>0.32)continue;
                double a0=ah?Math.min(a.x1,a.x2):Math.min(a.z1,a.z2);
                double a1=ah?Math.max(a.x1,a.x2):Math.max(a.z1,a.z2);
                double b0=ah?Math.min(b.x1,b.x2):Math.min(b.z1,b.z2);
                double b1=ah?Math.max(b.x1,b.x2):Math.max(b.z1,b.z2);
                double overlap=Math.min(a1,b1)-Math.max(a0,b0);
                double shorter=Math.min(a1-a0,b1-b0);
                if(shorter>0.35 && overlap>=shorter*0.60)return true;
            }
        }
        return false;
    }

    private void open(Class<?> c) {
        Intent i=new Intent(this,c);
        i.putExtra("projectId",projectId);
        startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(recoverButton!=null){
            boolean has=sceneRepo.hasScanCheckpoint(projectId);
            recoverButton.setVisibility(has?View.VISIBLE:View.GONE);recoverButton.setEnabled(has);
            if(has && !capturing && hint!=null)hint.setText("Є recovery незавершеного проходу. Можеш відновити й проаналізувати його або почати новий прохід.");
        }
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_CAMERA);
            return;
        }
        ensureSession();
        resumeArSession();
    }

    private void resumeArSession(){
        Session s=session;if(s==null)return;
        try{
            s.resume();
            surface.onResume();
            if(scanButton!=null)scanButton.setEnabled(depthSupported);
            if(fallbackButton!=null)fallbackButton.setVisibility(depthSupported?View.GONE:View.VISIBLE);
            if(status!=null)status.setText(depthSupported?(rawPrecisionMode?(automaticDepthMode?"ARCore готовий • RAW + DEPTH ✓":"ARCore готовий • RAW DEPTH PRECISION ✓"):"ARCore готовий • Depth API ✓"):"ARCore є, але Depth API недоступний. 3D Smart Scan тут недоступний.");
        }catch(Exception e){
            try{s.close();}catch(Exception ignored){}
            if(session==s)session=null;
            showError("ARCore resume: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));
        }
    }

    private void ensureSession() {
        if(session!=null) return;
        try{
            ArCoreApk.InstallStatus status=ArCoreApk.getInstance()
                    .requestInstall(this,!installRequested);
            if(status==ArCoreApk.InstallStatus.INSTALL_REQUESTED){
                installRequested=true;
                this.status.setText("Потрібна установка/оновлення Google Play Services for AR.");
                return;
            }

            session=new Session(this);

            // Precision scan prefers a stable 30 FPS tracking camera. ARCore can otherwise
            // prioritize a 60 FPS configuration on capable devices, which increases camera/GPU
            // load while our metric Raw Depth pipeline only needs a small subset of frames.
            // We intentionally do NOT disable hardware depth usage here.
            try {
                CameraConfigFilter cameraFilter=new CameraConfigFilter(session);
                cameraFilter.setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30));
                List<CameraConfig> configs=session.getSupportedCameraConfigs(cameraFilter);
                if(configs!=null&&!configs.isEmpty()) session.setCameraConfig(configs.get(0));
            } catch (Throwable ignored) {
                // Camera config filtering is an optimization. Keep the ARCore default if a
                // vendor build rejects it instead of making the scanner unavailable.
            }

            Config c=session.getConfig();
            boolean rawSupported=session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY);
            boolean automaticSupported=session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
            depthSupported=rawSupported||automaticSupported;
            rawPrecisionMode=rawSupported;
            automaticDepthMode=automaticSupported;
            // Prefer AUTOMATIC when available: ARCore still exposes Raw Depth + confidence,
            // while the full/smoothed depth image remains available as a temporary fallback.
            // RAW_DEPTH_ONLY made our fallback path impossible on devices where Raw Depth is
            // temporarily sparse or vendor timestamp metadata differs.
            if(automaticSupported) c.setDepthMode(Config.DepthMode.AUTOMATIC);
            else if(rawSupported) c.setDepthMode(Config.DepthMode.RAW_DEPTH_ONLY);
            collector.setSmoothedFallbackEnabled(automaticSupported);
            c.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
            // Fixed/default focus is more stable for AR tracking. Autofocus hunting can shift
            // calibration slightly between frames and is unnecessary for room-scale scanning.
            c.setFocusMode(Config.FocusMode.FIXED);
            c.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
            session.configure(c);

            this.status.setText(depthSupported
                    ? (rawPrecisionMode?(automaticDepthMode?"ARCore готовий • RAW + DEPTH ✓":"ARCore готовий • RAW DEPTH PRECISION ✓"):"ARCore готовий • Depth API ✓")
                    : "ARCore є, але Depth API недоступний. 3D Smart Scan тут недоступний.");
            if(!depthSupported)hint.setText("Depth API немає: справжню 3D-сцену цей пристрій зараз не побудує. Нижче є Camera2/IMU fallback лише для діагностики руху й камери — він НЕ створює 3D-план.");
            scanButton.setEnabled(depthSupported);
            if(fallbackButton!=null)fallbackButton.setVisibility(depthSupported?View.GONE:View.VISIBLE);
        }catch(Exception e){
            showError("Не вдалося запустити ARCore: "+e.getClass().getSimpleName()
                    +" • "+e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        boolean wasCapturing=capturing;
        capturing=false;
        capturePaused=false;
        if(wasCapturing)setScanningUi(false);
        else {
            if(pauseButton!=null)pauseButton.setVisibility(View.GONE);
            if(liveCoverageView!=null)liveCoverageView.setVisibility(View.GONE);
        }
        if(wasCapturing){
            submitWorker(() -> {
                try{
                    List<CloudPoint> pausePoints=collector.snapshotPointsForCheckpoint(50000);
                    List<ScanPathPoint> pausePath=collector.snapshotCameraPath();
                    if(pausePoints!=null&&pausePoints.size()>=120)sceneRepo.saveScanCheckpoint(projectId,pausePoints,pausePath,replacePreviousOnFinish);
                }catch(Throwable e){
                    new com.elektoryk.scan.storage.StabilityReporter(this).error("SCAN_PAUSE_CHECKPOINT","Pause checkpoint failed",e);
                }
            });
        }
        if(visualKeyframes!=null)visualKeyframes.flushAsync();
        surface.onPause();
        if(session!=null){
            try{ session.pause(); }catch(Exception ignored){}
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        activityDestroyed=true;
        // Let an already-running final snapshot/reconstruction finish and commit its checkpoint/scene.
        // Closing keyframe/semantic resources immediately races that worker and used to turn a clean
        // Activity teardown into a failed final scan. No new jobs are accepted after shutdown().
        worker.shutdown();
        telemetryWorker.shutdownNow();
        final ScanVisualKeyframeRecorder keyframesToClose=visualKeyframes;
        final IndoorSemanticSystem semanticsToClose=semanticSystem;
        Thread deferredCleanup=new Thread(() -> {
            try{
                // This daemon never blocks Activity teardown. More importantly, it also never closes
                // the semantic/keyframe resources underneath a slow but valid final reconstruction.
                while(!worker.awaitTermination(30,java.util.concurrent.TimeUnit.SECONDS)){}
            }catch(InterruptedException e){Thread.currentThread().interrupt();return;}
            if(keyframesToClose!=null){try{keyframesToClose.closeAsync();}catch(Exception ignored){}}
            if(semanticsToClose!=null){try{semanticsToClose.close();}catch(Exception ignored){}}
        },"elektoryk-scan-cleanup");
        deferredCleanup.setDaemon(true);deferredCleanup.start();
        if(originAnchor!=null){ try{originAnchor.detach();}catch(Exception ignored){} originAnchor=null; }
        scanOriginPose=null;
        lastLocalCameraPose=null;
        if(session!=null){
            try{ session.close(); }catch(Exception ignored){}
            session=null;
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==REQ_CAMERA && results.length>0
                && results[0]==PackageManager.PERMISSION_GRANTED){
            ensureSession();
            resumeArSession();
        }else if(requestCode==REQ_CAMERA){
            showError("Без CAMERA ARCore-сканування неможливе.");
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl,EGLConfig config){
        GLES20.glClearColor(0,0,0,1);
        background.createOnGlThread();
        surfaceGrid.createOnGlThread();
        if(session!=null) session.setCameraTextureName(background.getTextureId());
    }

    @Override
    public void onSurfaceChanged(GL10 gl,int width,int height){
        surfaceWidth=width; surfaceHeight=height;
        GLES20.glViewport(0,0,width,height);
        if(session!=null){
            session.setDisplayGeometry(displayRotation(),width,height);
            session.setCameraTextureName(background.getTextureId());
        }
    }

    @Override
    public void onDrawFrame(GL10 gl){
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
        Session s=session;
        if(s==null) return;
        try{
            s.setCameraTextureName(background.getTextureId());
            if(surfaceWidth>0 && surfaceHeight>0)
                s.setDisplayGeometry(displayRotation(),surfaceWidth,surfaceHeight);

            Frame frame=s.update();
            background.draw(frame);

            Camera camera=frame.getCamera();
            TrackingState tracking=camera.getTrackingState();
            long now=System.nanoTime();
            long frameTs=frame.getTimestamp();
            if(capturing){
                if(tracking==TrackingState.TRACKING){
                    if(trackingStableSinceNs==0)trackingStableSinceNs=now;
                    trackingCaptureStable=now-trackingStableSinceNs>=TRACKING_RECOVERY_HOLD_NS;
                }else{
                    trackingStableSinceNs=0;trackingCaptureStable=false;
                    if(semanticSystem!=null)semanticSystem.invalidateLiveFocus();
                    synchronized(this){readyCandidateSinceNs=0;readyCandidateWalls=-1;readyCandidateTopology=0;stableReady=false;}
                }
            }else{trackingStableSinceNs=0;trackingCaptureStable=false;}
            Collection<Plane> trackedPlanes=null;
            if(capturing && tracking==TrackingState.TRACKING){
                trackedPlanes=s.getAllTrackables(Plane.class);
            }
            if(capturing && !capturePaused && tracking==TrackingState.TRACKING && trackingCaptureStable){
                Pose effectiveOrigin=resolveScanOrigin(s,camera,frameTs);
                if(effectiveOrigin!=null){
                    if(collector.shouldCollectPlanes(frameTs)){
                        collector.collectPlanes(trackedPlanes,effectiveOrigin,camera.getPose(),frameTs);
                    }
                    collector.collect(frame,effectiveOrigin);
                    if(visualKeyframes!=null)visualKeyframes.maybeCapture(frame,effectiveOrigin,collector.latestDepthEvidence());
                    // Preserve the current local camera pose so an Anchor loss can be rebased
                    // without jumping or discarding the already scanned geometry.
                    lastLocalCameraPose=effectiveOrigin.inverse().compose(camera.getPose());
                }
            }

            // v0.17.5: render coverage where the user is actually looking — directly on top of
            // the live camera and attached to ARCore's world-space plane polygons. Pausing keeps
            // the colors visible but stops accumulating new coverage.
            if(capturing && tracking==TrackingState.TRACKING && trackedPlanes!=null){
                IndoorSemanticSystem.Snapshot semanticNow=semanticSystem==null?null:semanticSystem.snapshot();
                if(semanticNow!=null)surfaceGrid.setSemanticFocus(semanticNow.lookingAt,semanticNow.lookingAtShare,semanticNow.lastSemanticTimestampNs);
                ArCoreDepthCollector.DepthEvidenceFrame newest=collector.latestDepthEvidence();
                ArCoreDepthCollector.DepthEvidenceFrame gridEvidence=null;
                if(!capturePaused && trackingCaptureStable && newest!=null && newest.timestampNs>0 && newest.timestampNs!=lastGridDepthTimestampNs){
                    lastGridDepthTimestampNs=newest.timestampNs;
                    gridEvidence=newest;
                }
                surfaceGrid.draw(camera,trackedPlanes,frameTs,gridEvidence,scanOriginPose,collector.planeSnapshot());
            }

            // UI/coverage statistics are intentionally computed off the GL thread. In v0.17.2
            // confirmedSize()+3D snapshot could walk tens of thousands of voxels while camera
            // rendering was waiting, which looked like a frozen scanner.
            scheduleTelemetry(now);
            ArCoreDepthCollector.Stats st=latestStats;
            if(capturing && !capturePaused && st!=null && st.voxelPoints>=600 && !checkpointScheduled && now-lastCheckpointNs>60_000_000_000L){
                checkpointScheduled=true;
                lastCheckpointNs=now;
                submitWorker(() -> {
                    try{
                        List<CloudPoint> cp=collector.snapshotPointsForCheckpoint(50000);
                        List<ScanPathPoint> pp=collector.snapshotCameraPath();
                        if(cp.size()>=MIN_ANALYSIS_VOXELS)sceneRepo.saveScanCheckpoint(projectId,cp,pp,replacePreviousOnFinish);
                    }catch(Throwable e){
                        new com.elektoryk.scan.storage.StabilityReporter(this).error("SCAN_CHECKPOINT","Periodic scan checkpoint failed",e);
                    }finally{checkpointScheduled=false;}
                });
            }
            if(st==null || now-lastUiUpdateNs<250_000_000L)return;
            lastUiUpdateNs=now;

            final ArSurfaceGridRenderer.Stats gridStats=surfaceGrid.stats();
            latestGridStats=gridStats;
            runOnUiThread(() -> {
                if(activityDestroyed||isFinishing()||isDestroyed())return;
                if(tracking==TrackingState.TRACKING){
                    if(capturing && !trackingCaptureStable)
                        status.setText("TRACKING • відновлення позиції… • геометрію ще не записую");
                    else if(capturing && originFallbackActive)
                        status.setText("TRACKING • Depth ✓ • стабілізація координат");
                    else status.setText(depthSupported
                            ? (rawPrecisionMode?(automaticDepthMode?"TRACKING • RAW + DEPTH ✓":"TRACKING • RAW PRECISION ✓"):"TRACKING • Depth API ✓")
                            : "TRACKING • depth ✕");
                }else{
                    TrackingFailureReason reason=camera.getTrackingFailureReason();
                    status.setText("Tracking: "+tracking+" • "+trackingHint(reason));
                }
                String distance=st.currentMeanDistanceM>0
                        ? String.format(java.util.Locale.US,"%.1f м",st.currentMeanDistanceM):"—";
                String floorD=Float.isFinite(st.floorDistanceM)?String.format(java.util.Locale.US,"%.2fм↓",st.floorDistanceM):"—";
                String wallD=Float.isFinite(st.nearestWallDistanceM)?String.format(java.util.Locale.US,"%.2fм",st.nearestWallDistanceM):"—";
                String ceilD=Float.isFinite(st.ceilingDistanceM)?String.format(java.util.Locale.US,"%.2fм↑",st.ceilingDistanceM):"—";
                if(capturing){
                    String gridMode="Surface-сітка";
                    IndoorSemanticSystem.Snapshot sem=semanticSystem==null?null:semanticSystem.snapshot();
                    String ai="AI: —";
                    if(sem!=null){
                        if(sem.modelState==com.elektoryk.scan.semantic.SemanticModelManager.State.DOWNLOADING)ai="AI: завантажую indoor-модель…";
                        else if(sem.modelState==com.elektoryk.scan.semantic.SemanticModelManager.State.READY){
                            String dims="";if(sem.targetWidthM>0.15&&sem.targetHeightM>0.15){
                                dims=String.format(java.util.Locale.US," • ~%.2f×%.2f",sem.targetWidthM,sem.targetHeightM);
                                if(sem.lookingAt.objectLike&&sem.targetDepthM>0.10)dims+=String.format(java.util.Locale.US,"×%.2f м",sem.targetDepthM);else dims+=" м";
                            }
                            String modelTag=sem.modelFlavor==com.elektoryk.scan.semantic.SemanticModelManager.Flavor.TF_DEEPLAB_ADE151?"150":"32 (базові класи)";
                            ai="AI"+modelTag+": "+sem.lookingAt.ua+" "+Math.round(sem.lookingAtShare*100f)+"%"+dims+" • об'єктів "+sem.entities;
                        }else if(sem.modelState==com.elektoryk.scan.semantic.SemanticModelManager.State.ERROR)ai="AI: модель недоступна • геометричний режим";
                    }
                    String geom="";
                    if(gridStats.activeWidthM>0.35f&&gridStats.activeHeightM>0.35f){
                        String k="WALL".equals(gridStats.activeKind)?"стіна":"FLOOR".equals(gridStats.activeKind)?"підлога":"CEILING".equals(gridStats.activeKind)?"стеля":gridStats.activeKind;
                        geom=String.format(java.util.Locale.US," • %s ~%.2f×%.2f м",k,gridStats.activeWidthM,gridStats.activeHeightM);
                    }
                    metrics.setText("Покриття "+st.spatialCoveragePercent+"% • якість "+st.scanQualityPercent+"% • ракурси "+st.directionCoveragePercent+"%\n"
                             +gridMode+" 10 см • стін "+gridStats.stableWalls+" • підтверджено "+gridStats.coveredPercent()+"% • активних "+gridStats.visiblePlanes
                            +" • кадр 🟢 "+gridStats.activeGreenTiles+" • 🟡 "+gridStats.activeYellowTiles+" • 🔴 "+gridStats.activeRedTiles
                            +" • depth "+distance+geom+"\n"+ai);
                }else{
                    metrics.setText("3D "+st.spatialCoveragePercent+"% • ЯКІСТЬ "+st.scanQualityPercent+"% • multi-view "+st.multiViewPercent+"% • ракурси "+st.directionCoveragePercent+"%\n"
                            +"підлога "+st.lowBandPercent+"% • стіни "+st.midBandPercent+"% • верх "+st.highBandPercent+"% • depth "+distance
                            +" • RAW "+st.rawDepthFrames+" • smooth "+st.fallbackDepthFrames);
                }
                progress.setProgress(st.scanQualityPercent);
                if(liveCoverageView!=null)liveCoverageView.setSnapshot(st.coverageSnapshot);
                if(capturing && !capturePaused && !forceFinishArmed){
                    IndoorSemanticSystem.Snapshot sem=semanticSystem==null?null:semanticSystem.snapshot();
                    if(sem!=null&&sem.modelState==com.elektoryk.scan.semantic.SemanticModelManager.State.READY&&sem.lookingAtShare>=0.34f&&sem.lookingAt.objectLike&&!sem.lookingAt.isOpening()){
                        hint.setText("AI бачить: "+sem.lookingAt.ua+". Не зараховую це як стіну; обійди об'єкт або покажи його краї, щоб зберегти розміри й продовжити стіну позаду.");
                    }else if(sem!=null&&sem.modelState==com.elektoryk.scan.semantic.SemanticModelManager.State.READY&&sem.lookingAt.isOpening()&&sem.lookingAtShare>=0.24f){
                        hint.setText("AI бачить: "+sem.lookingAt.ua+". Зберігаю його як отвір конкретної стіни; покажи краї зліва/справа та верх/низ для точних розмірів.");
                    }else if(sem!=null&&sem.modelState==com.elektoryk.scan.semantic.SemanticModelManager.State.READY&&sem.lookingAt.isRoomEnvelope()&&sem.lookingAtShare>=0.30f&&gridStats.activeWidthM>0.35f){
                        hint.setText(String.format(java.util.Locale.US,"AI бачить: %s. Активна поверхня ~%.2f×%.2f м. Веди камерою вздовж її країв — розмір і покриття уточнюються в цій самій Surface ID.",sem.lookingAt.ua,gridStats.activeWidthM,gridStats.activeHeightM));
                    }else if(!gridStats.freshEvidence){
                        hint.setText("Цей кадр не додав нових Depth-точок. Уже відсканована сітка не стирається: поверни камеру на збережену стіну або зміни кут на 10–30 см.");
                    }else if(gridStats.depthFallback && gridStats.redTiles>gridStats.yellowTiles+gridStats.greenTiles){
                        hint.setText("Surface-сітка вже з'явилась. Червоне = один незалежний кадр; змісти телефон убік і покажи цю саму ділянку ще раз.");
                    }else if(gridStats.redTiles>gridStats.yellowTiles+gridStats.greenTiles){
                        hint.setText("Червоне — поверхня вже знайдена, але даних мало. Проведи камерою вздовж цієї стіни/стелі, не стій на одному місці.");
                    }else if(gridStats.yellowTiles>gridStats.greenTiles){
                        hint.setText("Жовте — вже зчитано. Змістись приблизно на 20–50 см і покажи цю ж поверхню ще раз, щоб вона стала зеленою.");
                    }else if(gridStats.greenTiles>0){
                        String extra=(st.liveHint!=null&&!st.liveHint.isEmpty())?" • "+st.liveHint:"";
                        hint.setText("Зелене — ця зона підтверджена. Можеш переходити далі до червоних/порожніх ділянок."+extra);
                    }
                }
                boolean heldReady=capturing&&!forceFinishArmed&&isStableReady(st,gridStats,System.nanoTime());
                if(heldReady)
                    scanButton.setText("Завершити • стабільний скан ✓");
                else if(capturing && !forceFinishArmed)
                    scanButton.setText((st.finishRecommended&&gridStats.visuallyReady())?"Перевіряю стабільність…":"Завершити скан і проаналізувати");
            });
        }catch(Exception e){
            runOnUiThread(() -> {if(!activityDestroyed&&!isFinishing()&&!isDestroyed())status.setText("AR frame error: "+e.getClass().getSimpleName());});
        }
    }

    /**
     * Resolve a stable scan-space origin without blocking Depth collection on one Anchor.
     *
     * While the origin Anchor is TRACKING we accept ARCore's normal anchor-pose refinement.
     * If that Anchor becomes PAUSED we keep the last logical origin unchanged. We deliberately
     * do not chase/rebase to the current camera pose because doing so mixes real user motion into
     * the coordinate frame of already accumulated voxels and creates parallel/shifted walls.
     */
    private Pose resolveScanOrigin(Session s,Camera camera,long frameTs){
        Pose cameraWorld=camera.getPose();
        if(scanOriginPose==null){
            scanOriginPose=Pose.makeTranslation(cameraWorld.tx(),cameraWorld.ty(),cameraWorld.tz());
            try{ originAnchor=s.createAnchor(scanOriginPose); }catch(Exception ignored){ originAnchor=null; }
            originFallbackActive=originAnchor==null || originAnchor.getTrackingState()!=TrackingState.TRACKING;
            return scanOriginPose;
        }

        if(originAnchor!=null && originAnchor.getTrackingState()==TrackingState.TRACKING){
            Pose candidate=originAnchor.getPose();
            // Accept small ARCore anchor refinements, but never snap the local camera several
            // decimeters because that would bend the accumulated room.
            if(lastLocalCameraPose==null || (localPoseJump(candidate,cameraWorld,lastLocalCameraPose)<0.35f
                    && localRotationJumpDeg(candidate,cameraWorld,lastLocalCameraPose)<18f)){
                scanOriginPose=candidate;
            }
            originFallbackActive=false;
            return scanOriginPose;
        }

        // Camera tracking can stay healthy while this one Anchor is temporarily PAUSED.
        // DO NOT move/rebase the logical scan origin to the current camera pose here.
        // The old v0.17.3 formula (cameraWorld * inverse(previousLocalCamera)) could absorb
        // real user motion into the origin every ~0.9 s. Because already accumulated voxels
        // were not transformed, one real wall became several parallel sheets.
        //
        // Keep the last known logical origin unchanged. When the Anchor starts TRACKING again,
        // its normal ARCore pose refinement is accepted above. Collector pose-jump guards still
        // reject a genuine relocalization shock instead of silently bending the room.
        originFallbackActive=true;
        return scanOriginPose;
    }

    private static float localPoseJump(Pose origin,Pose cameraWorld,Pose previousLocal){
        try{
            Pose nowLocal=origin.inverse().compose(cameraWorld);
            float dx=nowLocal.tx()-previousLocal.tx();
            float dy=nowLocal.ty()-previousLocal.ty();
            float dz=nowLocal.tz()-previousLocal.tz();
            return (float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        }catch(Exception e){ return Float.POSITIVE_INFINITY; }
    }

    private static float localRotationJumpDeg(Pose origin,Pose cameraWorld,Pose previousLocal){
        try{
            Pose nowLocal=origin.inverse().compose(cameraWorld);
            float[] a=nowLocal.getRotationQuaternion(),b=previousLocal.getRotationQuaternion();
            if(a==null||b==null||a.length<4||b.length<4)return Float.POSITIVE_INFINITY;
            double dot=Math.abs(a[0]*b[0]+a[1]*b[1]+a[2]*b[2]+a[3]*b[3]);
            dot=Math.max(0.0,Math.min(1.0,dot));
            return (float)Math.toDegrees(2.0*Math.acos(dot));
        }catch(Exception e){ return Float.POSITIVE_INFINITY; }
    }

    private boolean submitWorker(Runnable task){
        if(task==null||activityDestroyed||worker.isShutdown())return false;
        try{worker.execute(task);return true;}
        catch(java.util.concurrent.RejectedExecutionException ignored){return false;}
    }

    private boolean submitTelemetry(Runnable task){
        if(task==null||activityDestroyed||telemetryWorker.isShutdown())return false;
        try{telemetryWorker.execute(task);return true;}
        catch(java.util.concurrent.RejectedExecutionException ignored){return false;}
    }

    private void scheduleTelemetry(long nowNs){
        if(telemetryScheduled)return;
        if(latestStats!=null && nowNs-lastTelemetryScheduleNs<600_000_000L)return;
        telemetryScheduled=true;
        lastTelemetryScheduleNs=nowNs;
        submitTelemetry(() -> {
            try{
                latestStats=collector.stats();
            }catch(Throwable e){
                new com.elektoryk.scan.storage.StabilityReporter(this)
                        .error("SCAN_TELEMETRY","Live scan telemetry failed",e);
            }finally{
                telemetryScheduled=false;
            }
        });
    }

    private String trackingHint(TrackingFailureReason reason){
        if(reason==null)return "очікування";
        String n=String.valueOf(reason);
        if("EXCESSIVE_MOTION".equals(n))return "рух занадто швидкий";
        if("INSUFFICIENT_LIGHT".equals(n))return "замало світла";
        if("INSUFFICIENT_FEATURES".equals(n))return "мало деталей у кадрі — зміни кут";
        if("BAD_STATE".equals(n))return "ARCore тимчасово втратив позицію";
        if("CAMERA_UNAVAILABLE".equals(n))return "камера недоступна";
        return reason.toString();
    }

    private int displayRotation(){
        if(android.os.Build.VERSION.SDK_INT>=30){
            android.view.Display d=getDisplay();
            return d==null?Surface.ROTATION_0:d.getRotation();
        }
        return getWindowManager().getDefaultDisplay().getRotation();
    }

    private void showError(String m){
        status.setText(m);
        scanButton.setEnabled(false);
        if(hint!=null)hint.setText("ARCore Smart Scan не запущено. Camera2/IMU fallback нижче — лише діагностика, він не будує 3D-сцену.");
        if(fallbackButton!=null)fallbackButton.setVisibility(View.VISIBLE);
    }
}
