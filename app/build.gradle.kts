import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI
import java.security.MessageDigest

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(21)
    jvm()
    compilerOptions.freeCompilerArgs.addAll(
        "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
    )
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":core"))
            implementation(project(":player"))
            implementation(project(":data"))
            implementation(project(":network"))
            implementation(project(":platform"))
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.quark.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "quark"
            packageVersion = "0.1.0"
            // Everything under resources/<platform> is copied next to the
            // application and exposed as compose.application.resources.dir.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
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

tasks.matching { it.name == "run" || it.name.startsWith("package") }.configureEach {
    dependsOn(fetchMpv)
}
