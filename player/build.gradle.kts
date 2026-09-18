plugins { kotlin("multiplatform") }

kotlin {
    jvmToolchain(21)
    jvm()
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        jvmMain.dependencies {
            implementation("net.java.dev.jna:jna:5.17.0")
        }
        jvmTest.dependencies {
            implementation("net.java.dev.jna:jna:5.17.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
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
