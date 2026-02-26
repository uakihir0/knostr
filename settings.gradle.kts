pluginManagement {
    includeBuild("plugins")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "knostr"
include("core")
include("social")

val osName = System.getProperty("os.name").lowercase(java.util.Locale.getDefault())
if (osName.contains("mac")) {
    include("all")
}
