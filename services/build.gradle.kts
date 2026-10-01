plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
}

/**
 * Everything above the engine that is not drawing: the application graph,
 * sessions with each service, the view models' state, and the platform
 * services (folder watching, Discord, the local api). No Compose here, which
 * keeps it testable on a plain JVM and leaves `:app` with only the interface.
 */
kotlin {
    jvmToolchain(21)
    jvm()
    androidLibrary {
        namespace = "com.quark.services"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compilerOptions {
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "kotlinx.coroutines.FlowPreview",
        )
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(project(":player"))
            api(project(":data"))
            api(project(":network"))
            api(project(":platform"))
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.jaudiotagger)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.annotation)
            implementation(libs.media3.exoplayer)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}
