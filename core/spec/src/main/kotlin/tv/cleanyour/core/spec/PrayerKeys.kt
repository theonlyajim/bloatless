package tv.cleanyour.core.spec

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

// Bloatless: JAKIM prayer times and the azan on the home screen (launcher 3.0.1, tv/launcher: Solat,
// AzanReceiver, AzanActivity). The phone keeps a choice per TV (PrayerPrefs) and puts these keys in
// config.json on every send (LookCarry), so a new look never drops them.
//   solat_zone    JAKIM e-Solat zone, e.g. TRG04; missing turns the row and the azan off
//   azan          false keeps the row and mutes the azan (the launcher's default is true)
//   azan_volume   music volume while the azan plays, 0 to 30 here (the launcher takes -1 to 1000)
//   azan_prayers  which prayers play the azan, by name; missing means all five
data class PrayerSettings(
    val zone: String = "",
    val azan: Boolean = true,
    val volume: Int = DEFAULT_VOLUME,
    val prayers: Set<Int> = ALL,
) {
    val on: Boolean get() = zone.isNotEmpty()

    companion object {
        const val DEFAULT_VOLUME = 20
        val VOLUME = 0..30
        val ALL: Set<Int> = setOf(0, 1, 2, 3, 4)
        val OFF = PrayerSettings()
    }
}

data class JakimZone(val code: String, val state: String, val places: String)

object PrayerKeys {
    // The names the launcher reads in azan_prayers (Solat.NAMES), Subuh first.
    val NAMES = listOf("Subuh", "Zohor", "Asar", "Maghrib", "Isyak")
    val KEYS: Set<String> = setOf("solat_zone", "azan", "azan_volume", "azan_prayers")
    private val ZONE = Regex("[A-Z]{3}[0-9]{2}")

    // `doc` with this TV's prayer keys: the old ones go, and the new ones only when a zone is chosen.
    fun apply(doc: LauncherConfigDoc, s: PrayerSettings): LauncherConfigDoc {
        val unknown = LinkedHashMap(doc.unknown).apply { keys.removeAll(KEYS) }
        val zone = s.zone.trim().uppercase()
        if (zone.matches(ZONE)) {
            unknown["solat_zone"] = JsonPrimitive(zone)
            if (!s.azan) unknown["azan"] = JsonPrimitive(false)
            unknown["azan_volume"] = JsonPrimitive(s.volume.coerceIn(PrayerSettings.VOLUME))
            val which = s.prayers.filter { it in NAMES.indices }.sorted()
            if (which.size != NAMES.size) unknown["azan_prayers"] = JsonArray(which.map { JsonPrimitive(NAMES[it]) })
        }
        return doc.copy(unknown = unknown)
    }

