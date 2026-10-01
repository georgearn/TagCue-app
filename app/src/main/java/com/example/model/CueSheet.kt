package com.example.model

import android.net.Uri

enum class CueEncoding(val displayName: String, val charsetName: String) {
    AUTO("Auto-Detect", "AUTO"),
    UTF_8("UTF-8 (Unicode)", "UTF-8"),
    WINDOWS_1251("Windows-1251 (Cyrillic)", "windows-1251"),
    LATIN_1("Latin-1 (ISO-8859-1)", "ISO-8859-1"),
    WINDOWS_1252("Windows-1252 (Western)", "windows-1252")
}

enum class TrackNumberStyle(val displayName: String) {
    STYLE_1("1, 2, 3"),
    STYLE_01("01, 02, 03"),
    STYLE_01_OF_TOTAL("01 of 12")
}

data class CueIndex(
    val number: Int,
    val minutes: Int,
    val seconds: Int,
    val frames: Int
) {
    val totalFrames: Long
        get() = (minutes.toLong() * 60L + seconds.toLong()) * 75L + frames.toLong()

    val totalMilliseconds: Long
        get() = (totalFrames * 1000L) / 75L

    fun toSampleOffset(sampleRate: Int): Long {
        return (totalFrames * sampleRate.toLong()) / 75L
    }

    val formattedTime: String
        get() = "%02d:%02d:%02d".format(minutes, seconds, frames)
}

data class CueTrack(
    val number: Int,
    val title: String,
    val performer: String,
    val album: String,
    val genre: String = "",
    val date: String = "",
    val durationMs: Long = 0L,
    val indices: List<CueIndex> = emptyList(),
    val isSelectedForSplit: Boolean = true
) {
    val startIndex: CueIndex?
        get() = indices.find { it.number == 1 } ?: indices.firstOrNull()

    val formattedStartTime: String
        get() = startIndex?.formattedTime ?: "00:00:00"

    val index01: String
        get() = formattedStartTime

    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "--:--"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

data class CueSheet(
    val uri: Uri? = null,
    val rawBytes: ByteArray = ByteArray(0),
    val rawText: String = "",
    val encoding: CueEncoding = CueEncoding.AUTO,
    val title: String = "",
    val performer: String = "",
    val date: String = "",
    val genre: String = "",
    val audioFileName: String = "",
    val tracks: List<CueTrack> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CueSheet
        return uri == other.uri &&
                encoding == other.encoding &&
                title == other.title &&
                performer == other.performer &&
                tracks == other.tracks
    }

    override fun hashCode(): Int {
        var result = uri?.hashCode() ?: 0
        result = 31 * result + encoding.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + performer.hashCode()
        result = 31 * result + tracks.hashCode()
        return result
    }
}

