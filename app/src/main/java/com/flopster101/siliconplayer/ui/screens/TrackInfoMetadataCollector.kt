package com.flopster101.siliconplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.flopster101.siliconplayer.NativeBridge
import kotlinx.coroutines.delay

internal data class OpenMptMetadata(
    val typeLong: String = "",
    val tracker: String = "",
    val songMessage: String = "",
    val orderCount: Int = 0,
    val patternCount: Int = 0,
    val instrumentCount: Int = 0,
    val sampleCount: Int = 0,
    val instrumentNames: String = "",
    val sampleNames: String = ""
)

internal data class VgmPlayMetadata(
    val gameName: String = "",
    val systemName: String = "",
    val releaseDate: String = "",
    val encodedBy: String = "",
    val notes: String = "",
    val fileVersion: String = "",
    val deviceCount: Int = 0,
    val usedChipList: String = "",
    val hasLoopPoint: Boolean = false
)

internal data class FfmpegMetadata(
    val codecName: String = "",
    val containerName: String = "",
    val sampleFormatName: String = "",
    val channelLayoutName: String = "",
    val encoderName: String = ""
)

internal data class GmeMetadata(
    val systemName: String = "",
    val gameName: String = "",
    val copyright: String = "",
    val comment: String = "",
    val dumper: String = "",
    val trackCount: Int = 0,
    val voiceCount: Int = 0,
    val hasLoopPoint: Boolean = false,
    val loopStartMs: Int = -1,
    val loopLengthMs: Int = -1
)

internal data class LazyUsf2Metadata(
    val gameName: String = "",
    val copyright: String = "",
    val year: String = "",
    val usfBy: String = "",
    val lengthTag: String = "",
    val fadeTag: String = "",
    val enableCompare: Boolean = false,
    val enableFifoFull: Boolean = false
)

internal data class Vio2sfMetadata(
    val gameName: String = "",
    val copyright: String = "",
    val year: String = "",
    val comment: String = "",
    val lengthTag: String = "",
    val fadeTag: String = ""
)

internal data class SidMetadata(
    val formatName: String = "",
    val clockName: String = "",
    val speedName: String = "",
    val compatibilityName: String = "",
    val backendName: String = "",
    val chipCount: Int = 0,
    val modelSummary: String = "",
    val currentModelSummary: String = "",
    val baseAddressSummary: String = "",
    val commentSummary: String = ""
)

internal data class Sc68Metadata(
    val formatName: String = "",
    val hardwareName: String = "",
    val platformName: String = "",
    val replayName: String = "",
    val replayRateHz: Int = 0,
    val trackCount: Int = 0,
    val albumName: String = "",
    val year: String = "",
    val ripper: String = "",
    val converter: String = "",
    val timer: String = "",
    val canAsid: Boolean = false,
    val usesYm: Boolean = false,
    val usesSte: Boolean = false,
    val usesAmiga: Boolean = false
)

internal data class AdplugMetadata(
    val description: String = "",
    val patternCount: Int = 0,
    val currentPattern: Int = 0,
    val orderCount: Int = 0,
    val currentOrder: Int = 0,
    val currentRow: Int = 0,
    val currentSpeed: Int = 0,
    val instrumentCount: Int = 0,
    val instrumentNames: String = ""
)

internal data class HivelyTrackerMetadata(
    val formatName: String = "",
    val formatVersion: Int = 0,
    val positionCount: Int = 0,
    val restartPosition: Int = -1,
    val trackLengthRows: Int = 0,
    val trackCount: Int = 0,
    val instrumentCount: Int = 0,
    val speedMultiplier: Int = 0,
    val currentPosition: Int = -1,
    val currentRow: Int = -1,
    val currentTempo: Int = 0,
    val mixGainPercent: Int = 0,
    val instrumentNames: String = ""
)

