package com.sgggzg.qmzg;

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;

public class ScreenCaptureManager {

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth, screenHeight, screenDensity;
    private boolean isCapturing = false;

    public interface StopCallback {
        void onProjectionStopped();
    }

    private StopCallback stopCallback;

    public void setStopCallback(StopCallback callback) {
        this.stopCallback = callback;
    }

    // 【核心修复】直接传入 MediaProjection 和屏幕参数，不再传入 Context
    public boolean start(MediaProjection projection, int width, int height, int density, Handler handler) {
        try {
            this.mediaProjection = projection;
            this.screenWidth = width;
            this.screenHeight = height;
            this.screenDensity = density;

            if (mediaProjection == null) return false;

            mediaProjection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        isCapturing = false;
                        if (stopCallback != null) stopCallback.onProjectionStopped();
                    }
                }, handler);

            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
            virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                                                                  screenWidth, screenHeight, screenDensity,
                                                                  DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                                                                  imageReader.getSurface(), null, null);

            isCapturing = true;
            return true;
        } catch (Exception e) {
            isCapturing = false;
            return false;
        }
    }

    public Bitmap acquireLatestBitmap() {
        if (!isCapturing || imageReader == null) return null;

        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) return null;

            Image.Plane[] planes = image.getPlanes();
            if (planes.length == 0) return null;

            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * image.getWidth();

            Bitmap paddedBitmap = Bitmap.createBitmap(
                image.getWidth() + rowPadding / pixelStride,
                image.getHeight(),
                Bitmap.Config.ARGB_8888);
            paddedBitmap.copyPixelsFromBuffer(buffer);

            Bitmap resultBitmap = Bitmap.createBitmap(paddedBitmap, 0, 0, image.getWidth(), image.getHeight());
            paddedBitmap.recycle();
            return resultBitmap;
        } catch (Exception e) {
            return null;
        } finally {
            if (image != null) image.close();
        }
    }

    public void release() {
        isCapturing = false;
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Exception ignored) {}
            virtualDisplay = null;
        }
        if (imageReader != null) {
            try { imageReader.close(); } catch (Exception ignored) {}
            imageReader = null;
        }
        mediaProjection = null;
    }

    public boolean isCapturing() { return isCapturing; }
}
