package com.kow565.perchancecompanion;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class CharacterDesignerAi {
    private static final String TEXT_BASE = "https://text-generation.perchance.org/api";
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0 Mobile Safari/537.36";
    private static final Random RNG = new Random();

    static {
        if (!(CookieHandler.getDefault() instanceof CookieManager)) {
            CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        }
    }

    public static class CharacterPlan {
        public String name = "하린";
        public JSONObject state = new JSONObject();
        public String summary = "";
    }

    public CharacterPlan design(String requestedName, String description) throws Exception {
        String prompt = "Design one fictional adult companion character from the user's description. " +
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

        String raw = generateText(prompt);
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
            if (!identity.toLowerCase().contains("adult") && !identity.matches(".*\\b(1[89]|[2-9][0-9])-year-old.*")) {
                identity = "25-year-old adult woman named " + name + ", " + identity;
            }
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

    private String generateText(String prompt) throws Exception {
        String userKey = verifyKey();
        String requestId = "aiTextCompletion" + Math.abs(RNG.nextInt());
        URL u = new URL(TEXT_BASE + "/generate?userKey=" + enc(userKey) + "&requestId=" + requestId + "&__cacheBust=" + Math.random());
        JSONObject body = new JSONObject();
        body.put("generatorName", "ai-text-generator");
        body.put("instruction", prompt);
        body.put("instructionTokenCount", 1);
        body.put("startWith", "");
        body.put("startWithTokenCount", 1);
        body.put("stopSequences", new JSONArray());

        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(90000);
        c.setRequestMethod("POST");
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "text/event-stream, application/json, text/plain");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        c.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));

        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        if (is == null) throw new IllegalStateException("Perchance character HTTP " + code);
        StringBuilder output = new StringBuilder();
        StringBuilder raw = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                raw.append(line).append('\n');
                String trimmed = line.trim();
                try {
                    if (trimmed.startsWith("t:")) {
                        Object v = new JSONTokener(trimmed.substring(2)).nextValue();
                        if (v instanceof String) output.append((String) v);
                    } else if (trimmed.startsWith("data:")) {
                        String payload = trimmed.substring(5).trim();
                        if (payload.equals("[DONE]") || payload.isEmpty()) continue;
                        Object v = new JSONTokener(payload).nextValue();
                        if (v instanceof JSONObject) output.append(((JSONObject) v).optString("text", ""));
                        else if (v instanceof String) output.append((String) v);
                    }
                } catch (Exception ignored) {}
            }
        } finally { c.disconnect(); }
        if (code < 200 || code >= 300) throw new IllegalStateException("Perchance character HTTP " + code + ": " + raw);
        return output.length() > 0 ? output.toString() : raw.toString().trim();
    }

    private String verifyKey() throws Exception {
        URL u = new URL(TEXT_BASE + "/verifyUser?thread=0&__cacheBust=" + Math.random());
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestMethod("GET");
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "*/*");
        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        if (is == null) throw new IllegalStateException("Perchance verify HTTP " + code);
        StringBuilder b = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) b.append(line).append('\n');
        } finally { c.disconnect(); }
        String content = b.toString();
        String[] needles = {"\"userKey\":\"", "&quot;userKey&quot;:&quot;"};
        for (String n : needles) {
            int i = content.indexOf(n);
            if (i >= 0) {
                int start = i + n.length();
                int end = content.indexOf(n.contains("&quot;") ? "&quot;" : "\"", start);
                if (end > start) return content.substring(start, end);
            }
        }
        throw new IllegalStateException("Could not obtain Perchance userKey");
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

    private static String enc(String value) throws Exception {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
    }
}
