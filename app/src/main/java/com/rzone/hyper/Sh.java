package com.rzone.hyper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;

/** Bayangan seret: hanya ikon, sedikit diperbesar. */
final class Sh extends View.DragShadowBuilder {
    private final Bitmap b;
    private final int sz;

    Sh(View v, Bitmap b, int sz) {
        super(v);
        this.b = b;
        this.sz = sz;
    }

    @Override
    public void onProvideShadowMetrics(Point size, Point touch) {
        size.set(sz, sz);
        touch.set(sz / 2, sz / 2);
    }

    @Override
    public void onDrawShadow(Canvas c) {
        if (b != null && !b.isRecycled()) c.drawBitmap(b, null, new Rect(0, 0, sz, sz), new Paint(Paint.FILTER_BITMAP_FLAG));
    }
}
