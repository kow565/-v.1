package com.kow565.perchancecompanion;

import android.content.Context;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.WebSettings;

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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;

/** Perchance provider facade. Foreground calls prefer Perchance's imported browser plugins. */
public final class PerchanceClient {
    private static final String TEXT_BASE = "https://text-generation.perchance.org/api";
    private static final String IMAGE_BASE = "https://image-generation.perchance.org/api";
    private static final Random RNG = new Random();

    private PerchanceClient() {}

    public static String generateText(Context context, String prompt) throws Exception {
        if (!PerchanceBrowserTransport.isAvailable())
            throw new IllegalStateException("Perchance 공식 플러그인을 실행할 앱 화면이 필요해");
        return PerchanceBrowserTransport.generateText(prompt);
    }

    public static String generateImage(Context context, String prompt, String negativePrompt,
                                       int seed, String folder, String prefix) throws Exception {
        if (!PerchanceBrowserTransport.isAvailable())
            throw new IllegalStateException("Perchance 공식 플러그인을 실행할 앱 화면이 필요해");
        String dataUrl = PerchanceBrowserTransport.generateImageDataUrl(prompt, negativePrompt, seed);
        return saveDataUrl(context, dataUrl, folder, prefix);
    }

    public static String savePluginImageForTest(Context context, String dataUrl) throws Exception {
        return saveDataUrl(context, dataUrl, "diagnostics", "perchance_test");
    }

