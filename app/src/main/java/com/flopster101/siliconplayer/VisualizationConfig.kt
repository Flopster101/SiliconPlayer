package com.flopster101.siliconplayer

enum class VisualizationMode(
    val storageValue: String,
    val label: String
) {
    Off("off", "Off"),
    Bars("bars", "Bars"),
    Oscilloscope("oscilloscope", "Oscilloscope"),
    VuMeters("vu_meters", "VU meters"),
    ChannelScope("channel_scope", "Channel scope");

    companion object {
        fun fromStorage(value: String?): VisualizationMode {
            return entries.firstOrNull { it.storageValue == value } ?: Off
        }
    }
}

enum class VisualizationRenderBackend(
    val storageValue: String,
    val label: String
) {
    Compose("compose", "Compose"),
    OpenGlTexture("opengl_texture", "OpenGL ES (TextureView)"),
    OpenGlSurface("opengl_surface", "OpenGL ES (SurfaceView)");

    companion object {
        fun fromStorage(value: String?, fallback: VisualizationRenderBackend): VisualizationRenderBackend {
            return when (value) {
                // Legacy migration: old GPU-canvas backend now maps to composited OpenGL backend.
                "gpu" -> OpenGlTexture
                // Legacy migration: old OpenGL value now maps to explicit SurfaceView backend.
                "opengl" -> OpenGlSurface
                else -> entries.firstOrNull { it.storageValue == value } ?: fallback
            }
        }
    }
}

fun visualizationRenderBackendForMode(mode: VisualizationMode): VisualizationRenderBackend {
    return when (mode) {
        VisualizationMode.Bars -> VisualizationRenderBackend.OpenGlTexture
        VisualizationMode.Oscilloscope -> VisualizationRenderBackend.OpenGlTexture
        VisualizationMode.VuMeters -> VisualizationRenderBackend.OpenGlTexture
        VisualizationMode.ChannelScope -> VisualizationRenderBackend.OpenGlTexture
        VisualizationMode.Off -> VisualizationRenderBackend.Compose
    }
}

enum class VisualizationChannelScopeLayout(
    val storageValue: String,
    val label: String
) {
    ColumnFirst("column_first", "Column-first (4ch = 1x4)"),
    BalancedTwoColumn("balanced_two_column", "Balanced (4ch = 2x2)");

    companion object {
        fun fromStorage(value: String?): VisualizationChannelScopeLayout {
            return entries.firstOrNull { it.storageValue == value } ?: ColumnFirst
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

enum class VisualizationOscColorMode(
    val storageValue: String,
    val label: String
) {
    Artwork("artwork", "From artwork"),
    Monet("monet", "Monet accent"),
    White("white", "White"),
    Custom("custom", "Custom");

    companion object {
        fun fromStorage(value: String?, fallback: VisualizationOscColorMode): VisualizationOscColorMode {
            return entries.firstOrNull { it.storageValue == value } ?: fallback
        }
    }
}

enum class VisualizationChannelScopeBackgroundMode(
    val storageValue: String,
    val label: String
) {
    AutoDarkAccent("auto_dark_accent", "Auto dark accent"),
    Custom("custom", "Custom");

    companion object {
        fun fromStorage(value: String?): VisualizationChannelScopeBackgroundMode {
            return entries.firstOrNull { it.storageValue == value } ?: AutoDarkAccent
        }
    }
}

enum class VisualizationChannelScopeTextAnchor(
    val storageValue: String,
    val label: String
) {
    TopLeft("top_left", "Top left"),
    TopCenter("top_center", "Top center"),
    TopRight("top_right", "Top right"),
    BottomRight("bottom_right", "Bottom right"),
    BottomCenter("bottom_center", "Bottom center"),
    BottomLeft("bottom_left", "Bottom left");

    companion object {
        fun fromStorage(value: String?): VisualizationChannelScopeTextAnchor {
            return entries.firstOrNull { it.storageValue == value } ?: TopLeft
        }
    }
}

enum class VisualizationNoteNameFormat(
    val storageValue: String,
    val label: String
) {
    American("american", "American (C, C#, D...)"),
    International("international", "International (Do, Do#, Re...)");

    companion object {
        fun fromStorage(value: String?): VisualizationNoteNameFormat {
            return entries.firstOrNull { it.storageValue == value } ?: American
        }
    }
}

enum class VisualizationChannelScopeTextColorMode(
    val storageValue: String,
    val label: String
) {
    Monet("monet", "Monet accent"),
    OpenMptInspired("openmpt_inspired", "OpenMPT-inspired"),
    White("white", "White"),
    Custom("custom", "Custom");

    companion object {
        fun fromStorage(value: String?): VisualizationChannelScopeTextColorMode {
            return entries.firstOrNull { it.storageValue == value } ?: Monet
        }
    }
}

enum class VisualizationChannelScopeTextFont(
    val storageValue: String,
    val label: String
) {
    System("system", "System"),
    RaccoonSerif("raccoon_serif", "Raccoon Serif"),
    RaccoonMono("raccoon_mono", "Raccoon Mono"),
    RetroCuteMono("retro_cute_mono", "Retro Pixel Cute Mono"),
    RetroThick("retro_thick", "Retro Pixel Thick");

    companion object {
        fun fromStorage(value: String?): VisualizationChannelScopeTextFont {
            return entries.firstOrNull { it.storageValue == value } ?: RetroCuteMono
        }
    }
}

enum class VisualizationOscFpsMode(
    val storageValue: String,
    val label: String
) {
    Default("default", "30 fps (Default)"),
    Fps60("60fps", "60 fps"),
    NativeRefresh("native_refresh", "Screen refresh rate");

    companion object {
        fun fromStorage(value: String?): VisualizationOscFpsMode {
            return entries.firstOrNull { it.storageValue == value } ?: Default
        }
    }
}
