package com.rzone.hyper;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Penyimpanan pengaturan Hyper.
 *
 * Semua pengaturan tampilan disimpan sebagai int dengan kunci pendek (kompatibel
 * dengan v5.x sehingga pengaturan lama tetap terbaca). Total UI ("os") memilih
 * preset PixelOS / iOS, atau Custom OS yang nilainya bebas diatur pengguna.
 */
public class Prefs {
    /** Total UI */
    static final int OS_CUSTOM = 0, OS_PIXEL = 1, OS_IOS = 2;
    static final String[] OS_NAMES = {"Custom OS", "PixelOS", "iOS"};

    private static final Map<String, Integer> D = new HashMap<>();
    /** Kunci yang ikut diatur oleh Total UI (preset). */
    static final String[] THEME_KEYS;
    /** Kunci yang bila berubah membuat launcher dibangun ulang. */
    private static final String[] HOME_KEYS;
    private static final Map<String, Integer> PIXEL = new HashMap<>();
    private static final Map<String, Integer> IOS = new HashMap<>();

    static {
        // ---------- umum ----------
        d("en", 0);        // lockscreen aktif
        d("os", OS_PIXEL); // Total UI
        d("ui", 6);        // gaya panel (UiStyle.NAMES)
        d("ac", 0xFFA8C7FA); // warna aksen
        // ---------- wallpaper lockscreen ----------
        d("lm", 0);  // 0 bawaan, 1 foto sendiri
        d("ld", 0);  // id depth bawaan
        d("dep", 1); // efek depth foto sendiri
        d("ldv", 0); // variasi warna depth bawaan (Scene.VARIANTS)
        // ---------- jam ----------
        d("cs", 100); d("cy", 27); d("sh", 0); d("cc", 0xFFFFFFFF); d("cg", 0); // sh = desain jam (Clock.NAMES)
        d("h24", 1); d("co", 100); d("csd", 1); d("ct", 0); d("cl", 108);
        // ---------- lockscreen lain ----------
        d("lay", 1); // tata letak lockscreen: 0 Hyper, 1 Pixel, 2 iOS
        d("lp", 100); d("la", 1); d("dm", 0); d("tb", 1); d("lbt", 0); d("hs", 1); d("li", 1);
        d("bs", 1); d("ffp", 1); d("nlk", 1);
        // ---------- wallpaper launcher ----------
        d("hm", 0); d("hw", 0); d("hd", 10); d("hb", 1); d("wp", 1);
        // ---------- layar utama ----------
        d("hc", 4); d("hr", 0); d("isz", 100); d("ls", 12); d("lv", 1); d("gl", 1); d("gs", 34);
        d("pt", 0); d("lck", 0); d("bdg", 1);
        // ---------- dock & pencarian ----------
        d("sv", 1); d("sp", 0); d("sbs", 0); d("dk", 0); d("dn", 5);
        // ---------- drawer ----------
        d("dc", 5); d("dl", 1); d("rr", 1); d("lib", 0); d("dso", 0);
        // ---------- ikon ----------
        d("ik", 0); d("is", 0);
        // ---------- animasi & gestur ----------
        d("as", 100); d("sg", 0); d("dt", 0);
        // ---------- Tira ----------
        d("tts", 1); // Tira menjawab dengan suara bila ditanya lewat suara

        THEME_KEYS = new String[]{"ui", "ac", "cs", "cy", "sh", "cc", "cg", "h24", "co", "csd", "ct",
                "cl", "lay", "la", "dm", "tb", "lbt", "hs", "li", "bs", "ffp", "nlk", "hd", "hb", "wp",
                "hc", "hr", "isz", "ls", "lv", "gl", "gs", "pt", "bdg", "sv", "sp", "sbs", "dk", "dn", "dc",
                "dl", "rr", "lib", "ik", "is", "sg"};
        HOME_KEYS = new String[]{"os", "ui", "ac", "lm", "ld", "hm", "hw", "hd", "hb", "wp", "hc", "hr", "isz",
                "ls", "lv", "dc", "dl", "sv", "sp", "sbs", "dk", "dn", "pt", "as", "sg", "dt", "gl", "gs", "rr",
                "ik", "is", "lib", "dso", "bdg", "lck"};

        // PixelOS: Material You, jam bertumpuk, pill Google di bawah, ikon lingkaran.
        for (String k : THEME_KEYS) PIXEL.put(k, D.get(k));

        // iOS: kaca buram, jam tebal satu baris, dock panel, ikon squircle, App Library.
        IOS.putAll(PIXEL);
        p(IOS, "ui", 2); p(IOS, "ac", 0xFF0A84FF);
        p(IOS, "cs", 100); p(IOS, "cy", 8); p(IOS, "sh", 2); p(IOS, "cc", 0xFFFFFFFF);
        p(IOS, "cg", 0); p(IOS, "csd", 0); p(IOS, "ct", 0); p(IOS, "co", 100);
        p(IOS, "lay", 2); p(IOS, "la", 1); p(IOS, "dm", 0); p(IOS, "tb", 1); p(IOS, "lbt", 1);
        p(IOS, "hs", 1); p(IOS, "li", 1); p(IOS, "bs", 0); p(IOS, "ffp", 0); p(IOS, "nlk", 1);
        p(IOS, "hd", 0); p(IOS, "hb", 1); p(IOS, "wp", 1);
        p(IOS, "hc", 4); p(IOS, "hr", 6); p(IOS, "isz", 104); p(IOS, "ls", 11); p(IOS, "lv", 1);
        p(IOS, "gl", 0); p(IOS, "pt", 0); p(IOS, "bdg", 2);
        p(IOS, "sv", 1); p(IOS, "sp", 0); p(IOS, "sbs", 1); p(IOS, "dk", 1); p(IOS, "dn", 4);
        p(IOS, "dc", 4); p(IOS, "dl", 1); p(IOS, "rr", 1); p(IOS, "lib", 1); p(IOS, "sg", 1);
        p(IOS, "ik", 0); p(IOS, "is", 1);
    }

