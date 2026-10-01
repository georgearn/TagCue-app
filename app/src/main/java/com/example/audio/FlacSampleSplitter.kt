package com.example.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Sample-exact lossless FLAC cutting.
 *
 * Whole frames inside the cut are copied bit-for-bit (only the header is rewritten
 * to the variable-blocksize form and the CRCs are recomputed). The two frames that
 * straddle a cut point are decoded to PCM and re-emitted as VERBATIM frames
 * (still lossless, tiny cost: at most two frames per track).
 *
 * Verified offline against libFLAC and ffmpeg for 16/24-bit, mono/stereo,
 * fixed/LPC predictors and fixed/variable input blocksizes.
 */
internal object FlacCrc {
    private val table8 = IntArray(256)
    private val table16 = IntArray(256)

    init {
        for (i in 0 until 256) {
            var c8 = i
            for (k in 0 until 8) c8 = if (c8 and 0x80 != 0) ((c8 shl 1) xor 0x07) and 0xFF else (c8 shl 1) and 0xFF
            table8[i] = c8
            var c16 = i shl 8
            for (k in 0 until 8) c16 = if (c16 and 0x8000 != 0) ((c16 shl 1) xor 0x8005) and 0xFFFF else (c16 shl 1) and 0xFFFF
            table16[i] = c16
        }
    }

    fun crc8(b: ByteArray, from: Int, to: Int): Int {
        var c = 0
        for (i in from until to) c = table8[c xor (b[i].toInt() and 0xFF)]
        return c
    }

    fun crc16(b: ByteArray, from: Int, to: Int): Int {
        var c = 0
        for (i in from until to) c = ((c shl 8) and 0xFFFF) xor table16[(c shr 8) xor (b[i].toInt() and 0xFF)]
        return c
    }

    fun crc8(b: ByteBuffer, from: Int, to: Int): Int {
        var c = 0
        for (i in from until to) c = table8[c xor (b.get(i).toInt() and 0xFF)]
        return c
    }

    fun crc16(b: ByteBuffer, from: Int, to: Int): Int {
        var c = 0
        for (i in from until to) c = ((c shl 8) and 0xFFFF) xor table16[(c shr 8) xor (b.get(i).toInt() and 0xFF)]
        return c
    }
}

internal class FlacFrame(
    val pos: Int,
    val headerEnd: Int,          // index just after the header CRC-8 byte
    val numberLen: Int,          // bytes used by the UTF-8 coded frame/sample number
    val blockSize: Int,
    val startSample: Long,
    val bitsPerSample: Int,
    val channelBits: Int
) {
    var frameEnd: Int = -1       // exclusive, includes trailing CRC-16
}

internal object FlacSampleSplitter {

    private fun ByteBuffer.u(i: Int): Int = get(i).toInt() and 0xFF

    private fun ByteBuffer.copyRange(from: Int, len: Int): ByteArray {
        val out = ByteArray(len)
        val d = duplicate()
        d.position(from)
        d.get(out, 0, len)
        return out
    }

    private val BLOCK_SIZES = mapOf(
        1 to 192, 2 to 576, 3 to 1152, 4 to 2304, 5 to 4608,
        8 to 256, 9 to 512, 10 to 1024, 11 to 2048, 12 to 4096,
        13 to 8192, 14 to 16384, 15 to 32768
    )
    private val SAMPLE_SIZES = mapOf(1 to 8, 2 to 12, 4 to 16, 5 to 20, 6 to 24, 7 to 32)
    private val RATE_BITS = mapOf(
        88200 to 1, 176400 to 2, 192000 to 3, 8000 to 4, 16000 to 5, 22050 to 6,
        24000 to 7, 32000 to 8, 44100 to 9, 48000 to 10, 96000 to 11
    )
    private val SIZE_BITS = mapOf(8 to 1, 12 to 2, 16 to 4, 20 to 5, 24 to 6, 32 to 7)

    // ---------------------------------------------------------------- metadata

