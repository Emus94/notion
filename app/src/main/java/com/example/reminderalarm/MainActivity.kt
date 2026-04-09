package com.example.reminderalarm

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.reminderalarm.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayout

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ReminderAdapter
    private var currentTab: Int = TAB_PLANNED

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore result; alarm still rings via foreground service */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: ""
        binding.toolbar.title = getString(R.string.app_name) + "  •  v$version"

        binding.toolbar.inflateMenu(R.menu.main_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_theme -> { showThemeDialog(); true }
                R.id.action_mode -> { showModeDialog(); true }
                else -> false
            }
        }

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                refresh()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        adapter = ReminderAdapter(
            onDelete = { reminder ->
                AlarmScheduler.cancel(this, reminder.id)
                ReminderStore.delete(this, reminder.id)
                refresh()
            },
            onClick = { reminder ->
                val intent = Intent(this, AddReminderActivity::class.java).apply {
                    putExtra(AddReminderActivity.EXTRA_EDIT_ID, reminder.id)
                }
                startActivity(intent)
            }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddReminderActivity::class.java))
        }

        applyPaletteColors()
        ensurePermissions()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val all = ReminderStore.all(this)
        val filtered = when (currentTab) {
            TAB_PLANNED -> all.filter { it.enabled }
            else -> all.filter { !it.enabled }.sortedByDescending { it.triggerAtMillis }
        }
        adapter.submit(filtered)
        binding.empty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        binding.empty.text = when (currentTab) {
            TAB_PLANNED -> getString(R.string.empty_hint)
            else -> getString(R.string.empty_completed)
        }
    }

    private fun applyPaletteColors() {
        val primary = ThemeManager.primaryColor(this)
        val primaryDark = ThemeManager.primaryDarkColor(this)
        val accent = ThemeManager.accentColor(this)

        binding.appbar.setBackgroundColor(primary)
        binding.toolbar.setBackgroundColor(primary)
        binding.tabs.setBackgroundColor(primary)
        binding.fabAdd.backgroundTintList = ColorStateList.valueOf(accent)
        window.statusBarColor = primaryDark
    }

    private fun showThemeDialog() {
        val palettes = ThemeManager.Palette.values()
        val current = ThemeManager.current(this)
        val checked = palettes.indexOf(current)
        val names = palettes.map { it.displayName }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.theme_title)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                val picked = palettes[which]
                dialog.dismiss()
                if (picked == ThemeManager.Palette.CUSTOM) {
                    showCustomColorDialog()
                } else if (picked != current) {
                    ThemeManager.set(this, picked)
                    updateWidgets()
                    recreate()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCustomColorDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_color_picker, null)
        val preview = view.findViewById<View>(R.id.preview)
        val hexLabel = view.findViewById<TextView>(R.id.hexLabel)
        val hueBar = view.findViewById<SeekBar>(R.id.hueBar)
        val satBar = view.findViewById<SeekBar>(R.id.satBar)
        val valBar = view.findViewById<SeekBar>(R.id.valBar)

        val startColor = ThemeManager.customPrimary(this)
        val hsv = FloatArray(3)
        Color.colorToHSV(startColor, hsv)
        hueBar.progress = hsv[0].toInt()
        satBar.progress = (hsv[1] * 100).toInt().coerceAtLeast(50)
        valBar.progress = (hsv[2] * 100).toInt().coerceAtLeast(50)

        fun update() {
            val color = Color.HSVToColor(
                floatArrayOf(
                    hueBar.progress.toFloat(),
                    satBar.progress / 100f,
                    valBar.progress / 100f
                )
            )
            preview.setBackgroundColor(color)
            hexLabel.text = String.format("#%06X", 0xFFFFFF and color)
        }
        update()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { update() }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        hueBar.setOnSeekBarChangeListener(listener)
        satBar.setOnSeekBarChangeListener(listener)
        valBar.setOnSeekBarChangeListener(listener)

        AlertDialog.Builder(this)
            .setTitle(R.string.custom_color_title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val color = Color.HSVToColor(
                    floatArrayOf(
                        hueBar.progress.toFloat(),
                        satBar.progress / 100f,
                        valBar.progress / 100f
                    )
                )
                ThemeManager.setCustom(this, color)
                updateWidgets()
                recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showModeDialog() {
        val modes = AppModeManager.Mode.values()
        val current = AppModeManager.current(this)
        val checked = modes.indexOf(current)
        val names = modes.map { it.displayName }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.mode_title)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                val picked = modes[which]
                dialog.dismiss()
                if (picked != current) {
                    AppModeManager.set(this, picked)
                    recreate()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateWidgets() {
        val mgr = AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(ComponentName(this, ReminderWidget::class.java))
        if (ids.isNotEmpty()) {
            val intent = Intent(this, ReminderWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            sendBroadcast(intent)
        }
    }

    private fun ensurePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                runCatching { startActivity(intent) }
            }
        }
    }

    companion object {
        private const val TAB_PLANNED = 0
    }
}
