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

import java.net.URL;
import java.util.List;

public class PerchanceConnectActivity extends Activity {
    private static final String TEXT_LANDING = "https://perchance.org/ai-text-plugin-tester";
    private static final String TEXT_VERIFY = "https://text-generation.perchance.org/api/verifyUser?thread=0&__cacheBust=";
    private static final String IMAGE_PAGE = "https://perchance.org/ai-text-to-image-generator";

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
        status.setText("Perchance 텍스트 페이지를 여는 중…");
        web.loadUrl(TEXT_LANDING + "?harinConnect=" + System.currentTimeMillis());
    }

    private void handleLoaded(String url) {
        if (stage == 0 && url.startsWith("https://perchance.org/")) {
            stage = 1;
            status.setText("텍스트 생성 세션을 확인하는 중…");
            web.postDelayed(() -> web.loadUrl(TEXT_VERIFY + Math.random()), 600);
            return;
        }
        if (stage == 1 && url.startsWith("https://text-generation.perchance.org/")) {
            readTextPage(ok -> {
                textOk = ok;
                status.setText(ok ? "텍스트 연결 완료 · 이미지 키를 자동 확인하는 중…" : "텍스트 키를 읽지 못했어 · 이미지 연결은 계속 확인할게…");
                tryAutomaticImageKey();
            });
            return;
        }
        if (stage == 2 && url.startsWith("https://perchance.org/")) {
            // Current Perchance image key lives in the image generator's embedded panel rather than /api/verifyUser.
            web.postDelayed(this::openImageIframeFromPage, 900);
            return;
        }
        if (stage == 3) {
            web.postDelayed(this::readImagePanel, 700);
        }
    }

    private void tryAutomaticImageKey() {
        stage = -1;
        new Thread(() -> {
            try {
                String key = PerchanceClient.refreshImageKey(this);
                imageOk = key != null && !key.isEmpty();
            } catch (Exception ignored) {
                imageOk = false;
            }
            runOnUiThread(() -> {
                if (imageOk) {
                    stage = 4;
                    finishStatus();
                } else {
                    stage = 2;
                    status.setText("이미지 생성기 페이지에서 현재 키를 찾는 중…");
                    web.loadUrl(IMAGE_PAGE + "?harinConnect=" + System.currentTimeMillis());
                }
            });
        }, "perchance-image-key").start();
    }

    private void openImageIframeFromPage() {
        String js = "(function(){var f=document.querySelector('iframe#main')||document.querySelector('iframe[src*=\\\"ai-image-generator-panel\\\"]');return f?f.src:'';})()";
        web.evaluateJavascript(js, value -> {
            String src = decodeJsString(value).trim();
            if (src.isEmpty()) {
                // Some page versions embed the key in the top page itself.
                readImagePanel();
                return;
            }
            try {
                stage = 3;
                web.loadUrl(new URL(new URL(IMAGE_PAGE), src).toString());
            } catch (Exception e) {
                status.setText("이미지 패널 주소를 열지 못했어. 다시 연결을 눌러줘.");
            }
        });
    }

    private void readImagePanel() {
        web.evaluateJavascript("(function(){return document.documentElement ? document.documentElement.outerHTML : '';})()", value -> {
            String html = decodeJsString(value);
            List<String> candidates = PerchanceSession.parseUserKeys(html);
            if (candidates.isEmpty()) {
                web.evaluateJavascript("(function(){return document.documentElement ? document.documentElement.innerText : '';})()", textValue -> {
                    verifyImageCandidates(PerchanceSession.parseUserKeys(decodeJsString(textValue)));
                });
            } else {
                verifyImageCandidates(candidates);
            }
        });
    }

    private void verifyImageCandidates(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            status.setText("이미지 키를 찾지 못했어. 페이지가 완전히 뜬 뒤 ‘다시 연결’을 눌러줘.");
            return;
        }
        status.setText("이미지 키를 검증하는 중…");
        new Thread(() -> {
            String found = "";
            for (String candidate : candidates) {
                if (PerchanceClient.verifyImageKey(this, candidate)) {
                    found = candidate;
                    break;
                }
            }
            final String valid = found;
            if (!valid.isEmpty()) {
                String cookie = CookieManager.getInstance().getCookie("https://perchance.org");
                PerchanceSession.save(this, "image", valid, cookie);
                imageOk = true;
            }
            runOnUiThread(() -> {
                stage = 4;
                finishStatus();
            });
        }, "perchance-image-verify").start();
    }

    private void readTextPage(ValueCallback<Boolean> callback) {
        web.evaluateJavascript("(function(){return document.documentElement ? document.documentElement.innerText : document.body.innerText;})()", value -> {
            String text = decodeJsString(value);
            String key = PerchanceSession.parseUserKey(text);
            if (key.isEmpty()) {
                web.evaluateJavascript("(function(){return document.documentElement ? document.documentElement.outerHTML : '';})()", htmlValue -> {
                    String htmlKey = PerchanceSession.parseUserKey(decodeJsString(htmlValue));
                    if (!htmlKey.isEmpty()) {
                        String cookie = CookieManager.getInstance().getCookie("https://text-generation.perchance.org");
                        PerchanceSession.save(this, "text", htmlKey, cookie);
                        callback.onReceiveValue(true);
                    } else callback.onReceiveValue(false);
                });
            } else {
                String cookie = CookieManager.getInstance().getCookie("https://text-generation.perchance.org");
                PerchanceSession.save(this, "text", key, cookie);
                callback.onReceiveValue(true);
            }
        });
    }

    private void finishStatus() {
        if (textOk && imageOk) status.setText("연결 완료 ✓ 텍스트와 이미지 생성 준비가 끝났어.");
        else if (imageOk) status.setText("이미지 연결 완료 ✓ 텍스트 연결만 다시 확인해줘.");
        else status.setText("이미지 연결에 실패했어. 페이지가 보이는 상태에서 ‘다시 연결’을 눌러줘.");
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
