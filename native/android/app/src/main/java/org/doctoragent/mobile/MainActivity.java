package org.doctoragent.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(24, 59, 53);
    private static final int GREEN = Color.rgb(22, 101, 85);
    private static final int MUTED = Color.rgb(100, 122, 115);
    private static final int PANEL = Color.rgb(246, 248, 245);
    private final ArrayList<JSONObject> catalog = new ArrayList<>();
    private LinearLayout transcript;
    private ScrollView scroll;
    private EditText question;
    private TextToSpeech speech;
    private boolean speechReady = false;
    private final ArrayList<Button> listenButtons = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(245, 247, 242));
        getWindow().setNavigationBarColor(Color.rgb(245, 247, 242));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        loadCatalog();
        speech = new TextToSpeech(this, status -> getWindow().getDecorView().post(() -> configureOfflineSpeech(status)));
        buildScreen();
        addAssistant(catalog.isEmpty() ? "The bundled source library could not load. Reinstall a complete app build before using chat." : "Hi, I’m Doctor Agent. I can help explore general topics like nutrition, sleep, hydration, food safety, and movement. What would you like to understand?", new JSONArray(), "reference-only");
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 247, 242));
        root.setPadding(dp(16), dp(8), dp(16), dp(8));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.ime());
                view.setPadding(dp(16) + bars.left, dp(8) + bars.top, dp(16) + bars.right, dp(8) + bars.bottom);
            } else {
                view.setPadding(dp(16) + insets.getSystemWindowInsetLeft(), dp(8) + insets.getSystemWindowInsetTop(), dp(16) + insets.getSystemWindowInsetRight(), dp(8) + insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        TextView title = new TextView(this);
        title.setText("✚  Doctor Agent"); title.setTextColor(INK); title.setTextSize(20); title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));
        TextView privacy = new TextView(this);
        privacy.setText("PRIVATE · RUNNING ON THIS PHONE"); privacy.setTextColor(GREEN); privacy.setTextSize(10); privacy.setPadding(0, 0, 0, dp(12));
        root.addView(privacy);

        Button checkIn = new Button(this);
        checkIn.setText("Daily check-in · optional local journal");
        checkIn.setOnClickListener(view -> CheckInDialog.show(this));
        root.addView(checkIn);

        TextView notice = new TextView(this);
        notice.setText("General education only. This app cannot diagnose or prescribe and is not a substitute for professional care.");
        notice.setTextColor(Color.rgb(103, 91, 57)); notice.setTextSize(11); notice.setPadding(dp(12), dp(10), dp(12), dp(10));
        notice.setBackground(round(Color.rgb(255, 248, 230), dp(10)));
        LinearLayout.LayoutParams noticeParams = new LinearLayout.LayoutParams(-1, -2); noticeParams.bottomMargin = dp(10); root.addView(notice, noticeParams);

        scroll = new ScrollView(this);
        transcript = new LinearLayout(this); transcript.setOrientation(LinearLayout.VERTICAL); transcript.setPadding(dp(1), dp(2), dp(1), dp(12));
        scroll.addView(transcript);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout compose = new LinearLayout(this); compose.setOrientation(LinearLayout.HORIZONTAL); compose.setGravity(Gravity.CENTER_VERTICAL);
        question = new EditText(this); question.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1200)}); question.setSingleLine(false); question.setMinLines(1); question.setMaxLines(4); question.setTextSize(14);
        question.setSaveEnabled(false); question.setEnabled(!catalog.isEmpty());
        question.setHint("Ask a general health question…"); question.setPadding(dp(12), dp(10), dp(12), dp(10)); question.setBackground(round(Color.WHITE, dp(12)));
        question.setImeOptions(EditorInfo.IME_ACTION_SEND); question.setOnEditorActionListener((v, action, event) -> { if (action == EditorInfo.IME_ACTION_SEND) { send(); return true; } return false; });
        compose.addView(question, new LinearLayout.LayoutParams(0, -2, 1));
        Button send = new Button(this); send.setText("↑"); send.setTextColor(Color.WHITE); send.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN)); send.setOnClickListener(v -> send());
        send.setEnabled(!catalog.isEmpty());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(52), dp(48)); sendParams.leftMargin = dp(7); compose.addView(send, sendParams);
        root.addView(compose);
        LinearLayout footer = new LinearLayout(this); footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView session = new TextView(this); session.setText("Questions stay in this app session."); session.setTextSize(10); session.setTextColor(MUTED);
        footer.addView(session, new LinearLayout.LayoutParams(0, -2, 1));
        Button clear = new Button(this); clear.setText("Clear chat"); clear.setTextSize(10);
        clear.setOnClickListener(v -> { if (speech != null) speech.stop(); transcript.removeAllViews(); listenButtons.clear(); addAssistant("Chat cleared. What general health topic would you like to explore?", new JSONArray(), "reference-only"); });
        footer.addView(clear); root.addView(footer);
        setContentView(root);
        root.requestApplyInsets();
    }

    private void send() {
        String prompt = question.getText().toString().trim(); if (prompt.isEmpty()) return;
        question.setText(""); addUser(prompt);
        JSONObject answer = answer(prompt);
        addAssistant(answer.optString("text"), answer.optJSONArray("sources") == null ? new JSONArray() : answer.optJSONArray("sources"), answer.optString("mode"));
    }

    private JSONObject answer(String prompt) {
        ArrayList<EducationEngine.Source> sources = new ArrayList<>();
        for (JSONObject source : catalog) {
            ArrayList<String> keywords = new ArrayList<>();
            JSONArray keys = source.optJSONArray("keywords");
            if (keys != null) for (int i=0; i<keys.length(); i++) keywords.add(keys.optString(i));
            sources.add(new EducationEngine.Source(source.optString("id"), source.optString("title"), keywords, source.optString("text")));
        }
        EducationEngine.Answer answer = EducationEngine.answer(prompt, sources);
        JSONArray citations = new JSONArray();
        for (String id : answer.sourceIds) {
            for (JSONObject source : catalog) if (id.equals(source.optString("id"))) { citations.put(source); break; }
        }
        JSONObject result = new JSONObject();
        try { result.put("text", answer.text); result.put("mode", answer.mode); result.put("sources", citations); } catch (Exception ignored) { }
        return result;
    }

    private void addUser(String text) { addBubble(text, false, new JSONArray(), ""); }
    private void addAssistant(String text, JSONArray refs, String mode) { addBubble(text, true, refs, mode); }

    private void addBubble(String text, boolean assistant, JSONArray refs, String mode) {
        LinearLayout group = new LinearLayout(this); group.setOrientation(LinearLayout.VERTICAL); group.setPadding(dp(12), dp(10), dp(12), dp(10));
        group.setBackground(round(assistant ? PANEL : Color.rgb(232, 243, 237), dp(12)));
        TextView body = new TextView(this); body.setText(text); body.setTextColor(INK); body.setTextSize(13); body.setTextIsSelectable(true); group.addView(body);
        if (assistant) {
            TextView meta = new TextView(this); meta.setText(mode.equals("urgent-care") ? "URGENT CARE · GENERAL GUIDANCE" : "SOURCE-LED · GENERAL INFORMATION"); meta.setTextColor(MUTED); meta.setTextSize(9); meta.setPadding(0, dp(8), 0, 0); group.addView(meta);
            Button listen = new Button(this); listen.setText(speechReady ? "▶ Listen" : "Offline voice unavailable"); listen.setEnabled(speechReady); listen.setTextSize(10); listen.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)); listen.setTextColor(GREEN); listen.setOnClickListener(v -> { if (speechReady && speech != null) speech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "answer"); }); listenButtons.add(listen); group.addView(listen);
            for (int i=0; i<refs.length(); i++) {
                JSONObject source = refs.optJSONObject(i); if (source == null) continue;
                Button link = new Button(this); link.setText("↗ " + source.optString("title") + " — " + source.optString("source")); link.setTextSize(10); link.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                String url = source.optString("url"); link.setOnClickListener(v -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {} }); group.addView(link);
            }
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(10); transcript.addView(group, params);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void loadCatalog() {
        try (InputStream input = getAssets().open("knowledge.json"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count; while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            JSONArray array = new JSONArray(bytes.toString(StandardCharsets.UTF_8.name()));
            for (int i=0; i<array.length(); i++) catalog.add(array.getJSONObject(i));
        } catch (Exception ignored) { }
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable(); shape.setColor(color); shape.setCornerRadius(radius); return shape;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void configureOfflineSpeech(int status) {
        if (status != TextToSpeech.SUCCESS || speech == null || speech.getVoices() == null) return;
        Voice selected = null;
        for (Voice voice : speech.getVoices()) {
            if (!voice.isNetworkConnectionRequired() && voice.getLocale().getLanguage().equals("en")) {
                if (selected == null || voice.getName().compareTo(selected.getName()) < 0) selected = voice;
            }
        }
        speechReady = selected != null && speech.setVoice(selected) == TextToSpeech.SUCCESS;
        for (Button button : listenButtons) {
            button.setEnabled(speechReady);
            button.setText(speechReady ? "▶ Listen" : "Offline voice unavailable");
        }
    }

    @Override protected void onStop() {
        if (speech != null) speech.stop();
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (speech != null) { speech.stop(); speech.shutdown(); }
        super.onDestroy();
    }

}
