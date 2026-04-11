package com.example.reminderalarm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

/**
 * Loads bitmaps with on-the-fly downsampling so a 20MP camera shot
 * never has to be decoded at full size for a 56dp thumbnail. Accepts
 * either an absolute file path (preferred, produced by [ImageStorage])
 * or a content URI (legacy fallback for reminders saved with the old
 * pick-URI-directly approach).
 *
 * The sync [loadSampled] path is kept for code that needs a bitmap
 * right now (alarm screen background, edit form preview). Lists use
 * [loadAsync] which decodes off the main thread, caches in an in-memory
 * LRU (~50 entries), and guards against RecyclerView recycling by
 * tagging the target [ImageView].
 */
object ImageLoader {

    // ~50 entries of ~160KB each (200x200 ARGB_8888) ≈ 8MB — fine for
    // a background app. Size in kilobytes so LruCache's built-in memory
    // accounting works naturally.
    private val cache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ImageLoader-io").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadSampled(context: Context, pathOrUri: String?, maxDim: Int): Bitmap? {
        if (pathOrUri.isNullOrBlank()) return null
        val key = cacheKey(pathOrUri, maxDim)
        cache.get(key)?.let { return it }
        val bmp = runCatching {
            if (pathOrUri.startsWith("/")) {
                decodeFile(pathOrUri, maxDim)
            } else {
                decodeUri(context, Uri.parse(pathOrUri), maxDim)
            }
        }.getOrNull()
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    /**
     * Async variant for RecyclerView rows. Tags [target] with [pathOrUri]
     * so late decode callbacks for a now-recycled view get dropped
     * instead of painting the wrong thumbnail.
     */
    fun loadAsync(
        context: Context,
        pathOrUri: String?,
        maxDim: Int,
        target: ImageView
    ) {
        if (pathOrUri.isNullOrBlank()) {
            target.tag = null
            target.setImageBitmap(null)
            return
        }
        target.tag = pathOrUri
        val key = cacheKey(pathOrUri, maxDim)
        val cached = cache.get(key)
        if (cached != null) {
            target.setImageBitmap(cached)
            return
        }
        // Clear any leftover bitmap from the recycled view so we don't
        // flash the wrong thumbnail while decoding.
        target.setImageBitmap(null)
        val appContext = context.applicationContext
        ioExecutor.execute {
            val bmp = runCatching {
                if (pathOrUri.startsWith("/")) {
                    decodeFile(pathOrUri, maxDim)
                } else {
                    decodeUri(appContext, Uri.parse(pathOrUri), maxDim)
                }
            }.getOrNull() ?: return@execute
            cache.put(key, bmp)
            mainHandler.post {
                if (target.tag == pathOrUri) {
                    target.setImageBitmap(bmp)
                }
            }
        }
    }

    private fun cacheKey(pathOrUri: String, maxDim: Int): String = "$pathOrUri@$maxDim"

    private fun decodeFile(path: String, maxDim: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        }
        return BitmapFactory.decodeFile(path, decodeOpts)
    }

    private fun decodeUri(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        }
    }

    private fun sampleSize(width: Int, height: Int, maxDim: Int): Int {
        if (maxDim <= 0) return 1
        var sample = 1
        while (width / sample > maxDim || height / sample > maxDim) {
            sample *= 2
        }
        return sample
    }
}
