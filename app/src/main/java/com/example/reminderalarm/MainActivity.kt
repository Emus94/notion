package com.example.reminderalarm

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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

        // Show version in the toolbar so it's obvious which build is installed.
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: ""
        binding.toolbar.title = getString(R.string.app_name) + "  •  v$version"

        // Overflow menu with theme picker.
        binding.toolbar.inflateMenu(R.menu.main_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_theme) {
                showThemeDialog(); true
            } else false
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
        binding.empty.visibility =
            if (adapter.itemCount == 0) android.view.View.VISIBLE else android.view.View.GONE
        binding.empty.text = when (currentTab) {
            TAB_PLANNED -> getString(R.string.empty_hint)
            else -> getString(R.string.empty_completed)
        }
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
                if (picked != current) {
                    ThemeManager.set(this, picked)
                    dialog.dismiss()
                    recreate()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
