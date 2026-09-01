package com.kow565.perchancecompanion;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public class CharacterLibrary {
    private static final String LIB_PREFS = "harin_character_library_v1";
    private static final String COMPANION_PREFS = "harin_companion_store_v1";
    private final SharedPreferences prefs;

    public CharacterLibrary(Context context) {
        prefs = context.getSharedPreferences(LIB_PREFS, Context.MODE_PRIVATE);
    }

    public synchronized JSONArray characters() {
        try { return new JSONArray(prefs.getString("characters", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    public synchronized JSONObject saveCharacter(String name, JSONObject state, int seed, String previewPath) {
        JSONArray arr = characters();
        JSONObject c = new JSONObject();
        try {
            c.put("id", "char_" + System.currentTimeMillis());
            c.put("name", cleanName(name));
            c.put("state", state == null ? new JSONObject() : new JSONObject(state.toString()));
            c.put("seed", seed);
            c.put("preview", previewPath == null ? "" : previewPath);
            c.put("time", System.currentTimeMillis());
            arr.put(c);
            while (arr.length() > 24) arr.remove(0);
            prefs.edit().putString("characters", arr.toString()).apply();
        } catch (Exception ignored) {}
        return c;
    }

    public synchronized void deleteCharacter(String id) {
        JSONArray src = characters();
        JSONArray out = new JSONArray();
        for (int i = 0; i < src.length(); i++) {
            JSONObject c = src.optJSONObject(i);
            if (c != null && !id.equals(c.optString("id"))) out.put(c);
        }
        prefs.edit().putString("characters", out.toString()).apply();
    }

    public void activateInChat(Context context, JSONObject character, boolean startFresh) {
        if (character == null) return;
        JSONObject state = character.optJSONObject("state");
        if (state == null) return;
        SharedPreferences companion = context.getSharedPreferences(COMPANION_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = companion.edit()
                .putString("ai_name", cleanName(character.optString("name", "하린")))
                .putString("state", state.toString())
                .putInt("anchor_seed", character.optInt("seed", 428731))
                .putInt("turns_since_image", 0)
                .remove("next_contact_at")
                .remove("next_story_at");
        if (startFresh) e.remove("messages").remove("stories");
        e.apply();

        if (startFresh) {
            CompanionStore store = new CompanionStore(context);
            store.addMessage("ai", "안녕 🙂 이제 나랑 여기서 얘기하자.", "");
        }
    }

    private String cleanName(String name) {
        if (name == null || name.trim().isEmpty()) return "하린";
        String s = name.trim();
        return s.length() > 24 ? s.substring(0, 24) : s;
    }
}
