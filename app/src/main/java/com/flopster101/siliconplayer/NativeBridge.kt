package com.flopster101.siliconplayer

import android.content.Context
import com.flopster101.siliconplayer.data.resolveArchiveMountedCompanionPath

object NativeBridge {
    const val CHANNEL_SCOPE_TEXT_STATE_STRIDE = 8
    const val CHANNEL_SCOPE_TEXT_FLAG_ACTIVE = 1 shl 0
    const val CHANNEL_SCOPE_TEXT_FLAG_AMIGA_LEFT = 1 shl 1
    const val CHANNEL_SCOPE_TEXT_FLAG_AMIGA_RIGHT = 1 shl 2

    init {
        System.loadLibrary("siliconplayer")
    }

    @Volatile
    private var appContext: Context? = null

    fun installContext(context: Context) {
        appContext = context.applicationContext
    }

    @JvmStatic
    fun resolveArchiveCompanionPathForNative(basePath: String?, requestedPath: String?): String? {
        if (appContext == null) return null
        return resolveArchiveMountedCompanionPath(
            basePath = basePath,
            requestedPath = requestedPath
        )
    }

    external fun startEngine()
    external fun startEngineWithPauseResumeFade()
    external fun stopEngine()
    external fun stopEngineWithPauseResumeFade()
    external fun isEnginePlaying(): Boolean
    external fun loadAudio(path: String)
    external fun getSupportedExtensions(): Array<String>
    external fun getDuration(): Double
    external fun getPosition(): Double
    external fun consumeNaturalEndEvent(): Boolean
    external fun seekTo(seconds: Double)
    external fun isSeekInProgress(): Boolean
    external fun setLooping(enabled: Boolean)
    external fun setRepeatMode(mode: Int)
    external fun getTrackTitle(): String
    external fun getTrackArtist(): String
    external fun getTrackComposer(): String
    external fun getTrackGenre(): String
    external fun getTrackSampleRate(): Int
    external fun getTrackChannelCount(): Int
    external fun getTrackBitDepth(): Int
    external fun getTrackBitDepthLabel(): String
    external fun getRepeatModeCapabilities(): Int
    external fun getPlaybackCapabilities(): Int
    external fun getCurrentDecoderName(): String
    external fun getSubtuneCount(): Int
    external fun getCurrentSubtuneIndex(): Int
    external fun selectSubtune(index: Int): Boolean
    external fun getSubtuneTitle(index: Int): String
    external fun getSubtuneArtist(index: Int): String
    external fun getSubtuneDurationSeconds(index: Int): Double
    external fun getDecoderRenderSampleRateHz(): Int
    external fun getOutputStreamSampleRateHz(): Int
    external fun getOpenMptModuleTypeLong(): String
    external fun getOpenMptTracker(): String
    external fun getOpenMptSongMessage(): String
    external fun getOpenMptOrderCount(): Int
    external fun getOpenMptPatternCount(): Int
    external fun getOpenMptInstrumentCount(): Int
    external fun getOpenMptSampleCount(): Int
    external fun getOpenMptInstrumentNames(): String
    external fun getOpenMptSampleNames(): String
    external fun getOpenMptChannelVuLevels(): FloatArray
    external fun getChannelScopeSamples(samplesPerChannel: Int): FloatArray
    external fun getChannelScopeTextState(maxChannels: Int): IntArray
    external fun getVgmGameName(): String
    external fun getVgmSystemName(): String
    external fun getVgmReleaseDate(): String
    external fun getVgmEncodedBy(): String
    external fun getVgmNotes(): String
    external fun getVgmFileVersion(): String
    external fun getVgmDeviceCount(): Int
    external fun getVgmUsedChipList(): String
    external fun getVgmHasLoopPoint(): Boolean
    external fun getFfmpegCodecName(): String
    external fun getFfmpegContainerName(): String
    external fun getFfmpegSampleFormatName(): String
    external fun getFfmpegChannelLayoutName(): String
    external fun getFfmpegEncoderName(): String
    external fun getGmeSystemName(): String
    external fun getGmeGameName(): String
    external fun getGmeCopyright(): String
    external fun getGmeComment(): String
    external fun getGmeDumper(): String
    external fun getGmeTrackCount(): Int
    external fun getGmeVoiceCount(): Int
    external fun getGmeHasLoopPoint(): Boolean
    external fun getGmeLoopStartMs(): Int
    external fun getGmeLoopLengthMs(): Int
    external fun getLazyUsf2GameName(): String
    external fun getLazyUsf2Copyright(): String
    external fun getLazyUsf2Year(): String
    external fun getLazyUsf2UsfBy(): String
    external fun getLazyUsf2LengthTag(): String
    external fun getLazyUsf2FadeTag(): String
    external fun getLazyUsf2EnableCompare(): Boolean
    external fun getLazyUsf2EnableFifoFull(): Boolean
    external fun getVio2sfGameName(): String
    external fun getVio2sfCopyright(): String
    external fun getVio2sfYear(): String
    external fun getVio2sfComment(): String
    external fun getVio2sfLengthTag(): String
    external fun getVio2sfFadeTag(): String
    external fun getSidFormatName(): String
    external fun getSidClockName(): String
    external fun getSidSpeedName(): String
    external fun getSidCompatibilityName(): String
    external fun getSidBackendName(): String
    external fun getSidChipCount(): Int
    external fun getSidModelSummary(): String
    external fun getSidCurrentModelSummary(): String
    external fun getSidBaseAddressSummary(): String
    external fun getSidCommentSummary(): String
    external fun getSc68FormatName(): String
    external fun getSc68HardwareName(): String
    external fun getSc68PlatformName(): String
    external fun getSc68ReplayName(): String
    external fun getSc68ReplayRateHz(): Int
    external fun getSc68TrackCount(): Int
    external fun getSc68AlbumName(): String
    external fun getSc68Year(): String
    external fun getSc68Ripper(): String
    external fun getSc68Converter(): String
    external fun getSc68Timer(): String
    external fun getSc68CanAsid(): Boolean
    external fun getSc68UsesYm(): Boolean
    external fun getSc68UsesSte(): Boolean
    external fun getSc68UsesAmiga(): Boolean
    external fun getAdplugDescription(): String
    external fun getAdplugPatternCount(): Int
    external fun getAdplugCurrentPattern(): Int
    external fun getAdplugOrderCount(): Int
    external fun getAdplugCurrentOrder(): Int
    external fun getAdplugCurrentRow(): Int
    external fun getAdplugCurrentSpeed(): Int
    external fun getAdplugInstrumentCount(): Int
    external fun getAdplugInstrumentNames(): String
    external fun getTrackBitrate(): Long
    external fun isTrackVBR(): Boolean
    external fun setCoreOutputSampleRate(coreName: String, sampleRateHz: Int)
    external fun setCoreOption(coreName: String, optionName: String, optionValue: String)
    external fun getCoreCapabilities(coreName: String): Int
    external fun getCoreOptionApplyPolicy(coreName: String, optionName: String): Int
    external fun getCoreFixedSampleRateHz(coreName: String): Int
    external fun setAudioPipelineConfig(
        backendPreference: Int,
        performanceMode: Int,
        bufferPreset: Int,
        resamplerPreference: Int,
        allowFallback: Boolean
    )
    external fun setEndFadeApplyToAllTracks(enabled: Boolean)
    external fun setEndFadeDurationMs(durationMs: Int)
    external fun setEndFadeCurve(curve: Int)
    external fun getVisualizationWaveformScope(channelIndex: Int, windowMs: Int, triggerMode: Int): FloatArray
    external fun getVisualizationBars(): FloatArray
    external fun getVisualizationVuLevels(): FloatArray
    external fun getVisualizationChannelCount(): Int

