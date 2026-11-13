package com.rzone.hyper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

/** Pembuat ikon: bentuk (mask) + gaya. */
final class Icons {
    static final String[] SHAPES = {"Lingkaran", "Squircle", "Persegi", "Tetesan", "Heksagon", "Clover"};
    static final String[] STYLES = {"Asli", "Gradien", "Berwarna", "Gelap", "Bertema", "Kaca", "Neon", "Glossy", "Siluet"};

    private Icons() {
    }

    static Path shape(RectF b, int s) {
        Path p = new Path();
        float cx = b.centerX(), cy = b.centerY(), w = b.width();
        switch (s) {
            case 0:
                p.addCircle(cx, cy, w / 2f, Path.Direction.CW);
                return p;
            case 1: { // superellipse n=4 (squircle ala iOS)
                int n = 120;
                for (int i = 0; i <= n; i++) {
                    double t = i * 2 * Math.PI / n;
                    double ct = Math.cos(t), st = Math.sin(t);
                    float x = (float) (cx + w / 2f * Math.signum(ct) * Math.pow(Math.abs(ct), 0.5));
                    float y = (float) (cy + w / 2f * Math.signum(st) * Math.pow(Math.abs(st), 0.5));
                    if (i == 0) p.moveTo(x, y);
                    else p.lineTo(x, y);
                }
                p.close();
                return p;
            }
            case 2:
                p.addRoundRect(b, w * 0.26f, w * 0.26f, Path.Direction.CW);
                return p;
            case 3: {
                float r = w / 2f;
                p.addRoundRect(b, new float[]{r, r, r, r, r, r, r * 0.22f, r * 0.22f}, Path.Direction.CW);
                return p;
            }
            case 4:
                for (int i = 0; i < 6; i++) {
                    double a = Math.toRadians(i * 60 - 90);
                    float x = cx + (float) Math.cos(a) * w / 2f;
                    float y = cy + (float) Math.sin(a) * w / 2f;
                    if (i == 0) p.moveTo(x, y);
                    else p.lineTo(x, y);
                }
                p.close();
                return p;
            default: {
                float r = 0.29f * w, o = 0.21f * w;
                Path u = new Path();
                u.addCircle(cx - o, cy - o, r, Path.Direction.CW);
                float[][] cs = {{cx + o, cy - o}, {cx - o, cy + o}, {cx + o, cy + o}};
                for (float[] q : cs) {
                    Path t = new Path();
                    t.addCircle(q[0], q[1], r, Path.Direction.CW);
                    u.op(t, Path.Op.UNION);
                }
                Path q = new Path();
                q.addRect(cx - o, cy - o, cx + o, cy + o, Path.Direction.CW);
                u.op(q, Path.Op.UNION);
                return u;
            }
        }
    }

    static int dominant(Drawable dr) {
        Bitmap t = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        try {
            dr.setBounds(0, 0, 24, 24);
            dr.draw(new Canvas(t));
        } catch (Exception e) {
            t.recycle();
            return 0xFF60708B;
        }
        int[] px = new int[576];
        t.getPixels(px, 0, 24, 0, 0, 24, 24);
        t.recycle();
        float r = 0, g = 0, b = 0, ws = 0;
        float[] hsv = new float[3];
        for (int p : px) {
            if (Color.alpha(p) < 128) continue;
            Color.colorToHSV(p, hsv);
            float w = hsv[1] * hsv[2] + 0.15f;
            r += Color.red(p) * w;
            g += Color.green(p) * w;
            b += Color.blue(p) * w;
            ws += w;
        }
        if (ws == 0) return 0xFF60708B;
        return Color.rgb((int) (r / ws), (int) (g / ws), (int) (b / ws));
    }

