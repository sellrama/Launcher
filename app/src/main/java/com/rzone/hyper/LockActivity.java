package com.rzone.hyper;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

/**
 * Activity lockscreen Hyper (tampil di atas keyguard sistem).
 *
 * Perbaikan v6:
 *  - membuka kunci kini kembali ke aplikasi sebelumnya (dulu selalu memaksa membuka
 *    Hyper Launcher walaupun Hyper bukan launcher default);
 *  - tombol senter, notifikasi bisa diketuk, ketuk dua kali untuk mematikan layar;
 *  - sensor & animasi berhenti saat activity tidak terlihat.
 */
public class LockActivity extends Activity {
    private DepthView v;
    private CameraManager cm;
    private String torchId;
    private boolean torchOn;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final CameraManager.TorchCallback torchCb = new CameraManager.TorchCallback() {
        @Override
        public void onTorchModeChanged(String id, boolean enabled) {
            if (id.equals(torchId)) {
                torchOn = enabled;
                if (v != null) v.torchOn = enabled;
            }
        }
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setShowWhenLocked(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        hideBars();
        v = new DepthView(this, false);
        setContentView(v);
        v.onUnlock = () -> dismiss(null);
        v.onApps = () -> dismiss(new Intent(this, HomeActivity.class).putExtra(HomeActivity.EXTRA_DRAWER, true));
        v.onSettings = () -> dismiss(new Intent(this, MainActivity.class));
        v.onCamera = this::camera;
        v.onTorch = this::torch;
        v.onScreenOff = () -> ScreenOff.lock(this);
        v.onNotif = n -> {
            final PendingIntent pi = n.intent;
            final String key = n.key;
            dismiss(null, () -> {
                if (pi != null) {
                    try {
                        if (Build.VERSION.SDK_INT >= 34) {
                            ActivityOptions o = ActivityOptions.makeBasic();
                            o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                            pi.send(this, 0, null, null, null, null, o.toBundle());
                        } else {
                            pi.send();
                        }
                        NotifListener.cancel(key);
                    } catch (Exception ignored) {
                    }
                }
            });
        };
        cm = getSystemService(CameraManager.class);
        try {
            for (String id : cm.getCameraIdList()) {
                Boolean f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (f != null && f) {
                    torchId = id;
                    break;
                }
            }
            cm.registerTorchCallback(torchCb, h);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) hideBars();
    }

    private void torch() {
        if (torchId == null) {
            Toast.makeText(this, "Senter tidak tersedia", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            cm.setTorchMode(torchId, !torchOn);
        } catch (Exception e) {
            Toast.makeText(this, "Senter sedang dipakai aplikasi lain", Toast.LENGTH_SHORT).show();
        }
    }

    private void camera() {
        // Kamera aman bisa dibuka tanpa membuka kunci.
        if (!Util.start(this, new Intent("android.media.action.STILL_IMAGE_CAMERA_SECURE"))) {
            dismiss(new Intent("android.media.action.STILL_IMAGE_CAMERA"));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideBars();
        v.cfg();
        v.reset();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (v != null) v.reset();
    }

    @Override
    protected void onDestroy() {
        try {
            cm.unregisterTorchCallback(torchCb);
        } catch (Exception ignored) {
        }
        if (v != null) v.release();
        super.onDestroy();
    }

    private void dismiss(Intent next) {
        dismiss(next, null);
    }

    private void dismiss(final Intent next, final Runnable after) {
        KeyguardManager km = getSystemService(KeyguardManager.class);
        final Runnable done = () -> finishTo(next, after);
        if (km == null || !km.isKeyguardLocked()) {
            done.run();
            return;
        }
        km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
            @Override
            public void onDismissSucceeded() {
                done.run();
            }

            @Override
            public void onDismissCancelled() {
                v.reset();
            }

            @Override
            public void onDismissError() {
                v.reset();
            }
        });
    }

    private void finishTo(final Intent next, final Runnable after) {
        View dv = getWindow().getDecorView();
        dv.animate().scaleX(1.1f).scaleY(1.1f).alpha(0).setDuration(300).setInterpolator(new DecelerateInterpolator(2f))
                .withEndAction(() -> {
                    if (next != null) Util.start(this, next);
                    if (after != null) after.run();
                    finish();
                    overridePendingTransition(android.R.anim.fade_in, 0);
                }).start();
    }

    @Override
    public void onBackPressed() {
        // tombol kembali tidak membuka kunci
    }
}
