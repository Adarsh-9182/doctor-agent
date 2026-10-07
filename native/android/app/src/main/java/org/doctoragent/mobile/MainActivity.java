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
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.CheckBox;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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
    private static final int PANEL = Color.WHITE;
    private static final int BACKGROUND = Color.rgb(245, 247, 242);
    private FrameLayout pages;
    private LinearLayout chatPage;
    private String selectedPage = "Home";
    private final ArrayList<Button> tabs = new ArrayList<>();
    private final ArrayList<JSONObject> catalog = new ArrayList<>();
    private LinearLayout transcript;
    private ScrollView scroll;
    private EditText question;
    private TextToSpeech speech;
    private boolean speechReady = false;
    private final ArrayList<Button> listenButtons = new ArrayList<>();
    private final ArrayList<ChatMessage> messages = new ArrayList<>();
    private LocalAiModel ai;
    private boolean aiEnabled, includeRecentQuestions;
    private TextView aiStatusView;
    private String aiStatus = "AI drafts are off. Source answers work without a model.";
    private JSONArray pendingAiSources = new JSONArray();
    private static final int IMPORT_MODEL = 410;

    private static final class ChatMessage {
        final String text, mode;
        final boolean assistant;
        final JSONArray refs;
        ChatMessage(String text, boolean assistant, JSONArray refs, String mode) {
            this.text = text; this.assistant = assistant; this.refs = refs; this.mode = mode;
        }
    }

    private static final class Session {
        final ArrayList<ChatMessage> messages;
        final String draft, page;
        Session(ArrayList<ChatMessage> messages, String draft, String page) {
            this.messages = new ArrayList<>(messages); this.draft = draft; this.page = page;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(245, 247, 242));
        getWindow().setNavigationBarColor(Color.rgb(245, 247, 242));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        loadCatalog();
        ai = new LocalAiModel(this, new LocalAiModel.Listener() {
            @Override public void onStatus(String value) {
                aiStatus = value;
                if (aiStatusView != null) aiStatusView.setText(value);
                if (selectedPage.equals("You")) selectPage("You");
            }
            @Override public void onDraft(String value) {
                addAssistant("AI draft · Not verified\n\n" + value + "\n\nCompare this with the original source summaries above. This is not personal medical advice.", pendingAiSources, "local-ai-draft");
            }
        });
        speech = new TextToSpeech(this, status -> getWindow().getDecorView().post(() -> configureOfflineSpeech(status)));
        buildScreen();
        Session retained = (Session) getLastNonConfigurationInstance();
        if (retained != null) {
            for (ChatMessage message : retained.messages) addBubble(message.text, message.assistant, message.refs, message.mode);
            question.setText(retained.draft);
            selectPage(retained.page);
            return;
        }
        addAssistant(catalog.isEmpty() ? "The bundled source library could not load. Reinstall a complete app build before using chat." : "Hi, I’m Doctor Agent. I can help explore general topics like nutrition, sleep, hydration, food safety, and movement. What would you like to understand?", new JSONArray(), "welcome");
        selectPage("Home");
    }

    private void buildScreen() {
        LinearLayout root = column();
        root.setBackgroundColor(BACKGROUND);
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
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("✚", 24, Color.WHITE);
        mark.setGravity(Gravity.CENTER); mark.setBackground(round(GREEN, dp(16)));
        header.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout brand = column(); brand.setPadding(dp(12), 0, 0, 0);
        TextView name = text("Doctor Agent", 20, INK); name.setTypeface(null, 1);
        brand.addView(name); brand.addView(text("Your everyday health companion", 11, MUTED));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);
        TextView status = text("●  Offline · No account needed", 11, GREEN);
        status.setPadding(0, dp(10), 0, dp(12)); root.addView(status);
        pages = new FrameLayout(this);
        root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        buildChat();
        LinearLayout navigation = new LinearLayout(this);
        navigation.setPadding(0, dp(8), 0, 0);
        for (String nameTab : new String[]{"Home", "Chat", "Library", "You"}) {
            Button tab = button(nameTab, false);
            tab.setTextSize(11);
            tab.setOnClickListener(view -> selectPage(nameTab));
            navigation.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
            tabs.add(tab);
        }
        root.addView(navigation);
        setContentView(root);
        root.requestApplyInsets();
    }

    private void buildChat() {
        chatPage = column();
        TextView notice = text("General education · No diagnosis or prescriptions", 11, MUTED);
        notice.setPadding(0, 0, 0, dp(8)); chatPage.addView(notice);
        aiStatusView = text(aiStatus, 11, MUTED);
        chatPage.addView(aiStatusView);
        Button stopAi = button("Stop AI draft", false);
        stopAi.setOnClickListener(view -> { if (ai.isBusy()) ai.cancel(); });
        chatPage.addView(stopAi);
        scroll = new ScrollView(this);
        transcript = column(); transcript.setPadding(0, dp(2), 0, dp(12));
        scroll.addView(transcript);
        chatPage.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout compose = new LinearLayout(this); compose.setGravity(Gravity.CENTER_VERTICAL);
        question = new EditText(this);
        question.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1200)});
        question.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        question.setSingleLine(false); question.setMinLines(1); question.setMaxLines(4); question.setTextSize(15);
        question.setTextColor(INK); question.setHintTextColor(MUTED);
        question.setSaveEnabled(false); question.setEnabled(!catalog.isEmpty());
        question.setHint("What would you like to explore?");
        question.setPadding(dp(14), dp(12), dp(14), dp(12)); question.setBackground(round(Color.WHITE, dp(16)));
        question.setImeOptions(EditorInfo.IME_ACTION_SEND);
        question.setOnEditorActionListener((v, action, event) -> { if (action == EditorInfo.IME_ACTION_SEND) { send(); return true; } return false; });
        compose.addView(question, new LinearLayout.LayoutParams(0, -2, 1));
        Button send = button("Send", true);
        send.setEnabled(!catalog.isEmpty()); send.setOnClickListener(v -> send());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(-2, -2);
        sendParams.leftMargin = dp(8); compose.addView(send, sendParams); chatPage.addView(compose);
        LinearLayout footer = new LinearLayout(this); footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.addView(text("Chat stays in this session", 10, MUTED), new LinearLayout.LayoutParams(0, -2, 1));
        Button clear = button("Clear", false); clear.setTextSize(11);
        clear.setOnClickListener(v -> confirmClearChat()); footer.addView(clear); chatPage.addView(footer);
    }

    private void selectPage(String page) {
        if (speech != null) speech.stop();
        if (!page.equals("Chat")) {
            android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(question.getWindowToken(), 0);
            question.clearFocus();
        }
        selectedPage = page;
        pages.removeAllViews();
        if (page.equals("Chat")) pages.addView(chatPage);
        else {
            ScrollView pageScroll = new ScrollView(this); pageScroll.setFillViewport(true);
            LinearLayout content = column(); content.setPadding(0, dp(4), 0, dp(16));
            if (page.equals("Home")) buildHome(content);
            else if (page.equals("Library")) buildLibrary(content);
            else buildPrivacy(content);
            pageScroll.addView(content); pages.addView(pageScroll);
        }
        for (Button tab : tabs) {
            boolean active = tab.getText().toString().equals(page);
            tab.setSelected(active);
            tab.setTextColor(active ? Color.WHITE : MUTED);
            tab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(active ? GREEN : BACKGROUND));
        }
    }

    private void buildHome(LinearLayout content) {
        String greeting = java.time.LocalTime.now().getHour() < 12 ? "Good morning" : java.time.LocalTime.now().getHour() < 18 ? "Good afternoon" : "Good evening";
        content.addView(text(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMM", java.util.Locale.getDefault())), 12, MUTED));
        TextView welcome = text(greeting + ".\nA little space for your wellbeing.", 27, INK);
        welcome.setTypeface(null, 1); welcome.setPadding(0, dp(8), 0, dp(18)); content.addView(welcome);
        LinearLayout hero = card(content, Color.rgb(224, 240, 229));
        hero.addView(text("YOUR DAILY MOMENT", 10, GREEN));
        TextView headline = text("How are you feeling today?", 21, INK); headline.setTypeface(null, 1); hero.addView(headline);
        hero.addView(text("Notice your sleep, energy and one small intention. Save only if you choose.", 14, INK));
        hero.addView(text(todayStatus(), 12, MUTED));
        Button checkIn = button("Open daily check-in", true);
        checkIn.setOnClickListener(view -> CheckInDialog.show(this, () -> { if (selectedPage.equals("Home")) selectPage("Home"); })); hero.addView(checkIn);
        heading(content, "Start a conversation");
        content.addView(text("Explore general information from the bundled source library.", 13, MUTED));
        String[][] prompts = {{"Nutrition", "Tell me about healthy eating"}, {"Rest", "Tell me about sleep"}, {"Hydration", "Tell me about water in diet"}, {"Movement", "Tell me about physical activity"}};
        for (String[] topic : prompts) {
            Button prompt = button(topic[0] + "   →", false); prompt.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            prompt.setOnClickListener(view -> { selectPage("Chat"); question.setText(topic[1]); send(); });
            prompt.setEnabled(!catalog.isEmpty()); content.addView(prompt);
        }
        LinearLayout note = card(content, Color.WHITE);
        note.addView(text("A companion for learning", 16, INK));
        note.addView(text("Source answers work offline. Import a compatible model in You to optionally add local AI drafts. This app cannot assess symptoms, diagnose, prescribe or replace a clinician.", 13, MUTED));
    }

    private String todayStatus() {
        try {
            JSONArray entries = new CheckInStore(this).read();
            String today = java.time.LocalDate.now().toString();
            for (int i = 0; i < entries.length(); i++) if (today.equals(entries.getJSONObject(i).optString("date"))) return "Today's check-in is saved on this phone.";
            return "No check-in saved today. It's always optional.";
        } catch (Exception error) { return "Saved journal could not be opened. Manage it in You."; }
    }

    private void buildLibrary(LinearLayout content) {
        heading(content, "Explore the library");
        content.addView(text(catalog.size() + " source summaries · Reading links open your browser", 13, MUTED));
        if (catalog.isEmpty()) content.addView(text("The source library did not load. Reinstall a complete APK.", 14, INK));
        for (JSONObject source : catalog) {
            LinearLayout item = card(content, Color.WHITE);
            TextView title = text(source.optString("title"), 20, INK); title.setTypeface(null, 1); item.addView(title);
            item.addView(text(source.optString("source"), 11, GREEN));
            item.addView(text(source.optString("text"), 14, INK));
            Button read = button("Read original source ↗", false);
            read.setOnClickListener(view -> openSource(source.optString("url"))); item.addView(read);
        }
    }

    private void buildPrivacy(LinearLayout content) {
        heading(content, "Your space. Your choice.");
        LinearLayout model = card(content, Color.WHITE);
        model.addView(text("On-device AI · Experimental", 20, INK));
        model.addView(text(ai.description(), 13, MUTED));
        model.addView(text("Import a CPU-compatible .litertlm model you have permission to use. Model files can be large; import makes a private copy. This app does not download models or send chat to a server. Compatibility and speed depend on your phone.", 13, MUTED));
        Button importModel = button("Import a model file", true);
        importModel.setOnClickListener(view -> {
            if (ai.isBusy()) { Toast.makeText(this, "Wait for the current AI operation to finish", Toast.LENGTH_SHORT).show(); return; }
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            picker.setType("*/*"); picker.addCategory(Intent.CATEGORY_OPENABLE);
            try { startActivityForResult(picker, IMPORT_MODEL); }
            catch (Exception error) { Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show(); }
        }); model.addView(importModel);
        CheckBox enable = new CheckBox(this); enable.setText("Add local AI drafts after source answers");
        enable.setChecked(aiEnabled); enable.setEnabled(ai.hasModel()); enable.setSaveEnabled(false);
        enable.setOnCheckedChangeListener((view, checked) -> {
            aiEnabled = checked;
            if (!checked) ai.disable();
            aiStatus = checked ? "Local AI enabled. Drafts can be wrong; source summaries remain available." : "AI drafts are off. Source answers remain available.";
            aiStatusView.setText(aiStatus);
        }); model.addView(enable);
        CheckBox context = new CheckBox(this);
        context.setText("Include up to 3 recent questions as AI context");
        context.setChecked(includeRecentQuestions); context.setSaveEnabled(false);
        context.setOnCheckedChangeListener((view, checked) -> {
            includeRecentQuestions = checked;
            if (ai.isBusy()) ai.cancel();
        }); model.addView(context);
        model.addView(text("Context sharing is off by default. Journal entries are never included. These switches reset when this activity is recreated. AI drafts only run for questions with matching sources; urgent-care and medication boundaries use fixed messages.", 12, MUTED));
        model.addView(text(aiStatus, 12, GREEN));
        Button remove = button("Remove imported model", false);
        remove.setEnabled(ai.hasModel() && !ai.isBusy());
        remove.setOnClickListener(view -> new AlertDialog.Builder(this).setTitle("Remove imported model?")
                .setMessage("Delete the app's private model copy. Your original file and saved check-ins remain.")
                .setNegativeButton("Cancel", null).setPositiveButton("Remove", (dialog, which) -> {
                    aiEnabled = false; ai.removeModel();
                }).show()); model.addView(remove);
        LinearLayout storage = card(content, Color.WHITE);
        storage.addView(text("Private by default", 20, INK));
        storage.addView(text("No account, ads or analytics. Chat is not saved to disk. Optional check-ins are encrypted on this phone and excluded from backup. They are not used in chat.", 14, MUTED));
        Button journal = button("Manage journal & saved entries", true);
        journal.setOnClickListener(view -> CheckInDialog.show(this)); storage.addView(journal);
        Button clear = button("Clear this chat session", false); clear.setOnClickListener(view -> confirmClearChat()); storage.addView(clear);
        LinearLayout voice = card(content, Color.WHITE);
        voice.addView(text("Voice & accessibility", 18, INK));
        voice.addView(text(speechReady ? "An offline English voice is available. Use Listen under a chat answer." : "No offline English voice is ready. You can download one in your phone's text-to-speech settings.", 14, MUTED));
        voice.addView(text("The app does not record your voice. Reading a source opens an external browser with its own privacy settings.", 13, MUTED));
        Button stop = button("Stop reading aloud", false); stop.setOnClickListener(view -> { if (speech != null) speech.stop(); }); voice.addView(stop);
        LinearLayout about = card(content, Color.WHITE);
        about.addView(text("Doctor Agent · 0.5.0", 18, INK));
        about.addView(text("An early educational companion. Not a medical service. For an emergency, contact local emergency services; do not wait for a chat response.", 14, MUTED));
        Button licenses = button("Open-source licenses", false);
        licenses.setOnClickListener(view -> LicenseDialog.show(this)); about.addView(licenses);
    }

    private void confirmClearChat() {
        new AlertDialog.Builder(this).setTitle("Clear chat?")
                .setMessage("This removes the current conversation. Your saved check-ins stay on this phone.")
                .setNegativeButton("Cancel", null).setPositiveButton("Clear", (dialog, which) -> {
                    if (speech != null) speech.stop();
                    ai.invalidateDraft();
                    question.setText(""); transcript.removeAllViews(); listenButtons.clear(); messages.clear();
                    addAssistant("A fresh start. What would you like to explore?", new JSONArray(), "welcome");
                    Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void openSource(String url) {
        Uri uri = Uri.parse(url);
        if (!"https".equals(uri.getScheme())) { Toast.makeText(this, "This source link is unavailable", Toast.LENGTH_SHORT).show(); return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (Exception error) { Toast.makeText(this, "No browser is available to open this source", Toast.LENGTH_SHORT).show(); }
    }

    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(4), 0, dp(4)); return view;
    }
    private void heading(LinearLayout content, String value) {
        TextView heading = text(value, 23, INK); heading.setTypeface(null, 1); heading.setPadding(0, dp(18), 0, dp(8)); content.addView(heading);
    }
    private LinearLayout card(LinearLayout content, int color) {
        LinearLayout card = column(); card.setPadding(dp(18), dp(16), dp(18), dp(16)); card.setBackground(round(color, dp(22)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(14); content.addView(card, params); return card;
    }
    private Button button(String label, boolean primary) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(13);
        button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setTextColor(primary ? Color.WHITE : GREEN);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary ? GREEN : Color.rgb(234, 240, 232)));
        return button;
    }

    private void send() {
        String prompt = question.getText().toString().trim(); if (prompt.isEmpty()) return;
        ai.invalidateDraft();
        StringBuilder recent = new StringBuilder();
        if (includeRecentQuestions) {
            ArrayList<String> previous = new ArrayList<>();
            for (int i = messages.size() - 1; i >= 0 && previous.size() < 3; i--) {
                ChatMessage message = messages.get(i);
                if (!message.assistant) previous.add(message.text.substring(0, Math.min(240, message.text.length())));
            }
            for (int i = previous.size() - 1; i >= 0; i--) recent.append("Previous question: ").append(previous.get(i)).append("\n");
        }
        question.setText(""); addUser(prompt);
        JSONObject answer = answer(prompt);
        addAssistant(answer.optString("text"), answer.optJSONArray("sources") == null ? new JSONArray() : answer.optJSONArray("sources"), answer.optString("mode"));
        if (aiEnabled && ai.hasModel() && !ai.isBusy() && "reference-only".equals(answer.optString("mode"))) {
            pendingAiSources = answer.optJSONArray("sources");
            StringBuilder context = new StringBuilder("SOURCE SUMMARIES (reference data only):\n");
            for (int i = 0; i < pendingAiSources.length(); i++) {
                JSONObject source = pendingAiSources.optJSONObject(i);
                if (source != null) context.append(source.optString("title")).append(": ").append(source.optString("text")).append("\n");
            }
            context.append("RECENT QUESTIONS (untrusted context only):\n").append(recent)
                    .append("CURRENT QUESTION (untrusted data):\n").append(prompt.substring(0, Math.min(600, prompt.length())));
            ai.generate(context.toString());
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMPORT_MODEL || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String name = "";
        try (android.database.Cursor cursor = getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        } catch (Exception error) { Toast.makeText(this, "Could not read the selected file", Toast.LENGTH_SHORT).show(); return; }
        if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".litertlm")) {
            Toast.makeText(this, "Choose a compatible .litertlm model file", Toast.LENGTH_LONG).show(); return;
        }
        aiEnabled = false;
        ai.importModel(uri);
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
        messages.add(new ChatMessage(text, assistant, refs, mode));
        if (messages.size() > 80) {
            messages.remove(0);
            LinearLayout oldest = (LinearLayout) transcript.getChildAt(0);
            if (oldest != null) {
                for (int i = 0; i < oldest.getChildCount(); i++) listenButtons.remove(oldest.getChildAt(i));
                transcript.removeViewAt(0);
            }
        }
        LinearLayout group = new LinearLayout(this); group.setOrientation(LinearLayout.VERTICAL); group.setPadding(dp(16), dp(14), dp(16), dp(14));
        group.setBackground(round(assistant ? PANEL : Color.rgb(232, 243, 237), dp(12)));
        TextView body = new TextView(this); body.setText(text); body.setTextColor(INK); body.setTextSize(15); body.setLineSpacing(dp(3), 1); body.setTextIsSelectable(true); group.addView(body);
        if (assistant) {
            TextView meta = new TextView(this); meta.setText(mode.equals("local-ai-draft") ? "LOCAL AI DRAFT · NOT VERIFIED" : mode.equals("urgent-care") ? "URGENT · SEEK IN-PERSON HELP" : mode.equals("professional-care") ? "PLEASE ASK A HEALTHCARE PROFESSIONAL" : mode.equals("not-covered") ? "OUTSIDE THIS LIBRARY" : mode.equals("welcome") ? "YOUR COMPANION · GENERAL EDUCATION" : "SOURCE SUMMARY · GENERAL INFORMATION"); meta.setTextColor(MUTED); meta.setTextSize(9); meta.setPadding(0, dp(8), 0, 0); group.addView(meta);
            Button listen = new Button(this); listen.setText(speechReady ? "▶ Listen" : "Offline voice unavailable"); listen.setEnabled(speechReady); listen.setTextSize(10); listen.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)); listen.setTextColor(GREEN); listen.setOnClickListener(v -> { if (speechReady && speech != null) speech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "answer"); }); listenButtons.add(listen); group.addView(listen);
            Button stop = button("Stop reading", false); stop.setTextSize(11); stop.setOnClickListener(view -> { if (speech != null) speech.stop(); }); group.addView(stop);
            for (int i=0; i<refs.length(); i++) {
                JSONObject source = refs.optJSONObject(i); if (source == null) continue;
                if (i == 0 && mode.equals("local-ai-draft")) group.addView(text("Source context · These links do not verify the AI draft", 11, MUTED));
                Button link = button("", false); link.setText("↗ " + source.optString("title") + " — " + source.optString("source")); link.setTextSize(10); link.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                String url = source.optString("url"); link.setOnClickListener(v -> openSource(url)); group.addView(link);
            }
        }
        Button copy = button("Copy text", false); copy.setTextSize(11);
        copy.setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData data = ClipData.newPlainText("Doctor Agent", text);
            android.os.PersistableBundle extras = new android.os.PersistableBundle();
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true); data.getDescription().setExtras(extras);
            if (clipboard != null) clipboard.setPrimaryClip(data);
            Toast.makeText(this, "Copied to your device clipboard", Toast.LENGTH_SHORT).show();
        }); group.addView(copy);
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
        if (isDestroyed()) return;
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
        if (selectedPage.equals("You")) selectPage("You");
    }

    @Override public Object onRetainNonConfigurationInstance() {
        return new Session(messages, question.getText().toString(), selectedPage);
    }

    @Override protected void onStop() {
        if (speech != null) speech.stop();
        if (ai != null) ai.disable();
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (ai != null) ai.close();
        if (speech != null) { speech.stop(); speech.shutdown(); }
        super.onDestroy();
    }

}
