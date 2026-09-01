package com.kow565.perchancecompanion;

import android.content.Context;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CharacterDesignerAi {
    public static class CharacterPlan {
        public String name = "하린";
        public JSONObject state = new JSONObject();
        public String summary = "";
    }

    public CharacterPlan design(Context context, String requestedName, String description) throws Exception {
        String prompt = "Design one fictional adult DM companion character from the user's description. " +
                "The character must be clearly 18 or older; use age 25 by default when age is not specified. " +
                "Create a stable visual identity suitable for repeated image generation. Do not describe the character as a real person or celebrity. " +
                "Requested name: " + requestedName + "\nDescription: " + description + "\n" +
                "Return exactly one JSON object with this schema: " +
                "{\"name\":\"short name\",\"summary\":\"short Korean summary\",\"state\":{" +
                "\"identity\":\"English fixed identity with adult age, ethnicity/nationality if requested, face, eyes, skin, body build\"," +
                "\"hair\":\"English stable hair description\",\"outfit\":\"English default outfit\"," +
                "\"pose\":\"English neutral pose\",\"location\":\"English neutral default location\"," +
                "\"mood\":\"English personality/mood\",\"accessories\":\"English accessories\",\"lighting\":\"English lighting\"}}. " +
                "Keep identity concise but specific enough that the same face can be prompted repeatedly.";

        String raw = PerchanceClient.generateText(context, prompt);
        JSONObject obj = parseObject(raw);
        if (obj == null) throw new IllegalStateException("Character designer returned invalid JSON");
        CharacterPlan plan = new CharacterPlan();
        plan.name = cleanName(obj.optString("name", requestedName));
        plan.summary = obj.optString("summary", "캐릭터를 만들었어.").trim();
        JSONObject state = obj.optJSONObject("state");
        if (state == null) state = new JSONObject();
        plan.state = sanitizeState(state, plan.name);
        return plan;
    }

    private JSONObject sanitizeState(JSONObject input, String name) {
        JSONObject out = new JSONObject();
        try {
            String identity = input.optString("identity", "25-year-old adult woman named " + name + ", natural face, realistic proportions").trim();
            Matcher m = Pattern.compile("\\b(\\d{1,2})[- ]year[- ]old\\b", Pattern.CASE_INSENSITIVE).matcher(identity);
            if (m.find()) {
                try {
                    int age = Integer.parseInt(m.group(1));
                    if (age < 18) identity = m.replaceFirst("25-year-old adult");
                } catch (Exception ignored) {}
            }
            String lower = identity.toLowerCase();
            if (!lower.contains("adult") && !identity.matches(".*\\b(1[89]|[2-9][0-9])[- ]year[- ]old.*"))
                identity = "25-year-old adult person named " + name + ", " + identity;
            out.put("identity", limit(identity, 500));
            out.put("hair", limit(input.optString("hair", "natural shoulder-length dark hair"), 240));
            out.put("outfit", limit(input.optString("outfit", "casual everyday outfit"), 300));
            out.put("pose", limit(input.optString("pose", "relaxed natural posture"), 200));
            out.put("location", limit(input.optString("location", "cozy modern apartment"), 240));
            out.put("mood", limit(input.optString("mood", "warm and relaxed"), 200));
            out.put("accessories", limit(input.optString("accessories", "minimal accessories"), 200));
            out.put("lighting", limit(input.optString("lighting", "soft realistic natural light"), 200));
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

    private String cleanName(String name) {
        String s = name == null ? "" : name.trim();
        if (s.isEmpty()) s = "하린";
        return s.length() > 24 ? s.substring(0, 24) : s;
    }

    private String limit(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        return s.length() <= max ? s : s.substring(0, max);
    }
}
