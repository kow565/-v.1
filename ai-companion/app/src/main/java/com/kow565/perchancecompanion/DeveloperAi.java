package com.kow565.perchancecompanion;

import org.json.JSONObject;

public class DeveloperAi {
    public static class EditResult {
        public JSONObject patch = new JSONObject();
        public String summary = "";
        public boolean requiresRebuild = false;
        public String rebuildRequest = "";
    }

    public EditResult suggestEdit(CompanionStore store, String userRequest) throws Exception {
        String prompt = "You are the built-in AI app editor for an Android multi-character AI DM app. " +
                "Convert requests that can be changed at runtime into a JSON configuration patch. " +
                "Runtime-editable keys and valid ranges are: contactMinMinutes 15..1440, contactMaxMinutes 15..2880, " +
                "storyMinMinutes 60..2880, storyMaxMinutes 60..10080, quietStartHour 0..23, quietEndHour 0..23, " +
                "imageEveryTurns 1..20, behaviorInstructions string, visualInstructions string. " +
                "behaviorInstructions can describe this character's tone, initiative, personality habits and story/chat behavior. " +
                "visualInstructions can describe preferred visual style while preserving the same adult character identity. " +
                "If the request needs native Android source changes, UI changes, new permissions/screens/providers, set requiresRebuild=true and write a precise Korean rebuildRequest. " +
                "Do not invent extra keys. Current character: " + store.aiName() + ". Current runtime config: " + store.runtimeConfig().toString() + "\n" +
                "User edit request: " + userRequest + "\n" +
                "Return exactly one JSON object: {\"patch\":{},\"summary\":\"short Korean explanation\",\"requiresRebuild\":false,\"rebuildRequest\":\"\"}.";

        String raw = new AiEngine().generateUtilityText(store, prompt);
        JSONObject obj = parseObject(raw);
        if (obj == null) throw new IllegalStateException("AI editor returned invalid JSON");
        EditResult r = new EditResult();
        JSONObject patch = obj.optJSONObject("patch");
        if (patch != null) r.patch = sanitizePatch(patch);
        r.summary = obj.optString("summary", "수정안을 만들었어.").trim();
        r.requiresRebuild = obj.optBoolean("requiresRebuild", false);
        r.rebuildRequest = obj.optString("rebuildRequest", "").trim();
        return r;
    }

    private JSONObject sanitizePatch(JSONObject input) {
        JSONObject out = new JSONObject();
        String[] ints = {"contactMinMinutes", "contactMaxMinutes", "storyMinMinutes", "storyMaxMinutes", "quietStartHour", "quietEndHour", "imageEveryTurns"};
        String[] strings = {"behaviorInstructions", "visualInstructions"};
        try {
            for (String k : ints) if (input.has(k) && !input.isNull(k)) out.put(k, input.optInt(k));
            for (String k : strings) if (input.has(k) && !input.isNull(k)) out.put(k, input.optString(k, ""));
        } catch (Exception ignored) {}
        return out;
    }

    private JSONObject parseObject(String raw) {
        if (raw == null) return null;
        int first = raw.indexOf('{');
        int last = raw.lastIndexOf('}');
        if (first < 0 || last <= first) return null;
        try { return new JSONObject(raw.substring(first, last + 1)); }
        catch (Exception ignored) { return null; }
    }
}
