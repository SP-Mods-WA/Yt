package com.spmods.ytpro;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.net.*;
import android.util.*;
import android.webkit.*;
import java.io.*;
import org.json.*;
import android.content.pm.*;
import android.provider.Settings;
import java.net.URLEncoder;
import android.content.SharedPreferences;
import android.webkit.CookieManager;
import android.media.AudioManager;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;
import java.util.*;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.os.PowerManager;
import android.os.Handler;
import android.graphics.drawable.GradientDrawable;
import android.animation.ValueAnimator;

public class MainActivity extends Activity {

    private boolean portrait = false;
    private BroadcastReceiver broadcastReceiver;
    private AudioManager audioManager;

    private String icon = "";
    private String title = "";
    private String subtitle = "";
    private long duration;
    private boolean isPlaying = false;
    private boolean mediaSession = false;
    private boolean isPip = false;
    private boolean dL = false;

    private WebView web;
    private OnBackInvokedCallback backCallback;
    
    private RelativeLayout offlineLayout;
    private boolean isOffline = false;
    
    private boolean userNavigated = false;
    private String lastUrl = "";
    
    private PowerManager.WakeLock wakeLock;
    private Handler handler = new Handler();
    private boolean isPipRequested = false;
    
    // Premium Settings
    private SharedPreferences prefs;
    private boolean sponsorBlockEnabled = true;
    private boolean returnDislikeEnabled = true;
    private boolean autoSkipAds = true;
    private boolean hideComments = false;
    private boolean theaterMode = false;
    private boolean amoledDarkMode = true;
    private boolean playbackSpeedEnabled = true;
    private float currentPlaybackSpeed = 1.0f;
    
    private ImageView pipButton;
    private ImageView downloadButton;
    private ImageView settingsButton;
    private LinearLayout premiumOverlay;
    private boolean overlayVisible = false;
    
    private long lastBackPress = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        // Initialize UI elements
        pipButton = findViewById(R.id.pipButton);
        downloadButton = findViewById(R.id.downloadButton);
        settingsButton = findViewById(R.id.settingsButton);
        premiumOverlay = findViewById(R.id.premiumOverlay);
        
        // Apply premium theme
        applyPremiumTheme();
        
        // Initialize preferences
        prefs = getSharedPreferences("YTPro_Premium", MODE_PRIVATE);
        loadPremiumSettings();
        
        // Setup premium UI
        setupPremiumUI();
        
        // Setup WebView
        setupWebView();
        
        // Setup event listeners
        setupEventListeners();
        
        // Request permissions
        requestPermissions();
        
        // Start services
        startServices();
        
        // Keep screen on during video playback
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    
    private void applyPremiumTheme() {
        // Set status bar and navigation bar colors
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
        }
        
