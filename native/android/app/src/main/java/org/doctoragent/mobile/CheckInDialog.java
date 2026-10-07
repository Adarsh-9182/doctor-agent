package org.doctoragent.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

final class CheckInDialog {
    static void show(Activity activity) {
        CheckInStore store = new CheckInStore(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        label(activity, content, "A personal journal, not a medical assessment. Saving is optional. Keep up to 30 daily entries, encrypted on this phone. Entries are not used in chat. Saving again replaces today's entry.");
        label(activity, content, "Sleep last night (hours, 0–24)");
        EditText sleep = new EditText(activity);
        sleep.setHint("e.g. 7.5");
        sleep.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        sleep.setSaveEnabled(false);
        content.addView(sleep);
        label(activity, content, "Energy today (your own rating)");
        Spinner energy = new Spinner(activity);
        ArrayAdapter<String> choices = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item,
                new String[]{"Choose energy", "1 — Very low", "2 — Low", "3 — Moderate", "4 — Good", "5 — High"});
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        energy.setAdapter(choices);
        energy.setSaveEnabled(false);
        content.addView(energy);
        label(activity, content, "One goal for today (optional)");
        EditText goal = new EditText(activity);
        goal.setHint("e.g. Take a short walk");
        goal.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        goal.setSaveEnabled(false);
        content.addView(goal);
        CheckBox consent = new CheckBox(activity);
        consent.setText("Save this check-in on this phone");
        consent.setSaveEnabled(false);
        content.addView(consent);
        TextView feedback = label(activity, content, "");
        TextView history = label(activity, content, "");
        boolean readable;
        try { renderHistory(history, store.read()); readable = true; }
        catch (Exception error) { history.setText("Saved entries could not be opened. Nothing was overwritten. You can delete them below."); readable = false; }
        Button erase = new Button(activity);
        erase.setText("Delete all saved check-ins");
        content.addView(erase);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Daily check-in")
                .setView(scroll).setPositiveButton("Save today", null).setNegativeButton("Close", null).create();
        final boolean[] canRead = {readable};
        dialog.setOnShowListener(ignored -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setEnabled(false);
            consent.setOnCheckedChangeListener((button, checked) -> save.setEnabled(checked && canRead[0]));
            save.setOnClickListener(view -> {
                double hours;
                try { hours = Double.parseDouble(sleep.getText().toString().trim()); }
                catch (NumberFormatException error) { sleep.setError("Enter hours from 0 to 24"); return; }
                if (!Double.isFinite(hours) || hours < 0 || hours > 24) { sleep.setError("Enter hours from 0 to 24"); return; }
                if (energy.getSelectedItemPosition() == 0) { feedback.setText("Choose your energy rating first."); return; }
                try {
                    store.saveToday(hours, energy.getSelectedItemPosition(), goal.getText().toString().trim());
                    renderHistory(history, store.read());
                    consent.setChecked(false);
                    feedback.setText("Saved on this phone. You can update today's entry or delete all entries.");
                } catch (Exception error) { feedback.setText("Could not save the check-in. Please try again."); }
            });
            erase.setOnClickListener(view -> new AlertDialog.Builder(activity).setTitle("Delete all check-ins?")
                    .setMessage("This removes every saved journal entry from this phone.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete", (confirm, which) -> {
                        try {
                            store.deleteAll();
                            canRead[0] = true;
                            consent.setChecked(false);
                            save.setEnabled(false);
                            renderHistory(history, new JSONArray());
                            feedback.setText("All saved check-ins deleted.");
                        } catch (Exception error) { feedback.setText("Could not delete saved entries. Please try again."); }
                    }).show());
        });
        dialog.show();
    }

    private static TextView label(Activity activity, LinearLayout parent, String text) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setPadding(0, 12, 0, 8);
        parent.addView(label);
        return label;
    }

    private static void renderHistory(TextView view, JSONArray entries) throws Exception {
        if (entries.length() == 0) { view.setText("No saved check-ins yet."); return; }
        StringBuilder text = new StringBuilder("Recent check-ins\n");
        for (int i = entries.length() - 1; i >= Math.max(0, entries.length() - 7); i--) {
            JSONObject entry = entries.getJSONObject(i);
            text.append("\n").append(entry.getString("date")).append(" · Sleep ")
                    .append(entry.getDouble("sleepHours")).append("h · Energy ").append(entry.getInt("energy")).append("/5\n");
            String goal = entry.optString("goal");
            if (!goal.isEmpty()) text.append("Goal: ").append(goal).append("\n");
        }
        view.setText(text.toString());
    }
}
