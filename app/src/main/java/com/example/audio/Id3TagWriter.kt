package com.example.audio

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Rewrites the ID3v2 tag at the start of an MP3. Frames TagCue does not manage (lyrics, ratings,
 * replay gain, extra pictures and so on) are copied through untouched. The audio is copied by
 * channel transfer, never loaded into memory.
 */
internal object Id3TagWriter {

    private class Frame(val id: String, val flags: ByteArray, val body: ByteArray)

    // ID3v2.2 three-letter ids that have a v2.3 equivalent with the same body layout.
    private val V22_MAP = mapOf(
        "TCM" to "TCOM", "TEN" to "TENC", "TXX" to "TXXX", "TBP" to "TBPM", "TCR" to "TCOP",
        "TPB" to "TPUB", "TSS" to "TSSE", "TLA" to "TLAN", "TKE" to "TKEY", "TLE" to "TLEN",
        "TT1" to "TIT1", "TT3" to "TIT3", "TP3" to "TPE3", "TP4" to "TPE4", "TOA" to "TOPE",
        "TOT" to "TOAL", "TOL" to "TOLY", "TRC" to "TSRC", "COM" to "COMM",
        "ULT" to "USLT", "WXX" to "WXXX", "WAR" to "WOAR", "WCM" to "WCOM", "WCP" to "WCOP",
        "WAF" to "WOAF", "WAS" to "WOAS", "WPB" to "WPUB", "UFI" to "UFID"
    )
    private val TEXT_MANAGED = setOf("TIT2", "TPE1", "TALB", "TPE2", "TRCK", "TPOS", "TYER", "TDRC", "TCON")

    private fun synchsafe(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or
            ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun unsync(b: ByteArray): ByteArray {
        val o = ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            o.write(b[i].toInt())
            if (b[i] == 0xFF.toByte() && i + 1 < b.size && b[i + 1] == 0.toByte()) i++
            i++
        }
        return o.toByteArray()
    }

    fun write(src: File, dst: File, e: TagEdit) {
        RandomAccessFile(src, "r").use { raf ->
            var major = 3
            var audioStart = 0L
            var frames: List<Frame> = emptyList()

            val head = raf.readFullyOrNull(10)
            if (head != null && head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte() &&
                head[3].toInt() in 2..4
            ) {
                major = head[3].toInt()
                val flags = head[5].toInt() and 0xFF
                val size = synchsafe(head, 6)
                audioStart = 10L + size + (if (major == 4 && (flags and 0x10) != 0) 10 else 0)
                var body = raf.readFullyOrNull(size) ?: throw IllegalStateException("Truncated ID3 tag")
                if ((flags and 0x80) != 0) body = unsync(body)
                var off = 0
                if ((flags and 0x40) != 0 && major >= 3 && body.size >= 4) {
                    off = if (major == 3) be32(body, 0) + 4 else synchsafe(body, 0)
                }
                frames = parseFrames(body, off, major)
                if (major == 2) major = 3
            }

            val outMajor = if (major == 4) 4 else 3
            val keep = frames.filter { f ->
                f.id !in TEXT_MANAGED && !(e.pictureChanged && f.id == "APIC")
            }

            val fb = ByteArrayOutputStream()
            fun text(id: String, v: String) { if (v.isNotBlank()) writeFrame(fb, outMajor, id, textBody(outMajor, v)) }
            text("TIT2", e.title)
            text("TPE1", e.artist)
            text("TALB", e.album)
            text("TPE2", e.albumArtist)
            text("TRCK", e.trackText)
            text("TPOS", e.discNumber?.toString().orEmpty())
            if (outMajor == 4) text("TDRC", e.year)
            else text("TYER", e.year.trim().take(4))
            text("TCON", e.genre)
            if (e.newPicture != null) {
                val b = ByteArrayOutputStream()
                b.write(0)
                b.write(e.pictureMime.toByteArray(Charsets.ISO_8859_1)); b.write(0)
                b.write(3); b.write(0)
                b.write(e.newPicture)
                writeFrame(fb, outMajor, "APIC", b.toByteArray())
            }
            for (f in keep) {
                fb.write(f.id.toByteArray(Charsets.ISO_8859_1))
                val n = f.body.size
                writeSize(fb, outMajor, n)
                fb.write(f.flags)
                fb.write(f.body)
            }
            val frameBytes = fb.toByteArray()

            FileOutputStream(dst).use { fos ->
                val h = ByteArrayOutputStream()
                h.write("ID3".toByteArray())
                h.write(outMajor); h.write(0); h.write(0)
                val n = frameBytes.size
                h.write((n shr 21) and 0x7F); h.write((n shr 14) and 0x7F); h.write((n shr 7) and 0x7F); h.write(n and 0x7F)
                fos.write(h.toByteArray())
                fos.write(frameBytes)
                copyTail(raf, audioStart, fos)
            }
        }
    }

