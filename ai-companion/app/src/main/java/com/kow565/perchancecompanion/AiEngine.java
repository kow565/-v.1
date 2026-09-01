package com.kow565.perchancecompanion;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;

public class AiEngine {
    private static final String TEXT_BASE = "https://text-generation.perchance.org/api";
    private static final String IMAGE_BASE = "https://image-generation.perchance.org/api";
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0 Mobile Safari/537.36";
    private static final Random RNG = new Random();

    static {
        if (!(CookieHandler.getDefault() instanceof CookieManager)) {
            CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        }
    }

    public static class Turn {
        public String reply = "";
        public JSONObject state = new JSONObject();
        public boolean imageMoment = false;
        public String imagePrompt = "";
    }

    public static class StoryTurn {
        public String caption = "";
        public JSONObject state = new JSONObject();
        public String imagePrompt = "";
    }

    public Turn chatTurn(CompanionStore store, String latestUserMessage, boolean proactive) throws Exception {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd E HH:mm", Locale.KOREAN));
        String prompt = "You are " + store.aiName() + ", a fictional 25-year-old adult Korean girlfriend chatting privately with " + store.userName() + ". " +
                "Write natural Korean like a real Kakao/Instagram DM: usually 1-4 short sentences, warm and spontaneous, never mention being an AI. " +
                "You have your own day, moods and small activities. Do not be clingy or manipulative. " +
                "Current local time: " + now + ".\n" +
                "Persistent visual state:\n" + store.visualStatePrompt() + "\n" +
                "Recent conversation:\n" + store.recentTranscript(14) + "\n" +
                (proactive ? "You decided to message first. Start a fresh, believable check-in based on the time and your current state.\n" :
                        "Latest user message: " + latestUserMessage + "\n") +
                "Return exactly ONE JSON object and nothing else. Schema: " +
                "{\"reply\":\"Korean DM\",\"state\":{\"outfit\":null,\"pose\":null,\"location\":null,\"mood\":null,\"hair\":null,\"accessories\":null,\"lighting\":null}," +
                "\"imageMoment\":true,\"imagePrompt\":\"brief English scene description\"}. " +
                "For state fields use null when unchanged. Only change clothing/location/pose when the conversation clearly implies it or you naturally moved. " +
                "Set imageMoment true when a selfie/photo would feel natural; target roughly one image every few turns, not every message. " +
                "Never alter identity/age/face. All depicted people are adults.";

        String raw = generateText(prompt);
        JSONObject obj = parseObject(raw);
        Turn t = new Turn();
        if (obj != null) {
            t.reply = obj.optString("reply", "").trim();
            t.state = obj.optJSONObject("state") == null ? new JSONObject() : obj.optJSONObject("state");
            t.imageMoment = obj.optBoolean("imageMoment", false);
            t.imagePrompt = obj.optString("imagePrompt", "").trim();
        } else {
            t.reply = cleanRaw(raw);
        }
        if (t.reply.isEmpty()) t.reply = proactive ? "자기 뭐 하고 있어? 갑자기 생각나서 연락했어 🙂" : "응, 나 듣고 있어. 조금만 더 말해줘 🙂";
        return t;
    }

    public StoryTurn storyTurn(CompanionStore store) throws Exception {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd E HH:mm", Locale.KOREAN));
        String prompt = "You are " + store.aiName() + ", a fictional 25-year-old adult Korean woman with an Instagram-like private story feed. " +
                "Current time: " + now + ". Persistent state: " + store.visualStatePrompt() + ". " +
                "Recent chat context:\n" + store.recentTranscript(8) + "\n" +
                "Invent one believable thing you are doing now and post it as a casual story. Keep continuity with outfit/location unless you naturally moved. " +
                "Return exactly JSON: {\"caption\":\"very short Korean story caption\",\"state\":{\"outfit\":null,\"pose\":null,\"location\":null,\"mood\":null,\"hair\":null,\"accessories\":null,\"lighting\":null},\"imagePrompt\":\"English visual description of the story photo\"}. " +
                "Never change identity or age. All depicted people are adults.";
        String raw = generateText(prompt);
        JSONObject obj = parseObject(raw);
        StoryTurn st = new StoryTurn();
        if (obj != null) {
            st.caption = obj.optString("caption", "").trim();
            st.state = obj.optJSONObject("state") == null ? new JSONObject() : obj.optJSONObject("state");
            st.imagePrompt = obj.optString("imagePrompt", "").trim();
        }
        if (st.caption.isEmpty()) st.caption = "오늘도 그냥 소소하게 ☕";
        if (st.imagePrompt.isEmpty()) st.imagePrompt = "casual candid smartphone photo during an ordinary day";
        return st;
    }