    /**
     * Warna paling menonjol (bukan rata-rata): histogram hue dari piksel jenuh.
     * Ikon multi-warna (mis. Google) tidak lagi menjadi cokelat keruh.
     */
    static int vivid(Drawable dr) {
        Bitmap t = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        try {
            dr.setBounds(0, 0, 32, 32);
            dr.draw(new Canvas(t));
        } catch (Exception e) {
            t.recycle();
            return 0xFF3A4150;
        }
        int[] px = new int[1024];
        t.getPixels(px, 0, 32, 0, 0, 32, 32);
        t.recycle();
        float[] w = new float[12], r = new float[12], g = new float[12], b = new float[12];
        float[] hsv = new float[3];
        for (int p : px) {
            if (Color.alpha(p) < 160) continue;
            Color.colorToHSV(p, hsv);
            if (hsv[1] < 0.28f || hsv[2] < 0.25f) continue;
            int bin = (int) (hsv[0] / 30f) % 12;
            float k = hsv[1] * hsv[2];
            w[bin] += k;
            r[bin] += Color.red(p) * k;
            g[bin] += Color.green(p) * k;
            b[bin] += Color.blue(p) * k;
        }
        int best = -1;
        for (int i = 0; i < 12; i++) if (w[i] > 0 && (best < 0 || w[i] > w[best])) best = i;
        if (best < 0 || w[best] < 4) return 0xFF3A4150; // ikon abu-abu/hitam-putih → batu tulis gelap
        return Color.rgb((int) (r[best] / w[best]), (int) (g[best] / w[best]), (int) (b[best] / w[best]));
    }

    /** Apakah lapisan latar ikon adaptif berwarna putih/terang pucat? */
    static boolean whiteish(Drawable bg) {
        if (bg == null) return false;
        Bitmap t = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
        try {
            bg.setBounds(0, 0, 12, 12);
            bg.draw(new Canvas(t));
        } catch (Exception e) {
            t.recycle();
            return false;
        }
        int[] px = new int[144];
        t.getPixels(px, 0, 12, 0, 0, 12, 12);
        t.recycle();
        int light = 0;
        float[] hsv = new float[3];
        for (int p : px) {
            Color.colorToHSV(p, hsv);
            if (Color.alpha(p) > 200 && hsv[2] > 0.86f && hsv[1] < 0.16f) light++;
        }
        return light > 100;
    }

    private static int shade(int c, float f) {
        return f >= 0 ? UiStyle.mix(Color.WHITE, c, f) : UiStyle.mix(Color.BLACK, c, -f);
    }