    /** Returns metadata blocks and the byte offset where the audio frames start. */
    fun parseMetadata(bytes: ByteBuffer): Pair<List<FlacMetadataBlock>, Int> {
        require(bytes.limit() >= 4 && bytes.u(0) == 0x66 && bytes.u(1) == 0x4C && bytes.u(2) == 0x61 && bytes.u(3) == 0x43) {
            "Not a valid FLAC stream"
        }
        val blocks = mutableListOf<FlacMetadataBlock>()
        var p = 4
        while (true) {
            if (p + 4 > bytes.limit()) throw IllegalStateException("Truncated FLAC metadata")
            val h = bytes.u(p)
            val last = h and 0x80 != 0
            val type = h and 0x7F
            val len = (bytes.u(p + 1) shl 16) or (bytes.u(p + 2) shl 8) or bytes.u(p + 3)
            if (p + 4 + len > bytes.limit()) throw IllegalStateException("Truncated FLAC metadata block")
            blocks.add(FlacMetadataBlock(type, last, bytes.copyRange(p + 4, len)))
            p += 4 + len
            if (last) break
        }
        return Pair(blocks, p)
    }

    // ---------------------------------------------------------------- frame scan

    private fun readUtf8(b: ByteBuffer, p: Int): Pair<Long, Int>? {
        val f = b.u(p)
        val n: Int
        var v: Long
        when {
            f < 0x80 -> return Pair(f.toLong(), 1)
            f and 0xE0 == 0xC0 -> { n = 1; v = (f and 0x1F).toLong() }
            f and 0xF0 == 0xE0 -> { n = 2; v = (f and 0x0F).toLong() }
            f and 0xF8 == 0xF0 -> { n = 3; v = (f and 0x07).toLong() }
            f and 0xFC == 0xF8 -> { n = 4; v = (f and 0x03).toLong() }
            f and 0xFE == 0xFC -> { n = 5; v = (f and 0x01).toLong() }
            f == 0xFE -> { n = 6; v = 0L }
            else -> return null
        }
        for (i in 1..n) {
            val c = b.u(p + i)
            if (c and 0xC0 != 0x80) return null
            v = (v shl 6) or (c and 0x3F).toLong()
        }
        return Pair(v, n + 1)
    }

    private fun writeUtf8(out: ByteArrayOutputStream, value: Long) {
        if (value < 0x80) { out.write(value.toInt()); return }
        val n = when {
            value < 0x800 -> 1
            value < 0x10000 -> 2
            value < 0x200000 -> 3
            value < 0x4000000 -> 4
            value < 0x80000000L -> 5
            else -> 6
        }
        val first = intArrayOf(0xC0, 0xE0, 0xF0, 0xF8, 0xFC, 0xFE)[n - 1]
        val tmp = IntArray(n + 1)
        var v = value
        for (i in n downTo 1) {
            tmp[i] = 0x80 or (v and 0x3F).toInt()
            v = v ushr 6
        }
        tmp[0] = first or v.toInt()
        for (x in tmp) out.write(x)
    }

    private fun parseHeader(b: ByteBuffer, p: Int, si: FlacStreamInfo): FlacFrame? {
        try {
            if (p + 6 > b.limit()) return null
            if (b.u(p) != 0xFF || (b.u(p + 1) and 0xFE) != 0xF8) return null
            val variable = (b.u(p + 1) and 1) != 0
            val bsb = b.u(p + 2) ushr 4
            val srb = b.u(p + 2) and 0x0F
            val chb = b.u(p + 3) ushr 4
            val szb = (b.u(p + 3) ushr 1) and 7
            if (b.u(p + 3) and 1 != 0) return null
            if (bsb == 0 || srb == 15 || szb == 3 || chb > 10) return null
            val (num, ul) = readUtf8(b, p + 4) ?: return null
            var q = p + 4 + ul
            val blockSize = when (bsb) {
                6 -> { val v = b.u(q) + 1; q += 1; v }
                7 -> { val v = ((b.u(q) shl 8) or b.u(q + 1)) + 1; q += 2; v }
                else -> BLOCK_SIZES[bsb] ?: return null
            }
            when (srb) {
                12 -> q += 1
                13, 14 -> q += 2
            }
            if (q >= b.limit()) return null
            if (FlacCrc.crc8(b, p, q) != b.u(q)) return null
            val bps = if (szb == 0) si.bitsPerSample else (SAMPLE_SIZES[szb] ?: return null)
            val start = if (variable) num else num * si.maxBlockSize
            return FlacFrame(p, q + 1, ul, blockSize, start, bps, chb)
        } catch (_: IndexOutOfBoundsException) {
            return null
        }
    }

