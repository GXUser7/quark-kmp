plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)
    jvm()
    androidLibrary {
        namespace = "com.quark.player"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            implementation(libs.jna)
        }
        androidMain.dependencies {
            api(libs.media3.exoplayer)
            // VK and some SoundCloud tracks only come as HLS playlists.
            implementation(libs.media3.hls)
            implementation(libs.androidx.annotation)
            implementation(libs.kotlinx.coroutines.android)
        }
        jvmTest.dependencies {
            implementation(libs.jna)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}


// The engine test drives the real library, which lives in the app module's
// resources once :app:fetchMpv has run. Silent output: no device is opened.
tasks.withType<Test>().configureEach {
    systemProperty(
        "quark.mpv.path",
        rootProject.layout.projectDirectory.dir("app/resources/windows-x64").asFile.absolutePath,
    )
    systemProperty("quark.test.silent", "true")
}
