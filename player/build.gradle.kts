import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(21)
    jvm()
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
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

android {
    namespace = "com.quark.player"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
