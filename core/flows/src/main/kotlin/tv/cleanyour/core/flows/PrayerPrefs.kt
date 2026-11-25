package tv.cleanyour.core.flows

import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.spec.PrayerKeys
import tv.cleanyour.core.spec.PrayerSettings
import tv.cleanyour.core.store.DocumentStore

// Bloatless: each TV's prayer times choice, tvs/<id>/solat.txt, one "key=value" line each
// (zone, azan, volume, prayers as 0-4 by commas). A missing or unreadable file is "off".
class PrayerPrefs(private val store: DocumentStore) {
    private fun path(id: TvId) = "tvs/${id.value}/solat.txt"

    suspend fun load(id: TvId): PrayerSettings = store.read(path(id)) { decode(it.decodeToString()) } ?: PrayerSettings.OFF

    suspend fun save(id: TvId, s: PrayerSettings) {
        store.write(path(id), encode(s).encodeToByteArray())
    }

    companion object {
        fun encode(s: PrayerSettings): String = buildString {
            append("zone=").append(s.zone).append('\n')
            append("azan=").append(s.azan).append('\n')
            append("volume=").append(s.volume).append('\n')
            append("prayers=").append(s.prayers.sorted().joinToString(",")).append('\n')
        }

        fun decode(text: String): PrayerSettings {
            val m = text.lineSequence().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1).trim() } }.toMap()
            val zone = m["zone"].orEmpty().uppercase().takeIf { z -> PrayerKeys.zone(z) != null }.orEmpty()
            val prayers = m["prayers"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in PrayerKeys.NAMES.indices }?.toSet()
            return PrayerSettings(
                zone = zone,
                azan = m["azan"]?.toBooleanStrictOrNull() ?: true,
                volume = m["volume"]?.toIntOrNull()?.coerceIn(PrayerSettings.VOLUME) ?: PrayerSettings.DEFAULT_VOLUME,
                prayers = prayers ?: PrayerSettings.ALL,
            )
        }
    }
}