    // Gain control methods
    external fun setMasterGain(gainDb: Float)
    external fun setPluginGain(gainDb: Float)
    external fun setSongGain(gainDb: Float)
    external fun setForceMono(enabled: Boolean)
    external fun getMasterGain(): Float
    external fun getPluginGain(): Float
    external fun getSongGain(): Float
    external fun getForceMono(): Boolean
    external fun setMasterChannelMute(channelIndex: Int, enabled: Boolean)
    external fun setMasterChannelSolo(channelIndex: Int, enabled: Boolean)
    external fun getMasterChannelMute(channelIndex: Int): Boolean
    external fun getMasterChannelSolo(channelIndex: Int): Boolean
    external fun getDecoderToggleChannelNames(): Array<String>
    external fun getDecoderToggleChannelAvailability(): BooleanArray
    external fun setDecoderToggleChannelMuted(channelIndex: Int, enabled: Boolean)
    external fun getDecoderToggleChannelMuted(channelIndex: Int): Boolean
    external fun clearDecoderToggleChannelMutes()

    // Decoder Registry management methods
    external fun getRegisteredDecoderNames(): Array<String>
    external fun setDecoderEnabled(decoderName: String, enabled: Boolean)
    external fun isDecoderEnabled(decoderName: String): Boolean
    external fun setDecoderPriority(decoderName: String, priority: Int)
    external fun getDecoderPriority(decoderName: String): Int
    external fun getDecoderDefaultPriority(decoderName: String): Int
    external fun getDecoderSupportedExtensions(decoderName: String): Array<String>
    external fun getDecoderEnabledExtensions(decoderName: String): Array<String>
    external fun setDecoderEnabledExtensions(decoderName: String, extensions: Array<String>)
}
