package com.rzone.hyper;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Store Depth Wallpaper.
 *  - Ilustrasi: 20 adegan depth × 15 variasi warna = 300 wallpaper (offline, depth asli).
 *  - Foto Online: ribuan foto (picsum.photos), subjek dipotong otomatis (ML Kit) saat dipasang.
 */
public class StoreActivity extends Activity {
    private float d;
    private Prefs pr;
    private int acc;
    private GridView grid;
    private TextView tabA, tabB, status;
    private boolean online;
    private final List<int[]> illus = new ArrayList<>();           // {adegan, variasi}
    private final List<String[]> photos = new ArrayList<>();       // {id, penulis}
    private int page = 1;
    private boolean loading, endReached;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        pr = new Prefs(this);
        acc = pr.os() == Prefs.OS_IOS ? 0xFF0A84FF : pr.os() == Prefs.OS_PIXEL ? 0xFFA8C7FA : pr.gi("ac");
        for (int v = 0; v < Scene.VARIANTS.length; v++)
            for (int id = 0; id < Scene.DEPTH_NAMES.length; id++) illus.add(new int[]{id, v});

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(0xFF0D0E13);
        col.setOnApplyWindowInsetsListener((v, in) -> {
            @SuppressWarnings("deprecation") int t = in.getSystemWindowInsetTop(), bt = in.getSystemWindowInsetBottom();
            v.setPadding(0, t, 0, 0);
            grid.setPadding((int) (d * 10), (int) (d * 4), (int) (d * 10), bt + (int) (d * 16));
            return in;
        });
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding((int) (d * 8), (int) (d * 8), (int) (d * 16), (int) (d * 4));
        FrameLayout back = new FrameLayout(this);
        back.addView(new Glyph(this, Glyph.BACK, Color.WHITE), new FrameLayout.LayoutParams((int) (d * 22), (int) (d * 22), Gravity.CENTER));
        back.setOnClickListener(v -> finish());
        back.setContentDescription("Kembali");
        head.addView(back, new LinearLayout.LayoutParams((int) (d * 44), (int) (d * 44)));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Store Depth Wallpaper");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tt.addView(title);
        status = new TextView(this);
        status.setTextColor(0xFFA9ADBA);
        status.setTextSize(12);
        tt.addView(status);
        head.addView(tt, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(head);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding((int) (d * 16), (int) (d * 6), (int) (d * 16), (int) (d * 6));
        tabA = tab("Ilustrasi Depth (" + illus.size() + ")");
        tabB = tab("Foto Online (1000+)");
        tabs.addView(tabA, new LinearLayout.LayoutParams(0, (int) (d * 40), 1));
        View sp = new View(this);
        tabs.addView(sp, new LinearLayout.LayoutParams((int) (d * 8), 1));
        tabs.addView(tabB, new LinearLayout.LayoutParams(0, (int) (d * 40), 1));
        tabA.setOnClickListener(v -> setTab(false));
        tabB.setOnClickListener(v -> setTab(true));
        col.addView(tabs);

        grid = new GridView(this);
        grid.setNumColumns(3);
        grid.setVerticalSpacing((int) (d * 10));
        grid.setHorizontalSpacing((int) (d * 10));
        grid.setClipToPadding(false);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setVerticalScrollBarEnabled(false);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView v, int s) {
            }

