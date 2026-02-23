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

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.flopster101.siliconplayer"
        minSdk = 23
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
        ndk {
            // Exclude x86 from default app packaging/build variants.
            // x86 remains supported in dependency build scripts when explicitly requested.
            abiFilters += "arm64-v8a"
            abiFilters += "armeabi-v7a"
            abiFilters += "x86_64"
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
            externalNativeBuild {
                cmake {
                    // Apply aggressive native optimization for release-like builds.
                    cFlags += "-Ofast"
                    cppFlags += "-Ofast"
                }
            }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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
        jniLibs {
            // UADE launches uadecore via exec(), so the binary must exist as a real file
            // under nativeLibraryDir instead of being mmap-loaded directly from APK.
            useLegacyPackaging = true
        }
    }
    sourceSets {
        getByName("main") {
            assets.srcDir(layout.buildDirectory.dir("generated/uadeRuntimeAssets/main"))
            jniLibs.srcDir(layout.buildDirectory.dir("generated/uadeRuntimeJniLibs/main"))
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
    implementation(libs.androidx.material)
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

            val debugKeystore = rootProject.file("debug.keystore")
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

val uadeRuntimeAssetAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
val syncUadeRuntimeAssets = tasks.register("syncUadeRuntimeAssets") {
    group = "build setup"
    description = "Sync ABI-specific UADE runtime files into generated assets."

    doLast {
        val destinationRoot = layout.buildDirectory
            .dir("generated/uadeRuntimeAssets/main/uade")
            .get()
            .asFile
        delete(destinationRoot)

        uadeRuntimeAssetAbis.forEach { abi ->
            val prebuiltRoot = file("src/main/cpp/prebuilt/$abi")
            val sourceShareDir = File(prebuiltRoot, "share/uade")
            val sourceUadeCore = File(prebuiltRoot, "lib/uade/uadecore")
            if (!sourceShareDir.isDirectory || !sourceUadeCore.isFile) {
                logger.lifecycle(
                    "UADE runtime assets missing for $abi, skipping (expected: ${sourceShareDir.absolutePath}, ${sourceUadeCore.absolutePath})"
                )
                return@forEach
            }

            copy {
                from(sourceShareDir)
                into(File(destinationRoot, abi))
            }
            copy {
                from(sourceUadeCore)
                into(File(destinationRoot, abi))
                rename { "uadecore" }
            }
        }
    }
}

val syncUadeRuntimeJniLibs = tasks.register("syncUadeRuntimeJniLibs") {
    group = "build setup"
    description = "Sync ABI-specific runtime helper/shared native binaries into generated jniLibs."

    doLast {
        val destinationRoot = layout.buildDirectory
            .dir("generated/uadeRuntimeJniLibs/main")
            .get()
            .asFile
        delete(destinationRoot)

        uadeRuntimeAssetAbis.forEach { abi ->
            val sourceUadeCore = file("src/main/cpp/prebuilt/$abi/lib/uade/uadecore")
            if (!sourceUadeCore.isFile) {
                logger.lifecycle("UADE core executable missing for $abi, skipping (${sourceUadeCore.absolutePath})")
            } else {
                copy {
                    from(sourceUadeCore)
                    into(File(destinationRoot, abi))
                    rename { "libuadecore_exec.so" }
                }
            }

            val sourceFurnaceCore = file("src/main/cpp/prebuilt/$abi/lib/libfurnace.so")
            if (!sourceFurnaceCore.isFile) {
                logger.lifecycle("Furnace shared core missing for $abi, skipping (${sourceFurnaceCore.absolutePath})")
            } else {
                copy {
                    from(sourceFurnaceCore)
                    into(File(destinationRoot, abi))
                    rename { "libfurnace.so" }
                }
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(syncUadeRuntimeAssets)
    dependsOn(syncUadeRuntimeJniLibs)
}
