package com.example.model

import android.net.Uri

data class AudioMetadata(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val trackNumber: Int? = null,
    val totalTracks: Int? = null,
    val discNumber: Int? = null,
    val year: String = "",
    val genre: String = "",
    val durationMs: Long = 0L,
    val bitRate: Int = 0,
    val sampleRate: Int = 0,
    val hasEmbeddedArt: Boolean = false,
    val albumArtBytes: ByteArray? = null,
    val albumArtMime: String = "image/jpeg",
    val folderUri: Uri? = null,
    val folderName: String = ""
) {
    val effectiveAlbum: String
        get() = album.ifBlank { "Unknown Album" }

    val effectiveArtist: String
        get() = artist.ifBlank { albumArtist }.ifBlank { "Unknown Artist" }

    val effectiveTitle: String
        get() = title.ifBlank { fileName }

    val format: String
        get() = when {
            fileName.endsWith(".flac", ignoreCase = true) || mimeType.contains("flac") -> "FLAC"
            fileName.endsWith(".mp3", ignoreCase = true) || mimeType.contains("mp3") || mimeType.contains("mpeg") -> "MP3"
            fileName.endsWith(".m4a", ignoreCase = true) || fileName.endsWith(".aac", ignoreCase = true) || mimeType.contains("mp4") || mimeType.contains("aac") -> "M4A"
            fileName.endsWith(".ogg", ignoreCase = true) || fileName.endsWith(".opus", ignoreCase = true) || mimeType.contains("ogg") -> "OGG"
            fileName.endsWith(".wav", ignoreCase = true) || mimeType.contains("wav") -> "WAV"
            else -> "AUDIO"
        }

    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "--:--"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioMetadata
        return uri == other.uri &&
                fileName == other.fileName &&
                title == other.title &&
                artist == other.artist &&
                album == other.album &&
                albumArtist == other.albumArtist &&
                trackNumber == other.trackNumber &&
                totalTracks == other.totalTracks &&
                discNumber == other.discNumber &&
                year == other.year &&
                genre == other.genre &&
                hasEmbeddedArt == other.hasEmbeddedArt &&
                (albumArtBytes contentEquals other.albumArtBytes)
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + (albumArtBytes?.contentHashCode() ?: 0)
        return result
    }
}

enum class ProblemFilter(val displayName: String) {
    ALL("All Files"),
    MISSING_COVER("Missing Cover"),
    MISSING_TRACK_NUM("Missing Track #"),
    INCONSISTENT_ALBUM_ARTIST("Inconsistent Album Artist"),
    MISSING_YEAR("Missing Year")
}

data class EditableTrackState(
    val original: AudioMetadata,
    val pending: AudioMetadata,
    val isSelectedForApply: Boolean = false,
    val newAlbumArtBytes: ByteArray? = null,
    val removeAlbumArt: Boolean = false,
    val pendingNewFileName: String? = null
) {
    val isModified: Boolean
        get() = original.title != pending.title ||
                original.artist != pending.artist ||
                original.album != pending.album ||
                original.albumArtist != pending.albumArtist ||
                original.trackNumber != pending.trackNumber ||
                original.totalTracks != pending.totalTracks ||
                original.discNumber != pending.discNumber ||
                original.year != pending.year ||
                original.genre != pending.genre ||
                newAlbumArtBytes != null ||
                removeAlbumArt ||
                (pendingNewFileName != null && pendingNewFileName != original.fileName)

    val modifiedFieldCount: Int
        get() {
            var count = 0
            if (original.title != pending.title) count++
            if (original.artist != pending.artist) count++
            if (original.album != pending.album) count++
            if (original.albumArtist != pending.albumArtist) count++
            if (original.trackNumber != pending.trackNumber) count++
            if (original.totalTracks != pending.totalTracks) count++
            if (original.discNumber != pending.discNumber) count++
            if (original.year != pending.year) count++
            if (original.genre != pending.genre) count++
            if (newAlbumArtBytes != null || removeAlbumArt) count++
            if (pendingNewFileName != null && pendingNewFileName != original.fileName) count++
            return count
        }

    fun hasProblem(filter: ProblemFilter, allAlbumTracks: List<EditableTrackState> = emptyList()): Boolean {
        return when (filter) {
            ProblemFilter.ALL -> true
            ProblemFilter.MISSING_COVER -> !pending.hasEmbeddedArt && newAlbumArtBytes == null
            ProblemFilter.MISSING_TRACK_NUM -> pending.trackNumber == null || pending.trackNumber <= 0
            ProblemFilter.MISSING_YEAR -> pending.year.isBlank()
            ProblemFilter.INCONSISTENT_ALBUM_ARTIST -> {
                if (allAlbumTracks.size <= 1) false
                else {
                    val artistsInAlbum = allAlbumTracks.map { it.pending.albumArtist.ifBlank { it.pending.artist } }.distinct()
                    artistsInAlbum.size > 1
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EditableTrackState
        return original == other.original &&
                pending == other.pending &&
                isSelectedForApply == other.isSelectedForApply &&
                removeAlbumArt == other.removeAlbumArt &&
                pendingNewFileName == other.pendingNewFileName &&
                (newAlbumArtBytes contentEquals other.newAlbumArtBytes)
    }

    override fun hashCode(): Int {
        var result = original.hashCode()
        result = 31 * result + pending.hashCode()
        result = 31 * result + isSelectedForApply.hashCode()
        result = 31 * result + removeAlbumArt.hashCode()
        result = 31 * result + (newAlbumArtBytes?.contentHashCode() ?: 0)
        return result
    }
}

data class AlbumGroup(
    val albumKey: String,
    val albumTitle: String,
    val albumArtist: String,
    val coverArtBytes: ByteArray?,
    val tracks: List<EditableTrackState>
) {
    val totalTracks: Int get() = tracks.size
    val selectedTracks: Int get() = tracks.count { it.isSelectedForApply }
    val isFullySelected: Boolean get() = tracks.isNotEmpty() && tracks.all { it.isSelectedForApply }
    val isPartiallySelected: Boolean get() = tracks.any { it.isSelectedForApply } && !isFullySelected
    val isModified: Boolean get() = tracks.any { it.isModified }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AlbumGroup
        return albumKey == other.albumKey &&
                albumTitle == other.albumTitle &&
                albumArtist == other.albumArtist &&
                tracks == other.tracks &&
                (coverArtBytes contentEquals other.coverArtBytes)
    }

    override fun hashCode(): Int {
        var result = albumKey.hashCode()
        result = 31 * result + albumTitle.hashCode()
        result = 31 * result + albumArtist.hashCode()
        result = 31 * result + (coverArtBytes?.contentHashCode() ?: 0)
        return result
    }
}

sealed class FieldAction<out T> {
    object Keep : FieldAction<Nothing>()
    data class SetValue<T>(val value: T) : FieldAction<T>()
    object Clear : FieldAction<Nothing>()
}

enum class CoverArtAction {
    KEEP,
    SET_NEW,
    REMOVE
}

data class TagBackup(
    val timestamp: Long = System.currentTimeMillis(),
    val snapshot: Map<Uri, AudioMetadata>,
    /** old file uri -> uri after "rename files from tags", so undo can rename back */
    val renames: Map<Uri, Uri> = emptyMap()
)
