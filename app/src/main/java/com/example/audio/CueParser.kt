package com.example.audio

import android.net.Uri
import com.example.model.CueEncoding
import com.example.model.CueIndex
import com.example.model.CueSheet
import com.example.model.CueTrack
import java.io.InputStream
import java.nio.charset.Charset

object CueParser {

    fun parse(inputStream: InputStream, encoding: CueEncoding = CueEncoding.AUTO, uri: Uri? = null): CueSheet {
        val bytes = inputStream.readBytes()
        return parseBytes(bytes, encoding, uri)
    }

    fun parseBytes(bytes: ByteArray, encoding: CueEncoding = CueEncoding.AUTO, uri: Uri? = null): CueSheet {
        val text = decodeBytes(bytes, encoding)
        return parseText(text, bytes, encoding, uri)
    }

    fun decodeBytes(bytes: ByteArray, encoding: CueEncoding): String {
        return when (encoding) {
            CueEncoding.AUTO -> detectCharsetAndDecode(bytes)
            CueEncoding.UTF_8 -> String(bytes, Charsets.UTF_8)
            CueEncoding.WINDOWS_1251 -> try {
                String(bytes, Charset.forName("windows-1251"))
            } catch (_: Exception) {
                String(bytes, Charsets.UTF_8)
            }
            CueEncoding.LATIN_1 -> String(bytes, Charset.forName("ISO-8859-1"))
            CueEncoding.WINDOWS_1252 -> try {
                String(bytes, Charset.forName("windows-1252"))
            } catch (_: Exception) {
                String(bytes, Charset.forName("ISO-8859-1"))
            }
        }
    }

    private fun detectCharsetAndDecode(bytes: ByteArray): String {
        // UTF-8 BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        // UTF-16 BOM
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }

