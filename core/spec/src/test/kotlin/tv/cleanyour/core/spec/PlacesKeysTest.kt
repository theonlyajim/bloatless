package tv.cleanyour.core.spec

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Bloatless: the changing wallpaper's keys.
class PlacesKeysTest {
    private val base = LauncherConfigDoc(columns = 6, wallpaper = false, wallpaperRotationMinutes = 0)

    @Test fun offLeavesTheLookAlone() {
        val doc = PlacesKeys.apply(base, PlacesSettings.OFF)
        assertFalse(PlacesKeys.KEY in doc.unknown)
        assertEquals(false, doc.wallpaper)
        assertEquals(0, doc.wallpaperRotationMinutes)
    }

    @Test fun onTurnsTheWallpaperOnAndSetsTheInterval() {
        val doc = PlacesKeys.apply(base, PlacesSettings(true, 360))
        assertEquals(JsonPrimitive(true), doc.unknown[PlacesKeys.KEY])
        assertEquals(true, doc.wallpaper)
        assertEquals(360, doc.wallpaperRotationMinutes)
    }

    @Test fun roundTripsThroughTheCodecWithoutWarnings() {
        val s = PlacesSettings(true, 15)
        val back = LauncherConfigCodec.decode(LauncherConfigCodec.encode(PlacesKeys.apply(base, s))) as DecodeResult.Ok
        assertEquals(s, PlacesKeys.read(back.doc))
        assertTrue(back.warnings.none { "places" in it }, back.warnings.toString())
    }
}
