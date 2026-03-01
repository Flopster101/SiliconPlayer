package com.flopster101.siliconplayer

import android.os.Build

enum class ThemeMode(val storageValue: String, val label: String) {
    Auto("auto", "Auto"),
    Light("light", "Light"),
    Dark("dark", "Dark");

    companion object {
        fun fromStorage(value: String?): ThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: Auto
        }
    }
}

enum class AudioBackendPreference(val storageValue: String, val label: String, val nativeValue: Int) {
    AAudio("aaudio", "AAudio", 1),
    OpenSLES("opensl", "OpenSL ES", 2),
    AudioTrack("audiotrack", "AudioTrack", 3);

    companion object {
        fun fromStorage(value: String?): AudioBackendPreference {
            val stored = entries.firstOrNull { it.storageValue == value } ?: defaultAudioBackendForCurrentApi()
            return stored.coerceForCurrentApi()
        }
    }
}

fun AudioBackendPreference.coerceForCurrentApi(): AudioBackendPreference {
    if (this == AudioBackendPreference.AAudio && !isAaudioAvailableOnDevice()) {
        return AudioBackendPreference.OpenSLES
    }
    return this
}

fun isAaudioAvailableOnDevice(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

fun defaultAudioBackendForCurrentApi(): AudioBackendPreference {
    return if (isAaudioAvailableOnDevice()) AudioBackendPreference.AAudio else AudioBackendPreference.OpenSLES
}

fun AudioBackendPreference.defaultPerformanceMode(): AudioPerformanceMode {
    return when (this) {
        AudioBackendPreference.AAudio -> AudioPerformanceMode.LowLatency
        AudioBackendPreference.OpenSLES,
        AudioBackendPreference.AudioTrack -> AudioPerformanceMode.None
    }
}

fun AudioBackendPreference.defaultBufferPreset(): AudioBufferPreset {
    return when (this) {
        AudioBackendPreference.AAudio,
        AudioBackendPreference.OpenSLES,
        AudioBackendPreference.AudioTrack -> AudioBufferPreset.Small
    }
}

enum class AudioPerformanceMode(val storageValue: String, val label: String, val nativeValue: Int) {
    LowLatency("low_latency", "Low latency", 1),
    None("none", "None", 2),
    PowerSaving("power_saving", "Power saving", 3);

    companion object {
        fun fromStorage(value: String?): AudioPerformanceMode {
            return entries.firstOrNull { it.storageValue == value } ?: None
        }
    }
}

enum class AudioBufferPreset(val storageValue: String, val label: String, val nativeValue: Int) {
    VerySmall("very_small", "Very small", 0),
    Small("small", "Small", 1),
    Medium("medium", "Medium", 2),
    Large("large", "Large", 3);

    companion object {
        fun fromStorage(value: String?): AudioBufferPreset {
            return entries.firstOrNull { it.storageValue == value } ?: Small
        }
    }
}

enum class AudioResamplerPreference(val storageValue: String, val label: String, val nativeValue: Int) {
    BuiltIn("builtin", "Built-in", 1),
    Sox("sox", "SoX (Experimental)", 2);

    companion object {
        fun fromStorage(value: String?): AudioResamplerPreference {
            return entries.firstOrNull { it.storageValue == value } ?: BuiltIn
        }
    }
}

enum class FilenameDisplayMode(val storageValue: String, val label: String) {
    Always("always", "Always"),
    Never("never", "Never"),
    TrackerOnly("tracker_only", "Tracker/Chiptune formats only");

    companion object {
        fun fromStorage(value: String?): FilenameDisplayMode {
            return entries.firstOrNull { it.storageValue == value } ?: Always
        }
    }
}

enum class EndFadeCurve(val storageValue: String, val label: String, val nativeValue: Int) {
    Linear("linear", "Linear", 0),
    EaseIn("ease_in", "Ease-in", 1),
    EaseOut("ease_out", "Ease-out", 2);

    companion object {
        fun fromStorage(value: String?): EndFadeCurve {
            return entries.firstOrNull { it.storageValue == value } ?: Linear
        }
    }
}
