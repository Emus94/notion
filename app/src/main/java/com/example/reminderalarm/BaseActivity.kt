package com.example.reminderalarm

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Applies the user's selected palette before inflating views.
 */
abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.current(this).themeRes)
        super.onCreate(savedInstanceState)
    }
}
