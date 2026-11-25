package tv.cleanyour.core.flows

import org.junit.Test
import tv.cleanyour.core.spec.PlacesSettings
import kotlin.test.assertEquals

// Bloatless: tvs/<id>/wallpaper.txt.
class WallpaperPrefsTest {
    @Test fun roundTrips() {
        val s = PlacesSettings(true, 1440)
        assertEquals(s, WallpaperPrefs.decode(WallpaperPrefs.encode(s)))
    }

    @Test fun offOrGarbageIsOff() {
        assertEquals(PlacesSettings.OFF, WallpaperPrefs.decode("on=false\nminutes=15\n"))
        assertEquals(PlacesSettings.OFF, WallpaperPrefs.decode("rubbish"))
    }
}
