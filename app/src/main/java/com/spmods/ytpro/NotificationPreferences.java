package com.spmods.ytpro;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotificationPreferences {
    private static final String PREFS_NAME = "ytpro_notification_prefs";
    private static final String KEY_NOTIFICATIONS = "saved_notifications";
    private static final String KEY_VIEWED_IDS = "viewed_notification_ids";
    
    private SharedPreferences prefs;
    private Gson gson;
    
    public NotificationPreferences(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }
    
    public void saveNotifications(List<NotificationModel> notifications) {
        String json = gson.toJson(notifications);
        prefs.edit().putString(KEY_NOTIFICATIONS, json).apply();
    }
    
    public List<NotificationModel> getSavedNotifications() {
        String json = prefs.getString(KEY_NOTIFICATIONS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        
        Type type = new TypeToken<List<NotificationModel>>(){}.getType();
        List<NotificationModel> notifications = gson.fromJson(json, type);
        
        // Mark viewed notifications
        Set<String> viewedIds = getViewedIds();
        for (NotificationModel notif : notifications) {
            if (notif != null) {
                notif.setViewed(viewedIds.contains(notif.getId()));
            }
        }
        
        return notifications;
    }
    
    public void markAllAsViewed(List<NotificationModel> notifications) {
        Set<String> viewedIds = getViewedIds();
        for (NotificationModel notif : notifications) {
            if (notif != null) {
                viewedIds.add(notif.getId());
            }
        }
        prefs.edit().putStringSet(KEY_VIEWED_IDS, viewedIds).apply();
    }
    
    public Set<String> getViewedIds() {
        return new HashSet<>(prefs.getStringSet(KEY_VIEWED_IDS, new HashSet<>()));
    }
    
    public int getUnviewedCount() {
        List<NotificationModel> notifications = getSavedNotifications();
        int count = 0;
        for (NotificationModel notif : notifications) {
            if (notif != null && !notif.isViewed() && notif.isValid()) {
                count++;
            }
        }
        return count;
    }
}
