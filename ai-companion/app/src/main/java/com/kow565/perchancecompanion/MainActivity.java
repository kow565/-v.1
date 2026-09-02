package com.kow565.perchancecompanion;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final ExecutorService IMAGE_QUEUE = Executors.newSingleThreadExecutor();
    private CompanionStore store;
    private LinearLayout storyRow;
    private LinearLayout chatContainer;
    private ScrollView chatScroll;
    private EditText input;
    private TextView title;
    private TextView headerAvatar;
    private volatile boolean busy = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new CompanionStore(this);
        buildUi();
        CompanionJobService.schedule(this);
        requestNotifications();
        if (store.messages().length() == 0) store.addMessage("ai", "자기 왔어? 나 방금 좀 쉬고 있었어 🙂", "");
        renderAll();
        if (store.stories().length() == 0) generateInitialStory();
    }

    @Override protected void onResume() {
        super.onResume();
        store = new CompanionStore(this);
        if (chatContainer != null) renderAll();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setFitsSystemWindows(true);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(8), dp(10));
        header.setBackgroundColor(Color.WHITE);

        headerAvatar = avatarView(avatarLetter(), 42);
        header.addView(headerAvatar);

        LinearLayout nameBox = new LinearLayout(this);
        nameBox.setOrientation(LinearLayout.VERTICAL);
        nameBox.setPadding(dp(10), 0, 0, 0);
        title = new TextView(this);
        title.setText(store.aiName());
        title.setTextSize(17);
        title.setTextColor(Color.BLACK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView active = new TextView(this);
        active.setText("활동 중 · 가까운 친구");
        active.setTextColor(Color.rgb(110,110,110));
        active.setTextSize(12);
        nameBox.addView(title);
        nameBox.addView(active);
        header.addView(nameBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button studio = tinyButton("✦");
        studio.setContentDescription("이미지 및 캐릭터 스튜디오");
        studio.setOnClickListener(v -> startActivity(new Intent(this, StudioActivity.class)));
        header.addView(studio);

        Button settings = tinyButton("⚙");
        settings.setOnClickListener(v -> showSettings());
        header.addView(settings);
        root.addView(header);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(235,235,235));
        root.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        HorizontalScrollView storyScroll = new HorizontalScrollView(this);
        storyScroll.setHorizontalScrollBarEnabled(false);
        storyRow = new LinearLayout(this);
        storyRow.setOrientation(LinearLayout.HORIZONTAL);
        storyRow.setPadding(dp(12), dp(9), dp(12), dp(7));
        storyScroll.addView(storyRow);
        root.addView(storyScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92)));

        View divider2 = new View(this);
        divider2.setBackgroundColor(Color.rgb(242,242,242));
        root.addView(divider2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setClipToPadding(false);
        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(dp(10), dp(12), dp(10), dp(14));
        chatScroll.addView(chatContainer);
        root.addView(chatScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(10), dp(8), dp(10), dp(10));
        input = new EditText(this);
        input.setHint("메시지...");
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setTextSize(15);
        input.setPadding(dp(15), dp(8), dp(15), dp(8));
        input.setBackground(roundRect(Color.rgb(247,247,247), dp(22), Color.rgb(225,225,225), 1));
        composer.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button send = tinyButton("보내기");
        send.setTextColor(Color.rgb(0,120,255));
        send.setOnClickListener(v -> sendMessage());
        composer.addView(send);
        root.addView(composer);

        setContentView(root);
    }

    private void renderAll() {
        title.setText(store.aiName());
        if (headerAvatar != null) headerAvatar.setText(avatarLetter());
        renderStories();
        renderMessages();
    }

    private void renderStories() {
        storyRow.removeAllViews();
        JSONArray stories = store.stories();
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(dp(4), 0, dp(10), 0);

        FrameLayout ring = new FrameLayout(this);
        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setShape(GradientDrawable.OVAL);
        ringBg.setColor(Color.WHITE);
        ringBg.setStroke(dp(stories.length() > 0 ? 3 : 1), stories.length() > 0 ? Color.rgb(225,45,115) : Color.LTGRAY);
        ring.setBackground(ringBg);
        ring.setPadding(dp(4), dp(4), dp(4), dp(4));
        ring.addView(avatarView(avatarLetter(), 54), new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER));
        item.addView(ring, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView label = new TextView(this);
        label.setText(store.aiName());
        label.setTextSize(11);
        label.setTextColor(Color.DKGRAY);
        label.setGravity(Gravity.CENTER);
        item.addView(label, new LinearLayout.LayoutParams(dp(70), dp(20)));
        if (stories.length() > 0) item.setOnClickListener(v -> showStory(stories.optJSONObject(stories.length() - 1)));
        storyRow.addView(item);

        if (stories.length() > 1) {
            TextView more = new TextView(this);
            more.setText("스토리 " + stories.length() + "개\n자동 업로드 중");
            more.setTextSize(12);
            more.setGravity(Gravity.CENTER_VERTICAL);
            more.setTextColor(Color.GRAY);
            storyRow.addView(more, new LinearLayout.LayoutParams(dp(120), dp(74)));
        }
    }

    private void renderMessages() {
        chatContainer.removeAllViews();
        JSONArray arr = store.messages();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject m = arr.optJSONObject(i);
            if (m != null) addBubble(m);
        }
        if (busy) {
            TextView typing = new TextView(this);
            typing.setText(store.aiName() + " 입력 중…");
            typing.setTextColor(Color.GRAY);
            typing.setTextSize(12);
            typing.setPadding(dp(12), dp(8), 0, dp(8));
            chatContainer.addView(typing);
        }
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addBubble(JSONObject m) {
        boolean mine = "user".equals(m.optString("role"));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        row.setPadding(0, dp(3), 0, dp(3));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(3), dp(3), dp(3), dp(3));
        bubble.setBackground(roundRect(mine ? Color.rgb(55,151,240) : Color.rgb(239,239,239), dp(18), Color.TRANSPARENT, 0));

        String imagePath = m.optString("image", "");
        if (!imagePath.isEmpty() && new File(imagePath).exists()) {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageURI(android.net.Uri.fromFile(new File(imagePath)));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(235), dp(315));
            ip.setMargins(0, 0, 0, dp(5));
            bubble.addView(img, ip);
            img.setOnClickListener(v -> showPhoto(imagePath));
        }

        TextView text = new TextView(this);
        text.setText(m.optString("text") + (m.optBoolean("edited", false) ? "  (수정됨)" : ""));
        text.setTextSize(15);
        text.setTextColor(mine ? Color.WHITE : Color.BLACK);
        text.setPadding(dp(11), dp(8), dp(11), dp(8));
        bubble.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        String imageStatus = m.optString("imageStatus", "");
        if (imagePath.isEmpty() && !imageStatus.isEmpty()) {
            TextView photoState = new TextView(this);
            photoState.setText(imageStatus);
            photoState.setTextSize(11);
            photoState.setTextColor(mine ? Color.rgb(220, 238, 255) : Color.DKGRAY);
            photoState.setPadding(dp(11), 0, dp(11), dp(8));
            bubble.addView(photoState);
            if (imageStatus.startsWith("사진 실패")) {
                photoState.setText(imageStatus + " · 눌러서 재시도");
                photoState.setOnClickListener(v -> queueMessageImage(
                        CompanionStore.messageKey(m), m.optString("role"), m.optString("text"), "", false));
            }
        }
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.width = Math.min(getResources().getDisplayMetrics().widthPixels * 4 / 5, dp(330));
        row.addView(bubble, bp);
        chatContainer.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (mine) {
            bubble.setOnLongClickListener(v -> {
                editSentMessage(m);
                return true;
            });
        }
    }

    private void editSentMessage(JSONObject message) {
        final String key = CompanionStore.messageKey(message);
        final EditText editor = new EditText(this);
        editor.setText(message.optString("text", ""));
        editor.setSelection(editor.length());
        editor.setSingleLine(false);
        editor.setPadding(dp(18), dp(10), dp(18), dp(10));
        new AlertDialog.Builder(this)
                .setTitle("보낸 메시지 수정")
                .setView(editor)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    String changed = editor.getText().toString().trim();
                    if (!store.updateMessageText(key, changed)) return;
                    renderMessages();
                    queueMessageImage(key, "user", changed, "", false);
                })
                .show();
    }

    private void sendMessage() {
        if (busy) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");
        String userMessageId = store.addMessageWithId("user", text, "");
        store.setMessageImageStatus(userMessageId, "사진 대기 중…");
        busy = true;
        renderMessages();

        new Thread(() -> {
            try {
                AiEngine engine = new AiEngine();
                AiEngine.Turn t = engine.chatTurn(store, text, false);
                store.applyState(t.state);
                String aiMessageId = store.addMessageWithId("ai", t.reply, "");
                store.setMessageImageStatus(aiMessageId, "사진 대기 중…");
                busy = false;
                runOnUiThread(this::renderAll);
                queueMessageImage(userMessageId, "user", text, "", false);
                queueMessageImage(aiMessageId, "ai", t.reply, t.imagePrompt, true);
            } catch (Exception e) {
                store.addMessage("ai", "지금 잠깐 연결이 불안한가 봐. 조금 있다가 다시 말 걸어줘 🥲", "");
                busy = false;
                runOnUiThread(this::renderAll);
            }
        }).start();
    }

    private void queueMessageImage(String messageId, String role, String text, String hint, boolean countAiTurn) {
        store.setMessageImageStatus(messageId, "사진 대기 중…");
        runOnUiThread(this::renderAll);
        IMAGE_QUEUE.execute(() -> {
            store.setMessageImageStatus(messageId, "사진 생성 중…");
            runOnUiThread(this::renderAll);
            try {
                AiEngine engine = new AiEngine();
                String image = engine.generateMessageImage(getApplicationContext(), store, role, text, hint);
                store.setMessageImage(messageId, image);
                if (countAiTurn) store.bumpAiTurn(true);
            } catch (Exception e) {
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                reason = reason.replace('\n', ' ').trim();
                if (reason.length() > 90) reason = reason.substring(0, 90);
                store.setMessageImageStatus(messageId, "사진 실패: " + reason);
                if (countAiTurn) store.bumpAiTurn(false);
            }
            runOnUiThread(this::renderAll);
        });
    }

    private void generateInitialStory() {
        new Thread(() -> {
            try {
                AiEngine engine = new AiEngine();
                AiEngine.StoryTurn st = engine.storyTurn(store);
                store.applyState(st.state);
                String image = engine.generateImage(this, store, st.imagePrompt);
                store.addStory(st.caption, image);
                runOnUiThread(this::renderStories);
            } catch (Exception ignored) {}
        }).start();
    }

    private void showStory(JSONObject story) {
        if (story == null) return;
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        String path = story.optString("image", "");
        if (!path.isEmpty() && new File(path).exists()) {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageURI(android.net.Uri.fromFile(new File(path)));
            frame.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        TextView bar = new TextView(this);
        bar.setBackgroundColor(Color.WHITE);
        FrameLayout.LayoutParams barP = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        barP.setMargins(dp(10), dp(18), dp(10), 0);
        frame.addView(bar, barP);
        TextView name = new TextView(this);
        name.setText(store.aiName() + "  ·  story");
        name.setTextColor(Color.WHITE);
        name.setTextSize(16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setPadding(dp(18), dp(32), dp(10), dp(10));
        frame.addView(name);
        TextView caption = new TextView(this);
        caption.setText(story.optString("caption"));
        caption.setTextColor(Color.WHITE);
        caption.setTextSize(18);
        caption.setGravity(Gravity.CENTER);
        caption.setShadowLayer(4, 0, 1, Color.BLACK);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        cp.setMargins(dp(20), 0, dp(20), dp(60));
        frame.addView(caption, cp);
        frame.setOnClickListener(v -> d.dismiss());
        d.setContentView(frame);
        d.show();
    }

    private void showPhoto(String path) {
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView img = new ImageView(this);
        img.setBackgroundColor(Color.BLACK);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setImageURI(android.net.Uri.fromFile(new File(path)));
        img.setOnClickListener(v -> d.dismiss());
        d.setContentView(img);
        d.show();
    }

    private void showSettings() {
        Dialog d = new Dialog(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(20), dp(22), dp(18));
        TextView h = new TextView(this);
        h.setText("프로필 · 앱 설정");
        h.setTextSize(20);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(Color.BLACK);
        box.addView(h);

        EditText ai = new EditText(this);
        ai.setHint("AI 이름");
        ai.setText(store.aiName());
        box.addView(ai);
        EditText user = new EditText(this);
        user.setHint("나를 부를 이름");
        user.setText(store.userName());
        box.addView(user);

        TextView note = new TextView(this);
        note.setText(store.runtimeConfigSummary());
        note.setTextSize(12);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(8), 0, dp(10));
        box.addView(note);

        Button save = new Button(this);
        save.setText("이름 저장");
        save.setOnClickListener(v -> {
            store.setNames(ai.getText().toString(), user.getText().toString());
            d.dismiss();
            renderAll();
        });
        box.addView(save);

        Button studio = new Button(this);
        studio.setText("이미지 · 캐릭터 스튜디오");
        studio.setOnClickListener(v -> {
            d.dismiss();
            startActivity(new Intent(this, StudioActivity.class));
        });
        box.addView(studio);

        Button editor = new Button(this);
        editor.setText("AI로 앱 수정");
        editor.setOnClickListener(v -> {
            d.dismiss();
            startActivity(new Intent(this, DeveloperEditorActivity.class));
        });
        box.addView(editor);

        Button clear = new Button(this);
        clear.setText("대화/스토리 초기화");
        clear.setOnClickListener(v -> {
            store.clearConversation();
            store.addMessage("ai", "다시 시작해볼까? 🙂", "");
            d.dismiss();
            renderAll();
        });
        box.addView(clear);

        d.setContentView(box);
        d.show();
        if (d.getWindow() != null) d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.9), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private String avatarLetter() {
        String n = store.aiName();
        if (n == null || n.isEmpty()) return "AI";
        return n.substring(0, 1);
    }

    private TextView avatarView(String letter, int size) {
        TextView v = new TextView(this);
        v.setText(letter);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(size * 0.38f);
        v.setTextColor(Color.WHITE);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setBackground(roundRect(Color.rgb(190,90,145), dp(size/2), Color.TRANSPARENT, 0));
        v.setLayoutParams(new ViewGroup.LayoutParams(dp(size), dp(size)));
        return v;
    }

    private Button tinyButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(10), dp(7), dp(10), dp(7));
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private GradientDrawable roundRect(int color, int radius, int strokeColor, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 565);
        }
    }
}
