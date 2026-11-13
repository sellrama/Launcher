package com.rzone.hyper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * Animasi transisi aplikasi:
 *  - kembali ke layar utama: "kartu" aplikasi mengecil kembali ke posisi ikonnya;
 *  - membuka kunci: layar utama "mendarat" dengan zoom.
 */
final class Motion {
    static final PathInterpolator EMPH = new PathInterpolator(0.2f, 0, 0, 1);
    static final PathInterpolator EMPH_DECEL = new PathInterpolator(0.05f, 0.7f, 0.1f, 1);

    /** Info peluncuran terakhir (disimpan statis agar bertahan saat activity dibuat ulang). */
    static String lastKey;
    static final RectF lastRect = new RectF();
    static Bitmap lastIcon;
    static long lastAt;
    static boolean pendingUnlock;

    private Motion() {
    }

    static void launched(Item it, RectF iconOnScreen) {
        lastKey = it.key();
        lastRect.set(iconOnScreen);
        lastIcon = it.icon;
        lastAt = System.currentTimeMillis();
    }

    static int colorOf(Bitmap b) {
        if (b == null || b.isRecycled()) return 0xFF2A2D36;
        try {
            int c = Icons.vivid(new BitmapDrawable(null, b));
            // sedikit digelapkan agar terlihat seperti latar aplikasi
            return UiStyle.mix(c, 0xFF101114, 0.85f);
        } catch (Exception e) {
            return 0xFF2A2D36;
        }
    }

    /** Overlay kartu yang mengecil dari layar penuh ke kotak ikon. */
    static final class Shrink extends View {
        final RectF from = new RectF(), to = new RectF(), cur = new RectF(), ic = new RectF();
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), ip = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Bitmap icon;
        final int color;
        final float r0, r1;
        float t;

        Shrink(Context c, RectF from, RectF to, Bitmap icon, float radFrom, float radTo) {
            super(c);
            this.from.set(from);
            this.to.set(to);
            this.icon = icon;
            this.color = colorOf(icon);
            r0 = radFrom;
            r1 = radTo;
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        @Override
        protected void onDraw(Canvas c) {
            // posisi bergerak lebih cepat di awal, ukuran mengecil halus (seperti iOS/Pixel)
            float tp = t, ts = (float) Math.pow(t, 0.85);
            float cx = lerp(from.centerX(), to.centerX(), tp), cy = lerp(from.centerY(), to.centerY(), tp);
            float w = lerp(from.width(), to.width(), ts), h = lerp(from.height(), to.height(), ts);
            cur.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
            float rad = lerp(r0, r1, ts);
            float fade = t < 0.65f ? 1f : 1f - (t - 0.65f) / 0.35f;
            p.setColor(color);
            p.setAlpha((int) (255 * fade));
            p.setShadowLayer(rad * 0.5f * (1 - t) + 1, 0, rad * 0.15f, Color.argb((int) (90 * fade), 0, 0, 0));
            c.drawRoundRect(cur, rad, rad, p);
            if (icon != null && !icon.isRecycled()) {
                float is = Math.min(w, h) * lerp(0.28f, 1f, (float) Math.pow(t, 1.6));
                is = Math.max(is, to.width() * 0.6f);
                ic.set(cx - is / 2, cy - is / 2, cx + is / 2, cy + is / 2);
                ip.setAlpha((int) (255 * Math.min(1f, 0.3f + t * 1.4f)));
                c.drawBitmap(icon, null, ic, ip);
            }
        }

        void run(long dur, Runnable end) {
            ValueAnimator a = ValueAnimator.ofFloat(0, 1);
            a.setDuration(dur);
            a.setInterpolator(EMPH);
            a.addUpdateListener(v -> {
                t = (Float) v.getAnimatedValue();
                invalidate();
            });
            a.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator x) {
                    if (end != null) end.run();
                }
            });
            a.start();
        }
    }
}
