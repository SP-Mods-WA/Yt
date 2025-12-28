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
    
    // Native encryption library
    static {
        System.loadLibrary("ytproencrypt");
    }
    
    // Native method declarations
    public native String encryptStringNative(String input);
    public native String decryptStringNative(String input);
    public native boolean isValidEncryptedUrl(String encryptedUrl);
    public native String getEncryptedEndpoint(int endpointType);
    public native String encryptCookies(String cookies);
    public native String generateDownloadHash(String videoId, String quality);
    public native boolean verifyScriptIntegrity(String scriptContent, String expectedHash);
    public native String getSecureKey(int keyType);
    public native String encryptRequestData(String data);
    public native String decryptResponseData(String data);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        // Initialize native encryption
        initializeNativeEncryption();

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
        
        MainActivity.this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    
    private void initializeNativeEncryption() {
        try {
            // Generate and store secure key
            String secureKey = getSecureKey(0);
            Log.d("Encryption", "Native encryption initialized with key: " + secureKey.substring(0, 10) + "...");
        } catch (Exception e) {
            Log.e("Encryption", "Failed to initialize native encryption", e);
        }
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
        
        // Handle encrypted URLs
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            String intentUrl = data.toString();
            if (intentUrl.startsWith("ytpro://enc/")) {
                // Decrypt encrypted URL
                String encrypted = intentUrl.substring(12);
                url = decryptStringNative(encrypted);
            } else {
                url = intentUrl;
            }
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

                // Handle encrypted URLs
                if (url.startsWith("enc://")) {
                    try {
                        String decryptedUrl = decryptStringNative(url.substring(6));
                        Log.d("Encryption", "Decrypted URL: " + decryptedUrl);
                        return handleEncryptedRequest(decryptedUrl);
                    } catch (Exception e) {
                        Log.e("Encryption", "Failed to decrypt URL", e);
                        return super.shouldInterceptRequest(view, request);
                    }
                }
                
                if (url.contains("youtube.com/ytpro_cdn/")) {
                    String modifiedUrl = null;

                    if (url.contains("youtube.com/ytpro_cdn/esm")) {
                        modifiedUrl = url.replace("youtube.com/ytpro_cdn/esm", "esm.sh");
                        Log.d("CDN", "✅ ESM Redirect: " + modifiedUrl);
                        
                    } else if (url.contains("youtube.com/ytpro_cdn/npm/ytpro")) {
                        if (url.contains("innertube.js")) {
                            // Use encrypted endpoint
                            modifiedUrl = decryptStringNative(getEncryptedEndpoint(2));
                        } else if (url.contains("bgplay.js")) {
                            modifiedUrl = decryptStringNative(getEncryptedEndpoint(1));
                        } else if (url.contains("script.js")) {
                            modifiedUrl = decryptStringNative(getEncryptedEndpoint(0));
                        } else {
                            modifiedUrl = url.replace(
                                "youtube.com/ytpro_cdn/npm/ytpro/", 
                                "cdn.jsdelivr.net/gh/SP-Mods-WA/Yt@main/scripts/"
                            );
                        }
                        Log.d("CDN", "✅ jsDelivr Redirect: " + modifiedUrl);
                    }
                    
                    if (modifiedUrl == null) {
                        Log.e("CDN", "❌ modifiedUrl is NULL for: " + url);
                        return super.shouldInterceptRequest(view, request);
                    }
                    
                    try {
                        URL newUrl = new URL(modifiedUrl);
                        HttpsURLConnection connection = (HttpsURLConnection) newUrl.openConnection();

                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
                        connection.setRequestProperty("Accept", "*/*");
                        connection.setRequestProperty("Cache-Control", "no-cache");
                        connection.setRequestProperty("Pragma", "no-cache");
                        
                        connection.setConnectTimeout(15000);
                        connection.setReadTimeout(15000);
                        connection.setRequestMethod("GET");
                        connection.connect();

                        int responseCode = connection.getResponseCode();
                        Log.d("CDN", "📡 Response: " + responseCode + " for " + modifiedUrl);
                        
                        if (responseCode != 200) {
                            Log.e("CDN", "❌ Failed: " + responseCode);
                            return super.shouldInterceptRequest(view, request);
                        }

                        String contentType = connection.getContentType();
                        if (contentType == null || contentType.isEmpty()) {
                            if (modifiedUrl.endsWith(".js")) {
                                contentType = "application/javascript";
                            } else {
                                contentType = "text/plain";
                            }
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
                            connection.getResponseCode(),
                            "OK",
                            headers,
                            connection.getInputStream()
                        );

                    } catch (Exception e) {
                        Log.e("CDN Error", "❌ Exception for " + modifiedUrl + ": " + e.getMessage());
                        e.printStackTrace();
                        return super.shouldInterceptRequest(view, request);
                    }
                }

                return super.shouldInterceptRequest(view, request);
            }
            
            private WebResourceResponse handleEncryptedRequest(String url) {
                try {
                    URL requestUrl = new URL(url);
                    HttpsURLConnection connection = (HttpsURLConnection) requestUrl.openConnection();
                    
                    // Add encrypted headers
                    String encryptedHeaders = encryptRequestData("{\"User-Agent\":\"Mozilla/5.0\",\"Accept\":\"*/*\"}");
                    connection.setRequestProperty("X-Encrypted-Headers", encryptedHeaders);
                    
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    connection.connect();

                    if (connection.getResponseCode() != 200) {
                        return null;
                    }

                    // Read and decrypt response
                    InputStream inputStream = connection.getInputStream();
                    ByteArrayOutputStream result = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        result.write(buffer, 0, length);
                    }
                    
                    String encryptedResponse = result.toString("UTF-8");
                    String decryptedContent = decryptResponseData(encryptedResponse);
                    
                    return new WebResourceResponse(
                        "application/javascript",
                        "utf-8",
                        new ByteArrayInputStream(decryptedContent.getBytes("UTF-8"))
                    );
                    
                } catch (Exception e) {
                    Log.e("Encryption", "Error handling encrypted request", e);
                    return null;
                }
            }
            
            @Override
            public void onPageStarted(WebView p1, String p2, Bitmap p3) {
                super.onPageStarted(p1, p2, p3);
            }

            @Override
            public void onPageFinished(WebView p1, String url) {
                // Setup TrustedTypes first
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
                
                // Load encrypted scripts
                String scriptLoader = 
                    "(function() {" +
                    "  console.log('🔄 Starting YTPRO script loader...');" +
                    
                    "  function loadEncryptedScript(encryptedSrc, name) {" +
                    "    return new Promise((resolve, reject) => {" +
                    "      console.log('🔐 Loading encrypted ' + name);" +
                    "      Android.loadEncryptedScript(encryptedSrc, function(decryptedScript) {" +
                    "        try {" +
                    "          eval(decryptedScript);" +
                    "          console.log('✅ Loaded and decrypted ' + name);" +
                    "          resolve();" +
                    "        } catch (e) {" +
                    "          console.error('❌ Failed to eval ' + name + ':', e);" +
                    "          reject(e);" +
                    "        }" +
                    "      });" +
                    "    });" +
                    "  }" +
                    
                    "  function loadRegularScript(src, name) {" +
                    "    return new Promise((resolve, reject) => {" +
                    "      console.log('📥 Loading ' + name + ': ' + src);" +
                    "      var script = document.createElement('script');" +
                    "      script.src = src;" +
                    "      script.async = false;" +
                    "      script.onload = function() {" +
                    "        console.log('✅ Loaded ' + name);" +
                    "        resolve();" +
                    "      };" +
                    "      script.onerror = function(e) {" +
                    "        console.error('❌ Failed to load ' + name + ':', e);" +
                    "        reject(new Error('Failed to load ' + name));" +
                    "      };" +
                    "      document.body.appendChild(script);" +
                    "    });" +
                    "  }" +
                    
                    "  // Load scripts" +
                    "  loadRegularScript('https://youtube.com/ytpro_cdn/npm/ytpro/script.js', 'Main Script')" +
                    "    .then(() => loadRegularScript('https://youtube.com/ytpro_cdn/npm/ytpro/bgplay.js', 'BG Play'))" +
                    "    .then(() => loadRegularScript('https://youtube.com/ytpro_cdn/npm/ytpro/innertube.js', 'InnerTube'))" +
                    "    .then(() => {" +
                    "      console.log('✅ All YTPRO scripts loaded successfully!');" +
                    "      window.YTPRO_LOADED = true;" +
                    "    })" +
                    "    .catch((error) => {" +
                    "      console.error('❌ YTPRO script loading failed:', error);" +
                    "      window.YTPRO_LOADED = false;" +
                    "    });" +
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
                                "  window.location.hash='download';" +
                                "} else {" +
                                "  console.error('❌ ytproDownVid not available yet');" +
                                "}",
                                null
                            );
                            dL = false;
                        }
                    }, 2000);
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
        web.loadUrl(isInPictureInPictureMode ?
            "javascript:PIPlayer();" :
            "javascript:removePIP();", null);
            
        if(isInPictureInPictureMode){
            isPip=true;
        } else {
            isPip=false;
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
       
        if (android.os.Build.VERSION.SDK_INT >= 26 && web.getUrl().contains("watch")) {
            if(isPlaying){
                try {
                    PictureInPictureParams params;
                    isPip=true;
                    if (portrait) {
                        params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(9, 16)).build();
                        enterPictureInPictureMode(params);
                    } else {
                        params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build();
                        enterPictureInPictureMode(params);
                    }
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
            return BitmapFactory.decodeResource(MainActivity.this.getApplicationContext().getResources(), 2130837573);
        }

        public void onShowCustomView(View paramView, WebChromeClient.CustomViewCallback viewCallback) {
            this.mOriginalOrientation = portrait ?
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT :
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
            
            if (isPip) this.mOriginalOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
            

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
            this.mOriginalOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
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
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT :
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;

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
    }

    private void downloadFile(String filename, String url, String mtype) {
        if (Build.VERSION.SDK_INT > 22 && Build.VERSION.SDK_INT < Build.VERSION_CODES.R && 
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), R.string.grant_storage, Toast.LENGTH_SHORT).show());
            requestPermissions(new String[] {
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1);
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
            
            // Add encrypted metadata
            String encryptedMeta = encryptStringNative("{\"app\":\"YTPro\",\"timestamp\":" + System.currentTimeMillis() + "}");
            request.addRequestHeader("X-Encrypted-Meta", encryptedMeta);
            
            downloadManager.enqueue(request);
            Toast.makeText(this, getString(R.string.dl_started), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
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
        public void secureDownload(String encryptedData) {
            try {
                String decrypted = decryptStringNative(encryptedData);
                JSONObject data = new JSONObject(decrypted);
                downloadFile(
                    data.getString("filename"),
                    data.getString("url"),
                    data.getString("mimeType")
                );
            } catch (Exception e) {
                Log.e("SecureDownload", "Failed to process encrypted download", e);
                Toast.makeText(getApplicationContext(), "Download failed", Toast.LENGTH_SHORT).show();
            }
        }
        
        @JavascriptInterface
        public void loadEncryptedScript(String encryptedScript, final ValueCallback<String> callback) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String decrypted = decryptStringNative(encryptedScript);
                        final String finalScript = decrypted;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                callback.onReceiveValue(finalScript);
                            }
                        });
                    } catch (Exception e) {
                        Log.e("EncryptedScript", "Failed to decrypt script", e);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                callback.onReceiveValue("");
                            }
                        });
                    }
                }
            }).start();
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

            // Encrypt notification data
            String encryptedIcon = encryptStringNative(icon);
            String encryptedTitle = encryptStringNative(title);
            
            // Add extras to the Intent
            intent.putExtra("icon", encryptedIcon);
            intent.putExtra("title", encryptedTitle);
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
                .putExtra("icon", encryptStringNative(icon))
                .putExtra("title", encryptStringNative(title))
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
                .putExtra("icon", encryptStringNative(icon))
                .putExtra("title", encryptStringNative(title))
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
                .putExtra("icon", encryptStringNative(icon))
                .putExtra("title", encryptStringNative(title))
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
                .putExtra("icon", encryptStringNative(icon))
                .putExtra("title", encryptStringNative(title))
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
                // Encrypt response before sending to JS
                String encryptedResponse = encryptStringNative(response);
                runOnUiThread(() -> web.evaluateJavascript("callbackSNlM0e.resolve(`" + encryptedResponse + "`)", null));
            }).start();
        }
        
        @JavascriptInterface
        public void GeminiClient(String url, String headers, String body) {
            new Thread(() -> {
                JSONObject response = GeminiWrapper.getStream(url, headers, body);
                // Encrypt sensitive parts of response
                try {
                    if (response.has("url")) {
                        String originalUrl = response.getString("url");
                        response.put("url", encryptStringNative(originalUrl));
                    }
                } catch (JSONException e) {
                    Log.e("GeminiClient", "Error encrypting response", e);
                }
                runOnUiThread(() -> web.evaluateJavascript("callbackGeminiClient.resolve(" + response + ")", null));
            }).start();
        }
        
        @JavascriptInterface
        public String getAllCookies(String url) {
            String cookies = CookieManager.getInstance().getCookie(url);
            // Encrypt cookies before returning
            return encryptCookies(cookies);
        }
        
        @JavascriptInterface
        public String getSecureCookies(String url) {
            String cookies = CookieManager.getInstance().getCookie(url);
            return encryptCookies(cookies);
        }
        
        @JavascriptInterface
        public float getVolume() {
            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            return (float) currentVolume / maxVolume;
        }
        
        @JavascriptInterface
        public void setVolume(float volume) {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int targetVolume = (int) (max * volume);
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
                    final float brightness = Math.max(0f, Math.min(brightnessValue, 1f));
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
                Toast.makeText(getApplicationContext(), getString(R.string.no_pip), Toast.LENGTH_SHORT).show();
            }
        }
        
        @JavascriptInterface
        public String encryptData(String data) {
            return encryptStringNative(data);
        }
        
        @JavascriptInterface
        public String decryptData(String encryptedData) {
            return decryptStringNative(encryptedData);
        }
        
        @JavascriptInterface
        public String generateSecureHash(String videoId, String quality) {
            return generateDownloadHash(videoId, quality);
        }
        
        @JavascriptInterface
        public String getSecureEndpoint(int type) {
            return getEncryptedEndpoint(type);
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
                        Log.e("play", "play called");
                        break;
                    case "PAUSE_ACTION":
                        web.evaluateJavascript("pauseVideo();", null);
                        Log.e("pause", "pause called");
                        break;
                    case "NEXT_ACTION":
                        web.evaluateJavascript("playNext();", null);
                        break;
                    case "PREV_ACTION":
                        web.evaluateJavascript("playPrev();", null);
                        break;
                    case "SEEKTO":
                        String encryptedPos = intent.getExtras().getString("pos");
                        String decryptedPos = decryptStringNative(encryptedPos);
                        web.evaluateJavascript("seekTo('" + decryptedPos + "');", null);
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
        
        // Encrypt session data
        String sessionData = "{\"url\":\"" + web.getUrl() + "\",\"timestamp\":" + System.currentTimeMillis() + "}";
        String encryptedSession = encryptStringNative(sessionData);
        
        SharedPreferences prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);
        prefs.edit().putString("last_session", encryptedSession).apply();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Restore encrypted session
        SharedPreferences prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);
        String encryptedSession = prefs.getString("last_session", "");
        if (!encryptedSession.isEmpty()) {
            try {
                String sessionData = decryptStringNative(encryptedSession);
                JSONObject session = new JSONObject(sessionData);
                // Could use this to restore state
            } catch (Exception e) {
                Log.e("Session", "Failed to restore session", e);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Intent intent = new Intent(getApplicationContext(), ForegroundService.class);
        stopService(intent);

        if (broadcastReceiver != null) unregisterReceiver(broadcastReceiver);

        if (android.os.Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
    }

    private void checkScriptStatus() {
        web.evaluateJavascript(
            "(function() {" +
            "  return JSON.stringify({" +
            "    loaded: window.YTPRO_LOADED || false," +
            "    hasMainScript: typeof YTProVer !== 'undefined'," +
            "    hasInnerTube: typeof window.getDownloadStreams !== 'undefined'," +
            "    hasBgPlay: typeof window.initBgPlay !== 'undefined'" +
            "  });" +
            "})();",
            new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    Log.d("YTPRO Status", "📊 Script Status: " + value);
                }
            }
        );
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
        
        // Create main layout
        offlineLayout = new RelativeLayout(this);
        offlineLayout.setLayoutParams(new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        offlineLayout.setBackgroundColor(Color.parseColor("#0F0F0F"));
        
        // Create center container
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
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        iconParams.bottomMargin = dpToPx(24);
        iconView.setLayoutParams(iconParams);
        iconView.setGravity(Gravity.CENTER);
        centerLayout.addView(iconView);
        
        // Title
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
        
        // Message
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
        
        // Retry Button
        Button retryButton = new Button(this);
        retryButton.setText("Try again.");
        retryButton.setTextColor(Color.WHITE);
        retryButton.setTextSize(16);
        retryButton.setTypeface(null, Typeface.BOLD);
        retryButton.setAllCaps(false);
        
        // Button styling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            retryButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#FF0000"))
            );
        } else {
            retryButton.setBackgroundColor(Color.parseColor("#FF0000"));
        }
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            dpToPx(200),
            dpToPx(50)
        );
        retryButton.setLayoutParams(btnParams);
        
        // Retry button click
        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isNetworkAvailable()) {
                    hideOfflineScreen();
                    load(false);
                } else {
                    Toast.makeText(MainActivity.this, 
                        "Still no connection.", 
                        Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        centerLayout.addView(retryButton);
        offlineLayout.addView(centerLayout);
        
        // Add to main view
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
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isNetworkAvailable()) {
                    UpdateChecker updateChecker = new UpdateChecker(MainActivity.this);
                    updateChecker.checkForUpdate();
                }
            }
        }, 2000);
    }
    
    // Helper method for encrypted network requests
    private String fetchEncryptedData(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            // Add encrypted headers
            String encryptedHeaders = encryptStringNative("{\"timestamp\":" + System.currentTimeMillis() + "}");
            connection.setRequestProperty("X-Encrypted-Headers", encryptedHeaders);
            
            InputStream inputStream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            return response.toString();
        } catch (Exception e) {
            Log.e("Network", "Failed to fetch encrypted data", e);
            return "";
        }
    }
}
