package com.kow565.perchancecompanion;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

public class PerchanceConnectActivity extends Activity {
    private TextView status;
    private TextView detail;
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(30), dp(22), dp(24));
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Perchance 플러그인 진단");
        title.setTextSize(23);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView info = new TextView(this);
        info.setText("v0.4부터는 userKey나 내부 /api/generate를 주 경로로 사용하지 않아. Perchance의 실제 AI Character Chat generator runtime을 열고, 그 안에 import된 ai-text-plugin과 text-to-image-plugin 함수를 그대로 호출해. 아래 테스트는 그 플러그인 자체를 검사해.");
        info.setTextSize(14);
        info.setTextColor(Color.DKGRAY);
        info.setPadding(0, dp(14), 0, dp(18));
        root.addView(info);

        status = new TextView(this);
        status.setText("generator runtime 준비 중…");
        status.setTextSize(17);
        status.setTextColor(Color.BLACK);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        status.setPadding(0, dp(12), 0, dp(8));
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        detail = new TextView(this);
        detail.setText("상태를 읽는 중…");
        detail.setTextSize(12);
        detail.setTextColor(Color.GRAY);
        detail.setPadding(0, 0, 0, dp(18));
        root.addView(detail);

        Button rebuild = button("플러그인 런타임 새로 만들기");
        rebuild.setOnClickListener(v -> {
            detail.setText("Perchance generator를 처음부터 다시 여는 중…");
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
        setContentView(root);
    }

    private void testText() {
        detail.setText("root.aiTextPlugin에 실제 요청을 보내는 중…");
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
        detail.setText("root.textToImagePlugin에 실제 요청을 보내는 중…");
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
