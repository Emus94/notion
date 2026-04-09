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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
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
                R.id.action_projects -> {
                    startActivity(Intent(this, ProjectsActivity::class.java))
                    true
                }
                R.id.action_tags -> {
                    startActivity(Intent(this, TagsActivity::class.java))
                    true
                }
                R.id.action_theme -> { showThemeDialog(); true }
                R.id.action_mode -> { showModeDialog(); true }
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
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
        // Re-apply palette colors in case the user changed them in
        // Settings and navigated back here.
        applyPaletteColors()
        refresh()
    }

    private fun refresh() {
        val all = ReminderStore.all(this)
        val planned = all
            .filter { it.enabled && it.recurrence == Recurrence.NONE }
        val recurring = all
            .filter { it.enabled && it.recurrence != Recurrence.NONE }
        val completed = all
            .filter { !it.enabled }
            .sortedByDescending { it.triggerAtMillis }

        val filtered = when (currentTab) {
            TAB_PLANNED -> planned
            TAB_RECURRING -> recurring
            else -> completed
        }
        val projectsMap = ProjectStore.all(this).associateBy { it.id }
        val tagsMap = TagStore.all(this).associateBy { it.id }
        adapter.submit(filtered, projectsMap, tagsMap)

        // Show the count next to each tab label so the user always knows
        // how many items live in each bucket without switching tabs.
        binding.tabs.getTabAt(TAB_PLANNED)?.text =
            getString(R.string.tab_planned) + " (" + planned.size + ")"
        binding.tabs.getTabAt(TAB_RECURRING)?.text =
            getString(R.string.tab_recurring) + " (" + recurring.size + ")"
        binding.tabs.getTabAt(TAB_COMPLETED)?.text =
            getString(R.string.tab_completed) + " (" + completed.size + ")"

        binding.empty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        binding.empty.text = when (currentTab) {
            TAB_PLANNED -> getString(R.string.empty_hint)
            TAB_RECURRING -> getString(R.string.empty_recurring)
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
        ColorPickerHelper.show(
            context = this,
            title = getString(R.string.custom_color_title),
            initialColor = ThemeManager.customPrimary(this)
        ) { color ->
            ThemeManager.setCustom(this, color)
            updateWidgets()
            recreate()
        }
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
                return
            }
        }
        // Android 14+: full-screen notifications need a separate explicit opt-in
        // unless the app is categorised as alarm/calendar. Prompt the user so the
        // alarm can pop over the lock screen reliably.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            if (nm != null && !nm.canUseFullScreenIntent()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:$packageName")
                }
                runCatching { startActivity(intent) }
                return
            }
        }
        // "Display over other apps" — the only reliable way to force an
        // activity from a background BroadcastReceiver on Android 10+.
        // Without it the alarm only shows a heads-up notification when
        // the phone is unlocked.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.overlay_title)
                    .setMessage(R.string.overlay_message)
                    .setPositiveButton(R.string.overlay_open_settings) { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        runCatching { startActivity(intent) }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    companion object {
        private const val TAB_PLANNED = 0
        private const val TAB_RECURRING = 1
        private const val TAB_COMPLETED = 2
    }
}
