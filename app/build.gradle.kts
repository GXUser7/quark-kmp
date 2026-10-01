import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

/**
 * The shared interface and everything above the engine.
 *
 * `commonMain` holds the screens, the view models and the wiring; `jvmMain` is
 * the desktop application (window, file dialogs, libmpv, system integrations)
 * and `androidMain` the Android specifics the `:androidApp` module builds on.
 */
kotlin {
    jvmToolchain(21)
    jvm()
    androidLibrary {
        namespace = "com.quark.app"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "kotlinx.coroutines.FlowPreview",
        )
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":services"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.jaudiotagger)
            implementation(libs.jna)
            implementation(libs.jna.platform)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.annotation)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.documentfile)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.session)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}


compose.desktop {
    application {
        mainClass = "com.quark.app.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Xss4m")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "quark"
            packageVersion = project.version.toString()
            description = "quark — where sound begins"
            vendor = "PDG"
            copyright = "MIT licence, (c) z3nsh0w, aror and contributors"
            licenseFile.set(rootProject.file("LICENSE"))
            // The whole runtime rather than a hand-picked module list: sqlite-jdbc
            // needs java.sql, TLS needs jdk.crypto.ec, JNA needs jdk.unsupported,
            // and a module missed here only shows up as a crash on a user's
            // machine. The installer is dominated by libmpv anyway.
            includeAllModules = true
            // Everything under resources/<platform> is copied next to the
            // application and exposed as compose.application.resources.dir.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            windows {
                iconFile.set(project.file("icons/quark.ico"))
                menu = true
                menuGroup = "quark"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Fixed so a newer installer upgrades an older installation in
                // place instead of installing next to it.
                upgradeUuid = "6f1c2c1e-6c4f-4f0e-9c1a-3b8e0d2f7a51"
            }
            linux {
                iconFile.set(project.file("icons/quark.png"))
                packageName = "quark"
                debMaintainer = "quark@quarkaudio.ru"
                menuGroup = "AudioVideo"
            }
            macOS {
                iconFile.set(project.file("icons/quark.png"))
                bundleID = "com.quark.quark"
            }
        }
    }
}

// --- libmpv ------------------------------------------------------------------
//
// The playback backend is a 115 MB native library, which does not belong in git.
// `fetchMpv` downloads the official Windows build and unpacks the one file that
// matters into the resources tree, where Compose's packaging picks it up and the
// loader finds it at runtime. `run` and packaging depend on it, so a fresh clone
// needs no manual step.
//
// Unpacking needs real 7-Zip: the archive uses the BCJ2 filter, which neither
// commons-compress nor py7zr implements. So the task also fetches 7zr.exe, the
// public-domain console build, rather than expecting one to be installed.

val mpvVersion = "20260903"
val mpvBuild = "$mpvVersion-git-69e63f425a"
val mpvArchiveSha256 = "fac135c68a35b7639e39d72c0c365104edbaebdea39a0dfdd8c36e8c8e80faef"
val sevenZipSha256 = "ad4c82fadcbdf93c03b4fc440f300509c7d60c5c2f4d183e35d9d70d6957037d"

val isWindowsHost = System.getProperty("os.name").lowercase().contains("win")

val fetchMpv by tasks.registering {
    description = "Downloads libmpv-2.dll for Windows into app/resources/windows-x64."
    group = "build setup"

    val target = layout.projectDirectory.file("resources/windows-x64/libmpv-2.dll")
    val workDir = layout.buildDirectory.dir("mpv")
    outputs.file(target)
    // The build is pinned, so once the file is there there is nothing to do.
    onlyIf { isWindowsHost && !target.asFile.exists() }

    doLast {
        val work = workDir.get().asFile.apply { mkdirs() }

        val archive = File(work, "mpv-dev-$mpvBuild.7z")
        download(
            url = "https://github.com/shinchiro/mpv-winbuild-cmake/releases/" +
                "download/$mpvVersion/mpv-dev-x86_64-$mpvBuild.7z",
            into = archive,
            sha256 = mpvArchiveSha256,
        )

        val sevenZip = File(work, "7zr.exe")
        download(url = "https://www.7-zip.org/a/7zr.exe", into = sevenZip, sha256 = sevenZipSha256)

        target.asFile.parentFile.mkdirs()
        val result = providers.exec {
            commandLine(
                sevenZip.absolutePath, "e", "-y",
                "-o${target.asFile.parentFile.absolutePath}",
                archive.absolutePath, "libmpv-2.dll",
            )
        }
        check(result.result.get().exitValue == 0) {
            "7zr failed to extract libmpv-2.dll: ${result.standardError.asText.get()}"
        }
        check(target.asFile.exists()) { "7zr reported success but libmpv-2.dll is not there" }
        logger.lifecycle("libmpv ready at ${target.asFile}")
    }
}

fun Task.download(url: String, into: File, sha256: String) {
    if (into.exists() && into.sha256() == sha256) return
    logger.lifecycle("Downloading $url")
    into.parentFile.mkdirs()
    URI(url).toURL().openStream().use { input -> into.outputStream().use(input::copyTo) }
    val actual = into.sha256()
    check(actual == sha256) { "$url checksum mismatch: expected $sha256, got $actual" }
}

fun File.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(readBytes())
    .joinToString("") { "%02x".format(it) }

// Only the desktop tasks: the Android library plugin brings its own `package*`
// tasks, which have nothing to do with libmpv.
val desktopTasksNeedingMpv = setOf("run", "prepareAppResources", "createDistributable", "createReleaseDistributable")
tasks.matching { it.name in desktopTasksNeedingMpv }.configureEach {
    dependsOn(fetchMpv)
}
