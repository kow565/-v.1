package com.kow565.perchancecompanion;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public class CharacterLibrary {
    private static final String LIB_PREFS = "harin_character_library_v1";
    private final Context context;
    private final SharedPreferences prefs;

    public CharacterLibrary(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(LIB_PREFS, Context.MODE_PRIVATE);
        ensureBuiltIns();
    }

    public synchronized JSONArray characters() {
        try { return new JSONArray(prefs.getString("characters", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private synchronized void ensureBuiltIns() {
        JSONArray arr;
        try { arr = new JSONArray(prefs.getString("characters", "[]")); }
        catch (Exception e) { arr = new JSONArray(); }
        boolean changed = false;
        if (!containsId(arr, "harin_default")) { arr.put(builtInHarin()); changed = true; }
        if (!containsId(arr, "mina_default")) { arr.put(builtInMina()); changed = true; }
        if (!containsId(arr, "sora_default")) { arr.put(builtInSora()); changed = true; }
        if (changed) prefs.edit().putString("characters", arr.toString()).apply();
    }

    private boolean containsId(JSONArray arr, String id) {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c != null && id.equals(c.optString("id"))) return true;
        }
        return false;
    }

    private JSONObject builtInHarin() {
        JSONObject c = new JSONObject();
        try {
            c.put("id", "harin_default"); c.put("name", "하린"); c.put("seed", 428731); c.put("preview", ""); c.put("time", 1L); c.put("builtin", true);
            c.put("state", CompanionStore.defaultState());
        } catch (Exception ignored) {}
        return c;
    }

    private JSONObject builtInMina() {
        JSONObject s = new JSONObject(); JSONObject c = new JSONObject();
        try {
            s.put("identity", "25-year-old Korean woman named Mina, soft round face, dark brown eyes, long wavy black hair, clear skin, athletic slim build");
            s.put("hair", "long wavy black hair with wispy bangs");
            s.put("outfit", "navy zip hoodie, gray fitted top, black wide-leg pants");
            s.put("pose", "easygoing confident posture");
            s.put("location", "small cafe near a university district in Seoul");
            s.put("mood", "bright, teasing, energetic");
            s.put("accessories", "small hoop earrings and a simple smartwatch");
            s.put("lighting", "soft daylight from a cafe window");
            c.put("id", "mina_default"); c.put("name", "미나"); c.put("seed", 781245); c.put("preview", ""); c.put("time", 2L); c.put("builtin", true); c.put("state", s);
        } catch (Exception ignored) {}
        return c;
    }

    private JSONObject builtInSora() {
        JSONObject s = new JSONObject(); JSONObject c = new JSONObject();
        try {
            s.put("identity", "26-year-old Korean woman named Sora, defined oval face, warm hazel-brown eyes, medium-length chestnut hair, natural makeup, elegant slender build");
            s.put("hair", "medium-length chestnut hair in a soft layered cut");
            s.put("outfit", "charcoal cardigan, ivory blouse, dark straight jeans");
            s.put("pose", "calm composed posture");
            s.put("location", "quiet modern apartment living room in Seoul");
            s.put("mood", "calm, witty, thoughtful");
            s.put("accessories", "thin silver necklace and small stud earrings");
            s.put("lighting", "warm evening apartment light");
            c.put("id", "sora_default"); c.put("name", "소라"); c.put("seed", 613902); c.put("preview", ""); c.put("time", 3L); c.put("builtin", true); c.put("state", s);
        } catch (Exception ignored) {}
        return c;
    }

    public synchronized JSONObject saveCharacter(String name, JSONObject state, int seed, String previewPath) {
        JSONArray arr = characters();
        JSONObject c = new JSONObject();
        try {
            c.put("id", "char_" + System.currentTimeMillis() + "_" + Math.abs(seed % 10000));
            c.put("name", cleanName(name));
            c.put("state", state == null ? new JSONObject() : new JSONObject(state.toString()));
            c.put("seed", seed);
            c.put("preview", previewPath == null ? "" : previewPath);
            c.put("time", System.currentTimeMillis());
            c.put("builtin", false);
            arr.put(c);
            while (arr.length() > 60) {
                int remove = firstRemovable(arr);
                if (remove < 0) break;
                arr.remove(remove);
            }
            prefs.edit().putString("characters", arr.toString()).apply();
        } catch (Exception ignored) {}
        return c;
    }

    private int firstRemovable(JSONArray arr) {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c != null && !c.optBoolean("builtin", false)) return i;
        }
        return -1;
    }

    public synchronized void deleteCharacter(String id) {
        JSONArray src = characters();
        JSONArray out = new JSONArray();
        for (int i = 0; i < src.length(); i++) {
            JSONObject c = src.optJSONObject(i);
            if (c == null) continue;
            if (id.equals(c.optString("id")) && c.optBoolean("builtin", false)) { out.put(c); continue; }
            if (!id.equals(c.optString("id"))) out.put(c);
        }
        prefs.edit().putString("characters", out.toString()).apply();
    }

    public synchronized JSONObject find(String id) {
        JSONArray arr = characters();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c != null && id.equals(c.optString("id"))) return c;
        }
        return null;
    }

    public void activateInChat(Context ignoredContext, JSONObject character, boolean startFresh) {
        if (character == null) return;
        JSONObject state = character.optJSONObject("state");
        if (state == null) return;
        String id = character.optString("id", "char_" + System.currentTimeMillis());
        CompanionStore.selectProfile(context, id);
        CompanionStore store = new CompanionStore(context, id);
        boolean firstOpen = store.messages().length() == 0;
        store.initializeCharacter(cleanName(character.optString("name", "하린")), state, character.optInt("seed", 428731), startFresh);
        if (startFresh || firstOpen) store.addMessage("ai", greeting(character.optString("name", "하린")), "");
    }

    private String greeting(String name) {
        String n = cleanName(name);
        if ("미나".equals(n)) return "어, 왔네? 😏 뭐 하고 있었어?";
        if ("소라".equals(n)) return "왔어? 오늘은 좀 어땠어 🙂";
        return "자기 왔어? 나 방금 좀 쉬고 있었어 🙂";
    }

    private String cleanName(String name) {
        if (name == null || name.trim().isEmpty()) return "하린";
        String s = name.trim();
        return s.length() > 24 ? s.substring(0, 24) : s;
    }
}
