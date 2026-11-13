package com.rzone.hyper;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Otak" Tira: perintah HP dijalankan langsung di perangkat (cepat & tanpa internet),
 * selebihnya ditanyakan ke AI di https://api.nexray.eu.cc/ai/claude.
 */
final class TiraBrain {
    /** Otak utama + otak cadangan, semuanya dari Nexray. Dicoba berurutan sampai ada jawaban. */
    static final String BASE = "https://api.nexray.eu.cc/ai/";
    // Diuji lewat CI: claude, copilot & gemini aktif; deepseek kadang gagal (tetap dicoba terakhir).
    static final String[] BRAINS = {"claude", "copilot", "gemini", "deepseek"};
    /** Otak terakhir yang berhasil (dicoba lebih dulu berikutnya). */
    private static volatile String lastGood = "claude";
    private static final Locale ID = new Locale("id", "ID");

    /** Hasil satu jawaban. action dijalankan di UI thread setelah teks tampil (boleh null). */
    static final class Reply {
        final String text;
        final Runnable action;

        Reply(String t, Runnable a) {
            text = t;
            action = a;
        }
    }

    private final Context c;
    private final List<String[]> history = new ArrayList<>(); // {user, tira}

    TiraBrain(Context c) {
        this.c = c.getApplicationContext();
    }

    // =========================================================== perintah lokal

    /** Perintah yang bisa dijawab langsung. null bila harus ditanyakan ke AI. */
    /** Pola kata panggil: "hai tira", "halo tira", "hallo tira", "hey tira", "oke tira", "tira". */
    static final Pattern WAKE = Pattern.compile("^\\s*(?:(?:ha+i+|ha+l+o+|hel+o+|he+y|he+i|oke?|ok)\\s*,?\\s*)?(?:tira|tiara|tyra|teera)\\b[\\s,.!]*",
            Pattern.CASE_INSENSITIVE);

    /** Buang kata panggil di depan kalimat. */
    static String stripWake(String q) {
        Matcher m = WAKE.matcher(q);
        return m.find() ? q.substring(m.end()).trim() : q.trim();
    }

    /** Teks setelah kata panggil (null bila tidak ada kata panggil). */
    static String afterWake(String q) {
        Matcher m = Pattern.compile("\\b(?:ha+i+|ha+l+o+|hel+o+|he+y|he+i|oke?|ok)\\s*,?\\s*(?:tira|tiara|tyra)\\b[\\s,.!]*",
                Pattern.CASE_INSENSITIVE).matcher(q);
        return m.find() ? q.substring(m.end()).trim() : null;
    }

    static boolean hasWake(String q) {
        return Pattern.compile("\\b(?:ha+i+|ha+l+o+|hel+o+|he+y|he+i|oke?|ok)\\s*,?\\s*(?:tira|tiara|tyra)\\b", Pattern.CASE_INSENSITIVE)
                .matcher(q).find();
    }