        // Try strict UTF-8
        try {
            val decoder = Charsets.UTF_8.newDecoder()
            decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            val decoded = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            return decoded
        } catch (_: Exception) {
            // Check for high-frequency Windows-1251 Cyrillic bytes (0xC0..0xFF)
            var cyrillicScore = 0
            for (b in bytes) {
                val unsigned = b.toInt() and 0xFF
                if (unsigned in 0xC0..0xFF) {
                    cyrillicScore++
                }
            }
            if (cyrillicScore > 10) {
                try {
                    return String(bytes, Charset.forName("windows-1251"))
                } catch (_: Exception) {}
            }

            // Fallback to windows-1252 or Latin-1
            return try {
                String(bytes, Charset.forName("windows-1252"))
            } catch (_: Exception) {
                String(bytes, Charset.forName("ISO-8859-1"))
            }
        }
    }

    fun parseText(
        cueContent: String,
        rawBytes: ByteArray = cueContent.toByteArray(Charsets.UTF_8),
        encoding: CueEncoding = CueEncoding.AUTO,
        uri: Uri? = null
    ): CueSheet {
        var globalTitle = ""
        var globalPerformer = ""
        var globalDate = ""
        var globalGenre = ""
        var audioFileName = ""

        val rawTracks = mutableListOf<CueTrack>()
        var currentTrackNumber: Int? = null
        var currentTrackTitle = ""
        var currentTrackPerformer = ""
        var currentTrackGenre = ""
        var currentTrackDate = ""
        val currentIndices = mutableListOf<CueIndex>()

        fun finalizeCurrentTrack() {
            val num = currentTrackNumber ?: return
            val track = CueTrack(
                number = num,
                title = currentTrackTitle.ifBlank { "Track %02d".format(num) },
                performer = currentTrackPerformer.ifBlank { globalPerformer },
                album = globalTitle,
                genre = currentTrackGenre.ifBlank { globalGenre },
                date = currentTrackDate.ifBlank { globalDate },
                durationMs = 0L,
                indices = currentIndices.toList()
            )
            rawTracks.add(track)
            currentTrackTitle = ""
            currentTrackPerformer = ""
            currentTrackGenre = ""
            currentTrackDate = ""
            currentIndices.clear()
            currentTrackNumber = null
        }

        val lines = cueContent.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            val upper = line.uppercase()

            when {
                upper.startsWith("REM DATE") || upper.startsWith("REM YEAR") -> {
                    val dateVal = extractValue(line).trim()
                    if (currentTrackNumber == null) globalDate = dateVal else currentTrackDate = dateVal
                }
                upper.startsWith("REM GENRE") -> {
                    val genreVal = extractValue(line).trim()
                    if (currentTrackNumber == null) globalGenre = genreVal else currentTrackGenre = genreVal
                }
                upper.startsWith("PERFORMER") -> {
                    val performer = extractQuotedOrRest(line, "PERFORMER")
                    if (currentTrackNumber == null) {
                        globalPerformer = performer
                    } else {
                        currentTrackPerformer = performer
                    }
                }
                upper.startsWith("TITLE") -> {
                    val title = extractQuotedOrRest(line, "TITLE")
                    if (currentTrackNumber == null) {
                        globalTitle = title
                    } else {
                        currentTrackTitle = title
                    }
                }
                upper.startsWith("FILE") -> {
                    audioFileName = extractFileReference(line)
                }
                upper.startsWith("TRACK") -> {
                    finalizeCurrentTrack()
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        currentTrackNumber = parts[1].toIntOrNull() ?: (rawTracks.size + 1)
                    }
                }
                upper.startsWith("INDEX") -> {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 3) {
                        val indexNum = parts[1].toIntOrNull() ?: 1
                        val timeStr = parts[2]
                        val timeParts = timeStr.split(":")
                        if (timeParts.size == 3) {
                            val min = timeParts[0].toIntOrNull() ?: 0
                            val sec = timeParts[1].toIntOrNull() ?: 0
                            val frm = timeParts[2].toIntOrNull() ?: 0
                            currentIndices.add(CueIndex(indexNum, min, sec, frm))
                        }
                    }
                }
            }
        }

        finalizeCurrentTrack()

        // Calculate durations between consecutive track indices (1 second = 75 frames)
        val tracksWithDurations = rawTracks.mapIndexed { idx, track ->
            val nextTrack = rawTracks.getOrNull(idx + 1)
            val startTimeMs = track.startIndex?.totalMilliseconds ?: 0L
            val endTimeMs = nextTrack?.startIndex?.totalMilliseconds ?: 0L
            val dur = if (endTimeMs > startTimeMs) endTimeMs - startTimeMs else 0L
            track.copy(durationMs = dur)
        }

        return CueSheet(
            uri = uri,
            rawBytes = rawBytes,
            rawText = cueContent,
            encoding = encoding,
            title = globalTitle,
            performer = globalPerformer,
            date = globalDate,
            genre = globalGenre,
            audioFileName = audioFileName,
            tracks = tracksWithDurations
        )
    }

    private fun extractQuotedOrRest(line: String, keyword: String): String {
        val trimmed = line.trim()
        val afterKeyword = if (trimmed.length > keyword.length) {
            trimmed.substring(keyword.length).trim()
        } else ""

        if (afterKeyword.startsWith("\"") && afterKeyword.endsWith("\"") && afterKeyword.length >= 2) {
            return afterKeyword.substring(1, afterKeyword.length - 1)
        }
        val firstQuote = afterKeyword.indexOf('\"')
        val lastQuote = afterKeyword.lastIndexOf('\"')
        if (firstQuote != -1 && lastQuote > firstQuote) {
            return afterKeyword.substring(firstQuote + 1, lastQuote)
        }
        return afterKeyword
    }

    private fun extractValue(line: String): String {
        val parts = line.split("\\s+".toRegex(), 3)
        return if (parts.size >= 3) {
            parts[2].removeSurrounding("\"")
        } else ""
    }

    private fun extractFileReference(line: String): String {
        val firstQuote = line.indexOf('\"')
        val lastQuote = line.lastIndexOf('\"')
        if (firstQuote != -1 && lastQuote > firstQuote) {
            return line.substring(firstQuote + 1, lastQuote)
        }
        val parts = line.split("\\s+".toRegex())
        return if (parts.size >= 2) parts[1] else ""
    }
}
