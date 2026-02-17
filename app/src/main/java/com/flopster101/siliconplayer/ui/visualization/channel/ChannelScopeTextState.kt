package com.flopster101.siliconplayer.ui.visualization.channel

data class ChannelScopeChannelTextState(
    val channelIndex: Int,
    val note: Int,
    val volume: Int,
    val effectLetterAscii: Int,
    val effectParam: Int,
    val instrumentIndex: Int,
    val sampleIndex: Int,
    val flags: Int
)
