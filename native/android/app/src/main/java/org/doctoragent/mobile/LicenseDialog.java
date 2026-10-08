package org.doctoragent.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Bundled notices are available offline, paginated to keep rendering bounded. */
final class LicenseDialog {
    static void show(Activity activity) {
        new AlertDialog.Builder(activity).setTitle("Open-source licenses")
                .setItems(new String[]{"LiteRT-LM license", "LiteRT-LM third-party notices", "Qwen3 model license"}, (choice, selected) ->
                        showFile(activity, selected == 0 ? "LiteRT-LM-LICENSE.txt" : selected == 1 ? "LiteRT-LM-THIRD-PARTY-NOTICES.txt" : "Qwen3-LICENSE.txt"))
                .setNegativeButton("Close", null).show();
    }
    private static void showFile(Activity activity, String file) {
        TextView text = new TextView(activity);
        text.setTextSize(13); text.setTextIsSelectable(true);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding); text.setText("Loading bundled license…");
        ScrollView scroll = new ScrollView(activity); scroll.addView(text);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Bundled notices")
                .setView(scroll).setPositiveButton("Next", null).setNeutralButton("Previous", null)
                .setNegativeButton("Close", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
            new Thread(() -> {
                String contents;
                try (InputStream input = activity.getAssets().open(file); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                    contents = bytes.toString(StandardCharsets.UTF_8.name());
                } catch (Exception error) { contents = "Could not open the bundled notice. Reinstall a complete app build."; }
                final String loaded = contents;
                activity.runOnUiThread(() -> {
                    if (activity.isDestroyed() || !dialog.isShowing()) return;
                    int[] page = {0}; int pageSize = 12000;
                    int count = Math.max(1, (loaded.length() + pageSize - 1) / pageSize);
                    Runnable render = () -> {
                        int start = page[0] * pageSize;
                        text.setText(loaded.substring(start, Math.min(loaded.length(), start + pageSize)));
                        dialog.setTitle("Notices · " + (page[0] + 1) + " / " + count);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(page[0] + 1 < count);
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(page[0] > 0);
                        scroll.post(() -> scroll.scrollTo(0, 0));
                    };
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> { page[0]++; render.run(); });
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> { page[0]--; render.run(); });
                    render.run();
                });
            }, "doctor-agent-notices").start();
        });
        dialog.show();
    }
}
