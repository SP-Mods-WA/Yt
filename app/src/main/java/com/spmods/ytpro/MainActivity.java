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
    
    // Direct GitHub URLs - මේවා work වෙනවා
    private static final String SCRIPT_URL = "https://raw.githubusercontent.com/SP-Mods-WA/Yt/main/scripts/script.js";
    private static final String BGPLAY_URL = "https://raw.githubusercontent.com/SP-Mods-WA/Yt/main/scripts/bgplay.js";
    private static final String INNERTUBE_URL = "https://raw.githubusercontent.com/SP-Mods-WA/Yt/main/scripts/innertube.js";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        // Debugging enable
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
            checkForAppUpdate();
        }
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void load(boolean dl) {
        web = findViewById(R.id.web);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
       
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setSupportZoom(true);
        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setDisplayZoomControls(false);

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
        
        web.loadUrl(url);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setDatabaseEnabled(true);
        web.addJavascriptInterface(new WebAppInterface(this), "Android");
        web.setWebChromeClient(new CustomWebClient());
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(web, true);
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                
                Log.d("WebView", "🌐 Requesting: " + url);

                // Handle CDN requests with FIXED URLs
                if (url.contains("youtube.com/ytpro_cdn/")) {
                    try {
                        String modifiedUrl = null;
                        
                        if (url.contains("script.js")) {
                            modifiedUrl = SCRIPT_URL;
                        } else if (url.contains("bgplay.js")) {
                            modifiedUrl = BGPLAY_URL;
                        } else if (url.contains("innertube.js")) {
                            modifiedUrl = INNERTUBE_URL;
                        } else if (url.contains("youtube.com/ytpro_cdn/esm")) {
                            modifiedUrl = url.replace("youtube.com/ytpro_cdn/esm", "https://esm.sh");
                        }
                        
                        if (modifiedUrl != null) {
                            Log.d("CDN", "✅ Redirecting to: " + modifiedUrl);
                            return fetchURL(modifiedUrl);
                        }
                        
                    } catch (Exception e) {
                        Log.e("CDN", "❌ Error: " + e.getMessage());
                    }
                }
                
                return super.shouldInterceptRequest(view, request);
            }
            
            private WebResourceResponse fetchURL(String urlString) {
                try {
                    URL url = new URL(urlString);
                    HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                    
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
                    connection.setRequestProperty("Accept", "*/*");
                    connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                    connection.setRequestProperty("Cache-Control", "no-cache");
                    
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    connection.setRequestMethod("GET");
                    connection.connect();
                    
                    int responseCode = connection.getResponseCode();
                    Log.d("CDN", "📡 Response: " + responseCode + " from " + urlString);
                    
                    if (responseCode != 200) {
                        Log.e("CDN", "❌ Failed: " + responseCode);
                        return null;
                    }
                    
                    String contentType = connection.getContentType();
                    if (contentType == null || contentType.isEmpty()) {
                        contentType = "application/javascript";
                    }
                    
                    // Clean content type
                    if (contentType.contains(";")) {
                        contentType = contentType.split(";")[0].trim();
                    }
                    
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Access-Control-Allow-Origin", "*");
                    headers.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                    headers.put("Access-Control-Allow-Headers", "*");
                    headers.put("Content-Type", contentType + "; charset=utf-8");
                    headers.put("Cross-Origin-Resource-Policy", "cross-origin");
                    
                    return new WebResourceResponse(
                        contentType,
                        "utf-8",
                        connection.getResponseCode(),
                        "OK",
                        headers,
                        connection.getInputStream()
                    );
                    
                } catch (Exception e) {
                    Log.e("CDN", "❌ Fetch error: " + e.getMessage());
                    return null;
                }
            }
            
            @Override
            public void onPageStarted(WebView p1, String p2, Bitmap p3) {
                Log.d("Page", "🚀 Started: " + p2);
                super.onPageStarted(p1, p2, p3);
            }

            @Override
            public void onPageFinished(WebView p1, String url) {
                Log.d("Page", "✅ Finished: " + url);
                
                // Setup TrustedTypes
                web.evaluateJavascript(
                    "if (window.trustedTypes && window.trustedTypes.createPolicy) {" +
                    "  window.trustedTypes.createPolicy('default', {" +
                    "    createHTML: s => s," +
                    "    createScriptURL: s => s," +
                    "    createScript: s => s" +
                    "  });" +
                    "}",
                    null
                );
                
                // Enhanced script loader with FALLBACKS
                String scriptLoader = 
                    "(function() {" +
                    "  console.log('🔄 YTPRO Script Loader Starting...');" +
                    "  " +
                    "  var scripts = [" +
                    "    {name: 'script', url: 'https://youtube.com/ytpro_cdn/npm/ytpro/script.js', fallback: '" + SCRIPT_URL + "'}," +
                    "    {name: 'bgplay', url: 'https://youtube.com/ytpro_cdn/npm/ytpro/bgplay.js', fallback: '" + BGPLAY_URL + "'}," +
                    "    {name: 'innertube', url: 'https://youtube.com/ytpro_cdn/npm/ytpro/innertube.js', fallback: '" + INNERTUBE_URL + "'}" +
                    "  ];" +
                    "  " +
                    "  function loadScript(scriptObj, retryCount) {" +
                    "    return new Promise((resolve, reject) => {" +
                    "      var url = retryCount > 0 ? scriptObj.fallback : scriptObj.url;" +
                    "      var cacheBuster = '?v=' + Date.now();" +
                    "      var finalUrl = url + cacheBuster;" +
                    "      " +
                    "      console.log('📥 Loading ' + scriptObj.name + ' from: ' + finalUrl);" +
                    "      " +
                    "      var script = document.createElement('script');" +
                    "      script.src = finalUrl;" +
                    "      script.async = false;" +
                    "      " +
                    "      script.onload = function() {" +
                    "        console.log('✅ Loaded: ' + scriptObj.name);" +
                    "        resolve();" +
                    "      };" +
                    "      " +
                    "      script.onerror = function(e) {" +
                    "        console.error('❌ Failed: ' + scriptObj.name, e);" +
                    "        if (retryCount === 0 && scriptObj.fallback) {" +
                    "          console.log('🔄 Trying fallback for ' + scriptObj.name);" +
                    "          loadScript(scriptObj, 1).then(resolve).catch(reject);" +
                    "        } else {" +
                    "          reject();" +
                    "        }" +
                    "      };" +
                    "      " +
                    "      document.body.appendChild(script);" +
                    "    });" +
                    "  }" +
                    "  " +
                    "  // Load scripts sequentially
                    "  async function loadAllScripts() {" +
                    "    for (var i = 0; i < scripts.length; i++) {" +
                    "      try {" +
                    "        await loadScript(scripts[i], 0);" +
                    "      } catch (e) {" +
                    "        console.error('💥 All sources failed for: ' + scripts[i].name);" +
                    "      }" +
                    "    }" +
                    "    " +
                    "    console.log('🎉 All scripts attempted to load');" +
                    "    window.YTPRO_LOADED = true;" +
                    "    " +
                    "    // Initialize YTPRO if function exists
                    "    if (typeof window.initYTPro === 'function') {" +
                    "      window.initYTPro();" +
                    "    }" +
                    "  }" +
                    "  " +
                    "  // Start loading
                    "  loadAllScripts();" +
                    "})();";
                
                web.evaluateJavascript(scriptLoader, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        Log.d("WebView", "📜 Script loader injected");
                    }
                });

                // Check if download hash is present
                if (dl) {
                    web.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            web.evaluateJavascript(
                                "if (typeof window.ytproDownVid === 'function') {" +
                                "  console.log('⬇️ Triggering download...');" +
                                "  window.location.hash='download';" +
                                "} else {" +
                                "  console.log('⏳ Waiting for ytproDownVid...');" +
                                "  setTimeout(function() {" +
                                "    if (typeof window.ytproDownVid === 'function') {" +
                                "      window.location.hash='download';" +
                                "    }" +
                                "  }, 3000);" +
                                "}",
                                null
                            );
                            dL = false;
                        }
                    }, 3000);
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
                Log.e("WebError", errorCode + ": " + description + " - " + failingUrl);
                
                if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || 
                    errorCode == WebViewClient.ERROR_CONNECT || 
                    errorCode == WebViewClient.ERROR_TIMEOUT) {
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showOfflineScreen();
                        }
                    });
                }
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
            
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.e("WebError", "Advanced: " + error.getErrorCode() + " - " + error.getDescription());
                    
                    if (request.isForMainFrame()) {
                        int errorCode = error.getErrorCode();
                        if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || 
                            errorCode == WebViewClient.ERROR_CONNECT || 
                            errorCode == WebViewClient.ERROR_TIMEOUT) {
                            
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    showOfflineScreen();
                                }
                            });
                        }
                    }
                }
                super.onReceivedError(view, request, error);
            }
        });

        setReceiver();

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
            
            dispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                backCallback
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                web.loadUrl("https://m.youtube.com");
            } else {
                Toast.makeText(getApplicationContext(), "Microphone permission required", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(getApplicationContext(), "Storage permission required", Toast.LENGTH_SHORT).show();
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
       
        if (android.os.Build.VERSION.SDK_INT >= 26 && web.getUrl() != null && web.getUrl().contains("watch")) {
            if(isPlaying) {
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
                    e.printStackTrace();
                }
            }
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
            if (MainActivity.this == null) {
                return null;
            }
            // Create simple placeholder if resource doesn't exist
            Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.DKGRAY);
            return bitmap;
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
            
            this.mCustomViewCallback.onCustomViewHidden();
            this.mCustomViewCallback = null;
            web.clearFocus();
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            if (Build.VERSION.SDK_INT > 22 && request.getOrigin().toString().contains("youtube.com")) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
                    requestPermissions(new String[] {
                        Manifest.permission.RECORD_AUDIO
                    }, 101);
                } else {
                    request.grant(request.getResources());
                }
            }
        }
        
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            Log.d("WebConsole", consoleMessage.message() + " at " + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber());
            return true;
        }
    }

    private void downloadFile(String filename, String url, String mtype) {
        if (Build.VERSION.SDK_INT > 22 && Build.VERSION.SDK_INT < Build.VERSION_CODES.R && 
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Storage permission required", Toast.LENGTH_SHORT).show());
            requestPermissions(new String[] {
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1);
            return;
        }
        
        try {
            String encodedFileName = URLEncoder.encode(filename, "UTF-8").replaceAll("\\+", "%20");

            DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            
            request.setTitle(filename)
                .setDescription("Downloading " + filename)
                .setMimeType(mtype)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, encodedFileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            
            downloadManager.enqueue(request);
            Toast.makeText(this, "Download started: " + filename, Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public class WebAppInterface {
        Context mContext;
        
        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void showToast(String txt) {
            Toast.makeText(getApplicationContext(), txt, Toast.LENGTH_SHORT).show();
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
                return info.versionName;
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
            int targetVolume = Math.max(0, Math.min((int)(max * volume), max));
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
        }
        
        @JavascriptInterface
        public float getBrightness() {
            float brightnessPercent;
            
            try {
                int sysBrightness = Settings.System.getInt(
                    getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS
                );
                brightnessPercent = (sysBrightness / 255f) * 100f;
            } catch (Settings.SettingNotFoundException e) {
                brightnessPercent = 50f;
            }
            
            return brightnessPercent;
        }
        
        @JavascriptInterface
        public void setBrightness(final float brightnessValue) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    float brightness = Math.max(0f, Math.min(brightnessValue, 1f));
                    WindowManager.LayoutParams layout = getWindow().getAttributes();
                    layout.screenBrightness = brightness;
                    getWindow().setAttributes(layout);
                }
            });     
        }
        
        @JavascriptInterface
        public void pipvid(String x) {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                try {
                    PictureInPictureParams params;
                    if (x.equals("portrait")) {
                        params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(9, 16)).build();
                    } else {
                        params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build();
                    }
                    enterPictureInPictureMode(params);
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            } else {
                Toast.makeText(getApplicationContext(), "PiP not available", Toast.LENGTH_SHORT).show();
            }
        }
        
        @JavascriptInterface
        public void log(String message) {
            Log.d("JSLog", message);
        }
    }

    public void setReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getExtras().getString("actionname");

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
                        String pos = intent.getExtras().getString("pos");
                        if (pos != null) {
                            web.evaluateJavascript("if(typeof seekTo === 'function') seekTo('" + pos + "');", null);
                        }
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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (isPlaying) {
            Intent intent = new Intent(getApplicationContext(), ForegroundService.class);
            stopService(intent);
        }

        if (broadcastReceiver != null) unregisterReceiver(broadcastReceiver);

        if (android.os.Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
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
        isOffline = true;
        
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                offlineLayout = new RelativeLayout(MainActivity.this);
                offlineLayout.setLayoutParams(new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT
                ));
                offlineLayout.setBackgroundColor(Color.BLACK);
                
                LinearLayout centerLayout = new LinearLayout(MainActivity.this);
                centerLayout.setOrientation(LinearLayout.VERTICAL);
                centerLayout.setGravity(Gravity.CENTER);
                
                RelativeLayout.LayoutParams centerParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                );
                centerParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                centerLayout.setLayoutParams(centerParams);
                
                TextView iconView = new TextView(MainActivity.this);
                iconView.setText("📡");
                iconView.setTextSize(50);
                iconView.setTextColor(Color.WHITE);
                iconView.setGravity(Gravity.CENTER);
                centerLayout.addView(iconView);
                
                TextView titleView = new TextView(MainActivity.this);
                titleView.setText("No Internet Connection");
                titleView.setTextSize(18);
                titleView.setTextColor(Color.WHITE);
                titleView.setTypeface(null, Typeface.BOLD);
                titleView.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                titleParams.setMargins(0, 20, 0, 10);
                titleView.setLayoutParams(titleParams);
                centerLayout.addView(titleView);
                
                TextView messageView = new TextView(MainActivity.this);
                messageView.setText("Check your Wi-Fi or mobile data connection");
                messageView.setTextSize(14);
                messageView.setTextColor(Color.LTGRAY);
                messageView.setGravity(Gravity.CENTER);
                centerLayout.addView(messageView);
                
                Button retryButton = new Button(MainActivity.this);
                retryButton.setText("RETRY");
                retryButton.setTextColor(Color.WHITE);
                retryButton.setBackgroundColor(Color.RED);
                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(200, 50);
                btnParams.setMargins(0, 30, 0, 0);
                retryButton.setLayoutParams(btnParams);
                
                retryButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (isNetworkAvailable()) {
                            hideOfflineScreen();
                            web.reload();
                        } else {
                            Toast.makeText(MainActivity.this, "Still offline", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                
                centerLayout.addView(retryButton);
                offlineLayout.addView(centerLayout);
                
                ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
                root.addView(offlineLayout);
                
                if (web != null) {
                    web.setVisibility(View.GONE);
                }
            }
        });
    }

    private void hideOfflineScreen() {
        if (!isOffline || offlineLayout == null) return;
        
        isOffline = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (web != null) {
                    web.setVisibility(View.VISIBLE);
                }
                
                ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
                root.removeView(offlineLayout);
                offlineLayout = null;
            }
        });
    }

    private void checkForAppUpdate() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isNetworkAvailable()) {
                    Log.d("Update", "Checking for updates...");
                }
            }
        }, 2000);
    }
}
