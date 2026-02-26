import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.cocoapods)
    alias(libs.plugins.swiftpackage)
    id("module.publications")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    js(IR) {
        nodejs()
        browser()

        binaries.library()

        compilerOptions {
            target.set("es2015")
            generateTypeScriptDefinitions()
        }
    }

    val xcf = XCFramework("knostr")
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
        macosX64(),
        macosArm64(),
    ).forEach {
        it.binaries.framework {
            export(project(":core"))
            export(project(":social"))
            baseName = "knostr"
            xcf.add(this)
        }
    }

    cocoapods {
        name = "knostr"
        version = "0.0.1"
        summary = "knostr is Nostr protocol library for Kotlin Multiplatform."
        homepage = "https://github.com/uakihir0/knostr"
        authors = "Akihiro Urushihara"
        license = "MIT"
        framework { baseName = "knostr" }
    }

    sourceSets {
        all {
            languageSettings.apply {
                optIn("kotlin.js.ExperimentalJsExport")
            }
        }
        commonMain.dependencies {
            api(project(":core"))
            api(project(":social"))
        }
    }
}

multiplatformSwiftPackage {
    swiftToolsVersion("5.7")
    targetPlatforms {
        iOS { v("15") }
        macOS { v("12.0") }
    }
}

tasks.configureEach {
    if (name.contains("assembleKnostr") && name.contains("XCFramework")) {
        mustRunAfter(tasks.matching { it.name.contains("FatFramework") })
    }
}

tasks.podPublishXCFramework {
    doLast {
        providers.exec {
            executable = "sh"
            args = listOf(project.projectDir.path + "/../tool/rename_podfile.sh")
        }.standardOutput.asText.get()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}

afterEvaluate {
    tasks.withType<Kotlin2JsCompile>().configureEach {
        compilerOptions {
            target.set("es2015")
            freeCompilerArgs.add("-Xes-long-as-bigint")
        }
    }
}
