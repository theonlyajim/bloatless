package tv.cleanyour.core.flows

import tv.cleanyour.core.model.DeviceFacts
import tv.cleanyour.core.model.Module
import tv.cleanyour.core.model.ModuleVersion
import tv.cleanyour.core.model.Tier
import tv.cleanyour.core.registry.ArtifactFetcher
import tv.cleanyour.core.registry.Registry
import tv.cleanyour.core.trust.ApkInspector

// Bloatless 1.0.6: "Apps to add" on the Customise sheet. Each app is a module of the signed index the
// app ships with (tools/bloatless-index), so it is downloaded from its own GitHub release, checked by
// size, sha256 and signer, and installed by the same AddModule plan as Manage > Cards and modules.
// Nothing is ticked by default; the chosen apps are installed after the clean-up, before Finish
// switches debugging off. The full build only (StoreRules.addOns).
class SetupApps(val registry: Registry, val fetcher: ArtifactFetcher, val inspector: ApkInspector) {
    // The order the sheet shows them in; an id the index does not have is left out.
    fun offered(facts: DeviceFacts): List<AppOffer> = IDS.mapNotNull { id ->
        val m = registry.module(id)?.takeIf { it.tier == Tier.VERIFIED || it.tier == Tier.COMMUNITY } ?: return@mapNotNull null
        val v = best(m, facts) ?: return@mapNotNull null
        AppOffer(m.id, m.name, m.oneLine, v.artifact.size)
    }

    fun module(id: String): Module? = registry.module(id)

    companion object {
        val IDS = listOf("nuvio", "smarttube", "localsend")

        // The newest version this TV can run (ModulesMachine's rule).
        fun best(m: Module, facts: DeviceFacts): ModuleVersion? = m.versions
            .filter { it.minSdk <= facts.sdk && (it.abis.isEmpty() || it.abis.any { a -> a in facts.abis }) }
            .maxByOrNull { it.versionCode }
    }
}

// One row of "Apps to add": the module id, its name and line, and the download size.
data class AppOffer(val id: String, val name: String, val oneLine: String, val sizeBytes: Long)

// One chosen app on Done while it is added, and how it ended.
data class AppInstall(val id: String, val name: String, val step: AppStep, val percent: Int = 0)

enum class AppStep { WAITING, DOWNLOADING, CHECKING, INSTALLING, ADDED, ALREADY, FAILED }
