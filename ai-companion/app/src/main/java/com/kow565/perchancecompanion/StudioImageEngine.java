package com.kow565.perchancecompanion;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

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
import java.util.Random;

public class StudioImageEngine {
    private static final String IMAGE_BASE = "https://image-generation.perchance.org/api";
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0 Mobile Safari/537.36";
    private static final Random RNG = new Random();

    static {
        if (!(CookieHandler.getDefault() instanceof CookieManager)) {
            CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        }
    }

    public String generateFree(Context context, String prompt) throws Exception {
        int seed = 100000 + RNG.nextInt(800000000);
        return generate(context, prompt, seed, "free");
    }

    public String generateForActiveCharacter(Context context, CompanionStore store, String scenePrompt) throws Exception {
        String prompt = store.visualStatePrompt() + ". Scene requested by user: " + scenePrompt +
                ". Keep the exact same adult character identity and facial features. This image is independent from the chat timeline and must not alter conversation state.";
        return generate(context, prompt, store.anchorSeed(), "active");
    }

    public String generateCharacterPreview(Context context, JSONObject state, int seed) throws Exception {
        String prompt = statePrompt(state) +
                ". Clean character reference portrait, same single adult person, natural realistic photography, clear face, coherent anatomy, neutral composition suitable as an identity anchor.";
        return generate(context, prompt, seed, "character");
    }

    private String statePrompt(JSONObject s) {
        if (s == null) s = new JSONObject();
        return "IDENTITY: " + s.optString("identity") + ". HAIR: " + s.optString("hair") +
                ". OUTFIT: " + s.optString("outfit") + ". ACCESSORIES: " + s.optString("accessories") +
                ". POSE: " + s.optString("pose") + ". LOCATION: " + s.optString("location") +
                ". MOOD: " + s.optString("mood") + ". LIGHTING: " + s.optString("lighting");
    }

    private String generate(Context context, String prompt, int seed, String prefix) throws Exception {
        String userKey = verifyKey();
        String requestId = "aiImageCompletion" + Math.abs(RNG.nextInt());
        URL u = new URL(IMAGE_BASE + "/generate?userKey=" + enc(userKey) + "&requestId=" + requestId + "&__cacheBust=" + Math.random());
        JSONObject body = new JSONObject();
        body.put("generatorName", "ai-image-generator");
        body.put("channel", "ai-text-to-image-generator");
        body.put("subChannel", "public");
        body.put("prompt", prompt);
        body.put("negativePrompt", "different identity when identity is specified, child, underage, duplicate person, malformed anatomy, extra fingers, extra limbs, text, watermark, logo, low quality");
        body.put("seed", seed);
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

        File dir = new File(context.getFilesDir(), "studio");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create studio directory");
        File out = new File(dir, prefix + "_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(bytes); }
        return out.getAbsolutePath();
    }

    private String verifyKey() throws Exception {
        URL u = new URL(IMAGE_BASE + "/verifyUser?thread=0&__cacheBust=" + Math.random());
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
