package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Plays the azan over whatever is on screen, at the volume from config.json (azan_volume), with a small
 * glass card naming the prayer. Any button stops it; so does the end of the recording, or leaving it.
 * The volume the TV had before comes back afterwards, unless someone changed it meanwhile.
 *
 * The recordings are files/azan.mp3 and, for Subuh, files/azan_subuh.mp3 (falls back to azan.mp3).
 */
public final class AzanActivity extends Activity {
    private static final long MAX_MS = 8 * 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private int volumeBefore = -1, volumeSet = -1;
    private boolean done;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int index = getIntent().getIntExtra(Solat.EXTRA_INDEX, 1);
        if (index < 0 || index >= Solat.NAMES.length) index = 1;
        long at = getIntent().getLongExtra(Solat.EXTRA_AT, System.currentTimeMillis());

        File dir = Config.dir(this);
        File file = dir == null ? null : new File(dir, index == Solat.SUBUH ? "azan_subuh.mp3" : "azan.mp3");
        if (file != null && !file.isFile() && dir != null) file = new File(dir, "azan.mp3");
        // Bloatless: an azan.mp3 pushed to the TV wins; otherwise the recording built into the launcher.
        if (file != null && !file.isFile()) file = null;
        buildCard(index, at);

        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        if (audio != null) {
            // Other players pause while the azan plays, and carry on after.
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(change -> { })
                    .build();
            audio.requestAudioFocus(focus);
            int want = Config.load(this).azanVolume;
            if (want >= 0) {
                int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                volumeBefore = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                volumeSet = Math.min(want, max);
                try {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, volumeSet, 0);
                } catch (SecurityException e) {
                    volumeBefore = -1;
                }
            }
        }
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            if (file != null) {
                player.setDataSource(file.getPath());
            } else {
                try (android.content.res.AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.azan)) {
                    player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                }
            }
            player.setOnCompletionListener(mp -> stop());
            player.setOnErrorListener((mp, what, extra) -> {
                Log.w(Solat.TAG, "player error " + what + "/" + extra);
                stop();
                return true;
            });
            player.setOnPreparedListener(MediaPlayer::start);
            player.prepareAsync();
        } catch (Exception e) {
            Log.w(Solat.TAG, "can't play " + (file != null ? file : "built-in azan") + ": " + e);
            stop();
            return;
        }
        handler.postDelayed(this::stop, MAX_MS);
    }

    private void buildCard(int index, long at) {
        float d = getResources().getDisplayMetrics().density;
        Typeface outfit = null;
        try {
            outfit = ResourcesCompat.getFont(this, R.font.outfit);
        } catch (Exception ignored) {
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x99000000);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (36 * d);
        card.setPadding(pad * 2, pad, pad * 2, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(28 * d);
        bg.setColor(0xCC1C1C1E);
        bg.setStroke((int) Math.max(1, d), 0x33FFFFFF);
        card.setBackground(bg);

        boolean h24 = android.text.format.DateFormat.is24HourFormat(this);
        DateFormat fmt = new SimpleDateFormat(h24 ? "HH:mm" : "h:mm a", Locale.ROOT);
        fmt.setTimeZone(Solat.MY);

        card.addView(text("Telah masuk waktu", 20, 0xB3FFFFFF, outfit, 400, d));
        TextView name = text(Solat.NAMES[index], 64, Color.WHITE, outfit, 300, d);
        name.setPadding(0, (int) (6 * d), 0, (int) (2 * d));
        card.addView(name);
        card.addView(text(fmt.format(new Date(at)).toUpperCase(Locale.ROOT), 26, 0xE6FFFFFF, outfit, 400, d));
        TextView hint = text("Tekan mana-mana butang untuk tutup", 15, 0x80FFFFFF, outfit, 400, d);
        hint.setPadding(0, (int) (22 * d), 0, 0);
        card.addView(hint);

        root.addView(card, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setContentView(root);
    }

    private TextView text(String s, float sp, int color, Typeface face, int weight, float d) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        if (face != null) t.setTypeface(face);
        if (android.os.Build.VERSION.SDK_INT >= 26) t.setFontVariationSettings("'wght' " + weight);
        return t;
    }

    /** Any button stops the azan; the press goes no further. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) stop();
        return true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        stop();   // Home pressed, or another app came in front
    }

    @Override
    protected void onDestroy() {
        stop();
        super.onDestroy();
    }

    private void stop() {
        if (done) return;
        done = true;
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        if (audio != null) {
            // Put the volume back, unless it was changed by hand while the azan played.
            if (volumeBefore >= 0 && audio.getStreamVolume(AudioManager.STREAM_MUSIC) == volumeSet) {
                try {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBefore, 0);
                } catch (SecurityException ignored) {
                }
            }
            if (focus != null) audio.abandonAudioFocusRequest(focus);
        }
        if (!isFinishing()) finish();
        overridePendingTransition(0, 0);
    }
}
