package com.example.reminderalarm

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Creates a local JSON backup of all app data every time the app launches.
 * Backups are stored in [filesDir]/backups/ and at most [MAX_BACKUPS] are
 * kept — older ones are pruned automatically.
 */
object BackupManager {

    private const val MAX_BACKUPS = 5
    private const val PREFS = "backup_prefs"
    private const val KEY_LAST_BACKUP = "last_backup_time"
    private val nameFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun backup(context: Context) {
        val dir = File(context.filesDir, "backups").also { it.mkdirs() }
        val file = File(dir, "backup_${nameFmt.format(Date())}.json")
        try {
            file.outputStream().use { ExportImportManager.exportToStream(context, it) }
            prefs(context).edit().putLong(KEY_LAST_BACKUP, System.currentTimeMillis()).apply()
            pruneOldBackups(dir)
        } catch (_: Exception) {
            file.delete()
        }
    }

    fun lastBackupTime(context: Context): Long =
        prefs(context).getLong(KEY_LAST_BACKUP, 0L)

    private fun pruneOldBackups(dir: File) {
        dir.listFiles { f -> f.name.startsWith("backup_") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_BACKUPS)
            ?.forEach { it.delete() }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
