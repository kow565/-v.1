package com.kow565.perchancecompanion;

import android.app.Activity;
import android.os.Looper;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
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
 * Browser-backed Perchance transport.
 *
 * Perchance's AI plugins are designed to run inside browser embeds. This class keeps two tiny,
 * attached WebViews on the currently resumed Activity and performs the API calls from the same
 * origins as the official embeds. Cookies, browser verification and server session state therefore
 * stay in the WebView instead of being copied into native HTTP code.
 */
public final class PerchanceBrowserTransport {
    private static final String TEXT_EMBED = "https://text-generation.perchance.org/embed";
    private static final String IMAGE_EMBED = "https://image-generation.perchance.org/embed";
    private static final Object TEXT_LOCK = new Object();
    private static final Object IMAGE_LOCK = new Object();
    private static final ConcurrentHashMap<String, Pending> PENDING = new ConcurrentHashMap<>();
    private static WeakReference<Activity> currentActivity = new WeakReference<>(null);
    private static Activity sessionActivity;
    private static WebView textWeb;
    private static WebView imageWeb;
    private static volatile boolean textReady;
    private static volatile boolean imageReady;
    private static int textThreadCounter;
    private static int imageThreadCounter;

    private PerchanceBrowserTransport() {}

    public static void bind(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        currentActivity = new WeakReference<>(activity);
        activity.runOnUiThread(() -> ensureWebViews(activity));
    }

    public static void unbind(Activity activity) {
        if (activity == null || sessionActivity != activity) return;
        activity.runOnUiThread(() -> {
            destroyWebViews();
            sessionActivity = null;
            Activity current = currentActivity.get();
            if (current == activity) currentActivity = new WeakReference<>(null);
        });
    }

    public static boolean isAvailable() {
        Activity a = currentActivity.get();
        return a != null && !a.isFinishing();
    }

