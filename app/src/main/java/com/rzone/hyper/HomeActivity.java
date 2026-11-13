package com.rzone.hyper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Hyper Launcher: layar utama, dock, folder, App Drawer / App Library. */
public class HomeActivity extends Activity implements AppCell.L {
    static final String EXTRA_DRAWER = "drawer";

    /** Daftar aplikasi (cache antar-recreate). HANYA diubah di main thread. */
    static final List<Item> ALL = new ArrayList<>();
    static boolean loaded, introDone;
    static String loadedSig = "", lastSig = "";
    static final String[] PREF = {"com.google.android.apps.photos", "com.google.android.apps.maps", "com.android.chrome",
            "com.google.android.youtube", "com.google.android.gm", "com.google.android.calendar",
            "com.google.android.apps.messaging", "com.google.android.deskclock", "com.android.settings",
            "com.google.android.apps.youtube.music", "com.google.android.apps.docs", "com.google.android.googlequicksearchbox"};

    Prefs pr;
    SharedPreferences sp;
    UiStyle us;
    Widgets wd;
    LauncherApps la;
    final Handler h = new Handler(Looper.getMainLooper());
    int W, H, ui, ac, os, cols = 4, rows = 4, hn = 4, sbs;
    float d, top, navH, speed = 1, wallBase = 1;
    float searchTop, searchH, searchW, hotTop, hotH, hotLeft, hcw, glanceTop, pagerTop, pagerH, cellW, cellH, dropH;
    boolean sbShow, sbTop, glOn, dockPanel, locked, recreating, alive;
    /** iOS: App Library muncul dari kanan (geser melewati halaman terakhir), bukan drawer dari bawah. */
    boolean side;

    HomeRoot root;
    ImageView wall, blurV;
    View scrim, hl, dockV;
    FrameLayout home, pager, drawer, overlay, dropBar, fxLayer;
    TextClock glance;
    Dots dots;
    SearchBar search;
    TextView zl, zr, webRow;
    LinearLayout drawerCol, recentBox;
    GridView grid, libGrid;
    EditText et;
    TextView libAz, libCat;
    BaseAdapter ad, libAd;

    final List<Item[]> pages = new ArrayList<>();
    final Item[] hot = new Item[5];
    final List<AppCell> hotCells = new ArrayList<>();
    final List<Item> shown = new ArrayList<>();
    final Set<Integer> seen = new HashSet<>();
    final List<Object[]> libCats = new ArrayList<>();
    final PathInterpolator emph = new PathInterpolator(0.2f, 0, 0, 1);
    String wsig = "";
    float pagePos, dp;
    ValueAnimator posAnim, dpAnim;
    boolean libMode;

    // seret & lepas
    Item dragItem, curFolder, dragFolder;
    int dragSrc, dragPage, dragSlot, ovToken;
    boolean dragging, dropped;
    Runnable edgeR;

    private final LauncherApps.Callback laCb = new LauncherApps.Callback() {
        @Override
        public void onPackageRemoved(String p, UserHandle u) {
            reloadApps();
        }

        @Override
        public void onPackageAdded(String p, UserHandle u) {
            reloadApps();
        }

        @Override
        public void onPackageChanged(String p, UserHandle u) {
            reloadApps();
        }

        @Override
        public void onPackagesAvailable(String[] p, UserHandle u, boolean r) {
            reloadApps();
        }

        @Override
        public void onPackagesUnavailable(String[] p, UserHandle u, boolean r) {
            reloadApps();
        }
    };

    static float cl(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    long T(long ms) {
        return (long) (ms * speed);
    }

    float dp(float v) {
        return v * d;
    }

    // =================================================================== siklus hidup

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        alive = true;
        pr = new Prefs(this);
        sp = getSharedPreferences("home", MODE_PRIVATE);
        la = getSystemService(LauncherApps.class);
        wd = new Widgets(this);
        lastSig = pr.launcherSig();
        os = pr.os();
        ui = pr.gi("ui");
        ac = pr.gi("ac");
        speed = 100f / Math.max(50, pr.gi("as"));
        AppCell.SCALE = pr.gi("isz") / 100f;
        AppCell.LABEL_SP = pr.gi("ls");
        AppCell.BADGE = pr.gi("bdg");
        AppCell.BADGE_COLOR = os == Prefs.OS_PIXEL ? ac : 0xFFFF453A;
        AppCell.FOLDER_RAD = pr.gi("is") == 0 ? 0.5f : 0.3f;
        AppCell.FOLDER_BG = Zth.folderBg(this, 240);
        cols = Math.max(3, Math.min(6, pr.gi("hc")));
        hn = Math.max(3, Math.min(5, pr.gi("dn")));
        locked = pr.gb("lck");
        sbShow = pr.gb("sv");
        sbTop = pr.gi("sp") == 1;
        sbs = pr.gi("sbs");
        glOn = pr.gb("gl");
        dockPanel = pr.gb("dk");
        side = os == Prefs.OS_IOS;
        d = getResources().getDisplayMetrics().density;
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);
        W = m.widthPixels;
        H = m.heightPixels;
        estimateInsets();

        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        us = new UiStyle(this);
        root = new HomeRoot();
        Bitmap bg = Scene.homeBg(this, W / 2, H / 2);
        wsig = wallSig();
        us.init(bg, W, H);

