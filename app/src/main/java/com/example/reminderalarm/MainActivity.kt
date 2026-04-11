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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.reminderalarm.databinding.ActivityMainBinding
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ReminderAdapter
    private var currentTab: Int = TAB_PLANNED
    private var currentProjectFilter: Long? = null // null = "Wszystkie"
    private var currentSearchQuery: String = ""
    private var actionMode: ActionMode? = null

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: ""
        binding.toolbar.title = getString(R.string.app_name) + "  •  v$version"

        binding.toolbar.inflateMenu(R.menu.main_menu)
        setupSearch()
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_sort -> { showSortDialog(); true }
                R.id.action_projects -> {
                    startActivity(Intent(this, ProjectsActivity::class.java))
                    true
                }
                R.id.action_tags -> {
                    startActivity(Intent(this, TagsActivity::class.java))
                    true
                }
                R.id.action_templates -> {
                    startActivity(Intent(this, TemplatesActivity::class.java))
                    true
                }
                R.id.action_stats -> { showStatsDialog(); true }
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
            onClick = { reminder ->
                val intent = Intent(this, AddReminderActivity::class.java).apply {
                    putExtra(AddReminderActivity.EXTRA_EDIT_ID, reminder.id)
                }
                startActivity(intent)
            },
            onLongPress = { reminder -> startSelectionMode(reminder.id) },
            onSelectionChanged = { ids ->
                // Close the action mode as soon as the last item gets
                // unchecked — matches Gmail/Photos muscle memory.
                if (ids.isEmpty()) {
                    actionMode?.finish()
                } else {
                    actionMode?.title = getString(R.string.bulk_selected, ids.size)
                }
            }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        attachSwipeActions()

        binding.fabAdd.setOnClickListener { v ->
            // Micro-interaction: small squish on tap.
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(70).start()
            }.start()
            startActivity(Intent(this, AddReminderActivity::class.java))
        }
        binding.emptyCta.setOnClickListener {
            startActivity(Intent(this, AddReminderActivity::class.java))
        }

        applyPaletteColors()
        ensurePermissions()
    }

    private fun setupSearch() {
        val searchItem = binding.toolbar.menu.findItem(R.id.action_search) ?: return
        val searchView = searchItem.actionView as? SearchView ?: return
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText.orEmpty()
                refresh()
                return true
            }
        })
        searchItem.setOnActionExpandListener(object : android.view.MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: android.view.MenuItem): Boolean = true
            override fun onMenuItemActionCollapse(item: android.view.MenuItem): Boolean {
                currentSearchQuery = ""
                refresh()
                return true
            }
        })
    }

    // ---------------------------------------------------------------
    // Selection mode
    // ---------------------------------------------------------------

    private fun startSelectionMode(firstId: Long) {
        adapter.enterSelectionMode(firstId)
        actionMode = startSupportActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: android.view.Menu): Boolean {
                mode.menuInflater.inflate(R.menu.action_mode_menu, menu)
                mode.title = getString(R.string.bulk_selected, 1)
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: android.view.Menu) = false
            override fun onActionItemClicked(mode: ActionMode, item: android.view.MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.action_bulk_delete -> { bulkDelete(); mode.finish(); true }
                    R.id.action_bulk_complete -> { bulkComplete(); mode.finish(); true }
                    else -> false
                }
            }
            override fun onDestroyActionMode(mode: ActionMode) {
                adapter.exitSelectionMode()
                actionMode = null
            }
        })
    }

    private fun bulkDelete() {
        val selected = adapter.selectedReminders()
        if (selected.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage(getString(R.string.bulk_delete_confirm, selected.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                selected.forEach { r ->
                    AlarmScheduler.cancel(this, r.id)
                    ReminderStore.delete(this, r.id)
                    val path = r.imageUri
                    if (path != null && path.startsWith("/")) ImageStorage.delete(path)
                }
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun bulkComplete() {
        val selected = adapter.selectedReminders()
        if (selected.isEmpty()) return
        selected.forEach { r ->
            AlarmScheduler.cancel(this, r.id)
            ReminderStore.save(this, r.copy(enabled = false))
        }
        refresh()
    }

    private fun showStatsDialog() {
        val s = StatsCalculator.compute(this)
        val msg = buildString {
            append(getString(R.string.stats_week_header))
            append("\n✅  ")
            append(getString(R.string.stats_completed_line, s.completedThisWeek))
            append("\n⏳  ")
            append(getString(R.string.stats_upcoming_line, s.upcomingThisWeek))
            if (s.overdue > 0) {
                append("\n⚠️  ")
                append(getString(R.string.stats_overdue_line, s.overdue))
            }
            append("\n\n")
            append(getString(R.string.stats_streak_header))
            append("\n🔥  ")
            append(
                resources.getQuantityString(
                    R.plurals.stats_streak_days,
                    s.currentStreak,
                    s.currentStreak
                )
            )
            append("\n🏆  ")
            append(getString(R.string.stats_longest_line, s.longestStreak))
            append("\n\n")
            append(getString(R.string.stats_total_line, s.completedTotal))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.stats_title)
            .setMessage(msg)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showSortDialog() {
        val sorts = ListSortManager.Sort.values()
        val names = sorts.map { it.displayName }.toTypedArray()
        val checked = sorts.indexOf(ListSortManager.current(this))
        AlertDialog.Builder(this)
            .setTitle(R.string.sort_title)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                ListSortManager.set(this, sorts[which])
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        applyPaletteColors()
        checkOverlayWarning()
        refresh()
    }

    /**
     * Shows a persistent warning banner whenever the "display over other
     * apps" permission is missing. The banner stays visible on every
     * onResume until the user fixes it. Tapping it opens the system
     * settings page directly.
     */
    private fun checkOverlayWarning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {
            binding.overlayWarning.visibility = View.VISIBLE
            binding.overlayWarning.setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                runCatching { startActivity(intent) }
            }
        } else {
            binding.overlayWarning.visibility = View.GONE
        }
    }

    private fun refresh() {
        val all = ReminderStore.all(this)
        val planned = all.filter { it.enabled && !it.isRepeating() }
        val recurring = all.filter { it.enabled && it.isRepeating() }
        // "Zakończone" always show newest-first regardless of user sort.
        val completed = all.filter { !it.enabled }.sortedByDescending { it.triggerAtMillis }

        val baseForTab = when (currentTab) {
            TAB_PLANNED -> planned
            TAB_RECURRING -> recurring
            else -> completed
        }
        var filtered = if (currentProjectFilter != null) {
            baseForTab.filter { it.projectId == currentProjectFilter }
        } else baseForTab

        // Search: case-insensitive match against label or notes.
        val query = currentSearchQuery.trim()
        if (query.isNotEmpty()) {
            filtered = filtered.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.notes.contains(query, ignoreCase = true)
            }
        }

        // Apply sort (except for Completed tab — kept newest-first).
        if (currentTab != TAB_COMPLETED) {
            filtered = ListSortManager.apply(this, filtered)
        }

        val projectsMap = ProjectStore.all(this).associateBy { it.id }
        val tagsMap = TagStore.all(this).associateBy { it.id }
        adapter.submit(filtered, projectsMap, tagsMap)

        binding.tabs.getTabAt(TAB_PLANNED)?.text =
            getString(R.string.tab_planned) + " (" + planned.size + ")"
        binding.tabs.getTabAt(TAB_RECURRING)?.text =
            getString(R.string.tab_recurring) + " (" + recurring.size + ")"
        binding.tabs.getTabAt(TAB_COMPLETED)?.text =
            getString(R.string.tab_completed) + " (" + completed.size + ")"

        binding.emptyContainer.visibility =
            if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        binding.empty.text = when {
            query.isNotEmpty() -> getString(R.string.empty_search, query)
            currentTab == TAB_PLANNED -> getString(R.string.empty_hint)
            currentTab == TAB_RECURRING -> getString(R.string.empty_recurring)
            else -> getString(R.string.empty_completed)
        }
        // CTA only in the planned tab (nothing to add when viewing history).
        binding.emptyCta.visibility =
            if (currentTab == TAB_PLANNED && query.isEmpty()) View.VISIBLE else View.GONE

        rebuildFilterChips(projectsMap.values.toList())
    }

    private fun rebuildFilterChips(projects: List<Project>) {
        val group = binding.filterChips
        group.removeAllViews()

        // "Wszystkie" — the default, clears the filter.
        val allChip = Chip(this).apply {
            text = getString(R.string.filter_all)
            isCheckable = true
            isChecked = currentProjectFilter == null
            setOnClickListener {
                currentProjectFilter = null
                isChecked = true
                refresh()
            }
        }
        group.addView(allChip)

        projects.forEach { project ->
            val chip = Chip(this).apply {
                text = project.name
                isCheckable = true
                isChecked = currentProjectFilter == project.id
                chipBackgroundColor = ColorStateList.valueOf(
                    androidx.core.graphics.ColorUtils.setAlphaComponent(project.color, 0x22)
                )
                chipStrokeColor = ColorStateList.valueOf(project.color)
                chipStrokeWidth = resources.displayMetrics.density
                setTextColor(project.color)
                setOnClickListener {
                    currentProjectFilter = project.id
                    refresh()
                }
            }
            group.addView(chip)
        }

        // Hide the whole filter bar when there are no projects to pick from.
        binding.filterScroll.visibility =
            if (projects.isEmpty()) View.GONE else View.VISIBLE
    }

    // ---------------------------------------------------------------
    // Swipe: left = delete (undoable), right = mark completed (undoable)
    // ---------------------------------------------------------------

    private fun attachSwipeActions() {
        val redBg = ColorDrawable(0xFFE63946.toInt())
        val greenBg = ColorDrawable(0xFF2E7D32.toInt())

        val callback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val reminder = adapter.getAt(vh.bindingAdapterPosition) ?: run {
                    refresh(); return
                }
                when (direction) {
                    ItemTouchHelper.LEFT -> handleSwipeDelete(reminder)
                    ItemTouchHelper.RIGHT -> handleSwipeComplete(reminder)
                }
            }

            override fun onChildDraw(
                c: Canvas,
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = vh.itemView
                when {
                    dX > 0 -> {
                        greenBg.setBounds(
                            itemView.left,
                            itemView.top,
                            itemView.left + dX.toInt(),
                            itemView.bottom
                        )
                        greenBg.draw(c)
                    }
                    dX < 0 -> {
                        redBg.setBounds(
                            itemView.right + dX.toInt(),
                            itemView.top,
                            itemView.right,
                            itemView.bottom
                        )
                        redBg.draw(c)
                    }
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.list)
    }

    private fun handleSwipeDelete(reminder: Reminder) {
        AlarmScheduler.cancel(this, reminder.id)
        ReminderStore.delete(this, reminder.id)
        refresh()

        Snackbar.make(binding.list, R.string.reminder_deleted, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                ReminderStore.save(this, reminder)
                if (reminder.enabled) AlarmScheduler.schedule(this, reminder)
                refresh()
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    // Not undone — commit the image file deletion.
                    if (event != DISMISS_EVENT_ACTION) {
                        val path = reminder.imageUri
                        if (path != null && path.startsWith("/")) {
                            ImageStorage.delete(path)
                        }
                    }
                }
            })
            .show()
    }

    private fun handleSwipeComplete(reminder: Reminder) {
        // Mark as done: disable it and cancel its scheduled alarm.
        val updated = reminder.copy(enabled = false)
        AlarmScheduler.cancel(this, reminder.id)
        ReminderStore.save(this, updated)
        refresh()

        Snackbar.make(binding.list, R.string.reminder_completed, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                ReminderStore.save(this, reminder)
                if (reminder.enabled) AlarmScheduler.schedule(this, reminder)
                refresh()
            }
            .show()
    }

    // ---------------------------------------------------------------
    // Palette / theme plumbing (unchanged)
    // ---------------------------------------------------------------

    private fun applyPaletteColors() {
        val primary = ThemeManager.primaryColor(this)
        val primaryDark = ThemeManager.primaryDarkColor(this)
        val accent = ThemeManager.accentColor(this)

        // Subtle left-to-right gradient on the whole app bar — primary
        // to a slightly lighter shade so the header gets some depth
        // without screaming for attention.
        val lighter = lightenHsv(primary, 0.1f)
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(primary, lighter)
        )
        binding.appbar.background = gradient
        binding.toolbar.setBackgroundColor(Color.TRANSPARENT)
        binding.tabs.setBackgroundColor(Color.TRANSPARENT)
        binding.fabAdd.backgroundTintList = ColorStateList.valueOf(accent)
        binding.emptyCta.backgroundTintList = ColorStateList.valueOf(accent)
        window.statusBarColor = primaryDark
    }

    private fun lightenHsv(color: Int, amount: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] + amount).coerceAtMost(1f)
        return Color.HSVToColor(hsv)
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
                return
            }
        }
        // Ask the system to exempt the app from battery optimization so
        // AlarmSoundService stays alive reliably. Shows a one-tap system
        // dialog, not a full settings page.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                runCatching { startActivity(intent) }
            }
        }
    }

    companion object {
        private const val TAB_PLANNED = 0
        private const val TAB_RECURRING = 1
        private const val TAB_COMPLETED = 2
    }
}
