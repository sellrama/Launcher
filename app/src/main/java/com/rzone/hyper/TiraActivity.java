package com.rzone.hyper;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Tira — asisten AI Hyper (seperti Google Assistant / Ella).
 * Muncul sebagai panel di bawah layar di atas aplikasi apa pun.
 */
public class TiraActivity extends Activity {
    private static final int RQ_MIC = 21, RQ_VOICE = 22;
    private float d;
    private TiraBrain brain;
    private TiraWave wave;
    private LinearLayout chat, sheet;
    private ScrollView scroll;
    private EditText input;
    private TextView hint;
    private View scrim;
    private SpeechRecognizer sr;
    private TextToSpeech tts;
    private boolean ttsReady, speakNext, busy, closing;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int acc;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        brain = new TiraBrain(this);
        Prefs pr = new Prefs(this);
        acc = pr.os() == Prefs.OS_IOS ? 0xFF0A84FF : pr.os() == Prefs.OS_PIXEL ? 0xFFA8C7FA : pr.gi("ac");
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(0xFF0E0F16);

        FrameLayout root = new FrameLayout(this);
        scrim = new View(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setOnClickListener(v -> close());
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF2191A26, 0xFA0E0F16});
        bg.setCornerRadii(new float[]{d * 30, d * 30, d * 30, d * 30, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setClickable(true);
        sheet.setElevation(d * 16);

        // pegangan
        View handle = new View(this);
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(0x55FFFFFF);
        hg.setCornerRadius(d * 3);
        handle.setBackground(hg);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams((int) (d * 40), (int) (d * 5));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.topMargin = (int) (d * 10);
        sheet.addView(handle, hl);

        // judul
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding((int) (d * 20), (int) (d * 10), (int) (d * 12), 0);
        TextView title = new TextView(this);
        title.setText("Tira");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        head.addView(title);
        TextView badge = new TextView(this);
        badge.setText("AI");
        badge.setTextColor(0xFF0E0F16);
        badge.setTextSize(10);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding((int) (d * 6), (int) (d * 1), (int) (d * 6), (int) (d * 1));
        GradientDrawable bgd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF7C4DFF, 0xFF00E5FF});
        bgd.setCornerRadius(d * 8);
        badge.setBackground(bgd);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.leftMargin = (int) (d * 8);
        head.addView(badge, bl);
        head.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        TextView x = new TextView(this);
        x.setText("✕");
        x.setTextColor(0xAAFFFFFF);
        x.setTextSize(18);
        x.setPadding((int) (d * 12), (int) (d * 6), (int) (d * 12), (int) (d * 6));
        x.setOnClickListener(v -> close());
        head.addView(x);
        sheet.addView(head);

        // percakapan
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        chat = new LinearLayout(this);
        chat.setOrientation(LinearLayout.VERTICAL);
        chat.setPadding((int) (d * 16), (int) (d * 8), (int) (d * 16), (int) (d * 8));
        scroll.addView(chat);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, (int) (d * 260)));

        // saran
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding((int) (d * 16), 0, (int) (d * 8), 0);
        for (String s : new String[]{"Jam berapa?", "Nyalakan senter", "Baterai berapa?", "Atur alarm 6 pagi",
                "Ceritakan fakta unik", "Ganti tema ke iOS", "Buka kamera"}) {
            TextView c = new TextView(this);
            c.setText(s);
            c.setTextColor(Color.WHITE);
            c.setTextSize(13);
            c.setPadding((int) (d * 14), (int) (d * 8), (int) (d * 14), (int) (d * 8));
            GradientDrawable cg = new GradientDrawable();
            cg.setColor(0x1FFFFFFF);
            cg.setStroke((int) d, 0x33FFFFFF);
            cg.setCornerRadius(d * 18);
            c.setBackground(cg);
            c.setOnClickListener(v -> send(s, false));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.rightMargin = (int) (d * 8);
            chips.addView(c, cl);
        }
        hs.addView(chips);
        sheet.addView(hs);

        // gelombang + status
        FrameLayout wf = new FrameLayout(this);
        wave = new TiraWave(this);
        wf.addView(wave, new FrameLayout.LayoutParams(-1, -1));
        hint = new TextView(this);
        hint.setTextColor(0xCCFFFFFF);
        hint.setTextSize(13);
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        hp.topMargin = (int) (d * 6);
        wf.addView(hint, hp);
        sheet.addView(wf, new LinearLayout.LayoutParams(-1, (int) (d * 70)));

        // input
        LinearLayout in = new LinearLayout(this);
        in.setGravity(Gravity.CENTER_VERTICAL);
        in.setPadding((int) (d * 12), (int) (d * 4), (int) (d * 12), (int) (d * 14));
        input = new EditText(this);
        input.setHint("Tanya Tira apa saja...");
        input.setHintTextColor(0x88FFFFFF);
        input.setTextColor(Color.WHITE);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        GradientDrawable ig = new GradientDrawable();
        ig.setColor(0x1AFFFFFF);
        ig.setCornerRadius(d * 24);
        input.setBackground(ig);
        input.setPadding((int) (d * 18), (int) (d * 12), (int) (d * 18), (int) (d * 12));
        input.setOnEditorActionListener((v, a, e) -> {
            if (a == EditorInfo.IME_ACTION_SEND || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                send(input.getText().toString(), false);
                return true;
            }
            return false;
        });
        in.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        TextView mic = roundBtn("🎤");
        mic.setOnClickListener(v -> listen());
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams((int) (d * 48), (int) (d * 48));
        ml.leftMargin = (int) (d * 8);
        in.addView(mic, ml);
        TextView snd = roundBtn("➤");
        snd.setOnClickListener(v -> send(input.getText().toString(), false));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams((int) (d * 48), (int) (d * 48));
        sl.leftMargin = (int) (d * 6);
        in.addView(snd, sl);
        sheet.addView(in);

        FrameLayout.LayoutParams shl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(sheet, shl);
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            @SuppressWarnings("deprecation") int bt = ins.getSystemWindowInsetBottom();
            sheet.setPadding(0, 0, 0, bt);
            return ins;
        });
        setContentView(root);

        bubble("Hai, aku Tira 👋\nMau tanya apa, atau suruh aku buka aplikasi, nyalakan senter, atur alarm?", false);
        state(TiraWave.IDLE);

        tts = new TextToSpeech(getApplicationContext(), st -> {
            if (st == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(new Locale("id", "ID"));
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });

        // animasi masuk: panel naik dari bawah
        scrim.setAlpha(0);
        scrim.animate().alpha(1).setDuration(250).start();
        sheet.setTranslationY(d * 500);
        sheet.animate().translationY(0).setDuration(420).setInterpolator(new DecelerateInterpolator(2.4f)).start();

        String ci = getIntent().getStringExtra("ci_q");
        if (ci != null && !ci.isEmpty()) h.postDelayed(() -> send(ci, false), 500);
        else if (getIntent().getBooleanExtra("voice", false)) h.postDelayed(this::listen, 450);
    }

    private TextView roundBtn(String t) {
        TextView b = new TextView(this);
        b.setText(t);
        b.setTextSize(18);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF7C4DFF, 0xFF00B8D4});
        g.setShape(GradientDrawable.OVAL);
        b.setBackground(g);
        Util.press(b);
        return b;
    }

    private void state(int s) {
        wave.state(s);
        hint.setText(s == TiraWave.LISTEN ? "Mendengarkan..." : s == TiraWave.THINK ? "Tira sedang berpikir..." : "");
    }

    private void bubble(String text, boolean user) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(user ? 0xFF0E0F16 : Color.WHITE);
        t.setTextSize(15);
        t.setLineSpacing(d * 2, 1);
        t.setTextIsSelectable(!user);
        t.setPadding((int) (d * 14), (int) (d * 10), (int) (d * 14), (int) (d * 10));
        GradientDrawable g = new GradientDrawable();
        float r = d * 18, s = d * 6;
        g.setCornerRadii(user ? new float[]{r, r, s, s, r, r, r, r} : new float[]{s, s, r, r, r, r, r, r});
        if (user) g.setColor(UiStyle.lum(acc) > 0.5f ? acc : UiStyle.mix(Color.WHITE, acc, 0.6f));
        else g.setColor(0x24FFFFFF);
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.gravity = user ? Gravity.END : Gravity.START;
        lp.topMargin = (int) (d * 8);
        if (user) lp.leftMargin = (int) (d * 48);
        else lp.rightMargin = (int) (d * 32);
        chat.addView(t, lp);
        t.setAlpha(0);
        t.setScaleX(0.85f);
        t.setScaleY(0.85f);
        t.setPivotX(user ? d * 200 : 0);
        t.animate().alpha(1).scaleX(1).scaleY(1).setDuration(260).setInterpolator(new OvershootInterpolator(1.4f)).start();
        scroll.post(() -> scroll.smoothScrollTo(0, chat.getHeight()));
    }

    private TextView typing() {
        TextView t = new TextView(this);
        t.setText("• • •");
        t.setTextColor(0xAAFFFFFF);
        t.setTextSize(15);
        t.setPadding((int) (d * 14), (int) (d * 8), (int) (d * 14), (int) (d * 8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x24FFFFFF);
        g.setCornerRadius(d * 18);
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = (int) (d * 8);
        chat.addView(t, lp);
        t.animate().alpha(0.4f).setDuration(500).withEndAction(new Runnable() {
            boolean up;

            @Override
            public void run() {
                if (t.getParent() == null) return;
                up = !up;
                t.animate().alpha(up ? 1f : 0.4f).setDuration(500).withEndAction(this).start();
            }
        }).start();
        scroll.post(() -> scroll.smoothScrollTo(0, chat.getHeight()));
        return t;
    }

    private void send(String q, boolean voice) {
        q = q == null ? "" : q.trim();
        if (q.isEmpty() || busy) return;
        speakNext = voice;
        input.setText("");
        hideIme();
        bubble(q, true);
        TiraBrain.Reply r = brain.local(q);
        if (r != null) {
            answer(r.text);
            if (r.action != null) h.postDelayed(() -> {
                r.action.run();
                if (r.text.startsWith("Membuka") || r.text.startsWith("Mencari") || r.text.startsWith("Memutar")
                        || r.text.startsWith("Siap!") || r.text.startsWith("Oke, layar")) close();
            }, 650);
            return;
        }
        busy = true;
        state(TiraWave.THINK);
        final TextView dots = typing();
        final String fq = q;
        new Thread(() -> {
            final String a = brain.ask(fq);
            h.post(() -> {
                busy = false;
                chat.removeView(dots);
                if (!isFinishing()) answer(a);
            });
        }, "tira").start();
    }

    private void answer(String a) {
        bubble(a, false);
        if (speakNext && ttsReady) {
            state(TiraWave.SPEAK);
            tts.speak(a, TextToSpeech.QUEUE_FLUSH, null, "tira");
            h.postDelayed(() -> {
                if (!tts.isSpeaking()) state(TiraWave.IDLE);
                else h.postDelayed(() -> state(TiraWave.IDLE), Math.min(20000, a.length() * 70L));
            }, 800);
        } else {
            state(TiraWave.IDLE);
        }
    }

    // ------------------------------------------------------------- suara

    private void listen() {
        if (busy) return;
        if (tts != null) tts.stop();
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceIntent();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RQ_MIC);
            return;
        }
        if (sr == null) {
            sr = SpeechRecognizer.createSpeechRecognizer(this);
            sr.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle p) {
                    state(TiraWave.LISTEN);
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float v) {
                    wave.level(v);
                }

                @Override
                public void onBufferReceived(byte[] b) {
                }

                @Override
                public void onEndOfSpeech() {
                    state(TiraWave.THINK);
                }

                @Override
                public void onError(int e) {
                    state(TiraWave.IDLE);
                    hint.setText(e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                            ? "Tira tidak mendengar apa-apa. Ketuk 🎤 lagi." : "Mikrofon bermasalah, coba ketik saja.");
                }

                @Override
                public void onResults(Bundle r) {
                    ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (l != null && !l.isEmpty()) send(l.get(0), true);
                    else state(TiraWave.IDLE);
                }

                @Override
                public void onPartialResults(Bundle r) {
                    ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (l != null && !l.isEmpty()) input.setText(l.get(0));
                }

                @Override
                public void onEvent(int t, Bundle b) {
                }
            });
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        try {
            sr.startListening(i);
            state(TiraWave.LISTEN);
        } catch (Exception e) {
            voiceIntent();
        }
    }

    private void voiceIntent() {
        try {
            startActivityForResult(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Bicara ke Tira"), RQ_VOICE);
        } catch (Exception e) {
            hint.setText("Pengenal suara tidak tersedia, silakan ketik.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int rq, String[] p, int[] g) {
        super.onRequestPermissionsResult(rq, p, g);
        if (rq == RQ_MIC) {
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) listen();
            else hint.setText("Izin mikrofon ditolak. Kamu tetap bisa mengetik.");
        }
    }

    @Override
    protected void onActivityResult(int rq, int rs, Intent data) {
        super.onActivityResult(rq, rs, data);
        if (rq == RQ_VOICE && rs == RESULT_OK && data != null) {
            ArrayList<String> l = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (l != null && !l.isEmpty()) send(l.get(0), true);
        }
    }

    private void hideIme() {
        InputMethodManager im = getSystemService(InputMethodManager.class);
        if (im != null) im.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private void close() {
        if (closing) return;
        closing = true;
        hideIme();
        scrim.animate().alpha(0).setDuration(220).start();
        sheet.animate().translationY(sheet.getHeight() + d * 40).setDuration(260)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                }).start();
    }

    @Override
    public void onBackPressed() {
        close();
    }

    @Override
    protected void onDestroy() {
        if (sr != null) sr.destroy();
        if (tts != null) tts.shutdown();
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
