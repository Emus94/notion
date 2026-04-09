package com.example.reminderalarm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Loads bitmaps with on-the-fly downsampling so a 20MP camera shot
 * never has to be decoded at full size for a 56dp thumbnail. Accepts
 * either an absolute file path (preferred, produced by [ImageStorage])
 * or a content URI (legacy fallback for reminders saved with the old
 * pick-URI-directly approach).
 */
object ImageLoader {

    fun loadSampled(context: Context, pathOrUri: String?, maxDim: Int): Bitmap? {
        if (pathOrUri.isNullOrBlank()) return null
        return runCatching {
            if (pathOrUri.startsWith("/")) {
                decodeFile(pathOrUri, maxDim)
            } else {
                decodeUri(context, Uri.parse(pathOrUri), maxDim)
            }
        }.getOrNull()
    }

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
