package com.spmods.ytpro;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import java.util.List;

public class NotificationCheckService extends Service {
    private static final String TAG = "NotificationCheckService";
    private static final long CHECK_INTERVAL = 60 * 60 * 1000; // 1 hour in milliseconds
    
    private Handler handler;
    private Runnable checkRunnable;
    private NotificationFetcher fetcher;
    private AppNotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        fetcher = new NotificationFetcher(this);
        notificationManager = new AppNotificationManager(this);
        handler = new Handler();
        
        // Create runnable for periodic checks
        checkRunnable = new Runnable() {
            @Override
            public void run() {
                checkForNotifications();
                // Schedule next check
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        
        // Start periodic checking
        handler.post(checkRunnable);
        
        return START_STICKY;
    }

    private void checkForNotifications() {
        Log.d(TAG, "Checking for new notifications...");
        
        fetcher.fetchNotifications(new NotificationFetcher.NotificationCallback() {
            @Override
            public void onSuccess(List<NotificationModel> notifications) {
                Log.d(TAG, "Fetched " + notifications.size() + " notifications");
                notificationManager.showNotifications(notifications);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error fetching notifications: " + error);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        
        // Stop periodic checks
        if (handler != null && checkRunnable != null) {
            handler.removeCallbacks(checkRunnable);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
