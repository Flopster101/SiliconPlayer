package com.flopster101.siliconplayer

import java.io.File

internal fun fileMatchesSupportedExtensions(file: File, supportedExtensions: Set<String>): Boolean {
    return nameMatchesSupportedExtensions(file.name, supportedExtensions)
}