    public String generateImage(Context context, CompanionStore store, String scenePrompt) throws Exception {
        String userKey = verifyKey(IMAGE_BASE);
        String requestId = "aiImageCompletion" + Math.abs(RNG.nextInt());
        URL u = new URL(IMAGE_BASE + "/generate?userKey=" + enc(userKey) + "&requestId=" + requestId + "&__cacheBust=" + Math.random());
        JSONObject body = new JSONObject();
        body.put("generatorName", "ai-image-generator");
        body.put("channel", "ai-text-to-image-generator");
        body.put("subChannel", "public");
        body.put("prompt", store.visualStatePrompt() + " Scene: " + scenePrompt + ". same adult woman, consistent facial identity, consistent clothing unless state says otherwise, realistic candid smartphone photography, natural skin texture, highly coherent anatomy");
        body.put("negativePrompt", "different person, changed face, child, underage, duplicate person, deformed hands, extra fingers, extra limbs, text, watermark, logo, low quality");
        body.put("seed", store.anchorSeed());
        body.put("resolution", "512x768");
        body.put("guidanceScale", 7.0);

        String response = requestText(u, "POST", body.toString(), "application/json");
        JSONObject result = new JSONObject(response);
        if (result.has("status") && !result.optString("status").isEmpty() && !result.has("imageId")) {
            throw new IllegalStateException("Perchance image error: " + result.optString("status"));
        }
        String imageId = result.optString("imageId", "");
        if (imageId.isEmpty()) throw new IllegalStateException("No imageId: " + response);

        String proxy = findProxy(result);
        String downloadUrl = proxy == null || proxy.isEmpty()
                ? IMAGE_BASE + "/downloadTemporaryImage?imageId=" + enc(imageId)
                : (proxy.startsWith("http") ? proxy : IMAGE_BASE + (proxy.startsWith("/") ? proxy : "/" + proxy));

        byte[] bytes;
        try { bytes = requestBytes(new URL(downloadUrl)); }
        catch (Exception first) { bytes = requestBytes(new URL(IMAGE_BASE + "/downloadTemporaryImage?imageId=" + enc(imageId))); }

        File dir = new File(context.getFilesDir(), "generated");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create image directory");
        File out = new File(dir, "harin_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(bytes); }
        return out.getAbsolutePath();
    }

    private String generateText(String prompt) throws Exception {
        String userKey = verifyKey(TEXT_BASE);
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
        if (is == null) throw new IllegalStateException("Perchance text HTTP " + code);
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
        if (code < 200 || code >= 300) throw new IllegalStateException("Perchance text HTTP " + code + ": " + raw);
        if (output.length() > 0) return output.toString();
        String r = raw.toString().trim();
        try {
            JSONObject o = new JSONObject(r);
            if (o.has("text")) return o.optString("text");
        } catch (Exception ignored) {}
        return r;
    }

    private String verifyKey(String base) throws Exception {
        URL u = new URL(base + "/verifyUser?thread=0&__cacheBust=" + Math.random());
        String content = requestText(u, "GET", null, null);
        String[] needles = {"\"userKey\":\"", "&quot;userKey&quot;:&quot;"};
        for (String n : needles) {
            int i = content.indexOf(n);
            if (i >= 0) {
                int start = i + n.length();
                int end = content.indexOf(n.contains("&quot;") ? "&quot;" : "\"", start);
                if (end > start) return content.substring(start, end);
            }
        }
        if (content.contains("too_many_requests")) throw new IllegalStateException("Perchance rate limit reached");
        throw new IllegalStateException("Could not obtain Perchance userKey");
    }

    private String requestText(URL u, String method, String body, String contentType) throws Exception {
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(90000);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "*/*");
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", contentType == null ? "application/json" : contentType);
            c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        if (is == null) throw new IllegalStateException("HTTP " + code);
        StringBuilder b = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) b.append(line).append('\n');
        } finally { c.disconnect(); }
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + ": " + b);
        return b.toString().trim();
    }

    private byte[] requestBytes(URL u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(90000);
        c.setRequestProperty("User-Agent", UA);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("Image download HTTP " + code);
        try (BufferedInputStream in = new BufferedInputStream(c.getInputStream()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        } finally { c.disconnect(); }
    }

    private JSONObject parseObject(String raw) {
        if (raw == null) return null;
        int first = raw.indexOf('{');
        int last = raw.lastIndexOf('}');
        if (first < 0 || last <= first) return null;
        try { return new JSONObject(raw.substring(first, last + 1)); }
        catch (Exception ignored) { return null; }
    }

    private String cleanRaw(String raw) {
        if (raw == null) return "";
        return raw.replace("```json", "").replace("```", "").trim();
    }

    private String findProxy(Object value) {
        if (value instanceof String) {
            String s = (String) value;
            if (s.contains("downloadTemporaryImageViaProxy")) return s;
            if (s.startsWith("v1.") && s.length() > 80) return "/downloadTemporaryImageViaProxy?t=" + s;
        } else if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            java.util.Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String p = findProxy(o.opt(keys.next()));
                if (p != null) return p;
            }
        } else if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            for (int i = 0; i < a.length(); i++) {
                String p = findProxy(a.opt(i));
                if (p != null) return p;
            }
        }
        return null;
    }

    private static String enc(String value) throws Exception {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
    }
}
