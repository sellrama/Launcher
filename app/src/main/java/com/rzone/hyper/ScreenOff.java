package com.rzone.hyper;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/**
 * Layanan aksesibilitas kecil yang hanya dipakai untuk mematikan layar
 * (gestur ketuk dua kali). Tidak membaca isi layar.
 */
public class ScreenOff extends AccessibilityService {
    private static ScreenOff inst;

    static boolean enabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(new ComponentName(c, ScreenOff.class).flattenToString());
    }

    /** Matikan layar; bila belum diizinkan, arahkan ke pengaturan aksesibilitas. */
    static void lock(Context c) {
        if (Build.VERSION.SDK_INT < 28) {
            Toast.makeText(c, "Butuh Android 9 atau lebih baru", Toast.LENGTH_SHORT).show();
            return;
        }
        if (inst != null && inst.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)) return;
        Toast.makeText(c, "Aktifkan \"Hyper\" di Aksesibilitas untuk mematikan layar", Toast.LENGTH_LONG).show();
        Util.start(c, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    @Override
    protected void onServiceConnected() {
        inst = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (inst == this) inst = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
