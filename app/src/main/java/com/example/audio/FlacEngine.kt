package com.example.audio

import com.example.model.AudioMetadata
import com.example.model.CueSheet
import com.example.model.CueTrack
import com.example.model.ResolvedTrackMetadata
import com.example.model.SplitOutputConfig
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class FlacStreamInfo(
    val minBlockSize: Int,
    val maxBlockSize: Int,
    val minFrameSize: Int,
    val maxFrameSize: Int,
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val totalSamples: Long,
    val md5: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FlacStreamInfo
        return sampleRate == other.sampleRate && channels == other.channels && totalSamples == other.totalSamples
    }
    override fun hashCode(): Int = 31 * sampleRate + channels
}

data class FlacMetadataBlock(
    val type: Int, // 0: STREAMINFO, 4: VORBIS_COMMENT, 6: PICTURE
    val isLast: Boolean,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FlacMetadataBlock
        return type == other.type && isLast == other.isLast && data.contentEquals(other.data)
    }
    override fun hashCode(): Int = 31 * type + data.contentHashCode()
}

object FlacEngine {

    private val FLAC_SIGNATURE = byteArrayOf(0x66, 0x4C, 0x61, 0x43) // "fLaC"

    fun isFlac(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == 0x66.toByte() && bytes[1] == 0x4C.toByte() &&
                bytes[2] == 0x61.toByte() && bytes[3] == 0x43.toByte()
    }

    fun parseStreamInfo(payload: ByteArray): FlacStreamInfo {
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val minBlockSize = bb.short.toInt() and 0xFFFF
        val maxBlockSize = bb.short.toInt() and 0xFFFF
        val minFrameSize = ((bb.get().toInt() and 0xFF) shl 16) or ((bb.get().toInt() and 0xFF) shl 8) or (bb.get().toInt() and 0xFF)
        val maxFrameSize = ((bb.get().toInt() and 0xFF) shl 16) or ((bb.get().toInt() and 0xFF) shl 8) or (bb.get().toInt() and 0xFF)

        // 8 bytes: 20 bits sample rate, 3 bits channels-1, 5 bits bps-1, 36 bits total samples
        val b0 = bb.get().toLong() and 0xFF
        val b1 = bb.get().toLong() and 0xFF
        val b2 = bb.get().toLong() and 0xFF
        val b3 = bb.get().toLong() and 0xFF
        val b4 = bb.get().toLong() and 0xFF
        val b5 = bb.get().toLong() and 0xFF
        val b6 = bb.get().toLong() and 0xFF
        val b7 = bb.get().toLong() and 0xFF

        val sampleRate = ((b0 shl 12) or (b1 shl 4) or (b2 ushr 4)).toInt()
        val channels = (((b2 ushr 1) and 0x07) + 1).toInt()
        val bitsPerSample = ((((b2 and 0x01) shl 4) or (b3 ushr 4)) + 1).toInt()

        val totalSamples = ((b3 and 0x0F) shl 32) or (b4 shl 24) or (b5 shl 16) or (b6 shl 8) or b7

        val md5 = ByteArray(16)
        bb.get(md5)

        return FlacStreamInfo(
            minBlockSize = minBlockSize,
            maxBlockSize = maxBlockSize,
            minFrameSize = minFrameSize,
            maxFrameSize = maxFrameSize,
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            totalSamples = totalSamples,
            md5 = md5
        )
    }

    fun encodeStreamInfo(info: FlacStreamInfo, newTotalSamples: Long = info.totalSamples): ByteArray {
        val out = ByteArray(34)
        val bb = ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN)
        bb.putShort(info.minBlockSize.toShort())
        bb.putShort(info.maxBlockSize.toShort())

        // 24-bit min frame size
        bb.put((info.minFrameSize shr 16).toByte())
        bb.put((info.minFrameSize shr 8).toByte())
        bb.put(info.minFrameSize.toByte())

        // 24-bit max frame size
        bb.put((info.maxFrameSize shr 16).toByte())
        bb.put((info.maxFrameSize shr 8).toByte())
        bb.put(info.maxFrameSize.toByte())

