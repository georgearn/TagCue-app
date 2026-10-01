package com.example.audio

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Read-only view of a SAF document as a memory-mapped buffer, so large files
 * are paged in by the OS instead of being copied into the Java heap.
 * Falls back to a cache-file copy when the provider's descriptor cannot be mapped.
 */
class SourceFile private constructor(val buffer: ByteBuffer, private val temp: File?) : AutoCloseable {

    override fun close() {
        temp?.delete()
    }

    companion object {
        fun open(context: Context, uri: Uri): SourceFile {
            val resolver = context.contentResolver
            try {
                resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        val size = input.channel.size()
                        if (size in 1..Int.MAX_VALUE.toLong()) {
                            return SourceFile(input.channel.map(FileChannel.MapMode.READ_ONLY, 0, size), null)
                        }
                    }
                }
            } catch (_: Exception) {
                // fall through to the copy path
            }
            val temp = File.createTempFile("tagcue_src", ".tmp", context.cacheDir)
            try {
                resolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { out -> input.copyTo(out, 256 * 1024) }
                } ?: throw IllegalStateException("Cannot read source file")
                val size = temp.length()
                if (size == 0L || size > Int.MAX_VALUE) throw IllegalStateException("Source file is empty or larger than 2 GB")
                FileInputStream(temp).use { input ->
                    return SourceFile(input.channel.map(FileChannel.MapMode.READ_ONLY, 0, size), temp)
                }
            } catch (e: Exception) {
                temp.delete()
                throw e
            }
        }
    }
}
