package com.example.reminderalarm

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Home-screen widget showing the next three upcoming reminders.
 *
 * Layout: header with the palette-tinted background, a "Dziś" title,
 * and a "+" tap target that opens [AddReminderActivity]. Below that,
 * up to three rows with the reminder's time and label. Empty state
 * appears when there's nothing scheduled.
 *
 * The widget auto-refreshes via Android's update mechanism (30 min),
 * and also explicitly whenever [ReminderStore] mutates — see
 * [requestUpdate].
 */
class ReminderWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { renderWidget(context, appWidgetManager, it) }
    }

    private fun renderWidget(
        context: Context,
        manager: AppWidgetManager,
        id: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_add)

        // Palette background
        views.setInt(
            R.id.widgetRoot,
            "setBackgroundColor",
            ThemeManager.primaryColor(context)
        )

        // Upcoming: enabled, future, top 3 by trigger time
        val now = System.currentTimeMillis()
        val upcoming = ReminderStore.all(context)
            .filter { it.enabled && it.triggerAtMillis > now }
            .sortedBy { it.triggerAtMillis }
            .take(3)

        val rows = listOf(
            Triple(R.id.widgetRow1, R.id.widgetRow1Time, R.id.widgetRow1Label),
            Triple(R.id.widgetRow2, R.id.widgetRow2Time, R.id.widgetRow2Label),
            Triple(R.id.widgetRow3, R.id.widgetRow3Time, R.id.widgetRow3Label)
        )

        if (upcoming.isEmpty()) {
            views.setViewVisibility(R.id.widgetEmpty, View.VISIBLE)
            rows.forEach { (rowId, _, _) -> views.setViewVisibility(rowId, View.GONE) }
        } else {
            views.setViewVisibility(R.id.widgetEmpty, View.GONE)
            rows.forEachIndexed { i, (rowId, timeId, labelId) ->
                if (i < upcoming.size) {
                    val r = upcoming[i]
                    views.setViewVisibility(rowId, View.VISIBLE)
                    views.setTextViewText(timeId, formatTriggerForWidget(r.triggerAtMillis))
                    views.setTextViewText(
                        labelId,
                        r.label.ifBlank { context.getString(R.string.untitled) }
                    )
                } else {
                    views.setViewVisibility(rowId, View.GONE)
                }
            }
        }

        // "+" button opens the quick-add screen
        val addIntent = Intent(context, AddReminderActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val addPi = PendingIntent.getActivity(
            context,
            id * 4,
            addIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widgetAddBtn, addPi)

        // Tapping anywhere else opens the main app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPi = PendingIntent.getActivity(
            context,
            id * 4 + 1,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widgetTitle, openPi)
        views.setOnClickPendingIntent(R.id.widgetEmpty, openPi)
        rows.forEach { (rowId, _, _) ->
            views.setOnClickPendingIntent(rowId, openPi)
        }

        manager.updateAppWidget(id, views)
    }

    private fun formatTriggerForWidget(millis: Long): String {
        val now = Calendar.getInstance()
        val trigger = Calendar.getInstance().apply { timeInMillis = millis }
        val nowDoy = now.get(Calendar.DAY_OF_YEAR)
        val nowYear = now.get(Calendar.YEAR)
        val triggerDoy = trigger.get(Calendar.DAY_OF_YEAR)
        val triggerYear = trigger.get(Calendar.YEAR)

        val timeOnly = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
        return when {
            triggerYear == nowYear && triggerDoy == nowDoy ->
                timeOnly
            triggerYear == nowYear && triggerDoy == nowDoy + 1 ->
                "jutro $timeOnly"
            else ->
                SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(millis))
        }
    }

    companion object {
        /**
         * Re-renders every live instance of this widget. Call after any
         * mutation that could affect the "next 3 upcoming" list — adding,
         * editing, deleting, or swiping a reminder, or when a recurring
         * alarm advances to its next occurrence.
         */
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, ReminderWidget::class.java)
            )
            if (ids.isEmpty()) return
            val intent = Intent(context, ReminderWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