internal data class KlystrackMetadata(
    val formatName: String = "",
    val trackCount: Int = 0,
    val instrumentCount: Int = 0,
    val songLengthRows: Int = 0,
    val currentRow: Int = -1,
    val instrumentNames: String = ""
)

internal data class FurnaceMetadata(
    val formatName: String = "",
    val songVersion: Int = 0,
    val systemName: String = "",
    val systemNames: String = "",
    val systemCount: Int = 0,
    val songChannelCount: Int = 0,
    val instrumentCount: Int = 0,
    val wavetableCount: Int = 0,
    val sampleCount: Int = 0,
    val orderCount: Int = 0,
    val rowsPerPattern: Int = 0,
    val currentOrder: Int = -1,
    val currentRow: Int = -1,
    val currentTick: Int = -1,
    val currentSpeed: Int = 0,
    val grooveLength: Int = 0,
    val currentHz: Float = 0.0f
)

internal data class UadeMetadata(
    val formatName: String = "",
    val moduleName: String = "",
    val playerName: String = "",
    val moduleFileName: String = "",
    val playerFileName: String = "",
    val moduleMd5: String = "",
    val detectionExtension: String = "",
    val detectedFormatName: String = "",
    val detectedFormatVersion: String = "",
    val detectionByContent: Boolean = false,
    val detectionIsCustom: Boolean = false,
    val subsongMin: Int = 0,
    val subsongMax: Int = 0,
    val subsongDefault: Int = 0,
    val currentSubsong: Int = 0,
    val moduleBytes: Long = 0L,
    val songBytes: Long = 0L,
    val subsongBytes: Long = 0L
)

internal data class TrackInfoLiveMetadata(
    val bitrate: Long = 0L,
    val isVbr: Boolean = false,
    val audioBackendLabel: String = "(inactive)",
    val renderRateHz: Int = 0,
    val outputRateHz: Int = 0,
    val composer: String = "",
    val genre: String = "",
    val album: String = "",
    val year: String = "",
    val date: String = "",
    val copyrightText: String = "",
    val comment: String = "",
    val openMpt: OpenMptMetadata = OpenMptMetadata(),
    val vgmPlay: VgmPlayMetadata = VgmPlayMetadata(),
    val ffmpeg: FfmpegMetadata = FfmpegMetadata(),
    val gme: GmeMetadata = GmeMetadata(),
    val lazyUsf2: LazyUsf2Metadata = LazyUsf2Metadata(),
    val vio2sf: Vio2sfMetadata = Vio2sfMetadata(),
    val sid: SidMetadata = SidMetadata(),
    val sc68: Sc68Metadata = Sc68Metadata(),
    val adplug: AdplugMetadata = AdplugMetadata(),
    val hivelyTracker: HivelyTrackerMetadata = HivelyTrackerMetadata(),
    val klystrack: KlystrackMetadata = KlystrackMetadata(),
    val furnace: FurnaceMetadata = FurnaceMetadata(),
    val uade: UadeMetadata = UadeMetadata()
)

@Composable
internal fun rememberTrackInfoLiveMetadata(
    filePath: String?,
    decoderName: String?,
    isDialogVisible: Boolean
): TrackInfoLiveMetadata {
    var metadata by remember(filePath, decoderName) { mutableStateOf(TrackInfoLiveMetadata()) }

    LaunchedEffect(filePath, decoderName, isDialogVisible) {
        if (!isDialogVisible) return@LaunchedEffect
        while (isDialogVisible) {
            metadata = queryTrackInfoLiveMetadata(decoderName)
            delay(500)
        }
    }

    return metadata
}

