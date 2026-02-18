package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackUiSupportTest {

    @Test
    fun parseEnabledVisualizationModes_nullOrBlank_returnsSelectableDefaults() {
        val defaults = selectableVisualizationModes.toSet()
        assertEquals(defaults, parseEnabledVisualizationModes(null))
        assertEquals(defaults, parseEnabledVisualizationModes(""))
        assertEquals(defaults, parseEnabledVisualizationModes("   "))
    }

    @Test
    fun parseEnabledVisualizationModes_filtersUnknownValues_andKeepsKnownValues() {
        val parsed = parseEnabledVisualizationModes("bars,invalid,channel_scope,foo,vu_meters")
        assertEquals(
            setOf(
                VisualizationMode.Bars,
                VisualizationMode.ChannelScope,
                VisualizationMode.VuMeters
            ),
            parsed
        )
    }

    @Test
    fun parseEnabledVisualizationModes_onlyUnknownValues_returnsDefaults() {
        val defaults = selectableVisualizationModes.toSet()
        assertEquals(defaults, parseEnabledVisualizationModes("nope,unknown,???"))
    }

    @Test
    fun serializeEnabledVisualizationModes_usesSelectableOrder_andExcludesOff() {
        val serialized = serializeEnabledVisualizationModes(
            setOf(
                VisualizationMode.Off,
                VisualizationMode.ChannelScope,
                VisualizationMode.Bars
            )
        )
        assertEquals("bars,channel_scope", serialized)
        assertTrue(!serialized.contains("off"))
    }
}
