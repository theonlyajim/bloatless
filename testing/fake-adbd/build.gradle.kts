plugins { id("cleanyourtv.jvm") }

// W2 owns: in-process adbd with personas and fault knobs (ARCHITECTURE 6.4).
dependencies {
    api(project(":adb:wire"))
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Bloatless: the fake TV as a server for the emulator (FakeTvServer.kt).
tasks.register<JavaExec>("runFakeTv") {
    group = "application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("tv.cleanyour.testing.fakeadbd.FakeTvServerKt")
    systemProperty("cleanyourtv.repoRoot", rootProject.projectDir.absolutePath)
    standardInput = System.`in`
}
