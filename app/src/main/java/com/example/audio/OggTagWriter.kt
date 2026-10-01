package com.example.audio

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Rewrites the comment header of an Ogg Vorbis or Ogg Opus file. The comment packet may change
 * size, so the header pages are rebuilt and later pages get new sequence numbers and CRCs while
 * being streamed through. If the page count of the headers does not change, the audio pages are
 * copied byte for byte.
 */
internal object OggTagWriter {

    private val crcTable = IntArray(256).also { t ->
        for (i in 0 until 256) {
            var r = i shl 24
            repeat(8) { r = if (r and Int.MIN_VALUE != 0) (r shl 1) xor 0x04C11DB7 else r shl 1 }
            t[i] = r
        }
    }

    private fun crc(b: ByteArray): Int {
        var c = 0
        for (x in b) c = (c shl 8) xor crcTable[((c ushr 24) xor (x.toInt() and 0xFF)) and 0xFF]
        return c
    }

    private fun readPage(raf: RandomAccessFile): ByteArray? {
        val h = raf.readFullyOrNull(27) ?: return null
        require(h[0] == 'O'.code.toByte() && h[1] == 'g'.code.toByte() && h[2] == 'g'.code.toByte() && h[3] == 'S'.code.toByte()) { "Broken Ogg page" }
        val n = h[26].toInt() and 0xFF
        val seg = raf.readFullyOrNull(n) ?: throw IllegalStateException("Truncated Ogg page")
        var dl = 0
        for (s in seg) dl += s.toInt() and 0xFF
        val data = raf.readFullyOrNull(dl) ?: throw IllegalStateException("Truncated Ogg page")
        return h + seg + data
    }

    private fun segs(page: ByteArray): IntArray = IntArray(page[26].toInt() and 0xFF) { page[27 + it].toInt() and 0xFF }
    private fun dataOffset(page: ByteArray) = 27 + (page[26].toInt() and 0xFF)
    private fun seqOf(page: ByteArray) = le32(page, 18)

    private fun lacing(len: Int): IntArray {
        val n = len / 255
        return IntArray(n + 1) { if (it < n) 255 else len % 255 }
    }

    fun write(src: File, dst: File, e: TagEdit) {
        RandomAccessFile(src, "r").use { raf ->
            val first = readPage(raf) ?: throw IllegalStateException("Empty Ogg file")
            val d0 = first.copyOfRange(dataOffset(first), first.size)
            val vorbis = d0.size > 7 && d0[0] == 1.toByte() && String(d0, 1, 6, Charsets.ISO_8859_1) == "vorbis"
            val opus = d0.size > 8 && String(d0, 0, 8, Charsets.ISO_8859_1) == "OpusHead"
            require(vorbis || opus) { "Only Ogg Vorbis and Ogg Opus tag writing is supported" }
            val needed = if (vorbis) 3 else 2
            val serial = le32(first, 14)

            val packets = ArrayList<ByteArray>()
            packets.add(d0)
            var cur = ByteArrayOutputStream()
            var lastHeaderSeq = seqOf(first)
            while (packets.size < needed) {
                val pg = readPage(raf) ?: throw IllegalStateException("Truncated Ogg headers")
                val sg = segs(pg)
                var off = dataOffset(pg)
                for ((i, s) in sg.withIndex()) {
                    cur.write(pg, off, s); off += s
                    if (s < 255) {
                        packets.add(cur.toByteArray()); cur = ByteArrayOutputStream()
                        if (packets.size == needed && i != sg.size - 1) throw IllegalStateException("Audio shares a page with the headers")
                    }
                }
                lastHeaderSeq = seqOf(pg)
            }
            check(cur.size() == 0) { "Unexpected partial packet after headers" }
            val audioOffset = raf.filePointer
            val oldFirstAudioSeq = lastHeaderSeq + 1

            // New comment packet
            val commentOld = packets[1]
            val prefixLen = if (vorbis) 7 else 8
            require(commentOld.size > prefixLen + 8) { "Comment header missing" }
            val parsed = VorbisComments.parse(commentOld, prefixLen)
            val extra = if (e.pictureChanged) setOf("METADATA_BLOCK_PICTURE") else emptySet()
            val entries = VorbisComments.merge(parsed.entries, e, extra).toMutableList()
            if (e.newPicture != null) {
                entries.add("METADATA_BLOCK_PICTURE=" +
                    java.util.Base64.getEncoder().encodeToString(FlacTagWriter.buildPicture(e.newPicture, e.pictureMime)))
            }
            val cbody = VorbisComments.build(parsed.vendor, entries)
            val comment = ByteArrayOutputStream().apply {
                write(commentOld, 0, prefixLen)
                write(cbody)
                if (vorbis) write(1) // framing bit
            }.toByteArray()

            val headerPackets = if (vorbis) listOf(comment, packets[2]) else listOf(comment)
            val allSegs = headerPackets.flatMap { lacing(it.size).toList() }
            val allData = ByteArrayOutputStream().also { o -> headerPackets.forEach { o.write(it) } }.toByteArray()

            FileOutputStream(dst).use { fos ->
                val out = BufferedOutputStream(fos, 256 * 1024)
                out.write(first)
                var seq = 1
                var segPos = 0
                var dataPos = 0
                var continued = false
                while (segPos < allSegs.size) {
                    val end = minOf(segPos + 255, allSegs.size)
                    val group = allSegs.subList(segPos, end)
                    val dl = group.sum()
                    val page = ByteArray(27 + group.size + dl)
                    "OggS".toByteArray(Charsets.ISO_8859_1).copyInto(page)
                    page[5] = if (continued) 1 else 0
                    val endsOpen = group.last() == 255
                    val granule = if (endsOpen) -1L else 0L
                    for (k in 0 until 8) page[6 + k] = (granule ushr (8 * k)).toByte()
                    for (k in 0 until 4) page[14 + k] = (serial ushr (8 * k)).toByte()
                    for (k in 0 until 4) page[18 + k] = (seq ushr (8 * k)).toByte()
                    page[26] = group.size.toByte()
                    group.forEachIndexed { i, s -> page[27 + i] = s.toByte() }
                    allData.copyInto(page, 27 + group.size, dataPos, dataPos + dl)
                    val c = crc(page)
                    for (k in 0 until 4) page[22 + k] = (c ushr (8 * k)).toByte()
                    out.write(page)
                    continued = endsOpen
                    segPos = end; dataPos += dl; seq++
                }
                val delta = seq - oldFirstAudioSeq
                if (delta == 0) {
                    out.flush()
                    copyTail(raf, audioOffset, fos)
                } else {
                    raf.seek(audioOffset)
                    while (true) {
                        val start = raf.filePointer
                        val pg = readPage(raf) ?: break
                        if ((pg[5].toInt() and 0x02) != 0) { // next chained stream: leave as is
                            out.write(pg)
                            out.flush()
                            copyTail(raf, raf.filePointer, fos)
                            break
                        }
                        val ns = seqOf(pg) + delta
                        for (k in 0 until 4) pg[18 + k] = (ns ushr (8 * k)).toByte()
                        for (k in 0 until 4) pg[22 + k] = 0
                        val c = crc(pg)
                        for (k in 0 until 4) pg[22 + k] = (c ushr (8 * k)).toByte()
                        out.write(pg)
                    }
                    out.flush()
                }
            }
        }
    }
}
