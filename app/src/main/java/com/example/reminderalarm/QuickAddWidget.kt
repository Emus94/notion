package com.example.reminderalarm

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * The tiny "two icons" home-screen widget — half "Otwórz" (opens
 * the app), half "Dodaj" (jumps straight into [AddReminderActivity]).
 *
 * This is the lightweight companion to [ReminderWidget] (the big
 * "Dziś" widget). Both providers coexist so users can pick whichever
 * feels right for their launcher setup.
 */
class QuickAddWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick)
            views.setInt(
                R.id.quickRoot,
                "setBackgroundColor",
                ThemeManager.primaryColor(context)
            )

            // Left "⌂": open the app
            val openIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val openPi = PendingIntent.getActivity(
                context,
                id * 2,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.quickOpenSection, openPi)

            // Centre "🎤": open reminder form and immediately start voice
            val voiceIntent = Intent(context, AddReminderActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AddReminderActivity.EXTRA_START_VOICE, true)
            }
            val voicePi = PendingIntent.getActivity(
                context,
                id * 4 + 2,
                voiceIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.quickVoiceSection, voicePi)

            // Right "+": new reminder (normal, no voice)
            val addIntent = Intent(context, AddReminderActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val addPi = PendingIntent.getActivity(
                context,
                id * 4 + 3,
                addIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.quickAddSection, addPi)

            appWidgetManager.updateAppWidget(id, views)
        }
    }

    companion object {
        /** Re-renders every placed instance — called on theme changes. */
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, QuickAddWidget::class.java)
            )
            if (ids.isEmpty()) return
            val intent = Intent(context, QuickAddWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
