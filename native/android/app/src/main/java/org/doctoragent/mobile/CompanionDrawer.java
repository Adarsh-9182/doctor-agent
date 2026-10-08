package org.doctoragent.mobile;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/** A transient navigation panel; chat storage remains owned by ChatHistoryStore. */
final class CompanionDrawer {
    interface Listener { void page(String page); void fresh(); void history(); void open(JSONObject chat); }
    private final Activity activity;
    private final FrameLayout host;
    private FrameLayout overlay;
    private View previousFocus;
    CompanionDrawer(Activity activity, FrameLayout host) { this.activity = activity; this.host = host; }
    boolean isOpen() { return overlay != null; }
    void close() {
        if (overlay == null) return;
        host.removeView(overlay); overlay = null;
        host.getChildAt(0).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        if (previousFocus != null) previousFocus.requestFocus();
    }
    void show(JSONArray chats, boolean readable, String current, Listener listener) {
        close(); previousFocus = activity.getCurrentFocus();
        overlay = new FrameLayout(activity); overlay.setBackgroundColor(0x6630273d);
        overlay.setOnClickListener(v -> close());
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.rgb(246, 245, 250)); panel.setPadding(dp(18), dp(48), dp(18), dp(24));
        panel.setClickable(true);
        TextView brand = new TextView(activity); brand.setText("✳  Doctor Agent"); brand.setTextSize(23);
        brand.setTextColor(Color.rgb(37, 35, 55)); brand.setTypeface(null, 1); panel.addView(brand);
        item(panel, "Close menu  ×", () -> close());
        item(panel, "+  New conversation", () -> { close(); listener.fresh(); });
        for (String page : new String[]{"Chat", "Home", "Care", "Library", "You"})
            item(panel, (page.equals(current) ? "●  " : "    ") + page, () -> { close(); listener.page(page); });
        TextView label = new TextView(activity); label.setText("SAVED ON THIS PHONE"); label.setTextSize(10);
        label.setTextColor(Color.rgb(112, 107, 131)); label.setPadding(0, dp(24), 0, dp(10)); panel.addView(label);
        ScrollView scroll = new ScrollView(activity); LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
        if (!readable || chats.length() == 0) {
            TextView empty = new TextView(activity); empty.setText(readable
                    ? "No saved chats yet. Use Save in Chat to keep an encrypted snapshot."
                    : "Saved chats could not be read. Open Manage history to retry.");
            empty.setTextColor(Color.rgb(112, 107, 131)); empty.setTextSize(13); list.addView(empty);
        }
        for (int i = 0; i < chats.length(); i++) {
            JSONObject chat = chats.optJSONObject(i); if (chat == null) continue;
            item(list, chat.optString("title", "Health conversation"), () -> { close(); listener.open(chat); });
        }
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        item(panel, "Manage history  ↗", () -> { close(); listener.history(); });
        TextView privacy = new TextView(activity); privacy.setText("Private • Offline • No account");
        privacy.setTextColor(Color.rgb(112, 107, 131)); privacy.setTextSize(11); panel.addView(privacy);
        int width = Math.min(dp(320), activity.getResources().getDisplayMetrics().widthPixels - dp(36));
        ScrollView navigationScroll = new ScrollView(activity); navigationScroll.setFillViewport(true); navigationScroll.addView(panel);
        overlay.addView(navigationScroll, new FrameLayout.LayoutParams(width, -1, Gravity.START)); host.addView(overlay);
        host.getChildAt(0).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            panel.setTranslationX(-width); overlay.setAlpha(0);
            overlay.animate().alpha(1).setDuration(180).start();
            panel.animate().translationX(0).setDuration(250).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
        brand.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }
    private void item(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(activity); button.setText(label); button.setAllCaps(false); button.setTextSize(14);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); button.setTextColor(Color.rgb(75, 64, 109));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        button.setMinHeight(dp(48)); button.setOnClickListener(v -> action.run()); parent.addView(button);
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