data class SplitOutputConfig(
    val filenameTemplate: String = "{track} - {title}",
    val folderTemplate: String = "{artist}/{year} - {album}",
    val trackNumberStyle: TrackNumberStyle = TrackNumberStyle.STYLE_01,
    val useAlbumArtist: Boolean = false,
    val moveFeatIntoTitle: Boolean = true,
    val caseOption: CaseOption = CaseOption.ORIGINAL,
    val copyExistingCoverArt: Boolean = true
) {
    fun formatTrackNumber(number: Int, total: Int): String {
        return when (trackNumberStyle) {
            TrackNumberStyle.STYLE_1 -> number.toString()
            TrackNumberStyle.STYLE_01 -> number.toString().padStart(2, '0')
            TrackNumberStyle.STYLE_01_OF_TOTAL -> "${number.toString().padStart(2, '0')} of $total"
        }
    }

    fun resolveMetadata(
        track: CueTrack,
        albumTitle: String,
        albumPerformer: String,
        albumDate: String,
        totalTracks: Int
    ): ResolvedTrackMetadata {
        var effectiveArtist = if (useAlbumArtist) {
            albumPerformer.ifBlank { track.performer }
        } else {
            track.performer.ifBlank { albumPerformer }
        }
        var effectiveTitle = track.title.ifBlank { "Track ${track.number}" }

        if (moveFeatIntoTitle) {
            val featRegex = Regex("""(?i)\s+(?:feat\.?|ft\.?)\s+(.+)""")
            val match = featRegex.find(effectiveArtist)
            if (match != null) {
                val featured = match.groupValues[1].trim()
                effectiveArtist = effectiveArtist.substring(0, match.range.first).trim()
                if (!effectiveTitle.contains("feat", ignoreCase = true)) {
                    effectiveTitle = "$effectiveTitle (feat. $featured)"
                }
            }
        }

        val effectiveAlbum = albumTitle.ifBlank { "Unknown Album" }
        val effectiveYear = track.date.ifBlank { albumDate }
        val trackNumStr = formatTrackNumber(track.number, totalTracks)

        // Resolve filename template: {track}, {title}, {artist}, {album}, {year}
        var fn = filenameTemplate
            .replace("{track}", trackNumStr)
            .replace("{title}", effectiveTitle)
            .replace("{artist}", effectiveArtist)
            .replace("{album}", effectiveAlbum)
            .replace("{year}", effectiveYear)
            // Also support % tokens if typed
            .replace("%n", trackNumStr)
            .replace("%t", effectiveTitle)
            .replace("%a", effectiveArtist)
            .replace("%l", effectiveAlbum)
            .replace("%y", effectiveYear)

        fn = applyCase(fn, caseOption)
        val sanitizedFileName = fn.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
            .ifBlank { "track_${track.number}" }
        val finalFileName = if (sanitizedFileName.endsWith(".flac", ignoreCase = true)) {
            sanitizedFileName
        } else {
            "$sanitizedFileName.flac"
        }

        // Resolve folder template
        var folder = folderTemplate
            .replace("{artist}", effectiveArtist)
            .replace("{album}", effectiveAlbum)
            .replace("{year}", effectiveYear)
            .replace("%a", effectiveArtist)
            .replace("%l", effectiveAlbum)
            .replace("%y", effectiveYear)

        folder = applyCase(folder, caseOption)
        val sanitizedFolder = folder.split("/").joinToString("/") { part ->
            part.replace(Regex("""[\\:*?"<>|]"""), "_").trim()
        }.trim('/')

        return ResolvedTrackMetadata(
            trackNumber = track.number,
            trackNumberFormatted = trackNumStr,
            totalTracks = totalTracks,
            title = effectiveTitle,
            artist = effectiveArtist,
            albumArtist = albumPerformer.ifBlank { effectiveArtist },
            album = effectiveAlbum,
            year = effectiveYear,
            fileName = finalFileName,
            subFolderPath = sanitizedFolder
        )
    }

    private fun applyCase(str: String, option: CaseOption): String {
        return when (option) {
            CaseOption.ORIGINAL -> str
            CaseOption.TITLE_CASE -> toTitleCase(str)
            CaseOption.UPPERCASE -> str.uppercase()
            CaseOption.LOWERCASE -> str.lowercase()
            CaseOption.SENTENCE_CASE -> toSentenceCase(str)
        }
    }

    companion object {
        fun toTitleCase(str: String): String {
            return str.split(" ").joinToString(" ") { word ->
                if (word.isBlank()) word
                else word.lowercase().replaceFirstChar { it.uppercase() }
            }
        }

        fun toSentenceCase(str: String): String {
            if (str.isBlank()) return str
            return str.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}

data class ResolvedTrackMetadata(
    val trackNumber: Int,
    val trackNumberFormatted: String,
    val totalTracks: Int,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val year: String,
    val fileName: String,
    val subFolderPath: String
) {
    val fullRelativePath: String
        get() = if (subFolderPath.isNotBlank()) "$subFolderPath/$fileName" else fileName

    val relativeSubPath: String
        get() = fullRelativePath
}

enum class CaseOption(val label: String) {
    ORIGINAL("As is"),
    TITLE_CASE("Title Case"),
    SENTENCE_CASE("Sentence case"),
    UPPERCASE("UPPERCASE"),
    LOWERCASE("lowercase");

    val displayName: String get() = label
}
