package org.doctoragent.mobile;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.Locale;

/** Explicit on-device recognition only; no network recognizer fallback. Main thread only. */
final class OfflineVoiceInput {
    interface Listener {
        void onStatus(String text, boolean listening);
        void onTranscript(String text);
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private SpeechRecognizer recognizer;
    private int revision;
    private boolean closed;
    private Runnable timeout;

    OfflineVoiceInput(Context context, Listener listener) {
        this.context = context; this.listener = listener;
    }
    static boolean available(Context context) {
        if (Build.VERSION.SDK_INT < 31) return false;
        try { return SpeechRecognizer.isOnDeviceRecognitionAvailable(context); }
        catch (RuntimeException error) { return false; }
    }
    static String language() {
        Locale locale = Locale.getDefault();
        return "en".equals(locale.getLanguage()) ? locale.toLanguageTag() : "en-IN";
    }
    boolean isListening() { return recognizer != null; }
    private void status(String value, boolean listening) {
        if (!closed && listener != null) listener.onStatus(value, listening);
    }
    private void release() {
        if (timeout != null) { main.removeCallbacks(timeout); timeout = null; }
        SpeechRecognizer previous = recognizer; recognizer = null;
        if (previous != null) {
            try { previous.cancel(); } catch (RuntimeException ignored) {}
            try { previous.destroy(); } catch (RuntimeException ignored) {}
        }
    }
    void start() {
        if (closed || isListening()) return;
        if (!available(context)) { status("On-device voice is unavailable. You can type your question.", false); return; }
        int id = ++revision;
        try {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            recognizer.setRecognitionListener(new RecognitionListener() {
                private boolean current() { return !closed && id == revision && recognizer != null; }
                @Override public void onReadyForSpeech(Bundle params) { if (current()) status("Listening in " + language() + "… tap Cancel to stop.", true); }
                @Override public void onBeginningOfSpeech() { if (current()) status("Listening… your words will become an editable draft.", true); }
                @Override public void onRmsChanged(float value) {}
                @Override public void onBufferReceived(byte[] bytes) {} // Never save audio.
                @Override public void onEndOfSpeech() { if (current()) status("Preparing your voice draft…", true); }
                @Override public void onError(int error) {
                    if (!current()) return;
                    revision++; release();
                    String message;
                    switch (error) {
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "Microphone access is off. Allow it in app settings or keep typing."; break;
                        case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
                        case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE: message = "Offline recognition for " + language() + " is unavailable. Install the language in your phone's speech settings or type instead."; break;
                        case SpeechRecognizer.ERROR_NO_MATCH:
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: message = "No clear speech was recognised. Try again or type your question."; break;
                        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: message = "The phone's recognizer is busy. Try again in a moment."; break;
                        default: message = "On-device voice could not complete. Your typed draft is unchanged.";
                    }
                    status(message, false);
                }
                @Override public void onResults(Bundle results) {
                    if (!current()) return;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    revision++; release();
                    if (matches == null || matches.isEmpty() || matches.get(0) == null || matches.get(0).trim().isEmpty()) {
                        status("No clear speech was recognised. Your draft is unchanged.", false); return;
                    }
                    String text = matches.get(0).trim();
                    listener.onTranscript(text.substring(0, Math.min(1200, text.length())));
                    status("Voice draft ready. Review and edit before sending.", false);
                }
                @Override public void onPartialResults(Bundle partial) {}
                @Override public void onEvent(int type, Bundle params) {}
            });
            Intent input = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            input.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            input.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language());
            input.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            input.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            input.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            status("Starting on-device microphone…", true);
            timeout = () -> { if (id == revision) cancel("Voice stopped after 20 seconds. Your typed draft is unchanged."); };
            main.postDelayed(timeout, 20000);
            recognizer.startListening(input);
        } catch (RuntimeException error) {
            revision++; release(); status("On-device voice is unavailable. You can type your question.", false);
        }
    }
    void cancel(String message) {
        if (recognizer == null) return;
        revision++; release(); status(message, false);
    }
    void close() {
        closed = true; revision++; release(); listener = null;
    }
}
