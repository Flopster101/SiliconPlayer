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
    external fun getTrackSampleRate(): Int
    external fun getTrackChannelCount(): Int
    external fun getTrackBitDepth(): Int
    external fun getTrackBitDepthLabel(): String
    external fun getRepeatModeCapabilities(): Int
    external fun getPlaybackCapabilities(): Int
    external fun getCurrentDecoderName(): String
    external fun getTrackBitrate(): Long
    external fun isTrackVBR(): Boolean
    external fun setCoreOutputSampleRate(coreName: String, sampleRateHz: Int)
    external fun setCoreOption(coreName: String, optionName: String, optionValue: String)
    external fun getCoreCapabilities(coreName: String): Int
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
    external fun getDecoderSupportedExtensions(decoderName: String): Array<String>
    external fun getDecoderEnabledExtensions(decoderName: String): Array<String>
    external fun setDecoderEnabledExtensions(decoderName: String, extensions: Array<String>)
}
