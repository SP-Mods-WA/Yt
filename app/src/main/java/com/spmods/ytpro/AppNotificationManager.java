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

    public void showNotifications(List<NotificationModel> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            Log.d(TAG, "No notifications to show");
            return;
        }

        Set<String> shownNotifications = getShownNotifications();
        
        for (NotificationModel notif : notifications) {
            if (!shownNotifications.contains(notif.getId()) && notif.isValid()) {
                showNotification(notif);
                markAsShown(notif.getId());
            }
        }
    }

    private void showNotification(NotificationModel notif) {
        try {
            Notification.Builder builder;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(context, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(context);
            }

            builder.setSmallIcon(getIconForType(notif.getType()))
                   .setContentTitle(notif.getTitle())
                   .setContentText(notif.getMessage())
                   .setAutoCancel(true)
                   .setWhen(notif.getTimestamp());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                builder.setStyle(new Notification.BigTextStyle()
                    .bigText(notif.getMessage()));
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                builder.setPriority(getPriorityLegacy(notif.getPriority()));
            }

            if (notif.getActionUrl() != null && !notif.getActionUrl().isEmpty()) {
                Intent intent = createIntentFromUrl(notif.getActionUrl());
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, 
                    notif.getId().hashCode(),
                    intent,
                    flags
                );
                builder.setContentIntent(pendingIntent);
            }

            notificationManager.notify(notif.getId().hashCode(), builder.build());
            
            Log.d(TAG, "Notification shown: " + notif.getTitle());
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
        }
    }

    private Intent createIntentFromUrl(String url) {
        if (url.startsWith("ytpro://")) {
            Intent intent = new Intent(context, MainActivity.class);
            intent.setData(Uri.parse(url));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            return intent;
        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        }
    }

    private int getPriorityLegacy(String priority) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            switch (priority.toLowerCase()) {
                case "high":
                    return Notification.PRIORITY_HIGH;
                case "low":
                    return Notification.PRIORITY_LOW;
                default:
                    return Notification.PRIORITY_DEFAULT;
            }
        }
        return 0;
    }

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

    private Set<String> getShownNotifications() {
        return prefs.getStringSet(KEY_SHOWN_NOTIFICATIONS, new HashSet<String>());
    }

    private void markAsShown(String notificationId) {
        Set<String> shown = new HashSet<>(getShownNotifications());
        shown.add(notificationId);
        prefs.edit().putStringSet(KEY_SHOWN_NOTIFICATIONS, shown).apply();
    }

    public void clearShownNotifications() {
        prefs.edit().remove(KEY_SHOWN_NOTIFICATIONS).apply();
        Log.d(TAG, "Cleared shown notifications history");
    }

    public void cancelAllNotifications() {
        notificationManager.cancelAll();
    }
                }
