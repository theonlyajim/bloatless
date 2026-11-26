package tv.cleanyour.core.trust

import tv.cleanyour.core.model.LauncherArtifact
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.store.BlobStore

// Trust anchors compiled into the app. Changing one is a reviewed code change, never a data file.
object TrustRoots {
    // Root of the module index chain: signs keys.json only (KeysFile). Release key K2R, generated
    // 2026-10-20 for 0.1.0; it replaced the development keys of K2, which never signed a public
    // build. The private half stays offline with the maintainer (docs/history/DECISIONS-W5.md, K2R).
    const val INDEX_ROOT_PUBLIC_KEY = "RWTBm+H9rjMuaX944TBay7uDMxQEC8Q+E8yF1p7hyGl7Fa+8bzBKE4io"   // key id 692E33AEFDE19BC1

    // The sequence of the keys.json bundled with this build: an older keys file is refused.
    const val BUNDLED_KEYS_SEQUENCE = 1L

    val indexRoot: MinisignKey get() = Ed25519Minisign.parsePublicKey(INDEX_ROOT_PUBLIC_KEY)

    fun indexVerifier(): MinisignIndexVerifier = MinisignIndexVerifier(indexRoot, keysFloor = BUNDLED_KEYS_SEQUENCE)
}

// The launcher APK bundled in the phone app (D13, Q1): upstream github.com/Gathin23/cleanyourtv at
// main after v3.0.0, commit b06ab21 (third_party/cleanyourtv), launcher 3.0 (versionCode 9),
// `assembleRelease` twice, both re-signed with the dedicated launcher release key and compared by
// scripts/build-launcher-on-forge.sh (docs/history/reviews/LAUNCHER-300-build-output.txt). The
// verifyLauncherPin task (part of :core:trust check and of :android:platform preBuild) fails the
// build when the asset differs.
object PinnedLauncher {
    const val ASSET_NAME = "cleanyourtv-launcher.apk"
    const val UPSTREAM_COMMIT = "b06ab21105c49900cb1ed76b24414dba1e21d0db"
    const val PACKAGE = "com.gathin.tvhome"
    const val VERSION_CODE = 12L
    const val VERSION_NAME = "3.0.3"
    const val SIZE = 1_545_597L
    const val SHA256 = "f5e57f3a6d8f0ba31102e7f0f0b7186524c75fd7a246b1dda0be7ae77b367893"
    // Bloatless: launcher 3.0.3 = upstream 3.0 plus JAKIM prayer times, the azan (a foreground service since 3.0.3) and the week's places, built from tv/launcher.
    // Signed with the Bloatless release key (scripts/bloatless-release-key.sh).
    const val SIGNER_SHA256 = "2358ee3e6a924cc3504a64d0a6a60c1d430de94831f4aa3e51700fae08047f06"
}

// The TV curtain bundled in the phone app (RFC-CURTAIN-1): module :tv:curtain of this repo, built
// twice and compared, then signed with the launcher release key by scripts/build-curtain-on-forge.sh.
// One signer to pin: SIGNER_SHA256 is the launcher's. The verifyCurtainPin task (part of :core:trust
// check and of :android:platform preBuild) fails the build when the asset differs.
object PinnedCurtain {
    const val ASSET_NAME = "cleanyourtv-curtain.apk"
    const val PACKAGE = "tv.cleanyour.curtain"
    const val ACTIVITY = ".CurtainActivity"
    const val RECEIVER = ".CueReceiver"
    const val VERSION_CODE = 1L
    const val VERSION_NAME = "0.1"
    // Bloatless: the upstream curtain 0.1 re-signed with the launcher's key (zipalign + apksigner, v2/v3).
    const val SIZE = 65_955L
    const val SHA256 = "769802008e72cc4a5b5d59d8b0ed4ad76d49e4a1f5e3e87bcd8a867f1465a1bd"
    const val SIGNER_SHA256 = PinnedLauncher.SIGNER_SHA256
}

// LauncherPin over the BlobStore's bundled asset. bundled() refuses an asset whose bytes are not the
// pinned ones, so a swapped asset can never be offered as the launcher.
class BundledLauncherPin(private val blobs: BlobStore) : LauncherPin {
    override fun bundled(): LauncherArtifact {
        val blob = blobs.bundled(PinnedLauncher.ASSET_NAME)
        check(blob.sha256.hex == PinnedLauncher.SHA256 && blob.size == PinnedLauncher.SIZE) {
            "bundled launcher does not match its pin (sha256 ${blob.sha256.hex}, ${blob.size} bytes)"
        }
        return LauncherArtifact(
            pkg = PackageName(PinnedLauncher.PACKAGE),
            versionCode = PinnedLauncher.VERSION_CODE,
            versionName = PinnedLauncher.VERSION_NAME,
            sha256 = Sha256(PinnedLauncher.SHA256),
            signer = Sha256(PinnedLauncher.SIGNER_SHA256),
            blob = blob,
        )
    }
}

// CurtainPin over the BlobStore's bundled asset, refusing an asset whose bytes are not the pinned ones.
class BundledCurtainPin(private val blobs: BlobStore) : CurtainPin {
    override fun bundled(): LauncherArtifact {
        val blob = blobs.bundled(PinnedCurtain.ASSET_NAME)
        check(blob.sha256.hex == PinnedCurtain.SHA256 && blob.size == PinnedCurtain.SIZE) {
            "bundled curtain does not match its pin (sha256 ${blob.sha256.hex}, ${blob.size} bytes)"
        }
        return LauncherArtifact(
            pkg = PackageName(PinnedCurtain.PACKAGE),
            versionCode = PinnedCurtain.VERSION_CODE,
            versionName = PinnedCurtain.VERSION_NAME,
            sha256 = Sha256(PinnedCurtain.SHA256),
            signer = Sha256(PinnedCurtain.SIGNER_SHA256),
            blob = blob,
        )
    }
}
