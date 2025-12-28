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
import java.util.*;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        SharedPreferences prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);

        if (!prefs.contains("bgplay")) {
            prefs.edit().putBoolean("bgplay", true).apply();
        }
        
        if (!isNetworkAvailable()) {
            showOfflineScreen();
        } else {
            load(false);
        }
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void load(boolean dl) {
        web = findViewById(R.id.web);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
       
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        String url = "https://m.youtube.com/";
        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();
        
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            url = data.toString();
        } else if (Intent.ACTION_SEND.equals(action)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && (sharedText.contains("youtube.com") || sharedText.contains("youtu.be"))) {
                url = sharedText;
            }
        }
        
        web.loadUrl(url);
        
        web.addJavascriptInterface(new WebAppInterface(this), "Android");
        
        web.setWebChromeClient(new CustomWebClient());
        
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.d("YTPro", "🚀 Loading: " + url);
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d("YTPro", "✅ Loaded: " + url);
                super.onPageFinished(view, url);
                
                // Inject main script.js
                injectMainScript();
                
                if (dL) {
                    triggerDownload();
                }
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e("YTPro", "❌ Error: " + errorCode + " - " + description);
                
                if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || 
                    errorCode == WebViewClient.ERROR_CONNECT) {
                    runOnUiThread(() -> showOfflineScreen());
                }
                
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http") && !url.contains("youtube.com") && !url.contains("youtu.be")) {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(i);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
                return false;
            }
        });

        setReceiver();

        if (Build.VERSION.SDK_INT >= 33) {
            OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
            backCallback = () -> {
                if (web.canGoBack()) {
                    web.goBack();
                } else {
                    finish();
                }
            };
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
    }
    
            
    private void injectMainScript() {
    Log.d("YTPro", "🔄 Starting YTPRO script injection...");
    
    // Create a single JavaScript that loads all scripts
    String jsCode = 
        "(function() {" +
        "  console.log('🚀 YTPRO injection started');" +
        "  " +
        "  // Check if already loaded" +
        "  if (window.YTPRO_LOADED) {" +
        "    console.log('✅ YTPRO already loaded');" +
        "    return;" +
        "  }" +
        "  " +
        "  // Function to inject script directly" +
        "  function injectScript(code) {" +
        "    try {" +
        "      var script = document.createElement('script');" +
        "      script.textContent = code;" +
        "      document.head.appendChild(script);" +
        "      return true;" +
        "    } catch (e) {" +
        "      console.error('❌ Script injection failed:', e);" +
        "      return false;" +
        "    }" +
        "  }" +
        "  " +
        "  // Function to fetch script content" +
        "  function fetchScript(url, callback) {" +
        "    console.log('📥 Fetching: ' + url);" +
        "    " +
        "    // Create a unique timestamp to avoid cache" +
        "    var fetchUrl = url + '?t=' + Date.now() + '&r=' + Math.random();" +
        "    " +
        "    // Use XMLHttpRequest for better compatibility" +
        "    var xhr = new XMLHttpRequest();" +
        "    xhr.open('GET', fetchUrl, true);" +
        "    xhr.onreadystatechange = function() {" +
        "      if (xhr.readyState === 4) {" +
        "        if (xhr.status === 200) {" +
        "          console.log('✅ Fetched: ' + url);" +
        "          callback(xhr.responseText, true);" +
        "        } else {" +
        "          console.error('❌ Failed to fetch ' + url + ': ' + xhr.status);" +
        "          callback(null, false);" +
        "        }" +
        "      }" +
        "    };" +
        "    xhr.onerror = function() {" +
        "      console.error('❌ Network error fetching: ' + url);" +
        "      callback(null, false);" +
        "    };" +
        "    xhr.send();" +
        "  }" +
        "  " +
        "  // List of scripts to load (in order)" +
        "  var scripts = [" +
        "    'https://raw.githubusercontent.com/SP-Mods-WA/Yt/main/scripts/script.js'," +
        "    'https://raw.githubusercontent.com/SP-Mods-WA/Yt/main/scripts/bgplay.js'," +
        "    'https://raw.githubusercontent.com/SP-Mods-WA/Yt/main/scripts/innertube.js'" +
        "  ];" +
        "  " +
        "  var currentIndex = 0;" +
        "  " +
        "  function loadNextScript() {" +
        "    if (currentIndex >= scripts.length) {" +
        "      // All scripts loaded" +
        "      window.YTPRO_LOADED = true;" +
        "      console.log('🎉 All YTPRO scripts loaded successfully!');" +
        "      " +
        "      // Initialize YTPRO if function exists" +
        "      setTimeout(function() {" +
        "        if (typeof window.initYTPro === 'function') {" +
        "          window.initYTPro();" +
        "          console.log('✅ YTPRO initialized');" +
        "        } else {" +
        "          console.log('ℹ️ initYTPro function not found, checking for other init functions');" +
        "          " +
        "          // Try common initialization patterns" +
        "          if (typeof window.YTPRO !== 'undefined' && typeof window.YTPRO.init === 'function') {" +
        "            window.YTPRO.init();" +
        "          } else if (typeof window.ytproInit === 'function') {" +
        "            window.ytproInit();" +
        "          }" +
        "        }" +
        "      }, 500);" +
        "      return;" +
        "    }" +
        "    " +
        "    var url = scripts[currentIndex];" +
        "    " +
        "    fetchScript(url, function(content, success) {" +
        "      if (success && content) {" +
        "        // Inject the script content directly" +
        "        var injected = injectScript(content);" +
        "        if (injected) {" +
        "          console.log('✅ Injected: ' + url.split('/').pop());" +
        "          " +
        "          // Wait a bit before loading next script" +
        "          setTimeout(function() {" +
        "            currentIndex++;" +
        "            loadNextScript();" +
        "          }, 300);" +
        "        } else {" +
        "          // Try next URL or fallback" +
        "          currentIndex++;" +
        "          loadNextScript();" +
        "        }" +
        "      } else {" +
        "        // Try alternative URL or CDN" +
        "        console.log('🔄 Trying alternative for: ' + url.split('/').pop());" +
        "        " +
        "        // Try jsDelivr CDN as fallback" +
        "        var cdnUrl = url.replace('raw.githubusercontent.com', 'cdn.jsdelivr.net/gh');" +
        "        cdnUrl = cdnUrl.replace('/main/', '@main/');" +
        "        " +
        "        fetchScript(cdnUrl, function(cdnContent, cdnSuccess) {" +
        "          if (cdnSuccess && cdnContent) {" +
        "            var cdnInjected = injectScript(cdnContent);" +
        "            if (cdnInjected) {" +
        "              console.log('✅ Injected via CDN: ' + url.split('/').pop());" +
        "              setTimeout(function() {" +
        "                currentIndex++;" +
        "                loadNextScript();" +
        "              }, 300);" +
        "            } else {" +
        "              currentIndex++;" +
        "              loadNextScript();" +
        "            }" +
        "          } else {" +
        "            // Skip this script and continue" +
        "            console.log('⚠️ Skipping: ' + url.split('/').pop());" +
        "            currentIndex++;" +
        "            loadNextScript();" +
        "          }" +
        "        });" +
        "      }" +
        "    });" +
        "  }" +
        "  " +
        "  // Start loading scripts" +
        "  loadNextScript();" +
        "  " +
        "  // Set up periodic check to see if YTPRO loaded" +
        "  var checkAttempts = 0;" +
        "  var maxAttempts = 30; // 30 seconds" +
        "  var checkInterval = setInterval(function() {" +
        "    checkAttempts++;" +
        "    " +
        "    // Check for various YTPRO indicators" +
        "    var ytproLoaded = window.YTPRO_LOADED || " +
        "                     window.YTProVer || " +
        "                     (typeof window.YTPRO !== 'undefined') || " +
        "                     (typeof window.ytproDownVid === 'function');" +
        "    " +
        "    if (ytproLoaded) {" +
        "      clearInterval(checkInterval);" +
        "      console.log('✅ YTPRO successfully detected after ' + checkAttempts + ' checks');" +
        "    } else if (checkAttempts >= maxAttempts) {" +
        "      clearInterval(checkInterval);" +
        "      console.warn('⚠️ YTPRO not detected after ' + maxAttempts + ' checks');" +
        "      " +
        "      // Create minimal fallback functions" +
        "      window.YTPRO_LOADED = true;" +
        "      if (typeof window.ytproDownVid === 'undefined') {" +
        "        window.ytproDownVid = function() {" +
        "          console.log('📥 Download triggered (fallback)');" +
        "          return true;" +
        "        };" +
        "      }" +
        "    }" +
        "  }, 1000);" +
        "})();";
    
    // Execute the injection
    web.evaluateJavascript(jsCode, null);
    
    // Additional safety: inject a simple version after 5 seconds
    new Handler().postDelayed(() -> {
        injectFallbackScript();
    }, 5000);
}

