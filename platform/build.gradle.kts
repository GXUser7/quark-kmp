plugins { kotlin("multiplatform") }

kotlin {
    jvmToolchain(21)
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        jvmMain.dependencies {
            implementation("net.java.dev.jna:jna-platform:5.17.0")
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