        wall = new ImageView(this);
        wall.setImageBitmap(bg);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wall.setColorFilter(new PorterDuffColorFilter(Color.argb(pr.gi("hd") * 255 / 100, 0, 0, 0), PorterDuff.Mode.SRC_ATOP));
        wallBase = pr.gb("wp") ? 1.1f : 1f;
        wall.setScaleX(wallBase);
        wall.setScaleY(wallBase);
        blurV = new ImageView(this);
        blurV.setImageBitmap(us.blur);
        blurV.setScaleType(ImageView.ScaleType.CENTER_CROP);
        blurV.setAlpha(0f);
        scrim = new View(this);
        scrim.setBackgroundColor(Color.BLACK);
        scrim.setAlpha(0f);
        root.addView(wall, new FrameLayout.LayoutParams(-1, -1));
        root.addView(blurV, new FrameLayout.LayoutParams(-1, -1));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        home = new FrameLayout(this);
        glance = new TextClock(this);
        glance.setFormat24Hour("EEE, d MMM");
        glance.setFormat12Hour("EEE, d MMM");
        glance.setTextSize(pr.gi("gs"));
        glance.setTextColor(Color.WHITE);
        glance.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        glance.setShadowLayer(dp(8), 0, dp(2), Color.argb(110, 0, 0, 0));
        glance.setVisibility(glOn ? View.VISIBLE : View.GONE);
        glance.setOnClickListener(v -> Util.start(this, new Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis()))));
        home.addView(glance, new FrameLayout.LayoutParams(-2, -2));
        pager = new FrameLayout(this);
        home.addView(pager, new FrameLayout.LayoutParams(W, 10));
        hl = new View(this);
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(Color.argb(60, 255, 255, 255));
        hg.setCornerRadius(dp(24));
        hl.setBackground(hg);
        hl.setVisibility(View.GONE);
        home.addView(hl, new FrameLayout.LayoutParams(10, 10));
        dockV = new View(this);
        dockV.setBackground(us.panel(dockV, dp(os == Prefs.OS_IOS ? 34 : 30), ui, ac));
        dockV.setVisibility(dockPanel ? View.VISIBLE : View.GONE);
        home.addView(dockV, new FrameLayout.LayoutParams(10, 10));
        dots = new Dots(this);
        home.addView(dots, new FrameLayout.LayoutParams(W, (int) dp(14)));
        search = new SearchBar(this, us, ui, ac, sbs);
        search.setVisibility(sbShow ? View.VISIBLE : View.GONE);
        search.setOnClickListener(v -> searchClick());
        search.setOnLongClickListener(v -> {
            openTira(true);
            return true;
        });
        Util.press(search);
        home.addView(search, new FrameLayout.LayoutParams(10, 10));
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));

        buildDrawer();

        dropBar = new FrameLayout(this);
        dropBar.setVisibility(View.GONE);
        dropBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xCC000000, 0}));
        zl = zone("Hapus", Glyph.TRASH);
        zr = zone("Hapus pemasangan", Glyph.TRASH);
        dropBar.addView(zl, new FrameLayout.LayoutParams(W / 2, 10, Gravity.START));
        dropBar.addView(zr, new FrameLayout.LayoutParams(W / 2, 10, Gravity.END));
        root.addView(dropBar, new FrameLayout.LayoutParams(-1, 10));
        overlay = new FrameLayout(this);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        fxLayer = new FrameLayout(this);
        root.addView(fxLayer, new FrameLayout.LayoutParams(-1, -1));
        root.setOnDragListener(this::onDrag);
        root.setOnApplyWindowInsetsListener(this::onInsets);
        root.getViewTreeObserver().addOnGlobalLayoutListener(this::imePad);
        setContentView(root);
        place();

        try {
            la.registerCallback(laCb, h);
        } catch (Exception ignored) {
        }
        NotifListener.onChange = this::refreshBadges;
        if (!loaded) reloadApps();
        else ready();
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_DRAWER, false)) h.postDelayed(() -> animDp(1), 120);
        ciWidget(getIntent());
    }

    @Override
    protected void onDestroy() {
        alive = false;
        try {
            la.unregisterCallback(laCb);
        } catch (Exception ignored) {
        }
        if (NotifListener.onChange != null) NotifListener.onChange = null;
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!lastSig.equals(pr.launcherSig()) || !wsig.equals(wallSig())) {
            lastSig = pr.launcherSig();
            recreating = true;
            recreate();
            return;
        }
        NotifListener.onChange = this::refreshBadges;
        refreshBadges();
        if (loaded) root.post(this::playTransitions);
        if (!pr.iconSig().equals(loadedSig)) {
            IconPack.invalidate();
            reloadApps();
        } else if (loaded) {
            checkInstalled();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (dragging) endDrag();
    }

    @Override
    public void onBackPressed() {
        goHome();
    }

    /** Uji CI (build debug saja): tambah widget otomatis. */
    void ciWidget(Intent i) {
        if (i == null || !i.hasExtra("ci_widget")) return;
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) return;
        final String hint = i.getStringExtra("ci_widget");
        i.removeExtra("ci_widget");
        h.postDelayed(() -> wd.addFirst(hint), 2500);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        ciWidget(i);
        if (i != null && i.getBooleanExtra(EXTRA_DRAWER, false)) {
            resetOverlay();
            animDp(1);
            return;
        }
        goHome();
    }

    void goHome() {
        if (overlay.getChildCount() > 0) dismissOverlay();
        else if (dp > 0 && libMode && grid.getVisibility() == View.VISIBLE && et.getText().length() == 0) filter("");
        else if (dp > 0) animDp(0);
        else if (pagePos > 0.01f) snapTo(0);
    }

    String wallSig() {
        return pr.gi("hm") + "," + pr.gi("hw") + "," + pr.gi("ld") + "," + pr.gi("lm") + ","
                + Scene.wpFile(this).lastModified() + "," + Scene.homeFile(this).lastModified();
    }

    // =================================================================== tata letak

    private void estimateInsets() {
        int sid = getResources().getIdentifier("status_bar_height", "dimen", "android");
        top = sid > 0 ? getResources().getDimensionPixelSize(sid) : dp(28);
        int mode = Settings.Secure.getInt(getContentResolver(), "navigation_mode", 0);
        int nid = getResources().getIdentifier(mode == 2 ? "navigation_bar_gesture_height" : "navigation_bar_height", "dimen", "android");
        navH = nid > 0 ? getResources().getDimensionPixelSize(nid) : dp(20);
    }

    /** Perbaikan: pakai inset jendela yang sebenarnya (dulu hanya menebak dari resource → dock bisa tertutup navbar). */
    @SuppressWarnings("deprecation")
    private WindowInsets onInsets(View v, WindowInsets in) {
        float t = in.getSystemWindowInsetTop(), n = in.getSystemWindowInsetBottom();
        if (t > 0 && (Math.abs(t - top) > 1 || Math.abs(n - navH) > 1)) {
            top = t;
            navH = n;
            place();
            if (loaded && !dragging) {
                normalizeLayout();
                rebuild();
            }
        }
        return in;
    }

    /** Hitung & terapkan posisi semua elemen layar utama. */
    void place() {
        boolean ios = sbs == 1;
        searchH = ios ? dp(34) : dp(56);
        searchW = ios ? dp(96) : W - dp(32);
        hotH = dp(dockPanel ? 92 : 88);
        float bp = navH + dp(12);
        float f;
        if (sbShow && !sbTop && !ios) {
            searchTop = H - bp - searchH;
            f = searchTop - dp(8);
        } else {
            f = H - bp;
        }
        hotTop = f - hotH;
        if (sbShow && sbTop && !ios) {
            searchTop = top + dp(10);
            glanceTop = top + dp(76);
            pagerTop = top + dp(glOn ? 150 : 84);
        } else {
            glanceTop = top + dp(14);
            pagerTop = top + dp(glOn ? 100 : 24);
        }
        float gapBottom = ios && sbShow ? dp(54) : dp(24);
        pagerH = hotTop - gapBottom - pagerTop;
        if (ios && sbShow) searchTop = hotTop - dp(44);
        int hr = pr.gi("hr");
        rows = hr > 0 ? hr : (int) cl((float) Math.floor(pagerH / dp(88)), 3, 7);
        cellW = (float) W / cols;
        cellH = pagerH / rows;
        float dockW = dockPanel ? W - dp(24) : W;
        hotLeft = (W - dockW) / 2f;
        hcw = dockW / hn;
        dropH = top + dp(76);

        FrameLayout.LayoutParams gp = (FrameLayout.LayoutParams) glance.getLayoutParams();
        gp.leftMargin = (int) dp(24);
        gp.topMargin = (int) glanceTop;
        glance.setLayoutParams(gp);
        FrameLayout.LayoutParams pp = (FrameLayout.LayoutParams) pager.getLayoutParams();
        pp.width = W;
        pp.height = (int) pagerH;
        pp.topMargin = (int) pagerTop;
        pager.setLayoutParams(pp);
        FrameLayout.LayoutParams dl = (FrameLayout.LayoutParams) dockV.getLayoutParams();
        dl.width = (int) (W - dp(24));
        dl.height = (int) hotH;
        dl.leftMargin = (int) dp(12);
        dl.topMargin = (int) hotTop;
        dockV.setLayoutParams(dl);
        FrameLayout.LayoutParams dpl = (FrameLayout.LayoutParams) dots.getLayoutParams();
        dpl.topMargin = (int) (pagerTop + pagerH + dp(4));
        dots.setLayoutParams(dpl);
        dots.setVisibility(ios && sbShow ? View.GONE : View.VISIBLE);
        FrameLayout.LayoutParams sl = (FrameLayout.LayoutParams) search.getLayoutParams();
        sl.width = (int) searchW;
        sl.height = (int) searchH;
        sl.leftMargin = (int) ((W - searchW) / 2f);
        sl.topMargin = (int) searchTop;
        search.setLayoutParams(sl);
        FrameLayout.LayoutParams hlp = (FrameLayout.LayoutParams) hl.getLayoutParams();
        hlp.width = (int) cellW;
        hlp.height = (int) cellH;
        hl.setLayoutParams(hlp);
        for (int i = 0; i < dropBar.getChildCount(); i++) {
            View z = dropBar.getChildAt(i);
            FrameLayout.LayoutParams zp = (FrameLayout.LayoutParams) z.getLayoutParams();
            zp.height = (int) dropH;
            z.setLayoutParams(zp);
            z.setPadding(0, (int) top, 0, 0);
        }
        ViewGroup.LayoutParams dbl = dropBar.getLayoutParams();
        dbl.height = (int) dropH;
        dropBar.setLayoutParams(dbl);
        if (drawerCol != null) {
            drawerCol.setPadding(0, (int) (top + dp(12)), 0, drawerCol.getPaddingBottom());
        }
    }

    private int lastImePad = -1;

    private void imePad() {
        Rect r = new Rect();
        root.getWindowVisibleDisplayFrame(r);
        int hidden = root.getRootView().getHeight() - r.bottom;
        int pad = hidden > H * 0.15f ? (int) dp(8) + hidden : (int) (navH + dp(14));
        if (pad != lastImePad) {
            lastImePad = pad;
            drawerCol.setPadding(0, (int) (top + dp(12)), 0, pad);
        }
    }

    TextView zone(String t, int glyph) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Color.WHITE);
        v.setTextSize(14);
        v.setGravity(Gravity.CENTER);
        v.setAlpha(0.7f);
        return v;
    }

    void searchClick() {
        if (search.tappedMic()) {
            openTira(true); // mikrofon di search bar = Tira
            return;
        }
        if (sbs == 0) {
            Intent g = getPackageManager().getLaunchIntentForPackage("com.google.android.googlequicksearchbox");
            if (Util.start(this, new Intent(Intent.ACTION_WEB_SEARCH))) return;
            if (g != null && Util.start(this, g)) return;
        }
        openSearch();
    }

    void openTira(boolean voice) {
        Util.start(this, new Intent(this, TiraActivity.class).putExtra("voice", voice));
    }

    /** Buka drawer dengan keyboard siap mengetik. */
    void openSearch() {
        animDp(1);
        h.postDelayed(() -> {
            et.requestFocus();
            InputMethodManager im = getSystemService(InputMethodManager.class);
            if (im != null) im.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
        }, T(260));
    }

    // =================================================================== daftar aplikasi

    private volatile int loadGen;

    void reloadApps() {
        final int gen = ++loadGen;
        final Context app = getApplicationContext();
        new Thread(() -> {
            final List<Item> l = loadApps(app);
            final String sig = new Prefs(app).iconSig();
            h.post(() -> {
                if (gen != loadGen) return; // ada muatan yang lebih baru
                synchronized (ALL) {
                    ALL.clear();
                    ALL.addAll(l);
                }
                loadedSig = sig;
                loaded = true;
                if (alive && !isFinishing()) {
                    if (dragging) return; // jangan bangun ulang saat menyeret
                    ready();
                }
            });
        }, "hyper-apps").start();
    }

    /** Dijalankan di background; hanya mengembalikan daftar baru (tidak menyentuh ALL). */
    static List<Item> loadApps(Context x) {
        PackageManager pm = x.getPackageManager();
        Prefs pf = new Prefs(x);
        int shape = pf.gi("is"), style = pf.gi("ik"), acc = pf.gi("ac");
        int sz = (int) (x.getResources().getDisplayMetrics().density * 64);
        String pack = pf.str("ipk");
        Map<String, String> names = pf.names();
        List<Item> l = new ArrayList<>();
        List<ResolveInfo> ris;
        try {
            ris = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        } catch (Exception e) {
            ris = new ArrayList<>();
        }
        Set<String> seenKeys = new HashSet<>();
        for (ResolveInfo r : ris) {
            if (r.activityInfo == null) continue;
            Item it = new Item();
            it.pkg = r.activityInfo.packageName;
            it.cls = r.activityInfo.name;
            if (!seenKeys.add(it.key())) continue;
            try {
                it.original = r.loadLabel(pm).toString();
            } catch (Exception e) {
                it.original = it.pkg;
            }
            String nm = names.get(it.key());
            it.label = nm != null ? nm : it.original;
            try {
                Drawable ic = r.loadIcon(pm);
                it.icon = Zth.appIcon(x, pf, it.pkg, it.cls, it.original, ic, sz);
            } catch (Throwable e) {
                it.icon = Icons.fallback(x, sz);
            }
            l.add(it);
        }
        Collections.sort(l, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return l;
    }

    /** Cek cepat apakah ada aplikasi yang dipasang/dihapus saat launcher tidak aktif. */
    void checkInstalled() {
        final Context app = getApplicationContext();
        final Set<String> have = new HashSet<>();
        for (Item a : ALL) have.add(a.key());
        new Thread(() -> {
            Set<String> now = new HashSet<>();
            try {
                for (ResolveInfo r : app.getPackageManager().queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
                    if (r.activityInfo != null)
                        now.add(r.activityInfo.packageName + "/" + r.activityInfo.name);
                }
            } catch (Exception e) {
                return;
            }
            if (!now.equals(have)) h.post(this::reloadApps);
        }, "hyper-check").start();
    }

    Item find(String key) {
        for (Item a : ALL) if (a.key().equals(key)) return a;
        return null;
    }

    Item byPkg(String p) {
        for (Item a : ALL) if (a.pkg.equals(p)) return a;
        return null;
    }

    List<Item> visibleApps() {
        Set<String> hid = pr.hidden();
        List<Item> out = new ArrayList<>();
        for (Item a : ALL) if (!hid.contains(a.key())) out.add(a);
        return out;
    }

    // =================================================================== simpan / muat tata letak

    Item[] blank() {
        return new Item[cols * rows];
    }

    String enc(Item it) {
        if (it == null) return "-";
        if (it.kids == null) return it.key();
        StringBuilder b = new StringBuilder("F(" + it.label.replaceAll("[()|,;#]", " ") + "|");
        for (Item k : it.kids) b.append(k.key()).append(',');
        return b.append(')').toString();
    }

    Item dec(String s) {
        if (s.isEmpty() || s.equals("-")) return null;
        if (s.startsWith("F(") && s.endsWith(")")) {
            String in = s.substring(2, s.length() - 1);
            int bar = in.indexOf('|');
            if (bar < 0) return null;
            Item f = new Item();
            f.label = in.substring(0, bar);
            f.kids = new ArrayList<>();
            for (String k : in.substring(bar + 1).split(",")) {
                Item a = find(k);
                if (a != null) f.kids.add(a.copy());
            }
            if (f.kids.isEmpty()) return null;
            if (f.kids.size() == 1) return f.kids.get(0); // folder berisi 1 aplikasi → jadikan ikon biasa
            return f;
        }
        Item a = find(s);
        return a == null ? null : a.copy();
    }

    void save() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) b.append('#');
            Item[] a = pages.get(i);
            for (int s = 0; s < a.length; s++) {
                if (s > 0) b.append(';');
                b.append(enc(a[s]));
            }
        }
        StringBuilder hs = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (i > 0) hs.append(';');
            hs.append(enc(hot[i]));
        }
        sp.edit().putString("pages", b.toString()).putString("hot", hs.toString()).putString("grid", cols + "x" + rows).apply();
    }

    void loadLayout() {
        pages.clear();
        Arrays.fill(hot, null);
        String ps = sp.getString("pages", null);
        if (ps == null) {
            defaultLayout();
            return;
        }
        String grid = sp.getString("grid", cols + "x" + rows);
        boolean same = grid.equals(cols + "x" + rows);
        List<Item> flat = new ArrayList<>();
        for (String pg : ps.split("#", -1)) {
            String[] sl = pg.split(";", -1);
            if (same) {
                Item[] a = blank();
                for (int i = 0; i < a.length && i < sl.length; i++) a[i] = dec(sl[i]);
                pages.add(a);
            } else {
                // Perbaikan: ukuran grid berubah → susun ulang berurutan (dulu ikon bergeser/hilang).
                for (String s : sl) {
                    Item it = dec(s);
                    if (it != null) flat.add(it);
                }
            }
        }
        if (!same) {
            Item[] a = blank();
            int n = 0;
            for (Item it : flat) {
                if (n >= a.length) {
                    pages.add(a);
                    a = blank();
                    n = 0;
                }
                a[n++] = it;
            }
            pages.add(a);
        }
        String[] hs = sp.getString("hot", "").split(";", -1);
        for (int i = 0; i < 5 && i < hs.length; i++) hot[i] = dec(hs[i]);
        if (pages.isEmpty()) pages.add(blank());
    }

    void defaultLayout() {
        Item[] pg = blank();
        int n = 0, max = Math.min(8, pg.length);
        Set<String> used = new HashSet<>();
        List<Item> vis = visibleApps();
        for (String p : PREF) {
            Item it = byPkg(p);
            if (it != null && n < max && used.add(it.key())) pg[n++] = it.copy();
        }
        for (Item it : vis) {
            if (n >= max) break;
            if (used.add(it.key())) pg[n++] = it.copy();
        }
        pages.add(pg);
        PackageManager pm = getPackageManager();
        Intent[] hi = {new Intent(Intent.ACTION_DIAL), new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
                new Intent(Intent.ACTION_VIEW, Uri.parse("http://")), new Intent("android.media.action.STILL_IMAGE_CAMERA")};
        Set<String> hotPk = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            try {
                ResolveInfo ri = pm.resolveActivity(hi[i], PackageManager.MATCH_DEFAULT_ONLY);
                if (ri == null || ri.activityInfo == null || ri.activityInfo.packageName.equals("android")) continue;
                Item f = byPkg(ri.activityInfo.packageName);
                if (f != null && hotPk.add(f.pkg)) hot[i] = f.copy();
            } catch (Exception ignored) {
            }
        }
        // Perbaikan: v5.1 bisa berputar tanpa henti di sini bila jumlah aplikasi sedikit.
        int idx = 0;
        for (int i = 0; i < 5; i++) {
            if (hot[i] != null) continue;
            while (idx < vis.size() && (hotPk.contains(vis.get(idx).pkg) || used.contains(vis.get(idx).key()))) idx++;
            if (idx >= vis.size()) break;
            hot[i] = vis.get(idx).copy();
            hotPk.add(hot[i].pkg);
            idx++;
        }
    }

    /** Pindahkan ikon dock yang tidak muat (jumlah dock dikurangi) ke halaman. */
    @Override
    protected void onStart() {
        super.onStart();
        wd.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        wd.stop();
    }

    @Override
    protected void onActivityResult(int rq, int rs, Intent data) {
        super.onActivityResult(rq, rs, data);
        wd.onResult(rq, rs);
    }

    void normalizeLayout() {
        wd.normalize();
        for (int i = hn; i < 5; i++) {
            if (hot[i] != null) {
                placeFree(hot[i]);
                hot[i] = null;
            }
        }
        for (int i = 0; i < pages.size(); i++) {
            Item[] a = pages.get(i);
            if (a.length != cols * rows) {
                Item[] b = blank();
                List<Item> spill = new ArrayList<>();
                for (int s = 0; s < a.length; s++) {
                    if (a[s] == null) continue;
                    if (s < b.length) b[s] = a[s];
                    else spill.add(a[s]);
                }
                pages.set(i, b);
                for (Item it : spill) placeFree(it);
            }
        }
    }

    void ready() {
        if (recreating) return;
        loadLayout();
        normalizeLayout();
        save();
        rebuild();
        refreshRecent();
        filter(et.getText().toString());
        if (!introDone) {
            introDone = true;
            intro();
        }
    }

    // =================================================================== halaman & dock

    AppCell mk(Item it, int src, int page, int slot) {
        AppCell c = new AppCell(this);
        c.l = this;
        c.src = src;
        c.page = page;
        c.slot = slot;
        c.labels = src == AppCell.SRC_DOCK ? false : src == AppCell.SRC_DRAWER ? pr.gb("dl") : (pr.gb("lv") || src == AppCell.SRC_FOLDER);
        c.bind(it);
        if (dragging && it == dragItem) c.setVisibility(View.INVISIBLE);
        return c;
    }

    void buildPages() {
        pager.removeAllViews();
        for (int pi = 0; pi < pages.size(); pi++) {
            FrameLayout pg = new FrameLayout(this);
            Item[] a = pages.get(pi);
            for (int s = 0; s < a.length; s++) {
                if (a[s] == null) continue;
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) cellW, (int) cellH);
                lp.leftMargin = (int) ((s % cols) * cellW);
                lp.topMargin = (int) ((s / cols) * cellH);
                pg.addView(mk(a[s], AppCell.SRC_PAGE, pi, s), lp);
            }
            wd.addViews(pg, pi);
            pager.addView(pg, new FrameLayout.LayoutParams(-1, -1));
        }
        setPos(cl(pagePos, 0, pages.size() - 1));
    }

    void buildHot() {
        for (AppCell c : hotCells) home.removeView(c);
        hotCells.clear();
        for (int i = 0; i < hn; i++) {
            if (hot[i] == null) continue;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) hcw, (int) hotH);
            lp.leftMargin = (int) (hotLeft + i * hcw);
            lp.topMargin = (int) hotTop;
            AppCell c = mk(hot[i], AppCell.SRC_DOCK, 0, i);
            home.addView(c, lp);
            hotCells.add(c);
        }
    }

    void rebuild() {
        buildPages();
        buildHot();
    }

    void refreshBadges() {
        inval(root);
    }

    private void inval(View v) {
        if (v instanceof AppCell) v.invalidate();
        else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) inval(g.getChildAt(i));
        }
    }

    void setPos(float p) {
        pagePos = p;
        int n = pager.getChildCount();
        int pt = pr.gi("pt");
        for (int i = 0; i < n; i++) {
            View v = pager.getChildAt(i);
            float off = i - p, ab = Math.min(1, Math.abs(off));
            v.setTranslationX(W * off);
            v.setRotationY(0);
            v.setScaleX(1);
            v.setScaleY(1);
            v.setAlpha(1);
            v.setVisibility(Math.abs(off) >= 1.2f ? View.INVISIBLE : View.VISIBLE);
            if (pt == 1) {
                v.setCameraDistance(W * 8);
                v.setPivotX(off <= 0 ? W : 0);
                v.setPivotY(pagerH / 2);
                v.setRotationY(-off * 35);
                v.setAlpha(1 - 0.35f * ab);
            } else if (pt == 2) {
                v.setPivotX(W / 2f);
                v.setPivotY(pagerH / 2);
                float s = 1 - 0.25f * ab;
                v.setScaleX(s);
                v.setScaleY(s);
                v.setAlpha(1 - 0.7f * ab);
            } else if (pt == 3 && off < 0) {
                v.setPivotX(W / 2f);
                v.setPivotY(pagerH / 2);
                v.setTranslationX(W * off * 0.35f);
                float s = 1 + 0.15f * off;
                v.setScaleX(s);
                v.setScaleY(s);
                v.setAlpha(1 - ab);
            }
        }
        if (wallBase > 1) wall.setTranslationX(-p * 18 * d);
        dots.set(Math.max(1, pages.size()), p);
    }

    void snapTo(int idx) {
        if (posAnim != null) posAnim.cancel();
        posAnim = ValueAnimator.ofFloat(pagePos, idx);
        posAnim.setDuration(T(340));
        posAnim.setInterpolator(new DecelerateInterpolator(2.2f));
        posAnim.addUpdateListener(a -> setPos((Float) a.getAnimatedValue()));
        posAnim.start();
    }

    void intro() {
        int k = 0;
        List<View> vs = new ArrayList<>();
        if (pager.getChildCount() > 0) {
            ViewGroup pg = (ViewGroup) pager.getChildAt(0);
            for (int i = 0; i < pg.getChildCount(); i++) vs.add(pg.getChildAt(i));
        }
        vs.addAll(hotCells);
        for (View v : vs) {
            v.setAlpha(0);
            v.setScaleX(0.6f);
            v.setScaleY(0.6f);
            v.setTranslationY(dp(36));
            v.animate().alpha(1).scaleX(1).scaleY(1).translationY(0).setStartDelay(T(60 + k * 24)).setDuration(T(560))
                    .setInterpolator(new OvershootInterpolator(1.2f)).start();
            k++;
        }
        search.setAlpha(0);
        search.setTranslationY((sbTop && sbs != 1 ? -90 : 90) * d);
        search.animate().alpha(1).translationY(0).setStartDelay(T(100)).setDuration(T(600)).setInterpolator(emph).start();
        glance.setAlpha(0);
        glance.setTranslationY(-16 * d);
        glance.animate().alpha(1).translationY(0).setDuration(T(600)).setInterpolator(emph).start();
        dots.setAlpha(0);
        dots.animate().alpha(1).setStartDelay(T(320)).setDuration(T(500)).start();
    }

    // =================================================================== drawer

    void buildDrawer() {
        drawer = new FrameLayout(this);
        drawer.setClickable(true);
        drawer.setVisibility(View.INVISIBLE);
        drawerCol = new LinearLayout(this);
        drawerCol.setOrientation(LinearLayout.VERTICAL);
        drawerCol.setPadding(0, (int) (top + dp(12)), 0, (int) (navH + dp(14)));
        int panelStyle = ui == 0 ? 6 : ui;

        // ---- kolom pencarian (atas)
        FrameLayout sb = new FrameLayout(this);
        sb.setBackground(us.panel(sb, dp(28), ui, ac));
        et = new EditText(this);
        et.setBackground(null);
        et.setHint(os == Prefs.OS_IOS ? "App Library" : "Cari aplikasi");
        et.setHintTextColor(Color.rgb(190, 194, 200));
        et.setTextColor(UiStyle.onPanel(ui, ac));
        et.setTextSize(16);
        et.setSingleLine(true);
        et.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        et.setPadding((int) dp(54), 0, (int) dp(16), 0);
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        et.setOnEditorActionListener((v, a, ev) -> {
            String q = et.getText().toString().trim();
            if (!shown.isEmpty()) launchItem(shown.get(0), null);
            else if (!q.isEmpty()) Util.webSearch(this, q);
            return true;
        });
        sb.addView(et, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams((int) dp(22), (int) dp(22), Gravity.CENTER_VERTICAL | Gravity.START);
        gl.leftMargin = (int) dp(20);
        sb.addView(new Glyph(this, Glyph.SEARCH, UiStyle.onPanel(ui, ac)), gl);
        LinearLayout.LayoutParams sbl = new LinearLayout.LayoutParams(-1, (int) dp(52));
        sbl.leftMargin = sbl.rightMargin = (int) dp(16);
        sbl.bottomMargin = (int) dp(10);
        drawerCol.addView(sb, sbl);

        // ---- cari di web
        webRow = new TextView(this);
        webRow.setTextColor(Color.WHITE);
        webRow.setTextSize(15);
        webRow.setGravity(Gravity.CENTER_VERTICAL);
        webRow.setPadding((int) dp(20), 0, (int) dp(20), 0);
        webRow.setBackground(us.panel(webRow, dp(22), panelStyle, ac));
        webRow.setVisibility(View.GONE);
        webRow.setOnClickListener(v -> Util.webSearch(this, et.getText().toString().trim()));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, (int) dp(46));
        wl.leftMargin = wl.rightMargin = (int) dp(16);
        wl.bottomMargin = (int) dp(8);
        drawerCol.addView(webRow, wl);

        // ---- saran
        recentBox = new LinearLayout(this);
        recentBox.setPadding((int) dp(8), (int) dp(6), (int) dp(8), (int) dp(6));
        recentBox.setBackground(us.panel(recentBox, dp(30), panelStyle, ac));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.leftMargin = rl.rightMargin = (int) dp(16);
        rl.bottomMargin = (int) dp(8);
        if (pr.gb("rr")) drawerCol.addView(recentBox, rl);

        // ---- tab A–Z / Kategori
        LinearLayout libTab = new LinearLayout(this);
        libTab.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, (int) dp(40));
        tl.leftMargin = tl.rightMargin = (int) dp(16);
        tl.bottomMargin = (int) dp(6);
        libAz = libTabView("A — Z");
        libCat = libTabView(os == Prefs.OS_IOS ? "App Library" : "Kategori");
        libTab.addView(libAz, new LinearLayout.LayoutParams(0, -1, 1));
        libTab.addView(libCat, new LinearLayout.LayoutParams(0, -1, 1));
        libAz.setOnClickListener(v -> setLibMode(false));
        libCat.setOnClickListener(v -> setLibMode(true));
        drawerCol.addView(libTab, tl);
        libMode = pr.gi("lib") == 1 || side;
        if (side) libTab.setVisibility(View.GONE);
        paintLibTab();

        libGrid = new GridView(this);
        libGrid.setNumColumns(2);
        libGrid.setVerticalScrollBarEnabled(false);
        libGrid.setSelector(new ColorDrawable(0));
        libGrid.setClipToPadding(false);
        libGrid.setHorizontalSpacing((int) dp(12));
        libGrid.setVerticalSpacing((int) dp(12));
        libGrid.setPadding((int) dp(16), (int) dp(4), (int) dp(16), (int) dp(12));
        libAd = new LibAdapter();
        libGrid.setAdapter(libAd);

        grid = new GridView(this);
        grid.setNumColumns(Math.max(3, Math.min(6, pr.gi("dc"))));
        grid.setVerticalScrollBarEnabled(false);
        grid.setSelector(new ColorDrawable(0));
        grid.setClipToPadding(false);
        grid.setPadding(0, (int) dp(4), 0, (int) dp(12));
        grid.setVisibility(View.GONE);
        ad = new BaseAdapter() {
            @Override
            public int getCount() {
                return shown.size();
            }

            @Override
            public Object getItem(int i) {
                return shown.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View cv, ViewGroup p) {
                AppCell c = cv instanceof AppCell ? (AppCell) cv : new AppCell(HomeActivity.this);
                c.l = HomeActivity.this;
                c.src = AppCell.SRC_DRAWER;
                c.labels = pr.gb("dl");
                c.bind(shown.get(i));
                c.setLayoutParams(new AbsListView.LayoutParams(-1, (int) dp(c.labels ? 94 : 76)));
                c.animate().cancel();
                c.setScaleX(1);
                c.setScaleY(1);
                if (seen.add(i)) {
                    c.setAlpha(0);
                    c.setTranslationY(dp(44));
                    c.animate().alpha(1).translationY(0).setStartDelay(T(Math.min(i, 24) * 20L)).setDuration(T(420))
                            .setInterpolator(new DecelerateInterpolator(2)).start();
                } else {
                    c.setAlpha(1);
                    c.setTranslationY(0);
                }
                return c;
            }
        };
        grid.setAdapter(ad);
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView v, int s) {
                if (s == SCROLL_STATE_TOUCH_SCROLL) hideIme();
            }

            @Override
            public void onScroll(AbsListView v, int a, int b, int c) {
            }
        });
        drawerCol.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        drawerCol.addView(libGrid, new LinearLayout.LayoutParams(-1, 0, 1));
        drawer.addView(drawerCol, new FrameLayout.LayoutParams(-1, -1));
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));
    }

    void setLibMode(boolean cat) {
        libMode = cat;
        pr.si("lib", cat ? 1 : 0);
        lastSig = pr.launcherSig();
        paintLibTab();
        filter(et.getText().toString());
    }

    class LibAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return libCats.size();
        }

        @Override
        public Object getItem(int i) {
            return libCats.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @SuppressWarnings("unchecked")
        @Override
        public View getView(int i, View cv, ViewGroup p) {
            FrameLayout cell = cv instanceof FrameLayout ? (FrameLayout) cv : null;
            int isz = (int) dp(46);
            if (cell == null) {
                cell = new FrameLayout(HomeActivity.this);
                FrameLayout pane = new FrameLayout(HomeActivity.this);
                pane.setBackground(us.panel(pane, dp(26), ui == 0 ? 6 : ui, ac));
                LinearLayout col = new LinearLayout(HomeActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER_HORIZONTAL);
                col.setPadding((int) dp(14), (int) dp(14), (int) dp(14), (int) dp(10));
                for (int r = 0; r < 2; r++) {
                    LinearLayout row = new LinearLayout(HomeActivity.this);
                    row.setGravity(Gravity.CENTER);
                    for (int c = 0; c < 2; c++) {
                        ImageView iv = new ImageView(HomeActivity.this);
                        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(isz, isz);
                        il.setMargins((int) dp(5), (int) dp(4), (int) dp(5), (int) dp(4));
                        row.addView(iv, il);
                    }
                    col.addView(row);
                }
                TextView lbl = new TextView(HomeActivity.this);
                lbl.setTextSize(13);
                lbl.setGravity(Gravity.CENTER);
                lbl.setTextColor(UiStyle.onPanel(ui == 0 ? 6 : ui, ac));
                lbl.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                lbl.setPadding(0, (int) dp(6), 0, 0);
                col.addView(lbl);
                pane.addView(col, new FrameLayout.LayoutParams(-1, -2));
                cell.addView(pane, new FrameLayout.LayoutParams(-1, -2));
                Util.press(cell);
            }
            LinearLayout col = (LinearLayout) ((FrameLayout) cell.getChildAt(0)).getChildAt(0);
            Object[] o = libCats.get(i);
            final String name = (String) o[0];
            final List<Item> kids = (List<Item>) o[1];
            ((TextView) col.getChildAt(2)).setText(name);
            for (int k = 0; k < 4; k++) {
                LinearLayout row = (LinearLayout) col.getChildAt(k / 2);
                ImageView iv = (ImageView) row.getChildAt(k % 2);
                if (k < kids.size()) {
                    final Item it = kids.get(k);
                    Bitmap ic = it.icon == null || it.icon.isRecycled() ? Icons.fallback(HomeActivity.this, isz) : it.icon;
                    iv.setImageBitmap(ic);
                    iv.setVisibility(View.VISIBLE);
                    // seperti iOS: 3 ikon pertama langsung membuka aplikasi, ikon ke-4 membuka kategori
                    if (k < 3 || kids.size() <= 4) iv.setOnClickListener(v -> launchItem(it, v));
                    else iv.setOnClickListener(v -> openLibCategory(name));
                } else {
                    iv.setImageDrawable(null);
                    iv.setOnClickListener(null);
                    iv.setVisibility(View.INVISIBLE);
                }
            }
            cell.setOnClickListener(v -> openLibCategory(name));
            return cell;
        }
    }

    TextView libTabView(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(14);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.WHITE);
        return v;
    }

    void paintLibTab() {
        boolean cat = libMode;
        GradientDrawable az = new GradientDrawable(), ct = new GradientDrawable();
        az.setCornerRadius(dp(20));
        ct.setCornerRadius(dp(20));
        az.setColor(cat ? 0 : 0x66FFFFFF);
        ct.setColor(cat ? 0x66FFFFFF : 0);
        libAz.setBackground(az);
        libCat.setBackground(ct);
        libAz.setTextColor(cat ? 0xFFB5B9C2 : Color.WHITE);
        libCat.setTextColor(cat ? Color.WHITE : 0xFFB5B9C2);
    }

    @SuppressWarnings("unchecked")
    void openLibCategory(String name) {
        libGrid.setVisibility(View.GONE);
        grid.setVisibility(View.VISIBLE);
        shown.clear();
        for (Object[] o : libCats) if (o[0].equals(name)) shown.addAll((List<Item>) o[1]);
        seen.clear();
        ad.notifyDataSetChanged();
        grid.setSelection(0);
    }

    void buildLibCats() {
        libCats.clear();
        LinkedHashMap<String, List<Item>> map = new LinkedHashMap<>();
        // urutan kategori tetap (seperti App Library)
        for (String k : new String[]{"Komunikasi", "Media & Sosial", "Foto & Video", "Game", "Internet",
                "Belanja & Keuangan", "Alat", "Lainnya"}) map.put(k, new ArrayList<>());
        for (Item a : sorted(visibleApps())) {
            List<Item> l = map.get(Item.category(a.label));
            if (l != null) l.add(a);
        }
        for (Map.Entry<String, List<Item>> en : map.entrySet())
            if (!en.getValue().isEmpty()) libCats.add(new Object[]{en.getKey(), en.getValue()});
        libAd.notifyDataSetChanged();
    }

    int uses(Item a) {
        return sp.getInt("use:" + a.key(), 0);
    }

    List<Item> sorted(List<Item> l) {
        List<Item> all = new ArrayList<>(l);
        if (pr.gi("dso") == 1) {
            Collections.sort(all, (a, b) -> {
                int c = Integer.compare(uses(b), uses(a));
                return c != 0 ? c : a.label.compareToIgnoreCase(b.label);
            });
        } else {
            Collections.sort(all, (a, b) -> a.label.compareToIgnoreCase(b.label));
        }
        return all;
    }

    void filter(String q) {
        String q2 = q.trim().toLowerCase(Locale.ROOT);
        shown.clear();
        boolean searching = !q2.isEmpty();
        if (searching) {
            List<Item> starts = new ArrayList<>(), contains = new ArrayList<>();
            for (Item a : visibleApps()) {
                String l = a.label.toLowerCase(Locale.ROOT);
                if (l.startsWith(q2)) starts.add(a);
                else if (l.contains(q2) || a.original.toLowerCase(Locale.ROOT).contains(q2)) contains.add(a);
            }
            shown.addAll(starts);
            shown.addAll(contains);
            libGrid.setVisibility(View.GONE);
            grid.setVisibility(View.VISIBLE);
            webRow.setText("Cari \"" + q.trim() + "\" di web");
            webRow.setVisibility(View.VISIBLE);
        } else {
            webRow.setVisibility(View.GONE);
            if (libMode) {
                buildLibCats();
                libGrid.setVisibility(View.VISIBLE);
                grid.setVisibility(View.GONE);
            } else {
                libGrid.setVisibility(View.GONE);
                grid.setVisibility(View.VISIBLE);
                shown.addAll(sorted(visibleApps()));
            }
        }
        recentBox.setVisibility(searching ? View.GONE : View.VISIBLE);
        seen.clear();
        ad.notifyDataSetChanged();
    }

    void refreshRecent() {
        recentBox.removeAllViews();
        Set<String> hid = pr.hidden();
        List<Item> rs = new ArrayList<>();
        for (String k : sp.getString("recent", "").split(",")) {
            Item a = k.isEmpty() || hid.contains(k) ? null : find(k);
            if (a != null && rs.size() < 4 && !rs.contains(a)) rs.add(a);
        }
        List<Item> vis = visibleApps();
        Collections.sort(vis, (a, b) -> Integer.compare(uses(b), uses(a)));
        for (Item a : vis) {
            if (rs.size() >= 4) break;
            if (!rs.contains(a)) rs.add(a);
        }
        for (Item a : rs) {
            AppCell c = new AppCell(this);
            c.l = this;
            c.src = AppCell.SRC_RECENT;
            c.bind(a);
            recentBox.addView(c, new LinearLayout.LayoutParams(0, (int) dp(92), 1));
        }
    }

    void addRecent(Item it) {
        StringBuilder b = new StringBuilder(it.key());
        int n = 1;
        for (String k : sp.getString("recent", "").split(",")) {
            if (k.isEmpty() || k.equals(it.key())) continue;
            if (n++ < 4) b.append(',').append(k);
        }
        sp.edit().putString("recent", b.toString()).putInt("use:" + it.key(), uses(it) + 1).apply();
    }

    void setDp(float v) {
        float old = dp;
        dp = cl(v, 0, 1);
        boolean blurOn = pr.gb("hb");
        if (old == 0 && dp > 0) {
            seen.clear();
            ad.notifyDataSetChanged();
            refreshRecent();
        }
        drawer.setVisibility(dp > 0 ? View.VISIBLE : View.INVISIBLE);
        if (side) {
            // iOS: App Library bergeser masuk dari kanan
            drawer.setTranslationX((1 - dp) * W);
            drawer.setTranslationY(0);
            drawer.setAlpha(1);
            home.setTranslationX(-dp * W * 0.35f);
            home.setAlpha(Math.max(0, 1 - dp * 1.4f));
            home.setScaleX(1);
            home.setScaleY(1);
            home.setTranslationY(0);
        } else {
            drawer.setTranslationY((1 - dp) * H * 0.6f);
            drawer.setAlpha(Math.min(1, dp * 2.5f));
            home.setAlpha(Math.max(0, 1 - dp * 1.8f));
            home.setScaleX(1 - dp * 0.07f);
            home.setScaleY(1 - dp * 0.07f);
            home.setTranslationY(-dp * 24 * d);
        }
        home.setVisibility(dp >= 0.99f ? View.INVISIBLE : View.VISIBLE);
        search.setTranslationY((sbTop && sbs != 1 ? -1 : 1) * dp * 60 * d);
        blurV.setAlpha(blurOn ? dp : 0);
        scrim.setAlpha(dp * (blurOn ? 0.45f : 0.65f));
        float s = wallBase + dp * 0.06f;
        wall.setScaleX(s);
        wall.setScaleY(s);
        blurV.setScaleX(s);
        blurV.setScaleY(s);
    }

    void animDp(final float to) {
        if (dpAnim != null) dpAnim.cancel();
        dpAnim = ValueAnimator.ofFloat(dp, to);
        dpAnim.setDuration(T((long) (240 + Math.abs(to - dp) * 260)));
        dpAnim.setInterpolator(emph);
        dpAnim.addUpdateListener(a -> setDp((Float) a.getAnimatedValue()));
        dpAnim.addListener(new AnimatorListenerAdapter() {
            boolean c;

            @Override
            public void onAnimationCancel(Animator a) {
                c = true;
            }

            @Override
            public void onAnimationEnd(Animator a) {
                if (c || to != 0) return;
                hideIme();
                et.setText("");
                et.clearFocus();
                if (libMode) filter("");
            }
        });
        dpAnim.start();
    }

    void hideIme() {
        InputMethodManager im = getSystemService(InputMethodManager.class);
        if (im != null) im.hideSoftInputFromWindow(et.getWindowToken(), 0);
    }

    // =================================================================== membuka aplikasi

    void launchItem(Item it, View v) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(it.pkg, it.cls)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        boolean ok = false;
        try {
            if (v != null && v.isAttachedToWindow()) {
                // Jendela aplikasi membesar tepat dari kotak ikon (bukan dari tengah layar).
                android.graphics.RectF ir = new android.graphics.RectF();
                if (v instanceof AppCell) ((AppCell) v).iconRect(ir);
                else ir.set(0, 0, v.getWidth(), v.getHeight());
                startActivity(i, ActivityOptions.makeScaleUpAnimation(v, (int) ir.left, (int) ir.top,
                        (int) ir.width(), (int) ir.height()).toBundle());
                Motion.launched(it, v instanceof AppCell ? ((AppCell) v).iconOnWindow() : winRect(v));
            } else {
                startActivity(i);
                Motion.launched(it, new android.graphics.RectF(W / 2f - dp(30), H / 2f - dp(30), W / 2f + dp(30), H / 2f + dp(30)));
            }
            ok = true;
        } catch (Exception e) {
            try {
                Intent li = getPackageManager().getLaunchIntentForPackage(it.pkg);
                if (li != null) {
                    startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    ok = true;
                }
            } catch (Exception ignored) {
            }
        }
        if (ok) addRecent(it);
        else Toast.makeText(this, "Aplikasi tidak dapat dibuka", Toast.LENGTH_SHORT).show();
    }

    android.graphics.RectF winRect(View v) {
        int[] l = new int[2];
        v.getLocationInWindow(l);
        return new android.graphics.RectF(l[0], l[1], l[0] + v.getWidth(), l[1] + v.getHeight());
    }

    // =================================================================== animasi kembali & buka kunci

    /** Cari sel ikon yang terlihat untuk aplikasi ini (halaman aktif / dock / folder di halaman). */
    AppCell visibleCell(String key) {
        List<AppCell> cand = new ArrayList<>(hotCells);
        int pg = Math.round(pagePos);
        if (pg >= 0 && pg < pager.getChildCount()) {
            ViewGroup g = (ViewGroup) pager.getChildAt(pg);
            for (int i = 0; i < g.getChildCount(); i++) if (g.getChildAt(i) instanceof AppCell) cand.add((AppCell) g.getChildAt(i));
        }
        for (AppCell c : cand) {
            if (c.item == null) continue;
            if (c.item.kids == null && c.item.key().equals(key)) return c;
            if (c.item.kids != null) for (Item k : c.item.kids) if (k.key().equals(key)) return c;
        }
        return null;
    }

    void playTransitions() {
        long now = System.currentTimeMillis();
        if (Motion.pendingUnlock) {
            Motion.pendingUnlock = false;
            Motion.lastAt = 0;
            playUnlock();
            return;
        }
        if (Motion.lastAt > 0 && now - Motion.lastAt < 6 * 60 * 60 * 1000L && Motion.lastKey != null) {
            Motion.lastAt = 0;
            playReturn();
        }
    }

    /** Aplikasi "mengecil" kembali ke ikonnya. */
    void playReturn() {
        android.graphics.RectF to;
        final AppCell target = dp < 0.5f ? visibleCell(Motion.lastKey) : null;
        if (target != null) to = target.iconOnWindow();
        else if (dp >= 0.99f) to = new android.graphics.RectF(Motion.lastRect);
        else to = new android.graphics.RectF(W / 2f - dp(28), H * 0.45f - dp(28), W / 2f + dp(28), H * 0.45f + dp(28));
        if (to.width() <= 0) return;
        float screenRad = dp(os == Prefs.OS_IOS ? 44 : 28);
        float iconRad = to.width() * (pr.gi("is") == 0 ? 0.5f : 0.28f);
        final Motion.Shrink s = new Motion.Shrink(this, new android.graphics.RectF(0, 0, W, H), to, Motion.lastIcon, screenRad, iconRad);
        fxLayer.addView(s, new FrameLayout.LayoutParams(-1, -1));
        if (target != null) target.setAlpha(0f);
        View content = dp >= 0.99f ? drawer : home;
        content.setScaleX(0.9f);
        content.setScaleY(0.9f);
        content.animate().scaleX(1).scaleY(1).setDuration(T(480)).setInterpolator(Motion.EMPH).start();
        wall.setScaleX(wallBase + 0.08f);
        wall.setScaleY(wallBase + 0.08f);
        wall.animate().scaleX(wallBase).scaleY(wallBase).setDuration(T(520)).setInterpolator(Motion.EMPH).start();
        s.run(T(460), () -> {
            fxLayer.removeView(s);
            if (target != null) {
                target.setAlpha(1f);
                target.setScaleX(0.86f);
                target.setScaleY(0.86f);
                target.animate().scaleX(1).scaleY(1).setDuration(T(320)).setInterpolator(new OvershootInterpolator(2.2f)).start();
            }
        });
    }

    /** Setelah membuka kunci: layar utama mendarat dengan zoom, ikon menyebar dari tengah. */
    void playUnlock() {
        if (dp > 0) setDp(0);
        wall.setScaleX(wallBase + 0.14f);
        wall.setScaleY(wallBase + 0.14f);
        wall.animate().scaleX(wallBase).scaleY(wallBase).setDuration(T(700)).setInterpolator(Motion.EMPH_DECEL).start();
        List<View> vs = new ArrayList<>();
        int pg = Math.round(pagePos);
        if (pg >= 0 && pg < pager.getChildCount()) {
            ViewGroup g = (ViewGroup) pager.getChildAt(pg);
            for (int i = 0; i < g.getChildCount(); i++) vs.add(g.getChildAt(i));
        }
        vs.addAll(hotCells);
        float cxs = W / 2f, cys = H / 2f;
        for (View v : vs) {
            int[] l = new int[2];
            v.getLocationInWindow(l);
            float vx = l[0] + v.getWidth() / 2f, vy = l[1] + v.getHeight() / 2f;
            float dist = (float) Math.hypot(vx - cxs, vy - cys) / (float) Math.hypot(cxs, cys);
            v.animate().cancel();
            v.setAlpha(0f);
            v.setScaleX(1.25f);
            v.setScaleY(1.25f);
            v.setTranslationX((vx - cxs) * 0.12f);
            v.setTranslationY((vy - cys) * 0.12f);
            v.animate().alpha(1).scaleX(1).scaleY(1).translationX(0).translationY(0).setStartDelay(T((long) (dist * 140)))
                    .setDuration(T(520)).setInterpolator(Motion.EMPH_DECEL).start();
        }
        for (View v : new View[]{search, glance, dots, dockV}) {
            if (v.getVisibility() != View.VISIBLE) continue;
            v.setAlpha(0f);
            v.setTranslationY(dp(20));
            v.animate().alpha(1).translationY(0).setStartDelay(T(120)).setDuration(T(500)).setInterpolator(Motion.EMPH_DECEL).start();
        }
    }

    @Override
    public void click(AppCell c) {
        if (c.item == null) return;
        if (c.item.kids != null) {
            openFolder(c);
            return;
        }
        if (c.src == AppCell.SRC_FOLDER) dismissOverlay();
        launchItem(c.item, c);
    }

    // =================================================================== menu tekan lama

    static final class MI {
        final String label;
        final int glyph;
        final Drawable icon;
        final Runnable act;

        MI(String l, int g, Runnable a) {
            label = l;
            glyph = g;
            icon = null;
            act = a;
        }

        MI(String l, Drawable ic, Runnable a) {
            label = l;
            glyph = -1;
            icon = ic;
            act = a;
        }
    }

    List<ShortcutInfo> shortcuts(String pkg) {
        List<ShortcutInfo> out = new ArrayList<>();
        try {
            if (!la.hasShortcutHostPermission()) return out;
            LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery();
            q.setPackage(pkg);
            q.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                    | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
            List<ShortcutInfo> l = la.getShortcuts(q, Process.myUserHandle());
            if (l != null) {
                Collections.sort(l, (a, b) -> Integer.compare(a.getRank(), b.getRank()));
                for (ShortcutInfo s : l) {
                    if (out.size() >= 4) break;
                    if (s.isEnabled() && (s.isDynamic() || s.isDeclaredInManifest())) out.add(s);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    @Override
    public void longPress(final AppCell c) {
        int[] l = new int[2];
        c.getLocationInWindow(l);
        final Item it = c.item;
        if (it == null) return;
        List<MI> mi = new ArrayList<>();
        if (it.kids != null) {
            mi.add(new MI("Ganti nama folder", Glyph.PENCIL, () -> renameFolder(it)));
            if (!locked) {
                mi.add(new MI("Bubarkan folder", Glyph.GRID, () -> {
                    List<Item> ks = new ArrayList<>(it.kids);
                    removeSlot(c.src, c.page, c.slot, false);
                    for (Item k : ks) placeFree(k);
                    save();
                    rebuild();
                }));
                mi.add(new MI("Hapus folder", Glyph.TRASH, () -> removeSlot(c.src, c.page, c.slot, true)));
            }
            showMenu(l[0], l[1], c.getWidth(), c.getHeight(), mi);
            return;
        }
        for (final ShortcutInfo s : shortcuts(it.pkg)) {
            Drawable ic = null;
            try {
                ic = la.getShortcutIconDrawable(s, getResources().getDisplayMetrics().densityDpi);
            } catch (Exception ignored) {
            }
            CharSequence lb = s.getShortLabel() != null ? s.getShortLabel() : s.getLongLabel();
            mi.add(new MI(lb == null ? s.getId() : lb.toString(), ic, () -> {
                try {
                    la.startShortcut(s.getPackage(), s.getId(), null, null, Process.myUserHandle());
                } catch (Exception e) {
                    Toast.makeText(this, "Pintasan tidak dapat dibuka", Toast.LENGTH_SHORT).show();
                }
            }));
        }
        boolean onHome = c.src == AppCell.SRC_PAGE || c.src == AppCell.SRC_DOCK;
        if (c.src == AppCell.SRC_FOLDER && !locked) {
            mi.add(new MI("Keluarkan dari folder", Glyph.GRID, () -> {
                Item f = curFolder;
                if (f == null) return;
                f.kids.remove(it);
                placeFree(it.copy());
                dissolveIfNeeded(f);
                save();
                rebuild();
            }));
        }
        if (!onHome && c.src != AppCell.SRC_FOLDER) {
            mi.add(new MI("Tambah ke layar utama", Glyph.PLUS, () -> {
                placeFree(it.copy());
                save();
                rebuild();
                Toast.makeText(this, "Ditambahkan ke layar utama", Toast.LENGTH_SHORT).show();
            }));
        }
        mi.add(new MI("Ubah nama", Glyph.PENCIL, () -> renameApp(it)));
        mi.add(new MI("Info aplikasi", Glyph.INFO, () -> info(it)));
        if (onHome && !locked) mi.add(new MI("Hapus dari layar utama", Glyph.TRASH, () -> removeSlot(c.src, c.page, c.slot, true)));
        if (c.src == AppCell.SRC_DRAWER || c.src == AppCell.SRC_RECENT)
            mi.add(new MI("Sembunyikan aplikasi", Glyph.EYE_OFF, () -> {
                pr.setHidden(it.key(), true);
                loadedSig = pr.iconSig();
                filter(et.getText().toString());
                refreshRecent();
                Toast.makeText(this, "Disembunyikan. Tampilkan lagi di Pengaturan Hyper › App Drawer", Toast.LENGTH_LONG).show();
            }));
        mi.add(new MI("Hapus pemasangan", Glyph.TRASH, () -> uninstall(it)));
        showMenu(l[0], l[1], c.getWidth(), c.getHeight(), mi, c.src == AppCell.SRC_FOLDER);
    }

    void info(Item it) {
        Util.start(this, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + it.pkg)));
    }

    void uninstall(Item it) {
        Util.start(this, new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + it.pkg)));
    }

    void renameApp(final Item it) {
        final EditText e = new EditText(this);
        e.setText(it.label);
        e.setSelectAllOnFocus(true);
        e.setSingleLine(true);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Ubah nama")
                .setMessage("Nama asli: " + it.original).setView(e)
                .setPositiveButton("Simpan", (dg, w) -> applyName(it, e.getText().toString()))
                .setNeutralButton("Kembalikan", (dg, w) -> applyName(it, ""))
                .setNegativeButton("Batal", null).show();
    }

    void applyName(Item it, String s) {
        String n = s.trim();
        pr.setName(it.key(), n.equals(it.original) ? "" : n);
        String label = n.isEmpty() ? it.original : n;
        for (Item a : ALL) if (a.key().equals(it.key())) a.label = label;
        for (Item[] pg : pages) for (Item a : pg) relabel(a, it.key(), label);
        for (Item a : hot) relabel(a, it.key(), label);
        loadedSig = pr.iconSig();
        rebuild();
        filter(et.getText().toString());
        refreshRecent();
    }

    private void relabel(Item a, String key, String label) {
        if (a == null) return;
        if (a.kids != null) for (Item k : a.kids) relabel(k, key, label);
        else if (a.key().equals(key)) a.label = label;
    }

    void renameFolder(final Item f) {
        final EditText e = new EditText(this);
        e.setText(f.label);
        e.setSelectAllOnFocus(true);
        e.setSingleLine(true);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Nama folder").setView(e)
                .setPositiveButton("OK", (dg, w) -> {
                    String s = e.getText().toString().trim().replaceAll("[()|,;#]", " ");
                    if (s.isEmpty()) return;
                    f.label = s;
                    save();
                    rebuild();
                    dismissOverlay();
                }).setNegativeButton("Batal", null).show();
    }

    void removeSlot(int src, int page, int slot, boolean doSave) {
        if (src == AppCell.SRC_PAGE && page < pages.size()) pages.get(page)[slot] = null;
        else if (src == AppCell.SRC_DOCK) hot[slot] = null;
        if (doSave) {
            save();
            rebuild();
        }
    }

    /** Ganti/hapus folder yang tinggal berisi 1 atau 0 aplikasi. */
    void dissolveIfNeeded(Item f) {
        if (f == null || f.kids == null || f.kids.size() > 1) return;
        Item rep = f.kids.isEmpty() ? null : f.kids.get(0);
        for (Item[] a : pages) for (int i = 0; i < a.length; i++) if (a[i] == f) a[i] = rep;
        for (int i = 0; i < 5; i++) if (hot[i] == f) hot[i] = rep;
    }

    void resetOverlay() {
        ovToken++;
        overlay.animate().cancel();
        overlay.removeAllViews();
        overlay.setAlpha(1);
    }

    /** Tutup menu saja; bila di belakangnya ada folder, folder tetap terbuka. */
    void closeMenu() {
        boolean other = false;
        for (int i = overlay.getChildCount() - 1; i >= 0; i--) {
            View v = overlay.getChildAt(i);
            if ("menu".equals(v.getTag())) overlay.removeViewAt(i);
            else other = true;
        }
        if (!other) curFolder = null;
    }

    void dismissOverlay() {
        if (overlay.getChildCount() == 0) return;
        final int tk = ++ovToken;
        overlay.animate().alpha(0).setDuration(140).withEndAction(() -> {
            if (tk == ovToken) {
                overlay.removeAllViews();
                overlay.setAlpha(1);
                curFolder = null;
            }

        });
    }

    void showMenu(float ax, float ay, float aw, float ah, List<MI> items) {
        showMenu(ax, ay, aw, ah, items, false);
    }

    void showMenu(float ax, float ay, float aw, float ah, List<MI> items, boolean keep) {
        if (keep) closeMenu();
        else {
            resetOverlay();
            curFolder = null;
        }
        View sc = new View(this);
        sc.setTag("menu");
        sc.setClickable(true);
        sc.setOnClickListener(v -> {
            if (keep) closeMenu();
            else dismissOverlay();
        });
        overlay.addView(sc, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setTag("menu");
        menu.setMinimumWidth((int) dp(230));
        menu.setPadding(0, (int) dp(8), 0, (int) dp(8));
        menu.setBackground(us.panel(menu, dp(os == Prefs.OS_IOS ? 16 : 24), ui == 0 && os == Prefs.OS_IOS ? 2 : ui, ac));
        menu.setElevation(dp(10));
        int fg = UiStyle.onPanel(ui, ac);
        for (int i = 0; i < items.size(); i++) {
            final MI m = items.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) dp(20), 0, (int) dp(28), 0);
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, new ColorDrawable(Color.WHITE)));
            if (m.icon != null) {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(m.icon);
                row.addView(iv, new LinearLayout.LayoutParams((int) dp(24), (int) dp(24)));
            } else {
                row.addView(new Glyph(this, m.glyph, fg), new LinearLayout.LayoutParams((int) dp(22), (int) dp(22)));
            }
            TextView t = new TextView(this);
            t.setText(m.label);
            t.setTextColor(fg);
            t.setTextSize(15);
            t.setSingleLine(true);
            t.setPadding((int) dp(16), 0, 0, 0);
            row.addView(t);
            row.setOnClickListener(v -> {
                dismissOverlay();
                m.act.run();
            });
            menu.addView(row, new LinearLayout.LayoutParams(-1, (int) dp(50)));
            if (m.icon != null && i + 1 < items.size() && items.get(i + 1).icon == null) {
                View div = new View(this);
                div.setBackgroundColor(0x33FFFFFF);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, Math.max(1, (int) dp(0.7f)));
                dl.setMargins((int) dp(16), (int) dp(4), (int) dp(16), (int) dp(4));
                menu.addView(div, dl);
            }
            row.setAlpha(0);
            row.animate().alpha(1).setStartDelay(T(60 + i * 30L)).setDuration(T(200)).start();
        }
        menu.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        float cw = Math.min(menu.getMeasuredWidth(), W - dp(24)), ch = menu.getMeasuredHeight();
        float x = cl(ax + aw / 2 - cw / 2, dp(12), W - cw - dp(12));
        float y = ay - ch - dp(8);
        boolean above = true;
        if (y < top + dp(8)) {
            y = ay + ah + dp(8);
            above = false;
        }
        y = cl(y, top + dp(8), H - ch - navH - dp(8));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) cw, -2);
        lp.leftMargin = (int) x;
        lp.topMargin = (int) y;
        overlay.addView(menu, lp);
        menu.setPivotX(ax + aw / 2 - x);
        menu.setPivotY(above ? ch : 0);
        menu.setScaleX(0.4f);
        menu.setScaleY(0.4f);
        menu.setAlpha(0);
        menu.animate().scaleX(1).scaleY(1).alpha(1).setDuration(T(260)).setInterpolator(new OvershootInterpolator(1.1f)).start();
    }

    void wallpaperMenu(float x, float y) {
        List<MI> mi = new ArrayList<>();
        mi.add(new MI("Tanya Tira (AI)", Glyph.STAR, () -> openTira(false)));
        mi.add(new MI("Widget", Glyph.GRID, () -> wd.showPicker()));
        mi.add(new MI("Wallpaper & gaya", Glyph.IMAGE, () -> openSettings("hw")));
        mi.add(new MI("Total UI (PixelOS / iOS / Custom)", Glyph.STAR, () -> openSettings("main")));
        mi.add(new MI("Pengaturan Hyper", Glyph.GEAR, () -> openSettings("main")));
        mi.add(new MI(locked ? "Buka kunci tata letak" : "Kunci tata letak", Glyph.LOCK, () -> {
            locked = !locked;
            pr.si("lck", locked ? 1 : 0);
            lastSig = pr.launcherSig();
            Toast.makeText(this, locked ? "Tata letak dikunci" : "Tata letak bisa diubah", Toast.LENGTH_SHORT).show();
        }));
        mi.add(new MI("Lihat lockscreen", Glyph.LOCK, () -> startActivity(new Intent(this, LockActivity.class))));
        showMenu(x - 10, y - 10, 20, 20, mi);
    }

    void openSettings(String page) {
        startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_PAGE, page));
    }

    // =================================================================== folder

    void openFolder(AppCell fc) {
        resetOverlay();
        final Item f = fc.item;
        curFolder = f;
        int n = f.kids.size();
        int fr = (n + 2) / 3;
        View sc = new View(this);
        sc.setBackgroundColor(Color.argb(110, 0, 0, 0));
        sc.setClickable(true);
        sc.setOnClickListener(v -> dismissOverlay());
        overlay.addView(sc, new FrameLayout.LayoutParams(-1, -1));
        float cw = W - dp(56);
        float rowH = dp(100);
        float maxBody = H - top - navH - dp(160);
        float body = Math.min(fr * rowH, Math.max(rowH, maxBody));
        float ch = dp(64) + body + dp(12);
        FrameLayout card = new FrameLayout(this);
        card.setBackground(us.panel(card, dp(32), ui == 0 ? 6 : ui, ac));
        card.setClickable(true);
        TextView title = new TextView(this);
        title.setText(f.label);
        title.setTextColor(UiStyle.onPanel(ui == 0 ? 6 : ui, ac));
        title.setTextSize(18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        title.setOnClickListener(v -> renameFolder(f));
        card.addView(title, new FrameLayout.LayoutParams(-1, (int) dp(60)));
        // Perbaikan: folder berisi banyak aplikasi dulu melebihi layar tanpa bisa digulir.
        ScrollView scv = new ScrollView(this);
        scv.setVerticalScrollBarEnabled(false);
        FrameLayout inner = new FrameLayout(this);
        float kw = (cw - dp(24)) / 3f;
        for (int i = 0; i < n; i++) {
            AppCell c = new AppCell(this);
            c.l = this;
            c.src = AppCell.SRC_FOLDER;
            c.bind(f.kids.get(i));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) kw, (int) rowH);
            lp.leftMargin = (int) (dp(12) + (i % 3) * kw);
            lp.topMargin = (int) ((i / 3) * rowH);
            inner.addView(c, lp);
            c.setAlpha(0);
            c.setScaleX(0.5f);
            c.setScaleY(0.5f);
            c.animate().alpha(1).scaleX(1).scaleY(1).setStartDelay(T(120 + i * 40L)).setDuration(T(380))
                    .setInterpolator(new OvershootInterpolator(1.6f)).start();
        }
        scv.addView(inner, new FrameLayout.LayoutParams(-1, (int) (fr * rowH)));
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-1, (int) body);
        sl.topMargin = (int) dp(60);
        card.addView(scv, sl);
        float x = (W - cw) / 2;
        float y = cl(pagerTop + pagerH / 2 - ch / 2, top + dp(20), H - ch - dp(40));
        FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams((int) cw, (int) ch);
        lp2.leftMargin = (int) x;
        lp2.topMargin = (int) y;
        overlay.addView(card, lp2);
        int[] l = new int[2];
        fc.getLocationInWindow(l);
        card.setPivotX(l[0] + fc.getWidth() / 2f - x);
        card.setPivotY(l[1] + fc.getHeight() / 2f - y);
        card.setScaleX(0.3f);
        card.setScaleY(0.3f);
        card.setAlpha(0);
        card.animate().scaleX(1).scaleY(1).alpha(1).setDuration(T(340)).setInterpolator(emph).start();
        sc.setAlpha(0);
        sc.animate().alpha(1).setDuration(T(260)).start();
    }

    // =================================================================== seret & lepas

    @Override
    public void drag(AppCell c) {
        if (c.item == null) return;
        if (locked) {
            Toast.makeText(this, "Tata letak terkunci (tekan lama area kosong untuk membuka)", Toast.LENGTH_SHORT).show();
            return;
        }
        dragFolder = c.src == AppCell.SRC_FOLDER ? curFolder : null;
        if (c.src == AppCell.SRC_FOLDER && dragFolder == null) return;
        Item it = c.item;
        Bitmap ic = it.icon;
        dragItem = it;
        dragSrc = c.src;
        dragPage = c.page;
        dragSlot = c.slot;
        dragging = true;
        dropped = false;
        if (c.src == AppCell.SRC_PAGE || c.src == AppCell.SRC_DOCK) c.setVisibility(View.INVISIBLE);
        View.DragShadowBuilder sb = ic == null || it.kids != null ? new View.DragShadowBuilder(c) : new Sh(c, ic, (int) dp(72));
        boolean ok;
        try {
            ok = c.startDragAndDrop(ClipData.newPlainText("hyper", it.key()), sb, it, 0);
        } catch (Exception e) {
            ok = false;
        }
        resetOverlay();
        curFolder = null;
        if (!ok) {
            dragging = false;
            dragItem = null;
            dragFolder = null;
            rebuild();
            return;
        }
        boolean fromHome = fromHome();
        zl.setText(fromHome ? "Hapus" : "Info aplikasi");
        dropBar.setVisibility(View.VISIBLE);
        dropBar.setTranslationY(-dropH);
        dropBar.animate().translationY(0).setDuration(T(240)).setInterpolator(emph).start();
        if (c.src == AppCell.SRC_DRAWER || c.src == AppCell.SRC_RECENT) animDp(0);
    }

    boolean fromHome() {
        return dragSrc == AppCell.SRC_PAGE || dragSrc == AppCell.SRC_DOCK || dragSrc == AppCell.SRC_FOLDER;
    }

    /** {jenis(0 halaman,1 dock,2 zona kiri,3 zona kanan), halaman, slot, x, y, w, h} */
    int[] targetAt(float x, float y) {
        if (y < dropH) return new int[]{x < W / 2f ? 2 : 3, 0, 0, 0, 0, 0, 0};
        if (y >= hotTop - dp(4) && y < hotTop + hotH) {
            int s = (int) cl((x - hotLeft) / hcw, 0, hn - 1);
            return new int[]{1, 0, s, (int) (hotLeft + s * hcw), (int) hotTop, (int) hcw, (int) hotH};
        }
        if (y >= pagerTop && y < pagerTop + pagerH) {
            int col = (int) cl(x / cellW, 0, cols - 1);
            int row = (int) cl((y - pagerTop) / cellH, 0, rows - 1);
            int pg = (int) cl(Math.round(pagePos), 0, pages.size() - 1);
            return new int[]{0, pg, row * cols + col, (int) (col * cellW), (int) (pagerTop + row * cellH), (int) cellW, (int) cellH};
        }
        return null;
    }

    void setZone(int z) {
        zl.setAlpha(z == 2 ? 1 : 0.7f);
        zr.setAlpha(z == 3 ? 1 : 0.7f);
        zl.setScaleX(z == 2 ? 1.12f : 1);
        zl.setScaleY(z == 2 ? 1.12f : 1);
        zr.setScaleX(z == 3 ? 1.12f : 1);
        zr.setScaleY(z == 3 ? 1.12f : 1);
    }

    boolean onDrag(View v, DragEvent e) {
        switch (e.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return dragging;
            case DragEvent.ACTION_DRAG_LOCATION:
                hover(e.getX(), e.getY());
                break;
            case DragEvent.ACTION_DROP:
                drop(e.getX(), e.getY());
                break;
            case DragEvent.ACTION_DRAG_ENDED:
                endDrag();
                break;
        }
        return true;
    }

    void hover(float x, float y) {
        int[] t = targetAt(x, y);
        if (t == null || t[0] >= 2) {
            hl.setVisibility(View.GONE);
            setZone(t == null ? 0 : t[0]);
        } else {
            setZone(0);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) hl.getLayoutParams();
            if (lp.width != t[5] || lp.height != t[6]) {
                lp.width = t[5];
                lp.height = t[6];
                hl.setLayoutParams(lp);
            }
            if (hl.getVisibility() != View.VISIBLE) {
                hl.setVisibility(View.VISIBLE);
                hl.setTranslationX(t[3]);
                hl.setTranslationY(t[4]);
            } else {
                hl.animate().translationX(t[3]).translationY(t[4]).setDuration(120).start();
            }
        }
        boolean left = x < dp(26), right = x > W - dp(26);
        boolean inPager = y >= pagerTop && y < pagerTop + pagerH;
        if ((left || right) && inPager) {
            if (edgeR == null) {
                final int dir = left ? -1 : 1;
                edgeR = () -> {
                    edgeR = null;
                    flip(dir);
                };
                root.postDelayed(edgeR, 550);
            }
        } else if (edgeR != null) {
            root.removeCallbacks(edgeR);
            edgeR = null;
        }
    }

    boolean emptyPage(Item[] a) {
        if (wd.hasOnPage(pages.indexOf(a))) return false;
        for (Item i : a) if (i != null && i != dragItem) return false;
        return true;
    }

    void flip(int dir) {
        int t = Math.round(pagePos) + dir;
        if (t < 0) return;
        if (t >= pages.size()) {
            if (emptyPage(pages.get(pages.size() - 1))) return;
            pages.add(blank());
            buildPages();
        }
        snapTo(t);
    }

    void drop(float x, float y) {
        int[] t = targetAt(x, y);
        if (t == null || dragItem == null) return;
        dropped = true;
        Item it = dragItem;
        boolean fromHome = fromHome();
        if (t[0] == 2) {
            if (fromHome) removeSource();
            else info(it);
        } else if (t[0] == 3) {
            uninstall(it);
        } else {
            int s = t[2];
            if ((dragSrc == AppCell.SRC_DOCK && t[0] == 1 && dragSlot == s)
                    || (dragSrc == AppCell.SRC_PAGE && t[0] == 0 && dragPage == t[1] && dragSlot == s)) {
                dragging = false;
                rebuild();
                return;
            }
            Item nu = fromHome ? it : it.copy();
            if (fromHome) removeSource();
            Item[] arr = t[0] == 1 ? hot : pages.get(t[1]);
            Item o = arr[s];
            if (t[0] == 0 && wd.occupied(t[1], s)) {
                placeFree(nu); // sel itu ditempati widget
            } else if (o == null) {
                arr[s] = nu;
            } else if (o.kids != null && nu.kids == null) {
                o.kids.add(nu);
            } else if (o.kids == null && nu.kids == null) {
                Item f = new Item();
                f.label = Item.category(o.label).equals(Item.category(nu.label)) ? Item.category(o.label) : "Folder";
                f.kids = new ArrayList<>(Arrays.asList(o, nu));
                arr[s] = f;
            } else {
                placeFree(nu);
            }
        }
        save();
        dragging = false;
        rebuild();
        if (t[0] <= 1) {
            View c = t[0] == 1 ? cellOf(hotCells, t[2]) : pageCell(t[1], t[2]);
            if (c != null) {
                c.setScaleX(1.35f);
                c.setScaleY(1.35f);
                c.animate().scaleX(1).scaleY(1).setDuration(T(420)).setInterpolator(new OvershootInterpolator(2.4f)).start();
            }
        }
    }

    void removeSource() {
        if (dragSrc == AppCell.SRC_PAGE && dragPage < pages.size()) {
            if (pages.get(dragPage)[dragSlot] == dragItem) pages.get(dragPage)[dragSlot] = null;
        } else if (dragSrc == AppCell.SRC_DOCK) {
            if (hot[dragSlot] == dragItem) hot[dragSlot] = null;
        } else if (dragSrc == AppCell.SRC_FOLDER && dragFolder != null) {
            dragFolder.kids.remove(dragItem);
            dissolveIfNeeded(dragFolder);
        }
    }

    void placeFree(Item nu) {
        for (int p = 0; p < pages.size(); p++) {
            Item[] a = pages.get(p);
            for (int i = 0; i < a.length; i++) {
                if (a[i] == null && !wd.occupied(p, i)) {
                    a[i] = nu;
                    return;
                }
            }
        }
        Item[] a2 = blank();
        a2[0] = nu;
        pages.add(a2);
    }

    View cellOf(List<AppCell> l, int slot) {
        for (AppCell c : l) if (c.slot == slot) return c;
        return null;
    }

    View pageCell(int pg, int slot) {
        if (pg >= pager.getChildCount()) return null;
        ViewGroup g = (ViewGroup) pager.getChildAt(pg);
        for (int i = 0; i < g.getChildCount(); i++)
            if (g.getChildAt(i) instanceof AppCell && ((AppCell) g.getChildAt(i)).slot == slot) return g.getChildAt(i);
        return null;
    }

    void endDrag() {
        if (edgeR != null) {
            root.removeCallbacks(edgeR);
            edgeR = null;
        }
        hl.setVisibility(View.GONE);
        setZone(0);
        dropBar.animate().translationY(-dropH).setDuration(200).withEndAction(() -> {
            if (!dragging) dropBar.setVisibility(View.GONE);
        }).start();
        dragging = false;
        while (pages.size() > 1 && emptyPage(pages.get(pages.size() - 1)) && pagePos <= pages.size() - 2 + 0.01f) {
            pages.remove(pages.size() - 1);
        }
        save();
        rebuild();
        dragItem = null;
        dragFolder = null;
    }

    boolean overlayOpen() {
        return overlay.getChildCount() > 0 || dragging;
    }

    AppCell cellAt(float x, float y) {
        if (y >= hotTop) {
            for (AppCell c : hotCells)
                if (x >= c.getLeft() && x < c.getRight() && y >= c.getTop() && y < c.getBottom()) return c;
            return null;
        }
        int pg = Math.round(pagePos);
        if (pg < 0 || pg >= pager.getChildCount()) return null;
        ViewGroup g = (ViewGroup) pager.getChildAt(pg);
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (!(c instanceof AppCell)) continue;
            if (x >= c.getLeft() && x < c.getRight() && y - pagerTop >= c.getTop() && y - pagerTop < c.getBottom())
                return (AppCell) c;
        }
        return null;
    }

    // =================================================================== gestur

    void expandShade() {
        try {
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Exception ignored) {
        }
    }

    void swipeDown() {
        int a = pr.gi("sg");
        if (a == 0) expandShade();
        else if (a == 1) openSearch();
        else if (a == 3) openTira(false);
    }

    void doubleTap() {
        int a = pr.gi("dt");
        if (a == 1) startActivity(new Intent(this, LockActivity.class));
        else if (a == 2) animDp(1);
        else if (a == 3) ScreenOff.lock(this);
        else if (a == 4) openTira(true);
    }

    class HomeRoot extends FrameLayout {
        static final int M_NONE = 0, M_PAGE = 1, M_OPEN = 2, M_CLOSE = 3, M_DOWN = 4, M_DRAWER = 5, M_IGNORE = 6, M_MENU = 9;
        long lastTap;
        Runnable lpR;
        int mode;
        float sdp, sx, x0, y0;
        boolean shade, hz;
        final int slop;
        VelocityTracker vt;

        HomeRoot() {
            super(HomeActivity.this);
            slop = ViewConfiguration.get(HomeActivity.this).getScaledTouchSlop();
        }

        void init(MotionEvent e) {
            mode = M_NONE;
            shade = false;
            hz = false;
            x0 = e.getX();
            y0 = e.getY();
            sx = pagePos;
            sdp = dp;
            if (posAnim != null) posAnim.cancel();
            if (dpAnim != null && dpAnim.isRunning()) {
                dpAnim.cancel();
                mode = M_DRAWER; // pegang drawer yang sedang beranimasi
                hz = side;
            }
            if (vt != null) vt.recycle();
            vt = VelocityTracker.obtain();
            vt.addMovement(e);
            cancelLp();
            if (dp <= 0.01f && y0 > pagerTop && y0 < hotTop && cellAt(x0, y0) == null && wd.at(x0, y0) == null) {
                final float lx = x0, ly = y0;
                lpR = () -> {
                    mode = M_MENU;
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    wallpaperMenu(lx, ly);
                };
                postDelayed(lpR, 450);
            }
        }

        void cancelLp() {
            if (lpR != null) {
                removeCallbacks(lpR);
                lpR = null;
            }
        }

        /** Tentukan gestur setelah jari bergerak melewati slop. */
        void detect(MotionEvent e) {
            float dx = e.getX() - x0, dy = e.getY() - y0;
            if (Math.hypot(dx, dy) < slop) return;
            cancelLp();
            boolean vert = Math.abs(dy) > Math.abs(dx);
            hz = false;
            if (dp <= 0.01f && vert && wd.at(x0, y0) != null) {
                mode = M_IGNORE; // biarkan widget menggulir isinya sendiri
            } else if (dp <= 0.01f) {
                if (vert && dy < 0) mode = side ? M_IGNORE : M_OPEN;
                else if (vert) mode = M_DOWN;
                else if (y0 > pagerTop - dp(10) && y0 < hotTop + hotH) mode = M_PAGE;
            } else if (dp < 0.98f) {
                mode = M_DRAWER;
                hz = side;
            } else if (side && !vert && dx > 0) {
                mode = M_CLOSE; // App Library: geser ke kanan untuk kembali
                hz = true;
            } else if (!side && vert && dy > 0) {
                GridView g = libMode && libGrid.getVisibility() == View.VISIBLE ? libGrid : grid;
                if (!g.canScrollVertically(-1)) mode = M_CLOSE;
            }
            if (mode == M_OPEN || mode == M_CLOSE || mode == M_DRAWER) {
                sdp = dp;
                x0 = e.getX();
                y0 = e.getY();
                hideIme();
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (overlayOpen()) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    init(e);
                    return mode == M_DRAWER;
                case MotionEvent.ACTION_MOVE:
                    if (vt != null) {
                        vt.addMovement(e);
                        if (mode == M_NONE) detect(e);
                        return mode != M_NONE && mode != M_MENU && mode != M_IGNORE;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cancelLp();
                    break;
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (overlayOpen()) return true;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    init(e);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (vt == null) break;
                    vt.addMovement(e);
                    if (mode == M_NONE) detect(e);
                    float dx = e.getX() - x0, dy = e.getY() - y0;
                    if (mode == M_PAGE) {
                        float raw = sx - dx / W, last = pages.size() - 1;
                        if (side && raw > last) {
                            setPos(last);
                            setDp(raw - last);
                        } else {
                            if (side && dp > 0) setDp(0);
                            setPos(cl(raw, -0.22f, last + 0.22f));
                        }
                    } else if (mode == M_OPEN || mode == M_CLOSE || mode == M_DRAWER) {
                        setDp(hz ? sdp - dx / W : sdp - dy / (H * 0.6f));
                    } else if (mode == M_DOWN && !shade && dy > dp(50)) {
                        shade = true;
                        swipeDown();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cancelLp();
                    if (vt == null) break;
                    vt.addMovement(e);
                    vt.computeCurrentVelocity(1000);
                    float vx = vt.getXVelocity(), vy = vt.getYVelocity();
                    if (mode == M_PAGE && side && dp > 0) {
                        animDp(vx < -700 || (vx <= 700 && dp > 0.3f) ? 1 : 0);
                    } else if (mode == M_PAGE) {
                        int t = Math.round(pagePos);
                        if (Math.abs(vx) > 900) t = vx < 0 ? (int) Math.floor(pagePos) + 1 : (int) Math.ceil(pagePos) - 1;
                        snapTo((int) cl(t, 0, pages.size() - 1));
                    } else if ((mode == M_CLOSE || mode == M_DRAWER) && hz) {
                        animDp(vx > 700 || (vx >= -700 && dp < 0.7f) ? 0 : 1);
                    } else if (mode == M_OPEN || mode == M_CLOSE || mode == M_DRAWER) {
                        animDp(vy < -900 || (vy <= 900 && dp > 0.45f) ? 1 : 0);
                    } else if (mode == M_NONE && dp > 0 && dp < 1) {
                        animDp(dp > 0.5f ? 1 : 0);
                    } else if (mode == M_NONE && e.getActionMasked() == MotionEvent.ACTION_UP
                            && Math.hypot(e.getX() - x0, e.getY() - y0) < slop && dp <= 0.01f) {
                        long now = System.currentTimeMillis();
                        if (now - lastTap < 300) {
                            doubleTap();
                            lastTap = 0;
                        } else {
                            lastTap = now;
                        }
                    }
                    mode = M_NONE;
                    vt.recycle();
                    vt = null;
                    break;
            }
            return true;
        }
    }
}
