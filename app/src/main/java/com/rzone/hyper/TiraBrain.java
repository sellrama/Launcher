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
    static final String API = "https://api.nexray.eu.cc/ai/claude?text=";
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
        String ans;
        try {
            String body = get(API + URLEncoder.encode(p.toString(), "UTF-8"));
            ans = clean(parse(body));
            if (ans.isEmpty()) ans = "Hmm, Tira belum bisa menjawab itu. Coba tanya dengan kalimat lain ya.";
            history.add(new String[]{q, ans});
        } catch (Exception e) {
            ans = "Tira lagi susah terhubung ke internet. Cek koneksi kamu lalu coba lagi ya.";
        }
        return ans;
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
                String s = extract(new JSONObject(b), 0);
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
