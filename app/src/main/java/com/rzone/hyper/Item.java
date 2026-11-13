package com.rzone.hyper;

import android.graphics.Bitmap;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Satu aplikasi, atau folder (bila {@link #kids} tidak null). */
final class Item {
    Bitmap icon;
    List<Item> kids;
    String pkg = "";
    String cls = "";
    String label = "";
    /** Label asli dari sistem (sebelum diganti nama pengguna). */
    String original = "";

    String key() {
        return pkg + "/" + cls;
    }

    Item copy() {
        Item i = new Item();
        i.pkg = pkg;
        i.cls = cls;
        i.label = label;
        i.original = original;
        i.icon = icon;
        return i;
    }

    boolean isFolder() {
        return kids != null;
    }

    String letter() {
        return label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    // Kategori untuk App Library. Dicocokkan per kata (bukan substring sembarang) sehingga
    // "x" atau "wa" tidak lagi menangkap semua aplikasi yang mengandung huruf itu.
    private static final Pattern P_NET = w("chrome|firefox|opera|browser|internet|naver|edge|brave|duckduckgo|samsung internet|via");
    private static final Pattern P_COM = w("whatsapp|wa business|telegram|line|messages?|pesan|sms|chat|discord|slack|meet|zoom|mail|gmail|outlook|kontak|contacts|telepon|phone|dialer|signal|messenger");
    private static final Pattern P_FOTO = w("photos?|foto|camera|kamera|galeri|gallery|snapseed|capcut|canva|pixellab|lightroom|picsart|vsco|editor");
    private static final Pattern P_GAME = w("games?|genshin|pubg|free fire|mobile legends|legends|minecraft|roblox|clash|honkai|valorant|efootball|fifa|chess|catur");
    private static final Pattern P_SOS = w("youtube|yt music|music|musik|spotify|joox|netflix|vidio|video|tiktok|instagram|facebook|fb|twitter|x|threads|snapchat|pinterest|reddit|podcast|disney|prime video");
    private static final Pattern P_ALAT = w("settings|setelan|pengaturan|kalkulator|calculator|clock|jam|files?|file manager|berkas|cleaner|tools?|utilit(y|ies)|kalender|calendar|catatan|notes|keep|recorder|perekam|senter|flashlight|cuaca|weather|maps|peta|translate|terjemah|drive|docs|sheets|slides|authenticator|vpn");
    private static final Pattern P_UANG = w("bank|bca|bri|bni|mandiri|jago|seabank|pay|paypal|dana|ovo|gopay|gojek|grab|shopee|tokopedia|lazada|blibli|bukalapak|shop|store|market|wallet|dompet|play store");

    private static Pattern w(String alt) {
        return Pattern.compile("(^|[^a-z0-9])(" + alt + ")($|[^a-z0-9])");
    }

    static String category(String lb) {
        String l = lb == null ? "" : lb.toLowerCase(Locale.ROOT);
        if (P_UANG.matcher(l).find()) return "Belanja & Keuangan";
        if (P_NET.matcher(l).find()) return "Internet";
        if (P_COM.matcher(l).find()) return "Komunikasi";
        if (P_FOTO.matcher(l).find()) return "Foto & Video";
        if (P_GAME.matcher(l).find()) return "Game";
        if (P_SOS.matcher(l).find()) return "Media & Sosial";
        if (P_ALAT.matcher(l).find()) return "Alat";
        return "Lainnya";
    }
}
