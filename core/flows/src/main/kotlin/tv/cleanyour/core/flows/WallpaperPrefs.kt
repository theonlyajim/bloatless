package tv.cleanyour.core.flows

import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.spec.PlacesSettings
import tv.cleanyour.core.store.DocumentStore

// Bloatless: each TV's changing wallpaper choice, tvs/<id>/wallpaper.txt ("on=" and "minutes=" lines).
// A missing or unreadable file is off.
class WallpaperPrefs(private val store: DocumentStore) {
    private fun path(id: TvId) = "tvs/${id.value}/wallpaper.txt"

    suspend fun load(id: TvId): PlacesSettings = store.read(path(id)) { decode(it.decodeToString()) } ?: PlacesSettings.OFF

    suspend fun save(id: TvId, s: PlacesSettings) {
        store.write(path(id), encode(s).encodeToByteArray())
    }

    companion object {
        fun encode(s: PlacesSettings): String = "on=${s.on}\nminutes=${s.minutes}\n"

        fun decode(text: String): PlacesSettings {
            val m = text.lineSequence().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1).trim() } }.toMap()
            val on = m["on"]?.toBooleanStrictOrNull() ?: false
            val minutes = m["minutes"]?.toIntOrNull()?.takeIf { it in 1..1440 } ?: PlacesSettings.DEFAULT_MINUTES
            return if (on) PlacesSettings(true, minutes) else PlacesSettings.OFF
        }
    }
}
