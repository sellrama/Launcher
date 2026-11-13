package com.rzone.hyper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Calendar;

/** 20 desain jam lockscreen. Diisi oleh DepthView lalu dipanggil draw(). */
final class Clock {
    static final String[] NAMES = {
            "Bertumpuk", "Satu Baris", "iOS Tebal", "Tipis Raksasa", "Analog Klasik",
            "Analog Minimal", "Analog Titik", "Cincin", "Neon", "Outline",
            "Gradien", "Serif Elegan", "Digital 7-Segmen", "Flip Retro", "Gelembung",
            "Kiri Bertumpuk", "Kata", "Split", "Bayangan 3D", "Kapsul Kaca",
            "Terminal", "Kapsul Ganda", "Garis Bawah", "Radar", "Dot Matriks",
            "Miring Tebal", "Dua Warna", "Bingkai", "Progres Hari", "Tumpuk Elegan"};
    static final int N = NAMES.length;

    // ---- masukan (diisi DepthView setiap frame)
    float w, h, d, csize = 1, copa = 1, cline = 1.08f, e = 1, aod, px, py;
    int color = Color.WHITE, accent = 0xFF00E5FF, ui, ac;
    boolean shadow = true;
    char[] dig = {'1', '2', '0', '0'};
    float[] roll = {1, 1, 1, 1};
    long now;
    Calendar cal;
    UiStyle us;
    RectF dst;
    // ---- keluaran
    float top, bottom;

    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG), fp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect rb = new Rect();
    private final RectF rr = new RectF();
    private static final Typeface COND = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private static final Typeface BLACK = Typeface.create("sans-serif-black", Typeface.NORMAL);
    private static final Typeface THIN = Typeface.create("sans-serif-thin", Typeface.NORMAL);
    private static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private static final Typeface MED = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface SERIF = Typeface.create(Typeface.SERIF, Typeface.NORMAL);
    private static final Typeface MONO = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);

    private float al() {
        return copa * e * (1 - aod * 0.1f);
    }

    /** Gambar desain {@code face} mulai dari y = startTop. */
    void draw(Canvas c, int face, float startTop) {
        top = startTop;
        switch (face) {
            case 1:
                line(c, COND, 0.9f, 0, true);
                break;
            case 2:
                line(c, BLACK, 0.8f, 0, false);
                break;
            case 3:
                line(c, THIN, 0.92f, 0, true);
                break;
            case 4:
                analog(c, 0);
                break;
            case 5:
                analog(c, 1);
                break;
            case 6:
                analog(c, 2);
                break;
            case 7:
                ring(c);
                break;
            case 8:
                line(c, MED, 0.84f, 3, false);
                break;
            case 9:
                stack(c, COND, false, 1);
                break;
            case 10:
                stack(c, BLACK, false, 2);
                break;
            case 11:
                serif(c);
                break;
            case 12:
                seg7(c);
                break;
            case 13:
                flip(c);
                break;
            case 14:
                bubbles(c);
                break;
            case 15:
                stack(c, LIGHT, true, 0);
                break;
            case 16:
                words(c);
                break;
            case 17:
                split(c);
                break;
            case 18:
                line(c, BLACK, 0.8f, 4, false);
                break;
            case 19:
                glass(c);
                break;
            case 20:
                terminal(c);
                break;
            case 21:
                pills(c);
                break;
            case 22:
                underline(c);
                break;
            case 23:
                radar(c);
                break;
            case 24:
                dotMatrix(c);
                break;
            case 25:
                italic(c);
                break;
            case 26:
                twoTone(c);
                break;
            case 27:
                frame(c);
                break;
            case 28:
                progress(c);
                break;
            case 29:
                elegantStack(c);
                break;
            default:
                stack(c, COND, false, 0);
        }
    }

    // ================================================================ alat bantu

    /** mode: 0 isi, 1 outline, 2 gradien, 3 neon, 4 bayangan 3D */
    private void paint(Typeface tf, float size, int mode, float yTop, float yBot) {
        tp.reset();
        tp.setAntiAlias(true);
        tp.setTypeface(tf);
        tp.setTextSize(size);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setColor(color);
        if (tf == COND) tp.setTextScaleX(0.9f);
        if (mode == 1) {
            tp.setStyle(Paint.Style.STROKE);
            tp.setStrokeWidth(Math.max(d * 2, size * 0.022f));
            tp.setStrokeJoin(Paint.Join.ROUND);
        } else if (mode == 2) {
            tp.setShader(new LinearGradient(0, yTop, 0, yBot, color, accent, Shader.TileMode.CLAMP));
        } else if (mode == 3) {
            tp.setColor(UiStyle.mix(Color.WHITE, accent, 0.55f));
            tp.setShadowLayer(size * 0.12f, 0, 0, accent);
        } else if (shadow) {
            tp.setShadowLayer(size * 0.04f, 0, size * 0.01f, Color.argb(80, 0, 0, 0));
        }
    }

    private float rollE(int i) {
        float r = roll[i];
        if (r <= 0) return 0;
        return 1 - (float) Math.pow(1 - Math.min(r, 1), 3);
    }

    private void digit(Canvas c, int i, float x, float y, float dh) {
        float e1 = rollE(i);
        if (e1 <= 0) return;
        int base = tp.getAlpha();
        tp.setAlpha((int) (255 * e1 * al()));
        c.save();
        c.translate(0, (1 - e1) * dh * 0.3f);
        float s = 1 + (1 - e1) * 0.25f;
        c.scale(s, s, x, y);
        c.drawText(String.valueOf(dig[i]), x, y, tp);
        c.restore();
        tp.setAlpha(base);
    }

    private float digitH() {
        tp.getTextBounds("0", 0, 1, rb);
        return rb.height();
    }

    private int hour() {
        return dig[0] == '#' ? 12 : (dig[0] - '0') * 10 + (dig[1] - '0');
    }

    private int minute() {
        return dig[2] == '#' ? 0 : (dig[2] - '0') * 10 + (dig[3] - '0');
    }

    private String hhmm(String sep) {
        return "" + dig[0] + dig[1] + sep + dig[2] + dig[3];
    }

    // ================================================================ desain digital

    /** Satu baris "HH:MM". widthK = porsi lebar layar. */
    private void line(Canvas c, Typeface tf, float widthK, int mode, boolean blink) {
        paint(tf, 100, 0, 0, 0);
        float c100 = tp.measureText("0");
        float size = Math.min(0.22f * h, 100 * w * widthK / (c100 * 4.45f)) * csize;
        paint(tf, size, mode, top, top + size);
        float dh = digitH(), cell = tp.measureText("0"), col = cell * 0.45f;
        float x0 = (w - (cell * 4 + col)) / 2;
        float y = top + dh;
        if (mode == 2) paint(tf, size, 2, y - dh, y);
        float[] xs = {x0 + cell * 0.5f, x0 + cell * 1.5f, x0 + cell * 2 + col + cell * 0.5f, x0 + cell * 2 + col + cell * 1.5f};
        if (mode == 4) { // bayangan 3D
            int base = tp.getColor();
            for (int k = 6; k >= 1; k--) {
                tp.setColor(UiStyle.mix(Color.BLACK, accent, 0.25f + k * 0.04f));
                tp.setAlpha((int) (200 * al()));
                c.save();
                c.translate(k * d * 1.3f, k * d * 1.3f);
                for (int i = 0; i < 4; i++) c.drawText(String.valueOf(dig[i]), xs[i], y, tp);
                c.drawText(":", x0 + cell * 2 + col / 2, y - 0.06f * dh, tp);
                c.restore();
            }
            tp.setColor(base);
        }
        for (int i = 0; i < 4; i++) digit(c, i, xs[i], y, dh);
        float b = blink ? (float) (0.45 + Math.abs(Math.sin(now / 1000.0 * Math.PI)) * 0.55) : 1f;
        tp.setAlpha((int) (255 * al() * Util.clamp(roll[1], 0, 1) * b));
        c.drawText(":", x0 + cell * 2 + col / 2, y - 0.06f * dh, tp);
        bottom = y;
    }

    /** Dua baris "HH" di atas "MM". */
    private void stack(Canvas c, Typeface tf, boolean left, int mode) {
        float size = (left ? 0.26f : 0.3f) * h * csize;
        paint(tf, size, mode, top, top + size);
        float dh = digitH(), cell = tp.measureText("0");
        float y1 = top + dh, y2 = y1 + cline * dh;
        float cx = left ? d * 26 + cell : w / 2;
        if (mode == 2) paint(tf, size, 2, y1 - dh, y2);
        digit(c, 0, cx - cell / 2, y1, dh);
        digit(c, 1, cx + cell / 2, y1, dh);
        if (left) { // menit diberi warna aksen (gaya Pixel kiri)
            tp.setColor(accent);
        }
        digit(c, 2, cx - cell / 2, y2, dh);
        digit(c, 3, cx + cell / 2, y2, dh);
        bottom = y2;
    }

    private void serif(Canvas c) {
        float size = 0.17f * h * csize;
        paint(SERIF, size, 0, 0, 0);
        tp.setLetterSpacing(-0.02f);
        String s = hhmm(":");
        float tw = tp.measureText(s);
        if (tw > w * 0.9f) {
            size *= w * 0.9f / tw;
            tp.setTextSize(size);
        }
        float dh = digitH();
        float y = top + dh;
        tp.setAlpha((int) (255 * al()));
        c.drawText(s, w / 2, y, tp);
        fp.reset();
        fp.setAntiAlias(true);
        fp.setColor(accent);
        fp.setAlpha((int) (220 * al()));
        fp.setStrokeWidth(d * 1.5f);
        c.drawLine(w / 2 - d * 40, y + d * 16, w / 2 + d * 40, y + d * 16, fp);
        bottom = y + d * 16;
    }

    private void split(Canvas c) {
        float size = 0.26f * h * csize;
        paint(BLACK, size, 0, 0, 0);
        float dh = digitH();
        String hh = "" + dig[0] + dig[1];
        float hw = tp.measureText(hh);
        float small = size * 0.46f;
        tp.setTextSize(small);
        float mw = tp.measureText("00");
        float total = hw + d * 10 + mw;
        float x0 = (w - total) / 2;
        float y = top + dh;
        tp.setTextSize(size);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setAlpha((int) (255 * al()));
        c.drawText(hh, x0, y, tp);
        tp.setTextSize(small);
        tp.setColor(accent);
        tp.setAlpha((int) (255 * al()));
        tp.getTextBounds("0", 0, 1, rb);
        c.drawText("" + dig[2] + dig[3], x0 + hw + d * 10, y - dh + rb.height(), tp);
        tp.setColor(color);
        tp.setTextSize(small * 0.42f);
        tp.setTypeface(MED);
        tp.setAlpha((int) (200 * al()));
        c.drawText(cal.get(Calendar.AM_PM) == Calendar.AM ? "PAGI" : "MALAM", x0 + hw + d * 12, y, tp);
        bottom = y;
    }

    private void seg7(Canvas c) {
        // segmen: a b c d e f g
        int[] map = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};
        float dw = Math.min(w * 0.15f, h * 0.068f) * csize, dh = dw * 1.8f, t = dw * 0.17f, gap = dw * 0.3f;
        if (dw * 4 + gap * 4 > w * 0.9f) { // jangan sampai terpotong di tepi layar
            float k = w * 0.9f / (dw * 4 + gap * 4);
            dw *= k;
            dh *= k;
            t *= k;
            gap *= k;
        }
        float total = dw * 4 + gap * 3 + gap;
        float x = (w - total) / 2;
        fp.reset();
        fp.setAntiAlias(true);
        for (int i = 0; i < 4; i++) {
            int v = dig[i] >= '0' && dig[i] <= '9' ? map[dig[i] - '0'] : 0;
            seg(c, x, top, dw, dh, t, v);
            x += dw + gap;
            if (i == 1) {
                fp.setColor(accent);
                fp.setAlpha((int) (255 * al() * (0.4f + 0.6f * (float) Math.abs(Math.sin(now / 1000.0 * Math.PI)))));
                c.drawCircle(x - gap * 0.5f + gap * 0.5f, top + dh * 0.32f, t * 0.6f, fp);
                c.drawCircle(x - gap * 0.5f + gap * 0.5f, top + dh * 0.68f, t * 0.6f, fp);
                x += gap;
            }
        }
        bottom = top + dh;
    }

    private void seg(Canvas c, float x, float y, float dw, float dh, float t, int v) {
        float hh = dh / 2;
        float[][] s = {
                {x + t, y, x + dw - t, y + t},                 // a
                {x + dw - t, y + t, x + dw, y + hh - t / 2},   // b
                {x + dw - t, y + hh + t / 2, x + dw, y + dh - t}, // c
                {x + t, y + dh - t, x + dw - t, y + dh},       // d
                {x, y + hh + t / 2, x + t, y + dh - t},        // e
                {x, y + t, x + t, y + hh - t / 2},             // f
                {x + t, y + hh - t / 2, x + dw - t, y + hh + t / 2}}; // g
        for (int k = 0; k < 7; k++) {
            boolean on = (v & (1 << k)) != 0;
            fp.setColor(on ? color : Color.WHITE);
            fp.setAlpha((int) ((on ? 255 : 22) * al()));
            if (on) fp.setShadowLayer(t, 0, 0, UiStyle.alpha(accent, 160));
            else fp.clearShadowLayer();
            rr.set(s[k][0], s[k][1], s[k][2], s[k][3]);
            c.drawRoundRect(rr, t / 2, t / 2, fp);
        }
        fp.clearShadowLayer();
    }

    private void flip(Canvas c) {
        float cw = Math.min(w * 0.38f, h * 0.2f) * csize, ch = cw * 1.12f, gap = d * 10;
        float x0 = (w - cw * 2 - gap) / 2;
        String[] parts = {"" + dig[0] + dig[1], "" + dig[2] + dig[3]};
        for (int k = 0; k < 2; k++) {
            float x = x0 + k * (cw + gap);
            rr.set(x, top, x + cw, top + ch);
            fp.reset();
            fp.setAntiAlias(true);
            fp.setColor(0xFF1E2027);
            fp.setAlpha((int) (235 * al()));
            fp.setShadowLayer(d * 10, 0, d * 4, Color.argb(120, 0, 0, 0));
            c.drawRoundRect(rr, d * 14, d * 14, fp);
            fp.clearShadowLayer();
            fp.setColor(0xFF2A2D36);
            fp.setAlpha((int) (235 * al()));
            c.drawRoundRect(rr.left, rr.top, rr.right, rr.centerY(), d * 14, d * 14, fp);
            paint(COND, ch * 0.78f, 0, 0, 0);
            tp.setShadowLayer(0, 0, 0, 0);
            tp.setAlpha((int) (255 * al()));
            float dh = digitH();
            c.drawText(parts[k], rr.centerX(), rr.centerY() + dh / 2, tp);
            fp.setColor(Color.BLACK);
            fp.setAlpha((int) (200 * al()));
            fp.setStrokeWidth(d * 2);
            c.drawLine(rr.left, rr.centerY(), rr.right, rr.centerY(), fp);
        }
        bottom = top + ch;
    }

    private void bubbles(Canvas c) {
        float r = Math.min(w * 0.1f, h * 0.05f) * csize, gap = r * 0.35f;
        float total = r * 8 + gap * 3;
        float x = (w - total) / 2 + r;
        float cy = top + r;
        paint(MED, r * 1.15f, 0, 0, 0);
        tp.setShadowLayer(0, 0, 0, 0);
        float dh = digitH();
        for (int i = 0; i < 4; i++) {
            fp.reset();
            fp.setAntiAlias(true);
            fp.setColor(i < 2 ? UiStyle.alpha(Color.WHITE, 46) : UiStyle.alpha(accent, 120));
            fp.setAlpha((int) Math.min(255, fp.getAlpha() * al() * rollE(i) + 1));
            c.drawCircle(x, cy, r, fp);
            fp.setStyle(Paint.Style.STROKE);
            fp.setStrokeWidth(d * 1.4f);
            fp.setColor(Color.argb((int) (120 * al()), 255, 255, 255));
            c.drawCircle(x, cy, r, fp);
            tp.setAlpha((int) (255 * al() * rollE(i)));
            c.drawText(String.valueOf(dig[i]), x, cy + dh / 2, tp);
            x += r * 2 + gap;
        }
        bottom = cy + r;
    }

    private static final String[] NUM = {"nol", "satu", "dua", "tiga", "empat", "lima", "enam", "tujuh", "delapan",
            "sembilan", "sepuluh", "sebelas"};

    static String words(int n) {
        if (n < 12) return NUM[n];
        if (n < 20) return NUM[n - 10] + " belas";
        int t = n / 10, u = n % 10;
        return NUM[t] + " puluh" + (u == 0 ? "" : " " + NUM[u]);
    }

    private void words(Canvas c) {
        int hh = hour() % 12;
        if (hh == 0) hh = 12;
        int mm = minute();
        String[] lines = mm == 0 ? new String[]{"pukul", words(hh), "tepat"}
                : new String[]{"pukul", words(hh), "lewat", words(mm)};
        float size = Math.min(w * 0.13f, h * 0.06f) * csize;
        paint(BLACK, size, 0, 0, 0);
        tp.setTextAlign(Paint.Align.LEFT);
        float x = d * 28, y = top;
        for (int i = 0; i < lines.length; i++) {
            boolean key = i == 1 || i == 3;
            tp.setTypeface(key ? BLACK : LIGHT);
            tp.setColor(key ? (i == 3 ? accent : color) : UiStyle.alpha(color, 190));
            tp.setTextSize(key ? size : size * 0.62f);
            String s = lines[i];
            while (tp.measureText(s) > w - x * 2 && tp.getTextSize() > d * 12) tp.setTextSize(tp.getTextSize() * 0.92f);
            tp.setAlpha((int) (tp.getAlpha() * al()));
            y += tp.getTextSize() * 1.02f;
            c.drawText(s, x, y, tp);
        }
        bottom = y;
    }

    private void glass(Canvas c) {
        String s = hhmm(":");
        float size = 0.075f * h * csize;
        paint(MED, size, 0, 0, 0);
        float dh = digitH();
        float padX = d * 26, padY = d * 20;
        float cw = tp.measureText(s) + padX * 2, ch = dh + padY * 2;
        float lift = (1 - e) * 30;
        rr.set(w / 2 - cw / 2, top + lift, w / 2 + cw / 2, top + lift + ch);
        us.draw(c, rr, ch / 2, ui == 0 ? 1 : ui, ac, dst, px, py, 0, e * copa);
        int on = UiStyle.onPanel(ui, ac);
        tp.setColor(on == Color.WHITE ? color : on);
        tp.setShadowLayer(0, 0, 0, 0);
        tp.setAlpha((int) (255 * e * copa));
        c.drawText(s, w / 2, rr.centerY() + dh * 0.5f, tp);
        bottom = rr.bottom;
    }

    // ================================================================ desain 21–30

    private void terminal(Canvas c) {
        float size = Math.min(w * 0.17f, h * 0.085f) * csize;
        paint(MONO, size, 0, 0, 0);
        tp.setTextAlign(Paint.Align.LEFT);
        int green = 0xFF39FF88;
        tp.setColor(green);
        tp.setShadowLayer(size * 0.15f, 0, 0, UiStyle.alpha(green, 200));
        tp.setAlpha((int) (255 * al()));
        float dh = digitH();
        String s = hhmm(":");
        float tw = tp.measureText("> " + s + "_");
        float x = (w - tw) / 2, y = top + dh;
        String cur = (now / 530) % 2 == 0 ? "_" : " ";
        c.drawText("> " + s + cur, x, y, tp);
        tp.setTextSize(size * 0.32f);
        tp.setAlpha((int) (170 * al()));
        c.drawText("hyper@rzone:~$ date", x, top - size * 0.12f, tp);
        fp.reset();
        fp.setColor(Color.BLACK);
        fp.setAlpha((int) (40 * al()));
        for (float yy = top - size * 0.4f; yy < y + d * 4; yy += d * 3) c.drawRect(x - d * 6, yy, x + tw + d * 6, yy + d, fp);
        bottom = y;
    }

    private void pills(Canvas c) {
        float ph = Math.min(h * 0.11f, w * 0.26f) * csize, pw = ph * 1.7f, gap = d * 10;
        float x0 = (w - pw * 2 - gap) / 2;
        String[] parts = {"" + dig[0] + dig[1], "" + dig[2] + dig[3]};
        int[] cols = {UiStyle.alpha(color, 235), UiStyle.alpha(accent, 235)};
        for (int k = 0; k < 2; k++) {
            float x = x0 + k * (pw + gap);
            rr.set(x, top, x + pw, top + ph);
            fp.reset();
            fp.setAntiAlias(true);
            fp.setColor(cols[k]);
            fp.setAlpha((int) (fp.getAlpha() * al()));
            c.drawRoundRect(rr, ph / 2, ph / 2, fp);
            paint(BLACK, ph * 0.62f, 0, 0, 0);
            tp.setShadowLayer(0, 0, 0, 0);
            tp.setColor(UiStyle.lum(cols[k]) > 0.6f ? 0xFF101218 : Color.WHITE);
            tp.setAlpha((int) (255 * al()));
            c.drawText(parts[k], rr.centerX(), rr.centerY() + digitH() / 2, tp);
        }
        bottom = top + ph;
    }

    private void underline(Canvas c) {
        float size = Math.min(0.2f * h, w * 0.28f) * csize;
        paint(THIN, size, 0, 0, 0);
        String hh = "" + dig[0] + dig[1], mm = "" + dig[2] + dig[3];
        float a1 = tp.measureText(hh);
        tp.setTypeface(BLACK);
        float a2 = tp.measureText(mm);
        float x0 = (w - a1 - a2 - d * 6) / 2;
        float dh = digitH();
        float y = top + dh;
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTypeface(THIN);
        tp.setAlpha((int) (255 * al()));
        c.drawText(hh, x0, y, tp);
        tp.setTypeface(BLACK);
        tp.setColor(accent);
        tp.setAlpha((int) (255 * al()));
        c.drawText(mm, x0 + a1 + d * 6, y, tp);
        fp.reset();
        fp.setAntiAlias(true);
        fp.setColor(accent);
        fp.setAlpha((int) (255 * al()));
        float sec = cal.get(Calendar.SECOND) / 60f;
        rr.set(x0, y + d * 10, x0 + (a1 + a2 + d * 6) * Math.max(0.04f, sec), y + d * 14);
        c.drawRoundRect(rr, d * 2, d * 2, fp);
        bottom = y + d * 14;
    }

    private void radar(Canvas c) {
        float R = 0.3f * w * csize;
        float mx = w / 2, my = top + R + d * 6;
        bottom = my + R;
        float a = al();
        fp.reset();
        fp.setAntiAlias(true);
        fp.setStyle(Paint.Style.STROKE);
        fp.setStrokeWidth(d * 1.2f);
        fp.setColor(accent);
        for (int i = 1; i <= 3; i++) {
            fp.setAlpha((int) (90 * a));
            c.drawCircle(mx, my, R * i / 3f, fp);
        }
        float sec = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f;
        float ang = sec * 6 - 90;
        fp.setStyle(Paint.Style.FILL);
        fp.setShader(new android.graphics.SweepGradient(mx, my, new int[]{0, 0, UiStyle.alpha(accent, (int) (150 * a))},
                new float[]{0, 0.8f, 1}));
        c.save();
        c.rotate(ang, mx, my);
        c.drawCircle(mx, my, R, fp);
        c.restore();
        fp.setShader(null);
        paint(MED, R * 0.4f, 0, 0, 0);
        tp.setAlpha((int) (255 * a));
        c.drawText(hhmm(":"), mx, my + digitH() / 2, tp);
    }

    private static final int[][] DOT = { // 5×7 font angka
            {0x0E, 0x11, 0x13, 0x15, 0x19, 0x11, 0x0E}, {0x04, 0x0C, 0x04, 0x04, 0x04, 0x04, 0x0E},
            {0x0E, 0x11, 0x01, 0x02, 0x04, 0x08, 0x1F}, {0x1F, 0x02, 0x04, 0x02, 0x01, 0x11, 0x0E},
            {0x02, 0x06, 0x0A, 0x12, 0x1F, 0x02, 0x02}, {0x1F, 0x10, 0x1E, 0x01, 0x01, 0x11, 0x0E},
            {0x06, 0x08, 0x10, 0x1E, 0x11, 0x11, 0x0E}, {0x1F, 0x01, 0x02, 0x04, 0x08, 0x08, 0x08},
            {0x0E, 0x11, 0x11, 0x0E, 0x11, 0x11, 0x0E}, {0x0E, 0x11, 0x11, 0x0F, 0x01, 0x02, 0x0C}};

    private void dotMatrix(Canvas c) {
        float cell = Math.min(w / 26f, h / 60f) * csize, r = cell * 0.38f;
        float total = cell * (5 * 4 + 3 + 2);
        float x = (w - total) / 2;
        fp.reset();
        fp.setAntiAlias(true);
        for (int i = 0; i < 4; i++) {
            int v = dig[i] >= '0' && dig[i] <= '9' ? dig[i] - '0' : 0;
            for (int row = 0; row < 7; row++) {
                for (int col = 0; col < 5; col++) {
                    boolean on = (DOT[v][row] & (1 << (4 - col))) != 0;
                    fp.setColor(on ? color : Color.WHITE);
                    fp.setAlpha((int) ((on ? 255 : 18) * al() * (on ? rollE(i) : 1)));
                    c.drawCircle(x + col * cell + cell / 2, top + row * cell + cell / 2, r, fp);
                }
            }
            x += cell * 6;
            if (i == 1) {
                fp.setColor(accent);
                fp.setAlpha((int) (255 * al()));
                c.drawCircle(x, top + cell * 2.5f, r, fp);
                c.drawCircle(x, top + cell * 4.5f, r, fp);
                x += cell * 1;
            }
        }
        bottom = top + cell * 7;
    }

    private void italic(Canvas c) {
        c.save();
        c.skew(-0.18f, 0);
        c.translate(top * 0.18f + w * 0.03f, 0);
        line(c, BLACK, 0.82f, 2, false);
        c.restore();
    }

    private void twoTone(Canvas c) {
        float size = Math.min(0.2f * h, w * 0.24f) * csize;
        paint(BLACK, size, 0, 0, 0);
        String s = hhmm(":");
        float tw = tp.measureText(s);
        if (tw > w * 0.9f) {
            size *= w * 0.9f / tw;
            tp.setTextSize(size);
        }
        float dh = digitH(), y = top + dh;
        tp.setTextAlign(Paint.Align.LEFT);
        String hh = "" + dig[0] + dig[1] + ":";
        float x0 = (w - tp.measureText(s)) / 2;
        tp.setAlpha((int) (255 * al()));
        c.drawText(hh, x0, y, tp);
        tp.setColor(accent);
        tp.setAlpha((int) (255 * al()));
        c.drawText("" + dig[2] + dig[3], x0 + tp.measureText(hh), y, tp);
        bottom = y;
    }

    private void frame(Canvas c) {
        float size = Math.min(0.13f * h, w * 0.2f) * csize;
        paint(MED, size, 0, 0, 0);
        String s = hhmm(":");
        float dh = digitH();
        float pw = tp.measureText(s) + d * 40, ph = dh + d * 44;
        rr.set(w / 2 - pw / 2, top, w / 2 + pw / 2, top + ph);
        fp.reset();
        fp.setAntiAlias(true);
        fp.setStyle(Paint.Style.STROKE);
        fp.setStrokeWidth(d * 2.5f);
        fp.setColor(color);
        fp.setAlpha((int) (230 * al()));
        c.drawRoundRect(rr, d * 22, d * 22, fp);
        fp.setColor(accent);
        fp.setStrokeWidth(d * 5);
        float cl = d * 26;
        c.drawLine(rr.left + d * 18, rr.top, rr.left + d * 18 + cl, rr.top, fp);
        c.drawLine(rr.right - d * 18 - cl, rr.bottom, rr.right - d * 18, rr.bottom, fp);
        tp.setAlpha((int) (255 * al()));
        c.drawText(s, w / 2, rr.centerY() + dh / 2, tp);
        bottom = rr.bottom;
    }

    private void progress(Canvas c) {
        line(c, MED, 0.74f, 0, false);
        float y = bottom + d * 18;
        float frac = (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)) / 1440f;
        float bw = w * 0.62f;
        rr.set(w / 2 - bw / 2, y, w / 2 + bw / 2, y + d * 6);
        fp.reset();
        fp.setAntiAlias(true);
        fp.setColor(Color.WHITE);
        fp.setAlpha((int) (50 * al()));
        c.drawRoundRect(rr, d * 3, d * 3, fp);
        rr.right = rr.left + bw * frac;
        fp.setShader(new LinearGradient(rr.left, 0, rr.left + bw, 0, color, accent, Shader.TileMode.CLAMP));
        fp.setAlpha((int) (255 * al()));
        c.drawRoundRect(rr, d * 3, d * 3, fp);
        fp.setShader(null);
        paint(MED, d * 12, 0, 0, 0);
        tp.setAlpha((int) (180 * al()));
        c.drawText(Math.round(frac * 100) + "% hari ini berlalu", w / 2, y + d * 26, tp);
        bottom = y + d * 26;
    }

    private void elegantStack(Canvas c) {
        float size = 0.24f * h * csize;
        paint(THIN, size, 0, 0, 0);
        tp.setLetterSpacing(0.04f);
        float dh = digitH();
        float y1 = top + dh;
        tp.setAlpha((int) (255 * al()));
        c.drawText("" + dig[0] + dig[1], w / 2, y1, tp);
        fp.reset();
        fp.setAntiAlias(true);
        fp.setColor(accent);
        fp.setAlpha((int) (230 * al()));
        float ly = y1 + dh * 0.22f;
        c.drawRect(w / 2 - d * 30, ly - d * 1, w / 2 + d * 30, ly + d * 1, fp);
        float y2 = y1 + dh * 1.45f;
        tp.setAlpha((int) (255 * al()));
        c.drawText("" + dig[2] + dig[3], w / 2, y2, tp);
        bottom = y2;
    }

    // ================================================================ analog

    /** style 0 klasik (angka & strip), 1 minimal, 2 titik */
    private void analog(Canvas c, int style) {
        float R = 0.34f * w * csize;
        float mx = w / 2, my = top + R + h * 0.01f;
        bottom = my + R;
        float sec = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f;
        float mn = cal.get(Calendar.MINUTE) + sec / 60f;
        float hr = cal.get(Calendar.HOUR) % 12 + mn / 60f;
        float spin = -(1 - e) * 300;
        float a = al();
        c.save();
        float sc = 0.85f + e * 0.15f;
        c.scale(sc, sc, mx, my);
        fp.reset();
        fp.setAntiAlias(true);
        fp.setStrokeCap(Paint.Cap.ROUND);
        fp.setColor(color);
        if (style == 1) {
            fp.setStyle(Paint.Style.FILL);
            fp.setColor(UiStyle.alpha(Color.BLACK, 70));
            c.drawCircle(mx, my, R, fp);
            fp.setColor(color);
            for (int i = 0; i < 4; i++) {
                double an = Math.toRadians(i * 90);
                fp.setAlpha((int) (255 * a));
                c.drawCircle(mx + (float) Math.sin(an) * R * 0.86f, my - (float) Math.cos(an) * R * 0.86f, d * 3, fp);
            }
        } else if (style == 2) {
            fp.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 60; i++) {
                double an = Math.toRadians(i * 6);
                boolean big = i % 5 == 0;
                fp.setColor(i <= (int) mn ? accent : color);
                fp.setAlpha((int) (255 * a * (big ? 1f : 0.45f)));
                c.drawCircle(mx + (float) Math.sin(an) * R * 0.92f, my - (float) Math.cos(an) * R * 0.92f, d * (big ? 3.4f : 1.6f), fp);
            }
        } else {
            for (int i = 0; i < 60; i++) {
                boolean big = i % 5 == 0;
                double an = Math.toRadians(i * 6);
                float r2 = R - (big ? 0.11f * R : 0.045f * R);
                fp.setStrokeWidth((big ? 3.2f : 1.4f) * d);
                fp.setAlpha((int) (255 * a * (big ? 1f : 0.5f)));
                c.drawLine(mx + (float) Math.sin(an) * R, my - (float) Math.cos(an) * R,
                        mx + (float) Math.sin(an) * r2, my - (float) Math.cos(an) * r2, fp);
            }
            paint(MED, 0.26f * R, 0, 0, 0);
            tp.setAlpha((int) (a * 255));
            for (int n : new int[]{12, 3, 6, 9}) {
                double an = Math.toRadians(n * 30);
                c.drawText(String.valueOf(n), mx + (float) Math.sin(an) * R * 0.7f,
                        my - (float) Math.cos(an) * R * 0.7f + tp.getTextSize() * 0.35f, tp);
            }
        }
        fp.setStyle(Paint.Style.STROKE);
        if (shadow) fp.setShadowLayer(12, 0, 4, Color.argb(90, 0, 0, 0));
        fp.setColor(color);
        fp.setAlpha((int) (a * 255));
        hand(c, mx, my, hr * 30 + spin, R * 0.5f, R * (style == 1 ? 0.05f : 0.075f), 0);
        hand(c, mx, my, mn * 6 + spin, R * 0.76f, R * (style == 1 ? 0.035f : 0.05f), 0);
        if (aod < 0.5f) {
            fp.setColor(style == 0 ? Color.parseColor("#FF5A4F") : accent);
            fp.setAlpha((int) (a * 255));
            hand(c, mx, my, sec * 6 + spin, R * 0.86f, d * 2f, R * 0.15f);
        }
        fp.clearShadowLayer();
        fp.setStyle(Paint.Style.FILL);
        c.drawCircle(mx, my, R * 0.045f, fp);
        c.restore();
    }

    private void hand(Canvas c, float mx, float my, float deg, float len, float wd, float tail) {
        double a = Math.toRadians(deg);
        fp.setStrokeWidth(wd);
        c.drawLine(mx - (float) Math.sin(a) * tail, my + (float) Math.cos(a) * tail,
                mx + (float) Math.sin(a) * len, my - (float) Math.cos(a) * len, fp);
    }

    private void ring(Canvas c) {
        float R = 0.3f * w * csize;
        float mx = w / 2, my = top + R + d * 8;
        bottom = my + R;
        float mn = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f;
        float hr = cal.get(Calendar.HOUR) % 12 + mn / 60f;
        float a = al();
        fp.reset();
        fp.setAntiAlias(true);
        fp.setStyle(Paint.Style.STROKE);
        fp.setStrokeCap(Paint.Cap.ROUND);
        float sw = R * 0.1f;
        fp.setStrokeWidth(sw);
        fp.setColor(Color.WHITE);
        fp.setAlpha((int) (40 * a));
        rr.set(mx - R, my - R, mx + R, my + R);
        c.drawOval(rr, fp);
        fp.setColor(color);
        fp.setAlpha((int) (255 * a));
        c.drawArc(rr, -90, 360 * hr / 12f * e, false, fp);
        float r2 = R - sw * 1.6f;
        rr.set(mx - r2, my - r2, mx + r2, my + r2);
        fp.setColor(Color.WHITE);
        fp.setAlpha((int) (40 * a));
        c.drawOval(rr, fp);
        fp.setColor(accent);
        fp.setAlpha((int) (255 * a));
        c.drawArc(rr, -90, 360 * mn / 60f * e, false, fp);
        paint(MED, R * 0.42f, 0, 0, 0);
        tp.setAlpha((int) (255 * a));
        float dh = digitH();
        c.drawText(hhmm(":"), mx, my + dh / 2, tp);
    }
}
