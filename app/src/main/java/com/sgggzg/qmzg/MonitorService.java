package com.sgggzg.qmzg;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MonitorService extends Service {

    public static final String ACTION_STOP_MONITOR = "com.sgggzg.qmzg.STOP_MONITOR";
    public static final String ACTION_STOP_ALARM = "com.sgggzg.qmzg.STOP_ALARM";
    public static final String ACTION_TEST_ALARM = "com.sgggzg.qmzg.TEST_ALARM";
    public static final String ACTION_SERVICE_STATE_CHANGED = "com.sgggzg.qmzg.SERVICE_STATE_CHANGED";

    public static Intent sCaptureIntent = null;
    public static int sResultCode = 0;

    private OcrEngine ocrEngine;
    private ScreenCaptureManager screenCaptureManager;
    private FloatingBallManager floatingBallManager;
    private AlarmWindowManager alarmWindowManager;
    private Handler handler;
    private MediaPlayer alarmPlayer;
    private PowerManager.WakeLock wakeLock;

    private int realAlertCount = 0;
    private long lastAlertTime = 0;

    private long ocrIntervalMs = 3000;
    private boolean skipNoChange = true;
    private Bitmap lastCroppedBitmap = null;

    private List<List<String>> conditionGroups = new ArrayList<>();
    private List<String> excludeWords = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        AppState.isMonitoring = true;
        AppState.appendLog("=== Service onCreate ===");
        handler = new Handler(Looper.getMainLooper());
        NotificationHelper.createChannel(this);

        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WindowDetector::MonitorWakeLock");
            wakeLock.acquire(10 * 60 * 1000L);
        } catch (Exception e) {
            writeFileLog("申请 WakeLock 失败: " + e.getMessage());
        }

        ocrEngine = new OcrEngine();
        ocrEngine.init(this, new OcrEngine.InitCallback() {
                @Override public void onSuccess() { sendLog("Tesseract 初始化成功！"); }
                @Override public void onFailure(String errorMsg) { sendLog("Tesseract 初始化失败: " + errorMsg); }
            });

        screenCaptureManager = new ScreenCaptureManager();
        screenCaptureManager.setStopCallback(() -> {
            sendLog("MediaProjection 被系统停止");
            stopSelf();
        });

        floatingBallManager = new FloatingBallManager(this, new FloatingBallManager.ActionCallback() {
                @Override public void onStopMonitor() { sendLog("悬浮球：停止监控"); stopSelf(); }
                @Override public void onStopAlarm() { sendLog("悬浮球：停止报警"); stopAlarmAndResetUI(); }
                @Override public void onLogMessage(String msg) { sendLog(msg); }
            });

        alarmWindowManager = new AlarmWindowManager(this, new AlarmWindowManager.AlarmActionCallback() {
                @Override public void onDismissAlarm() { stopAlarmAndResetUI(); }
            });

        sendServiceStateChanged();
    }

    private void sendServiceStateChanged() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_SERVICE_STATE_CHANGED));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            sendLog("系统意外重启服务，强制停止");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (action != null) {
            if (ACTION_STOP_MONITOR.equals(action)) {
                sendLog("通知栏点击：停止监控");
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_STOP_ALARM.equals(action)) {
                sendLog("收到停止报警指令"); stopAlarmAndResetUI(); return START_STICKY;
            } else if (ACTION_TEST_ALARM.equals(action)) {
                sendLog("=== 测试报警 ===");
                loadConfig();
                playAlarmEffect();
                alarmWindowManager.show("【测试】来自158的XX军团已经达成占领条件，正在尝试占领XX城", 1);
                return START_STICKY;
            }
        }

        try {
            sendLog("=== onStartCommand ===");
            loadConfig();
            startForeground();
            floatingBallManager.show();
            handler.postDelayed(this::startCapture, 1000);
        } catch (Exception e) {
            sendLog("onStartCommand异常: " + e.getMessage()); stopSelf();
        }
        return START_STICKY;
    }

    private void loadConfig() {
        SharedPreferences prefs = getSharedPreferences("config", MODE_PRIVATE);
        AppState.targetId = prefs.getString("targetId", "158");
        AppState.keyword = prefs.getString("keyword", "正在尝试占领");
        AppState.topRatio = prefs.getFloat("topRatio", 0.66f);
        AppState.bottomRatio = prefs.getFloat("bottomRatio", 0.81f);
        AppState.isVibrateEnabled = prefs.getBoolean("vibrate", true);
        AppState.isSoundEnabled = prefs.getBoolean("sound", true);

        float intervalSec = prefs.getFloat("ocr_interval", 3.0f);
        ocrIntervalMs = (long) (intervalSec * 1000);
        skipNoChange = prefs.getBoolean("no_change_skip", true);

        conditionGroups.clear();
        String jsonStr = prefs.getString("conditionGroups", "[[\"158\",\"正在尝试占领\"]]");
        try {
            JSONArray jsonArray = new JSONArray(jsonStr);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONArray groupArray = jsonArray.getJSONArray(i);
                List<String> group = new ArrayList<>();
                for (int j = 0; j < groupArray.length(); j++) group.add(groupArray.getString(j));
                conditionGroups.add(group);
            }
        } catch (JSONException e) { e.printStackTrace(); }

        excludeWords.clear();
        String excludeJson = prefs.getString("excludeWords", "[\"击飞了一名敌人\",\"成功坚守至占领计时结束\",\"来自101\"]");
        try {
            JSONArray jsonArray = new JSONArray(excludeJson);
            for (int i = 0; i < jsonArray.length(); i++) excludeWords.add(jsonArray.getString(i));
        } catch (JSONException e) { e.printStackTrace(); }

        sendLog("配置已加载: 条件组数=" + conditionGroups.size() + ", 剔除词数=" + excludeWords.size());
        sendLog("性能配置: 间隔=" + intervalSec + "秒, 无变化跳过=" + skipNoChange);
    }

    private void startForeground() {
        try {
            Notification notification = NotificationHelper.buildNotification(this, "游戏公告监控中", "检测: " + AppState.targetId + " + " + AppState.keyword, false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(1, notification);
            }
            sendLog("前台服务已启动");
        } catch (Exception e) { sendLog("startForeground 异常: " + e.getMessage()); }
    }

    private void startCapture() {
        sendLog("=== 开始屏幕捕获 ===");
        if (sCaptureIntent == null) { sendLog("错误: sCaptureIntent 为 null"); stopSelf(); return; }

        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            MediaProjection mediaProjection = mpm.getMediaProjection(sResultCode, sCaptureIntent);
            if (mediaProjection == null) {
                sendLog("错误: mediaProjection 为 null"); stopSelf(); return;
            }

            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            wm.getDefaultDisplay().getMetrics(metrics);

            boolean success = screenCaptureManager.start(mediaProjection, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, handler);
            if (success) {
                startMonitoringLoop();
                sendLog("屏幕捕获已启动");
            } else {
                sendLog("屏幕捕获启动失败"); stopSelf();
            }
        } catch (Exception e) {
            sendLog("startCapture 异常: " + e.getMessage()); stopSelf();
        }
    }

    private void startMonitoringLoop() {
        handler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!AppState.isMonitoring) return;
                    captureAndAnalyze();
                    handler.postDelayed(this, ocrIntervalMs);
                }
            }, ocrIntervalMs);
    }

    private void captureAndAnalyze() {
        if (AppState.isAlarming) return;
        try {
            Bitmap bitmap = screenCaptureManager.acquireLatestBitmap();
            if (bitmap == null) return;

            Bitmap cropped = cropArea(bitmap);
            bitmap.recycle();
            if (cropped == null) return;

            if (skipNoChange && lastCroppedBitmap != null) {
                if (isSameImage(cropped, lastCroppedBitmap)) {
                    sendLog("画面无变化，跳过本次OCR");
                    cropped.recycle();
                    return; 
                }
            }

            if (lastCroppedBitmap != null) lastCroppedBitmap.recycle();
            lastCroppedBitmap = cropped.copy(cropped.getConfig(), false);

            sendImage(cropped);

            String result = ocrEngine.recognize(cropped);
            cropped.recycle();

            if (result != null && !result.trim().isEmpty()) {
                sendLog("OCR: " + (result.length() > 100 ? result.substring(0, 100) + "..." : result).replace("\n", " "));
                if (ConditionMatcher.shouldTriggerAlarm(result, conditionGroups, excludeWords, msg -> sendLog(msg))) {
                    sendLog("匹配到报警条件！");
                    triggerRealAlarm(result);
                }
            }
        } catch (Exception e) { sendLog("captureAndAnalyze异常: " + e.getMessage()); }
    }

    private boolean isSameImage(Bitmap b1, Bitmap b2) {
        if (b1 == null || b2 == null) return false;
        try {
            int targetWidth = 128;
            int originalWidth = b1.getWidth();
            int originalHeight = b1.getHeight();
            if (originalWidth == 0 || originalHeight == 0) return false;

            float scale = (float) targetWidth / originalWidth;
            int targetHeight = Math.max(1, (int) (originalHeight * scale));

            Bitmap s1 = Bitmap.createScaledBitmap(b1, targetWidth, targetHeight, false);
            Bitmap s2 = Bitmap.createScaledBitmap(b2, targetWidth, targetHeight, false);

            long diff = 0;
            for (int x = 0; x < targetWidth; x++) {
                for (int y = 0; y < targetHeight; y++) {
                    int p1 = s1.getPixel(x, y);
                    int p2 = s2.getPixel(x, y);
                    diff += Math.abs(Color.red(p1) - Color.red(p2));
                    diff += Math.abs(Color.green(p1) - Color.green(p2));
                    diff += Math.abs(Color.blue(p1) - Color.blue(p2));
                }
            }
            s1.recycle();
            s2.recycle();

            long totalChannels = (long) targetWidth * targetHeight * 3;
            long avgDiff = diff / totalChannels;
            return avgDiff < 4;
        } catch (Exception e) { return false; }
    }

    private Bitmap cropArea(Bitmap bitmap) {
        try {
            int height = bitmap.getHeight();
            if (height == 0) return null;
            int top = (int) (height * AppState.topRatio);
            int bottom = (int) (height * AppState.bottomRatio);
            if (bottom <= top) return null;
            return Bitmap.createBitmap(bitmap, 0, top, bitmap.getWidth(), bottom - top);
        } catch (Exception e) { sendLog("cropArea 异常: " + e.getMessage()); return null; }
    }

    private void playAlarmEffect() {
        if (AppState.isVibrateEnabled) {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200, 500, 200, 500}, -1));
                } else {
                    vibrator.vibrate(new long[]{0, 500, 200, 500, 200, 500}, -1);
                }
            }
        }

        if (AppState.isSoundEnabled) {
            try {
                if (alarmPlayer == null) {
                    alarmPlayer = MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
                    if (alarmPlayer != null) {
                        alarmPlayer.setLooping(true);
                        alarmPlayer.start();
                    }
                }
            } catch (Exception e) { sendLog("播放报警音失败: " + e.getMessage()); }
        }
    }

    private void stopAlarmEffect() {
        if (alarmPlayer != null) {
            try { alarmPlayer.stop(); alarmPlayer.release(); } catch (Exception e) {}
            alarmPlayer = null;
        }
    }

    private void triggerRealAlarm(String detectedText) {
        try {
            long now = System.currentTimeMillis();
            if (now - lastAlertTime <= 30000) return;

            sendLog("=== 触发真实报警 ===");
            AppState.isAlarming = true;
            lastAlertTime = now;
            realAlertCount++;

            playAlarmEffect();
            alarmWindowManager.show(detectedText, realAlertCount);
            NotificationHelper.notify(this, "警报！检测到目标公告", "点击查看详情", true);
            floatingBallManager.setAlarming(true);

        } catch (Exception e) { sendLog("triggerRealAlarm 异常: " + e.getMessage()); }
    }

    private void stopAlarmAndResetUI() {
        AppState.isAlarming = false;
        stopAlarmEffect();

        if (alarmWindowManager != null) alarmWindowManager.hide();
        NotificationHelper.notify(this, "游戏公告监控", "监控中", false);
        floatingBallManager.setAlarming(false);
    }

    private void sendImage(Bitmap bitmap) {
        // 【核心修复】根据开关拦截图像上传
        if (!AppState.isImageCaptureEnabled) return;
        if (bitmap == null) return;

        try {
            AppState.lastPreviewBitmap = bitmap.copy(bitmap.getConfig(), false);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
            byte[] data = baos.toByteArray();
            Intent intent = new Intent(MainActivity.ACTION_IMAGE);
            intent.putExtra(MainActivity.EXTRA_IMAGE, data);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    private void sendLog(final String msg) {
        // 【核心修复】根据开关拦截日志写入
        if (!AppState.isLogWriteEnabled) return;

        writeFileLog(msg);
        AppState.appendLog(msg);
        try {
            Intent intent = new Intent(MainActivity.ACTION_LOG);
            intent.putExtra(MainActivity.EXTRA_LOG, msg);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    private void writeFileLog(String msg) {
        try {
            File dir = getExternalFilesDir("ocr");
            if (dir == null) dir = new File(getFilesDir(), "ocr");
            if (!dir.exists() && !dir.mkdirs()) return;
            File logFile = new File(dir, "crash.log");
            FileOutputStream fos = new FileOutputStream(logFile, true);
            String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            fos.write((timestamp + " " + msg + "\n").getBytes());
            fos.close();
        } catch (Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        AppState.isMonitoring = false;
        try { stopAlarmAndResetUI(); } catch (Exception ignored) {}

        sendLog("=== onDestroy ===");
        if (handler != null) handler.removeCallbacksAndMessages(null);

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }

        if (lastCroppedBitmap != null) {
            lastCroppedBitmap.recycle();
            lastCroppedBitmap = null;
        }

        try { if (alarmWindowManager != null) alarmWindowManager.hide(); } catch (Exception ignored) {}
        try { if (floatingBallManager != null) floatingBallManager.hide(); } catch (Exception ignored) {}
        try { if (screenCaptureManager != null) screenCaptureManager.release(); } catch (Exception ignored) {}
        try { if (ocrEngine != null) ocrEngine.recycle(); } catch (Exception ignored) {}

        stopForeground(true);
        NotificationHelper.cancelAll(this);

        sendServiceStateChanged();
        try { LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(MainActivity.ACTION_SERVICE_STOPPED)); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
