plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
}

import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

data class ProcessCommandResult(
    val exitCode: Int,
    val output: String
)

val androidBuildToolsVersion = "36.1.0"

fun runProcessAndCapture(
    command: List<String>,
    workingDir: File? = null
): ProcessCommandResult {
    val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
    if (workingDir != null) {
        processBuilder.directory(workingDir)
    }
    val process = processBuilder.start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()
    return ProcessCommandResult(exitCode = exitCode, output = output)
}

fun runProcessWithInheritedIo(
    command: List<String>,
    workingDir: File? = null
): Int {
    val processBuilder = ProcessBuilder(command)
    if (workingDir != null) {
        processBuilder.directory(workingDir)
    }
    val process = processBuilder
        .inheritIO()
        .start()
    return process.waitFor()
}

fun parseBooleanGradleProperty(value: String?): Boolean {
    return when (value?.trim()?.lowercase()) {
        "1", "true", "yes", "on" -> true
        else -> false
    }
}

fun parseCsvGradleProperty(value: String?): Set<String> {
    if (value.isNullOrBlank()) return emptySet()
    return value
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}

fun gitShortSha(): String {
    return try {
        val result = runProcessAndCapture(
            command = listOf("git", "rev-parse", "--short", "HEAD"),
            workingDir = rootProject.projectDir
        )
        if (result.exitCode == 0) {
            result.output.trim().ifBlank { "nogit" }
        } else {
            "nogit"
        }
    } catch (_: Exception) {
        "nogit"
    }
}

