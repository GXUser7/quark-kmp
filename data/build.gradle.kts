plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("app.cash.sqldelight")
}

kotlin {
    jvmToolchain(21)
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation("app.cash.sqldelight:runtime:2.1.0")
            implementation("io.ktor:ktor-client-core:3.4.0")
            implementation("app.cash.sqldelight:coroutines-extensions:2.1.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
        }
        jvmMain.dependencies {
            implementation("app.cash.sqldelight:sqlite-driver:2.1.0")
            implementation("net.jthink:jaudiotagger:3.0.1")
        }
        jvmTest.dependencies {
            implementation("app.cash.sqldelight:sqlite-driver:2.1.0")
            implementation("io.ktor:ktor-client-mock:3.4.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
    }
}

sqldelight {
    databases {
        create("QuarkDatabase") {
            packageName.set("com.quark.data.db")
        }
    }
}
