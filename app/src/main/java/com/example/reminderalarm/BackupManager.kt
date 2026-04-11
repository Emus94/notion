package com.example.reminderalarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Creates JSON backups of all app data.
 *
 * Two tiers:
 *  - **Internal** — written to [filesDir]/backups/ on every app launch.
 *    Survives app updates but is deleted if the user uninstalls the app.
 *  - **External** — written to a user-picked folder (via Storage Access
 *    Framework) on every launch. Survives uninstall. The folder can also
 *    be a Google Drive folder if the Drive app is installed — the system
 *    picker will show it automatically.
 *
 * Both tiers keep at most [MAX_BACKUPS] files. The external write runs
 * on a background thread so cloud providers don't block app startup.
 */
object BackupManager {

    private const val MAX_BACKUPS = 5
    private const val PREFS = "backup_prefs"
    private const val KEY_LAST_BACKUP = "last_backup_time"
    private const val KEY_TREE_URI = "backup_tree_uri"
    private val nameFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    // -----------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------

    fun backup(context: Context) {
        val filename = "backup_${nameFmt.format(Date())}.json"

        // Internal (fast, always).
        backupInternal(context, filename)

        // External (may be slow — run off the main thread).
        val externalUri = getExternalFolder(context)
        if (externalUri != null) {
            val appContext = context.applicationContext
            Thread {
                runCatching { backupExternal(appContext, externalUri, filename) }
            }.start()
        }
    }

    fun lastBackupTime(context: Context): Long =
        prefs(context).getLong(KEY_LAST_BACKUP, 0L)

    fun getExternalFolder(context: Context): Uri? {
        val raw = prefs(context).getString(KEY_TREE_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    /** Persists a user-picked SAF tree URI. Takes the persistable permission. */
    fun setExternalFolder(context: Context, uri: Uri?) {
        if (uri == null) {
            // Release previous permission, if any.
            val prev = getExternalFolder(context)
            if (prev != null) {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        prev,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            }
            prefs(context).edit().remove(KEY_TREE_URI).apply()
            return
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        prefs(context).edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    /** Human-readable name of the currently selected external folder. */
    fun getExternalFolderName(context: Context): String? {
        val uri = getExternalFolder(context) ?: return null
        return runCatching {
            DocumentFile.fromTreeUri(context, uri)?.name
        }.getOrNull()
    }

    // -----------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------

    private fun backupInternal(context: Context, filename: String) {
        val dir = File(context.filesDir, "backups").also { it.mkdirs() }
        val file = File(dir, filename)
        try {
            file.outputStream().use { ExportImportManager.exportToStream(context, it) }
            prefs(context).edit().putLong(KEY_LAST_BACKUP, System.currentTimeMillis()).apply()
            dir.listFiles { f -> f.name.startsWith("backup_") && f.name.endsWith(".json") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_BACKUPS)
                ?.forEach { it.delete() }
        } catch (_: Exception) {
            file.delete()
        }
    }

    private fun backupExternal(context: Context, treeUri: Uri, filename: String) {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return
        if (!tree.canWrite()) return

        // DocumentFile.createFile appends its own extension, so strip ".json".
        val displayName = filename.removeSuffix(".json")
        val newFile = tree.createFile(ExportImportManager.EXPORT_MIME, displayName) ?: return

        try {
            context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                ExportImportManager.exportToStream(context, out)
            }
        } catch (e: Exception) {
            runCatching { newFile.delete() }
            return
        }

        // Prune older backups in the external folder.
        runCatching {
            tree.listFiles()
                .filter { f ->
                    val n = f.name ?: return@filter false
                    n.startsWith("backup_") && n.endsWith(".json")
                }
                .sortedByDescending { it.lastModified() }
                .drop(MAX_BACKUPS)
                .forEach { it.delete() }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
