import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("module.publications")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        compilations["test"].compileTaskProvider.configure {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
        compilations["test"].attributes {
            attribute(
                TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
                17
            )
        }
    }

    js(IR) {
        nodejs()
        browser()

        compilerOptions {
            target.set("es2015")
        }

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { target.set("es2015") }
            }
        }
    }

    if (HostManager.hostIsMac) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
        macosX64()
        macosArm64()
    }

    mingwX64()
    linuxX64()

    compilerOptions {
        freeCompilerArgs.add("-Xenable-suspend-function-exporting")
    }

    sourceSets {
        all {
            languageSettings.apply {
                optIn("kotlin.js.ExperimentalJsExport")
            }
        }

        commonMain.dependencies {
            implementation(libs.ktor.core)
            implementation(libs.khttpclient)
            implementation(libs.datetime)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)

            implementation(project.dependencies.platform(libs.cryptography.bom))
            implementation(libs.cryptography.core)
        }

        // secp256k1-kmp supports JVM, Apple, and Linux (not JS or mingwX64).
        // Use an intermediate "signingMain" source set for platforms that support it.
        val signingMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.secp256k1.kmp)
            }
        }
        val signingTest by creating {
            dependsOn(commonTest.get())
        }

        jvmMain { dependsOn(signingMain) }
        jvmTest { dependsOn(signingTest) }

        if (HostManager.hostIsMac) {
            appleMain {
                dependsOn(signingMain)
                dependencies {
                    implementation(libs.cryptography.openssl)
                }
            }
        }

        val linuxX64Main by getting { dependsOn(signingMain) }

        // Unsupported targets get a stub signerFactory
        val unsupportedSigningMain by creating {
            dependsOn(commonMain.get())
        }

        jsMain { dependsOn(unsupportedSigningMain) }
        val mingwX64Main by getting { dependsOn(unsupportedSigningMain) }

        // for test
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }

        jvmTest.dependencies {
            implementation(libs.cryptography.jdk)
            implementation(libs.secp256k1.kmp.jni.jvm)
            implementation(libs.slf4j.simple)
        }
    }
}


tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}