private void injectFallbackScript() {
    // Check if scripts loaded
    String checkScript = 
        "(function() {" +
        "  var status = {" +
        "    YTPRO_LOADED: !!window.YTPRO_LOADED," +
        "    YTProVer: window.YTProVer || 'none'," +
        "    ytproDownVid: typeof window.ytproDownVid," +
        "    YTPRO: typeof window.YTPRO" +
        "  };" +
        "  console.log('📊 Current YTPRO Status:', status);" +
        "  return JSON.stringify(status);" +
        "})();";
    
    web.evaluateJavascript(checkScript, new ValueCallback<String>() {
        @Override
        public void onReceiveValue(String value) {
            Log.d("YTPro", "📊 Injection Status: " + value);
            
            // If not loaded, inject a minimal version
            if (value != null && !value.contains("\"YTPRO_LOADED\":true")) {
                Log.w("YTPro", "⚠️ YTPRO not loaded, injecting fallback...");
                
                String fallbackJs = 
                    "(function() {" +
                    "  console.log('🔄 Injecting fallback YTPRO...');" +
                    "  " +
                    "  // Create minimal YTPRO object" +
                    "  window.YTPRO = window.YTPRO || {};" +
                    "  window.YTPRO_LOADED = true;" +
                    "  window.YTProVer = '2.0-fallback';" +
                    "  " +
                    "  // Essential functions" +
                    "  window.ytproDownVid = function() {" +
                    "    console.log('📥 Download video (fallback)');" +
                    "    // Trigger download by navigating to hash" +
                    "    window.location.hash = '#download';" +
                    "    return true;" +
                    "  };" +
                    "  " +
                    "  console.log('✅ Fallback YTPRO created');" +
                    "})();";
                
                web.evaluateJavascript(fallbackJs, null);
            }
        }
    });
        }
    
    
    private void triggerDownload() {
        new Handler().postDelayed(() -> {
            web.evaluateJavascript(
                "(function() {" +
                "  console.log('⬇️ Checking for download function...');" +
                "  if (typeof window.ytproDownVid === 'function') {" +
                "    console.log('✅ Triggering download...');" +
                "    window.location.hash = '#download';" +
                "  } else {" +
                "    console.log('❌ ytproDownVid not found');" +
                "    console.log('Available functions:', Object.keys(window).filter(k => typeof window[k] === 'function').join(', '));" +
                "  }" +
                "})();",
                null
            );
            dL = false;
        }, 4000);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                web.reload();
            } else {
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show();
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
        if (isInPictureInPictureMode) {
            web.evaluateJavascript("if(typeof PIPlayer === 'function') PIPlayer();", null);
        } else {
            web.evaluateJavascript("if(typeof removePIP === 'function') removePIP();", null);
        }
        isPip = isInPictureInPictureMode;
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
       
        if (Build.VERSION.SDK_INT >= 26 && web != null && web.getUrl() != null && 
            web.getUrl().contains("/watch") && isPlaying) {
            try {
                PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
                if (portrait) {
                    builder.setAspectRatio(new Rational(9, 16));
                } else {
                    builder.setAspectRatio(new Rational(16, 9));
                }
                enterPictureInPictureMode(builder.build());
                isPip = true;
            } catch (Exception e) {
                Log.e("PIP", "Failed to enter PiP", e);
            }
        }
    }

    public class CustomWebClient extends WebChromeClient {
        private View mCustomView;
        private WebChromeClient.CustomViewCallback mCustomViewCallback;
        
        @Override
        public Bitmap getDefaultVideoPoster() {
            Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.DKGRAY);
            return bitmap;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (mCustomView != null) {
                callback.onCustomViewHidden();
                return;
            }
            
            mCustomView = view;
            mCustomViewCallback = callback;
            
            FrameLayout decor = (FrameLayout) getWindow().getDecorView();
            decor.addView(mCustomView, new FrameLayout.LayoutParams(-1, -1));
            
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
            
            if (portrait) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }
        }
        
        @Override
        public void onHideCustomView() {
            if (mCustomView == null) return;
            
            FrameLayout decor = (FrameLayout) getWindow().getDecorView();
            decor.removeView(mCustomView);
            
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            
            mCustomViewCallback.onCustomViewHidden();
            mCustomView = null;
            mCustomViewCallback = null;
        }
        
        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            if (request.getOrigin().toString().contains("youtube.com")) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
                } else {
                    request.grant(request.getResources());
                }
            }
        }
        
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            Log.d("WebConsole", consoleMessage.message());
            return true;
        }
    }

    private void downloadFile(String filename, String url, String mimeType) {
        if (Build.VERSION.SDK_INT > 22 && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return;
            }
        }
        
        try {
            String encodedName = URLEncoder.encode(filename, "UTF-8").replaceAll("\\+", "%20");
            
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            
            request.setTitle(filename)
                   .setDescription("Downloading video")
                   .setMimeType(mimeType)
                   .setAllowedOverMetered(true)
                   .setAllowedOverRoaming(true)
                   .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, encodedName)
                   .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            
            dm.enqueue(request);
            Toast.makeText(this, "Download started: " + filename, Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e("Download", "Error: " + e.getMessage());
            Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show();
        }
    }

    public class WebAppInterface {
        Context mContext;
        
        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void showToast(String txt) {
            Toast.makeText(mContext, txt, Toast.LENGTH_SHORT).show();
        }
        
        @JavascriptInterface
        public void gohome(String x) {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
        }

        @JavascriptInterface
        public void downvid(String name, String url, String m) {
            downloadFile(name, url, m);
        }
        
        @JavascriptInterface
        public void fullScreen(boolean value) {
            portrait = value;
        }
        
        @JavascriptInterface
        public void oplink(String url) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(mContext, "Cannot open link", Toast.LENGTH_SHORT).show();
            }
        }
        
        @JavascriptInterface
        public String getInfo() {
            try {
                PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
                return info.versionName;
            } catch (Exception e) {
                return "1.0";
            }
        }
        
        @JavascriptInterface
        public void setBgPlay(boolean bgplay) {
            getSharedPreferences("YTPRO", MODE_PRIVATE).edit().putBoolean("bgplay", bgplay).apply();
        }

        @JavascriptInterface
        public void bgStart(String iconn, String titlen, String subtitlen, long dura) {
            icon = iconn;
            title = titlen;
            subtitle = subtitlen;
            duration = dura;
            isPlaying = true;
            mediaSession = true;

            Intent intent = new Intent(getApplicationContext(), ForegroundService.class);
            intent.putExtra("icon", icon);
            intent.putExtra("title", title);
            intent.putExtra("subtitle", subtitle);
            intent.putExtra("duration", duration);
            intent.putExtra("currentPosition", 0);
            intent.putExtra("action", "play");
            startService(intent);
        }

        @JavascriptInterface
        public void bgUpdate(String iconn, String titlen, String subtitlen, long dura) {
            icon = iconn;
            title = titlen;
            subtitle = subtitlen;
            duration = dura;
            isPlaying = true;

            sendBroadcast(new Intent("UPDATE_NOTIFICATION")
                .putExtra("icon", icon)
                .putExtra("title", title)
                .putExtra("subtitle", subtitle)
                .putExtra("duration", duration)
                .putExtra("currentPosition", 0)
                .putExtra("action", "pause"));
        }
        
        @JavascriptInterface
        public void bgStop() {
            isPlaying = false;
            mediaSession = false;
            stopService(new Intent(getApplicationContext(), ForegroundService.class));
        }
        
        @JavascriptInterface
        public void bgPause(long ct) {
            isPlaying = false;
            sendBroadcast(new Intent("UPDATE_NOTIFICATION")
                .putExtra("icon", icon)
                .putExtra("title", title)
                .putExtra("subtitle", subtitle)
                .putExtra("duration", duration)
                .putExtra("currentPosition", ct)
                .putExtra("action", "pause"));
        }
        
        @JavascriptInterface
        public void bgPlay(long ct) {
            isPlaying = true;
            sendBroadcast(new Intent("UPDATE_NOTIFICATION")
                .putExtra("icon", icon)
                .putExtra("title", title)
                .putExtra("subtitle", subtitle)
                .putExtra("duration", duration)
                .putExtra("currentPosition", ct)
                .putExtra("action", "play"));
        }
        
        @JavascriptInterface
        public void bgBuffer(long ct) {
            isPlaying = true;
            sendBroadcast(new Intent("UPDATE_NOTIFICATION")
                .putExtra("icon", icon)
                .putExtra("title", title)
                .putExtra("subtitle", subtitle)
                .putExtra("duration", duration)
                .putExtra("currentPosition", ct)
                .putExtra("action", "buffer"));
        }
        
        @JavascriptInterface
        public void getSNlM0e(String cookies) {
            new Thread(() -> {
                String response = GeminiWrapper.getSNlM0e(cookies);
                runOnUiThread(() -> web.evaluateJavascript("callbackSNlM0e.resolve(`" + response + "`)", null));
            }).start();
        }
        
        @JavascriptInterface
        public void GeminiClient(String url, String headers, String body) {
            new Thread(() -> {
                JSONObject response = GeminiWrapper.getStream(url, headers, body);
                runOnUiThread(() -> web.evaluateJavascript("callbackGeminiClient.resolve(" + response + ")", null));
            }).start();
        }
        
        @JavascriptInterface
        public String getAllCookies(String url) {
            String cookies = CookieManager.getInstance().getCookie(url);
            return cookies != null ? cookies : "";
        }
        
        @JavascriptInterface
        public float getVolume() {
            int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            return max > 0 ? (float) current / max : 0.5f;
        }
        
        @JavascriptInterface
        public void setVolume(float volume) {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int target = Math.max(0, Math.min((int)(max * volume), max));
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
        }
        
        @JavascriptInterface
        public float getBrightness() {
            try {
                int brightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
                return (brightness / 255f) * 100f;
            } catch (Exception e) {
                return 50f;
            }
        }
        
        @JavascriptInterface
        public void setBrightness(final float percent) {
            runOnUiThread(() -> {
                float brightness = Math.max(0, Math.min(percent / 100f, 1f));
                WindowManager.LayoutParams layout = getWindow().getAttributes();
                layout.screenBrightness = brightness;
                getWindow().setAttributes(layout);
            });
        }
        
        @JavascriptInterface
        public void pipvid(String mode) {
            if (Build.VERSION.SDK_INT >= 26) {
                try {
                    PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
                    if ("portrait".equals(mode)) {
                        builder.setAspectRatio(new Rational(9, 16));
                    } else {
                        builder.setAspectRatio(new Rational(16, 9));
                    }
                    enterPictureInPictureMode(builder.build());
                } catch (Exception e) {
                    Toast.makeText(mContext, "PiP not available", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(mContext, "PiP not available", Toast.LENGTH_SHORT).show();
            }
        }
        
        @JavascriptInterface
        public void log(String message) {
            Log.d("JSLog", message);
        }
    }

    private void setReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getStringExtra("actionname");
                Log.d("Receiver", "Action: " + action);

                if (action == null) return;
                
                switch (action) {
                    case "PLAY_ACTION":
                        web.evaluateJavascript("if(typeof playVideo === 'function') playVideo();", null);
                        break;
                    case "PAUSE_ACTION":
                        web.evaluateJavascript("if(typeof pauseVideo === 'function') pauseVideo();", null);
                        break;
                    case "NEXT_ACTION":
                        web.evaluateJavascript("if(typeof playNext === 'function') playNext();", null);
                        break;
                    case "PREV_ACTION":
                        web.evaluateJavascript("if(typeof playPrev === 'function') playPrev();", null);
                        break;
                    case "SEEKTO":
                        String pos = intent.getStringExtra("pos");
                        if (pos != null) {
                            web.evaluateJavascript("if(typeof seekTo === 'function') seekTo('" + pos + "');", null);
                        }
                        break;
                }
            }
        };

        IntentFilter filter = new IntentFilter("TRACKS_TRACKS");
        registerReceiver(broadcastReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (isOffline && isNetworkAvailable()) {
            hideOfflineScreen();
            if (web != null) {
                web.reload();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isPlaying) {
            stopService(new Intent(this, ForegroundService.class));
        }
        
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
        }
        
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
        } else {
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    private void showOfflineScreen() {
        if (isOffline || web == null) return;
        
        isOffline = true;
        runOnUiThread(() -> {
            web.setVisibility(View.GONE);
            
            offlineLayout = new RelativeLayout(this);
            offlineLayout.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            ));
            offlineLayout.setBackgroundColor(Color.BLACK);
            
            LinearLayout center = new LinearLayout(this);
            center.setOrientation(LinearLayout.VERTICAL);
            center.setGravity(Gravity.CENTER);
            
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            );
            params.addRule(RelativeLayout.CENTER_IN_PARENT);
            center.setLayoutParams(params);
            
            TextView icon = new TextView(this);
            icon.setText("📡");
            icon.setTextSize(50);
            icon.setTextColor(Color.WHITE);
            icon.setGravity(Gravity.CENTER);
            center.addView(icon);
            
            TextView title = new TextView(this);
            title.setText("No Internet");
            title.setTextSize(18);
            title.setTextColor(Color.WHITE);
            title.setTypeface(null, Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            titleParams.setMargins(0, 20, 0, 10);
            title.setLayoutParams(titleParams);
            center.addView(title);
            
            TextView message = new TextView(this);
            message.setText("Please check your connection");
            message.setTextSize(14);
            message.setTextColor(Color.LTGRAY);
            message.setGravity(Gravity.CENTER);
            message.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            center.addView(message);
            
            Button retry = new Button(this);
            retry.setText("RETRY");
            retry.setTextColor(Color.WHITE);
            retry.setBackgroundColor(Color.RED);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                200, 50
            );
            btnParams.setMargins(0, 30, 0, 0);
            retry.setLayoutParams(btnParams);
            
            retry.setOnClickListener(v -> {
                if (isNetworkAvailable()) {
                    hideOfflineScreen();
                    web.reload();
                } else {
                    Toast.makeText(this, "Still offline", Toast.LENGTH_SHORT).show();
                }
            });
            
            center.addView(retry);
            offlineLayout.addView(center);
            
            ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
            root.addView(offlineLayout);
        });
    }

    private void hideOfflineScreen() {
        if (!isOffline || offlineLayout == null) return;
        
        isOffline = false;
        runOnUiThread(() -> {
            web.setVisibility(View.VISIBLE);
            ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
            root.removeView(offlineLayout);
            offlineLayout = null;
        });
    }
}

