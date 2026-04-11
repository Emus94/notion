package com.example.reminderalarm

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModeManager.applyAtStartup(this)
        BackupManager.backup(this)
    }
}
