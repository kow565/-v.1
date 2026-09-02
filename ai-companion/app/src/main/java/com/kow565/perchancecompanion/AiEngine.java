package com.kow565.perchancecompanion;

import android.content.Context;

import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class AiEngine {
    public static class Turn {
        public String reply = "";
        public JSONObject state = new JSONObject();
        public boolean imageMoment = false;
        public String imagePrompt = "";
    }

    public static class StoryTurn {
        public String caption = "";
        public JSONObject state = new JSONObject();
        public String imagePrompt = "";
    }

    public Turn chatTurn(CompanionStore store, String latestUserMessage, boolean proactive) throws Exception {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd E HH:mm", Locale.KOREAN));
        String prompt = "You are " + store.aiName() + ", a fictional adult person chatting privately with " + store.userName() + " in a mobile DM app. " +
                "Use the personality implied by the persistent mood/state and user behavior customization. Write natural Korean like a real Instagram DM: usually 1-4 short sentences, spontaneous and conversational, never mention being an AI or a language model. " +
                "You have your own day, moods, routines and opinions. Do not be clingy, coercive or manipulative. Current local time: " + now + ".\n" +
                "Persistent visual/personality state:\n" + store.visualStatePrompt() + "\n" +
                "Recent conversation for THIS character only:\n" + store.recentTranscript(18) + "\n" +
                (proactive ? "You decided to message first. Start a fresh believable check-in based on the time, your personality and current state.\n" : "Latest user message: " + latestUserMessage + "\n") +
                "Return exactly ONE JSON object and nothing else. Schema: " +
                "{\"reply\":\"Korean DM\",\"state\":{\"outfit\":null,\"pose\":null,\"location\":null,\"mood\":null,\"hair\":null,\"accessories\":null,\"lighting\":null}," +
                "\"imageMoment\":true,\"imagePrompt\":\"brief English scene description\"}. " +
                "For state fields use null when unchanged. Only change clothing/location/pose when the conversation clearly implies it or you naturally moved. " +
                "Set imageMoment true only when a selfie/photo would feel natural. Never alter identity or age. All depicted people are adults.";

        String raw = PerchanceClient.generateText(store.appContext(), prompt);
        JSONObject obj = parseObject(raw);
        Turn t = new Turn();
        if (obj != null) {
            t.reply = obj.optString("reply", "").trim();
            t.state = obj.optJSONObject("state") == null ? new JSONObject() : obj.optJSONObject("state");
            t.imageMoment = obj.optBoolean("imageMoment", false);
            t.imagePrompt = obj.optString("imagePrompt", "").trim();
        } else t.reply = cleanRaw(raw);
        if (t.reply.isEmpty()) t.reply = proactive ? "뭐 하고 있어? 갑자기 생각나서 연락했어 🙂" : "응, 듣고 있어. 더 말해줘 🙂";
        return t;
    }

    public StoryTurn storyTurn(CompanionStore store) throws Exception {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd E HH:mm", Locale.KOREAN));
        String prompt = "You are " + store.aiName() + ", a fictional adult person with a private Instagram-like story feed. " +
                "Current time: " + now + ". Persistent state: " + store.visualStatePrompt() + ". " +
                "Recent DM context for this character only:\n" + store.recentTranscript(10) + "\n" +
                "Invent one believable thing you are doing now and post it as a casual story. Keep visual continuity unless you naturally moved or changed. " +
                "Return exactly JSON: {\"caption\":\"very short Korean story caption\",\"state\":{\"outfit\":null,\"pose\":null,\"location\":null,\"mood\":null,\"hair\":null,\"accessories\":null,\"lighting\":null},\"imagePrompt\":\"English visual description of the story photo\"}. " +
                "Never change identity or age. All depicted people are adults.";
        String raw = PerchanceClient.generateText(store.appContext(), prompt);
        JSONObject obj = parseObject(raw);
        StoryTurn st = new StoryTurn();
        if (obj != null) {
            st.caption = obj.optString("caption", "").trim();
            st.state = obj.optJSONObject("state") == null ? new JSONObject() : obj.optJSONObject("state");
            st.imagePrompt = obj.optString("imagePrompt", "").trim();
        }
        if (st.caption.isEmpty()) st.caption = "오늘도 그냥 소소하게 ☕";
        if (st.imagePrompt.isEmpty()) st.imagePrompt = "casual candid smartphone photo during an ordinary day";
        return st;
    }

    public String generateImage(Context context, CompanionStore store, String scenePrompt) throws Exception {
        String prompt = store.visualStatePrompt() + " Scene: " + scenePrompt +
                ". same adult person, consistent facial identity, consistent clothing unless state says otherwise, realistic candid smartphone photography, natural skin texture, coherent anatomy";
        String negative = "different person, changed face, child, underage, duplicate person, deformed hands, extra fingers, extra limbs, text, watermark, logo, low quality";
        return PerchanceClient.generateImage(context, prompt, negative, store.anchorSeed(), "generated", store.profileId());
    }

    public String generateMessageImage(Context context, CompanionStore store, String role,
                                       String message, String sceneHint) throws Exception {
        String text = message == null ? "" : message.replace('\n', ' ').trim();
        if (text.length() > 280) text = text.substring(0, 280);
        String scene;
        if ("user".equals(role)) {
            scene = "The companion has just received this private DM: \"" + text +
                    "\". Show her immediate natural facial expression and believable current activity, " +
                    "as a candid photo that visually responds to the message.";
        } else {
            String hint = sceneHint == null ? "" : sceneHint.trim();
            scene = hint.isEmpty()
                    ? "The companion is sending this DM: \"" + text + "\". Show the matching expression, pose and situation."
                    : hint + ". Her expression and situation match this DM: \"" + text + "\".";
        }
        return generateImage(context, store, scene);
    }

    public String generateUtilityText(CompanionStore store, String prompt) throws Exception {
        return PerchanceClient.generateText(store.appContext(), prompt);
    }

    private JSONObject parseObject(String raw) {
        if (raw == null) return null;
        int first = raw.indexOf('{');
        int last = raw.lastIndexOf('}');
        if (first < 0 || last <= first) return null;
        try { return new JSONObject(raw.substring(first, last + 1)); }
        catch (Exception ignored) { return null; }
    }

    private String cleanRaw(String raw) {
        if (raw == null) return "";
        return raw.replace("```json", "").replace("```", "").trim();
    }
}
