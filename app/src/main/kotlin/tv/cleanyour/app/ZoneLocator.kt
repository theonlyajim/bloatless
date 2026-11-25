package tv.cleanyour.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import tv.cleanyour.core.spec.ZoneFinder
import kotlin.coroutines.resume

// Bloatless: the JAKIM zone for where the phone is, from one coarse fix (network, else the last one known). The
// location is used once here and never stored or sent; only the zone code comes back.
object ZoneLocator {
    @SuppressLint("MissingPermission")   // asked first by the caller (ACCESS_COARSE_LOCATION)
    suspend fun zone(context: Context): String? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        // One current fix from the first provider that gives one (fused, network, GPS), else the newest last known.
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER); add(LocationManager.GPS_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        var fix: Location? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for (p in providers) {
                fix = withTimeoutOrNull(8_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        lm.getCurrentLocation(p, signal, context.mainExecutor) { cont.resume(it) }
                    }
                }
                if (fix != null) break
            }
        }
        if (fix == null) fix = (providers + LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }
        return fix?.let { ZoneFinder.nearest(it.latitude, it.longitude) }
    }
}
