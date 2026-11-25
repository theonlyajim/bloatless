package tv.cleanyour.core.flows

import org.junit.Test
import tv.cleanyour.core.spec.PrayerSettings
import kotlin.test.assertEquals

// Bloatless: tvs/<id>/solat.txt.
class PrayerPrefsTest {
    @Test fun roundTrips() {
        val s = PrayerSettings("SGR01", azan = false, volume = 7, prayers = setOf(0, 2))
        assertEquals(s, PrayerPrefs.decode(PrayerPrefs.encode(s)))
    }

    @Test fun anUnknownZoneOrGarbageLoadsAsOff() {
        assertEquals(PrayerSettings.OFF, PrayerPrefs.decode("zone=KTN03\n"))
        assertEquals(PrayerSettings.OFF, PrayerPrefs.decode("nonsense"))
    }

    @Test fun missingLinesTakeTheDefaults() {
        assertEquals(PrayerSettings("TRG04"), PrayerPrefs.decode("zone=TRG04\n"))
    }
}
