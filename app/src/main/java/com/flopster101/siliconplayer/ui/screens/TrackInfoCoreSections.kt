package com.flopster101.siliconplayer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun TrackInfoDetailsRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
internal fun TrackInfoCoreSections(
    decoderName: String?,
    sampleRateHz: Int,
    metadata: TrackInfoLiveMetadata
) {
    when {
        decoderName.equals("LibOpenMPT", ignoreCase = true) -> {
            TrackInfoSectionHeader("OpenMPT")
            if (metadata.openMpt.typeLong.isNotBlank()) TrackInfoDetailsRow("Module type", metadata.openMpt.typeLong)
            if (metadata.openMpt.tracker.isNotBlank()) TrackInfoDetailsRow("Tracker", metadata.openMpt.tracker)
            TrackInfoDetailsRow("Orders", metadata.openMpt.orderCount.toString())
            TrackInfoDetailsRow("Patterns", metadata.openMpt.patternCount.toString())
            TrackInfoDetailsRow("Instruments", metadata.openMpt.instrumentCount.toString())
            TrackInfoDetailsRow("Samples", metadata.openMpt.sampleCount.toString())
            if (metadata.openMpt.songMessage.isNotBlank()) TrackInfoDetailsRow("Message", metadata.openMpt.songMessage)
            if (metadata.openMpt.instrumentNames.isNotBlank()) TrackInfoDetailsRow("Instrument names", metadata.openMpt.instrumentNames)
            if (metadata.openMpt.sampleNames.isNotBlank()) TrackInfoDetailsRow("Sample names", metadata.openMpt.sampleNames)
        }

        decoderName.equals("VGMPlay", ignoreCase = true) -> {
            TrackInfoSectionHeader("VGMPlay")
            if (metadata.vgmPlay.gameName.isNotBlank()) TrackInfoDetailsRow("Game", metadata.vgmPlay.gameName)
            if (metadata.vgmPlay.systemName.isNotBlank()) TrackInfoDetailsRow("System", metadata.vgmPlay.systemName)
            if (metadata.vgmPlay.releaseDate.isNotBlank()) TrackInfoDetailsRow("Release date", metadata.vgmPlay.releaseDate)
            if (metadata.vgmPlay.encodedBy.isNotBlank()) TrackInfoDetailsRow("Encoded by", metadata.vgmPlay.encodedBy)
            if (metadata.vgmPlay.fileVersion.isNotBlank()) TrackInfoDetailsRow("VGM version", metadata.vgmPlay.fileVersion)
            if (metadata.vgmPlay.deviceCount > 0) TrackInfoDetailsRow("Used chips", metadata.vgmPlay.deviceCount.toString())
            if (metadata.vgmPlay.usedChipList.isNotBlank()) TrackInfoDetailsRow("Chip list", metadata.vgmPlay.usedChipList)
            TrackInfoDetailsRow("Has loop point", if (metadata.vgmPlay.hasLoopPoint) "Yes" else "No")
            if (metadata.vgmPlay.notes.isNotBlank()) TrackInfoDetailsRow("Notes", metadata.vgmPlay.notes)
        }

        decoderName.equals("FFmpeg", ignoreCase = true) -> {
            TrackInfoSectionHeader("FFmpeg")
            if (metadata.ffmpeg.codecName.isNotBlank()) TrackInfoDetailsRow("Codec", metadata.ffmpeg.codecName)
            if (metadata.ffmpeg.containerName.isNotBlank()) TrackInfoDetailsRow("Container", metadata.ffmpeg.containerName)
            if (metadata.ffmpeg.sampleFormatName.isNotBlank()) TrackInfoDetailsRow("Sample format", metadata.ffmpeg.sampleFormatName)
            if (metadata.ffmpeg.channelLayoutName.isNotBlank()) TrackInfoDetailsRow("Channel layout", metadata.ffmpeg.channelLayoutName)
            if (metadata.ffmpeg.encoderName.isNotBlank()) TrackInfoDetailsRow("Encoder", metadata.ffmpeg.encoderName)
        }

        decoderName.equals("Game Music Emu", ignoreCase = true) -> {
            TrackInfoSectionHeader("Game Music Emu")
            if (metadata.gme.systemName.isNotBlank()) TrackInfoDetailsRow("System", metadata.gme.systemName)
            if (metadata.gme.gameName.isNotBlank()) TrackInfoDetailsRow("Game", metadata.gme.gameName)
            if (metadata.gme.trackCount > 0) TrackInfoDetailsRow("Track count", metadata.gme.trackCount.toString())
            if (metadata.gme.voiceCount > 0) TrackInfoDetailsRow("Voice count", metadata.gme.voiceCount.toString())
            TrackInfoDetailsRow("Has loop point", if (metadata.gme.hasLoopPoint) "Yes" else "No")
            if (metadata.gme.loopStartMs >= 0) TrackInfoDetailsRow("Loop start", formatTime(metadata.gme.loopStartMs / 1000.0))
            if (metadata.gme.loopLengthMs > 0) TrackInfoDetailsRow("Loop length", formatTime(metadata.gme.loopLengthMs / 1000.0))
            if (metadata.gme.copyright.isNotBlank()) TrackInfoDetailsRow("Copyright", metadata.gme.copyright)
            if (metadata.gme.dumper.isNotBlank()) TrackInfoDetailsRow("Dumper", metadata.gme.dumper)
            if (metadata.gme.comment.isNotBlank()) TrackInfoDetailsRow("Comment", metadata.gme.comment)
        }

        decoderName.equals("LazyUSF2", ignoreCase = true) -> {
            TrackInfoSectionHeader("LazyUSF2")
            if (metadata.lazyUsf2.gameName.isNotBlank()) TrackInfoDetailsRow("Game", metadata.lazyUsf2.gameName)
            if (metadata.lazyUsf2.year.isNotBlank()) TrackInfoDetailsRow("Year", metadata.lazyUsf2.year)
            if (metadata.lazyUsf2.usfBy.isNotBlank()) TrackInfoDetailsRow("USF ripper", metadata.lazyUsf2.usfBy)
            if (metadata.lazyUsf2.copyright.isNotBlank()) TrackInfoDetailsRow("Copyright", metadata.lazyUsf2.copyright)
            if (metadata.lazyUsf2.lengthTag.isNotBlank()) TrackInfoDetailsRow("Tagged length", metadata.lazyUsf2.lengthTag)
            if (metadata.lazyUsf2.fadeTag.isNotBlank()) TrackInfoDetailsRow("Tagged fade", metadata.lazyUsf2.fadeTag)
            TrackInfoDetailsRow("Compare hack", if (metadata.lazyUsf2.enableCompare) "Enabled" else "Disabled")
            TrackInfoDetailsRow("FIFO full hack", if (metadata.lazyUsf2.enableFifoFull) "Enabled" else "Disabled")
        }

        decoderName.equals("Vio2SF", ignoreCase = true) -> {
            TrackInfoSectionHeader("Vio2SF")
            if (metadata.vio2sf.gameName.isNotBlank()) TrackInfoDetailsRow("Game", metadata.vio2sf.gameName)
            if (metadata.vio2sf.year.isNotBlank()) TrackInfoDetailsRow("Year", metadata.vio2sf.year)
            if (metadata.vio2sf.copyright.isNotBlank()) TrackInfoDetailsRow("Copyright", metadata.vio2sf.copyright)
            if (metadata.vio2sf.lengthTag.isNotBlank()) TrackInfoDetailsRow("Tagged length", metadata.vio2sf.lengthTag)
            if (metadata.vio2sf.fadeTag.isNotBlank()) TrackInfoDetailsRow("Tagged fade", metadata.vio2sf.fadeTag)
            if (metadata.vio2sf.comment.isNotBlank()) TrackInfoDetailsRow("Comment", metadata.vio2sf.comment)
        }

        decoderName.equals("LibSIDPlayFP", ignoreCase = true) -> {
            TrackInfoSectionHeader("LibSIDPlayFP")
            if (metadata.sid.backendName.isNotBlank()) TrackInfoDetailsRow("Engine", metadata.sid.backendName)
            if (metadata.sid.formatName.isNotBlank()) TrackInfoDetailsRow("Format name", metadata.sid.formatName)
            if (metadata.sid.clockName.isNotBlank()) TrackInfoDetailsRow("Declared clock", metadata.sid.clockName)
            if (metadata.sid.speedName.isNotBlank()) TrackInfoDetailsRow("Playback timing", metadata.sid.speedName)
            if (metadata.sid.compatibilityName.isNotBlank()) TrackInfoDetailsRow("Compatibility", metadata.sid.compatibilityName)
            if (metadata.sid.chipCount > 0) TrackInfoDetailsRow("SID chips", metadata.sid.chipCount.toString())
            if (metadata.sid.modelSummary.isNotBlank()) TrackInfoDetailsRow("SID models (declared)", metadata.sid.modelSummary)
            if (metadata.sid.currentModelSummary.isNotBlank()) TrackInfoDetailsRow("SID models (current)", metadata.sid.currentModelSummary)
            if (metadata.sid.baseAddressSummary.isNotBlank()) TrackInfoDetailsRow("SID base addresses", metadata.sid.baseAddressSummary)
            if (metadata.sid.commentSummary.isNotBlank()) TrackInfoDetailsRow("Comments", metadata.sid.commentSummary)
        }

        decoderName.equals("SC68", ignoreCase = true) -> {
            TrackInfoSectionHeader("SC68")
            if (metadata.sc68.formatName.isNotBlank()) TrackInfoDetailsRow("Format name", metadata.sc68.formatName)
            if (metadata.sc68.hardwareName.isNotBlank()) TrackInfoDetailsRow("Hardware", metadata.sc68.hardwareName)
            if (metadata.sc68.platformName.isNotBlank()) TrackInfoDetailsRow("Platform", metadata.sc68.platformName)
            if (metadata.sc68.replayName.isNotBlank()) TrackInfoDetailsRow("Replay", metadata.sc68.replayName)
            if (metadata.sc68.replayRateHz > 0) TrackInfoDetailsRow("Replay rate", "${metadata.sc68.replayRateHz} Hz")
            if (sampleRateHz > 0) TrackInfoDetailsRow("Frequency", "${sampleRateHz} Hz")
            if (metadata.sc68.trackCount > 0) TrackInfoDetailsRow("Track count", metadata.sc68.trackCount.toString())
            if (metadata.sc68.albumName.isNotBlank()) TrackInfoDetailsRow("Album", metadata.sc68.albumName)
            if (metadata.sc68.year.isNotBlank()) TrackInfoDetailsRow("Year", metadata.sc68.year)
            if (metadata.sc68.ripper.isNotBlank()) TrackInfoDetailsRow("Ripper", metadata.sc68.ripper)
            if (metadata.sc68.converter.isNotBlank()) TrackInfoDetailsRow("Converter", metadata.sc68.converter)
            if (metadata.sc68.timer.isNotBlank()) TrackInfoDetailsRow("Timer", metadata.sc68.timer)
            TrackInfoDetailsRow("Can aSID", if (metadata.sc68.canAsid) "Yes" else "No")
            TrackInfoDetailsRow("Uses YM-2149", if (metadata.sc68.usesYm) "Yes" else "No")
            TrackInfoDetailsRow("Uses STE", if (metadata.sc68.usesSte) "Yes" else "No")
            TrackInfoDetailsRow("Uses Amiga Paula", if (metadata.sc68.usesAmiga) "Yes" else "No")
        }

        decoderName.equals("AdPlug", ignoreCase = true) -> {
            TrackInfoSectionHeader("AdPlug")
            if (metadata.adplug.description.isNotBlank()) TrackInfoDetailsRow("Description", metadata.adplug.description)
            if (metadata.adplug.orderCount > 0) TrackInfoDetailsRow("Orders", metadata.adplug.orderCount.toString())
            if (metadata.adplug.orderCount > 0) TrackInfoDetailsRow("Current order", metadata.adplug.currentOrder.toString())
            if (metadata.adplug.patternCount > 0) TrackInfoDetailsRow("Patterns", metadata.adplug.patternCount.toString())
            if (metadata.adplug.patternCount > 0) TrackInfoDetailsRow("Current pattern", metadata.adplug.currentPattern.toString())
            if (metadata.adplug.patternCount > 0) TrackInfoDetailsRow("Current row", metadata.adplug.currentRow.toString())
            if (metadata.adplug.currentSpeed > 0) TrackInfoDetailsRow("Current speed", metadata.adplug.currentSpeed.toString())
            if (metadata.adplug.instrumentCount > 0) TrackInfoDetailsRow("Instruments", metadata.adplug.instrumentCount.toString())
            if (metadata.adplug.instrumentNames.isNotBlank()) TrackInfoDetailsRow("Instrument names", metadata.adplug.instrumentNames)
        }

        decoderName.equals("UADE", ignoreCase = true) -> {
            TrackInfoSectionHeader("UADE")
            if (metadata.uade.formatName.isNotBlank()) TrackInfoDetailsRow("Format name", metadata.uade.formatName)
            if (metadata.uade.moduleName.isNotBlank()) TrackInfoDetailsRow("Module name", metadata.uade.moduleName)
            if (metadata.uade.playerName.isNotBlank()) TrackInfoDetailsRow("Player name", metadata.uade.playerName)
            if (metadata.uade.detectionExtension.isNotBlank()) TrackInfoDetailsRow("Detected extension", metadata.uade.detectionExtension)
            if (metadata.uade.detectedFormatName.isNotBlank()) TrackInfoDetailsRow("Detected format", metadata.uade.detectedFormatName)
            if (metadata.uade.detectedFormatVersion.isNotBlank()) TrackInfoDetailsRow("Detected version", metadata.uade.detectedFormatVersion)
            TrackInfoDetailsRow("Detection source", if (metadata.uade.detectionByContent) "Content" else "Filename/extension")
            TrackInfoDetailsRow("Custom format", if (metadata.uade.detectionIsCustom) "Yes" else "No")
            if (metadata.uade.subsongMax >= metadata.uade.subsongMin) {
                TrackInfoDetailsRow("Subsong range", "${metadata.uade.subsongMin}..${metadata.uade.subsongMax}")
                TrackInfoDetailsRow("Default subsong", metadata.uade.subsongDefault.toString())
                TrackInfoDetailsRow("Current subsong", metadata.uade.currentSubsong.toString())
            }
            if (metadata.uade.moduleFileName.isNotBlank()) TrackInfoDetailsRow("Module file", metadata.uade.moduleFileName)
            if (metadata.uade.playerFileName.isNotBlank()) TrackInfoDetailsRow("Player file", metadata.uade.playerFileName)
            if (metadata.uade.moduleMd5.isNotBlank()) TrackInfoDetailsRow("Module MD5", metadata.uade.moduleMd5)
            if (metadata.uade.moduleBytes > 0L) TrackInfoDetailsRow("Module size", formatFileSize(metadata.uade.moduleBytes))
            if (metadata.uade.songBytes > 0L) TrackInfoDetailsRow("Rendered bytes (song)", formatFileSize(metadata.uade.songBytes))
            if (metadata.uade.subsongBytes > 0L) TrackInfoDetailsRow("Rendered bytes (subsong)", formatFileSize(metadata.uade.subsongBytes))
        }
    }
}

