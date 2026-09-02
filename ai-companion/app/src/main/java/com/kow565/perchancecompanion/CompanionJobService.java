package com.kow565.perchancecompanion;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalTime;
import java.util.Random;

public class CompanionJobService extends JobService {
    private static final int JOB_ID = 56525;
    private static final String CHANNEL = "harin_messages";
    private final Random random = new Random();

    public static void schedule(Context context) {
        if (context == null) return;
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) return;
            JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(context, CompanionJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .setPeriodic(30L * 60L * 1000L)
                    .build();
            scheduler.schedule(info);
        } catch (Throwable ignored) {}
    }

    @Override public boolean onStartJob(JobParameters params) {
        try {
            new Thread(() -> {
                try { runAllCharacters(); }
                catch (Throwable ignored) {}
                finally { try { jobFinished(params, false); } catch (Throwable ignored) {} }
            }, "harin-background-agent").start();
            return true;
        } catch (Throwable ignored) { return false; }
    }

    private void runAllCharacters() {
        if (!PerchanceSession.hasText(this)) return;
        CharacterLibrary library = new CharacterLibrary(this);
        JSONArray chars = library.characters();
        long now = System.currentTimeMillis();
        int hour = LocalTime.now().getHour();
        AiEngine engine = new AiEngine();

        for (int i = 0; i < chars.length(); i++) {
            JSONObject c = chars.optJSONObject(i);
            if (c == null) continue;
            String id = c.optString("id", "");
            if (id.isEmpty()) continue;
            CompanionStore store = new CompanionStore(this, id);
            if (store.messages().length() == 0) {
                store.initializeCharacter(c.optString("name", "하린"), c.optJSONObject("state"), c.optInt("seed", 428731), false);
                store.addMessage("ai", firstGreeting(c.optString("name", "하린")), "");
            }
            runCharacter(store, engine, now, hour);
        }
    }

    private void runCharacter(CompanionStore store, AiEngine engine, long now, int hour) {
        if (store.nextContactAt() == 0L) store.setNextContactAt(now + randomDelay(store.contactMinMinutes(), store.contactMaxMinutes()));
        if (store.nextStoryAt() == 0L) store.setNextStoryAt(now + randomDelay(store.storyMinMinutes(), store.storyMaxMinutes()));
        if (isQuietHour(hour, store.quietStartHour(), store.quietEndHour())) return;

        if (now >= store.nextContactAt()) {
            try {
                AiEngine.Turn t = engine.chatTurn(store, "", true);
                store.applyState(t.state);
                String image = "";
                if (PerchanceSession.hasImage(this)) {
                    try { image = engine.generateMessageImage(this, store, "ai", t.reply, t.imagePrompt); }
                    catch (Throwable ignored) {}
                }
                store.addMessage("ai", t.reply, image);
                store.bumpAiTurn(!image.isEmpty());
                notifyUser(store.aiName(), t.reply);
            } catch (Throwable ignored) {}
            store.setNextContactAt(now + randomDelay(store.contactMinMinutes(), store.contactMaxMinutes()));
        }

        if (now >= store.nextStoryAt()) {
            try {
                AiEngine.StoryTurn st = engine.storyTurn(store);
                store.applyState(st.state);
                String image = "";
                if (PerchanceSession.hasImage(this)) {
                    try { image = engine.generateImage(this, store, st.imagePrompt); }
                    catch (Throwable ignored) {}
                }
                store.addStory(st.caption, image);
                notifyUser(store.aiName(), "새 스토리를 올렸어");
            } catch (Throwable ignored) {}
            store.setNextStoryAt(now + randomDelay(store.storyMinMinutes(), store.storyMaxMinutes()));
        }
    }

    private String firstGreeting(String name) {
        if ("미나".equals(name)) return "어, 왔네? 😏 뭐 하고 있었어?";
        if ("소라".equals(name)) return "왔어? 오늘은 좀 어땠어 🙂";
        return "자기 왔어? 나 방금 좀 쉬고 있었어 🙂";
    }

    private long randomDelay(int minMinutes, int maxMinutes) {
        int lo = Math.max(1, minMinutes);
        int hi = Math.max(lo, maxMinutes);
        int selected = lo + (hi == lo ? 0 : random.nextInt(hi - lo + 1));
        return selected * 60L * 1000L;
    }

    private boolean isQuietHour(int hour, int start, int end) {
        if (start == end) return false;
        if (start < end) return hour >= start && hour < end;
        return hour >= start || hour < end;
    }

    private void notifyUser(String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26)
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, "AI DM 메시지와 스토리", NotificationManager.IMPORTANCE_DEFAULT));
            Intent intent = new Intent(this, InboxActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            android.app.Notification n = new android.app.Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build();
            nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), n);
        } catch (Throwable ignored) {}
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
