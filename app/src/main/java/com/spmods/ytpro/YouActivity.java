package com.spmods.ytpro;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/*
 * YouActivity — "You" tab content
 *
 * ✅ Header  → MainActivity එකේ existing header use කරනවා (R.id.header)
 * ✅ Bottom nav → MainActivity එකේ existing bottom nav (R.id.bottomNav)
 * ✅ WebView area → You tab content replace කරනවා (R.id.webContainer)
 *
 * MainActivity layout structure assumed:
 *
 *   <FrameLayout> (root)
 *     ├── <RelativeLayout id="header" />         ← sticky top bar
 *     ├── <FrameLayout id="webContainer" />      ← WebView / content area
 *     └── <LinearLayout id="bottomNav" />        ← bottom navigation
 *   </FrameLayout>
 *
 * YouActivity හදන විදිහ:
 *   MainActivity layout inflate කරනවා → webContainer hide → YouContent show
 *   Bottom nav + header → same as MainActivity, "You" tab active කරනවා
 */

public class YouActivity extends Activity {

    // ── Colors (same as your existing app theme) ──
    private static final int BG      = Color.parseColor("#0F0F0F");
    private static final int SURFACE = Color.parseColor("#161616");
    private static final int SURFACE2= Color.parseColor("#1C1C1C");
    private static final int RED     = Color.parseColor("#FF0033");
    private static final int CYAN    = Color.parseColor("#00F2EA");
    private static final int TEXT    = Color.parseColor("#F5F5F5");
    private static final int SUB     = Color.parseColor("#999999");
    private static final int MUTED   = Color.parseColor("#555555");
    private static final int BORDER  = Color.parseColor("#1F1F1F");

    private SharedPreferences prefs;
    private LinearLayout rootLayout;

