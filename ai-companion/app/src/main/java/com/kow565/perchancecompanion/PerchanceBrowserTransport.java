package com.kow565.perchancecompanion;

import android.app.Activity;
import android.os.Looper;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Uses Perchance the way Perchance generators do: load a real generator runtime and call its
 * imported ai-text-plugin / text-to-image-plugin functions. We intentionally do not scrape or
 * persist userKey values on this primary path. The plugins own their verification, cookies,
 * embeds and server protocol.
 */
public final class PerchanceBrowserTransport {
    private static final String HOST_PAGE = "https://perchance.org/ai-character-chat";
    private static final Object TEXT_LOCK = new Object();
    private static final Object IMAGE_LOCK = new Object();
    private static final ConcurrentHashMap<String, Pending> PENDING = new ConcurrentHashMap<>();

    private static WeakReference<Activity> currentActivity = new WeakReference<>(null);
    private static Activity sessionActivity;
    private static WebView runtimeWeb;
    private static volatile boolean runtimeReady;
    private static volatile String runtimeUrl = "";
    private static volatile String lastDiagnostic = "대기";
    private static int generationEpoch = 0;

    private PerchanceBrowserTransport() {}

    public static void bind(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        currentActivity = new WeakReference<>(activity);
        activity.runOnUiThread(() -> ensureRuntime(activity));
    }

    public static void unbind(Activity activity) {
        if (activity == null || sessionActivity != activity) return;
        activity.runOnUiThread(() -> {
            destroyRuntime();
            sessionActivity = null;
            Activity current = currentActivity.get();
            if (current == activity) currentActivity = new WeakReference<>(null);
        });
    }

    public static boolean isAvailable() {
        Activity a = currentActivity.get();
        return a != null && !a.isFinishing();
    }

    public static String statusSummary() {
        if (!isAvailable()) return "브라우저 런타임 대기";
        return runtimeReady ? "Perchance 플러그인 ✓" : "Perchance 플러그인 준비 중 · " + lastDiagnostic;
    }

    public static String diagnosticSummary() {
        return "ready=" + runtimeReady + " · runtime=" + (runtimeUrl.isEmpty() ? "없음" : runtimeUrl) + " · " + lastDiagnostic;
    }

    public static void restart() {
        Activity a = currentActivity.get();
        if (a == null || a.isFinishing()) return;
        a.runOnUiThread(() -> {
            destroyRuntime();
            sessionActivity = null;
            ensureRuntime(a);
        });
    }

