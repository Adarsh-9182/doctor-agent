package org.doctoragent.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;

final class CareWorkspace {
    interface Listener { void addToChat(String notes); }

    static void build(Activity activity, LinearLayout content, VisitNotes.Draft draft, Listener listener) {
        label(activity, content, "Prepare for a health visit", 24);
        label(activity, content, "Organize your own symptoms, concerns and questions. The form stays in this session, including rotation. Up to 600 characters per field. Review before adding to Chat; saving that chat remains optional.", 13);
        label(activity, content, "This form cannot assess urgency, identify a cause, or choose treatment. Do not wait for it in an emergency.", 13);
        ArrayList<EditText> inputs = new ArrayList<>();
        for (int i = 0; i < VisitNotes.LABELS.length; i++) {
            label(activity, content, VisitNotes.LABELS[i], 14);
            EditText input = new EditText(activity); input.setTextColor(Color.rgb(24, 59, 53));
            android.graphics.drawable.GradientDrawable surface = new android.graphics.drawable.GradientDrawable();
            surface.setColor(Color.WHITE); surface.setCornerRadius(dp(activity, 14));
            surface.setStroke(dp(activity, 1), Color.rgb(216, 226, 220)); input.setBackground(surface);
            input.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
            input.setTextSize(14); input.setMinLines(2); input.setMaxLines(5); input.setSaveEnabled(false);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(VisitNotes.LIMIT)});
            input.setHint(i == 0 ? "Use your own words" : "Leave blank if you prefer"); input.setText(draft.values[i]);
            int field = i;
            input.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) { draft.values[field] = s.toString(); }
                public void afterTextChanged(Editable value) {}
            }); content.addView(input); inputs.add(input);
        }
        Button review = new Button(activity); review.setText("Review visit notes"); review.setAllCaps(false);
        review.setOnClickListener(view -> {
            String notes;
            try { notes = VisitNotes.format(draft); }
            catch (IllegalArgumentException error) { inputs.get(0).setError(error.getMessage()); inputs.get(0).requestFocus(); return; }
            Runnable showPreview = () -> preview(activity, notes, listener);
            EducationEngine.Answer boundary = EducationEngine.answer(draft.raw(), Collections.emptyList());
            if (boundary.mode.equals("urgent-care")) new AlertDialog.Builder(activity).setTitle("Seek urgent help")
                    .setMessage(boundary.text).setNegativeButton("Close", null)
                    .setNeutralButton("Review my notes", (dialog, which) -> showPreview.run()).show();
            else showPreview.run();
        }); content.addView(review);
        Button clear = new Button(activity); clear.setText("Clear these notes"); clear.setAllCaps(false);
        clear.setOnClickListener(view -> new AlertDialog.Builder(activity).setTitle("Clear this form?")
                .setMessage("Remove the notes in this form. Notes already added to a chat or saved in History remain there.")
                .setNegativeButton("Keep notes", null).setPositiveButton("Clear", (dialog, which) -> {
                    draft.clear(); for (EditText input : inputs) input.setText("");
                }).show()); content.addView(clear);
        label(activity, content, "Report questions", 18);
        label(activity, content, "Ask Chat for general explanations of lab reports and medical terms. PDF/photo upload and personal result interpretation are not available yet.", 13);
    }

    private static void preview(Activity activity, String notes, Listener listener) {
        TextView text = new TextView(activity); text.setText(notes); text.setTextIsSelectable(true); text.setTextSize(14);
        int padding = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(activity); scroll.addView(text);
        new AlertDialog.Builder(activity).setTitle("Review your notes").setView(scroll)
                .setNegativeButton("Keep editing", null)
                .setPositiveButton("Add to chat", (dialog, which) -> listener.addToChat(notes)).show();
    }

    private static int dp(Activity activity, int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private static void label(Activity activity, LinearLayout parent, String value, int size) {
        TextView text = new TextView(activity); text.setText(value); text.setTextSize(size);
        text.setTextColor(Color.rgb(24, 59, 53)); text.setPadding(0, dp(activity, 16), 0, dp(activity, 8)); parent.addView(text);
    }
}
