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

import org.json.JSONObject;

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
        title.setText("Perchance 브라우저 연결");
        title.setTextSize(23);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView info = new TextView(this);
        info.setText("이 버전은 userKey를 앱에 복사해 두는 방식보다, Perchance의 실제 text/image embed를 앱 안에서 계속 실행하는 방식을 우선 사용해. 쿠키·브라우저 검증·세션 재검증은 WebView 안에 유지되고, 멈춘 요청은 새 embed로 한 번 자동 재시도해.");
        info.setTextSize(14);
        info.setTextColor(Color.DKGRAY);
        info.setPadding(0, dp(14), 0, dp(18));
        root.addView(info);

        status = new TextView(this);
        status.setText("브라우저 세션 준비 중…");
        status.setTextSize(18);
        status.setTextColor(Color.BLACK);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        status.setPadding(0, dp(12), 0, dp(8));
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        detail = new TextView(this);
        detail.setText("앱 화면이 열린 뒤 몇 초 동안 text-generation / image-generation embed를 미리 불러와.");
        detail.setTextSize(13);
        detail.setTextColor(Color.GRAY);
        detail.setPadding(0, 0, 0, dp(18));
        root.addView(detail);

        Button rebuild = button("브라우저 세션 새로 만들기");
        rebuild.setOnClickListener(v -> {
            detail.setText("기존 embed를 버리고 새 세션을 만드는 중…");
            PerchanceBrowserTransport.unbind(this);
            PerchanceBrowserTransport.bind(this);
        });
        root.addView(rebuild, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button textTest = button("텍스트 연결 테스트");
        textTest.setOnClickListener(v -> testText());
        root.addView(textTest, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button imageTest = button("이미지 연결 테스트");
        imageTest.setOnClickListener(v -> testImage());
        root.addView(imageTest, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button done = button("DM으로 돌아가기");
        done.setOnClickListener(v -> finish());
        root.addView(done, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void testText() {
        detail.setText("텍스트 embed에 실제 짧은 요청을 보내는 중…");
        new Thread(() -> {
            try {
                String result = PerchanceBrowserTransport.generateText("Reply with exactly: OK");
                runOnUiThread(() -> detail.setText("텍스트 테스트 성공 ✓ · 응답: " + compact(result)));
            } catch (Exception e) {
                runOnUiThread(() -> detail.setText("텍스트 테스트 실패 · " + compact(e.getMessage())));
            }
        }, "perchance-text-test").start();
    }

    private void testImage() {
        detail.setText("이미지 embed에 실제 테스트 요청을 보내는 중…");
        new Thread(() -> {
            try {
                JSONObject result = PerchanceBrowserTransport.generateImage(
                        "a simple neutral gray ceramic mug on a plain table, realistic photo",
                        "text, watermark, malformed object", -1);
                String id = result.optString("imageId", "");
                runOnUiThread(() -> detail.setText("이미지 테스트 성공 ✓ · imageId: " + compact(id)));
            } catch (Exception e) {
                runOnUiThread(() -> detail.setText("이미지 테스트 실패 · " + compact(e.getMessage())));
            }
        }, "perchance-image-test").start();
    }

    private void refreshStatusLoop() {
        if (!polling) return;
        status.setText(PerchanceBrowserTransport.statusSummary());
        handler.postDelayed(this::refreshStatusLoop, 600);
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
        return v.length() <= 120 ? v : v.substring(0, 120);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