    public static String generateText(String prompt) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Perchance browser generation must run off the main thread");
        synchronized (TEXT_LOCK) {
            Exception last = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    int thread = Math.floorMod(textThreadCounter++, 2);
                    return runTextOnce(prompt == null ? "" : prompt, thread);
                } catch (Exception e) {
                    last = e;
                    reset("text");
                }
            }
            throw new IllegalStateException("Perchance 브라우저 텍스트 연결 실패: " + safe(last));
        }
    }

    public static JSONObject generateImage(String prompt, String negativePrompt, int seed) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Perchance browser generation must run off the main thread");
        synchronized (IMAGE_LOCK) {
            Exception last = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    int thread = Math.floorMod(imageThreadCounter++, 2);
                    String raw = runImageOnce(prompt == null ? "" : prompt,
                            negativePrompt == null ? "" : negativePrompt, seed, thread);
                    JSONObject o = new JSONObject(raw);
                    String status = o.optString("status", "");
                    if (!status.isEmpty() && !"success".equalsIgnoreCase(status) && !o.has("imageId"))
                        throw new IllegalStateException(status + ": " + o.optString("message", o.optString("error", "")));
                    if (o.optString("imageId", "").isEmpty())
                        throw new IllegalStateException("imageId missing");
                    return o;
                } catch (Exception e) {
                    last = e;
                    reset("image");
                }
            }
            throw new IllegalStateException("Perchance 브라우저 이미지 연결 실패: " + safe(last));
        }
    }

    public static String statusSummary() {
        if (!isAvailable()) return "브라우저 대기";
        return (textReady ? "텍스트 ✓" : "텍스트 준비 중") + " · " +
                (imageReady ? "이미지 ✓" : "이미지 준비 중");
    }

    private static String runTextOnce(String prompt, int thread) throws Exception {
        Activity a = requireActivity();
        ensureReady(a, "text", 20000);
        String id = UUID.randomUUID().toString();
        String promptJs = JSONObject.quote(prompt);
        String script = "(async()=>{const RID=" + JSONObject.quote(id) + ";" +
                "const send=(s)=>HarinNative.ok(RID,btoa(unescape(encodeURIComponent(s))));" +
                "const fail=(s)=>HarinNative.fail(RID,'text',String(s));" +
                "const keyFrom=async(t)=>{let k='';" +
                "try{const r=await fetch('/api/verifyUser?thread='+t+'&__cacheBust='+Math.random(),{credentials:'include',cache:'no-store'});" +
                "const x=await r.text();try{const j=JSON.parse(x);k=j.userKey||j.key||'';}catch(e){}" +
                "if(!k){const m=x.match(/\\\"userKey\\\"\\s*:\\s*\\\"([^\\\"]+)/);if(m)k=m[1];}}catch(e){}return k;};" +
                "const parse=(raw)=>{let out='';for(const ln of raw.split(/\\r?\\n/)){const z=ln.trim();try{" +
                "if(z.startsWith('t:')){const v=JSON.parse(z.slice(2));if(typeof v==='string')out+=v;}" +
                "else if(z.startsWith('data:')){const p=z.slice(5).trim();if(!p||p==='[DONE]')continue;const v=JSON.parse(p);" +
                "if(typeof v==='string')out+=v;else if(v&&typeof v.text==='string')out+=v.text;}}catch(e){}}" +
                "if(out)return out;try{const j=JSON.parse(raw);return j.text||raw;}catch(e){return raw;}};" +
                "try{let key=await keyFrom(" + thread + ");if(!key)throw new Error('userKey not available in browser session');" +
                "const body={generatorName:'ai-text-generator',instruction:" + promptJs + ",instructionTokenCount:1,startWith:'',startWithTokenCount:1,stopSequences:[]};" +
                "const req='harinText'+Math.random().toString(36).slice(2);" +
                "const u='/api/generate?userKey='+encodeURIComponent(key)+'&requestId='+encodeURIComponent(req)+'&__cacheBust='+Math.random();" +
                "let r=await fetch(u,{method:'POST',credentials:'include',cache:'no-store',headers:{'Content-Type':'application/json','Accept':'text/event-stream, application/json, text/plain'},body:JSON.stringify(body)});" +
                "let raw=await r.text();if(!r.ok||raw.includes('invalid_key')){" +
                "key=await keyFrom(" + ((thread + 1) % 2) + ");if(!key)throw new Error('browser key refresh failed');" +
                "const u2='/api/generate?userKey='+encodeURIComponent(key)+'&requestId='+encodeURIComponent(req+'r')+'&__cacheBust='+Math.random();" +
                "r=await fetch(u2,{method:'POST',credentials:'include',cache:'no-store',headers:{'Content-Type':'application/json','Accept':'text/event-stream, application/json, text/plain'},body:JSON.stringify(body)});raw=await r.text();}" +
                "if(!r.ok)throw new Error('HTTP '+r.status+' '+raw.slice(0,180));const out=parse(raw);if(!out.trim())throw new Error('empty text stream');send(out);" +
                "}catch(e){fail(e&&e.message?e.message:e);}})();";
        return execute(a, textWeb, id, script, "text", 55000);
    }

    private static String runImageOnce(String prompt, String negativePrompt, int seed, int thread) throws Exception {
        Activity a = requireActivity();
        ensureReady(a, "image", 20000);
        String id = UUID.randomUUID().toString();
        String p = JSONObject.quote(prompt);
        String n = JSONObject.quote(negativePrompt);
        String script = "(async()=>{const RID=" + JSONObject.quote(id) + ";" +
                "const send=(s)=>HarinNative.ok(RID,btoa(unescape(encodeURIComponent(s))));" +
                "const fail=(s)=>HarinNative.fail(RID,'image',String(s));" +
                "const verified=async(k)=>{try{const r=await fetch('/api/checkUserVerificationStatus?userKey='+encodeURIComponent(k)+'&__cacheBust='+Math.random(),{credentials:'include',cache:'no-store'});const x=(await r.text()).toLowerCase();return r.ok&&x.includes('verified')&&!x.includes('not_verified')&&!x.includes('invalid');}catch(e){return false;}};" +
                "const keyFrom=async(t)=>{let k='';try{const r=await fetch('/api/verifyUser?thread='+t+'&__cacheBust='+Math.random(),{credentials:'include',cache:'no-store'});const x=await r.text();try{const j=JSON.parse(x);k=j.userKey||j.key||'';}catch(e){}if(!k){const m=x.match(/\\\"userKey\\\"\\s*:\\s*\\\"([^\\\"]+)/);if(m)k=m[1];}}catch(e){}" +
                "if(k)return k;const html=document.documentElement?document.documentElement.outerHTML:'';const c=[...new Set(html.match(/[a-f0-9]{64}/gi)||[])];for(const v of c){if(await verified(v))return v;}return '';};" +
                "try{let key=await keyFrom(" + thread + ");if(!key)throw new Error('image userKey not available in browser session');" +
                "const make=async(k)=>{const q=new URLSearchParams({prompt:" + p + ",negativePrompt:" + n + ",userKey:k,__cache_bust:String(Math.random()),seed:String(" + seed + "),resolution:'512x768',guidanceScale:'7',channel:'ai-text-to-image-generators',subChannel:'public',requestId:'harinImage'+Math.random().toString(36).slice(2)});" +
                "const r=await fetch('/api/generate?'+q.toString(),{credentials:'include',cache:'no-store',headers:{'Accept':'application/json, text/plain, */*'}});const x=await r.text();return {r,x};};" +
                "let z=await make(key);if(!z.r.ok||z.x.includes('invalid_key')){key=await keyFrom(" + ((thread + 1) % 2) + ");if(!key)throw new Error('image browser key refresh failed');z=await make(key);}" +
                "if(!z.r.ok)throw new Error('HTTP '+z.r.status+' '+z.x.slice(0,180));let obj;try{obj=JSON.parse(z.x);}catch(e){throw new Error('invalid image JSON '+z.x.slice(0,160));}" +
                "if(obj.status&&obj.status!=='success'&&!obj.imageId)throw new Error(obj.status+': '+(obj.message||obj.error||''));if(!obj.imageId)throw new Error('imageId missing');send(JSON.stringify(obj));" +
                "}catch(e){fail(e&&e.message?e.message:e);}})();";
        return execute(a, imageWeb, id, script, "image", 65000);
    }

    private static String execute(Activity a, WebView web, String id, String script, String kind, long timeoutMs) throws Exception {
        if (web == null) throw new IllegalStateException(kind + " WebView unavailable");
        Pending p = new Pending();
        PENDING.put(id, p);
        a.runOnUiThread(() -> {
            try { web.evaluateJavascript(script, null); }
            catch (Throwable t) {
                Pending q = PENDING.get(id);
                if (q != null) { q.error = t.getClass().getSimpleName() + ": " + t.getMessage(); q.latch.countDown(); }
            }
        });
        boolean done = p.latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        PENDING.remove(id);
        if (!done) throw new IllegalStateException(kind + " embed watchdog timeout");
        if (p.error != null && !p.error.isEmpty()) throw new IllegalStateException(p.error);
        if (p.result == null) throw new IllegalStateException(kind + " embed returned no result");
        return p.result;
    }

    private static void ensureReady(Activity a, String kind, long timeoutMs) throws Exception {
        a.runOnUiThread(() -> ensureWebViews(a));
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            boolean ready = "text".equals(kind) ? textReady : imageReady;
            if (ready) return;
            Thread.sleep(80);
        }
        throw new IllegalStateException(kind + " embed did not finish loading");
    }

    private static Activity requireActivity() {
        Activity a = currentActivity.get();
        if (a == null || a.isFinishing()) throw new IllegalStateException("열려 있는 앱 화면이 없어 브라우저 세션을 사용할 수 없어");
        return a;
    }

    private static void ensureWebViews(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (sessionActivity == activity && textWeb != null && imageWeb != null) return;
        destroyWebViews();
        sessionActivity = activity;
        textReady = false;
        imageReady = false;
        try {
            textWeb = createWeb(activity, "text");
            imageWeb = createWeb(activity, "image");
            attach(activity, textWeb);
            attach(activity, imageWeb);
            textWeb.loadUrl(TEXT_EMBED);
            imageWeb.loadUrl(IMAGE_EMBED);
        } catch (Throwable t) {
            destroyWebViews();
        }
    }

    private static WebView createWeb(Activity activity, String kind) {
        WebView w = new WebView(activity);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0 Mobile Safari/537.36");
        android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(w, true);
        w.addJavascriptInterface(new Bridge(), "HarinNative");
        w.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if ("text".equals(kind) && url != null && url.startsWith("https://text-generation.perchance.org/")) textReady = true;
                if ("image".equals(kind) && url != null && url.startsWith("https://image-generation.perchance.org/")) imageReady = true;
            }
        });
        w.setClickable(false);
        w.setFocusable(false);
        w.setAlpha(0.01f);
        w.setTranslationX(-20f);
        w.setTranslationY(-20f);
        return w;
    }

    private static void attach(Activity activity, WebView web) {
        ViewGroup.LayoutParams p = new ViewGroup.LayoutParams(2, 2);
        activity.addContentView(web, p);
    }

    private static void reset(String kind) {
        Activity a = currentActivity.get();
        if (a == null || a.isFinishing()) return;
        a.runOnUiThread(() -> {
            try {
                if ("text".equals(kind) && textWeb != null) {
                    textReady = false;
                    textWeb.loadUrl(TEXT_EMBED + "?harinReload=" + System.currentTimeMillis());
                } else if ("image".equals(kind) && imageWeb != null) {
                    imageReady = false;
                    imageWeb.loadUrl(IMAGE_EMBED + "?harinReload=" + System.currentTimeMillis());
                }
            } catch (Throwable ignored) {}
        });
    }

    private static void destroyWebViews() {
        try { if (textWeb != null) { textWeb.stopLoading(); textWeb.removeJavascriptInterface("HarinNative"); textWeb.destroy(); } } catch (Throwable ignored) {}
        try { if (imageWeb != null) { imageWeb.stopLoading(); imageWeb.removeJavascriptInterface("HarinNative"); imageWeb.destroy(); } } catch (Throwable ignored) {}
        textWeb = null;
        imageWeb = null;
        textReady = false;
        imageReady = false;
    }

    private static String decode(String value) {
        try { return new String(Base64.decode(value, Base64.DEFAULT), StandardCharsets.UTF_8); }
        catch (Exception e) { return value == null ? "" : value; }
    }

    private static String safe(Exception e) {
        if (e == null) return "unknown error";
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) return e.getClass().getSimpleName();
        return m.length() > 180 ? m.substring(0, 180) : m;
    }

    public static final class Bridge {
        @JavascriptInterface public void ok(String id, String base64) {
            Pending p = PENDING.get(id);
            if (p == null) return;
            p.result = decode(base64);
            p.latch.countDown();
        }

        @JavascriptInterface public void fail(String id, String kind, String message) {
            Pending p = PENDING.get(id);
            if (p == null) return;
            p.error = (kind == null ? "Perchance" : kind) + ": " + (message == null ? "unknown error" : message);
            p.latch.countDown();
        }
    }

    private static final class Pending {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile String result;
        volatile String error;
    }
}
