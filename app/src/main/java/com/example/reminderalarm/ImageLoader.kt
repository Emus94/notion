package com.example.reminderalarm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
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
        val raw = BitmapFactory.decodeFile(path, decodeOpts) ?: return null
        // EXIF orientation: camera JPEGs are stored landscape even when
        // the phone was held portrait. Rotate to match the intended
        // viewing orientation.
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return applyExifOrientation(raw, orientation)
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
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null
        // Read EXIF from a fresh stream (ExifInterface consumes it).
        val orientation = readOrientation(context, uri)
        return applyExifOrientation(raw, orientation)
    }

    private fun readOrientation(context: Context, uri: Uri): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    /**
     * Rotates / mirrors [src] to match the EXIF orientation. Returns the
     * same [src] when orientation is NORMAL so we don't allocate an
     * identical bitmap copy for the common case.
     */
    private fun applyExifOrientation(src: Bitmap, orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) return src
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
            else -> return src
        }
        return runCatching {
            val rotated = Bitmap.createBitmap(
                src, 0, 0, src.width, src.height, matrix, true
            )
            // Free the original if a new bitmap was allocated.
            if (rotated != src) src.recycle()
            rotated
        }.getOrDefault(src)
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
