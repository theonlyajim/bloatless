package tv.cleanyour.testing.fakeadbd

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tv.cleanyour.adb.wire.AdbPublicKeyCodec
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

// Bloatless: the reference Mi TV 4A as a real adbd on 127.0.0.1:<port> (default 5555), so the app in the
// Android emulator can set it up at 10.0.2.2 with no real TV touched. Its Allow dialog says yes after a moment.
// On top of the persona the tests use, it answers what a served TV needs: /proc/meminfo, the HOME and launcher
// queries (so the new home screen can be offered), and the bundled launcher and curtain as installable APKs.
// Run: ./gradlew :testing:fake-adbd:runFakeTv
fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: 5555
    val root = File(System.getProperty("cleanyourtv.repoRoot") ?: ".")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val tv = Personas.miTv()
    tv.files["/proc/meminfo"] = "MemTotal:        1952344 kB\nMemFree:          312000 kB\nMemAvailable:     905000 kB\n".toByteArray()

    // The APKs the app streams: known by their bytes, as installApk expects.
    val bundled = File(root, "android/platform/src/main/assets/bundled")
    for ((file, pkg, version) in listOf(Triple("cleanyourtv-launcher.apk", "com.gathin.tvhome", 11L), Triple("cleanyourtv-curtain.apk", "tv.cleanyour.curtain", 1L))) {
        val bytes = File(bundled, file).takeIf { it.isFile }?.readBytes() ?: continue
        tv.apkCatalog[sha256Hex(bytes)] = FakePackage(pkg, "/data/app/$pkg-1/base.apk", "enabled", version, BUNDLED_SIGNER)
        // The app checks an install by hashing the file `pm path` names, so the file is there to read.
        tv.files["/data/app/$pkg-1/base.apk"] = bytes
    }

    // The TV apps that show on a TV home screen (leanback launcher), from the capture's own packages.
    val tvApps = listOf("com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "in.startv.hotstar",
        "com.sonyliv", "com.mxtech.videoplayer.television", "com.google.android.youtube.tvmusic", "com.android.vending")
    fun enabled(pkg: String) = tv.packages[pkg]?.let { it.state == "enabled" || it.state == "default" } == true
    fun activities(components: List<String>): String = buildString {
        append("${components.size} activities found:\n")
        components.forEachIndexed { i, c ->
            append("  Activity #$i:\n    priority=${if (i == 0) 2 else 0} preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n    $c\n")
        }
    }
    // The install check's script (ProbeScripts.installedSha256): a pipe and $( ), which the fake shell leaves alone.
    val installedHash = Regex("""^p=\$\(pm path '?([A-Za-z0-9_.]+)'? \| grep base\.apk.*sha256sum""")
    tv.rawHandlers += { line ->
        installedHash.find(line.trim())?.groupValues?.get(1)?.let { pkg ->
            val path = tv.packages[pkg]?.path
            val bytes = path?.let { tv.files[it] }
            if (bytes == null) ExecOutcome("", 1, "sha256sum: : No such file or directory\n") else ExecOutcome("${sha256Hex(bytes)}  $path\n")
        }
    }
    // The safety timers (Watchdog): armed with sh -c, seen by pgrep -f until pkill -f defuses them.
    val proc = Regex("""^'?(pgrep|pkill)'? '?-f'? '?\[c\]leanyourtv-rollback-([0-9A-Za-z]+)'?$""")
    // The probe's package flags read (ProbeScripts.PACKAGE_FLAGS) pipes dumpsys into grep: answered with the lines
    // the grep keeps, so versionCodes and states read as on a real TV.
    fun packageFlags(): String = buildString {
        append("Packages:\n")
        synchronized(tv) { tv.packages.values.toList() }.forEach { p ->
            val flags = if (p.system) "[ SYSTEM HAS_CODE ALLOW_CLEAR_USER_DATA ]" else "[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP ]"
            val enabled = when (p.state) { "enabled" -> 1; "disabled" -> 2; "disabled-user" -> 3; else -> 0 }
            append("  Package [${p.name}] (${Integer.toHexString(p.name.hashCode())}):\n    codePath=${p.path.substringBeforeLast('/')}\n    versionCode=${p.versionCode} minSdk=${tv.sdk} targetSdk=${tv.sdk}\n")
            append("    pkgFlags=$flags\n    User 0: ceDataInode=0 installed=true hidden=false suspended=false stopped=false notLaunched=false enabled=$enabled instant=false virtual=false\n")
        }
    }
    // The probe batches its reads in one line ("a; echo MARK; b | grep ..."): the piped dumpsys is swapped for a
    // placeholder, the rest runs in the fake shell, and the placeholder becomes what the grep would keep.
    var shellRef: FakeAdbd? = null
    val pipedDump = Regex("""dumpsys package packages \| grep -E '[^']*'""")
    tv.rawHandlers += { line ->
        val m = pipedDump.find(line)
        val shell = shellRef
        if (m == null || shell == null) null else {
            val rest = line.replaceRange(m.range, "echo __FAKE_FLAGS__")
            val out = kotlinx.coroutines.runBlocking { shell.shell.run(rest) }
            ExecOutcome(String(out.stdout).replace("__FAKE_FLAGS__\n", packageFlags()).toByteArray(), out.stderr, out.exit)
        }
    }

    // Finish switches debugging off (DebugOff); for a demo the served TV records it but stays reachable, as if
    // the person switched it straight back on with the remote.
    val adbOff = Regex("""^'?settings'? '?put'? '?global'? '?adb_enabled'? '?0'?$""")
    tv.rawHandlers += { line ->
        if (adbOff.matches(line.trim())) { synchronized(tv) { tv.settings["global"]?.put("adb_enabled", "0") }; ExecOutcome("") } else null
    }
    // The process name is the argument after the script (sh -c '...' cleanyourtv-rollback-TOKEN), so arming is
    // seen on the whole line; the timer itself never fires here (the phone defuses it or the test is over).
    val armed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val arm = Regex("""sh -c '.*' (cleanyourtv-rollback-[0-9A-Za-z]+) """)
    tv.rawHandlers += { line ->
        val t = line.trim()
        val armedNow = if (t.startsWith("nohup setsid sh -c") || t.startsWith("( sh -c")) arm.find(t)?.groupValues?.get(1) else null
        if (armedNow != null) {
            armed += armedNow
            ExecOutcome("")
        } else proc.find(t)?.let { m ->
            val token = "cleanyourtv-rollback-" + m.groupValues[2]
            when (m.groupValues[1]) {
                "pgrep" -> if (token in armed) ExecOutcome("4242\n") else ExecOutcome("", 1)
                else -> { armed -= token; ExecOutcome("") }
            }
        }
    }
    tv.handlers += handler@{ argv, _ ->
        if (argv.size < 3 || argv[0] != "cmd" || argv[1] != "package" || argv[2] != "query-activities") return@handler null
        val out = when {
            "android.intent.category.HOME" in argv -> listOfNotNull(
                tv.home.takeIf { enabled(it.substringBefore('/')) },
                "com.gathin.tvhome/.MainActivity".takeIf { enabled("com.gathin.tvhome") && tv.home != it },
            )
            "android.intent.category.LEANBACK_LAUNCHER" in argv || "android.intent.category.LAUNCHER" in argv ->
                tvApps.filter { enabled(it) }.map { "$it/.MainActivity" }
            else -> emptyList()
        }
        ExecOutcome(activities(out))
    }

    val adbd = FakeAdbd(tv, Faults().apply { allow = AllowBehaviour.Allow(afterMs = 1500) }, scope)
    shellRef = adbd
    // A real TV remembers the phone it allowed ("Always allow"); so does this one, across restarts of the server.
    val keysFile = File(root, "build/fake-tv-adb-keys")
    if (keysFile.isFile) keysFile.readLines().filter { it.isNotBlank() }.forEach { runCatching { adbd.trustedKeys += AdbPublicKeyCodec.parseLine(it) } }
    scope.launch {
        var saved = -1
        while (true) {
            if (adbd.trustedKeys.size != saved) {
                saved = adbd.trustedKeys.size
                keysFile.parentFile.mkdirs()
                keysFile.writeText(adbd.trustedKeys.joinToString("\n") { AdbPublicKeyCodec.line(it) } + "\n")
            }
            kotlinx.coroutines.delay(1000)
        }
    }
    val ss = ServerSocket(port, 50, InetAddress.getLoopbackAddress())
    println("Fake Mi TV 4A listening on 127.0.0.1:$port (10.0.2.2:$port from the emulator)")
    runBlocking {
        while (true) {
            val s = try { ss.accept() } catch (e: IOException) { break }
            println("connection from ${s.remoteSocketAddress}")
            if (!adbd.adbEnabled || adbd.rebooting || !adbd.acceptConnections) { s.close(); continue }
            adbd.serve(BlockingAdbSocket(s))
        }
    }
}

// The signer the app pins for both bundled APKs (core/trust Pins.kt: the debug key for now).
private const val BUNDLED_SIGNER = "b66cbe08b17a834dd145822109d5be0981b4fc077efe5a177ef3d6f3e455b724"
