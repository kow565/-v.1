package com.kow565.perchancecompanion;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Random;

public class CompanionStore {
    private static final String PREFS = "harin_companion_store_v1";
    private final SharedPreferences prefs;

    public CompanionStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureDefaults();
    }

    private synchronized void ensureDefaults() {
        if (!prefs.contains("state")) {
            JSONObject s = new JSONObject();
            try {
                s.put("identity", "25-year-old Korean woman named Harin, oval face, warm brown eyes, straight dark-brown shoulder-length hair, natural makeup, slim build");
                s.put("outfit", "cream knit cardigan, white fitted t-shirt, straight blue jeans");
                s.put("pose", "relaxed natural posture");
                s.put("location", "cozy studio apartment in Seoul");
                s.put("mood", "warm, playful, affectionate");
                s.put("hair", "straight dark-brown shoulder-length hair, center part");
                s.put("accessories", "small silver earrings");
                s.put("lighting", "soft realistic indoor light");
            } catch (Exception ignored) {}
            prefs.edit().putString("state", s.toString()).apply();
        }
        if (!prefs.contains("anchor_seed")) {
            int seed = 100000 + new Random().nextInt(800000000);
            prefs.edit().putInt("anchor_seed", seed).apply();
        }
        if (!prefs.contains("ai_name")) prefs.edit().putString("ai_name", "하린").apply();
        if (!prefs.contains("user_name")) prefs.edit().putString("user_name", "자기").apply();
    }

    public synchronized String aiName() { return prefs.getString("ai_name", "하린"); }
    public synchronized String userName() { return prefs.getString("user_name", "자기"); }
    public synchronized int anchorSeed() { return prefs.getInt("anchor_seed", 428731); }

    public synchronized void setNames(String aiName, String userName) {
        prefs.edit().putString("ai_name", aiName.trim().isEmpty() ? "하린" : aiName.trim())
                .putString("user_name", userName.trim().isEmpty() ? "자기" : userName.trim()).apply();
    }

    public synchronized JSONObject state() {
        try { return new JSONObject(prefs.getString("state", "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }

    public synchronized void applyState(JSONObject updates) {
        if (updates == null) return;
        JSONObject current = state();
        String[] allowed = {"outfit", "pose", "location", "mood", "hair", "accessories", "lighting"};
        try {
            for (String key : allowed) {
                if (updates.has(key) && !updates.isNull(key)) {
                    String value = updates.optString(key, "").trim();
                    if (!value.isEmpty() && !value.equalsIgnoreCase("unchanged")) current.put(key, value);
                }
            }
            prefs.edit().putString("state", current.toString()).apply();
        } catch (Exception ignored) {}
    }

    public synchronized String visualStatePrompt() {
        JSONObject s = state();
        return "IDENTITY (never change unless user explicitly asks): " + s.optString("identity") + ". " +
                "HAIR: " + s.optString("hair") + ". OUTFIT: " + s.optString("outfit") + ". " +
                "ACCESSORIES: " + s.optString("accessories") + ". POSE: " + s.optString("pose") + ". " +
                "LOCATION: " + s.optString("location") + ". MOOD: " + s.optString("mood") + ". " +
                "LIGHTING: " + s.optString("lighting") + ".";
    }

    public synchronized JSONArray messages() {
        try { return new JSONArray(prefs.getString("messages", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    public synchronized void addMessage(String role, String text, String imagePath) {
        JSONArray arr = messages();
        JSONObject m = new JSONObject();
        try {
            m.put("role", role);
            m.put("text", text == null ? "" : text);
            m.put("image", imagePath == null ? "" : imagePath);
            m.put("time", System.currentTimeMillis());
            arr.put(m);
            while (arr.length() > 80) arr.remove(0);
            prefs.edit().putString("messages", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public synchronized String recentTranscript(int count) {
        JSONArray arr = messages();
        StringBuilder b = new StringBuilder();
        int start = Math.max(0, arr.length() - count);
        for (int i = start; i < arr.length(); i++) {
            JSONObject m = arr.optJSONObject(i);
            if (m == null) continue;
            String who = "user".equals(m.optString("role")) ? userName() : aiName();
            b.append(who).append(": ").append(m.optString("text")).append("\n");
        }
        return b.toString();
    }

    public synchronized JSONArray stories() {
        JSONArray all;
        try { all = new JSONArray(prefs.getString("stories", "[]")); }
        catch (Exception e) { all = new JSONArray(); }
        JSONArray fresh = new JSONArray();
        long cutoff = System.currentTimeMillis() - 48L * 60L * 60L * 1000L;
        for (int i = 0; i < all.length(); i++) {
            JSONObject s = all.optJSONObject(i);
            if (s != null && s.optLong("time", 0) >= cutoff) fresh.put(s);
        }
        if (fresh.length() != all.length()) prefs.edit().putString("stories", fresh.toString()).apply();
        return fresh;
    }

    public synchronized void addStory(String caption, String imagePath) {
        JSONArray arr = stories();
        JSONObject s = new JSONObject();
        try {
            s.put("caption", caption == null ? "" : caption);
            s.put("image", imagePath == null ? "" : imagePath);
            s.put("time", System.currentTimeMillis());
            s.put("state", state());
            arr.put(s);
            while (arr.length() > 20) arr.remove(0);
            prefs.edit().putString("stories", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public synchronized int aiTurnsSinceImage() { return prefs.getInt("turns_since_image", 0); }
    public synchronized void bumpAiTurn(boolean madeImage) {
        prefs.edit().putInt("turns_since_image", madeImage ? 0 : aiTurnsSinceImage() + 1).apply();
    }

    public synchronized long nextContactAt() { return prefs.getLong("next_contact_at", 0L); }
    public synchronized long nextStoryAt() { return prefs.getLong("next_story_at", 0L); }
    public synchronized void setNextContactAt(long value) { prefs.edit().putLong("next_contact_at", value).apply(); }
    public synchronized void setNextStoryAt(long value) { prefs.edit().putLong("next_story_at", value).apply(); }

    public synchronized void clearConversation() {
        prefs.edit().remove("messages").remove("stories").remove("next_contact_at").remove("next_story_at").apply();
    }
}