internal fun appendCoreTrackInfoCopyRows(
    builder: StringBuilder,
    decoderName: String?,
    sampleRateHz: Int,
    metadata: TrackInfoLiveMetadata
) {
    fun row(label: String, value: String) {
        builder.append(label).append(": ").append(value).append('\n')
    }

    when {
        decoderName.equals("LibOpenMPT", ignoreCase = true) -> {
            builder.append('\n').append("[OpenMPT]").append('\n')
            if (metadata.openMpt.typeLong.isNotBlank()) row("Module type", metadata.openMpt.typeLong)
            if (metadata.openMpt.tracker.isNotBlank()) row("Tracker", metadata.openMpt.tracker)
            row("Orders", metadata.openMpt.orderCount.toString())
            row("Patterns", metadata.openMpt.patternCount.toString())
            row("Instruments", metadata.openMpt.instrumentCount.toString())
            row("Samples", metadata.openMpt.sampleCount.toString())
            if (metadata.openMpt.songMessage.isNotBlank()) row("Message", metadata.openMpt.songMessage)
            if (metadata.openMpt.instrumentNames.isNotBlank()) row("Instrument names", metadata.openMpt.instrumentNames)
            if (metadata.openMpt.sampleNames.isNotBlank()) row("Sample names", metadata.openMpt.sampleNames)
        }

        decoderName.equals("VGMPlay", ignoreCase = true) -> {
            builder.append('\n').append("[VGMPlay]").append('\n')
            if (metadata.vgmPlay.gameName.isNotBlank()) row("Game", metadata.vgmPlay.gameName)
            if (metadata.vgmPlay.systemName.isNotBlank()) row("System", metadata.vgmPlay.systemName)
            if (metadata.vgmPlay.releaseDate.isNotBlank()) row("Release date", metadata.vgmPlay.releaseDate)
            if (metadata.vgmPlay.encodedBy.isNotBlank()) row("Encoded by", metadata.vgmPlay.encodedBy)
            if (metadata.vgmPlay.fileVersion.isNotBlank()) row("VGM version", metadata.vgmPlay.fileVersion)
            if (metadata.vgmPlay.deviceCount > 0) row("Used chips", metadata.vgmPlay.deviceCount.toString())
            if (metadata.vgmPlay.usedChipList.isNotBlank()) row("Chip list", metadata.vgmPlay.usedChipList)
            row("Has loop point", if (metadata.vgmPlay.hasLoopPoint) "Yes" else "No")
            if (metadata.vgmPlay.notes.isNotBlank()) row("Notes", metadata.vgmPlay.notes)
        }

        decoderName.equals("FFmpeg", ignoreCase = true) -> {
            builder.append('\n').append("[FFmpeg]").append('\n')
            if (metadata.ffmpeg.codecName.isNotBlank()) row("Codec", metadata.ffmpeg.codecName)
            if (metadata.ffmpeg.containerName.isNotBlank()) row("Container", metadata.ffmpeg.containerName)
            if (metadata.ffmpeg.sampleFormatName.isNotBlank()) row("Sample format", metadata.ffmpeg.sampleFormatName)
            if (metadata.ffmpeg.channelLayoutName.isNotBlank()) row("Channel layout", metadata.ffmpeg.channelLayoutName)
            if (metadata.ffmpeg.encoderName.isNotBlank()) row("Encoder", metadata.ffmpeg.encoderName)
        }

        decoderName.equals("Game Music Emu", ignoreCase = true) -> {
            builder.append('\n').append("[Game Music Emu]").append('\n')
            if (metadata.gme.systemName.isNotBlank()) row("System", metadata.gme.systemName)
            if (metadata.gme.gameName.isNotBlank()) row("Game", metadata.gme.gameName)
            if (metadata.gme.trackCount > 0) row("Track count", metadata.gme.trackCount.toString())
            if (metadata.gme.voiceCount > 0) row("Voice count", metadata.gme.voiceCount.toString())
            row("Has loop point", if (metadata.gme.hasLoopPoint) "Yes" else "No")
            if (metadata.gme.loopStartMs >= 0) row("Loop start", formatTime(metadata.gme.loopStartMs / 1000.0))
            if (metadata.gme.loopLengthMs > 0) row("Loop length", formatTime(metadata.gme.loopLengthMs / 1000.0))
            if (metadata.gme.copyright.isNotBlank()) row("Copyright", metadata.gme.copyright)
            if (metadata.gme.dumper.isNotBlank()) row("Dumper", metadata.gme.dumper)
            if (metadata.gme.comment.isNotBlank()) row("Comment", metadata.gme.comment)
        }

        decoderName.equals("LazyUSF2", ignoreCase = true) -> {
            builder.append('\n').append("[LazyUSF2]").append('\n')
            if (metadata.lazyUsf2.gameName.isNotBlank()) row("Game", metadata.lazyUsf2.gameName)
            if (metadata.lazyUsf2.year.isNotBlank()) row("Year", metadata.lazyUsf2.year)
            if (metadata.lazyUsf2.usfBy.isNotBlank()) row("USF ripper", metadata.lazyUsf2.usfBy)
            if (metadata.lazyUsf2.copyright.isNotBlank()) row("Copyright", metadata.lazyUsf2.copyright)
            if (metadata.lazyUsf2.lengthTag.isNotBlank()) row("Tagged length", metadata.lazyUsf2.lengthTag)
            if (metadata.lazyUsf2.fadeTag.isNotBlank()) row("Tagged fade", metadata.lazyUsf2.fadeTag)
            row("Compare hack", if (metadata.lazyUsf2.enableCompare) "Enabled" else "Disabled")
            row("FIFO full hack", if (metadata.lazyUsf2.enableFifoFull) "Enabled" else "Disabled")
        }

        decoderName.equals("Vio2SF", ignoreCase = true) -> {
            builder.append('\n').append("[Vio2SF]").append('\n')
            if (metadata.vio2sf.gameName.isNotBlank()) row("Game", metadata.vio2sf.gameName)
            if (metadata.vio2sf.year.isNotBlank()) row("Year", metadata.vio2sf.year)
            if (metadata.vio2sf.copyright.isNotBlank()) row("Copyright", metadata.vio2sf.copyright)
            if (metadata.vio2sf.lengthTag.isNotBlank()) row("Tagged length", metadata.vio2sf.lengthTag)
            if (metadata.vio2sf.fadeTag.isNotBlank()) row("Tagged fade", metadata.vio2sf.fadeTag)
            if (metadata.vio2sf.comment.isNotBlank()) row("Comment", metadata.vio2sf.comment)
        }

        decoderName.equals("LibSIDPlayFP", ignoreCase = true) -> {
            builder.append('\n').append("[LibSIDPlayFP]").append('\n')
            if (metadata.sid.backendName.isNotBlank()) row("Engine", metadata.sid.backendName)
            if (metadata.sid.formatName.isNotBlank()) row("Format name", metadata.sid.formatName)
            if (metadata.sid.clockName.isNotBlank()) row("Declared clock", metadata.sid.clockName)
            if (metadata.sid.speedName.isNotBlank()) row("Playback timing", metadata.sid.speedName)
            if (metadata.sid.compatibilityName.isNotBlank()) row("Compatibility", metadata.sid.compatibilityName)
            if (metadata.sid.chipCount > 0) row("SID chips", metadata.sid.chipCount.toString())
            if (metadata.sid.modelSummary.isNotBlank()) row("SID models (declared)", metadata.sid.modelSummary)
            if (metadata.sid.currentModelSummary.isNotBlank()) row("SID models (current)", metadata.sid.currentModelSummary)
            if (metadata.sid.baseAddressSummary.isNotBlank()) row("SID base addresses", metadata.sid.baseAddressSummary)
            if (metadata.sid.commentSummary.isNotBlank()) row("Comments", metadata.sid.commentSummary)
        }

        decoderName.equals("SC68", ignoreCase = true) -> {
            builder.append('\n').append("[SC68]").append('\n')
            if (metadata.sc68.formatName.isNotBlank()) row("Format name", metadata.sc68.formatName)
            if (metadata.sc68.hardwareName.isNotBlank()) row("Hardware", metadata.sc68.hardwareName)
            if (metadata.sc68.platformName.isNotBlank()) row("Platform", metadata.sc68.platformName)
            if (metadata.sc68.replayName.isNotBlank()) row("Replay", metadata.sc68.replayName)
            if (metadata.sc68.replayRateHz > 0) row("Replay rate", "${metadata.sc68.replayRateHz} Hz")
            if (sampleRateHz > 0) row("Frequency", "${sampleRateHz} Hz")
            if (metadata.sc68.trackCount > 0) row("Track count", metadata.sc68.trackCount.toString())
            if (metadata.sc68.albumName.isNotBlank()) row("Album", metadata.sc68.albumName)
            if (metadata.sc68.year.isNotBlank()) row("Year", metadata.sc68.year)
            if (metadata.sc68.ripper.isNotBlank()) row("Ripper", metadata.sc68.ripper)
            if (metadata.sc68.converter.isNotBlank()) row("Converter", metadata.sc68.converter)
            if (metadata.sc68.timer.isNotBlank()) row("Timer", metadata.sc68.timer)
            row("Can aSID", if (metadata.sc68.canAsid) "Yes" else "No")
            row("Uses YM-2149", if (metadata.sc68.usesYm) "Yes" else "No")
            row("Uses STE", if (metadata.sc68.usesSte) "Yes" else "No")
            row("Uses Amiga Paula", if (metadata.sc68.usesAmiga) "Yes" else "No")
        }

        decoderName.equals("AdPlug", ignoreCase = true) -> {
            builder.append('\n').append("[AdPlug]").append('\n')
            if (metadata.adplug.description.isNotBlank()) row("Description", metadata.adplug.description)
            if (metadata.adplug.orderCount > 0) row("Orders", metadata.adplug.orderCount.toString())
            if (metadata.adplug.orderCount > 0) row("Current order", metadata.adplug.currentOrder.toString())
            if (metadata.adplug.patternCount > 0) row("Patterns", metadata.adplug.patternCount.toString())
            if (metadata.adplug.patternCount > 0) row("Current pattern", metadata.adplug.currentPattern.toString())
            if (metadata.adplug.patternCount > 0) row("Current row", metadata.adplug.currentRow.toString())
            if (metadata.adplug.currentSpeed > 0) row("Current speed", metadata.adplug.currentSpeed.toString())
            if (metadata.adplug.instrumentCount > 0) row("Instruments", metadata.adplug.instrumentCount.toString())
            if (metadata.adplug.instrumentNames.isNotBlank()) row("Instrument names", metadata.adplug.instrumentNames)
        }

        decoderName.equals("UADE", ignoreCase = true) -> {
            builder.append('\n').append("[UADE]").append('\n')
            if (metadata.uade.formatName.isNotBlank()) row("Format name", metadata.uade.formatName)
            if (metadata.uade.moduleName.isNotBlank()) row("Module name", metadata.uade.moduleName)
            if (metadata.uade.playerName.isNotBlank()) row("Player name", metadata.uade.playerName)
            if (metadata.uade.detectionExtension.isNotBlank()) row("Detected extension", metadata.uade.detectionExtension)
            if (metadata.uade.detectedFormatName.isNotBlank()) row("Detected format", metadata.uade.detectedFormatName)
            if (metadata.uade.detectedFormatVersion.isNotBlank()) row("Detected version", metadata.uade.detectedFormatVersion)
            row("Detection source", if (metadata.uade.detectionByContent) "Content" else "Filename/extension")
            row("Custom format", if (metadata.uade.detectionIsCustom) "Yes" else "No")
            if (metadata.uade.subsongMax >= metadata.uade.subsongMin) {
                row("Subsong range", "${metadata.uade.subsongMin}..${metadata.uade.subsongMax}")
                row("Default subsong", metadata.uade.subsongDefault.toString())
                row("Current subsong", metadata.uade.currentSubsong.toString())
            }
            if (metadata.uade.moduleFileName.isNotBlank()) row("Module file", metadata.uade.moduleFileName)
            if (metadata.uade.playerFileName.isNotBlank()) row("Player file", metadata.uade.playerFileName)
            if (metadata.uade.moduleMd5.isNotBlank()) row("Module MD5", metadata.uade.moduleMd5)
            if (metadata.uade.moduleBytes > 0L) row("Module size", formatFileSize(metadata.uade.moduleBytes))
            if (metadata.uade.songBytes > 0L) row("Rendered bytes (song)", formatFileSize(metadata.uade.songBytes))
            if (metadata.uade.subsongBytes > 0L) row("Rendered bytes (subsong)", formatFileSize(metadata.uade.subsongBytes))
        }
    }
}

@Composable
private fun TrackInfoSectionHeader(title: String) {
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}
