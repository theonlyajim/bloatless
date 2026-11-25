package tv.cleanyour.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.cleanyour.core.flows.PrayerTimesSource
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection

// Bloatless: today's times from JAKIM e-Solat over HTTPS, kept per zone for the day so the page asks once.
// Only the zone code is sent; nothing about the phone or the TV.
class HttpPrayerTimes : PrayerTimesSource {
    private val cache = ConcurrentHashMap<String, Pair<LocalDate, List<String>>>()

    override suspend fun today(zone: String): List<String>? {
        if (!zone.matches(Regex("[A-Z]{3}[0-9]{2}"))) return null
        val day = LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur"))
        cache[zone]?.takeIf { it.first == day }?.let { return it.second }
        val times = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(PrayerTimesSource.URL + zone).openConnection() as HttpsURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 10_000
                try {
                    if (c.responseCode != 200) null
                    else c.inputStream.use { s -> PrayerTimesSource.parse(s.readBytes().take(64 * 1024).toByteArray().decodeToString()) }
                } finally { c.disconnect() }
            }.getOrNull()
        } ?: return null
        cache[zone] = day to times
        return times
    }
}
