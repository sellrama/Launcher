package com.rzone.hyper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

/**
 * Service latar depan yang memunculkan lockscreen Hyper saat layar mati.
 *
 * Perbaikan v6: tidak memunculkan lockscreen saat sedang telepon (sensor jarak
 * mematikan layar ketika ponsel didekatkan ke telinga), tidak memaksa start
 * bila izin overlay dicabut, dan notifikasi bisa diketuk untuk membuka pengaturan.
 */
public class LockService extends Service {
    private boolean registered;

    private final BroadcastReceiver r = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (!new Prefs(c).enabled()) return;
            if (inCall(c)) return;
            if (!Settings.canDrawOverlays(c)) return;
            try {
                c.startActivity(new Intent(c, LockActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION
                                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
            } catch (Exception ignored) {
            }
        }
    };

    static boolean inCall(Context c) {
        try {
            AudioManager am = c.getSystemService(AudioManager.class);
            int m = am.getMode();
            return m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_IN_COMMUNICATION || m == AudioManager.MODE_RINGTONE;
        } catch (Exception e) {
            return false;
        }
    }

    static void sync(Context c) {
        Intent i = new Intent(c, LockService.class);
        try {
            if (new Prefs(c).enabled()) c.startForegroundService(i);
            else c.stopService(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        if (!new Prefs(this).enabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("hyper", "Hyper Lockscreen", NotificationManager.IMPORTANCE_MIN));
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, "hyper")
                .setContentTitle("Hyper Lockscreen aktif")
                .setContentText("Ketuk untuk membuka pengaturan")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(1, n);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!registered) {
            IntentFilter f2 = new IntentFilter();
            f2.addAction(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f2, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(r, f2);
            registered = true;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (registered) {
            try {
                unregisterReceiver(r);
            } catch (Exception ignored) {
            }
            registered = false;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }
}
