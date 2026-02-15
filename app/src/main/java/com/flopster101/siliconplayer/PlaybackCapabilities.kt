package com.flopster101.siliconplayer

const val PLAYBACK_CAP_SEEK = 1 shl 0
const val PLAYBACK_CAP_RELIABLE_DURATION = 1 shl 1
const val PLAYBACK_CAP_LIVE_REPEAT_MODE = 1 shl 2
const val PLAYBACK_CAP_CUSTOM_SAMPLE_RATE = 1 shl 3
const val PLAYBACK_CAP_LIVE_SAMPLE_RATE_CHANGE = 1 shl 4
const val PLAYBACK_CAP_FIXED_SAMPLE_RATE = 1 shl 5

fun canSeekPlayback(flags: Int): Boolean = (flags and PLAYBACK_CAP_SEEK) != 0

fun hasReliableDuration(flags: Int): Boolean = (flags and PLAYBACK_CAP_RELIABLE_DURATION) != 0

fun supportsLiveRepeatMode(flags: Int): Boolean = (flags and PLAYBACK_CAP_LIVE_REPEAT_MODE) != 0

fun supportsCustomSampleRate(flags: Int): Boolean = (flags and PLAYBACK_CAP_CUSTOM_SAMPLE_RATE) != 0

fun supportsLiveSampleRateChange(flags: Int): Boolean = (flags and PLAYBACK_CAP_LIVE_SAMPLE_RATE_CHANGE) != 0

fun hasFixedSampleRate(flags: Int): Boolean = (flags and PLAYBACK_CAP_FIXED_SAMPLE_RATE) != 0
