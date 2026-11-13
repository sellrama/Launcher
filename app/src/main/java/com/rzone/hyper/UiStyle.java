package com.rzone.hyper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Gaya panel (tombol, kapsul, dock, menu, folder, drawer).
 *
 * Perbaikan v6: state blur/ukuran layar dulu static dan dipakai bersama oleh launcher,
 * lockscreen dan pratinjau pengaturan, sehingga panel kaca launcher bisa menampilkan
 * wallpaper lockscreen setelah lockscreen muncul. Sekarang setiap layar punya instance sendiri.
 */
final class UiStyle {
    static final String[] NAMES = {"Polos", "Liquid Glass", "Frosted", "Neon", "Outline", "Gradien", "Tonal"};

    Bitmap blur;
    int sw = 1080, sh = 2400;
    final float d;
    private final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();

    UiStyle(Context x) {
        d = x.getResources().getDisplayMetrics().density;
    }

    /** Siapkan blur dari wallpaper. bg boleh null. */
    void init(Bitmap bg, int w, int h) {
        sw = Math.max(1, w);
        sh = Math.max(1, h);
        Bitmap old = blur;
        blur = null;
        if (bg != null && !bg.isRecycled() && bg.getWidth() > 2) {
            try {
                blur = Util.blur(bg);
            } catch (Throwable t) {
                blur = null;
            }
        }
        if (old != null && old != blur && !old.isRecycled()) old.recycle();
    }

    static int shift(int a) {
        float[] hsv = new float[3];
        Color.colorToHSV(a, hsv);
        hsv[0] = (hsv[0] + 60f) % 360f;
        return Color.HSVToColor(hsv);
    }

    /** Campur warna: t=1 → a, t=0 → b. */
    static int mix(int a, int b, float t) {
        return Color.rgb((int) (Color.red(a) * t + Color.red(b) * (1 - t)),
                (int) (Color.green(a) * t + Color.green(b) * (1 - t)),
                (int) (Color.blue(a) * t + Color.blue(b) * (1 - t)));
    }

    static int alpha(int c, int a) {
        return Color.argb(Math.max(0, Math.min(255, a)), Color.red(c), Color.green(c), Color.blue(c));
    }

    /** Warna teks yang terbaca di atas panel gaya ini. */
    static int onPanel(int style, int ac) {
        if (style == 5) return lum(ac) > 0.62f ? 0xFF10131A : Color.WHITE;
        return Color.WHITE;
    }

    static float lum(int c) {
        return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f;
    }

