package org.doctoragent.mobile;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.*;

/** Optional local, inexact reminder. Preferences contain only scheduling settings. */
final class DailyReminder {
    static final String ACTION = "org.doctoragent.mobile.DAILY_CHECK_IN";
    static final String OPEN_JOURNAL = "open_daily_check_in";
    private static final String CHANNEL = "daily-check-in";
    private static final int ID = 701;

    private static SharedPreferences settings(Context context) {
        return context.getSharedPreferences("reminder-settings", Context.MODE_PRIVATE);
    }
    static boolean enabled(Context context) { return settings(context).getBoolean("enabled", false); }
    static int hour(Context context) { return settings(context).getInt("hour", 20); }
    static int minute(Context context) { return settings(context).getInt("minute", 0); }
    static String timeLabel(Context context) {
        return java.time.LocalTime.of(hour(context), minute(context)).format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }
    static void createChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Daily check-in", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Optional daily invitation to open your private journal.");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
    }
    static boolean allowed(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    private static PendingIntent alarmIntent(Context context) {
        Intent intent = new Intent(context, DailyReminderReceiver.class).setAction(ACTION);
        return PendingIntent.getBroadcast(context, ID, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private static long nextTime(Context context) {
        ZoneId zone = ZoneId.systemDefault(); ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime target = now.toLocalDate().atTime(hour(context), minute(context)).atZone(zone);
        if (!target.isAfter(now)) target = now.toLocalDate().plusDays(1).atTime(hour(context), minute(context)).atZone(zone);
        return target.toInstant().toEpochMilli();
    }
    private static void cancelAlarm(Context context) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager != null) manager.cancel(alarmIntent(context));
    }
    private static boolean schedule(Context context, long time) {
        try {
            AlarmManager manager = context.getSystemService(AlarmManager.class);
            if (manager == null) return false;
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, alarmIntent(context));
            return settings(context).edit().putLong("nextTime", time).commit();
        } catch (RuntimeException error) { return false; }
    }
    static boolean enable(Context context) {
        createChannel(context);
        if (!allowed(context)) return false;
        if (!settings(context).edit().putBoolean("enabled", true).commit()) return false;
        if (schedule(context, nextTime(context))) return true;
        disable(context); return false;
    }
    static void disable(Context context) {
        settings(context).edit().putBoolean("enabled", false).remove("nextTime").apply();
        cancelAlarm(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(ID);
    }
    static boolean setTime(Context context, int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return false;
        if (!settings(context).edit().putInt("hour", hour).putInt("minute", minute).remove("nextTime").commit()) return false;
        if (enabled(context)) {
            if (!allowed(context)) { cancelAlarm(context); return true; }
            if (!schedule(context, nextTime(context))) { disable(context); return false; }
        }
        return true;
    }
    /** Preserve an existing due time on app resume; recompute after clock/timezone changes. */
    static boolean restore(Context context, boolean recompute) {
        if (!enabled(context) || !allowed(context)) { cancelAlarm(context); return true; }
        long due = settings(context).getLong("nextTime", 0);
        long now = System.currentTimeMillis();
        if (recompute || due <= 0 || due < now - 8L * 60 * 60 * 1000) due = nextTime(context);
        return schedule(context, due);
    }
    static void deliver(Context context) {
        if (!enabled(context)) return;
        createChannel(context);
        if (!allowed(context)) { cancelAlarm(context); return; }
        // Schedule the next local calendar date before opening any activity.
        if (!schedule(context, nextTime(context))) disable(context);
        Intent open = new Intent(context, MainActivity.class).putExtra(OPEN_JOURNAL, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(context, ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(org.doctoragent.mobile.R.drawable.ic_notification)
                .setContentTitle("A moment for yourself")
                .setContentText("Your optional daily check-in is here when you want it.")
                .setContentIntent(content).setAutoCancel(true).setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_REMINDER).build();
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null && allowed(context)) manager.notify(ID, notification);
        } catch (RuntimeException ignored) { /* A permission change must not crash the receiver. */ }
    }
}
