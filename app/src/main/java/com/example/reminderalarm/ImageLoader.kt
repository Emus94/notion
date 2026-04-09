package com.example.reminderalarm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * Loads bitmaps from content URIs with on-the-fly downsampling so the
 * app never tries to decode a 20MP camera shot at full size into memory.
 */
object ImageLoader {

    /**
     * Returns a bitmap whose longest side is roughly [maxDim] pixels,
     * or null if the URI is unreadable. Uses the standard two-pass
     * decode: first just the image bounds, then a downsampled decode.
     */
    fun loadSampled(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return@runCatching null

            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return@runCatching null

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(w, h, maxDim)
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        }.getOrNull()
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
