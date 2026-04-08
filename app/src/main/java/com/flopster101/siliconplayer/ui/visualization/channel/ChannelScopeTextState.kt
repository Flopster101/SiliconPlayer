package com.flopster101.siliconplayer.ui.visualization.channel

data class ChannelScopeChannelTextState(
    val channelIndex: Int,
    val note: Int,
    val volume: Int,
    val effectPrimaryLetterAscii: Int,
    val effectPrimaryParam: Int,
    val effectSecondaryLetterAscii: Int,
    val effectSecondaryParam: Int,
    val instrumentIndex: Int,
    val sampleIndex: Int,
    val flags: Int
)
