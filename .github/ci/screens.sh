#!/bin/bash
# Ambil screenshot semua layar Hyper di emulator untuk tiap Total UI, plus log crash.
set -x
PKG=com.rzone.hyper
APK=app/build/outputs/apk/debug/app-debug.apk
OUT=ci-screens
rm -rf $OUT; mkdir -p $OUT/raw

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'
adb install -r -g $APK || exit 1
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell cmd notification allow_listener $PKG/$PKG.NotifListener
adb shell cmd package set-home-activity $PKG/.HomeActivity
# launcher bawaan emulator sering ANR & menutupi screenshot
adb shell am force-stop com.google.android.apps.nexuslauncher || true
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell cmd notification post -S bigtext -t 'Budi' ci1 'Nanti jadi ketemu jam 7 kan?'
adb shell cmd notification post -S bigtext -t 'Promo' ci2 'Diskon 50% khusus hari ini'
adb logcat -c

ci() { adb shell am start -W --activity-clear-task -f 0x10000000 -n $PKG/.CiActivity "$@" | grep -iE "error|Status" ; }
shot() { sleep ${2:-4}; adb exec-out screencap -p > $OUT/raw/$1.png; }
home_key() { adb shell input keyevent KEYCODE_HOME; }

for OS in 1 2 0; do
  N=$( [ $OS = 1 ] && echo pixel || ([ $OS = 2 ] && echo ios || echo custom) )
  ci --ei os $OS --es open main;            shot ${N}_01_settings
  adb shell input swipe 540 1800 540 600 300; shot ${N}_02_settings_scroll 2
  ci --es open clk;                          shot ${N}_03_clock 5
  ci --es open lms;                          shot ${N}_04_lockmisc 5
  ci --es open lock;                         shot ${N}_05_lock 5
  adb shell input swipe 540 2000 540 600 250; shot ${N}_06_after_unlock 2
  ci --es open home;                         shot ${N}_07_home 5
  adb shell input swipe 540 1700 540 500 300; shot ${N}_08_drawer 3
  home_key;                                  shot ${N}_09_home_again 3
  adb shell input swipe 900 1200 100 1200 300; shot ${N}_10_page2 3
  home_key; sleep 2
  adb shell input swipe 540 1200 540 1201 1200; shot ${N}_11_longpress_empty 2
  home_key; sleep 1
  ci --es open tira --es q siapa_kamu_dan_bisa_apa;  shot ${N}_12_tira 12
  adb shell input keyevent KEYCODE_BACK
done

# beberapa desain jam
for SH in 0 2 5 7 9 12 13 16 19 20 21 22 23 24 25 26 27 28 29; do
  ci --ei os 0 --es set "sh=$SH,lay=1" --es open lock; shot clock_face_$SH 3
done
# AOD: ketuk dua kali di lockscreen (jam mengecil & turun ke tengah)
ci --ei os 1 --es set "la=1" --es open lock; sleep 4
adb shell input tap 540 1200; sleep 0.12; adb shell input tap 540 1200; shot aod 3
ci --ei os 2 --es open lock; sleep 4
adb shell input tap 540 1200; sleep 0.12; adb shell input tap 540 1200; shot aod_ios 3
# ikon di layar utama & drawer (cek tanpa latar putih)
ci --ei os 1 --es open drawer; shot icons_drawer 4

# ---------- Store Depth Wallpaper
ci --ei os 1 --es open store; shot store_illus 8
adb shell input swipe 540 1800 540 700 300; shot store_illus_scroll 5
adb shell input tap 800 330; shot store_online 12
adb shell input keyevent KEYCODE_BACK; sleep 1

# ---------- tema .zth uji (dibuat dengan Python)
python3 .github/ci/make_zth.py /tmp/uji.zth && \
  adb shell mkdir -p /sdcard/Android/data/$PKG/files && \
  adb push /tmp/uji.zth /sdcard/Android/data/$PKG/files/uji.zth
ci --ei os 2 --es zth uji.zth --es open home; shot zth_home 8
adb shell input swipe 900 1200 100 1200 300; shot zth_library 4
home_key; sleep 1
ci --es open tema; shot zth_settings 5
ci --es open lock; shot zth_lock 5

adb logcat -d -b crash > $OUT/crash.txt
adb logcat -d -s AndroidRuntime:E Hyper:* > $OUT/runtime.txt
adb logcat -d | grep -iE "rzone|FATAL|ANR" | tail -400 > $OUT/app_log.txt

# kecilkan gambar agar repo tidak membengkak
for f in $OUT/raw/*.png; do
  python3 -c "import sys;from PIL import Image;im=Image.open(sys.argv[1]).convert('RGB');im=im.resize((432,int(im.height*432/im.width)));im.save(sys.argv[2],quality=72)" "$f" "$OUT/$(basename "${f%.png}").jpg" 2>/dev/null \
    || ffmpeg -loglevel error -y -i "$f" -vf scale=432:-1 -q:v 5 "$OUT/$(basename "${f%.png}").jpg" || cp "$f" $OUT/
done
rm -rf $OUT/raw
ls -la $OUT
exit 0