    private static String saveDataUrl(Context context, String dataUrl, String folder, String prefix) throws Exception {
        if (dataUrl == null || !dataUrl.startsWith("data:image/"))
            throw new IllegalStateException("Perchance image plugin did not return an image data URL");
        int comma = dataUrl.indexOf(',');
        if (comma < 0) throw new IllegalStateException("Perchance image data URL is malformed");
        String header = dataUrl.substring(0, comma);
        String payload = dataUrl.substring(comma + 1);
        byte[] bytes;
        if (header.contains(";base64")) {
            try { bytes = Base64.decode(payload, Base64.DEFAULT); }
            catch (Exception e) { throw new IllegalStateException("Perchance image base64 decode failed", e); }
        } else {
            bytes = java.net.URLDecoder.decode(payload, StandardCharsets.UTF_8.toString())
                    .getBytes(StandardCharsets.ISO_8859_1);
        }
        if (bytes.length < 1024) throw new IllegalStateException("Perchance image plugin returned too little image data");

        String mime = header.substring("data:image/".length()).split("[;,+]", 2)[0].toLowerCase(Locale.ROOT);
        String ext = mime.contains("png") ? "png" : mime.contains("webp") ? "webp" : "jpg";
        File dir = new File(context.getFilesDir(), folder);
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create image directory");
        String safePrefix = (prefix == null || prefix.trim().isEmpty() ? "image" : prefix).replaceAll("[^A-Za-z0-9_-]", "_");
        File out = new File(dir, safePrefix + "_" + System.currentTimeMillis() + "." + ext);
        try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(bytes); }
        return out.getAbsolutePath();
    }

    private static String generateTextDirect(Context context, String prompt) throws Exception {
        String key = obtainTextKey(context);
        String requestId = "harinText" + Math.abs(RNG.nextInt());
        URL u = new URL(TEXT_BASE + "/generate?userKey=" + enc(key) + "&requestId=" + enc(requestId) + "&__cacheBust=" + Math.random());
        JSONObject body = new JSONObject();
        body.put("generatorName", "ai-text-generator");
        body.put("instruction", prompt);
        body.put("instructionTokenCount", 1);
        body.put("startWith", "");
        body.put("startWithTokenCount", 1);
        body.put("stopSequences", new JSONArray());

        HttpURLConnection c = open(u, "POST", context, "text", "https://text-generation.perchance.org/embed");
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
                        String p = t.substring(5).trim();
                        if (p.isEmpty() || "[DONE]".equals(p)) continue;
                        Object v = new JSONTokener(p).nextValue();
                        if (v instanceof JSONObject) output.append(((JSONObject) v).optString("text", ""));
                        else if (v instanceof String) output.append((String) v);
                    }
                } catch (Exception ignored) {}
            }
        } finally { c.disconnect(); }
        if (code < 200 || code >= 300) throw errorFor(code, "text", raw.toString());
        if (output.length() > 0) return output.toString();
        String r = raw.toString().trim();
        if (r.isEmpty()) throw new IllegalStateException("Perchance direct text stream was empty");
        return r;
    }

    private static String generateImageDirect(Context context, String prompt, String negativePrompt,
                                              int seed, String folder, String prefix) throws Exception {
        String key = obtainImageKey(context);
        String q = IMAGE_BASE + "/generate" +
                "?prompt=" + enc(prompt) +
                "&negativePrompt=" + enc(negativePrompt == null ? "" : negativePrompt) +
                "&userKey=" + enc(key) +
                "&__cache_bust=" + Math.random() +
                "&seed=" + seed +
                "&resolution=512x768&guidanceScale=7" +
                "&channel=ai-text-to-image-generators&subChannel=public" +
                "&requestId=" + enc("harinImage" + Math.abs(RNG.nextInt()));
        HttpURLConnection c = open(new URL(q), "GET", context, "image", "https://image-generation.perchance.org/embed");
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        int code = c.getResponseCode();
        String response = readResponse(c, code);
        c.disconnect();
        if (code < 200 || code >= 300) throw errorFor(code, "image", response);
        JSONObject result;
        try { result = new JSONObject(response); }
        catch (Exception e) { throw new IllegalStateException("Perchance direct image JSON invalid: " + trim(response, 160)); }
        String imageId = result.optString("imageId", "");
        if (imageId.isEmpty()) throw new IllegalStateException("Perchance direct imageId missing: " + trim(response, 160));
        String download = result.optString("imageDownloadUrl", "").trim();
        if (download.isEmpty()) download = IMAGE_BASE + "/downloadTemporaryImage?imageId=" + enc(imageId);
        else if (!download.startsWith("http")) download = "https://image-generation.perchance.org" + (download.startsWith("/") ? download : "/" + download);
        byte[] bytes = requestBytes(context, new URL(download), "image", "https://image-generation.perchance.org/embed");
        if (bytes.length < 1024) throw new IllegalStateException("Perchance direct image download too small");
        File dir = new File(context.getFilesDir(), folder);
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create image directory");
        File out = new File(dir, prefix.replaceAll("[^A-Za-z0-9_-]", "_") + "_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(bytes); }
        return out.getAbsolutePath();
    }

    private static String obtainTextKey(Context context) throws Exception {
        String cached = PerchanceSession.key(context, "text");
        if (!cached.isEmpty()) return cached;
        for (int thread = 0; thread < 2; thread++) {
            URL u = new URL(TEXT_BASE + "/verifyUser?thread=" + thread + "&__cacheBust=" + Math.random());
            HttpURLConnection c = open(u, "GET", context, "text", "https://text-generation.perchance.org/embed");
            int code = c.getResponseCode();
            String content = readResponse(c, code);
            c.disconnect();
            if (code >= 200 && code < 300) {
                String key = PerchanceSession.parseUserKey(content);
                if (!key.isEmpty()) {
                    PerchanceSession.save(context, "text", key, browserCookie("https://text-generation.perchance.org"));
                    return key;
                }
            }
        }
        throw new IllegalStateException("Perchance direct text verification failed");
    }

    private static String obtainImageKey(Context context) throws Exception {
        String cached = PerchanceSession.key(context, "image");
        if (!cached.isEmpty()) return cached;
        for (int thread = 0; thread < 2; thread++) {
            URL u = new URL(IMAGE_BASE + "/verifyUser?thread=" + thread + "&__cacheBust=" + Math.random());
            HttpURLConnection c = open(u, "GET", context, "image", "https://image-generation.perchance.org/embed");
            int code = c.getResponseCode();
            String content = readResponse(c, code);
            c.disconnect();
            if (code >= 200 && code < 300) {
                String key = PerchanceSession.parseUserKey(content);
                if (!key.isEmpty()) {
                    PerchanceSession.save(context, "image", key, browserCookie("https://image-generation.perchance.org"));
                    return key;
                }
            }
        }
        throw new IllegalStateException("Perchance direct image verification failed");
    }

    private static HttpURLConnection open(URL u, String method, Context context, String kind, String referer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(90000);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(context));
        c.setRequestProperty("Referer", referer == null ? "https://perchance.org/" : referer);
        String nativeCookie = PerchanceSession.cookie(context, kind);
        String browser = browserCookie("text".equals(kind) ? "https://text-generation.perchance.org" : "https://image-generation.perchance.org");
        String cookie = mergeCookies(nativeCookie, browser);
        if (!cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        return c;
    }

    private static String browserCookie(String url) {
        try {
            String c = CookieManager.getInstance().getCookie(url);
            return c == null ? "" : c.trim();
        } catch (Throwable ignored) { return ""; }
    }

    private static String mergeCookies(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        if (x.isEmpty()) return y;
        if (y.isEmpty() || x.equals(y)) return x;
        return x + "; " + y;
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

    private static byte[] requestBytes(Context context, URL u, String kind, String referer) throws Exception {
        HttpURLConnection c = open(u, "GET", context, kind, referer);
        c.setRequestProperty("Accept", "image/webp,image/jpeg,image/png,*/*");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) { c.disconnect(); throw errorFor(code, kind, "download failed"); }
        try (BufferedInputStream in = new BufferedInputStream(c.getInputStream()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        } finally { c.disconnect(); }
    }

    private static IllegalStateException errorFor(int code, String kind, String detail) {
        String d = detail == null ? "" : detail;
        if (code == 429 || d.contains("too_many_requests"))
            return new IllegalStateException("Perchance 요청 제한(429)");
        if (code >= 500) return new IllegalStateException("Perchance 서버 HTTP " + code);
        return new IllegalStateException("Perchance " + kind + " HTTP " + code + (d.isEmpty() ? "" : ": " + trim(d, 160)));
    }

    private static IllegalStateException combined(String kind, Exception plugin, Exception direct) {
        return new IllegalStateException("Perchance " + kind + " 실패 · plugin runtime: " + trim(safe(plugin), 220) +
                " · direct fallback: " + trim(safe(direct), 140));
    }

    private static String safe(Exception e) {
        if (e == null) return "unknown";
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m.trim();
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
    }
}
