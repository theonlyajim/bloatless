package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** JAKIM e-Solat answers: Malay month names, the next prayer, and today's five. */
public class SolatTest {
    private static final String JSON = "{\"prayerTime\":["
            + "{\"date\":\"31-Dis-2026\",\"fajr\":\"06:01:00\",\"dhuhr\":\"13:13:00\",\"asr\":\"16:35:00\",\"maghrib\":\"19:08:00\",\"isha\":\"20:22:00\"},"
            + "{\"date\":\"01-Jan-2027\",\"fajr\":\"06:02:00\",\"dhuhr\":\"13:13:00\",\"asr\":\"16:36:00\",\"maghrib\":\"19:08:00\",\"isha\":\"20:23:00\"},"
            + "{\"date\":\"02-Xyz-2027\",\"fajr\":\"06:02:00\"}"
            + "],\"status\":\"OK!\"}";

    private static long at(int y, int m, int d, int h, int min) {
        Calendar c = Calendar.getInstance(Solat.MY, Locale.ROOT);
        c.clear();
        c.set(y, m, d, h, min, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void readsMalayAndEnglishMonths() {
        assertEquals(2, Solat.month("Mac"));
        assertEquals(4, Solat.month("Mei"));
        assertEquals(7, Solat.month("Ogos"));
        assertEquals(9, Solat.month("Okt"));
        assertEquals(11, Solat.month("Dis"));
        assertEquals(11, Solat.month("Dec"));
        assertEquals(-1, Solat.month("Xyz"));
    }

    @Test
    public void parsesEveryPrayerAndSkipsBadDays() {
        List<Solat.Time> t = Solat.parse(JSON);
        assertEquals(10, t.size());
        assertEquals(at(2026, Calendar.DECEMBER, 31, 6, 1), t.get(0).at);
        assertEquals(Solat.SUBUH, t.get(0).index);
        assertEquals(at(2027, Calendar.JANUARY, 1, 20, 23), t.get(9).at);
        assertEquals(0, Solat.parse("not json").size());
    }

    @Test
    public void nextCrossesTheNewYear() {
        List<Solat.Time> t = Solat.parse(JSON);
        Solat.Time n = Solat.next(t, at(2026, Calendar.DECEMBER, 31, 21, 0));
        assertNotNull(n);
        assertEquals(Solat.SUBUH, n.index);
        assertEquals(at(2027, Calendar.JANUARY, 1, 6, 2), n.at);
        assertNull(Solat.next(t, at(2027, Calendar.JANUARY, 1, 21, 0)));
    }

    @Test
    public void todayHoldsTheDaysFive() {
        Solat.Time[] day = Solat.today(Solat.parse(JSON), at(2026, Calendar.DECEMBER, 31, 0, 30));
        assertEquals(5, day.length);
        assertEquals(at(2026, Calendar.DECEMBER, 31, 16, 35), day[2].at);
    }
}
