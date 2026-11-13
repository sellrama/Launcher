package com.rzone.hyper;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Tema .zth (format tema HiOS/XOS Tecno/Infinix): zip berisi
 *   NamaTema/icons/*.png      — ikon per aplikasi (nama kelas/paket dengan "_" atau nama aplikasi)
 *   NamaTema/icons/icon_mask.png, icon_bg.png, folder_bg.png
 *   NamaTema/wallpaper/wallpaper.jpg, lockscreen_wallpaper.jpg
 *   NamaTema/preview/*.jpg
 */
final class Zth {
    private Zth() {
    }

    static File root(Context c) {
        File f = new File(c.getFilesDir(), "themes");
        //noinspection ResultOfMethodCallIgnored
        f.mkdirs();
        return f;
    }

    static List<File> installed(Context c) {
        List<File> out = new ArrayList<>();
        File[] l = root(c).listFiles();
        if (l != null) for (File f : l) if (f.isDirectory() && (new File(f, "icons").isDirectory() || new File(f, "wallpaper").isDirectory())) out.add(f);
        out.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    static File active(Context c) {
        String n = new Prefs(c).str("zth");
        if (n.isEmpty()) return null;
        File f = new File(root(c), n);
        return f.isDirectory() ? f : null;
    }

    static File preview(File theme, boolean lock) {
        File p = new File(theme, "preview");
        File[] l = p.listFiles();
        if (l == null) return null;
        for (File f : l) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (lock == n.contains("lock") && (n.endsWith(".jpg") || n.endsWith(".png") || n.endsWith(".webp"))) return f;
        }
        return l.length > 0 ? l[0] : null;
    }

