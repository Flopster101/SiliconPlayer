package com.flopster101.siliconplayer

object AppDefaults {
    object Browser {
        val nameSortMode = BrowserNameSortMode.Natural
        const val showParentDirectoryEntry = true
        const val showFileIconChipBackground = true
        const val showUnsupportedFiles = false
        const val showPreviewFiles = true
        const val showHiddenFilesAndFolders = false
    }

    object Player {
        const val keepScreenOn = false
        const val fadePauseResume = true
        const val preloadNextCachedRemoteTrack = true
        const val artworkCornerRadiusDp = 6
        const val endFadeApplyToAllTracks = false
        const val endFadeDurationMs = 10_000
        val endFadeCurve = EndFadeCurve.Linear
    }

    object AudioProcessing {
        const val outputLimiterEnabled = false

        object Dsp {
            const val bassEnabled = false
            const val bassDepth = 2
            const val bassRange = 2
            const val surroundEnabled = false
            const val surroundDepth = 8
            const val surroundDelayMs = 20
            const val reverbEnabled = false
            const val reverbDepth = 8
            const val reverbPreset = 0
            const val bitCrushEnabled = false
            const val bitCrushBits = 16
        }
    }

    object Visualization {
        const val showDebugInfo = false

        object Bars {
            const val count = 40
            const val smoothingPercent = 60
            const val roundnessDp = 6
            const val frequencyGridEnabled = false
            val fpsMode = VisualizationOscFpsMode.Fps60
            const val overlayArtwork = true
            const val useThemeColor = true
            val renderBackend = VisualizationRenderBackend.OpenGlTexture
            const val customColorArgb = 0xFF6BD8FF.toInt()
            val colorModeNoArtwork = VisualizationOscColorMode.Monet
            val colorModeWithArtwork = VisualizationOscColorMode.Artwork
            val countRange = 8..96
            val smoothingRange = 0..95
            val roundnessRange = 0..24
        }

        object Oscilloscope {
            const val stereo = true
            const val windowMs = 30
            val triggerMode = VisualizationOscTriggerMode.Rising
            val fpsMode = VisualizationOscFpsMode.Default
            val renderBackend = VisualizationRenderBackend.OpenGlTexture
            const val lineWidthDp = 3
            const val gridWidthDp = 2
            const val verticalGridEnabled = false
            const val centerLineEnabled = false
            val lineColorModeNoArtwork = VisualizationOscColorMode.Monet
            val gridColorModeNoArtwork = VisualizationOscColorMode.Monet
            val lineColorModeWithArtwork = VisualizationOscColorMode.Artwork
            val gridColorModeWithArtwork = VisualizationOscColorMode.Artwork
            const val customLineColorArgb = 0xFF6BD8FF.toInt()
            const val customGridColorArgb = 0x66FFFFFF
            val windowRangeMs = 5..200
            val lineWidthRangeDp = 1..12
            val gridWidthRangeDp = 1..8
        }

        object Vu {
            val anchor = VisualizationVuAnchor.Bottom
            const val useThemeColor = true
            const val smoothingPercent = 40
            val fpsMode = VisualizationOscFpsMode.Fps60
            val renderBackend = VisualizationRenderBackend.OpenGlTexture
            val colorModeNoArtwork = VisualizationOscColorMode.Monet
            val colorModeWithArtwork = VisualizationOscColorMode.Artwork
            const val customColorArgb = 0xFF6BD8FF.toInt()
            val smoothingRange = 0..95
        }

        object ChannelScope {
            const val windowMs = 30
            val renderBackend = VisualizationRenderBackend.OpenGlTexture
            const val dcRemovalEnabled = true
            const val gainPercent = 240
            val triggerMode = VisualizationOscTriggerMode.Rising
            val fpsMode = VisualizationOscFpsMode.Default
            const val lineWidthDp = 3
            const val gridWidthDp = 2
            const val verticalGridEnabled = false
            const val centerLineEnabled = false
            const val showArtworkBackground = true
            val backgroundMode = VisualizationChannelScopeBackgroundMode.AutoDarkAccent
            const val customBackgroundColorArgb = 0xFF101418.toInt()
            val layout = VisualizationChannelScopeLayout.ColumnFirst
            val lineColorModeNoArtwork = VisualizationOscColorMode.Monet
            val gridColorModeNoArtwork = VisualizationOscColorMode.Monet
            val lineColorModeWithArtwork = VisualizationOscColorMode.Artwork
            val gridColorModeWithArtwork = VisualizationOscColorMode.Artwork
            const val customLineColorArgb = 0xFF6BD8FF.toInt()
            const val customGridColorArgb = 0x66FFFFFF
            const val textEnabled = true
            val textAnchor = VisualizationChannelScopeTextAnchor.TopLeft
            const val textPaddingDp = 6
            const val textSizeSp = 8
            const val textHideWhenOverflow = true
            const val textShadowEnabled = true
            val textFont = VisualizationChannelScopeTextFont.RetroCuteMono
            val textColorMode = VisualizationChannelScopeTextColorMode.OpenMptInspired
            const val customTextColorArgb = 0xFFFFFFFF.toInt()
            val textNoteFormat = VisualizationNoteNameFormat.American
            const val textShowChannel = true
            const val textShowNote = true
            const val textShowVolume = true
            const val textShowEffect = true
            const val textShowInstrumentSample = true
            const val textVuEnabled = false
            val textVuAnchor = VisualizationVuAnchor.Bottom
            val textVuColorMode = VisualizationChannelScopeTextColorMode.OpenMptInspired
            const val textVuCustomColorArgb = 0xFFFFFFFF.toInt()

            val windowRangeMs = 5..200
            val gainRangePercent = 25..600
            val lineWidthRangeDp = 1..12
            val gridWidthRangeDp = 1..8
            val textPaddingRangeDp = 0..24
            val textSizeRangeSp = 6..22
        }
    }
}
