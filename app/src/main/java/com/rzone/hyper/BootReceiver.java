package com.rzone.hyper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Menyalakan ulang service lockscreen setelah HP menyala atau aplikasi diperbarui. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i == null ? null : i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a)) {
            if (new Prefs(c).enabled()) LockService.sync(c);
        }
    }
}
