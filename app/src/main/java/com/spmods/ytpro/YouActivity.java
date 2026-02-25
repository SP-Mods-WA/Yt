package com.spmods.ytpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;

public class YouActivity extends Activity {

    private LinearLayout rootLayout;
    private ScrollView scrollView;
    private ImageView avatarImage;
    private TextView nameText, handleText;
    private WebView hiddenWeb;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String BG       = "#0F0F0F";
    private static final String CARD     = "#1A1A1A";
    private static final String ACCENT   = "#FF0000";
    private static final String TEXT_SEC = "#AAAAAA";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(Color.parseColor(BG));
        }
        buildUI();
        setupHiddenWebView();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Hidden WebView — uses existing YouTube session to fetch account data
    // ══════════════════════════════════════════════════════════════════════════
    private void setupHiddenWebView() {
        hiddenWeb = new WebView(this);
        hiddenWeb.getSettings().setJavaScriptEnabled(true);
        hiddenWeb.getSettings().setDomStorageEnabled(true);

        // Share cookies with main WebView
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(hiddenWeb, true);
        }

        hiddenWeb.addJavascriptInterface(new AccountBridge(), "AccountBridge");

        hiddenWeb.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (url.contains("youtube.com")) {
                    // Inject JS to extract account info from YouTube page
                    view.evaluateJavascript(
                        "(function(){" +
                        "  try {" +
                        "    var name = '';" +
                        "    var handle = '';" +
                        "    var avatar = '';" +
                        // Try ytInitialData
                        "    if (window.ytInitialData) {" +
                        "      var d = window.ytInitialData;" +
                        // Name from header
                        "      try { name = d.header.pageHeaderRenderer.content.pageHeaderViewModel.title.dynamicTextViewModel.text.content; } catch(e){}" +
                        "      if (!name) try { name = d.header.c4TabbedHeaderRenderer.title; } catch(e){}" +
                        // Handle
                        "      try { handle = d.header.c4TabbedHeaderRenderer.channelHandleText.runs[0].text; } catch(e){}" +
                        "      if (!handle) try { handle = d.header.pageHeaderRenderer.content.pageHeaderViewModel.metadata.contentMetadataViewModel.metadataRows[0].metadataParts[0].text.content; } catch(e){}" +
                        // Avatar
                        "      try {" +
                        "        var thumbs = d.header.c4TabbedHeaderRenderer.avatar.thumbnails;" +
                        "        avatar = thumbs[thumbs.length-1].url;" +
                        "      } catch(e){}" +
                        "      if (!avatar) try {" +
                        "        var thumbs2 = d.header.pageHeaderRenderer.content.pageHeaderViewModel.image.decoratedAvatarViewModel.avatar.avatarViewModel.image.sources;" +
                        "        avatar = thumbs2[thumbs2.length-1].url;" +
                        "      } catch(e){}" +
                        "    }" +
                        // Fallback: DOM elements
                        "    if (!name) {" +
                        "      var el = document.querySelector('#channel-name, .ytd-channel-name, #channel-header-container #text');" +
                        "      if (el) name = el.innerText;" +
                        "    }" +
                        "    if (avatar && avatar.startsWith('//')) avatar = 'https:' + avatar;" +
                        "    window.AccountBridge.onData(name || '', handle || '', avatar || '');" +
                        "  } catch(ex) {" +
                        "    window.AccountBridge.onData('', '', '');" +
                        "  }" +
                        "})();",
                        null
                    );
                }
            }
        });

        // Load YouTube channel/me page — already logged in via cookies
        hiddenWeb.loadUrl("https://m.youtube.com/channel/me");
    }

    // JS → Java bridge
    private class AccountBridge {
        @JavascriptInterface
        public void onData(String name, String handle, String avatar) {
            Log.d("YouActivity", "name=" + name + " handle=" + handle + " avatar=" + avatar);
            mainHandler.post(() -> {
                if (!name.isEmpty()) {
                    nameText.setText(name);
                } else {
                    nameText.setText("Not signed in");
                }
                if (!handle.isEmpty()) {
                    handleText.setText(handle);
                } else if (name.isEmpty()) {
                    handleText.setText("Sign in to YouTube first");
                } else {
                    handleText.setText("");
                }
                if (!avatar.isEmpty()) {
                    loadAvatar(avatar);
                }
            });
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UI
    // ══════════════════════════════════════════════════════════════════════════
    private void buildUI() {
        scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor(BG));

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        buildTopBar();
        buildProfileCard();

        addSectionLabel("Your content");
        addRow("History",       () -> goTo("https://m.youtube.com/feed/history"));
        addRow("Playlists",     () -> goTo("https://m.youtube.com/feed/library"));
        addRow("Your videos",   () -> goTo("https://m.youtube.com/channel/me/videos"));
        addRow("Liked videos",  () -> goTo("https://m.youtube.com/playlist?list=LL"));

        addSectionLabel("Account");
        addRow("Settings",      () -> goTo("https://m.youtube.com/account"));
        addRow("Notifications", () -> startActivity(new Intent(this, NotificationActivity.class)));
        addRow("Privacy",       () -> goTo("https://myaccount.google.com/privacy"));

        addSectionLabel("App");
        addRow("Background play", this::toggleBgPlay);
        addRow("App version",     this::showVersion);
        addRow("Sign out",        this::confirmSignOut);

        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(80)));
        rootLayout.addView(space);

        scrollView.addView(rootLayout);
        setContentView(scrollView);
    }

    private void buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.parseColor(BG));
        int statusH = getStatusBarHeight();
        bar.setPadding(dpToPx(16), statusH + dpToPx(8), dpToPx(16), dpToPx(8));
        bar.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView title = new TextView(this);
        title.setText("You");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(null, Typeface.BOLD);
        bar.addView(title);
        rootLayout.addView(bar);
    }

    private void buildProfileCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dpToPx(20), dpToPx(28), dpToPx(20), dpToPx(24));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dpToPx(16), dpToPx(8), dpToPx(16), 0);
        card.setLayoutParams(clp);
        card.setBackground(roundedBg(CARD, 16));

        avatarImage = new ImageView(this);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dpToPx(84), dpToPx(84));
        alp.bottomMargin = dpToPx(14);
        avatarImage.setLayoutParams(alp);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor("#333333"));
        avatarImage.setBackground(circle);
        avatarImage.setClipToOutline(true);
        avatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(avatarImage);

        nameText = new TextView(this);
        nameText.setText("Loading...");
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(19f);
        nameText.setTypeface(null, Typeface.BOLD);
        nameText.setGravity(Gravity.CENTER);
        card.addView(nameText);

        handleText = new TextView(this);
        handleText.setText("");
        handleText.setTextColor(Color.parseColor(TEXT_SEC));
        handleText.setTextSize(13f);
        handleText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dpToPx(2);
        handleText.setLayoutParams(hlp);
        card.addView(handleText);

        Button viewBtn = new Button(this);
        viewBtn.setText("View channel");
        viewBtn.setTextColor(Color.WHITE);
        viewBtn.setTextSize(14f);
        viewBtn.setAllCaps(false);
        viewBtn.setTypeface(null, Typeface.BOLD);
        viewBtn.setBackground(roundedBg(ACCENT, 20));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(40));
        blp.topMargin = dpToPx(16);
        blp.gravity = Gravity.CENTER;
        viewBtn.setLayoutParams(blp);
        viewBtn.setPadding(dpToPx(28), 0, dpToPx(28), 0);
        viewBtn.setOnClickListener(v -> goTo("https://m.youtube.com/channel/me"));
        card.addView(viewBtn);

        rootLayout.addView(card);
    }

    private void addSectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text.toUpperCase());
        tv.setTextColor(Color.parseColor(TEXT_SEC));
        tv.setTextSize(11f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dpToPx(24), dpToPx(20), dpToPx(16), dpToPx(6));
        tv.setLayoutParams(lp);
        rootLayout.addView(tv);
    }

    private void addRow(String label, Runnable action) {
        RelativeLayout row = new RelativeLayout(this);
        row.setBackground(roundedBg(CARD, 12));
        row.setPadding(dpToPx(16), 0, dpToPx(16), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(56));
        lp.setMargins(dpToPx(16), 0, dpToPx(16), dpToPx(2));
        row.setLayoutParams(lp);

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(Color.WHITE);
        lbl.setTextSize(15f);
        RelativeLayout.LayoutParams llp = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        llp.addRule(RelativeLayout.CENTER_VERTICAL);
        llp.addRule(RelativeLayout.ALIGN_PARENT_START);
        lbl.setLayoutParams(llp);
        row.addView(lbl);

        TextView chev = new TextView(this);
        chev.setText(">");
        chev.setTextColor(Color.parseColor(TEXT_SEC));
        chev.setTextSize(16f);
        RelativeLayout.LayoutParams clp = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        clp.addRule(RelativeLayout.CENTER_VERTICAL);
        clp.addRule(RelativeLayout.ALIGN_PARENT_END);
        chev.setLayoutParams(clp);
        row.addView(chev);

        row.setOnClickListener(v -> {
            v.animate().alpha(0.6f).setDuration(80)
                .withEndAction(() -> v.animate().alpha(1f).setDuration(80).start()).start();
            action.run();
        });
        rootLayout.addView(row);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Avatar loader
    // ══════════════════════════════════════════════════════════════════════════
    private void loadAvatar(String url) {
        new Thread(() -> {
            try {
                java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.connect();
                Bitmap raw = BitmapFactory.decodeStream(conn.getInputStream());
                if (raw == null) return;
                int size = Math.min(raw.getWidth(), raw.getHeight());
                Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(out);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                canvas.drawBitmap(raw, 0, 0, paint);
                mainHandler.post(() -> {
                    avatarImage.setBackground(null);
                    avatarImage.setImageBitmap(out);
                });
            } catch (Exception ignored) {}
        }).start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Actions
    // ══════════════════════════════════════════════════════════════════════════
    private void goTo(String url) {
        Intent i = new Intent(this, MainActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void toggleBgPlay() {
        SharedPreferences prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);
        boolean cur = prefs.getBoolean("bgplay", true);
        prefs.edit().putBoolean("bgplay", !cur).apply();
        Toast.makeText(this, "Background play " + (!cur ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
    }

    private void showVersion() {
        try {
            String ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            Toast.makeText(this, "YTPro v" + ver, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "YTPro", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(this)
            .setTitle("Sign out?")
            .setMessage("This will clear cookies and sign you out.")
            .setPositiveButton("Sign out", (d, w) -> {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
                Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();
                goTo("https://m.youtube.com/");
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════════
    private GradientDrawable roundedBg(String color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(color));
        d.setCornerRadius(dpToPx(radiusDp));
        return d;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dpToPx(24);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hiddenWeb != null) {
            hiddenWeb.stopLoading();
            hiddenWeb.destroy();
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