    // What a config.json says, as the launcher reads it (Config.load in tv/launcher).
    fun read(doc: LauncherConfigDoc?): PrayerSettings {
        val u = doc?.unknown ?: return PrayerSettings.OFF
        val zone = (u["solat_zone"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.uppercase().orEmpty()
        if (!zone.matches(ZONE)) return PrayerSettings.OFF
        val azan = (u["azan"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: true
        val volume = (u["azan_volume"] as? JsonPrimitive)?.content?.toIntOrNull()?.coerceIn(PrayerSettings.VOLUME) ?: PrayerSettings.DEFAULT_VOLUME
        val prayers = (u["azan_prayers"] as? JsonArray)?.mapNotNull { e: JsonElement ->
            (e as? JsonPrimitive)?.content?.let { n -> NAMES.indexOfFirst { it.equals(n.trim(), ignoreCase = true) }.takeIf { it >= 0 } }
        }?.toSet() ?: PrayerSettings.ALL
        return PrayerSettings(zone, azan, volume, prayers)
    }

    fun zone(code: String): JakimZone? = ZONES.firstOrNull { it.code == code }

    val STATES: List<String> get() = ZONES.map { it.state }.distinct()

    // JAKIM e-Solat zones, each checked against the e-Solat API on 2026-11-25 (KTN03 is gone).
    val ZONES: List<JakimZone> = listOf(
        JakimZone("JHR01", "Johor", "Pulau Aur, Pulau Pemanggil"),
        JakimZone("JHR02", "Johor", "Johor Bahru, Kota Tinggi, Mersing, Kulai"),
        JakimZone("JHR03", "Johor", "Kluang, Pontian"),
        JakimZone("JHR04", "Johor", "Batu Pahat, Muar, Segamat, Gemas, Tangkak"),
        JakimZone("KDH01", "Kedah", "Kota Setar, Kubang Pasu, Pokok Sena"),
        JakimZone("KDH02", "Kedah", "Kuala Muda, Yan, Pendang"),
        JakimZone("KDH03", "Kedah", "Padang Terap, Sik"),
        JakimZone("KDH04", "Kedah", "Baling"),
        JakimZone("KDH05", "Kedah", "Bandar Baharu, Kulim"),
        JakimZone("KDH06", "Kedah", "Langkawi"),
        JakimZone("KDH07", "Kedah", "Puncak Gunung Jerai"),
        JakimZone("KTN01", "Kelantan", "Kota Bharu, Bachok, Machang, Pasir Mas, Pasir Puteh, Tanah Merah, Tumpat, Kuala Krai"),
        JakimZone("KTN02", "Kelantan", "Gua Musang, Jeli, Lojing"),
        JakimZone("MLK01", "Melaka", "Seluruh Melaka"),
        JakimZone("NGS01", "Negeri Sembilan", "Tampin, Jempol"),
        JakimZone("NGS02", "Negeri Sembilan", "Jelebu, Kuala Pilah, Rembau"),
        JakimZone("NGS03", "Negeri Sembilan", "Port Dickson, Seremban"),
        JakimZone("PHG01", "Pahang", "Pulau Tioman"),
        JakimZone("PHG02", "Pahang", "Kuantan, Pekan, Muadzam Shah"),
        JakimZone("PHG03", "Pahang", "Jerantut, Temerloh, Maran, Bera, Chenor, Jengka"),
        JakimZone("PHG04", "Pahang", "Bentong, Lipis, Raub"),
        JakimZone("PHG05", "Pahang", "Genting Sempah, Janda Baik, Bukit Tinggi"),
        JakimZone("PHG06", "Pahang", "Cameron Highlands, Genting Highlands, Bukit Fraser"),
        JakimZone("PHG07", "Pahang", "Rompin, Endau, Pontian"),
        JakimZone("PLS01", "Perlis", "Kangar, Padang Besar, Arau"),
        JakimZone("PNG01", "Pulau Pinang", "Seluruh Pulau Pinang"),
        JakimZone("PRK01", "Perak", "Tapah, Slim River, Tanjung Malim"),
        JakimZone("PRK02", "Perak", "Ipoh, Kuala Kangsar, Sungai Siput, Batu Gajah, Kampar"),
        JakimZone("PRK03", "Perak", "Lenggong, Pengkalan Hulu, Grik"),
        JakimZone("PRK04", "Perak", "Temengor, Belum"),
        JakimZone("PRK05", "Perak", "Teluk Intan, Bagan Datuk, Seri Iskandar, Lumut, Sitiawan, Pangkor"),
        JakimZone("PRK06", "Perak", "Taiping, Selama, Bagan Serai, Parit Buntar"),
        JakimZone("PRK07", "Perak", "Bukit Larut"),
        JakimZone("SBH01", "Sabah", "Sandakan, Bukit Garam, Semawang, Temanggong, Tambisan"),
        JakimZone("SBH02", "Sabah", "Beluran, Telupid, Pinangah, Terusan, Kuamut"),
        JakimZone("SBH03", "Sabah", "Lahad Datu, Kunak, Semporna, Tungku"),
        JakimZone("SBH04", "Sabah", "Tawau, Balong, Merotai, Kalabakan"),
        JakimZone("SBH05", "Sabah", "Kudat, Kota Marudu, Pitas, Pulau Banggi"),
        JakimZone("SBH06", "Sabah", "Gunung Kinabalu"),
        JakimZone("SBH07", "Sabah", "Kota Kinabalu, Ranau, Kota Belud, Tuaran, Penampang, Papar, Putatan"),
        JakimZone("SBH08", "Sabah", "Keningau, Pensiangan, Tambunan, Nabawan"),
        JakimZone("SBH09", "Sabah", "Beaufort, Kuala Penyu, Sipitang, Tenom, Membakut, Weston"),
        JakimZone("SGR01", "Selangor", "Gombak, Petaling, Sepang, Hulu Langat, Hulu Selangor, Shah Alam"),
        JakimZone("SGR02", "Selangor", "Kuala Selangor, Sabak Bernam"),
        JakimZone("SGR03", "Selangor", "Klang, Kuala Langat"),
        JakimZone("SWK01", "Sarawak", "Limbang, Lawas, Sundar, Trusan"),
        JakimZone("SWK02", "Sarawak", "Miri, Niah, Bekenu, Sibuti, Marudi"),
        JakimZone("SWK03", "Sarawak", "Bintulu, Pandan, Belaga, Suai, Tatau, Sebauh"),
        JakimZone("SWK04", "Sarawak", "Sibu, Mukah, Dalat, Song, Igan, Oya, Balingian, Kanowit, Kapit"),
        JakimZone("SWK05", "Sarawak", "Sarikei, Matu, Julau, Rajang, Daro, Bintangor, Belawai"),
        JakimZone("SWK06", "Sarawak", "Sri Aman, Lubok Antu, Roban, Betong, Saratok, Pusa, Spaoh"),
        JakimZone("SWK07", "Sarawak", "Serian, Simunjan, Samarahan, Sebuyau, Meludam"),
        JakimZone("SWK08", "Sarawak", "Kuching, Bau, Lundu, Sematan"),
        JakimZone("SWK09", "Sarawak", "Kampung Patarikan"),
        JakimZone("TRG01", "Terengganu", "Kuala Terengganu, Marang, Kuala Nerus"),
        JakimZone("TRG02", "Terengganu", "Besut, Setiu"),
        JakimZone("TRG03", "Terengganu", "Hulu Terengganu"),
        JakimZone("TRG04", "Terengganu", "Dungun, Kemaman"),
        JakimZone("WLY01", "Wilayah Persekutuan", "Kuala Lumpur, Putrajaya"),
        JakimZone("WLY02", "Wilayah Persekutuan", "Labuan"),
    )
}

// Bloatless: the zone nearest a place, from the towns of each zone (approximate, the person confirms it). Zones that
// are a mountain or a single village (Gunung Jerai, Bukit Larut, Temengor, Gunung Kinabalu, Kampung Patarikan,
// Genting Sempah) are never picked from a location: their towns sit inside a bigger zone around them.
object ZoneFinder {
    private val TOWNS: Map<String, List<Pair<Double, Double>>> = mapOf(
        "JHR01" to listOf(2.45 to 104.52), "JHR02" to listOf(1.49 to 103.74, 1.74 to 103.90, 2.43 to 103.83, 1.66 to 103.60),
        "JHR03" to listOf(2.03 to 103.32, 1.49 to 103.39), "JHR04" to listOf(1.85 to 102.93, 2.05 to 102.57, 2.50 to 102.82, 2.27 to 102.54),
        "KDH01" to listOf(6.12 to 100.37, 6.43 to 100.43), "KDH02" to listOf(5.65 to 100.49, 5.96 to 100.37), "KDH03" to listOf(5.81 to 100.73, 6.22 to 100.73),
        "KDH04" to listOf(5.68 to 100.92), "KDH05" to listOf(5.37 to 100.56, 5.21 to 100.62), "KDH06" to listOf(6.35 to 99.80),
        "KTN01" to listOf(6.13 to 102.24, 5.53 to 102.20, 5.76 to 102.22), "KTN02" to listOf(4.88 to 101.97, 5.70 to 101.84),
        "MLK01" to listOf(2.19 to 102.25, 2.38 to 102.21),
        "NGS01" to listOf(2.47 to 102.23, 2.75 to 102.55), "NGS02" to listOf(2.74 to 102.25, 3.06 to 102.10, 2.42 to 102.10),
        "NGS03" to listOf(2.73 to 101.94, 2.52 to 101.80),
        "PHG01" to listOf(2.79 to 104.16), "PHG02" to listOf(3.81 to 103.33, 3.49 to 103.39, 2.79 to 102.82),
        "PHG03" to listOf(3.45 to 102.42, 3.93 to 102.36, 3.53 to 102.75), "PHG04" to listOf(3.79 to 101.86, 3.52 to 101.91, 4.18 to 102.05),
        "PHG06" to listOf(4.47 to 101.38), "PHG07" to listOf(2.80 to 103.49),
        "PLS01" to listOf(6.44 to 100.20), "PNG01" to listOf(5.41 to 100.33, 5.35 to 100.47),
        "PRK01" to listOf(4.20 to 101.26, 3.68 to 101.52), "PRK02" to listOf(4.60 to 101.08, 4.77 to 100.94, 4.31 to 101.15),
        "PRK03" to listOf(5.43 to 101.13), "PRK05" to listOf(4.02 to 101.02, 4.21 to 100.70, 4.37 to 100.63),
        "PRK06" to listOf(4.85 to 100.74, 5.01 to 100.53, 5.11 to 100.48),
        "SBH01" to listOf(5.84 to 118.12), "SBH02" to listOf(5.89 to 117.56), "SBH03" to listOf(5.03 to 118.33, 4.48 to 118.61),
        "SBH04" to listOf(4.24 to 117.89), "SBH05" to listOf(6.88 to 116.84, 6.49 to 116.77),
        "SBH07" to listOf(5.98 to 116.07, 5.96 to 116.68, 5.73 to 115.93), "SBH08" to listOf(5.34 to 116.16), "SBH09" to listOf(5.35 to 115.75, 4.80 to 115.77),
        "SGR01" to listOf(3.07 to 101.52, 3.25 to 101.65, 2.99 to 101.79, 2.72 to 101.75, 3.32 to 101.58, 3.11 to 101.61),
        "SGR02" to listOf(3.34 to 101.25, 3.68 to 100.99), "SGR03" to listOf(3.04 to 101.45, 2.82 to 101.50),
        "SWK01" to listOf(4.75 to 115.00), "SWK02" to listOf(4.40 to 113.99), "SWK03" to listOf(3.17 to 113.04), "SWK04" to listOf(2.29 to 111.83, 2.90 to 112.10),
        "SWK05" to listOf(2.13 to 111.52), "SWK06" to listOf(1.24 to 111.46), "SWK07" to listOf(1.17 to 110.57), "SWK08" to listOf(1.55 to 110.36),
        "TRG01" to listOf(5.33 to 103.14), "TRG02" to listOf(5.74 to 102.49), "TRG03" to listOf(5.07 to 103.01), "TRG04" to listOf(4.76 to 103.42, 4.23 to 103.42),
        "WLY01" to listOf(3.14 to 101.69, 2.93 to 101.69), "WLY02" to listOf(5.28 to 115.24),
    )

    // The zone with the town nearest (lat, lon), or null outside Malaysia (more than about 60 km from every town).
    fun nearest(lat: Double, lon: Double): String? {
        var best: String? = null
        var bestKm = Double.MAX_VALUE
        for ((zone, towns) in TOWNS) for ((tLat, tLon) in towns) {
            val km = km(lat, lon, tLat, tLon)
            if (km < bestKm) { bestKm = km; best = zone }
        }
        return best.takeIf { bestKm <= 60.0 }
    }

    private fun km(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(bLat - aLat); val dLon = Math.toRadians(bLon - aLon)
        val h = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(aLat)) * Math.cos(Math.toRadians(bLat)) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(h))
    }
}