    static String displayName(Context c, Uri u) {
        try (Cursor cur = c.getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {
        }
        String p = u.getLastPathSegment();
        return p == null ? "tema.zth" : p;
    }

    private static String safe(String s) {
        String r = s.replaceAll("[^A-Za-z0-9 _.()-]", "_").trim();
        if (r.isEmpty() || r.equals(".") || r.equals("..")) r = "tema";
        return r.length() > 60 ? r.substring(0, 60) : r;
    }

    /** Pasang file .zth. Mengembalikan nama folder tema. Jalankan di thread latar. */
    static String install(Context c, Uri u) throws Exception {
        String fallback = safe(displayName(c, u).replaceAll("(?i)\\.zth$", ""));
        File tmp = new File(root(c), ".installing");
        deleteAll(tmp);
        //noinspection ResultOfMethodCallIgnored
        tmp.mkdirs();
        String themeName = null;
        int files = 0;
        byte[] buf = new byte[64 * 1024];
        try (InputStream in = c.getContentResolver().openInputStream(u); ZipInputStream z = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName().replace('\\', '/');
                if (name.contains("../") || name.startsWith("/")) continue; // tolak path berbahaya
                String[] parts = name.split("/");
                // cari segmen "icons" / "wallpaper" / "preview"; segmen sebelumnya = nama tema
                int k = -1;
                for (int i = 0; i < parts.length - 1; i++) {
                    String seg = parts[i].toLowerCase(Locale.ROOT);
                    if (seg.equals("icons") || seg.equals("wallpaper") || seg.equals("preview")) {
                        k = i;
                        break;
                    }
                }
                if (k < 0) continue;
                if (themeName == null && k > 0) themeName = safe(parts[k - 1]);
                String sub = parts[k].toLowerCase(Locale.ROOT);
                String file = safe(parts[parts.length - 1]);
                String low = file.toLowerCase(Locale.ROOT);
                if (!(low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".webp"))) continue;
                File dir = new File(tmp, sub);
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                try (FileOutputStream o = new FileOutputStream(new File(dir, file))) {
                    int n;
                    long total = 0;
                    while ((n = z.read(buf)) > 0) {
                        total += n;
                        if (total > 40L * 1024 * 1024) throw new Exception("berkas terlalu besar");
                        o.write(buf, 0, n);
                    }
                }
                files++;
            }
        }
        if (files == 0) {
            deleteAll(tmp);
            throw new Exception("bukan tema .zth yang dikenali");
        }
        String nm = themeName != null ? themeName : fallback;
        File dst = new File(root(c), nm);
        deleteAll(dst);
        if (!tmp.renameTo(dst)) throw new Exception("gagal menyimpan tema");
        synchronized (Zth.class) {
            index.remove(dst.getPath());
        }
        return nm;
    }

    static void delete(Context c, File theme) {
        if (theme.equals(active(c))) new Prefs(c).str("zth", "");
        deleteAll(theme);
        synchronized (Zth.class) {
            index.remove(theme.getPath());
        }
    }

    private static void deleteAll(File f) {
        if (f == null || !f.exists()) return;
        File[] l = f.listFiles();
        if (l != null) for (File x : l) deleteAll(x);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    // ------------------------------------------------------------------ pencocokan ikon

    private static final Map<String, Map<String, File>> index = new HashMap<>();
    private static final Map<String, String> ALIAS = new HashMap<>();

    static {
        String[][] a = {
                {"com.android.settings", "settings"}, {"com.android.chrome", "chrome"}, {"com.whatsapp", "whatsapp"},
                {"com.google.android.youtube", "youtube"}, {"com.google.android.gm", "gmail"},
                {"com.google.android.apps.maps", "googlemaps"}, {"com.google.android.apps.photos", "googlephotos"},
                {"com.google.android.googlequicksearchbox", "google"}, {"com.android.vending", "playstore"},
                {"com.google.android.deskclock", "clock"}, {"com.google.android.dialer", "phone"},
                {"com.android.dialer", "phone"}, {"com.google.android.calculator", "calculator"},
                {"com.google.android.GoogleCamera", "camera"}, {"com.android.camera", "camera"},
                {"com.android.camera2", "camera"}, {"com.twitter.android", "twitter"}, {"com.instagram.android", "instagram"},
                {"com.google.android.apps.docs", "googledrive"}, {"com.google.android.play.games", "playgames"},
                {"com.tencent.mm", "wechat"}, {"com.google.android.keep", "notes"}, {"com.google.android.apps.recorder", "recorder"},
        };
        for (String[] x : a) ALIAS.put(x[0], x[1]);
        String[][] w = { // nama Indonesia → nama file umum di tema
                {"setelan", "settings"}, {"pengaturan", "settings"}, {"kamera", "camera"}, {"jam", "clock"},
                {"telepon", "phone"}, {"kalkulator", "calculator"}, {"kompas", "compass"}, {"catatan", "notes"},
                {"perekam", "recorder"}, {"tema", "theme"}, {"asisten", "asisten"}, {"peta", "googlemaps"},
                {"foto", "googlephotos"}, {"pesan", "messages"}, {"kontak", "contacts"}, {"kalender", "calendar"}};
        for (String[] x : w) ALIAS.put("label:" + x[0], x[1]);
    }

    static String norm(String s) {
        String r = s.toLowerCase(Locale.ROOT).replaceAll("\\(\\d+\\)", "").replaceAll("\\.png$|\\.jpg$|\\.webp$", "");
        return r.replaceAll("[^a-z0-9]", "");
    }

    private static synchronized Map<String, File> index(File theme) {
        Map<String, File> m = index.get(theme.getPath());
        if (m != null) return m;
        m = new HashMap<>();
        File[] l = new File(theme, "icons").listFiles();
        if (l != null) for (File f : l) {
            String k = norm(f.getName());
            if (!k.isEmpty() && !m.containsKey(k)) m.put(k, f);
        }
        index.put(theme.getPath(), m);
        return m;
    }

    static File iconFile(File theme, String pkg, String cls, String label) {
        Map<String, File> m = index(theme);
        if (m.isEmpty()) return null;
        String[] keys = {
                norm(cls), norm(pkg + "_" + cls.substring(cls.lastIndexOf('.') + 1)), norm(pkg),
                ALIAS.containsKey(pkg) ? ALIAS.get(pkg) : null,
                label == null ? null : norm(label),
                label == null ? null : "google" + norm(label),
                label == null ? null : ALIAS.get("label:" + norm(label))};
        for (String k : keys) {
            if (k == null || k.isEmpty()) continue;
            File f = m.get(k);
            if (f != null) return f;
        }
        return null;
    }

    private static Bitmap load(File f, int sz) {
        if (f == null || !f.exists()) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int s = 1;
            while (o.outWidth / (s * 2) >= sz) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o2);
            if (b == null) return null;
            if (b.getWidth() == sz && b.getHeight() == sz) return b;
            Bitmap out = Bitmap.createScaledBitmap(b, sz, sz, true);
            if (out != b) b.recycle();
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Ikon untuk aplikasi menurut tema; aplikasi tanpa ikon tema dibentuk dengan icon_mask + icon_bg. */
    static Bitmap icon(Context c, File theme, String pkg, String cls, String label, Drawable app, int sz) {
        Bitmap b = load(iconFile(theme, pkg, cls, label), sz);
        if (b != null) return b;
        return masked(theme, app, sz);
    }

    static Bitmap masked(File theme, Drawable app, int sz) {
        Bitmap out = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Bitmap mask = load(new File(theme, "icons/icon_mask.png"), sz);
        Bitmap bg = load(new File(theme, "icons/icon_bg.png"), sz);
        // latar: warna utama ikon (tanpa putih), lalu icon_bg tema di atasnya
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        int col = app != null ? Icons.vivid(app) : 0xFF3A4150;
        p.setShader(new android.graphics.LinearGradient(0, 0, 0, sz, UiStyle.mix(0xFFFFFFFF, col, 0.12f),
                UiStyle.mix(0xFF000000, col, 0.25f), android.graphics.Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, sz, sz, p);
        p.setShader(null);
        if (bg != null) cv.drawBitmap(bg, 0, 0, p);
        if (app != null) {
            int in = Math.round(sz * 0.16f);
            try {
                Drawable fg = app instanceof android.graphics.drawable.AdaptiveIconDrawable
                        ? ((android.graphics.drawable.AdaptiveIconDrawable) app).getForeground() : app;
                Rect r = app instanceof android.graphics.drawable.AdaptiveIconDrawable
                        ? new Rect(-Math.round(sz * 0.08f), -Math.round(sz * 0.08f), sz + Math.round(sz * 0.08f), sz + Math.round(sz * 0.08f))
                        : new Rect(in, in, sz - in, sz - in);
                fg.setBounds(r);
                fg.draw(cv);
            } catch (Exception ignored) {
            }
        }
        if (mask != null) {
            Paint mp = new Paint(Paint.FILTER_BITMAP_FLAG);
            mp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            cv.drawBitmap(mask, 0, 0, mp);
            mask.recycle();
        } else {
            Bitmap shaped = Icons.make(null, new android.graphics.drawable.BitmapDrawable(null, out), sz, 1, 0, 0);
            out.recycle();
            out = shaped;
        }
        if (bg != null) bg.recycle();
        return out;
    }

    static Bitmap folderBg(Context c, int sz) {
        File t = active(c);
        return t == null ? null : load(new File(t, "icons/folder_bg.png"), sz);
    }

    /** Ikon aplikasi dari sumber yang aktif: tema .zth → icon pack → gaya Hyper. */
    static Bitmap appIcon(Context x, Prefs pf, String pkg, String cls, String label, Drawable d, int sz) {
        File t = active(x);
        if (t != null) {
            try {
                return icon(x, t, pkg, cls, label, d, sz);
            } catch (Throwable ignored) {
            }
        }
        return IconPack.make(x, pf.str("ipk"), pkg, cls, d, sz, pf.gi("is"), pf.gi("ik"), pf.gi("ac"));
    }

    /** Pasang wallpaper tema ke layar utama & lockscreen Hyper. */
    static void applyWallpapers(Context c, File theme) throws Exception {
        File home = new File(theme, "wallpaper/wallpaper.jpg");
        File lock = new File(theme, "wallpaper/lockscreen_wallpaper.jpg");
        if (!lock.exists()) lock = home;
        if (!home.exists()) home = lock;
        Prefs pr = new Prefs(c);
        if (home.exists()) {
            copy(home, Scene.homeFile(c));
            pr.si("hm", 2);
        }
        if (lock.exists()) {
            copy(lock, Scene.wpFile(c));
            //noinspection ResultOfMethodCallIgnored
            Scene.fgFile(c).delete();
            pr.si("lm", 1);
            pr.si("dep", 1);
        }
    }

    private static void copy(File a, File b) throws Exception {
        File tmp = new File(b.getPath() + ".tmp");
        try (InputStream in = new java.io.FileInputStream(a); FileOutputStream o = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        }
        if (!tmp.renameTo(b)) throw new Exception("gagal menyalin");
    }
}
