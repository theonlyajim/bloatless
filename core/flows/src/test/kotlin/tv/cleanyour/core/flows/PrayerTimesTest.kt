package tv.cleanyour.core.flows

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Bloatless: today's times as e-Solat answers them, and which one is next.
class PrayerTimesTest {
    private val answer = """{"prayerTime":[{"hijri":"1448-06-05","date":"25-Nov-2026","day":"Wednesday","imsak":"05:36:00","fajr":"05:46:00",
        "syuruk":"06:59:00","dhuhr":"12:58:00","asr":"16:20:00","maghrib":"19:01:00","isha":"20:14:00"}],"status":"OK!"}"""

    @Test fun readsTheFiveTimes() {
        assertEquals(listOf("05:46", "12:58", "16:20", "19:01", "20:14"), PrayerTimesSource.parse(answer))
    }

    @Test fun anAnswerWithoutTimesIsNothing() {
        assertNull(PrayerTimesSource.parse("""{"prayerTime":[],"status":"NO_RECORD!"}"""))
    }

    @Test fun theNextOneWrapsToSubuhAfterIsyak() {
        val t = listOf("05:46", "12:58", "16:20", "19:01", "20:14")
        assertEquals(2, PrayerTimesSource.nextIndex(t, 15 * 60))
        assertEquals(0, PrayerTimesSource.nextIndex(t, 21 * 60))
        assertEquals(0, PrayerTimesSource.nextIndex(t, 3 * 60))
    }
}
