package com.kow565.perchancecompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class HarinKeepAliveService extends Service {
    private static final String CHANNEL = "harin_connection";
    private static final int NOTIFICATION_ID = 56501;

    public static void start(Context context) {
        Intent intent = new Intent(context, HarinKeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (Throwable ignored) {}
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Harin 연결 유지", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Harin을 백그라운드에서 다시 열 수 있게 유지합니다.");
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, InboxActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("Harin 실행 중")
                .setContentText("눌러서 Perchance 대화를 다시 열기")
                .setOngoing(true)
                .setContentIntent(content)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
