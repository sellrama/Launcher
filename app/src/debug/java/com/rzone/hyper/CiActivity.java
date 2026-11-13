package com.rzone.hyper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Hook CI (debug saja). Contoh:
 *  am start -n com.rzone.hyper/.CiActivity --ei os 2 --es set "sh=3,lay=2" --es open lock
 */
public class CiActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        Prefs pr = new Prefs(this);
        if (in.hasExtra("os")) pr.applyOs(in.getIntExtra("os", Prefs.OS_PIXEL));
        String set = in.getStringExtra("set");
        if (set != null) {
            for (String kv : set.split(",")) {
                int i = kv.indexOf('=');
                if (i > 0) pr.si(kv.substring(0, i).trim(), Integer.parseInt(kv.substring(i + 1).trim()));
            }
        }
        String open = in.getStringExtra("open");
        if (open != null) {
            Intent t = null;
            switch (open) {
                case "lock":
                    t = new Intent(this, LockActivity.class);
                    break;
                case "home":
                    t = new Intent(this, HomeActivity.class);
                    break;
                case "drawer":
                    t = new Intent(this, HomeActivity.class).putExtra(HomeActivity.EXTRA_DRAWER, true);
                    break;
                case "tira":
                    t = new Intent(this, TiraActivity.class).putExtra("ci_q", in.getStringExtra("q"));
                    break;
                default:
                    t = new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_PAGE, open);
            }
            t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(t);
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