    private fun parseFrames(body: ByteArray, start: Int, major: Int): List<Frame> {
        val out = ArrayList<Frame>()
        var p = start
        if (major == 2) {
            while (p + 6 <= body.size && body[p] != 0.toByte()) {
                val id3 = String(body, p, 3, Charsets.ISO_8859_1)
                val n = ((body[p + 3].toInt() and 0xFF) shl 16) or ((body[p + 4].toInt() and 0xFF) shl 8) or (body[p + 5].toInt() and 0xFF)
                p += 6
                if (n < 0 || p + n > body.size) break
                val mapped = when (id3) {
                    "TT2" -> "TIT2"; "TP1" -> "TPE1"; "TAL" -> "TALB"; "TP2" -> "TPE2"; "TRK" -> "TRCK"
                    "TPA" -> "TPOS"; "TYE" -> "TYER"; "TCO" -> "TCON"; "PIC" -> null
                    else -> V22_MAP[id3]
                }
                if (mapped != null) out.add(Frame(mapped, byteArrayOf(0, 0), body.copyOfRange(p, p + n)))
                p += n
            }
            return out
        }
        while (p + 10 <= body.size && body[p] != 0.toByte()) {
            val id = String(body, p, 4, Charsets.ISO_8859_1)
            val n = if (major == 4) synchsafe(body, p + 4) else be32(body, p + 4)
            val flags = byteArrayOf(body[p + 8], body[p + 9])
            p += 10
            if (n < 0 || p + n > body.size) break
            out.add(Frame(id, flags, body.copyOfRange(p, p + n)))
            p += n
        }
        return out
    }

    private fun writeSize(o: ByteArrayOutputStream, major: Int, n: Int) {
        if (major == 4) {
            o.write((n shr 21) and 0x7F); o.write((n shr 14) and 0x7F); o.write((n shr 7) and 0x7F); o.write(n and 0x7F)
        } else {
            o.write(n ushr 24); o.write(n ushr 16); o.write(n ushr 8); o.write(n)
        }
    }

    private fun writeFrame(o: ByteArrayOutputStream, major: Int, id: String, body: ByteArray) {
        o.write(id.toByteArray(Charsets.ISO_8859_1))
        writeSize(o, major, body.size)
        o.write(0); o.write(0)
        o.write(body)
    }

    /** v2.4 allows UTF-8. v2.3 only allows ISO-8859-1 and UTF-16, so use whichever fits. */
    private fun textBody(major: Int, text: String): ByteArray {
        val o = ByteArrayOutputStream()
        if (major == 4) {
            o.write(3); o.write(text.toByteArray(Charsets.UTF_8))
        } else if (text.all { it.code <= 0xFF }) {
            o.write(0); o.write(text.toByteArray(Charsets.ISO_8859_1))
        } else {
            o.write(1); o.write(0xFF); o.write(0xFE); o.write(text.toByteArray(Charsets.UTF_16LE))
        }
        return o.toByteArray()
    }
}