    // Toggle states
    private boolean bgPlayEnabled    = true;
    private boolean pipEnabled       = true;
    private boolean notifEnabled     = true;
    private boolean autoSkipSponsors = true;
    private boolean gestureControls  = true;
    private boolean shortsBlocked    = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        );

        prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);
        bgPlayEnabled    = prefs.getBoolean("bgplay", true);
        autoSkipSponsors = "true".equals(prefs.getString("autoSpn", "true"));
        gestureControls  = "true".equals(prefs.getString("gesC",    "true"));
        shortsBlocked    = "true".equals(prefs.getString("shorts",  "false"));

        // ── Main layout: MainActivity layout inflate කරනවා ──
        setContentView(R.layout.activity_main);

        // ── WebView container hide කරනවා, You content show කරනවා ──
        View webContainer = findViewById(R.id.webContainer);
        if (webContainer != null) webContainer.setVisibility(View.GONE);

        // ── You content inject කරනවා webContainer parent ඇතුළේ ──
        ViewGroup parent = (ViewGroup) webContainer.getParent();
        ScrollView youScroll = buildYouContent();

        // webContainer LayoutParams copy කරලා YouContent add කරනවා
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        lp.topMargin    = getHeaderHeight();
        lp.bottomMargin = getBottomNavHeight();
        youScroll.setLayoutParams(lp);
        parent.addView(youScroll);

        // ── Bottom nav "You" tab active කරනවා ──
        activateYouTab();

        // ── Back button: MainActivity වලට return ──
        // (default back behavior works fine)
    }

    @Override
    public void onBackPressed() {
        // You tab ගිහිල්ලා back press → MainActivity home
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("url", "https://m.youtube.com/");
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right);
    }

    // ═══════════════════════════════════════════════════
    //  "You" tab active කරනවා (bottom nav)
    // ═══════════════════════════════════════════════════
    private void activateYouTab() {
        try {
            // Bottom nav icons and labels
            android.widget.ImageView iconHome          = findViewById(R.id.iconHome);
            android.widget.ImageView iconShorts        = findViewById(R.id.iconShorts);
            android.widget.ImageView iconSubscriptions = findViewById(R.id.iconSubscriptions);
            android.widget.ImageView iconYou           = findViewById(R.id.iconYou);
            TextView textHome          = findViewById(R.id.textHome);
            TextView textShorts        = findViewById(R.id.textShorts);
            TextView textSubscriptions = findViewById(R.id.textSubscriptions);
            TextView textYou           = findViewById(R.id.textYou);

            // Deactivate all
            int inactive = Color.parseColor("#888888");
            if (iconHome != null)          iconHome.setColorFilter(inactive);
            if (iconShorts != null)        iconShorts.setColorFilter(inactive);
            if (iconSubscriptions != null) iconSubscriptions.setColorFilter(inactive);
            if (textHome != null)          textHome.setTextColor(inactive);
            if (textShorts != null)        textShorts.setTextColor(inactive);
            if (textSubscriptions != null) textSubscriptions.setTextColor(inactive);

            // Activate You
            if (iconYou != null)  iconYou.setColorFilter(RED);
            if (textYou != null)  textYou.setTextColor(RED);

            // Bottom nav click handlers (navigate back to MainActivity)
            setupNavClicks();

        } catch (Exception e) {
            // Layout IDs different වෙලා හැකිනම් silently ignore
        }
    }

    private void setupNavClicks() {
        LinearLayout navHome          = findViewById(R.id.navHome);
        LinearLayout navShorts        = findViewById(R.id.navShorts);
        LinearLayout navUpload        = findViewById(R.id.navUpload);
        LinearLayout navSubscriptions = findViewById(R.id.navSubscriptions);
        LinearLayout navYou           = findViewById(R.id.navYou);

        if (navHome != null) navHome.setOnClickListener(v -> goToMain("https://m.youtube.com/"));
        if (navShorts != null) navShorts.setOnClickListener(v -> goToMain("https://m.youtube.com/shorts"));
        if (navUpload != null) navUpload.setOnClickListener(v ->
            Toast.makeText(this, "Upload coming soon! 🎥", Toast.LENGTH_SHORT).show());
        if (navSubscriptions != null) navSubscriptions.setOnClickListener(v ->
            goToMain("https://m.youtube.com/feed/subscriptions"));
        if (navYou != null) navYou.setOnClickListener(v -> {
            // Already on You tab — scroll to top
            if (rootLayout != null) rootLayout.scrollTo(0, 0);
        });
    }

    private void goToMain(String url) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("url", url);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right);
    }

    // ── Get header/bottomnav heights from existing dimens ──
    private int getHeaderHeight() {
        try {
            return (int) getResources().getDimension(R.dimen.header_height);
        } catch (Exception e) { return dp(56); }
    }

    private int getBottomNavHeight() {
        try {
            return (int) getResources().getDimension(R.dimen.bottom_nav_height);
        } catch (Exception e) { return dp(70); }
    }

    // ═══════════════════════════════════════════════════
    //  BUILD YOU CONTENT (ScrollView)
    // ═══════════════════════════════════════════════════
    private ScrollView buildYouContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(BG);
        scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(BG);
        rootLayout.setPadding(0, 0, 0, dp(16));
        scrollView.addView(rootLayout);

        if (isLoggedIn()) {
            buildLoggedInUI();
        } else {
            buildGuestUI();
        }

        return scrollView;
    }

    // ═══════════════════════════════════════════════════
    //  LOGIN CHECK
    // ═══════════════════════════════════════════════════
    private boolean isLoggedIn() {
        String cookies = CookieManager.getInstance().getCookie("https://m.youtube.com");
        if (cookies != null && cookies.contains("__Secure-1PSID=")) return true;
        String name = prefs.getString("yt_username", "");
        return name != null && !name.isEmpty();
    }

    private String getUserName() {
        if (!isLoggedIn()) return "Guest User";
        String name = prefs.getString("yt_username", "");
        return (name != null && !name.isEmpty()) ? name : "YT Pro User";
    }

    private String getUserHandle() {
        if (!isLoggedIn()) return "Sign in to access your account";
        String email = prefs.getString("yt_email", "");
        String name  = prefs.getString("yt_username", "");
        String handle = (name != null && !name.isEmpty())
            ? "@" + name.toLowerCase().replace(" ", "_") : "";
        if (email != null && !email.isEmpty()) return handle + " · " + email;
        return handle.isEmpty() ? "@ytpro_user" : handle;
    }

    private String getUserInitial() {
        if (!isLoggedIn()) return "?";
        String name = getUserName();
        return name.length() > 0 ? String.valueOf(name.charAt(0)).toUpperCase() : "Y";
    }

    // ═══════════════════════════════════════════════════
    //  LOGGED IN UI
    // ═══════════════════════════════════════════════════
    private void buildLoggedInUI() {
        addProfileSection();
        addStatsRow();
        addManageAccountButton();
        addDividerThick();
        addSectionLabel("Watch History", true);
        addHistoryScroll();
        addSectionLabel("Your Playlists", true);
        addPlaylistScroll();
        addDividerThick();
        addSectionLabel("YT Pro Features", false);
        addFeaturesMenu();
        addSectionLabel("General", false);
        addGeneralMenu();
        addSignOutButton();
        addVersionText();
    }

    // ═══════════════════════════════════════════════════
    //  GUEST UI
    // ═══════════════════════════════════════════════════
    private void buildGuestUI() {
        addGuestProfileSection();
        addDividerThick();
        addSectionLabel("Sign in to unlock", false);
        addSignInBenefits();
        addDividerThick();
        addSectionLabel("YT Pro Features", false);
        addFeaturesMenu();
        addSectionLabel("General", false);
        addGeneralMenu();
        addVersionText();
    }

    private void addGuestProfileSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setGravity(Gravity.CENTER);
        section.setPadding(dp(24), dp(40), dp(24), dp(24));

        // Avatar
        FrameLayout avatarWrap = new FrameLayout(this);
        LinearLayout.LayoutParams awlp = new LinearLayout.LayoutParams(dp(90), dp(90));
        awlp.gravity      = Gravity.CENTER_HORIZONTAL;
        awlp.bottomMargin = dp(16);
        avatarWrap.setLayoutParams(awlp);

        TextView avatar = new TextView(this);
        avatar.setText("?");
        avatar.setTextSize(36);
        avatar.setTextColor(SUB);
        avatar.setTypeface(null, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable ab = new GradientDrawable();
        ab.setShape(GradientDrawable.OVAL);
        ab.setColor(SURFACE2);
        ab.setStroke(dp(2), BORDER);
        avatar.setBackground(ab);
        avatarWrap.addView(avatar, new FrameLayout.LayoutParams(dp(90), dp(90)));
        section.addView(avatarWrap);

        TextView nameView = new TextView(this);
        nameView.setText("Guest User");
        nameView.setTextColor(TEXT);
        nameView.setTextSize(22);
        nameView.setTypeface(null, Typeface.BOLD);
        nameView.setGravity(Gravity.CENTER);
        section.addView(nameView);

        TextView subView = new TextView(this);
        subView.setText("Sign in to sync your subscriptions,\nwatch history and more");
        subView.setTextColor(SUB);
        subView.setTextSize(13);
        subView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sublp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sublp.topMargin    = dp(8);
        sublp.bottomMargin = dp(28);
        subView.setLayoutParams(sublp);
        section.addView(subView);

        // Sign In Button
        TextView signInBtn = new TextView(this);
        signInBtn.setText("  Sign In with Google  ");
        signInBtn.setTextColor(Color.WHITE);
        signInBtn.setTextSize(15);
        signInBtn.setTypeface(null, Typeface.BOLD);
        signInBtn.setGravity(Gravity.CENTER);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.RECTANGLE);
        btnBg.setCornerRadius(dp(25));
        btnBg.setColor(RED);
        signInBtn.setBackground(btnBg);
        signInBtn.setPadding(dp(32), dp(14), dp(32), dp(14));
        LinearLayout.LayoutParams btnlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnlp.gravity = Gravity.CENTER_HORIZONTAL;
        signInBtn.setLayoutParams(btnlp);

        // ✅ Sign In → MainActivity හරහා Google login
        signInBtn.setOnClickListener(v -> {
            Intent intent = new Intent(YouActivity.this, MainActivity.class);
            intent.putExtra("url",
                "https://accounts.google.com/ServiceLogin?service=youtube&hl=en" +
                "&continue=https://m.youtube.com/");
            intent.putExtra("fromLogin", true);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        section.addView(signInBtn);
        rootLayout.addView(section);
    }

    private void addSignInBenefits() {
        LinearLayout menu = makeMenuContainer();
        String[][] b = {
            {"📺","#1AFF0033","Watch History",  "Continue where you left off"},
            {"🔔","#1AFFA500","Subscriptions",  "Never miss a video"},
            {"❤️","#1AFF0033","Liked Videos",   "Save your favourite videos"},
            {"📋","#1A4285F4","Playlists",      "Create and manage playlists"},
            {"🤖","#1A9B72CB","Gemini AI",      "Ask questions about videos"},
        };
        for (int i = 0; i < b.length; i++) {
            menu.addView(buildNavRow(b[i][0], b[i][1], b[i][2], b[i][3]));
            if (i < b.length - 1) menu.addView(menuDivider());
        }
        rootLayout.addView(menu);
    }

    // ═══════════════════════════════════════════════════
    //  PROFILE SECTION
    // ═══════════════════════════════════════════════════
    private void addProfileSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(16), dp(24), dp(16), 0);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout avatarWrap = new FrameLayout(this);
        LinearLayout.LayoutParams awlp = new LinearLayout.LayoutParams(dp(76), dp(76));
        awlp.rightMargin = dp(16);
        avatarWrap.setLayoutParams(awlp);

        GradientDrawable ring = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT, new int[]{RED, CYAN, RED});
        ring.setShape(GradientDrawable.OVAL);
        View ringView = new View(this);
        ringView.setBackground(ring);
        avatarWrap.addView(ringView, new FrameLayout.LayoutParams(dp(76), dp(76)));

        TextView avatar = new TextView(this);
        avatar.setText(getUserInitial());
        avatar.setTextSize(26);
        avatar.setTextColor(Color.WHITE);
        avatar.setTypeface(null, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        avBg.setColor(RED);
        avatar.setBackground(avBg);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(dp(68), dp(68));
        alp.gravity = Gravity.CENTER;
        avatarWrap.addView(avatar, alp);

        View onlineDot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.parseColor("#00D26A"));
        dotBg.setStroke(dp(2), BG);
        onlineDot.setBackground(dotBg);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(14), dp(14));
        dlp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        dlp.bottomMargin = dp(2);
        dlp.rightMargin  = dp(2);
        avatarWrap.addView(onlineDot, dlp);
        row.addView(avatarWrap);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView nameView = new TextView(this);
        nameView.setText(getUserName());
        nameView.setTextColor(TEXT);
        nameView.setTextSize(18);
        nameView.setTypeface(null, Typeface.BOLD);
        info.addView(nameView);

        TextView handleView = new TextView(this);
        handleView.setText(getUserHandle());
        handleView.setTextColor(SUB);
        handleView.setTextSize(12);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(2);
        handleView.setLayoutParams(hlp);
        info.addView(handleView);

        TextView badge = new TextView(this);
        badge.setText("✦ YT Pro Member");
        badge.setTextColor(RED);
        badge.setTextSize(11);
        badge.setTypeface(null, Typeface.BOLD);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.RECTANGLE);
        badgeBg.setCornerRadius(dp(20));
        badgeBg.setColor(Color.parseColor("#1AFF0033"));
        badgeBg.setStroke(dp(1), Color.parseColor("#44FF0033"));
        badge.setBackground(badgeBg);
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(6);
        badge.setLayoutParams(blp);
        info.addView(badge);

        row.addView(info);
        section.addView(row);
        rootLayout.addView(section);
    }

    // ═══════════════════════════════════════════════════
    //  STATS ROW
    // ═══════════════════════════════════════════════════
    private void addStatsRow() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dp(16), dp(20), dp(16), 0);
        c.setLayoutParams(clp);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(SURFACE);
        bg.setStroke(dp(1), BORDER);
        c.setBackground(bg);
        c.setClipToOutline(true);

        String[][] stats = {{"24K","Subscribers"},{"1.2K","Watch Later"},{"348","Liked"}};
        for (int i = 0; i < stats.length; i++) {
            if (i > 0) {
                View d = new View(this); d.setBackgroundColor(BORDER);
                c.addView(d, new LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT));
            }
            LinearLayout stat = new LinearLayout(this);
            stat.setOrientation(LinearLayout.VERTICAL);
            stat.setGravity(Gravity.CENTER);
            stat.setPadding(dp(8), dp(16), dp(8), dp(16));
            stat.setLayoutParams(new LinearLayout.LayoutParams(0, dp(80), 1f));

            TextView num = new TextView(this);
            num.setText(stats[i][0]); num.setTextColor(TEXT); num.setTextSize(20);
            num.setTypeface(null, Typeface.BOLD); num.setGravity(Gravity.CENTER);
            stat.addView(num);

            TextView label = new TextView(this);
            label.setText(stats[i][1]); label.setTextColor(SUB); label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            llp.topMargin = dp(3); label.setLayoutParams(llp);
            stat.addView(label);
            c.addView(stat);
        }
        rootLayout.addView(c);
    }

    // ═══════════════════════════════════════════════════
    //  MANAGE ACCOUNT
    // ═══════════════════════════════════════════════════
    private void addManageAccountButton() {
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.HORIZONTAL);
        btn.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.setMargins(dp(16), dp(12), dp(16), dp(4));
        btn.setLayoutParams(blp);
        btn.setPadding(dp(14), dp(14), dp(14), dp(14));
        btn.setBackground(makeRoundedBorder(SURFACE, BORDER, dp(14)));
        btn.setClickable(true);

        TextView icon = new TextView(this);
        icon.setText("👤"); icon.setTextSize(18); icon.setGravity(Gravity.CENTER);
        GradientDrawable ib = new GradientDrawable();
        ib.setShape(GradientDrawable.RECTANGLE); ib.setCornerRadius(dp(10));
        ib.setColor(Color.parseColor("#1AFF0033")); icon.setBackground(ib);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(38), dp(38));
        ilp.rightMargin = dp(12); icon.setLayoutParams(ilp); btn.addView(icon);

        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(this); t.setText("Manage Google Account");
        t.setTextColor(TEXT); t.setTextSize(14); t.setTypeface(null, Typeface.BOLD); tc.addView(t);
        TextView s = new TextView(this); s.setText("Privacy, data & security");
        s.setTextColor(SUB); s.setTextSize(12); tc.addView(s);
        btn.addView(tc);

        TextView ch = new TextView(this); ch.setText("›"); ch.setTextColor(MUTED); ch.setTextSize(20);
        btn.addView(ch);
        btn.setOnClickListener(v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com"))));
        rootLayout.addView(btn);
    }

    // ═══════════════════════════════════════════════════
    //  WATCH HISTORY
    // ═══════════════════════════════════════════════════
    private void addHistoryScroll() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(20); hsv.setLayoutParams(lp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), 0, dp(16), dp(4));

        String[][] items = {
            {"🎵","#1A0A2E","Lo-fi Hip Hop Beats to Study & Relax","Lofi Girl · 2h ago","4:32","65"},
            {"💻","#0D2137","Android Studio Tutorial for Beginners 2024","Coding With Tea · Yesterday","18:45","30"},
            {"🌿","#1A2E0A","Sri Lanka Travel Vlog 2024","Travel SL · 2 days ago","12:20","100"},
            {"🎮","#2E0A0A","PUBG Mobile Pro Tips — Season 30","GamersLK · 3 days ago","32:10","15"},
        };
        for (String[] item : items)
            row.addView(buildHistoryCard(item[0],item[1],item[2],item[3],item[4],Integer.parseInt(item[5])));

        hsv.addView(row);
        rootLayout.addView(hsv);
    }

    private View buildHistoryCard(String emoji, String color, String title,
                                   String ch, String dur, int prog) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(180), LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.rightMargin = dp(12); card.setLayoutParams(clp);

        FrameLayout thumb = new FrameLayout(this);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(180), dp(101));
        tlp.bottomMargin = dp(8); thumb.setLayoutParams(tlp);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setShape(GradientDrawable.RECTANGLE); tbg.setCornerRadius(dp(10));
        tbg.setColor(Color.parseColor(color)); thumb.setBackground(tbg);
        thumb.setClipToOutline(true);

        TextView ev = new TextView(this); ev.setText(emoji); ev.setTextSize(28); ev.setGravity(Gravity.CENTER);
        thumb.addView(ev, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView db = new TextView(this); db.setText(dur); db.setTextColor(Color.WHITE); db.setTextSize(9);
        db.setPadding(dp(4),dp(2),dp(4),dp(2));
        GradientDrawable dbb = new GradientDrawable(); dbb.setShape(GradientDrawable.RECTANGLE);
        dbb.setCornerRadius(dp(3)); dbb.setColor(Color.parseColor("#CC000000")); db.setBackground(dbb);
        FrameLayout.LayoutParams dblp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        dblp.gravity = Gravity.BOTTOM | Gravity.RIGHT; dblp.bottomMargin = dp(5); dblp.rightMargin = dp(5);
        db.setLayoutParams(dblp); thumb.addView(db);

        FrameLayout pb = new FrameLayout(this);
        FrameLayout.LayoutParams pblp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(3));
        pblp.gravity = Gravity.BOTTOM; pb.setLayoutParams(pblp);
        pb.setBackgroundColor(Color.parseColor("#44FFFFFF"));
        View fill = new View(this); fill.setBackgroundColor(RED);
        fill.post(() -> { ViewGroup.LayoutParams vp = fill.getLayoutParams(); vp.width = (int)(pb.getWidth()*prog/100f); fill.setLayoutParams(vp); });
        pb.addView(fill, new FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT));
        thumb.addView(pb); card.addView(thumb);

        TextView tv = new TextView(this); tv.setText(title); tv.setTextColor(TEXT); tv.setTextSize(12);
        tv.setMaxLines(2); tv.setEllipsize(android.text.TextUtils.TruncateAt.END); card.addView(tv);

        TextView cv = new TextView(this); cv.setText(ch); cv.setTextColor(SUB); cv.setTextSize(11);
        LinearLayout.LayoutParams chlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chlp.topMargin = dp(2); cv.setLayoutParams(chlp); card.addView(cv);
        return card;
    }

    // ═══════════════════════════════════════════════════
    //  PLAYLISTS
    // ═══════════════════════════════════════════════════
    private void addPlaylistScroll() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8); hsv.setLayoutParams(lp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), 0, dp(16), dp(4));

        String[][] pls = {
            {"🎵","#1A0A2E,#6E1A8A","Chill Vibes","24 videos"},
            {"💻","#0A1A2E,#1A5A8A","Dev Tutorials","18 videos"},
            {"🎬","#2E1A0A,#8A5A1A","Watch Later","7 videos"},
            {"❤️","#0A2E0A,#1A8A3A","Liked Videos","348 videos"},
        };
        for (String[] pl : pls) row.addView(buildPlaylistCard(pl[0],pl[1],pl[2],pl[3]));
        hsv.addView(row); rootLayout.addView(hsv);
    }

    private View buildPlaylistCard(String emoji, String colors, String name, String count) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.rightMargin = dp(12); card.setLayoutParams(clp);

        FrameLayout thumb = new FrameLayout(this);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(150), dp(150));
        tlp.bottomMargin = dp(8); thumb.setLayoutParams(tlp);
        String[] ca = colors.split(",");
        GradientDrawable tbg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor(ca[0]), Color.parseColor(ca[1])});
        tbg.setCornerRadius(dp(12)); thumb.setBackground(tbg); thumb.setClipToOutline(true);

        TextView ev = new TextView(this); ev.setText(emoji); ev.setTextSize(36); ev.setGravity(Gravity.CENTER);
        thumb.addView(ev, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView cv = new TextView(this); cv.setText(count); cv.setTextColor(Color.WHITE);
        cv.setTextSize(10); cv.setTypeface(null, Typeface.BOLD);
        FrameLayout.LayoutParams cvlp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        cvlp.gravity = Gravity.BOTTOM | Gravity.RIGHT; cvlp.bottomMargin = dp(8); cvlp.rightMargin = dp(8);
        cv.setLayoutParams(cvlp); thumb.addView(cv); card.addView(thumb);

        TextView nv = new TextView(this); nv.setText(name); nv.setTextColor(TEXT);
        nv.setTextSize(13); nv.setTypeface(null, Typeface.BOLD); card.addView(nv);
        return card;
    }

    // ═══════════════════════════════════════════════════
    //  FEATURES MENU
    // ═══════════════════════════════════════════════════
    private void addFeaturesMenu() {
        LinearLayout menu = makeMenuContainer();
        menu.addView(buildToggleRow("🎵","#1AFF0033","Background Play","Screen off ව්ව‍ත් play කරයි",bgPlayEnabled,
            c -> { bgPlayEnabled=c; prefs.edit().putBoolean("bgplay",c).apply(); }));
        menu.addView(menuDivider());
        menu.addView(buildToggleRow("📱","#1400F2EA","Picture in Picture","Mini window mode",pipEnabled,
            c -> pipEnabled=c));
        menu.addView(menuDivider());
        menu.addView(buildBadgeToggleRow("🔔","#1AFFA500","Push Notifications","New videos & updates","3 NEW",notifEnabled,
            c -> notifEnabled=c));
        menu.addView(menuDivider());
        menu.addView(buildToggleRow("⏭️","#1400D26A","Auto-skip Sponsors","Sponsor segments skip කරයි",autoSkipSponsors,
            c -> { autoSkipSponsors=c; prefs.edit().putString("autoSpn",c?"true":"false").apply(); }));
        menu.addView(menuDivider());
        menu.addView(buildToggleRow("👋","#1A4285F4","Gesture Controls","Volume & brightness swipe",gestureControls,
            c -> { gestureControls=c; prefs.edit().putString("gesC",c?"true":"false").apply(); }));
        menu.addView(menuDivider());
        menu.addView(buildToggleRow("🚫","#1A64C864","Hide Shorts","Shorts feed hide කරයි",shortsBlocked,
            c -> { shortsBlocked=c; prefs.edit().putString("shorts",c?"true":"false").apply(); }));
        rootLayout.addView(menu);
    }

    // ═══════════════════════════════════════════════════
    //  GENERAL MENU
    // ═══════════════════════════════════════════════════
    private void addGeneralMenu() {
        LinearLayout menu = makeMenuContainer();
        String[][] items = {
            {"⬇️","#0DFFFFFF","Downloads","Offline videos (12 files)"},
            {"📊","#0DFFFFFF","Watch Time Stats","Today: 2h 34m"},
            {"🔒","#0DFFFFFF","Privacy & Security","Clear history, data"},
            {"🌙","#0DFFFFFF","Appearance","Dark · AMOLED · Light"},
            {"🌐","#0DFFFFFF","Language & Region","Sinhala · English"},
            {"🤖","#0D4285F4","Gemini AI","Ask about videos"},
            {"🔄","#0D00D26A","Check for Updates","Current: v2.1 ✓"},
            {"🐛","#0DFF6B6B","Report a Bug","spmodsofficial@gmail.com"},
        };
        for (int i = 0; i < items.length; i++) {
            View row = buildNavRow(items[i][0], items[i][1], items[i][2], items[i][3]);
            menu.addView(row);
            if (i < items.length-1) menu.addView(menuDivider());
            final int idx = i;
            row.setOnClickListener(v -> {
                switch (idx) {
                    case 6: goToMain("https://m.youtube.com/#settings"); break;
                    case 7: startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:spmodsofficial@gmail.com"))); break;
                    default: Toast.makeText(this, items[idx][2]+" — coming soon!", Toast.LENGTH_SHORT).show(); break;
                }
            });
        }
        rootLayout.addView(menu);
    }

    // ═══════════════════════════════════════════════════
    //  SIGN OUT
    // ═══════════════════════════════════════════════════
    private void addSignOutButton() {
        TextView btn = new TextView(this);
        btn.setText("⏻   Sign Out"); btn.setTextColor(RED);
        btn.setTextSize(15); btn.setTypeface(null, Typeface.BOLD); btn.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE); bg.setCornerRadius(dp(14));
        bg.setColor(Color.TRANSPARENT); bg.setStroke(dp(1), Color.parseColor("#44FF0033"));
        btn.setBackground(bg); btn.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), dp(16), dp(16), dp(8)); btn.setLayoutParams(lp); btn.setClickable(true);

        btn.setOnClickListener(v ->
            new android.app.AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out", (d, w) -> {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    prefs.edit().remove("yt_username").remove("yt_email").apply();
                    recreate(); // Guest UI show
                })
                .setNegativeButton("Cancel", null)
                .show()
        );
        rootLayout.addView(btn);
    }

    private void addVersionText() {
        TextView ver = new TextView(this);
        ver.setText("YT Pro v2.1 · Not affiliated with YouTube™");
        ver.setTextColor(MUTED); ver.setTextSize(10); ver.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8); ver.setLayoutParams(lp); rootLayout.addView(ver);
    }

    // ═══════════════════════════════════════════════════
    //  TOGGLE HELPERS
    // ═══════════════════════════════════════════════════
    interface ToggleCallback { void onToggle(boolean checked); }

    private View buildToggleRow(String emoji, String iconColor, String title, String desc,
                                 boolean on, ToggleCallback cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView icon = new TextView(this); icon.setText(emoji); icon.setTextSize(18); icon.setGravity(Gravity.CENTER);
        GradientDrawable ib = new GradientDrawable(); ib.setShape(GradientDrawable.RECTANGLE);
        ib.setCornerRadius(dp(10)); ib.setColor(Color.parseColor(iconColor)); icon.setBackground(ib);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
        ilp.rightMargin = dp(12); icon.setLayoutParams(ilp); row.addView(icon);

        LinearLayout tc = new LinearLayout(this); tc.setOrientation(LinearLayout.VERTICAL);
        tc.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView tv = new TextView(this); tv.setText(title); tv.setTextColor(TEXT); tv.setTextSize(14); tc.addView(tv);
        TextView dv = new TextView(this); dv.setText(desc); dv.setTextColor(SUB); dv.setTextSize(12); tc.addView(dv);
        row.addView(tc);

        FrameLayout toggle = buildToggle(on); row.addView(toggle);
        boolean[] state = {on};
        row.setOnClickListener(v -> { state[0]=!state[0]; updateToggle(toggle,state[0]); cb.onToggle(state[0]); });
        return row;
    }

    private View buildBadgeToggleRow(String emoji, String iconColor, String title, String desc,
                                      String badge, boolean on, ToggleCallback cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView icon = new TextView(this); icon.setText(emoji); icon.setTextSize(18); icon.setGravity(Gravity.CENTER);
        GradientDrawable ib = new GradientDrawable(); ib.setShape(GradientDrawable.RECTANGLE);
        ib.setCornerRadius(dp(10)); ib.setColor(Color.parseColor(iconColor)); icon.setBackground(ib);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
        ilp.rightMargin = dp(12); icon.setLayoutParams(ilp); row.addView(icon);

        LinearLayout tc = new LinearLayout(this); tc.setOrientation(LinearLayout.VERTICAL);
        tc.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView tv = new TextView(this); tv.setText(title); tv.setTextColor(TEXT); tv.setTextSize(14); tc.addView(tv);
        TextView dv = new TextView(this); dv.setText(desc); dv.setTextColor(SUB); dv.setTextSize(12); tc.addView(dv);
        row.addView(tc);

        TextView bv = new TextView(this); bv.setText(badge); bv.setTextColor(Color.WHITE);
        bv.setTextSize(10); bv.setTypeface(null, Typeface.BOLD);
        GradientDrawable bb = new GradientDrawable(); bb.setShape(GradientDrawable.RECTANGLE);
        bb.setCornerRadius(dp(10)); bb.setColor(RED); bv.setBackground(bb);
        bv.setPadding(dp(6),dp(2),dp(6),dp(2));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.rightMargin = dp(8); bv.setLayoutParams(blp); row.addView(bv);

        FrameLayout toggle = buildToggle(on); row.addView(toggle);
        boolean[] state = {on};
        row.setOnClickListener(v -> { state[0]=!state[0]; updateToggle(toggle,state[0]); cb.onToggle(state[0]); });
        return row;
    }

    private View buildNavRow(String emoji, String iconColor, String title, String desc) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14)); row.setClickable(true); row.setFocusable(true);

        TextView icon = new TextView(this); icon.setText(emoji); icon.setTextSize(18); icon.setGravity(Gravity.CENTER);
        GradientDrawable ib = new GradientDrawable(); ib.setShape(GradientDrawable.RECTANGLE);
        ib.setCornerRadius(dp(10)); ib.setColor(Color.parseColor(iconColor)); icon.setBackground(ib);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
        ilp.rightMargin = dp(12); icon.setLayoutParams(ilp); row.addView(icon);

        LinearLayout tc = new LinearLayout(this); tc.setOrientation(LinearLayout.VERTICAL);
        tc.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView tv = new TextView(this); tv.setText(title); tv.setTextColor(TEXT); tv.setTextSize(14); tc.addView(tv);
        TextView dv = new TextView(this); dv.setText(desc); dv.setTextColor(SUB); dv.setTextSize(12); tc.addView(dv);
        row.addView(tc);

        TextView ch = new TextView(this); ch.setText("›"); ch.setTextColor(MUTED); ch.setTextSize(20); row.addView(ch);
        return row;
    }

    private FrameLayout buildToggle(boolean on) {
        FrameLayout toggle = new FrameLayout(this);
        toggle.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(24)));
        GradientDrawable t = new GradientDrawable(); t.setShape(GradientDrawable.RECTANGLE);
        t.setCornerRadius(dp(12)); t.setColor(on ? RED : SURFACE2); t.setStroke(dp(1), on ? RED : BORDER);
        toggle.setBackground(t);
        View knob = new View(this);
        GradientDrawable k = new GradientDrawable(); k.setShape(GradientDrawable.OVAL); k.setColor(Color.WHITE);
        knob.setBackground(k);
        FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(dp(18), dp(18));
        klp.gravity = Gravity.CENTER_VERTICAL; klp.leftMargin = on ? dp(21) : dp(3);
        knob.setLayoutParams(klp); toggle.addView(knob);
        return toggle;
    }

    private void updateToggle(FrameLayout toggle, boolean on) {
        GradientDrawable t = new GradientDrawable(); t.setShape(GradientDrawable.RECTANGLE);
        t.setCornerRadius(dp(12)); t.setColor(on ? RED : SURFACE2); t.setStroke(dp(1), on ? RED : BORDER);
        toggle.setBackground(t);
        View knob = toggle.getChildAt(0);
        FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(dp(18), dp(18));
        klp.gravity = Gravity.CENTER_VERTICAL; klp.leftMargin = on ? dp(21) : dp(3);
        knob.setLayoutParams(klp);
    }

    // ═══════════════════════════════════════════════════
    //  MISC
    // ═══════════════════════════════════════════════════
    private void addSectionLabel(String label, boolean seeAll) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(dp(16), dp(20), dp(16), dp(10)); row.setLayoutParams(rlp);

        TextView t = new TextView(this); t.setText(label); t.setTextColor(TEXT);
        t.setTextSize(16); t.setTypeface(null, Typeface.BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);

        if (seeAll) {
            TextView sa = new TextView(this); sa.setText("See all"); sa.setTextColor(RED);
            sa.setTextSize(13); sa.setTypeface(null, Typeface.BOLD);
            sa.setOnClickListener(v -> Toast.makeText(this,"See all — coming soon!",Toast.LENGTH_SHORT).show());
            row.addView(sa);
        }
        rootLayout.addView(row);
    }

    private LinearLayout makeMenuContainer() {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mlp.setMargins(dp(16), 0, dp(16), dp(20)); menu.setLayoutParams(mlp);
        menu.setBackground(makeRoundedBorder(SURFACE, BORDER, dp(16)));
        menu.setClipToOutline(true);
        return menu;
    }

    private View menuDivider() {
        View d = new View(this); d.setBackgroundColor(BORDER);
        d.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return d;
    }

    private void addDividerThick() {
        View d = new View(this); d.setBackgroundColor(Color.parseColor("#1A000000"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        lp.topMargin = dp(8); d.setLayoutParams(lp); rootLayout.addView(d);
    }

    private android.graphics.drawable.Drawable makeRoundedBorder(int color, int border, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(radius);
        gd.setColor(color); gd.setStroke(dp(1), border); return gd;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
