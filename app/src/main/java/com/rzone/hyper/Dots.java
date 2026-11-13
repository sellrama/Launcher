package com.rzone.hyper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Indikator halaman. */
final class Dots extends View {
    private final float d;
    private int n = 1;
    private float pos;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    Dots(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
    }

    void set(int n, float pos) {
        this.n = Math.max(1, n);
        this.pos = pos;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (n <= 1) return;
        float gap = d * 14;
        float x0 = getWidth() / 2f - (n - 1) * gap / 2f;
        float cy = getHeight() / 2f;
        for (int i = 0; i < n; i++) {
            float dist = Math.min(1f, Math.abs(i - pos));
            p.setColor(Color.argb((int) ((1f - 0.55f * dist) * 255), 255, 255, 255));
            p.setShadowLayer(d * 2, 0, d * 0.5f, Color.argb(70, 0, 0, 0));
            c.drawCircle(x0 + i * gap, cy, (3f + (1 - dist) * 1.6f) * d, p);
        }
    }
}
