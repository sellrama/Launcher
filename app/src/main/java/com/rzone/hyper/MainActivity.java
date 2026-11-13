package com.rzone.hyper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

/** Pengaturan Hyper. Tampilannya mengikuti Total UI (PixelOS / iOS / Custom OS). */
public class MainActivity extends Activity {
    static final String EXTRA_PAGE = "page";
    private static final int RQ_WP = 7, RQ_FG = 8, RQ_HOME = 9, RQ_ROLE = 10;

    // palet (diisi ulang tiap halaman sesuai Total UI)
    private int BG, SURF, SURF2, TEXT, MUTED, ACC, ON_ACC, DIV;
    private float RAD;
    private boolean ios;

    private float d;
    private Prefs pr;
    private ScrollView sv;
    private LinearLayout col;
    private LinearLayout group; // grup baris (gaya iOS)
    private String page = "main";
    private DepthView pv;
    private View stylePv;
    private LinearLayout iconRow;
    private TextView statusTv, depthSt;
    private final Handler h = new Handler(Looper.getMainLooper());

    // =================================================================== palet

    private void palette() {
        int os = pr.os();
        ios = os == Prefs.OS_IOS;
        TEXT = Color.WHITE;
        DIV = 0x26FFFFFF;
        if (os == Prefs.OS_PIXEL) {
            BG = 0xFF111318;
            SURF = 0xFF1D2026;
            SURF2 = 0xFF2B2F37;
            MUTED = 0xFFB4B9C4;
            ACC = 0xFFA8C7FA;
            ON_ACC = 0xFF062E6F;
            RAD = 24;
        } else if (ios) {
            BG = Color.BLACK;
            SURF = 0xFF1C1C1E;
            SURF2 = 0xFF2C2C2E;
            MUTED = 0xFF8E8E93;
            ACC = 0xFF0A84FF;
            ON_ACC = Color.WHITE;
            RAD = 12;
        } else {
            BG = 0xFF0D0E13;
            SURF = 0xFF1A1B23;
            SURF2 = 0xFF2A2C37;
            MUTED = 0xFFA9ADBA;
            ACC = pr.gi("ac");
            if (UiStyle.lum(ACC) < 0.25f) ACC = UiStyle.mix(Color.WHITE, ACC, 0.45f);
            ON_ACC = UiStyle.lum(ACC) > 0.6f ? 0xFF0B0F1A : Color.WHITE;
            RAD = 20;
        }
    }

    // =================================================================== bangun UI dasar

