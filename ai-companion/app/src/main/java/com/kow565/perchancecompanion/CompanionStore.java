package com.kow565.perchancecompanion;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.Random;

public class CompanionStore {
    private static final String LEGACY_PREFS = "harin_companion_store_v1";
    private static final String PROFILE_PREFIX = "harin_companion_store_v2_";
    private static final String GLOBAL_PREFS = "harin_dm_global_v2";
    private static final String DEFAULT_PROFILE = "harin_default";

    private final Context context;
    private final String profileId;
    private final SharedPreferences prefs;

    public CompanionStore(Context context) {
        this(context, activeProfileId(context));
    }

    public CompanionStore(Context context, String profileId) {
        this.context = context.getApplicationContext();
        this.profileId = cleanId(profileId);
        this.prefs = this.context.getSharedPreferences(PROFILE_PREFIX + this.profileId, Context.MODE_PRIVATE);
        migrateLegacyIfNeeded();
        ensureDefaults();
    }

    public Context appContext() { return context; }
    public String profileId() { return profileId; }

    public static String activeProfileId(Context context) {
        return context.getApplicationContext().getSharedPreferences(GLOBAL_PREFS, Context.MODE_PRIVATE)
                .getString("active_character_id", DEFAULT_PROFILE);
    }

    public static void selectProfile(Context context, String id) {
        context.getApplicationContext().getSharedPreferences(GLOBAL_PREFS, Context.MODE_PRIVATE)
                .edit().putString("active_character_id", cleanId(id)).apply();
    }

