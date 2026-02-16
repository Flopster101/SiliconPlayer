package com.flopster101.siliconplayer

enum class VisualizationMode(
    val storageValue: String,
    val label: String
) {
    Off("off", "Off"),
    Bars("bars", "Bars"),
    Oscilloscope("oscilloscope", "Oscilloscope"),
    VuMeters("vu_meters", "VU meters");

    companion object {
        fun fromStorage(value: String?): VisualizationMode {
            return entries.firstOrNull { it.storageValue == value } ?: Off
        }
    }
}

enum class VisualizationVuAnchor(
    val storageValue: String,
    val label: String
) {
    Top("top", "Top"),
    Center("center", "Center"),
    Bottom("bottom", "Bottom");

    companion object {
        fun fromStorage(value: String?): VisualizationVuAnchor {
            return entries.firstOrNull { it.storageValue == value } ?: Bottom
        }
    }
}

enum class VisualizationOscTriggerMode(
    val storageValue: String,
    val label: String,
    val nativeValue: Int
) {
    Off("off", "Off", 0),
    Rising("rising", "Rising edge", 1),
    Falling("falling", "Falling edge", 2);

    companion object {
        fun fromStorage(value: String?): VisualizationOscTriggerMode {
            return entries.firstOrNull { it.storageValue == value } ?: Off
        }
    }
}