    private TextView tv(String t, float sp, boolean bold, int color) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return v;
    }

    private GradientDrawable round(int color, float radDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(d * radDp);
        return g;
    }

    private LinearLayout card() {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(round(SURF, RAD));
        g.setPadding((int) (d * 18), (int) (d * 6), (int) (d * 18), (int) (d * 16));
        return g;
    }

    private void add(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = (int) (d * 10);
        col.addView(v, lp);
    }

    private TextView lab(String t) {
        TextView v = tv(t, 14, true, TEXT);
        v.setPadding(0, (int) (d * 14), 0, (int) (d * 6));
        return v;
    }

    private void section(String t) {
        group = null;
        TextView v = tv(ios ? t.toUpperCase() : t, ios ? 12 : 13, !ios, ios ? MUTED : ACC);
        if (!ios) v.setLetterSpacing(0.04f);
        v.setPadding((int) (d * (ios ? 16 : 6)), (int) (d * 22), 0, (int) (d * 2));
        col.addView(v);
    }

    /** Baris navigasi. iOS: tergabung dalam satu kartu dengan garis pemisah; Pixel/Custom: kartu terpisah. */
    private void row(String title, String sub, int glyph, final Runnable f) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int pv = (int) (d * (ios ? 11 : 16));
        r.setPadding((int) (d * 16), pv, (int) (d * 16), pv);
        if (glyph >= 0) {
            FrameLayout ic = new FrameLayout(this);
            ic.setBackground(round(ios ? glyphColor(glyph) : SURF2, ios ? 8 : 20));
            ic.addView(new Glyph(this, glyph, ios ? Color.WHITE : ACC), new FrameLayout.LayoutParams((int) (d * 20), (int) (d * 20), Gravity.CENTER));
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams((int) (d * (ios ? 30 : 40)), (int) (d * (ios ? 30 : 40)));
            il.rightMargin = (int) (d * 14);
            r.addView(ic, il);
        }
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(tv(title, ios ? 16 : 16, !ios, TEXT));
        if (sub != null) {
            TextView s = tv(sub, 12.5f, false, MUTED);
            s.setPadding(0, (int) (d * 2), 0, 0);
            tx.addView(s);
        }
        r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
        if (ios) r.addView(tv("›", 22, false, 0xFF5A5A5F));
        r.setOnClickListener(v -> f.run());
        r.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), null,
                round(Color.WHITE, ios ? 12 : RAD)));
        if (ios) {
            if (group == null) {
                group = new LinearLayout(this);
                group.setOrientation(LinearLayout.VERTICAL);
                group.setBackground(round(SURF, RAD));
                group.setClipToOutline(true);
                group.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, -2);
                gl.topMargin = (int) (d * 6);
                col.addView(group, gl);
            } else {
                View div = new View(this);
                div.setBackgroundColor(DIV);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, Math.max(1, (int) (d * 0.5f)));
                dl.leftMargin = (int) (d * (glyph >= 0 ? 60 : 16));
                group.addView(div, dl);
            }
            group.addView(r, new LinearLayout.LayoutParams(-1, -2));
        } else {
            r.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), round(SURF, RAD),
                    round(Color.WHITE, RAD)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = (int) (d * 6);
            col.addView(r, lp);
            Util.press(r);
        }
    }

    private int glyphColor(int g) {
        switch (g) {
            case Glyph.LOCK:
                return 0xFF8E8E93;
            case Glyph.IMAGE:
                return 0xFF0A84FF;
            case Glyph.STAR:
                return 0xFFFF9F0A;
            case Glyph.GRID:
                return 0xFF5E5CE6;
            case Glyph.SEARCH:
                return 0xFF30B0C7;
            case Glyph.GEAR:
                return 0xFF636366;
            case Glyph.EYE_OFF:
                return 0xFFFF375F;
            case Glyph.FLASH:
                return 0xFFFFD60A;
            case Glyph.TRASH:
                return 0xFFFF453A;
            default:
                return 0xFF32D74B;
        }
    }

    // =================================================================== kontrol pengaturan

    /** Simpan satu pengaturan. Mengubah pengaturan tema saat memakai preset → otomatis jadi Custom OS. */
    private void set(String key, int v) {
        boolean sw = pr.touch(key);
        pr.si(key, v);
        if (sw) Toast.makeText(this, "Beralih ke Custom OS (perubahanmu tersimpan)", Toast.LENGTH_SHORT).show();
    }

    private void fx(boolean reload) {
        if (pv != null) {
            if (reload) pv.reload();
            else pv.cfg();
        }
        if (stylePv != null) stylePv.invalidate();
    }

    private void paint(TextView[] t, int s) {
        for (int i = 0; i < t.length; i++) {
            t[i].setBackground(round(i == s ? ACC : SURF2, ios ? 9 : 40));
            t[i].setTextColor(i == s ? ON_ACC : TEXT);
        }
    }

    private View chipRow(String[] names, int sel, final IntConsumer on) {
        LinearLayout row = new LinearLayout(this);
        final TextView[] t = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView v = tv(names[i], 13, false, TEXT);
            v.setPadding((int) (d * 16), (int) (d * 9), (int) (d * 16), (int) (d * 9));
            t[i] = v;
            v.setOnClickListener(x -> {
                paint(t, idx);
                on.accept(idx);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = (int) (d * 8);
            row.addView(v, lp);
        }
        paint(t, sel);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        return hs;
    }

    private void chips(LinearLayout cd, String label, final String key, final boolean reload, final Runnable after, String... names) {
        if (label != null) cd.addView(lab(label));
        int sel = Math.max(0, Math.min(pr.gi(key), names.length - 1));
        cd.addView(chipRow(names, sel, i -> {
            set(key, i);
            fx(reload);
            if (after != null) after.run();
        }));
    }

    private void chipsK(LinearLayout cd, String label, String key, String... names) {
        chips(cd, label, key, false, null, names);
    }

    /** Toggle dengan Switch (dulu berupa dua chip "Aktif / Mati"). */
    private void togK(LinearLayout cd, String label, final String key) {
        togK(cd, label, null, key, null);
    }

    private void togK(LinearLayout cd, String label, String sub, final String key, final Runnable after) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, (int) (d * 10), 0, (int) (d * 2));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(tv(label, 14.5f, false, TEXT));
        if (sub != null) tx.addView(tv(sub, 12, false, MUTED));
        r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
        Switch s = new Switch(this);
        s.setChecked(pr.gb(key));
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{ios ? Color.WHITE : ON_ACC, 0xFFB8BCC6}));
        s.setTrackTintList(new ColorStateList(st, new int[]{ios ? 0xFF30D158 : ACC, SURF2}));
        s.setOnCheckedChangeListener((b, c) -> {
            set(key, c ? 1 : 0);
            fx(false);
            if (after != null) after.run();
        });
        r.addView(s);
        r.setOnClickListener(v -> s.toggle());
        cd.addView(r);
    }

    private void sliderK(LinearLayout cd, final String label, final String key, final int min, int max, final String unit) {
        LinearLayout top = new LinearLayout(this);
        top.setPadding(0, (int) (d * 12), 0, 0);
        top.addView(tv(label, 14, false, TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        final TextView val = tv(pr.gi(key) + unit, 13, true, ACC);
        top.addView(val);
        cd.addView(top);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(max - min, pr.gi(key) - min)));
        sb.setProgressTintList(ColorStateList.valueOf(ACC));
        sb.setThumbTintList(ColorStateList.valueOf(ACC));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(SURF2));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                if (!u) return;
                set(key, min + p);
                val.setText((min + p) + unit);
                fx(false);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        cd.addView(sb);
    }

    private void colorK(LinearLayout cd, String label, final String key, final Runnable after, final int... cols) {
        cd.addView(lab(label));
        LinearLayout r = new LinearLayout(this);
        final View[] vs = new View[cols.length];
        for (int i = 0; i < cols.length; i++) {
            final int cv = cols[i];
            View s = new View(this);
            vs[i] = s;
            s.setOnClickListener(x -> {
                set(key, cv);
                ring(vs, cols, cv);
                fx(false);
                if (after != null) after.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (d * 36), (int) (d * 36));
            lp.rightMargin = (int) (d * 10);
            r.addView(s, lp);
        }
        ring(vs, cols, pr.gi(key));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(r);
        cd.addView(hs);
    }

    private void ring(View[] vs, int[] cols, int sel) {
        for (int i = 0; i < vs.length; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(cols[i] == 0 ? 0xFF444444 : cols[i]);
            g.setStroke((int) ((cols[i] == sel ? 3 : 1) * d), cols[i] == sel ? Color.WHITE : 0x55FFFFFF);
            vs[i].setBackground(g);
        }
    }

    private void note(String t) {
        LinearLayout n = card();
        n.setPadding((int) (d * 18), (int) (d * 14), (int) (d * 18), (int) (d * 14));
        n.addView(tv(t, 12.5f, false, MUTED));
        add(n);
    }

    // =================================================================== thumbnail wallpaper (async)

    private View thumbs(final boolean depth, int sel, final IntConsumer on) {
        final int n = depth ? Scene.DEPTH_NAMES.length : Scene.WALL_NAMES.length;
        LinearLayout ll = new LinearLayout(this);
        final FrameLayout[] fr = new FrameLayout[n];
        final ImageView[] ivs = new ImageView[n];
        for (int i = 0; i < n; i++) {
            final int idx = i;
            FrameLayout f = new FrameLayout(this);
            int p = (int) (d * 3);
            f.setPadding(p, p, p, p);
            fr[i] = f;
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackground(round(SURF2, 14));
            iv.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            iv.setClipToOutline(true);
            ivs[i] = iv;
            f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            TextView nm = tv(depth ? Scene.DEPTH_NAMES[i] : Scene.WALL_NAMES[i], 9.5f, true, Color.WHITE);
            nm.setShadowLayer(d * 3, 0, d, 0xAA000000);
            nm.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            nl.bottomMargin = (int) (d * 6);
            f.addView(nm, nl);
            f.setOnClickListener(x -> {
                on.accept(idx);
                ringT(fr, idx);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (d * 70), (int) (d * 140));
            lp.rightMargin = (int) (d * 8);
            ll.addView(f, lp);
        }
        ringT(fr, sel);
        final int w = (int) (d * 64), hh = (int) (d * 128);
        // Perbaikan: thumbnail dulu dibuat di UI thread → halaman terasa macet saat dibuka.
        new Thread(() -> {
            for (int i = 0; i < n; i++) {
                final int k = i;
                Bitmap b;
                try {
                    b = depth ? Scene.depthThumb(i, w, hh) : Scene.wall(i, w, hh);
                } catch (Throwable t) {
                    b = null;
                }
                final Bitmap fb = b;
                if (fb != null) h.post(() -> ivs[k].setImageBitmap(fb));
            }
        }, "hyper-thumbs").start();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(ll);
        return hs;
    }

    private void ringT(FrameLayout[] fr, int sel) {
        for (int i = 0; i < fr.length; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(d * 17);
            g.setStroke((int) (d * 2.5f), i == sel ? ACC : 0);
            fr[i].setBackground(g);
        }
    }

    private void preview() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(round(Color.BLACK, ios ? 36 : 28));
        frame.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        frame.setClipToOutline(true);
        pv = new DepthView(this, true);
        frame.addView(pv, new FrameLayout.LayoutParams(-1, -1));
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);
        float ratio = (float) m.heightPixels / Math.max(1, m.widthPixels);
        int pw = (int) (d * 190);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(pw, (int) (pw * ratio));
        fl.gravity = Gravity.CENTER_HORIZONTAL;
        fl.topMargin = (int) (d * 12);
        col.addView(frame, fl);
        pv.post(() -> {
            try {
                pv.reload();
            } catch (Throwable ignored) {
            }
        });
    }

    // =================================================================== siklus hidup

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        pr = new Prefs(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(col);
        sv.setOnApplyWindowInsetsListener((v, in) -> {
            @SuppressWarnings("deprecation") int t = in.getSystemWindowInsetTop(), bt = in.getSystemWindowInsetBottom();
            col.setPadding((int) (d * 16), t + (int) (d * 20), (int) (d * 16), bt + (int) (d * 40));
            return in;
        });
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(sv);
        String p = getIntent() != null ? getIntent().getStringExtra(EXTRA_PAGE) : null;
        show(p == null ? "main" : p);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        String p = i != null ? i.getStringExtra(EXTRA_PAGE) : null;
        if (p != null) show(p);
    }

    @Override
    public void onBackPressed() {
        if (page.equals("main")) super.onBackPressed();
        else show(page.equals("hid") ? "drw" : "main");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statusTv != null) refresh();
        if (page.equals("akt")) show("akt");
    }

    @Override
    protected void onDestroy() {
        if (pv != null) pv.release();
        super.onDestroy();
    }

    private void show(String p) {
        page = p;
        if (pv != null) pv.release();
        pv = null;
        stylePv = null;
        iconRow = null;
        statusTv = null;
        depthSt = null;
        group = null;
        palette();
        sv.setBackgroundColor(BG);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        col.removeAllViews();
        if (p.equals("main")) {
            col.addView(tv(ios ? "Pengaturan" : "Hyper", ios ? 34 : 32, true, TEXT));
            col.addView(tv("RZONE  ·  Lockscreen & Launcher  ·  v" + version(), 13, false, MUTED));
            TextView pb = tv("Powered by RZONEDEV  ·  TikTok @maramadhona", 11.5f, false, ACC);
            pb.setPadding(0, (int) (d * 6), 0, 0);
            pb.setOnClickListener(v -> Util.start(this, new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/@maramadhona"))));
            col.addView(pb);
        } else {
            TextView hd = tv("‹  " + title(p), ios ? 22 : 20, true, ios ? ACC : TEXT);
            hd.setPadding(0, (int) (d * 4), 0, (int) (d * 6));
            hd.setOnClickListener(v -> onBackPressed());
            col.addView(hd);
        }
        switch (p) {
            case "akt":
                pgAkt();
                break;
            case "lw":
                pgLockWp();
                break;
            case "clk":
                pgClock();
                break;
            case "lms":
                pgLockMisc();
                break;
            case "ui":
                pgUi();
                break;
            case "hw":
                pgHomeWp();
                break;
            case "home":
                pgHome();
                break;
            case "dock":
                pgDock();
                break;
            case "drw":
                pgDrawer();
                break;
            case "hid":
                pgHidden();
                break;
            case "ico":
                pgIcons();
                break;
            case "anim":
                pgAnim();
                break;
            case "cos":
                pgCustomOs();
                break;
            case "abt":
                pgAbout();
                break;
            default:
                page = "main";
                pgMain();
        }
        sv.scrollTo(0, 0);
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "6";
        }
    }

    private String title(String p) {
        switch (p) {
            case "akt":
                return "Aktivasi & Izin";
            case "lw":
                return "Wallpaper Lockscreen (Depth)";
            case "clk":
                return "Jam Lockscreen";
            case "lms":
                return "Lockscreen Lainnya";
            case "ui":
                return "Gaya UI";
            case "hw":
                return "Wallpaper Launcher";
            case "home":
                return "Layar Utama";
            case "dock":
                return "Dock & Pencarian";
            case "drw":
                return "App Drawer";
            case "hid":
                return "Aplikasi Tersembunyi";
            case "ico":
                return "Ikon Aplikasi";
            case "anim":
                return "Animasi & Gestur";
            case "cos":
                return "Studio Custom OS";
            default:
                return "Reset & Info";
        }
    }

    // =================================================================== halaman utama + Total UI

    private void pgMain() {
        section("Total UI");
        totalUi();
        section("Lockscreen");
        row("Aktivasi & Izin", statusLine(), Glyph.LOCK, () -> show("akt"));
        row("Wallpaper Depth", "20 bawaan, foto sendiri dengan depth otomatis", Glyph.IMAGE, () -> show("lw"));
        row("Jam", "Bentuk, gaya, ukuran, warna, posisi", Glyph.STAR, () -> show("clk"));
        row("Lockscreen lainnya", "Tata letak, notifikasi, tombol, senter, AOD", Glyph.FLASH, () -> show("lms"));
        section("Tampilan");
        row("Gaya UI", "Polos, Liquid Glass, Frosted, Neon, Outline, Gradien, Tonal", Glyph.GRID, () -> show("ui"));
        row("Ikon aplikasi", "Bentuk, gaya, ukuran, icon pack, badge", Glyph.GRID, () -> show("ico"));
        section("Launcher");
        row("Wallpaper launcher", "15 bawaan, sama dengan lockscreen, atau foto", Glyph.IMAGE, () -> show("hw"));
        row("Layar utama", "Grid, label, At a Glance, efek halaman, kunci tata letak", Glyph.GRID, () -> show("home"));
        row("Dock & pencarian", "Panel dock, jumlah ikon, gaya search bar", Glyph.SEARCH, () -> show("dock"));
        row("App drawer", "Kolom, urutan, App Library, aplikasi tersembunyi", Glyph.GRID, () -> show("drw"));
        row("Animasi & gestur", "Kecepatan, geser bawah, ketuk dua kali", Glyph.GEAR, () -> show("anim"));
        section("Lainnya");
        row("Reset & info", "Powered by RZONEDEV · gratis", Glyph.INFO, () -> show("abt"));
    }

    private String statusLine() {
        boolean ov = Settings.canDrawOverlays(this);
        return (pr.enabled() ? "Lockscreen aktif" : "Lockscreen mati") + (ov ? "" : " · izin overlay belum")
                + (Util.isDefaultLauncher(this) ? " · launcher default" : "");
    }

    /** Kartu pemilih Total UI: mengubah lockscreen, launcher, ikon, drawer, menu & pengaturan sekaligus. */
    private void totalUi() {
        LinearLayout c = card();
        c.setPadding((int) (d * 14), (int) (d * 14), (int) (d * 14), (int) (d * 14));
        TextView info = tv("Ubah seluruh tampilan sekaligus, dari lockscreen sampai launcher, ikon, drawer, menu "
                + "dan halaman pengaturan ini.", 12.5f, false, MUTED);
        c.addView(info);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, (int) (d * 12), 0, 0);
        int cur = pr.os();
        int[] order = {Prefs.OS_PIXEL, Prefs.OS_IOS, Prefs.OS_CUSTOM};
        String[] subs = {"Material You", "Kaca & squircle", "Atur sendiri"};
        for (int i = 0; i < 3; i++) {
            final int os = order[i];
            LinearLayout opt = new LinearLayout(this);
            opt.setOrientation(LinearLayout.VERTICAL);
            opt.setGravity(Gravity.CENTER_HORIZONTAL);
            opt.setPadding((int) (d * 6), (int) (d * 8), (int) (d * 6), (int) (d * 10));
            GradientDrawable bg = round(os == cur ? UiStyle.alpha(ACC, 40) : SURF2, ios ? 14 : 22);
            bg.setStroke((int) (d * 2), os == cur ? ACC : 0);
            opt.setBackground(bg);
            OsPreview prev = new OsPreview(this, os);
            opt.addView(prev, new LinearLayout.LayoutParams((int) (d * 66), (int) (d * 120)));
            TextView t = tv(Prefs.OS_NAMES[os], 14, true, TEXT);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, (int) (d * 8), 0, 0);
            opt.addView(t);
            TextView s = tv(os == cur ? "Dipakai" : subs[i], 11, false, os == cur ? ACC : MUTED);
            s.setGravity(Gravity.CENTER);
            opt.addView(s);
            opt.setOnClickListener(v -> chooseOs(os));
            Util.press(opt);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i < 2) lp.rightMargin = (int) (d * 8);
            row.addView(opt, lp);
        }
        c.addView(row);
        if (cur == Prefs.OS_CUSTOM) {
            TextView cs = tv("Buka Studio Custom OS  ›", 13.5f, true, ACC);
            cs.setPadding(0, (int) (d * 14), 0, 0);
            cs.setOnClickListener(v -> show("cos"));
            c.addView(cs);
        }
        add(c);
    }

    private void chooseOs(int os) {
        if (os == Prefs.OS_CUSTOM && pr.os() == Prefs.OS_CUSTOM) {
            show("cos");
            return;
        }
        pr.applyOs(os);
        Toast.makeText(this, "Total UI: " + Prefs.OS_NAMES[os] + " diterapkan", Toast.LENGTH_SHORT).show();
        show(os == Prefs.OS_CUSTOM ? "cos" : "main");
    }

    /** Pratinjau mini Total UI. */
    final class OsPreview extends View {
        final int os;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();

        OsPreview(Context c, int os) {
            super(c);
            this.os = os;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight();
            r.set(d, d, w - d, hh - d);
            int[] g = os == Prefs.OS_PIXEL ? new int[]{0xFF2B1B4D, 0xFFD6567B, 0xFFFFB45E}
                    : os == Prefs.OS_IOS ? new int[]{0xFF2E86DE, 0xFF74B9FF, 0xFFDFF1FF}
                    : new int[]{0xFF0B0B14, 0xFF6A5CFF, 0xFFFF4FA3};
            p.setShader(new LinearGradient(0, 0, 0, hh, g, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, d * 10, d * 10, p);
            p.setShader(null);
            int acc = os == Prefs.OS_PIXEL ? 0xFFA8C7FA : os == Prefs.OS_IOS ? 0xFF0A84FF : pr.gi("ac");
            // jam
            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(os == Prefs.OS_IOS ? "sans-serif-black" : "sans-serif-condensed", Typeface.BOLD));
            p.setTextSize(os == Prefs.OS_PIXEL ? d * 15 : d * 17);
            if (os == Prefs.OS_PIXEL) {
                c.drawText("12", w / 2, hh * 0.2f, p);
                c.drawText("45", w / 2, hh * 0.33f, p);
            } else {
                c.drawText("12:45", w / 2, hh * 0.25f, p);
            }
            // ikon
            float isz = w * 0.17f, gap = (w - isz * 4) / 5f;
            int shape = os == Prefs.OS_IOS ? 1 : os == Prefs.OS_PIXEL ? 0 : pr.gi("is");
            int[] ic = {0xFFFF6B6B, 0xFF4ECDC4, 0xFFFFD93D, 0xFF6C5CE7, 0xFF1DD1A1, 0xFFFF9FF3, 0xFF54A0FF, 0xFFFECA57};
            for (int i = 0; i < 8; i++) {
                float x = gap + (i % 4) * (isz + gap), y = hh * 0.45f + (i / 4) * (isz + gap * 1.6f);
                p.setColor(ic[i]);
                c.drawPath(Icons.shape(new RectF(x, y, x + isz, y + isz), shape), p);
            }
            // dock / search
            float dy = hh * 0.82f;
            if (os == Prefs.OS_IOS || (os == Prefs.OS_CUSTOM && pr.gb("dk"))) {
                p.setColor(0x66FFFFFF);
                c.drawRoundRect(new RectF(d * 4, dy - d * 4, w - d * 4, dy + isz + d * 4), d * 8, d * 8, p);
            }
            for (int i = 0; i < 4; i++) {
                float x = gap + i * (isz + gap);
                p.setColor(UiStyle.mix(acc, ic[i], 0.4f));
                c.drawPath(Icons.shape(new RectF(x, dy, x + isz, dy + isz), shape), p);
            }
            p.setColor(os == Prefs.OS_PIXEL ? 0xCC2B2D31 : 0x88FFFFFF);
            float sy = os == Prefs.OS_IOS ? hh * 0.74f : hh * 0.94f;
            float sw = os == Prefs.OS_IOS ? w * 0.36f : w * 0.8f;
            c.drawRoundRect(new RectF(w / 2 - sw / 2, sy - d * 3.5f, w / 2 + sw / 2, sy + d * 3.5f), d * 4, d * 4, p);
        }
    }

    // =================================================================== Studio Custom OS

    private void pgCustomOs() {
        LinearLayout s = card();
        s.addView(lab(pr.os() == Prefs.OS_CUSTOM ? "Custom OS aktif" : "Sedang memakai " + Prefs.OS_NAMES[pr.os()]));
        s.addView(tv("Custom OS menyimpan tampilan buatanmu sendiri. Mengubah pengaturan apa pun saat memakai PixelOS/iOS "
                + "otomatis menjadikannya Custom OS, dan pilihanmu tetap tersimpan saat berganti preset lalu kembali.",
                12.5f, false, MUTED));
        LinearLayout btns = new LinearLayout(this);
        btns.setPadding(0, (int) (d * 12), 0, 0);
        btns.addView(button("Mulai dari PixelOS", () -> {
            pr.customFrom(Prefs.OS_PIXEL);
            Toast.makeText(this, "Custom OS berbasis PixelOS", Toast.LENGTH_SHORT).show();
            show("cos");
        }), new LinearLayout.LayoutParams(0, -2, 1));
        View sp = new View(this);
        btns.addView(sp, new LinearLayout.LayoutParams((int) (d * 8), 1));
        btns.addView(button("Mulai dari iOS", () -> {
            pr.customFrom(Prefs.OS_IOS);
            Toast.makeText(this, "Custom OS berbasis iOS", Toast.LENGTH_SHORT).show();
            show("cos");
        }), new LinearLayout.LayoutParams(0, -2, 1));
        s.addView(btns);
        add(s);

        LinearLayout c = card();
        c.addView(lab("Dasar"));
        chips(c, "Tata letak lockscreen", "lay", false, null, DepthView.LAYOUTS);
        chips(c, "Gaya UI (panel, dock, menu, folder)", "ui", false, null, UiStyle.NAMES);
        colorK(c, "Warna aksen", "ac", () -> show("cos"), 0xFFA8C7FA, 0xFF0A84FF, 0xFF00E5FF, 0xFFFF4081, 0xFF7CFC00,
                0xFFFFC400, 0xFFB388FF, 0xFFFF6E40, 0xFF30D158);
        chipsK(c, "Bentuk ikon", "is", Icons.SHAPES);
        chipsK(c, "Gaya ikon", "ik", Icons.STYLES);
        chipsK(c, "Search bar", "sbs", "Google", "Kapsul iOS", "Minimal");
        togK(c, "Panel dock", "dk");
        chipsK(c, "Badge notifikasi", "bdg", "Mati", "Titik", "Angka");
        chipsK(c, "App drawer", "lib", "A — Z", "Kategori (App Library)");
        add(c);
        section("Atur lebih detail");
        row("Jam lockscreen", null, Glyph.STAR, () -> show("clk"));
        row("Lockscreen lainnya", null, Glyph.FLASH, () -> show("lms"));
        row("Layar utama", null, Glyph.GRID, () -> show("home"));
        row("Dock & pencarian", null, Glyph.SEARCH, () -> show("dock"));
        row("App drawer", null, Glyph.GRID, () -> show("drw"));
        row("Ikon aplikasi", null, Glyph.GRID, () -> show("ico"));
        row("Animasi & gestur", null, Glyph.GEAR, () -> show("anim"));
    }

    private TextView button(String t, Runnable r) {
        TextView b = tv(t, 13.5f, true, ON_ACC);
        b.setGravity(Gravity.CENTER);
        b.setPadding((int) (d * 10), (int) (d * 11), (int) (d * 10), (int) (d * 11));
        b.setBackground(round(ACC, ios ? 12 : 40));
        b.setOnClickListener(v -> r.run());
        Util.press(b);
        return b;
    }

    // =================================================================== aktivasi

    private void refresh() {
        if (statusTv == null) return;
        String s = "Izin overlay: " + (Settings.canDrawOverlays(this) ? "aktif ✓" : "belum")
                + "\nLockscreen: " + (pr.enabled() ? "aktif ✓" : "mati")
                + "\nLauncher default: " + (Util.isDefaultLauncher(this) ? "Hyper ✓" : "bukan")
                + "\nAkses notifikasi: " + (NotifListener.granted(this) ? "aktif ✓" : "belum")
                + "\nMatikan layar (aksesibilitas): " + (ScreenOff.enabled(this) ? "aktif ✓" : "belum");
        statusTv.setText(s);
    }

    private void pgAkt() {
        LinearLayout sc = card();
        statusTv = tv("", 14, false, TEXT);
        statusTv.setLineSpacing(d * 3, 1);
        statusTv.setPadding(0, (int) (d * 10), 0, 0);
        sc.addView(statusTv);
        add(sc);
        refresh();
        section("Lockscreen");
        row(pr.enabled() ? "Matikan lockscreen" : "Aktifkan lockscreen", "Layar mati lalu nyala, Hyper muncul di atas lockscreen",
                Glyph.LOCK, this::toggleLock);
        row("Izin tampil di atas aplikasi lain", "Wajib agar lockscreen muncul otomatis", Glyph.GRID,
                () -> Util.start(this, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        row("Izin notifikasi", "Untuk service latar belakang (Android 13+)", Glyph.INFO, () -> {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            else Toast.makeText(this, "Tidak diperlukan di versi Android ini", Toast.LENGTH_SHORT).show();
        });
        row("Coba lockscreen sekarang", null, Glyph.STAR, () -> startActivity(new Intent(this, LockActivity.class)));
        section("Launcher & lainnya");
        row("Jadikan launcher default", "Hyper Launcher", Glyph.GRID, this::makeDefault);
        row("Akses notifikasi", "Notifikasi di lockscreen & badge ikon", Glyph.INFO,
                () -> Util.start(this, new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        row("Matikan layar dengan ketuk dua kali", "Aktifkan \"Hyper\" di Aksesibilitas (tidak membaca layar)", Glyph.LOCK,
                () -> Util.start(this, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }

    private void toggleLock() {
        if (!pr.enabled() && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan \"Tampil di atas aplikasi lain\" dulu", Toast.LENGTH_LONG).show();
            Util.start(this, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        pr.enabled(!pr.enabled());
        if (pr.enabled() && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        LockService.sync(this);
        Toast.makeText(this, pr.enabled() ? "Lockscreen aktif" : "Lockscreen dimatikan", Toast.LENGTH_SHORT).show();
        show("akt");
    }

    private void makeDefault() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                try {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), RQ_ROLE);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        if (!Util.start(this, new Intent(Settings.ACTION_HOME_SETTINGS)))
            Util.start(this, new Intent(Settings.ACTION_SETTINGS));
    }

    // =================================================================== wallpaper lockscreen

    private void pgLockWp() {
        preview();
        LinearLayout dc = card();
        depthSt = tv("", 12.5f, false, MUTED);
        depthSt.setPadding(0, (int) (d * 10), 0, 0);
        dc.addView(depthSt);
        add(dc);
        depthStatus();
        LinearLayout tc = card();
        tc.addView(lab("Wallpaper Depth bawaan (" + Scene.DEPTH_NAMES.length + ")"));
        tc.addView(thumbs(true, pr.gi("lm") == 0 ? Scene.clampId(pr.gi("ld"), Scene.DEPTH_NAMES.length) : -1, i -> {
            pr.si("lm", 0);
            pr.si("ld", i);
            fx(true);
            depthStatus();
        }));
        add(tc);
        section("Foto sendiri");
        row("Pilih foto (depth otomatis)", "Subjek dipotong otomatis, jam tampil di belakangnya", Glyph.IMAGE,
                () -> pick(RQ_WP));
        row("Pasang PNG foreground (manual)", "Gambar transparan berisi subjek, dipasang di atas jam", Glyph.IMAGE,
                () -> pick(RQ_FG));
        row("Pasang sebagai wallpaper sistem", "Dipakai juga di layar utama & layar kunci Android", Glyph.IMAGE,
                this::setSystemWallpaper);
        LinearLayout ec = card();
        togK(ec, "Efek depth (foto sendiri)", null, "dep", () -> {
            fx(true);
            depthStatus();
        });
        add(ec);
    }

    private void pick(int rq) {
        try {
            startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), rq);
        } catch (Exception e) {
            Toast.makeText(this, "Tidak ada aplikasi galeri", Toast.LENGTH_SHORT).show();
        }
    }

    private void setSystemWallpaper() {
        Toast.makeText(this, "Memasang wallpaper...", Toast.LENGTH_SHORT).show();
        final Context app = getApplicationContext();
        new Thread(() -> {
            String msg;
            try {
                WallpaperManager wm = WallpaperManager.getInstance(app);
                DisplayMetrics m = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(m);
                Bitmap b = Scene.homeBg(app, m.widthPixels, m.heightPixels);
                try {
                    wm.setBitmap(b, null, true, WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
                } catch (Exception e) {
                    wm.setBitmap(b);
                }
                b.recycle();
                msg = "Wallpaper sistem terpasang";
            } catch (Exception e) {
                msg = "Gagal: " + e.getMessage();
            }
            final String fm = msg;
            h.post(() -> Toast.makeText(app, fm, Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void depthStatus() {
        if (depthSt == null) return;
        boolean has = Scene.fgFile(this).exists();
        // Perbaikan: indeks dulu tidak dibatasi → crash bila nilai "ld" tidak valid.
        depthSt.setText(!Scene.lockCustom(this)
                ? "Depth bawaan: " + Scene.DEPTH_NAMES[Scene.clampId(pr.gi("ld"), Scene.DEPTH_NAMES.length)]
                : !pr.gb("dep") ? "Foto sendiri, efek depth mati"
                : has ? "Depth aktif: subjek berada di atas jam"
                : "Foto sendiri tanpa subjek. Pilih foto lagi atau pasang PNG foreground");
    }

    // =================================================================== jam & lockscreen

    private void pgClock() {
        preview();
        LinearLayout c = card();
        chipsK(c, "Bentuk", "sh", DepthView.CLOCK_SHAPES);
        togK(c, "Jam kapsul (ikut Gaya UI)", "cgk");
        chipsK(c, "Gaya", "st", DepthView.CLOCK_STYLES);
        sliderK(c, "Ukuran", "cs", 50, 150, "%");
        sliderK(c, "Posisi vertikal", "cy", 0, 100, "");
        sliderK(c, "Transparansi", "co", 20, 100, "%");
        sliderK(c, "Jarak baris (bertumpuk)", "cl", 80, 140, "%");
        sliderK(c, "Kemiringan", "ct", -20, 20, "°");
        colorK(c, "Warna", "cc", null, 0xFFFFFFFF, 0xFFFFE066, 0xFF7DF9FF, 0xFFFF8FAB, 0xFFB9FBC0, 0xFFFFA36C, 0xFFB388FF, 0xFF111111);
        colorK(c, "Warna kedua (gaya Gradien, abu-abu = otomatis)", "cg", null, 0, 0xFFFF5F6D, 0xFF00C9FF, 0xFFFFC371,
                0xFF7F00FF, 0xFF11998E);
        togK(c, "Bayangan", "csd");
        togK(c, "Format 24 jam", "h24");
        add(c);
    }

    private void pgLockMisc() {
        preview();
        LinearLayout c = card();
        chips(c, "Tata letak", "lay", false, null, DepthView.LAYOUTS);
        chipsK(c, "Tanggal (tata letak Hyper)", "dm", "Teks", "Kapsul (ikut Gaya UI)");
        togK(c, "Tampilkan baterai", "bs");
        c.addView(lab("Teks kustom"));
        EditText e = new EditText(this);
        e.setText(pr.txt());
        e.setTextColor(TEXT);
        e.setHint("mis. nama kamu");
        e.setHintTextColor(MUTED);
        e.setSingleLine(true);
        e.setBackground(round(SURF2, ios ? 10 : 16));
        e.setPadding((int) (d * 14), (int) (d * 10), (int) (d * 14), (int) (d * 10));
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int a, int b2, int c2) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b2, int c2) {
                pr.txt(s.toString());
                fx(false);
            }
        });
        c.addView(e);
        togK(c, "Ikon gembok", "li");
        togK(c, "Teks \"Geser ke atas\"", "hs");
        togK(c, "Tombol bawah", "tb");
        chipsK(c, "Tombol kiri", "lbt", "Aplikasi", "Senter");
        chipsK(c, "Ketuk dua kali", "la", "Tidak ada", "AOD (layar redup)", "Matikan layar");
        togK(c, "Fake Fingerprint in-display", "ffp");
        togK(c, "Notifikasi di lockscreen", "Butuh izin akses notifikasi. Ketuk notifikasi untuk membukanya.", "nlk", null);
        sliderK(c, "Kekuatan parallax", "lp", 0, 200, "%");
        add(c);
        section("Izin");
        row("Izin baca notifikasi", NotifListener.granted(this) ? "Sudah aktif" : "Belum aktif", Glyph.INFO,
                () -> Util.start(this, new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        row("Izin matikan layar (aksesibilitas)", ScreenOff.enabled(this) ? "Sudah aktif" : "Untuk \"Ketuk dua kali → Matikan layar\"",
                Glyph.LOCK, () -> Util.start(this, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }

    // =================================================================== gaya UI & ikon

    private void pgUi() {
        LinearLayout c = card();
        c.addView(lab("Pratinjau gaya"));
        final UiStyle us = new UiStyle(this);
        stylePv = new View(this) {
            Bitmap bg;
            final RectF r = new RectF(), dst = new RectF();

            @Override
            protected void onDraw(Canvas cv) {
                float w = getWidth(), hh = getHeight();
                if (bg == null && w > 0) {
                    bg = Scene.homeBg(getContext(), (int) w / 2, (int) hh / 2);
                    us.init(bg, (int) w, (int) hh);
                }
                if (bg != null) cv.drawBitmap(bg, null, new RectF(0, 0, w, hh), null);
                dst.set(0, 0, w, hh);
                int ui = pr.gi("ui"), ac = pr.gi("ac");
                r.set(w * 0.06f, hh * 0.12f, w * 0.62f, hh * 0.42f);
                us.draw(cv, r, r.height() / 2, ui, ac, dst, 0, 0, 0, 1);
                r.set(w * 0.68f, hh * 0.12f, w * 0.94f, hh * 0.42f);
                us.draw(cv, r, r.height() / 2, ui, ac, dst, 0, 0, 0, 1);
                r.set(w * 0.06f, hh * 0.56f, w * 0.94f, hh * 0.88f);
                us.draw(cv, r, us.d * 26, ui, ac, dst, 0, 0, 0, 1);
            }
        };
        stylePv.setBackground(round(Color.BLACK, 16));
        stylePv.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        stylePv.setClipToOutline(true);
        c.addView(stylePv, new LinearLayout.LayoutParams(-1, (int) (d * 130)));
        chips(c, "Gaya UI", "ui", false, () -> stylePv.invalidate(), UiStyle.NAMES);
        colorK(c, "Warna aksen", "ac", () -> show("ui"), 0xFFA8C7FA, 0xFF0A84FF, 0xFF00E5FF, 0xFFFF4081, 0xFF76FF03,
                0xFFFFC400, 0xFFB388FF, 0xFFFF6E40, 0xFF30D158);
        add(c);
        note("Gaya berlaku untuk: tombol & notifikasi lockscreen, pencarian, dock, menu, folder dan App Drawer. "
                + "Tonal = Material You (PixelOS), Frosted = kaca buram (iOS).");
    }

    private void pgIcons() {
        LinearLayout c = card();
        c.addView(lab("Pratinjau"));
        iconRow = new LinearLayout(this);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(iconRow);
        c.addView(hs);
        refreshIcons();
        chips(c, "Bentuk", "is", false, this::refreshIcons, Icons.SHAPES);
        chips(c, "Gaya ikon", "ik", false, this::refreshIcons, Icons.STYLES);
        sliderK(c, "Ukuran ikon", "isz", 80, 130, "%");
        chipsK(c, "Badge notifikasi", "bdg", "Mati", "Titik", "Angka");
        add(c);
        LinearLayout pc = card();
        pc.addView(lab("Icon pack"));
        final List<ResolveInfo> packs = IconPack.list(this);
        List<String> names = new ArrayList<>();
        names.add("Ikon bawaan (Hyper)");
        for (ResolveInfo r : packs) names.add(IconPack.label(this, r));
        String cur = pr.str("ipk");
        int sel = 0;
        for (int i = 0; i < packs.size(); i++)
            if (packs.get(i).activityInfo != null && packs.get(i).activityInfo.packageName.equals(cur)) sel = i + 1;
        pc.addView(chipRow(names.toArray(new String[0]), sel, i -> {
            pr.str("ipk", i == 0 ? "" : packs.get(i - 1).activityInfo.packageName);
            IconPack.invalidate();
            refreshIcons();
        }));
        if (packs.isEmpty()) {
            TextView t = tv("Belum ada icon pack terpasang. Pasang dari Play Store (mis. yang mendukung Nova/ADW/Lawnchair).",
                    12, false, MUTED);
            t.setPadding(0, (int) (d * 8), 0, 0);
            pc.addView(t);
        }
        add(pc);
        note("Ikon \"Bertema\" memakai lapisan monokrom (Android 13+) dan warna aksen. Ikon dari icon pack dipakai apa adanya; "
                + "aplikasi yang tidak ada di pack memakai gaya Hyper.");
    }

    private void refreshIcons() {
        if (iconRow == null) return;
        iconRow.removeAllViews();
        final PackageManager pm = getPackageManager();
        final int sz = (int) (d * 64);
        final int shape = pr.gi("is"), style = pr.gi("ik"), ac = pr.gi("ac");
        final String pack = pr.str("ipk");
        final LinearLayout target = iconRow;
        new Thread(() -> {
            List<ResolveInfo> l;
            try {
                l = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
            } catch (Exception e) {
                return;
            }
            int n = 0;
            for (ResolveInfo r : l) {
                if (n++ >= 12) break;
                Bitmap b;
                try {
                    b = IconPack.make(this, pack, r.activityInfo.packageName, r.activityInfo.name, r.loadIcon(pm), sz, shape, style, ac);
                } catch (Exception e) {
                    continue;
                }
                final Bitmap fb = b;
                h.post(() -> {
                    if (iconRow != target) return;
                    ImageView iv = new ImageView(this);
                    iv.setImageBitmap(fb);
                    target.addView(iv, new LinearLayout.LayoutParams((int) (d * 60), (int) (d * 60)));
                });
            }
        }).start();
    }

    // =================================================================== launcher

    private void pgHomeWp() {
        LinearLayout c = card();
        chips(c, "Sumber wallpaper", "hm", false, null, "Bawaan", "Sama lockscreen", "Foto sendiri");
        c.addView(lab("Wallpaper bawaan (" + Scene.WALL_NAMES.length + ")"));
        c.addView(thumbs(false, pr.gi("hm") == 0 ? Scene.clampId(pr.gi("hw"), Scene.WALL_NAMES.length) : -1, i -> {
            pr.si("hm", 0);
            pr.si("hw", i);
            show("hw");
        }));
        add(c);
        row("Pilih foto untuk layar utama", null, Glyph.IMAGE, () -> pick(RQ_HOME));
        row("Pasang sebagai wallpaper sistem", "Dipakai juga oleh aplikasi lain & layar kunci Android", Glyph.IMAGE,
                this::setSystemWallpaper);
        LinearLayout o = card();
        sliderK(o, "Redup wallpaper", "hd", 0, 80, "%");
        togK(o, "Parallax wallpaper saat geser halaman", "wp");
        togK(o, "Blur wallpaper saat drawer terbuka", "hb");
        add(o);
    }

    private void pgHome() {
        LinearLayout c = card();
        sliderK(c, "Kolom", "hc", 3, 6, "");
        sliderK(c, "Baris (0 = otomatis)", "hr", 0, 7, "");
        sliderK(c, "Ukuran ikon", "isz", 80, 130, "%");
        togK(c, "Label ikon", "lv");
        sliderK(c, "Ukuran label", "ls", 9, 15, "sp");
        togK(c, "At a Glance (tanggal)", "gl");
        sliderK(c, "Ukuran tanggal", "gs", 22, 48, "sp");
        chipsK(c, "Efek geser halaman", "pt", "Geser", "Kubus", "Zoom", "Tumpuk");
        togK(c, "Kunci tata letak", "Cegah ikon terseret atau terhapus tidak sengaja", "lck", null);
        add(c);
    }

    private void pgDock() {
        LinearLayout c = card();
        togK(c, "Search bar", "sv");
        chipsK(c, "Gaya search bar", "sbs", "Google", "Kapsul iOS", "Minimal");
        chipsK(c, "Posisi search bar (Google/Minimal)", "sp", "Bawah", "Atas");
        togK(c, "Panel dock (ikut Gaya UI)", "dk");
        sliderK(c, "Jumlah ikon dock", "dn", 3, 5, "");
        add(c);
    }

    private void pgDrawer() {
        LinearLayout c = card();
        sliderK(c, "Kolom drawer", "dc", 3, 6, "");
        togK(c, "Label ikon", "dl");
        togK(c, "Baris saran aplikasi", "rr");
        chipsK(c, "Tampilan", "lib", "A — Z", "Kategori (App Library)");
        chipsK(c, "Urutan", "dso", "A — Z", "Paling sering dipakai");
        add(c);
        int n = pr.hidden().size();
        row("Aplikasi tersembunyi", n == 0 ? "Tidak ada. Tekan lama ikon di drawer › Sembunyikan" : n + " aplikasi", Glyph.EYE_OFF,
                () -> show("hid"));
    }

    private void pgHidden() {
        final Set<String> hid = pr.hidden();
        if (hid.isEmpty()) {
            note("Belum ada aplikasi yang disembunyikan. Di App Drawer, tekan lama ikon lalu pilih \"Sembunyikan aplikasi\".");
            return;
        }
        note("Ketuk aplikasi untuk menampilkannya lagi di App Drawer.");
        PackageManager pm = getPackageManager();
        for (final String key : hid) {
            int s = key.indexOf('/');
            String label = key;
            if (s > 0) {
                try {
                    label = pm.getActivityInfo(new android.content.ComponentName(key.substring(0, s), key.substring(s + 1)), 0)
                            .loadLabel(pm).toString();
                } catch (Exception e) {
                    label = key.substring(0, s) + " (tidak terpasang)";
                }
            }
            row(label, "Ketuk untuk tampilkan", Glyph.EYE_OFF, () -> {
                pr.setHidden(key, false);
                Toast.makeText(this, "Ditampilkan lagi", Toast.LENGTH_SHORT).show();
                show("hid");
            });
        }
    }

    private void pgAnim() {
        LinearLayout c = card();
        sliderK(c, "Kecepatan animasi", "as", 50, 200, "%");
        chipsK(c, "Geser ke bawah di layar utama", "sg", "Tarik notifikasi", "Pencarian", "Tidak ada");
        chipsK(c, "Ketuk dua kali di area kosong", "dt", "Tidak ada", "Buka lockscreen", "Buka drawer", "Matikan layar");
        add(c);
        note("\"Matikan layar\" butuh izin Aksesibilitas untuk Hyper (Android 9+). Hyper hanya memakainya untuk mengunci layar.");
    }

    // =================================================================== reset & info

    private void pgAbout() {
        section("Reset");
        row("Reset semua pengaturan", "Kembali ke bawaan PixelOS (tata letak layar utama tidak diubah)", Glyph.TRASH,
                () -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Reset pengaturan?").setPositiveButton("Reset", (a, b) -> {
                            pr.reset();
                            LockService.sync(this);
                            show("main");
                        }).setNegativeButton("Batal", null).show());
        row("Reset tata letak layar utama", "Susun ulang ikon ke bawaan", Glyph.GRID, () -> {
            getSharedPreferences("home", MODE_PRIVATE).edit().remove("pages").remove("hot").remove("grid").apply();
            HomeActivity.lastSig = "";
            Toast.makeText(this, "Selesai, buka launcher", Toast.LENGTH_SHORT).show();
        });
        LinearLayout n = card();
        n.setPadding((int) (d * 18), (int) (d * 14), (int) (d * 18), (int) (d * 14));
        n.addView(tv("Powered by RZONEDEV", 16, true, TEXT));
        n.addView(tv("Aplikasi ini SEPENUHNYA GRATIS. Jika ada yang menjual, laporkan ke WhatsApp 0882-0085-19349.", 12.5f, false, MUTED));
        TextView tt = tv("TikTok: @maramadhona", 13, true, ACC);
        tt.setPadding(0, (int) (d * 10), 0, 0);
        tt.setOnClickListener(v -> Util.start(this, new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/@maramadhona"))));
        n.addView(tt);
        add(n);
    }

    // =================================================================== memuat gambar

    /**
     * Perbaikan: v5.1 menghitung skala dengan pembagian integer (1440 / lebar_foto = 0),
     * sehingga hampir semua foto kamera gagal dimuat ("Gagal memuat gambar").
     */
    private Bitmap decode(Uri u, int maxSide) throws Exception {
        int ori = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream a = getContentResolver().openInputStream(u)) {
            if (a != null) ori = new ExifInterface(a).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception ignored) {
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream a = getContentResolver().openInputStream(u)) {
            BitmapFactory.decodeStream(a, null, o);
        }
        if (o.outWidth <= 0 || o.outHeight <= 0) throw new Exception("bukan gambar");
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        Bitmap b;
        try (InputStream a = getContentResolver().openInputStream(u)) {
            b = BitmapFactory.decodeStream(a, null, o2);
        }
        if (b == null) throw new Exception("gagal decode");
        float k = Math.min(1f, (float) maxSide / Math.max(b.getWidth(), b.getHeight()));
        Matrix m = new Matrix();
        m.postScale(k, k);
        if (ori == ExifInterface.ORIENTATION_ROTATE_90) m.postRotate(90);
        else if (ori == ExifInterface.ORIENTATION_ROTATE_180) m.postRotate(180);
        else if (ori == ExifInterface.ORIENTATION_ROTATE_270) m.postRotate(270);
        else if (ori == ExifInterface.ORIENTATION_FLIP_HORIZONTAL) m.postScale(-1, 1);
        if (k == 1f && m.isIdentity()) return b;
        Bitmap out = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        if (out != b) b.recycle();
        return out;
    }

    private void save(Bitmap b, File f, boolean png) throws Exception {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            b.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, png ? 100 : 92, o);
        }
        if (!tmp.renameTo(f)) throw new Exception("gagal menyimpan");
    }

    private void status(String s) {
        if (depthSt != null) depthSt.setText(s);
        else Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(final int rq, int rs, Intent data) {
        super.onActivityResult(rq, rs, data);
        if (rq == RQ_ROLE) {
            Toast.makeText(this, Util.isDefaultLauncher(this) ? "Hyper jadi launcher default" : "Launcher tidak diubah",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (rq < RQ_WP || rq > RQ_HOME || rs != RESULT_OK || data == null || data.getData() == null) return;
        final Uri u = data.getData();
        status("Memproses gambar...");
        new Thread(() -> {
            try {
                final Bitmap bmp = decode(u, 1440);
                if (rq == RQ_FG) {
                    // Perbaikan: PNG dulu ditarik paksa ke ukuran wallpaper (gepeng bila rasio beda).
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(Scene.wpFile(this).getPath(), o);
                    Bitmap fg = o.outWidth > 0 ? Util.crop(bmp, o.outWidth, o.outHeight, true) : bmp;
                    save(fg, Scene.fgFile(this), true);
                    fg.recycle();
                    h.post(() -> {
                        pr.si("lm", 1);
                        pr.si("dep", 1);
                        fx(true);
                        depthStatus();
                    });
                } else if (rq == RQ_HOME) {
                    save(bmp, Scene.homeFile(this), false);
                    bmp.recycle();
                    h.post(() -> {
                        pr.si("hm", 2);
                        show("hw");
                    });
                } else {
                    save(bmp, Scene.wpFile(this), false);
                    //noinspection ResultOfMethodCallIgnored
                    Scene.fgFile(this).delete();
                    pr.si("lm", 1);
                    pr.si("dep", 1);
                    h.post(() -> {
                        fx(true);
                        segment(bmp);
                    });
                }
            } catch (Throwable e) {
                h.post(() -> status("Gagal memuat gambar"));
            }
        }).start();
    }

    private void segment(final Bitmap bmp) {
        status("Mendeteksi subjek (pertama kali mungkin perlu unduh model, mohon tunggu)...");
        final SubjectSegmenter sg;
        try {
            sg = SubjectSegmentation.getClient(new SubjectSegmenterOptions.Builder().enableForegroundBitmap().build());
        } catch (Throwable t) {
            status("Depth otomatis tidak tersedia di perangkat ini. Pakai PNG foreground manual.");
            return;
        }
        sg.process(InputImage.fromBitmap(bmp, 0)).addOnSuccessListener(r -> {
            final Bitmap f = r.getForegroundBitmap();
            new Thread(() -> {
                boolean ok = false;
                try {
                    if (f != null) {
                        save(f, Scene.fgFile(this), true);
                        ok = true;
                    }
                } catch (Exception ignored) {
                }
                final boolean fok = ok;
                h.post(() -> {
                    sg.close();
                    bmp.recycle();
                    fx(true);
                    if (fok) depthStatus();
                    else status("Subjek tidak ditemukan di foto ini. Coba foto lain atau pasang PNG foreground.");
                });
            }).start();
        }).addOnFailureListener(e -> {
            sg.close();
            status("Depth otomatis gagal (butuh Google Play Services & koneksi untuk unduh model). Pakai PNG foreground manual.");
        });
    }

}
