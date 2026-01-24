package com.spmods.ytpro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppNotificationManager {
    private static final String TAG = "AppNotificationManager";
    private static final String CHANNEL_ID = "ytpro_notifications";
    private static final String CHANNEL_NAME = "YT Pro Notifications";
    private static final String PREFS_NAME = "notification_prefs";
    private static final String KEY_SHOWN_NOTIFICATIONS = "shown_notifications";
    
    private Context context;
    private NotificationManager notificationManager;
    private SharedPreferences prefs;

    public AppNotificationManager(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) 
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        createNotificationChannel();
    }

    // Create notification channel (for Android O and above)
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications for app updates and announcements");
            channel.enableLights(true);
            channel.setLightColor(Color.BLUE);
            channel.enableVibration(true);
            
            notificationManager.createNotificationChannel(channel);
        }
    }

    // Show notifications
    public void showNotifications(List<NotificationModel> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            Log.d(TAG, "No notifications to show");
            return;
        }

        Set<String> shownNotifications = getShownNotifications();
        
        for (NotificationModel notif : notifications) {
            // Check if already shown
            if (!shownNotifications.contains(notif.getId()) && notif.isValid()) {
                showNotification(notif);
                markAsShown(notif.getId());
            }
        }
    }

    // Show single notification
    private void showNotification(NotificationModel notif) {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(notif.getTitle())
                .setContentText(notif.getMessage())
                .setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(notif.getMessage()))
                .setPriority(getPriority(notif.getPriority()))
                .setAutoCancel(true)
                .setWhen(notif.getTimestamp());

            // Add action if URL is provided
            if (notif.getActionUrl() != null && !notif.getActionUrl().isEmpty()) {
                Intent intent = createIntentFromUrl(notif.getActionUrl());
                PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, 
                    notif.getId().hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                builder.setContentIntent(pendingIntent);
            }

            // Set notification icon based on type
            int icon = getIconForType(notif.getType());
            builder.setSmallIcon(icon);

            // Show notification
            notificationManager.notify(notif.getId().hashCode(), builder.build());
            
            Log.d(TAG, "Notification shown: " + notif.getTitle());
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
        }
    }

    // Create intent from URL
    private Intent createIntentFromUrl(String url) {
        if (url.startsWith("ytpro://")) {
            // Deep link to app
            Intent intent = new Intent(context, MainActivity.class);
            intent.setData(Uri.parse(url));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            return intent;
        } else {
            // External URL
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        }
    }

    // Get priority for notification
    private int getPriority(String priority) {
        switch (priority.toLowerCase()) {
            case "high":
                return NotificationCompat.PRIORITY_HIGH;
            case "low":
                return NotificationCompat.PRIORITY_LOW;
            default:
                return NotificationCompat.PRIORITY_DEFAULT;
        }
    }

    // Get icon for notification type
    private int getIconForType(String type) {
        switch (type.toLowerCase()) {
            case "update":
                return android.R.drawable.stat_sys_download_done;
            case "warning":
                return android.R.drawable.stat_notify_error;
            case "feature":
                return android.R.drawable.star_on;
            default:
                return android.R.drawable.ic_dialog_info;
        }
    }

    // Get shown notifications from SharedPreferences
    private Set<String> getShownNotifications() {
        return prefs.getStringSet(KEY_SHOWN_NOTIFICATIONS, new HashSet<>());
    }

    // Mark notification as shown
    private void markAsShown(String notificationId) {
        Set<String> shown = new HashSet<>(getShownNotifications());
        shown.add(notificationId);
        prefs.edit().putStringSet(KEY_SHOWN_NOTIFICATIONS, shown).apply();
    }

    // Clear all shown notifications history (useful for testing)
    public void clearShownNotifications() {
        prefs.edit().remove(KEY_SHOWN_NOTIFICATIONS).apply();
        Log.d(TAG, "Cleared shown notifications history");
    }

    // Cancel all notifications
    public void cancelAllNotifications() {
        notificationManager.cancelAll();
    }
        }