        // Apply AMOLED black theme
        View rootView = findViewById(android.R.id.content);
        rootView.setBackgroundColor(Color.BLACK);
    }
    
    private void loadPremiumSettings() {
        sponsorBlockEnabled = prefs.getBoolean("sponsorBlock", true);
        returnDislikeEnabled = prefs.getBoolean("returnDislike", true);
        autoSkipAds = prefs.getBoolean("autoSkipAds", true);
        hideComments = prefs.getBoolean("hideComments", false);
        theaterMode = prefs.getBoolean("theaterMode", false);
        amoledDarkMode = prefs.getBoolean("amoledDarkMode", true);
        playbackSpeedEnabled = prefs.getBoolean("playbackSpeed", true);
        currentPlaybackSpeed = prefs.getFloat("playbackSpeedValue", 1.0f);
    }
    
    private void savePremiumSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("sponsorBlock", sponsorBlockEnabled);
        editor.putBoolean("returnDislike", returnDislikeEnabled);
        editor.putBoolean("autoSkipAds", autoSkipAds);
        editor.putBoolean("hideComments", hideComments);
        editor.putBoolean("theaterMode", theaterMode);
        editor.putBoolean("amoledDarkMode", amoledDarkMode);
        editor.putBoolean("playbackSpeed", playbackSpeedEnabled);
        editor.putFloat("playbackSpeedValue", currentPlaybackSpeed);
        editor.apply();
    }
    
    private void setupPremiumUI() {
        // Setup PIP button
        if (pipButton != null) {
            pipButton.setOnClickListener(v -> enterPipMode("landscape"));
            pipButton.setOnLongClickListener(v -> {
                showToast("🎬 Picture-in-Picture Mode");
                return true;
            });
        }
        
        // Setup download button
        if (downloadButton != null) {
            downloadButton.setOnClickListener(v -> showDownloadOptions());
        }
        
        // Setup settings button
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> showPremiumSettings());
        }
        
        // Setup premium overlay (floating controls)
        setupPremiumOverlay();
    }
    
    private void setupPremiumOverlay() {
        if (premiumOverlay != null) {
            // Make overlay semi-transparent
            premiumOverlay.setBackgroundColor(Color.parseColor("#80000000"));
            premiumOverlay.setVisibility(View.GONE);
            
            // Setup overlay buttons
            ImageView overlayPip = findViewById(R.id.overlayPip);
            ImageView overlayDownload = findViewById(R.id.overlayDownload);
            ImageView overlaySpeed = findViewById(R.id.overlaySpeed);
            ImageView overlayQuality = findViewById(R.id.overlayQuality);
            ImageView overlayClose = findViewById(R.id.overlayClose);
            
            if (overlayPip != null) {
                overlayPip.setOnClickListener(v -> enterPipMode("landscape"));
            }
            
            if (overlayDownload != null) {
                overlayDownload.setOnClickListener(v -> showDownloadOptions());
            }
            
            if (overlaySpeed != null) {
                overlaySpeed.setOnClickListener(v -> showPlaybackSpeedDialog());
            }
            
            if (overlayQuality != null) {
                overlayQuality.setOnClickListener(v -> showQualityOptions());
            }
            
            if (overlayClose != null) {
                overlayClose.setOnClickListener(v -> togglePremiumOverlay());
            }
        }
    }
    
    private void togglePremiumOverlay() {
        if (premiumOverlay != null) {
            if (overlayVisible) {
                premiumOverlay.setVisibility(View.GONE);
            } else {
                premiumOverlay.setVisibility(View.VISIBLE);
                // Auto-hide after 5 seconds
                handler.postDelayed(() -> {
                    if (premiumOverlay != null) {
                        premiumOverlay.setVisibility(View.GONE);
                        overlayVisible = false;
                    }
                }, 5000);
            }
            overlayVisible = !overlayVisible;
        }
    }
    
    private void setupWebView() {
        web = findViewById(R.id.web);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        
        // Configure WebView settings
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        }
        
        web.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        web.setBackgroundColor(Color.BLACK);
        
        // Set WebView client with ad blocking
        web.setWebViewClient(new PremiumWebViewClient());
        web.setWebChromeClient(new PremiumWebChromeClient());
        
        // Add JavaScript interface
        web.addJavascriptInterface(new PremiumWebInterface(this), "YTPro");
        
        // Load YouTube
        loadYouTube();
    }
    
    private void loadYouTube() {
        Intent intent = getIntent();
        String url = "https://m.youtube.com";
        
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) {
                url = data.toString();
                userNavigated = true;
            }
        }
        
        web.loadUrl(url);
    }
    
    private void setupEventListeners() {
        // Setup bottom navigation
        setupBottomNavigation();
        
        // Setup gesture detector for premium overlay
        setupGestureDetector();
    }
    
    private void setupGestureDetector() {
        web.setOnTouchListener(new View.OnTouchListener() {
            private GestureDetector gestureDetector = new GestureDetector(MainActivity.this, 
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        togglePremiumOverlay();
                        return true;
                    }
                    
                    @Override
                    public void onLongPress(MotionEvent e) {
                        showVideoOptions();
                    }
                });
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return false;
            }
        });
    }
    
    private void setupBottomNavigation() {
        // Find bottom navigation views
        View navHome = findViewById(R.id.navHome);
        View navShorts = findViewById(R.id.navShorts);
        View navSubscriptions = findViewById(R.id.navSubscriptions);
        View navLibrary = findViewById(R.id.navYou);
        
        // Set click listeners
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                userNavigated = true;
                web.loadUrl("https://m.youtube.com");
                updateBottomNavigation(0);
            });
        }
        
        if (navShorts != null) {
            navShorts.setOnClickListener(v -> {
                userNavigated = true;
                web.loadUrl("https://m.youtube.com/shorts");
                updateBottomNavigation(1);
            });
        }
        
        if (navSubscriptions != null) {
            navSubscriptions.setOnClickListener(v -> {
                userNavigated = true;
                web.loadUrl("https://m.youtube.com/feed/subscriptions");
                updateBottomNavigation(2);
            });
        }
        
        if (navLibrary != null) {
            navLibrary.setOnClickListener(v -> {
                userNavigated = true;
                web.loadUrl("https://m.youtube.com/feed/library");
                updateBottomNavigation(3);
            });
        }
    }
    
    private void updateBottomNavigation(int activeIndex) {
        // Update UI to show active tab
        int[] tabIcons = {R.id.iconHome, R.id.iconShorts, R.id.iconSubscriptions, R.id.iconYou};
        int[] tabTexts = {R.id.textHome, R.id.textShorts, R.id.textSubscriptions, R.id.textYou};
        
        for (int i = 0; i < tabIcons.length; i++) {
            ImageView icon = findViewById(tabIcons[i]);
            TextView text = findViewById(tabTexts[i]);
            
            if (icon != null && text != null) {
                if (i == activeIndex) {
                    icon.setColorFilter(Color.RED);
                    text.setTextColor(Color.RED);
                } else {
                    icon.setColorFilter(Color.GRAY);
                    text.setTextColor(Color.GRAY);
                }
            }
        }
    }
    
    private void requestPermissions() {
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != 
                PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
        
        // Request storage permission for downloads
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        }
    }
    
    private void startServices() {
        // Start background service for notifications
        Intent serviceIntent = new Intent(this, NotificationService.class);
        startService(serviceIntent);
    }
    
    // Premium Features Methods
    private void showPremiumSettings() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.PremiumDialogTheme);
        builder.setTitle("⚡ YouTube Pro Settings");
        
        View view = getLayoutInflater().inflate(R.layout.dialog_premium_settings, null);
        
        // Initialize switches
        Switch switchSponsor = view.findViewById(R.id.switchSponsor);
        Switch switchDislike = view.findViewById(R.id.switchDislike);
        Switch switchAds = view.findViewById(R.id.switchAds);
        Switch switchComments = view.findViewById(R.id.switchComments);
        Switch switchTheater = view.findViewById(R.id.switchTheater);
        Switch switchAmoled = view.findViewById(R.id.switchAmoled);
        Switch switchSpeed = view.findViewById(R.id.switchSpeed);
        
        // Set current values
        switchSponsor.setChecked(sponsorBlockEnabled);
        switchDislike.setChecked(returnDislikeEnabled);
        switchAds.setChecked(autoSkipAds);
        switchComments.setChecked(hideComments);
        switchTheater.setChecked(theaterMode);
        switchAmoled.setChecked(amoledDarkMode);
        switchSpeed.setChecked(playbackSpeedEnabled);
        
        builder.setView(view);
        
        builder.setPositiveButton("💾 Save", (dialog, which) -> {
            // Save settings
            sponsorBlockEnabled = switchSponsor.isChecked();
            returnDislikeEnabled = switchDislike.isChecked();
            autoSkipAds = switchAds.isChecked();
            hideComments = switchComments.isChecked();
            theaterMode = switchTheater.isChecked();
            amoledDarkMode = switchAmoled.isChecked();
            playbackSpeedEnabled = switchSpeed.isChecked();
            
            savePremiumSettings();
            applyPremiumSettings();
            
            showToast("✅ Premium settings saved!");
        });
        
        builder.setNegativeButton("Cancel", null);
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Customize dialog buttons
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        
        if (positiveButton != null) {
            positiveButton.setTextColor(Color.RED);
            positiveButton.setTypeface(null, Typeface.BOLD);
        }
        
        if (negativeButton != null) {
            negativeButton.setTextColor(Color.GRAY);
        }
    }
    
    private void applyPremiumSettings() {
        // Apply settings to WebView
        String js = "javascript:(function() {" +
            "try {" +
            "  // Apply sponsor block" +
            "  if (window.sponsorBlock) window.sponsorBlock(" + sponsorBlockEnabled + ");" +
            
            "  // Apply dislike count" +
            "  if (window.returnDislike) window.returnDislike(" + returnDislikeEnabled + ");" +
            
            "  // Apply ad blocking" +
            "  if (" + autoSkipAds + ") {" +
            "    var ads = document.querySelectorAll('.video-ads, .ad-container, #player-ads');" +
            "    ads.forEach(ad => ad.style.display = 'none');" +
            "  }" +
            
            "  // Apply hide comments" +
            "  if (" + hideComments + ") {" +
            "    var comments = document.getElementById('comments');" +
            "    if (comments) comments.style.display = 'none';" +
            "  }" +
            
            "  // Apply theater mode" +
            "  if (" + theaterMode + ") {" +
            "    document.body.classList.add('theater-mode');" +
            "  } else {" +
            "    document.body.classList.remove('theater-mode');" +
            "  }" +
            
            "  // Apply AMOLED dark mode" +
            "  if (" + amoledDarkMode + ") {" +
            "    document.body.style.backgroundColor = '#000000';" +
            "    var app = document.querySelector('ytm-app');" +
            "    if (app) app.style.backgroundColor = '#000000';" +
            "  }" +
            
            "  // Apply playback speed" +
            "  if (" + playbackSpeedEnabled + ") {" +
            "    var video = document.querySelector('video');" +
            "    if (video) video.playbackRate = " + currentPlaybackSpeed + ";" +
            "  }" +
            
            "  console.log('✅ Premium settings applied');" +
            "} catch(e) { console.error('Settings error:', e); }" +
            "})()";
        
        web.evaluateJavascript(js, null);
    }
    
    private void showDownloadOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📥 Download Video");
        builder.setMessage("Select download quality:");
        
        String[] qualities = {"360p", "480p", "720p HD", "1080p Full HD", "1440p 2K", "2160p 4K"};
        
        builder.setItems(qualities, (dialog, which) -> {
            String quality = qualities[which];
            downloadVideo(quality);
        });
        
        builder.setNegativeButton("Cancel", null);
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void downloadVideo(String quality) {
        // This would trigger JavaScript to get video URL
        web.evaluateJavascript(
            "javascript:(function() {" +
            "  try {" +
            "    var video = document.querySelector('video');" +
            "    if (video && video.src) {" +
            "      YTPro.downloadVideo(video.src, '" + quality + "');" +
            "    } else {" +
            "      alert('No video found or video source not available');" +
            "    }" +
            "  } catch(e) { console.error(e); }" +
            "})()", 
            null
        );
    }
    
    private void showPlaybackSpeedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🎚️ Playback Speed");
        
        String[] speeds = {"0.25x", "0.5x", "0.75x", "Normal (1x)", "1.25x", "1.5x", "1.75x", "2x"};
        
        builder.setSingleChoiceItems(speeds, 3, (dialog, which) -> {
            float speed = 1.0f;
            switch (which) {
                case 0: speed = 0.25f; break;
                case 1: speed = 0.5f; break;
                case 2: speed = 0.75f; break;
                case 3: speed = 1.0f; break;
                case 4: speed = 1.25f; break;
                case 5: speed = 1.5f; break;
                case 6: speed = 1.75f; break;
                case 7: speed = 2.0f; break;
            }
            
            currentPlaybackSpeed = speed;
            prefs.edit().putFloat("playbackSpeedValue", speed).apply();
            
            // Apply to video
            web.evaluateJavascript(
                "var video = document.querySelector('video');" +
                "if(video) video.playbackRate = " + speed + ";",
                null
            );
            
            showToast("Speed: " + speeds[which]);
            dialog.dismiss();
        });
        
        builder.setNegativeButton("Cancel", null);
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void showQualityOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🎬 Video Quality");
        
        String[] qualities = {"Auto", "144p", "240p", "360p", "480p", "720p", "1080p", "1440p", "2160p"};
        
        builder.setItems(qualities, (dialog, which) -> {
            String quality = qualities[which];
            
            // Change video quality via JavaScript
            web.evaluateJavascript(
                "javascript:(function() {" +
                "  try {" +
                "    var settingsButton = document.querySelector('.ytp-settings-button');" +
                "    if (settingsButton) {" +
                "      settingsButton.click();" +
                "      setTimeout(() => {" +
                "        var qualityOption = document.querySelector('.ytp-menuitem[aria-label*=\"Quality\"]');" +
                "        if (qualityOption) {" +
                "          qualityOption.click();" +
                "          setTimeout(() => {" +
                "            var targetQuality = document.querySelector('.ytp-menuitem-label:contains(\"" + quality + "\")');" +
                "            if (targetQuality) targetQuality.click();" +
                "          }, 300);" +
                "        }" +
                "      }, 300);" +
                "    }" +
                "  } catch(e) { console.error(e); }" +
                "})()", 
                null
            );
            
            showToast("Quality: " + quality);
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void showVideoOptions() {
        PopupMenu popup = new PopupMenu(this, web);
        popup.getMenuInflater().inflate(R.menu.video_options, popup.getMenu());
        
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            
            if (id == R.id.action_pip) {
                enterPipMode("landscape");
                return true;
            }
            else if (id == R.id.action_download) {
                showDownloadOptions();
                return true;
            }
            else if (id == R.id.action_speed) {
                showPlaybackSpeedDialog();
                return true;
            }
            else if (id == R.id.action_quality) {
                showQualityOptions();
                return true;
            }
            else if (id == R.id.action_stats) {
                showVideoStats();
                return true;
            }
            else if (id == R.id.action_share) {
                shareVideo();
                return true;
            }
            return false;
        });
        
        popup.show();
    }
    
    private void showVideoStats() {
        web.evaluateJavascript(
            "javascript:(function() {" +
            "  var video = document.querySelector('video');" +
            "  if (video) {" +
            "    var stats = '📊 Video Stats\\n' +" +
            "      'Resolution: ' + video.videoWidth + 'x' + video.videoHeight + '\\n' +" +
            "      'Duration: ' + video.duration.toFixed(2) + 's\\n' +" +
            "      'Current: ' + video.currentTime.toFixed(2) + 's\\n' +" +
            "      'Buffered: ' + video.buffered.end(0).toFixed(2) + 's\\n' +" +
            "      'Playback: ' + video.playbackRate + 'x\\n' +" +
            "      'Volume: ' + (video.volume * 100).toFixed(0) + '%\\n' +" +
            "      'Paused: ' + video.paused;" +
            "    YTPro.showToast(stats);" +
            "  } else {" +
            "    YTPro.showToast('No video playing');" +
            "  }" +
            "})()",
            null
        );
    }
    
    private void shareVideo() {
        String currentUrl = web.getUrl();
        if (currentUrl != null && currentUrl.contains("youtube.com/watch")) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Watch this on YouTube Pro: " + currentUrl);
            startActivity(Intent.createChooser(shareIntent, "Share Video"));
        } else {
            showToast("No video to share");
        }
    }
    
    // PIP Methods
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        
        isPip = isInPictureInPictureMode;
        isPipRequested = false;
        
        if (isInPictureInPictureMode) {
            // Entering PIP - Hide UI elements
            hideUIForPip();
            
            // Ensure video continues playing
            handler.postDelayed(() -> {
                web.evaluateJavascript(
                    "var video = document.querySelector('video');" +
                    "if(video && video.paused) video.play();",
                    null
                );
            }, 100);
            
        } else {
            // Exiting PIP - Show UI elements
            showUIAfterPip();
        }
    }
    
    private void hideUIForPip() {
        // Hide bottom navigation
        View bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav != null) {
            bottomNav.setVisibility(View.GONE);
        }
        
        // Hide premium overlay
        if (premiumOverlay != null) {
            premiumOverlay.setVisibility(View.GONE);
        }
        
        // Hide floating buttons
        if (pipButton != null) pipButton.setVisibility(View.GONE);
        if (downloadButton != null) downloadButton.setVisibility(View.GONE);
        if (settingsButton != null) settingsButton.setVisibility(View.GONE);
    }
    
    private void showUIAfterPip() {
        // Show bottom navigation
        View bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav != null) {
            bottomNav.setVisibility(View.VISIBLE);
        }
        
        // Show floating buttons
        if (pipButton != null) pipButton.setVisibility(View.VISIBLE);
        if (downloadButton != null) downloadButton.setVisibility(View.VISIBLE);
        if (settingsButton != null) settingsButton.setVisibility(View.VISIBLE);
    }
    
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        
        // Auto-enter PIP when leaving app during video playback
        if (!isPip && !isPipRequested) {
            String currentUrl = web.getUrl();
            boolean isVideoPage = currentUrl != null && 
                (currentUrl.contains("watch") || currentUrl.contains("shorts"));
            
            if (isVideoPage && isPlaying) {
                handler.postDelayed(() -> {
                    enterPipMode("landscape");
                }, 300);
            }
        }
    }
    
    private void enterPipMode(String orientation) {
        if (Build.VERSION.SDK_INT < 26) {
            showToast("PIP requires Android 8.0+");
            return;
        }
        
        if (isPip || isPipRequested) return;
        
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            
            isPipRequested = true;
            
            try {
                // Prepare video for PIP
                web.evaluateJavascript(
                    "var video = document.querySelector('video');" +
                    "if(video) window.pipReady = true;",
                    null
                );
                
                // Create PIP parameters
                PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
                
                if ("portrait".equals(orientation)) {
                    builder.setAspectRatio(new android.util.Rational(9, 16));
                } else {
                    builder.setAspectRatio(new android.util.Rational(16, 9));
                }
                
                builder.setAutoEnterEnabled(true);
                
                // Enter PIP
                boolean success = enterPictureInPictureMode(builder.build());
                
                if (success) {
                    showToast("🎬 PIP Mode Activated");
                } else {
                    showToast("❌ PIP Failed");
                    isPipRequested = false;
                }
                
            } catch (Exception e) {
                showToast("PIP Error: " + e.getMessage());
                isPipRequested = false;
            }
        });
    }
    
    // Utility Methods
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            if (System.currentTimeMillis() - lastBackPress < 2000) {
                super.onBackPressed();
            } else {
                showToast("Press back again to exit");
                lastBackPress = System.currentTimeMillis();
            }
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Apply premium settings when resuming
        handler.postDelayed(this::applyPremiumSettings, 1000);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Release resources
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
    
    // WebView Client Classes
    private class PremiumWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            
            // Block redirects to shorts if not intended
            if (url.contains("/shorts") && !userNavigated) {
                String currentUrl = view.getUrl();
                if (currentUrl != null && !currentUrl.contains("/shorts")) {
                    return true;
                }
            }
            
            // Block ads
            if (autoSkipAds && (
                url.contains("doubleclick.net") ||
                url.contains("googleads") ||
                url.contains("ads.youtube") ||
                url.contains("adservice.google"))) {
                return true;
            }
            
            userNavigated = false;
            return false;
        }
        
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            
            // Block ads at network level
            if (autoSkipAds && (
                url.contains("googlesyndication") ||
                url.contains("googleads") ||
                url.contains("doubleclick") ||
                url.contains("pagead") ||
                url.contains("adsystem") ||
                url.contains("adservice"))) {
                return new WebResourceResponse("text/plain", "utf-8", null);
            }
            
            return super.shouldInterceptRequest(view, request);
        }
        
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            
            // Apply premium settings after page loads
            handler.postDelayed(() -> {
                applyPremiumSettings();
                
                // Apply custom CSS for better UI
                applyCustomCSS();
                
                // Auto-hide YouTube premium prompts
                hidePremiumPrompts();
            }, 1500);
        }
        
        private void applyCustomCSS() {
            String css = "'" +
                "body { background-color: #000000 !important; }" +
                "ytm-pivot-bar-renderer { display: none !important; }" +
                ".video-ads, .ad-container, #player-ads { display: none !important; }" +
                "ytm-mealbar-promo-renderer { display: none !important; }" +
                ".ytp-ad-image-overlay, .ytp-ad-text-overlay { display: none !important; }" +
                "'";
            
            web.evaluateJavascript(
                "var style = document.createElement('style');" +
                "style.innerHTML = " + css + ";" +
                "document.head.appendChild(style);",
                null
            );
        }
        
        private void hidePremiumPrompts() {
            web.evaluateJavascript(
                "setInterval(() => {" +
                "  var prompts = document.querySelectorAll('ytm-mealbar-promo-renderer, ytm-banner-promo-renderer');" +
                "  prompts.forEach(prompt => prompt.remove());" +
                "}, 1000);",
                null
            );
        }
    }
    
    private class PremiumWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            // You can add a progress bar here if needed
        }
        
        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            // Update title for notification/background playback
            MainActivity.this.title = title;
        }
    }
    
    // JavaScript Interface
    public class PremiumWebInterface {
        Context mContext;
        
        PremiumWebInterface(Context c) {
            mContext = c;
        }
        
        @JavascriptInterface
        public void showToast(String message) {
            showToast(message);
        }
        
        @JavascriptInterface
        public void downloadVideo(String url, String quality) {
            // Download video file
            DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("YouTube Pro Download - " + quality);
            request.setDescription("Downloaded via YouTube Pro");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, 
                "YouTubePro/" + System.currentTimeMillis() + ".mp4");
            
            downloadManager.enqueue(request);
            showToast("📥 Download started: " + quality);
        }
        
        @JavascriptInterface
        public void playVideo() {
            isPlaying = true;
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YTPro::VideoPlayback");
            }
            if (!wakeLock.isHeld()) {
                wakeLock.acquire();
            }
        }
        
        @JavascriptInterface
        public void pauseVideo() {
            isPlaying = false;
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
        
        @JavascriptInterface
        public void getVideoInfo() {
            web.evaluateJavascript(
                "var video = document.querySelector('video');" +
                "if(video) {" +
                "  var info = {" +
                "    url: window.location.href," +
                "    title: document.title," +
                "    duration: video.duration," +
                "    currentTime: video.currentTime," +
                "    playing: !video.paused" +
                "  };" +
                "  YTPro.onVideoInfo(JSON.stringify(info));" +
                "}",
                null
            );
        }
        
        @JavascriptInterface
        public void onVideoInfo(String jsonInfo) {
            // Handle video info
            try {
                JSONObject info = new JSONObject(jsonInfo);
                title = info.optString("title", "");
                duration = (long) info.optDouble("duration", 0);
                isPlaying = info.optBoolean("playing", false);
                
                // Update notification if playing
                if (isPlaying) {
                    updateNotification();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void updateNotification() {
        // Create notification for background playback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "ytpro_playback",
                "YouTube Pro Playback",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Video playback notifications");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "ytpro_playback")
            .setContentTitle(title)
            .setContentText("Playing in YouTube Pro")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true);
        
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(1001, builder.build());
    }
}
