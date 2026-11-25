package tv.cleanyour.core.spec

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Bloatless: the zone a phone's location points at.
class ZoneFinderTest {
    @Test fun knownTowns() {
        assertEquals("TRG04", ZoneFinder.nearest(4.77, 103.40))      // Dungun
        assertEquals("WLY01", ZoneFinder.nearest(3.15, 101.70))      // Kuala Lumpur
        assertEquals("WLY01", ZoneFinder.nearest(2.92, 101.68))      // Putrajaya
        assertEquals("SGR01", ZoneFinder.nearest(3.07, 101.52))      // Shah Alam
        assertEquals("JHR02", ZoneFinder.nearest(1.47, 103.76))      // Johor Bahru
        assertEquals("SWK08", ZoneFinder.nearest(1.55, 110.34))      // Kuching
        assertEquals("SBH07", ZoneFinder.nearest(5.98, 116.07))      // Kota Kinabalu
        assertEquals("PNG01", ZoneFinder.nearest(5.41, 100.33))      // George Town
    }

    @Test fun outsideMalaysiaIsNothing() {
        assertNull(ZoneFinder.nearest(51.5, -0.12))                   // London
        assertNull(ZoneFinder.nearest(-6.2, 106.8))                   // Jakarta
    }

    @Test fun everyTownIsAZone() {
        val codes = PrayerKeys.ZONES.map { it.code }.toSet()
        assertTrue(listOf("TRG01", "KTN01", "MLK01", "PLS01", "WLY02").all { it in codes })
    }
}
