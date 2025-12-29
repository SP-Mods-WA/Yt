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
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Handler;
import android.text.TextUtils;

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
  
  // CDN Configuration
  private static final String YOUR_GITHUB_USER = "SP-Mods-WA";
  private static final String YOUR_REPO = "Yt";
  private static final String YOUR_BRANCH = "main";
  private static final String YOUR_SCRIPTS_FOLDER = "scripts";
  
  private static final String YOUR_CDN_BASE = "https://cdn.jsdelivr.net/gh/" + 
        YOUR_GITHUB_USER + "/" + YOUR_REPO + "@" + YOUR_BRANCH + "/" + YOUR_SCRIPTS_FOLDER + "/";
  
  private static final String MAIN_SCRIPT_URL = YOUR_CDN_BASE + "script.js";
  private static final String BGPLAY_SCRIPT_URL = YOUR_CDN_BASE + "bgplay.js";
  private static final String INNERTUBE_SCRIPT_URL = YOUR_CDN_BASE + "innertube.js";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    
    // Initialize WebView
    web = findViewById(R.id.web);
    
    // Setup logging
    WebView.setWebContentsDebuggingEnabled(true);
    
    SharedPreferences prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);

    if (!prefs.contains("bgplay")) {
      prefs.edit().putBoolean("bgplay", true).apply();
    }
    
    // Check network
    if (!isNetworkAvailable()) {
        showOfflineScreen();
    } else {
        load(false);
        checkForAppUpdate();
    }
    
    // Keep screen on
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    
    // Setup periodic network check
    new Handler().postDelayed(new Runnable() {
        @Override
        public void run() {
            checkNetworkStatus();
        }
    }, 5000);
  }

  public void load(boolean dl) {
    dL = dl;
    
    audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
   
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setSupportZoom(true);
    web.getSettings().setBuiltInZoomControls(true);
    web.getSettings().setDisplayZoomControls(false);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setDatabaseEnabled(true);
    web.getSettings().setMediaPlaybackRequiresUserGesture(false);
    web.getSettings().setAllowFileAccess(true);
    web.getSettings().setAllowContentAccess(true);
    
    // Enable mixed content for Android 5.0+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    }
    
    web.setLayerType(View.LAYER_TYPE_HARDWARE, null);

    Intent intent = getIntent();
    String action = intent.getAction();
    Uri data = intent.getData();
    String url = "https://m.youtube.com/";
    
    if (Intent.ACTION_VIEW.equals(action) && data != null) {
      url = data.toString();
    } else if (Intent.ACTION_SEND.equals(action)) {
      String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
      if (sharedText != null && (sharedText.contains("youtube.com") || sharedText.contains("youtu.be"))) {
        url = sharedText;
      }
    }
    
    Log.d("YTPRO", "Loading URL: " + url);
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
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            
            Log.d("YTPRO_CDN", "🔍 Intercepting: " + url);
            
            // ✅ YOUR CDN REDIRECTS
            if (url.contains("youtube.com/ytpro_cdn/")) {
                String modifiedUrl = null;
                
                if (url.contains("youtube.com/ytpro_cdn/esm")) {
                    modifiedUrl = url.replace("youtube.com/ytpro_cdn/esm", "esm.sh");
                    Log.d("YTPRO_CDN", "🔄 ESM Redirect: " + modifiedUrl);
                    
                } else if (url.contains("youtube.com/ytpro_cdn/npm/ytpro")) {
                    // Handle specific files
                    if (url.contains("innertube.js")) {
                        modifiedUrl = INNERTUBE_SCRIPT_URL;
                    } else if (url.contains("bgplay.js")) {
                        modifiedUrl = BGPLAY_SCRIPT_URL;
                    } else if (url.contains("script.js")) {
                        modifiedUrl = MAIN_SCRIPT_URL;
                    } else {
                        // Extract filename and redirect to your CDN
                        String fileName = extractFileName(url);
                        if (fileName != null) {
                            modifiedUrl = YOUR_CDN_BASE + fileName;
                        }
                    }
                    
                    if (modifiedUrl != null) {
                        Log.d("YTPRO_CDN", "✅ Your CDN Redirect: " + modifiedUrl);
                    }
                }
                
                // Process the modified URL
                if (modifiedUrl != null) {
                    try {
                        URL newUrl = new URL(modifiedUrl);
                        HttpsURLConnection connection = (HttpsURLConnection) newUrl.openConnection();
                        
                        // Set headers
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
                        connection.setRequestProperty("Accept", "*/*");
                        connection.setRequestProperty("Cache-Control", "no-cache");
                        connection.setRequestProperty("Pragma", "no-cache");
                        
                        connection.setConnectTimeout(15000);
                        connection.setReadTimeout(15000);
                        connection.setRequestMethod("GET");
                        connection.connect();
                        
                        int responseCode = connection.getResponseCode();
                        Log.d("YTPRO_CDN", "📡 Response: " + responseCode + " for " + modifiedUrl);
                        
                        if (responseCode == 200) {
                            String contentType = connection.getContentType();
                            if (contentType == null || contentType.isEmpty()) {
                                contentType = "application/javascript";
                            }
                            
                            String encoding = connection.getContentEncoding();
                            if (encoding == null) encoding = "utf-8";
                            
                            Map<String, String> headers = new HashMap<>();
                            headers.put("Access-Control-Allow-Origin", "*");
                            headers.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                            headers.put("Access-Control-Allow-Headers", "*");
                            headers.put("Content-Type", contentType + "; charset=utf-8");
                            headers.put("Cross-Origin-Resource-Policy", "cross-origin");
                            
                            return new WebResourceResponse(
                                "application/javascript",
                                "utf-8",
                                responseCode,
                                "OK",
                                headers,
                                connection.getInputStream()
                            );
                        } else {
                            Log.e("YTPRO_CDN", "❌ CDN returned error: " + responseCode);
                        }
                    } catch (Exception e) {
                        Log.e("YTPRO_CDN", "❌ CDN Error: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            
            return super.shouldInterceptRequest(view, request);
        }
        
        private String extractFileName(String url) {
            try {
                // Extract filename from URL like: youtube.com/ytpro_cdn/npm/ytpro/filename.js
                String[] parts = url.split("/");
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].equals("ytpro") && i + 1 < parts.length) {
                        return parts[i + 1];
                    }
                }
            } catch (Exception e) {
                Log.e("YTPRO_CDN", "Error extracting filename", e);
            }
            return null;
        }
      
        @Override
        public void onPageStarted(WebView p1, String p2, Bitmap p3) {
            super.onPageStarted(p1, p2, p3);
            Log.d("YTPRO", "Page started: " + p2);
        }

        @Override
        public void onPageFinished(WebView p1, String url) {
            Log.d("YTPRO", "Page finished: " + url);
            
            // ✅ Setup TrustedTypes
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
            
            // ✅ Load YOUR scripts with proper timing
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    loadYourScripts();
                }
            }, 1000); // Wait 1 second for page to stabilize
            
            // Handle download flag
            if (dL) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        triggerDownloadSection();
                    }
                }, 3000); // Wait for scripts to load
            }
            
            // Stop media session when leaving video pages
            if (!url.contains("youtube.com/watch") && !url.contains("youtube.com/shorts") && isPlaying) {
                isPlaying = false;
                mediaSession = false;
                stopService(new Intent(getApplicationContext(), ForegroundService.class));
            }

            super.onPageFinished(p1, url);
        }
        
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            Log.e("YTPRO", "WebView Error [" + errorCode + "]: " + description + " - " + failingUrl);
            if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || 
                errorCode == WebViewClient.ERROR_CONNECT || 
                errorCode == WebViewClient.ERROR_TIMEOUT) {
                showOfflineScreen();
            }
            super.onReceivedError(view, errorCode, description, failingUrl);
        }
        
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.e("YTPRO", "WebResourceError [" + error.getErrorCode() + "]: " + error.getDescription());
                if (request.isForMainFrame()) {
                    int errorCode = error.getErrorCode();
                    if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || 
                        errorCode == WebViewClient.ERROR_CONNECT || 
                        errorCode == WebViewClient.ERROR_TIMEOUT) {
                        showOfflineScreen();
                    }
                }
            }
            super.onReceivedError(view, request, error);
        }
    });

    setReceiver();
    
    // Setup back navigation for Android 13+
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
        backCallback = new OnBackInvokedCallback() {
            @Override
            public void onBackInvoked() {
                if (web.canGoBack()) {
                    web.goBack();
                } else {
                    finish();
                }
            }
        };
        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
    }
  }
  
  private void loadYourScripts() {
      String scriptLoader = 
          "(function() {" +
          "  console.log('🚀 Loading YTPRO from SP-Mods CDN...');" +
          
          "  function loadScript(src, name) {" +
          "    return new Promise((resolve, reject) => {" +
          "      console.log('📥 Loading: ' + name);" +
          "      var script = document.createElement('script');" +
          "      script.src = src;" +
          "      script.async = false;" +
          "      script.onload = function() {" +
          "        console.log('✅ ' + name + ' loaded');" +
          "        resolve();" +
          "      };" +
          "      script.onerror = function(e) {" +
          "        console.error('❌ Failed: ' + name, e);" +
          "        reject(new Error('Failed: ' + name));" +
          "      };" +
          "      document.body.appendChild(script);" +
          "    });" +
          "  }" +
          
          "  var CDN_BASE = '" + YOUR_CDN_BASE + "';" +
          "  console.log('🔗 CDN Base URL:', CDN_BASE);" +
          
          "  // Load scripts sequentially" +
          "  loadScript(CDN_BASE + 'script.js', 'Main Script')" +
          "    .then(function() {" +
          "      console.log('🎯 Main Script initialized');" +
          "      return loadScript(CDN_BASE + 'bgplay.js', 'BG Play');" +
          "    })" +
          "    .then(function() {" +
          "      console.log('🎵 BG Play initialized');" +
          "      return loadScript(CDN_BASE + 'innertube.js', 'InnerTube');" +
          "    })" +
          "    .then(function() {" +
          "      console.log('🎬 InnerTube initialized');" +
          "      window.YTPRO_LOADED = true;" +
          "      console.log('✅ All YTPRO scripts loaded successfully!');" +
          "      " +
          "      // Initialize download functionality" +
          "      if (typeof window.initDownload === 'function') {" +
          "        window.initDownload();" +
          "      }" +
          "      " +
          "      // Check if download section should be shown" +
          "      if (window.location.hash === '#download') {" +
          "        showDownloadSection();" +
          "      }" +
          "    })" +
          "    .catch(function(error) {" +
          "      console.error('❌ Script loading failed:', error);" +
          "      window.YTPRO_LOADED = false;" +
          "    });" +
          "})();";
      
      web.evaluateJavascript(scriptLoader, new ValueCallback<String>() {
          @Override
          public void onReceiveValue(String value) {
              Log.d("YTPRO", "Script loader injected: " + value);
          }
      });
  }
  
  private void triggerDownloadSection() {
      web.evaluateJavascript(
          "(function() {" +
          "  console.log('🔄 Triggering download section...');" +
          "  " +
          "  // Method 1: Check for ytproDownVid function" +
          "  if (typeof window.ytproDownVid === 'function') {" +
          "    console.log('✅ Calling ytproDownVid()');" +
          "    window.ytproDownVid();" +
          "    return true;" +
          "  }" +
          "  " +
          "  // Method 2: Set hash" +
          "  console.log('📝 Setting location hash to download');" +
          "  window.location.hash = 'download';" +
          "  " +
          "  // Method 3: Call showDownloadSection if exists" +
          "  setTimeout(function() {" +
          "    if (typeof showDownloadSection === 'function') {" +
          "      console.log('✅ Calling showDownloadSection()');" +
          "      showDownloadSection();" +
          "    }" +
          "  }, 1000);" +
          "  " +
          "  return false;" +
          "})();",
          new ValueCallback<String>() {
              @Override
              public void onReceiveValue(String value) {
                  Log.d("YTPRO", "Download trigger result: " + value);
                  dL = false;
              }
          }
      );
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
    web.evaluateJavascript(
        isInPictureInPictureMode ? "javascript:PIPlayer();" : "javascript:removePIP();",
        null
    );
    isPip = isInPictureInPictureMode;
  }

  @Override
  protected void onUserLeaveHint() {
    super.onUserLeaveHint();
   
    if (Build.VERSION.SDK_INT >= 26 && web.getUrl() != null && web.getUrl().contains("watch")) {
        if (isPlaying) {
            try {
                PictureInPictureParams params;
                isPip = true;
                if (portrait) {
                    params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(9, 16)).build();
                } else {
                    params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build();
                }
                enterPictureInPictureMode(params);
            } catch (IllegalStateException e) {
                Log.e("YTPRO", "PIP error", e);
            }
        }
    }
  }

  public class CustomWebClient extends WebChromeClient {
    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    private int mOriginalOrientation;
    private int mOriginalSystemUiVisibility;
    
    public CustomWebClient() {}

    public Bitmap getDefaultVideoPoster() {
      if (MainActivity.this == null) return null;
      return BitmapFactory.decodeResource(MainActivity.this.getApplicationContext().getResources(), 2130837573);
    }

    public void onShowCustomView(View paramView, WebChromeClient.CustomViewCallback viewCallback) {
      this.mOriginalOrientation = portrait ?
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT :
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
      
      if (isPip) this.mOriginalOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
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
      ((FrameLayout) MainActivity.this.getWindow().getDecorView()).addView(this.mCustomView, new FrameLayout.LayoutParams(-1, -1));
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
      if (web != null) web.clearFocus();
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
      public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
    Log.d("WebView Console", consoleMessage.message() + " -- From line "
            + consoleMessage.lineNumber() + " of "
            + consoleMessage.sourceId());
    return super.onConsoleMessage(consoleMessage);
}

  }
  

  private void downloadFile(String filename, String url, String mtype) {
    if (Build.VERSION.SDK_INT > 22 && Build.VERSION.SDK_INT < Build.VERSION_CODES.R && 
        checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
      runOnUiThread(() -> Toast.makeText(getApplicationContext(), R.string.grant_storage, Toast.LENGTH_SHORT).show());
      requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, 1);
      return;
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
      Log.e("YTPRO", "Download error", e);
      Toast.makeText(this, "Download error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  public class WebAppInterface {
    Context mContext;
    WebAppInterface(Context c) {
      mContext = c;
    }

    @JavascriptInterface
    public void showToast(String txt) {
      Toast.makeText(getApplicationContext(), txt + "", Toast.LENGTH_SHORT).show();
    }
    
    @JavascriptInterface
    public void gohome(String x) {
      Intent startMain = new Intent(Intent.ACTION_MAIN);
      startMain.addCategory(Intent.CATEGORY_HOME);
      startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(startMain);
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
      Intent i = new Intent();
      i.setAction(Intent.ACTION_VIEW);
      i.setData(Uri.parse(url));
      startActivity(i);
    }
    
    @JavascriptInterface
    public String getInfo() {
      PackageManager manager = getApplicationContext().getPackageManager();
      try {
        PackageInfo info = manager.getPackageInfo(getApplicationContext().getPackageName(), PackageManager.GET_ACTIVITIES);
        return info.versionName + "";
      } catch (PackageManager.NameNotFoundException e) {
        return "1.0";
      }
    }
    
    @JavascriptInterface
    public void setBgPlay(boolean bgplay) {
      SharedPreferences prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);
      prefs.edit().putBoolean("bgplay", bgplay).apply();
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
      duration = (long)(dura);
      isPlaying = true;

      getApplicationContext().sendBroadcast(new Intent("UPDATE_NOTIFICATION")
        .putExtra("icon", icon)
        .putExtra("title", title)
        .putExtra("subtitle", subtitle)
        .putExtra("duration", duration)
        .putExtra("currentPosition", 0)
        .putExtra("action", "pause")
      );
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
      getApplicationContext().sendBroadcast(new Intent("UPDATE_NOTIFICATION")
        .putExtra("icon", icon)
        .putExtra("title", title)
        .putExtra("subtitle", subtitle)
        .putExtra("duration", duration)
        .putExtra("currentPosition", ct)
        .putExtra("action", "pause")
      );
    }
    
    @JavascriptInterface
    public void bgPlay(long ct) {
      isPlaying = true;
      getApplicationContext().sendBroadcast(new Intent("UPDATE_NOTIFICATION")
        .putExtra("icon", icon)
        .putExtra("title", title)
        .putExtra("subtitle", subtitle)
        .putExtra("duration", duration)
        .putExtra("currentPosition", ct)
        .putExtra("action", "play")
      );
    }
    
    @JavascriptInterface
    public void bgBuffer(long ct) {
      isPlaying = true;
      getApplicationContext().sendBroadcast(new Intent("UPDATE_NOTIFICATION")
        .putExtra("icon", icon)
        .putExtra("title", title)
        .putExtra("subtitle", subtitle)
        .putExtra("duration", duration)
        .putExtra("currentPosition", ct)
        .putExtra("action", "buffer")
      );
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
      int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
      int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
      return maxVolume > 0 ? (float) currentVolume / maxVolume : 0.5f;
    }
    
    @JavascriptInterface
    public void setVolume(float volume) {
      int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
      int targetVolume = (int) (max * Math.max(0, Math.min(1, volume)));
      audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
    }
    
    @JavascriptInterface
    public float getBrightness() {
      try {
        int sysBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        return (sysBrightness / 255f) * 100f;
      } catch (Settings.SettingNotFoundException e) {
        return 50f;
      }
    }
    
    @JavascriptInterface
    public void setBrightness(final float brightnessValue) {
      runOnUiThread(() -> {
        float brightness = Math.max(0f, Math.min(brightnessValue, 1f));
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = brightness;
        getWindow().setAttributes(layout);
      });
    }
    
    @JavascriptInterface
    public void pipvid(String x) {
      if (Build.VERSION.SDK_INT >= 26) {
        try {
          PictureInPictureParams params;
          if (x.equals("portrait")) {
            params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(9, 16)).build();
          } else {
            params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build();
          }
          enterPictureInPictureMode(params);
        } catch (IllegalStateException e) {
          Log.e("YTPRO", "PIP error", e);
        }
      } else {
        Toast.makeText(getApplicationContext(), getString(R.string.no_pip), Toast.LENGTH_SHORT).show();
      }
    }
    
    @JavascriptInterface
    public void openDownloadSection() {
      runOnUiThread(() -> {
        dL = true;
        triggerDownloadSection();
      });
    }
  }

  public void setReceiver() {
    broadcastReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String action = intent.getStringExtra("actionname");
        Log.d("YTPRO", "Broadcast action: " + action);

        if (action != null) {
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
              String pos = intent.getStringExtra("pos");
              if (pos != null) {
                web.evaluateJavascript("seekTo('" + pos + "');", null);
              }
              break;
          }
        }
      }
    };

    IntentFilter filter = new IntentFilter("TRACKS_TRACKS");
    if (Build.VERSION.SDK_INT >= 34 && getApplicationInfo().targetSdkVersion >= 34) {
      registerReceiver(broadcastReceiver, filter, RECEIVER_EXPORTED);
    } else {
      registerReceiver(broadcastReceiver, filter);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    CookieManager.getInstance().flush();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    
    Intent intent = new Intent(getApplicationContext(), ForegroundService.class);
    stopService(intent);
    
    if (broadcastReceiver != null) {
      unregisterReceiver(broadcastReceiver);
    }
    
    if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
      getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
    }
  }
  
  private void checkNetworkStatus() {
    if (!isNetworkAvailable() && !isOffline) {
      runOnUiThread(() -> showOfflineScreen());
    } else if (isNetworkAvailable() && isOffline) {
      runOnUiThread(() -> hideOfflineScreen());
    }
    
    // Continue checking every 5 seconds
    new Handler().postDelayed(new Runnable() {
        @Override
        public void run() {
            checkNetworkStatus();
        }
    }, 5000);
  }

  private boolean isNetworkAvailable() {
    ConnectivityManager connectivityManager = 
        (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    
    if (connectivityManager != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;
            
            NetworkCapabilities capabilities = 
                connectivityManager.getNetworkCapabilities(network);
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
    if (isOffline) return;
    
    isOffline = true;
    
    runOnUiThread(() -> {
        // Hide WebView
        if (web != null) {
            web.setVisibility(View.GONE);
        }
        
        // Create offline layout
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
        
        // Icon
        TextView iconView = new TextView(this);
        iconView.setText("📡");
        iconView.setTextSize(80);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        centerLayout.addView(iconView);
        
        // Title
        TextView titleView = new TextView(this);
        titleView.setText("No internet connection");
        titleView.setTextSize(20);
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        centerLayout.addView(titleView);
        
        // Message
        TextView messageView = new TextView(this);
        messageView.setText("Check your Wi-Fi or mobile data connection.");
        messageView.setTextSize(14);
        messageView.setTextColor(Color.parseColor("#AAAAAA"));
        messageView.setGravity(Gravity.CENTER);
        messageView.setLayoutParams(new LinearLayout.LayoutParams(
            dpToPx(280),
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        centerLayout.addView(messageView);
        
        // Retry Button
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
        
        retryButton.setLayoutParams(new LinearLayout.LayoutParams(
            dpToPx(200),
            dpToPx(50)
        ));
        
        retryButton.setOnClickListener(v -> {
            if (isNetworkAvailable()) {
                hideOfflineScreen();
                if (web != null) {
                    web.setVisibility(View.VISIBLE);
                    web.reload();
                }
            } else {
                Toast.makeText(MainActivity.this, 
                    "Still no connection.", 
                    Toast.LENGTH_SHORT).show();
            }
        });
        
        centerLayout.addView(retryButton);
        offlineLayout.addView(centerLayout);
        
        addContentView(offlineLayout, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
    });
  }

  private void hideOfflineScreen() {
    if (!isOffline || offlineLayout == null) return;
    
    runOnUiThread(() -> {
        isOffline = false;
        if (offlineLayout.getParent() != null) {
            ((ViewGroup) offlineLayout.getParent()).removeView(offlineLayout);
        }
        if (web != null) {
            web.setVisibility(View.VISIBLE);
        }
    });
  }

  private int dpToPx(int dp) {
    float density = getResources().getDisplayMetrics().density;
    return Math.round(dp * density);
  }

  private void checkForAppUpdate() {
    new Handler().postDelayed(() -> {
        if (isNetworkAvailable()) {
            UpdateChecker updateChecker = new UpdateChecker(MainActivity.this);
            updateChecker.checkForUpdate();
        }
    }, 2000);
  }
  
  // Debug method to check script loading
  private void debugScriptStatus() {
      web.evaluateJavascript(
          "(function() {" +
          "  var status = {" +
          "    YTPRO_LOADED: window.YTPRO_LOADED || false," +
          "    ytproDownVid: typeof window.ytproDownVid," +
          "    showDownloadSection: typeof showDownloadSection," +
          "    initDownload: typeof initDownload," +
          "    locationHash: window.location.hash" +
          "  };" +
          "  console.log('🔍 Script Status:', JSON.stringify(status, null, 2));" +
          "  return JSON.stringify(status);" +
          "})();",
          new ValueCallback<String>() {
              @Override
              public void onReceiveValue(String value) {
                  Log.d("YTPRO_DEBUG", "Script Status: " + value);
              }
          }
      );
  }
}




