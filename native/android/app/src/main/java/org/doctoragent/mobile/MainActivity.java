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
import android.view.MotionEvent;
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
import java.util.HashMap;

public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(37, 35, 55);
    private static final int GREEN = Color.rgb(106, 82, 174);
    private static final int MUTED = Color.rgb(119, 114, 135);
    private static final int PANEL = Color.WHITE;
    private static final int BACKGROUND = Color.rgb(250, 249, 253);
    private static final int BORDER = Color.rgb(233, 229, 241);
    private FrameLayout pages;
    private LinearLayout chatPage;
    private String selectedPage = "Chat";
    private CompanionDrawer drawer;
    private Button stopAiButton;
    private final ArrayList<Button> tabs = new ArrayList<>();
    private final ArrayList<JSONObject> catalog = new ArrayList<>();
    private final HashMap<String, JSONObject> localizedCatalog = new HashMap<>();
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
    private boolean pendingAiHindi, pendingAiDevanagari;
    private static final int IMPORT_MODEL = 410;
    private static final int MICROPHONE_PERMISSION = 411;
    private static final int NOTIFICATION_PERMISSION = 412;
    private boolean reminderEnableRequested;
    private OfflineVoiceInput voiceInput;
    private TextView voiceStatusView;
    private Button speakButton;
    private ChatHistoryStore chatHistory;
    private String savedChatId;
    private TextView chatStorageStatus;
    private Button saveChatButton;
    private boolean replayingChat;
    private VisitNotes.Draft visitNotesDraft = new VisitNotes.Draft();

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
        final String draft, page, savedChatId;
        final VisitNotes.Draft visitDraft;
        Session(ArrayList<ChatMessage> messages, String draft, String page, String savedChatId, VisitNotes.Draft visitDraft) {
            this.messages = new ArrayList<>(messages); this.draft = draft; this.page = page;
            this.savedChatId = savedChatId; this.visitDraft = visitDraft.copy();
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(250, 249, 253));
        getWindow().setNavigationBarColor(Color.rgb(250, 249, 253));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        loadCatalog();
        chatHistory = new ChatHistoryStore(this);
        ai = new LocalAiModel(this, new LocalAiModel.Listener() {
            @Override public void onStatus(String value) {
                aiStatus = value;
                if (aiStatusView != null) aiStatusView.setText(value);
                if (stopAiButton != null) stopAiButton.setVisibility(ai.isBusy() ? View.VISIBLE : View.GONE);
                if (selectedPage.equals("You")) selectPage("You");
            }
            @Override public void onDraft(String value) {
                String prefix = pendingAiDevanagari ? "AI का मसौदा · सत्यापित नहीं\n\n"
                        : pendingAiHindi ? "AI draft · verify nahi kiya gaya\n\n" : "AI draft · Not verified\n\n";
                String suffix = pendingAiDevanagari ? "\n\nइसे मूल स्रोत-सारांश के साथ पढ़ें। यह व्यक्तिगत चिकित्सीय सलाह नहीं है।"
                        : pendingAiHindi ? "\n\nIse original sources ke saath padhein. Yeh niji medical salah nahi hai."
                        : "\n\nCompare this with the original source summaries above. This is not personal medical advice.";
                addAssistant(prefix + value + suffix, pendingAiSources, "local-ai-draft");
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
            savedChatId = retained.savedChatId;
            visitNotesDraft = retained.visitDraft;
            replayingChat = true;
            for (ChatMessage message : retained.messages) addBubble(message.text, message.assistant, message.refs, message.mode);
            replayingChat = false;
            updateChatStorageStatus();
            question.setText(retained.draft);
            selectPage(retained.page);
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            openReminderJournal(getIntent());
            return;
        }
        addAssistant(catalog.isEmpty() ? "The bundled source library could not load. Reinstall a complete app build before using chat." : "Hi, I’m Doctor Agent.\n\nWhat’s on your mind?\n\nExplore general health information, or open Care to organize questions for your next doctor visit.", new JSONArray(), "welcome");
        selectPage("Chat");
        openReminderJournal(getIntent());
    }

    private void buildScreen() {
        LinearLayout root = column();
        root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(18), dp(8), dp(18), dp(8));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.ime());
                view.setPadding(dp(16) + bars.left, dp(8) + bars.top, dp(16) + bars.right, dp(8) + bars.bottom);
            } else {
                view.setPadding(dp(16) + insets.getSystemWindowInsetLeft(), dp(8) + insets.getSystemWindowInsetTop(), dp(16) + insets.getSystemWindowInsetRight(), dp(8) + insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        FrameLayout surface = new FrameLayout(this); surface.addView(root);
        drawer = new CompanionDrawer(this, surface);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button menu = button("☰", false); menu.setContentDescription("Open navigation and saved conversations");
        menu.setMinWidth(0); menu.setMinimumWidth(0); menu.setTextSize(22);
        menu.setOnClickListener(v -> showNavigation()); header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout brand = column(); brand.setPadding(dp(12), 0, 0, 0);
        TextView name = text("Doctor Agent", 19, INK); name.setTypeface(null, 1);
        brand.addView(name); brand.addView(text("●  Private health companion", 10, GREEN));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button fresh = button("＋", false); fresh.setContentDescription("Start a new conversation");
        fresh.setMinWidth(0); fresh.setMinimumWidth(0); fresh.setTextSize(23); fresh.setOnClickListener(v -> confirmClearChat());
        header.addView(fresh, new LinearLayout.LayoutParams(dp(48), dp(48))); root.addView(header);
        pages = new FrameLayout(this);
        root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        buildChat();
        setContentView(surface);
        root.requestApplyInsets();
    }

    private void buildChat() {
        chatPage = column();
        aiStatusView = text(aiStatus, 10, MUTED); aiStatusView.setMaxLines(2);
        chatPage.addView(aiStatusView);
        LinearLayout promptBar = new LinearLayout(this);
        promptBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView explore = text("Explore", 11, MUTED); explore.setPadding(0, 0, dp(8), 0);
        promptBar.addView(explore);
        android.widget.HorizontalScrollView topicScroll = new android.widget.HorizontalScrollView(this);
        topicScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout topicRow = new LinearLayout(this);
        String[][] starters = {{"Symptoms", "Help me organize symptoms for my doctor."},
                {"Reports", "Explain lab reports."}, {"Medicines", "Tell me about medicine safety."},
                {"Mental health", "Tell me about mental health."}, {"Conditions", "Tell me about diabetes and blood pressure."}};
        for (String[] starter : starters) {
            Button chip = button(starter[0], false);
            chip.setTextSize(11); chip.setMinHeight(dp(38)); chip.setMinimumHeight(dp(38));
            chip.setPadding(dp(10), 0, dp(10), 0);
            chip.setOnClickListener(view -> offerTopic(starter[1]));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, dp(38));
            chipParams.rightMargin = dp(6); topicRow.addView(chip, chipParams);
        }
        topicScroll.addView(topicRow);
        promptBar.addView(topicScroll, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams promptParams = new LinearLayout.LayoutParams(-1, -2);
        promptParams.topMargin = dp(8); promptParams.bottomMargin = dp(4);
        chatPage.addView(promptBar, promptParams);
        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        transcript = column(); transcript.setPadding(0, dp(8), 0, dp(12));
        scroll.addView(transcript);
        chatPage.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout composer = column(); composer.setPadding(dp(10), dp(4), dp(10), dp(6));
        android.graphics.drawable.GradientDrawable surface = round(Color.WHITE, dp(25));
        surface.setStroke(dp(1), BORDER); composer.setBackground(surface); composer.setElevation(dp(3));
        question = new EditText(this);
        question.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1200)});
        question.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        question.setMinLines(1); question.setMaxLines(4); question.setTextSize(16); question.setTextColor(INK);
        question.setHintTextColor(MUTED); question.setSaveEnabled(false); question.setEnabled(!catalog.isEmpty());
        question.setHint("What’s on your mind?"); question.setPadding(dp(8), dp(12), dp(8), dp(12));
        question.setBackgroundColor(Color.TRANSPARENT); question.setImeOptions(EditorInfo.IME_ACTION_SEND);
        question.setOnEditorActionListener((v, action, event) -> { if (action == EditorInfo.IME_ACTION_SEND) { send(); return true; } return false; });
        composer.addView(question, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER_VERTICAL);
        speakButton = button("Voice", false); speakButton.setContentDescription("Dictate a question using on-device voice");
        speakButton.setEnabled(!catalog.isEmpty() && OfflineVoiceInput.available(this));
        speakButton.setOnClickListener(view -> {
            if (voiceInput.isListening()) voiceInput.cancel("Voice cancelled. Your typed draft is unchanged."); else requestVoiceInput();
        }); controls.addView(speakButton);
        saveChatButton = button("Save", false); saveChatButton.setTextSize(12);
        saveChatButton.setContentDescription("Save an encrypted snapshot of this conversation");
        saveChatButton.setOnClickListener(v -> saveCurrentChat()); controls.addView(saveChatButton);
        controls.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        stopAiButton = button("Stop", false); stopAiButton.setContentDescription("Stop local AI generation"); stopAiButton.setVisibility(View.GONE);
        stopAiButton.setOnClickListener(v -> { if (ai.isBusy()) ai.cancel(); }); controls.addView(stopAiButton);
        Button sendButton = button("↑", true); sendButton.setTextSize(23); sendButton.setMinWidth(0); sendButton.setMinimumWidth(0);
        sendButton.setContentDescription("Send message"); sendButton.setEnabled(!catalog.isEmpty()); sendButton.setOnClickListener(v -> send());
        controls.addView(sendButton, new LinearLayout.LayoutParams(dp(48), dp(48))); composer.addView(controls);
        chatPage.addView(composer);
        voiceStatusView = text(OfflineVoiceInput.available(this) ? "Voice drafts stay on device · Review before sending" : "Typing available · Offline voice unavailable", 10, MUTED);
        voiceStatusView.setMaxLines(2); chatPage.addView(voiceStatusView);
        chatStorageStatus = text("Session only · Save to keep a copy", 10, MUTED); chatStorageStatus.setGravity(Gravity.CENTER);
        chatPage.addView(chatStorageStatus);
    }

    private void selectPage(String page) {
        if (drawer != null) drawer.close();
        if (speech != null) speech.stop();
        if (!page.equals("Chat")) {
            if (voiceInput != null) voiceInput.cancel("Voice stopped. Your typed draft is unchanged.");
            android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(question.getWindowToken(), 0);
            question.clearFocus();
        }
        selectedPage = page;
        pages.removeAllViews();
        if (page.equals("Chat")) { pages.addView(chatPage); animatePage(chatPage); }
        else {
            ScrollView pageScroll = new ScrollView(this); pageScroll.setFillViewport(true);
            LinearLayout content = column(); content.setPadding(0, dp(4), 0, dp(16));
            if (page.equals("Home")) buildHome(content);
            else if (page.equals("Care")) buildCare(content);
            else if (page.equals("Library")) buildLibrary(content);
            else buildPrivacy(content);
            pageScroll.addView(content); pages.addView(pageScroll); animatePage(pageScroll);
        }
        for (Button tab : tabs) {
            boolean active = tab.getText().toString().equals(page);
            tab.setSelected(active);
            tab.setTextColor(active ? Color.WHITE : MUTED);
            tab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(active ? GREEN : BACKGROUND));
            tab.animate().cancel();
            tab.animate().translationY(active ? -dp(2) : 0).setDuration(160).start();
        }
    }

    private void animatePage(View page) {
        page.setAlpha(0f); page.setTranslationY(dp(9));
        page.post(() -> {
            if (page.getParent() != pages || !android.animation.ValueAnimator.areAnimatorsEnabled()) {
                page.setAlpha(1f); page.setTranslationY(0); return;
            }
            page.animate().alpha(1f).translationY(0).setDuration(210)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        });
    }

    private void buildCare(LinearLayout content) {
        CareWorkspace.build(this, content, visitNotesDraft, notes -> {
            ai.invalidateDraft();
            selectPage("Chat");
            addUser(notes);
            JSONArray refs = new JSONArray();
            for (JSONObject source : catalog) if (source.optString("id").equals("medlineplus-doctor-visit")) refs.put(source);
            addAssistant(VisitNotes.questions(), refs, "visit-summary");
        });
    }

    private void buildHome(LinearLayout content) {
        String greeting = java.time.LocalTime.now().getHour() < 12 ? "Good morning" : java.time.LocalTime.now().getHour() < 18 ? "Good afternoon" : "Good evening";
        JSONObject profile = new JSONObject();
        try { profile = CompanionProfile.read(this); } catch (Exception ignored) {}
        String preferredName = profile.optString("name");
        content.addView(text(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMM", java.util.Locale.getDefault())), 12, MUTED));
        TextView welcome = text(greeting + (preferredName.isEmpty() ? "." : ", " + preferredName + ".") + "\nYour health, in one place.", 27, INK);
        welcome.setTypeface(null, 1); welcome.setPadding(0, dp(8), 0, dp(18)); content.addView(welcome);
        LinearLayout hero = card(content, Color.rgb(239, 234, 250));
        hero.addView(text("YOUR HEALTH COMPANION", 10, GREEN));
        TextView careHeadline = text("What can I help you with today?", 21, INK); careHeadline.setTypeface(null, 1); hero.addView(careHeadline);
        hero.addView(text("Explore health questions, understand medical terms and prepare for a conversation with your clinician.", 14, INK));
        Button askHealth = button("Ask a health question", true);
        askHealth.setOnClickListener(view -> { selectPage("Chat"); question.requestFocus(); }); hero.addView(askHealth);
        Button prepareVisit = button("Prepare symptom & visit notes", false);
        prepareVisit.setOnClickListener(view -> selectPage("Care")); hero.addView(prepareVisit);
        hero = card(content, Color.WHITE);
        hero.addView(text("YOUR DAILY CHECK-IN", 10, GREEN));
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
        heading(content, "A question to get started");
        content.addView(text("Pick a prompt, make it your own, then send when you're ready.", 13, MUTED));
        android.widget.HorizontalScrollView startersScroll = new android.widget.HorizontalScrollView(this);
        startersScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout starterRow = new LinearLayout(this);
        String[][] prompts = {{"01  ·  Symptoms", "Help me organize symptoms for my doctor."},
                {"02  ·  Lab reports", "Explain what lab reports can and cannot tell me."},
                {"03  ·  Medicines", "Tell me about medicine safety."},
                {"04  ·  Mental health", "Tell me about mental health."},
                {"05  ·  Conditions", "Tell me about diabetes and blood pressure."}};
        for (String[] topic : prompts) {
            Button prompt = button(topic[0] + "\nExplore this topic  →", false);
            prompt.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); prompt.setTextSize(12);
            prompt.setMinHeight(dp(72)); prompt.setMinimumHeight(dp(72)); prompt.setPadding(dp(14), dp(8), dp(14), dp(8));
            prompt.setOnClickListener(view -> { selectPage("Chat"); offerTopic(topic[1]); });
            prompt.setEnabled(!catalog.isEmpty());
            LinearLayout.LayoutParams topicParams = new LinearLayout.LayoutParams(dp(178), dp(74));
            topicParams.rightMargin = dp(9); starterRow.addView(prompt, topicParams);
        }
        startersScroll.addView(starterRow);
        LinearLayout.LayoutParams starterParams = new LinearLayout.LayoutParams(-1, dp(82));
        starterParams.topMargin = dp(6); content.addView(startersScroll, starterParams);
        LinearLayout note = card(content, Color.WHITE);
        note.addView(text("Your health companion", 16, INK));
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
        context.setText("Include up to 3 recent source-backed exchanges as AI context");
        context.setChecked(includeRecentQuestions); context.setSaveEnabled(false);
        context.setOnCheckedChangeListener((view, checked) -> {
            includeRecentQuestions = checked;
            if (ai.isBusy()) ai.cancel();
        }); model.addView(context);
        model.addView(text("Recent-exchange context is off by default. It includes bounded previous questions and their source summaries, never previous AI replies or journal entries. Emergency and medication-boundary exchanges are excluded. The AI and recent-context switches reset when this activity is recreated. Saved-preference sharing is managed separately in Companion memory. AI drafts only run for questions with matching sources.", 12, MUTED));
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
        storage.addView(text("No account, ads or analytics. Chats stay in this session unless you explicitly save a snapshot. Saved chats, preferences and optional check-ins are encrypted on this phone and excluded from backup. Saved chats are opened only when you choose one in History. Journal entries remain separate from chat. Care forms stay in this session; reviewed notes enter Chat only when you choose Add to chat.", 14, MUTED));
        Button history = button("Manage saved chats", false); history.setOnClickListener(view -> showChatHistory()); storage.addView(history);
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
        String appVersion = "";
        try { appVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception ignored) {}
        about.addView(text("Doctor Agent · " + appVersion, 18, INK));
        about.addView(text("An early educational companion. Not a medical service. For an emergency, contact local emergency services; do not wait for a chat response.", 14, MUTED));
        Button licenses = button("Open-source licenses", false);
        licenses.setOnClickListener(view -> LicenseDialog.show(this)); about.addView(licenses);
    }

    private void confirmClearChat() {
        confirmLeaveCurrentChat(() -> {
            resetConversation(); savedChatId = null;
            addAssistant("A fresh start. What would you like to explore?", new JSONArray(), "welcome");
            updateChatStorageStatus(); selectPage("Chat");
        });
    }

    private boolean hasUserMessages() {
        for (ChatMessage message : messages) if (!message.assistant) return true;
        return false;
    }

    private void confirmLeaveCurrentChat(Runnable next) {
        if (!hasUserMessages() && question.getText().toString().trim().isEmpty()) { next.run(); return; }
        new AlertDialog.Builder(this).setTitle("Leave this chat?")
                .setMessage("The current session and unsent draft will be replaced. Use Save or Update first if you want to keep the latest messages. Existing saved copies and check-ins stay on this phone.")
                .setNegativeButton("Stay here", null).setPositiveButton("Continue", (dialog, which) -> next.run()).show();
    }

    private void resetConversation() {
        if (speech != null) speech.stop();
        voiceInput.cancel("Voice stopped. Conversation changed."); ai.invalidateDraft();
        question.setText(""); transcript.removeAllViews(); listenButtons.clear(); messages.clear();
    }

    private void updateChatStorageStatus() {
        if (chatStorageStatus != null) chatStorageStatus.setText(savedChatId == null
                ? "Session only · Save to keep a copy" : "Saved copy exists · Update to keep latest messages");
        if (saveChatButton != null) {
            saveChatButton.setText(savedChatId == null ? "Save" : "Update");
            saveChatButton.setEnabled(hasUserMessages());
        }
    }

    private void saveCurrentChat() {
        if (!hasUserMessages()) { Toast.makeText(this, "Start a conversation before saving", Toast.LENGTH_SHORT).show(); return; }
        if (savedChatId == null) new AlertDialog.Builder(this).setTitle("Save this chat on your phone?")
                .setMessage("Save an encrypted copy of the currently visible messages, including source links and labelled AI drafts. The title uses your first question; you can rename or delete it in History. Your unsent draft is excluded. This is a snapshot: use Update after more messages.")
                .setNegativeButton("Cancel", null).setPositiveButton("Save chat", (dialog, which) -> writeChatSnapshot()).show();
        else writeChatSnapshot();
    }

    private void writeChatSnapshot() {
        String id = savedChatId == null ? java.util.UUID.randomUUID().toString() : savedChatId;
        try {
            JSONArray snapshot = new JSONArray();
            for (ChatMessage message : messages) snapshot.put(new JSONObject().put("text", message.text)
                    .put("assistant", message.assistant).put("mode", message.mode).put("sources", message.refs));
            chatHistory.save(id, snapshot); savedChatId = id; updateChatStorageStatus();
            Toast.makeText(this, "Chat snapshot saved on this phone", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            String detail = error instanceof IllegalStateException && error.getMessage() != null
                    && (error.getMessage().startsWith("20 chats") || error.getMessage().startsWith("Saved chats have reached"))
                    ? error.getMessage() : "The chat could not be saved. Existing saved copies have not been replaced. Try again or review History.";
            new AlertDialog.Builder(this).setTitle("Chat was not saved").setMessage(detail).setPositiveButton("OK", null).show();
        }
    }

    private void showChatHistory() {
        ChatHistoryDialog.show(this, chatHistory, new ChatHistoryDialog.Listener() {
            @Override public void open(JSONObject chat) {
                openSavedChat(chat);
            }
            @Override public void deleted(String id) {
                if (id == null || id.equals(savedChatId)) { savedChatId = null; updateChatStorageStatus(); }
            }
        });
    }

    private void openSavedChat(JSONObject chat) {
                confirmLeaveCurrentChat(() -> {
                    ArrayList<ChatMessage> restored = new ArrayList<>();
                    try {
                        JSONArray snapshot = chat.getJSONArray("messages");
                        for (int i = 0; i < snapshot.length(); i++) {
                            JSONObject message = snapshot.getJSONObject(i);
                            restored.add(new ChatMessage(message.getString("text"), message.getBoolean("assistant"),
                                    message.getJSONArray("sources"), message.getString("mode")));
                        }
                        String id = chat.getString("id");
                        resetConversation(); savedChatId = id; replayingChat = true;
                        for (ChatMessage message : restored) addBubble(message.text, message.assistant, message.refs, message.mode);
                        replayingChat = false; updateChatStorageStatus(); selectPage("Chat");
                        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                    } catch (Exception error) {
                        replayingChat = false;
                        Toast.makeText(MainActivity.this, "This saved chat could not be opened", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showNavigation() {
        if (voiceInput != null) voiceInput.cancel("Voice stopped while navigation is open.");
        android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(question.getWindowToken(), 0);
        JSONArray chats = new JSONArray(); boolean readable = true;
        try { chats = chatHistory.read(); } catch (Exception error) { readable = false; }
        drawer.show(chats, readable, selectedPage, new CompanionDrawer.Listener() {
            public void page(String page) { selectPage(page); }
            public void fresh() { confirmClearChat(); }
            public void history() { showChatHistory(); }
            public void open(JSONObject chat) { openSavedChat(chat); }
        });
    }

    @Override public void onBackPressed() {
        if (drawer != null && drawer.isOpen()) { drawer.close(); return; }
        if (!selectedPage.equals("Chat")) { selectPage("Chat"); return; }
        super.onBackPressed();
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
        LinearLayout card = column(); card.setPadding(dp(18), dp(17), dp(18), dp(17));
        android.graphics.drawable.GradientDrawable surface = round(color, dp(22));
        surface.setStroke(dp(1), color == Color.WHITE ? BORDER : Color.rgb(215, 234, 220));
        card.setBackground(surface); card.setElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(12); content.addView(card, params); return card;
    }
    private Button button(String label, boolean primary) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(13);
        button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setTextColor(primary ? Color.WHITE : GREEN);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setPadding(dp(12), dp(6), dp(12), dp(6));
        android.graphics.drawable.GradientDrawable shape = round(primary ? GREEN : Color.rgb(241, 238, 248), dp(16));
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x226a52ae), shape, null));
        button.setOnTouchListener((view, event) -> {
            if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                view.animate().cancel(); view.animate().scaleX(.985f).scaleY(.985f).setDuration(80).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                view.animate().cancel(); view.animate().scaleX(1f).scaleY(1f).setDuration(150)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            }
            return false;
        });
        return button;
    }

    private JSONArray recentSourceContext() {
        ArrayList<JSONObject> exchanges = new ArrayList<>();
        for (int i = messages.size() - 1; i > 0 && exchanges.size() < 3; i--) {
            ChatMessage response = messages.get(i);
            ChatMessage request = messages.get(i - 1);
            if (!response.assistant || request.assistant || !response.mode.equals("reference-only")) continue;
            try {
                JSONArray sources = new JSONArray(); int used = 0;
                for (int j = 0; j < response.refs.length(); j++) {
                    JSONObject source = response.refs.getJSONObject(j);
                    String title = source.optString("title"), text = source.optString("text");
                    if (used + title.length() + text.length() > 450) continue;
                    sources.put(new JSONObject().put("id", source.optString("id"))
                            .put("title", title).put("summary", text));
                    used += title.length() + text.length();
                }
                if (sources.length() == 0) continue;
                exchanges.add(new JSONObject().put("question", request.text.substring(0, Math.min(240, request.text.length())))
                        .put("sourceSummaries", sources).put("someSourcesOmitted", sources.length() < response.refs.length()));
            } catch (org.json.JSONException ignored) {}
        }
        JSONArray recent = new JSONArray();
        for (int i = exchanges.size() - 1; i >= 0; i--) recent.put(exchanges.get(i));
        return recent;
    }

    private ChatMessage previousSourceReply() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage reply = messages.get(i);
            if (!reply.assistant || reply.mode.equals("local-ai-draft")) continue;
            return reply.mode.equals("reference-only") && reply.refs.length() > 0 ? reply : null;
        }
        return null;
    }

    private void send() {
        String prompt = question.getText().toString().trim(); if (prompt.isEmpty()) return;
        voiceInput.cancel("Voice stopped. Your typed question was sent.");
        ai.invalidateDraft();
        String recent = includeRecentQuestions ? recentSourceContext().toString() : "[]";
        question.setText(""); addUser(prompt);
        JSONObject answer = answer(prompt);
        addAssistant(answer.optString("text"), answer.optJSONArray("sources") == null ? new JSONArray() : answer.optJSONArray("sources"), answer.optString("mode"));
        if (aiEnabled && ai.hasModel() && !ai.isBusy() && "reference-only".equals(answer.optString("mode"))) {
            pendingAiSources = answer.optJSONArray("sources");
            pendingAiHindi = LocalLanguage.isHindi(prompt);
            pendingAiDevanagari = LocalLanguage.isDevanagari(prompt);
            StringBuilder context = new StringBuilder("SOURCE SUMMARIES (reference data only):\n");
            for (int i = 0; i < pendingAiSources.length(); i++) {
                JSONObject source = pendingAiSources.optJSONObject(i);
                if (source != null) context.append(source.optString("title")).append(": ").append(source.optString("text")).append("\n");
            }
            context.append("RECENT SOURCE-BACKED EXCHANGES (untrusted data, not new evidence):\n").append(recent).append("\n")
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
            JSONObject localized = localizedCatalog.get(source.optString("id"));
            JSONArray localizedKeys = localized == null ? null : localized.optJSONArray("keywords");
            if (localizedKeys != null) for (int i=0; i<localizedKeys.length(); i++) keywords.add(localizedKeys.optString(i));
            ArrayList<String> routingTerms = new ArrayList<>();
            JSONArray routes = source.optJSONArray("retrieval_terms");
            if (routes != null) for (int i = 0; i < routes.length(); i++) routingTerms.add(routes.optString(i));
            sources.add(new EducationEngine.Source(source.optString("id"), source.optString("title"), keywords,
                    source.optString("text"), routingTerms));
        }
        EducationEngine.Answer answer = EducationEngine.answer(prompt, sources);
        JSONArray citations = new JSONArray();
        for (String id : answer.sourceIds) {
            for (JSONObject source : catalog) if (id.equals(source.optString("id"))) { citations.put(source); break; }
        }
        JSONObject result = new JSONObject();
        try { result.put("text", answer.text); result.put("mode", answer.mode); result.put("sources", citations); } catch (Exception ignored) { }
        // Apply the current question's safety boundary before resolving any follow-up.
        if (answer.mode.equals("urgent-care") || answer.mode.equals("professional-care")) return result;
        boolean hindi = LocalLanguage.isHindi(prompt);
        String normalized = ConversationContext.normalize(prompt);
        if (!answer.mode.equals("reference-only") && (ConversationContext.isFollowUp(prompt) || LocalLanguage.isFollowUp(prompt))) {
            try {
                ChatMessage previous = previousSourceReply();
                if (previous == null) {
                    result.put("text", "Which topic would you like to explore? I don't have a previous source-backed topic to continue. Choose a topic below, or type a more specific question.")
                            .put("mode", "clarification").put("sources", new JSONArray());
                } else {
                    StringBuilder text = new StringBuilder("Continuing our previous topic. This is the information available in the bundled summaries; I don't have extra examples or detail beyond them:\n\n");
                    for (int j = 0; j < previous.refs.length(); j++) {
                        JSONObject source = previous.refs.getJSONObject(j);
                        text.append(source.optString("title")).append(": ").append(source.optString("text")).append("\n\n");
                    }
                    text.append("You can ask a more specific question or open the original sources. This is general information, not a personal care plan.");
                    result.put("text", text.toString()).put("mode", "reference-only").put("sources", previous.refs);
                }
            } catch (org.json.JSONException ignored) {}
            if (hindi) localizeReply(result, prompt);
            return result;
        }
        if (ConversationContext.needsTopic(prompt) || LocalLanguage.needsTopic(prompt)) {
            try {
                result.put("text", "What would you like to focus on: medicines, lab reports, mental health, conditions, or preparing for a doctor visit? I can explain the information in my library and help you prepare questions for a healthcare professional. I can't assess symptoms or create a personal treatment plan.")
                        .put("mode", "clarification").put("sources", new JSONArray());
            } catch (org.json.JSONException ignored) {}
            if (hindi) localizeReply(result, prompt);
            return result;
        }
        if (answer.mode.equals("not-covered")) {
            try {
                if (LocalLanguage.isGreeting(prompt)) {
                    result.put("text", LocalLanguage.isThanks(prompt)
                            ? "You're welcome. I'm here when you want to explore a topic or take a quiet moment for your daily check-in."
                            : "Hi! I'm here to help you explore general health information, keep a private daily check-in, and work toward everyday habit goals. What would you like to explore?");
                    result.put("mode", "welcome");
                }
            } catch (Exception ignored) {}
        }
        if (hindi) localizeReply(result, prompt);
        return result;
    }

    private String localizedSummary(JSONObject source, boolean devanagari) {
        JSONObject localized = localizedCatalog.get(source.optString("id"));
        if (localized == null) return source.optString("title") + ": " + source.optString("text");
        String titleKey = devanagari ? "title_deva" : "title";
        String textKey = devanagari ? "text_deva" : "text";
        return localized.optString(titleKey, localized.optString("title", source.optString("title"))) + ": "
                + localized.optString(textKey, localized.optString("text", source.optString("text")));
    }

    private void localizeReply(JSONObject reply, String prompt) {
        String mode = reply.optString("mode");
        JSONArray refs = reply.optJSONArray("sources");
        boolean devanagari = LocalLanguage.isDevanagari(prompt);
        try {
            if ("reference-only".equals(mode) && refs != null && refs.length() > 0) {
                StringBuilder text = new StringBuilder(devanagari ? "मेरे स्रोतों से सामान्य जानकारी:\n\n" : "Mere sources se saamanya jankari:\n\n");
                JSONArray translatedRefs = new JSONArray();
                for (int i = 0; i < refs.length(); i++) {
                    JSONObject source = refs.getJSONObject(i);
                    text.append(localizedSummary(source, devanagari)).append("\n\n");
                    JSONObject display = new JSONObject(source.toString());
                    JSONObject localized = localizedCatalog.get(source.optString("id"));
                    if (localized != null) display.put("title", localized.optString(devanagari ? "title_deva" : "title", localized.optString("title", source.optString("title"))));
                    translatedRefs.put(display);
                }
                text.append(devanagari ? "यह सामान्य जानकारी है, व्यक्तिगत जाँच या उपचार की सलाह नहीं।"
                        : "Yeh aam jankari hai, aapke liye niji jaanch ya ilaaj ki salah nahi.");
                reply.put("text", text.toString()).put("sources", translatedRefs);
            } else if ("clarification".equals(mode)) {
                reply.put("text", LocalLanguage.askForTopicMessage(prompt));
            } else if ("welcome".equals(mode)) {
                if (LocalLanguage.isThanks(prompt)) reply.put("text", devanagari
                        ? "कोई बात नहीं। जब चाहें किसी सामान्य स्वास्थ्य विषय पर पूछें या अपना दैनिक चेक-इन करें।"
                        : "Koi baat nahi. Jab chahein kisi aam health topic par baat karein ya apna daily check-in karein.");
                else reply.put("text", devanagari
                        ? "नमस्ते! मैं दवाओं, लैब रिपोर्ट, मानसिक स्वास्थ्य, बीमारियों और डॉक्टर से मिलने की तैयारी की सामान्य जानकारी खोजने में मदद कर सकता हूँ। आप किस बारे में पूछना चाहेंगे?"
                        : "Namaste! Main dawaiyon, lab reports, mental health, conditions aur doctor visit ki taiyari par aam jankari dhoondhne mein madad kar sakta hoon. Aap kis baare mein poochhna chahenge?");
            } else if ("not-covered".equals(mode)) {
                reply.put("text", devanagari
                        ? "इस विषय पर मेरी छोटी स्रोत-पुस्तक में उपयुक्त जानकारी नहीं मिली। आप दवाओं, लैब रिपोर्ट, मानसिक स्वास्थ्य या डॉक्टर से मिलने की तैयारी के बारे में पूछ सकते हैं; या किसी योग्य स्वास्थ्य पेशेवर से बात करें।"
                        : "Is topic ke liye meri chhoti source library mein munasib jankari nahi mili. Aap medicines, lab reports, mental health ya doctor visit ki taiyari ke baare mein poochh sakte hain; ya kisi qualified healthcare professional se baat karein.");
            }
        } catch (org.json.JSONException ignored) {}
    }

    private void addUser(String text) { addBubble(text, false, new JSONArray(), ""); }
    private void addAssistant(String text, JSONArray refs, String mode) { addBubble(text, true, refs, mode); }

    private void offerTopic(String prompt) {
        Runnable fill = () -> {
            voiceInput.cancel("Voice stopped. Review your suggested question before sending.");
            question.setText(prompt); question.setSelection(question.length()); question.requestFocus();
        };
        if (question.getText().toString().trim().isEmpty()) fill.run();
        else new AlertDialog.Builder(this).setTitle("Replace your draft?")
                .setMessage("Your current unsent question will be replaced with: " + prompt)
                .setPositiveButton("Replace draft", (dialog, which) -> fill.run())
                .setNegativeButton("Keep draft", null).show();
    }

    private void addBubble(String text, boolean assistant, JSONArray refs, String mode) {
        messages.add(new ChatMessage(text, assistant, refs, mode));
        if (messages.size() > 80) {
            messages.remove(0);
            LinearLayout oldest = (LinearLayout) transcript.getChildAt(0);
            if (oldest != null) {
                for (Button listen : new ArrayList<>(listenButtons)) {
                    android.view.ViewParent parent = listen.getParent();
                    while (parent != null && parent != oldest) parent = parent.getParent();
                    if (parent == oldest) listenButtons.remove(listen);
                }
                transcript.removeViewAt(0);
            }
        }
        LinearLayout group = new LinearLayout(this); group.setOrientation(LinearLayout.VERTICAL); group.setPadding(dp(15), dp(13), dp(15), dp(11));
        android.graphics.drawable.GradientDrawable bubble = round(assistant ? PANEL : Color.rgb(225, 240, 230), assistant ? dp(20) : dp(21));
        if (!assistant) bubble.setStroke(dp(1), BORDER);
        group.setBackground(assistant ? round(BACKGROUND, dp(20)) : round(Color.rgb(239, 234, 250), dp(21)));
        group.setElevation(0);
        TextView speaker = text(assistant ? "DOCTOR AGENT" : "YOU", 9, assistant ? GREEN : MUTED);
        speaker.setTypeface(null, 1); speaker.setLetterSpacing(.06f); speaker.setPadding(0, 0, 0, dp(6)); group.addView(speaker);
        TextView body = new TextView(this); body.setText(text); body.setTextColor(INK); body.setTextSize(mode.equals("welcome") ? 23 : 16); body.setLineSpacing(dp(3), 1); body.setTextIsSelectable(true); group.addView(body);
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        if (assistant) {
            TextView meta = new TextView(this); meta.setText(mode.equals("visit-summary") ? "YOUR NOTES · NOT A CLINICAL ASSESSMENT" : mode.equals("local-ai-draft") ? "LOCAL AI DRAFT · NOT VERIFIED" : mode.equals("urgent-care") ? "URGENT · SEEK IN-PERSON HELP" : mode.equals("professional-care") ? "PLEASE ASK A HEALTHCARE PROFESSIONAL" : mode.equals("not-covered") ? "OUTSIDE THIS LIBRARY" : mode.equals("welcome") ? "YOUR COMPANION · GENERAL EDUCATION" : "SOURCE SUMMARY · GENERAL INFORMATION"); meta.setTextColor(MUTED); meta.setTextSize(9); meta.setPadding(0, dp(8), 0, 0); group.addView(meta);
            if (mode.equals("clarification")) meta.setText("LET'S CHOOSE A TOPIC");
            if (!mode.equals("urgent-care") && !mode.equals("local-ai-draft") && !mode.equals("visit-summary")) {
                Button care = button("Prepare visit notes", false);
                care.setOnClickListener(view -> selectPage("Care")); group.addView(care);
            }
            if (mode.equals("clarification") || mode.equals("not-covered")) {
                boolean lastDevanagari = lastUserUsedDevanagari();
                group.addView(text(lastDevanagari ? "विषय चुनें। संदेश भेजने से पहले मसौदा पढ़ लें।"
                        : lastUserUsedHindi() ? "Topic chunein. Bhejne se pehle draft padh lein."
                        : "Choose a topic to fill a draft. Review it, then tap Send.", 11, MUTED));
                android.widget.HorizontalScrollView topics = new android.widget.HorizontalScrollView(this);
                LinearLayout row = new LinearLayout(this);
                String[][] suggestedTopics = lastDevanagari
                        ? new String[][]{{"रिपोर्ट", "जाँच रिपोर्ट के बारे में बताइए।"}, {"दवाएँ", "दवाओं के बारे में बताइए।"},
                        {"मानसिक स्वास्थ्य", "मानसिक स्वास्थ्य के बारे में बताइए।"}, {"मधुमेह", "मधुमेह के बारे में बताइए।"},
                        {"रक्तचाप", "रक्तचाप के बारे में बताइए।"}}
                        : lastUserUsedHindi()
                        ? new String[][]{{"Reports", "Lab reports ke baare mein batao."}, {"Medicines", "Medicines ke baare mein batao."},
                        {"Mental health", "Mental health ke baare mein batao."}, {"Diabetes", "Diabetes ke baare mein batao."},
                        {"Doctor visit", "Doctor appointment ke liye taiyari samjhao."}}
                        : new String[][]{{"Reports", "Explain lab reports."}, {"Medicines", "Tell me about medicine safety."},
                        {"Mental health", "Tell me about mental health."}, {"Conditions", "Tell me about diabetes and blood pressure."},
                        {"Doctor visit", "Help me organize symptoms for my doctor."}};
                for (String[] topic : suggestedTopics) {
                    Button choice = button(topic[0], false);
                    choice.setOnClickListener(view -> offerTopic(topic[1])); row.addView(choice);
                }
                topics.addView(row); group.addView(topics);
            }
            Button listen = new Button(this); listen.setText(speechReady ? "▶ Listen" : "No voice"); listen.setEnabled(speechReady); listen.setTextSize(10); listen.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)); listen.setTextColor(GREEN); listen.setOnClickListener(v -> { if (speechReady && speech != null) { voiceInput.cancel("Dictation stopped before reading aloud."); speech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "answer"); } }); listenButtons.add(listen); actions.addView(listen, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button stop = button("Stop reading", false); stop.setTextSize(11); stop.setOnClickListener(view -> { if (speech != null) speech.stop(); }); actions.addView(stop, new LinearLayout.LayoutParams(0, dp(48), 1));
            LinearLayout evidencePanel = column(); evidencePanel.setVisibility(View.GONE);
            if (refs.length() > 0) {
                Button sourcesToggle = button("↗  Sources · " + refs.length(), false);
                sourcesToggle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                sourcesToggle.setOnClickListener(v -> { boolean expand = evidencePanel.getVisibility() != View.VISIBLE; evidencePanel.setVisibility(expand ? View.VISIBLE : View.GONE); sourcesToggle.setText((expand ? "⌄  " : "↗  ") + "Sources · " + refs.length()); });
                group.addView(sourcesToggle); group.addView(evidencePanel);
            }
            for (int i=0; i<refs.length(); i++) {
                JSONObject source = refs.optJSONObject(i); if (source == null) continue;
                if (i == 0 && mode.equals("local-ai-draft")) evidencePanel.addView(text("Source context · These links do not verify the AI draft", 11, MUTED));
                Button link = button("", false); link.setText("↗ " + source.optString("title") + " — " + source.optString("source")); link.setTextSize(10); link.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                String url = source.optString("url"); link.setOnClickListener(v -> openSource(url)); evidencePanel.addView(link);
                TextView evidence = text("Bundled app summary (English), not a verbatim quotation:\n\n" + source.optString("text"), 12, MUTED);
                evidence.setTextIsSelectable(true); evidence.setVisibility(View.GONE);
                Button details = button("View source summary", false); details.setTextSize(11);
                details.setOnClickListener(view -> {
                    boolean opening = evidence.getVisibility() != View.VISIBLE;
                    evidence.setVisibility(opening ? View.VISIBLE : View.GONE);
                    details.setText(opening ? "Hide source summary" : "View source summary");
                }); evidencePanel.addView(details); evidencePanel.addView(evidence);
            }
        }
        Button copy = button("Copy", false); copy.setTextSize(11);
        copy.setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData data = ClipData.newPlainText("Doctor Agent", text);
            android.os.PersistableBundle extras = new android.os.PersistableBundle();
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true); data.getDescription().setExtras(extras);
            if (clipboard != null) clipboard.setPrimaryClip(data);
            Toast.makeText(this, "Copied to your device clipboard", Toast.LENGTH_SHORT).show();
        }); actions.addView(copy, new LinearLayout.LayoutParams(0, dp(48), 1)); group.addView(actions);
        LinearLayout messageRow = new LinearLayout(this);
        messageRow.setGravity(assistant ? Gravity.START : Gravity.END);
        messageRow.setOrientation(LinearLayout.HORIZONTAL);
        if (assistant) {
            TextView avatar = text("✚", 12, Color.WHITE); avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(round(GREEN, dp(11)));
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(26), dp(26));
            avatarParams.topMargin = dp(4); avatarParams.rightMargin = dp(7);
            messageRow.addView(avatar, avatarParams);
            messageRow.addView(group, new LinearLayout.LayoutParams(0, -2, 1f));
        } else {
            messageRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, .13f));
            messageRow.addView(group, new LinearLayout.LayoutParams(0, -2, .87f));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(11); transcript.addView(messageRow, params);
        if (!replayingChat && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            messageRow.setAlpha(0f); messageRow.setTranslationY(dp(7));
            messageRow.animate().alpha(1f).translationY(0).setDuration(230)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
        updateChatStorageStatus();
        if (!replayingChat) scroll.post(() -> { if (scroll != null) scroll.fullScroll(View.FOCUS_DOWN); });
    }

    private boolean lastUserUsedHindi() {
        for (int i = messages.size() - 1; i >= 0; i--) if (!messages.get(i).assistant) return LocalLanguage.isHindi(messages.get(i).text);
        return false;
    }

    private boolean lastUserUsedDevanagari() {
        for (int i = messages.size() - 1; i >= 0; i--) if (!messages.get(i).assistant) return LocalLanguage.isDevanagari(messages.get(i).text);
        return false;
    }

    private void loadCatalog() {
        try (InputStream input = getAssets().open("knowledge.json"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count; while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            JSONArray array = new JSONArray(bytes.toString(StandardCharsets.UTF_8.name()));
            for (int i=0; i<array.length(); i++) catalog.add(array.getJSONObject(i));
        } catch (Exception ignored) { }
        try (InputStream input = getAssets().open("knowledge_hi.json"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count; while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            JSONArray array = new JSONArray(bytes.toString(StandardCharsets.UTF_8.name()));
            for (int i=0; i<array.length(); i++) { JSONObject entry = array.getJSONObject(i); localizedCatalog.put(entry.optString("id"), entry); }
        } catch (Exception ignored) { localizedCatalog.clear(); }
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
            button.setText(speechReady ? "▶ Listen" : "No voice");
        }
        if (selectedPage.equals("You")) selectPage("You");
    }

    @Override public Object onRetainNonConfigurationInstance() {
        return new Session(messages, question.getText().toString(), selectedPage, savedChatId, visitNotesDraft);
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
