package com.example.reminderalarm

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Applies the user's selected palette and optional custom background
 * tint for the current display mode (light / dark) before inflating
 * views.
 */
abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.current(this).themeRes)
        super.onCreate(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        applyCustomBackground()
    }

    /**
     * If the user has set a custom window background for the current
     * display mode, paint the root view with it. Called in onPostCreate
     * so the content view has already been set by the concrete activity.
     */
    private fun applyCustomBackground() {
        val nightMode = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        val customBg = if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            AppSettings.getCustomDarkBg(this)
        } else {
            AppSettings.getCustomLightBg(this)
        }
        if (customBg != null) {
            window.decorView.setBackgroundColor(customBg)
        }
    }
}
