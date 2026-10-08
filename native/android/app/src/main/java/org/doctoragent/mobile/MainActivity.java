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
    private static final int MICROPHONE_PERMISSION = 411;
    private static final int NOTIFICATION_PERMISSION = 412;
    private boolean reminderEnableRequested;
    private OfflineVoiceInput voiceInput;
    private TextView voiceStatusView;
    private Button speakButton;

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
        voiceInput = new OfflineVoiceInput(this, new OfflineVoiceInput.Listener() {
            @Override public void onStatus(String value, boolean listening) {
                if (voiceStatusView != null) voiceStatusView.setText(value);
                if (speakButton != null) speakButton.setText(listening ? "Cancel voice" : "Speak");
            }
            @Override public void onTranscript(String value) { useVoiceDraft(value); }
        });
        speech = new TextToSpeech(this, status -> getWindow().getDecorView().post(() -> configureOfflineSpeech(status)));
        buildScreen();
        Session retained = (Session) getLastNonConfigurationInstance();
        if (retained != null) {
            for (ChatMessage message : retained.messages) addBubble(message.text, message.assistant, message.refs, message.mode);
            question.setText(retained.draft);
            selectPage(retained.page);
            openReminderJournal(getIntent());
            return;
        }
        addAssistant(catalog.isEmpty() ? "The bundled source library could not load. Reinstall a complete app build before using chat." : "Hi, I’m Doctor Agent. I can help explore general topics like nutrition, sleep, hydration, food safety, and movement. What would you like to understand?", new JSONArray(), "welcome");
        selectPage("Home");
        openReminderJournal(getIntent());
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
        LinearLayout voiceControls = new LinearLayout(this);
        speakButton = button("Speak", false);
        speakButton.setEnabled(!catalog.isEmpty() && OfflineVoiceInput.available(this));
        speakButton.setOnClickListener(view -> {
            if (voiceInput.isListening()) voiceInput.cancel("Voice cancelled. Your typed draft is unchanged.");
            else requestVoiceInput();
        });
        voiceControls.addView(speakButton, new LinearLayout.LayoutParams(0, -2, 1));
        Button stopAi = button("Stop AI", false);
        stopAi.setOnClickListener(view -> { if (ai.isBusy()) ai.cancel(); });
        voiceControls.addView(stopAi, new LinearLayout.LayoutParams(0, -2, 1));
        chatPage.addView(voiceControls);
        voiceStatusView = text(OfflineVoiceInput.available(this)
                ? "Optional on-device voice · Review your draft before sending"
                : "On-device voice needs Android 12+ and a compatible recognizer. Typing works.", 10, MUTED);
        chatPage.addView(voiceStatusView);
        LinearLayout footer = new LinearLayout(this); footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.addView(text("Chat stays in this session", 10, MUTED), new LinearLayout.LayoutParams(0, -2, 1));
        Button clear = button("Clear", false); clear.setTextSize(11);
        clear.setOnClickListener(v -> confirmClearChat()); footer.addView(clear); chatPage.addView(footer);
    }

    private void selectPage(String page) {
        if (speech != null) speech.stop();
        if (!page.equals("Chat")) {
            if (voiceInput != null) voiceInput.cancel("Voice stopped. Your typed draft is unchanged.");
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
        JSONObject profile = new JSONObject();
        try { profile = CompanionProfile.read(this); } catch (Exception ignored) {}
        String preferredName = profile.optString("name");
        content.addView(text(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMM", java.util.Locale.getDefault())), 12, MUTED));
        TextView welcome = text(greeting + (preferredName.isEmpty() ? "." : ", " + preferredName + ".") + "\nA little space for your wellbeing.", 27, INK);
        welcome.setTypeface(null, 1); welcome.setPadding(0, dp(8), 0, dp(18)); content.addView(welcome);
        LinearLayout hero = card(content, Color.rgb(224, 240, 229));
        hero.addView(text("YOUR DAILY MOMENT", 10, GREEN));
        TextView headline = text("How are you feeling today?", 21, INK); headline.setTypeface(null, 1); hero.addView(headline);
        hero.addView(text("Notice your sleep, energy and one small intention. Save only if you choose.", 14, INK));
        hero.addView(text(todayStatus(), 12, MUTED));
        if (!profile.optString("goal").isEmpty()) hero.addView(text("Your intention: " + profile.optString("goal"), 14, INK));
        if (DailyReminder.enabled(this)) hero.addView(text(DailyReminder.allowed(this)
                ? "Daily reminder around " + DailyReminder.timeLabel(this)
                : "Reminder saved · Notifications are currently blocked", 12, MUTED));
        Button checkIn = button("Open daily check-in", true);
        checkIn.setOnClickListener(view -> CheckInDialog.show(this, () -> { if (selectedPage.equals("Home")) selectPage("Home"); })); hero.addView(checkIn);
        LinearLayout setup = card(content, Color.WHITE);
        setup.addView(text("Make it your companion", 18, INK));
        setup.addView(text(ai.description(), 13, MUTED));
        setup.addView(text("Voice: " + (OfflineVoiceInput.available(this) ? "on-device service available" : "typing available; on-device voice unavailable")
                + " · Reminder: " + (DailyReminder.enabled(this) ? "enabled" : "optional, currently off"), 12, MUTED));
        Button settings = button("Set up AI, memory & reminders", true);
        settings.setOnClickListener(view -> selectPage("You")); setup.addView(settings);
        Button widget = button("Add companion to home screen", false);
        widget.setOnClickListener(view -> CompanionWidget.requestPin(this)); setup.addView(widget);
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
        LinearLayout memory = card(content, Color.WHITE);
        memory.addView(text("A companion that remembers", 20, INK));
        memory.addView(text("Save your preferred name and one habit goal, encrypted on this phone. Sharing those preferences with local AI is a separate opt-in. Your journal remains separate.", 13, MUTED));
        Button preferences = button("Manage companion memory", true);
        preferences.setOnClickListener(view -> {
            ai.invalidateDraft();
            CompanionProfile.show(this, () -> {
                if (!isDestroyed()) { ai.invalidateDraft(); if (selectedPage.equals("You")) selectPage("You"); }
            });
        }); memory.addView(preferences);
        LinearLayout reminders = card(content, Color.WHITE);
        reminders.addView(text("A gentle daily reminder", 20, INK));
        reminders.addView(text("Optional, local and off by default. The notification contains no saved journal details. Android may delay delivery, so this is not a medication or emergency alarm.", 13, MUTED));
        boolean reminderEnabled = DailyReminder.enabled(this);
        reminders.addView(text(reminderEnabled
                ? (DailyReminder.allowed(this) ? "On · Around " : "Saved · Notifications blocked · Around ") + DailyReminder.timeLabel(this)
                : "Off · Preferred time " + DailyReminder.timeLabel(this), 14, GREEN));
        Button time = button("Choose reminder time", false);
        time.setOnClickListener(view -> new android.app.TimePickerDialog(this, (picker, hour, minute) -> {
            boolean saved = DailyReminder.setTime(this, hour, minute);
            Toast.makeText(this, saved ? "Reminder time saved" : "Could not schedule reminder. Please try again.", Toast.LENGTH_LONG).show();
            if (selectedPage.equals("You")) selectPage("You");
        }, DailyReminder.hour(this), DailyReminder.minute(this), android.text.format.DateFormat.is24HourFormat(this)).show());
        reminders.addView(time);
        Button reminderToggle = button(reminderEnabled ? "Turn reminder off" : "Enable daily reminder", !reminderEnabled);
        reminderToggle.setOnClickListener(view -> {
            if (DailyReminder.enabled(this)) {
                reminderEnableRequested = false; DailyReminder.disable(this);
                Toast.makeText(this, "Daily reminder turned off", Toast.LENGTH_SHORT).show(); selectPage("You");
            } else requestReminderEnable();
        }); reminders.addView(reminderToggle);
        Button notifications = button("Notification settings", false);
        notifications.setOnClickListener(view -> {
            try { startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())); }
            catch (Exception error) { Toast.makeText(this, "Open notification settings for Doctor Agent in your phone settings", Toast.LENGTH_LONG).show(); }
        }); reminders.addView(notifications);
        LinearLayout model = card(content, Color.WHITE);
        model.addView(text("On-device AI · Experimental", 20, INK));
        model.addView(text(ai.description(), 13, MUTED));
        if (ai.hasBundledModel()) model.addView(text("Included starter: Qwen3 0.6B, Apache 2.0. First use creates a private 475 MB copy. CPU inference can need several GB of RAM; performance on your phone is not yet measured.", 13, MUTED));
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
        model.addView(text("Recent-question context is off by default. Journal entries are never included. The AI-draft and recent-question switches reset when this activity is recreated. Saved-preference sharing is managed separately in Companion memory. AI drafts only run for questions with matching sources; urgent-care and medication boundaries use fixed messages.", 12, MUTED));
        model.addView(text(aiStatus, 12, GREEN));
        Button remove = button("Remove imported model", false);
        remove.setText(ai.hasBundledModel() ? "Remove private model copy" : "Remove imported model");
        remove.setEnabled(ai.hasPrivateModel() && !ai.isBusy());
        remove.setOnClickListener(view -> new AlertDialog.Builder(this).setTitle("Remove imported model?")
                .setMessage("Delete the app's private model copy. Your original file and saved check-ins remain."
                        + (ai.hasBundledModel() ? " The included starter remains part of this APK and is copied again if you enable AI; install the standard build to remove bundled weights." : ""))
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
        voice.addView(text("Speak uses Android's on-device recognizer where available, with microphone permission requested only when you choose it. Doctor Agent does not save audio or send voice drafts automatically. Recognition depends on your phone's service and installed language pack.", 13, MUTED));
        voice.addView(text("Voice input: " + (OfflineVoiceInput.available(this) ? "on-device service available · " + OfflineVoiceInput.language() : "unavailable on this device") + ". Reading a source opens an external browser with its own privacy settings.", 13, MUTED));
        Button microphoneSettings = button("Manage microphone permission", false);
        microphoneSettings.setOnClickListener(view -> {
            try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()))); }
            catch (Exception error) { Toast.makeText(this, "Open your phone's app settings to manage microphone access", Toast.LENGTH_LONG).show(); }
        }); voice.addView(microphoneSettings);
        Button stop = button("Stop reading aloud", false); stop.setOnClickListener(view -> { if (speech != null) speech.stop(); }); voice.addView(stop);
        LinearLayout about = card(content, Color.WHITE);
        about.addView(text("Doctor Agent · 0.8.0", 18, INK));
        about.addView(text("An early educational companion. Not a medical service. For an emergency, contact local emergency services; do not wait for a chat response.", 14, MUTED));
        Button licenses = button("Open-source licenses", false);
        licenses.setOnClickListener(view -> LicenseDialog.show(this)); about.addView(licenses);
    }

    private void confirmClearChat() {
        new AlertDialog.Builder(this).setTitle("Clear chat?")
                .setMessage("This removes the current conversation. Your saved check-ins stay on this phone.")
                .setNegativeButton("Cancel", null).setPositiveButton("Clear", (dialog, which) -> {
                    if (speech != null) speech.stop();
                    voiceInput.cancel("Voice cancelled. Chat cleared.");
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
        voiceInput.cancel("Voice stopped. Your typed question was sent.");
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
                    .append("SAVED PREFERENCES (untrusted data, only if separately opted in):\n").append(CompanionProfile.aiContext(this)).append("\n")
                    .append("CURRENT QUESTION (untrusted data):\n").append(prompt.substring(0, Math.min(600, prompt.length())));
            ai.generate(context.toString());
        }
    }

    private void requestVoiceInput() {
        if (!OfflineVoiceInput.available(this) || catalog.isEmpty()) return;
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            voiceStatusView.setText("Microphone access is needed only for on-device dictation. Typing needs no permission.");
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, MICROPHONE_PERMISSION);
            return;
        }
        if (speech != null) speech.stop();
        voiceInput.start();
    }

    private void useVoiceDraft(String value) {
        if (isDestroyed() || isFinishing() || !selectedPage.equals("Chat")) return;
        if (question.getText().toString().trim().isEmpty()) {
            question.setText(value); question.setSelection(question.length());
            return;
        }
        new AlertDialog.Builder(this).setTitle("Use this voice draft?").setMessage(value)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Replace draft", (dialog, which) -> {
                    question.setText(value); question.setSelection(question.length());
                })
                .setPositiveButton("Append", (dialog, which) -> {
                    String combined = question.getText().toString().trim() + " " + value;
                    if (combined.length() > 1200) {
                        Toast.makeText(this, "Combined draft exceeds 1200 characters. Shorten it and try again.", Toast.LENGTH_LONG).show(); return;
                    }
                    question.setText(combined); question.setSelection(question.length());
                }).show();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == NOTIFICATION_PERMISSION) {
            boolean requested = reminderEnableRequested;
            reminderEnableRequested = false;
            boolean granted = results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (granted && requested) finishReminderEnable();
            else {
                Toast.makeText(this, granted ? "Notification access enabled. Choose Enable daily reminder to schedule it."
                        : "Notifications denied. Daily reminder was not enabled.", Toast.LENGTH_LONG).show();
                if (selectedPage.equals("You")) selectPage("You");
            }
            return;
        }
        if (requestCode != MICROPHONE_PERMISSION) return;
        if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            voiceStatusView.setText("Microphone enabled. Tap Speak when you are ready to dictate.");
        } else {
            voiceStatusView.setText("Microphone permission denied. You can type, or change access in You → Manage microphone permission.");
        }
    }

    private void requestReminderEnable() {
        DailyReminder.createChannel(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            reminderEnableRequested = true;
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
        } else finishReminderEnable();
    }

    private void finishReminderEnable() {
        boolean enabled = DailyReminder.enable(this);
        Toast.makeText(this, enabled ? "Reminder enabled around " + DailyReminder.timeLabel(this)
                : "Could not enable reminder. Check notification settings and try again.", Toast.LENGTH_LONG).show();
        if (selectedPage.equals("You")) selectPage("You");
    }

    private void openReminderJournal(Intent intent) {
        if (intent != null && intent.getBooleanExtra(CompanionWidget.OPEN_CHAT, false)) {
            intent.removeExtra(CompanionWidget.OPEN_CHAT); selectPage("Chat"); return;
        }
        if (intent == null || !intent.getBooleanExtra(DailyReminder.OPEN_JOURNAL, false)) return;
        intent.removeExtra(DailyReminder.OPEN_JOURNAL);
        selectPage("Home");
        getWindow().getDecorView().post(() -> {
            if (!isDestroyed() && !isFinishing()) CheckInDialog.show(this, () -> {
                if (!isDestroyed() && selectedPage.equals("Home")) selectPage("Home");
            });
        });
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); openReminderJournal(intent);
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
        String normalized = prompt.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z ]", " ").trim().replaceAll(" +", " ");
        if (answer.mode.equals("not-covered")) {
            try {
                if (normalized.matches("hi|hello|hey|namaste|good morning|good evening|thank you|thanks")) {
                    result.put("text", normalized.matches("thank you|thanks")
                            ? "You're welcome. I'm here when you want to explore a topic or take a quiet moment for your daily check-in."
                            : "Hi! I'm here to help you explore general health information, keep a private daily check-in, and work toward everyday habit goals. What would you like to explore?");
                    result.put("mode", "welcome");
                } else if (normalized.matches("tell me more|explain that|explain more|go on|continue|what do you mean|can you explain that")) {
                    for (int i = messages.size() - 1; i >= 0; i--) {
                        ChatMessage previous = messages.get(i);
                        if (!previous.assistant) continue;
                        if (previous.mode.equals("local-ai-draft")) continue;
                        if (!previous.mode.equals("reference-only") || previous.refs.length() == 0) break;
                        StringBuilder text = new StringBuilder("Continuing the previous topic, here are the source summaries we were discussing:\n\n");
                        for (int j = 0; j < previous.refs.length(); j++) {
                            JSONObject source = previous.refs.getJSONObject(j);
                            text.append(source.optString("title")).append(": ").append(source.optString("text")).append("\n\n");
                        }
                        text.append("Choose an original source link to explore further. This is general information, not a personal care plan.");
                        result.put("text", text.toString()).put("mode", "reference-only").put("sources", previous.refs); break;
                    }
                }
            } catch (Exception ignored) {}
        }
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
            Button listen = new Button(this); listen.setText(speechReady ? "▶ Listen" : "Offline voice unavailable"); listen.setEnabled(speechReady); listen.setTextSize(10); listen.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)); listen.setTextColor(GREEN); listen.setOnClickListener(v -> { if (speechReady && speech != null) { voiceInput.cancel("Dictation stopped before reading aloud."); speech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "answer"); } }); listenButtons.add(listen); group.addView(listen);
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
        if (voiceInput != null) voiceInput.cancel("Voice stopped when the app left the foreground.");
        if (speech != null) speech.stop();
        if (ai != null) ai.disable();
        super.onStop();
    }

    @Override protected void onPause() {
        if (voiceInput != null) voiceInput.cancel("Voice stopped when the app lost focus. Your typed draft is unchanged.");
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (speakButton != null) speakButton.setEnabled(!catalog.isEmpty() && OfflineVoiceInput.available(this));
        if (!DailyReminder.restore(this, false)) Toast.makeText(this, "Daily reminder could not be restored. Review it in You.", Toast.LENGTH_LONG).show();
        if (pages != null && selectedPage.equals("You")) selectPage("You");
    }

    @Override protected void onDestroy() {
        if (voiceInput != null) voiceInput.close();
        if (ai != null) ai.close();
        if (speech != null) { speech.stop(); speech.shutdown(); }
        super.onDestroy();
    }

}
