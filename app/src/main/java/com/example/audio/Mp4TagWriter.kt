package com.example.audio

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Rewrites the iTunes-style metadata list (moov/udta/meta/ilst) of an M4A/MP4 file. Items TagCue
 * does not manage are preserved. Only the moov atom is held in memory; the rest of the file is
 * copied by channel transfer. When moov sits before the media data and changes size, the chunk
 * offset tables (stco/co64) are shifted so playback still finds the samples.
 */
internal object Mp4TagWriter {

    private class Atom(val type: String, val off: Int, val size: Int, val hdr: Int)

    private val MANAGED = setOf("©nam", "©ART", "©alb", "aART", "©day", "©gen", "gnre", "trkn", "disk")
    private val CONTAINERS = setOf("trak", "mdia", "minf", "stbl")

    private fun atoms(b: ByteArray, from: Int, to: Int): List<Atom> {
        val out = ArrayList<Atom>()
        var p = from
        while (p + 8 <= to) {
            var size = be32(b, p)
            val type = String(b, p + 4, 4, Charsets.ISO_8859_1)
            var hdr = 8
            if (size == 1) { // 64-bit size; only sane values fit in memory here
                size = ((((b[p + 12].toInt() and 0xFF) shl 24) or ((b[p + 13].toInt() and 0xFF) shl 16) or
                    ((b[p + 14].toInt() and 0xFF) shl 8) or (b[p + 15].toInt() and 0xFF)))
                hdr = 16
            } else if (size == 0) size = to - p
            if (size < hdr || p + size > to) break
            out.add(Atom(type, p, size, hdr))
            p += size
        }
        return out
    }

