package com.sgggzg.qmzg;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationHelper {

    private static final String CHANNEL_ID = "game_monitor";
    private static final int NOTIFICATION_ID = 1;

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "游戏监控", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("实时监控游戏公告");
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    public static Notification buildNotification(Context context, String title, String content, boolean isAlarming) {
        Intent stopMonitorIntent = new Intent(context, MonitorService.class);
        stopMonitorIntent.setAction(MonitorService.ACTION_STOP_MONITOR);
        // 【核心修复】使用 FLAG_CANCEL_CURRENT 清除旧的缓存
        PendingIntent stopMonitorPendingIntent = PendingIntent.getService(context, 1, stopMonitorIntent,
                                                                          PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title).setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_info_details).setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止监控", stopMonitorPendingIntent);

        if (isAlarming) {
            Intent stopAlarmIntent = new Intent(context, MonitorService.class);
            stopAlarmIntent.setAction(MonitorService.ACTION_STOP_ALARM);
            // 【核心修复】同样加上 FLAG_CANCEL_CURRENT
            PendingIntent stopAlarmPendingIntent = PendingIntent.getService(context, 2, stopAlarmIntent,
                                                                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);
            builder.addAction(android.R.drawable.ic_lock_idle_alarm, "停止报警", stopAlarmPendingIntent);
        }
        return builder.build();
    }

    public static void notify(Context context, String title, String content, boolean isAlarming) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(context, title, content, isAlarming));
    }

    // 【新增】彻底清除所有通知的方法
    public static void cancelAll(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancelAll();
    }
}