    private static void drawD(Canvas c, Drawable d, Rect r, int tint) {
        if (d == null) return;
        try {
            d.setColorFilter(tint != 0 ? new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN) : null);
            d.setBounds(r);
            d.draw(c);
            d.setColorFilter(null);
        } catch (Exception ignored) {
        }
    }

    private static void fill(Canvas c, RectF b, int col) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(col);
        c.drawRect(b, p);
    }

    private static void fillG(Canvas c, RectF b, int c1, int c2) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, b.top, 0, b.bottom, c1, c2, Shader.TileMode.CLAMP));
        c.drawRect(b, p);
    }

    static Bitmap fallback(Context x, int sz) {
        sz = Math.max(8, sz);
        Bitmap b = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.argb(200, 60, 62, 68));
        c.drawRoundRect(new RectF(0, 0, sz, sz), sz * 0.24f, sz * 0.24f, p);
        p.setColor(Color.WHITE);
        p.setTextSize(sz * 0.5f);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("?", sz / 2f, sz * 0.66f, p);
        return b;
    }

    /** Gambar ikon jadi (bentuk + gaya) dari drawable aplikasi. */
    static Bitmap make(Context x, Drawable dr, int sz, int shape, int style, int accent) {
        if (dr == null) return fallback(x, sz);
        Bitmap out = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float in = sz * 0.07f;
        RectF box = new RectF(in, in, sz - in, sz - in);
        Path path = shape(box, shape);
        float w = box.width();
        PathEffect fx = shape == 4 ? new CornerPathEffect(sz * 0.07f) : null;
        boolean ad = dr instanceof AdaptiveIconDrawable;
        Drawable fg = dr, bgD = null, mono = null;
        Rect rect = new Rect((int) (box.left + w * 0.17f), (int) (box.top + w * 0.17f),
                (int) (box.right - w * 0.17f), (int) (box.bottom - w * 0.17f));
        if (ad) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) dr;
            fg = a.getForeground();
            bgD = a.getBackground();
            if (Build.VERSION.SDK_INT >= 33) mono = a.getMonochrome();
            rect = new Rect((int) (box.left - w * 0.25f), (int) (box.top - w * 0.25f),
                    (int) (box.right + w * 0.25f), (int) (box.bottom + w * 0.25f));
        }
        // Gaya "Asli": ikon asli aplikasi + dibentuk, TANPA plat/latar tambahan dan tanpa putih.
        //  - adaptif berlatar warna  → latar + logo, dibentuk mask
        //  - adaptif berlatar putih  → latar putih dibuang, hanya logo asli (tanpa plat)
        //  - ikon lama (non-adaptif) → apa adanya, tanpa plat
        if (style == 0 && (!ad || whiteish(bgD))) {
            if (ad) {
                // logo dari lapisan depan, sedikit diperbesar karena tanpa latar
                float g = w * 0.42f;
                drawD(c, fg, new Rect((int) (box.left - g), (int) (box.top - g), (int) (box.right + g), (int) (box.bottom + g)), 0);
            } else {
                drawD(c, dr, new Rect((int) box.left, (int) box.top, (int) box.right, (int) box.bottom), 0);
            }
            return out;
        }
        Bitmap layer = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas lc = new Canvas(layer);
        int dom = style != 3 && style != 5 && style != 6 ? vivid(dr) : 0;
        switch (style) {
            case 1:
                fillG(lc, box, shade(dom, 0.18f), shade(dom, -0.28f));
                drawD(lc, fg, rect, 0);
                break;
            case 2: // Berwarna: plat warna utama ikon, jenuh
                fillG(lc, box, shade(dom, 0.06f), shade(dom, -0.18f));
                drawD(lc, fg, rect, 0);
                break;
            case 3:
                fill(lc, box, 0xFF1E2023);
                drawD(lc, fg, rect, 0);
                break;
            case 4: { // Bertema (Material You)
                // Material You gelap (tanpa plat putih)
                int bgc = UiStyle.mix(accent, 0xFF161A22, 0.28f);
                int fgc = UiStyle.mix(accent, Color.WHITE, 0.5f);
                fill(lc, box, bgc);
                drawD(lc, mono != null ? mono : fg, rect, fgc);
                break;
            }
            case 5:
                fillG(lc, box, UiStyle.alpha(dom == 0 ? 0xFF8899AA : 0xFF8899AA, 70), Color.argb(26, 255, 255, 255));
                drawD(lc, fg, rect, 0);
                break;
            case 6:
                fill(lc, box, 0xFF0E0F18);
                drawD(lc, fg, rect, 0);
                break;
            case 7: {
                fillG(lc, box, shade(dom, 0.3f), shade(dom, -0.25f));
                drawD(lc, fg, rect, 0);
                Paint gl = new Paint(Paint.ANTI_ALIAS_FLAG);
                gl.setShader(new LinearGradient(0, box.top, 0, box.centerY(), Color.argb(130, 255, 255, 255),
                        Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
                lc.drawOval(new RectF(box.left - w * 0.1f, box.top - w * 0.35f, box.right + w * 0.1f, box.centerY()), gl);
                break;
            }
            case 8:
                fillG(lc, box, shade(dom, -0.5f), shade(dom, -0.75f));
                drawD(lc, mono != null ? mono : fg, rect, Color.WHITE);
                break;
            default: // Asli (adaptif berlatar warna): latar + logo asli
                drawD(lc, bgD, rect, 0);
                drawD(lc, fg, rect, 0);
        }
        // mask bentuk
        Bitmap mask = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG);
        mp.setColor(Color.WHITE);
        mp.setPathEffect(fx);
        new Canvas(mask).drawPath(path, mp);
        Paint dst = new Paint(Paint.ANTI_ALIAS_FLAG);
        dst.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        lc.drawBitmap(mask, 0, 0, dst);
        if (style != 5 && style != 6) {
            Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
            sh.setColor(Color.BLACK);
            sh.setPathEffect(fx);
            sh.setShadowLayer(sz * 0.04f, 0, sz * 0.025f, Color.argb(110, 0, 0, 0));
            c.drawPath(path, sh);
        }
        c.drawBitmap(layer, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setPathEffect(fx);
        if (style == 6) {
            rim.setStrokeWidth(sz * 0.03f);
            rim.setColor(accent);
            rim.setShadowLayer(sz * 0.05f, 0, 0, accent);
        } else if (style == 5) {
            rim.setStrokeWidth(sz * 0.022f);
            rim.setShader(new LinearGradient(0, box.top, box.right, box.bottom, Color.argb(220, 255, 255, 255),
                    Color.argb(40, 255, 255, 255), Shader.TileMode.CLAMP));
        } else {
            rim.setStrokeWidth(sz * 0.012f);
            rim.setColor(Color.argb(30, 255, 255, 255));
        }
        c.drawPath(path, rim);
        mask.recycle();
        layer.recycle();
        return out;
    }
}
