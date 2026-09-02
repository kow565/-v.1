package com.kow565.perchancecompanion;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

public class PerchanceConnectActivity extends Activity {
    private static final String PERCHANCE_PAGE = "https://perchance.org/87fjsh5tkf";
    private TextView status;
    private TextView detail;
    private WebView visiblePerchance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean polling;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        PerchanceBrowserTransport.bind(this);
        polling = true;
        refreshStatusLoop();
    }

    @Override protected void onPause() {
        polling = false;
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(16), dp(22), dp(16), dp(24));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Perchance 플러그인 진단");
        title.setTextSize(23);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView info = new TextView(this);
        info.setText("v0.6.1은 Perchance 생성기를 정상 iframe 안에서 실행해. userKey나 내부 /api/generate 대신 Harin 전용 bridge가 공식 AI Text Plugin과 Text-to-Image Plugin을 import해. 아래 브리지 화면에서 브라우저 검증이 나오면 직접 완료해 줘.");
        info.setTextSize(13);
        info.setTextColor(Color.DKGRAY);
        info.setPadding(0, dp(12), 0, dp(12));
        root.addView(info);

        status = new TextView(this);
        status.setText("generator runtime 준비 중…");
        status.setTextSize(17);
        status.setTextColor(Color.BLACK);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        status.setPadding(0, dp(8), 0, dp(6));
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        detail = new TextView(this);
        detail.setText("상태를 읽는 중…");
        detail.setTextSize(12);
        detail.setTextColor(Color.GRAY);
        detail.setPadding(0, 0, 0, dp(10));
        root.addView(detail);

        TextView browserLabel = new TextView(this);
        browserLabel.setText("Harin 공식 Perchance iframe 브리지");
        browserLabel.setTextSize(14);
        browserLabel.setTextColor(Color.BLACK);
        browserLabel.setPadding(0, dp(4), 0, dp(5));
        root.addView(browserLabel);

        visiblePerchance = new WebView(this);
        WebSettings ws = visiblePerchance.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);
        ws.setSupportMultipleWindows(true);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setSafeBrowsingEnabled(true);
        ws.setUserAgentString(PerchanceBrowserTransport.chromeLikeUserAgent(this));
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(visiblePerchance, true);
        visiblePerchance.setWebChromeClient(new WebChromeClient());
        visiblePerchance.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                detail.setText("원본 Perchance 페이지 로드됨 · 확인/동의/브라우저 검증 화면이 보이면 그대로 완료한 뒤 플러그인 런타임 새로 만들기를 눌러줘.");
            }
        });
        root.addView(visiblePerchance, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));
        visiblePerchance.loadUrl(PERCHANCE_PAGE + "?harinVisible=" + System.currentTimeMillis());

        Button reloadPage = button("원본 Perchance 다시 불러오기");
        reloadPage.setOnClickListener(v -> visiblePerchance.loadUrl(PERCHANCE_PAGE + "?harinVisible=" + System.currentTimeMillis()));
        root.addView(reloadPage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button rebuild = button("플러그인 런타임 새로 만들기");
        rebuild.setOnClickListener(v -> {
            CookieManager.getInstance().flush();
            detail.setText("현재 브라우저 쿠키를 유지한 채 Perchance generator runtime을 처음부터 다시 여는 중…");
            PerchanceBrowserTransport.restart();
        });
        root.addView(rebuild, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button textTest = button("AI Text Plugin 실제 테스트");
        textTest.setOnClickListener(v -> testText());
        root.addView(textTest, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button imageTest = button("Text-to-Image Plugin 실제 테스트");
        imageTest.setOnClickListener(v -> testImage());
        root.addView(imageTest, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button done = button("DM으로 돌아가기");
        done.setOnClickListener(v -> finish());
        root.addView(done, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private void testText() {
        detail.setText("공식 AI Text Plugin에 실제 요청을 보내는 중…");
        new Thread(() -> {
            try {
                String result = PerchanceBrowserTransport.generateText("Reply with exactly: OK");
                runOnUiThread(() -> detail.setText("텍스트 플러그인 성공 ✓ · " + compact(result)));
            } catch (Exception e) {
                runOnUiThread(() -> detail.setText("텍스트 플러그인 실패 · " + compact(e.getMessage()) + "\n" + PerchanceBrowserTransport.diagnosticSummary()));
            }
        }, "perchance-plugin-text-test").start();
    }

    private void testImage() {
        detail.setText("공식 Text-to-Image Plugin에 실제 요청을 보내는 중…");
        new Thread(() -> {
            try {
                String dataUrl = PerchanceBrowserTransport.generateImageDataUrl(
                        "a simple neutral gray ceramic mug on a plain table, realistic photo",
                        "text, watermark, malformed object", 123456);
                String path = PerchanceClient.savePluginImageForTest(this, dataUrl);
                long size = new File(path).length();
                runOnUiThread(() -> detail.setText("이미지 플러그인 성공 ✓ · 저장 " + size + " bytes\n" + compact(path)));
            } catch (Exception e) {
                runOnUiThread(() -> detail.setText("이미지 플러그인 실패 · " + compact(e.getMessage()) + "\n" + PerchanceBrowserTransport.diagnosticSummary()));
            }
        }, "perchance-plugin-image-test").start();
    }

    private void refreshStatusLoop() {
        if (!polling) return;
        status.setText(PerchanceBrowserTransport.statusSummary());
        handler.postDelayed(this::refreshStatusLoop, 700);
    }

    @Override protected void onDestroy() {
        if (visiblePerchance != null) {
            try {
                visiblePerchance.stopLoading();
                visiblePerchance.destroy();
            } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private String compact(String s) {
        if (s == null || s.trim().isEmpty()) return "응답 없음";
        String v = s.trim().replace('\n', ' ');
        return v.length() <= 300 ? v : v.substring(0, 300);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
