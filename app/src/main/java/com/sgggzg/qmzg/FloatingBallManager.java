package com.sgggzg.qmzg;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingBallManager {

    // 按钮回调
    public interface ActionCallback {
        void onStopMonitor();
        void onStopAlarm();
        void onLogMessage(String msg);
    }

    private Context context;
    private WindowManager windowManager;
    private View floatingView;
    private TextView tvFloatingBall;
    private LinearLayout layoutFloatMenu;
    private TextView btnFloatStopAlarm;
    private WindowManager.LayoutParams floatingParams;
    private boolean isMenuExpanded = false;
    private ActionCallback callback;

    public FloatingBallManager(Context context, ActionCallback callback) {
        this.context = context;
        this.callback = callback;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    /**
     * 显示悬浮球
     */
    public void show() {
        if (floatingView != null) return;

        try {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            floatingView = inflater.inflate(R.layout.float_ball, null);

            tvFloatingBall = floatingView.findViewById(R.id.tv_float_ball);
            layoutFloatMenu = floatingView.findViewById(R.id.layout_float_menu);
            btnFloatStopAlarm = floatingView.findViewById(R.id.btn_float_stop_alarm);
            TextView btnFloatStopMonitor = floatingView.findViewById(R.id.btn_float_stop_monitor);

            int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

            floatingParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
            floatingParams.gravity = Gravity.TOP | Gravity.START;
            floatingParams.x = 50;
            floatingParams.y = 200;

            // 点击小圆球展开/收起菜单
            tvFloatingBall.setOnClickListener(v -> {
                isMenuExpanded = !isMenuExpanded;
                layoutFloatMenu.setVisibility(isMenuExpanded ? View.VISIBLE : View.GONE);
                if (floatingView != null && windowManager != null) {
                    windowManager.updateViewLayout(floatingView, floatingParams);
                }
            });

            // 拖动逻辑
            tvFloatingBall.setOnTouchListener(new View.OnTouchListener() {
                    private int initialX, initialY;
                    private float initialTouchX, initialTouchY;
                    private boolean isDragging = false;

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                initialX = floatingParams.x;
                                initialY = floatingParams.y;
                                initialTouchX = event.getRawX();
                                initialTouchY = event.getRawY();
                                isDragging = false;
                                return true;
                            case MotionEvent.ACTION_MOVE:
                                if (Math.abs(event.getRawX() - initialTouchX) > 20
                                    || Math.abs(event.getRawY() - initialTouchY) > 20) {
                                    isDragging = true;
                                }
                                if (isDragging) {
                                    floatingParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                                    floatingParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                                    if (windowManager != null && floatingView != null) {
                                        windowManager.updateViewLayout(floatingView, floatingParams);
                                    }
                                }
                                return true;
                            case MotionEvent.ACTION_UP:
                                if (!isDragging) v.performClick();
                                return true;
                        }
                        return false;
                    }
                });

            // 停止报警
            btnFloatStopAlarm.setOnClickListener(v -> {
                if (callback != null) callback.onStopAlarm();
            });

            // 停止监控
            btnFloatStopMonitor.setOnClickListener(v -> {
                if (callback != null) callback.onStopMonitor();
            });

            windowManager.addView(floatingView, floatingParams);
        } catch (Exception e) {
            if (callback != null) callback.onLogMessage("添加悬浮窗失败: " + e.getMessage());
        }
    }

    /**
     * 移除悬浮球
     */
    public void hide() {
        if (windowManager != null && floatingView != null) {
            try { windowManager.removeView(floatingView); } catch (Exception ignored) {}
            floatingView = null;
        }
    }

    /**
     * 报警时，展开悬浮球并显示“停止报警”按钮，播放放大动画
     */
    public void setAlarming(boolean isAlarming) {
        if (floatingView == null) return;

        // 必须在主线程操作 UI
        floatingView.post(() -> {
            try {
                if (isAlarming) {
                    if (btnFloatStopAlarm != null) btnFloatStopAlarm.setVisibility(View.VISIBLE);
                    if (layoutFloatMenu != null) {
                        layoutFloatMenu.setVisibility(View.VISIBLE);
                        isMenuExpanded = true;
                    }
                    if (windowManager != null && floatingView != null) {
                        windowManager.updateViewLayout(floatingView, floatingParams);
                    }
                    pulseAnimation();
                } else {
                    if (btnFloatStopAlarm != null) btnFloatStopAlarm.setVisibility(View.GONE);
                    if (layoutFloatMenu != null) {
                        layoutFloatMenu.setVisibility(View.GONE);
                        isMenuExpanded = false;
                    }
                    if (windowManager != null && floatingView != null) {
                        windowManager.updateViewLayout(floatingView, floatingParams);
                    }
                }
            } catch (Exception e) {
                if (callback != null) callback.onLogMessage("更新悬浮球状态失败: " + e.getMessage());
            }
        });
    }

    private void pulseAnimation() {
        if (tvFloatingBall == null) return;
        try {
            ScaleAnimation scale = new ScaleAnimation(1.0f, 1.3f, 1.0f, 1.3f,
                                                      Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            scale.setDuration(300);
            scale.setRepeatCount(3);
            scale.setRepeatMode(Animation.REVERSE);
            tvFloatingBall.startAnimation(scale);
        } catch (Exception ignored) {}
    }
}
