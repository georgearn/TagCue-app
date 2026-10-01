package com.example.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.example.model.AudioMetadata
import com.example.model.EditableTrackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

object AudioTagManager {

    suspend fun readMetadata(context: Context, uri: Uri): AudioMetadata? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var fileName = "unknown_track"
        var sizeBytes = 0L
        var mimeType = "audio/*"

        try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {}

        mimeType = resolver.getType(uri) ?: when {
            fileName.endsWith(".flac", ignoreCase = true) -> "audio/flac"
            fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            fileName.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
            fileName.endsWith(".ogg", ignoreCase = true) || fileName.endsWith(".opus", ignoreCase = true) -> "audio/ogg"
            fileName.endsWith(".wav", ignoreCase = true) -> "audio/wav"
            else -> "audio/*"
        }

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)

            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim().orEmpty()
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim().orEmpty()
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim().orEmpty()
            val albumArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim().orEmpty()
            val genre = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.trim().orEmpty()
            val date = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.trim().orEmpty()
            val year = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.trim().orEmpty().ifBlank { date }

            val rawTrackNum = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            var trackNum: Int? = null
            var totalTracks: Int? = null
            if (!rawTrackNum.isNullOrBlank()) {
                if (rawTrackNum.contains("/")) {
                    val parts = rawTrackNum.split("/")
                    trackNum = parts[0].trim().toIntOrNull()
                    totalTracks = parts.getOrNull(1)?.trim()?.toIntOrNull()
                } else {
                    trackNum = rawTrackNum.trim().toIntOrNull()
                }
            }

            val discNum = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull()

            val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val bitRateStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val bitRate = bitRateStr?.toIntOrNull() ?: 0

            val sampleRateStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            val sampleRate = sampleRateStr?.toIntOrNull() ?: 44100

            val embeddedPicture = mmr.embeddedPicture

            // If title is blank, fallback to filename without extension
            val finalTitle = title.ifBlank {
                fileName.substringBeforeLast(".")
            }

            val base = AudioMetadata(
                uri = uri,
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                title = finalTitle,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                trackNumber = trackNum,
                totalTracks = totalTracks,
                discNumber = discNum,
                year = year,
                genre = genre,
                durationMs = durationMs,
                bitRate = bitRate,
                sampleRate = sampleRate,
                hasEmbeddedArt = embeddedPicture != null,
                albumArtBytes = embeddedPicture
            )
            return@withContext if (fileName.endsWith(".wav", ignoreCase = true)) withWavInfo(context, base) else base
        } catch (_: Exception) {
            // Fallback basic metadata if MMR cannot parse container
            val fallback = AudioMetadata(
                uri = uri,
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                title = fileName.substringBeforeLast(".")
            )
            return@withContext if (fileName.endsWith(".wav", ignoreCase = true)) withWavInfo(context, fallback) else fallback
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {}
        }
    }

    /** Android's retriever ignores WAV LIST/INFO tags, so read them directly. */
    private fun withWavInfo(context: Context, m: AudioMetadata): AudioMetadata {
        val info = runCatching {
            context.contentResolver.openFileDescriptor(m.uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { WavTagWriter.readInfo(it.channel) }
            }
        }.getOrNull().orEmpty()
        if (info.isEmpty()) return m
        return m.copy(
            title = info["INAM"].orEmpty().ifBlank { m.title },
            artist = info["IART"].orEmpty().ifBlank { m.artist },
            album = info["IPRD"].orEmpty().ifBlank { m.album },
            year = info["ICRD"].orEmpty().ifBlank { m.year },
            genre = info["IGNR"].orEmpty().ifBlank { m.genre },
            trackNumber = m.trackNumber ?: info["ITRK"]?.substringBefore('/')?.trim()?.toIntOrNull()
        )
    }

    fun listSubFolders(context: Context, treeUri: Uri): List<Pair<Uri, String>> {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val children = try {
            rootDoc.listFiles()
        } catch (_: Exception) {
            emptyArray()
        }
        return children
            .filter { it.isDirectory }
            .mapNotNull { dir ->
                val name = dir.name ?: return@mapNotNull null
                dir.uri to name
            }
            .sortedBy { it.second.lowercase() }
    }

    suspend fun readFolderAudioFiles(
        context: Context,
        treeUri: Uri,
        onProgress: (loaded: Int, total: Int, currentName: String) -> Unit
    ): List<AudioMetadata> = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val audioExtensions = setOf("flac", "mp3", "m4a", "aac", "ogg", "opus", "wav")

        val audioFiles = mutableListOf<DocumentFile>()

        fun scanRecursively(dir: DocumentFile) {
            val children = try {
                dir.listFiles()
            } catch (_: Exception) {
                emptyArray()
            }
            for (child in children) {
                if (child.isDirectory) {
                    scanRecursively(child)
                } else if (child.isFile) {
                    val name = child.name ?: ""
                    if (audioExtensions.any { name.endsWith(".$it", ignoreCase = true) }) {
                        audioFiles.add(child)
                    }
                }
            }
        }

        scanRecursively(rootDoc)
        val sortedFiles = audioFiles.sortedBy { it.name?.lowercase() ?: "" }

        val list = mutableListOf<AudioMetadata>()
        for (i in sortedFiles.indices) {
            val file = sortedFiles[i]
            onProgress(i + 1, sortedFiles.size, file.name ?: "")
            val meta = readMetadata(context, file.uri)
            if (meta != null) {
                list.add(meta)
            }
        }
        return@withContext list
    }

    /**
     * Writes the pending tags into the file behind [trackState].original.uri.
     *
     * The document is copied to a cache file, the edited copy is built in a second cache file, and
     * the result is streamed back. Nothing is held in memory beyond small buffers, and the first
     * copy is what a failed write-back is rolled back from (SAF has no atomic replace).
     */
    suspend fun saveTrackMetadata(
        context: Context,
        trackState: EditableTrackState
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val original = trackState.original
        val pending = trackState.pending
        val stamp = "${System.nanoTime()}"
        val inFile = File(context.cacheDir, "tag_in_$stamp")
        val outFile = File(context.cacheDir, "tag_out_$stamp")
        try {
            resolver.openInputStream(original.uri)?.use { input ->
                inFile.outputStream().use { input.copyTo(it, 256 * 1024) }
            } ?: return@withContext Result.failure(Exception("Cannot read ${original.fileName}"))

            val edit = TagEdit(
                title = pending.title,
                artist = pending.artist,
                album = pending.album,
                albumArtist = pending.albumArtist,
                trackNumber = pending.trackNumber,
                totalTracks = pending.totalTracks,
                discNumber = pending.discNumber,
                year = pending.year,
                genre = pending.genre,
                newPicture = trackState.newAlbumArtBytes,
                removePicture = trackState.removeAlbumArt
            )
            TagFileWriter.write(inFile, outFile, original.fileName, edit)

            val expected = outFile.length()
            try {
                resolver.openOutputStream(original.uri, "wt")?.use { out ->
                    outFile.inputStream().use { it.copyTo(out, 256 * 1024) }
                    out.flush()
                } ?: return@withContext Result.failure(Exception("Cannot open file output stream for writing."))

                val written = resolver.openAssetFileDescriptor(original.uri, "r")?.use { it.length } ?: -1L
                if (written >= 0 && written != expected) {
                    throw java.io.IOException("Size mismatch after write ($written of $expected bytes)")
                }
            } catch (writeError: Exception) {
                runCatching {
                    resolver.openOutputStream(original.uri, "wt")?.use { out ->
                        inFile.inputStream().use { it.copyTo(out, 256 * 1024) }
                        out.flush()
                    }
                }
                return@withContext Result.failure(writeError)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            inFile.delete()
            outFile.delete()
        }
    }
}
