package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val thumbnails = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

/**
 * Decodes cover art at about [maxPx] pixels on a background thread and caches the result.
 * Decoding full-size covers on the main thread for every album card is what made scrolling choppy.
 */
@Composable
fun rememberThumbnail(bytes: ByteArray?, maxPx: Int): ImageBitmap? {
    val key = remember(bytes, maxPx) {
        bytes?.takeIf { it.isNotEmpty() }?.let { "${it.size}:${it.contentHashCode()}:$maxPx" }
    }
    return produceState<ImageBitmap?>(initialValue = key?.let { thumbnails.get(it) }?.asImageBitmap(), key1 = key) {
        if (key == null || bytes == null) { value = null; return@produceState }
        value = thumbnails.get(key)?.asImageBitmap() ?: withContext(Dispatchers.Default) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            }.getOrNull()?.also { thumbnails.put(key, it) }?.asImageBitmap()
        }
    }.value
}
