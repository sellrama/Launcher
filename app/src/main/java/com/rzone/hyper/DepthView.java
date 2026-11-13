package com.rzone.hyper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Lockscreen Hyper: wallpaper depth (subjek di depan jam), jam beranimasi, parallax,
 * notifikasi, tombol bawah, AOD.
 *
 * Tata letak (Total UI): 0 Hyper, 1 Pixel, 2 iOS.
 */
public class DepthView extends View implements SensorEventListener {
    static final String[] LAYOUTS = {"Hyper", "Pixel", "iOS"};

    public Runnable onUnlock, onApps, onCamera, onSettings, onTorch, onScreenOff;
    public Consumer<NotifListener.N> onNotif;
    /** Diisi LockActivity agar tombol senter menyala. */
    boolean torchOn;

    private final boolean preview;
    private final float d;
    private final SensorManager sm;
    private final UiStyle us;
    private final GestureDetector gd;
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pb = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect rb = new Rect();
    private final RectF rr = new RectF(), dst = new RectF();
    private final Typeface cond = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private final Calendar cal = Calendar.getInstance();
    private final Locale ID = new Locale("id", "ID");
    private final List<Object[]> notifHits = new ArrayList<>(); // {RectF, N}

    // konfigurasi
    private float csize = 1, cpos = 0.27f, copa = 1, cline = 1.08f, par = 1, ctilt;
    private int cshape, ccolor = Color.WHITE, accent, ui, ac, layout, dtAction = 1, leftBtn;
    private boolean h24 = true, shadow = true, capsule, btnsOn = true, hintOn = true, lockIcon = true, battOn = true,
            fakeFp, notifOn;
    private final Clock clk = new Clock();
    private final Paint black = new Paint();
    private String extra = "";
    private SimpleDateFormat fmt = new SimpleDateFormat("EEEE, d MMM", ID);

    // state
    private Bitmap bg, fg;
    private int dynId = -1, batt, btn;
    private String bsig = "", dateStr = "";
    private final char[] dig = new char[4];
    private final float[] roll = new float[4], bp = new float[2];
    private boolean first = true, drag, woke, fpTouch, fpOk, sensorOn, visible;
    private float p, aod, aodT, tx, ty, cx, cy, y0, fpPulse, clockTop, clockBottom;
    private long last, t0, timeChk, battChk = -10000, nowT;
    private ValueAnimator anim;
    private VelocityTracker vt;

