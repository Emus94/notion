package com.example.reminderalarm

import android.app.Application
import com.google.android.material.color.DynamicColors

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModeManager.applyAtStartup(this)
        // Material You (Android 12+) — opt-in. When on, the wallpaper
        // palette overrides our own colours for every activity.
        if (AppSettings.isDynamicColorsEnabled(this)) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
        BackupManager.backup(this)
    }
}
