package com.rzone.hyper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;

/**
 * Bar pencarian layar utama.
 * Gaya 0: pill Google (PixelOS), 1: kapsul "Cari" kecil (iOS), 2: minimal.
 */
final class SearchBar extends View {
    private final float d;
    private final RectF r = new RectF(), dst = new RectF();
    private final int[] loc = new int[2];
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG), g = new Paint(Paint.ANTI_ALIAS_FLAG),
            s = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int ui, ac, kind;
    private final UiStyle us;

    SearchBar(Context c, UiStyle us, int ui, int ac, int kind) {
        super(c);
        this.us = us;
        this.ui = ui;
        this.ac = ac;
        this.kind = kind;
        d = c.getResources().getDisplayMetrics().density;
        t.setTextSize(d * 16);
        t.setColor(Color.rgb(222, 224, 230));
        g.setTextAlign(Paint.Align.CENTER);
        g.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeWidth(d * 2);
        s.setStrokeCap(Paint.Cap.ROUND);
        s.setColor(Color.rgb(225, 228, 234));
        setContentDescription("Cari");
    }

    float lastX = -1;

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) lastX = e.getX();
        return super.onTouchEvent(e);
    }

    /** Ketukan terakhir mengenai ikon mikrofon (pill Google)? */
    boolean tappedMic() {
        float h = getHeight(), w = getWidth();
        return kind == 0 && lastX > w - h * 2.1f && lastX < w - h * 1.0f;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), gy = h / 2f;
        r.set(0, 0, w, h);
        getLocationInWindow(loc);
        dst.set(-loc[0], -loc[1], -loc[0] + us.sw, -loc[1] + us.sh);
        if (kind == 2) { // minimal
            s.setColor(Color.argb(200, 255, 255, 255));
            s.setStrokeWidth(d * 1.4f);
            c.drawRoundRect(r.left + d, r.top + d, r.right - d, r.bottom - d, h / 2, h / 2, s);
            s.setStrokeWidth(d * 2);
            s.setColor(Color.WHITE);
            Glyph.draw(c, Glyph.SEARCH, h * 0.55f, gy, d * 8, s, d, new RectF());
            t.setColor(Color.argb(220, 255, 255, 255));
            c.drawText("Cari aplikasi", h * 0.95f, gy + d * 5.6f, t);
            return;
        }
        us.draw(c, r, h / 2f, ui, ac, dst, 0, 0, 0, 1);
        int on = UiStyle.onPanel(ui, ac);
        if (kind == 1) { // iOS: kaca kecil dengan kaca pembesar + "Cari"
            t.setColor(on);
            t.setTextSize(d * 14);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            float tw = t.measureText("Cari");
            float gw = d * 16;
            float x0 = (w - (gw + d * 6 + tw)) / 2f;
            s.setColor(on);
            s.setStrokeWidth(d * 1.8f);
            Glyph.draw(c, Glyph.SEARCH, x0 + gw / 2f, gy, d * 7, s, d, new RectF());
            c.drawText("Cari", x0 + gw + d * 6, gy + d * 5, t);
            return;
        }
        // PixelOS: logo G berwarna, teks, mic & lens
        float gx = h * 0.5f;
        g.setTextSize(h * 0.5f);
        g.setShader(new SweepGradient(gx, gy, new int[]{Color.parseColor("#4285F4"), Color.parseColor("#34A853"),
                Color.parseColor("#FBBC05"), Color.parseColor("#EA4335"), Color.parseColor("#4285F4")},
                new float[]{0, 0.3f, 0.5f, 0.8f, 1}));
        c.drawText("G", gx, gy + g.getTextSize() * 0.35f, g);
        t.setColor(on == Color.WHITE ? Color.rgb(222, 224, 230) : on);
        c.drawText("Telusuri", h * 0.95f, gy + d * 5.6f, t);
        s.setColor(on == Color.WHITE ? Color.rgb(225, 228, 234) : on);
        float mx = w - h * 1.5f;
        r.set(mx - d * 4, gy - d * 10, mx + d * 4, gy + d * 3);
        c.drawRoundRect(r, d * 4, d * 4, s);
        r.set(mx - d * 8, gy - d * 6, mx + d * 8, gy + d * 8);
        c.drawArc(r, 10, 160, false, s);
        c.drawLine(mx, gy + d * 8, mx, gy + d * 12, s);
        float lx = w - h * 0.62f;
        r.set(lx - d * 10, gy - d * 10, lx + d * 10, gy + d * 10);
        c.drawRoundRect(r, d * 5, d * 5, s);
        c.drawCircle(lx, gy, d * 3.6f, s);
    }
}
