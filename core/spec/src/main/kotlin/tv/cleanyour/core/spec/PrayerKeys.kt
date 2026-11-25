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
