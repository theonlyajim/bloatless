package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Bloatless: plays the azan as a foreground service, so it sounds even when Android does not let the
 * card (AzanActivity) open over another app, such as YouTube. Started by AzanReceiver from the exact
 * alarm (a start Android 12+ allows from the background); the card, when it shows, is only the
 * picture and the way to stop it. The volume the TV had comes back afterwards, unless someone
 * changed it meanwhile. Each step is written to files/azan-log.txt, so a TV that stays quiet can say why.
 *
 * The recordings are files/azan.mp3 and, for Subuh, files/azan_subuh.mp3 (falls back to azan.mp3),
 * else the one built into the launcher.
 */
public final class AzanService extends Service {
    private static final long MAX_MS = 8 * 60_000L;
    private static final int NOTIFICATION_ID = 7;
    private static final String CHANNEL = "azan";

    /** The service while the azan plays; null otherwise. Main thread only. */
    static AzanService current;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private int volumeBefore = -1, volumeSet = -1;
    private boolean done;

    /** Starts the azan for prayer `index`; false when Android refused the start. */
    static boolean start(Context ctx, int index, long at) {
        Intent i = new Intent(ctx, AzanService.class).putExtra(Solat.EXTRA_INDEX, index).putExtra(Solat.EXTRA_AT, at);
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
            return true;
        } catch (Exception e) {
            log(ctx, "service start refused: " + e);
            return false;
        }
    }

    /** Stops the azan, if it plays. */
    static void stopNow() {
        if (current != null) current.finishUp("stopped");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        if (current != null && current != this) current.finishUp("replaced");
        if (player != null) return START_NOT_STICKY;   // already playing
        current = this;
        int index = intent == null ? 1 : intent.getIntExtra(Solat.EXTRA_INDEX, 1);
        if (index < 0 || index >= Solat.NAMES.length) index = 1;
        play(index);
        return START_NOT_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            if (nm != null) nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Azan", NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        Notification n = b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Azan")
                .setOngoing(true)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NOTIFICATION_ID, n);
        } catch (Exception e) {
            log(this, "foreground refused: " + e);   // it still plays while the process lives
        }
    }

    private void play(int index) {
        File dir = Config.dir(this);
        File file = dir == null ? null : new File(dir, index == Solat.SUBUH ? "azan_subuh.mp3" : "azan.mp3");
        if (file != null && !file.isFile() && dir != null) file = new File(dir, "azan.mp3");
        if (file != null && !file.isFile()) file = null;

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
            int granted = audio.requestAudioFocus(focus);
            int want = Config.load(this).azanVolume;
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int now = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (want >= 0) {
                volumeBefore = now;
                volumeSet = Math.min(want, max);
                try {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, volumeSet, 0);
                } catch (SecurityException e) {
                    volumeBefore = -1;
                }
            }
            log(this, "azan " + Solat.NAMES[index] + ": focus " + granted + ", volume " + now + " -> " + volumeSet + " of " + max
                    + ", fixed " + audio.isVolumeFixed() + ", muted " + (Build.VERSION.SDK_INT >= 23 && audio.isStreamMute(AudioManager.STREAM_MUSIC))
                    + ", file " + (file != null ? file.getName() : "built-in"));
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
            player.setOnCompletionListener(mp -> finishUp("finished"));
            player.setOnErrorListener((mp, what, extra) -> {
                finishUp("player error " + what + "/" + extra);
                return true;
            });
            player.setOnPreparedListener(mp -> {
                mp.start();
                log(this, "playing, " + mp.getDuration() / 1000 + " s");
            });
            player.prepareAsync();
        } catch (Exception e) {
            finishUp("can't play " + (file != null ? file : "built-in azan") + ": " + e);
            return;
        }
        handler.postDelayed(() -> finishUp("time limit"), MAX_MS);
    }

    private void finishUp(String why) {
        if (done) return;
        done = true;
        log(this, "azan end: " + why);
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
        if (current == this) current = null;
        AzanActivity.close();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        finishUp("service ended");
        super.onDestroy();
    }

    /** One line in files/azan-log.txt (the last 16 KB kept) and in logcat. */
    static void log(Context ctx, String line) {
        Log.i(Solat.TAG, line);
        try {
            File dir = Config.dir(ctx);
            if (dir == null) return;
            File f = new File(dir, "azan-log.txt");
            if (f.length() > 16 * 1024) {
                File old = new File(dir, "azan-log.old.txt");
                //noinspection ResultOfMethodCallIgnored
                f.renameTo(old);
            }
            try (FileWriter w = new FileWriter(f, true)) {
                w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()) + " " + line + "\n");
            }
        } catch (Exception ignored) {
        }
    }
}