    Reply local(String raw) {
        String stripped = stripWake(raw);
        if (stripped.isEmpty()) return new Reply("Ya, Tira di sini. Mau dibantu apa?", null);
        final String q = stripped.toLowerCase(ID).replaceAll("[?!.,]+$", "");
        Matcher m;
        if (q.matches(".*\\b(jam berapa|pukul berapa|sekarang jam)\\b.*")) {
            return new Reply("Sekarang jam " + new SimpleDateFormat("HH:mm", ID).format(new Date()) + ".", null);
        }
        if (q.matches(".*\\b(hari apa|tanggal berapa|tanggal hari ini|hari ini tanggal)\\b.*")) {
            return new Reply("Hari ini " + new SimpleDateFormat("EEEE, d MMMM yyyy", ID).format(new Date()) + ".", null);
        }
        if (q.matches(".*\\b(baterai|batre|batrei|battery)\\b.*")) {
            int b = 0;
            try {
                b = ((BatteryManager) c.getSystemService(Context.BATTERY_SERVICE)).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            } catch (Exception ignored) {
            }
            return new Reply("Baterai kamu " + b + "%" + (b <= 20 ? ". Sudah waktunya dicas ya!" : "."), null);
        }
        if (q.matches(".*\\b(senter|flashlight|lampu senter)\\b.*")) {
            final boolean on = !q.matches(".*\\b(matikan|mati|off|padamkan)\\b.*");
            return new Reply(on ? "Senter dinyalakan." : "Senter dimatikan.", () -> torch(on));
        }
        if (q.matches(".*\\b(kunci layar|matikan layar|lock screen)\\b.*")) {
            return new Reply("Oke, layar dikunci.", () -> ScreenOff.lock(c));
        }
        if ((m = Pattern.compile("(?:alarm|bangunkan|bangunin)(?: aku| saya)?(?: jam| pukul)? (\\d{1,2})(?:[:.](\\d{2}))?\\s*(pagi|siang|sore|malam)?").matcher(q)).find()) {
            int h = Integer.parseInt(m.group(1));
            int mi = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            String part = m.group(3);
            if (part != null && (part.equals("sore") || part.equals("malam")) && h < 12) h += 12;
            if (part != null && part.equals("siang") && h < 11) h += 12;
            final int fh = h % 24, fm = mi % 60;
            return new Reply(String.format(ID, "Alarm diatur jam %02d:%02d.", fh, fm), () -> Util.start(c,
                    new Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, fh)
                            .putExtra(AlarmClock.EXTRA_MINUTES, fm).putExtra(AlarmClock.EXTRA_MESSAGE, "Tira")
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)));
        }
        if ((m = Pattern.compile("timer (\\d+)\\s*(detik|menit|jam)").matcher(q)).find()) {
            int n = Integer.parseInt(m.group(1));
            int sec = m.group(2).equals("detik") ? n : m.group(2).equals("menit") ? n * 60 : n * 3600;
            final int fs = sec;
            return new Reply("Timer " + n + " " + m.group(2) + " dimulai.", () -> Util.start(c,
                    new Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, fs)
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)));
        }
        if ((m = Pattern.compile("^(?:telepon|telfon|telpon|panggil|call) ([+\\d][\\d \\-]{4,})$").matcher(q)).find()) {
            final String num = m.group(1).replaceAll("[ \\-]", "");
            return new Reply("Membuka panggilan ke " + num + ".", () -> Util.start(c, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + num))));
        }
        if ((m = Pattern.compile("^(?:cari|carikan|google|search)(?: di (?:google|web|internet))? (?!video|vidio|film|klip)(.+)$").matcher(q)).find()) {
            final String s = m.group(1);
            return new Reply("Mencari \"" + s + "\"...", () -> Util.webSearch(c, s));
        }
        if ((m = Pattern.compile("^(?:putar|putarkan|play|buka|bukakan|tonton|nonton|cari|carikan|tampilkan)(?: aku| saya)? (?:video|vidio|film|klip)(?: tentang| soal| dari)? ?(.*)$").matcher(q)).find()
                || (m = Pattern.compile("^(?:tonton|nonton) (.+)$").matcher(q)).find()) {
            final String s = m.group(1).trim();
            if (s.isEmpty()) return new Reply("Membuka YouTube.", () -> openYoutube(""));
            return new Reply("Memutar video \"" + s + "\" di YouTube.", () -> openYoutube(s));
        }
        if ((m = Pattern.compile("^(?:putar|putarkan|play) (?:lagu |musik )?(.+)$").matcher(q)).find()) {
            final String s = m.group(1);
            return new Reply("Memutar " + s + ".", () -> {
                Intent i = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).putExtra(android.app.SearchManager.QUERY, s);
                if (!Util.start(c, i)) Util.webSearch(c, s + " lagu");
            });
        }
        if (q.matches(".*\\b(wifi|wi-fi)\\b.*")) return new Reply("Membuka pengaturan Wi-Fi.", () -> Util.start(c, new Intent(Settings.ACTION_WIFI_SETTINGS)));
        if (q.matches(".*\\bbluetooth\\b.*")) return new Reply("Membuka pengaturan Bluetooth.", () -> Util.start(c, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        if ((m = Pattern.compile("(?:tema|total ui|tampilan|ganti ke|ubah ke|pakai) ?(ios|iphone|pixel|pixelos|android|custom)").matcher(q)).find()) {
            String o = m.group(1);
            final int os = o.startsWith("i") ? Prefs.OS_IOS : o.equals("custom") ? Prefs.OS_CUSTOM : Prefs.OS_PIXEL;
            return new Reply("Siap! Tampilan diganti ke " + Prefs.OS_NAMES[os] + ".", () -> {
                new Prefs(c).applyOs(os);
                Util.start(c, new Intent(c, HomeActivity.class));
            });
        }
        Reply cz = customize(q);
        if (cz != null) return cz;
        if ((m = Pattern.compile("^(?:buka|bukakan|jalankan|open|launch) (?:aplikasi |app )?(.+)$").matcher(q)).find()) {
            final String name = m.group(1).trim();
            final ResolveInfo ri = findApp(name);
            if (ri != null) {
                String label = ri.loadLabel(c.getPackageManager()).toString();
                return new Reply("Membuka " + label + ".", () -> Util.start(c, new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER).setClassName(ri.activityInfo.packageName, ri.activityInfo.name)));
            }
            if (name.contains("kamera") || name.contains("camera"))
                return new Reply("Membuka kamera.", () -> Util.start(c, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)));
            if (name.contains("pengaturan") || name.contains("setting"))
                return new Reply("Membuka pengaturan.", () -> Util.start(c, new Intent(Settings.ACTION_SETTINGS)));
            return new Reply("Aku tidak menemukan aplikasi \"" + name + "\" di HP kamu.", null);
        }
        if (q.matches("^(hai|halo|hello|hi|hey|p|assalamualaikum)( tira)?$")) {
            return new Reply("Hai! Aku Tira. Mau dibantu apa hari ini?", null);
        }
        return null;
    }

    private static final String[][] COLORS = {{"putih", "FFFFFFFF"}, {"hitam", "FF111111"}, {"merah", "FFFF4D4D"},
            {"biru", "FF0A84FF"}, {"hijau", "FF30D158"}, {"kuning", "FFFFD60A"}, {"oranye", "FFFF9F0A"}, {"jingga", "FFFF9F0A"},
            {"ungu", "FFB388FF"}, {"pink", "FFFF6FB1"}, {"merah muda", "FFFF6FB1"}, {"emas", "FFFFC400"}, {"cyan", "FF00E5FF"},
            {"tosca", "FF1DE9B6"}, {"abu", "FF9AA0A6"}, {"biru muda", "FF7DD3FC"}};

    private static Integer color(String q) {
        for (int i = COLORS.length - 1; i >= 0; i--) // nama dua kata dicek lebih dulu
            if (q.contains(COLORS[i][0])) return (int) Long.parseLong(COLORS[i][1], 16);
        return null;
    }

    private Reply set(String key, int v, String msg) {
        Prefs p = new Prefs(c);
        p.touch(key);
        p.si(key, v);
        return new Reply(msg, null);
    }

    /** Kustomisasi tampilan lewat Tira (jam, warna, wallpaper, gaya UI, tata letak, tombol, AOD). */
    private Reply customize(String q) {
        Matcher m;
        if ((m = Pattern.compile("^(?:ganti|ubah|pakai|set|atur)?\\s*(?:desain |model |gaya )?jam(?: lockscreen)?(?: ke| jadi| menjadi)? (.+)$").matcher(q)).find()
                && !q.contains("warna") && !q.matches(".*\\d.*")) {
            String n = m.group(1).trim();
            for (int i = 0; i < Clock.N; i++) {
                if (Clock.NAMES[i].toLowerCase(ID).contains(n) || n.contains(Clock.NAMES[i].toLowerCase(ID)))
                    return set("sh", i, "Jam lockscreen diganti ke " + Clock.NAMES[i] + ".");
            }
            return new Reply("Desain jam \"" + n + "\" tidak ada. Contoh: Neon, Analog Klasik, iOS Tebal, Flip Retro, Terminal.", null);
        }
        if (q.contains("warna jam") || q.contains("warna angka jam")) {
            Integer col = color(q);
            if (col != null) return set("cc", col, "Warna jam diganti.");
        }
        if (q.contains("warna aksen") || q.contains("warna tema") || q.contains("warna utama")) {
            Integer col = color(q);
            if (col != null) return set("ac", col, "Warna aksen diganti.");
        }
        if ((m = Pattern.compile("^(?:ganti |ubah |pakai |pasang )?wallpaper(?: lockscreen| depth)?(?: ke| jadi)? (.+)$").matcher(q)).find()) {
            String n = m.group(1).trim();
            for (int i = 0; i < Scene.DEPTH_NAMES.length; i++) {
                String dn = Scene.DEPTH_NAMES[i].toLowerCase(ID);
                if (n.contains(dn) || dn.contains(n)) {
                    int v = 0;
                    for (int k = 1; k < Scene.VARIANTS.length; k++) if (n.contains(Scene.VARIANTS[k].toLowerCase(ID))) v = k;
                    Prefs p = new Prefs(c);
                    p.si("lm", 0);
                    p.si("ld", i);
                    p.si("ldv", v);
                    return new Reply("Wallpaper lockscreen diganti ke " + Scene.DEPTH_NAMES[i] + " (" + Scene.VARIANTS[v] + ").", null);
                }
            }
        }
        if ((m = Pattern.compile("gaya (?:ui|panel|tampilan) (?:ke |jadi )?(.+)$").matcher(q)).find()) {
            String n = m.group(1).trim();
            for (int i = 0; i < UiStyle.NAMES.length; i++)
                if (UiStyle.NAMES[i].toLowerCase(ID).contains(n) || n.contains(UiStyle.NAMES[i].toLowerCase(ID)))
                    return set("ui", i, "Gaya UI diganti ke " + UiStyle.NAMES[i] + ".");
        }
        if ((m = Pattern.compile("tata letak (?:lockscreen |lock )?(?:ke |jadi )?(hyper|pixel|ios)").matcher(q)).find()) {
            String n = m.group(1);
            int v = n.equals("ios") ? 2 : n.equals("pixel") ? 1 : 0;
            return set("lay", v, "Tata letak lockscreen diganti ke " + DepthView.LAYOUTS[v] + ".");
        }
        if (q.matches(".*\\b(aod|always on)\\b.*")) {
            boolean off = q.matches(".*\\b(matikan|mati|off|nonaktifkan)\\b.*");
            return set("la", off ? 0 : 1, off ? "AOD dimatikan." : "AOD aktif. Ketuk dua kali di lockscreen untuk masuk AOD.");
        }
        if (q.matches(".*\\b(sembunyikan|hilangkan|matikan|tampilkan|munculkan|nyalakan) (?:tombol )?(senter|kamera|tombol bawah)\\b.*")) {
            boolean show = q.matches(".*\\b(tampilkan|munculkan|nyalakan)\\b.*");
            return set("tb", show ? 1 : 0, show ? "Tombol bawah lockscreen ditampilkan." : "Tombol bawah lockscreen disembunyikan.");
        }
        if ((m = Pattern.compile("ukuran jam (\\d{2,3})").matcher(q)).find()) {
            int v = Math.max(50, Math.min(150, Integer.parseInt(m.group(1))));
            return set("cs", v, "Ukuran jam jadi " + v + "%.");
        }
        return null;
    }

    /** Buka/cari video di YouTube (aplikasi bila ada, kalau tidak lewat peramban). */
    private void openYoutube(String q) {
        if (q.isEmpty()) {
            Intent li = c.getPackageManager().getLaunchIntentForPackage("com.google.android.youtube");
            if (li != null && Util.start(c, li)) return;
            Util.start(c, new Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com")));
            return;
        }
        Intent yt = new Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube")
                .putExtra(android.app.SearchManager.QUERY, q);
        if (Util.start(c, yt)) return;
        Util.start(c, new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))));
    }

    private ResolveInfo findApp(String name) {
        PackageManager pm = c.getPackageManager();
        List<ResolveInfo> l = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        String n = name.toLowerCase(ID);
        ResolveInfo best = null;
        int bestScore = 0;
        for (ResolveInfo r : l) {
            String lb = r.loadLabel(pm).toString().toLowerCase(ID);
            int s = lb.equals(n) ? 100 : lb.startsWith(n) ? 80 : lb.contains(n) ? 60 : n.contains(lb) && lb.length() > 2 ? 40 : 0;
            if (s > bestScore) {
                bestScore = s;
                best = r;
            }
        }
        return best;
    }

    private void torch(boolean on) {
        try {
            CameraManager cm = c.getSystemService(CameraManager.class);
            for (String id : cm.getCameraIdList()) {
                Boolean f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (f != null && f) {
                    cm.setTorchMode(id, on);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    // =========================================================== AI (jaringan, panggil di thread latar)

    String ask(String q) {
        q = stripWake(q);
        StringBuilder p = new StringBuilder();
        p.append("Kamu adalah Tira, asisten AI pribadi di HP pengguna (bagian dari launcher RZONE Hyper buatan RZONEDEV). ")
                .append("Jawab dalam bahasa Indonesia yang santai, ramah, jelas dan singkat (maksimal 4 kalimat kecuali diminta detail). ")
                .append("Jangan pakai format markdown dan jangan pakai emoji. ")
                .append("Waktu lokal pengguna sekarang: ")
                .append(new SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", ID).format(new Date()))
                .append(" (zona ").append(java.util.TimeZone.getDefault().getID()).append(").\n");
        int from = Math.max(0, history.size() - 4);
        for (int i = from; i < history.size(); i++) {
            p.append("Pengguna: ").append(cut(history.get(i)[0], 200)).append('\n');
            p.append("Tira: ").append(cut(history.get(i)[1], 300)).append('\n');
        }
        p.append("Pengguna: ").append(cut(q, 600)).append("\nTira:");
        String prompt = p.toString();
        String ans = null;
        java.util.List<String> order = new ArrayList<>();
        order.add(lastGood);
        for (String b : BRAINS) if (!b.equals(lastGood)) order.add(b);
        boolean net = false;
        for (String brain : order) {
            try {
                String body = get(BASE + brain + "?text=" + URLEncoder.encode(prompt, "UTF-8"));
                net = true;
                String a = clean(parse(body));
                if (a.isEmpty() || looksBroken(a)) continue;
                lastGood = brain;
                ans = a;
                break;
            } catch (Exception ignored) {
            }
        }
        if (ans != null) history.add(new String[]{q, ans});
        else if (net) ans = "Hmm, semua otak Tira sedang sibuk. Coba tanya lagi sebentar ya.";
        else ans = "Tira lagi susah terhubung ke internet. Cek koneksi kamu lalu coba lagi ya.";
        return ans;
    }

    /** Jawaban yang sebenarnya pesan error dari server (dianggap gagal, coba otak lain). */
    private static boolean looksBroken(String a) {
        String l = a.toLowerCase(Locale.ROOT);
        if (l.startsWith("<!doctype") || l.startsWith("<html")) return true;
        if (a.length() < 120 && (l.contains("error") || l.contains("not found") || l.contains("limit")
                || l.contains("invalid") || l.contains("forbidden") || l.contains("unauthorized") || l.contains("gagal")))
            return true;
        return l.equals("false") || l.equals("null") || l.equals("true");
    }

    private static String cut(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private static String get(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(45000);
        h.setRequestProperty("Accept", "application/json, text/plain, */*");
        h.setRequestProperty("User-Agent", "RZONE-Hyper-Tira/1.0");
        try {
            int code = h.getResponseCode();
            InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
            if (in == null) throw new Exception("HTTP " + code);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            String s = bo.toString("UTF-8");
            if (code >= 400 && s.trim().isEmpty()) throw new Exception("HTTP " + code);
            return s;
        } finally {
            h.disconnect();
        }
    }

    private static final String[] KEYS = {"result", "response", "answer", "reply", "message", "text", "content", "output", "data", "msg"};

    /** Ambil teks jawaban dari JSON apa pun bentuknya; kalau bukan JSON, pakai teks apa adanya. */
    static String parse(String body) {
        String b = body == null ? "" : body.trim();
        try {
            if (b.startsWith("{")) {
                JSONObject j = new JSONObject(b);
                if (j.has("status") && !j.optBoolean("status", true)) return ""; // {"status":false,"error":...}
                String s = extract(j, 0);
                if (s != null) return s;
            } else if (b.startsWith("[")) {
                String s = extract(new JSONArray(b), 0);
                if (s != null) return s;
            }
        } catch (Exception ignored) {
        }
        return b;
    }

    private static String extract(Object o, int depth) {
        if (o == null || depth > 5 || o == JSONObject.NULL) return null;
        if (o instanceof String) {
            String s = ((String) o).trim();
            return s.isEmpty() ? null : s;
        }
        if (o instanceof JSONObject) {
            JSONObject j = (JSONObject) o;
            for (String k : KEYS) {
                if (!j.has(k)) continue;
                String s = extract(j.opt(k), depth + 1);
                if (s != null && !s.equalsIgnoreCase("success") && !s.equalsIgnoreCase("ok")) return s;
            }
            return null;
        }
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            return a.length() > 0 ? extract(a.opt(0), depth + 1) : null;
        }
        return null;
    }

    private static final Pattern EMOJI = Pattern.compile(
            "[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{1F1E6}-\\x{1F1FF}\\x{FE0E}\\x{FE0F}\\x{200D}\\x{20E3}\\x{E0020}-\\x{E007F}]");

    /** Tanpa emoji di seluruh UI: buang emoji dari jawaban AI. */
    static String noEmoji(String s) {
        return EMOJI.matcher(s).replaceAll("").replaceAll("[ \\t]{2,}", " ").replaceAll("(?m)^\\s+$", "").trim();
    }

    static String clean(String s) {
        return noEmoji(s).replace("**", "").replace("__", "").replaceAll("(?m)^#{1,6}\\s*", "").replaceAll("^Tira:\\s*", "").trim();
    }
}
