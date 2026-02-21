package com.flopster101.siliconplayer

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import java.io.File

private const val UADE_RUNTIME_LOG_TAG = "UadeRuntimeSupport"

object UadeRuntimeSupport {
    @Volatile
    private var cachedRuntimeBaseDir: String? = null

    fun ensureInstalled(context: Context): String? {
        cachedRuntimeBaseDir?.let { return it }

        synchronized(this) {
            cachedRuntimeBaseDir?.let { return it }

            val assetManager = context.assets
            val abi = resolveBestAbi(assetManager) ?: run {
                Log.e(UADE_RUNTIME_LOG_TAG, "No UADE runtime assets found for supported ABIs")
                return null
            }

            val targetDir = File(context.filesDir, "uade_runtime/$abi")
            val runtimeVersion = "${BuildConfig.VERSION_CODE}-${BuildConfig.GIT_SHA}"
            val stampFile = File(targetDir, ".runtime-stamp")
            val coreFile = File(targetDir, "uadecore")
            val scoreFile = File(targetDir, "score")
            val uaercFile = File(targetDir, "uaerc")
            val playersDir = File(targetDir, "players")

            val alreadyPrepared =
                targetDir.isDirectory &&
                    coreFile.isFile &&
                    scoreFile.isFile &&
                    uaercFile.isFile &&
                    playersDir.isDirectory &&
                    stampFile.isFile &&
                    stampFile.readText().trim() == runtimeVersion

            if (!alreadyPrepared) {
                targetDir.deleteRecursively()
                copyAssetTree(assetManager, "uade/$abi", targetDir)
                if (!coreFile.setExecutable(true, false)) {
                    Log.w(
                        UADE_RUNTIME_LOG_TAG,
                        "Failed to set executable bit on ${coreFile.absolutePath}"
                    )
                }
                stampFile.writeText(runtimeVersion)
            }

            cachedRuntimeBaseDir = targetDir.absolutePath
            return cachedRuntimeBaseDir
        }
    }

    fun resolveUadeCoreExecutablePath(context: Context): String? {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        if (!nativeLibDir.isNullOrBlank()) {
            return File(nativeLibDir, "libuadecore_exec.so").absolutePath
        }

        val runtimeBaseDir = ensureInstalled(context) ?: return null
        val fromRuntime = File(runtimeBaseDir, "uadecore")
        return fromRuntime.takeIf { it.isFile }?.absolutePath
    }

    private fun resolveBestAbi(assetManager: AssetManager): String? {
        return Build.SUPPORTED_ABIS.firstOrNull { abi ->
            try {
                val entries = assetManager.list("uade/$abi") ?: emptyArray()
                entries.contains("uadecore")
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun copyAssetTree(assetManager: AssetManager, assetPath: String, output: File) {
        val entries = assetManager.list(assetPath) ?: emptyArray()
        if (entries.isEmpty()) {
            output.parentFile?.mkdirs()
            assetManager.open(assetPath).use { input ->
                output.outputStream().use { out -> input.copyTo(out) }
            }
            return
        }

        output.mkdirs()
        for (entry in entries) {
            val childAssetPath = "$assetPath/$entry"
            val childOutput = File(output, entry)
            copyAssetTree(assetManager, childAssetPath, childOutput)
        }
    }
}
