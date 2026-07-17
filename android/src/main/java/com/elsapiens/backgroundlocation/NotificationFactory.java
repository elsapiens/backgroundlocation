package com.elsapiens.backgroundlocation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

/**
 * Builds the foreground-service notifications for both tracking services.
 *
 * Centralised so channel creation and notification styling live in one place, and so
 * apps can customise the user-visible text via the tracking options instead of shipping
 * hard-coded English strings.
 */
public class NotificationFactory {

    public static final String TASK_CHANNEL_ID = "location_service_channel";
    public static final String WORK_HOUR_CHANNEL_ID = "WorkHourLocationChannel";

    private static final String DEFAULT_TASK_TITLE = "Location Tracking Active";
    private static final String DEFAULT_TASK_TEXT = "Your location is being recorded for the active task";
    private static final String DEFAULT_WORK_HOUR_TITLE = "Work Hour Tracking";
    private static final String DEFAULT_WORK_HOUR_TEXT = "Location is shared periodically during work hours";

    private final Context context;

    public NotificationFactory(Context context) {
        this.context = context;
    }

    public void createTaskChannel() {
        createChannel(TASK_CHANNEL_ID, "Location Tracking");
    }

    public void createWorkHourChannel() {
        createChannel(WORK_HOUR_CHANNEL_ID, "Work Hour Location Tracking");
    }

    public Notification buildTaskNotification(String title, String text) {
        return buildNotification(TASK_CHANNEL_ID,
            orDefault(title, DEFAULT_TASK_TITLE),
            orDefault(text, DEFAULT_TASK_TEXT));
    }

    public Notification buildWorkHourNotification(String title, String text) {
        return buildNotification(WORK_HOUR_CHANNEL_ID,
            orDefault(title, DEFAULT_WORK_HOUR_TITLE),
            orDefault(text, DEFAULT_WORK_HOUR_TEXT));
    }

    private void createChannel(String channelId, String name) {
        NotificationChannel channel = new NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String channelId, String title, String text) {
        return new NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(buildLaunchIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }

    /** Tapping the notification brings the app to the foreground. */
    private PendingIntent buildLaunchIntent() {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch == null) {
            launch = new Intent();
        }
        return PendingIntent.getActivity(context, 0, launch,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