    /** Finds every frame. A frame boundary is accepted only if header CRC-8 and the previous frame's CRC-16 both check out. */
    fun scanFrames(b: ByteBuffer, audioOffset: Int, si: FlacStreamInfo): List<FlacFrame> {
        val frames = ArrayList<FlacFrame>()
        val n = b.limit()
        var cur = parseHeader(b, audioOffset, si) ?: throw IllegalStateException("No valid FLAC frame found")
        while (true) {
            var q = cur.headerEnd + 1
            var next: FlacFrame? = null
            while (q < n - 5) {
                if (b.u(q) == 0xFF && (b.u(q + 1) and 0xFE) == 0xF8) {
                    val h2 = parseHeader(b, q, si)
                    if (h2 != null && frameCrcOk(b, cur.pos, q)) { next = h2; break }
                }
                q++
            }
            if (next == null) {
                // Last frame ends at end of stream.
                if (n - cur.pos > 2 && frameCrcOk(b, cur.pos, n)) {
                    cur.frameEnd = n
                    frames.add(cur)
                }
                break
            }
            cur.frameEnd = q
            frames.add(cur)
            cur = next
        }
        if (frames.isEmpty()) throw IllegalStateException("No valid FLAC frame found")
        return frames
    }

    private fun frameCrcOk(b: ByteBuffer, from: Int, endExclusive: Int): Boolean {
        val stored = (b.u(endExclusive - 2) shl 8) or b.u(endExclusive - 1)
        return FlacCrc.crc16(b, from, endExclusive - 2) == stored
    }

    // ---------------------------------------------------------------- decoding

    private class BitReader(val b: ByteBuffer, startByte: Int) {
        var pos: Long = startByte.toLong() * 8

        fun bits(n: Int): Long {
            var v = 0L
            for (i in 0 until n) {
                val byte = b.get((pos shr 3).toInt()).toInt()
                v = (v shl 1) or ((byte shr (7 - (pos and 7).toInt())) and 1).toLong()
                pos++
            }
            return v
        }

        fun signed(n: Int): Long {
            if (n == 0) return 0L
            val v = bits(n)
            return if ((v shr (n - 1)) != 0L) v - (1L shl n) else v
        }

        fun unary(): Int {
            var n = 0
            while (bits(1) == 0L) n++
            return n
        }
    }

    private fun readResidual(br: BitReader, blockSize: Int, order: Int): LongArray {
        val method = br.bits(2).toInt()
        if (method > 1) throw IllegalStateException("Bad residual coding method")
        val partOrder = br.bits(4).toInt()
        val paramBits = if (method == 0) 4 else 5
        val escape = (1 shl paramBits) - 1
        val out = LongArray(blockSize - order)
        var idx = 0
        for (part in 0 until (1 shl partOrder)) {
            val count = (blockSize shr partOrder) - (if (part == 0) order else 0)
            val k = br.bits(paramBits).toInt()
            if (k == escape) {
                val nb = br.bits(5).toInt()
                for (i in 0 until count) out[idx++] = br.signed(nb)
            } else {
                for (i in 0 until count) {
                    val q = br.unary().toLong()
                    val r = if (k > 0) br.bits(k) else 0L
                    val u = (q shl k) or r
                    out[idx++] = if (u and 1L == 0L) (u shr 1) else -((u + 1) shr 1)
                }
            }
        }
        return out
    }

