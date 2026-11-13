package com.rzone.hyper;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dukungan icon pack (ADW / Nova / Lawnchair / GO).
 *
 * Perbaikan v6: v5.1 hanya menebak nama drawable dari potongan terakhir nama paket
 * (mis. "whatsapp"), sehingga hampir semua ikon tidak ditemukan. Sekarang memakai
 * appfilter.xml milik icon pack (ComponentInfo{paket/kelas} → drawable), seperti launcher lain.
 */
final class IconPack {
    private static String loadedPkg = null;
    private static final Map<String, String> MAP = new HashMap<>();   // "pkg/cls" → drawable
    private static final Map<String, String> PKG = new HashMap<>();   // "pkg" → drawable (cadangan)
    private static Resources res;

    private IconPack() {
    }

    static List<ResolveInfo> list(Context x) {
        PackageManager pm = x.getPackageManager();
        List<ResolveInfo> out = new ArrayList<>();
        String[] acts = {"org.adw.launcher.THEMES", "com.gau.go.launcherex.theme", "com.novalauncher.THEME",
                "com.novaline.launcher.THEME", "app.lawnchair.icons.THEMES", "com.teslacoilsw.launcher.THEME"};
        for (String act : acts) {
            try {
                for (ResolveInfo r : pm.queryIntentActivities(new Intent(act), 0)) {
                    if (r.activityInfo != null && !contains(out, r.activityInfo.packageName)) out.add(r);
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static boolean contains(List<ResolveInfo> l, String pkg) {
        for (ResolveInfo r : l) if (r.activityInfo != null && pkg.equals(r.activityInfo.packageName)) return true;
        return false;
    }

    static String label(Context x, ResolveInfo r) {
        try {
            return r.loadLabel(x.getPackageManager()).toString();
        } catch (Exception e) {
            return r.activityInfo != null ? r.activityInfo.packageName : "?";
        }
    }

    private static synchronized boolean load(Context x, String packPkg) {
        if (packPkg.equals(loadedPkg)) return res != null;
        loadedPkg = packPkg;
        MAP.clear();
        PKG.clear();
        res = null;
        try {
            res = x.getPackageManager().getResourcesForApplication(packPkg);
        } catch (Exception e) {
            return false;
        }
        XmlPullParser xp = null;
        InputStream in = null;
        try {
            int id = res.getIdentifier("appfilter", "xml", packPkg);
            if (id != 0) {
                xp = res.getXml(id);
            } else {
                in = res.getAssets().open("appfilter.xml");
                xp = XmlPullParserFactory.newInstance().newPullParser();
                xp.setInput(in, "UTF-8");
            }
            int ev = xp.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && "item".equals(xp.getName())) {
                    String comp = xp.getAttributeValue(null, "component");
                    String draw = xp.getAttributeValue(null, "drawable");
                    if (comp != null && draw != null && comp.startsWith("ComponentInfo{") && comp.endsWith("}")) {
                        String cn = comp.substring(14, comp.length() - 1);
                        int s = cn.indexOf('/');
                        if (s > 0) {
                            String pkg = cn.substring(0, s);
                            String cls = cn.substring(s + 1);
                            if (cls.startsWith(".")) cls = pkg + cls;
                            MAP.put(pkg + "/" + cls, draw);
                            if (!PKG.containsKey(pkg)) PKG.put(pkg, draw);
                        }
                    }
                }
                ev = xp.next();
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    static synchronized Drawable get(Context x, String packPkg, String appPkg, String appCls) {
        if (packPkg == null || packPkg.isEmpty() || appPkg == null) return null;
        if (!load(x, packPkg) || res == null) return null;
        String name = MAP.get(appPkg + "/" + appCls);
        if (name == null) name = PKG.get(appPkg);
        if (name == null) return null;
        try {
            int id = res.getIdentifier(name, "drawable", packPkg);
            if (id == 0) id = res.getIdentifier(name, "mipmap", packPkg);
            if (id == 0) return null;
            Drawable d = res.getDrawableForDensity(id, x.getResources().getDisplayMetrics().densityDpi, null);
            return d != null ? d : res.getDrawable(id, null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Ikon dari icon pack (apa adanya), atau ikon Hyper bila tidak ada di pack. */
    static Bitmap make(Context x, String packPkg, String appPkg, String appCls, Drawable fallback, int sz,
                       int shape, int style, int accent) {
        Drawable d = get(x, packPkg, appPkg, appCls);
        if (d != null) {
            try {
                Bitmap out = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(out);
                int in = Math.round(sz * 0.04f);
                d.setBounds(in, in, sz - in, sz - in);
                d.draw(c);
                return out;
            } catch (Exception ignored) {
            }
        }
        return Icons.make(x, fallback, sz, shape, style, accent);
    }

    static synchronized void invalidate() {
        loadedPkg = null;
        MAP.clear();
        PKG.clear();
        res = null;
    }
}
