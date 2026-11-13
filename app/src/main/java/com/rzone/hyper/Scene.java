package com.rzone.hyper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.io.File;
import java.util.Random;

/** Wallpaper bawaan (digambar prosedural pada kanvas 400×800 lalu diskalakan). */
final class Scene {
    static final String[] WALL_NAMES = {"Senja", "Aurora", "Samudra", "Gunung Salju", "Hutan Kabut", "Gurun",
            "Kota Malam", "Galaksi", "Bulan", "Sakura", "Mesh Gradien", "Geometri", "AMOLED", "Pastel", "Langit Biru"};
    static final String[] DEPTH_NAMES = {"Kota & Pesawat", "Pegunungan", "Hutan Pinus", "Bulan Besar",
            "Samudra & Kapal", "Pencakar Langit", "Planet", "Gurun & Kaktus", "Balon Udara", "Torii & Fuji",
            "Sunset Tropis", "Langit Nebula", "Kota Hujan", "Padang Lavender", "Danau Dingin", "Hutan Autumn",
            "Candi & Kabut", "Salju Bulan", "Kapal Layar", "Vulkan Merah"};
    /** 15 variasi warna untuk setiap adegan depth (20 × 15 = 300 wallpaper di Store). */
    static final String[] VARIANTS = {"Asli", "Senja", "Malam", "Fajar", "Neon", "Pastel", "Monokrom", "Hangat",
            "Dingin", "Ungu", "Hijau", "Merah", "Emas", "Sepia", "Terbalik"};
    private static final Path PLANE = new Path();

    static {
        float[][] r = {{0, 0}, {8, 40}, {68, 66}, {67, 75}, {6, 62}, {2, 92}, {18, 104}, {18, 110}, {0, 104}};
        PLANE.moveTo(r[0][0], r[0][1]);
        for (int i = 1; i < r.length; i++) PLANE.lineTo(r[i][0], r[i][1]);
        for (int i = r.length - 2; i >= 1; i--) PLANE.lineTo(-r[i][0], r[i][1]);
        PLANE.close();
    }

    private Scene() {
    }

    private static Bitmap bmp(int w, int h) {
        return Bitmap.createBitmap(Math.max(2, w), Math.max(2, h), Bitmap.Config.ARGB_8888);
    }

    private static Canvas cv(Bitmap b) {
        Canvas c = new Canvas(b);
        c.scale(b.getWidth() / 400f, b.getHeight() / 800f);
        return c;
    }

    private static int C(String s) {
        return Color.parseColor(s);
    }