            @Override
            public void onScroll(AbsListView v, int first, int vis, int total) {
                if (online && !loading && !endReached && first + vis >= total - 9) loadMore();
            }
        });
        col.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(col);
        setTab(false);
    }

    private TextView tab(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(13);
        v.setSingleLine(true);
        return v;
    }

    private void setTab(boolean on) {
        online = on;
        for (TextView t : new TextView[]{tabA, tabB}) {
            boolean sel = (t == tabB) == on;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(d * 20);
            g.setColor(sel ? acc : 0xFF2A2C37);
            t.setBackground(g);
            t.setTextColor(sel ? (UiStyle.lum(acc) > 0.6f ? 0xFF0B0F1A : Color.WHITE) : Color.WHITE);
        }
        status.setText(on ? "Foto dari picsum.photos · subjek dipotong otomatis saat dipasang"
                : Scene.DEPTH_NAMES.length + " adegan × " + Scene.VARIANTS.length + " variasi · depth asli, offline");
        if (on && photos.isEmpty()) loadMore();
        adapter.notifyDataSetChanged();
        grid.setSelection(0);
    }

    private void loadMore() {
        loading = true;
        final int pg = page;
        pool.execute(() -> {
            List<String[]> got = new ArrayList<>();
            try {
                JSONArray a = new JSONArray(get("https://picsum.photos/v2/list?page=" + pg + "&limit=60"));
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    got.add(new String[]{o.optString("id"), o.optString("author")});
                }
            } catch (Exception e) {
                android.util.Log.w("Hyper", "store list", e);
            }
            if (got.isEmpty()) {
                // Daftar API gagal: pakai ID foto picsum yang sudah diketahui (0–1084) langsung.
                for (int id = (pg - 1) * 60; id < pg * 60 && id <= 1084; id++) got.add(new String[]{String.valueOf(id), "Foto #" + id});
            }
            h.post(() -> {
                loading = false;
                if (got.isEmpty()) {
                    endReached = true;
                    return;
                }
                page++;
                photos.addAll(got);
                if (online) adapter.notifyDataSetChanged();
            });
        });
    }

    private static String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "RZONE-Hyper");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    private static Bitmap getBitmap(String u) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            try (InputStream in = c.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            android.util.Log.w("Hyper", "store img " + u, e);
            return null;
        }
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return online ? photos.size() : illus.size();
        }

        @Override
        public Object getItem(int i) {
            return i;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View cv, ViewGroup p) {
            FrameLayout cell = cv instanceof FrameLayout ? (FrameLayout) cv : null;
            int tw = (int) ((getResources().getDisplayMetrics().widthPixels - d * 40) / 3);
            int th = (int) (tw * 2.0f);
            if (cell == null) {
                cell = new FrameLayout(StoreActivity.this);
                ImageView iv = new ImageView(StoreActivity.this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(d * 14);
                g.setColor(0xFF1E2029);
                iv.setBackground(g);
                iv.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                iv.setClipToOutline(true);
                cell.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                TextView t = new TextView(StoreActivity.this);
                t.setTextColor(Color.WHITE);
                t.setTextSize(10);
                t.setShadowLayer(d * 3, 0, d, 0xCC000000);
                t.setGravity(Gravity.CENTER);
                t.setPadding((int) (d * 4), 0, (int) (d * 4), (int) (d * 6));
                t.setMaxLines(2);
                cell.addView(t, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
                cell.setLayoutParams(new AbsListView.LayoutParams(-1, th));
                Util.press(cell);
            }
            final ImageView iv = (ImageView) cell.getChildAt(0);
            TextView t = (TextView) cell.getChildAt(1);
            final String key;
            if (online) {
                final String[] ph = photos.get(i);
                key = "p" + ph[0];
                t.setText(ph[1]);
                cell.setOnClickListener(v -> confirmPhoto(ph));
            } else {
                final int[] it = illus.get(i);
                key = "i" + it[0] + "_" + it[1];
                t.setText(Scene.DEPTH_NAMES[it[0]] + "\n" + Scene.VARIANTS[it[1]]);
                cell.setOnClickListener(v -> applyIllus(it[0], it[1]));
            }
            iv.setTag(key);
            Bitmap c = cache.get(key);
            iv.setImageBitmap(c);
            if (c == null) {
                final int fw = tw / 2, fh = th / 2;
                pool.execute(() -> {
                    Bitmap b;
                    if (key.startsWith("p")) b = getBitmap("https://picsum.photos/id/" + key.substring(1) + "/" + fw * 2 + "/" + fh * 2);
                    else {
                        String[] s = key.substring(1).split("_");
                        b = Scene.storeThumb(Integer.parseInt(s[0]), Integer.parseInt(s[1]), fw, fh);
                    }
                    if (b == null) return;
                    cache.put(key, b);
                    h.post(() -> {
                        if (key.equals(iv.getTag())) iv.setImageBitmap(b);
                    });
                });
            }
            return cell;
        }
    }

    private void applyIllus(int id, int v) {
        pr.si("lm", 0);
        pr.si("ld", id);
        pr.si("ldv", v);
        Toast.makeText(this, "Dipasang: " + Scene.DEPTH_NAMES[id] + " · " + Scene.VARIANTS[v], Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmPhoto(final String[] ph) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Pasang foto ini?")
                .setMessage("Foto oleh " + ph[1] + ". Subjek akan dipotong otomatis agar tampil di depan jam (butuh internet).")
                .setPositiveButton("Pasang", (a, w) -> applyPhoto(ph[0]))
                .setNegativeButton("Batal", null).show();
    }

    private void applyPhoto(final String id) {
        status.setText("Mengunduh wallpaper...");
        final android.util.DisplayMetrics m = getResources().getDisplayMetrics();
        pool.execute(() -> {
            int w = Math.min(1440, m.widthPixels), hh = Math.min(3200, m.heightPixels);
            Bitmap b = getBitmap("https://picsum.photos/id/" + id + "/" + w + "/" + hh);
            if (b == null) {
                h.post(() -> status.setText("Gagal mengunduh. Cek koneksi internet."));
                return;
            }
            try (FileOutputStream o = new FileOutputStream(Scene.wpFile(this))) {
                b.compress(Bitmap.CompressFormat.JPEG, 92, o);
            } catch (Exception e) {
                h.post(() -> status.setText("Gagal menyimpan wallpaper."));
                return;
            }
            //noinspection ResultOfMethodCallIgnored
            Scene.fgFile(this).delete();
            pr.si("lm", 1);
            pr.si("dep", 1);
            h.post(() -> segment(b));
        });
    }

    private void segment(final Bitmap b) {
        status.setText("Memotong subjek untuk efek depth...");
        final SubjectSegmenter sg;
        try {
            sg = SubjectSegmentation.getClient(new SubjectSegmenterOptions.Builder().enableForegroundBitmap().build());
        } catch (Throwable t) {
            done("Wallpaper dipasang (depth otomatis tidak tersedia di perangkat ini)");
            return;
        }
        sg.process(InputImage.fromBitmap(b, 0)).addOnSuccessListener(r -> {
            Bitmap f = r.getForegroundBitmap();
            pool.execute(() -> {
                boolean ok = false;
                if (f != null) {
                    try (FileOutputStream o = new FileOutputStream(Scene.fgFile(this))) {
                        f.compress(Bitmap.CompressFormat.PNG, 100, o);
                        ok = true;
                    } catch (Exception ignored) {
                    }
                }
                final boolean fok = ok;
                h.post(() -> {
                    sg.close();
                    done(fok ? "Wallpaper depth dipasang" : "Wallpaper dipasang (subjek tidak terdeteksi)");
                });
            });
        }).addOnFailureListener(e -> {
            sg.close();
            done("Wallpaper dipasang (model depth belum terunduh)");
        });
    }

    private void done(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        super.onDestroy();
    }
}
