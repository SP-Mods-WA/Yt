package com.spmods.ytpro;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class NotificationActivity extends Activity {
    
    private RecyclerView recyclerView;
    private NotificationAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyView;
    private NotificationFetcher fetcher;
    private NotificationPreferences prefs;
    private ImageView backButton;
    private RelativeLayout headerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);
        
        // Set status bar color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#0F0F0F"));
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decorView = window.getDecorView();
                decorView.setSystemUiVisibility(0);
            }
        }
        
        prefs = new NotificationPreferences(this);
        
        // Initialize views
        headerLayout = findViewById(R.id.notificationHeader);
        backButton = findViewById(R.id.backButton);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyView = findViewById(R.id.emptyView);
        
        // Back button
        backButton.setOnClickListener(v -> finish());
        
        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(this, prefs);
        recyclerView.setAdapter(adapter);
        
        // Load saved notifications first
        loadSavedNotifications();
        
        // Fetch new notifications
        fetcher = new NotificationFetcher(this);
        fetchNewNotifications();
    }
    
    private void loadSavedNotifications() {
        List<NotificationModel> savedNotifications = prefs.getSavedNotifications();
        if (!savedNotifications.isEmpty()) {
            adapter.setNotifications(savedNotifications);
            // Mark all as viewed when page opens
            prefs.markAllAsViewed(savedNotifications);
        }
    }
    
    private void fetchNewNotifications() {
        progressBar.setVisibility(View.VISIBLE);
        
        fetcher.fetchNotifications(new NotificationFetcher.NotificationCallback() {
            @Override
            public void onSuccess(List<NotificationModel> notifications) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    
                    if (notifications.isEmpty()) {
                        if (adapter.getItemCount() == 0) {
                            emptyView.setVisibility(View.VISIBLE);
                            emptyView.setText("No notifications available");
                        }
                    } else {
                        emptyView.setVisibility(View.GONE);
                        // Save and show
                        prefs.saveNotifications(notifications);
                        adapter.setNotifications(notifications);
                        // Mark all as viewed
                        prefs.markAllAsViewed(notifications);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (adapter.getItemCount() == 0) {
                        emptyView.setVisibility(View.VISIBLE);
                        emptyView.setText("Failed to load notifications\n" + error);
                    }
                });
            }
        });
    }
}
