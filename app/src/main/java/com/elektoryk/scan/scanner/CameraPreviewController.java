package com.elektoryk.scan.scanner;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.Display;
import android.view.TextureView;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Dependency-free Camera2 preview. This is deliberately isolated from ARCore so
 * the application can boot and diagnose devices even when ARCore is unavailable.
 */
public class CameraPreviewController implements TextureView.SurfaceTextureListener {
    public interface Listener {
        void onCameraReady(String cameraId, Size previewSize);
        void onCameraError(String message, Throwable error);
    }

    private final Activity activity;
    private final TextureView textureView;
    private final Listener listener;

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession session;
    private Size previewSize;
    private String cameraId;
    private boolean started;
    private Surface previewSurface;

    public CameraPreviewController(Activity activity, TextureView textureView, Listener listener) {
        this.activity = activity;
        this.textureView = textureView;
        this.listener = listener;
    }

    public void start() {
        if(started)return;
        started = true;
        startThread();
        textureView.setSurfaceTextureListener(this);
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        }
    }

    public void stop() {
        started = false;
        if(textureView.getSurfaceTextureListener()==this)textureView.setSurfaceTextureListener(null);
        closeCamera();
        stopThread();
    }

    private void startThread() {
        if (cameraThread != null) return;
        cameraThread = new HandlerThread("ElektorykCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopThread() {
        HandlerThread t = cameraThread;
        cameraThread = null;
        cameraHandler = null;
        if (t != null) {
            t.quitSafely();
            try { t.join(600); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void chooseBackCamera(int viewWidth, int viewHeight) throws CameraAccessException {
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) throw new CameraAccessException(CameraAccessException.CAMERA_ERROR);

        String fallback = null;
        for (String id : manager.getCameraIdList()) {
            if (fallback == null) fallback = id;
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                cameraId = id;
                previewSize = choosePreviewSize(c, viewWidth, viewHeight);
                configureTransform(viewWidth, viewHeight);
                return;
            }
        }

        cameraId = fallback;
        if (cameraId != null) {
            previewSize = choosePreviewSize(manager.getCameraCharacteristics(cameraId), viewWidth, viewHeight);
            configureTransform(viewWidth, viewHeight);
        }
    }

    private Size choosePreviewSize(CameraCharacteristics c, int viewWidth, int viewHeight) {
        StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) return new Size(1280, 720);
        Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
        if (sizes == null || sizes.length == 0) return new Size(1280, 720);

        final double targetRatio = viewWidth > 0 && viewHeight > 0
                ? (double)Math.max(viewWidth, viewHeight) / Math.max(1, Math.min(viewWidth, viewHeight))
                : 16.0 / 9.0;

        Arrays.sort(sizes, new Comparator<Size>() {
            @Override public int compare(Size a, Size b) {
                long aa = (long)a.getWidth() * a.getHeight();
                long bb = (long)b.getWidth() * b.getHeight();
                return Long.compare(aa, bb);
            }
        });

        Size best = sizes[0];
        double bestScore = Double.MAX_VALUE;
        for (Size s : sizes) {
            long pixels = (long)s.getWidth() * s.getHeight();
            if (pixels > 1920L * 1080L) continue; // preview should stay light on phone
            double ratio = (double)Math.max(s.getWidth(), s.getHeight()) /
                    Math.max(1, Math.min(s.getWidth(), s.getHeight()));
            double score = Math.abs(ratio - targetRatio) + Math.abs(pixels - 1280L*720L) / 5_000_000.0;
            if (score < bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    private void openCamera(int width, int height) {
        if (!started || cameraDevice != null) return;
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            notifyError("Немає дозволу на камеру", null);
            return;
        }

        try {
            chooseBackCamera(width, height);
            if (cameraId == null) {
                notifyError("Камеру не знайдено", null);
                return;
            }
            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) {
                    if(!started){camera.close();return;}
                    cameraDevice = camera;
                    createPreviewSession();
                }

                @Override public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    if (cameraDevice == camera) cameraDevice = null;
                    notifyError("Камера від'єднана системою", null);
                }

                @Override public void onError(CameraDevice camera, int error) {
                    camera.close();
                    if (cameraDevice == camera) cameraDevice = null;
                    notifyError("Camera2 error=" + error, null);
                }
            }, cameraHandler);
        } catch (Exception e) {
            notifyError("Не вдалося відкрити Camera2", e);
        }
    }

    private void createPreviewSession() {
        if (cameraDevice == null || !textureView.isAvailable()) return;
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null) return;
            Size s = previewSize != null ? previewSize : new Size(1280, 720);
            texture.setDefaultBufferSize(s.getWidth(), s.getHeight());
            if(previewSurface!=null){try{previewSurface.release();}catch(Exception ignored){}}
            previewSurface = new Surface(texture);

            final CaptureRequest.Builder request = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(previewSurface);
            request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    if (!started || cameraDevice == null) {cameraCaptureSession.close();return;}
                    session = cameraCaptureSession;
                    try {
                        session.setRepeatingRequest(request.build(), null, cameraHandler);
                        if (listener != null) listener.onCameraReady(cameraId, previewSize);
                    } catch (CameraAccessException e) {
                        notifyError("Не вдалося запустити preview", e);
                    }
                }

                @Override public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    notifyError("Camera preview configuration failed", null);
                }
            }, cameraHandler);
        } catch (Exception e) {
            notifyError("Не вдалося створити preview session", e);
        }
    }


    private void configureTransform(final int viewWidth, final int viewHeight) {
        if (previewSize == null || viewWidth <= 0 || viewHeight <= 0) return;

        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                try {
                    int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                    Matrix matrix = new Matrix();
                    RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);

                    boolean swap = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
                    float bufferW = swap ? previewSize.getWidth() : previewSize.getHeight();
                    float bufferH = swap ? previewSize.getHeight() : previewSize.getWidth();

                    RectF bufferRect = new RectF(0, 0, bufferW, bufferH);
                    float cx = viewRect.centerX();
                    float cy = viewRect.centerY();
                    bufferRect.offset(cx - bufferRect.centerX(), cy - bufferRect.centerY());

                    matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                    float scale = Math.max(
                            (float)viewHeight / Math.max(1f, bufferH),
                            (float)viewWidth / Math.max(1f, bufferW));
                    matrix.postScale(scale, scale, cx, cy);

                    if (rotation == Surface.ROTATION_90) {
                        matrix.postRotate(-90, cx, cy);
                    } else if (rotation == Surface.ROTATION_270) {
                        matrix.postRotate(90, cx, cy);
                    } else if (rotation == Surface.ROTATION_180) {
                        matrix.postRotate(180, cx, cy);
                    }
                    textureView.setTransform(matrix);
                } catch (Exception ignored) {}
            }
        });
    }

    private void closeCamera() {
        try {
            if (session != null) session.close();
        } catch (Exception ignored) {}
        session = null;

        try {
            if (cameraDevice != null) cameraDevice.close();
        } catch (Exception ignored) {}
        cameraDevice = null;
        if(previewSurface!=null){try{previewSurface.release();}catch(Exception ignored){}previewSurface=null;}
    }

    private void notifyError(final String message, final Throwable error) {
        if (listener == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                listener.onCameraError(message, error);
            }
        });
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if(started)openCamera(width, height);
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        configureTransform(width, height);
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        closeCamera();
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
}
