package com.kow565.perchancecompanion;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class DeveloperEditorActivity extends Activity {
    private CompanionStore store;
    private TextView current;
    private EditText request;
    private TextView result;
    private Button apply;
    private Button copyRebuild;
    private volatile boolean busy = false;
    private DeveloperAi.EditResult pending;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new CompanionStore(this);
        buildUi();
        refreshCurrent();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(24);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(52), dp(48)));
        TextView title = new TextView(this);
        title.setText("AI 앱 편집기");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(top);

        TextView intro = new TextView(this);
        intro.setText("원하는 변경을 자연어로 적으면 Perchance 텍스트 AI가 현재 앱 설정을 읽고 수정안을 만들어. 선톡/스토리 주기, 사진 빈도, 말투·행동, 이미지 스타일은 즉시 적용할 수 있고, UI나 새 기능처럼 코드 수정이 필요한 요청은 다음 APK 빌드용 요청으로 분리해줘.");
        intro.setTextSize(14);
        intro.setTextColor(Color.DKGRAY);
        intro.setPadding(0, dp(8), 0, dp(16));
        root.addView(intro);

        root.addView(label("현재 적용값"));
        current = panelText();
        root.addView(current, wrapWithMargin(0, dp(8)));

        TextView requestLabel = label("어떻게 바꿀까?");
        requestLabel.setPadding(0, dp(18), 0, dp(6));
        root.addView(requestLabel);

        request = new EditText(this);
        request.setHint("예: 선톡은 30~90분 사이로 하고, 밤 2시부터 아침 9시까지는 쉬어. 사진은 대략 두세 번 대화할 때마다 보내고 말투는 더 장난스럽게 해줘.");
        request.setMinLines(5);
        request.setGravity(Gravity.TOP);
        request.setTextSize(15);
        request.setPadding(dp(14), dp(12), dp(14), dp(12));
        request.setBackground(roundRect(Color.rgb(248,248,248), dp(14), Color.rgb(220,220,220), 1));
        root.addView(request, wrapWithMargin(0, dp(10)));

        Button ask = new Button(this);
        ask.setText("AI에게 수정안 만들기");
        ask.setAllCaps(false);
        ask.setOnClickListener(v -> askEditor());
        root.addView(ask);

        result = panelText();
        result.setText("아직 수정안이 없어.");
        root.addView(result, wrapWithMargin(dp(10), dp(8)));

        apply = new Button(this);
        apply.setText("이 수정안 즉시 적용");
        apply.setAllCaps(false);
        apply.setEnabled(false);
        apply.setOnClickListener(v -> applyPending());
        root.addView(apply);

        copyRebuild = new Button(this);
        copyRebuild.setText("다음 APK 수정 요청 복사");
        copyRebuild.setAllCaps(false);
        copyRebuild.setEnabled(false);
        copyRebuild.setOnClickListener(v -> copyRebuildRequest());
        root.addView(copyRebuild);

        Button reset = new Button(this);
        reset.setText("AI 편집 설정 기본값으로 되돌리기");
        reset.setAllCaps(false);
        reset.setOnClickListener(v -> {
            store.resetRuntimeConfig();
            pending = null;
            apply.setEnabled(false);
            copyRebuild.setEnabled(false);
            result.setText("기본 설정으로 되돌렸어.");
            refreshCurrent();
        });
        root.addView(reset);

        TextView note = new TextView(this);
        note.setText("이 편집기는 앱이 자기 APK 바이너리를 직접 덮어쓰지 않아. 즉시 바꿀 수 있는 값은 로컬 설정으로 적용하고, 네이티브 코드 변경은 GitHub에서 다음 버전 APK를 빌드하는 방식으로 분리해.");
        note.setTextSize(12);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(12), 0, 0);
        root.addView(note);

        setContentView(scroll);
    }

    private void askEditor() {
        if (busy) return;
        String text = request.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "바꾸고 싶은 내용을 적어줘.", Toast.LENGTH_SHORT).show();
            return;
        }
        busy = true;
        apply.setEnabled(false);
        copyRebuild.setEnabled(false);
        result.setText("AI가 현재 설정을 읽고 수정안을 만드는 중…");
        new Thread(() -> {
            try {
                DeveloperAi.EditResult r = new DeveloperAi().suggestEdit(store, text);
                pending = r;
                runOnUiThread(() -> {
                    StringBuilder b = new StringBuilder();
                    b.append(r.summary.isEmpty() ? "수정안을 만들었어." : r.summary);
                    if (r.patch.length() > 0) b.append("\n\n즉시 적용 패치:\n").append(r.patch.toString());
                    if (r.requiresRebuild) b.append("\n\n다음 APK 빌드 필요:\n").append(r.rebuildRequest);
                    result.setText(b.toString());
                    apply.setEnabled(r.patch.length() > 0);
                    copyRebuild.setEnabled(r.requiresRebuild && !r.rebuildRequest.isEmpty());
                });
            } catch (Exception e) {
                runOnUiThread(() -> result.setText("편집 요청 실패: " + safeMessage(e)));
            } finally {
                busy = false;
            }
        }).start();
    }

    private void applyPending() {
        if (pending == null || pending.patch.length() == 0) return;
        store.applyRuntimeConfig(pending.patch);
        CompanionJobService.schedule(this);
        result.append("\n\n✓ 이 기기에서 바로 적용했어.");
        apply.setEnabled(false);
        refreshCurrent();
    }

    private void copyRebuildRequest() {
        if (pending == null || pending.rebuildRequest.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("AI Companion APK edit request", pending.rebuildRequest));
            Toast.makeText(this, "다음 APK 수정 요청을 복사했어.", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshCurrent() {
        current.setText(store.runtimeConfigSummary() +
                (store.behaviorInstructions().isEmpty() ? "" : "\n행동 지시: " + store.behaviorInstructions()) +
                (store.visualInstructions().isEmpty() ? "" : "\n이미지 지시: " + store.visualInstructions()));
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(17);
        v.setTextColor(Color.BLACK);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private TextView panelText() {
        TextView v = new TextView(this);
        v.setTextSize(13);
        v.setTextColor(Color.DKGRAY);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setBackground(roundRect(Color.rgb(248,248,248), dp(12), Color.rgb(225,225,225), 1));
        return v;
    }

    private LinearLayout.LayoutParams wrapWithMargin(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, top, 0, bottom);
        return p;
    }

    private GradientDrawable roundRect(int color, int radius, int strokeColor, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) return e.getClass().getSimpleName();
        return m.length() > 180 ? m.substring(0, 180) : m;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
