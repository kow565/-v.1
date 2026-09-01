package com.kow565.perchancecompanion;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Iterator;

public final class PerchanceSession {
    private static final String PREFS = "harin_perchance_session_v2";
    private static final long MAX_AGE_MS = 12L * 60L * 60L * 1000L;

    private PerchanceSession() {}

    public static void save(Context context, String kind, String key, String cookie) {
        if (context == null || key == null || key.trim().isEmpty()) return;
        SharedPreferences.Editor e = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putString(kind + "_key", key.trim());
        e.putString(kind + "_cookie", cookie == null ? "" : cookie);
        e.putLong(kind + "_time", System.currentTimeMillis());
        e.apply();
    }

    public static String key(Context context, String kind) {
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long t = p.getLong(kind + "_time", 0L);
        if (t == 0L || System.currentTimeMillis() - t > MAX_AGE_MS) return "";
        return p.getString(kind + "_key", "");
    }

    public static String cookie(Context context, String kind) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(kind + "_cookie", "");
    }

    public static boolean hasText(Context context) { return !key(context, "text").isEmpty(); }
    public static boolean hasImage(Context context) { return !key(context, "image").isEmpty(); }
    public static boolean isReady(Context context) { return hasText(context) && hasImage(context); }

    public static void clear(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    public static String parseUserKey(String content) {
        if (content == null) return "";
        String raw = content.trim();
        try {
            Object parsed = new org.json.JSONTokener(raw).nextValue();
            String found = findKey(parsed);
            if (!found.isEmpty()) return found;
        } catch (Exception ignored) {}

        String normalized = raw.replace("&quot;", "\"").replace("\\\"", "\"");
        String needle = "\"userKey\"";
        int i = normalized.indexOf(needle);
        if (i >= 0) {
            int colon = normalized.indexOf(':', i + needle.length());
            if (colon >= 0) {
                int q1 = normalized.indexOf('\"', colon + 1);
                int q2 = q1 < 0 ? -1 : normalized.indexOf('\"', q1 + 1);
                if (q1 >= 0 && q2 > q1 + 1) return normalized.substring(q1 + 1, q2).trim();
            }
        }
        return "";
    }

    private static String findKey(Object value) {
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            String direct = o.optString("userKey", "").trim();
            if (!direct.isEmpty()) return direct;
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String found = findKey(o.opt(it.next()));
                if (!found.isEmpty()) return found;
            }
        } else if (value instanceof org.json.JSONArray) {
            org.json.JSONArray a = (org.json.JSONArray) value;
            for (int i = 0; i < a.length(); i++) {
                String found = findKey(a.opt(i));
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }
}
