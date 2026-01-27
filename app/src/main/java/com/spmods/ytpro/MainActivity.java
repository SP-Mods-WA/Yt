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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
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
                
                if (newUrl.contains("/shorts") && !userNavigated) {
                    String currentUrl = view.getUrl();
                    if (currentUrl != null && !currentUrl.contains("/shorts")) {
                        Log.d("WebView", "🛑 Blocked auto-redirect to shorts");
                        return true;
                    }
                }
                
                userNavigated = false;
                return false;
            }
            
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

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
                }
                
                web.evaluateJavascript(
                    "(function() {" +
                    "  var style = document.createElement('style');" +
                    "  style.innerHTML = 'ytm-pivot-bar-renderer { display: none !important; } body { padding-bottom: 65px !important; }';" +
                    "  document.head.appendChild(style);" +
                    "})();",
                    null
                );
                
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
            "    loadScript('https://youtube.com/ytpro_cdn/npm/ytpro/innertube.js')" +
            "  ])" +
            "  .then(() => { window.YTPRO_LOADED = true; console.log('✅ YTPRO loaded'); })" +
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
        
        final ImageView iconHome = findViewById(R.id.iconHome);
        final ImageView iconShorts = findViewById(R.id.iconShorts);
        final ImageView iconSubscriptions = findViewById(R.id.iconSubscriptions);
        final ImageView iconYou = findViewById(R.id.iconYou);
        
        final TextView textHome = findViewById(R.id.textHome);
        final TextView textShorts = findViewById(R.id.textShorts);
        final TextView textSubscriptions = findViewById(R.id.textSubscriptions);
        final TextView textYou = findViewById(R.id.textYou);
        
        navHome.setOnClickListener(v -> {
            userNavigated = true;
            setActiveTab(iconHome, textHome, iconShorts, textShorts, iconSubscriptions, textSubscriptions, iconYou, textYou);
            web.loadUrl("https://m.youtube.com/");
        });
        
        navShorts.setOnClickListener(v -> {
            userNavigated = true;
            setActiveTab(iconShorts, textShorts, iconHome, textHome, iconSubscriptions, textSubscriptions, iconYou, textYou);
            web.loadUrl("https://m.youtube.com/shorts");
        });
        
        navUpload.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Upload feature coming soon! 🎥", Toast.LENGTH_SHORT).show();
        });
        
        navSubscriptions.setOnClickListener(v -> {
            userNavigated = true;
            setActiveTab(iconSubscriptions, textSubscriptions, iconHome, textHome, iconShorts, textShorts, iconYou, textYou);
            web.loadUrl("https://m.youtube.com/feed/subscriptions");
        });
        
        navYou.setOnClickListener(v -> {
            userNavigated = true;
            setActiveTab(iconYou, textYou, iconHome, textHome, iconShorts, textShorts, iconSubscriptions, textSubscriptions);
            web.loadUrl("https://m.youtube.com/feed/account");
        });
    }

    private void setActiveTab(ImageView activeIcon, TextView activeText, Object... inactiveElements) {
        activeIcon.setColorFilter(Color.parseColor("#FF0000"));
        activeText.setTextColor(Color.WHITE);
        
        for (Object element : inactiveElements) {
            if (element instanceof ImageView) {
                ((ImageView) element).setColorFilter(Color.parseColor("#AAAAAA"));
            } else if (element instanceof TextView) {
                ((TextView) element).setTextColor(Color.parseColor("#AAAAAA"));
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
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            finish();
        }
    }

@Override
public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
    
    Log.d("PIP", "🔄 PIP state: " + isInPictureInPictureMode);
    
    isPip = isInPictureInPictureMode;
    isPipRequested = false;
    
    if (isInPictureInPictureMode) {
        Log.d("PIP", "🎬 ENTERING PIP");
        
        if (isPlaying && wakeLock != null && !wakeLock.isHeld()) {
            try {
                wakeLock.acquire(10 * 60 * 1000L);
                Log.d("WakeLock", "✅ Acquired for PIP");
            } catch (Exception e) {
                Log.e("WakeLock", "❌ Failed: " + e.getMessage());
            }
        }
        
        handler.postDelayed(() -> {
            runOnUiThread(() -> {
                web.evaluateJavascript(
                    "(function() {" +
                    "  console.log('🎬 PIP: Setting up video...');" +
                    "  " +
                    "  var video = document.querySelector('video');" +
                    "  if (!video) {" +
                    "    console.log('❌ No video element found');" +
                    "    return;" +
                    "  }" +
                    "  " +
                    "  window.wasPlayingBeforePIP = !video.paused;" +
                    "  window.pipMode = true;" +
                    "  console.log('📼 Was playing:', window.wasPlayingBeforePIP);" +
                    "  " +
                    "  video.setAttribute('playsinline', 'true');" +
                    "  video.muted = false;" +
                    "  " +
                    "  ['ytm-pivot-bar-renderer', 'ytm-mobile-topbar-renderer'].forEach(sel => {" +
                    "    var el = document.querySelector(sel);" +
                    "    if (el) el.style.display = 'none';" +
                    "  });" +
                    "  " +
                    "  video.style.position = 'fixed';" +
                    "  video.style.top = '0';" +
                    "  video.style.left = '0';" +
                    "  video.style.width = '100%';" +
                    "  video.style.height = '100%';" +
                    "  video.style.zIndex = '999999';" +
                    "  video.style.objectFit = 'contain';" +
                    "  video.style.backgroundColor = '#000';" +
                    "  " +
                    "  if (window.wasPlayingBeforePIP && video.paused) {" +
                    "    console.log('🔄 Resuming playback...');" +
                    "    video.play().then(() => {" +
                    "      console.log('✅ Video resumed in PIP');" +
                    "    }).catch(e => {" +
                    "      console.log('⚠️ Resume failed:', e);" +
                    "    });" +
                    "  } else {" +
                    "    console.log('⏸️ Video was paused, keeping paused');" +
                    "  }" +
                    "  " +
                    "  var userPaused = false;" +
                    "  var lastPauseTime = 0;" +
                    "  " +
                    "  video.addEventListener('pause', function(e) {" +
                    "    var now = Date.now();" +
                    "    if (now - lastPauseTime > 500) {" +
                    "      userPaused = true;" +
                    "      window.wasPlayingBeforePIP = false;" +
                    "      console.log('⏸️ User paused video');" +
                    "    }" +
                    "    lastPauseTime = now;" +
                    "  });" +
                    "  " +
                    "  video.addEventListener('play', function(e) {" +
                    "    userPaused = false;" +
                    "    window.wasPlayingBeforePIP = true;" +
                    "    console.log('▶️ User played video');" +
                    "  });" +
                    "  " +
                    "  window.pipPlayInterval = setInterval(() => {" +
                    "    if (!userPaused && window.wasPlayingBeforePIP && video.paused) {" +
                    "      var timeSincePause = Date.now() - lastPauseTime;" +
                    "      if (timeSincePause > 2000) {" +
                    "        console.log('⚠️ Unexpected pause detected, recovering...');" +
                    "        video.play().catch(e => console.log('Recovery failed:', e));" +
                    "      }" +
                    "    }" +
                    "  }, 3000);" +
                    "  " +
                    "  console.log('✅ PIP setup complete with user controls');" +
                    "})();",
                    null
                );
            });
        }, 300);
        
    } else {
        Log.d("PIP", "🏠 EXITING PIP");
        
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                Log.d("WakeLock", "❌ Released");
            }
        } catch (Exception e) {
            Log.e("WakeLock", "❌ Release error: " + e.getMessage());
        }
        
        runOnUiThread(() -> {
            web.evaluateJavascript(
                "(function() {" +
                "  console.log('🔄 Restoring UI after PIP...');" +
                "  " +
                "  window.pipMode = false;" +
                "  " +
                "  if (window.pipPlayInterval) {" +
                "    clearInterval(window.pipPlayInterval);" +
                "    window.pipPlayInterval = null;" +
                "  }" +
                "  " +
                "  ['ytm-pivot-bar-renderer', 'ytm-mobile-topbar-renderer'].forEach(sel => {" +
                "    var el = document.querySelector(sel);" +
                "    if (el) el.style.display = '';" +
                "  });" +
                "  " +
                "  var video = document.querySelector('video');" +
                "  if (video) {" +
                "    video.style.position = '';" +
                "    video.style.top = '';" +
                "    video.style.left = '';" +
                "    video.style.width = '';" +
                "    video.style.height = '';" +
                "    video.style.zIndex = '';" +
                "    video.style.objectFit = '';" +
                "    video.style.backgroundColor = '';" +
                "    " +
                "    if (window.wasPlayingBeforePIP && video.paused) {" +
                "      setTimeout(() => {" +
                "        video.play().catch(e => {" +
                "          console.log('Auto-resume failed, trying button click');" +
                "          var playBtn = document.querySelector('.ytp-play-button');" +
                "          if (playBtn) playBtn.click();" +
                "        });" +
                "      }, 500);" +
                "    }" +
                "  }" +
                "  " +
                "  window.wasPlayingBeforePIP = undefined;" +
                "  console.log('✅ UI restored');" +
                "})();",
                null
            );
            
            handler.postDelayed(() -> {
                web.requestLayout();
                web.invalidate();
            }, 300);
        });
    }
}


    // ✅ UPDATED USER LEAVE HINT METHOD
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        
        Log.d("PIP", "🏠 User leaving app");
        
        // ✅ Check conditions දැන්ම
        if (isPip || isPipRequested) {
            Log.d("PIP", "⏸️ Already in PIP or requested");
            return;
        }
        
        // ✅ Check Android version
        if (android.os.Build.VERSION.SDK_INT < 26) {
            Log.d("PIP", "❌ Android 8.0+ required");
            return;
        }
        
        // ✅ Check if video is playing
        String currentUrl = web.getUrl();
        boolean isVideoPage = currentUrl != null && 
            (currentUrl.contains("watch") || currentUrl.contains("shorts"));
        
        if (!isVideoPage || !isPlaying) {
            Log.d("PIP", "⏸️ Not a video page or not playing");
            return;
        }
        
        // ✅ Try to enter PIP
        Log.d("PIP", "🚀 Entering PIP...");
        isPipRequested = true;
        
        try {
            // Play state save කරන්න
            web.evaluateJavascript(
                "(function() {" +
                "  var video = document.querySelector('video');" +
                "  if (video) window.wasPlayingBeforePIP = !video.paused;" +
                "})();",
                null
            );
            
            // Navigation hide කරන්න
            web.evaluateJavascript(
                "var nav = document.querySelector('ytm-pivot-bar-renderer');" +
                "if (nav) nav.style.display = 'none';",
                null
            );
            
            // PIP params create කරන්න
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
            
            if (portrait) {
                builder.setAspectRatio(new Rational(9, 16));
            } else {
                builder.setAspectRatio(new Rational(16, 9));
            }
            
            builder.setAutoEnterEnabled(true);
            PictureInPictureParams params = builder.build();
            
            // Enter PIP with delay
            handler.postDelayed(() -> {
                try {
                    if (!isFinishing() && !isDestroyed()) {
                        boolean success = enterPictureInPictureMode(params);
                        if (success) {
                            Log.d("PIP", "✅ PIP entered");
                        } else {
                            Log.e("PIP", "❌ PIP failed");
                            isPipRequested = false;
                        }
                    }
                } catch (IllegalStateException e) {
                    Log.e("PIP", "❌ IllegalState: " + e.getMessage());
                    isPipRequested = false;
                    showPipErrorToast("PIP not available now");
                } catch (Exception e) {
                    Log.e("PIP", "❌ Error: " + e.getMessage());
                    isPipRequested = false;
                    showPipErrorToast("PIP failed");
                }
            }, 200);
            
        } catch (Exception e) {
            Log.e("PIP", "❌ Setup error: " + e.getMessage());
            isPipRequested = false;
        }
    }
    
    private void showPipErrorToast(String message) {
        handler.post(() -> {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
        });
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
                .setDescription(filename)
                .setMimeType(mtype)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, encodedFileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE | 
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            downloadManager.enqueue(request);
            Toast.makeText(this, getString(R.string.dl_started), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
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
            
            // ✅ WakeLock acquire කරන්න
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
            
            // ✅ WakeLock release කරන්න
            runOnUiThread(() -> {
                releaseWakeLock();
            });
            
            stopService(new Intent(getApplicationContext(), ForegroundService.class)); 
        }
        
        @JavascriptInterface public void bgPause(long ct) { 
            isPlaying=false; 
            
            // ✅ WakeLock release කරන්න
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
            
            // ✅ WakeLock acquire කරන්න
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
        
        // ✅ UPDATED PIPVID METHOD
        @JavascriptInterface 
        public void pipvid(String orientation) { 
            if (Build.VERSION.SDK_INT < 26) { 
                runOnUiThread(() -> 
                    Toast.makeText(getApplicationContext(), 
                        getString(R.string.no_pip), Toast.LENGTH_SHORT).show()
                );
                return;
            }
            
            // ✅ Check if already in PIP
            if (isPip || isPipRequested) {
                Log.d("PIP", "⏸️ Already in PIP or requested");
                return;
            }
            
            runOnUiThread(() -> {
                isPipRequested = true;
                
                try {
                    // Play state save කරන්න
                    web.evaluateJavascript(
                        "(function() {" +
                        "  var video = document.querySelector('video');" +
                        "  if (video) window.wasPlayingBeforePIP = !video.paused;" +
                        "})();",
                        null
                    );
                    
                    // Navigation hide කරන්න
                    web.evaluateJavascript(
                        "var nav = document.querySelector('ytm-pivot-bar-renderer');" +
                        "if (nav) nav.style.display = 'none';",
                        null
                    );
                    
                    // PIP params with orientation
                    handler.postDelayed(() -> {
                        try {
                            PictureInPictureParams.Builder builder = 
                                new PictureInPictureParams.Builder();
                            
                            if (orientation.equals("portrait")) {
                                builder.setAspectRatio(new Rational(9, 16));
                                Log.d("PIP", "📱 Portrait 9:16");
                            } else {
                                builder.setAspectRatio(new Rational(16, 9));
                                Log.d("PIP", "📺 Landscape 16:9");
                            }
                            
                            builder.setAutoEnterEnabled(true);
                            
                            boolean success = enterPictureInPictureMode(builder.build());
                            
                            if (success) {
                                Log.d("PIP", "✅ Manual PIP successful");
                            } else {
                                Log.e("PIP", "❌ Manual PIP failed");
                                isPipRequested = false;
                                showPipErrorToast("Cannot enter PIP now");
                            }
                            
                        } catch (Exception e) {
                            Log.e("PIP", "❌ Error: " + e.getMessage());
                            isPipRequested = false;
                            showPipErrorToast("PIP failed: " + e.getMessage());
                        }
                    }, 150);
                    
                } catch (Exception e) {
                    Log.e("PIP", "❌ Setup error: " + e.getMessage());
                    isPipRequested = false;
                    showPipErrorToast("PIP error");
                }
            });
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
                }
            }
        };

        if (Build.VERSION.SDK_INT >= 34 && getApplicationInfo().targetSdkVersion >= 34) {
            registerReceiver(broadcastReceiver, new IntentFilter("TRACKS_TRACKS"), RECEIVER_EXPORTED);
        } else {
            registerReceiver(broadcastReceiver, new IntentFilter("TRACKS_TRACKS"));
        }
    }
    
    // ✅ UPDATED LIFECYCLE METHODS
    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
        
        // ✅ App background යනකොට playing නැත්නම් release කරන්න
        if (!isPlaying) {
            releaseWakeLock();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // ✅ App foreground එනකොට playing නම් acquire කරන්න
        if (isPlaying) {
            acquireWakeLock();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // ✅ App close වෙනකොට release කරන්න
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
    
    // ✅ NEW WAKELOCK HELPER METHODS
    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            try {
                wakeLock.acquire(30 * 60 * 1000L); // 30 minutes
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
        offlineLayout.setBackgroundColor(Color.parseColor("#0F0F0F"));
        
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
        iconView.setText("📡");
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
        retryButton.setText("Try again");
        retryButton.setTextColor(Color.WHITE);
        retryButton.setTextSize(16);
        retryButton.setTypeface(null, Typeface.BOLD);
        retryButton.setAllCaps(false);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            retryButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#FF0000"))
            );
        } else {
            retryButton.setBackgroundColor(Color.parseColor("#FF0000"));
        }
        
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
}
