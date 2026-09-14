package com.sgggzg.qmzg;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class RegionAdjustView extends View {

    private Paint paint;
    private RectF rect = new RectF(0.1f, 0.3f, 0.9f, 0.7f); // 归一化坐标
    private float topRatio = 0.3f;
    private float bottomRatio = 0.7f;
    private float leftRatio = 0.1f;
    private float rightRatio = 0.9f;

    private boolean isDragging = false;
    private int dragEdge = -1; // 0:top, 1:bottom, 2:left, 3:right
    private float lastY, lastX;

    public interface OnRegionChangedListener {
        void onRegionChanged(float top, float bottom);
    }
    private OnRegionChangedListener listener;

    public RegionAdjustView(Context context) {
        super(context);
        init();
    }
    public RegionAdjustView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.parseColor("#E94560"));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8);
        paint.setAlpha(200);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public void setRegion(float top, float bottom) {
        this.topRatio = top;
        this.bottomRatio = bottom;
        invalidate();
    }

    public void setOnRegionChangedListener(OnRegionChangedListener l) {
        this.listener = l;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        float left = leftRatio * w;
        float right = rightRatio * w;
        float top = topRatio * h;
        float bottom = bottomRatio * h;
        rect.set(left, top, right, bottom);
        canvas.drawRect(rect, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int w = getWidth();
        int h = getHeight();
        float left = leftRatio * w;
        float right = rightRatio * w;
        float top = topRatio * h;
        float bottom = bottomRatio * h;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 检测点击在哪个边缘（简化：只检测上下边缘）
                if (Math.abs(y - top) < 40 && x > left && x < right) {
                    dragEdge = 0; // top
                    isDragging = true;
                } else if (Math.abs(y - bottom) < 40 && x > left && x < right) {
                    dragEdge = 1; // bottom
                    isDragging = true;
                } else {
                    isDragging = false;
                }
                lastY = y;
                lastX = x;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isDragging && dragEdge == 0) {
                    float newTop = y / h;
                    newTop = Math.max(0, Math.min(newTop, bottomRatio - 0.05f));
                    topRatio = newTop;
                    invalidate();
                    if (listener != null) listener.onRegionChanged(topRatio, bottomRatio);
                } else if (isDragging && dragEdge == 1) {
                    float newBottom = y / h;
                    newBottom = Math.min(1, Math.max(newBottom, topRatio + 0.05f));
                    bottomRatio = newBottom;
                    invalidate();
                    if (listener != null) listener.onRegionChanged(topRatio, bottomRatio);
                }
                return true;

            case MotionEvent.ACTION_UP:
                isDragging = false;
                dragEdge = -1;
                return true;
        }
        return super.onTouchEvent(event);
    }

    public float getTopRatio() { return topRatio; }
    public float getBottomRatio() { return bottomRatio; }
}
