plugins {
    kotlin("multiplatform") version "2.3.10" apply false
    kotlin("plugin.serialization") version "2.3.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
    id("app.cash.sqldelight") version "2.1.0" apply false
}
allprojects { group = "com.quark"; version = "0.1.0" }
