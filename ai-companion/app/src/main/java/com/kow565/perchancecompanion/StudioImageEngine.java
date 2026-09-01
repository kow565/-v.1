package com.kow565.perchancecompanion;

import android.content.Context;

import org.json.JSONObject;

import java.util.Random;

public class StudioImageEngine {
    private static final Random RNG = new Random();
    private static final String NEGATIVE = "different identity when identity is specified, child, underage, duplicate person, malformed anatomy, extra fingers, extra limbs, text, watermark, logo, low quality";

    public String generateFree(Context context, String prompt) throws Exception {
        int seed = 100000 + RNG.nextInt(800000000);
        return PerchanceClient.generateImage(context, prompt, NEGATIVE, seed, "studio", "free");
    }

    public String generateForActiveCharacter(Context context, CompanionStore store, String scenePrompt) throws Exception {
        String prompt = store.visualStatePrompt() + ". Scene requested by user: " + scenePrompt +
                ". Keep the exact same adult character identity and facial features. This image is independent from the chat timeline and must not alter conversation state.";
        return PerchanceClient.generateImage(context, prompt, NEGATIVE, store.anchorSeed(), "studio", store.profileId());
    }

    public String generateCharacterPreview(Context context, JSONObject state, int seed) throws Exception {
        String prompt = statePrompt(state) +
                ". Clean character reference portrait, same single adult person, natural realistic photography, clear face, coherent anatomy, neutral composition suitable as an identity anchor.";
        return PerchanceClient.generateImage(context, prompt, NEGATIVE, seed, "studio", "character");
    }

    private String statePrompt(JSONObject s) {
        if (s == null) s = new JSONObject();
        return "IDENTITY: " + s.optString("identity") + ". HAIR: " + s.optString("hair") +
                ". OUTFIT: " + s.optString("outfit") + ". ACCESSORIES: " + s.optString("accessories") +
                ". POSE: " + s.optString("pose") + ". LOCATION: " + s.optString("location") +
                ". MOOD: " + s.optString("mood") + ". LIGHTING: " + s.optString("lighting");
    }
}
