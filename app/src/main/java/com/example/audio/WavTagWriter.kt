package com.example.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * WAV tags live in a LIST/INFO chunk. TagCue manages INAM, IART, IPRD, ICRD, IGNR and ITRK;
 * other INFO entries and every other chunk are kept. WAV has no cover art and no album artist
 * or disc fields, so those are ignored for this format.
 */
internal object WavTagWriter {

    private val MANAGED = setOf("INAM", "IART", "IPRD", "ICRD", "IGNR", "ITRK")

    private class Chunk(val id: String, val pos: Long, val size: Long) // pos = start of the 8-byte header

    private fun readAt(ch: FileChannel, pos: Long, n: Int): ByteArray? {
        val bb = ByteBuffer.allocate(n)
        var p = pos
        while (bb.hasRemaining()) {
            val r = ch.read(bb, p)
            if (r < 0) return null
            p += r
        }
        return bb.array()
    }

    private fun chunks(ch: FileChannel): List<Chunk> {
        val len = ch.size()
        val h = readAt(ch, 0, 12) ?: throw IllegalStateException("Not a WAV file")
        require(String(h, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(h, 8, 4, Charsets.ISO_8859_1) == "WAVE") {
            "Only plain RIFF/WAVE files are supported"
        }
        val out = ArrayList<Chunk>()
        var pos = 12L
        while (pos + 8 <= len) {
            val c = readAt(ch, pos, 8) ?: break
            var size = le32(c, 4).toLong() and 0xFFFFFFFFL
            if (pos + 8 + size > len) size = len - pos - 8 // streamed files with a bogus size
            out.add(Chunk(String(c, 0, 4, Charsets.ISO_8859_1), pos, size))
            pos += 8 + size + (size and 1)
        }
        return out
    }

    /** INFO entries of a LIST chunk, or null if this chunk is not LIST/INFO. */
    private fun infoOf(ch: FileChannel, c: Chunk): LinkedHashMap<String, ByteArray>? {
        if (c.id != "LIST" || c.size < 4 || c.size > 4 * 1024 * 1024) return null
        val b = readAt(ch, c.pos + 8, c.size.toInt()) ?: return null
        if (String(b, 0, 4, Charsets.ISO_8859_1) != "INFO") return null
        val m = LinkedHashMap<String, ByteArray>()
        var p = 4
        while (p + 8 <= b.size) {
            val id = String(b, p, 4, Charsets.ISO_8859_1)
            val n = le32(b, p + 4)
            p += 8
            if (n < 0 || p + n > b.size) break
            m[id] = b.copyOfRange(p, p + n)
            p += n + (n and 1)
        }
        return m
    }

    fun write(src: File, dst: File, e: TagEdit) {
        RandomAccessFile(src, "r").use { raf ->
            val ch = raf.channel
            val all = chunks(ch)
            val info = LinkedHashMap<String, ByteArray>()
            val infoChunks = HashSet<Chunk>()
            for (c in all) infoOf(ch, c)?.let { info.putAll(it); infoChunks.add(c) }
            MANAGED.forEach { info.remove(it) }

            val fresh = linkedMapOf(
                "INAM" to e.title, "IART" to e.artist, "IPRD" to e.album,
                "ICRD" to e.year, "IGNR" to e.genre, "ITRK" to e.trackNumber?.toString().orEmpty()
            )
            val body = java.io.ByteArrayOutputStream()
            body.write("INFO".toByteArray())
            fun put(id: String, v: ByteArray) {
                body.write(id.toByteArray(Charsets.ISO_8859_1))
                val n = v.size
                body.write(n); body.write(n ushr 8); body.write(n ushr 16); body.write(n ushr 24)
                body.write(v)
                if (n and 1 == 1) body.write(0)
            }
            for ((k, v) in fresh) if (v.isNotBlank()) put(k, v.toByteArray(Charsets.UTF_8) + 0)
            for ((k, v) in info) put(k, v)
            val listPayload = body.toByteArray()
            val hasInfo = listPayload.size > 4

            var written = 0L
            FileOutputStream(dst).use { fos ->
                fun w(b: ByteArray) { fos.write(b); written += b.size }
                w("RIFF".toByteArray()); w(ByteArray(4)); w("WAVE".toByteArray())
                var wroteList = !hasInfo
                fun writeList() {
                    if (wroteList) return
                    val n = listPayload.size
                    w("LIST".toByteArray())
                    w(byteArrayOf(n.toByte(), (n ushr 8).toByte(), (n ushr 16).toByte(), (n ushr 24).toByte()))
                    w(listPayload)
                    if (n and 1 == 1) w(byteArrayOf(0))
                    wroteList = true
                }
                for (c in all) {
                    if (c in infoChunks) continue
                    if (c.id == "data") writeList()
                    val total = minOf(8 + c.size + (c.size and 1), raf.length() - c.pos)
                    copyRange(raf, c.pos, total, fos)
                    written += total
                }
                writeList()
            }
            RandomAccessFile(dst, "rw").use { out ->
                val riff = (written - 8).toInt()
                out.seek(4)
                out.write(byteArrayOf(riff.toByte(), (riff ushr 8).toByte(), (riff ushr 16).toByte(), (riff ushr 24).toByte()))
            }
        }
    }

    /** Reads LIST/INFO text values so WAV tags can be shown (Android's retriever ignores them). */
    fun readInfo(ch: FileChannel): Map<String, String> = runCatching {
        val out = HashMap<String, String>()
        for (c in chunks(ch)) infoOf(ch, c)?.forEach { (k, v) ->
            out[k] = String(v, Charsets.UTF_8).trimEnd('\u0000').trim()
        }
        out
    }.getOrDefault(emptyMap())
}