    private fun readSubframe(br: BitReader, blockSize: Int, bps: Int): LongArray {
        if (br.bits(1) != 0L) throw IllegalStateException("Bad subframe padding")
        val type = br.bits(6).toInt()
        var wasted = 0
        if (br.bits(1) != 0L) wasted = br.unary() + 1
        val eb = bps - wasted
        val s = LongArray(blockSize)
        when {
            type == 0 -> { val v = br.signed(eb); for (i in 0 until blockSize) s[i] = v }
            type == 1 -> for (i in 0 until blockSize) s[i] = br.signed(eb)
            type in 8..12 -> {
                val o = type and 7
                for (i in 0 until o) s[i] = br.signed(eb)
                val res = readResidual(br, blockSize, o)
                for (i in o until blockSize) {
                    val r = res[i - o]
                    s[i] = when (o) {
                        0 -> r
                        1 -> s[i - 1] + r
                        2 -> 2 * s[i - 1] - s[i - 2] + r
                        3 -> 3 * s[i - 1] - 3 * s[i - 2] + s[i - 3] + r
                        else -> 4 * s[i - 1] - 6 * s[i - 2] + 4 * s[i - 3] - s[i - 4] + r
                    }
                }
            }
            type >= 32 -> {
                val o = (type and 31) + 1
                for (i in 0 until o) s[i] = br.signed(eb)
                val precision = br.bits(4).toInt() + 1
                if (precision == 16) throw IllegalStateException("Bad LPC precision")
                val shift = br.signed(5).toInt()
                if (shift < 0) throw IllegalStateException("Negative LPC shift")
                val coef = LongArray(o) { br.signed(precision) }
                val res = readResidual(br, blockSize, o)
                for (i in o until blockSize) {
                    var acc = 0L
                    for (j in 0 until o) acc += coef[j] * s[i - 1 - j]
                    s[i] = (acc shr shift) + res[i - o]
                }
            }
            else -> throw IllegalStateException("Bad subframe type")
        }
        if (wasted > 0) for (i in 0 until blockSize) s[i] = s[i] shl wasted
        return s
    }

    /** Decodes a frame to per-channel PCM (left/right already reconstructed). */
    private fun decodeFrame(b: ByteBuffer, f: FlacFrame): Array<LongArray> {
        val br = BitReader(b, f.headerEnd)
        val chb = f.channelBits
        val n = f.blockSize
        if (chb < 8) {
            return Array(chb + 1) { readSubframe(br, n, f.bitsPerSample) }
        }
        val extra = if (chb == 9) intArrayOf(1, 0) else intArrayOf(0, 1)
        val c0 = readSubframe(br, n, f.bitsPerSample + extra[0])
        val c1 = readSubframe(br, n, f.bitsPerSample + extra[1])
        return when (chb) {
            8 -> arrayOf(c0, LongArray(n) { c0[it] - c1[it] })
            9 -> arrayOf(LongArray(n) { c0[it] + c1[it] }, c1)
            else -> {
                val l = LongArray(n)
                val r = LongArray(n)
                for (i in 0 until n) {
                    val side = c1[i]
                    val mid = (c0[i] shl 1) or (side and 1L)
                    l[i] = (mid + side) shr 1
                    r[i] = (mid - side) shr 1
                }
                arrayOf(l, r)
            }
        }
    }

    // ---------------------------------------------------------------- encoding

    private fun rewriteFrame(b: ByteBuffer, f: FlacFrame, newStart: Long): ByteArray {
        val out = ByteArrayOutputStream(f.frameEnd - f.pos + 8)
        out.write(0xFF)
        out.write(0xF9) // variable blocksize strategy
        out.write(b.u(f.pos + 2))
        out.write(b.u(f.pos + 3))
        writeUtf8(out, newStart)
        // blocksize / samplerate extension bytes sit between the number and the CRC-8
        val extFrom = f.pos + 4 + f.numberLen
        val extTo = f.headerEnd - 1
        if (extTo > extFrom) out.write(b.copyRange(extFrom, extTo - extFrom))
        val hdr = out.toByteArray()
        out.write(FlacCrc.crc8(hdr, 0, hdr.size))
        out.write(b.copyRange(f.headerEnd, f.frameEnd - 2 - f.headerEnd))
        val all = out.toByteArray()
        val c16 = FlacCrc.crc16(all, 0, all.size)
        val res = all.copyOf(all.size + 2)
        res[all.size] = (c16 shr 8).toByte()
        res[all.size + 1] = c16.toByte()
        return res
    }

