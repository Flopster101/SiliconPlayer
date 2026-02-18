plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

import java.io.ByteArrayOutputStream
import java.io.File

fun gitShortSha(): String {
    return try {
        val stdout = ByteArrayOutputStream()
        exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            standardOutput = stdout
            isIgnoreExitValue = true
        }
        stdout.toString().trim().ifBlank { "nogit" }
    } catch (_: Exception) {
        "nogit"
    }
}

android {
    namespace = "com.flopster101.siliconplayer"
    compileSdk = 34
    buildToolsVersion = "36.1.0"
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.flopster101.siliconplayer"
        minSdk = 26
        targetSdk = 34
        versionCode = 1000
        versionName = "0.1.0"
        buildConfigField("String", "GIT_SHA", "\"${gitShortSha()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++20"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("optimizedDebug") {
            initWith(getByName("release"))
            // Keep debug signing so it can replace/debug-install like normal debug builds.
            signingConfig = signingConfigs.getByName("debug")
            // Make it clear on-device which build is installed.
            versionNameSuffix = "-optdebug"
            matchingFallbacks += listOf("release")
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

fun resolveAndroidSdkDir(): File {
    val sdkEnv = System.getenv("ANDROID_SDK_ROOT")
        ?: System.getenv("ANDROID_HOME")
    require(!sdkEnv.isNullOrBlank()) { "ANDROID_SDK_ROOT/ANDROID_HOME is not set" }
    val sdkDir = File(sdkEnv)
    require(sdkDir.isDirectory) { "Android SDK directory not found: $sdkDir" }
    return sdkDir
}

fun resolveBuildToolsDir(sdkDir: File): File {
    val configured = android.buildToolsVersion
    require(!configured.isNullOrBlank()) { "android.buildToolsVersion must be set" }
    val configuredDir = File(sdkDir, "build-tools/$configured")
    require(configuredDir.isDirectory) { "Configured build-tools directory not found: $configuredDir" }
    return configuredDir
}

fun zipalignSupports16k(zipalign: File): Boolean {
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()
    try {
        exec {
            commandLine(zipalign.absolutePath)
            standardOutput = out
            errorOutput = err
            isIgnoreExitValue = true
        }
    } catch (_: Exception) {
        return false
    }
    val usage = out.toString() + "\n" + err.toString()
    return usage.contains("-P")
}

fun register16kAlignTaskForVariant(variantName: String) {
    val taskSuffix = variantName.replaceFirstChar { c ->
        if (c.isLowerCase()) c.titlecase() else c.toString()
    }
    val assembleTaskName = "assemble$taskSuffix"
    val alignTaskName = "align${taskSuffix}Apk16k"
    val apkRelativePath = "outputs/apk/$variantName/app-$variantName.apk"

    val alignTask = tasks.register(alignTaskName) {
        group = "build"
        description = "Zipalign $variantName APK native libs to 16KB page boundaries and re-sign."

        doLast {
            val apk = layout.buildDirectory.file(apkRelativePath).get().asFile
            if (!apk.exists()) {
                throw GradleException("APK not found at: ${apk.absolutePath}")
            }

            val sdkDir = resolveAndroidSdkDir()
            val buildToolsDir = resolveBuildToolsDir(sdkDir)
            val zipalign = File(buildToolsDir, "zipalign")
            val apksigner = File(buildToolsDir, "apksigner")
            require(zipalign.exists() && zipalign.canExecute()) {
                "zipalign not found/executable at ${zipalign.absolutePath}"
            }
            require(apksigner.exists() && apksigner.canExecute()) {
                "apksigner not found/executable at ${apksigner.absolutePath}"
            }
            require(zipalignSupports16k(zipalign)) {
                "Configured zipalign does not support '-P 16': ${zipalign.absolutePath}"
            }

            logger.lifecycle("Using zipalign: ${zipalign.absolutePath}")
            logger.lifecycle("Using apksigner: ${apksigner.absolutePath}")

            val alignedUnsigned = File(apk.parentFile, "app-$variantName-aligned-unsigned.apk")
            val alignedSigned = File(apk.parentFile, "app-$variantName-aligned-signed.apk")

            exec {
                commandLine(
                    zipalign.absolutePath,
                    "-f",
                    "-P", "16",
                    "-v", "4",
                    apk.absolutePath,
                    alignedUnsigned.absolutePath
                )
            }

            val debugKeystore = File(System.getProperty("user.home"), ".android/debug.keystore")
            require(debugKeystore.exists()) { "Debug keystore not found at ${debugKeystore.absolutePath}" }

            exec {
                commandLine(
                    apksigner.absolutePath,
                    "sign",
                    "--ks", debugKeystore.absolutePath,
                    "--ks-key-alias", "androiddebugkey",
                    "--ks-pass", "pass:android",
                    "--key-pass", "pass:android",
                    "--out", alignedSigned.absolutePath,
                    alignedUnsigned.absolutePath
                )
            }

            copy {
                from(alignedSigned)
                into(apk.parentFile)
                rename { apk.name }
            }
            alignedUnsigned.delete()
            alignedSigned.delete()
        }
    }

    tasks.configureEach {
        if (name == assembleTaskName) {
            finalizedBy(alignTask)
        }
    }
}

register16kAlignTaskForVariant("debug")
register16kAlignTaskForVariant("optimizedDebug")