private fun queryTrackInfoLiveMetadata(decoderName: String?): TrackInfoLiveMetadata {
    val common = TrackInfoLiveMetadata(
        bitrate = NativeBridge.getTrackBitrate(),
        isVbr = NativeBridge.isTrackVBR(),
        audioBackendLabel = NativeBridge.getAudioBackendLabel(),
        renderRateHz = NativeBridge.getDecoderRenderSampleRateHz(),
        outputRateHz = NativeBridge.getOutputStreamSampleRateHz(),
        composer = NativeBridge.getTrackComposer(),
        genre = NativeBridge.getTrackGenre(),
        album = NativeBridge.getTrackAlbum(),
        year = NativeBridge.getTrackYear(),
        date = NativeBridge.getTrackDate(),
        copyrightText = NativeBridge.getTrackCopyright(),
        comment = NativeBridge.getTrackComment()
    )

    return when {
        decoderName.equals("LibOpenMPT", ignoreCase = true) -> common.copy(
            openMpt = OpenMptMetadata(
                typeLong = NativeBridge.getOpenMptModuleTypeLong(),
                tracker = NativeBridge.getOpenMptTracker(),
                songMessage = NativeBridge.getOpenMptSongMessage(),
                orderCount = NativeBridge.getOpenMptOrderCount(),
                patternCount = NativeBridge.getOpenMptPatternCount(),
                instrumentCount = NativeBridge.getOpenMptInstrumentCount(),
                sampleCount = NativeBridge.getOpenMptSampleCount(),
                instrumentNames = NativeBridge.getOpenMptInstrumentNames(),
                sampleNames = NativeBridge.getOpenMptSampleNames()
            )
        )

        decoderName.equals("VGMPlay", ignoreCase = true) -> common.copy(
            vgmPlay = VgmPlayMetadata(
                gameName = NativeBridge.getVgmGameName(),
                systemName = NativeBridge.getVgmSystemName(),
                releaseDate = NativeBridge.getVgmReleaseDate(),
                encodedBy = NativeBridge.getVgmEncodedBy(),
                notes = NativeBridge.getVgmNotes(),
                fileVersion = NativeBridge.getVgmFileVersion(),
                deviceCount = NativeBridge.getVgmDeviceCount(),
                usedChipList = NativeBridge.getVgmUsedChipList(),
                hasLoopPoint = NativeBridge.getVgmHasLoopPoint()
            )
        )

        decoderName.equals("FFmpeg", ignoreCase = true) -> common.copy(
            ffmpeg = FfmpegMetadata(
                codecName = NativeBridge.getFfmpegCodecName(),
                containerName = NativeBridge.getFfmpegContainerName(),
                sampleFormatName = NativeBridge.getFfmpegSampleFormatName(),
                channelLayoutName = NativeBridge.getFfmpegChannelLayoutName(),
                encoderName = NativeBridge.getFfmpegEncoderName()
            )
        )

        decoderName.equals("Game Music Emu", ignoreCase = true) -> common.copy(
            gme = GmeMetadata(
                systemName = NativeBridge.getGmeSystemName(),
                gameName = NativeBridge.getGmeGameName(),
                copyright = NativeBridge.getGmeCopyright(),
                comment = NativeBridge.getGmeComment(),
                dumper = NativeBridge.getGmeDumper(),
                trackCount = NativeBridge.getGmeTrackCount(),
                voiceCount = NativeBridge.getGmeVoiceCount(),
                hasLoopPoint = NativeBridge.getGmeHasLoopPoint(),
                loopStartMs = NativeBridge.getGmeLoopStartMs(),
                loopLengthMs = NativeBridge.getGmeLoopLengthMs()
            )
        )

        decoderName.equals("LazyUSF2", ignoreCase = true) -> common.copy(
            lazyUsf2 = LazyUsf2Metadata(
                gameName = NativeBridge.getLazyUsf2GameName(),
                copyright = NativeBridge.getLazyUsf2Copyright(),
                year = NativeBridge.getLazyUsf2Year(),
                usfBy = NativeBridge.getLazyUsf2UsfBy(),
                lengthTag = NativeBridge.getLazyUsf2LengthTag(),
                fadeTag = NativeBridge.getLazyUsf2FadeTag(),
                enableCompare = NativeBridge.getLazyUsf2EnableCompare(),
                enableFifoFull = NativeBridge.getLazyUsf2EnableFifoFull()
            )
        )

        decoderName.equals("Vio2SF", ignoreCase = true) -> common.copy(
            vio2sf = Vio2sfMetadata(
                gameName = NativeBridge.getVio2sfGameName(),
                copyright = NativeBridge.getVio2sfCopyright(),
                year = NativeBridge.getVio2sfYear(),
                comment = NativeBridge.getVio2sfComment(),
                lengthTag = NativeBridge.getVio2sfLengthTag(),
                fadeTag = NativeBridge.getVio2sfFadeTag()
            )
        )

        decoderName.equals("LibSIDPlayFP", ignoreCase = true) -> common.copy(
            sid = SidMetadata(
                formatName = NativeBridge.getSidFormatName(),
                clockName = NativeBridge.getSidClockName(),
                speedName = NativeBridge.getSidSpeedName(),
                compatibilityName = NativeBridge.getSidCompatibilityName(),
                backendName = NativeBridge.getSidBackendName(),
                chipCount = NativeBridge.getSidChipCount(),
                modelSummary = NativeBridge.getSidModelSummary(),
                currentModelSummary = NativeBridge.getSidCurrentModelSummary(),
                baseAddressSummary = NativeBridge.getSidBaseAddressSummary(),
                commentSummary = NativeBridge.getSidCommentSummary()
            )
        )

        decoderName.equals("SC68", ignoreCase = true) -> common.copy(
            sc68 = Sc68Metadata(
                formatName = NativeBridge.getSc68FormatName(),
                hardwareName = NativeBridge.getSc68HardwareName(),
                platformName = NativeBridge.getSc68PlatformName(),
                replayName = NativeBridge.getSc68ReplayName(),
                replayRateHz = NativeBridge.getSc68ReplayRateHz(),
                trackCount = NativeBridge.getSc68TrackCount(),
                albumName = NativeBridge.getSc68AlbumName(),
                year = NativeBridge.getSc68Year(),
                ripper = NativeBridge.getSc68Ripper(),
                converter = NativeBridge.getSc68Converter(),
                timer = NativeBridge.getSc68Timer(),
                canAsid = NativeBridge.getSc68CanAsid(),
                usesYm = NativeBridge.getSc68UsesYm(),
                usesSte = NativeBridge.getSc68UsesSte(),
                usesAmiga = NativeBridge.getSc68UsesAmiga()
            )
        )

        decoderName.equals("AdPlug", ignoreCase = true) -> common.copy(
            adplug = AdplugMetadata(
                description = NativeBridge.getAdplugDescription(),
                patternCount = NativeBridge.getAdplugPatternCount(),
                currentPattern = NativeBridge.getAdplugCurrentPattern(),
                orderCount = NativeBridge.getAdplugOrderCount(),
                currentOrder = NativeBridge.getAdplugCurrentOrder(),
                currentRow = NativeBridge.getAdplugCurrentRow(),
                currentSpeed = NativeBridge.getAdplugCurrentSpeed(),
                instrumentCount = NativeBridge.getAdplugInstrumentCount(),
                instrumentNames = NativeBridge.getAdplugInstrumentNames()
            )
        )

        decoderName.equals("HivelyTracker", ignoreCase = true) -> common.copy(
            hivelyTracker = HivelyTrackerMetadata(
                formatName = NativeBridge.getHivelyFormatName(),
                formatVersion = NativeBridge.getHivelyFormatVersion(),
                positionCount = NativeBridge.getHivelyPositionCount(),
                restartPosition = NativeBridge.getHivelyRestartPosition(),
                trackLengthRows = NativeBridge.getHivelyTrackLengthRows(),
                trackCount = NativeBridge.getHivelyTrackCount(),
                instrumentCount = NativeBridge.getHivelyInstrumentCount(),
                speedMultiplier = NativeBridge.getHivelySpeedMultiplier(),
                currentPosition = NativeBridge.getHivelyCurrentPosition(),
                currentRow = NativeBridge.getHivelyCurrentRow(),
                currentTempo = NativeBridge.getHivelyCurrentTempo(),
                mixGainPercent = NativeBridge.getHivelyMixGainPercent(),
                instrumentNames = NativeBridge.getHivelyInstrumentNames()
            )
        )

        decoderName.equals("Klystrack", ignoreCase = true) -> common.copy(
            klystrack = KlystrackMetadata(
                formatName = NativeBridge.getKlystrackFormatName(),
                trackCount = NativeBridge.getKlystrackTrackCount(),
                instrumentCount = NativeBridge.getKlystrackInstrumentCount(),
                songLengthRows = NativeBridge.getKlystrackSongLengthRows(),
                currentRow = NativeBridge.getKlystrackCurrentRow(),
                instrumentNames = NativeBridge.getKlystrackInstrumentNames()
            )
        )

        decoderName.equals("Furnace", ignoreCase = true) -> common.copy(
            furnace = FurnaceMetadata(
                formatName = NativeBridge.getFurnaceFormatName(),
                songVersion = NativeBridge.getFurnaceSongVersion(),
                systemName = NativeBridge.getFurnaceSystemName(),
                systemNames = NativeBridge.getFurnaceSystemNames(),
                systemCount = NativeBridge.getFurnaceSystemCount(),
                songChannelCount = NativeBridge.getFurnaceSongChannelCount(),
                instrumentCount = NativeBridge.getFurnaceInstrumentCount(),
                wavetableCount = NativeBridge.getFurnaceWavetableCount(),
                sampleCount = NativeBridge.getFurnaceSampleCount(),
                orderCount = NativeBridge.getFurnaceOrderCount(),
                rowsPerPattern = NativeBridge.getFurnaceRowsPerPattern(),
                currentOrder = NativeBridge.getFurnaceCurrentOrder(),
                currentRow = NativeBridge.getFurnaceCurrentRow(),
                currentTick = NativeBridge.getFurnaceCurrentTick(),
                currentSpeed = NativeBridge.getFurnaceCurrentSpeed(),
                grooveLength = NativeBridge.getFurnaceGrooveLength(),
                currentHz = NativeBridge.getFurnaceCurrentHz()
            )
        )

        decoderName.equals("UADE", ignoreCase = true) -> common.copy(
            uade = UadeMetadata(
                formatName = NativeBridge.getUadeFormatName(),
                moduleName = NativeBridge.getUadeModuleName(),
                playerName = NativeBridge.getUadePlayerName(),
                moduleFileName = NativeBridge.getUadeModuleFileName(),
                playerFileName = NativeBridge.getUadePlayerFileName(),
                moduleMd5 = NativeBridge.getUadeModuleMd5(),
                detectionExtension = NativeBridge.getUadeDetectionExtension(),
                detectedFormatName = NativeBridge.getUadeDetectedFormatName(),
                detectedFormatVersion = NativeBridge.getUadeDetectedFormatVersion(),
                detectionByContent = NativeBridge.getUadeDetectionByContent(),
                detectionIsCustom = NativeBridge.getUadeDetectionIsCustom(),
                subsongMin = NativeBridge.getUadeSubsongMin(),
                subsongMax = NativeBridge.getUadeSubsongMax(),
                subsongDefault = NativeBridge.getUadeSubsongDefault(),
                currentSubsong = NativeBridge.getUadeCurrentSubsong(),
                moduleBytes = NativeBridge.getUadeModuleBytes(),
                songBytes = NativeBridge.getUadeSongBytes(),
                subsongBytes = NativeBridge.getUadeSubsongBytes()
            )
        )

        else -> common
    }
}
