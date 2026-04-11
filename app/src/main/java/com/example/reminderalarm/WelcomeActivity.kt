package com.example.reminderalarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.reminderalarm.databinding.ActivityWelcomeBinding
import com.example.reminderalarm.databinding.ItemWelcomePermissionBinding

/**
 * First-launch onboarding — walks the user through every permission
 * ForgetMeNot needs. Each row is a tappable card that jumps to the
 * right system screen (or fires a runtime prompt, for the ones where
 * that's possible). Statuses refresh on every onResume so coming back
 * from Settings paints the green check immediately.
 *
 * Also reachable from Settings → "Pokaż powitanie ponownie" so the
 * user can re-audit permissions later without reinstalling.
 */
class WelcomeActivity : BaseActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    /** Card bindings kept so onResume can re-check status without re-inflating. */
    private data class Card(
        val binding: ItemWelcomePermissionBinding,
        val isGranted: () -> Boolean
    )
    private val cards = mutableListOf<Card>()

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshAll() }

    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshAll() }

    private val micPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshAll() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // REQUIRED
        addCard(
            binding.requiredContainer,
            icon = "🔔",
            titleRes = R.string.perm_notif_title,
            descRes = R.string.perm_notif_desc,
            isGranted = ::isNotificationsGranted,
            onClick = { requestNotifications() }
        )
        addCard(
            binding.requiredContainer,
            icon = "⏰",
            titleRes = R.string.perm_exact_title,
            descRes = R.string.perm_exact_desc,
            isGranted = ::isExactAlarmGranted,
            onClick = { openExactAlarmSettings() }
        )
        addCard(
            binding.requiredContainer,
            icon = "📱",
            titleRes = R.string.perm_fullscreen_title,
            descRes = R.string.perm_fullscreen_desc,
            isGranted = ::isFullScreenIntentGranted,
            onClick = { openFullScreenIntentSettings() }
        )
        addCard(
            binding.requiredContainer,
            icon = "🪟",
            titleRes = R.string.perm_overlay_title,
            descRes = R.string.perm_overlay_desc,
            isGranted = ::isOverlayGranted,
            onClick = { openOverlaySettings() }
        )
        addCard(
            binding.requiredContainer,
            icon = "🔋",
            titleRes = R.string.perm_battery_title,
            descRes = R.string.perm_battery_desc,
            isGranted = ::isBatteryOptIgnored,
            onClick = { openBatteryOptSettings() }
        )

        // OPTIONAL
        addCard(
            binding.optionalContainer,
            icon = "📍",
            titleRes = R.string.perm_location_title,
            descRes = R.string.perm_location_desc,
            isGranted = ::isLocationGranted,
            onClick = { requestLocation() }
        )
        addCard(
            binding.optionalContainer,
            icon = "🎤",
            titleRes = R.string.perm_mic_title,
            descRes = R.string.perm_mic_desc,
            isGranted = ::isMicGranted,
            onClick = { requestMic() }
        )

        binding.btnContinue.setOnClickListener {
            AppSettings.setWelcomeCompleted(this, true)
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    // ------------------------------------------------------------------
    // Card inflation
    // ------------------------------------------------------------------

    private fun addCard(
        container: ViewGroup,
        icon: String,
        titleRes: Int,
        descRes: Int,
        isGranted: () -> Boolean,
        onClick: () -> Unit
    ) {
        val cardBinding = ItemWelcomePermissionBinding
            .inflate(LayoutInflater.from(this), container, false)
        cardBinding.permIcon.text = icon
        cardBinding.permTitle.setText(titleRes)
        cardBinding.permDescription.setText(descRes)
        cardBinding.root.setOnClickListener { onClick() }
        container.addView(cardBinding.root)
        cards.add(Card(cardBinding, isGranted))
    }

    private fun refreshAll() {
        for (card in cards) {
            val granted = card.isGranted()
            card.binding.permStatus.text = if (granted) "✓" else "!"
            card.binding.permStatus.setTextColor(
                if (granted) 0xFF2E7D32.toInt() else 0xFFE53935.toInt()
            )
        }
    }

    // ------------------------------------------------------------------
    // Permission checks
    // ------------------------------------------------------------------

    private fun isNotificationsGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun isExactAlarmGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    private fun isFullScreenIntentGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = getSystemService(NotificationManager::class.java) ?: return true
        return nm.canUseFullScreenIntent()
    }

    private fun isOverlayGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return Settings.canDrawOverlays(this)
    }

    private fun isBatteryOptIgnored(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isLocationGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun isMicGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------------
    // Permission grant actions
    // ------------------------------------------------------------------

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            runCatching { startActivity(intent) }
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
    }

    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(intent) }
    }

    private fun openBatteryOptSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(intent) }
    }

    private fun requestLocation() {
        locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun requestMic() {
        micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
