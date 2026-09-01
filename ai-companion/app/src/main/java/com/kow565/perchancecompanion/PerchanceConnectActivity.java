package com.kow565.perchancecompanion;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONTokener;

public class PerchanceConnectActivity extends Activity {
    private static final String LANDING = "https://perchance.org/ai-text-plugin-tester";
    private static final String TEXT_VERIFY = "https://text-generation.perchance.org/api/verifyUser?thread=0&__cacheBust=";
    private static final String IMAGE_VERIFY = "https://image-generation.perchance.org/api/verifyUser?thread=0&__cacheBust=";

    private WebView web;
    private TextView status;
    private int stage = 0;
    private boolean textOk = false;
    private boolean imageOk = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        startConnection();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(14), dp(18), dp(14), dp(14));

        TextView title = new TextView(this);
        title.setText("Perchance 연결");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setText("브라우저 세션을 준비하는 중…");
        status.setTextSize(14);
        status.setTextColor(Color.DKGRAY);
        status.setPadding(dp(4), dp(12), dp(4), dp(10));
        root.addView(status);

        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/127 Mobile Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                handleLoaded(url);
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        Button retry = new Button(this);
        retry.setText("다시 연결");
        retry.setOnClickListener(v -> startConnection());
        actions.addView(retry, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button done = new Button(this);
        done.setText("DM으로 돌아가기");
        done.setOnClickListener(v -> finish());
        actions.addView(done, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(actions);

        setContentView(root);
    }

    private void startConnection() {
        textOk = false;
        imageOk = false;
        stage = 0;
        PerchanceSession.clear(this);
        status.setText("Perchance 페이지를 여는 중…");
        web.loadUrl(LANDING + "?harinConnect=" + System.currentTimeMillis());
    }

    private void handleLoaded(String url) {
        if (stage == 0 && url.startsWith("https://perchance.org/")) {
            stage = 1;
            status.setText("텍스트 생성 세션을 확인하는 중…");
            web.postDelayed(() -> web.loadUrl(TEXT_VERIFY + Math.random()), 700);
            return;
        }
        if (stage == 1 && url.startsWith("https://text-generation.perchance.org/")) {
            readCurrentPage("text", "https://text-generation.perchance.org", ok -> {
                textOk = ok;
                stage = 2;
                status.setText(ok ? "텍스트 연결 완료 · 이미지 세션 확인 중…" : "텍스트 키를 읽지 못했어. 이미지 연결도 확인하는 중…");
                web.loadUrl(IMAGE_VERIFY + Math.random());
            });
            return;
        }
        if (stage == 2 && url.startsWith("https://image-generation.perchance.org/")) {
            readCurrentPage("image", "https://image-generation.perchance.org", ok -> {
                imageOk = ok;
                stage = 3;
                if (textOk && imageOk) status.setText("연결 완료 ✓ 이제 모든 DM과 이미지 생성에서 이 세션을 사용해.");
                else status.setText("연결이 완전히 되지 않았어. 아래 '다시 연결'을 한 번 눌러줘. Perchance 페이지가 보이면 그대로 두면 돼.");
            });
        }
    }

    private void readCurrentPage(String kind, String cookieUrl, ValueCallback<Boolean> callback) {
        web.evaluateJavascript("(function(){return document.documentElement ? document.documentElement.innerText : document.body.innerText;})()", value -> {
            String text = decodeJsString(value);
            String key = PerchanceSession.parseUserKey(text);
            if (key.isEmpty()) {
                // Some responses are rendered as preformatted HTML; try the raw HTML too.
                web.evaluateJavascript("(function(){return document.documentElement ? document.documentElement.outerHTML : '';})()", htmlValue -> {
                    String html = decodeJsString(htmlValue);
                    String htmlKey = PerchanceSession.parseUserKey(html);
                    if (!htmlKey.isEmpty()) {
                        String cookie = CookieManager.getInstance().getCookie(cookieUrl);
                        PerchanceSession.save(this, kind, htmlKey, cookie);
                        callback.onReceiveValue(true);
                    } else callback.onReceiveValue(false);
                });
            } else {
                String cookie = CookieManager.getInstance().getCookie(cookieUrl);
                PerchanceSession.save(this, kind, key, cookie);
                callback.onReceiveValue(true);
            }
        });
    }

    private String decodeJsString(String value) {
        if (value == null || "null".equals(value)) return "";
        try {
            Object v = new JSONTokener(value).nextValue();
            return v instanceof String ? (String) v : String.valueOf(v);
        } catch (Exception e) {
            return value;
        }
    }

    @Override protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