    private fun verbatimFrame(channels: Array<LongArray>, from: Int, to: Int, start: Long, si: FlacStreamInfo): ByteArray {
        val count = to - from
        val bsb = if (count <= 256) 6 else 7
        var srb = RATE_BITS[si.sampleRate] ?: 0
        var srExt = ByteArray(0)
        if (srb == 0) {
            val sr = si.sampleRate
            when {
                sr % 1000 == 0 && sr / 1000 <= 255 -> { srb = 12; srExt = byteArrayOf((sr / 1000).toByte()) }
                sr <= 65535 -> { srb = 13; srExt = byteArrayOf((sr shr 8).toByte(), sr.toByte()) }
                sr % 10 == 0 && sr / 10 <= 65535 -> { srb = 14; srExt = byteArrayOf(((sr / 10) shr 8).toByte(), (sr / 10).toByte()) }
            }
        }
        val szb = SIZE_BITS[si.bitsPerSample] ?: 0
        val hdrOut = ByteArrayOutputStream()
        hdrOut.write(0xFF)
        hdrOut.write(0xF9)
        hdrOut.write((bsb shl 4) or srb)
        hdrOut.write(((channels.size - 1) shl 4) or (szb shl 1))
        writeUtf8(hdrOut, start)
        if (bsb == 6) hdrOut.write(count - 1) else { hdrOut.write((count - 1) shr 8); hdrOut.write((count - 1) and 0xFF) }
        hdrOut.write(srExt)
        val hdr = hdrOut.toByteArray()

        val body = BitWriter()
        for (c in channels) {
            body.write(0x02L, 8) // subframe header: VERBATIM, no wasted bits
            for (i in from until to) body.write(c[i], si.bitsPerSample)
        }
        val bodyBytes = body.finish()

        val out = ByteArrayOutputStream(hdr.size + 1 + bodyBytes.size + 2)
        out.write(hdr)
        out.write(FlacCrc.crc8(hdr, 0, hdr.size))
        out.write(bodyBytes)
        val all = out.toByteArray()
        val c16 = FlacCrc.crc16(all, 0, all.size)
        out.write(c16 shr 8)
        out.write(c16 and 0xFF)
        return out.toByteArray()
    }

    private class BitWriter {
        private val out = ByteArrayOutputStream()
        private var acc = 0L
        private var nbits = 0

        fun write(value: Long, n: Int) {
            for (i in n - 1 downTo 0) {
                acc = (acc shl 1) or ((value shr i) and 1L)
                nbits++
                if (nbits == 8) { out.write(acc.toInt() and 0xFF); acc = 0; nbits = 0 }
            }
        }

        fun finish(): ByteArray {
            if (nbits > 0) { out.write((acc shl (8 - nbits)).toInt() and 0xFF); acc = 0; nbits = 0 }
            return out.toByteArray()
        }
    }

    // ---------------------------------------------------------------- public cut

    class CutStats(val minBlock: Int, val maxBlock: Int, val samples: Long)

    /** Sizes of the frames [writeCut] will emit. Needed up front for STREAMINFO, no decoding involved. */
    fun stats(frames: List<FlacFrame>, startSample: Long, endSample: Long): CutStats {
        var minBlock = Int.MAX_VALUE
        var maxBlock = 0
        var written = 0L
        for (f in frames) {
            val fs = f.startSample
            val fe = fs + f.blockSize
            if (fe <= startSample || fs >= endSample) continue
            val n = (minOf(endSample, fe) - maxOf(startSample, fs)).toInt()
            written += n
            minBlock = minOf(minBlock, n)
            maxBlock = maxOf(maxBlock, n)
        }
        if (written == 0L) { minBlock = 0; maxBlock = 0 }
        return CutStats(minBlock, maxBlock, written)
    }

    /**
     * Streams samples [startSample, endSample) as a new frame stream starting at sample 0.
     * Frames are produced one at a time, so memory use stays at a single frame.
     */
    fun writeCut(
        out: java.io.OutputStream,
        b: ByteBuffer,
        frames: List<FlacFrame>,
        si: FlacStreamInfo,
        startSample: Long,
        endSample: Long
    ) {
        for (f in frames) {
            val fs = f.startSample
            val fe = fs + f.blockSize
            if (fe <= startSample || fs >= endSample) continue
            if (fs >= startSample && fe <= endSample) {
                out.write(rewriteFrame(b, f, fs - startSample))
            } else {
                val pcm = decodeFrame(b, f)
                val a = (maxOf(startSample, fs) - fs).toInt()
                val z = (minOf(endSample, fe) - fs).toInt()
                out.write(verbatimFrame(pcm, a, z, maxOf(startSample, fs) - startSample, si))
            }
        }
    }
}
