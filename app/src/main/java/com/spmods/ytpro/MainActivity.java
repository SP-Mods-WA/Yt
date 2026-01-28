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
    
    private long lastBackPress = 0;

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
            Log.d("PlayProtect", "✅ Play Protect warnings disabled");
        } catch (Exception e) {
            Log.e("PlayProtect", "❌ Failed: " + e.getMessage());
        }
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
        // ඔබගේ bottom_navigation.xml එකේ IDs අනුව
        View bottomNav = findViewById(R.id.bottomNavigation);
        
        // Home button
        View navHome = bottomNav.findViewById(R.id.navHome);
        View navShorts = bottomNav.findViewById(R.id.navShorts);
        View navUpload = bottomNav.findViewById(R.id.navUpload);
        View navSubscriptions = bottomNav.findViewById(R.id.navSubscriptions);
        View navYou = bottomNav.findViewById(R.id.navYou);
        
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                userNavigated = true;
                web.loadUrl("https://m.youtube.com/");
            });
        }
        
        if (navShorts != null) {
            navShorts.setOnClickListener(v -> {
                userNavigated = true;
                web.loadUrl("https://m.youtube.com/shorts");
            });
        }
        
        if (navUpload != null) {
            navUpload.setOnClickListener(v -> {
                Toast.makeText(MainActivity.this, "Upload feature coming soon! 🎥", Toast.LENGTH_SHORT).show();
            });
        }
        
        if (navSubscriptions != null) {
            navSubscriptions.setOnClickListener(v -> {
                userNavigated = true;
                web.loadUrl("https://m.youtube.com/feed/subscriptions");
            });
        }
        
        if (navYou != null) {
            navYou.setOnClickListener(v -> {
                userNavigated = true;
                web.loadUrl("https://m.youtube.com/feed/account");
            });
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                web.loadUrl("https://m.youtube.com");
            } else {
                Toast.makeText(getApplicationContext(), "Grant microphone permission", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(getApplicationContext(), "Grant storage permission", Toast.LENGTH_SHORT).show();
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
            if (System.currentTimeMillis() - lastBackPress < 2000) {
                super.onBackPressed();
            } else {
                Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
                lastBackPress = System.currentTimeMillis();
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        
        isPip = isInPictureInPictureMode;
        isPipRequested = false;
        
        if (isInPictureInPictureMode) {
            // Entering PIP
            handler.postDelayed(() -> {
                runOnUiThread(() -> {
                    web.evaluateJavascript(
                        "(function() {" +
                        "  var video = document.querySelector('video');" +
                        "  if (!video) return;" +
                        "  window.wasPlayingBeforePIP = !video.paused;" +
                        "  window.pipMode = true;" +
                        "  if (window.wasPlayingBeforePIP && video.paused) {" +
                        "    setTimeout(() => { video.play(); }, 100);" +
                        "  }" +
                        "})();",
                        null
                    );
                });
            }, 150);
            
            // Hide bottom navigation in PIP
            View bottomNav = findViewById(R.id.bottomNavigation);
            if (bottomNav != null) {
                bottomNav.setVisibility(View.GONE);
            }
            
        } else {
            // Exiting PIP
            runOnUiThread(() -> {
                // Show bottom navigation
                View bottomNav = findViewById(R.id.bottomNavigation);
                if (bottomNav != null) {
                    bottomNav.setVisibility(View.VISIBLE);
                }
                
                web.evaluateJavascript(
                    "(function() {" +
                    "  window.pipMode = false;" +
                    "  var video = document.querySelector('video');" +
                    "  if (video && window.wasPlayingBeforePIP && video.paused) {" +
                    "    setTimeout(() => { video.play(); }, 300);" +
                    "  }" +
                    "  window.wasPlayingBeforePIP = undefined;" +
                    "})();",
                    null
                );
            });
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        
        if (isPip || isPipRequested) return;
        
        String currentUrl = web.getUrl();
        boolean isVideoPage = currentUrl != null && 
            (currentUrl.contains("watch") || currentUrl.contains("shorts"));
        
        if (isVideoPage && isPlaying && Build.VERSION.SDK_INT >= 26) {
            handler.postDelayed(() -> {
                enterPipMode("landscape");
            }, 300);
        }
    }

    private void enterPipMode(String orientation) {
        if (Build.VERSION.SDK_INT < 26) return;
        if (isPip || isPipRequested) return;
        
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            
            isPipRequested = true;
            
            try {
                web.evaluateJavascript(
                    "(function() {" +
                    "  var video = document.querySelector('video');" +
                    "  if (video) window.wasPlayingBeforePIP = !video.paused;" +
                    "})();",
                    null
                );
                
                PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
                
                if ("portrait".equals(orientation)) {
                    builder.setAspectRatio(new android.util.Rational(9, 16));
                } else {
                    builder.setAspectRatio(new android.util.Rational(16, 9));
                }
                
                builder.setAutoEnterEnabled(true);
                
                boolean success = enterPictureInPictureMode(builder.build());
                
                if (!success) {
                    isPipRequested = false;
                }
                
            } catch (Exception e) {
                isPipRequested = false;
            }
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
            if (isPip) return;
            
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
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Grant storage permission", Toast.LENGTH_SHORT).show());
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
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
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
    }
    
    public void setReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getExtras().getString("actionname");

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
            } catch (Exception e) {
            }
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception e) {
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
    }

    private void checkNotificationsNow() {
        NotificationFetcher fetcher = new NotificationFetcher(this);
        AppNotificationManager notificationManager = new AppNotificationManager(this);
        
        fetcher.fetchNotifications(new NotificationFetcher.NotificationCallback() {
            @Override
            public void onSuccess(List<NotificationModel> notifications) {
                notificationManager.showNotifications(notifications);
            }

            @Override
            public void onError(String error) {
            }
        });
    }
}