    private static void d(String k, int v) {
        D.put(k, v);
    }

    private static void p(Map<String, Integer> m, String k, int v) {
        m.put(k, v);
    }

    private final SharedPreferences p;

    public Prefs(Context c) {
        this.p = c.getSharedPreferences("rz", Context.MODE_PRIVATE);
        migrate();
    }

    /** v5.x menyimpan "ipk" sebagai string padahal default-nya int; tidak dipakai lagi sebagai int. */
    private void migrate() {
        int ver = p.getInt("ver", 0);
        if (ver >= 7) return;
        SharedPreferences.Editor e = p.edit();
        // v5.x → v6: pengguna lama mempertahankan tampilan mereka sebagai Custom OS.
        if (ver < 6 && (p.contains("ui") || p.contains("hc") || p.contains("sh"))) e.putInt("os", OS_CUSTOM);
        // v6 → v7: "bentuk" (4) + "gaya" (8) + kapsul kaca digabung menjadi 20 desain jam; Big Clock dihapus.
        if (p.contains("sh") || p.contains("st") || p.contains("cgk")) {
            int sh = safeInt("sh", 0), st = safeInt("st", 0);
            int face;
            if (safeInt("cgk", 0) == 1) face = 19;
            else if (sh == 2) face = 4;
            else if (st == 5) face = 9;
            else if (st == 6) face = 10;
            else if (st == 3) face = 11;
            else if (st == 4) face = 12;
            else if (st == 7) face = 2;
            else if (st == 1 && sh == 1) face = 3;
            else face = sh == 1 ? 1 : 0;
            e.putInt("sh", face);
            e.remove("st").remove("cgk");
        }
        e.remove("cust"); // snapshot Custom lama memakai kunci jam lama
        e.putInt("ver", 7);
        e.apply();
    }

    private int safeInt(String k, int def) {
        try {
            return p.getInt(k, def);
        } catch (ClassCastException ex) {
            return def;
        }
    }

    public int gi(String k) {
        Integer v = D.get(k);
        try {
            return p.getInt(k, v == null ? 0 : v);
        } catch (ClassCastException e) {
            return v == null ? 0 : v;
        }
    }

    public void si(String k, int v) {
        p.edit().putInt(k, v).apply();
    }

    public boolean gb(String k) {
        return gi(k) == 1;
    }

    public String str(String k) {
        try {
            return p.getString(k, "");
        } catch (ClassCastException e) {
            return "";
        }
    }

    public void str(String k, String v) {
        p.edit().putString(k, v).apply();
    }

    public String txt() {
        return str("txt");
    }

    public void txt(String s) {
        str("txt", s);
    }

    public boolean enabled() {
        return gb("en");
    }

    public void enabled(boolean z) {
        si("en", z ? 1 : 0);
    }

