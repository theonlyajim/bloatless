package tv.cleanyour.core.flows

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.discovery.DiscoveryEvent
import tv.cleanyour.core.model.ArtifactRef
import tv.cleanyour.core.model.Module
import tv.cleanyour.core.model.ModuleVersion
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Tier
import tv.cleanyour.core.planner.PlanKind
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.core.planner.PlanResult
import tv.cleanyour.core.registry.IndexState
import tv.cleanyour.core.model.ApkInfo
import tv.cleanyour.core.trust.VerifiedIndex
import tv.cleanyour.testing.fixtures.FakeApkInspector
import tv.cleanyour.testing.fixtures.FakeArtifactFetcher
import tv.cleanyour.testing.fixtures.FakeRegistry
import tv.cleanyour.testing.fixtures.sha256Of
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// Bloatless 1.0.6: an app ticked under "Apps to add" is downloaded, checked and installed by an
// AddModule plan after the clean-up, and Finish waits for it.
@OptIn(ExperimentalCoroutinesApi::class)
class SetupAppsInstallTest {
    private val apk = "smarttube apk bytes".encodeToByteArray()
    private val signer = sha256Of("smarttube signer".encodeToByteArray())
    private val ref = ArtifactRef("https://github.com/yuliskov/SmartTube/releases/download/32.80s/st.apk", apk.size.toLong(), sha256Of(apk), signer)
    private val smarttube = Module("smarttube", "SmartTube", "YouTube for the TV, without ads", "", PackageName("org.smarttube.stable"), "app",
        Tier.COMMUNITY, "yuliskov", "https://github.com/yuliskov/SmartTube", "MIT", null, null,
        listOf(ModuleVersion(2470, "32.80", "2026-11-19T13:56:02Z", ref, emptyList(), "", 17, emptyList(), emptyList())))

    private suspend fun TestScope.toFound(h: Harness, m: SetupMachine) {
        h.discovery.emit(DiscoveryEvent.Found(miTvFound))
        settle()
        m.dispatch(SetupIntent.Start)
        settle()
        h.discovery.open5555[HOST] = true
        settle(2_100)
        settle(700)
    }

    private fun harness(): Pair<Harness, SetupApps> {
        val h = Harness()
        h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
        val registry = FakeRegistry(IndexState.Ready(VerifiedIndex(3, Long.MAX_VALUE, listOf(smarttube), emptyList()), stale = false, fetchedAtMs = 0))
        val inspector = FakeApkInspector(mapOf(sha256Of(apk) to ApkInfo(smarttube.pkg, 2470, "32.80", signer, sha256Of(apk), apk.size.toLong(), "test")))
        val apps = SetupApps(registry, FakeArtifactFetcher(h.blobs, mapOf(ref.url to apk)), inspector)
        val base = h.planner.respond
        h.planner.respond = { request, snap ->
            if (request is PlanRequest.AddModule) PlanResult.Ok(h.plan(listOf(Op.Disable(PackageName("com.example.placeholder"))), kind = PlanKind.ADD_MODULE, id = "01FAKEADDMODULE00000000001"))
            else base(request, snap)
        }
        return h to apps
    }

    @Test fun aTickedAppIsAddedAfterTheCleanUp() = runTest(timeout = 30.seconds) {
        val (h, apps) = harness()
        val m = SetupMachine(h.setupDeps(apps = apps), backgroundScope, SetupStart.Fresh)
        toFound(h, m)
        assertEquals(listOf("smarttube"), m.extras.value.offeredApps.map { it.id })
        m.dispatch(SetupIntent.AddApp("smarttube", true)); settle()
        assertEquals(setOf("smarttube"), h.tvs.tvs.value.single().wizard?.apps)
        m.dispatch(SetupIntent.MakeItClean); settle(); settle(1_000)
        assertIs<SetupState.Done>(m.state.value)
        assertEquals(listOf(AppInstall("smarttube", "SmartTube", AppStep.ADDED, 100)), m.extras.value.doneApps)
        val add = h.planner.requests.filterIsInstance<PlanRequest.AddModule>().single()
        assertEquals("smarttube", add.module.id)
        assertTrue(h.logLines.any { it == "setup: app smarttube added" })
    }

    @Test fun nothingIsAddedUnlessTicked() = runTest(timeout = 30.seconds) {
        val (h, apps) = harness()
        val m = SetupMachine(h.setupDeps(apps = apps), backgroundScope, SetupStart.Fresh)
        toFound(h, m)
        m.dispatch(SetupIntent.MakeItClean); settle(); settle(1_000)
        assertIs<SetupState.Done>(m.state.value)
        assertEquals(emptyList(), m.extras.value.doneApps)
        assertTrue(h.planner.requests.none { it is PlanRequest.AddModule })
    }

    @Test fun thePlayBuildOffersNoApps() = runTest(timeout = 30.seconds) {
        val (h, apps) = harness()
        val m = SetupMachine(h.setupDeps(apps = apps, store = StoreRules.PLAY), backgroundScope, SetupStart.Fresh)
        toFound(h, m)
        assertEquals(emptyList(), m.extras.value.offeredApps)
        m.dispatch(SetupIntent.AddApp("smarttube", true)); settle()
        assertEquals(emptySet(), h.tvs.tvs.value.single().wizard?.apps.orEmpty())
    }
}
