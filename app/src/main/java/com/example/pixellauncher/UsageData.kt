package com.example.pixellauncher

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.os.Process
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Context.launcherPreferences by preferencesDataStore(name = "pixel_launcher_settings")

object RailAppSelectionStore {
    private val selectionKey = stringPreferencesKey("rail_app_selection_v1")

    fun selection(context: Context): Flow<List<String>?> = context.launcherPreferences.data.map { prefs ->
        prefs[selectionKey]?.lineSequence()?.filter(String::isNotBlank)?.toList()
    }

    suspend fun save(context: Context, packageNames: List<String>) {
        context.launcherPreferences.edit { prefs ->
            prefs[selectionKey] = packageNames.distinct().take(8).joinToString("\n")
        }
    }
}

data class TodoTask(val id: Int, val title: String, val done: Boolean)

object TodoStore {
    private val tasksKey = stringPreferencesKey("home_todo_tasks_v1")

    fun tasks(context: Context): Flow<List<TodoTask>> = context.launcherPreferences.data.map { prefs ->
        decode(prefs[tasksKey].orEmpty())
    }

    suspend fun add(context: Context, title: String) {
        val clean = title.trim().take(80)
        if (clean.isBlank()) return
        context.launcherPreferences.edit { prefs ->
            val current = decode(prefs[tasksKey].orEmpty())
            prefs[tasksKey] = encode(current + TodoTask((current.maxOfOrNull { it.id } ?: 0) + 1, clean, false))
        }
    }

    suspend fun toggle(context: Context, id: Int) {
        context.launcherPreferences.edit { prefs ->
            prefs[tasksKey] = encode(decode(prefs[tasksKey].orEmpty()).map { if (it.id == id) it.copy(done = !it.done) else it })
        }
    }

    suspend fun remove(context: Context, id: Int) {
        context.launcherPreferences.edit { prefs ->
            prefs[tasksKey] = encode(decode(prefs[tasksKey].orEmpty()).filterNot { it.id == id })
        }
    }

    private fun encode(tasks: List<TodoTask>) = tasks.joinToString("\n") {
        "${it.id}|${if (it.done) 1 else 0}|${Base64.getUrlEncoder().withoutPadding().encodeToString(it.title.toByteArray(Charsets.UTF_8))}"
    }

    private fun decode(raw: String): List<TodoTask> = raw.lineSequence().mapNotNull { line ->
        val fields = line.split('|', limit = 3)
        if (fields.size != 3) return@mapNotNull null
        runCatching {
            TodoTask(fields[0].toInt(), String(Base64.getUrlDecoder().decode(fields[2]), Charsets.UTF_8), fields[1] == "1")
        }.getOrNull()
    }.toList()
}

data class AgendaEvent(val time: String, val title: String, val beginsAt: Long)

fun hasCalendarAccess(context: Context): Boolean =
    context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED

