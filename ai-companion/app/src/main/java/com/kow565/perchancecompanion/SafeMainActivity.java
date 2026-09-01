package com.kow565.perchancecompanion;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SafeMainActivity extends MainActivity {
    private Thread.UncaughtExceptionHandler previousHandler;

    @Override protected void onCreate(Bundle savedInstanceState) {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            if (thread != Looper.getMainLooper().getThread()) {
                try {
                    getSharedPreferences("harin_crash_guard", MODE_PRIVATE).edit()
                            .putString("last_background_error", error.getClass().getName() + ": " + String.valueOf(error.getMessage()))
                            .putLong("last_background_error_time", System.currentTimeMillis())
                            .apply();
                } catch (Throwable ignored) {}
                return;
            }
            if (previousHandler != null) previousHandler.uncaughtException(thread, error);
        });

        try {
            super.onCreate(savedInstanceState);
        } catch (Throwable error) {
            showRecoveryScreen(error);
        }
    }

    private void showRecoveryScreen(Throwable error) {
        try {
            ScrollView scroll = new ScrollView(this);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(22), dp(30), dp(22), dp(30));
            root.setBackgroundColor(Color.WHITE);
            scroll.addView(root);

            TextView title = new TextView(this);
            title.setText("Harin 복구 모드");
            title.setTextSize(24);
            title.setTextColor(Color.BLACK);
            title.setGravity(Gravity.CENTER_HORIZONTAL);
            root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView message = new TextView(this);
            message.setText("시작 중 오류를 감지해서 앱이 종료되는 대신 복구 화면을 열었어.\n\n오류: " +
                    error.getClass().getSimpleName() + "\n" + String.valueOf(error.getMessage()));
            message.setTextSize(14);
            message.setTextColor(Color.DKGRAY);
            message.setPadding(0, dp(20), 0, dp(18));
            root.addView(message);

            Button retry = new Button(this);
            retry.setText("다시 열기");
            retry.setOnClickListener(v -> recreate());
            root.addView(retry);

            Button reset = new Button(this);
            reset.setText("로컬 대화/설정 초기화 후 다시 열기");
            reset.setOnClickListener(v -> {
                try { getSharedPreferences("harin_companion_store_v1", MODE_PRIVATE).edit().clear().commit(); }
                catch (Throwable ignored) {}
                recreate();
            });
            root.addView(reset);

            setContentView(scroll);
        } catch (Throwable fatal) {
            if (previousHandler != null) previousHandler.uncaughtException(Thread.currentThread(), fatal);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
