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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PerchanceClient {
    private static final String TEXT_BASE = "https://text-generation.perchance.org/api";
    private static final String IMAGE_BASE = "https://image-generation.perchance.org/api";
    private static final String IMAGE_KEY_PAGE = "https://perchance.org/ai-text-to-image-generator";
    private static final String IMAGE_VERIFY = IMAGE_BASE + "/checkUserVerificationStatus";
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0 Mobile Safari/537.36";
    private static final Random RNG = new Random();
    private static final Pattern MAIN_IFRAME = Pattern.compile("(?is)<iframe[^>]*(?:id=[\\\"']main[\\\"'][^>]*src=[\\\"']([^\\\"']+)|src=[\\\"']([^\\\"']*ai-image-generator-panel[^\\\"']*)[\\\"'])");

    private PerchanceClient() {}

    public static String generateText(Context context, String prompt) throws Exception {
        String key = obtainTextKey(context);
        String requestId = "aiTextCompletion" + Math.abs(RNG.nextInt());
        URL u = new URL(TEXT_BASE + "/generate?userKey=" + enc(key) + "&requestId=" + enc(requestId) + "&__cacheBust=" + Math.random());
        JSONObject body = new JSONObject();
        body.put("generatorName", "ai-text-generator");
        body.put("instruction", prompt);
        body.put("instructionTokenCount", 1);
        body.put("startWith", "");
        body.put("startWithTokenCount", 1);
        body.put("stopSequences", new JSONArray());

        HttpURLConnection c = open(u, "POST", context, "text", "https://perchance.org/");
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
        String key = obtainImageKey(context, false);
        String requestId = String.valueOf(Math.random());
        String url = IMAGE_BASE + "/generate" +
                "?prompt=" + enc(prompt) +
                "&negativePrompt=" + enc(negativePrompt == null ? "" : negativePrompt) +
                "&userKey=" + enc(key) +
                "&__cache_bust=" + Math.random() +
                "&seed=" + seed +
                "&resolution=512x768" +
                "&guidanceScale=7" +
                "&channel=ai-text-to-image-generators" +
                "&subChannel=public" +
                "&requestId=" + enc(requestId);

        HttpURLConnection c = open(new URL(url), "GET", context, "image", IMAGE_KEY_PAGE);
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        int code = c.getResponseCode();
        String response = readResponse(c, code);
        c.disconnect();

        if (code < 200 || code >= 300 || response.toLowerCase(Locale.ROOT).contains("invalid_key")) {
            // A cached image key can expire before our local TTL. Refresh once automatically.
            PerchanceSession.clearKind(context, "image");
            String fresh = obtainImageKey(context, true);
            if (!fresh.equals(key)) return generateImage(context, prompt, negativePrompt, seed, folder, prefix);
            if (code < 200 || code >= 300) throw errorFor(code, "image", response);
        }

        JSONObject result;
        try { result = new JSONObject(response); }
        catch (Exception e) { throw new IllegalStateException("Perchance 이미지 응답을 해석하지 못했어: " + trim(response, 180)); }

        String status = result.optString("status", "");
        if (!status.isEmpty() && !"success".equalsIgnoreCase(status) && !result.has("imageId"))
            throw new IllegalStateException("Perchance image error: " + status + " " + result.optString("message", result.optString("error", "")));

        String imageId = result.optString("imageId", "");
        if (imageId.isEmpty()) throw new IllegalStateException("Perchance imageId missing: " + trim(response, 180));

        String imageDownloadUrl = result.optString("imageDownloadUrl", "").trim();
        String downloadUrl;
        if (!imageDownloadUrl.isEmpty()) {
            downloadUrl = imageDownloadUrl.startsWith("http") ? imageDownloadUrl : "https://image-generation.perchance.org" + (imageDownloadUrl.startsWith("/") ? imageDownloadUrl : "/" + imageDownloadUrl);
        } else {
            String proxy = findProxy(result);
            downloadUrl = proxy == null || proxy.isEmpty()
                    ? IMAGE_BASE + "/downloadTemporaryImage?imageId=" + enc(imageId)
                    : (proxy.startsWith("http") ? proxy : "https://image-generation.perchance.org" + (proxy.startsWith("/") ? proxy : "/" + proxy));
        }

        byte[] bytes;
        try { bytes = requestBytes(context, new URL(downloadUrl), "image", IMAGE_KEY_PAGE); }
        catch (Exception first) { bytes = requestBytes(context, new URL(IMAGE_BASE + "/downloadTemporaryImage?imageId=" + enc(imageId)), "image", IMAGE_KEY_PAGE); }

        if (bytes.length < 1024) throw new IllegalStateException("Perchance 이미지 다운로드 결과가 비정상적으로 작아.");
        File dir = new File(context.getFilesDir(), folder);
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create image directory");
        String ext = result.optString("fileExtension", "jpg").replaceAll("[^A-Za-z0-9]", "");
        if (ext.isEmpty()) ext = "jpg";
        File out = new File(dir, prefix + "_" + System.currentTimeMillis() + "." + ext);
        try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(bytes); }
        return out.getAbsolutePath();
    }

    public static String refreshImageKey(Context context) throws Exception {
        PerchanceSession.clearKind(context, "image");
        return obtainImageKey(context, true);
    }

    public static boolean verifyImageKey(Context context, String key) {
        if (key == null || key.trim().isEmpty()) return false;
        try {
            URL u = new URL(IMAGE_VERIFY + "?userKey=" + enc(key.trim()) + "&__cacheBust=" + Math.random());
            HttpURLConnection c = open(u, "GET", context, "image", IMAGE_KEY_PAGE);
            c.setRequestProperty("Accept", "*/*");
            int code = c.getResponseCode();
            String response = readResponse(c, code).toLowerCase(Locale.ROOT);
            c.disconnect();
            if (code < 200 || code >= 300) return false;
            if (response.contains("not_verified") || response.contains("invalid")) return false;
            return response.contains("verified") || response.contains("success") || response.contains("true");
        } catch (Exception e) {
            return false;
        }
    }

    private static String obtainTextKey(Context context) throws Exception {
        String cached = PerchanceSession.key(context, "text");
        if (!cached.isEmpty()) return cached;
        try {
            URL u = new URL(TEXT_BASE + "/verifyUser?thread=0&__cacheBust=" + Math.random());
            HttpURLConnection c = open(u, "GET", context, "text", "https://perchance.org/");
            c.setRequestProperty("Accept", "*/*");
            int code = c.getResponseCode();
            String content = readResponse(c, code);
            c.disconnect();
            if (code >= 200 && code < 300) {
                String key = PerchanceSession.parseUserKey(content);
                if (!key.isEmpty()) {
                    PerchanceSession.save(context, "text", key, PerchanceSession.cookie(context, "text"));
                    return key;
                }
            }
        } catch (Exception ignored) {}
        throw new IllegalStateException("PERCHANCE_CONNECT_REQUIRED: Perchance 텍스트 세션 연결이 필요해");
    }

    private static String obtainImageKey(Context context, boolean forceNetwork) throws Exception {
        if (!forceNetwork) {
            String cached = PerchanceSession.key(context, "image");
            if (!cached.isEmpty() && verifyImageKey(context, cached)) return cached;
            if (!cached.isEmpty()) PerchanceSession.clearKind(context, "image");
        }

        String page = fetchPlain(context, new URL(IMAGE_KEY_PAGE), "image", IMAGE_KEY_PAGE);
        List<String> direct = PerchanceSession.parseUserKeys(page);
        for (String candidate : direct) {
            if (verifyImageKey(context, candidate)) {
                PerchanceSession.save(context, "image", candidate, PerchanceSession.cookie(context, "image"));
                return candidate;
            }
        }

        String iframe = findImageIframe(page);
        if (!iframe.isEmpty()) {
            URL iframeUrl = new URL(new URL(IMAGE_KEY_PAGE), iframe);
            String iframeHtml = fetchPlain(context, iframeUrl, "image", IMAGE_KEY_PAGE);
            List<String> keys = PerchanceSession.parseUserKeys(iframeHtml);
            for (String candidate : keys) {
                if (verifyImageKey(context, candidate)) {
                    PerchanceSession.save(context, "image", candidate, PerchanceSession.cookie(context, "image"));
                    return candidate;
                }
            }
        }

        throw new IllegalStateException("PERCHANCE_IMAGE_KEY_REQUIRED: Perchance 이미지 키를 자동으로 가져오지 못했어. 연결 화면에서 이미지 생성기 페이지를 열어줘.");
    }

    private static String findImageIframe(String html) {
        if (html == null) return "";
        Matcher m = MAIN_IFRAME.matcher(html);
        if (!m.find()) return "";
        String a = m.group(1);
        String b = m.group(2);
        return a != null && !a.trim().isEmpty() ? a.trim() : (b == null ? "" : b.trim());
    }

    private static String fetchPlain(Context context, URL url, String kind, String referer) throws Exception {
        HttpURLConnection c = open(url, "GET", context, kind, referer);
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json,text/plain,*/*");
        int code = c.getResponseCode();
        String response = readResponse(c, code);
        c.disconnect();
        if (code < 200 || code >= 300) throw errorFor(code, kind, response);
        return response;
    }

    private static HttpURLConnection open(URL u, String method, Context context, String kind, String referer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(90000);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Referer", referer == null || referer.isEmpty() ? "https://perchance.org/" : referer);
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

    private static byte[] requestBytes(Context context, URL u, String kind, String referer) throws Exception {
        HttpURLConnection c = open(u, "GET", context, kind, referer);
        c.setRequestProperty("Accept", "image/webp,image/jpeg,image/png,*/*");
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
        String lower = d.toLowerCase(Locale.ROOT);
        if (code == 401 || code == 403 || lower.contains("invalid_key") || lower.contains("userkey"))
            return new IllegalStateException("PERCHANCE_CONNECT_REQUIRED: Perchance " + kind + " 세션을 다시 연결해줘");
        if (code == 429 || lower.contains("too_many_requests"))
            return new IllegalStateException("Perchance 요청 제한에 도달했어. 잠시 후 다시 시도해줘.");
        return new IllegalStateException("Perchance " + kind + " HTTP " + code + (d.isEmpty() ? "" : ": " + trim(d, 180)));
    }

    private static String findProxy(Object value) {
        if (value instanceof String) {
            String s = (String) value;
            if (s.contains("downloadTemporaryImageViaProxy")) return s;
            if (s.startsWith("v1.") && s.length() > 80) return "/api/downloadTemporaryImageViaProxy?t=" + s;
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
    private static String enc(String value) throws Exception { return URLEncoder.encode(value, StandardCharsets.UTF_8.toString()); }
}