    // ------------------------------------------------------------------ Total UI

    static boolean isThemeKey(String k) {
        for (String t : THEME_KEYS) if (t.equals(k)) return true;
        return false;
    }

    int os() {
        int o = gi("os");
        return o < 0 || o > 2 ? OS_CUSTOM : o;
    }

    /** Terapkan Total UI. Nilai Custom OS disimpan dulu agar bisa dipulihkan. */
    void applyOs(int os) {
        int cur = os();
        SharedPreferences.Editor e = p.edit();
        if (cur == OS_CUSTOM && os != OS_CUSTOM) e.putString("cust", snapshot());
        if (os == OS_CUSTOM) {
            String s = str("cust");
            if (!s.isEmpty()) restore(e, s);
        } else {
            Map<String, Integer> m = os == OS_IOS ? IOS : PIXEL;
            for (Map.Entry<String, Integer> en : m.entrySet()) e.putInt(en.getKey(), en.getValue());
        }
        e.putInt("os", os);
        e.apply();
    }

    /** Salin preset ke Custom OS sebagai titik awal kustomisasi. */
    void customFrom(int os) {
        Map<String, Integer> m = os == OS_IOS ? IOS : PIXEL;
        SharedPreferences.Editor e = p.edit();
        for (Map.Entry<String, Integer> en : m.entrySet()) e.putInt(en.getKey(), en.getValue());
        e.putInt("os", OS_CUSTOM);
        e.remove("cust");
        e.apply();
    }

    /**
     * Dipanggil sebelum pengguna mengubah satu pengaturan tema. Bila sedang memakai
     * preset, tampilan saat ini menjadi Custom OS (sehingga perubahan tidak hilang).
     * @return true bila baru saja beralih ke Custom OS
     */
    boolean touch(String key) {
        if (!isThemeKey(key) || os() == OS_CUSTOM) return false;
        p.edit().putInt("os", OS_CUSTOM).remove("cust").apply();
        return true;
    }

    private String snapshot() {
        StringBuilder b = new StringBuilder();
        for (String k : THEME_KEYS) b.append(k).append('=').append(gi(k)).append(';');
        return b.toString();
    }

    private static void restore(SharedPreferences.Editor e, String s) {
        for (String kv : s.split(";")) {
            int i = kv.indexOf('=');
            if (i <= 0) continue;
            try {
                e.putInt(kv.substring(0, i), Integer.parseInt(kv.substring(i + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ launcher

    public String launcherSig() {
        StringBuilder b = new StringBuilder();
        for (String k : HOME_KEYS) b.append(gi(k)).append(',');
        return b.toString();
    }

    /** Berubah bila ikon perlu dibuat ulang (termasuk icon pack, nama & aplikasi tersembunyi). */
    public String iconSig() {
        return gi("ik") + "," + gi("is") + "," + gi("ac") + "," + str("ipk") + "," + str("zth") + "," + str("nm").hashCode()
                + "," + str("hid").hashCode();
    }

    // ------------------------------------------------------------------ aplikasi tersembunyi & nama

    Set<String> hidden() {
        Set<String> s = new HashSet<>();
        for (String k : str("hid").split("\n")) if (!k.isEmpty()) s.add(k);
        return s;
    }

    void setHidden(String key, boolean hide) {
        Set<String> s = hidden();
        if (hide) s.add(key);
        else s.remove(key);
        str("hid", String.join("\n", s));
    }

    Map<String, String> names() {
        Map<String, String> m = new HashMap<>();
        for (String line : str("nm").split("\n")) {
            int i = line.indexOf('\t');
            if (i > 0) m.put(line.substring(0, i), line.substring(i + 1));
        }
        return m;
    }

    void setName(String key, String label) {
        Map<String, String> m = names();
        if (label == null || label.trim().isEmpty()) m.remove(key);
        else m.put(key, label.replace('\n', ' ').replace('\t', ' ').trim());
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> en : m.entrySet())
            b.append(en.getKey()).append('\t').append(en.getValue()).append('\n');
        str("nm", b.toString());
    }

    /** Reset semua pengaturan, kecuali status aktif lockscreen, nama & aplikasi tersembunyi. */
    public void reset() {
        boolean en = enabled();
        String hid = str("hid"), nm = str("nm");
        p.edit().clear().putInt("ver", 7).apply();
        enabled(en);
        str("hid", hid);
        str("nm", nm);
    }
}
