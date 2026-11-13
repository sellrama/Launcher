package com.rzone.hyper;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import java.io.File;

final class Util {
    private Util() {
    }

    /**
     * Blur cepat: kecilkan, box-blur beberapa kali, lalu biarkan GPU memperbesar dengan filter.
     * Versi lama hanya memperkecil lalu memperbesar sehingga hasilnya kotak-kotak.
     */
    static Bitmap blur(Bitmap bg) {
        int w = Math.max(8, bg.getWidth() / 10);
        int h = Math.max(8, bg.getHeight() / 10);
        Bitmap s = Bitmap.createScaledBitmap(bg, w, h, true);
        if (s == bg || !s.isMutable()) {
            Bitmap c = s.copy(Bitmap.Config.ARGB_8888, true);
            if (s != bg) s.recycle();
            s = c;
        }
        int[] px = new int[w * h];
        s.getPixels(px, 0, w, 0, 0, w, h);
        int[] tmp = new int[w * h];
        for (int pass = 0; pass < 3; pass++) {
            boxH(px, tmp, w, h, 2);
            boxV(tmp, px, w, h, 2);
        }
        s.setPixels(px, 0, w, 0, 0, w, h);
        return s;
    }

    private static void boxH(int[] in, int[] out, int w, int h, int r) {
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int a = 0, rr = 0, g = 0, b = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int xx = Math.max(0, Math.min(w - 1, x + k));
                    int c = in[row + xx];
                    a += c >>> 24;
                    rr += (c >> 16) & 255;
                    g += (c >> 8) & 255;
                    b += c & 255;
                    n++;
                }
                out[row + x] = ((a / n) << 24) | ((rr / n) << 16) | ((g / n) << 8) | (b / n);
            }
        }
    }

    private static void boxV(int[] in, int[] out, int w, int h, int r) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int a = 0, rr = 0, g = 0, b = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int yy = Math.max(0, Math.min(h - 1, y + k));
                    int c = in[yy * w + x];
                    a += c >>> 24;
                    rr += (c >> 16) & 255;
                    g += (c >> 8) & 255;
                    b += c & 255;
                    n++;
                }
                out[y * w + x] = ((a / n) << 24) | ((rr / n) << 16) | ((g / n) << 8) | (b / n);
            }
        }
    }

    /** Efek tekan (memantul) untuk tombol & kartu. */
    static void press(View v) {
        v.setOnTouchListener((x, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                x.animate().scaleX(0.96f).scaleY(0.96f).setDuration(110).setInterpolator(new DecelerateInterpolator()).start();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                x.animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(new OvershootInterpolator(2.5f)).start();
            }
            return false;
        });
    }

    /** Decode file gambar dengan sampling agar tidak kehabisan memori. */
    static Bitmap decodeFile(File f, int reqW, int reqH) {
        if (f == null || !f.exists()) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int s = 1;
            while (o.outWidth / (s * 2) >= reqW && o.outHeight / (s * 2) >= reqH) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            return BitmapFactory.decodeFile(f.getPath(), o2);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Potong tengah (center-crop) ke ukuran w×h. Sumber didaur ulang bila recycleSrc. */
    static Bitmap crop(Bitmap src, int w, int h, boolean recycleSrc) {
        w = Math.max(2, w);
        h = Math.max(2, h);
        // Perbaikan: v5.1 memakai pembagian integer di sini sehingga k = 0 untuk foto yang
        // lebih besar dari layar dan wallpaper sendiri tampil hitam/kosong.
        float k = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        cv.translate((w - src.getWidth() * k) / 2f, (h - src.getHeight() * k) / 2f);
        cv.scale(k, k);
        cv.drawBitmap(src, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
        if (recycleSrc && src != out) src.recycle();
        return out;
    }

    static boolean isDefaultLauncher(Context c) {
        try {
            ResolveInfo ri = c.getPackageManager().resolveActivity(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null && c.getPackageName().equals(ri.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    /** Cari di web; bila tidak ada aplikasi pencarian, buka peramban. */
    static boolean webSearch(Context c, String q) {
        Intent i = new Intent(Intent.ACTION_WEB_SEARCH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (q != null && !q.isEmpty()) i.putExtra(SearchManager.QUERY, q);
        try {
            c.startActivity(i);
            return true;
        } catch (Exception ignored) {
        }
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q="
                    + Uri.encode(q == null ? "" : q))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean start(Context c, Intent i) {
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean hasComponent(Context c, ComponentName cn) {
        try {
            c.getPackageManager().getActivityInfo(cn, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }
}
