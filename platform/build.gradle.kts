plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)
    jvm()
    androidLibrary {
        namespace = "com.quark.platform"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {
            implementation(libs.jna)
            implementation(libs.jna.platform)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

