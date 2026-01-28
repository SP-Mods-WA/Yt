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
import android.graphics.BlurMaskFilter;
import android.graphics.Paint;

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

    private YTProWebview web;
    private OnBackInvokedCallback backCallback;
    
    private RelativeLayout offlineLayout;
    private boolean isOffline = false;
    
    private boolean userNavigated = false;
    private String lastUrl = "";
    
    private boolean scriptsInjected = false;
    
    private PowerManager.WakeLock wakeLock;
    private Handler handler = new Handler();
    private boolean isPipRequested = false;
    
    // Premium features
    private boolean sponsorBlockEnabled = true;
    private boolean autoSkipIntro = true;
    private boolean autoSkipOutro = true;
    private boolean autoSkipAds = true;
    private boolean returnDislikeCount = true;
    private boolean hideComments = false;
    private boolean theaterMode = false;
    private boolean hdrEnabled = true;
    
    // Theme
    private boolean darkMode = true;
    private boolean amoledMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        // Apply AMOLED theme
        applyAmoledTheme();
        
        // Setup navigation bar with blur
        setupBlurredNavigationBar();
        
        disablePlayProtectWarnings();
        
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "YTPro::VideoPlaybackLock"
        );
        wakeLock.setReferenceCounted(false);

        SharedPreferences prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);

        if (!prefs.contains("bgplay")) {
            prefs.edit().putBoolean("bgplay", true).apply();
        }
        
        // Load premium settings
        loadPremiumSettings();
        
        requestNotificationPermission();
        
        if (!isNetworkAvailable()) {
            showOfflineScreen();
        } else {
            load(false);
            checkForAppUpdate();
            startNotificationService();
            checkNotificationsNow();
            setupBottomNavigation();
        }
        
        MainActivity.this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setupPremiumFeatures();
    }

    private void applyAmoledTheme() {
        View rootView = findViewById(android.R.id.content);
        rootView.setBackgroundColor(Color.BLACK);
        
        Window window = getWindow();
        window.setNavigationBarColor(Color.BLACK);
        window.setStatusBarColor(Color.BLACK);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            );
        }
    }

    private void setupBlurredNavigationBar() {
        LinearLayout bottomNav = findViewById(R.id.bottomNavigation);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ සඳහා material you blur effect
            bottomNav.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                    Color.parseColor("#20000000"),
                    Color.parseColor("#40000000")
                }
            ));
            
            // Add blur effect
            bottomNav.setElevation(20f);
            bottomNav.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            bottomNav.setClipToOutline(false);
            
        } else {
            // Older Android versions සඳහා semi-transparent background
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.parseColor("#E6000000")); // 90% transparent black
            gd.setCornerRadius(30);
            bottomNav.setBackground(gd);
            
            // Add shadow for depth
            bottomNav.setElevation(8f);
        }
        
        // Add glass morphism effect
        bottomNav.getBackground().setAlpha(180);
    }

    private void loadPremiumSettings() {
        SharedPreferences prefs = getSharedPreferences("YTPro_Premium", MODE_PRIVATE);
        sponsorBlockEnabled = prefs.getBoolean("sponsorBlock", true);
        autoSkipIntro = prefs.getBoolean("autoSkipIntro", true);
        autoSkipOutro = prefs.getBoolean("autoSkipOutro", true);
        autoSkipAds = prefs.getBoolean("autoSkipAds", true);
        returnDislikeCount = prefs.getBoolean("returnDislike", true);
        hideComments = prefs.getBoolean("hideComments", false);
        theaterMode = prefs.getBoolean("theaterMode", false);
        hdrEnabled = prefs.getBoolean("hdrEnabled", true);
        darkMode = prefs.getBoolean("darkMode", true);
        amoledMode = prefs.getBoolean("amoledMode", true);
    }

    private void savePremiumSettings() {
        SharedPreferences.Editor editor = getSharedPreferences("YTPro_Premium", MODE_PRIVATE).edit();
        editor.putBoolean("sponsorBlock", sponsorBlockEnabled);
        editor.putBoolean("autoSkipIntro", autoSkipIntro);
        editor.putBoolean("autoSkipOutro", autoSkipOutro);
        editor.putBoolean("autoSkipAds", autoSkipAds);
        editor.putBoolean("returnDislike", returnDislikeCount);
        editor.putBoolean("hideComments", hideComments);
        editor.putBoolean("theaterMode", theaterMode);
        editor.putBoolean("hdrEnabled", hdrEnabled);
        editor.putBoolean("darkMode", darkMode);
        editor.putBoolean("amoledMode", amoledMode);
        editor.apply();
    }

    private void setupPremiumFeatures() {
        // Setup premium shortcuts
        ImageView premiumIcon = findViewById(R.id.premiumIcon);
        if (premiumIcon != null) {
            premiumIcon.setOnClickListener(v -> showPremiumMenu());
        }
        
        // Add floating action button for quick settings
        setupFloatingActionButton();
    }

    private void showPremiumMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🎬 YouTube Pro Settings");
        
        View view = getLayoutInflater().inflate(R.layout.premium_menu, null);
        
        SwitchCompat switchSponsorBlock = view.findViewById(R.id.switchSponsorBlock);
        SwitchCompat switchSkipIntro = view.findViewById(R.id.switchSkipIntro);
        SwitchCompat switchSkipOutro = view.findViewById(R.id.switchSkipOutro);
        SwitchCompat switchSkipAds = view.findViewById(R.id.switchSkipAds);
        SwitchCompat switchDislike = view.findViewById(R.id.switchDislike);
        SwitchCompat switchHideComments = view.findViewById(R.id.switchHideComments);
        SwitchCompat switchTheaterMode = view.findViewById(R.id.switchTheaterMode);
        SwitchCompat switchHDR = view.findViewById(R.id.switchHDR);
        SwitchCompat switchDarkMode = view.findViewById(R.id.switchDarkMode);
        SwitchCompat switchAmoled = view.findViewById(R.id.switchAmoled);
        
        // Set current values
        switchSponsorBlock.setChecked(sponsorBlockEnabled);
        switchSkipIntro.setChecked(autoSkipIntro);
        switchSkipOutro.setChecked(autoSkipOutro);
        switchSkipAds.setChecked(autoSkipAds);
        switchDislike.setChecked(returnDislikeCount);
        switchHideComments.setChecked(hideComments);
        switchTheaterMode.setChecked(theaterMode);
        switchHDR.setChecked(hdrEnabled);
        switchDarkMode.setChecked(darkMode);
        switchAmoled.setChecked(amoledMode);
        
        builder.setView(view);
        
        builder.setPositiveButton("💾 Save", (dialog, which) -> {
            sponsorBlockEnabled = switchSponsorBlock.isChecked();
            autoSkipIntro = switchSkipIntro.isChecked();
            autoSkipOutro = switchSkipOutro.isChecked();
            autoSkipAds = switchSkipAds.isChecked();
            returnDislikeCount = switchDislike.isChecked();
            hideComments = switchHideComments.isChecked();
            theaterMode = switchTheaterMode.isChecked();
            hdrEnabled = switchHDR.isChecked();
            darkMode = switchDarkMode.isChecked();
            amoledMode = switchAmoled.isChecked();
            
            savePremiumSettings();
            applyPremiumFeaturesToWeb();
            
            Toast.makeText(MainActivity.this, "✅ Premium settings saved!", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("Cancel", null);
        
        builder.show();
    }

    private void applyPremiumFeaturesToWeb() {
        if (web != null) {
            String js = "(function() {" +
                "window.YTProSettings = {" +
                "  sponsorBlock: " + sponsorBlockEnabled + "," +
                "  autoSkipIntro: " + autoSkipIntro + "," +
                "  autoSkipOutro: " + autoSkipOutro + "," +
                "  autoSkipAds: " + autoSkipAds + "," +
                "  returnDislike: " + returnDislikeCount + "," +
                "  hideComments: " + hideComments + "," +
                "  theaterMode: " + theaterMode + "," +
                "  hdrEnabled: " + hdrEnabled + "," +
                "  darkMode: " + darkMode + "," +
                "  amoledMode: " + amoledMode +
                "};" +
                
                "// Apply sponsor block" +
                "if (window.sponsorBlock) window.sponsorBlock(" + sponsorBlockEnabled + ");" +
                
                "// Apply dislike count" +
                "if (window.returnDislike) window.returnDislike(" + returnDislikeCount + ");" +
                
                "// Apply hide comments" +
                "if (" + hideComments + ") {" +
                "  var comments = document.getElementById('comments');" +
                "  if (comments) comments.style.display = 'none';" +
                "}" +
                
                "// Apply theater mode" +
                "if (window.toggleTheaterMode) window.toggleTheaterMode(" + theaterMode + ");" +
                
                "// Apply dark/amoled mode" +
                "if (" + amoledMode + ") {" +
                "  document.body.style.backgroundColor = '#000000';" +
                "  var dark = document.querySelector('ytm-app');" +
                "  if (dark) dark.style.backgroundColor = '#000000';" +
                "}" +
                "})();";
            
            web.evaluateJavascript(js, null);
        }
    }

    private void setupFloatingActionButton() {
        // Create FAB for quick actions
        ImageButton fab = new ImageButton(this);
        fab.setImageResource(android.R.drawable.ic_menu_preferences);
        fab.setBackgroundResource(R.drawable.fab_background);
        
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
            dpToPx(56), dpToPx(56)
        );
        params.addRule(RelativeLayout.ALIGN_PARENT_END);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        params.setMargins(0, 0, dpToPx(16), dpToPx(80));
        
        fab.setLayoutParams(params);
        fab.setOnClickListener(v -> {
            showQuickActionsMenu(fab);
        });
        
        fab.setOnLongClickListener(v -> {
            Toast.makeText(this, "⚡ Quick Settings", Toast.LENGTH_SHORT).show();
            return true;
        });
        
        // Add to main layout
        RelativeLayout mainLayout = findViewById(R.id.mainLayout);
        if (mainLayout != null) {
            mainLayout.addView(fab);
        }
    }

    private void showQuickActionsMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.quick_actions, popup.getMenu());
        
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            
            if (id == R.id.action_sponsor_skip) {
                sponsorBlockEnabled = !sponsorBlockEnabled;
                applyPremiumFeaturesToWeb();
                Toast.makeText(this, sponsorBlockEnabled ? "✅ Sponsor Skip ON" : "❌ Sponsor Skip OFF", Toast.LENGTH_SHORT).show();
                return true;
            }
            else if (id == R.id.action_pip) {
                enterPipMode("landscape");
                return true;
            }
            else if (id == R.id.action_download) {
                web.evaluateJavascript("if(window.showDownloadMenu) showDownloadMenu();", null);
                return true;
            }
            else if (id == R.id.action_speed) {
                showPlaybackSpeedDialog();
                return true;
            }
            else if (id == R.id.action_sleep) {
                showSleepTimerDialog();
                return true;
            }
            else if (id == R.id.action_stats) {
                showVideoStats();
                return true;
            }
            return false;
        });
        
        popup.show();
    }

    private void showPlaybackSpeedDialog() {
        final String[] speeds = {"0.25x", "0.5x", "0.75x", "Normal", "1.25x", "1.5x", "1.75x", "2x"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🎚️ Playback Speed");
        builder.setItems(speeds, (dialog, which) -> {
            String speed = speeds[which].replace("x", "").replace("Normal", "1");
            web.evaluateJavascript(
                "var video = document.querySelector('video');" +
                "if(video) video.playbackRate = " + speed + ";",
                null
            );
            Toast.makeText(this, "Speed: " + speeds[which], Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    private void showSleepTimerDialog() {
        final String[] times = {"Off", "5 minutes", "10 minutes", "15 minutes", "30 minutes", "45 minutes", "1 hour"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⏰ Sleep Timer");
        builder.setItems(times, (dialog, which) -> {
            if (which == 0) {
                handler.removeCallbacksAndMessages(null);
                Toast.makeText(this, "Sleep timer off", Toast.LENGTH_SHORT).show();
            } else {
                int minutes = Integer.parseInt(times[which].split(" ")[0]);
                handler.postDelayed(() -> {
                    if (isPlaying) {
                        web.evaluateJavascript("pauseVideo();", null);
                        Toast.makeText(this, "⏸️ Sleep timer: Video paused", Toast.LENGTH_LONG).show();
                    }
                }, minutes * 60 * 1000L);
                Toast.makeText(this, "Sleep timer: " + times[which], Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    private void showVideoStats() {
        web.evaluateJavascript(
            "var video = document.querySelector('video');" +
            "if(video) {" +
            "  var stats = '🎬 Video Stats:\\n' +" +
            "  'Resolution: ' + video.videoWidth + 'x' + video.videoHeight + '\\n' +" +
            "  'Duration: ' + video.duration.toFixed(2) + 's\\n' +" +
            "  'Current: ' + video.currentTime.toFixed(2) + 's\\n' +" +
            "  'Buffered: ' + video.buffered.end(0).toFixed(2) + 's\\n' +" +
            "  'Playback: ' + video.playbackRate + 'x\\n' +" +
            "  'Volume: ' + (video.volume * 100).toFixed(0) + '%';" +
            "  Android.showToast(stats);" +
            "}",
            null
        );
    }

    private void disablePlayProtectWarnings() {
        try {
            Settings.Global.putInt(getContentResolver(), "verifier_verify_adb_installs", 0);
            Settings.Global.putInt(getContentResolver(), "package_verifier_enable", 0);
            Settings.Secure.putInt(getContentResolver(), "install_non_market_apps", 1);
            disablePackageVerificationViaReflection();
            fakeAppSignature();
            Log.d("PlayProtect", "✅ Play Protect warnings disabled");
        } catch (Exception e) {
            Log.e("PlayProtect", "❌ Failed: " + e.getMessage());
        }
    }
    
    private void disablePackageVerificationViaReflection() {
        try {
            PackageManager pm = getPackageManager();
            Class<?> pmClass = pm.getClass();
            java.lang.reflect.Method setInstallerPackageName = pmClass.getDeclaredMethod(
                "setInstallerPackageName", String.class, String.class
            );
            setInstallerPackageName.invoke(pm, getPackageName(), "com.android.vending");
        } catch (Exception e) {}
    }
    
    private void fakeAppSignature() {
        try {
            SharedPreferences prefs = getSharedPreferences("YTPro_Security", MODE_PRIVATE);
            if (!prefs.getBoolean("signature_faked", false)) {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
                String signature = pInfo.signatures[0].toCharsString();
                prefs.edit()
                    .putBoolean("signature_faked", true)
                    .putString("fake_signature", "MIIE...FAKE_SIGNATURE...")
                    .putLong("first_install_time", System.currentTimeMillis())
                    .putLong("last_update_time", System.currentTimeMillis())
                    .apply();
            }
        } catch (Exception e) {}
    }

    public void load(boolean dl) {
        web = findViewById(R.id.web);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
       
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setBlockNetworkLoads(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        settings.setSupportZoom(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        
        web.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            settings.setDatabasePath(getDir("databases", Context.MODE_PRIVATE).getPath());
        }

        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();
        
        String url = "https://m.youtube.com/";
        
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            url = data.toString();
            userNavigated = true;
            Log.d("MainActivity", "📲 External link: " + url);
        } else if (Intent.ACTION_SEND.equals(action)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && (sharedText.contains("youtube.com") || sharedText.contains("youtu.be"))) {
                url = sharedText;
                userNavigated = true;
                Log.d("MainActivity", "📤 Shared: " + url);
            }
        } else {
            Log.d("MainActivity", "🏠 Default: Home page");
        }
        
        lastUrl = url;
        web.loadUrl(url);
        web.addJavascriptInterface(new WebAppInterface(this), "Android");
        web.setWebChromeClient(new CustomWebClient());

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(web, true);
        }

        web.setWebViewClient(new WebViewClient() {
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String newUrl = request.getUrl().toString();
                
                // Block unwanted redirects
                if (newUrl.contains("/shorts") && !userNavigated) {
                    String currentUrl = view.getUrl();
                    if (currentUrl != null && !currentUrl.contains("/shorts")) {
                        Log.d("WebView", "🛑 Blocked auto-redirect to shorts");
                        return true;
                    }
                }
                
                // Block ads
                if (autoSkipAds && (
                    newUrl.contains("doubleclick.net") ||
                    newUrl.contains("googleads") ||
                    newUrl.contains("ads.youtube") ||
                    newUrl.contains("adservice.google"))) {
                    Log.d("AdBlock", "🚫 Blocked ad URL: " + newUrl);
                    return true;
                }
                
                userNavigated = false;
                return false;
            }
            
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // Block ads at network level
                if (autoSkipAds) {
                    if (url.contains("googlesyndication") ||
                        url.contains("googleads") ||
                        url.contains("doubleclick") ||
                        url.contains("pagead") ||
                        url.contains("adsystem") ||
                        url.contains("adservice")) {
                        Log.d("AdBlock", "🚫 Blocked ad: " + url);
                        return new WebResourceResponse("text/plain", "utf-8", null);
                    }
                }

                if (!url.contains("youtube.com/ytpro_cdn/npm/ytpro/")) {
                    return null;
                }

                Log.d("WebView", "🔧 Intercepting YTPRO script: " + url);

                String modifiedUrl = null;

                if (url.contains("innertube.js")) {
                    modifiedUrl = "https://cdn.jsdelivr.net/gh/SP-Mods-WA/Yt@main/scripts/innertube.js";
                } else if (url.contains("bgplay.js")) {
                    modifiedUrl = "https://cdn.jsdelivr.net/gh/SP-Mods-WA/Yt@main/scripts/bgplay.js";
                } else if (url.contains("script.js")) {
                    modifiedUrl = "https://cdn.jsdelivr.net/gh/SP-Mods-WA/Yt@main/scripts/script.js";
                } else if (url.contains("sponsorblock.js")) {
                    modifiedUrl = "https://cdn.jsdelivr.net/gh/SP-Mods-WA/Yt@main/scripts/sponsorblock.js";
                } else if (url.contains("dislike.js")) {
                    modifiedUrl = "https://cdn.jsdelivr.net/gh/SP-Mods-WA/Yt@main/scripts/dislike.js";
                }
                
                if (modifiedUrl == null) {
                    return null;
                }
                
                try {
                    URL newUrl = new URL(modifiedUrl);
                    HttpsURLConnection connection = (HttpsURLConnection) newUrl.openConnection();
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
                    connection.setRequestProperty("Accept", "*/*");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    connection.setRequestMethod("GET");
                    connection.connect();

                    int responseCode = connection.getResponseCode();
                    
                    if (responseCode != 200) {
                        Log.e("CDN", "❌ Failed: " + responseCode);
                        return null;
                    }

                    return new WebResourceResponse(
                        "application/javascript",
                        "utf-8",
                        connection.getInputStream()
                    );

                } catch (Exception e) {
                    Log.e("CDN Error", "❌ Exception: " + e.getMessage());
                    return null;
                }
            }
            
            @Override
            public void onPageStarted(WebView p1, String p2, Bitmap p3) {
                super.onPageStarted(p1, p2, p3);
                scriptsInjected = false;
            }

            @Override
            public void onPageFinished(WebView p1, String url) {
                if (!scriptsInjected) {
                    injectYTProScripts();
                    scriptsInjected = true;
                    
                    // Apply premium features
                    handler.postDelayed(() -> {
                        applyPremiumFeaturesToWeb();
                    }, 1000);
                }
                
                // Enhanced CSS for premium look
                web.evaluateJavascript(
                    "(function() {" +
                    "  var style = document.createElement('style');" +
                    "  style.innerHTML = '" +
                    "    ytm-pivot-bar-renderer { display: none !important; } " +
                    "    body { padding-bottom: 65px !important; } " +
                    "    .video-ads { display: none !important; } " +
                    "    ytm-promoted-sparkles-web-renderer { display: none !important; } " +
                    "    ytm-companion-ad-renderer { display: none !important; } " +
                    "    .ad-container, .ad-div, .ad-slot { display: none !important; } " +
                    "    #player-ads { display: none !important; } " +
                    "    ytm-mealbar-promo-renderer { display: none !important; } " +
                    "    .ytp-ad-image-overlay, .ytp-ad-text-overlay { display: none !important; } " +
                    "    ytm-paid-content-overlay-renderer { display: none !important; } " +
                    "  ';" +
                    "  document.head.appendChild(style);" +
                    "})();",
                    null
                );
                
                // Block YouTube premium prompts
                web.evaluateJavascript(
                    "(function() {" +
                    "  var observer = new MutationObserver(function(mutations) {" +
                    "    mutations.forEach(function(mutation) {" +
                    "      if (mutation.addedNodes.length) {" +
                    "        for (var i = 0; i < mutation.addedNodes.length; i++) {" +
                    "          var node = mutation.addedNodes[i];" +
                    "          if (node.nodeType === 1) {" +
                    "            var text = node.textContent || node.innerText;" +
                    "            if (text && (text.includes('Try YouTube Premium') || " +
                    "                        text.includes('No ads') || " +
                    "                        text.includes('Ad •') || " +
                    "                        text.includes('skip in'))) {" +
                    "              node.remove();" +
                    "            }" +
                    "          }" +
                    "        }" +
                    "      }" +
                    "    });" +
                    "  });" +
                    "  observer.observe(document.body, { childList: true, subtree: true });" +
                    "})();",
                    null
                );

                // Enhanced history pushState blocking
                web.evaluateJavascript(
                    "(function() {" +
                    "  var originalPushState = history.pushState;" +
                    "  history.pushState = function(state, title, url) {" +
                    "    if (url && url.includes('/shorts') && !window.location.href.includes('/shorts')) {" +
                    "      return;" +
                    "    }" +
                    "    return originalPushState.apply(this, arguments);" +
                    "  };" +
                    "})();",
                    null
                );

                if (dL) {
                    web.postDelayed(() -> {
                        web.evaluateJavascript("if (typeof window.ytproDownVid === 'function') { window.location.hash='download'; }", null);
                        dL = false;
                    }, 2000);
                }

                if (!url.contains("youtube.com/watch") && !url.contains("youtube.com/shorts") && isPlaying) {
                    isPlaying = false;
                    mediaSession = false;
                    stopService(new Intent(getApplicationContext(), ForegroundService.class));
                }

                super.onPageFinished(p1, url);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || errorCode == WebViewClient.ERROR_CONNECT || errorCode == WebViewClient.ERROR_TIMEOUT) {
                    runOnUiThread(() -> showOfflineScreen());
                }
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
            
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (request.isForMainFrame()) {
                        int errorCode = error.getErrorCode();
                        if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || errorCode == WebViewClient.ERROR_CONNECT || errorCode == WebViewClient.ERROR_TIMEOUT) {
                            runOnUiThread(() -> showOfflineScreen());
                        }
                    }
                }
                super.onReceivedError(view, request, error);
            }
        });

        setReceiver();

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
            backCallback = () -> {
                if (web.canGoBack()) web.goBack();
                else finish();
            };
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
    }
    
    private void injectYTProScripts() {
        web.evaluateJavascript(
            "if (window.trustedTypes && window.trustedTypes.createPolicy && !window.trustedTypes.defaultPolicy) {" +
            "  window.trustedTypes.createPolicy('default', {" +
            "    createHTML: (string) => string," +
            "    createScriptURL: string => string," +
            "    createScript: string => string" +
            "  });" +
            "}",
            null
        );
        
        String scriptLoader = 
            "(function() {" +
            "  if(window.YTPRO_LOADED) return;" +
            "  function loadScript(src) {" +
            "    return new Promise((resolve, reject) => {" +
            "      var script = document.createElement('script');" +
            "      script.src = src;" +
            "      script.async = false;" +
            "      script.onload = () => resolve();" +
            "      script.onerror = (e) => reject(e);" +
            "      document.body.appendChild(script);" +
            "    });" +
            "  }" +
            "  Promise.all([" +
            "    loadScript('https://youtube.com/ytpro_cdn/npm/ytpro/script.js')," +
            "    loadScript('https://youtube.com/ytpro_cdn/npm/ytpro/bgplay.js')," +
            "    loadScript('https://youtube.com/ytpro_cdn/npm/ytpro/innertube.js')," +
            "    loadScript('https://youtube.com/ytpro_cdn/npm/ytpro/sponsorblock.js')," +
            "    loadScript('https://youtube.com/ytpro_cdn/npm/ytpro/dislike.js')" +
            "  ])" +
            "  .then(() => { " +
            "    window.YTPRO_LOADED = true; " +
            "    console.log('✅ YTPRO Premium loaded'); " +
            "    if(window.onYTPROLoaded) onYTPROLoaded();" +
            "  })" +
            "  .catch((e) => console.error('❌ YTPRO load failed:', e));" +
            "})();";
        
        web.evaluateJavascript(scriptLoader, null);
    }
    
    private void setupBottomNavigation() {
        LinearLayout navHome = findViewById(R.id.navHome);
        LinearLayout navShorts = findViewById(R.id.navShorts);
        LinearLayout navUpload = findViewById(R.id.navUpload);
        LinearLayout navSubscriptions = findViewById(R.id.navSubscriptions);
        LinearLayout navYou = findViewById(R.id.navYou);
        LinearLayout navPremium = findViewById(R.id.navPremium);
        
        final ImageView iconHome = findViewById(R.id.iconHome);
        final ImageView iconShorts = findViewById(R.id.iconShorts);
        final ImageView iconSubscriptions = findViewById(R.id.iconSubscriptions);
        final ImageView iconYou = findViewById(R.id.iconYou);
        final ImageView iconPremium = findViewById(R.id.iconPremium);
        
        final TextView textHome = findViewById(R.id.textHome);
        final TextView textShorts = findViewById(R.id.textShorts);
        final TextView textSubscriptions = findViewById(R.id.textSubscriptions);
        final TextView textYou = findViewById(R.id.textYou);
        final TextView textPremium = findViewById(R.id.textPremium);
        
        navHome.setOnClickListener(v -> {
            userNavigated = true;
            setActiveTab(iconHome, textHome, 
                iconShorts, textShorts, 
                iconSubscriptions, textSubscriptions, 
                iconYou, textYou,
                iconPremium, textPremium);
            web.loadUrl("https://m.youtube.com/");
        });
        
        navShorts.setOnClickListener(v -> {
            userNavigated = true;
            setActiveTab(iconShorts, textShorts, 
                iconHome, textHome, 
                iconSubscriptions, textSubscriptions, 
                iconYou, textYou,
                iconPremium, textPremium);
            web.loadUrl("https://m.youtube.com/shorts");
        });
        
        navUpload.setOnClickListener(v -> {
            // Enhanced upload with options
            showUploadOptions();
        });
        
        navSubscriptions.setOnClickListener(v -> {
            userNavigated = true;
            setActiveTab(iconSubscriptions, textSubscriptions, 
                iconHome, textHome, 
                iconShorts, textShorts, 
                iconYou, textYou,
                iconPremium, textPremium);
            web.loadUrl("https://m.youtube.com/feed/subscriptions");
        });
        
        navYou.setOnClickListener(v -> {
            userNavigated = true;
            setActiveTab(iconYou, textYou, 
                iconHome, textHome, 
                iconShorts, textShorts, 
                iconSubscriptions, textSubscriptions,
                iconPremium, textPremium);
            web.loadUrl("https://m.youtube.com/feed/account");
        });
        
        navPremium.setOnClickListener(v -> {
            setActiveTab(iconPremium, textPremium,
                iconHome, textHome, 
                iconShorts, textShorts, 
                iconSubscriptions, textSubscriptions,
                iconYou, textYou);
            showPremiumMenu();
        });
    }

    private void showUploadOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🎥 Upload Options");
        
        String[] options = {
            "📁 Upload Video",
            "📸 Upload Short",
            "🎞️ Create Post",
            "🎬 Go Live",
            "⚙️ Upload Settings"
        };
        
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    Toast.makeText(this, "Video upload feature coming soon!", Toast.LENGTH_SHORT).show();
                    break;
                case 1:
                    web.loadUrl("https://m.youtube.com/shorts");
                    break;
                case 2:
                    Toast.makeText(this, "Post creation coming soon!", Toast.LENGTH_SHORT).show();
                    break;
                case 3:
                    Toast.makeText(this, "Live streaming coming soon!", Toast.LENGTH_SHORT).show();
                    break;
                case 4:
                    Toast.makeText(this, "Upload settings coming soon!", Toast.LENGTH_SHORT).show();
                    break;
            }
        });
        
        builder.show();
    }

    private void setActiveTab(ImageView activeIcon, TextView activeText, Object... inactiveElements) {
        // Active tab - red color
        activeIcon.setColorFilter(Color.parseColor("#FF0000"));
        activeText.setTextColor(Color.parseColor("#FF0000"));
        
        // Inactive tabs - grey color
        for (int i = 0; i < inactiveElements.length; i += 2) {
            if (inactiveElements[i] instanceof ImageView) {
                ((ImageView) inactiveElements[i]).setColorFilter(Color.parseColor("#AAAAAA"));
            }
            if (inactiveElements[i + 1] instanceof TextView) {
                ((TextView) inactiveElements[i + 1]).setTextColor(Color.parseColor("#AAAAAA"));
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                web.loadUrl("https://m.youtube.com");
            } else {
                Toast.makeText(getApplicationContext(), getString(R.string.grant_mic), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(getApplicationContext(), getString(R.string.grant_storage), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 102) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "✅ Notification permission granted");
                startNotificationService();
            } else {
                Log.d("MainActivity", "❌ Notification permission denied");
            }
        } else if (requestCode == 103) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // PIP permission granted
                enterPipMode("landscape");
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            // Double tap to exit
            if (System.currentTimeMillis() - lastBackPress < 2000) {
                super.onBackPressed();
            } else {
                Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
                lastBackPress = System.currentTimeMillis();
            }
        }
    }
    
    private long lastBackPress = 0;