fun gitCommandOutput(workingDir: File, vararg args: String): String? {
    return try {
        val result = runProcessAndCapture(
            command = args.toList(),
            workingDir = workingDir
        )
        if (result.exitCode == 0) {
            result.output.trim().ifBlank { null }
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

fun resolveGitVersionString(repoDir: File): String? {
    return resolveGitVersionString(repoDir, useTag = true, tagPatterns = listOf("*"))
}

fun resolveLatestTagByPattern(repoDir: File, patterns: List<String>): String? {
    if (!repoDir.isDirectory) return null
    val normalizedPatterns = patterns.map { it.trim() }.filter { it.isNotEmpty() }
    val effectivePatterns = if (normalizedPatterns.isEmpty()) listOf("*") else normalizedPatterns
    for (pattern in effectivePatterns) {
        val listed = gitCommandOutput(
            repoDir,
            "git",
            "tag",
            "--list",
            pattern,
            "--sort=-version:refname"
        ) ?: continue
        val first = listed
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
        if (!first.isNullOrBlank()) {
            return first
        }
    }
    return null
}

fun resolveGitVersionString(
    repoDir: File,
    useTag: Boolean,
    tagPatterns: List<String>
): String? {
    if (!repoDir.isDirectory) return null
    val shortSha = gitCommandOutput(repoDir, "git", "rev-parse", "--short=8", "HEAD") ?: return null
    if (!useTag) {
        return shortSha
    }
    val selectedTag = resolveLatestTagByPattern(repoDir, tagPatterns)
    return if (selectedTag.isNullOrBlank()) shortSha else "$selectedTag-$shortSha"
}

fun escapeKotlinString(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

val aboutVersionSources = linkedMapOf(
    "core.ffmpeg" to "external/ffmpeg",
    "core.libopenmpt" to "external/libopenmpt",
    "core.vgmplay" to "external/libvgm",
    "core.gme" to "external/libgme",
    "core.libsidplayfp" to "external/libsidplayfp",
    "core.lazyusf2" to "external/lazyusf2",
    "core.vio2sf" to "external/2sf/vio2sf",
    "core.sc68" to "external/sc68",
    "core.adplug" to "external/adplug",
    "core.uade" to "external/uade",
    "core.hivelytracker" to "external/hivelytracker",
    "core.klystrack" to "external/klystrack",
    "core.furnace" to "external/furnace",
    "lib.psflib" to "external/psflib",
    "lib.libsoxr" to "external/libsoxr",
    "lib.libresidfp" to "external/libresidfp",
    "lib.resid" to "external/resid",
    "lib.openssl" to "external/openssl",
    "lib.libbinio" to "external/libbinio"
)

// Prefer dependency-specific release tag families where upstream uses multiple namespaces.
val aboutVersionTagPatterns = mapOf(
    "core.libopenmpt" to listOf("libopenmpt-*", "OpenMPT-*")
)

// Build-time toggle:
//   -PaboutVersionDisableTagsFor=core.libopenmpt,core.lazyusf2
// IDs not listed here continue using tag+hash when tags exist.
val aboutVersionDisableTagsFor = parseCsvGradleProperty(
    providers.gradleProperty("aboutVersionDisableTagsFor").orNull
)

val aboutVersionOverrides = mapOf(
    "core.sc68" to "r713"
)

val generatedAboutVersionDir = layout.buildDirectory.dir("generated/source/aboutVersions/main")
val generatedAboutVersionFile = generatedAboutVersionDir.map {
    File(it.asFile, "com/flopster101/siliconplayer/GeneratedAboutVersions.java")
}

val generateAboutVersions by tasks.registering {
    group = "build setup"
    description = "Generate About versions from submodule git metadata."
    outputs.file(generatedAboutVersionFile)
    inputs.property("aboutVersionDisableTagsFor", aboutVersionDisableTagsFor.toList().sorted().joinToString(","))
    inputs.property(
        "aboutVersionTagPatterns",
        aboutVersionTagPatterns
            .toSortedMap()
            .entries
            .joinToString("|") { (id, patterns) -> "$id=${patterns.joinToString(",")}" }
    )
    inputs.property(
        "aboutVersionOverrides",
        aboutVersionOverrides
            .toSortedMap()
            .entries
            .joinToString("|") { (id, value) -> "$id=$value" }
    )
    inputs.property(
        "aboutVersionSourceHeads",
        aboutVersionSources
            .entries
            .joinToString("|") { (id, path) ->
                val sourceDir = rootProject.file(path)
                val head = gitCommandOutput(sourceDir, "git", "rev-parse", "HEAD") ?: "missing"
                "$id=$head"
            }
    )
    doLast {
        val resolved = linkedMapOf<String, String>()
        for ((id, path) in aboutVersionSources) {
            val sourceDir = rootProject.file(path)
            val useTag = id !in aboutVersionDisableTagsFor
            val tagPatterns = aboutVersionTagPatterns[id] ?: listOf("*")
            val gitVersion = resolveGitVersionString(
                repoDir = sourceDir,
                useTag = useTag,
                tagPatterns = tagPatterns
            )
            val forcedVersion = aboutVersionOverrides[id]
            resolved[id] = forcedVersion ?: gitVersion ?: "unknown"
        }

        val outFile = generatedAboutVersionFile.get()
        outFile.parentFile.mkdirs()
        val mapBody = resolved.entries.joinToString("\n") { (id, version) ->
            "        map.put(\"${escapeKotlinString(id)}\", \"${escapeKotlinString(version)}\");"
        }
        outFile.writeText(
            """
            |package com.flopster101.siliconplayer;
            |
            |import java.util.Collections;
            |import java.util.LinkedHashMap;
            |import java.util.Map;
            |
            |public final class GeneratedAboutVersions {
            |    private static final Map<String, String> BY_ID;
            |
            |    static {
            |        Map<String, String> map = new LinkedHashMap<>();
            |$mapBody
            |        BY_ID = Collections.unmodifiableMap(map);
            |    }
            |
            |    private GeneratedAboutVersions() {
            |    }
            |
            |    public static String versionForId(String entityId) {
            |        return BY_ID.get(entityId);
            |    }
            |}
            |""".trimMargin()
        )
    }
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
    namespace = "com.flopster101.siliconplayer"
    compileSdk = 34
    buildToolsVersion = androidBuildToolsVersion
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
        minSdk = 21
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
            // x86 is opt-in via -PenableX86=true for compatibility testing.
            // Default app builds keep x86 excluded to reduce size/build time.
            abiFilters += "arm64-v8a"
            abiFilters += "armeabi-v7a"
            abiFilters += "x86_64"
            if (parseBooleanGradleProperty(providers.gradleProperty("enableX86").orNull)) {
                abiFilters += "x86"
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
            externalNativeBuild {
                cmake {
                    // Apply aggressive native optimization for release-like builds.
                    cFlags += "-O3 -ffast-math"
                    cppFlags += "-O3 -ffast-math"
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
    buildFeatures {
        compose = true
        buildConfig = true
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
            assets.directories.add("build/generated/uadeRuntimeAssets/main")
            jniLibs.directories.add("build/generated/uadeRuntimeJniLibs/main")
            java.directories.add("build/generated/source/aboutVersions/main")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateAboutVersions)
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
    val configured = androidBuildToolsVersion
    val configuredDir = File(sdkDir, "build-tools/$configured")
    require(configuredDir.isDirectory) { "Configured build-tools directory not found: $configuredDir" }
    return configuredDir
}

fun zipalignSupports16k(zipalign: File): Boolean {
    try {
        val result = runProcessAndCapture(listOf(zipalign.absolutePath))
        return result.output.contains("-P")
    } catch (_: Exception) {
        return false
    }
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

            val zipalignExitCode = runProcessWithInheritedIo(
                command = listOf(
                    zipalign.absolutePath,
                    "-f",
                    "-P", "16",
                    "-v", "4",
                    apk.absolutePath,
                    alignedUnsigned.absolutePath
                )
            )
            if (zipalignExitCode != 0) {
                throw GradleException("zipalign failed with exit code $zipalignExitCode")
            }

            val debugKeystore = rootProject.file("debug.keystore")
            require(debugKeystore.exists()) { "Debug keystore not found at ${debugKeystore.absolutePath}" }

            val apksignerExitCode = runProcessWithInheritedIo(
                command = listOf(
                    apksigner.absolutePath,
                    "sign",
                    "--ks", debugKeystore.absolutePath,
                    "--ks-key-alias", "androiddebugkey",
                    "--ks-pass", "pass:android",
                    "--key-pass", "pass:android",
                    "--out", alignedSigned.absolutePath,
                    alignedUnsigned.absolutePath
                )
            )
            if (apksignerExitCode != 0) {
                throw GradleException("apksigner failed with exit code $apksignerExitCode")
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

val enableX86 = parseBooleanGradleProperty(providers.gradleProperty("enableX86").orNull)
val uadeRuntimeAssetAbis = buildList {
    add("arm64-v8a")
    add("armeabi-v7a")
    add("x86_64")
    if (enableX86) add("x86")
}
val syncUadeRuntimeAssets = tasks.register("syncUadeRuntimeAssets") {
    group = "build setup"
    description = "Sync shared/per-ABI UADE runtime files into generated assets."

    doLast {
        val destinationRoot = layout.buildDirectory
            .dir("generated/uadeRuntimeAssets/main/uade")
            .get()
            .asFile
        delete(destinationRoot)

        // UADE share assets (players/score/config) are architecture-independent.
        // Keep a single common copy and only keep uadecore split by ABI.
        val sourceCommonShareDir =
            uadeRuntimeAssetAbis
                .asSequence()
                .map { abi -> File(file("src/main/cpp/prebuilt/$abi"), "share/uade") }
                .firstOrNull { it.isDirectory }

        if (sourceCommonShareDir == null) {
            logger.lifecycle("UADE shared runtime assets missing (expected share/uade under any configured ABI)")
        } else {
            copy {
                from(sourceCommonShareDir)
                into(File(destinationRoot, "common"))
            }
        }

        uadeRuntimeAssetAbis.forEach { abi ->
            val sourceUadeCore = file("src/main/cpp/prebuilt/$abi/lib/uade/uadecore")
            if (!sourceUadeCore.isFile) {
                logger.lifecycle(
                    "UADE runtime core missing for $abi, skipping (${sourceUadeCore.absolutePath})"
                )
                return@forEach
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
