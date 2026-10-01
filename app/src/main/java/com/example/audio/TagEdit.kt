package com.example.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * The fields TagCue edits. A blank string (or null number) removes that field from the file;
 * every other field already in the file is left alone. Pictures: [newPicture] replaces the
 * cover, [removePicture] deletes it, and with neither set the existing art is kept.
 */
class TagEdit(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val trackNumber: Int?,
    val totalTracks: Int?,
    val discNumber: Int?,
    val year: String,
    val genre: String,
    val newPicture: ByteArray? = null,
    val removePicture: Boolean = false
) {
    val pictureChanged: Boolean get() = newPicture != null || removePicture

    /** "3" or "3/12". */
    val trackText: String
        get() = when {
            trackNumber == null -> ""
            totalTracks != null -> "$trackNumber/$totalTracks"
            else -> trackNumber.toString()
        }

    val pictureMime: String get() = sniffImageMime(newPicture)
}

internal fun sniffImageMime(b: ByteArray?): String {
    if (b != null && b.size > 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte()) return "image/png"
    return "image/jpeg"
}

internal fun be32(b: ByteArray, o: Int): Int =
    ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
        ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

internal fun le32(b: ByteArray, o: Int): Int =
    (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
        ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

internal fun putBe32(b: ByteArray, o: Int, v: Int) {
    b[o] = (v ushr 24).toByte(); b[o + 1] = (v ushr 16).toByte(); b[o + 2] = (v ushr 8).toByte(); b[o + 3] = v.toByte()
}

internal fun RandomAccessFile.readFullyOrNull(n: Int): ByteArray? {
    val b = ByteArray(n)
    var r = 0
    while (r < n) {
        val k = read(b, r, n - r)
        if (k < 0) return null
        r += k
    }
    return b
}

/** Copies [from, end of file) of [src] into [out]'s current position without loading it. */
internal fun copyTail(src: RandomAccessFile, from: Long, out: java.io.FileOutputStream) {
    copyRange(src, from, src.length() - from, out)
}

internal fun copyRange(src: RandomAccessFile, from: Long, count: Long, out: java.io.FileOutputStream) {
    var pos = from
    var left = count
    val ch = src.channel
    while (left > 0) {
        val n = ch.transferTo(pos, left, out.channel)
        if (n <= 0) break
        pos += n; left -= n
    }
    check(left == 0L) { "Short copy: $left bytes missing" }
}

internal object VorbisComments {
    class Parsed(val vendor: String, val entries: List<String>)

    val MANAGED = setOf(
        "TITLE", "ARTIST", "ALBUM", "ALBUMARTIST", "TRACKNUMBER", "TRACKTOTAL", "TOTALTRACKS",
        "DATE", "YEAR", "GENRE", "DISCNUMBER"
    )

    fun parse(b: ByteArray, off: Int): Parsed {
        var p = off
        val vl = le32(b, p); p += 4
        val vendor = String(b, p, vl, Charsets.UTF_8); p += vl
        val n = le32(b, p); p += 4
        val list = ArrayList<String>()
        for (i in 0 until n) {
            if (p + 4 > b.size) break
            val l = le32(b, p); p += 4
            if (l < 0 || p + l > b.size) break
            list.add(String(b, p, l, Charsets.UTF_8)); p += l
        }
        return Parsed(vendor, list)
    }

    fun build(vendor: String, entries: List<String>): ByteArray {
        val o = java.io.ByteArrayOutputStream()
        fun le(v: Int) { o.write(v and 0xFF); o.write((v ushr 8) and 0xFF); o.write((v ushr 16) and 0xFF); o.write((v ushr 24) and 0xFF) }
        val vb = vendor.toByteArray(Charsets.UTF_8)
        le(vb.size); o.write(vb)
        le(entries.size)
        for (e in entries) { val eb = e.toByteArray(Charsets.UTF_8); le(eb.size); o.write(eb) }
        return o.toByteArray()
    }

    /** Existing entries minus the ones TagCue manages, preceded by the new managed values. */
    fun merge(existing: List<String>, e: TagEdit, dropKeys: Set<String> = emptySet()): List<String> {
        val out = ArrayList<String>()
        fun add(k: String, v: String) { if (v.isNotBlank()) out.add("$k=$v") }
        add("TITLE", e.title)
        add("ARTIST", e.artist)
        add("ALBUM", e.album)
        add("ALBUMARTIST", e.albumArtist)
        add("TRACKNUMBER", e.trackNumber?.toString().orEmpty())
        add("TRACKTOTAL", e.totalTracks?.toString().orEmpty())
        add("DISCNUMBER", e.discNumber?.toString().orEmpty())
        add("DATE", e.year)
        add("GENRE", e.genre)
        for (line in existing) {
            val key = line.substringBefore('=').uppercase()
            if (key in MANAGED || key in dropKeys) continue
            out.add(line)
        }
        return out
    }
}

internal object FlacTagWriter {
    private const val VENDOR = "TagCue"

    fun write(src: File, dst: File, e: TagEdit) {
        RandomAccessFile(src, "r").use { raf ->
            val magic = raf.readFullyOrNull(4)
            require(magic != null && String(magic, Charsets.ISO_8859_1) == "fLaC") { "Not a FLAC file" }

            var streamInfo: ByteArray? = null
            var vendor = VENDOR
            var comments: List<String> = emptyList()
            val pictures = ArrayList<ByteArray>()
            val others = ArrayList<Pair<Int, ByteArray>>()
            while (true) {
                val h = raf.readFullyOrNull(4) ?: throw IllegalStateException("Truncated FLAC metadata")
                val last = (h[0].toInt() and 0x80) != 0
                val type = h[0].toInt() and 0x7F
                val len = ((h[1].toInt() and 0xFF) shl 16) or ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF)
                if (type == 1) { // PADDING: skip
                    raf.seek(raf.filePointer + len)
                } else {
                    val data = raf.readFullyOrNull(len) ?: throw IllegalStateException("Truncated FLAC block")
                    when (type) {
                        0 -> streamInfo = data
                        4 -> runCatching { VorbisComments.parse(data, 0) }.getOrNull()?.let { vendor = it.vendor; comments = it.entries }
                        6 -> pictures.add(data)
                        else -> others.add(type to data)
                    }
                }
                if (last) break
            }
            val audioStart = raf.filePointer
            val si = streamInfo ?: throw IllegalStateException("STREAMINFO block not found")

            val blocks = ArrayList<Pair<Int, ByteArray>>()
            blocks.add(0 to si)
            blocks.add(4 to VorbisComments.build(vendor, VorbisComments.merge(comments, e)))
            blocks.addAll(others)
            when {
                e.newPicture != null -> blocks.add(6 to buildPicture(e.newPicture, e.pictureMime))
                e.removePicture -> {}
                else -> pictures.forEach { blocks.add(6 to it) }
            }
            for ((_, d) in blocks) require(d.size < (1 shl 24)) { "Metadata block too large for FLAC (16 MB limit)" }

            java.io.FileOutputStream(dst).use { fos ->
                val hdr = java.io.ByteArrayOutputStream()
                hdr.write("fLaC".toByteArray(Charsets.ISO_8859_1))
                blocks.forEachIndexed { i, (type, data) ->
                    hdr.write((if (i == blocks.size - 1) 0x80 else 0) or type)
                    hdr.write(data.size ushr 16); hdr.write(data.size ushr 8); hdr.write(data.size)
                    hdr.write(data)
                }
                fos.write(hdr.toByteArray())
                copyTail(raf, audioStart, fos)
            }
        }
    }

    fun buildPicture(bytes: ByteArray, mime: String, type: Int = 3): ByteArray {
        val bo = java.io.ByteArrayOutputStream()
        val d = java.io.DataOutputStream(bo)
        d.writeInt(type)
        val m = mime.toByteArray(Charsets.ISO_8859_1)
        d.writeInt(m.size); d.write(m)
        d.writeInt(0) // description
        d.writeInt(0); d.writeInt(0); d.writeInt(24); d.writeInt(0)
        d.writeInt(bytes.size); d.write(bytes)
        d.flush()
        return bo.toByteArray()
    }
}