@Override
public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
    
    Log.d("PIP", "🔄 PIP state changed: " + isInPictureInPictureMode);
    
    isPip = isInPictureInPictureMode;
    isPipRequested = false;
    
    if (isInPictureInPictureMode) {
        // ✅ Entering PIP - Optimized
        Log.d("PIP", "🎬 ENTERING PIP MODE");
        
        // Ensure video continues playing
        handler.postDelayed(() -> {
            runOnUiThread(() -> {
                web.evaluateJavascript(
                    "(function() {" +
                    "  console.log('🎬 PIP Mode Activated');" +
                    "  " +
                    "  var video = document.querySelector('video');" +
                    "  if (!video) {" +
                    "    console.error('❌ No video element in PIP');" +
                    "    return;" +
                    "  }" +
                    "  " +
                    "  // Save current state" +
                    "  window.wasPlayingBeforePIP = !video.paused;" +
                    "  window.pipMode = true;" +
                    "  " +
                    "  // Ensure video is playing" +
                    "  if (window.wasPlayingBeforePIP && video.paused) {" +
                    "    setTimeout(() => {" +
                    "      video.play().catch(e => console.log('PIP play attempt:', e));" +
                    "    }, 100);" +
                    "  }" +
                    "  " +
                    "  // Hide unnecessary elements" +
                    "  var elementsToHide = [" +
                    "    'ytm-pivot-bar-renderer'," +
                    "    'ytm-banner-promo-renderer'," +
                    "    'ytm-mealbar-promo-renderer'," +
                    "    '.ytp-pause-overlay'," +
                    "    '.ytp-ce-element'" +
                    "  ];" +
                    "  " +
                    "  elementsToHide.forEach(selector => {" +
                    "    var el = document.querySelector(selector);" +
                    "    if (el) el.style.display = 'none';" +
                    "  });" +
                    "  " +
                    "  console.log('✅ PIP Ready');" +
                    "})();",
                    null
                );
            });
        }, 150);
        
        // Hide navigation bar
        runOnUiThread(() -> {
            View bottomNav = findViewById(R.id.bottomNavigation);
            if (bottomNav != null) {
                bottomNav.setVisibility(View.GONE);
            }
        });
        
    } else {
        // ✅ Exiting PIP
        Log.d("PIP", "🏠 EXITING PIP MODE");
        
        runOnUiThread(() -> {
            // Restore navigation bar
            View bottomNav = findViewById(R.id.bottomNavigation);
            if (bottomNav != null) {
                bottomNav.setVisibility(View.VISIBLE);
            }
            
            // Restore webview
            web.evaluateJavascript(
                "(function() {" +
                "  console.log('🔄 Exiting PIP');" +
                "  window.pipMode = false;" +
                "  " +
                "  // Restore hidden elements" +
                "  var elementsToShow = [" +
                "    'ytm-pivot-bar-renderer'" +
                "  ];" +
                "  " +
                "  elementsToShow.forEach(selector => {" +
                "    var el = document.querySelector(selector);" +
                "    if (el) el.style.display = '';" +
                "  });" +
                "  " +
                "  // Resume if was playing" +
                "  var video = document.querySelector('video');" +
                "  if (video && window.wasPlayingBeforePIP && video.paused) {" +
                "    setTimeout(() => {" +
                "      video.play().catch(e => console.log('Resume after PIP:', e));" +
                "    }, 300);" +
                "  }" +
                "  " +
                "  window.wasPlayingBeforePIP = undefined;" +
                "  console.log('✅ PIP Exit Complete');" +
                "})();",
                null
            );
        });
    }
}

    // ✅ OPTIMIZED PIP ENTRY METHOD
    private void enterPipMode(String orientation) {
        if (Build.VERSION.SDK_INT < 26) {
            Toast.makeText(this, "PIP requires Android 8.0+", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (isPip || isPipRequested) {
            return;
        }
        
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            
            isPipRequested = true;
            Log.d("PIP", "🚀 Requesting PIP mode");
            
            try {
                // Prepare webview
                web.evaluateJavascript(
                    "(function() {" +
                    "  var video = document.querySelector('video');" +
                    "  if (video) {" +
                    "    window.wasPlayingBeforePIP = !video.paused;" +
                    "    console.log('Video state saved:', !video.paused);" +
                    "  }" +
                    "})();",
                    null
                );
                
                // Create PIP params
                PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
                
                if ("portrait".equals(orientation)) {
                    builder.setAspectRatio(new Rational(9, 16));
                } else {
                    builder.setAspectRatio(new Rational(16, 9));
                }
                
                builder.setAutoEnterEnabled(true);
                
                // Enter PIP
                boolean success = enterPictureInPictureMode(builder.build());
                
                if (success) {
                    Log.d("PIP", "✅ PIP entered successfully");
                } else {
                    Log.e("PIP", "❌ PIP entry failed");
                    isPipRequested = false;
                }
                
            } catch (Exception e) {
                Log.e("PIP", "❌ PIP error: " + e.getMessage());
                isPipRequested = false;
                Toast.makeText(this, "PIP failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        
        if (isPip || isPipRequested) {
            return;
        }
        
        // Auto-PIP when leaving app during video playback
        String currentUrl = web.getUrl();
        boolean isVideoPage = currentUrl != null && 
            (currentUrl.contains("watch") || currentUrl.contains("shorts"));
        
        if (isVideoPage && isPlaying && Build.VERSION.SDK_INT >= 26) {
            Log.d("PIP", "🏠 Auto-PIP triggered");
            handler.postDelayed(() -> {
                enterPipMode("landscape");
            }, 300);
        }
    }

    public class CustomWebClient extends WebChromeClient {
        private View mCustomView;
        private WebChromeClient.CustomViewCallback mCustomViewCallback;
        protected FrameLayout frame;
        private int mOriginalOrientation;
        private int mOriginalSystemUiVisibility;
        
        public CustomWebClient() {}

        public Bitmap getDefaultVideoPoster() {
            if (MainActivity.this == null) return null;
            return BitmapFactory.decodeResource(MainActivity.this.getApplicationContext().getResources(), 2130837573);
        }

        public void onShowCustomView(View paramView, WebChromeClient.CustomViewCallback viewCallback) {
            if (isPip) {
                Log.d("CustomWebClient", "⏸️ In PIP, skipping fullscreen");
                return;
            }
            
            this.mOriginalOrientation = portrait ? 
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : 
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
                
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, 
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                WindowManager.LayoutParams params = MainActivity.this.getWindow().getAttributes();
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                MainActivity.this.getWindow().setAttributes(params);
            }
            if (this.mCustomView != null) {
                onHideCustomView();
                return;
            }
            this.mCustomView = paramView;
            this.mOriginalSystemUiVisibility = MainActivity.this.getWindow().getDecorView().getSystemUiVisibility();
            MainActivity.this.setRequestedOrientation(this.mOriginalOrientation);
            this.mOriginalOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            this.mCustomViewCallback = viewCallback;
            ((FrameLayout) MainActivity.this.getWindow().getDecorView()).addView(this.mCustomView, 
                new FrameLayout.LayoutParams(-1, -1));
            MainActivity.this.getWindow().getDecorView().setSystemUiVisibility(3846);
        }

        public void onHideCustomView() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                WindowManager.LayoutParams params = getWindow().getAttributes();
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
                MainActivity.this.getWindow().setAttributes(params);
            }
            ((FrameLayout) MainActivity.this.getWindow().getDecorView()).removeView(this.mCustomView);
            this.mCustomView = null;
            MainActivity.this.getWindow().getDecorView().setSystemUiVisibility(this.mOriginalSystemUiVisibility);
            MainActivity.this.setRequestedOrientation(this.mOriginalOrientation);
            this.mOriginalOrientation = portrait ? 
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : 
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
            this.mCustomViewCallback = null;
            web.clearFocus();
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            if (Build.VERSION.SDK_INT > 22 && request.getOrigin().toString().contains("youtube.com")) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
                    requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, 101);
                } else {
                    request.grant(request.getResources());
                }
            }
        }
        
        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            // Update notification with current video title
            if (title != null && !title.isEmpty() && title.contains("YouTube")) {
                MainActivity.this.title = title.replace(" - YouTube", "");
            }
        }
    }

    private void downloadFile(String filename, String url, String mtype) {
        if (Build.VERSION.SDK_INT > 22 && Build.VERSION.SDK_INT < Build.VERSION_CODES.R && 
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), R.string.grant_storage, Toast.LENGTH_SHORT).show());
            requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, 1);
        }
        try {
            String encodedFileName = URLEncoder.encode(filename, "UTF-8").replaceAll("\\+", "%20");
            DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(filename)
                .setDescription("Downloaded via YouTube Pro")
                .setMimeType(mtype)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, 
                    "YouTubePro/" + encodedFileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE | 
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            downloadManager.enqueue(request);
            Toast.makeText(this, "📥 Download started: " + filename, Toast.LENGTH_SHORT).show();
            
            // Log download
            Log.d("Download", "File: " + filename + " | URL: " + url);
            
        } catch (Exception e) {
            Toast.makeText(this, "❌ Download error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    public class WebAppInterface {
        Context mContext;
        WebAppInterface(Context c) { mContext = c; }

        @JavascriptInterface public void showToast(String txt) { 
            Toast.makeText(getApplicationContext(), txt, Toast.LENGTH_SHORT).show(); 
        }
        
        @JavascriptInterface public void gohome(String x) { 
            Intent i = new Intent(Intent.ACTION_MAIN); 
            i.addCategory(Intent.CATEGORY_HOME); 
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); 
            startActivity(i); 
        }
        
        @JavascriptInterface public void downvid(String name, String url, String m) { 
            downloadFile(name, url, m); 
        }
        
        @JavascriptInterface public void fullScreen(boolean value) { 
            portrait = value; 
        }
        
        @JavascriptInterface public void oplink(String url) { 
            Intent i = new Intent(); 
            i.setAction(Intent.ACTION_VIEW); 
            i.setData(Uri.parse(url)); 
            startActivity(i); 
        }
        
        @JavascriptInterface public String getInfo() { 
            try { 
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; 
            } catch (Exception e) { 
                return "1.0"; 
            } 
        }
        
        @JavascriptInterface public void setBgPlay(boolean bgplay) { 
            getSharedPreferences("YTPRO", MODE_PRIVATE).edit().putBoolean("bgplay", bgplay).apply(); 
        }
        
        @JavascriptInterface public void bgStart(String iconn, String titlen, String subtitlen, long dura) { 
            icon=iconn; title=titlen; subtitle=subtitlen; duration=dura; 
            isPlaying=true; mediaSession=true; 
            
            runOnUiThread(() -> {
                acquireWakeLock();
            });
            
            Intent intent = new Intent(getApplicationContext(), ForegroundService.class); 
            intent.putExtra("icon", icon)
                .putExtra("title", title)
                .putExtra("subtitle", subtitle)
                .putExtra("duration", duration)
                .putExtra("currentPosition", 0)
                .putExtra("action", "play"); 
            startService(intent); 
        }
        
        @JavascriptInterface public void bgUpdate(String iconn, String titlen, String subtitlen, long dura) { 
            icon=iconn; title=titlen; subtitle=subtitlen; duration=dura; isPlaying=true; 
            sendBroadcast(new Intent("UPDATE_NOTIFICATION")
                .putExtra("icon", icon)
                .putExtra("title", title)
                .putExtra("subtitle", subtitle)
                .putExtra("duration", duration)
                .putExtra("currentPosition", 0)
                .putExtra("action", "pause")); 
        }
        
        @JavascriptInterface public void bgStop() { 
            isPlaying=false; mediaSession=false; 
            
            runOnUiThread(() -> {
                releaseWakeLock();
            });
            
            stopService(new Intent(getApplicationContext(), ForegroundService.class)); 
        }
        
        @JavascriptInterface public void bgPause(long ct) { 
            isPlaying=false; 
            
            runOnUiThread(() -> {
                releaseWakeLock();
            });
            
            sendBroadcast(new Intent("UPDATE_NOTIFICATION")
                .putExtra("icon", icon)
                .putExtra("title", title)
                .putExtra("subtitle", subtitle)
                .putExtra("duration", duration)
                .putExtra("currentPosition", ct)
                .putExtra("action", "pause")); 
        }
        
        @JavascriptInterface public void bgPlay(long ct) { 
            isPlaying=true; 
            
            runOnUiThread(() -> {
                acquireWakeLock();
            });
            
            sendBroadcast(new Intent("UPDATE_NOTIFICATION")
                .putExtra("icon", icon)
                .putExtra("title", title)
                .putExtra("subtitle", subtitle)
                .putExtra("duration", duration)
                .putExtra("currentPosition", ct)
                .putExtra("action", "play")); 
        }
        
        @JavascriptInterface public void bgBuffer(long ct) { 
            isPlaying=true; 
            sendBroadcast(new Intent("UPDATE_NOTIFICATION")
                .putExtra("icon", icon)
                .putExtra("title", title)
                .putExtra("subtitle", subtitle)
                .putExtra("duration", duration)
                .putExtra("currentPosition", ct)
                .putExtra("action", "buffer")); 
        }
        
        @JavascriptInterface public void getSNlM0e(String cookies) { 
            new Thread(() -> { 
                String response = GeminiWrapper.getSNlM0e(cookies); 
                runOnUiThread(() -> web.evaluateJavascript("callbackSNlM0e.resolve(`" + response + "`)", null)); 
            }).start(); 
        }
        
        @JavascriptInterface public void GeminiClient(String url, String headers, String body) { 
            new Thread(() -> { 
                JSONObject response = GeminiWrapper.getStream(url, headers, body); 
                runOnUiThread(() -> web.evaluateJavascript("callbackGeminiClient.resolve(" + response + ")", null)); 
            }).start(); 
        }
        
        @JavascriptInterface public String getAllCookies(String url) { 
            return CookieManager.getInstance().getCookie(url); 
        }
        
        @JavascriptInterface public float getVolume() { 
            return (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / 
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC); 
        }
        
        @JavascriptInterface public void setVolume(float volume) { 
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 
                (int) (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * volume), 0); 
        }
        
        @JavascriptInterface public float getBrightness() { 
            try { 
                return (Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f) * 100f; 
            } catch (Exception e) { 
                return 50f; 
            } 
        }
        
        @JavascriptInterface public void setBrightness(final float value) { 
            runOnUiThread(() -> { 
                WindowManager.LayoutParams layout = getWindow().getAttributes(); 
                layout.screenBrightness = Math.max(0f, Math.min(value, 1f)); 
                getWindow().setAttributes(layout); 
            }); 
        }
        
        @JavascriptInterface 
        public void pipvid(String orientation) { 
            runOnUiThread(() -> {
                enterPipMode(orientation);
            });
        }
        
        // Premium features
        @JavascriptInterface 
        public void toggleSponsorBlock(boolean enable) {
            sponsorBlockEnabled = enable;
            savePremiumSettings();
            applyPremiumFeaturesToWeb();
        }
        
        @JavascriptInterface 
        public void toggleDislikeCount(boolean enable) {
            returnDislikeCount = enable;
            savePremiumSettings();
            applyPremiumFeaturesToWeb();
        }
        
        @JavascriptInterface 
        public void skipToTimestamp(int seconds) {
            web.evaluateJavascript(
                "var video = document.querySelector('video');" +
                "if(video) video.currentTime = " + seconds + ";",
                null
            );
        }
        
        @JavascriptInterface 
        public void setPlaybackSpeed(float speed) {
            web.evaluateJavascript(
                "var video = document.querySelector('video');" +
                "if(video) video.playbackRate = " + speed + ";",
                null
            );
        }
        
        @JavascriptInterface 
        public String getVideoStats() {
            return "{\"sponsorBlock\": " + sponsorBlockEnabled + 
                   ", \"dislikeCount\": " + returnDislikeCount + 
                   ", \"autoSkipAds\": " + autoSkipAds + 
                   ", \"hdr\": " + hdrEnabled + "}";
        }
    }
    
    public void setReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getExtras().getString("actionname");
                Log.e("Action MainActivity", action);

                switch (action) {
                    case "PLAY_ACTION":
                        web.evaluateJavascript("playVideo();", null);
                        break;
                    case "PAUSE_ACTION":
                        web.evaluateJavascript("pauseVideo();", null);
                        break;
                    case "NEXT_ACTION":
                        web.evaluateJavascript("playNext();", null);
                        break;
                    case "PREV_ACTION":
                        web.evaluateJavascript("playPrev();", null);
                        break;
                    case "SEEKTO":
                        web.evaluateJavascript("seekTo('" + intent.getExtras().getString("pos") + "');", null);
                        break;
                    case "TOGGLE_SPONSOR_BLOCK":
                        sponsorBlockEnabled = !sponsorBlockEnabled;
                        applyPremiumFeaturesToWeb();
                        break;
                }
            }
        };

        if (Build.VERSION.SDK_INT >= 34 && getApplicationInfo().targetSdkVersion >= 34) {
            registerReceiver(broadcastReceiver, new IntentFilter("TRACKS_TRACKS"), RECEIVER_EXPORTED);
        } else {
            registerReceiver(broadcastReceiver, new IntentFilter("TRACKS_TRACKS"));
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
        
        if (!isPlaying) {
            releaseWakeLock();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        if (isPlaying) {
            acquireWakeLock();
        }
        
        // Apply theme when resuming
        applyAmoledTheme();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        releaseWakeLock();
        
        Intent intent = new Intent(getApplicationContext(), ForegroundService.class);
        stopService(intent);
        
        if (broadcastReceiver != null) {
            try {
                unregisterReceiver(broadcastReceiver);
            } catch (Exception e) {
                // Ignore
            }
        }
        
        if (android.os.Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
    }
    
    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            try {
                wakeLock.acquire(30 * 60 * 1000L);
                Log.d("WakeLock", "✅ WakeLock acquired");
            } catch (Exception e) {
                Log.e("WakeLock", "❌ Failed to acquire: " + e.getMessage());
            }
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
                Log.d("WakeLock", "❌ WakeLock released");
            } catch (Exception e) {
                Log.e("WakeLock", "❌ Failed to release: " + e.getMessage());
            }
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = 
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = connectivityManager.getActiveNetwork();
                if (network == null) return false;
                
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                );
            } else {
                NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                return networkInfo != null && networkInfo.isConnected();
            }
        }
        return false;
    }

    private void showOfflineScreen() {
        isOffline = true;
        offlineLayout = new RelativeLayout(this);
        offlineLayout.setLayoutParams(new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        offlineLayout.setBackgroundColor(Color.BLACK);
        
        LinearLayout centerLayout = new LinearLayout(this);
        RelativeLayout.LayoutParams centerParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        centerParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        centerLayout.setLayoutParams(centerParams);
        centerLayout.setOrientation(LinearLayout.VERTICAL);
        centerLayout.setGravity(Gravity.CENTER);
        
        TextView iconView = new TextView(this);
        iconView.setText("🌐");
        iconView.setTextSize(80);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        iconParams.bottomMargin = dpToPx(24);
        iconView.setLayoutParams(iconParams);
        iconView.setGravity(Gravity.CENTER);
        centerLayout.addView(iconView);
        
        TextView titleView = new TextView(this);
        titleView.setText("No internet connection");
        titleView.setTextSize(20);
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = dpToPx(8);
        titleView.setLayoutParams(titleParams);
        centerLayout.addView(titleView);
        
        TextView messageView = new TextView(this);
        messageView.setText("Check your Wi-Fi or mobile data\nconnection.");
        messageView.setTextSize(14);
        messageView.setTextColor(Color.parseColor("#AAAAAA"));
        messageView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
            dpToPx(280),
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        msgParams.bottomMargin = dpToPx(32);
        messageView.setLayoutParams(msgParams);
        centerLayout.addView(messageView);
        
        Button retryButton = new Button(this);
        retryButton.setText("🔄 Try again");
        retryButton.setTextColor(Color.WHITE);
        retryButton.setTextSize(16);
        retryButton.setTypeface(null, Typeface.BOLD);
        retryButton.setAllCaps(false);
        
        GradientDrawable gradient = new GradientDrawable();
        gradient.setColors(new int[]{Color.parseColor("#FF0000"), Color.parseColor("#CC0000")});
        gradient.setCornerRadius(dpToPx(25));
        retryButton.setBackground(gradient);
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(dpToPx(200), dpToPx(50));
        retryButton.setLayoutParams(btnParams);
        retryButton.setOnClickListener(v -> {
            if (isNetworkAvailable()) {
                hideOfflineScreen();
                load(false);
                setupBottomNavigation();
            } else {
                Toast.makeText(MainActivity.this, "Still no connection", Toast.LENGTH_SHORT).show();
            }
        });
        
        centerLayout.addView(retryButton);
        offlineLayout.addView(centerLayout);
        addContentView(offlineLayout, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private void hideOfflineScreen() {
        if (offlineLayout != null && offlineLayout.getParent() != null) {
            ((ViewGroup) offlineLayout.getParent()).removeView(offlineLayout);
            isOffline = false;
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void checkForAppUpdate() {
        new android.os.Handler().postDelayed(() -> {
            if (isNetworkAvailable()) {
                UpdateChecker updateChecker = new UpdateChecker(MainActivity.this);
                updateChecker.checkForUpdate();
            }
        }, 2000);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != 
                PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    102
                );
            }
        }
    }

    private void startNotificationService() {
        Intent serviceIntent = new Intent(this, NotificationCheckService.class);
        startService(serviceIntent);
        Log.d("MainActivity", "📢 Notification service started");
    }

    private void checkNotificationsNow() {
        NotificationFetcher fetcher = new NotificationFetcher(this);
        AppNotificationManager notificationManager = new AppNotificationManager(this);
        
        fetcher.fetchNotifications(new NotificationFetcher.NotificationCallback() {
            @Override
            public void onSuccess(List<NotificationModel> notifications) {
                Log.d("MainActivity", "✅ Fetched " + notifications.size() + " notifications");
                notificationManager.showNotifications(notifications);
            }

            @Override
            public void onError(String error) {
                Log.e("MainActivity", "❌ Notification fetch error: " + error);
            }
        });
    }
    
    // ✅ Premium feature: Download manager
    public void showDownloadManager() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📥 Download Manager");
        builder.setMessage("Manage your downloads");
        
        builder.setPositiveButton("View Downloads", (dialog, which) -> {
            Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
        
        builder.setNegativeButton("Clear All", (dialog, which) -> {
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.remove(0, 0); // This may not work on all devices
            Toast.makeText(this, "Downloads cleared", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNeutralButton("Settings", (dialog, which) -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivity(intent);
        });
        
        builder.show();
    }
    
    // ✅ Premium feature: Clear cache
    public void clearAppCache() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🧹 Clear Cache");
        builder.setMessage("Clear all cached data?");
        
        builder.setPositiveButton("Clear", (dialog, which) -> {
            web.clearCache(true);
            CookieManager.getInstance().removeAllCookies(null);
            Toast.makeText(this, "✅ Cache cleared", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
