package com.example.pixellauncher

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId

private val Context.launcherPreferences by preferencesDataStore(name = "pixel_launcher_settings")

object AppLimitStore {
    private const val PREFIX = "daily_limit_minutes_"
    const val DEFAULT_LIMIT_MINUTES = 120

    fun limits(context: Context): Flow<Map<String, Int>> = context.launcherPreferences.data.map { prefs ->
        prefs.asMap().entries.mapNotNull { (key, value) ->
            if (key.name.startsWith(PREFIX)) {
                val packageName = key.name.removePrefix(PREFIX)
                val minutes = value as? Int
                if (packageName.isNotBlank() && minutes != null) packageName to minutes else null
            } else null
        }.toMap()
    }

    suspend fun setLimit(context: Context, packageName: String, minutes: Int) {
        if (packageName.isBlank()) return
        context.launcherPreferences.edit { prefs ->
            prefs[intPreferencesKey(PREFIX + packageName)] = minutes.coerceIn(15, 24 * 60)
        }
    }

    fun getLimit(limits: Map<String, Int>, packageName: String?): Int =
        packageName?.let(limits::get) ?: DEFAULT_LIMIT_MINUTES
}

fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
    return runCatching {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
}

/** Opens Android's special-access page, then falls back to this app's settings if an OEM omits it. */
fun openUsageAccessSettings(context: Context) {
    val usageSettings = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(usageSettings); true }.getOrDefault(false)) return

    val appDetails = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(appDetails) }
}

fun todayUsageMillis(context: Context, packageNames: Set<String>): Map<String, Long> {
    if (packageNames.isEmpty() || !hasUsageAccess(context)) return emptyMap()
    val startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val now = System.currentTimeMillis()
    val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyMap()
    return runCatching {
        val stats = manager.queryAndAggregateUsageStats(startOfToday, now).orEmpty()
        packageNames.associateWith { packageName -> stats[packageName]?.totalTimeInForeground ?: 0L }
    }.getOrDefault(emptyMap())
}

data class PlayingTrack(val title: String, val artist: String, val packageName: String)

object MusicPlayback {
    private val mutableTrack = MutableStateFlow<PlayingTrack?>(null)
    val track = mutableTrack.asStateFlow()

    internal fun update(track: PlayingTrack?) {
        mutableTrack.value = track
    }
}

fun hasNotificationAccess(context: Context): Boolean {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return false
    return runCatching {
        manager.isNotificationListenerAccessGranted(
            ComponentName(context, MusicNotificationListener::class.java),
        )
    }.getOrDefault(false)
}

/** Notification access is optional and only used to read media title/artist from media notifications. */
fun openNotificationAccessSettings(context: Context) {
    val component = ComponentName(context, MusicNotificationListener::class.java)
    val detailSettings = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(detailSettings); true }.getOrDefault(false)) return

    val generalSettings = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(generalSettings); true }.getOrDefault(false)) return

    val appDetails = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(appDetails) }
}

class MusicNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        refreshActiveMediaNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        parseMediaNotification(sbn)?.let(MusicPlayback::update)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        refreshActiveMediaNotifications()
    }

    private fun refreshActiveMediaNotifications() {
        val current = runCatching { activeNotifications.orEmpty() }.getOrDefault(emptyArray())
            .mapNotNull(::parseMediaNotification)
            .lastOrNull()
        MusicPlayback.update(current)
    }

    private fun parseMediaNotification(sbn: StatusBarNotification): PlayingTrack? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null
        val hasMediaSession = extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
        if (notification.category != Notification.CATEGORY_TRANSPORT && !hasMediaSession) return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        if (title.isBlank()) return null
        val artist = listOf(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
        return PlayingTrack(title, artist, sbn.packageName)
    }
}
