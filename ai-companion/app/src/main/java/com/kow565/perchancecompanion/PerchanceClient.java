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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public final class PerchanceClient {
    private static final String TEXT_BASE = "https://text-generation.perchance.org/api";
    private static final String IMAGE_BASE = "https://image-generation.perchance.org/api";
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0 Mobile Safari/537.36";
    private static final Random RNG = new Random();

    private PerchanceClient() {}

    public static String generateText(Context context, String prompt) throws Exception {
        String key = obtainKey(context, "text", TEXT_BASE);
        String requestId = "aiTextCompletion" + Math.abs(RNG.nextInt());
        URL u = new URL(TEXT_BASE + "/generate?userKey=" + enc(key) + "&requestId=" + requestId + "&__cacheBust=" + Math.random());
        JSONObject body = new JSONObject();
        body.put("generatorName", "ai-text-generator");
        body.put("instruction", prompt);
        body.put("instructionTokenCount", 1);
        body.put("startWith", "");
        body.put("startWithTokenCount", 1);
        body.put("stopSequences", new JSONArray());

        HttpURLConnection c = open(u, "POST", context, "text");
        c.setRequestProperty("Accept", "text/event-stream, application/json, text/plain");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        c.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));

        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        if (is == null) throw errorFor(code, "text", "empty response");
        StringBuilder output = new StringBuilder();
        StringBuilder raw = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                raw.append(line).append('\n');
                String t = line.trim();
                try {
                    if (t.startsWith("t:")) {
                        Object v = new JSONTokener(t.substring(2)).nextValue();
                        if (v instanceof String) output.append((String) v);
                    } else if (t.startsWith("data:")) {
                        String payload = t.substring(5).trim();
                        if (payload.isEmpty() || "[DONE]".equals(payload)) continue;
                        Object v = new JSONTokener(payload).nextValue();
                        if (v instanceof JSONObject) output.append(((JSONObject) v).optString("text", ""));
                        else if (v instanceof String) output.append((String) v);
                    }
                } catch (Exception ignored) {}
            }
        } finally { c.disconnect(); }
        if (code < 200 || code >= 300) throw errorFor(code, "text", raw.toString());
        if (output.length() > 0) return output.toString();
        String r = raw.toString().trim();
        try {
            JSONObject o = new JSONObject(r);
            if (o.has("text")) return o.optString("text", "");
        } catch (Exception ignored) {}
        return r;
    }

    public static String generateImage(Context context, String prompt, String negativePrompt, int seed, String folder, String prefix) throws Exception {
        String key = obtainKey(context, "image", IMAGE_BASE);
        String requestId = "aiImageCompletion" + Math.abs(RNG.nextInt());
        URL u = new URL(IMAGE_BASE + "/generate?userKey=" + enc(key) + "&requestId=" + requestId + "&__cacheBust=" + Math.random());
        JSONObject body = new JSONObject();
        body.put("generatorName", "ai-image-generator");
        body.put("channel", "ai-text-to-image-generator");
        body.put("subChannel", "public");
        body.put("prompt", prompt);
        body.put("negativePrompt", negativePrompt);
        body.put("seed", seed);
        body.put("resolution", "512x768");
        body.put("guidanceScale", 7.0);

        HttpURLConnection c = open(u, "POST", context, "image");
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        c.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
        int code = c.getResponseCode();
        String response = readResponse(c, code);
        c.disconnect();
        if (code < 200 || code >= 300) throw errorFor(code, "image", response);

        JSONObject result = new JSONObject(response);
        if (result.has("status") && !result.optString("status").isEmpty() && !result.has("imageId"))
            throw new IllegalStateException("Perchance image error: " + result.optString("status"));
        String imageId = result.optString("imageId", "");
        if (imageId.isEmpty()) throw new IllegalStateException("Perchance imageId missing");
        String proxy = findProxy(result);
        String downloadUrl = proxy == null || proxy.isEmpty()
                ? IMAGE_BASE + "/downloadTemporaryImage?imageId=" + enc(imageId)
                : (proxy.startsWith("http") ? proxy : IMAGE_BASE + (proxy.startsWith("/") ? proxy : "/" + proxy));
        byte[] bytes;
        try { bytes = requestBytes(context, new URL(downloadUrl), "image"); }
        catch (Exception first) { bytes = requestBytes(context, new URL(IMAGE_BASE + "/downloadTemporaryImage?imageId=" + enc(imageId)), "image"); }

        File dir = new File(context.getFilesDir(), folder);
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create image directory");
        File out = new File(dir, prefix + "_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(bytes); }
        return out.getAbsolutePath();
    }

    private static String obtainKey(Context context, String kind, String base) throws Exception {
        String cached = PerchanceSession.key(context, kind);
        if (!cached.isEmpty()) return cached;
        try {
            URL u = new URL(base + "/verifyUser?thread=0&__cacheBust=" + Math.random());
            HttpURLConnection c = open(u, "GET", context, kind);
            c.setRequestProperty("Accept", "*/*");
            int code = c.getResponseCode();
            String content = readResponse(c, code);
            c.disconnect();
            if (code >= 200 && code < 300) {
                String key = PerchanceSession.parseUserKey(content);
                if (!key.isEmpty()) {
                    PerchanceSession.save(context, kind, key, PerchanceSession.cookie(context, kind));
                    return key;
                }
            }
        } catch (Exception ignored) {}
        throw new IllegalStateException("PERCHANCE_CONNECT_REQUIRED: Perchance 브라우저 세션 연결이 필요해");
    }

    private static HttpURLConnection open(URL u, String method, Context context, String kind) throws Exception {
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(90000);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Referer", "https://perchance.org/");
        c.setRequestProperty("Origin", "https://perchance.org");
        String cookie = PerchanceSession.cookie(context, kind);
        if (cookie != null && !cookie.trim().isEmpty()) c.setRequestProperty("Cookie", cookie);
        return c;
    }

    private static String readResponse(HttpURLConnection c, int code) throws Exception {
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        if (is == null) return "";
        StringBuilder b = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) b.append(line).append('\n');
        }
        return b.toString().trim();
    }

    private static byte[] requestBytes(Context context, URL u, String kind) throws Exception {
        HttpURLConnection c = open(u, "GET", context, kind);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) { c.disconnect(); throw errorFor(code, kind, "download failed"); }
        try (BufferedInputStream in = new BufferedInputStream(c.getInputStream()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        } finally { c.disconnect(); }
    }

    private static IllegalStateException errorFor(int code, String kind, String detail) {
        String d = detail == null ? "" : detail;
        if (code == 401 || code == 403 || d.contains("userKey") || d.contains("verifyUser"))
            return new IllegalStateException("PERCHANCE_CONNECT_REQUIRED: Perchance " + kind + " 세션을 다시 연결해줘");
        if (code == 429 || d.contains("too_many_requests"))
            return new IllegalStateException("Perchance 요청 제한에 도달했어. 잠시 후 다시 시도해줘.");
        return new IllegalStateException("Perchance " + kind + " HTTP " + code + (d.isEmpty() ? "" : ": " + trim(d, 180)));
    }

    private static String findProxy(Object value) {
        if (value instanceof String) {
            String s = (String) value;
            if (s.contains("downloadTemporaryImageViaProxy")) return s;
            if (s.startsWith("v1.") && s.length() > 80) return "/downloadTemporaryImageViaProxy?t=" + s;
        } else if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            java.util.Iterator<String> keys = o.keys();
            while (keys.hasNext()) { String p = findProxy(o.opt(keys.next())); if (p != null) return p; }
        } else if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            for (int i = 0; i < a.length(); i++) { String p = findProxy(a.opt(i)); if (p != null) return p; }
        }
        return null;
    }

    private static String trim(String s, int max) { return s.length() <= max ? s : s.substring(0, max); }
    private static String enc(String value) throws Exception { return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.toString()); }
}
