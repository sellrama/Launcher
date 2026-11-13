package com.rzone.hyper;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Animasi "nyawa" Tira: gelombang warna-warni yang mengalir.
 * IDLE: bernapas pelan; LISTEN: mengikuti volume suara; THINK: berputar cepat; SPEAK: berdenyut.
 */
final class TiraWave extends View {
    static final int IDLE = 0, LISTEN = 1, THINK = 2, SPEAK = 3;
    private static final int[] COLS = {0xFF7C4DFF, 0xFF00E5FF, 0xFFFF4FA3, 0xFFFFC14D, 0xFF4D8BFF};
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float d;
    private int state;
    private float level, shown, energy;
    private final long t0 = SystemClock.uptimeMillis();

    TiraWave(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        glow.setMaskFilter(new BlurMaskFilter(d * 18, BlurMaskFilter.Blur.NORMAL));
        setLayerType(LAYER_TYPE_SOFTWARE, null); // BlurMaskFilter butuh software layer
    }

    void state(int s) {
        state = s;
        invalidate();
    }

    /** rms dari SpeechRecognizer (-2..10). */
    void level(float rms) {
        level = Math.max(0, Math.min(1, (rms + 2) / 12f));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float t = (SystemClock.uptimeMillis() - t0) / 1000f;
        float target = state == LISTEN ? 0.35f + level * 0.65f : state == THINK ? 0.55f : state == SPEAK ? 0.45f + 0.25f * (float) Math.abs(Math.sin(t * 6)) : 0.22f;
        energy += (target - energy) * 0.12f;
        shown += (level - shown) * 0.3f;
        float speed = state == THINK ? 3.2f : state == LISTEN ? 2.2f : 1.1f;
        float mid = h * 0.62f;
        for (int k = 0; k < COLS.length; k++) {
            float ph = t * speed * (0.7f + k * 0.18f) + k * 1.3f;
            float amp = h * 0.38f * energy * (0.55f + 0.45f * (float) Math.sin(t * 1.7f + k));
            path.reset();
            path.moveTo(0, h);
            for (float x = 0; x <= w; x += d * 4) {
                float nx = x / w;
                float env = (float) Math.sin(Math.PI * nx); // menipis di tepi
                float y = mid - env * amp * (float) (0.6 * Math.sin(nx * 6.0 + ph) + 0.4 * Math.sin(nx * 11.0 - ph * 1.3));
                path.lineTo(x, y);
            }
            path.lineTo(w, h);
            path.close();
            int col = COLS[k];
            glow.setColor(UiStyle.alpha(col, 110));
            c.drawPath(path, glow);
            p.setShader(new LinearGradient(0, mid - amp, 0, h, UiStyle.alpha(col, 170), UiStyle.alpha(col, 0), Shader.TileMode.CLAMP));
            c.drawPath(path, p);
        }
        // titik cahaya di tengah
        float r = d * (10 + 16 * energy);
        p.setShader(new RadialGradient(w / 2, mid, r * 2.2f, new int[]{Color.WHITE, UiStyle.alpha(0xFFB9A6FF, 90), 0},
                new float[]{0, 0.35f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(w / 2, mid, r * 2.2f, p);
        p.setShader(null);
        if (isShown()) postInvalidateOnAnimation();
    }
}
