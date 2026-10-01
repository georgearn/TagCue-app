package com.example.audio

import java.io.File
import java.io.RandomAccessFile

/** Picks a format-specific writer by looking at the file's first bytes. */
object TagFileWriter {

    enum class Kind { FLAC, MP3, OGG, WAV, MP4 }

    fun detect(f: File, fileName: String): Kind? {
        val head = RandomAccessFile(f, "r").use { it.readFullyOrNull(minOf(12L, it.length()).toInt()) } ?: return null
        fun s(o: Int, n: Int) = if (head.size >= o + n) String(head, o, n, Charsets.ISO_8859_1) else ""
        return when {
            s(0, 4) == "fLaC" -> Kind.FLAC
            s(0, 4) == "OggS" -> Kind.OGG
            s(0, 4) == "RIFF" && s(8, 4) == "WAVE" -> Kind.WAV
            s(4, 4) == "ftyp" -> Kind.MP4
            s(0, 3) == "ID3" -> Kind.MP3
            fileName.endsWith(".mp3", true) -> Kind.MP3
            else -> null
        }
    }

    /** Writes the edited copy of [src] to [dst]. Throws if the format cannot be written. */
    fun write(src: File, dst: File, fileName: String, e: TagEdit) {
        when (detect(src, fileName)) {
            Kind.FLAC -> FlacTagWriter.write(src, dst, e)
            Kind.MP3 -> Id3TagWriter.write(src, dst, e)
            Kind.OGG -> OggTagWriter.write(src, dst, e)
            Kind.WAV -> WavTagWriter.write(src, dst, e)
            Kind.MP4 -> Mp4TagWriter.write(src, dst, e)
            null -> throw IllegalArgumentException("Tag writing is not supported for this file type")
        }
    }
}
