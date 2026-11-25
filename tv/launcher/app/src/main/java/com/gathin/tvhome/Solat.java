package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Prayer times from JAKIM's e-Solat (www.e-solat.gov.my) for one zone (config.json, solat_zone, e.g.
 * TRG04 for Dungun and Kemaman). A whole year is fetched at once and kept in files/solat/, so the
 * launcher goes online about once a year per zone. The next prayer is set as one exact alarm
 * (AzanReceiver), which plays the azan (AzanActivity) and sets the one after it.
 *
 * Every failure leaves what was there: no file, no alarm, no row on Home.
 */
final class Solat {
    static final String TAG = "TvHome.Solat";
    static final String[] KEYS = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
    static final String[] NAMES = {"Subuh", "Zohor", "Asar", "Maghrib", "Isyak"};
    static final int SUBUH = 0;
    static final TimeZone MY = TimeZone.getTimeZone("Asia/Kuala_Lumpur");
    private static final String API = "https://www.e-solat.gov.my/index.php?r=esolatApi/TakwimSolat&period=duration&zone=";
    /** English and Malay month abbreviations; JAKIM writes Mac, Mei, Ogos, Okt and Dis. */
    private static final String[][] MONTHS = {
            {"jan"}, {"feb"}, {"mar", "mac"}, {"apr"}, {"may", "mei"}, {"jun"},
            {"jul"}, {"aug", "ogos", "ogo"}, {"sep"}, {"oct", "okt"}, {"nov"}, {"dec", "dis"}};
    static final String ACTION_AZAN = "com.gathin.tvhome.AZAN";
    static final String ACTION_CHECK = "com.gathin.tvhome.SOLAT_CHECK";
    static final String EXTRA_INDEX = "index";
    static final String EXTRA_AT = "at";
    private static final long RETRY_MS = 30 * 60_000L;

    private Solat() {
    }

    /** One prayer: which of the five, and when (epoch millis). */
    static final class Time {
        final int index;
        final long at;

        Time(int index, long at) {
            this.index = index;
            this.at = at;
        }
    }

    // ---- parsing (pure, unit-tested) ----

    /** Month 0..11 from "Nov" or "Dis", or -1. */
    static int month(String abbr) {
        String a = abbr.trim().toLowerCase(Locale.ROOT);
        for (int m = 0; m < 12; m++) for (String s : MONTHS[m]) if (a.equals(s)) return m;
        return -1;
    }