fun todayCalendarEvents(context: Context): List<AgendaEvent> {
    if (!hasCalendarAccess(context)) return emptyList()
    val zone = ZoneId.systemDefault()
    val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .appendPath(start.toString()).appendPath(end.toString()).build()
    val projection = arrayOf(
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.ALL_DAY,
    )
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    return runCatching {
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            buildList {
                while (cursor.moveToNext() && size < 4) {
                    val beginsAt = cursor.getLong(beginCol)
                    val title = cursor.getString(titleCol)?.trim().orEmpty().ifBlank { "UNTITLED EVENT" }
                    val time = if (cursor.getInt(allDayCol) != 0) "ALL DAY" else
                        java.time.Instant.ofEpochMilli(beginsAt).atZone(zone).format(timeFormat)
                    add(AgendaEvent(time, title, beginsAt))
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
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

data class PlayingTrack(val title: String, val artist: String, val packageName: String, val isPlaying: Boolean = false)

data class LockAlert(val app: String, val title: String, val detail: String)

object LockNotifications {
    private val mutableAlerts = MutableStateFlow<List<LockAlert>>(emptyList())
    val alerts = mutableAlerts.asStateFlow()

    internal fun update(alerts: List<LockAlert>) {
        mutableAlerts.value = alerts.take(3)
    }
}

object MusicPlayback {
    private val mutableTrack = MutableStateFlow<PlayingTrack?>(null)
    val track = mutableTrack.asStateFlow()
    @Volatile private var controller: MediaController? = null

    internal fun update(track: PlayingTrack?, activeController: MediaController?) {
        mutableTrack.value = track
        controller = activeController
    }

    fun toggle(): Boolean {
        val active = controller ?: return false
        runCatching {
            val state = active.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING) {
                active.transportControls.pause()
            } else active.transportControls.play()
        }
        return true
    }

    fun skipPrevious() { controller?.transportControls?.skipToPrevious() }
    fun skipNext() { controller?.transportControls?.skipToNext() }

    fun spotifyIsActive(): Boolean = controller?.packageName == "com.spotify.music"

    fun openSpotify(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(launch) }
        } else {
            val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.spotify.music"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(store) }
        }
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

/** Notification access is optional; it reads Spotify metadata and public lock-screen previews in memory. */
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
    private var mediaSessionManager: MediaSessionManager? = null
    private var watchedController: MediaController? = null
    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshActiveMediaNotifications()
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = refreshActiveMediaNotifications()
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { refreshActiveMediaNotifications() }

    override fun onListenerConnected() {
        mediaSessionManager = getSystemService(MediaSessionManager::class.java)
        runCatching {
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                sessionsListener,
                ComponentName(this, MusicNotificationListener::class.java),
            )
        }
        refreshActiveMediaNotifications()
        refreshLockNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        refreshActiveMediaNotifications()
        refreshLockNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        refreshActiveMediaNotifications()
        refreshLockNotifications()
    }

    private fun refreshActiveMediaNotifications() {
        val controller = runCatching {
            mediaSessionManager?.getActiveSessions(ComponentName(this, MusicNotificationListener::class.java))
                ?.firstOrNull { it.packageName == "com.spotify.music" }
        }.getOrNull()
        if (controller !== watchedController) {
            runCatching { watchedController?.unregisterCallback(controllerCallback) }
            watchedController = controller
            runCatching { controller?.registerCallback(controllerCallback) }
        }
        val metadata = controller?.metadata
        val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            .ifBlank { metadata?.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty() }
        val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            .ifBlank { metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty() }
        val notificationTrack = if (title.isBlank()) runCatching { activeNotifications.orEmpty() }
            .getOrDefault(emptyArray()).firstNotNullOfOrNull { sbn ->
                parseMediaNotification(sbn)?.takeIf { it.packageName == "com.spotify.music" }
            } else null
        val playbackState = controller?.playbackState?.state
        val track = when {
            title.isNotBlank() -> PlayingTrack(
                title = title,
                artist = artist,
                packageName = "com.spotify.music",
                isPlaying = playbackState == PlaybackState.STATE_PLAYING,
            )
            notificationTrack != null -> notificationTrack.copy(isPlaying = playbackState == PlaybackState.STATE_PLAYING)
            else -> null
        }
        MusicPlayback.update(track, controller)
    }

    override fun onDestroy() {
        runCatching { mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        runCatching { watchedController?.unregisterCallback(controllerCallback) }
        watchedController = null
        MusicPlayback.update(null, null)
        super.onDestroy()
    }

    private fun refreshLockNotifications() {
        val notifications = runCatching { activeNotifications.orEmpty() }.getOrDefault(emptyArray())
        LockNotifications.update(notifications.mapNotNull(::parseLockNotification))
    }

    private fun parseLockNotification(sbn: StatusBarNotification): LockAlert? {
        if (sbn.packageName == packageName) return null
        val notification = sbn.notification ?: return null
        if (notification.visibility == Notification.VISIBILITY_SECRET) return null

        val appLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName.substringAfterLast('.').uppercase())
        val displayNotification = if (notification.visibility == Notification.VISIBILITY_PUBLIC) {
            notification
        } else {
            notification.publicVersion ?: return LockAlert(appLabel, "CONTENT HIDDEN", "")
        }
        val extras = displayNotification.extras ?: return LockAlert(appLabel, "NEW NOTIFICATION", "")
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            .ifBlank { "NEW NOTIFICATION" }
        val detail = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        return LockAlert(appLabel, title, detail)
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
