package com.rzone.hyper;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/**
 * Mendengarkan kata panggil "Hai Tira" / "Halo Tira" / "Hallo Tira".
 * Kalimat setelah kata panggil langsung dikirim ke Tira ("Hai Tira, putar video kucing").
 *
 * Mode (pref "tw"): 0 mati, 1 hanya saat layar utama Hyper terbuka, 2 selalu (saat layar menyala).
 * Memakai pengenal suara perangkat secara berulang, jadi menambah pemakaian baterai.
 */
public class HotwordService extends Service {
    static final String EXTRA_HOLD = "hold";
    private static boolean running;
    /** Dijeda saat Tira terbuka (mikrofon dipakai Tira). */
    static volatile boolean paused;

    private final Handler h = new Handler(Looper.getMainLooper());
    private SpeechRecognizer sr;
    private boolean listening, screenOn = true, registered;
    private int errors;
    private AudioManager am;

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            screenOn = Intent.ACTION_SCREEN_ON.equals(i.getAction());
            if (screenOn) schedule(600);
            else stopListening();
        }
    };

    static boolean canRun(Context c) {
        return c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && SpeechRecognizer.isRecognitionAvailable(c);
    }

    /** Nyalakan/matikan sesuai mode. home = dipanggil dari layar utama yang sedang terlihat. */
    static void sync(Context c, boolean home) {
        int mode = new Prefs(c).gi("tw");
        boolean want = canRun(c) && (mode == 2 || (mode == 1 && home));
        Intent i = new Intent(c, HotwordService.class);
        try {
            if (want && !running) c.startForegroundService(i);
            else if (!want && running) c.stopService(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        am = getSystemService(AudioManager.class);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        if (!canRun(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("tira_wake", "Tira mendengarkan", NotificationManager.IMPORTANCE_MIN));
        PendingIntent pi = PendingIntent.getActivity(this, 1, new Intent(this, TiraActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, "tira_wake")
                .setContentTitle("Tira siap dipanggil")
                .setContentText("Ucapkan \"Hai Tira\" atau \"Halo Tira\"")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 30) startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(2, n);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        if (!registered) {
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screen, f);
            registered = true;
        }
        PowerManager pm = getSystemService(PowerManager.class);
        screenOn = pm == null || pm.isInteractive();
        schedule(300);
        return START_STICKY;
    }

    private void schedule(long ms) {
        h.removeCallbacks(loop);
        h.postDelayed(loop, ms);
    }

    private final Runnable loop = this::listen;

    private void listen() {
        if (!running || listening) return;
        if (paused || !screenOn || LockService.inCall(this)) {
            schedule(1500);
            return;
        }
        if (sr == null) {
            sr = SpeechRecognizer.createSpeechRecognizer(this);
            sr.setRecognitionListener(listener);
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        muteBeep(true);
        try {
            sr.startListening(i);
            listening = true;
        } catch (Exception e) {
            listening = false;
            schedule(2000);
        }
        h.postDelayed(() -> muteBeep(false), 700);
    }

    private void stopListening() {
        h.removeCallbacks(loop);
        if (sr != null && listening) {
            try {
                sr.cancel();
            } catch (Exception ignored) {
            }
        }
        listening = false;
    }

    /** Redam bunyi "bip" pengenal suara yang berulang. */
    private void muteBeep(boolean mute) {
        if (am == null) return;
        int dir = mute ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE;
        try {
            am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, dir, 0);
        } catch (Exception ignored) {
        }
    }

    private boolean check(ArrayList<String> l, boolean fin) {
        if (l == null) return false;
        for (String s : l) {
            if (s == null) continue;
            String rest = TiraBrain.afterWake(s);
            if (rest == null) continue;
            wake(fin ? rest : "");
            return true;
        }
        return false;
    }

    private void wake(String cmd) {
        stopListening();
        paused = true;
        Intent i = new Intent(this, TiraActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (cmd != null && cmd.trim().length() > 1) i.putExtra("cmd", cmd.trim());
        else i.putExtra("voice", true);
        try {
            startActivity(i);
        } catch (Exception e) {
            paused = false;
        }
        schedule(2500);
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle b) {
            errors = 0;
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float v) {
        }

        @Override
        public void onBufferReceived(byte[] b) {
        }

        @Override
        public void onEndOfSpeech() {
        }

        @Override
        public void onError(int e) {
            listening = false;
            if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                stopSelf();
                return;
            }
            errors++;
            long wait = e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 1500
                    : e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ? 250
                    : Math.min(30000, 1000L * errors);
            schedule(wait);
        }

        @Override
        public void onResults(Bundle r) {
            listening = false;
            if (!check(r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION), true)) schedule(200);
        }

        @Override
        public void onPartialResults(Bundle r) {
            // tunggu hasil akhir agar perintah setelah kata panggil ikut terbaca
        }

        @Override
        public void onEvent(int t, Bundle b) {
        }
    };

    @Override
    public void onDestroy() {
        running = false;
        stopListening();
        muteBeep(false);
        if (sr != null) sr.destroy();
        sr = null;
        if (registered) {
            try {
                unregisterReceiver(screen);
            } catch (Exception ignored) {
            }
        }
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }
}
