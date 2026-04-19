package com.example.reminderalarm

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Android Auto entry point. Shows a read-only list of the next few
 * upcoming reminders on the car head unit so the driver can glance at
 * what's coming without touching the phone.
 *
 * Pre-alarm notifications already appear on the car screen because
 * they use [NotificationCompat] with [CATEGORY_REMINDER].
 */
class ReminderCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        }

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen =
            ReminderListCarScreen(carContext)
    }
}

/**
 * Single screen for the car — a [ListTemplate] with up to 6 upcoming
 * reminders (Android Auto template limits). Each row shows the
 * reminder label + formatted time. Location-based reminders show
 * the place name instead of a time.
 */
class ReminderListCarScreen(
    carContext: androidx.car.app.CarContext
) : Screen(carContext) {

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())

    override fun onGetTemplate(): Template {
        val now = System.currentTimeMillis()
        val reminders = ReminderStore.all(carContext)
            .filter { it.enabled }
            .filter { it.triggerAtMillis > now || it.isLocationBased() }
            .sortedBy { it.triggerAtMillis }
            .take(6) // car template limit

        val listBuilder = ItemList.Builder()

        if (reminders.isEmpty()) {
            listBuilder.setNoItemsMessage(
                carContext.getString(R.string.widget_empty)
            )
        } else {
            reminders.forEach { r ->
                val label = r.label.ifBlank {
                    carContext.getString(R.string.untitled)
                }
                val time = formatForCar(r)
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(label)
                        .addText(time)
                        .build()
                )
            }
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun formatForCar(r: Reminder): String {
        if (r.isLocationBased()) {
            val place = r.locationName ?: carContext.getString(R.string.location_tag_generic)
            return "\uD83D\uDCCD $place"
        }
        val now = Calendar.getInstance()
        val trigger = Calendar.getInstance().apply { timeInMillis = r.triggerAtMillis }
        val isToday = now.get(Calendar.DAY_OF_YEAR) == trigger.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == trigger.get(Calendar.YEAR)
        return if (isToday) {
            timeFmt.format(Date(r.triggerAtMillis))
        } else {
            dateFmt.format(Date(r.triggerAtMillis))
        }
    }
}
