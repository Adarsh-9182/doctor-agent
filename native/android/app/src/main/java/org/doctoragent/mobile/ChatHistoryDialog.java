package org.doctoragent.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import org.json.JSONArray;
import org.json.JSONObject;

/** Saved chat controls; transcripts are only exposed after choosing Open. */
final class ChatHistoryDialog {
    interface Listener {
        void open(JSONObject chat);
        void deleted(String id);
    }

    static void show(Activity activity, ChatHistoryStore store, Listener listener) {
        LinearLayout list = new LinearLayout(activity); list.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(18 * activity.getResources().getDisplayMetrics().density);
        list.setPadding(padding, padding, padding, padding);
        label(activity, list, "Save or update a chat explicitly. Saved copies stay encrypted on this phone and are excluded from backup. Up to 20 conversations, 80 messages per snapshot.");
        ScrollView scroll = new ScrollView(activity); scroll.addView(list);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Saved chats")
                .setView(scroll).setNegativeButton("Close", null).create();
        try {
            JSONArray chats = store.read();
            if (chats.length() == 0) label(activity, list, "No saved chats yet. Use Save in Chat after starting a conversation.");
            for (int i = 0; i < chats.length(); i++) {
                JSONObject chat = chats.getJSONObject(i);
                TextView title = label(activity, list, chat.getString("title"));
                title.setTypeface(null, 1); title.setTextSize(16);
                String date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
                        java.text.DateFormat.SHORT).format(new java.util.Date(chat.getLong("updated")));
                label(activity, list, "Saved " + date + " · " + chat.getJSONArray("messages").length() + " messages");
                LinearLayout actions = new LinearLayout(activity); actions.setGravity(Gravity.CENTER_VERTICAL);
                Button open = new Button(activity); open.setText("Open"); open.setAllCaps(false);
                open.setContentDescription("Open saved chat: " + chat.getString("title"));
                open.setOnClickListener(view -> { dialog.dismiss(); listener.open(chat); });
                actions.addView(open, new LinearLayout.LayoutParams(0, -2, 1));
                Button rename = new Button(activity); rename.setText("Rename"); rename.setAllCaps(false);
                rename.setContentDescription("Rename saved chat: " + chat.getString("title"));
                rename.setOnClickListener(view -> {
                    EditText input = new EditText(activity); input.setSingleLine(true); input.setText(chat.optString("title"));
                    input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(140)});
                    input.setSelection(input.length());
                    AlertDialog edit = new AlertDialog.Builder(activity).setTitle("Chat title · up to 70 characters")
                            .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Rename", null).create();
                    edit.setOnShowListener(ignored -> edit.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                        try {
                            store.rename(chat.getString("id"), input.getText().toString());
                            edit.dismiss(); dialog.dismiss(); show(activity, store, listener);
                        } catch (IllegalArgumentException error) { input.setError(error.getMessage()); }
                        catch (Exception error) { showError(activity, "This saved chat could not be renamed. Try again."); }
                    })); edit.show();
                });
                actions.addView(rename, new LinearLayout.LayoutParams(0, -2, 1));
                Button delete = new Button(activity); delete.setText("Delete"); delete.setAllCaps(false);
                delete.setContentDescription("Delete saved chat: " + chat.getString("title"));
                delete.setOnClickListener(view -> new AlertDialog.Builder(activity).setTitle("Delete saved chat?")
                        .setMessage("Delete the saved copy of “" + chat.optString("title") + "”? A chat currently open on screen will stay in this session.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Delete", (confirmation, which) -> {
                            try {
                                store.delete(chat.getString("id")); listener.deleted(chat.getString("id"));
                                dialog.dismiss(); show(activity, store, listener);
                            } catch (Exception error) { showError(activity, "The saved chat could not be deleted. Try again."); }
                        }).show());
                actions.addView(delete, new LinearLayout.LayoutParams(0, -2, 1)); list.addView(actions);
            }
            if (chats.length() > 0) dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Delete all", (ignored, which) -> confirmDeleteAll(activity, store, listener));
        } catch (Exception error) {
            label(activity, list, "Saved chats could not be opened. Existing data has not been overwritten. You can retry, or delete the stored file below to reset history.");
            Button reset = new Button(activity); reset.setText("Delete saved chat data"); reset.setAllCaps(false);
            reset.setOnClickListener(view -> { dialog.dismiss(); confirmDeleteAll(activity, store, listener); }); list.addView(reset);
        }
        dialog.show();
    }

    private static void confirmDeleteAll(Activity activity, ChatHistoryStore store, Listener listener) {
        new AlertDialog.Builder(activity).setTitle("Delete all saved chats?")
                .setMessage("This deletes conversation snapshots from this phone. Your current session, journal, and preferences remain available.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete all", (dialog, which) -> {
                    try { store.deleteAll(); listener.deleted(null); show(activity, store, listener); }
                    catch (Exception error) { showError(activity, "Saved chats could not be deleted. Try again."); }
                }).show();
    }

    private static TextView label(Activity activity, LinearLayout list, String value) {
        TextView text = new TextView(activity); text.setText(value); text.setTextColor(Color.rgb(24, 59, 53));
        text.setTextSize(13); text.setPadding(0, 8, 0, 8); list.addView(text); return text;
    }

    private static void showError(Activity activity, String message) {
        new AlertDialog.Builder(activity).setTitle("Saved chats").setMessage(message).setPositiveButton("OK", null).show();
    }
}
