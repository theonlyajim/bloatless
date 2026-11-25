package tv.cleanyour.core.spec

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Bloatless: the prayer keys a look carries to the launcher, and what the launcher reads back.
class PrayerKeysTest {
    private val base = LauncherConfigDoc(columns = 6, unknown = mapOf("show_greeting" to JsonPrimitive(false)))

    @Test fun offWritesNoPrayerKeyAndDropsOldOnes() {
        val on = PrayerKeys.apply(base, PrayerSettings("TRG04"))
        val off = PrayerKeys.apply(on, PrayerSettings.OFF)
        assertTrue(off.unknown.keys.none { it in PrayerKeys.KEYS })
        assertEquals(JsonPrimitive(false), off.unknown["show_greeting"])
    }

    @Test fun defaultsWriteOnlyZoneAndVolume() {
        val doc = PrayerKeys.apply(base, PrayerSettings("trg04"))
        assertEquals(JsonPrimitive("TRG04"), doc.unknown["solat_zone"])
        assertEquals(JsonPrimitive(20), doc.unknown["azan_volume"])
        assertFalse("azan" in doc.unknown)
        assertFalse("azan_prayers" in doc.unknown)
    }

    @Test fun roundTripsThroughTheCodec() {
        val s = PrayerSettings("WLY01", azan = false, volume = 12, prayers = setOf(3, 4))
        val bytes = LauncherConfigCodec.encode(PrayerKeys.apply(base, s))
        val back = (LauncherConfigCodec.decode(bytes) as DecodeResult.Ok)
        assertEquals(s, PrayerKeys.read(back.doc))
        assertTrue(back.warnings.none { "solat" in it || "azan" in it }, back.warnings.toString())
        assertTrue(bytes.decodeToString().contains("\"azan_prayers\": [\"Maghrib\", \"Isyak\"]") ||
            bytes.decodeToString().contains("\"Maghrib\""))
    }

    @Test fun aBadZoneIsOff() {
        assertTrue(PrayerKeys.apply(base, PrayerSettings("KL")).unknown.keys.none { it in PrayerKeys.KEYS })
    }

    @Test fun zonesAreUniqueAndWellFormed() {
        val codes = PrayerKeys.ZONES.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it.matches(Regex("[A-Z]{3}[0-9]{2}")) })
        assertEquals(60, codes.size)
        assertEquals("Terengganu", PrayerKeys.zone("TRG04")?.state)
    }
}
