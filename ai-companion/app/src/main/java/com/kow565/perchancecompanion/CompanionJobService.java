package com.kow565.perchancecompanion;

import android.app.JobInfo;
import android.app.JobParameters;
import android.app.JobScheduler;
import android.app.JobService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalTime;
import java.util.Random;

public class CompanionJobService extends JobService {
    private static final int JOB_ID = 56525;
    private static final String CHANNEL = "harin_messages";
    private final Random random = new Random();

    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(context, CompanionJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(30L * 60L * 1000L)
                .build();
        scheduler.schedule(info);
    }

    @Override public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try { runAgent(); } finally { jobFinished(params, false); }
        }).start();
        return true;
    }

    private void runAgent() {
        CompanionStore store = new CompanionStore(this);
        AiEngine engine = new AiEngine();
        long now = System.currentTimeMillis();
        if (store.nextContactAt() == 0L) store.setNextContactAt(now + minutes(45 + random.nextInt(90)));
        if (store.nextStoryAt() == 0L) store.setNextStoryAt(now + minutes(120 + random.nextInt(240)));

        int hour = LocalTime.now().getHour();
        boolean quiet = hour >= 1 && hour < 8;

        if (!quiet && now >= store.nextContactAt()) {
            try {
                AiEngine.Turn t = engine.chatTurn(store, "", true);
                store.applyState(t.state);
                boolean makeImage = t.imageMoment || store.aiTurnsSinceImage() >= 3;
                String image = "";
                if (makeImage) {
                    try { image = engine.generateImage(this, store, t.imagePrompt.isEmpty() ? "a casual selfie sent to her partner" : t.imagePrompt); }
                    catch (Exception ignored) {}
                }
                store.addMessage("ai", t.reply, image);
                store.bumpAiTurn(!image.isEmpty());
                notifyUser(store.aiName(), t.reply);
            } catch (Exception ignored) {}
            store.setNextContactAt(now + minutes(55 + random.nextInt(190)));
        }

        if (!quiet && now >= store.nextStoryAt()) {
            try {
                AiEngine.StoryTurn st = engine.storyTurn(store);
                store.applyState(st.state);
                String image = engine.generateImage(this, store, st.imagePrompt);
                store.addStory(st.caption, image);
                notifyUser(store.aiName(), "새 스토리를 올렸어");
            } catch (Exception ignored) {}
            store.setNextStoryAt(now + minutes(300 + random.nextInt(540)));
        }
    }

    private long minutes(int m) { return m * 60L * 1000L; }

    private void notifyUser(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CHANNEL, "하린 메시지와 스토리", NotificationManager.IMPORTANCE_DEFAULT));
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification n = new android.app.Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), n);
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
