package org.doctoragent.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputFilter;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit saved preferences; journal entries are never exposed through this profile. */
final class CompanionProfile {
    private static CheckInStore store(Context context) {
        return new CheckInStore(context, "companion-profile.enc", "doctor-agent-profile-v1");
    }
    static JSONObject read(Context context) throws Exception {
        JSONArray entries = store(context).read();
        return entries.length() == 0 ? new JSONObject() : entries.getJSONObject(0);
    }
    static String aiContext(Context context) {
        try {
            JSONObject saved = read(context);
            if (!saved.optBoolean("shareWithAi", false)) return "";
            return new JSONObject().put("preferredName", saved.optString("name").substring(0, Math.min(40, saved.optString("name").length())))
                    .put("habitGoal", saved.optString("goal").substring(0, Math.min(160, saved.optString("goal").length()))).toString();
        } catch (Exception error) { return ""; }
    }
    static void show(Activity activity, Runnable onClose) {
        LinearLayout form = new LinearLayout(activity); form.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding, padding, padding);
        TextView explanation = new TextView(activity);
        explanation.setText("Optional encrypted memory on this phone. Save a preferred name and one everyday habit goal. You control whether these preferences are supplied to local AI. Your daily journal stays separate."); form.addView(explanation);
        EditText name = new EditText(activity); name.setHint("Preferred name (optional)"); name.setSingleLine(true);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)}); name.setSaveEnabled(false); form.addView(name);
        EditText goal = new EditText(activity); goal.setHint("Everyday habit goal (optional)");
        goal.setFilters(new InputFilter[]{new InputFilter.LengthFilter(160)}); goal.setSaveEnabled(false); form.addView(goal);
        CheckBox share = new CheckBox(activity); share.setText("Allow these preferences in local AI drafts");
        share.setSaveEnabled(false); form.addView(share);
        CheckBox saveConsent = new CheckBox(activity); saveConsent.setText("Save these preferences on this phone");
        saveConsent.setSaveEnabled(false); form.addView(saveConsent);
        TextView feedback = new TextView(activity); form.addView(feedback);
        boolean readable;
        try {
            JSONObject saved = read(activity); name.setText(saved.optString("name")); goal.setText(saved.optString("goal"));
            share.setChecked(saved.optBoolean("shareWithAi", false)); readable = true;
        } catch (Exception error) {
            feedback.setText("Saved preferences could not be opened. Nothing was overwritten. You can delete them below."); readable = false;
        }
        Button delete = new Button(activity); delete.setText("Delete saved preferences"); form.addView(delete);
        ScrollView scroll = new ScrollView(activity); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Companion memory").setView(scroll)
                .setPositiveButton("Save", null).setNegativeButton("Close", null).create();
        boolean[] canRead = {readable};
        dialog.setOnDismissListener(ignored -> onClose.run());
        dialog.setOnShowListener(ignored -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE); save.setEnabled(false);
            saveConsent.setOnCheckedChangeListener((view, checked) -> save.setEnabled(checked && canRead[0]));
            save.setOnClickListener(view -> {
                try {
                    JSONObject entry = new JSONObject().put("name", name.getText().toString().trim())
                            .put("goal", goal.getText().toString().trim()).put("shareWithAi", share.isChecked());
                    store(activity).write(new JSONArray().put(entry));
                    saveConsent.setChecked(false);
                    feedback.setText("Preferences saved. AI sharing is " + (share.isChecked() ? "on" : "off") + ".");
                } catch (Exception error) { feedback.setText("Could not save preferences. Please try again."); }
            });
            delete.setOnClickListener(view -> new AlertDialog.Builder(activity).setTitle("Forget saved preferences?")
                    .setMessage("Remove the saved name, habit goal and AI-sharing choice. Journal entries remain.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Forget", (confirm, which) -> {
                        try {
                            store(activity).deleteAll(); canRead[0] = true;
                            name.setText(""); goal.setText(""); share.setChecked(false); saveConsent.setChecked(false);
                            save.setEnabled(false); feedback.setText("Saved preferences deleted.");
                        } catch (Exception error) { feedback.setText("Could not delete preferences. Please try again."); }
                    }).show());
        });
        dialog.show();
    }
}