    private static String cleanId(String value) {
        String s = value == null ? "" : value.trim();
        if (s.isEmpty()) s = DEFAULT_PROFILE;
        return s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private void migrateLegacyIfNeeded() {
        if (!DEFAULT_PROFILE.equals(profileId) || prefs.contains("migration_done")) return;
        SharedPreferences legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = prefs.edit().putBoolean("migration_done", true);
        if (!prefs.contains("state") && legacy.contains("state")) {
            for (Map.Entry<String, ?> entry : legacy.getAll().entrySet()) {
                Object v = entry.getValue();
                String k = entry.getKey();
                if (v instanceof String) e.putString(k, (String) v);
                else if (v instanceof Integer) e.putInt(k, (Integer) v);
                else if (v instanceof Long) e.putLong(k, (Long) v);
                else if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
                else if (v instanceof Float) e.putFloat(k, (Float) v);
            }
        }
        e.apply();
    }

    private synchronized void ensureDefaults() {
        if (!prefs.contains("state")) {
            JSONObject s = defaultState();
            prefs.edit().putString("state", s.toString()).apply();
        }
        if (!prefs.contains("anchor_seed")) {
            int seed = 100000 + new Random().nextInt(800000000);
            prefs.edit().putInt("anchor_seed", seed).apply();
        }
        if (!prefs.contains("ai_name")) prefs.edit().putString("ai_name", "하린").apply();
        if (!prefs.contains("user_name")) prefs.edit().putString("user_name", "자기").apply();
        if (!prefs.contains("runtime_config")) prefs.edit().putString("runtime_config", defaultRuntimeConfig().toString()).apply();
    }

    public static JSONObject defaultState() {
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
        return s;
    }

    private JSONObject defaultRuntimeConfig() {
        JSONObject o = new JSONObject();
        try {
            o.put("contactMinMinutes", 55);
            o.put("contactMaxMinutes", 245);
            o.put("storyMinMinutes", 300);
            o.put("storyMaxMinutes", 840);
            o.put("quietStartHour", 1);
            o.put("quietEndHour", 8);
            o.put("imageEveryTurns", 3);
            o.put("behaviorInstructions", "");
            o.put("visualInstructions", "");
        } catch (Exception ignored) {}
        return o;
    }

    public synchronized String aiName() { return prefs.getString("ai_name", "하린"); }
    public synchronized String userName() { return prefs.getString("user_name", "자기"); }
    public synchronized int anchorSeed() { return prefs.getInt("anchor_seed", 428731); }

    public synchronized void setNames(String aiName, String userName) {
        prefs.edit().putString("ai_name", aiName == null || aiName.trim().isEmpty() ? "하린" : aiName.trim())
                .putString("user_name", userName == null || userName.trim().isEmpty() ? "자기" : userName.trim()).apply();
    }

    public synchronized void initializeCharacter(String name, JSONObject state, int seed, boolean startFresh) {
        SharedPreferences.Editor e = prefs.edit()
                .putString("ai_name", name == null || name.trim().isEmpty() ? "하린" : name.trim())
                .putString("state", state == null ? defaultState().toString() : state.toString())
                .putInt("anchor_seed", seed)
                .putInt("turns_since_image", 0)
                .remove("next_contact_at").remove("next_story_at");
        if (startFresh) e.remove("messages").remove("stories");
        e.apply();
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
        String extra = visualInstructions();
        return "IDENTITY (never change unless user explicitly asks): " + s.optString("identity") + ". " +
                "HAIR: " + s.optString("hair") + ". OUTFIT: " + s.optString("outfit") + ". " +
                "ACCESSORIES: " + s.optString("accessories") + ". POSE: " + s.optString("pose") + ". " +
                "LOCATION: " + s.optString("location") + ". MOOD: " + s.optString("mood") + ". " +
                "LIGHTING: " + s.optString("lighting") + "." +
                (extra.isEmpty() ? "" : " USER VISUAL CUSTOMIZATION: " + extra + ".");
    }

    public synchronized JSONObject runtimeConfig() {
        try { return new JSONObject(prefs.getString("runtime_config", defaultRuntimeConfig().toString())); }
        catch (Exception e) { return defaultRuntimeConfig(); }
    }

    public synchronized void applyRuntimeConfig(JSONObject patch) {
        if (patch == null) return;
        JSONObject current = runtimeConfig();
        try {
            if (patch.has("contactMinMinutes")) current.put("contactMinMinutes", clamp(patch.optInt("contactMinMinutes", contactMinMinutes()), 15, 1440));
            if (patch.has("contactMaxMinutes")) current.put("contactMaxMinutes", clamp(patch.optInt("contactMaxMinutes", contactMaxMinutes()), 15, 2880));
            if (patch.has("storyMinMinutes")) current.put("storyMinMinutes", clamp(patch.optInt("storyMinMinutes", storyMinMinutes()), 60, 2880));
            if (patch.has("storyMaxMinutes")) current.put("storyMaxMinutes", clamp(patch.optInt("storyMaxMinutes", storyMaxMinutes()), 60, 10080));
            if (patch.has("quietStartHour")) current.put("quietStartHour", clamp(patch.optInt("quietStartHour", quietStartHour()), 0, 23));
            if (patch.has("quietEndHour")) current.put("quietEndHour", clamp(patch.optInt("quietEndHour", quietEndHour()), 0, 23));
            if (patch.has("imageEveryTurns")) current.put("imageEveryTurns", clamp(patch.optInt("imageEveryTurns", imageEveryTurns()), 1, 20));
            if (patch.has("behaviorInstructions")) current.put("behaviorInstructions", limit(patch.optString("behaviorInstructions", ""), 1200));
            if (patch.has("visualInstructions")) current.put("visualInstructions", limit(patch.optString("visualInstructions", ""), 1200));
            int cMin = current.optInt("contactMinMinutes", 55);
            if (current.optInt("contactMaxMinutes", 245) < cMin) current.put("contactMaxMinutes", cMin);
            int sMin = current.optInt("storyMinMinutes", 300);
            if (current.optInt("storyMaxMinutes", 840) < sMin) current.put("storyMaxMinutes", sMin);
            prefs.edit().putString("runtime_config", current.toString()).remove("next_contact_at").remove("next_story_at").apply();
        } catch (Exception ignored) {}
    }

    public synchronized void resetRuntimeConfig() {
        prefs.edit().putString("runtime_config", defaultRuntimeConfig().toString()).remove("next_contact_at").remove("next_story_at").apply();
    }

    public synchronized int contactMinMinutes() { return clamp(runtimeConfig().optInt("contactMinMinutes", 55), 15, 1440); }
    public synchronized int contactMaxMinutes() { return Math.max(contactMinMinutes(), clamp(runtimeConfig().optInt("contactMaxMinutes", 245), 15, 2880)); }
    public synchronized int storyMinMinutes() { return clamp(runtimeConfig().optInt("storyMinMinutes", 300), 60, 2880); }
    public synchronized int storyMaxMinutes() { return Math.max(storyMinMinutes(), clamp(runtimeConfig().optInt("storyMaxMinutes", 840), 60, 10080)); }
    public synchronized int quietStartHour() { return clamp(runtimeConfig().optInt("quietStartHour", 1), 0, 23); }
    public synchronized int quietEndHour() { return clamp(runtimeConfig().optInt("quietEndHour", 8), 0, 23); }
    public synchronized int imageEveryTurns() { return clamp(runtimeConfig().optInt("imageEveryTurns", 3), 1, 20); }
    public synchronized String behaviorInstructions() { return runtimeConfig().optString("behaviorInstructions", "").trim(); }
    public synchronized String visualInstructions() { return runtimeConfig().optString("visualInstructions", "").trim(); }

    public synchronized String runtimeConfigSummary() {
        return "선톡 " + contactMinMinutes() + "~" + contactMaxMinutes() + "분 / 스토리 " + storyMinMinutes() + "~" + storyMaxMinutes() +
                "분 / 사진 약 " + imageEveryTurns() + "턴 / 휴식 " + String.format("%02d:00~%02d:00", quietStartHour(), quietEndHour());
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
            while (arr.length() > 120) arr.remove(0);
            prefs.edit().putString("messages", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public synchronized String recentTranscript(int count) {
        JSONArray arr = messages();
        StringBuilder b = new StringBuilder();
        String behavior = behaviorInstructions();
        if (!behavior.isEmpty()) b.append("[User app behavior customization: ").append(behavior).append("]\n");
        int start = Math.max(0, arr.length() - count);
        for (int i = start; i < arr.length(); i++) {
            JSONObject m = arr.optJSONObject(i);
            if (m == null) continue;
            String who = "user".equals(m.optString("role")) ? userName() : aiName();
            b.append(who).append(": ").append(m.optString("text")).append("\n");
        }
        return b.toString();
    }

    public synchronized String lastMessagePreview() {
        JSONArray arr = messages();
        if (arr.length() == 0) return "새 대화를 시작해봐";
        JSONObject m = arr.optJSONObject(arr.length() - 1);
        if (m == null) return "";
        String t = m.optString("text", "").replace('\n', ' ').trim();
        if (t.isEmpty() && !m.optString("image", "").isEmpty()) t = "사진";
        return t.length() > 44 ? t.substring(0, 44) + "…" : t;
    }

    public synchronized long lastMessageTime() {
        JSONArray arr = messages();
        JSONObject m = arr.length() == 0 ? null : arr.optJSONObject(arr.length() - 1);
        return m == null ? 0L : m.optLong("time", 0L);
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

    private synchronized int rawTurnsSinceImage() { return prefs.getInt("turns_since_image", 0); }
    public synchronized int aiTurnsSinceImage() { return rawTurnsSinceImage(); }
    public synchronized void bumpAiTurn(boolean madeImage) { prefs.edit().putInt("turns_since_image", madeImage ? 0 : rawTurnsSinceImage() + 1).apply(); }

    public synchronized long nextContactAt() { return prefs.getLong("next_contact_at", 0L); }
    public synchronized long nextStoryAt() { return prefs.getLong("next_story_at", 0L); }
    public synchronized void setNextContactAt(long value) { prefs.edit().putLong("next_contact_at", value).apply(); }
    public synchronized void setNextStoryAt(long value) { prefs.edit().putLong("next_story_at", value).apply(); }

    public synchronized void clearConversation() {
        prefs.edit().remove("messages").remove("stories").remove("next_contact_at").remove("next_story_at").apply();
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private String limit(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        return s.length() <= max ? s : s.substring(0, max);
    }
}