    private fun atom(type: String, vararg parts: ByteArray): ByteArray {
        val n = 8 + parts.sumOf { it.size }
        val o = ByteArrayOutputStream(n)
        o.write(byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte()))
        o.write(type.toByteArray(Charsets.ISO_8859_1))
        parts.forEach { o.write(it) }
        return o.toByteArray()
    }

    private fun item(type: String, flags: Int, payload: ByteArray): ByteArray {
        val head = byteArrayOf(0, (flags ushr 16).toByte(), (flags ushr 8).toByte(), flags.toByte(), 0, 0, 0, 0)
        return atom(type, atom("data", head, payload))
    }

    private fun text(type: String, v: String): ByteArray? =
        if (v.isBlank()) null else item(type, 1, v.toByteArray(Charsets.UTF_8))

    private fun u16(v: Int) = byteArrayOf((v ushr 8).toByte(), v.toByte())

    fun write(src: File, dst: File, e: TagEdit) {
        RandomAccessFile(src, "r").use { raf ->
            val len = raf.length()
            var pos = 0L
            var moovPos = -1L
            var moovSize = 0L
            var moovHdr = 8
            var index = 0
            while (pos + 8 <= len) {
                raf.seek(pos)
                val h = raf.readFullyOrNull(8) ?: break
                var size = be32(h, 0).toLong() and 0xFFFFFFFFL
                val type = String(h, 4, 4, Charsets.ISO_8859_1)
                var hdr = 8
                if (size == 1L) { size = raf.readLong(); hdr = 16 } else if (size == 0L) size = len - pos
                require(size >= hdr) { "Broken MP4 atom" }
                if (index == 0) require(type == "ftyp") { "Not an MP4/M4A container (raw AAC has no tag area)" }
                require(type != "moof") { "Fragmented MP4 is not supported" }
                if (type == "moov" && moovPos < 0) { moovPos = pos; moovSize = size; moovHdr = hdr }
                pos += size
                index++
            }
            require(moovPos >= 0) { "No moov atom found" }
            require(moovSize < 512L * 1024 * 1024) { "moov atom too large" }

            raf.seek(moovPos + moovHdr)
            val mb = raf.readFullyOrNull((moovSize - moovHdr).toInt()) ?: throw IllegalStateException("Truncated moov")

            val newChildren = ByteArrayOutputStream()
            var udtaDone = false
            for (a in atoms(mb, 0, mb.size)) {
                if (a.type == "udta" && !udtaDone) {
                    newChildren.write(rebuildUdta(mb, a, e)); udtaDone = true
                } else newChildren.write(mb, a.off, a.size)
            }
            if (!udtaDone) newChildren.write(atom("udta", newMeta(e, emptyList())))
            val content = newChildren.toByteArray()
            val newMoovSize = 8L + content.size
            val delta = (newMoovSize - moovSize).toInt()
            val oldMoovEnd = moovPos + moovSize
            if (delta != 0) fixOffsets(content, 0, content.size, oldMoovEnd, delta)

            FileOutputStream(dst).use { fos ->
                copyRange(raf, 0, moovPos, fos)
                fos.write(atom("moov", content))
                copyRange(raf, oldMoovEnd, len - oldMoovEnd, fos)
            }
        }
    }

    private fun rebuildUdta(mb: ByteArray, udta: Atom, e: TagEdit): ByteArray {
        val o = ByteArrayOutputStream()
        var metaDone = false
        for (c in atoms(mb, udta.off + udta.hdr, udta.off + udta.size)) {
            if (c.type == "meta" && !metaDone) { o.write(rebuildMeta(mb, c, e)); metaDone = true }
            else o.write(mb, c.off, c.size)
        }
        if (!metaDone) o.write(newMeta(e, emptyList()))
        return atom("udta", o.toByteArray())
    }

    private fun hdlr(): ByteArray {
        val p = ByteArrayOutputStream()
        p.write(ByteArray(8))                         // version/flags + predefined
        p.write("mdir".toByteArray()); p.write("appl".toByteArray())
        p.write(ByteArray(8)); p.write(0)             // reserved + empty name
        return atom("hdlr", p.toByteArray())
    }

    private fun newMeta(e: TagEdit, existingIlst: List<ByteArray>): ByteArray =
        atom("meta", ByteArray(4), hdlr(), buildIlst(e, existingIlst))

    private fun rebuildMeta(mb: ByteArray, meta: Atom, e: TagEdit): ByteArray {
        val start = meta.off + meta.hdr + 4 // skip version/flags
        val kids = atoms(mb, start, meta.off + meta.size)
        val o = ByteArrayOutputStream()
        if (kids.none { it.type == "hdlr" }) o.write(hdlr())
        var ilstDone = false
        for (k in kids) {
            when (k.type) {
                "ilst" -> if (!ilstDone) {
                    val items = atoms(mb, k.off + k.hdr, k.off + k.size)
                    o.write(buildIlst(e, items.map { mb.copyOfRange(it.off, it.off + it.size) })); ilstDone = true
                }
                "free", "skip" -> {}
                else -> o.write(mb, k.off, k.size)
            }
        }
        if (!ilstDone) o.write(buildIlst(e, emptyList()))
        return atom("meta", mb.copyOfRange(meta.off + meta.hdr, meta.off + meta.hdr + 4), o.toByteArray())
    }

    private fun typeOf(item: ByteArray) = String(item, 4, 4, Charsets.ISO_8859_1)

    private fun buildIlst(e: TagEdit, existing: List<ByteArray>): ByteArray {
        val o = ByteArrayOutputStream()
        var oldDiscTotal = 0
        for (raw in existing) {
            val t = typeOf(raw)
            if (t == "disk" && raw.size >= 8 + 16 + 6) oldDiscTotal = ((raw[8 + 16 + 4].toInt() and 0xFF) shl 8) or (raw[8 + 16 + 5].toInt() and 0xFF)
            if (t in MANAGED) continue
            if (t == "covr" && e.pictureChanged) continue
            o.write(raw)
        }
        text("©nam", e.title)?.let { o.write(it) }
        text("©ART", e.artist)?.let { o.write(it) }
        text("©alb", e.album)?.let { o.write(it) }
        text("aART", e.albumArtist)?.let { o.write(it) }
        text("©day", e.year)?.let { o.write(it) }
        text("©gen", e.genre)?.let { o.write(it) }
        if (e.trackNumber != null) {
            o.write(item("trkn", 0, byteArrayOf(0, 0) + u16(e.trackNumber) + u16(e.totalTracks ?: 0) + byteArrayOf(0, 0)))
        }
        if (e.discNumber != null) {
            o.write(item("disk", 0, byteArrayOf(0, 0) + u16(e.discNumber) + u16(oldDiscTotal)))
        }
        if (e.newPicture != null) {
            o.write(item("covr", if (e.pictureMime == "image/png") 14 else 13, e.newPicture))
        }
        return atom("ilst", o.toByteArray())
    }

    private fun fixOffsets(b: ByteArray, from: Int, to: Int, oldMoovEnd: Long, delta: Int) {
        for (a in atoms(b, from, to)) {
            when {
                a.type in CONTAINERS -> fixOffsets(b, a.off + a.hdr, a.off + a.size, oldMoovEnd, delta)
                a.type == "stco" -> {
                    val n = be32(b, a.off + 12)
                    for (i in 0 until n) {
                        val p = a.off + 16 + 4 * i
                        val v = be32(b, p).toLong() and 0xFFFFFFFFL
                        if (v >= oldMoovEnd) putBe32(b, p, (v + delta).toInt())
                    }
                }
                a.type == "co64" -> {
                    val n = be32(b, a.off + 12)
                    for (i in 0 until n) {
                        val p = a.off + 16 + 8 * i
                        var v = 0L
                        for (k in 0 until 8) v = (v shl 8) or (b[p + k].toLong() and 0xFF)
                        if (v >= oldMoovEnd) {
                            v += delta
                            for (k in 0 until 8) b[p + k] = (v ushr (56 - 8 * k)).toByte()
                        }
                    }
                }
            }
        }
    }
}
