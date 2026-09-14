package com.sgggzg.qmzg;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmWindowManager {

    public interface AlarmActionCallback {
        void onDismissAlarm();
    }

    private Context context;
    private WindowManager windowManager;
    private View alarmView;
    private AlarmActionCallback callback;
    private boolean isHiding = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public AlarmWindowManager(Context context, AlarmActionCallback callback) {
        this.context = context;
        this.callback = callback;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean isShowing() {
        return alarmView != null;
    }

    public void show(String text, int count) {
        if (alarmView != null) return;
        isHiding = false;

        mainHandler.post(() -> {
            try {
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                alarmView = inflater.inflate(R.layout.window_alarm, null);

                TextView tvTime = alarmView.findViewById(R.id.tvAlertTime);
                TextView tvContent = alarmView.findViewById(R.id.tvAlertContent);
                TextView tvCount = alarmView.findViewById(R.id.tvAlertCount);
                TextView btnDismiss = alarmView.findViewById(R.id.btnDismiss);

                tvTime.setText(new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date()));
                tvContent.setText(text);
                tvCount.setText("已报警 " + count + " 次");

                // 【核心】点击关闭，直接回调 Service，由 Service 统一处理状态
                btnDismiss.setOnClickListener(v -> {
                    if (callback != null) callback.onDismissAlarm();
                    hide();
                });

                int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                    WindowManager.LayoutParams.TYPE_PHONE;

                // 完全参考悬浮球可点击的 Flags 组合
                int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

                int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    (int) (screenWidth * 0.85), // 固定宽度，不使用 MATCH_PARENT
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutFlag,
                    flags,
                    PixelFormat.TRANSLUCENT);

                params.gravity = Gravity.CENTER;
                params.x = 0;
                params.y = 0;

                // 添加视图后，通过透明度动画实现渐入
                alarmView.setAlpha(0f);
                windowManager.addView(alarmView, params);
                alarmView.animate().alpha(1f).setDuration(300).start();

            } catch (Exception e) {
                e.printStackTrace();
                alarmView = null;
            }
        });
    }

    public void hide() {
        if (windowManager != null && alarmView != null && !isHiding) {
            isHiding = true;
            mainHandler.post(() -> {
                alarmView.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                    if (windowManager != null && alarmView != null) {
                        try { windowManager.removeView(alarmView); } catch (Exception ignored) {}
                        alarmView = null;
                    }
                    isHiding = false;
                }).start();
            });
        }
    }
}
