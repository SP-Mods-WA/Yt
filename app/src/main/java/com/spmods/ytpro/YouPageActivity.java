package com.spmods.ytpro;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class YouPageActivity extends Activity {
    
    private String userName = "Guest";
    private String userEmail = "Not signed in";
    private SharedPreferences prefs;
    private TextView nameText;
    private TextView emailText;
    private ImageView profileImage;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_you_page);
        
        prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#0F0F0F"));
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decorView = window.getDecorView();
                decorView.setSystemUiVisibility(0);
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestAccountPermissions();
        }
        
        setupHeader();
        setupUserProfile();
        setupMenuItems();
    }
    
    private void requestAccountPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.GET_ACCOUNTS) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                
                requestPermissions(
                    new String[]{
                        android.Manifest.permission.GET_ACCOUNTS,
                        android.Manifest.permission.READ_CONTACTS
                    },
                    200
                );
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 200) {
            if (grantResults.length > 0 && 
                grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Account access granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Account access denied. Some features may not work.", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void setupHeader() {
        ImageView backButton = findViewById(R.id.youBackButton);
        ImageView searchButton = findViewById(R.id.youSearchButton);
        ImageView moreButton = findViewById(R.id.youMoreButton);
        
        backButton.setOnClickListener(v -> finish());
        
        searchButton.setOnClickListener(v -> {
            Toast.makeText(this, "Search coming soon", Toast.LENGTH_SHORT).show();
        });
        
        moreButton.setOnClickListener(v -> {
            showMoreOptions();
        });
    }
    
    private void setupUserProfile() {
        LinearLayout profileSection = findViewById(R.id.profileSection);
        nameText = findViewById(R.id.userName);
        emailText = findViewById(R.id.userEmail);
        profileImage = findViewById(R.id.profileImage);
        
        loadAccountInfo();
        
        profileSection.setOnClickListener(v -> {
            showAccountSwitcher();
        });
    }
    
    private void loadAccountInfo() {
        userName = prefs.getString("user_name", "Guest");
        userEmail = prefs.getString("user_email", "Not signed in");
        
        nameText.setText(userName);
        emailText.setText(userEmail);
        
        if (!userEmail.equals("Not signed in")) {
            profileImage.setColorFilter(Color.parseColor("#FF0000"));
        } else {
            profileImage.setColorFilter(Color.parseColor("#AAAAAA"));
        }
    }
    
    private void showAccountSwitcher() {
        AccountManager accountManager = AccountManager.get(this);
        Account[] accounts = accountManager.getAccountsByType("com.google");
        
        if (accounts.length == 0) {
            showLoginDialog();
        } else {
            showAccountPickerDialog(accounts);
        }
    }
    
    private void showAccountPickerDialog(Account[] accounts) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose an account");
        
        List<String> accountNames = new ArrayList<>();
        accountNames.add("➕ Add another account");
        
        String currentEmail = prefs.getString("user_email", "");
        
        for (Account account : accounts) {
            String displayName = account.name;
            if (account.name.equals(currentEmail)) {
                displayName = "✓ " + account.name + " (Current)";
            }
            accountNames.add(displayName);
        }
        
        accountNames.add("👤 Use YouTube without an account");
        
        String[] items = accountNames.toArray(new String[0]);
        
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                Intent intent = new Intent(YouPageActivity.this, YouTubeLoginActivity.class);
                startActivity(intent);
            } else if (which == items.length - 1) {
                prefs.edit()
                    .putString("user_name", "Guest")
                    .putString("user_email", "Not signed in")
                    .apply();
                loadAccountInfo();
                Toast.makeText(this, "Using YouTube as Guest", Toast.LENGTH_SHORT).show();
            } else {
                Account selectedAccount = accounts[which - 1];
                selectAccount(selectedAccount);
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void selectAccount(Account account) {
        String name = account.name.split("@")[0];
        name = name.substring(0, 1).toUpperCase() + name.substring(1);
        
        prefs.edit()
            .putString("user_name", name)
            .putString("user_email", account.name)
            .apply();
        
        loadAccountInfo();
        
        Toast.makeText(this, "Switched to " + account.name, Toast.LENGTH_SHORT).show();
        
        Intent intent = new Intent("ACCOUNT_CHANGED");
        intent.putExtra("email", account.name);
        sendBroadcast(intent);
    }
    
    private void showLoginDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sign in");
        builder.setMessage("You need to add a Google account to your device first.\n\nGo to Settings → Accounts → Add account → Google");
        
        builder.setPositiveButton("Open Login Page", (dialog, which) -> {
            Intent intent = new Intent(YouPageActivity.this, YouTubeLoginActivity.class);
            startActivity(intent);
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void showMoreOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("More options");
        
        String[] options = {
            "Switch account",
            "Sign out",
            "Manage accounts on Google"
        };
        
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    showAccountSwitcher();
                    break;
                case 1:
                    signOut();
                    break;
                case 2:
                    openGoogleAccountSettings();
                    break;
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void signOut() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sign out");
        builder.setMessage("Are you sure you want to sign out?");
        
        builder.setPositiveButton("Sign out", (dialog, which) -> {
            prefs.edit()
                .putString("user_name", "Guest")
                .putString("user_email", "Not signed in")
                .apply();
            loadAccountInfo();
            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show();
            
            android.webkit.CookieManager.getInstance().removeAllCookies(null);
            
            Intent intent = new Intent("ACCOUNT_CHANGED");
            intent.putExtra("email", "");
            sendBroadcast(intent);
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void openGoogleAccountSettings() {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_SYNC_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void setupMenuItems() {
        findViewById(R.id.menuHistory).setOnClickListener(v -> {
            openWebPage("https://m.youtube.com/feed/history");
        });
        
        findViewById(R.id.menuPlaylists).setOnClickListener(v -> {
            openWebPage("https://m.youtube.com/feed/playlists");
        });
        
        findViewById(R.id.menuYourVideos).setOnClickListener(v -> {
            openWebPage("https://m.youtube.com/feed/my_videos");
        });
        
        findViewById(R.id.menuWatchLater).setOnClickListener(v -> {
            openWebPage("https://m.youtube.com/playlist?list=WL");
        });
        
        findViewById(R.id.menuLiked).setOnClickListener(v -> {
            openWebPage("https://m.youtube.com/playlist?list=LL");
        });
        
        findViewById(R.id.menuDownloads).setOnClickListener(v -> {
            Toast.makeText(this, "Downloads feature coming soon", Toast.LENGTH_SHORT).show();
        });
        
        findViewById(R.id.menuPremium).setOnClickListener(v -> {
            Toast.makeText(this, "Premium not required in YTPro 😎", Toast.LENGTH_SHORT).show();
        });
        
        findViewById(R.id.menuSettings).setOnClickListener(v -> {
            openWebPage("https://m.youtube.com/#settings");
        });
        
        findViewById(R.id.menuHelp).setOnClickListener(v -> {
            Toast.makeText(this, "Help & Feedback", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void openWebPage(String url) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(android.net.Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadAccountInfo();
    }
}