    /**
     * Every prayer in an e-Solat answer, in order. Days whose date or times don't parse are skipped.
     */
    static List<Time> parse(String json) {
        List<Time> out = new ArrayList<>();
        try {
            JSONArray days = new JSONObject(json).getJSONArray("prayerTime");
            Calendar c = Calendar.getInstance(MY, Locale.ROOT);
            for (int i = 0; i < days.length(); i++) {
                JSONObject d = days.optJSONObject(i);
                if (d == null) continue;
                String[] date = d.optString("date").split("-");
                if (date.length != 3) continue;
                int m = month(date[1]);
                if (m < 0) continue;
                int day, year;
                try {
                    day = Integer.parseInt(date[0].trim());
                    year = Integer.parseInt(date[2].trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                for (int k = 0; k < KEYS.length; k++) {
                    String[] hm = d.optString(KEYS[k]).split(":");
                    if (hm.length < 2) continue;
                    try {
                        c.clear();
                        c.set(year, m, day, Integer.parseInt(hm[0].trim()), Integer.parseInt(hm[1].trim()), 0);
                        out.add(new Time(k, c.getTimeInMillis()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            // Not an e-Solat answer: no times. Callers log what they fetched.
        }
        return out;
    }

    /** The first prayer strictly after {@code now}, or null. */
    static Time next(List<Time> times, long now) {
        for (Time t : times) if (t.at > now) return t;
        return null;
    }

    /** The five prayers on the calendar day (Malaysia time) that holds {@code now}; missing ones are null. */
    static Time[] today(List<Time> times, long now) {
        Calendar c = Calendar.getInstance(MY, Locale.ROOT);
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis(), end = start + 24 * 3600_000L;
        Time[] out = new Time[KEYS.length];
        for (Time t : times) if (t.at >= start && t.at < end) out[t.index] = t;
        return out;
    }

    // ---- storage ----

    static File file(Context ctx, String zone, int year) {
        File base = Config.dir(ctx);
        return base == null ? null : new File(new File(base, "solat"), zone + "-" + year + ".json");
    }

    static int year(long now) {
        Calendar c = Calendar.getInstance(MY, Locale.ROOT);
        c.setTimeInMillis(now);
        return c.get(Calendar.YEAR);
    }

    /** This year's and next year's prayers, whichever are on disk. */
    static List<Time> load(Context ctx, String zone, long now) {
        List<Time> all = new ArrayList<>();
        int y = year(now);
        for (int year = y; year <= y + 1; year++) {
            String text = read(file(ctx, zone, year));
            if (text != null) all.addAll(parse(text));
        }
        return all;
    }

    private static String read(File f) {
        if (f == null || !f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    // ---- network ----

    /**
     * Fetches this year's times if they are missing, and next year's from December on. Blocking: call it
     * off the main thread. True when something new was saved.
     */
    static boolean fetchMissing(Context ctx, String zone, long now) {
        int y = year(now);
        boolean got = false;
        Calendar c = Calendar.getInstance(MY, Locale.ROOT);
        c.setTimeInMillis(now);
        boolean december = c.get(Calendar.MONTH) == Calendar.DECEMBER;
        for (int year = y; year <= (december ? y + 1 : y); year++) {
            File f = file(ctx, zone, year);
            if (f == null || f.isFile()) continue;
            got |= fetchYear(zone, year, f);
        }
        return got;
    }

    private static boolean fetchYear(String zone, int year, File dest) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(API + zone).openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(20_000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(("datestart=" + year + "-01-01&dateend=" + year + "-12-31").getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() != 200) {
                Log.w(TAG, "fetch " + zone + " " + year + ": HTTP " + conn.getResponseCode());
                return false;
            }
            byte[] body;
            try (InputStream in = conn.getInputStream()) {
                body = readAll(in);
            }
            String text = new String(body, StandardCharsets.UTF_8);
            // Keep only an answer that holds most of the year, so a partial or error page is tried again.
            if (parse(text).size() < 300 * KEYS.length) {
                Log.w(TAG, "fetch " + zone + " " + year + ": too few times");
                return false;
            }
            File dir = dest.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return false;
            File tmp = new File(dest.getPath() + ".tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(body);
            }
            boolean ok = tmp.renameTo(dest);
            Log.i(TAG, "saved " + dest.getName() + " " + ok);
            return ok;
        } catch (Exception e) {
            Log.w(TAG, "fetch " + zone + " " + year + ": " + e);
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ---- alarm ----

    /**
     * Sets the alarm for the next prayer, fetching the year first when it is missing. Blocking (may go
     * online): call it off the main thread. With no zone configured it cancels any alarm.
     */
    static void schedule(Context ctx) {
        Context app = ctx.getApplicationContext();
        Config cfg = Config.load(app);
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent azan = pending(app, ACTION_AZAN, null);
        PendingIntent check = pending(app, ACTION_CHECK, null);
        if (cfg.solatZone.isEmpty()) {
            am.cancel(azan);
            am.cancel(check);
            return;
        }
        long now = System.currentTimeMillis();
        fetchMissing(app, cfg.solatZone, now);
        Time t = next(load(app, cfg.solatZone, now), now + 1000);
        if (t == null) {
            // No times yet (offline at boot): look again in half an hour.
            am.cancel(azan);
            am.set(AlarmManager.RTC, now + RETRY_MS, check);
            Log.i(TAG, "no times for " + cfg.solatZone + ", retry in 30 min");
            return;
        }
        am.cancel(check);
        PendingIntent pi = pending(app, ACTION_AZAN, t);
        // RTC, not RTC_WAKEUP: while the TV is in standby nothing should sound. The alarm then fires late
        // when the TV wakes, and AzanReceiver skips it as stale.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setWindow(AlarmManager.RTC, t.at, 30_000, pi);
        } else {
            am.setExact(AlarmManager.RTC, t.at, pi);
        }
        Log.i(TAG, "next " + NAMES[t.index] + " at " + new java.util.Date(t.at));
        // In December, next year's times are fetched by the call above; daily checks keep that going.
        // A check a day also keeps the alarm right after the clock or time zone changes without a broadcast.
        am.set(AlarmManager.RTC, Math.min(t.at + 60_000, now + 24 * 3600_000L), check);
    }

    private static PendingIntent pending(Context ctx, String action, Time t) {
        Intent i = new Intent(ctx, AzanReceiver.class).setAction(action);
        if (t != null) i.putExtra(EXTRA_INDEX, t.index).putExtra(EXTRA_AT, t.at);
        return PendingIntent.getBroadcast(ctx, ACTION_AZAN.equals(action) ? 1 : 2, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** {@link #schedule} on a background thread. */
    static void scheduleAsync(Context ctx, Runnable then) {
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                schedule(app);
            } catch (Exception e) {
                Log.w(TAG, "schedule: " + e);
            }
            if (then != null) then.run();
        }, "solat").start();
    }
}