    public DepthView(Context ctx, boolean preview) {
        super(ctx);
        this.preview = preview;
        d = getResources().getDisplayMetrics().density;
        sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        us = new UiStyle(ctx);
        last = t0 = SystemClock.uptimeMillis();
        Arrays.fill(dig, '#');
        gd = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (drag || btn != 0 || aodT > 0.5f) return false;
                if (dtAction == 1) {
                    aodT = 1;
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                } else if (dtAction == 2 && onScreenOff != null) {
                    onScreenOff.run();
                }
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (aodT > 0.5f) return true;
                for (Object[] h : notifHits) {
                    if (((RectF) h[0]).contains(e.getX(), e.getY())) {
                        if (onNotif != null) onNotif.accept((NotifListener.N) h[1]);
                        return true;
                    }
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (drag || aodT >= 0.5f || btn != 0 || onSettings == null) return;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                onSettings.run();
            }
        });
        cfg();
    }

    UiStyle style() {
        return us;
    }

    public void cfg() {
        Prefs q = new Prefs(getContext());
        csize = q.gi("cs") / 100f;
        cpos = q.gi("cy") / 100f;
        cshape = q.gi("sh");
        ccolor = q.gi("cc");
        h24 = q.gb("h24");
        copa = q.gi("co") / 100f;
        shadow = q.gb("csd");
        ctilt = q.gi("ct");
        cline = q.gi("cl") / 100f;
        par = q.gi("lp") / 100f;
        dtAction = q.gi("la");
        capsule = q.gi("dm") == 1;
        btnsOn = q.gb("tb");
        leftBtn = q.gi("lbt");
        hintOn = q.gb("hs");
        lockIcon = q.gb("li");
        battOn = q.gb("bs");
        ui = q.gi("ui");
        ac = q.gi("ac");
        extra = q.txt();
        fakeFp = q.gb("ffp");
        notifOn = q.gb("nlk");
        layout = q.gi("lay");
        fmt = new SimpleDateFormat(layout == 1 ? "EEE, d MMM" : layout == 2 ? "EEEE, d MMMM" : "EEEE, d MMM", ID);
        int g = q.gi("cg");
        if (g != 0) {
            accent = g;
        } else {
            float[] hsv = new float[3];
            Color.colorToHSV(ccolor, hsv);
            hsv[0] = (hsv[0] + 190) % 360;
            hsv[1] = 0.7f;
            hsv[2] = 1;
            accent = Color.HSVToColor(hsv);
        }
        timeChk = 0;
        invalidate();
    }

    public void reload() {
        cfg();
        if (getWidth() == 0) return;
        Context c = getContext();
        Prefs q = new Prefs(c);
        String sig = getWidth() + "," + getHeight() + "," + q.gi("lm") + "," + q.gi("ld") + "," + q.gi("dep") + ","
                + Scene.wpFile(c).lastModified() + "," + Scene.fgFile(c).lastModified();
        if (bg == null || !sig.equals(bsig)) {
            Bitmap oldBg = bg, oldFg = fg;
            try {
                int w = getWidth(), h = getHeight();
                bg = Scene.lockBg(c, w, h);
                fg = Scene.lockFg(c, w, h);
                dynId = Scene.dynId(c);
                bsig = sig;
            } catch (Throwable t) {
                bg = null;
                fg = null;
                dynId = -1;
                bsig = "";
            }
            // Perbaikan: bitmap lama dulu tidak dibebaskan → memori bocor tiap ganti wallpaper.
            if (oldBg != null && oldBg != bg) oldBg.recycle();
            if (oldFg != null && oldFg != fg) oldFg.recycle();
            us.init(bg, getWidth(), getHeight());
        }
        invalidate();
    }

    public void reset() {
        if (anim != null) anim.cancel();
        p = 0;
        aodT = 0;
        aod = 0;
        fpOk = false;
        fpTouch = false;
        t0 = SystemClock.uptimeMillis();
        first = true;
        Arrays.fill(dig, '#');
        reload();
        invalidate();
    }

    /** Animasikan lockscreen keluar (geser ke atas & pudar), lalu jalankan end. */
    void exit(Runnable end) {
        if (p >= 0.99f) {
            if (end != null) end.run();
            return;
        }
        animP(1, end);
    }

    void release() {
        if (bg != null) bg.recycle();
        if (fg != null) fg.recycle();
        bg = fg = null;
        bsig = "";
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        reload();
    }

    // ---------------------------------------------------------------- sensor (hanya saat terlihat)

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateSensor();
    }

    @Override
    protected void onDetachedFromWindow() {
        visible = false;
        updateSensor();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        visible = v == VISIBLE;
        updateSensor();
        if (visible) invalidate();
    }

    private void updateSensor() {
        boolean want = visible && isAttachedToWindow() && par > 0 && !preview;
        if (want == sensorOn || sm == null) return;
        if (want) {
            Sensor s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (s != null) sensorOn = sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
        } else {
            sm.unregisterListener(this);
            sensorOn = false;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        tx += (clamp(e.values[0] / 5f) - tx) * 0.2f;
        ty += (clamp((e.values[1] - 6f) / 5f) - ty) * 0.2f;
    }

    @Override
    public void onAccuracyChanged(Sensor s, int a) {
    }

    private static float clamp(float v) {
        return Math.max(-1, Math.min(1, v));
    }

    // ---------------------------------------------------------------- sentuhan

    private void animP(float to, Runnable end) {
        if (anim != null) anim.cancel();
        final boolean[] cancelled = {false};
        anim = ValueAnimator.ofFloat(p, to);
        anim.setDuration(420);
        anim.setInterpolator(new DecelerateInterpolator(2.2f));
        anim.addUpdateListener(a -> {
            p = (Float) a.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator a) {
                cancelled[0] = true;
            }

            @Override
            public void onAnimationEnd(Animator a) {
                if (!cancelled[0] && end != null) end.run();
            }
        });
        anim.start();
    }

    private float btnY() {
        return getHeight() - d * (layout == 1 ? 76 : 82);
    }

    private int hit(float x, float y) {
        if (!btnsOn) return 0;
        float r = d * 38;
        if (Math.abs(y - btnY()) > r) return 0;
        if (Math.abs(x - d * 58) < r) return 1;
        return Math.abs(x - (getWidth() - d * 58)) < r ? 2 : 0;
    }

    private float fpY() {
        return getHeight() - d * 150;
    }

    private boolean fpHit(float x, float y) {
        return Math.hypot(x - getWidth() / 2f, y - fpY()) < d * 44;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (preview) return false;
        gd.onTouchEvent(e);
        invalidate();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (aodT > 0.5f) {
                    aodT = 0;
                    woke = true;
                    return true;
                }
                woke = false;
                y0 = e.getY();
                drag = false;
                btn = hit(e.getX(), e.getY());
                if (fakeFp) fpTouch = fpHit(e.getX(), e.getY());
                if (anim != null) anim.cancel();
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (vt != null) vt.addMovement(e);
                if (woke) return true;
                float dy = y0 - e.getY();
                if (dy > d * 12) {
                    drag = true;
                    btn = 0;
                    fpTouch = false;
                }
                if (drag) p = Math.max(0, Math.min(1, dy / (getHeight() * 0.7f)));
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                if (fakeFp && up && !woke && !drag && !fpOk && fpTouch && fpHit(e.getX(), e.getY())) {
                    fpTouch = false;
                    fpOk = true;
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    postDelayed(() -> {
                        if (onUnlock != null) onUnlock.run();
                    }, 260);
                    recycleVt();
                    return true;
                }
                fpTouch = false;
                if (!woke && vt != null) {
                    vt.computeCurrentVelocity(1000);
                    float vy = vt.getYVelocity();
                    if (drag) {
                        if (p > 0.22f || vy < -1500) animP(1, onUnlock);
                        else animP(0, null);
                    } else if (btn != 0 && up && hit(e.getX(), e.getY()) == btn) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        Runnable r = btn == 1 ? (leftBtn == 1 ? onTorch : onApps) : onCamera;
                        if (r != null) r.run();
                    }
                }
                recycleVt();
                drag = false;
                btn = 0;
                return true;
            }
        }
        return true;
    }

    private void recycleVt() {
        if (vt != null) {
            vt.recycle();
            vt = null;
        }
    }

    // ---------------------------------------------------------------- jam

    private void updateTime(long now) {
        cal.setTimeInMillis(System.currentTimeMillis());
        int h = cal.get(Calendar.HOUR_OF_DAY);
        if (!h24) {
            h %= 12;
            if (h == 0) h = 12;
        }
        String s = String.format(Locale.US, "%02d%02d", h, cal.get(Calendar.MINUTE));
        for (int i = 0; i < 4; i++) {
            if (dig[i] != s.charAt(i)) {
                dig[i] = s.charAt(i);
                roll[i] = first ? -0.1f - i * 0.12f : 0;
            }
        }
        first = false;
        String ds = fmt.format(cal.getTime());
        dateStr = ds.isEmpty() ? ds : ds.substring(0, 1).toUpperCase(ID) + ds.substring(1);
        if (now - battChk > 8000) {
            battChk = now;
            try {
                batt = ((BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE))
                        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            } catch (Exception e) {
                batt = 0;
            }
        }
    }

    private String battStr(String sep) {
        return battOn && batt > 0 ? sep + batt + "%" : "";
    }

    private float clockTopY(float h) {
        float base = layout == 2 ? 0.10f : layout == 1 ? 0.075f : 0.02f;
        return h * (base + cpos * 0.2f);
    }

    private boolean analogFace() {
        return cshape == 4 || cshape == 5 || cshape == 6 || cshape == 7;
    }

    private void drawClock(Canvas c, float w, float h, float e) {
        clk.w = w;
        clk.h = h;
        clk.d = d;
        clk.csize = csize;
        clk.copa = copa;
        clk.cline = cline;
        clk.e = e;
        clk.aod = aod;
        clk.px = cx;
        clk.py = cy;
        clk.color = ccolor;
        clk.accent = accent;
        clk.ui = ui;
        clk.ac = ac;
        clk.shadow = shadow;
        clk.dig = dig;
        clk.roll = roll;
        clk.now = nowT;
        cal.setTimeInMillis(System.currentTimeMillis());
        clk.cal = cal;
        clk.us = us;
        clk.dst = dst;
        clk.draw(c, Math.max(0, Math.min(Clock.N - 1, cshape)), clockTopY(h));
        clockTop = clk.top;
        clockBottom = clk.bottom;
    }

    // ---------------------------------------------------------------- notifikasi

    private void drawNotifs(Canvas c, float w, float h, float a) {
        notifHits.clear();
        List<NotifListener.N> items = NotifListener.snapshot();
        if (items.isEmpty() || a <= 0.01f) return;
        int n = Math.min(3, items.size());
        float nh = d * 64, gap = d * 10;
        float ny;
        if (layout == 2) {
            // iOS: menumpuk dari bawah, di atas tombol
            float bottom = (btnsOn ? btnY() - d * 46 : h - d * 90);
            if (fakeFp) bottom = Math.min(bottom, fpY() - d * 44);
            ny = bottom - n * nh - (n - 1) * gap;
            ny = Math.max(ny, clockBottom + d * 70);
        } else if (layout == 1) {
            ny = clockBottom + d * 40;
        } else {
            // Perbaikan: v5.1 menaruh notifikasi tepat di atas tanggal sehingga saling menimpa.
            ny = clockBottom + d * (extra.isEmpty() ? 66 : 92);
        }
        float limit = layout == 2 ? h - d * 60 : h - d * (fakeFp ? 190 : 140);
        float rad = layout == 2 ? d * 20 : layout == 1 ? d * 24 : d * 22;
        for (int i = 0; i < n; i++) {
            NotifListener.N it = items.get(i);
            if (ny + nh > limit) break;
            RectF box = new RectF(d * 14, ny, w - d * 14, ny + nh);
            us.draw(c, box, rad, ui, ac, dst, cx, cy, 0, a * 0.96f);
            int on = UiStyle.onPanel(ui, ac);
            float tx0 = box.left + d * 16, tw = box.width() - d * 32;
            tp.reset();
            tp.setAntiAlias(true);
            tp.setTextAlign(Paint.Align.LEFT);
            tp.setTextSize(d * 12);
            tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tp.setColor(on == Color.WHITE ? (layout == 2 ? Color.argb(200, 255, 255, 255) : accent) : on);
            tp.setAlpha((int) (a * 235));
            String app = layout == 2 ? it.app.toUpperCase(ID) : it.app;
            c.drawText(TextUtils.ellipsize(app, tp, tw * 0.7f, TextUtils.TruncateAt.END).toString(), tx0, box.top + d * 22, tp);
            tp.setTextAlign(Paint.Align.RIGHT);
            tp.setAlpha((int) (a * 160));
            c.drawText(ago(it.when), box.right - d * 16, box.top + d * 22, tp);
            tp.setTextAlign(Paint.Align.LEFT);
            tp.setColor(on);
            tp.setAlpha((int) (a * 240));
            tp.setTextSize(d * 14);
            c.drawText(TextUtils.ellipsize(it.title, tp, tw, TextUtils.TruncateAt.END).toString(), tx0, box.top + d * 41, tp);
            tp.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            tp.setTextSize(d * 12.5f);
            tp.setAlpha((int) (a * 190));
            c.drawText(TextUtils.ellipsize(it.text, tp, tw, TextUtils.TruncateAt.END).toString(), tx0, box.top + d * 57, tp);
            notifHits.add(new Object[]{box, it});
            ny += nh + gap;
        }
        if (items.size() > n && layout == 2) {
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setColor(Color.WHITE);
            tp.setTextSize(d * 12);
            tp.setAlpha((int) (a * 170));
            c.drawText("+" + (items.size() - n) + " notifikasi lain", w / 2, ny + d * 8, tp);
        }
    }

    private String ago(long when) {
        long m = (System.currentTimeMillis() - when) / 60000;
        if (m < 1) return "sekarang";
        if (m < 60) return m + " mnt";
        if (m < 24 * 60) return (m / 60) + " j";
        return (m / 1440) + " h";
    }

    private void drawFingerprint(Canvas c, float w, float h, float a) {
        float cx0 = w / 2, cy0 = fpY(), R = d * 30;
        fpPulse += 0.045f;
        float glowR = R * (1.15f + (float) Math.sin(fpPulse) * 0.08f);
        fp.reset();
        fp.setAntiAlias(true);
        if (fpTouch || fpOk) {
            fp.setShader(new RadialGradient(cx0, cy0, glowR, UiStyle.alpha(ac, 160), 0, Shader.TileMode.CLAMP));
            c.drawCircle(cx0, cy0, glowR, fp);
        }
        fp.setShader(null);
        fp.setStyle(Paint.Style.STROKE);
        fp.setStrokeWidth(d * 2.2f);
        fp.setStrokeCap(Paint.Cap.ROUND);
        fp.setColor(fpOk ? ac : Color.WHITE);
        fp.setAlpha((int) ((fpOk ? 255 : 190) * a));
        double ph = fpOk ? 0 : fpPulse % (Math.PI * 2);
        for (int i = 0; i < 3; i++) {
            float r2 = R * (0.42f + i * 0.24f);
            float rot = fpOk ? 0 : (float) Math.toDegrees(ph) + i * 24;
            rr.set(cx0 - r2, cy0 - r2, cx0 + r2, cy0 + r2);
            c.drawArc(rr, rot + 200, fpOk ? 360 : 190, false, fp);
        }
        rr.set(cx0 - R * 0.16f, cy0 - R * 0.16f, cx0 + R * 0.16f, cy0 + R * 0.16f);
        c.drawRoundRect(rr, R * 0.12f, R * 0.12f, fp);
    }

    private void drawLockIcon(Canvas c, float lx, float ly, float a) {
        float lift = Math.min(1, p * 5) * 4 * d;
        fp.reset();
        fp.setAntiAlias(true);
        fp.setColor(Color.WHITE);
        fp.setAlpha((int) (235 * a));
        rr.set(lx - d * 8, ly, lx + d * 8, ly + d * 12);
        c.drawRoundRect(rr, d * 3, d * 3, fp);
        fp.setStyle(Paint.Style.STROKE);
        fp.setStrokeWidth(d * 2);
        fp.setStrokeCap(Paint.Cap.ROUND);
        float ay = ly - d * 5 - lift;
        rr.set(lx - d * 5, ay - d * 5, lx + d * 5, ay + d * 5);
        c.drawArc(rr, 180, 180, false, fp);
        c.drawLine(lx - d * 5, ay, lx - d * 5, ly, fp);
        c.drawLine(lx + d * 5, ay, lx + d * 5, ly, fp);
    }

    private void textShadow(Paint q) {
        q.setShadowLayer(10, 0, 2, Color.argb(140, 0, 0, 0));
    }

    private void drawUi(Canvas c, float w, float h, long now, float e) {
        float a = e * (1 - aod);
        // ---------- ikon gembok ----------
        if (lockIcon) {
            if (layout == 1 && !fakeFp) drawLockIcon(c, w / 2, h - d * 150, a);
            else if (layout != 1) drawLockIcon(c, w / 2, d * (layout == 2 ? 48 : 60), a);
        }
        // ---------- tanggal ----------
        tp.reset();
        tp.setAntiAlias(true);
        tp.setColor(Color.WHITE);
        float extraY;
        if (layout == 1) {
            // Pixel: tanggal & baterai kecil di kiri atas
            tp.setTextAlign(Paint.Align.LEFT);
            tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tp.setTextSize(d * 17);
            textShadow(tp);
            tp.setAlpha((int) (a * 255));
            float y = d * 64;
            c.drawText(dateStr + battStr("  ·  "), d * 26, y, tp);
            extraY = y + d * 24;
            if (!extra.isEmpty()) {
                tp.setTextSize(d * 14);
                tp.setAlpha((int) (a * 220));
                c.drawText(extra, d * 26, extraY, tp);
            }
        } else if (layout == 2 && !analogFace()) {
            // iOS: tanggal di atas jam
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tp.setTextSize(d * 19);
            if (shadow) textShadow(tp);
            tp.setColor(ccolor);
            tp.setAlpha((int) (a * 235 * copa));
            float y = clockTop - d * 10;
            c.drawText(dateStr + battStr("  ·  "), w / 2, y, tp);
            if (!extra.isEmpty()) {
                tp.setTextSize(d * 15);
                tp.setAlpha((int) (a * 220));
                c.drawText(extra, w / 2, clockBottom + d * 34, tp);
            }
        } else {
            String s2 = dateStr + battStr("  •  ");
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(d * 16);
            tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            float ty0 = clockBottom + d * 40 + (1 - e) * 20 * d;
            if (capsule) {
                float cw = tp.measureText(s2) + d * 44, ch = d * 44;
                rr.set(w / 2 - cw / 2, ty0 - d * 28, w / 2 + cw / 2, ty0 - d * 28 + ch);
                us.draw(c, rr, ch / 2, ui, ac, dst, cx, cy, 0, a);
                tp.setColor(UiStyle.onPanel(ui, ac));
                ty0 = rr.centerY() + d * 5.5f;
            } else {
                textShadow(tp);
            }
            {
                tp.setAlpha((int) (a * 255));
                c.drawText(s2, w / 2, ty0, tp);
            }
            if (!extra.isEmpty()) {
                tp.setColor(Color.WHITE);
                tp.setTextSize(d * 14);
                tp.setAlpha((int) (220 * a));
                c.drawText(extra, w / 2, ty0 + d * 28, tp);
            }
        }
        // ---------- petunjuk & bar ----------
        tp.reset();
        tp.setAntiAlias(true);
        tp.setColor(Color.WHITE);
        tp.setTextAlign(Paint.Align.CENTER);
        if (hintOn) {
            tp.setTextSize(d * (layout == 2 ? 13 : 12));
            tp.setShadowLayer(8, 0, 2, Color.argb(140, 0, 0, 0));
            tp.setAlpha((int) (210 * a));
            float hy = layout == 2 ? h - d * 52 : h - d * 128;
            if (layout == 1) hy = h - d * 112;
            c.drawText("Geser ke atas untuk membuka", w / 2, hy + (float) Math.sin(now / 340f) * 6 * d, tp);
        }
        fp.reset();
        fp.setAntiAlias(true);
        fp.setColor(Color.WHITE);
        fp.setAlpha((int) (a * 200));
        float bw = layout == 2 ? d * 68 : d * 55;
        c.drawRoundRect(w / 2 - bw, h - d * 20, w / 2 + bw, h - d * 15.5f, d * 2.5f, d * 2.5f, fp);
        tp.setShadowLayer(0, 0, 0, 0);
        tp.setTextSize(d * 10);
        tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tp.setAlpha((int) (110 * a));
        c.drawText("Powered by RZONEDEV", w / 2, h - d * 30, tp);
        // ---------- notifikasi / sidik jari ----------
        if (notifOn) drawNotifs(c, w, h, a);
        else notifHits.clear();
        if (fakeFp) drawFingerprint(c, w, h, a);
        // ---------- tombol bawah ----------
        if (btnsOn) {
            float by = btnY();
            for (int k = 0; k < 2; k++) {
                bp[k] += ((btn == k + 1 ? 1f : 0f) - bp[k]) * 0.35f;
                float bx = k == 0 ? d * 58 : w - d * 58;
                float sc = 1 - bp[k] * 0.1f;
                c.save();
                c.scale(sc, sc, bx, by);
                float br = layout == 2 ? d * 25 : d * 27;
                rr.set(bx - br, by - br, bx + br, by + br);
                boolean lit = k == 0 && leftBtn == 1 && torchOn;
                float rad = layout == 1 ? d * 18 : br;
                if (lit) {
                    fp.reset();
                    fp.setAntiAlias(true);
                    fp.setColor(Color.WHITE);
                    fp.setAlpha((int) (a * 245));
                    c.drawRoundRect(rr, rad, rad, fp);
                } else {
                    us.draw(c, rr, rad, ui, ac, dst, cx, cy, bp[k], a);
                }
                int ic = lit ? 0xFF111111 : UiStyle.onPanel(ui, ac);
                fp.reset();
                fp.setAntiAlias(true);
                fp.setColor(ic);
                fp.setAlpha((int) (a * 255));
                fp.setStrokeWidth(d * 2.2f);
                if (k == 0 && leftBtn == 1) {
                    Glyph.draw(c, Glyph.FLASH, bx, by, d * 11, fp, d, new RectF());
                } else if (k == 0) {
                    for (int m = 0; m < 4; m++) {
                        float ox = bx + (m % 2 == 0 ? -9 : 2) * d, oy = by + (m < 2 ? -9 : 2) * d;
                        c.drawRoundRect(ox, oy, ox + d * 7, oy + d * 7, d * 2, d * 2, fp);
                    }
                } else {
                    fp.setStyle(Paint.Style.STROKE);
                    c.drawRoundRect(bx - d * 11, by - d * 7, bx + d * 11, by + d * 9, d * 4, d * 4, fp);
                    c.drawCircle(bx, by + d, d * 4, fp);
                    c.drawLine(bx - d * 4, by - d * 7, bx - d * 2, by - d * 10, fp);
                    c.drawLine(bx + d * 4, by - d * 7, bx + d * 2, by - d * 10, fp);
                    c.drawLine(bx - d * 2, by - d * 10, bx + d * 2, by - d * 10, fp);
                }
                c.restore();
            }
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        long now = SystemClock.uptimeMillis();
        nowT = now;
        float dt = Math.min(0.05f, (now - last) / 1000f);
        last = now;
        float lp = preview ? 0 : par;
        cx += (tx * lp + (float) Math.sin(now / 1700f) * 0.22f * par - cx) * Math.min(1, dt * 6);
        cy += (ty * lp + (float) Math.cos(now / 2100f) * 0.14f * par - cy) * Math.min(1, dt * 6);
        aod += (aodT - aod) * Math.min(1, dt * 4);
        for (int i = 0; i < 4; i++) roll[i] += dt / 0.75f;
        if (now - timeChk > 500) {
            timeChk = now;
            updateTime(now);
        }
        float ip = Math.max(0, Math.min(1, (now - t0) / 1100f));
        float e = 1 - (float) Math.pow(1 - ip, 3);
        // Seluruh lockscreen mengikuti jari ke atas dan memudar, memperlihatkan layar di belakangnya
        // (jendela lockscreen transparan) — transisi ke home terasa menyatu.
        float slide = p * h * 0.7f;
        int sv = p > 0 ? c.saveLayerAlpha(0, 0, w, h, (int) (255 * Math.pow(1 - p, 0.9))) : c.save();
        if (p > 0) {
            float k = 1 - p * 0.05f;
            c.scale(k, k, w / 2, h);
        }
        c.translate(0, -slide);
        c.drawRect(0, 0, w, h + slide + 1, black);
        if (bg != null && !bg.isRecycled()) {
            c.save();
            c.translate(-cx * 9 * d, -cy * 9 * d);
            c.scale(1.12f, 1.12f, w / 2, h / 2);
            pb.setAlpha((int) ((1 - aod) * 255));
            c.drawBitmap(bg, 0, 0, pb);
            c.restore();
        }
        dst.set(w / 2 - w * 0.56f - cx * 9 * d, h / 2 - h * 0.56f - cy * 9 * d,
                w / 2 + w * 0.56f - cx * 9 * d, h / 2 + h * 0.56f - cy * 9 * d);
        c.save();
        c.translate(cx * 18 * d, cy * 18 * d);
        if (ctilt != 0) c.rotate(ctilt, w / 2, 0.22f * h);
        drawClock(c, w, h, e);
        c.restore();
        if (aod < 0.99f) {
            int al = (int) (e * 255 * (1 - aod));
            c.save();
            c.translate(-cx * 34 * d, -cy * 30 * d + (1 - e) * h * 0.12f);
            c.scale(1.12f, 1.12f, w / 2, h / 2);
            if (fg != null && !fg.isRecycled()) {
                pb.setAlpha(al);
                c.drawBitmap(fg, 0, 0, pb);
            }
            if (dynId >= 0) Scene.dyn(c, w, h, now, al, dynId);
            c.restore();
        }
        drawUi(c, w, h, now, e);
        c.restoreToCount(sv);
        if (!visible && !preview) return; // jangan menggambar terus saat tidak terlihat
        if (aod > 0.98f && aodT > 0.5f && !analogFace()) postInvalidateDelayed(1000);
        else postInvalidateOnAnimation();
    }
}
