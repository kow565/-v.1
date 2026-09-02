package com.kow565.perchancecompanion;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class InboxActivity extends Activity {
    public static final String WEB_APP_URL = "https://perchance.org/87fjsh5tkf";
    private boolean launched;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        HarinKeepAliveService.start(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != getPackageManager().PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!launched) {
            launched = true;
            openHarin();
        }
    }

    private void openHarin() {
        Uri url = Uri.parse(WEB_APP_URL);
        Intent chrome = new Intent(Intent.ACTION_VIEW, url);
        chrome.setPackage("com.android.chrome");
        chrome.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(chrome);
        } catch (ActivityNotFoundException missingChrome) {
            Intent browser = new Intent(Intent.ACTION_VIEW, url);
            browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(browser);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(40), dp(28), dp(40));
        root.setBackgroundColor(Color.rgb(250, 247, 249));

        TextView mark = new TextView(this);
        mark.setText("H");
        mark.setTextSize(42);
        mark.setTextColor(Color.rgb(199, 82, 137));
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setGravity(Gravity.CENTER);
        root.addView(mark, new LinearLayout.LayoutParams(dp(90), dp(90)));

        TextView title = new TextView(this);
        title.setText("Harin");
        title.setTextSize(30);
        title.setTextColor(Color.rgb(25, 25, 25));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap(dp(10)));

        TextView description = new TextView(this);
        description.setText("실제 Chrome에서 Perchance와 연결해요.\n대화와 장면 이미지는 휴대폰에 보관됩니다.");
        description.setTextSize(15);
        description.setTextColor(Color.DKGRAY);
        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(0, 1.25f);
        root.addView(description, matchWrap(dp(14)));

        Button open = new Button(this);
        open.setText("Chrome에서 Harin 열기");
        open.setTextSize(16);
        open.setAllCaps(false);
        open.setOnClickListener(v -> openHarin());
        root.addView(open, matchWrap(dp(28)));

        Button battery = new Button(this);
        battery.setText("배터리 최적화 제외 화면 열기");
        battery.setTextSize(14);
        battery.setAllCaps(false);
        battery.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
            catch (Throwable ignored) { startActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)); }
        });
        root.addView(battery, matchWrap(dp(8)));

        Button appSettings = new Button(this);
        appSettings.setText("Harin 앱 설정 열기");
        appSettings.setTextSize(14);
        appSettings.setAllCaps(false);
        appSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        root.addView(appSettings, matchWrap(dp(8)));

        TextView note = new TextView(this);
        note.setText("배터리 화면에서 Harin을 '제한 없음'으로 바꿔 주세요.\n상단 알림을 누르면 언제든 대화를 다시 열 수 있어요.");
        note.setTextSize(12);
        note.setTextColor(Color.GRAY);
        note.setGravity(Gravity.CENTER);
        root.addView(note, matchWrap(dp(18)));

        setContentView(root);
    }

    private LinearLayout.LayoutParams matchWrap(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = top;
        return p;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
