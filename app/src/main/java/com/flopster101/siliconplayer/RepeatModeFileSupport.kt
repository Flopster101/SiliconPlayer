package com.flopster101.siliconplayer

import java.io.File

fun repeatCapabilitiesForFile(file: File?): RepeatCapabilities {
    return repeatCapabilitiesForExtension(file?.extension)
}

fun availableRepeatModesForFile(file: File?): List<RepeatMode> {
    return availableRepeatModesForExtension(file?.extension)
}

fun repeatModeCapabilitiesFlagsForFileFallback(file: File?): Int {
    return repeatModeCapabilitiesFlagsForExtensionFallback(file?.extension)
}

fun resolveRepeatModeForFile(preferredMode: RepeatMode, file: File?): RepeatMode {
    return resolveRepeatModeForExtension(preferredMode, file?.extension)
}
