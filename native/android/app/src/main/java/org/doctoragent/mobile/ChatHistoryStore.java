package org.doctoragent.mobile;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;

/** Explicitly saved conversation snapshots, encrypted with a separate Keystore key. */
final class ChatHistoryStore {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_CHATS = 20;
    private final CheckInStore encrypted;
    private final File payload;

    ChatHistoryStore(Context context) {
        encrypted = new CheckInStore(context, "chats.enc", "doctor-agent-chats-v1");
        payload = new File(context.getNoBackupFilesDir(), "chats.enc");
    }

    JSONArray read() throws Exception {
        if (payload.length() > MAX_BYTES + 28) throw new IllegalStateException("Saved chat storage is too large");
        JSONArray chats = encrypted.read();
        if (chats.length() > MAX_CHATS) throw new IllegalStateException("Invalid saved chat count");
        for (int i = 0; i < chats.length(); i++) validate(chats.getJSONObject(i));
        return chats;
    }

    void save(String id, JSONArray messages) throws Exception {
        if (messages.length() == 0) throw new IllegalArgumentException("Start a conversation before saving");
        JSONArray previous = read();
        ArrayList<JSONObject> ordered = new ArrayList<>();
        JSONObject existing = null;
        for (int i = 0; i < previous.length(); i++) {
            JSONObject entry = previous.getJSONObject(i);
            if (id.equals(entry.getString("id"))) existing = entry;
            else ordered.add(entry);
        }
        if (existing == null && previous.length() >= MAX_CHATS)
            throw new IllegalStateException("20 chats are already saved. Delete one in History, then try again.");
        String title = "Health conversation";
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.getJSONObject(i);
            if (!message.getBoolean("assistant")) {
                title = message.getString("text").replaceAll("\\s+", " ").trim();
                int count = title.codePointCount(0, title.length());
                if (count > 70) title = title.substring(0, title.offsetByCodePoints(0, 70)) + "…";
                break;
            }
        }
        if (existing != null) title = existing.getString("title");
        long now = System.currentTimeMillis();
        JSONObject snapshot = new JSONObject().put("id", id).put("title", title)
                .put("created", existing == null ? now : existing.getLong("created"))
                .put("updated", now).put("messages", messages);
        validate(snapshot);
        ordered.add(snapshot);
        ordered.sort((a, b) -> Long.compare(b.optLong("updated"), a.optLong("updated")));
        JSONArray result = new JSONArray();
        for (JSONObject entry : ordered) result.put(entry);
        write(result);
    }

    void delete(String id) throws Exception {
        JSONArray previous = read(), remaining = new JSONArray();
        for (int i = 0; i < previous.length(); i++) {
            JSONObject entry = previous.getJSONObject(i);
            if (!id.equals(entry.getString("id"))) remaining.put(entry);
        }
        if (remaining.length() == 0) encrypted.deleteAll();
        else write(remaining);
    }

    void deleteAll() throws Exception { encrypted.deleteAll(); }

    void rename(String id, String title) throws Exception {
        title = title.replaceAll("\\s+", " ").trim();
        if (title.isEmpty() || title.codePointCount(0, title.length()) > 70)
            throw new IllegalArgumentException("Use a title from 1 to 70 characters");
        JSONArray chats = read(); boolean found = false;
        for (int i = 0; i < chats.length(); i++) {
            JSONObject chat = chats.getJSONObject(i);
            if (id.equals(chat.getString("id"))) { chat.put("title", title); validate(chat); found = true; }
        }
        if (!found) throw new IllegalStateException("This saved chat no longer exists");
        write(chats);
    }

    private void write(JSONArray chats) throws Exception {
        if (chats.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalStateException("Saved chats have reached the 2 MB limit. Delete a chat in History, then try again.");
        encrypted.write(chats);
    }

    private void validate(JSONObject chat) throws Exception {
        if (!chat.getString("id").matches("[a-f0-9-]{36}") || chat.getString("title").length() > 150
                || chat.getLong("created") < 0 || chat.getLong("updated") < 0)
            throw new IllegalStateException("Invalid saved chat");
        JSONArray messages = chat.getJSONArray("messages");
        if (messages.length() == 0 || messages.length() > 80) throw new IllegalStateException("Invalid saved conversation");
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.getJSONObject(i);
            message.getBoolean("assistant");
            if (message.getString("text").length() > 12000 || message.getString("mode").length() > 64
                    || message.getJSONArray("sources").length() > 6)
                throw new IllegalStateException("Invalid saved message");
            JSONArray sources = message.getJSONArray("sources");
            for (int j = 0; j < sources.length(); j++) sources.getJSONObject(j);
        }
    }
}
