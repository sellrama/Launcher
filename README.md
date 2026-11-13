# RZONE Hyper v8 — Lockscreen, Launcher & Tira AI

Powered by **RZONEDEV** · TikTok [@maramadhona](https://www.tiktok.com/@maramadhona) · aplikasi **gratis**.

Source code ini adalah RZONE Hyper v5.1 yang disusun ulang menjadi proyek Android Studio (Java), lalu diperbaiki bug-nya, dirapikan tampilannya, dan ditambah fitur. APK v5.1 asli tetap ada di `RZONE-Hyper-v5.1.apk`.

## Yang baru di v7–v8

- **Tira (asisten AI)**: panel ala Google Assistant/Ella, input suara lewat tombol, jawaban bersuara, perintah HP (buka aplikasi, putar video YouTube, senter, alarm/timer, baterai) dan kustomisasi tampilan ("ganti jam ke neon", "warna aksen biru", "tata letak lockscreen ios"). Otak memakai API Nexray dengan cadangan otomatis (claude → copilot → gemini → deepseek). Tombol Tira ada di lockscreen, ikon Tira di drawer, tekan lama tombol Home (asisten digital), dan mikrofon di search bar.
- **30 desain jam** dengan galeri pratinjau; Big Clock dihapus.
- **AOD**: ketuk dua kali di lockscreen; layar hitam penuh, jam tidak redup, jam mengecil & meluncur ke tengah, anti burn-in.
- **Animasi**: aplikasi membesar dari ikonnya dan mengecil kembali ke ikon saat keluar; lockscreen transparan bergeser mengikuti jari lalu home "mendarat".
- **Total UI berbeda total**: pengaturan PixelOS (tile bersambung), iOS (grup + profil), Custom (dasbor tile) + pencarian; launcher iOS memakai App Library di kanan halaman terakhir.
- **Tema .zth** (HiOS/XOS): impor dari *Pengaturan › Tema .zth* — ikon, latar folder, wallpaper home & lockscreen.
- **Store Depth Wallpaper**: 300 ilustrasi depth (20 adegan × 15 variasi) + foto online dengan subjek dipotong otomatis.
- **Widget**: tekan lama area kosong › Widget; tekan lama widget untuk ubah ukuran/hapus.
- **Ikon asli tanpa plat putih**, tanpa emoji di seluruh UI.
- **Update bertumpuk**: semua build ditandatangani kunci tetap (`keystore/hyper.jks`) dan versionCode naik otomatis, jadi APK baru cukup dipasang menimpa yang lama.

## Total UI

Menu baru di bagian paling atas Pengaturan Hyper. Satu pilihan langsung mengubah **seluruh tampilan**: lockscreen, jam, notifikasi lockscreen, tombol, launcher, dock, search bar, ikon, folder, menu tekan-lama, App Drawer, sampai halaman pengaturannya sendiri.

| | PixelOS | iOS | Custom OS |
|---|---|---|---|
| Lockscreen | Tanggal kecil di kiri atas, jam besar bertumpuk, pintasan kotak membulat | Tanggal di atas jam tebal satu baris, notifikasi menumpuk dari bawah, tombol **senter** & kamera | Bebas diatur |
| Panel/menu | Tonal (Material You) | Frosted (kaca buram) | Bebas |
| Ikon | Lingkaran, dock 5 ikon tanpa panel | Squircle, dock 4 ikon dengan panel kaca | Bebas |
| Search bar | Pill Google di bawah | Kapsul kecil "Cari" di atas dock | Google / iOS / Minimal |
| Drawer | A–Z + saran | App Library (kategori) | Bebas |
| Badge notifikasi | Titik | Angka merah | Mati / titik / angka |
| Halaman pengaturan | Material, kartu membulat | Daftar berkelompok ala iOS | Warna aksen sendiri |

**Custom OS** adalah tampilan buatanmu sendiri:
- Mengubah pengaturan apa pun saat memakai PixelOS/iOS otomatis menjadikannya Custom OS. Perubahanmu tidak hilang.
- Saat berganti ke PixelOS/iOS lalu kembali ke Custom OS, tampilan Custom terakhirmu dipulihkan.
- **Studio Custom OS** menyediakan tombol "Mulai dari PixelOS / iOS" dan kontrol dasar dalam satu halaman.

## Fitur launcher baru

- **Pintasan aplikasi** (App Shortcuts) di menu tekan-lama. Butuh Hyper sebagai launcher default.
- **Sembunyikan aplikasi** dari drawer. Bisa ditampilkan lagi di *App Drawer › Aplikasi tersembunyi*.
- **Ubah nama** aplikasi, dengan tombol "Kembalikan" ke nama asli.
- **Badge notifikasi** di ikon (titik atau angka). Folder menjumlahkan isinya.
- **Kunci tata letak** agar ikon tidak terseret atau terhapus tidak sengaja.
- **Seret ikon keluar dari folder**. Folder yang tinggal 1 aplikasi otomatis menjadi ikon biasa.
- **Bubarkan folder** (isinya dipindah ke layar utama) dan **Tambah ke layar utama** dari drawer.
- **Cari di web** langsung dari pencarian drawer. Hasil yang diawali kata kunci tampil lebih dulu.
- **Urutan drawer**: A–Z atau paling sering dipakai. Saran aplikasi juga memakai data pemakaian.
- **Ketuk dua kali → Matikan layar**, di layar utama maupun lockscreen. Memakai layanan aksesibilitas kecil yang tidak membaca isi layar.
- **Tombol senter** di lockscreen.
- **Ketuk notifikasi** di lockscreen untuk langsung membukanya.
- Tombol **Jadikan launcher default** memakai dialog sistem (Android 10+).
- **Icon pack** kini benar-benar berfungsi karena membaca `appfilter.xml`.
- Model ML Kit untuk depth otomatis diunduh saat aplikasi dipasang.

## Bug v5.1 yang diperbaiki

**Crash / hang**
1. Hampir semua foto kamera gagal dimuat ("Gagal memuat gambar"). Penyebabnya pembagian integer saat menghitung skala (`1440 / lebar_foto = 0`).
2. Wallpaper foto sendiri bisa tampil kosong atau hitam karena pembagian integer yang sama di fungsi crop.
3. Kemungkinan hang (ANR) saat menggambar gedung pada wallpaper "Kota Malam" dan "Pencakar Langit" (loop tanpa kondisi berhenti).
4. Launcher bisa hang saat pertama dibuka di HP dengan sedikit aplikasi (loop dock tanpa henti).
5. Crash `ConcurrentModificationException`: daftar aplikasi diubah dari thread latar saat UI sedang membacanya.
6. Crash di halaman Wallpaper bila nilai depth tidak valid (indeks array tidak dibatasi).
7. Gambar besar di-decode tanpa sampling dan bitmap lama tidak dibebaskan, sehingga memori bocor dan bisa kehabisan memori (OOM).

**Lockscreen**

8. Membuka kunci selalu memaksa membuka Hyper Launcher walaupun Hyper bukan launcher default. Sekarang kembali ke aplikasi sebelumnya.
9. Lockscreen muncul di tengah telepon saat sensor jarak mematikan layar.
10. Sensor parallax terus aktif walaupun lockscreen tidak terlihat (boros baterai).
11. Notifikasi lockscreen kosong sampai ada notifikasi baru, karena notifikasi yang sudah ada tidak dimuat.
12. Menghapus satu notifikasi ikut menghapus semua notifikasi lain dari aplikasi yang sama.
13. Notifikasi menimpa tanggal dan teks kustom. Teks notifikasi dipotong per huruf, bukan per lebar layar.
14. Service tidak menyala lagi setelah aplikasi diperbarui (`MY_PACKAGE_REPLACED`).

**Launcher**

15. Aplikasi yang baru dipasang atau dihapus saat launcher tidak aktif tidak muncul sampai HP direstart.
16. Mengganti icon pack tidak memperbarui ikon launcher.
17. Icon pack hampir tidak pernah menemukan ikon (hanya menebak nama drawable).
18. Panel kaca launcher kadang menampilkan blur wallpaper lockscreen (state blur dipakai bersama).
19. Mengubah jumlah kolom/baris membuat ikon bergeser atau hilang. Sekarang disusun ulang berurutan.
20. Mengurangi jumlah ikon dock membuat ikon yang tidak muat hilang. Sekarang dipindah ke halaman.
21. Folder dengan banyak aplikasi melebihi layar dan tidak bisa digulir.
22. Dock dan search bar bisa tertutup navigation bar karena posisi hanya ditebak dari resource. Sekarang memakai inset jendela.
23. Kategori App Library salah tebak (mis. kata "x" atau "wa" menangkap hampir semua aplikasi).
24. Klik search bar tidak berbuat apa-apa di HP tanpa Google. Sekarang membuka pencarian drawer.

**Wallpaper bawaan**

25. "Torii & Fuji": latar Fuji tertimpa latar "Sunset Tropis", dan pulau sunset ikut tergambar di depan torii (`case` tanpa `return`).
26. "Kapal Layar": lautan kedua menimpa yang pertama.
27. PNG foreground manual ditarik paksa ke ukuran wallpaper sehingga gepeng. Sekarang di-crop sesuai rasio.

**Pengaturan & tampilan**

28. Thumbnail wallpaper dibuat di UI thread sehingga halaman macet saat dibuka.
29. Mengaktifkan lockscreen tanpa izin overlay tampak berhasil padahal lockscreen tidak akan muncul. Sekarang diarahkan ke izin dulu.
30. Toggle Aktif/Mati diganti Switch. Slider menampilkan nilai di kanan. Halaman pengaturan memakai inset layar dengan benar.

## Build

Wajib JDK 17 dan Android SDK 34.

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk
```

Setiap push juga otomatis dibangun oleh **GitHub Actions** (`.github/workflows/build.yml`). APK bisa diunduh dari tab *Actions › Build APK › Artifacts*.

> APK v6 ditandatangani dengan kunci yang berbeda dari v5.1, jadi **hapus dulu v5.1** sebelum memasang v6. Untuk rilis resmi, ganti `signingConfig` di `app/build.gradle` dengan keystore milikmu.

## Struktur kode

| File | Isi |
|---|---|
| `Prefs.java` | Pengaturan, preset Total UI, aplikasi tersembunyi & nama |
| `DepthView.java` / `LockActivity.java` / `LockService.java` | Lockscreen |
| `HomeActivity.java` / `AppCell.java` | Launcher, dock, folder, drawer |
| `MainActivity.java` | Pengaturan (mengikuti Total UI) |
| `UiStyle.java` / `Icons.java` / `IconPack.java` / `Scene.java` | Gaya panel, ikon, icon pack, wallpaper prosedural |
| `NotifListener.java` / `ScreenOff.java` / `BootReceiver.java` | Notifikasi, matikan layar, autostart |
