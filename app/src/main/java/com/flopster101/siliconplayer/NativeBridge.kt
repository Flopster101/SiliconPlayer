package com.flopster101.siliconplayer

object NativeBridge {
    init {
        System.loadLibrary("siliconplayer")
    }

    external fun startEngine()
    external fun stopEngine()
    external fun isEnginePlaying(): Boolean
    external fun loadAudio(path: String)
    external fun getSupportedExtensions(): Array<String>
    external fun getDuration(): Double
    external fun getPosition(): Double
    external fun consumeNaturalEndEvent(): Boolean
    external fun seekTo(seconds: Double)
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
    external fun getVisualizationWaveform(channelIndex: Int): FloatArray
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
