package com.kow565.perchancecompanion;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class InboxActivity extends Activity {
    private LinearLayout dmList;
    private TextView connectionStatus;
    private CharacterLibrary library;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        library = new CharacterLibrary(this);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        library = new CharacterLibrary(this);
        renderConnection();
        renderDmList();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setFitsSystemWindows(true);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(13), dp(10), dp(9));
        TextView title = new TextView(this);
        title.setText("메시지");
        title.setTextSize(25);
        title.setTextColor(Color.BLACK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button add = compactButton("＋");
        add.setContentDescription("새 캐릭터 추가");
        add.setOnClickListener(v -> startActivity(new Intent(this, StudioActivity.class)));
        header.addView(add);
        root.addView(header);

        LinearLayout connectBar = new LinearLayout(this);
        connectBar.setGravity(Gravity.CENTER_VERTICAL);
        connectBar.setPadding(dp(14), dp(7), dp(10), dp(8));
        connectBar.setBackgroundColor(Color.rgb(249,249,249));
        connectionStatus = new TextView(this);
        connectionStatus.setTextSize(12);
        connectionStatus.setTextColor(Color.DKGRAY);
        connectBar.addView(connectionStatus, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button connect = compactButton("Perchance 연결");
        connect.setTextSize(11);
        connect.setOnClickListener(v -> startActivity(new Intent(this, PerchanceConnectActivity.class)));
        connectBar.addView(connect);
        root.addView(connectBar);

        TextView hint = new TextView(this);
        hint.setText("AI 캐릭터마다 대화와 사진 상태가 따로 유지돼");
        hint.setTextSize(12);
        hint.setTextColor(Color.GRAY);
        hint.setPadding(dp(16), dp(10), dp(16), dp(7));
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        dmList = new LinearLayout(this);
        dmList.setOrientation(LinearLayout.VERTICAL);
        dmList.setPadding(dp(8), 0, dp(8), dp(24));
        scroll.addView(dmList);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private void renderConnection() {
        if (connectionStatus == null) return;
        if (PerchanceSession.isReady(this)) {
            connectionStatus.setText("● Perchance 연결됨");
            connectionStatus.setTextColor(Color.rgb(20,130,65));
        } else {
            connectionStatus.setText("● Perchance 연결 필요 · 대화/이미지 생성 전 한 번 연결해줘");
            connectionStatus.setTextColor(Color.rgb(190,70,40));
        }
    }

    private void renderDmList() {
        if (dmList == null) return;
        dmList.removeAllViews();
        JSONArray arr = library.characters();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c == null) continue;
            final JSONObject character = c;
            String id = c.optString("id", "");
            CompanionStore store = new CompanionStore(this, id);
            if (store.messages().length() == 0) store.initializeCharacter(c.optString("name", "하린"), c.optJSONObject("state"), c.optInt("seed", 428731), false);

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(10), dp(8), dp(10));
            row.setBackgroundColor(Color.WHITE);

            TextView avatar = avatar(c.optString("name", "AI"));
            row.addView(avatar, new LinearLayout.LayoutParams(dp(58), dp(58)));

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(12), 0, dp(8), 0);
            TextView name = new TextView(this);
            name.setText(c.optString("name", "캐릭터"));
            name.setTextSize(16);
            name.setTextColor(Color.BLACK);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            texts.addView(name);

            TextView preview = new TextView(this);
            preview.setText(store.lastMessagePreview());
            preview.setTextSize(13);
            preview.setTextColor(Color.rgb(100,100,100));
            preview.setSingleLine(true);
            texts.addView(preview);
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView time = new TextView(this);
            time.setText(formatTime(store.lastMessageTime()));
            time.setTextSize(11);
            time.setTextColor(Color.GRAY);
            row.addView(time);

            row.setOnClickListener(v -> openDm(character));
            dmList.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));

            View divider = new View(this);
            divider.setBackgroundColor(Color.rgb(242,242,242));
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
            dp.setMargins(dp(78), 0, dp(8), 0);
            dmList.addView(divider, dp);
        }

        TextView addMore = new TextView(this);
        addMore.setText("＋ 새 AI 캐릭터 만들기");
        addMore.setTextSize(14);
        addMore.setTextColor(Color.rgb(0,110,220));
        addMore.setGravity(Gravity.CENTER);
        addMore.setPadding(dp(8), dp(18), dp(8), dp(18));
        addMore.setOnClickListener(v -> startActivity(new Intent(this, StudioActivity.class)));
        dmList.addView(addMore);
    }

    private void openDm(JSONObject character) {
        library.activateInChat(this, character, false);
        startActivity(new Intent(this, SafeMainActivity.class));
    }

    private TextView avatar(String name) {
        TextView v = new TextView(this);
        String letter = name == null || name.isEmpty() ? "AI" : name.substring(0, 1);
        v.setText(letter);
        v.setTextSize(21);
        v.setTextColor(Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        int hash = Math.abs((name == null ? 0 : name.hashCode()));
        g.setColor(Color.rgb(90 + hash % 100, 80 + (hash / 7) % 110, 120 + (hash / 13) % 90));
        v.setBackground(g);
        return v;
    }

    private String formatTime(long t) {
        if (t <= 0) return "";
        long diff = System.currentTimeMillis() - t;
        if (diff < 60L * 60L * 1000L) return Math.max(1, diff / 60000L) + "분";
        if (diff < 24L * 60L * 60L * 1000L) return diff / 3600000L + "시간";
        return new SimpleDateFormat("M/d", Locale.KOREA).format(new Date(t));
    }

    private Button compactButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        return b;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
