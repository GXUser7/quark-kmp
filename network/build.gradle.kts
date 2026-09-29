plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)
    jvm()
    androidLibrary {
        namespace = "com.quark.network"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.ktor.client.core)
            // CIO for the quark backend, whose search and song endpoints take a
            // json body on GET — OkHttp refuses to send one.
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        // Code that needs the JDK (the OkHttp engine) is shared between the
        // desktop JVM and Android by pointing both source sets at one
        // directory: a common source set cannot see java.* at all. The local
        // control api and its mDNS announcement are desktop-only.
        jvmMain {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.websockets)
                implementation(libs.jmdns)
            }
        }
        androidMain {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

