package com.kow565.perchancecompanion;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PerchanceSession {
    private static final String PREFS = "harin_perchance_session_v2";
    private static final long MAX_AGE_MS = 12L * 60L * 60L * 1000L;
    private static final Pattern KEY64 = Pattern.compile("(?i)userKey(?:[\\\"']?\\s*[:=]\\s*[\\\"']?|[^a-f0-9]{0,12})([a-f0-9]{64})");

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

    public static boolean isReady(Context context) {
        // v0.4 primary connectivity is the imported plugin runtime, not cached userKey values.
        try {
            if (PerchanceBrowserTransport.statusSummary().contains("플러그인 ✓")) return true;
        } catch (Throwable ignored) {}
        return hasText(context) && hasImage(context); // legacy/direct fallback status only
    }

    public static void clearKind(Context context, String kind) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(kind + "_key").remove(kind + "_cookie").remove(kind + "_time").apply();
    }

    public static void clear(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    public static String parseUserKey(String content) {
        List<String> keys = parseUserKeys(content);
        return keys.isEmpty() ? "" : keys.get(0);
    }

    public static List<String> parseUserKeys(String content) {
        ArrayList<String> out = new ArrayList<>();
        if (content == null) return out;
        String raw = content.trim();
        try {
            Object parsed = new org.json.JSONTokener(raw).nextValue();
            collectKeys(parsed, out);
        } catch (Exception ignored) {}

        String normalized = raw.replace("&quot;", "\"").replace("\\\"", "\"");
        Matcher m = KEY64.matcher(normalized);
        while (m.find()) addUnique(out, m.group(1));

        String needle = "\"userKey\"";
        int i = normalized.indexOf(needle);
        if (i >= 0) {
            int colon = normalized.indexOf(':', i + needle.length());
            if (colon >= 0) {
                int q1 = normalized.indexOf('\"', colon + 1);
                int q2 = q1 < 0 ? -1 : normalized.indexOf('\"', q1 + 1);
                if (q1 >= 0 && q2 > q1 + 1) addUnique(out, normalized.substring(q1 + 1, q2).trim());
            }
        }
        return out;
    }

    private static void collectKeys(Object value, List<String> out) {
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            String direct = o.optString("userKey", "").trim();
            if (!direct.isEmpty()) addUnique(out, direct);
            Iterator<String> it = o.keys();
            while (it.hasNext()) collectKeys(o.opt(it.next()), out);
        } else if (value instanceof org.json.JSONArray) {
            org.json.JSONArray a = (org.json.JSONArray) value;
            for (int i = 0; i < a.length(); i++) collectKeys(a.opt(i), out);
        }
    }

    private static void addUnique(List<String> out, String key) {
        if (key == null) return;
        String k = key.trim();
        if (!k.isEmpty() && !out.contains(k)) out.add(k);
    }
}
