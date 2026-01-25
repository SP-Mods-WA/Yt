package com.spmods.ytpro;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class NotificationFetcher {
    private static final String TAG = "NotificationFetcher";
    private static final String NOTIFICATIONS_URL = "https://raw.githubusercontent.com/SP-Mods-WA/Yt/main/notifications.json";
    
    private Context context;

    public NotificationFetcher(Context context) {
        this.context = context;
    }

    public void fetchNotifications(NotificationCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(NOTIFICATIONS_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Cache-Control", "no-cache");
                
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    List<NotificationModel> notifications = parseNotifications(response.toString());
                    
                    if (callback != null) {
                        callback.onSuccess(notifications);
                    }
                } else {
                    Log.e(TAG, "HTTP Error: " + responseCode);
                    if (callback != null) {
                        callback.onError("HTTP Error: " + responseCode);
                    }
                }
                
                connection.disconnect();
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching notifications", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        }).start();
    }

    private List<NotificationModel> parseNotifications(String jsonString) {
        List<NotificationModel> notificationList = new ArrayList<>();
        
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray notificationsArray = jsonObject.getJSONArray("notifications");
            
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            
            for (int i = 0; i < notificationsArray.length(); i++) {
                JSONObject notifObj = notificationsArray.getJSONObject(i);
                
                NotificationModel notification = new NotificationModel();
                notification.setId(notifObj.getString("id"));
                notification.setTitle(notifObj.getString("title"));
                notification.setMessage(notifObj.getString("message"));
                notification.setType(notifObj.getString("type"));
                notification.setPriority(notifObj.getString("priority"));
                notification.setIconUrl(notifObj.optString("icon_url", ""));
                notification.setActionUrl(notifObj.optString("action_url", ""));
                notification.setDismissible(notifObj.optBoolean("is_dismissible", true));
                
                try {
                    Date timestampDate = dateFormat.parse(notifObj.getString("timestamp"));
                    Date showUntilDate = dateFormat.parse(notifObj.getString("show_until"));
                    
                    if (timestampDate != null && showUntilDate != null) {
                        notification.setTimestamp(timestampDate.getTime());
                        notification.setShowUntil(showUntilDate.getTime());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing dates", e);
                }
                
                if (notification.isValid()) {
                    notificationList.add(notification);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing JSON", e);
        }
        
        return notificationList;
    }

    public interface NotificationCallback {
        void onSuccess(List<NotificationModel> notifications);
        void onError(String error);
    }
                    }
