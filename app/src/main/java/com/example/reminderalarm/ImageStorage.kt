package com.example.reminderalarm

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Copies picker-chosen images into the app's private files directory.
 *
 * This avoids all the fragility of relying on persistable URI grants:
 * some document providers only hand out short-lived permissions, and
 * by the time AlarmActivity fires from a BroadcastReceiver in a
 * freshly-started process the URI may no longer be readable. Copying
 * once, up-front, guarantees we always own the bytes.
 */
object ImageStorage {
    private const val DIR_NAME = "reminder_images"

    /** Copies [source] into internal storage and returns the absolute path, or null on failure. */
    fun copyToInternal(context: Context, source: Uri): String? {
        return runCatching {
            val dir = File(context.filesDir, DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            val file = File(dir, "reminder_${System.currentTimeMillis()}.img")
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            } ?: return@runCatching null
            file.absolutePath
        }.getOrNull()
    }

    /** Best-effort delete of a previously copied image. */
    fun delete(path: String?) {
        if (path == null) return
        runCatching { File(path).delete() }
    }
}
