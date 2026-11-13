package com.rzone.hyper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Ikon garis sederhana untuk menu. */
final class Glyph extends View {
    static final int INFO = 0, TRASH = 1, IMAGE = 2, GEAR = 3, SEARCH = 4, FLASH = 5, STAR = 6, EYE_OFF = 7,
            PENCIL = 8, LOCK = 9, GRID = 10, PLUS = 11;
    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int t;
    private final RectF r = new RectF();

    Glyph(Context c, int type) {
        this(c, type, 0xFFFFFFFF);
    }

    Glyph(Context c, int type, int color) {
        super(c);
        t = type;
        d = c.getResources().getDisplayMetrics().density;
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(d * 2);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    void color(int c) {
        p.setColor(c);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        draw(c, t, getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) * 0.4f, p, d, r);
    }

    static void draw(Canvas c, int t, float cx, float cy, float rr, Paint p, float d, RectF r) {
        Paint.Style st = p.getStyle();
        p.setStyle(Paint.Style.STROKE);
        switch (t) {
            case INFO:
                c.drawCircle(cx, cy, rr, p);
                c.drawLine(cx, cy - 0.05f * rr, cx, cy + 0.5f * rr, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy - 0.42f * rr, d * 1.4f, p);
                break;
            case TRASH:
                c.drawLine(cx - rr * 0.8f, cy - rr * 0.5f, cx + rr * 0.8f, cy - rr * 0.5f, p);
                c.drawLine(cx - rr * 0.25f, cy - rr * 0.8f, cx + rr * 0.25f, cy - rr * 0.8f, p);
                r.set(cx - rr * 0.6f, cy - rr * 0.4f, cx + rr * 0.6f, cy + rr * 0.85f);
                c.drawRoundRect(r, d * 2, d * 2, p);
                break;
            case IMAGE: {
                r.set(cx - rr, cy - rr * 0.8f, cx + rr, cy + rr * 0.8f);
                c.drawRoundRect(r, d * 3, d * 3, p);
                Path m = new Path();
                m.moveTo(cx - rr * 0.8f, cy + rr * 0.6f);
                m.lineTo(cx - rr * 0.15f, cy - rr * 0.1f);
                m.lineTo(cx + rr * 0.25f, cy + rr * 0.35f);
                m.lineTo(cx + rr * 0.45f, cy + rr * 0.1f);
                m.lineTo(cx + rr * 0.8f, cy + rr * 0.6f);
                c.drawPath(m, p);
                break;
            }
            case GEAR:
                c.drawCircle(cx, cy, rr * 0.42f, p);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    c.drawLine(cx + (float) Math.cos(a) * rr * 0.7f, cy + (float) Math.sin(a) * rr * 0.7f,
                            cx + (float) Math.cos(a) * rr, cy + (float) Math.sin(a) * rr, p);
                }
                break;
            case FLASH: {
                Path f = new Path();
                f.moveTo(cx - rr * 0.45f, cy - rr * 0.9f);
                f.lineTo(cx + rr * 0.45f, cy - rr * 0.9f);
                f.lineTo(cx + rr * 0.45f, cy - rr * 0.45f);
                f.lineTo(cx + rr * 0.22f, cy - rr * 0.1f);
                f.lineTo(cx + rr * 0.22f, cy + rr * 0.9f);
                f.lineTo(cx - rr * 0.22f, cy + rr * 0.9f);
                f.lineTo(cx - rr * 0.22f, cy - rr * 0.1f);
                f.lineTo(cx - rr * 0.45f, cy - rr * 0.45f);
                f.close();
                c.drawPath(f, p);
                c.drawLine(cx, cy + rr * 0.12f, cx, cy + rr * 0.38f, p);
                break;
            }
            case STAR: {
                Path s = new Path();
                for (int i = 0; i < 10; i++) {
                    double a = Math.toRadians(-90 + i * 36);
                    float rad = i % 2 == 0 ? rr : rr * 0.45f;
                    float x = cx + (float) Math.cos(a) * rad, y = cy + (float) Math.sin(a) * rad;
                    if (i == 0) s.moveTo(x, y);
                    else s.lineTo(x, y);
                }
                s.close();
                c.drawPath(s, p);
                break;
            }
            case EYE_OFF: {
                r.set(cx - rr, cy - rr * 0.55f, cx + rr, cy + rr * 0.55f);
                c.drawOval(r, p);
                c.drawCircle(cx, cy, rr * 0.25f, p);
                c.drawLine(cx - rr * 0.85f, cy + rr * 0.85f, cx + rr * 0.85f, cy - rr * 0.85f, p);
                break;
            }
            case PENCIL: {
                Path s = new Path();
                s.moveTo(cx - rr * 0.8f, cy + rr * 0.8f);
                s.lineTo(cx - rr * 0.6f, cy + rr * 0.2f);
                s.lineTo(cx + rr * 0.45f, cy - rr * 0.85f);
                s.lineTo(cx + rr * 0.85f, cy - rr * 0.45f);
                s.lineTo(cx - rr * 0.2f, cy + rr * 0.6f);
                s.close();
                c.drawPath(s, p);
                break;
            }
            case LOCK:
                r.set(cx - rr * 0.65f, cy - rr * 0.1f, cx + rr * 0.65f, cy + rr * 0.85f);
                c.drawRoundRect(r, d * 2.5f, d * 2.5f, p);
                r.set(cx - rr * 0.42f, cy - rr * 0.8f, cx + rr * 0.42f, cy + rr * 0.05f);
                c.drawArc(r, 180, 180, false, p);
                c.drawLine(cx - rr * 0.42f, cy - rr * 0.38f, cx - rr * 0.42f, cy - rr * 0.1f, p);
                c.drawLine(cx + rr * 0.42f, cy - rr * 0.38f, cx + rr * 0.42f, cy - rr * 0.1f, p);
                break;
            case GRID:
                for (int i = 0; i < 4; i++) {
                    float ox = cx + (i % 2 == 0 ? -rr * 0.85f : rr * 0.15f);
                    float oy = cy + (i < 2 ? -rr * 0.85f : rr * 0.15f);
                    r.set(ox, oy, ox + rr * 0.7f, oy + rr * 0.7f);
                    c.drawRoundRect(r, d * 2, d * 2, p);
                }
                break;
            case PLUS:
                c.drawLine(cx - rr * 0.7f, cy, cx + rr * 0.7f, cy, p);
                c.drawLine(cx, cy - rr * 0.7f, cx, cy + rr * 0.7f, p);
                break;
            default: // SEARCH
                c.drawCircle(cx - rr * 0.15f, cy - rr * 0.15f, rr * 0.6f, p);
                c.drawLine(cx + rr * 0.3f, cy + rr * 0.3f, cx + rr * 0.85f, cy + rr * 0.85f, p);
        }
        p.setStyle(st);
    }
}
