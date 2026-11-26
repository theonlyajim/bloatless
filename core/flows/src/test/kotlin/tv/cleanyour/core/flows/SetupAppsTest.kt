package tv.cleanyour.core.flows

import org.junit.Test
import tv.cleanyour.core.model.ArtifactRef
import tv.cleanyour.core.model.Level
import tv.cleanyour.core.model.Module
import tv.cleanyour.core.model.ModuleVersion
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.Tier
import tv.cleanyour.core.model.WizardDraft
import tv.cleanyour.core.registry.IndexState
import tv.cleanyour.core.trust.ApkInspector
import tv.cleanyour.core.trust.VerifiedIndex
import tv.cleanyour.testing.fixtures.FakeArtifactFetcher
import tv.cleanyour.testing.fixtures.FakeRegistry
import tv.cleanyour.testing.fixtures.MemoryBlobStore
import tv.cleanyour.testing.fixtures.Samples
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Bloatless 1.0.6: "Apps to add" offers the index's apps the TV can run, in SetupApps.IDS order.
class SetupAppsTest {
    private fun version(code: Long, minSdk: Int = 24, abis: List<String> = listOf("armeabi-v7a"), size: Long = 25_000_000) = ModuleVersion(
        versionCode = code, versionName = "1.$code", releasedAt = "2026-11-20T00:00:00Z",
        artifact = ArtifactRef("https://github.com/x/y/releases/download/v1/app.apk", size, Sha256("a".repeat(64)), Sha256("b".repeat(64))),
        permissions = emptyList(), networkStatement = "", minSdk = minSdk, abis = abis, postInstall = emptyList(),
    )

    private fun module(id: String, name: String, tier: Tier = Tier.COMMUNITY, versions: List<ModuleVersion> = listOf(version(1))) = Module(
        id = id, name = name, oneLine = "$name line", description = "", pkg = PackageName("org.example.$id"), type = "app", tier = tier,
        publisher = "x", sourceUrl = "https://github.com/x/$id", licence = "MIT", accent = null, artwork = null, versions = versions,
    )

    private fun apps(vararg modules: Module): SetupApps {
        val registry = FakeRegistry(IndexState.Ready(VerifiedIndex(3, Long.MAX_VALUE, modules.toList(), emptyList()), stale = false, fetchedAtMs = 0))
        return SetupApps(registry, FakeArtifactFetcher(MemoryBlobStore()), object : ApkInspector { override fun inspect(blob: tv.cleanyour.core.model.BlobRef) = null })
    }

    @Test fun theIndexAppsTheTvCanRunInTheSheetsOrder() {
        val facts = Samples.miTvFacts().copy(sdk = 30, abis = listOf("armeabi-v7a"))
        val a = apps(
            module("localsend", "LocalSend"), module("retro", "Retro"), module("smarttube", "SmartTube"),
            module("nuvio", "Nuvio", versions = listOf(version(1), version(2, size = 55_000_000))),
        )
        assertEquals(listOf("nuvio", "smarttube", "localsend"), a.offered(facts).map { it.id }, "Retro is a module, not one of Setup's apps")
        assertEquals(AppOffer("nuvio", "Nuvio", "Nuvio line", 55_000_000), a.offered(facts).first(), "the newest version's size")
    }

    @Test fun anAppTheTvCannotRunOrThatIsPausedIsLeftOut() {
        val old = Samples.miTvFacts().copy(sdk = 23, abis = listOf("armeabi-v7a"))
        val x86 = Samples.miTvFacts().copy(sdk = 30, abis = listOf("x86"))
        val a = apps(module("nuvio", "Nuvio"), module("smarttube", "SmartTube", versions = listOf(version(1, minSdk = 17))),
            module("localsend", "LocalSend", tier = Tier.QUARANTINED))
        assertEquals(listOf("smarttube"), a.offered(old).map { it.id }, "Nuvio needs Android 7")
        assertEquals(emptyList(), a.offered(x86).map { it.id }, "armeabi-v7a builds only")
        assertNull(SetupApps.best(a.module("nuvio")!!, old))
    }

    @Test fun theChosenAppsSurviveARestartWithTheDraft() {
        val tv = Samples.tv().copy(wizard = WizardDraft(3, Level.SIMPLE, emptySet(), emptySet(), null, null, false, null, 5, apps = setOf("smarttube", "nuvio")))
        assertEquals(tv, TvJson.decode(TvJson.encode(tv)))
        val unknown = TvJson.encode(tv).decodeToString().replace("\"smarttube\"", "\"somethingelse\"")
        assertEquals(setOf("nuvio"), TvJson.decode(unknown.encodeToByteArray()).wizard?.apps, "an id the app does not offer is dropped")
        val old = TvJson.encode(tv).decodeToString().replace(",\"apps\":[\"nuvio\",\"smarttube\"]", "")
        assertEquals(emptySet(), TvJson.decode(old.encodeToByteArray()).wizard?.apps, "a draft from before 1.0.6 has none")
    }
}
