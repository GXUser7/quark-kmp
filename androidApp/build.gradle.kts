import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * The APK: the Android entry points (application, activity, playback service)
 * around the shared interface in `:app`.
 *
 * Release builds are signed with the key described by the QUARK_KEYSTORE_*
 * environment variables when they are set — CI supplies them from secrets — and
 * with the debug key otherwise, so a checkout can always produce an installable
 * package.
 */
android {
    namespace = "com.quark.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.quark.audio"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 20
        versionName = project.version.toString()
    }

    signingConfigs {
        val keystore = System.getenv("QUARK_KEYSTORE_FILE")
        if (!keystore.isNullOrBlank() && file(keystore).exists()) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("QUARK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("QUARK_KEY_ALIAS")
                keyPassword = System.getenv("QUARK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/*.kotlin_module",
                "/META-INF/versions/9/previous-compilation-data.bin",
                "/META-INF/io.netty.versions.properties",
            )
        }
    }

    buildFeatures { buildConfig = true }

    lint {
        // Media3 marks half its API unstable; the opt-ins are deliberate and
        // lint's objections would only stop the release build.
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":app"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
}
