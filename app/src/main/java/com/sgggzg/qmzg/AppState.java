package com.sgggzg.qmzg;

import android.graphics.Bitmap;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppState {
    // 【统一生命周期控制变量】
    public static boolean isMonitoring = false;
    public static boolean isAlarming = false;

    // 【新增】图像与日志写入开关
    public static boolean isImageCaptureEnabled = true;
    public static boolean isLogWriteEnabled = true;

    // 【统一数据缓存】解决后台广播丢失问题
    public static Bitmap lastPreviewBitmap = null;
    public static final StringBuilder logBuffer = new StringBuilder();

    // 供 UI 和 Service 共同读取的配置
    public static String targetId = "158";
    public static String keyword = "正在尝试占领";
    public static float topRatio = 0.66f;
    public static float bottomRatio = 0.81f;
    public static boolean isVibrateEnabled = true;
    public static boolean isSoundEnabled = true;

    public static List<List<String>> conditionGroups = new ArrayList<>();
    public static List<String> excludeWords = new ArrayList<>();

    public static void appendLog(String msg) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
        String time = sdf.format(new Date());
        logBuffer.append(time).append(" ").append(msg).append("\n");
        String[] lines = logBuffer.toString().split("\n");
        if (lines.length > 500) {
            int start = lines.length - 400;
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < lines.length; i++) sb.append(lines[i]).append("\n");
            logBuffer.setLength(0);
            logBuffer.append(sb);
        }
    }
}
