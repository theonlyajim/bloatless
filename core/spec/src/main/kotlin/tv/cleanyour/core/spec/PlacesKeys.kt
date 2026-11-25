package tv.cleanyour.core.spec

import kotlinx.serialization.json.JsonPrimitive

// Bloatless: a changing wallpaper, pictures of places renewed each week (launcher 3.0.1, PlaceWallpapers),
// each shown for `minutes`. Off by default; the phone keeps it per TV (WallpaperPrefs) and LookCarry puts it
// in config.json on every send, so a new look never drops it.
//   places                       true turns it on (the launcher's default is off)
//   wallpaper_rotation_minutes   how long each picture stays (modelled: LauncherConfigDoc.wallpaperRotationMinutes)
data class PlacesSettings(val on: Boolean = false, val minutes: Int = DEFAULT_MINUTES) {
    companion object {
        const val DEFAULT_MINUTES = 60
        val CHOICES = listOf(15, 60, 360, 1440)
        val OFF = PlacesSettings()
    }
}

object PlacesKeys {
    const val KEY = "places"

    fun apply(doc: LauncherConfigDoc, s: PlacesSettings): LauncherConfigDoc {
        val unknown = LinkedHashMap(doc.unknown).apply { remove(KEY) }
        if (!s.on) return doc.copy(unknown = unknown)
        unknown[KEY] = JsonPrimitive(true)
        return doc.copy(unknown = unknown, wallpaper = true, wallpaperRotationMinutes = s.minutes.coerceIn(1, 1440))
    }

    fun read(doc: LauncherConfigDoc?): PlacesSettings {
        val on = (doc?.unknown?.get(KEY) as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
        if (!on) return PlacesSettings.OFF
        return PlacesSettings(true, doc?.wallpaperRotationMinutes?.takeIf { it > 0 } ?: PlacesSettings.DEFAULT_MINUTES)
    }
}