    private static Paint P(int col) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(col);
        return p;
    }

    private static void grad(Canvas c, String... cols) {
        int[] a = new int[cols.length];
        for (int i = 0; i < a.length; i++) a[i] = C(cols[i]);
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, 0, 800, a, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 400, 800, p);
    }

    private static void glow(Canvas c, float x, float y, float r, int col, int a0) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(x, y, r, new int[]{UiStyle.alpha(col, a0), UiStyle.alpha(col, 0)}, null,
                Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    private static void ridge(Canvas c, float base, float amp, float fr, float ph, int col) {
        Path p = new Path();
        p.moveTo(0, 800);
        for (float x = 0; x <= 400; x += 6) {
            p.lineTo(x, base - (float) (Math.sin(x * fr + ph) * 0.6 + Math.sin(x * fr * 2.3 + 1.7 * ph) * 0.4) * amp);
        }
        p.lineTo(400, 800);
        p.close();
        c.drawPath(p, P(col));
    }

    private static void peaks(Canvas c, Random r, float base, float hmin, float hmax, int n, int col) {
        Path p = new Path();
        p.moveTo(0, 800);
        p.lineTo(0, base);
        float w = 400f / n;
        for (int i = 0; i < n; i++) {
            float x = i * w;
            p.lineTo(x + 0.5f * w, base - hmin - r.nextFloat() * (hmax - hmin));
            p.lineTo(x + w, base - r.nextFloat() * hmin * 0.4f);
        }
        p.lineTo(400, 800);
        p.close();
        c.drawPath(p, P(col));
    }

    private static void stars(Canvas c, int n, Random r, float maxY) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < n; i++) {
            p.setColor(Color.argb(120 + r.nextInt(135), 255, 255, 255));
            c.drawCircle(r.nextFloat() * 400, r.nextFloat() * maxY, 0.5f + r.nextFloat() * 1.2f, p);
        }
    }

    private static void pine(Canvas c, float x, float base, float h, int col) {
        Paint p = P(col);
        c.drawRect(x - h * 0.02f, base - h * 0.15f, x + h * 0.02f, base, p);
        for (int k = 0; k < 5; k++) {
            float yt = base - h + k * h * 0.17f;
            float hw = 0.06f * h * (k + 1.7f);
            Path t = new Path();
            t.moveTo(x, yt);
            t.lineTo(x - hw, yt + h * 0.3f);
            t.lineTo(x + hw, yt + h * 0.3f);
            t.close();
            c.drawPath(t, p);
        }
    }

    /**
     * Gedung berjendela. Perbaikan: v5.1 punya loop tanpa kondisi berhenti di baris jendela,
     * yang bisa membuat aplikasi hang (ANR) saat memilih "Kota Malam" / "Pencakar Langit".
     */
    private static void towers(Canvas c, Random r, float minH, float maxH, boolean centerTall, int col, int win) {
        Paint p = P(col);
        Paint wp = P(win);
        float x = -10;
        while (x < 400) {
            float w = 24 + r.nextInt(26);
            float k = centerTall ? 1f - Math.abs(x + w / 2f - 200f) / 230f : 1f;
            float h = minH + (maxH - minH) * Math.max(0.15f, k) * (0.55f + r.nextFloat() * 0.45f);
            c.drawRect(x, 800 - h, x + w, 800, p);
            for (float wy = 800 - h + 10; wy < 780; wy += 14) {
                for (float wx = x + 5; wx < x + w - 6; wx += 9) {
                    if (r.nextFloat() < 0.38f) {
                        wp.setAlpha(120 + r.nextInt(120));
                        c.drawRect(wx, wy, wx + 4, wy + 7, wp);
                    }
                }
            }
            x += w + r.nextInt(6);
        }
    }

    private static void moon(Canvas c, Random r, float x, float y, float rad, int col) {
        glow(c, x, y, rad * 2.2f, col, 110);
        c.drawCircle(x, y, rad, P(col));
        Paint cr = P(Color.argb(40, 90, 90, 110));
        for (int i = 0; i < 6; i++) {
            double a = r.nextDouble() * 6.28;
            double d = r.nextDouble() * rad * 0.6;
            c.drawCircle(x + (float) (Math.cos(a) * d), y + (float) (Math.sin(a) * d), (0.06f + r.nextFloat() * 0.1f) * rad, cr);
        }
    }

    private static void clouds(Canvas c, int a, float[][] cl) {
        Paint p = P(Color.argb(a, 255, 255, 255));
        p.setMaskFilter(new BlurMaskFilter(16, BlurMaskFilter.Blur.NORMAL));
        for (float[] q : cl) c.drawOval(new RectF(q[0] - q[2], q[1] - q[2] * 0.24f, q[0] + q[2], q[1] + q[2] * 0.24f), p);
    }

    private static void sea(Canvas c, float top, String a, String b) {
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, top, 0, 800, C(a), C(b), Shader.TileMode.CLAMP));
        c.drawRect(0, top, 400, 800, p);
    }

    // =============================================================== wallpaper launcher

    static Bitmap wall(int id, int w, int h) {
        Bitmap b = bmp(w, h);
        Canvas c = cv(b);
        Random r = new Random(id * 77L + 5);
        switch (id) {
            case 0:
                grad(c, "#2B1B4D", "#D6567B", "#FFB45E");
                glow(c, 200, 480, 260, C("#FFD08A"), 200);
                c.drawCircle(200, 470, 56, P(C("#FFF2C4")));
                ridge(c, 600, 60, 0.012f, 0, C("#5B2A66"));
                ridge(c, 660, 50, 0.016f, 2, C("#3A1B4A"));
                ridge(c, 730, 40, 0.02f, 4, C("#1A0E2B"));
                break;
            case 1: {
                grad(c, "#050816", "#0B1A3A", "#0D2B2F");
                stars(c, 80, r, 500);
                int[] ac = {C("#2CFFB0"), C("#2CC7FF"), C("#B04CFF")};
                for (int k = 0; k < 3; k++) {
                    Path p = new Path();
                    float y0 = 190 + k * 80;
                    p.moveTo(0, y0);
                    for (int x = 0; x <= 400; x += 10) p.lineTo(x, y0 + (float) Math.sin(x * 0.02 + k * 1.7) * 40);
                    for (int x = 400; x >= 0; x -= 10) p.lineTo(x, y0 + 110 + (float) Math.sin(x * 0.015 + k) * 30);
                    p.close();
                    Paint q = P(UiStyle.alpha(ac[k], 110));
                    q.setMaskFilter(new BlurMaskFilter(22, BlurMaskFilter.Blur.NORMAL));
                    c.drawPath(p, q);
                }
                ridge(c, 720, 40, 0.015f, 1, C("#04070D"));
                break;
            }
            case 2:
                grad(c, "#7EC8E3", "#CDEBF5", "#F6E7C1");
                glow(c, 200, 440, 200, C("#FFF3C4"), 160);
                sea(c, 440, "#2A8FBF", "#0A3558");
                ridge(c, 480, 10, 0.03f, 0, Color.argb(120, 255, 255, 255));
                ridge(c, 540, 14, 0.025f, 2, C("#1B6E9A"));
                ridge(c, 620, 16, 0.02f, 4, C("#0F4C75"));
                ridge(c, 710, 14, 0.018f, 1, C("#0A3558"));
                break;
            case 3:
                grad(c, "#A8C8F0", "#DDE9F7", "#F7F4EE");
                peaks(c, r, 640, 160, 320, 6, C("#6E86A8"));
                peaks(c, r, 710, 120, 260, 5, C("#3E5578"));
                ridge(c, 770, 30, 0.02f, 0, C("#1E2E48"));
                break;
            case 4:
                grad(c, "#2E4A4F", "#9BB8B4", "#E4EEEA");
                for (int row = 0; row < 4; row++) {
                    float base = 520 + row * 80;
                    int col = Color.rgb(40 - row * 8, 70 - row * 10, 70 - row * 10);
                    for (float x = -10; x < 420; x += 26 + r.nextInt(14)) pine(c, x, base + 40, 150 + row * 36 + r.nextInt(50), col);
                    Paint f = new Paint();
                    f.setShader(new LinearGradient(0, base - 20, 0, base + 80, Color.argb(0, 255, 255, 255),
                            Color.argb(110, 255, 255, 255), Shader.TileMode.CLAMP));
                    c.drawRect(0, base - 20, 400, base + 80, f);
                }
                break;
            case 5:
                grad(c, "#FFB36B", "#FFD9A0", "#FFF0D2");
                glow(c, 200, 360, 220, C("#FFF6DC"), 190);
                c.drawCircle(200, 360, 48, P(C("#FFF6DC")));
                ridge(c, 560, 50, 0.012f, 0, C("#E39A5B"));
                ridge(c, 620, 60, 0.01f, 2, C("#C77A3E"));
                ridge(c, 700, 50, 0.014f, 4, C("#8F4F27"));
                break;
            case 6:
                grad(c, "#0B1030", "#1E2A5E", "#4A3A7A");
                stars(c, 40, r, 300);
                moon(c, r, 300, 150, 26, C("#F4F1D8"));
                towers(c, r, 160, 420, false, C("#0A0C1C"), C("#FFD27A"));
                break;
            case 7:
                grad(c, "#02030A", "#070A1E", "#0C0820");
                glow(c, 120, 260, 220, C("#7B2FF7"), 100);
                glow(c, 300, 500, 260, C("#00C6FF"), 80);
                glow(c, 200, 700, 200, C("#FF3CAC"), 70);
                stars(c, 160, r, 800);
                break;
            case 8:
                grad(c, "#02030A", "#0B1130", "#1B2150");
                stars(c, 90, r, 600);
                moon(c, r, 200, 330, 110, C("#EDE9D8"));
                ridge(c, 730, 30, 0.02f, 1, C("#05060C"));
                break;
            case 9: {
                grad(c, "#FFD6E8", "#FFB6D5", "#F59AC1");
                Paint br = P(C("#5A3A31"));
                br.setStyle(Paint.Style.STROKE);
                br.setStrokeWidth(7);
                br.setStrokeCap(Paint.Cap.ROUND);
                Path p = new Path();
                p.moveTo(420, 40);
                p.quadTo(260, 90, 120, 210);
                c.drawPath(p, br);
                for (int i = 0; i < 70; i++) {
                    float t = r.nextFloat();
                    float x = 420 - t * 300 + r.nextInt(50) - 25;
                    float y = 40 + t * 170 + r.nextInt(60) - 30;
                    c.drawCircle(x, y, 5 + r.nextInt(9), P(Color.argb(190, 255, 200 + r.nextInt(40), 220)));
                }
                for (int i = 0; i < 40; i++) {
                    float x = r.nextInt(400), y = 250 + r.nextInt(520);
                    c.save();
                    c.rotate(r.nextInt(360), x, y);
                    c.drawOval(new RectF(x, y, x + 12, y + 7), P(Color.argb(200, 255, 235, 242)));
                    c.restore();
                }
                break;
            }
            case 10:
                c.drawColor(C("#0B0B14"));
                glow(c, 80, 160, 320, C("#6A5CFF"), 190);
                glow(c, 330, 380, 330, C("#FF4FA3"), 170);
                glow(c, 120, 640, 330, C("#00D4FF"), 150);
                glow(c, 330, 780, 260, C("#7CFFB2"), 110);
                break;
            case 11: {
                int cols = 8, rows = 16;
                float cw = 400f / cols, ch = 800f / rows;
                for (int i = 0; i < cols; i++) {
                    for (int j = 0; j < rows; j++) {
                        for (int k = 0; k < 2; k++) {
                            float t = Util.clamp((j + i * 0.5f) / (rows + cols * 0.5f) + (r.nextFloat() - 0.5f) * 0.12f, 0, 1);
                            int col = UiStyle.mix(C("#7597DE"), C("#2B1055"), t);
                            Path p = new Path();
                            float x = i * cw, y = j * ch;
                            if (k == 0) {
                                p.moveTo(x, y);
                                p.lineTo(x + cw, y);
                                p.lineTo(x, y + ch);
                            } else {
                                p.moveTo(x + cw, y);
                                p.lineTo(x + cw, y + ch);
                                p.lineTo(x, y + ch);
                            }
                            p.close();
                            c.drawPath(p, P(col));
                        }
                    }
                }
                break;
            }
            case 12:
                c.drawColor(Color.BLACK);
                glow(c, 200, 420, 260, C("#2A2A33"), 110);
                break;
            case 13:
                grad(c, "#FFE5EC", "#FFF1E6", "#E4F0FF");
                ridge(c, 500, 40, 0.012f, 0, C("#FFC8DD"));
                ridge(c, 580, 40, 0.015f, 2, C("#CDB4DB"));
                ridge(c, 660, 34, 0.017f, 4, C("#A2D2FF"));
                ridge(c, 740, 26, 0.02f, 1, C("#BDE0FE"));
                break;
            default:
                grad(c, "#2E86DE", "#74B9FF", "#DFF1FF");
                clouds(c, 230, new float[][]{{90, 200, 100}, {300, 140, 120}, {200, 330, 150}, {60, 480, 90},
                        {330, 560, 110}, {180, 700, 130}});
        }
        return b;
    }

    static void snowflakes(Canvas c, Random r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.argb(235, 250, 252, 255));
        for (int i = 0; i < 70; i++) {
            p.setAlpha(120 + r.nextInt(115));
            c.drawCircle(r.nextFloat() * 400, r.nextFloat() * 800, 1.5f + r.nextFloat() * 4, p);
        }
    }

    // =============================================================== depth: latar (di belakang jam)

    static Bitmap depthBg(int id, int w, int h) {
        Bitmap b = bmp(w, h);
        Canvas c = cv(b);
        Random r = new Random(id * 31L + 9);
        switch (id) {
            case 0: {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setShader(new LinearGradient(0, 0, 0, 800, new int[]{C("#5F656B"), C("#C7CBCE"), C("#D9A977")},
                        new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, 400, 800, p);
                clouds(c, 200, new float[][]{{90, 300, 110}, {320, 200, 120}, {200, 440, 150}});
                Paint q = P(C("#43474D"));
                c.drawRect(120, 540, 164, 800, q);
                c.drawRect(170, 480, 200, 800, q);
                c.drawRect(205, 560, 275, 800, q);
                break;
            }
            case 1:
                grad(c, "#F6B26B", "#F9D9A4", "#FFF3DA");
                glow(c, 200, 320, 260, C("#FFE9A8"), 190);
                c.drawCircle(200, 320, 70, P(C("#FFF8E1")));
                clouds(c, 150, new float[][]{{80, 230, 90}, {330, 420, 100}});
                break;
            case 2:
                grad(c, "#8FA7B3", "#D5E0E4", "#F3F6F7");
                glow(c, 300, 170, 100, Color.WHITE, 150);
                c.drawCircle(300, 170, 34, P(C("#FBFCFC")));
                break;
            case 3:
                grad(c, "#05070F", "#0E1530", "#1D2750");
                stars(c, 100, r, 600);
                moon(c, r, 200, 300, 120, C("#F1EEDF"));
                break;
            case 4:
                grad(c, "#2B1B4D", "#E0627E", "#FFC27A");
                glow(c, 200, 430, 220, C("#FFD9A0"), 200);
                c.drawCircle(200, 430, 70, P(C("#FFF1CF")));
                sea(c, 450, "#7A3E6B", "#1A1033");
                break;
            case 5:
                grad(c, "#1B1040", "#7A2F8F", "#FF7A8A");
                stars(c, 40, r, 300);
                glow(c, 200, 520, 300, C("#FF9ABF"), 150);
                break;
            case 6:
                grad(c, "#02030A", "#08102A", "#120A28");
                glow(c, 100, 250, 220, C("#5B2CF0"), 90);
                glow(c, 320, 420, 240, C("#00A6FF"), 70);
                stars(c, 150, r, 700);
                break;
            case 7:
                grad(c, "#FF9A5A", "#FFC98A", "#FFEBC4");
                glow(c, 200, 330, 240, C("#FFF3D6"), 200);
                c.drawCircle(200, 330, 90, P(C("#FFF3D6")));
                break;
            case 8:
                grad(c, "#4FA3F7", "#9AD0FF", "#E8F6FF");
                clouds(c, 230, new float[][]{{70, 180, 90}, {320, 280, 110}, {150, 480, 120}, {340, 620, 100}});
                break;
            case 9: {
                // Perbaikan: v5.1 menggambar Fuji lalu menimpanya dengan latar "Sunset Tropis".
                grad(c, "#FFB5C8", "#FFD9E3", "#FFF1F5");
                glow(c, 310, 230, 120, C("#FF8A7A"), 120);
                c.drawCircle(310, 230, 40, P(C("#FF6F61")));
                Path f = new Path();
                f.moveTo(-20, 650);
                f.lineTo(150, 440);
                f.lineTo(250, 440);
                f.lineTo(420, 650);
                f.close();
                c.drawPath(f, P(C("#6D83B5")));
                Path s = new Path();
                s.moveTo(150, 440);
                s.lineTo(250, 440);
                s.lineTo(232, 482);
                s.lineTo(210, 470);
                s.lineTo(192, 492);
                s.lineTo(170, 476);
                s.close();
                c.drawPath(s, P(Color.WHITE));
                Paint mist = new Paint();
                mist.setShader(new LinearGradient(0, 560, 0, 800, Color.argb(0, 255, 240, 245),
                        Color.argb(200, 255, 240, 245), Shader.TileMode.CLAMP));
                c.drawRect(0, 560, 400, 800, mist);
                break;
            }
            case 10:
                grad(c, "#FF7E5F", "#FEB47B", "#FFE8C2");
                glow(c, 200, 340, 250, C("#FFE2A8"), 200);
                c.drawCircle(200, 340, 78, P(C("#FFF4D6")));
                sea(c, 460, "#0E6B7B", "#063D4A");
                break;
            case 11:
                grad(c, "#0B0F2A", "#2E1A5E", "#7A3FA8");
                stars(c, 180, r, 800);
                glow(c, 130, 300, 240, C("#B05CFF"), 130);
                glow(c, 300, 520, 260, C("#FF5CA8"), 110);
                glow(c, 200, 150, 180, C("#5CA8FF"), 90);
                break;
            case 12: {
                grad(c, "#0A0E14", "#16202C", "#24333F");
                Paint rl = P(Color.argb(120, 160, 200, 230));
                for (int i = 0; i < 60; i++) {
                    float x = r.nextFloat() * 400, y = r.nextFloat() * 800, l = 12 + r.nextFloat() * 26;
                    c.drawLine(x, y, x + 2, y + l, rl);
                }
                break;
            }
            case 13:
                grad(c, "#C9A7E8", "#E3C8F0", "#F6EAF2");
                glow(c, 200, 300, 200, C("#FFF6FB"), 150);
                break;
            case 14:
                grad(c, "#A8C4D8", "#D8E8EE", "#F2F8F8");
                sea(c, 520, "#3A6E8E", "#16354A");
                stars(c, 30, r, 250);
                break;
            case 15:
                grad(c, "#E8A34A", "#C96A2E", "#7A3A1E");
                glow(c, 200, 280, 220, C("#FFD9A0"), 170);
                c.drawCircle(200, 280, 64, P(C("#FFE8C2")));
                break;
            case 16:
                grad(c, "#8A9BAE", "#C4CED8", "#EDE8DC");
                glow(c, 200, 260, 180, C("#FFFDF0"), 160);
                c.drawCircle(200, 260, 46, P(C("#FFFDF0")));
                break;
            case 17:
                grad(c, "#0A1024", "#1B2C52", "#3A5C8E");
                stars(c, 130, r, 500);
                moon(c, r, 210, 260, 96, C("#F2EFE2"));
                snowflakes(c, r);
                break;
            case 18:
                // Perbaikan: lautan kedua dulu menimpa yang pertama seluruhnya.
                grad(c, "#F2C879", "#E89A5A", "#8A5A7A");
                glow(c, 200, 360, 230, C("#FFE4B0"), 190);
                c.drawCircle(200, 360, 66, P(C("#FFF2D2")));
                sea(c, 470, "#C97A8A", "#5A3E6E");
                ridge(c, 560, 6, 0.04f, 0, Color.argb(70, 255, 230, 200));
                break;
            default:
                grad(c, "#1A0E14", "#5A1E1E", "#C84A2E");
                stars(c, 60, r, 300);
                glow(c, 200, 620, 220, C("#FF7A3A"), 160);
                glow(c, 200, 620, 90, C("#FFD98A"), 200);
        }
        return b;
    }

    // =============================================================== depth: subjek (di depan jam)

    static Bitmap depthFg(int id, int w, int h) {
        Bitmap b = bmp(w, h);
        Canvas c = cv(b);
        Random r = new Random(id * 53L + 3);
        switch (id) {
            case 0: {
                Paint p = P(C("#0D0F12"));
                Path l = new Path();
                l.moveTo(0, 800);
                l.lineTo(0, 230);
                l.lineTo(64, 330);
                l.lineTo(116, 800);
                l.close();
                c.drawPath(l, p);
                Path rr = new Path();
                rr.moveTo(400, 800);
                rr.lineTo(400, 200);
                rr.lineTo(338, 318);
                rr.lineTo(286, 800);
                rr.close();
                c.drawPath(rr, p);
                for (int i = 0; i < 22; i++) {
                    if (i % 4 == 3) continue;
                    Paint q = P(C(i % 3 == 0 ? "#FFB35A" : "#FFD98A"));
                    q.setAlpha(210);
                    c.drawRect(10 + (i % 2) * 20, 380 + i * 18, 16 + (i % 2) * 20, 389 + i * 18, q);
                    c.drawRect(362 - (i % 2) * 20, 360 + i * 19, 368 - (i % 2) * 20, 369 + i * 19, q);
                }
                String[] neon = {"#FF5A36", "#35C7FF", "#FFD23A", "#FF3A8A"};
                float[] xs = {132, 176, 212, 258}, ws = {38, 30, 44, 26}, ys = {650, 620, 670, 640}, hs = {70, 90, 60, 80};
                for (int i = 0; i < 4; i++) c.drawRect(xs[i], ys[i], xs[i] + ws[i], ys[i] + hs[i], P(C(neon[i])));
                break;
            }
            case 1:
                peaks(c, r, 600, 180, 340, 5, C("#2C3E50"));
                peaks(c, r, 700, 120, 260, 6, C("#1B2A38"));
                ridge(c, 775, 20, 0.02f, 0, C("#0F1A24"));
                break;
            case 2: {
                ridge(c, 770, 14, 0.02f, 0, C("#1C2B2D"));
                float[][] t = {{20, 560}, {75, 470}, {345, 600}, {390, 500}, {-10, 420}, {420, 440}};
                int[] col = {C("#16252A"), C("#1B2F34"), C("#16252A"), C("#1B2F34"), C("#223D42"), C("#223D42")};
                for (int i = 0; i < t.length; i++) pine(c, t[i][0], 800, t[i][1], col[i]);
                Paint f = new Paint();
                f.setShader(new LinearGradient(0, 600, 0, 800, Color.argb(0, 255, 255, 255), Color.argb(150, 255, 255, 255),
                        Shader.TileMode.CLAMP));
                c.drawRect(0, 600, 400, 800, f);
                break;
            }
            case 3: {
                ridge(c, 660, 70, 0.012f, 0.5f, C("#05070C"));
                Paint br = P(C("#05070C"));
                br.setStyle(Paint.Style.STROKE);
                br.setStrokeCap(Paint.Cap.ROUND);
                br.setStrokeWidth(8);
                Path p = new Path();
                p.moveTo(420, 460);
                p.quadTo(330, 400, 250, 290);
                c.drawPath(p, br);
                br.setStrokeWidth(5);
                float[][] tw = {{330, 410, 300, 360, 335, 300}, {285, 330, 250, 330, 215, 300}, {390, 440, 370, 380, 395, 330}};
                for (float[] q : tw) {
                    Path s = new Path();
                    s.moveTo(q[0], q[1]);
                    s.quadTo(q[2], q[3], q[4], q[5]);
                    c.drawPath(s, br);
                }
                break;
            }
            case 4:
                ridge(c, 650, 16, 0.03f, 0, C("#0B2A4A"));
                ridge(c, 720, 18, 0.025f, 2, C("#08203A"));
                ridge(c, 780, 14, 0.03f, 4, C("#051528"));
                break;
            case 5:
                towers(c, r, 260, 640, true, C("#0A0714"), C("#FFC36B"));
                break;
            case 6: {
                Paint ring = P(C("#FFD6A8"));
                ring.setStyle(Paint.Style.STROKE);
                ring.setStrokeWidth(5);
                ring.setAlpha(150);
                c.drawOval(new RectF(-50, 440, 450, 510), ring);
                Paint pl = new Paint(Paint.ANTI_ALIAS_FLAG);
                pl.setShader(new RadialGradient(140, 520, 460, new int[]{C("#FF9A6A"), C("#8A2F6E"), C("#1A0B2E")},
                        new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
                c.drawCircle(200, 740, 380, pl);
                Paint rim = P(C("#FFC9A0"));
                rim.setStyle(Paint.Style.STROKE);
                rim.setStrokeWidth(3);
                rim.setAlpha(170);
                c.drawCircle(200, 740, 380, rim);
                break;
            }
            case 7: {
                ridge(c, 560, 50, 0.012f, 0, C("#C9783A"));
                ridge(c, 640, 50, 0.01f, 2, C("#A25626"));
                ridge(c, 720, 40, 0.014f, 4, C("#6E3517"));
                Paint cp = P(C("#1F3B2A"));
                cp.setStyle(Paint.Style.STROKE);
                cp.setStrokeCap(Paint.Cap.ROUND);
                cp.setStrokeJoin(Paint.Join.ROUND);
                cp.setStrokeWidth(28);
                c.drawLine(70, 720, 70, 340, cp);
                cp.setStrokeWidth(20);
                Path a1 = new Path();
                a1.moveTo(70, 570);
                a1.lineTo(30, 570);
                a1.lineTo(30, 470);
                c.drawPath(a1, cp);
                Path a2 = new Path();
                a2.moveTo(70, 510);
                a2.lineTo(112, 510);
                a2.lineTo(112, 420);
                c.drawPath(a2, cp);
                c.drawLine(335, 740, 335, 470, cp);
                cp.setStrokeWidth(14);
                Path a3 = new Path();
                a3.moveTo(335, 640);
                a3.lineTo(305, 640);
                a3.lineTo(305, 570);
                c.drawPath(a3, cp);
                break;
            }
            case 8:
                ridge(c, 740, 30, 0.015f, 0, C("#3E8E5A"));
                ridge(c, 785, 20, 0.02f, 2, C("#2B6E45"));
                break;
            case 9: {
                ridge(c, 775, 14, 0.02f, 0, C("#1A1A2E"));
                Paint t = P(C("#C0392B"));
                c.drawRect(95, 330, 125, 790, t);
                c.drawRect(275, 330, 305, 790, t);
                c.drawRect(75, 352, 325, 374, t);
                Path k = new Path();
                k.moveTo(50, 330);
                k.quadTo(200, 345, 350, 330);
                k.lineTo(366, 300);
                k.quadTo(200, 322, 34, 300);
                k.close();
                c.drawPath(k, P(C("#8E2A20")));
                c.drawRect(95, 760, 125, 790, P(C("#222222")));
                c.drawRect(275, 760, 305, 790, P(C("#222222")));
                Paint br = P(C("#4B2E2A"));
                br.setStyle(Paint.Style.STROKE);
                br.setStrokeWidth(6);
                br.setStrokeCap(Paint.Cap.ROUND);
                Path p = new Path();
                p.moveTo(-10, 40);
                p.quadTo(100, 70, 210, 150);
                c.drawPath(p, br);
                for (int i = 0; i < 40; i++) {
                    float tt = r.nextFloat();
                    c.drawCircle(tt * 210 + r.nextInt(30) - 10, tt * 110 + 40 + r.nextInt(50) - 25, 5 + r.nextInt(8),
                            P(Color.argb(210, 255, 190 + r.nextInt(40), 210)));
                }
                // Perbaikan: v5.1 tidak punya "return" di sini sehingga pulau "Sunset Tropis" ikut tergambar.
                break;
            }
            case 10:
                peaks(c, r, 700, 60, 140, 4, C("#0A4250"));
                ridge(c, 770, 24, 0.02f, 0, C("#052830"));
                break;
            case 11: {
                Paint cl = new Paint(Paint.ANTI_ALIAS_FLAG);
                cl.setColor(Color.argb(160, 255, 255, 255));
                cl.setMaskFilter(new BlurMaskFilter(14, BlurMaskFilter.Blur.NORMAL));
                c.drawOval(new RectF(60, 620, 200, 690), cl);
                c.drawOval(new RectF(240, 680, 370, 740), cl);
                break;
            }
            case 12: {
                Paint um = P(C("#0A0E14"));
                c.drawRoundRect(new RectF(30, 640, 90, 800), 8, 8, um);
                c.drawRoundRect(new RectF(150, 600, 230, 800), 8, 8, um);
                Paint lt = P(C("#FFD98A"));
                lt.setAlpha(220);
                c.drawRect(165, 630, 175, 650, lt);
                c.drawRect(190, 660, 200, 680, lt);
                Path pp = new Path();
                pp.moveTo(0, 800);
                pp.lineTo(0, 740);
                pp.lineTo(400, 760);
                pp.lineTo(400, 800);
                pp.close();
                c.drawPath(pp, P(C("#04060A")));
                break;
            }
            case 13: {
                Paint lv = P(C("#7C5AA6"));
                lv.setAlpha(230);
                for (int i = 0; i < 26; i++) {
                    float x = r.nextFloat() * 400, y = 600 + r.nextFloat() * 190;
                    c.drawOval(new RectF(x, y, x + 9, y + 26), lv);
                }
                c.drawCircle(310, 220, 34, P(Color.WHITE));
                break;
            }
            case 14: {
                peaks(c, r, 480, 100, 200, 4, C("#2E4A5E"));
                Paint ft = new Paint();
                ft.setShader(new LinearGradient(0, 480, 0, 540, Color.argb(120, 220, 235, 240), Color.argb(0, 220, 235, 240),
                        Shader.TileMode.CLAMP));
                c.drawRect(0, 480, 400, 540, ft);
                break;
            }
            case 15: {
                int tp = C("#3A1A0E");
                for (int i = 0; i < 7; i++) pine(c, 10 + i * 58 + r.nextInt(20), 800, 180 + r.nextInt(120), tp);
                glow(c, 200, 300, 140, C("#FFE8C2"), 130);
                c.drawCircle(200, 300, 40, P(C("#FFF2D6")));
                break;
            }
            case 16: {
                Path st = new Path();
                st.moveTo(0, 800);
                st.lineTo(0, 700);
                st.lineTo(70, 660);
                st.lineTo(120, 800);
                st.close();
                c.drawPath(st, P(C("#4A3E30")));
                Paint gl = new Paint();
                gl.setShader(new LinearGradient(0, 620, 0, 800, Color.argb(0, 230, 230, 220), Color.argb(160, 230, 230, 220),
                        Shader.TileMode.CLAMP));
                c.drawRect(0, 620, 400, 800, gl);
                break;
            }
            case 17: {
                peaks(c, r, 660, 130, 250, 5, C("#E8F0F8"));
                peaks(c, r, 740, 90, 180, 4, C("#F8FBFF"));
                ridge(c, 788, 16, 0.02f, 0, Color.WHITE);
                Paint tr = P(C("#2A3A4A"));
                tr.setStrokeCap(Paint.Cap.ROUND);
                tr.setStrokeWidth(3);
                for (int i = 0; i < 5; i++) {
                    float x = 30 + i * 82 + r.nextInt(18);
                    c.drawLine(x, 800, x, 640 + r.nextInt(60), tr);
                }
                break;
            }
            case 18: {
                Paint bt = P(C("#1A1226"));
                Path hull = new Path();
                hull.moveTo(120, 640);
                hull.lineTo(280, 640);
                hull.lineTo(258, 672);
                hull.lineTo(142, 672);
                hull.close();
                c.drawPath(hull, bt);
                c.drawRect(196, 480, 204, 640, bt);
                Path sa = new Path();
                sa.moveTo(206, 484);
                sa.lineTo(206, 610);
                sa.lineTo(252, 610);
                sa.close();
                c.drawPath(sa, P(C("#E8DCC8")));
                Path sb = new Path();
                sb.moveTo(194, 500);
                sb.lineTo(194, 610);
                sb.lineTo(150, 610);
                sb.close();
                c.drawPath(sb, P(C("#D8C8B0")));
                break;
            }
            default: {
                Paint vl = new Paint(Paint.ANTI_ALIAS_FLAG);
                vl.setShader(new RadialGradient(200, 760, 260, new int[]{C("#FFD98A"), C("#E8642A"), C("#4A1410")},
                        new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP));
                Path v = new Path();
                v.moveTo(40, 800);
                v.lineTo(140, 560);
                v.lineTo(200, 590);
                v.lineTo(260, 560);
                v.lineTo(360, 800);
                v.close();
                c.drawPath(v, vl);
                Path lava = new Path();
                lava.moveTo(150, 620);
                lava.lineTo(200, 648);
                lava.lineTo(258, 618);
                lava.lineTo(230, 680);
                lava.lineTo(180, 690);
                lava.close();
                c.drawPath(lava, P(Color.argb(230, 255, 220, 150)));
            }
        }
        return b;
    }

    /** Elemen bergerak (pesawat, burung, kapal, balon, kelopak). */
    static void dyn(Canvas cv, float w, float h, long now, int alpha, int id) {
        if (id != 0 && id != 1 && id != 4 && id != 8 && id != 9) return;
        float t = now / 830f;
        cv.save();
        cv.scale(w / 400f, h / 800f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        switch (id) {
            case 0:
                cv.translate(200 + (float) Math.sin(t) * 16, 296 + (float) Math.sin(t + 1) * 7);
                cv.rotate((float) Math.sin(t) * 1.5f);
                p.setColor(C("#14161A"));
                p.setAlpha(alpha);
                cv.drawPath(PLANE, p);
                break;
            case 1:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.4f);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(C("#16212B"));
                p.setAlpha(alpha);
                for (int i = 0; i < 5; i++) {
                    float x = (now / 45f + i * 95) % 520f - 60;
                    float y = 300 + i * 24 + (float) Math.sin(now / 300f + i) * 8;
                    float f = (float) Math.sin(now / 130f + i) * 5;
                    Path v = new Path();
                    v.moveTo(x - 9, y - f);
                    v.quadTo(x - 4, y - 5, x, y);
                    v.quadTo(x + 4, y - 5, x + 9, y - f);
                    cv.drawPath(v, p);
                }
                break;
            case 4: {
                cv.translate(200 + (float) Math.sin(t) * 10, 470 + (float) Math.sin(t * 1.3f) * 5);
                cv.rotate((float) Math.sin(t) * 2);
                p.setColor(C("#12121E"));
                p.setAlpha(alpha);
                Path hull = new Path();
                hull.moveTo(-55, 0);
                hull.lineTo(55, 0);
                hull.lineTo(38, 22);
                hull.lineTo(-38, 22);
                hull.close();
                cv.drawPath(hull, p);
                cv.drawRect(-2, -140, 2, 0, p);
                p.setColor(C("#F7EBDD"));
                p.setAlpha(alpha);
                Path s1 = new Path();
                s1.moveTo(4, -138);
                s1.lineTo(4, -8);
                s1.lineTo(52, -8);
                s1.close();
                cv.drawPath(s1, p);
                Path s2 = new Path();
                s2.moveTo(-4, -120);
                s2.lineTo(-4, -8);
                s2.lineTo(-42, -8);
                s2.close();
                cv.drawPath(s2, p);
                break;
            }
            case 8: {
                float[][] bl = {{110, 300, 44}, {290, 230, 36}, {220, 520, 52}};
                int[] cl = {C("#E74C3C"), C("#F1C40F"), C("#9B59B6")};
                for (int i = 0; i < 3; i++) {
                    float x = bl[i][0] + (float) Math.sin(t * 0.7f + i * 2) * 10;
                    float y = bl[i][1] + (float) Math.sin(t * 0.9f + i) * 12;
                    float rr = bl[i][2];
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(cl[i]);
                    p.setAlpha(alpha);
                    cv.drawOval(new RectF(x - rr, y - rr * 1.15f, x + rr, y + rr * 1.05f), p);
                    p.setColor(Color.argb(alpha / 3, 255, 255, 255));
                    cv.drawOval(new RectF(x - rr * 0.35f, y - rr * 1.15f, x + rr * 0.35f, y + rr * 1.05f), p);
                    p.setColor(C("#4A3B2A"));
                    p.setAlpha(alpha);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1.2f);
                    cv.drawLine(x - rr * 0.5f, y + rr * 0.95f, x - rr * 0.2f, y + rr * 1.5f, p);
                    cv.drawLine(x + rr * 0.5f, y + rr * 0.95f, x + rr * 0.2f, y + rr * 1.5f, p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(C("#7A4E2D"));
                    p.setAlpha(alpha);
                    cv.drawRect(x - rr * 0.22f, y + rr * 1.5f, x + rr * 0.22f, y + rr * 1.75f, p);
                }
                break;
            }
            default:
                p.setColor(Color.argb(alpha * 3 / 4, 255, 215, 228));
                for (int i = 0; i < 12; i++) {
                    float y = (now / 25f + i * 70) % 860f - 30;
                    float x = 40 + i * 30 + (float) Math.sin(now / 600f + i) * 22;
                    cv.save();
                    cv.rotate((now / 12f + i * 40) % 360f, x, y);
                    cv.drawOval(new RectF(x, y, x + 10, y + 6), p);
                    cv.restore();
                }
        }
        cv.restore();
    }

    // =============================================================== sumber gambar

    static int clampId(int id, int n) {
        return id < 0 || id >= n ? 0 : id;
    }

    static File wpFile(Context x) {
        return new File(x.getFilesDir(), "wp.jpg");
    }

    static File fgFile(Context x) {
        return new File(x.getFilesDir(), "fg.png");
    }

    static File homeFile(Context x) {
        return new File(x.getFilesDir(), "home.jpg");
    }

    static boolean lockCustom(Context x) {
        return new Prefs(x).gi("lm") == 1 && wpFile(x).exists();
    }

    static int dynId(Context x) {
        if (lockCustom(x)) return -1;
        return clampId(new Prefs(x).gi("ld"), DEPTH_NAMES.length);
    }

    static Bitmap lockBg(Context x, int w, int h) {
        if (lockCustom(x)) {
            Bitmap s = Util.decodeFile(wpFile(x), w, h);
            if (s != null) return Util.crop(s, w, h, true);
        }
        Prefs p = new Prefs(x);
        return variant(depthBg(clampId(p.gi("ld"), DEPTH_NAMES.length), w, h), p.gi("ldv"));
    }

    static android.graphics.ColorMatrix matrix(int v) {
        android.graphics.ColorMatrix m = new android.graphics.ColorMatrix();
        switch (v) {
            case 1: tint(m, 1.15f, 0.92f, 0.78f, 18, 0, -10); break;   // Senja
            case 2: tint(m, 0.55f, 0.62f, 0.95f, -10, -6, 12); break;  // Malam
            case 3: tint(m, 1.08f, 0.98f, 1.06f, 14, 6, 16); break;    // Fajar
            case 4: m.setSaturation(1.9f); rot(m, 40); break;          // Neon
            case 5: m.setSaturation(0.55f); tintAdd(m, 40); break;     // Pastel
            case 6: m.setSaturation(0f); break;                        // Monokrom
            case 7: tint(m, 1.12f, 1.0f, 0.82f, 10, 4, -8); break;     // Hangat
            case 8: tint(m, 0.86f, 1.0f, 1.16f, -6, 2, 12); break;     // Dingin
            case 9: rot(m, 75); break;                                 // Ungu
            case 10: rot(m, -95); break;                               // Hijau
            case 11: rot(m, 150); m.postConcat(sat(1.2f)); break;      // Merah
            case 12: tint(m, 1.18f, 1.02f, 0.62f, 20, 10, -20); break; // Emas
            case 13: sepia(m); break;                                  // Sepia
            case 14: rot(m, 180); break;                               // Terbalik (warna komplementer)
            default: break;
        }
        return m;
    }

    private static android.graphics.ColorMatrix sat(float s) {
        android.graphics.ColorMatrix m = new android.graphics.ColorMatrix();
        m.setSaturation(s);
        return m;
    }

    private static void tint(android.graphics.ColorMatrix m, float r, float g, float b, float ar, float ag, float ab) {
        m.postConcat(new android.graphics.ColorMatrix(new float[]{r, 0, 0, 0, ar, 0, g, 0, 0, ag, 0, 0, b, 0, ab, 0, 0, 0, 1, 0}));
    }

    private static void tintAdd(android.graphics.ColorMatrix m, float a) {
        m.postConcat(new android.graphics.ColorMatrix(new float[]{0.85f, 0, 0, 0, a, 0, 0.85f, 0, 0, a, 0, 0, 0.85f, 0, a, 0, 0, 0, 1, 0}));
    }

    private static void rot(android.graphics.ColorMatrix m, float deg) {
        android.graphics.ColorMatrix r = new android.graphics.ColorMatrix();
        // putar hue di ruang YIQ (aproksimasi standar)
        double a = Math.toRadians(deg);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float lr = 0.213f, lg = 0.715f, lb = 0.072f;
        r.set(new float[]{
                lr + c * (1 - lr) + s * (-lr), lg + c * (-lg) + s * (-lg), lb + c * (-lb) + s * (1 - lb), 0, 0,
                lr + c * (-lr) + s * 0.143f, lg + c * (1 - lg) + s * 0.140f, lb + c * (-lb) + s * (-0.283f), 0, 0,
                lr + c * (-lr) + s * (-(1 - lr)), lg + c * (-lg) + s * lg, lb + c * (1 - lb) + s * lb, 0, 0,
                0, 0, 0, 1, 0});
        m.postConcat(r);
    }

    private static void sepia(android.graphics.ColorMatrix m) {
        m.setSaturation(0);
        tint(m, 1.07f, 0.95f, 0.78f, 12, 6, -6);
    }

    /** Terapkan variasi warna ke bitmap (menghasilkan bitmap baru, sumber didaur ulang). */
    static Bitmap variant(Bitmap src, int v) {
        if (src == null || v <= 0 || v >= VARIANTS.length) return src;
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix(v)));
        new Canvas(out).drawBitmap(src, 0, 0, p);
        src.recycle();
        return out;
    }

    /** Thumbnail store: adegan id dengan variasi v. */
    static Bitmap storeThumb(int id, int v, int w, int h) {
        return variant(depthThumb(id, w, h), v);
    }

    static Bitmap lockFg(Context x, int w, int h) {
        Prefs p = new Prefs(x);
        if (!lockCustom(x)) return variant(depthFg(clampId(p.gi("ld"), DEPTH_NAMES.length), w, h), p.gi("ldv"));
        if (!p.gb("dep")) return null;
        Bitmap s = Util.decodeFile(fgFile(x), w, h);
        return s == null ? null : Util.crop(s, w, h, true);
    }

    static Bitmap homeBg(Context x, int w, int h) {
        Prefs p = new Prefs(x);
        int hm = p.gi("hm");
        if (hm == 2) {
            Bitmap s = Util.decodeFile(homeFile(x), w, h);
            if (s != null) return Util.crop(s, w, h, true);
        }
        if (hm == 1) return lockBg(x, w, h);
        return wall(clampId(p.gi("hw"), WALL_NAMES.length), w, h);
    }

    static Bitmap depthThumb(int id, int w, int h) {
        Bitmap b = depthBg(clampId(id, DEPTH_NAMES.length), w, h);
        Bitmap fr = depthFg(clampId(id, DEPTH_NAMES.length), w, h);
        new Canvas(b).drawBitmap(fr, 0, 0, null);
        fr.recycle();
        return b;
    }
}
