package com.rzone.hyper;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Membaca notifikasi untuk lockscreen & badge ikon launcher.
 *
 * Perbaikan v6:
 *  - notifikasi yang sudah ada dimuat saat listener terhubung (dulu daftar kosong sampai ada notifikasi baru);
 *  - menghapus satu notifikasi tidak lagi menghapus semua notifikasi lain dari aplikasi yang sama;
 *  - notifikasi menyimpan PendingIntent sehingga bisa diketuk dari lockscreen;
 *  - hitungan per aplikasi untuk badge ikon.
 */
public class NotifListener extends NotificationListenerService {

    static final class N {
        String key, pkg, app, title, text;
        long when;
        PendingIntent intent;
    }

    /** Notifikasi terbaru (paling baru di depan), maksimal 8. Akses wajib synchronized(ITEMS). */
    static final List<N> ITEMS = new ArrayList<>();
    /** Jumlah notifikasi per paket. Akses wajib synchronized(ITEMS). */
    static final Map<String, Integer> COUNTS = new HashMap<>();
    /** Dipanggil di main thread saat daftar berubah. */
    static Runnable onChange;
    private static NotifListener inst;
    private static final Handler H = new Handler(Looper.getMainLooper());

    static boolean granted(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(new ComponentName(c, NotifListener.class).flattenToString());
    }

    static int count(String pkg) {
        synchronized (ITEMS) {
            Integer n = COUNTS.get(pkg);
            return n == null ? 0 : n;
        }
    }

    static List<N> snapshot() {
        synchronized (ITEMS) {
            return new ArrayList<>(ITEMS);
        }
    }

    /** Tutup notifikasi (setelah dibuka dari lockscreen). */
    static void cancel(String key) {
        NotifListener l = inst;
        if (l == null || key == null) return;
        try {
            l.cancelNotification(key);
        } catch (Exception ignored) {
        }
    }

    static String label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private static boolean relevant(StatusBarNotification sbn) {
        if (sbn == null || sbn.isOngoing()) return false;
        Notification n = sbn.getNotification();
        if (n == null) return false;
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return false;
        return !"com.rzone.hyper".equals(sbn.getPackageName());
    }

    private N toN(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        CharSequence t = n.extras != null ? n.extras.getCharSequence(Notification.EXTRA_TITLE) : null;
        CharSequence x = n.extras != null ? n.extras.getCharSequence(Notification.EXTRA_TEXT) : null;
        if (x == null && n.extras != null) x = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        N it = new N();
        it.key = sbn.getKey();
        it.pkg = sbn.getPackageName();
        it.app = label(getPackageManager(), it.pkg);
        it.title = t == null ? "" : t.toString();
        it.text = x == null ? "" : x.toString();
        it.when = sbn.getPostTime();
        it.intent = n.contentIntent;
        return it;
    }

    @Override
    public void onListenerConnected() {
        inst = this;
        rebuild();
    }

    @Override
    public void onListenerDisconnected() {
        if (inst == this) inst = null;
        synchronized (ITEMS) {
            ITEMS.clear();
            COUNTS.clear();
        }
        notifyChange();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!relevant(sbn)) return;
        N it = toN(sbn);
        synchronized (ITEMS) {
            for (int i = ITEMS.size() - 1; i >= 0; i--) if (ITEMS.get(i).key.equals(it.key)) ITEMS.remove(i);
            ITEMS.add(0, it);
            while (ITEMS.size() > 8) ITEMS.remove(ITEMS.size() - 1);
        }
        recount();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        synchronized (ITEMS) {
            for (int i = ITEMS.size() - 1; i >= 0; i--) if (ITEMS.get(i).key.equals(sbn.getKey())) ITEMS.remove(i);
        }
        recount();
    }

    private void rebuild() {
        List<N> l = new ArrayList<>();
        try {
            StatusBarNotification[] all = getActiveNotifications();
            if (all != null) for (StatusBarNotification s : all) if (relevant(s)) l.add(toN(s));
        } catch (Exception ignored) {
        }
        l.sort((a, b) -> Long.compare(b.when, a.when));
        synchronized (ITEMS) {
            ITEMS.clear();
            for (int i = 0; i < l.size() && i < 8; i++) ITEMS.add(l.get(i));
        }
        recount();
    }

    private void recount() {
        Map<String, Integer> m = new HashMap<>();
        try {
            StatusBarNotification[] all = getActiveNotifications();
            if (all != null) for (StatusBarNotification s : all) {
                if (!relevant(s)) continue;
                Integer n = m.get(s.getPackageName());
                m.put(s.getPackageName(), n == null ? 1 : n + 1);
            }
        } catch (Exception ignored) {
        }
        synchronized (ITEMS) {
            COUNTS.clear();
            COUNTS.putAll(m);
        }
        notifyChange();
    }

    private static void notifyChange() {
        H.post(() -> {
            Runnable r = onChange;
            if (r != null) r.run();
        });
    }
}
