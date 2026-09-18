pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
dependencyResolutionManagement { repositories { mavenCentral(); google() } }
rootProject.name = "quark-kmp"
include(":core", ":network", ":data", ":player", ":platform", ":app")
