package com.example.reminderalarm

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Home screen widget split into two tap targets:
 * - the left half opens AddReminderActivity for quick add
 * - the right half opens the main app
 * The background color follows the currently selected palette.
 */
class ReminderWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_add)
            views.setInt(
                R.id.widgetRoot,
                "setBackgroundColor",
                ThemeManager.primaryColor(context)
            )

            // Left "+": new reminder
            val addIntent = Intent(context, AddReminderActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val addPi = PendingIntent.getActivity(
                context,
                id * 2,
                addIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetAddSection, addPi)

            // Right "⌂": open the app
            val openIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val openPi = PendingIntent.getActivity(
                context,
                id * 2 + 1,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetOpenSection, openPi)

            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
