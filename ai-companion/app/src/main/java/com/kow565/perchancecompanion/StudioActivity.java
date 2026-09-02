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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Random;

public class StudioActivity extends Activity {
    private CompanionStore store;
    private CharacterLibrary library;
    private ImageView characterPreview;
    private ImageView freePreview;
    private TextView characterStatus;
    private LinearLayout savedList;
    private Button usePendingButton;
    private JSONObject pendingCharacter;
    private volatile boolean busy = false;
    private final Random random = new Random();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new CompanionStore(this);
        library = new CharacterLibrary(this);
        buildUi();
        renderSavedCharacters();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(30));
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
        title.setText("이미지 · 캐릭터 스튜디오");
        title.setTextSize(21);
        title.setTextColor(Color.BLACK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(top);

        TextView intro = new TextView(this);
        intro.setText("대화 내용과 무관하게 이미지를 만들거나 새 캐릭터를 설계할 수 있어. 여기서 만든 자유 이미지는 채팅 상태를 바꾸지 않고, 저장한 캐릭터만 원할 때 대화 상대에 적용돼.");
        intro.setTextSize(14);
        intro.setTextColor(Color.DKGRAY);
        intro.setPadding(0, dp(6), 0, dp(16));
        root.addView(intro);

        root.addView(sectionTitle("새 캐릭터 만들기"));
        EditText name = editor("캐릭터 이름 (비워도 됨)", 1);
        root.addView(name);
        EditText description = editor("외모, 헤어, 분위기, 기본 의상 등을 자연어로 적어줘. 예: 25살 한국인 여성, 긴 흑발, 차분한 인상, 캐주얼한 스타일...", 5);
        root.addView(description);

        Button create = actionButton("AI로 캐릭터 설계 + 미리보기 생성");
        create.setOnClickListener(v -> {
            String desc = description.getText().toString().trim();
            if (desc.isEmpty()) {
                Toast.makeText(this, "캐릭터 설명을 적어줘.", Toast.LENGTH_SHORT).show();
                return;
            }
            createCharacter(name.getText().toString(), desc);
        });
        root.addView(create);

        characterPreview = previewView();
        root.addView(characterPreview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(430)));
        characterPreview.setVisibility(View.GONE);

        characterStatus = new TextView(this);
        characterStatus.setTextSize(13);
        characterStatus.setTextColor(Color.DKGRAY);
        characterStatus.setPadding(dp(4), dp(8), dp(4), dp(8));
        root.addView(characterStatus);

        usePendingButton = actionButton("이 캐릭터로 새 대화 시작");
        usePendingButton.setEnabled(false);
        usePendingButton.setOnClickListener(v -> {
            if (pendingCharacter != null) activateCharacter(pendingCharacter);
        });
        root.addView(usePendingButton);

        root.addView(space(18));
        root.addView(sectionTitle("대화와 무관한 자유 이미지"));
        EditText freePrompt = editor("이미지 설명을 적어줘. 이 생성은 현재 DM이나 스토리 상태를 바꾸지 않아.", 4);
        root.addView(freePrompt);

        LinearLayout imageButtons = new LinearLayout(this);
        imageButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button free = actionButton("완전 자유 생성");
        Button active = actionButton("현재 캐릭터로 생성");
        free.setOnClickListener(v -> generateStandalone(freePrompt.getText().toString(), false));
        active.setOnClickListener(v -> generateStandalone(freePrompt.getText().toString(), true));
        imageButtons.addView(free, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        imageButtons.addView(active, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(imageButtons);

        freePreview = previewView();
        freePreview.setVisibility(View.GONE);
        root.addView(freePreview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(430)));

        TextView freeNote = new TextView(this);
        freeNote.setText("‘현재 캐릭터로 생성’도 채팅 시간선에는 영향을 주지 않고, 얼굴/외형 기준만 가져와서 별도의 이미지를 만들어.");
        freeNote.setTextSize(12);
        freeNote.setTextColor(Color.GRAY);
        freeNote.setPadding(dp(4), dp(7), dp(4), dp(10));
        root.addView(freeNote);

        root.addView(space(18));
        root.addView(sectionTitle("저장된 캐릭터"));
        savedList = new LinearLayout(this);
        savedList.setOrientation(LinearLayout.VERTICAL);
        root.addView(savedList);

        setContentView(scroll);
    }

    private void createCharacter(String requestedName, String description) {
        if (busy) return;
        busy = true;
        characterStatus.setText("캐릭터를 설계하고 미리보기를 만드는 중…");
        usePendingButton.setEnabled(false);
        new Thread(() -> {
            try {
                CharacterDesignerAi.CharacterPlan plan = new CharacterDesignerAi().design(requestedName, description);
                int seed = 100000 + random.nextInt(800000000);
                String preview = new StudioImageEngine().generateCharacterPreview(this, plan.state, seed);
                JSONObject character = library.saveCharacter(plan.name, plan.state, seed, preview);
                pendingCharacter = character;
                runOnUiThread(() -> {
                    showImage(characterPreview, preview);
                    characterStatus.setText(plan.summary + "\n고정 seed: " + seed + " · 저장 완료");
                    usePendingButton.setEnabled(true);
                    renderSavedCharacters();
                });
            } catch (Exception e) {
                runOnUiThread(() -> characterStatus.setText("생성 실패: " + safeMessage(e)));
            } finally {
                busy = false;
            }
        }).start();
    }

    private void generateStandalone(String prompt, boolean activeCharacter) {
        if (busy) return;
        String text = prompt == null ? "" : prompt.trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "이미지 설명을 적어줘.", Toast.LENGTH_SHORT).show();
            return;
        }
        busy = true;
        Toast.makeText(this, "이미지를 만드는 중…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                StudioImageEngine engine = new StudioImageEngine();
                String path = activeCharacter ? engine.generateForActiveCharacter(this, store, text) : engine.generateFree(this, text);
                runOnUiThread(() -> showImage(freePreview, path));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "이미지 생성 실패: " + safeMessage(e), Toast.LENGTH_LONG).show());
            } finally {
                busy = false;
            }
        }).start();
    }

    private void renderSavedCharacters() {
        if (savedList == null) return;
        savedList.removeAllViews();
        JSONArray arr = library.characters();
        if (arr.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("아직 저장된 캐릭터가 없어.");
            empty.setTextColor(Color.GRAY);
            empty.setPadding(dp(4), dp(10), dp(4), dp(14));
            savedList.addView(empty);
            return;
        }
        for (int i = arr.length() - 1; i >= 0; i--) {
            JSONObject c = arr.optJSONObject(i);
            if (c == null) continue;
            JSONObject item = c;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(10), dp(10), dp(10));
            card.setBackground(roundRect(Color.rgb(248,248,248), dp(14), Color.rgb(225,225,225), 1));

            String preview = c.optString("preview", "");
            if (!preview.isEmpty() && new File(preview).exists()) {
                ImageView img = previewView();
                img.setImageURI(android.net.Uri.fromFile(new File(preview)));
                card.addView(img, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));
            }

            TextView n = new TextView(this);
            n.setText(c.optString("name", "캐릭터"));
            n.setTextSize(18);
            n.setTextColor(Color.BLACK);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            n.setPadding(dp(4), dp(8), dp(4), dp(3));
            card.addView(n);

            JSONObject state = c.optJSONObject("state");
            TextView identity = new TextView(this);
            identity.setText(state == null ? "" : state.optString("identity", ""));
            identity.setTextSize(12);
            identity.setTextColor(Color.DKGRAY);
            identity.setMaxLines(3);
            identity.setPadding(dp(4), 0, dp(4), dp(6));
            card.addView(identity);

            LinearLayout buttons = new LinearLayout(this);
            Button use = actionButton("대화 시작");
            use.setOnClickListener(v -> activateCharacter(item));
            Button delete = actionButton("삭제");
            delete.setOnClickListener(v -> {
                library.deleteCharacter(item.optString("id"));
                renderSavedCharacters();
            });
            buttons.addView(use, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            buttons.addView(delete, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            card.addView(buttons);

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, 0, dp(12));
            savedList.addView(card, cp);
        }
    }

    private void activateCharacter(JSONObject character) {
        library.activateInChat(this, character, true);
        store = new CompanionStore(this);
        Toast.makeText(this, character.optString("name", "캐릭터") + "로 대화를 시작해.", Toast.LENGTH_SHORT).show();
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void showImage(ImageView view, String path) {
        if (path == null || path.isEmpty() || !new File(path).exists()) return;
        view.setImageURI(android.net.Uri.fromFile(new File(path)));
        view.setVisibility(View.VISIBLE);
    }

    private TextView sectionTitle(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(18);
        v.setTextColor(Color.BLACK);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(dp(2), dp(5), dp(2), dp(7));
        return v;
    }

    private EditText editor(String hint, int minLines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setMinLines(minLines);
        e.setGravity(Gravity.TOP);
        e.setTextSize(14);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(roundRect(Color.rgb(248,248,248), dp(12), Color.rgb(220,220,220), 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(8));
        e.setLayoutParams(p);
        return e;
    }

    private Button actionButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        return b;
    }

    private ImageView previewView() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setBackgroundColor(Color.rgb(242,242,242));
        return v;
    }

    private View space(int height) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return v;
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
        return m.length() > 140 ? m.substring(0, 140) : m;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