    public static String generateText(String prompt) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Perchance plugin generation must run off the main thread");
        synchronized (TEXT_LOCK) {
            Exception last = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    ensureReady(requireActivity(), 45000);
                    return runTextOnce(prompt == null ? "" : prompt);
                } catch (Exception e) {
                    last = e;
                    restartAndWait();
                }
            }
            throw new IllegalStateException("Perchance AI Text Plugin 실패: " + safe(last));
        }
    }

    public static String generateImageDataUrl(String prompt, String negativePrompt, int seed) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Perchance plugin generation must run off the main thread");
        synchronized (IMAGE_LOCK) {
            Exception last = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    ensureReady(requireActivity(), 45000);
                    return runImageOnce(prompt == null ? "" : prompt,
                            negativePrompt == null ? "" : negativePrompt, seed);
                } catch (Exception e) {
                    last = e;
                    restartAndWait();
                }
            }
            throw new IllegalStateException("Perchance Text-to-Image Plugin 실패: " + safe(last));
        }
    }

    private static String runTextOnce(String prompt) throws Exception {
        Activity a = requireActivity();
        String id = UUID.randomUUID().toString();
        String script = "(async()=>{const RID=" + JSONObject.quote(id) + ";" +
                "const ok=(s)=>HarinNative.ok(RID,btoa(unescape(encodeURIComponent(String(s)))));" +
                "const fail=(s)=>HarinNative.fail(RID,'text',String(s));" +
                "try{" +
                "const fn=(window.root&&typeof root.aiTextPlugin==='function'?root.aiTextPlugin:(typeof window.aiTextPlugin==='function'?window.aiTextPlugin:null));" +
                "if(!fn)throw new Error('root.aiTextPlugin is not available');" +
                "const job=fn({instruction:" + JSONObject.quote(prompt) + ",startWith:'',stopSequences:[]});" +
                "const data=await job;let out='';" +
                "if(typeof data==='string')out=data;" +
                "else if(data){out=data.text||data.completion||data.fullText||data.fullTextSoFar||data.output||data.result||'';}" +
                "if(!out&&job&&typeof job==='object'){out=job.text||job.completion||job.fullText||job.fullTextSoFar||'';}" +
                "if(!String(out).trim())throw new Error('aiTextPlugin returned no text; keys='+(data&&typeof data==='object'?Object.keys(data).join(',') : typeof data));" +
                "ok(out);" +
                "}catch(e){fail(e&&e.stack?e.stack:(e&&e.message?e.message:e));}})();";
        return execute(a, id, script, "text", 90000);
    }

    private static String runImageOnce(String prompt, String negativePrompt, int seed) throws Exception {
        Activity a = requireActivity();
        String id = UUID.randomUUID().toString();
        String script = "(async()=>{const RID=" + JSONObject.quote(id) + ";" +
                "const ok=(s)=>HarinNative.okRaw(RID,String(s));" +
                "const fail=(s)=>HarinNative.fail(RID,'image',String(s));" +
                "try{" +
                "const fn=(window.root&&typeof root.textToImagePlugin==='function'?root.textToImagePlugin:(typeof window.textToImagePlugin==='function'?window.textToImagePlugin:null));" +
                "if(!fn)throw new Error('root.textToImagePlugin is not available');" +
                "let job=fn({prompt:" + JSONObject.quote(prompt) + ",negativePrompt:" + JSONObject.quote(negativePrompt) + ",resolution:'512x768',seed:" + seed + ",guidanceScale:7});" +
                "let data=await Promise.resolve(job);" +
                "let url='';if(typeof data==='string'&&data.startsWith('data:image/'))url=data;" +
                "else if(data){url=data.dataUrl||data.dataURL||data.url||((data.output&&data.output.dataUrl)||'');}" +
                "if(!url&&job&&typeof job==='object'){url=job.dataUrl||job.dataURL||'';}" +
                "if(!url)throw new Error('textToImagePlugin returned no dataUrl; keys='+(data&&typeof data==='object'?Object.keys(data).join(',') : typeof data));" +
                "if(!String(url).startsWith('data:image/'))throw new Error('image result is not a data URL: '+String(url).slice(0,80));" +
                "ok(url);" +
                "}catch(e){fail(e&&e.stack?e.stack:(e&&e.message?e.message:e));}})();";
        return execute(a, id, script, "image", 150000);
    }

    private static String execute(Activity a, String id, String script, String kind, long timeoutMs) throws Exception {
        WebView web = runtimeWeb;
        if (web == null) throw new IllegalStateException("Perchance generator WebView unavailable");
        Pending p = new Pending();
        PENDING.put(id, p);
        a.runOnUiThread(() -> {
            try { web.evaluateJavascript(script, null); }
            catch (Throwable t) {
                Pending q = PENDING.get(id);
                if (q != null) {
                    q.error = t.getClass().getSimpleName() + ": " + t.getMessage();
                    q.latch.countDown();
                }
            }
        });
        boolean done = p.latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        PENDING.remove(id);
        if (!done) throw new IllegalStateException(kind + " plugin watchdog timeout");
        if (p.error != null && !p.error.isEmpty()) throw new IllegalStateException(p.error);
        if (p.result == null) throw new IllegalStateException(kind + " plugin returned no result");
        return p.result;
    }

    private static void ensureReady(Activity a, long timeoutMs) throws Exception {
        a.runOnUiThread(() -> ensureRuntime(a));
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (runtimeReady) return;
            Thread.sleep(100);
        }
        throw new IllegalStateException("Perchance generator runtime did not expose both plugins: " + lastDiagnostic);
    }

    private static void restartAndWait() {
        try {
            restart();
            long end = System.currentTimeMillis() + 20000;
            while (System.currentTimeMillis() < end && !runtimeReady) Thread.sleep(120);
        } catch (Throwable ignored) {}
    }

    private static Activity requireActivity() {
        Activity a = currentActivity.get();
        if (a == null || a.isFinishing())
            throw new IllegalStateException("열려 있는 앱 화면이 없어 Perchance 플러그인 런타임을 사용할 수 없어");
        return a;
    }

    private static void ensureRuntime(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (sessionActivity == activity && runtimeWeb != null) return;
        destroyRuntime();
        sessionActivity = activity;
        runtimeReady = false;
        runtimeUrl = "";
        lastDiagnostic = "AI Character Chat 로딩";
        final int epoch = ++generationEpoch;
        try {
            runtimeWeb = createWeb(activity, epoch);
            attach(activity, runtimeWeb);
            runtimeWeb.loadUrl(HOST_PAGE + "?harinRuntime=" + System.currentTimeMillis());
        } catch (Throwable t) {
            lastDiagnostic = "WebView 생성 실패: " + safe(t);
            destroyRuntime();
        }
    }

    private static WebView createWeb(Activity activity, int epoch) {
        WebView w = new WebView(activity);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        // Do not spoof a Chrome version. The real Android WebView UA must match its browser fingerprint.
        s.setUserAgentString(WebSettings.getDefaultUserAgent(activity));
        android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(w, true);
        w.addJavascriptInterface(new Bridge(), "HarinNative");
        w.setWebChromeClient(new WebChromeClient());
        w.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (epoch != generationEpoch || view != runtimeWeb) return;
                if (url == null) return;
                if (url.startsWith("https://perchance.org/ai-character-chat")) {
                    lastDiagnostic = "generator iframe 찾는 중";
                    discoverGeneratorIframe(view, epoch, 0);
                } else if (url.startsWith("https://") && url.contains(".perchance.org/")) {
                    runtimeUrl = url;
                    lastDiagnostic = "imported plugin 확인 중";
                    probePlugins(view, epoch, 0);
                }
            }
        });
        w.setClickable(false);
        w.setFocusable(false);
        w.setAlpha(0.02f);
        return w;
    }

    private static void discoverGeneratorIframe(WebView web, int epoch, int attempt) {
        if (web == null || epoch != generationEpoch || web != runtimeWeb) return;
        if (attempt > 80) {
            lastDiagnostic = "AI Character Chat generator iframe을 찾지 못함";
            return;
        }
        String js = "(()=>{const f=document.querySelector('#outputIframeEl')||document.querySelector('iframe[src*=\".perchance.org\"]');return f&&f.src?f.src:'';})()";
        web.evaluateJavascript(js, value -> {
            if (epoch != generationEpoch || web != runtimeWeb) return;
            String src = decodeJsString(value).trim();
            if (!src.isEmpty() && src.startsWith("https://") && src.contains(".perchance.org/")) {
                runtimeUrl = src;
                lastDiagnostic = "generator runtime 이동";
                web.loadUrl(src);
            } else {
                web.postDelayed(() -> discoverGeneratorIframe(web, epoch, attempt + 1), 250);
            }
        });
    }

    private static void probePlugins(WebView web, int epoch, int attempt) {
        if (web == null || epoch != generationEpoch || web != runtimeWeb) return;
        if (attempt > 120) {
            lastDiagnostic = "generator는 열렸지만 aiTextPlugin/textToImagePlugin import가 준비되지 않음";
            return;
        }
        String js = "(()=>{const r=window.root||{};return JSON.stringify({text:typeof r.aiTextPlugin==='function'||typeof window.aiTextPlugin==='function',image:typeof r.textToImagePlugin==='function'||typeof window.textToImagePlugin==='function',root:!!window.root,href:location.href});})()";
        web.evaluateJavascript(js, value -> {
            if (epoch != generationEpoch || web != runtimeWeb) return;
            String raw = decodeJsString(value);
            try {
                JSONObject o = new JSONObject(raw);
                boolean text = o.optBoolean("text", false);
                boolean image = o.optBoolean("image", false);
                runtimeUrl = o.optString("href", runtimeUrl);
                lastDiagnostic = "root=" + o.optBoolean("root", false) + " text=" + text + " image=" + image;
                if (text && image) {
                    runtimeReady = true;
                    return;
                }
            } catch (Exception ignored) {
                lastDiagnostic = "plugin probe 응답 해석 실패: " + raw;
            }
            web.postDelayed(() -> probePlugins(web, epoch, attempt + 1), 300);
        });
    }

    private static void attach(Activity activity, WebView web) {
        // Keep a real attached browser surface. Some verification/browser features behave poorly in a detached WebView.
        ViewGroup.LayoutParams p = new ViewGroup.LayoutParams(12, 12);
        activity.addContentView(web, p);
    }

    private static void destroyRuntime() {
        runtimeReady = false;
        runtimeUrl = "";
        WebView w = runtimeWeb;
        runtimeWeb = null;
        if (w != null) {
            try {
                w.stopLoading();
                w.removeJavascriptInterface("HarinNative");
                ViewGroup parent = (ViewGroup) w.getParent();
                if (parent != null) parent.removeView(w);
                w.destroy();
            } catch (Throwable ignored) {}
        }
    }

    private static String decodeJsString(String value) {
        if (value == null || "null".equals(value)) return "";
        try {
            Object v = new org.json.JSONTokener(value).nextValue();
            return v instanceof String ? (String) v : String.valueOf(v);
        } catch (Exception e) {
            return value;
        }
    }

    private static String safe(Throwable t) {
        if (t == null) return "unknown";
        String s = t.getMessage();
        if (s == null || s.trim().isEmpty()) s = t.getClass().getSimpleName();
        return s.length() > 350 ? s.substring(0, 350) : s;
    }

    private static final class Pending {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile String result;
        volatile String error;
    }

    public static final class Bridge {
        @JavascriptInterface public void ok(String id, String base64) {
            Pending p = PENDING.get(id);
            if (p == null) return;
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                p.result = new String(bytes, StandardCharsets.UTF_8);
            } catch (Throwable t) {
                p.error = "bridge decode failed: " + safe(t);
            }
            p.latch.countDown();
        }

        @JavascriptInterface public void okRaw(String id, String value) {
            Pending p = PENDING.get(id);
            if (p == null) return;
            p.result = value;
            p.latch.countDown();
        }

        @JavascriptInterface public void fail(String id, String kind, String message) {
            Pending p = PENDING.get(id);
            if (p == null) return;
            p.error = kind + " plugin: " + message;
            p.latch.countDown();
        }
    }
}
