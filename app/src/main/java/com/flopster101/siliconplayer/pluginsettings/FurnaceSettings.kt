package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.CoreChoiceSelectorCard
import com.flopster101.siliconplayer.FurnaceConfig

internal class FurnaceSettings(
    private val ym2612Core: Int,
    private val snCore: Int,
    private val nesCore: Int,
    private val c64Core: Int,
    private val gbQuality: Int,
    private val dsidQuality: Int,
    private val ayCore: Int,
    private val onYm2612CoreChanged: (Int) -> Unit,
    private val onSnCoreChanged: (Int) -> Unit,
    private val onNesCoreChanged: (Int) -> Unit,
    private val onC64CoreChanged: (Int) -> Unit,
    private val onGbQualityChanged: (Int) -> Unit,
    private val onDsidQualityChanged: (Int) -> Unit,
    private val onAyCoreChanged: (Int) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                CoreChoiceSelectorCard(
                    title = "YM2612 core",
                    description = "Default FM core for Genesis/Mega Drive chip playback.",
                    selectedValue = ym2612Core,
                    options = FurnaceConfig.ym2612CoreChoices,
                    onSelected = onYm2612CoreChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "SN76489 core",
                    description = "Default PSG emulation core.",
                    selectedValue = snCore,
                    options = FurnaceConfig.snCoreChoices,
                    onSelected = onSnCoreChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "NES core",
                    description = "Default APU emulation backend for NES-family chips.",
                    selectedValue = nesCore,
                    options = FurnaceConfig.nesCoreChoices,
                    onSelected = onNesCoreChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "C64 core",
                    description = "Default SID emulation core for C64 systems.",
                    selectedValue = c64Core,
                    options = FurnaceConfig.c64CoreChoices,
                    onSelected = onC64CoreChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "dSID quality",
                    description = "Quality level used by the dSID backend.",
                    selectedValue = dsidQuality,
                    options = FurnaceConfig.qualityChoices,
                    onSelected = onDsidQualityChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "Game Boy quality",
                    description = "Quality/performance level for the Game Boy core.",
                    selectedValue = gbQuality,
                    options = FurnaceConfig.qualityChoices,
                    onSelected = onGbQualityChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "AY core",
                    description = "Default AY-3-8910/YM2149 core selection.",
                    selectedValue = ayCore,
                    options = FurnaceConfig.ayCoreChoices,
                    onSelected = onAyCoreChanged
                )
            }
        }
    }
}