        // 8 bytes: 20 bits sample rate, 3 bits (ch-1), 5 bits (bps-1), 36 bits total samples
        val sr = info.sampleRate.toLong()
        val ch = (info.channels - 1).toLong()
        val bps = (info.bitsPerSample - 1).toLong()
        val samples = newTotalSamples and 0x0000000FFFFFFFFFL

        val b0 = ((sr ushr 12) and 0xFF).toByte()
        val b1 = ((sr ushr 4) and 0xFF).toByte()
        val b2 = (((sr and 0x0F) shl 4) or ((ch and 0x07) shl 1) or ((bps ushr 4) and 0x01)).toByte()
        val b3 = (((bps and 0x0F) shl 4) or ((samples ushr 32) and 0x0F)).toByte()
        val b4 = ((samples ushr 24) and 0xFF).toByte()
        val b5 = ((samples ushr 16) and 0xFF).toByte()
        val b6 = ((samples ushr 8) and 0xFF).toByte()
        val b7 = (samples and 0xFF).toByte()

        bb.put(b0)
        bb.put(b1)
        bb.put(b2)
        bb.put(b3)
        bb.put(b4)
        bb.put(b5)
        bb.put(b6)
        bb.put(b7)

        // Keep or zero md5
        bb.put(ByteArray(16))
        return out
    }

    fun buildVorbisComment(
        title: String,
        artist: String,
        album: String,
        albumArtist: String = "",
        trackNumber: String = "",
        trackTotal: String = "",
        date: String = "",
        genre: String = "",
        vendor: String = "TagCue Audio Editor"
    ): ByteArray {
        val comments = mutableListOf<String>()
        if (title.isNotBlank()) comments.add("TITLE=$title")
        if (artist.isNotBlank()) comments.add("ARTIST=$artist")
        if (album.isNotBlank()) comments.add("ALBUM=$album")
        if (albumArtist.isNotBlank()) comments.add("ALBUMARTIST=$albumArtist")
        if (trackNumber.isNotBlank()) comments.add("TRACKNUMBER=$trackNumber")
        if (trackTotal.isNotBlank()) comments.add("TRACKTOTAL=$trackTotal")
        if (date.isNotBlank()) comments.add("DATE=$date")
        if (genre.isNotBlank()) comments.add("GENRE=$genre")

        val baos = ByteArrayOutputStream()
        // Vendor length (LE 32-bit uint)
        val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
        writeUint32Le(baos, vendorBytes.size.toLong())
        baos.write(vendorBytes)

        // Comment list length (LE 32-bit uint)
        writeUint32Le(baos, comments.size.toLong())
        for (c in comments) {
            val cBytes = c.toByteArray(Charsets.UTF_8)
            writeUint32Le(baos, cBytes.size.toLong())
            baos.write(cBytes)
        }
        return baos.toByteArray()
    }

    fun buildPictureBlock(
        pictureBytes: ByteArray,
        mimeType: String = "image/jpeg",
        pictureType: Int = 3, // 3 = Cover (front)
        description: String = ""
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = java.io.DataOutputStream(baos)
        dos.writeInt(pictureType)
        val mimeBytes = mimeType.toByteArray(Charsets.ISO_8859_1)
        dos.writeInt(mimeBytes.size)
        dos.write(mimeBytes)
        val descBytes = description.toByteArray(Charsets.UTF_8)
        dos.writeInt(descBytes.size)
        dos.write(descBytes)
        dos.writeInt(0) // width
        dos.writeInt(0) // height
        dos.writeInt(24) // color depth
        dos.writeInt(0) // colors used
        dos.writeInt(pictureBytes.size)
        dos.write(pictureBytes)
        dos.flush()
        return baos.toByteArray()
    }

    fun extractPictureBytes(picturePayload: ByteArray): ByteArray? {
        return try {
            val bb = ByteBuffer.wrap(picturePayload).order(ByteOrder.BIG_ENDIAN)
            val picType = bb.int
            val mimeLen = bb.int
            bb.position(bb.position() + mimeLen)
            val descLen = bb.int
            bb.position(bb.position() + descLen)
            bb.int // width
            bb.int // height
            bb.int // depth
            bb.int // colors
            val picDataLen = bb.int
            val data = ByteArray(picDataLen)
            bb.get(data)
            data
        } catch (_: Exception) {
            null
        }
    }

    private fun writeUint32Le(out: OutputStream, value: Long) {
        out.write((value and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 24) and 0xFF).toInt())
    }

    /**
     * Lossless, sample-exact FLAC splitting. See [FlacSampleSplitter] for how cuts are made.
     * The source is read through a [java.nio.ByteBuffer] (memory-mapped on device), and each
     * track is streamed to the writer the caller provides, so memory use stays small.
     */
    fun splitFlacByCue(
        flac: java.nio.ByteBuffer,
        cueSheet: CueSheet,
        config: SplitOutputConfig,
        onProgress: (trackIndex: Int, totalTracks: Int, trackTitle: String) -> Unit,
        onTrackReady: (resolved: ResolvedTrackMetadata, track: CueTrack, write: (OutputStream) -> Unit) -> Unit
    ) {
        val (metadataBlocks, audioOffset) = FlacSampleSplitter.parseMetadata(flac)
        val streamInfoBlock = metadataBlocks.find { it.type == 0 }
            ?: throw IllegalStateException("STREAMINFO block missing")
        val streamInfo = parseStreamInfo(streamInfoBlock.data)
        val pictureBlock = metadataBlocks.find { it.type == 6 }
        val frames = FlacSampleSplitter.scanFrames(flac, audioOffset, streamInfo)
        val totalSamples = frames.last().let { it.startSample + it.blockSize }

        val tracksToSplit = cueSheet.tracks.filter { it.isSelectedForSplit }
        val totalTracks = tracksToSplit.size

        for (i in tracksToSplit.indices) {
            val track = tracksToSplit[i]
            onProgress(i + 1, totalTracks, track.title)

            val startSample = track.startIndex?.toSampleOffset(streamInfo.sampleRate) ?: 0L
            val nextTrack = cueSheet.tracks.getOrNull(cueSheet.tracks.indexOf(track) + 1)
            val endSample = (nextTrack?.startIndex?.toSampleOffset(streamInfo.sampleRate) ?: totalSamples)
                .coerceAtMost(totalSamples)
            if (endSample <= startSample) continue

            val stats = FlacSampleSplitter.stats(frames, startSample, endSample)
            val outInfo = streamInfo.copy(
                minBlockSize = stats.minBlock,
                maxBlockSize = stats.maxBlock,
                minFrameSize = 0,
                maxFrameSize = 0
            )

            val resolved = config.resolveMetadata(
                track = track,
                albumTitle = cueSheet.title,
                albumPerformer = cueSheet.performer,
                albumDate = cueSheet.date,
                totalTracks = cueSheet.tracks.size
            )

            val commentBytes = buildVorbisComment(
                title = resolved.title,
                artist = resolved.artist,
                album = resolved.album,
                albumArtist = resolved.albumArtist,
                trackNumber = track.number.toString(),
                trackTotal = cueSheet.tracks.size.toString(),
                date = resolved.year,
                genre = track.genre.ifBlank { cueSheet.genre }
            )

            val outputBlocks = mutableListOf(
                FlacMetadataBlock(0, false, encodeStreamInfo(outInfo, stats.samples)),
                FlacMetadataBlock(4, false, commentBytes)
            )
            if (config.copyExistingCoverArt && pictureBlock != null) {
                outputBlocks.add(FlacMetadataBlock(6, false, pictureBlock.data))
            }

            onTrackReady(resolved, track) { out ->
                out.write(FLAC_SIGNATURE)
                for (bIdx in outputBlocks.indices) {
                    val block = outputBlocks[bIdx]
                    val isLast = bIdx == outputBlocks.size - 1
                    out.write((if (isLast) 0x80 else 0x00) or (block.type and 0x7F))
                    val len = block.data.size
                    out.write((len shr 16) and 0xFF)
                    out.write((len shr 8) and 0xFF)
                    out.write(len and 0xFF)
                    out.write(block.data)
                }
                FlacSampleSplitter.writeCut(out, flac, frames, streamInfo, startSample, endSample)
            }
        }
    }
}
