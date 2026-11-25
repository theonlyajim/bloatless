package tv.cleanyour.core.flows

// Bloatless: today's five JAKIM prayer times for a zone, for the Prayer times page ("HH:mm", Subuh first).
// The app's source asks e-Solat (www.e-solat.gov.my) once per zone and day; NONE shows no times.
fun interface PrayerTimesSource {
    suspend fun today(zone: String): List<String>?

    companion object {
        val NONE = PrayerTimesSource { null }
        const val URL = "https://www.e-solat.gov.my/index.php?r=esolatApi/TakwimSolat&period=today&zone="
        private val KEYS = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")

        // The five times from an e-Solat answer ({"prayerTime":[{"fajr":"05:46:00",...}]}), or null.
        fun parse(json: String): List<String>? {
            val times = KEYS.map { k -> Regex("\"$k\"\\s*:\\s*\"(\\d{1,2}):(\\d{2})").find(json)?.let { "${it.groupValues[1].padStart(2, '0')}:${it.groupValues[2]}" } }
            return if (times.all { it != null }) times.filterNotNull() else null
        }

        // Which of the five comes next at `nowMinutes` past midnight (Malaysia time); 0 (Subuh) after Isyak.
        fun nextIndex(times: List<String>, nowMinutes: Int): Int {
            val mins = times.map { t -> t.substringBefore(':').toInt() * 60 + t.substringAfter(':').toInt() }
            return mins.indexOfFirst { it > nowMinutes }.let { if (it < 0) 0 else it }
        }
    }
}
