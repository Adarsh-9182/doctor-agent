package org.doctoragent.mobile;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import android.widget.Toast;

/** A static entry point, with no journal values, microphone use or background inference. */
public final class CompanionWidget extends AppWidgetProvider {
    static final String OPEN_CHAT = "open_companion_chat";
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.companion_widget);
            views.setOnClickPendingIntent(R.id.widget_chat, open(context, id, false));
            views.setOnClickPendingIntent(R.id.widget_checkin, open(context, id, true));
            manager.updateAppWidget(id, views);
        }
    }
    private PendingIntent open(Context context, int id, boolean journal) {
        Intent intent = new Intent(context, MainActivity.class)
                .setData(Uri.parse("doctor-agent://widget/" + id + (journal ? "/journal" : "/chat")))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(journal ? DailyReminder.OPEN_JOURNAL : OPEN_CHAT, true);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static void requestPin(Activity activity) {
        AppWidgetManager manager = activity.getSystemService(AppWidgetManager.class);
        try {
            if (manager != null && manager.isRequestPinAppWidgetSupported()
                    && manager.requestPinAppWidget(new ComponentName(activity, CompanionWidget.class), null, null)) return;
        } catch (RuntimeException ignored) {}
        Toast.makeText(activity, "Long-press your home screen, choose Widgets, then Doctor Agent.", Toast.LENGTH_LONG).show();
    }
}
