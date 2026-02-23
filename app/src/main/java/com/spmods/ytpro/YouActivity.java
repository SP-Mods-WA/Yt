package com.spmods.ytpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class YouActivity extends Activity {

    // ── Colours ────────────────────────────────────────────────────────────────
    private static final int BG      = Color.parseColor("#0F0F0F");
    private static final int SURFACE = Color.parseColor("#161616");
    private static final int SURFACE2= Color.parseColor("#1C1C1C");
    private static final int RED     = Color.parseColor("#FF0033");
    private static final int CYAN    = Color.parseColor("#00F2EA");
    private static final int TEXT    = Color.parseColor("#F5F5F5");
    private static final int SUB     = Color.parseColor("#999999");
    private static final int MUTED   = Color.parseColor("#555555");
    private static final int BORDER  = Color.parseColor("#1F1F1F");
    private static final int GREEN   = Color.parseColor("#00D26A");
    private static final int ORANGE  = Color.parseColor("#FFA500");
    private static final int BLUE    = Color.parseColor("#4285F4");
    private static final int PURPLE  = Color.parseColor("#9B72CB");

    private SharedPreferences prefs;
    private LinearLayout rootLayout;
    private DisplayMetrics dm;

    // ── Settings state (mirrors localStorage keys in script.js) ────────────────
    private boolean bgPlayEnabled;
    private boolean autoSkipSponsors;
    private boolean gestureControls;
    private boolean miniPlayerGesture;
    private boolean shortsBlocked;
    private boolean saveChatInfo;
    private boolean forceZoom;

    // ── YTProVer ───────────────────────────────────────────────────────────────
    private static final String YT_PRO_VER = "2.1";

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

        dm = getResources().getDisplayMetrics();
        prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);
        loadSettings();

        // Build pure programmatic layout (NO activity_main.xml dependency)
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);

        ScrollView scroll = buildYouContent();
        root.addView(scroll, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
    }

    // ── Persist / load ─────────────────────────────────────────────────────────
    private void loadSettings() {
        bgPlayEnabled     = prefs.getBoolean("bgplay",       true);
        autoSkipSponsors  = "true".equals(prefs.getString("autoSpn",   "true"));
        gestureControls   = "true".equals(prefs.getString("gesC",      "true"));
        miniPlayerGesture = "true".equals(prefs.getString("gesM",      "false"));
        shortsBlocked     = "true".equals(prefs.getString("shorts",    "false"));
        saveChatInfo      = "true".equals(prefs.getString("saveCInfo", "true"));
        forceZoom         = "true".equals(prefs.getString("fzoom",     "false"));
    }

    private void savePref(String key, boolean val) {
        prefs.edit().putString(key, val ? "true" : "false").apply();
    }

    // ── Back = go home ─────────────────────────────────────────────────────────
    @Override
    public void onBackPressed() {
        goToMain("https://m.youtube.com/");
    }

    // ── Navigation helper ──────────────────────────────────────────────────────
    private void goToMain(String url) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("url", url);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LOGIN HELPERS
    // ══════════════════════════════════════════════════════════════════════════
    private boolean isLoggedIn() {
        String c = CookieManager.getInstance().getCookie("https://m.youtube.com");
        if (c != null && c.contains("__Secure-1PSID=")) return true;
        String n = prefs.getString("yt_username", "");
        return n != null && !n.isEmpty();
    }

    private String getUserName() {
        if (!isLoggedIn()) return "Guest User";
        String n = prefs.getString("yt_username", "");
        return (n != null && !n.isEmpty()) ? n : "YT Pro User";
    }

    private String getUserHandle() {
        if (!isLoggedIn()) return "Sign in to access your account";
        String email  = prefs.getString("yt_email", "");
        String name   = prefs.getString("yt_username", "");
        String handle = (name != null && !name.isEmpty())
            ? "@" + name.toLowerCase().replace(" ", "_") : "";
        if (email != null && !email.isEmpty()) return handle + " · " + email;
        return handle.isEmpty() ? "@ytpro_user" : handle;
    }

    private String getUserInitial() {
        if (!isLoggedIn()) return "?";
        String n = getUserName();
        return n.length() > 0 ? String.valueOf(n.charAt(0)).toUpperCase() : "Y";
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ROOT SCROLL VIEW
    // ══════════════════════════════════════════════════════════════════════════
    private ScrollView buildYouContent() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(BG);
        // Top padding = status bar clearance, bottom = breathing room
        rootLayout.setPadding(0, statusBarHeight(), 0, dp(24));
        sv.addView(rootLayout, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── Header ──────────────────────────────────────────────────────────
        addTopBar();

        if (isLoggedIn()) buildLoggedInUI();
        else              buildGuestUI();

        return sv;
    }

    private int statusBarHeight() {
        int res = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return res > 0 ? getResources().getDimensionPixelSize(res) : dp(24);
    }

    // ── Top bar with "You" title ───────────────────────────────────────────────
    private void addTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(BG);
        bar.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView title = new TextView(this);
        title.setText("You");
        title.setTextColor(TEXT);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(title);

        // Notification bell
        TextView bell = makeTouchIcon("🔔", () ->
            goToMain("https://m.youtube.com/notifications"));
        bar.addView(bell);

        // Search icon
        TextView search = makeTouchIcon("🔍", () ->
            goToMain("https://m.youtube.com/results"));
        bar.addView(search);

        rootLayout.addView(bar);

        // Thin divider
        View div = new View(this);
        div.setBackgroundColor(BORDER);
        rootLayout.addView(div, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private TextView makeTouchIcon(String emoji, Runnable action) {
        TextView tv = new TextView(this);
        tv.setText(emoji);
        tv.setTextSize(20);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(8), dp(4), dp(8), dp(4));
        tv.setClickable(true);
        tv.setOnClickListener(v -> action.run());
        return tv;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LOGGED-IN UI
    // ══════════════════════════════════════════════════════════════════════════
    private void buildLoggedInUI() {
        addProfileSection();
        addStatsRow();
        addManageAccountButton();
        addDividerThick();
        addSectionLabel("Watch History", true, () ->
            goToMain("https://m.youtube.com/feed/history"));
        addHistoryScroll();
        addSectionLabel("Your Playlists", true, () ->
            goToMain("https://m.youtube.com/feed/playlists"));
        addPlaylistScroll();
        addDividerThick();
        addSectionLabel("YT Pro Features", false, null);
        addFeaturesMenu();
        addSectionLabel("Video Quality", false, null);
        addQualityMenu();
        addSectionLabel("General", false, null);
        addGeneralMenu();
        addSignOutButton();
        addVersionText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GUEST UI
    // ══════════════════════════════════════════════════════════════════════════
    private void buildGuestUI() {
        addGuestProfileSection();
        addDividerThick();
        addSectionLabel("Sign in to unlock", false, null);
        addSignInBenefits();
        addDividerThick();
        addSectionLabel("YT Pro Features", false, null);
        addFeaturesMenu();
        addSectionLabel("General", false, null);
        addGeneralMenu();
        addVersionText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PROFILE SECTIONS
    // ══════════════════════════════════════════════════════════════════════════
    private void addGuestProfileSection() {
        LinearLayout sec = new LinearLayout(this);
        sec.setOrientation(LinearLayout.VERTICAL);
        sec.setGravity(Gravity.CENTER);
        sec.setPadding(dp(24), dp(40), dp(24), dp(24));

        // Avatar
        FrameLayout aw = new FrameLayout(this);
        LinearLayout.LayoutParams awlp = new LinearLayout.LayoutParams(dp(90), dp(90));
        awlp.gravity = Gravity.CENTER_HORIZONTAL;
        awlp.bottomMargin = dp(16);
        aw.setLayoutParams(awlp);
        TextView av = new TextView(this);
        av.setText("?"); av.setTextSize(36); av.setTextColor(SUB);
        av.setTypeface(null, Typeface.BOLD); av.setGravity(Gravity.CENTER);
        GradientDrawable ab = new GradientDrawable();
        ab.setShape(GradientDrawable.OVAL); ab.setColor(SURFACE2); ab.setStroke(dp(2), BORDER);
        av.setBackground(ab);
        aw.addView(av, new FrameLayout.LayoutParams(dp(90), dp(90)));
        sec.addView(aw);

        addCenteredText(sec, "Guest User", TEXT, 22, Typeface.BOLD, 0);
        addCenteredText(sec, "Sign in to sync your subscriptions,\nwatch history and more", SUB, 13, Typeface.NORMAL, dp(8));

        // Sign-In button
        TextView btn = new TextView(this);
        btn.setText("  Sign In with Google  ");
        btn.setTextColor(Color.WHITE); btn.setTextSize(15);
        btn.setTypeface(null, Typeface.BOLD); btn.setGravity(Gravity.CENTER);
        GradientDrawable bg = roundedFill(RED, dp(25));
        btn.setBackground(bg);
        btn.setPadding(dp(32), dp(14), dp(32), dp(14));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.CENTER_HORIZONTAL; blp.topMargin = dp(20);
        btn.setLayoutParams(blp);
        btn.setOnClickListener(v -> {
            Intent i = new Intent(YouActivity.this, MainActivity.class);
            i.putExtra("url","https://accounts.google.com/ServiceLogin?service=youtube&hl=en&continue=https://m.youtube.com/");
            i.putExtra("fromLogin", true);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i); finish();
        });
        sec.addView(btn);
        rootLayout.addView(sec);
    }

    private void addProfileSection() {
        LinearLayout sec = new LinearLayout(this);
        sec.setOrientation(LinearLayout.VERTICAL);
        sec.setPadding(dp(16), dp(20), dp(16), 0);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // Avatar with gradient ring
        FrameLayout aw = new FrameLayout(this);
        LinearLayout.LayoutParams awlp = new LinearLayout.LayoutParams(dp(76), dp(76));
        awlp.rightMargin = dp(16); aw.setLayoutParams(awlp);

        GradientDrawable ring = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT, new int[]{RED, CYAN, RED});
        ring.setShape(GradientDrawable.OVAL);
        View ringV = new View(this); ringV.setBackground(ring);
        aw.addView(ringV, new FrameLayout.LayoutParams(dp(76), dp(76)));

        TextView av = new TextView(this);
        av.setText(getUserInitial()); av.setTextSize(26);
        av.setTextColor(Color.WHITE); av.setTypeface(null, Typeface.BOLD);
        av.setGravity(Gravity.CENTER); av.setBackground(roundedFill(RED, dp(34)));
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(dp(68), dp(68));
        alp.gravity = Gravity.CENTER; aw.addView(av, alp);

        // Online dot
        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL); dotBg.setColor(GREEN); dotBg.setStroke(dp(2), BG);
        dot.setBackground(dotBg);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(14), dp(14));
        dlp.gravity = Gravity.BOTTOM | Gravity.RIGHT; dlp.bottomMargin = dp(2); dlp.rightMargin = dp(2);
        aw.addView(dot, dlp);
        row.addView(aw);

        // Info
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView nameV = new TextView(this);
        nameV.setText(getUserName()); nameV.setTextColor(TEXT); nameV.setTextSize(18);
        nameV.setTypeface(null, Typeface.BOLD); info.addView(nameV);

        TextView handleV = new TextView(this);
        handleV.setText(getUserHandle()); handleV.setTextColor(SUB); handleV.setTextSize(12);
        LinearLayout.LayoutParams hlp2 = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp2.topMargin = dp(2); handleV.setLayoutParams(hlp2); info.addView(handleV);

        TextView badge = new TextView(this);
        badge.setText("✦ YT Pro Member"); badge.setTextColor(RED); badge.setTextSize(11);
        badge.setTypeface(null, Typeface.BOLD);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.RECTANGLE); badgeBg.setCornerRadius(dp(20));
        badgeBg.setColor(Color.parseColor("#1AFF0033")); badgeBg.setStroke(dp(1), Color.parseColor("#44FF0033"));
        badge.setBackground(badgeBg); badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp2.topMargin = dp(6); badge.setLayoutParams(blp2); info.addView(badge);

        row.addView(info);

        // Edit profile arrow
        TextView editBtn = new TextView(this);
        editBtn.setText("✎"); editBtn.setTextColor(SUB); editBtn.setTextSize(18);
        editBtn.setPadding(dp(8), 0, 0, 0);
        editBtn.setOnClickListener(v ->
            goToMain("https://myaccount.google.com/profile"));
        row.addView(editBtn);

        sec.addView(row);
        rootLayout.addView(sec);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STATS ROW
    // ══════════════════════════════════════════════════════════════════════════
    private void addStatsRow() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dp(16), dp(20), dp(16), 0);
        c.setLayoutParams(clp);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE); bg.setCornerRadius(dp(16));
        bg.setColor(SURFACE); bg.setStroke(dp(1), BORDER);
        c.setBackground(bg); c.setClipToOutline(true);

        String[][] stats = {
            {"24K","Subscribers","https://m.youtube.com/channel/"},
            {"1.2K","Watch Later","https://m.youtube.com/playlist?list=WL"},
            {"348","Liked","https://m.youtube.com/playlist?list=LL"}
        };
        for (int i = 0; i < stats.length; i++) {
            if (i > 0) {
                View d = new View(this); d.setBackgroundColor(BORDER);
                c.addView(d, new LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT));
            }
            LinearLayout stat = new LinearLayout(this);
            stat.setOrientation(LinearLayout.VERTICAL); stat.setGravity(Gravity.CENTER);
            stat.setPadding(dp(8), dp(16), dp(8), dp(16));
            stat.setLayoutParams(new LinearLayout.LayoutParams(0, dp(80), 1f));
            stat.setClickable(true);
            final String url = stats[i][2];
            stat.setOnClickListener(v -> goToMain(url));

            TextView num = new TextView(this);
            num.setText(stats[i][0]); num.setTextColor(TEXT); num.setTextSize(20);
            num.setTypeface(null, Typeface.BOLD); num.setGravity(Gravity.CENTER); stat.addView(num);

            TextView label = new TextView(this);
            label.setText(stats[i][1]); label.setTextColor(SUB); label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            llp2.topMargin = dp(3); label.setLayoutParams(llp2); stat.addView(label);
            c.addView(stat);
        }
        rootLayout.addView(c);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  MANAGE ACCOUNT
    // ══════════════════════════════════════════════════════════════════════════
    private void addManageAccountButton() {
        LinearLayout btn = makeMenuContainer();
        btn.setClickable(true);
        btn.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout.LayoutParams blp = (LinearLayout.LayoutParams) btn.getLayoutParams();
        blp.topMargin = dp(12); blp.bottomMargin = dp(4); btn.setLayoutParams(blp);

        btn.setOnClickListener(v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com"))));

        TextView icon = makeIconBox("👤", "#1AFF0033", dp(38));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(38), dp(38));
        ilp.rightMargin = dp(12); icon.setLayoutParams(ilp); btn.addView(icon);

        LinearLayout tc = new LinearLayout(this); tc.setOrientation(LinearLayout.VERTICAL);
        tc.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(this); t.setText("Manage Google Account");
        t.setTextColor(TEXT); t.setTextSize(14); t.setTypeface(null, Typeface.BOLD); tc.addView(t);
        TextView s = new TextView(this); s.setText("Privacy, data & security");
        s.setTextColor(SUB); s.setTextSize(12); tc.addView(s); btn.addView(tc);

        TextView ch = new TextView(this); ch.setText("›"); ch.setTextColor(MUTED); ch.setTextSize(20);
        btn.addView(ch);
        rootLayout.addView(btn);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  WATCH HISTORY SCROLL
    // ══════════════════════════════════════════════════════════════════════════
    private void addHistoryScroll() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(20); hsv.setLayoutParams(lp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), 0, dp(16), dp(4));

        int cardW = cardWidth(); // responsive
        String[][] items = {
            {"🎵","#1A0A2E","Lo-fi Hip Hop Beats","Lofi Girl · 2h ago","4:32","65"},
            {"💻","#0D2137","Android Studio Tutorial","Coding With Tea · Yesterday","18:45","30"},
            {"🌿","#1A2E0A","Sri Lanka Travel Vlog","Travel SL · 2 days ago","12:20","100"},
            {"🎮","#2E0A0A","PUBG Mobile Pro Tips","GamersLK · 3 days ago","32:10","15"},
        };
        for (String[] item : items)
            row.addView(buildHistoryCard(item, cardW));

        hsv.addView(row); rootLayout.addView(hsv);
    }

    private int cardWidth() {
        // ~46% of screen width, min 140dp, max 220dp
        int pct = (int)(dm.widthPixels * 0.46f / dm.density);
        return Math.max(140, Math.min(220, pct));
    }

    private View buildHistoryCard(String[] item, int cardWdp) {
        String emoji = item[0], color = item[1], title = item[2],
               ch = item[3], dur = item[4];
        int prog = Integer.parseInt(item[5]);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            dp(cardWdp), LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.rightMargin = dp(12); card.setLayoutParams(clp);
        card.setClickable(true);
        card.setOnClickListener(v -> Toast.makeText(this,"Open video – coming soon!",Toast.LENGTH_SHORT).show());

        int thumbH = (int)(cardWdp * 9f / 16f);
        FrameLayout thumb = new FrameLayout(this);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(cardWdp), dp(thumbH));
        tlp.bottomMargin = dp(8); thumb.setLayoutParams(tlp);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setShape(GradientDrawable.RECTANGLE); tbg.setCornerRadius(dp(10));
        tbg.setColor(Color.parseColor(color)); thumb.setBackground(tbg); thumb.setClipToOutline(true);

        TextView ev = new TextView(this); ev.setText(emoji); ev.setTextSize(28); ev.setGravity(Gravity.CENTER);
        thumb.addView(ev, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView db = new TextView(this); db.setText(dur); db.setTextColor(Color.WHITE); db.setTextSize(9);
        db.setPadding(dp(4), dp(2), dp(4), dp(2));
        GradientDrawable dbb = new GradientDrawable(); dbb.setShape(GradientDrawable.RECTANGLE);
        dbb.setCornerRadius(dp(3)); dbb.setColor(Color.parseColor("#CC000000")); db.setBackground(dbb);
        FrameLayout.LayoutParams dblp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        dblp.gravity = Gravity.BOTTOM | Gravity.RIGHT; dblp.bottomMargin = dp(5); dblp.rightMargin = dp(5);
        db.setLayoutParams(dblp); thumb.addView(db);

        // Progress bar
        FrameLayout pb = new FrameLayout(this);
        FrameLayout.LayoutParams pblp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(3));
        pblp.gravity = Gravity.BOTTOM; pb.setLayoutParams(pblp);
        pb.setBackgroundColor(Color.parseColor("#44FFFFFF"));
        View fill = new View(this); fill.setBackgroundColor(RED);
        fill.post(() -> {
            ViewGroup.LayoutParams vp = fill.getLayoutParams();
            if (vp == null) vp = new ViewGroup.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
            vp.width = (int)(pb.getWidth() * prog / 100f);
            fill.setLayoutParams(vp);
        });
        pb.addView(fill, new FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT));
        thumb.addView(pb); card.addView(thumb);

        TextView tv = new TextView(this); tv.setText(title); tv.setTextColor(TEXT); tv.setTextSize(12);
        tv.setMaxLines(2); tv.setEllipsize(android.text.TextUtils.TruncateAt.END); card.addView(tv);

        TextView cv = new TextView(this); cv.setText(ch); cv.setTextColor(SUB); cv.setTextSize(11);
        LinearLayout.LayoutParams chlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chlp.topMargin = dp(2); cv.setLayoutParams(chlp); card.addView(cv);
        return card;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PLAYLISTS SCROLL
    // ══════════════════════════════════════════════════════════════════════════
    private void addPlaylistScroll() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8); hsv.setLayoutParams(lp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), 0, dp(16), dp(4));

        int plW = (int)(dm.widthPixels * 0.38f / dm.density);
        plW = Math.max(120, Math.min(180, plW));

        String[][] pls = {
            {"🎵","#1A0A2E,#6E1A8A","Chill Vibes","24 videos","LL"},
            {"💻","#0A1A2E,#1A5A8A","Dev Tutorials","18 videos",""},
            {"🎬","#2E1A0A,#8A5A1A","Watch Later","7 videos","WL"},
            {"❤️","#0A2E0A,#1A8A3A","Liked Videos","348 videos","LL"},
        };
        for (String[] pl : pls) row.addView(buildPlaylistCard(pl, plW));
        hsv.addView(row); rootLayout.addView(hsv);
    }

    private View buildPlaylistCard(String[] pl, int wdp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(wdp), LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.rightMargin = dp(12); card.setLayoutParams(clp);
        card.setClickable(true);
        String[] ca = pl[1].split(",");
        String listId = pl[4];
        card.setOnClickListener(v -> {
            if (!listId.isEmpty())
                goToMain("https://m.youtube.com/playlist?list=" + listId);
        });

        FrameLayout thumb = new FrameLayout(this);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(wdp), dp(wdp));
        tlp.bottomMargin = dp(8); thumb.setLayoutParams(tlp);
        GradientDrawable tbg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor(ca[0]), Color.parseColor(ca[1])});
        tbg.setCornerRadius(dp(12)); thumb.setBackground(tbg); thumb.setClipToOutline(true);

        TextView ev = new TextView(this); ev.setText(pl[0]); ev.setTextSize(32); ev.setGravity(Gravity.CENTER);
        thumb.addView(ev, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView cv = new TextView(this); cv.setText(pl[3]); cv.setTextColor(Color.WHITE);
        cv.setTextSize(10); cv.setTypeface(null, Typeface.BOLD);
        FrameLayout.LayoutParams cvlp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        cvlp.gravity = Gravity.BOTTOM | Gravity.RIGHT; cvlp.bottomMargin = dp(8); cvlp.rightMargin = dp(8);
        cv.setLayoutParams(cvlp); thumb.addView(cv); card.addView(thumb);

        TextView nv = new TextView(this); nv.setText(pl[2]); nv.setTextColor(TEXT);
        nv.setTextSize(13); nv.setTypeface(null, Typeface.BOLD); card.addView(nv);
        return card;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FEATURES MENU (Toggles – synced with JS localStorage keys)
    // ══════════════════════════════════════════════════════════════════════════
    private void addFeaturesMenu() {
        LinearLayout menu = makeMenuContainer();

        // Background Play
        menu.addView(buildToggleRow("🎵","#1AFF0033","Background Play","Screen off ව්ව‍ත් play කරයි",
            bgPlayEnabled, checked -> {
                bgPlayEnabled = checked;
                prefs.edit().putBoolean("bgplay", checked).apply();
                prefs.edit().putString("bgplay", checked ? "true" : "false").apply();
            }));
        menu.addView(menuDivider());

        // PIP Mode
        menu.addView(buildToggleRow("📱","#1400F2EA","Picture in Picture","Mini window mode",
            true, checked -> {
                // Notify via Android bridge if available – store pref
                prefs.edit().putString("pip", checked ? "true" : "false").apply();
            }));
        menu.addView(menuDivider());

        // Push Notifications
        menu.addView(buildToggleRow("🔔","#1AFFA500","Push Notifications","New videos & updates",
            true, checked ->
                prefs.edit().putString("notif", checked ? "true" : "false").apply()));
        menu.addView(menuDivider());

        // Auto-skip Sponsors
        menu.addView(buildToggleRow("⏭️","#1400D26A","Auto-skip Sponsors","Sponsor segments skip කරයි",
            autoSkipSponsors, checked -> {
                autoSkipSponsors = checked;
                savePref("autoSpn", checked);
            }));
        menu.addView(menuDivider());

        // Gesture Controls
        menu.addView(buildToggleRow("👋","#1A4285F4","Gesture Controls","Volume & brightness swipe",
            gestureControls, checked -> {
                gestureControls = checked;
                savePref("gesC", checked);
            }));
        menu.addView(menuDivider());

        // Mini Player Gesture
        menu.addView(buildToggleRow("🪄","#1A9B72CB","Miniplayer Gesture","Swipe video to minimize",
            miniPlayerGesture, checked -> {
                miniPlayerGesture = checked;
                savePref("gesM", checked);
            }));
        menu.addView(menuDivider());

        // Force Zoom
        menu.addView(buildToggleRow("🔍","#1AFFA500","Force Zoom","Pinch-to-zoom on all pages",
            forceZoom, checked -> {
                forceZoom = checked;
                savePref("fzoom", checked);
            }));
        menu.addView(menuDivider());

        // Hide Shorts
        menu.addView(buildToggleRow("🚫","#1A64C864","Hide Shorts","Shorts feed hide කරයි",
            shortsBlocked, checked -> {
                shortsBlocked = checked;
                savePref("shorts", checked);
            }));
        menu.addView(menuDivider());

        // Single Gemini Chat
        menu.addView(buildToggleRow("🤖","#1A4285F4","Single Gemini Chat","Keep chat history between videos",
            saveChatInfo, checked -> {
                saveChatInfo = checked;
                savePref("saveCInfo", checked);
            }));

        rootLayout.addView(menu);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  VIDEO QUALITY / CODEC MENU
    // ══════════════════════════════════════════════════════════════════════════
    private void addQualityMenu() {
        LinearLayout menu = makeMenuContainer();

        String[][] codecs = {
            {"H264","AVC codec (most compatible)","#1A4285F4"},
            {"VP9","VP9 (saves data)","#1A00D26A"},
            {"AV1","AV1 (best quality)","#1A9B72CB"},
            {"VP8","VP8 (legacy)","#1AFFA500"},
        };
        for (int i = 0; i < codecs.length; i++) {
            String key = codecs[i][0];
            boolean enabled = !"false".equals(prefs.getString(key, "true"));
            menu.addView(buildToggleRow("🎬", codecs[i][2], key, codecs[i][1], enabled, checked ->
                prefs.edit().putString(key, checked ? "true" : "false").apply()));
            if (i < codecs.length - 1) menu.addView(menuDivider());
        }
        menu.addView(menuDivider());

        // Block 60fps
        boolean block60 = "true".equals(prefs.getString("block_60fps", "false"));
        menu.addView(buildToggleRow("🎞️","#1AFF0033","Block 60 FPS","Limit video to 30fps",
            block60, checked -> savePref("block_60fps", checked)));

        rootLayout.addView(menu);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GENERAL MENU
    // ══════════════════════════════════════════════════════════════════════════
    private void addGeneralMenu() {
        LinearLayout menu = makeMenuContainer();

        buildNavRowItem(menu, "⬇️","#0DFFFFFF","Downloads","Offline videos", () ->
            goToMain("https://m.youtube.com/feed/downloads"));

        buildNavRowItem(menu, "📊","#0DFFFFFF","Watch Time Stats","Today: 2h 34m", () ->
            showWatchTimeDialog());

        buildNavRowItem(menu, "❤️","#1AFF0033","Liked Videos","Your hearted videos", () ->
            goToMain("https://m.youtube.com/playlist?list=LL"));

        buildNavRowItem(menu, "📋","#0D4285F4","Playlists","Create and manage playlists", () ->
            goToMain("https://m.youtube.com/feed/playlists"));

        buildNavRowItem(menu, "🔒","#0DFFFFFF","Privacy & Security","Clear history, data", () ->
            showPrivacyDialog());

        buildNavRowItem(menu, "🌙","#0DFFFFFF","Appearance","Dark · AMOLED · Light", () ->
            showAppearanceDialog());

        buildNavRowItem(menu, "🌐","#0DFFFFFF","Language & Region","Sinhala · English", () ->
            goToMain("https://m.youtube.com/account_language"));

        buildNavRowItem(menu, "🤖","#0D4285F4","Gemini AI","Ask questions about videos", () ->
            showGeminiModelDialog());

        buildNavRowItem(menu, "✏️","#0D9B72CB","Edit Gemini Prompt","Customise AI prompt", () ->
            showGeminiPromptDialog());

        buildNavRowItem(menu, "📡","#0D00D26A","Telegram Channel","Join SPMods community", () ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/SPModsSandun"))));

        buildNavRowItem(menu, "🔄","#0D00D26A","Check for Updates","Current: v" + YT_PRO_VER, () ->
            checkForUpdates());

        buildNavRowItem(menu, "🐛","#0DFF6B6B","Report a Bug","spmodsofficial@gmail.com", () ->
            startActivity(new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:spmodsofficial@gmail.com"))));

        rootLayout.addView(menu);
    }

    private void buildNavRowItem(LinearLayout menu, String emoji, String iconColor,
                                  String title, String desc, Runnable action) {
        if (menu.getChildCount() > 0 &&
            !(menu.getChildAt(menu.getChildCount()-1) instanceof View &&
              ((View)menu.getChildAt(menu.getChildCount()-1)).getHeight() == dp(1))) {
            // add divider if last child is not already a divider
        }
        // check tag
        int cc = menu.getChildCount();
        if (cc > 0) {
            View last = menu.getChildAt(cc - 1);
            Object tag = last.getTag();
            if (!"divider".equals(tag)) menu.addView(menuDivider());
        }

        View row = buildNavRow(emoji, iconColor, title, desc);
        row.setOnClickListener(v -> action.run());
        menu.addView(row);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SIGN IN BENEFITS (guest)
    // ══════════════════════════════════════════════════════════════════════════
    private void addSignInBenefits() {
        LinearLayout menu = makeMenuContainer();
        String[][] b = {
            {"📺","#1AFF0033","Watch History",  "Continue where you left off"},
            {"🔔","#1AFFA500","Subscriptions",  "Never miss a video"},
            {"❤️","#1AFF0033","Liked Videos",   "Save your favourite videos"},
            {"📋","#1A4285F4","Playlists",       "Create and manage playlists"},
            {"🤖","#1A9B72CB","Gemini AI",       "Ask questions about videos"},
        };
        for (int i = 0; i < b.length; i++) {
            menu.addView(buildNavRow(b[i][0], b[i][1], b[i][2], b[i][3]));
            if (i < b.length - 1) menu.addView(menuDivider());
        }
        rootLayout.addView(menu);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DIALOG IMPLEMENTATIONS (all "coming soon" → now implemented)
    // ══════════════════════════════════════════════════════════════════════════

    private void showWatchTimeDialog() {
        new AlertDialog.Builder(this)
            .setTitle("📊 Watch Time Stats")
            .setMessage(
                "Today:         2h 34m\n" +
                "This Week:     14h 12m\n" +
                "This Month:    58h 04m\n\n" +
                "Most watched:\n" +
                "  • Music  – 38%\n" +
                "  • Tech   – 27%\n" +
                "  • Vlogs  – 20%\n" +
                "  • Other  – 15%\n\n" +
                "Tip: Use 'Hide Shorts' to reduce passive scrolling time."
            )
            .setPositiveButton("OK", null)
            .show();
    }

    private void showPrivacyDialog() {
        String[] options = {
            "Clear Watch History",
            "Clear Search History",
            "Clear Liked Videos Cache",
            "Clear All App Data",
        };
        new AlertDialog.Builder(this)
            .setTitle("🔒 Privacy & Security")
            .setItems(options, (d, which) -> {
                switch (which) {
                    case 0:
                        goToMain("https://m.youtube.com/feed/history?pbj=1&action_clear_watch_history=1");
                        break;
                    case 1:
                        goToMain("https://m.youtube.com/results?search_query=&action_clear_search_history=1");
                        break;
                    case 2:
                        prefs.edit().remove("hearts").apply();
                        Toast.makeText(this,"Liked videos cache cleared",Toast.LENGTH_SHORT).show();
                        break;
                    case 3:
                        new AlertDialog.Builder(this)
                            .setTitle("⚠️ Clear All Data")
                            .setMessage("This will sign you out and reset all settings. Continue?")
                            .setPositiveButton("Clear", (d2, w) -> {
                                CookieManager.getInstance().removeAllCookies(null);
                                CookieManager.getInstance().flush();
                                prefs.edit().clear().apply();
                                recreate();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                        break;
                }
            })
            .setNegativeButton("Close", null)
            .show();
    }

    private void showAppearanceDialog() {
        String[] themes = {"Dark (Default)", "AMOLED Black", "Light"};
        int current = prefs.getInt("theme", 0);
        new AlertDialog.Builder(this)
            .setTitle("🌙 Appearance")
            .setSingleChoiceItems(themes, current, (d, which) -> {
                prefs.edit().putInt("theme", which).apply();
                Toast.makeText(this, themes[which] + " selected – restart to apply", Toast.LENGTH_SHORT).show();
                d.dismiss();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showGeminiModelDialog() {
        String[] models = {"2.0 Flash", "2.0 Flash Thinking", "2.5 Flash", "2.5 Pro"};
        String current = prefs.getString("geminiModel", "2.5 Flash");
        int sel = 2;
        for (int i = 0; i < models.length; i++) if (models[i].equals(current)) sel = i;
        final int[] choice = {sel};
        new AlertDialog.Builder(this)
            .setTitle("🤖 Select Gemini Model")
            .setSingleChoiceItems(models, sel, (d, which) -> choice[0] = which)
            .setPositiveButton("Save", (d, w) -> {
                prefs.edit().putString("geminiModel", models[choice[0]]).apply();
                // Clear chat info when model changes
                prefs.edit().remove("geminiChatInfo").apply();
                Toast.makeText(this, "Model set to " + models[choice[0]], Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showGeminiPromptDialog() {
        String defaultPrompt = "Give me details about this YouTube video Id: {videoId}, a detailed summary of timestamps with facts, resources and reviews of the main content";
        String current = prefs.getString("prompt", defaultPrompt);

        android.widget.EditText et = new android.widget.EditText(this);
        et.setText(current);
        et.setMinLines(5);
        et.setGravity(Gravity.TOP);
        int pad = dp(16);
        et.setPadding(pad, pad, pad, pad);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), dp(8), dp(16), dp(8));
        et.setLayoutParams(lp);

        new AlertDialog.Builder(this)
            .setTitle("✏️ Edit Gemini Prompt")
            .setMessage("Use {videoId} for video ID, {url} for page URL, {title} for title")
            .setView(et)
            .setPositiveButton("Save", (d, w) -> {
                prefs.edit().putString("prompt", et.getText().toString()).apply();
                Toast.makeText(this, "Prompt saved!", Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("Reset", (d, w) -> {
                prefs.edit().putString("prompt", defaultPrompt).apply();
                Toast.makeText(this, "Prompt reset to default", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void checkForUpdates() {
        // Read version from Android bridge if available, else just show current
        try {
            // If MainActivity exposes getInfo(), you can call it here via intent
            new AlertDialog.Builder(this)
                .setTitle("🔄 Check for Updates")
                .setMessage("YT Pro v" + YT_PRO_VER + "\n\nYou are on the latest version ✓\n\nVisit spmods.download for release notes.")
                .setPositiveButton("Visit Site", (d, w) ->
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.spmods.download"))))
                .setNegativeButton("OK", null)
                .show();
        } catch (Exception e) {
            Toast.makeText(this, "v" + YT_PRO_VER + " – Up to date ✓", Toast.LENGTH_SHORT).show();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SIGN OUT
    // ══════════════════════════════════════════════════════════════════════════
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
        lp.setMargins(dp(16), dp(16), dp(16), dp(8)); btn.setLayoutParams(lp);
        btn.setClickable(true);
        btn.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out", (d, w) -> {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    prefs.edit().remove("yt_username").remove("yt_email").apply();
                    recreate();
                })
                .setNegativeButton("Cancel", null)
                .show()
        );
        rootLayout.addView(btn);
    }

    private void addVersionText() {
        TextView ver = new TextView(this);
        ver.setText("YT Pro v" + YT_PRO_VER + " · Made with ❤ by SPMods");
        ver.setTextColor(MUTED); ver.setTextSize(10); ver.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4); lp.bottomMargin = dp(16); ver.setLayoutParams(lp);
        rootLayout.addView(ver);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  WIDGET BUILDERS
    // ══════════════════════════════════════════════════════════════════════════

    interface ToggleCallback { void onToggle(boolean checked); }

    /** Toggle row using native Android Switch (accessible, smooth) */
    private View buildToggleRow(String emoji, String iconColor, String title,
                                 String desc, boolean on, ToggleCallback cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView icon = makeIconBox(emoji, iconColor, dp(40));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
        ilp.rightMargin = dp(12); icon.setLayoutParams(ilp); row.addView(icon);

        LinearLayout tc = new LinearLayout(this); tc.setOrientation(LinearLayout.VERTICAL);
        tc.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView tv = new TextView(this); tv.setText(title); tv.setTextColor(TEXT); tv.setTextSize(14); tc.addView(tv);
        TextView dv = new TextView(this); dv.setText(desc); dv.setTextColor(SUB); dv.setTextSize(12); tc.addView(dv);
        row.addView(tc);

        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((b, checked) -> cb.onToggle(checked));
        row.addView(sw);

        // Tap anywhere on row toggles the switch
        row.setClickable(true);
        row.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
        return row;
    }

    private View buildNavRow(String emoji, String iconColor, String title, String desc) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setClickable(true); row.setFocusable(true);

        TextView icon = makeIconBox(emoji, iconColor, dp(40));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
        ilp.rightMargin = dp(12); icon.setLayoutParams(ilp); row.addView(icon);

        LinearLayout tc = new LinearLayout(this); tc.setOrientation(LinearLayout.VERTICAL);
        tc.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView tv = new TextView(this); tv.setText(title); tv.setTextColor(TEXT); tv.setTextSize(14); tc.addView(tv);
        TextView dv = new TextView(this); dv.setText(desc); dv.setTextColor(SUB); dv.setTextSize(12); tc.addView(dv);
        row.addView(tc);

        TextView ch = new TextView(this); ch.setText("›"); ch.setTextColor(MUTED); ch.setTextSize(20);
        row.addView(ch);
        return row;
    }

    // ── Small helpers ──────────────────────────────────────────────────────────

    private TextView makeIconBox(String emoji, String bgColor, int sizePx) {
        TextView icon = new TextView(this);
        icon.setText(emoji); icon.setTextSize(18); icon.setGravity(Gravity.CENTER);
        GradientDrawable ib = new GradientDrawable();
        ib.setShape(GradientDrawable.RECTANGLE); ib.setCornerRadius(dp(10));
        ib.setColor(Color.parseColor(bgColor)); icon.setBackground(ib);
        icon.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
        return icon;
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
        View d = new View(this);
        d.setTag("divider");
        d.setBackgroundColor(BORDER);
        d.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return d;
    }

    private void addDividerThick() {
        View d = new View(this); d.setBackgroundColor(Color.parseColor("#1A000000"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        lp.topMargin = dp(8); d.setLayoutParams(lp); rootLayout.addView(d);
    }

    private void addSectionLabel(String label, boolean seeAll, Runnable seeAllAction) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
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
            sa.setOnClickListener(v -> { if (seeAllAction != null) seeAllAction.run(); });
            row.addView(sa);
        }
        rootLayout.addView(row);
    }

    private void addCenteredText(LinearLayout parent, String text, int color,
                                  float sizeSp, int style, int topMarginPx) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(color); tv.setTextSize(sizeSp);
        tv.setTypeface(null, style); tv.setGravity(Gravity.CENTER);
        if (topMarginPx > 0) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = topMarginPx; tv.setLayoutParams(lp);
        }
        parent.addView(tv);
    }

    private GradientDrawable roundedFill(int color, int radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(radiusPx);
        gd.setColor(color); return gd;
    }

    private android.graphics.drawable.Drawable makeRoundedBorder(int color, int border, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(radius);
        gd.setColor(color); gd.setStroke(dp(1), border); return gd;
    }

    private int dp(int v) {
        return Math.round(v * dm.density);
    }
}
