plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
}

allprojects {
    group = "com.quark"
    version = "0.2.0"
}
