package com.rzone.hyper;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Widget Android di layar utama Hyper.
 * Disimpan sebagai {id, halaman, kolom, baris, lebar, tinggi} (satuan sel grid).
 */
final class Widgets {
    static final int RQ_BIND = 31, RQ_CONF = 32;
    private static final int HOST_ID = 0x4859;

    private final HomeActivity a;
    final AppWidgetManager awm;
    final AppWidgetHost host;
    /** {id, page, col, row, sx, sy} */
    final List<int[]> recs = new ArrayList<>();
    private int pendingId = -1;

    Widgets(HomeActivity a) {
        this.a = a;
        awm = AppWidgetManager.getInstance(a);
        host = new AppWidgetHost(a.getApplicationContext(), HOST_ID);
        load();
    }

    void start() {
        try {
            host.startListening();
        } catch (Exception ignored) {
        }
    }

    void stop() {
        try {
            host.stopListening();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ simpan / muat

    private void load() {
        recs.clear();
        String s = a.sp.getString("widgets", "");
        for (String e : s.split(";")) {
            String[] f = e.split(",");
            if (f.length != 6) continue;
            try {
                int[] r = new int[6];
                for (int i = 0; i < 6; i++) r[i] = Integer.parseInt(f[i]);
                if (awm.getAppWidgetInfo(r[0]) != null) recs.add(r);
                else host.deleteAppWidgetId(r[0]); // aplikasinya sudah dihapus
            } catch (Exception ignored) {
            }
        }
    }

    void save() {
        StringBuilder b = new StringBuilder();
        for (int[] r : recs) {
            if (b.length() > 0) b.append(';');
            b.append(r[0]).append(',').append(r[1]).append(',').append(r[2]).append(',').append(r[3]).append(',')
                    .append(r[4]).append(',').append(r[5]);
        }
        a.sp.edit().putString("widgets", b.toString()).apply();
    }

    /** Jaga widget tetap di dalam grid (mis. setelah jumlah kolom/baris diubah). */
    void normalize() {
        for (int[] r : recs) {
            r[4] = Math.max(1, Math.min(r[4], a.cols));
            r[5] = Math.max(1, Math.min(r[5], a.rows));
            r[2] = Math.max(0, Math.min(r[2], a.cols - r[4]));
            r[3] = Math.max(0, Math.min(r[3], a.rows - r[5]));
            while (a.pages.size() <= r[1]) a.pages.add(a.blank());
        }
    }

    // ------------------------------------------------------------------ okupansi grid

    boolean covers(int[] r, int page, int col, int row) {
        return r[1] == page && col >= r[2] && col < r[2] + r[4] && row >= r[3] && row < r[3] + r[5];
    }

    boolean occupied(int page, int slot) {
        int col = slot % a.cols, row = slot / a.cols;
        for (int[] r : recs) if (covers(r, page, col, row)) return true;
        return false;
    }

    boolean hasOnPage(int page) {
        for (int[] r : recs) if (r[1] == page) return true;
        return false;
    }

    private boolean free(int page, int col, int row, int sx, int sy, int[] ignore) {
        if (col < 0 || row < 0 || col + sx > a.cols || row + sy > a.rows) return false;
        for (int[] r : recs) {
            if (r == ignore || r[1] != page) continue;
            if (col < r[2] + r[4] && col + sx > r[2] && row < r[3] + r[5] && row + sy > r[3]) return false;
        }
        return true;
    }

    /** Pindahkan ikon yang tertutup widget ke tempat kosong lain. */
    private void evictIcons(int[] r) {
        if (r[1] >= a.pages.size()) return;
        Item[] pg = a.pages.get(r[1]);
        List<Item> moved = new ArrayList<>();
        for (int s = 0; s < pg.length; s++) {
            if (pg[s] != null && covers(r, r[1], s % a.cols, s / a.cols)) {
                moved.add(pg[s]);
                pg[s] = null;
            }
        }
        for (Item it : moved) a.placeFree(it);
    }

    // ------------------------------------------------------------------ tampilan

    void addViews(FrameLayout pg, int page) {
        for (final int[] r : recs) {
            if (r[1] != page) continue;
            AppWidgetProviderInfo info = awm.getAppWidgetInfo(r[0]);
            if (info == null) continue;
            AppWidgetHostView v;
            try {
                v = host.createView(a.getApplicationContext(), r[0], info);
            } catch (Exception e) {
                continue;
            }
            v.setPadding(0, 0, 0, 0);
            WidgetFrame f = new WidgetFrame(a, r);
            f.addView(v, new FrameLayout.LayoutParams(-1, -1));
            int pad = (int) a.dp(6);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (r[4] * a.cellW) - pad * 2, (int) (r[5] * a.cellH) - pad * 2);
            lp.leftMargin = (int) (r[2] * a.cellW) + pad;
            lp.topMargin = (int) (r[3] * a.cellH) + pad;
            pg.addView(f, lp);
            updateSize(r);
        }
    }

    private void updateSize(int[] r) {
        try {
            float d = a.d;
            int w = (int) ((r[4] * a.cellW - a.dp(12)) / d), h = (int) ((r[5] * a.cellH - a.dp(12)) / d);
            Bundle o = new Bundle();
            o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w);
            o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w);
            o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h);
            o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h);
            awm.updateAppWidgetOptions(r[0], o);
        } catch (Exception ignored) {
        }
    }

    /** Widget di titik layar (koordinat root) pada halaman yang terlihat. */
    View at(float x, float y) {
        int pg = Math.round(a.pagePos);
        if (pg < 0 || pg >= a.pager.getChildCount()) return null;
        ViewGroup g = (ViewGroup) a.pager.getChildAt(pg);
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c instanceof WidgetFrame && x >= c.getLeft() && x < c.getRight()
                    && y - a.pagerTop >= c.getTop() && y - a.pagerTop < c.getBottom()) return c;
        }
        return null;
    }

    /** Pembungkus widget: tekan lama memunculkan menu ukuran/hapus. */
    final class WidgetFrame extends FrameLayout {
        final int[] rec;
        private float x0, y0;
        private boolean fired;
        private final int slop;
        private final Runnable lp = new Runnable() {
            @Override
            public void run() {
                fired = true;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                menu(WidgetFrame.this);
            }
        };

        WidgetFrame(Context c, int[] rec) {
            super(c);
            this.rec = rec;
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    fired = false;
                    x0 = e.getX();
                    y0 = e.getY();
                    postDelayed(lp, 500);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.hypot(e.getX() - x0, e.getY() - y0) > slop) removeCallbacks(lp);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(lp);
                    break;
            }
            return fired;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            return fired || super.onTouchEvent(e);
        }
    }

    // ------------------------------------------------------------------ menu widget

    private void menu(WidgetFrame f) {
        final int[] r = f.rec;
        int[] l = new int[2];
        f.getLocationInWindow(l);
        List<HomeActivity.MI> mi = new ArrayList<>();
        if (a.locked) {
            Toast.makeText(a, "Tata letak terkunci", Toast.LENGTH_SHORT).show();
            return;
        }
        mi.add(new HomeActivity.MI("Lebih lebar", Glyph.PLUS, () -> resize(r, 1, 0)));
        mi.add(new HomeActivity.MI("Lebih sempit", Glyph.GRID, () -> resize(r, -1, 0)));
        mi.add(new HomeActivity.MI("Lebih tinggi", Glyph.PLUS, () -> resize(r, 0, 1)));
        mi.add(new HomeActivity.MI("Lebih pendek", Glyph.GRID, () -> resize(r, 0, -1)));
        mi.add(new HomeActivity.MI("Hapus widget", Glyph.TRASH, () -> remove(r)));
        a.showMenu(l[0], l[1], f.getWidth(), Math.min(f.getHeight(), (int) a.dp(80)), mi);
    }

    private void resize(int[] r, int dx, int dy) {
        int sx = r[4] + dx, sy = r[5] + dy;
        if (sx < 1 || sy < 1) return;
        int col = r[2], row = r[3];
        if (col + sx > a.cols) col = a.cols - sx;
        if (row + sy > a.rows) row = a.rows - sy;
        if (!free(r[1], col, row, sx, sy, r)) {
            Toast.makeText(a, "Tidak ada ruang untuk ukuran itu", Toast.LENGTH_SHORT).show();
            return;
        }
        r[2] = col;
        r[3] = row;
        r[4] = sx;
        r[5] = sy;
        evictIcons(r);
        save();
        a.save();
        a.rebuild();
    }

    private void remove(int[] r) {
        try {
            host.deleteAppWidgetId(r[0]);
        } catch (Exception ignored) {
        }
        recs.remove(r);
        save();
        a.rebuild();
    }

    // ------------------------------------------------------------------ memilih & memasang widget

    void showPicker() {
        final List<AppWidgetProviderInfo> list = new ArrayList<>();
        try {
            list.addAll(awm.getInstalledProviders());
        } catch (Exception ignored) {
        }
        if (list.isEmpty()) {
            Toast.makeText(a, "Tidak ada widget yang tersedia", Toast.LENGTH_SHORT).show();
            return;
        }
        final android.content.pm.PackageManager pm = a.getPackageManager();
        list.sort((x, y) -> app(pm, x).compareToIgnoreCase(app(pm, y)));
        a.resetOverlay();
        View sc = new View(a);
        sc.setBackgroundColor(0x99000000);
        sc.setClickable(true);
        sc.setOnClickListener(v -> a.dismissOverlay());
        a.overlay.addView(sc, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(a.us.panel(card, a.dp(28), a.ui == 0 ? 6 : a.ui, a.ac));
        card.setClickable(true);
        TextView t = new TextView(a);
        t.setText("Widget");
        t.setTextColor(Color.WHITE);
        t.setTextSize(20);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding((int) a.dp(22), (int) a.dp(18), 0, (int) a.dp(8));
        card.addView(t);
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) a.dp(12), 0, (int) a.dp(12), (int) a.dp(14));
        String lastApp = "";
        int dpi = a.getResources().getDisplayMetrics().densityDpi;
        for (final AppWidgetProviderInfo info : list) {
            String appName = app(pm, info);
            if (!appName.equals(lastApp)) {
                lastApp = appName;
                TextView h = new TextView(a);
                h.setText(appName);
                h.setTextColor(0xCCFFFFFF);
                h.setTextSize(13);
                h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                h.setPadding((int) a.dp(10), (int) a.dp(14), 0, (int) a.dp(6));
                col.addView(h);
            }
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) a.dp(10), (int) a.dp(8), (int) a.dp(10), (int) a.dp(8));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x1AFFFFFF);
            bg.setCornerRadius(a.dp(18));
            row.setBackground(bg);
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Drawable pv = null;
            try {
                pv = info.loadPreviewImage(a, dpi);
                if (pv == null) pv = info.loadIcon(a, dpi);
            } catch (Exception ignored) {
            }
            iv.setImageDrawable(pv);
            row.addView(iv, new LinearLayout.LayoutParams((int) a.dp(96), (int) a.dp(72)));
            LinearLayout tx = new LinearLayout(a);
            tx.setOrientation(LinearLayout.VERTICAL);
            tx.setPadding((int) a.dp(14), 0, 0, 0);
            TextView nm = new TextView(a);
            String label;
            try {
                label = info.loadLabel(pm);
            } catch (Exception e) {
                label = info.provider.getShortClassName();
            }
            nm.setText(label);
            nm.setTextColor(Color.WHITE);
            nm.setTextSize(15);
            tx.addView(nm);
            int[] sp = span(info);
            TextView sz = new TextView(a);
            sz.setText(sp[0] + " × " + sp[1]);
            sz.setTextColor(0x99FFFFFF);
            sz.setTextSize(12);
            tx.addView(sz);
            row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
            row.setOnClickListener(v -> {
                a.dismissOverlay();
                add(info);
            });
            Util.press(row);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = (int) a.dp(6);
            col.addView(row, rl);
        }
        sv.addView(col);
        card.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-1, (int) (a.H * 0.72f), Gravity.BOTTOM);
        cl.leftMargin = cl.rightMargin = (int) a.dp(10);
        cl.bottomMargin = (int) (a.navH + a.dp(10));
        a.overlay.addView(card, cl);
        card.setTranslationY(a.H * 0.4f);
        card.setAlpha(0);
        card.animate().translationY(0).alpha(1).setDuration(a.T(360)).setInterpolator(Motion.EMPH).start();
    }

    private static String app(android.content.pm.PackageManager pm, AppWidgetProviderInfo i) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(i.provider.getPackageName(), 0)).toString();
        } catch (Exception e) {
            return i.provider.getPackageName();
        }
    }

    /** Ukuran awal widget dalam sel grid. */
    int[] span(AppWidgetProviderInfo info) {
        int w = info.minWidth;
        int sx, sy;
        if (android.os.Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            sx = info.targetCellWidth;
            sy = info.targetCellHeight;
        } else {
            sx = (int) Math.ceil((w + a.dp(8)) / a.cellW);
            sy = (int) Math.ceil((info.minHeight + a.dp(8)) / a.cellH);
        }
        return new int[]{Math.max(1, Math.min(a.cols, sx)), Math.max(1, Math.min(a.rows, sy))};
    }

    void add(AppWidgetProviderInfo info) {
        int id;
        try {
            id = host.allocateAppWidgetId();
        } catch (Exception e) {
            Toast.makeText(a, "Widget tidak bisa ditambahkan", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingId = id;
        boolean ok;
        try {
            ok = awm.bindAppWidgetIdIfAllowed(id, info.getProfile(), info.provider, null);
        } catch (Exception e) {
            ok = false;
        }
        if (ok) {
            configure(id);
            return;
        }
        // minta izin pengguna lewat dialog sistem
        try {
            a.startActivityForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile()), RQ_BIND);
        } catch (Exception e) {
            cancel();
        }
    }

    private void configure(int id) {
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
        if (info != null && info.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(a, id, 0, RQ_CONF, null);
                return;
            } catch (Exception ignored) {
            }
        }
        place(id);
    }

    private void cancel() {
        if (pendingId >= 0) {
            try {
                host.deleteAppWidgetId(pendingId);
            } catch (Exception ignored) {
            }
        }
        pendingId = -1;
    }

    /** @return true bila hasil aktivitas ini milik widget */
    boolean onResult(int rq, int rs) {
        if (rq == RQ_BIND) {
            if (rs == android.app.Activity.RESULT_OK && pendingId >= 0) configure(pendingId);
            else cancel();
            return true;
        }
        if (rq == RQ_CONF) {
            if (rs == android.app.Activity.RESULT_OK && pendingId >= 0) place(pendingId);
            else cancel();
            return true;
        }
        return false;
    }

    /** Letakkan widget baru di area kosong pertama (mulai dari halaman yang terlihat). */
    void place(int id) {
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
        if (info == null) {
            cancel();
            return;
        }
        int[] sp = span(info);
        int start = Math.max(0, Math.min(a.pages.size() - 1, Math.round(a.pagePos)));
        int[] r = null;
        for (int k = 0; k < a.pages.size() && r == null; k++) {
            int pg = (start + k) % a.pages.size();
            for (int row = 0; row <= a.rows - sp[1] && r == null; row++)
                for (int col = 0; col <= a.cols - sp[0] && r == null; col++)
                    if (free(pg, col, row, sp[0], sp[1], null)) r = new int[]{id, pg, col, row, sp[0], sp[1]};
        }
        if (r == null) {
            a.pages.add(a.blank());
            r = new int[]{id, a.pages.size() - 1, 0, 0, sp[0], sp[1]};
        }
        recs.add(r);
        pendingId = -1;
        evictIcons(r);
        save();
        a.save();
        a.rebuild();
        a.snapTo(r[1]);
        String lbl;
        try {
            lbl = info.loadLabel(a.getPackageManager());
        } catch (Exception e) {
            lbl = "Widget";
        }
        Toast.makeText(a, lbl + " ditambahkan", Toast.LENGTH_SHORT).show();
    }

    /** Uji CI: tambah widget pertama yang cocok tanpa dialog (butuh `appwidget grantbind`). */
    void addFirst(String hint) {
        for (AppWidgetProviderInfo i : awm.getInstalledProviders()) {
            if (hint == null || i.provider.getPackageName().toLowerCase(Locale.ROOT).contains(hint)) {
                add(i);
                return;
            }
        }
    }
}
