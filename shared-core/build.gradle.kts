plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

kotlin {
    androidLibrary {
        namespace = "com.flopster101.siliconplayer.shared.core"
        compileSdk = 34
        minSdk = 21
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
