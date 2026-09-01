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

public class DeveloperAi {
    private static final String TEXT_BASE = "https://text-generation.perchance.org/api";
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0 Mobile Safari/537.36";
    private static final Random RNG = new Random();

    static {
        if (!(CookieHandler.getDefault() instanceof CookieManager)) {
            CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        }
    }

    public static class EditResult {
        public JSONObject patch = new JSONObject();
        public String summary = "";
        public boolean requiresRebuild = false;
        public String rebuildRequest = "";
    }

    public EditResult suggestEdit(CompanionStore store, String userRequest) throws Exception {
        String prompt = "You are the built-in AI app editor for an Android AI companion app. " +
                "The user describes how they want the app to behave. Convert requests that can be changed at runtime into a safe JSON configuration patch. " +
                "Runtime-editable keys and valid ranges are: contactMinMinutes 15..1440, contactMaxMinutes 15..2880, " +
                "storyMinMinutes 60..2880, storyMaxMinutes 60..10080, quietStartHour 0..23, quietEndHour 0..23, " +
                "imageEveryTurns 1..20, behaviorInstructions string, visualInstructions string. " +
                "behaviorInstructions can describe tone, initiative, personality habits and story/chat behavior. visualInstructions can describe preferred visual style while preserving the same adult character identity. " +
                "If the request needs native Android source changes, UI layout changes, new permissions, new screens, a new provider, or anything outside those keys, set requiresRebuild=true and write a precise Korean rebuildRequest for the next APK version. " +
                "Do not invent extra keys. Preserve values that were not requested by simply omitting them from patch. " +
                "Current runtime config: " + store.runtimeConfig().toString() + "\n" +
                "User edit request: " + userRequest + "\n" +
                "Return exactly one JSON object and nothing else with schema: " +
                "{\"patch\":{},\"summary\":\"short Korean explanation\",\"requiresRebuild\":false,\"rebuildRequest\":\"\"}.";

        String raw = generateText(prompt);
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
        if (is == null) throw new IllegalStateException("Perchance editor HTTP " + code);
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
        if (code < 200 || code >= 300) throw new IllegalStateException("Perchance editor HTTP " + code + ": " + raw);
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
            String line;
            while ((line = br.readLine()) != null) b.append(line).append('\n');
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

    private static String enc(String value) throws Exception {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
    }
}
