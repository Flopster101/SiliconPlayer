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
