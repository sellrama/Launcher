package com.rzone.hyper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** Satu ikon (aplikasi atau folder) di layar utama, dock, drawer, folder. */
public class AppCell extends View {
    static float SCALE = 1f;
    static float LABEL_SP = 12f;
    /** 0 mati, 1 titik, 2 angka */
    static int BADGE = 1;
    static int BADGE_COLOR = 0xFFFF453A;
    /** Bentuk latar pratinjau folder (radius relatif). */
    static float FOLDER_RAD = 0.32f;
    /** Latar folder dari tema .zth (null = latar kaca bawaan). */
    static Bitmap FOLDER_BG;

    interface L {
        void click(AppCell c);

        void drag(AppCell c);

        void longPress(AppCell c);
    }

    // sumber: 0 halaman, 1 dock, 2 drawer, 3 folder, 4 saran
    static final int SRC_PAGE = 0, SRC_DOCK = 1, SRC_DRAWER = 2, SRC_FOLDER = 3, SRC_RECENT = 4;

    Item item;
    L l;
    boolean labels = true;
    int page, slot, src;
    private final float d;
    private boolean down, lp, moved;
    private float x0, y0;
    private String shown;
    private final Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF(), r2 = new RectF();

    private final Runnable lpr = () -> {
        if (!down || moved) return;
        lp = true;
        // jangan biarkan GridView/launcher merebut sentuhan saat ikon akan diseret
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (l != null) l.longPress(this);
    };

    public AppCell(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        tp.setColor(Color.WHITE);
        tp.setTextSize(LABEL_SP * d);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        tp.setShadowLayer(d * 3, 0, d, Color.argb(150, 0, 0, 0));
        setFocusable(true);
    }

    void bind(Item it) {
        item = it;
        shown = null;
        setContentDescription(it == null ? null : it.label);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        shown = null;
    }

    /** Kotak ikon (tanpa label) dalam koordinat view ini. */
    void iconRect(RectF out) {
        float s = d * 56 * SCALE;
        float avail = getHeight() - (labels ? d * 22 : 0) - d * 4;
        s = Math.max(d * 24, Math.min(s, Math.min(getWidth() - d * 4, avail)));
        float cx = getWidth() / 2f;
        float top = (getHeight() - (labels ? s + d * 22 : s)) / 2f;
        out.set(cx - s / 2f, top, cx + s / 2f, top + s);
    }

    /** Kotak ikon dalam koordinat jendela. */
    RectF iconOnWindow() {
        RectF r = new RectF();
        iconRect(r);
        int[] l = new int[2];
        getLocationInWindow(l);
        r.offset(l[0], l[1]);
        // perhitungkan skala animasi tekan
        float cx = r.centerX(), cy = r.centerY(), k = getScaleX();
        if (k > 0 && k != 1) r.set(cx - r.width() / 2 / k, cy - r.height() / 2 / k, cx + r.width() / 2 / k, cy + r.height() / 2 / k);
        return r;
    }

    private static boolean ok(Bitmap b) {
        return b != null && !b.isRecycled();
    }

    private int badgeCount() {
        if (BADGE == 0 || item == null) return 0;
        if (item.kids == null) return NotifListener.count(item.pkg);
        int n = 0;
        for (Item k : item.kids) n += NotifListener.count(k.pkg);
        return n;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (item == null) return;
        float s = d * 56 * SCALE;
        // ikon tidak boleh lebih besar dari sel (dulu bisa terpotong pada grid rapat / ukuran 130%)
        float avail = getHeight() - (labels ? d * 22 : 0) - d * 4;
        s = Math.max(d * 24, Math.min(s, Math.min(getWidth() - d * 4, avail)));
        float cx = getWidth() / 2f;
        float top = (getHeight() - (labels ? s + d * 22 : s)) / 2f;
        r.set(cx - s / 2f, top, cx + s / 2f, top + s);
        if (item.kids != null) {
            if (ok(FOLDER_BG)) {
                c.drawBitmap(FOLDER_BG, null, r, ip);
            } else {
                fp.setColor(Color.argb(95, 255, 255, 255));
                c.drawRoundRect(r, s * FOLDER_RAD, s * FOLDER_RAD, fp);
            }
            float m = 0.3f * s, g = 0.08f * s;
            float sx = cx - m - g / 2f, sy = top + (s - m * 2 - g) / 2f;
            for (int i = 0; i < 4 && i < item.kids.size(); i++) {
                Item k = item.kids.get(i);
                if (!ok(k.icon)) continue;
                float px = sx + (i % 2) * (m + g), py = sy + (i / 2) * (m + g);
                r2.set(px, py, px + m, py + m);
                c.drawBitmap(k.icon, null, r2, ip);
            }
        } else if (ok(item.icon)) {
            c.drawBitmap(item.icon, null, r, ip);
        }
        int n = badgeCount();
        if (n > 0) {
            fp.setColor(BADGE_COLOR);
            fp.setShadowLayer(d * 2, 0, d * 0.5f, Color.argb(90, 0, 0, 0));
            if (BADGE == 1) {
                c.drawCircle(r.right - s * 0.12f, r.top + s * 0.12f, d * 5.5f, fp);
            } else {
                String t = n > 99 ? "99+" : String.valueOf(n);
                tp.setTextSize(d * 11);
                float bw = Math.max(d * 20, tp.measureText(t) + d * 10), bh = d * 20;
                r2.set(r.right - bw * 0.7f, r.top - bh * 0.3f, r.right + bw * 0.3f, r.top + bh * 0.7f);
                c.drawRoundRect(r2, bh / 2, bh / 2, fp);
                tp.clearShadowLayer();
                c.drawText(t, r2.centerX(), r2.centerY() + d * 4, tp);
                tp.setShadowLayer(d * 3, 0, d, Color.argb(150, 0, 0, 0));
                tp.setTextSize(LABEL_SP * d);
            }
            fp.clearShadowLayer();
        }
        if (labels) {
            if (shown == null) {
                tp.setTextSize(LABEL_SP * d);
                shown = TextUtils.ellipsize(item.label, tp, getWidth() - d * 6, TextUtils.TruncateAt.END).toString();
            }
            c.drawText(shown, cx, top + s + d * 17, tp);
        }
    }

    private void release() {
        animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(380).setInterpolator(new OvershootInterpolator(3f)).start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                down = true;
                lp = false;
                moved = false;
                x0 = e.getRawX();
                y0 = e.getRawY();
                animate().scaleX(0.9f).scaleY(0.9f).setStartDelay(0).setDuration(90).setInterpolator(null).start();
                postDelayed(lpr, 380);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dist = (float) Math.hypot(e.getRawX() - x0, e.getRawY() - y0);
                if (lp) {
                    if (dist > d * 10 && down && l != null) {
                        down = false;
                        l.drag(this);
                    }
                } else if (dist > d * 8) {
                    moved = true;
                    removeCallbacks(lpr);
                    release();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                removeCallbacks(lpr);
                boolean fire = down && !lp && !moved;
                down = false;
                release();
                if (fire && l != null) {
                    playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    l.click(this);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(lpr);
                down = false;
                release();
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        if (l != null) l.click(this);
        return true;
    }
}
