package tv.cleanyour.core.engine

import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Prior
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.ops.FailurePolicy
import tv.cleanyour.core.ops.ShellRenderer
import tv.cleanyour.core.planner.Step
import tv.cleanyour.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals

// Bloatless: replacing a home screen app signed with another key (Clean Your TV's, D36) uninstalls it and then
// installs ours as absent; the TV still having it before the run starts is the plan, not a change.
class StalenessReplaceTest {
    private val pkg = PackageName("com.gathin.tvhome")
    private val sha = Sha256("a".repeat(64))
    private val blob = BlobRef(sha, 100, "bundled:test")
    private val renderer = ShellRenderer()
    private fun step(i: Int, op: Op, prior: Prior) = Step(i, op, renderer.render(op), prior, "launcher", "", FailurePolicy.CONTINUE)
    private val snapshotWithOld = Samples.miTvSnapshot().let { s ->
        s.copy(packages = s.packages + (pkg to s.packages.values.first().copy(signer = Sha256("b".repeat(64)))))
    }

    @Test fun anInstallAfterTheUninstallOfTheSameAppIsNoChange() {
        val steps = listOf(step(0, Op.Uninstall(pkg), Prior(present = true)), step(1, Op.InstallStream(blob, sha, pkg, 11, sha), Prior(present = false)))
        assertEquals(emptyList(), Staleness.diff(steps, snapshotWithOld))
    }

    @Test fun anInstallOnItsOwnStillSeesTheAppAppear() {
        val steps = listOf(step(0, Op.InstallStream(blob, sha, pkg, 11, sha), Prior(present = false)))
        assertEquals(1, Staleness.diff(steps, snapshotWithOld).size)
    }
}