    void draw(Canvas c, RectF r, float rad, int style, int ac, RectF dst, float hx, float hy, float press, float a) {
        if (a <= 0.01f) return;
        int ai = (int) (255 * a);
        path.reset();
        path.addRoundRect(r, rad, rad, Path.Direction.CW);
        if (style == 1 || style == 2) {
            glass(c, r, rad, dst, style == 1, hx, hy, press, a);
            return;
        }
        P.reset();
        P.setAntiAlias(true);
        switch (style) {
            case 3: // Neon
                P.setColor(Color.argb((int) (205 * a), 10, 12, 18));
                c.drawPath(path, P);
                P.setStyle(Paint.Style.STROKE);
                P.setStrokeWidth(d * 2f);
                P.setColor(alpha(ac, ai));
                P.setShadowLayer(d * 10f, 0, 0, alpha(ac, (int) (200 * a)));
                c.drawPath(path, P);
                break;
            case 4: // Outline
                P.setColor(Color.argb((int) (45 * a), 0, 0, 0));
                c.drawPath(path, P);
                P.setStyle(Paint.Style.STROKE);
                P.setStrokeWidth(d * 1.7f);
                P.setColor(Color.argb((int) (225 * a), 255, 255, 255));
                c.drawPath(path, P);
                break;
            case 5: // Gradien
                P.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, ac, shift(ac), Shader.TileMode.CLAMP));
                P.setAlpha((int) (240 * a));
                c.drawPath(path, P);
                break;
            case 6: { // Tonal (Material You)
                int base = mix(ac, 0xFF1B1D24, 0.24f + press * 0.1f);
                P.setColor(alpha(base, (int) (245 * a)));
                c.drawPath(path, P);
                break;
            }
            default: { // Polos
                int k = (int) (press * 25);
                P.setColor(Color.argb((int) (238 * a), 43 + k, 45 + k, 49 + k));
                c.drawPath(path, P);
            }
        }
    }

    private void glass(Canvas c, RectF r, float rad, RectF dst, boolean lens, float hx, float hy, float press, float a) {
        int ai = (int) (255 * a);
        P.reset();
        P.setAntiAlias(true);
        P.setFilterBitmap(true);
        if (lens) {
            P.setColor(Color.argb((int) (40 * a), 0, 0, 0));
            P.setShadowLayer(r.height() * 0.28f, 0, r.height() * 0.1f, Color.argb((int) (a * 80), 0, 0, 0));
            c.drawRoundRect(r, rad, rad, P);
            P.clearShadowLayer();
        }
        c.save();
        c.clipPath(path);
        if (blur == null || dst == null || blur.isRecycled()) {
            P.setColor(Color.argb((int) (165 * a), 40, 42, 48));
            c.drawRect(r, P);
        } else {
            float k = lens ? 1.07f + 0.07f * press : 1f;
            c.save();
            c.scale(k, k, r.centerX(), r.centerY());
            P.setColor(Color.WHITE);
            P.setAlpha(ai);
            c.drawBitmap(blur, null, dst, P);
            c.restore();
        }
        P.reset();
        P.setAntiAlias(true);
        // Frosted: lapisan gelap tipis agar teks putih tetap terbaca di wallpaper terang (gaya iOS).
        if (!lens) {
            P.setColor(Color.argb((int) ((70 + 30 * press) * a), 30, 32, 38));
            c.drawRect(r, P);
            P.setColor(Color.argb((int) ((38 + 30 * press) * a), 255, 255, 255));
        } else {
            P.setColor(Color.argb((int) ((44 + 46 * press) * a), 255, 255, 255));
        }
        c.drawRect(r, P);
        if (lens) {
            P.setShader(new LinearGradient(r.left, r.top, r.left, r.bottom,
                    Color.argb((int) (80 * a), 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
            c.drawRect(r, P);
            float big = Math.max(r.width(), r.height());
            P.setShader(new RadialGradient(r.left + r.width() * (0.26f + 0.12f * hx), r.top + r.height() * (0.16f + 0.1f * hy),
                    big * 0.6f, Color.argb((int) ((120 + 60 * press) * a), 255, 255, 255), Color.argb(0, 255, 255, 255),
                    Shader.TileMode.CLAMP));
            c.drawRect(r, P);
        }
        c.restore();
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth((lens ? 1.5f : 1f) * d);
        P.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                new int[]{Color.argb((int) ((lens ? 235 : 110) * a), 255, 255, 255), Color.argb((int) (28 * a), 255, 255, 255),
                        Color.argb((int) ((lens ? 150 : 70) * a), 255, 255, 255)}, new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
        c.drawPath(path, P);
    }

    /** Drawable latar panel yang mengikuti posisi view (agar blur pas dengan wallpaper di belakangnya). */
    Drawable panel(final View host, final float radPx, final int style, final int ac) {
        return new Drawable() {
            final RectF r = new RectF();
            final RectF dst = new RectF();
            final int[] loc = new int[2];

            @Override
            public void draw(Canvas c) {
                Rect b = getBounds();
                r.set(b);
                host.getLocationInWindow(loc);
                dst.set(-loc[0], -loc[1], -loc[0] + sw, -loc[1] + sh);
                UiStyle.this.draw(c, r, Math.min(radPx, Math.min(r.width(), r.height()) / 2f), style, ac, dst, 0, 0, 0, 1);
            }

            @Override
            public void setAlpha(int a) {
            }

            @Override
            public void setColorFilter(ColorFilter f) {
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
    }
}
