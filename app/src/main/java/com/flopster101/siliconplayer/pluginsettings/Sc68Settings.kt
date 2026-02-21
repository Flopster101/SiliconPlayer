package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.IntChoice
import com.flopster101.siliconplayer.CoreChoiceSelectorCard
import com.flopster101.siliconplayer.CoreDialogSliderCard
import com.flopster101.siliconplayer.PlayerSettingToggleCard

internal class Sc68Settings(
    private val asid: Int,
    private val ymEngine: Int,
    private val ymVolModel: Int,
    private val amigaFilter: Boolean,
    private val amigaBlend: Int,
    private val amigaClock: Int,
    private val onAsidChanged: (Int) -> Unit,
    private val onYmEngineChanged: (Int) -> Unit,
    private val onYmVolModelChanged: (Int) -> Unit,
    private val onAmigaFilterChanged: (Boolean) -> Unit,
    private val onAmigaBlendChanged: (Int) -> Unit,
    private val onAmigaClockChanged: (Int) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                CoreChoiceSelectorCard(
                    title = "aSID filter",
                    description = "Enable the aSIDifier compatibility path for unsupported tunes.",
                    selectedValue = asid,
                    options = listOf(
                        IntChoice(0, "Off"),
                        IntChoice(1, "On"),
                        IntChoice(2, "Force")
                    ),
                    onSelected = onAsidChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "YM engine",
                    description = "YM-2149 emulation backend.",
                    selectedValue = ymEngine,
                    options = listOf(
                        IntChoice(0, "BLEP"),
                        IntChoice(1, "Pulse")
                    ),
                    onSelected = onYmEngineChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "YM volume model",
                    description = "YM-2149 output volume model.",
                    selectedValue = ymVolModel,
                    options = listOf(
                        IntChoice(0, "Atari ST"),
                        IntChoice(1, "Linear")
                    ),
                    onSelected = onYmVolModelChanged
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "Amiga filter",
                    description = "Enable Paula low-pass filter when used.",
                    checked = amigaFilter,
                    onCheckedChange = onAmigaFilterChanged
                )
            }
            spacer()
            custom {
                CoreDialogSliderCard(
                    title = "Amiga blend",
                    description = "Paula left/right voice blend factor (128 is mono-like).",
                    value = amigaBlend,
                    valueRange = 0..255,
                    step = 1,
                    valueLabel = { it.toString() },
                    showNudgeButtons = true,
                    nudgeStep = 1,
                    onValueChanged = onAmigaBlendChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "Amiga clock",
                    description = "Paula timing standard.",
                    selectedValue = amigaClock,
                    options = listOf(
                        IntChoice(0, "PAL"),
                        IntChoice(1, "NTSC")
                    ),
                    onSelected = onAmigaClockChanged
                )
            }
        }
    }
}
