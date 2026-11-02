package com.example.pixellauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.app.ActivityManager
import android.media.AudioManager
import android.os.Environment
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.floor

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FMT = DateTimeFormatter.ofPattern("EEE dd MMM yyyy", Locale.ENGLISH)
private val Mint = Color(0xFF6FE39A)
private val Yellow = Color(0xFFF2D94E)
private val PixelSteps = Easing { floor(it * 6f) / 6f }

@Composable
private fun rememberCursorAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "terminal cursor")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursor blink",
    ).value
}

private fun readBatteryPercent(context: Context): Int {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val property = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    if (property in 0..100) return property
    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0
    val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else 0
}

private data class SystemMetrics(val ramPercent: Int, val storagePercent: Int)

private fun readSystemMetrics(context: Context): SystemMetrics {
    val memory = ActivityManager.MemoryInfo()
    (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memory)
    val ram = if (memory.totalMem > 0) {
        ((memory.totalMem - memory.availMem).toDouble() / memory.totalMem * 100).roundToInt().coerceIn(0, 100)
    } else 0

    val storage = runCatching {
        val stats = StatFs(Environment.getDataDirectory().path)
        val total = stats.totalBytes
        if (total > 0) ((total - stats.availableBytes).toDouble() / total * 100).roundToInt().coerceIn(0, 100) else 0
    }.getOrDefault(0)
    return SystemMetrics(ram, storage)
}

@Composable
fun HomeScreen(homeTick: Int, resumeTick: Int = 0) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var menuOpen by remember { mutableStateOf(false) }
    var returningToMenuFromApp by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var editingApp by remember { mutableStateOf<AppInfo?>(null) }
    var addingTodo by remember { mutableStateOf(false) }
    var usageMillis by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var calendarAllowed by remember(resumeTick) { mutableStateOf(hasCalendarAccess(ctx)) }
    var agenda by remember { mutableStateOf<List<AgendaEvent>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val usageAllowed = remember(resumeTick) { hasUsageAccess(ctx) }
    val notificationAllowed = remember(resumeTick) { hasNotificationAccess(ctx) }
    val dailyLimits by remember(ctx) { AppLimitStore.limits(ctx) }.collectAsState(initial = emptyMap())
    val todos by remember(ctx) { TodoStore.tasks(ctx) }.collectAsState(initial = emptyList())
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        calendarAllowed = granted
    }
    val track by MusicPlayback.track.collectAsState()
    val batteryPercent by produceState(initialValue = readBatteryPercent(ctx), key1 = ctx) {
        while (true) {
            value = readBatteryPercent(ctx)
            delay(30_000)
        }
    }
    val systemMetrics by produceState(initialValue = SystemMetrics(0, 0), key1 = ctx) {
        while (true) {
            value = withContext(Dispatchers.IO) { readSystemMetrics(ctx) }
            delay(5_000)
        }
    }
    val railShortcuts = remember(apps) { resolveRailShortcuts(apps) }
    val usagePackages = remember(railShortcuts) { railShortcuts.mapNotNull { it.app?.pkg }.toSet() }
    val ringItems = remember(railShortcuts, usageMillis, dailyLimits, batteryPercent, usageAllowed) {
        buildRingItems(railShortcuts, usageMillis, dailyLimits, batteryPercent, usageAllowed)
    }

    // Reload the app list when something is installed, updated or removed.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                reload++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { ctx.unregisterReceiver(receiver) }
    }
    LaunchedEffect(reload) { apps = withContext(Dispatchers.Default) { loadApps(ctx) } }
    LaunchedEffect(usageAllowed, resumeTick, usagePackages) {
        if (!usageAllowed) {
            usageMillis = emptyMap()
            return@LaunchedEffect
        }
        while (isActive) {
            usageMillis = withContext(Dispatchers.IO) { todayUsageMillis(ctx, usagePackages) }
            delay(60_000)
        }
    }
    LaunchedEffect(homeTick) {
        if (!returningToMenuFromApp) menuOpen = false
    }
    LaunchedEffect(resumeTick) {
        if (returningToMenuFromApp) returningToMenuFromApp = false
    }
    LaunchedEffect(menuOpen) { if (!menuOpen) query = "" }

    // On a launcher, Back never leaves home. It only closes the app menu.
    BackHandler(enabled = true) {
        when {
            editingApp != null -> editingApp = null
            menuOpen -> menuOpen = false
        }
    }

    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now(); delay(1000)
        }
    }
    val deviceName = remember(ctx, resumeTick) { readDeviceName(ctx) }
    LaunchedEffect(calendarAllowed, now.toLocalDate(), resumeTick) {
        agenda = if (calendarAllowed) withContext(Dispatchers.IO) { todayCalendarEvents(ctx) } else emptyList()
    }

    Box(
        Modifier.fillMaxSize()
            .background(Px.bg)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1C100B), Px.bg),
                        center = Offset(size.width * 0.5f, size.height * 0.55f),
                        radius = size.maxDimension * 0.72f,
                    ),
                )
            },
    ) {
        PixelDust(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 48.dp, top = 48.dp, bottom = 48.dp, end = 168.dp)
                .blur(if (menuOpen || editingApp != null) 8.dp else 0.dp),
        ) {
            TopBar(batteryPercent, deviceName)
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                LeftDashboard(
                    now = now,
                    deviceName = deviceName,
                    todos = todos,
                    onToggleTodo = { id -> scope.launch { TodoStore.toggle(ctx, id) } },
                    onRemoveTodo = { id -> scope.launch { TodoStore.remove(ctx, id) } },
                    onAddTodo = { addingTodo = true },
                    modifier = Modifier.weight(0.42f).fillMaxHeight(),
                )
                RightDashboard(
                    ctx = ctx,
                    systemMetrics = systemMetrics,
                    notificationAllowed = notificationAllowed,
                    track = track,
                    agenda = agenda,
                    calendarAllowed = calendarAllowed,
                    onEnableUsage = { openUsageAccessSettings(ctx) },
                    onEnableNotifications = { openNotificationAccessSettings(ctx) },
                    onEnableCalendar = { calendarPermission.launch(android.Manifest.permission.READ_CALENDAR) },
                    modifier = Modifier.weight(0.58f).fillMaxHeight(),
                )
            }

            Spacer(Modifier.height(14.dp))
            SearchWindow(appCount = apps.size, modifier = Modifier.fillMaxWidth()) { menuOpen = true }
        }

        EdgeShortcutRail(
            rings = ringItems,
            onLaunch = { launchApp(ctx, it) },
            onEnableUsage = { openUsageAccessSettings(ctx) },
            onEditLimit = { editingApp = it },
            modifier = Modifier.align(Alignment.CenterEnd).blur(if (menuOpen || editingApp != null) 8.dp else 0.dp),
        )

        // Keep the working Milestone 1 app list and search while the home layout is rebuilt.
        AnimatedVisibility(
            visible = menuOpen,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.88f, animationSpec = tween(280, easing = PixelSteps)),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.96f, animationSpec = tween(140, easing = PixelSteps)),
        ) {
            AppMenu(
                apps = apps,
                query = query,
                onQuery = { query = it },
                onClose = { menuOpen = false },
                onLaunch = {
                    returningToMenuFromApp = true
                    launchApp(ctx, it)
                },
            )
        }

        editingApp?.let { app ->
            DailyLimitDialog(
                app = app,
                currentLimit = AppLimitStore.getLimit(dailyLimits, app.pkg),
                usedMillis = usageMillis[app.pkg] ?: 0L,
                onDismiss = { editingApp = null },
                onSave = { minutes ->
                    scope.launch { AppLimitStore.setLimit(ctx, app.pkg, minutes) }
                    editingApp = null
                },
            )
        }

        if (addingTodo) {
            TodoTaskDialog(
                onDismiss = { addingTodo = false },
                onSave = { title ->
                    scope.launch { TodoStore.add(ctx, title) }
                    addingTodo = false
                },
            )
        }
    }
}

@Composable
private fun TopBar(batteryPercent: Int, deviceName: String) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).drawBehind {
            val y = size.height - 1.dp.toPx()
            drawLine(Color(0xFF3A2419), Offset.Zero.copy(y = y), Offset(size.width, y), 1.dp.toPx())
            drawLine(Px.coral, Offset.Zero.copy(y = y), Offset(176.dp.toPx(), y), 2.dp.toPx())
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PxText("> PIXEL.OS", 15.sp, Px.coral, FontWeight.Bold, fontFamily = PixelFonts.display)
        Spacer(Modifier.width(18.dp))
        PxText("${deviceName.uppercase(Locale.ENGLISH)} // ONLINE", 19.sp, Px.dim, maxLines = 1)
        Spacer(Modifier.weight(1f))
        PxText("WIFI OK", 24.sp, Px.dim)
        Spacer(Modifier.width(22.dp))
        PxText("BAT $batteryPercent%", 24.sp, Px.dim)
        Spacer(Modifier.width(10.dp))
        SegmentedBar(batteryPercent, Px.coral, Modifier.width(72.dp))
    }
}

@Composable
private fun LeftDashboard(
    now: LocalDateTime,
    deviceName: String,
    todos: List<TodoTask>,
    onToggleTodo: (Int) -> Unit,
    onRemoveTodo: (Int) -> Unit,
    onAddTodo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cursorAlpha = rememberCursorAlpha()
    Column(modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PxText(
                    "GOOD ${
                        now.format(DateTimeFormatter.ofPattern("a", Locale.ENGLISH))
                            .let { if (it == "AM") "MORNING" else "EVENING" }
                    }", 28.sp, Px.coral
                )
                PxText("_", 28.sp, Px.coral, modifier = Modifier.graphicsLayer { alpha = cursorAlpha })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val clock = now.format(TIME_FMT)
                PxText(clock.substringBefore(':'), 76.sp, Px.cream, FontWeight.Bold, fontFamily = PixelFonts.display)
                PxText(
                    ":",
                    76.sp,
                    Px.cream,
                    FontWeight.Bold,
                    modifier = Modifier.graphicsLayer { alpha = cursorAlpha },
                    fontFamily = PixelFonts.display
                )
                PxText(clock.substringAfter(':'), 76.sp, Px.cream, FontWeight.Bold, fontFamily = PixelFonts.display)
            }
            PxText(now.format(DATE_FMT).uppercase(Locale.ENGLISH), 28.sp, Px.dim)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(88.dp), verticalAlignment = Alignment.CenterVertically) {
            PixelMascot(Modifier.width(108.dp).fillMaxHeight())
            Spacer(Modifier.width(10.dp))
            WindowPanel(modifier = Modifier.weight(1f).fillMaxHeight(), entranceDelayMs = 100) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PxText("> hello, $deviceName", 24.sp, Px.cream, maxLines = 2)
                    PxText("_", 24.sp, Px.cream, modifier = Modifier.graphicsLayer { alpha = cursorAlpha })
                }
                Spacer(Modifier.height(4.dp))
                PxText("${todos.count { !it.done }} TASKS LEFT TODAY", 20.sp, Px.dim)
            }
        }
        Spacer(Modifier.height(12.dp))
        WindowPanel(title = "TODO.md", modifier = Modifier.fillMaxWidth().weight(1f), entranceDelayMs = 250) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (todos.isEmpty()) PxText("NO TASKS YET", 22.sp, Px.dim)
                todos.forEach { task -> TodoRow(task, onToggleTodo, onRemoveTodo) }
            }
            SettingsActionButton("+ ADD TASK", onAddTodo)
        }
    }
}

@Composable
private fun TodoRow(task: TodoTask, onToggle: (Int) -> Unit, onRemove: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        PxText(
            if (task.done) "[x]" else "[ ]", 22.sp, if (task.done) Px.coral else Px.dim,
            modifier = Modifier.clickable { onToggle(task.id) })
        Spacer(Modifier.width(8.dp))
        PxText(
            task.title, 22.sp, if (task.done) Px.dim else Px.cream,
            modifier = Modifier.weight(1f).clickable { onToggle(task.id) }, maxLines = 1
        )
        PxText("×", 24.sp, Px.dim, modifier = Modifier.clickable { onRemove(task.id) })
    }
}

@Composable
private fun PixelMascot(modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "mascot bob")
    val bob by motion.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "mascot bob",
    )
    val blink by motion.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "mascot blink",
    )
    Canvas(modifier.graphicsLayer { translationY = -bob * 8.dp.toPx() }) {
        val cellWidth = size.width / 14f
        val cellHeight = size.height / 12f
        fun pixel(x: Int, y: Int, color: Color) {
            drawRect(color, Offset(x * cellWidth, y * cellHeight), Size(cellWidth, cellHeight))
        }

        // Head, shoulders, arms, and body from the 14 x 12 reference grid.
        for (x in 3..10) pixel(x, 0, Color(0xFFF4A384))
        for (y in 1..3) for (x in 2..11) pixel(x, y, Px.coral)
        for (y in 4..5) for (x in 0..13) pixel(x, y, Px.coral)
        for (y in 6..7) for (x in 2..11) pixel(x, y, Px.coral)
        for (x in 2..11) pixel(x, 8, Px.coralDark)
        for (y in 9..11) {
            for (x in 2..3) pixel(x, y, Px.coralDark)
            pixel(5, y, Px.coralDark)
            pixel(8, y, Px.coralDark)
            for (x in 10..11) pixel(x, y, Px.coralDark)
        }
        // Dark pixel eyes form inward pointing chevrons.
        if (blink in 0.93f..0.97f) {
            pixel(4, 4, Px.bg); pixel(9, 4, Px.bg)
        } else {
            pixel(4, 3, Px.bg); pixel(5, 4, Px.bg); pixel(4, 5, Px.bg)
            pixel(9, 3, Px.bg); pixel(8, 4, Px.bg); pixel(9, 5, Px.bg)
        }
    }
}

@Composable
private fun PixelDust(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pixel dust")
    val twinkle by transition.animateFloat(
        0.15f, 0.85f,
        infiniteRepeatable(tween(1750, easing = LinearEasing), RepeatMode.Reverse),
        label = "dust twinkle",
    )
    val particles = remember {
        listOf(
            Triple(.11f, .18f, 5f), Triple(.29f, .71f, 4f), Triple(.42f, .31f, 6f),
            Triple(.56f, .82f, 4f), Triple(.68f, .17f, 5f), Triple(.79f, .48f, 4f),
            Triple(.91f, .28f, 5f), Triple(.18f, .53f, 4f), Triple(.63f, .66f, 6f),
            Triple(.36f, .12f, 4f), Triple(.86f, .78f, 4f), Triple(.49f, .48f, 5f),
        )
    }
    Canvas(modifier) {
        particles.forEachIndexed { index, (x, y, pixelSize) ->
            val phase = if (index % 2 == 0) twinkle else 1f - twinkle
            drawRect(
                Px.coral.copy(alpha = phase),
                Offset(this.size.width * x, this.size.height * y),
                Size(pixelSize.dp.toPx(), pixelSize.dp.toPx()),
            )
        }
    }
}

@Composable
private fun RightDashboard(
    ctx: Context,
    systemMetrics: SystemMetrics,
    notificationAllowed: Boolean,
    track: PlayingTrack?,
    agenda: List<AgendaEvent>,
    calendarAllowed: Boolean,
    onEnableUsage: () -> Unit,
    onEnableNotifications: () -> Unit,
    onEnableCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WindowPanel(title = "TODAY", modifier = Modifier.weight(1.05f), entranceDelayMs = 180) {
            when {
                !calendarAllowed -> {
                    PxText("YOUR CALENDAR AGENDA", 21.sp, Px.dim)
                    SettingsActionButton("ENABLE CALENDAR ACCESS", onEnableCalendar)
                }

                agenda.isEmpty() -> PxText("NOTHING SCHEDULED TODAY", 22.sp, Px.dim)
                else -> agenda.forEach { AgendaRow(it.time, it.title) }
            }
        }

        Row(Modifier.fillMaxWidth().weight(1.45f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WindowPanel(
                title = "NOW.PLAYING",
                modifier = Modifier.weight(1f),
                entranceDelayMs = 330,
                onClick = { MusicPlayback.openSpotify(ctx) },
            ) {
                if (notificationAllowed) {
                    Equalizer(isPlaying = track?.isPlaying == true)
                    PxText(track?.title ?: "SPOTIFY READY", 22.sp, Px.cream, maxLines = 1)
                    PxText(track?.artist ?: "TAP CARD TO OPEN SPOTIFY", 20.sp, Px.dim, maxLines = 1)
                    PlaybackControls(
                        playing = track?.isPlaying == true,
                        onPlayPause = { if (!MusicPlayback.toggle()) MusicPlayback.openSpotify(ctx) },
                    )
                } else {
                    PxText("SPOTIFY CONTROLS", 21.sp, Px.cream, maxLines = 1)
                    PxText(
                        "Media controls and public lock-screen previews use notification access.",
                        19.sp,
                        Px.dim,
                        maxLines = 2
                    )
                    SettingsActionButton("ENABLE MEDIA ACCESS", onEnableNotifications)
                }
                NowPlayingVolume(ctx)
            }
            WindowPanel(title = "SYS.MONITOR", modifier = Modifier.weight(1f), entranceDelayMs = 480) {
                PxText("LIVE DEVICE MEMORY", 17.sp, Mint)
                MonitorRow("RAM USED", systemMetrics.ramPercent, Px.coral)
                MonitorRow("STORAGE USED", systemMetrics.storagePercent, Yellow)
            }
        }

        WindowPanel(title = "QUICK.TOGGLE", modifier = Modifier.height(92.dp), entranceDelayMs = 630) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PanelButton(ctx, "WI-FI", Settings.Panel.ACTION_WIFI, true, Modifier.weight(1f))
                PanelButton(ctx, "BT", Settings.ACTION_BLUETOOTH_SETTINGS, true, Modifier.weight(1f))
                PanelButton(ctx, "DND", Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, false, Modifier.weight(1f))
                PanelButton(ctx, "ROTATE", Settings.ACTION_DISPLAY_SETTINGS, false, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AgendaRow(time: String, title: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PxText(time, 22.sp, Px.coral)
        Spacer(Modifier.width(14.dp))
        PxText(title, 24.sp, Px.cream)
    }
}

@Composable
private fun PlaybackControls(playing: Boolean, onPlayPause: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PlaybackButton("|<", Modifier.weight(1f)) { MusicPlayback.skipPrevious() }
        PlaybackButton(if (playing) "PAUSE" else "PLAY", Modifier.weight(1.4f), onPlayPause)
        PlaybackButton(">|", Modifier.weight(1f)) { MusicPlayback.skipNext() }
    }
}

@Composable
private fun NowPlayingVolume(context: Context) {
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember(audioManager) { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember(audioManager) {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVolume))
    }
    LaunchedEffect(audioManager) {
        while (true) {
            volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVolume)
            delay(800)
        }
    }
    val setVolumeAt: (Float, Float) -> Unit = { x, width ->
        val next = ((x / width.coerceAtLeast(1f)).coerceIn(0f, 1f) * maxVolume).roundToInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
        volume = next
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PxText("VOL", 16.sp, Px.dim)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(1f).height(24.dp)
                .pointerInput(maxVolume) {
                    detectTapGestures { setVolumeAt(it.x, size.width.toFloat()) }
                }
                .pointerInput(maxVolume) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        setVolumeAt(change.position.x, size.width.toFloat())
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                val centerY = size.height / 2f
                drawRect(Color(0xFF2A1C16), Offset.Zero, Size(size.width, 6.dp.toPx()))
                drawRect(Px.coral, Offset.Zero, Size(size.width * volume / maxVolume, 6.dp.toPx()))
                val thumbX = size.width * volume / maxVolume
                drawRect(Px.cream, Offset((thumbX - 4.dp.toPx()).coerceIn(0f, size.width - 8.dp.toPx()), centerY - 6.dp.toPx()), Size(8.dp.toPx(), 12.dp.toPx()))
            }
        }
        Spacer(Modifier.width(8.dp))
        PxText("${(volume * 100 / maxVolume)}%", 16.sp, Px.cream)
    }
}

@Composable
private fun PlaybackButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(34.dp).pixelBox(Px.panel, Px.line, shadow = 0.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PxText(label, 8.sp, Px.coral, fontFamily = PixelFonts.display, maxLines = 1)
    }
}

@Composable
private fun Equalizer(isPlaying: Boolean) {
    val heights = listOf(.38f, .72f, .52f, .94f, .44f, .82f, .31f, .67f, .48f, .9f, .58f, .76f)
    Row(
        Modifier.fillMaxWidth().height(34.dp).padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEachIndexed { index, height ->
            val transition = rememberInfiniteTransition(label = "equalizer $index")
            val level by transition.animateFloat(
                0.2f, 1f,
                infiniteRepeatable(tween(900, delayMillis = index * 55, easing = LinearEasing), RepeatMode.Reverse),
                label = "equalizer bar $index",
            )
            Box(Modifier.weight(1f).fillMaxHeight(if (isPlaying) height * level else 0.14f).background(Px.coral))
        }
    }
}

@Composable
private fun MonitorRow(label: String, percent: Int, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PxText(label, 18.sp, Px.dim, modifier = Modifier.weight(1f))
        PxText("$percent%", 20.sp, Px.cream)
    }
    SegmentedBar(percent, color, Modifier.fillMaxWidth().padding(bottom = 5.dp))
}

@Composable
private fun SegmentedBar(percent: Int, color: Color, modifier: Modifier = Modifier) {
    val count = 16
    val filled = (count * percent.coerceIn(0, 100) / 100f).toInt()
    val pulse = rememberInfiniteTransition(label = "segmented bar pulse")
    val alpha by pulse.animateFloat(
        1f, 0.6f,
        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar pulse",
    )
    Box(modifier.height(9.dp).background(Color(0xFF1C1411))) {
        Row(
            Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f).fillMaxHeight()
                .graphicsLayer { this.alpha = alpha }) {
            repeat(count) { index ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().padding(end = 2.dp)
                        .background(if (index < filled) color else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun SettingsActionButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(34.dp)
            .pixelBox(Px.panel, Px.coral, shadow = 0.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PxText(label, 9.sp, Px.coral, fontFamily = PixelFonts.display, maxLines = 1)
    }
}

@Composable
private fun PanelButton(
    ctx: Context,
    label: String,
    action: String,
    active: Boolean,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
) {
    val fill = if (active) Px.coral else Px.panel
    val textColor = if (active) Px.bg else Px.dim
    Box(
        modifier.height(46.dp)
            .pixelBox(fill, if (active) Px.coral else Px.line, shadow = 0.dp)
            .clickable {
                if (onClick != null) onClick()
                else runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
        contentAlignment = Alignment.Center,
    ) {
        PxText(label, 12.sp, textColor, fontFamily = PixelFonts.display, maxLines = 1)
    }
}

@Composable
private fun WindowPanel(
    title: String? = null,
    modifier: Modifier = Modifier,
    entranceDelayMs: Int = 0,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val floatTransition = rememberInfiniteTransition(label = "window float")
    val float by floatTransition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "window float",
    )
    val opacity = remember { Animatable(0f) }
    LaunchedEffect(entranceDelayMs) {
        delay(entranceDelayMs.toLong())
        opacity.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    Column(
        modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).graphicsLayer {
            translationY = -float * 6.dp.toPx()
            alpha = opacity.value
        }.drawBehind {
            val shadow = 4.dp.toPx()
            val border = 1.5.dp.toPx()
            drawRect(Color.Black, Offset(shadow, shadow), Size(size.width, size.height))
            drawRect(Color(0xD1120D0B))
            drawRect(Px.line, style = Stroke(border))
            val mark = 12.dp.toPx()
            val thick = 2.dp.toPx()
            drawLine(Px.coral, Offset(0f, 1f), Offset(mark, 1f), thick)
            drawLine(Px.coral, Offset(1f, 0f), Offset(1f, mark), thick)
        }.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = {
            if (title != null) {
                PxText(title, 12.sp, Px.coral, fontFamily = PixelFonts.display, maxLines = 1)
                Spacer(Modifier.height(4.dp))
            }
            content()
        },
    )
}

@Composable
private fun SearchWindow(appCount: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val cursorAlpha = rememberCursorAlpha()
    Row(
        modifier.height(58.dp)
            .drawBehind {
                drawRect(Color.Black, Offset(4.dp.toPx(), 4.dp.toPx()), Size(size.width, size.height))
                drawRect(Color(0xD1120D0B))
                drawRect(Px.line, style = Stroke(1.5.dp.toPx()))
                drawLine(Px.coral, Offset(0f, 1f), Offset(12.dp.toPx(), 1f), 2.dp.toPx())
                drawLine(Px.coral, Offset(1f, 0f), Offset(1f, 12.dp.toPx()), 2.dp.toPx())
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PxText("> SEARCH APPS", 24.sp, Px.cream)
        PxText("_", 24.sp, Px.cream, modifier = Modifier.graphicsLayer { alpha = cursorAlpha })
        Spacer(Modifier.weight(1f))
        PxText("$appCount APPS", 20.sp, Px.dim)
    }
}

private data class RailShortcutSpec(
    val label: String,
    val packageName: String,
    val matches: List<String>,
)

private data class RailShortcut(val spec: RailShortcutSpec, val app: AppInfo?)
private data class RingItem(
    val label: String,
    val percent: Int,
    val color: Color,
    val app: AppInfo? = null,
    val battery: Boolean = false,
    val usageAccess: Boolean = true,
)

private val RailShortcutSpecs = listOf(
    RailShortcutSpec("GOOGLE", "com.google.android.googlequicksearchbox", listOf("google")),
    RailShortcutSpec("PLAY STORE", "com.android.vending", listOf("play store", "playstore")),
    RailShortcutSpec("SPOTIFY", "com.spotify.music", listOf("spotify")),
)

private fun buildRingItems(
    shortcuts: List<RailShortcut>,
    usageMillis: Map<String, Long>,
    limits: Map<String, Int>,
    batteryPercent: Int,
    usageAccess: Boolean,
): List<RingItem> {
    val appRings = shortcuts.map { shortcut ->
        val app = shortcut.app
        val limitMinutes = AppLimitStore.getLimit(limits, app?.pkg)
        val minutesUsed = app?.pkg?.let { usageMillis[it] ?: 0L } ?: 0L
        val percent = if (usageAccess && app != null) {
            ((minutesUsed.toDouble() / 60_000.0) / limitMinutes * 100).roundToInt().coerceIn(0, 100)
        } else 0
        val ringColor = when {
            !usageAccess -> Px.dim
            percent < 60 -> Mint
            percent <= 90 -> Yellow
            else -> Px.coral
        }
        RingItem(shortcut.spec.label, percent, ringColor, app, usageAccess = usageAccess)
    }
    return appRings + RingItem(
        label = "BATTERY",
        percent = batteryPercent.coerceIn(0, 100),
        color = Px.coral,
        battery = true,
    )
}

private fun resolveRailShortcuts(apps: List<AppInfo>): List<RailShortcut> {
    val used = mutableSetOf<AppInfo>()
    return RailShortcutSpecs.map { spec ->
        val match = apps.firstOrNull { app -> app !in used && app.pkg == spec.packageName }
            ?: apps.firstOrNull { app ->
                app !in used && spec.matches.any { token -> app.label.contains(token, ignoreCase = true) }
            }
        if (match != null) used += match
        RailShortcut(spec, match)
    }
}

@Composable
private fun EdgeShortcutRail(
    rings: List<RingItem>,
    onLaunch: (AppInfo) -> Unit,
    onEnableUsage: () -> Unit,
    onEditLimit: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.width(152.dp).height(500.dp)) {
        PixelRailShape(Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxHeight().padding(start = 40.dp, top = 34.dp, bottom = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxHeight().width(112.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                rings.forEach { ring ->
                    RailShortcutButton(
                        ring = ring,
                        onClick = { ring.app?.let(onLaunch) },
                        onLongClick = {
                            if (ring.app == null || ring.battery) return@RailShortcutButton
                            if (ring.usageAccess) onEditLimit(ring.app) else onEnableUsage()
                        },
                    )
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 13.dp)
                .width(30.dp).height(5.dp).background(Px.coral),
        )
    }
}

@Composable
private fun PixelRailShape(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 8.dp.toPx()
        val left = size.width * 0.25f
        val path = Path().apply {
            moveTo(size.width, 0f)
            lineTo(left + step * 4, 0f)
            lineTo(left + step * 3, 0f)
            lineTo(left + step * 3, step)
            lineTo(left + step * 2, step)
            lineTo(left + step * 2, step * 2)
            lineTo(left + step, step * 2)
            lineTo(left + step, step * 4)
            lineTo(left, step * 4)
            lineTo(left, size.height - step * 4)
            lineTo(left + step, size.height - step * 4)
            lineTo(left + step, size.height - step * 2)
            lineTo(left + step * 2, size.height - step * 2)
            lineTo(left + step * 2, size.height - step)
            lineTo(left + step * 3, size.height - step)
            lineTo(left + step * 3, size.height)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, Color(0xFF0B0706))
        drawPath(path, Px.coral, style = Stroke(width = 3.dp.toPx()))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailShortcutButton(ring: RingItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier.width(100.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                val radius = (size.minDimension - stroke) / 2f
                val topLeft = Offset((size.width - radius * 2f) / 2f, (size.height - radius * 2f) / 2f)
                val ringSize = Size(radius * 2f, radius * 2f)
                drawArc(
                    Color(0xFF2A1C16), 0f, 360f, false, topLeft, ringSize,
                    style = Stroke(stroke),
                )
                drawArc(
                    ring.color, -90f, 360f * ring.percent / 100f, false, topLeft, ringSize,
                    style = Stroke(stroke),
                )
            }
            ring.app?.let { app ->
                Image(app.icon, contentDescription = app.label, modifier = Modifier.size(34.dp))
            } ?: if (ring.battery) {
                BatteryGlyph(Modifier.size(30.dp))
            } else PxText("?", 26.sp, Px.cream, fontFamily = PixelFonts.display)
        }
        PxText(
            if (!ring.usageAccess && !ring.battery) "--" else "${ring.percent}%",
            20.sp,
            Px.cream,
            align = TextAlign.Center
        )
    }
}

@Composable
private fun BatteryGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 3.dp.toPx()
        val bodyWidth = size.width * 0.72f
        val bodyHeight = size.height * 0.52f
        val left = (size.width - bodyWidth) / 2f
        val top = (size.height - bodyHeight) / 2f
        drawRect(Px.cream, Offset(left, top), Size(bodyWidth, bodyHeight), style = Stroke(stroke))
        drawRect(
            Px.cream,
            Offset(left + bodyWidth, top + bodyHeight * 0.3f),
            Size(size.width * 0.12f, bodyHeight * 0.4f)
        )
        drawRect(Px.coral, Offset(left + stroke, top + stroke), Size(bodyWidth * 0.52f, bodyHeight - stroke * 2))
    }
}

@Composable
private fun DailyLimitDialog(
    app: AppInfo,
    currentLimit: Int,
    usedMillis: Long,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var editedLimit by remember(app.pkg, currentLimit) { mutableIntStateOf(currentLimit) }
    val outerInteraction = remember { MutableInteractionSource() }
    val innerInteraction = remember { MutableInteractionSource() }
    val usedMinutes = (usedMillis / 60_000L).toInt()

    Box(
        Modifier.fillMaxSize().background(Color(0xCC060403))
            .clickable(interactionSource = outerInteraction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.42f)
                .pixelBox(Color(0xFF120D0B), Px.coral, shadow = 6.dp, borderWidth = 2.dp)
                .clickable(interactionSource = innerInteraction, indication = null) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PxText("DAILY APP LIMIT", 14.sp, Px.coral, fontFamily = PixelFonts.display)
            PxText(app.label.uppercase(Locale.ENGLISH), 28.sp, Px.cream, fontFamily = PixelFonts.body)
            PxText("USED TODAY: ${usedMinutes / 60}H ${usedMinutes % 60}M", 22.sp, Px.dim)
            PxText("$editedLimit MIN / DAY", 26.sp, Px.cream, fontFamily = PixelFonts.display)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LimitAdjustButton("- 15", Modifier.weight(1f), onClick = {
                    editedLimit = (editedLimit - 15).coerceAtLeast(15)
                })
                LimitAdjustButton("+ 15", Modifier.weight(1f), onClick = {
                    editedLimit = (editedLimit + 15).coerceAtMost(24 * 60)
                })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LimitAdjustButton("CANCEL", Modifier.weight(1f), onDismiss)
                LimitAdjustButton("SAVE", Modifier.weight(1f), onClick = { onSave(editedLimit) }, primary = true)
            }
        }
    }
}

@Composable
private fun TodoTaskDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().pixelBox(Px.panel, Px.coral, shadow = 6.dp, borderWidth = 2.dp).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PxText("ADD TODO", 13.sp, Px.coral, fontFamily = PixelFonts.display)
            Box(
                Modifier.fillMaxWidth().height(48.dp).pixelBox(Color(0xFF0A0807), Px.line, shadow = 0.dp)
                    .padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart
            ) {
                if (title.isBlank()) PxText("Task name...", 22.sp, Px.dim)
                BasicTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(color = Px.cream, fontSize = 22.sp, fontFamily = PixelFonts.body),
                    cursorBrush = SolidColor(Px.coral),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LimitAdjustButton("CANCEL", Modifier.weight(1f), onDismiss)
                LimitAdjustButton("ADD", Modifier.weight(1f), onClick = { onSave(title) }, primary = true)
            }
        }
    }
}

private fun readDeviceName(context: Context): String {
    val configured = runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }
        .getOrNull()?.trim()?.takeIf { it.isNotBlank() && !it.equals("Owner", ignoreCase = true) }
    return configured ?: Build.MODEL.replace('_', ' ').trim().ifBlank { "ANDROID DEVICE" }
}

@Composable
private fun LimitAdjustButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    Box(
        modifier.height(42.dp)
            .pixelBox(
                fill = if (primary) Px.coral else Px.panel,
                border = if (primary) Px.coral else Px.line,
                shadow = 0.dp,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PxText(label, 10.sp, if (primary) Px.bg else Px.cream, fontFamily = PixelFonts.display)
    }
}

@Composable
fun AppMenu(
    apps: List<AppInfo>,
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
) {
    val noRippleOuter = remember { MutableInteractionSource() }
    val noRippleInner = remember { MutableInteractionSource() }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Box(
        Modifier.fillMaxSize().background(Color(0xCC060403))
            .clickable(interactionSource = noRippleOuter, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.78f).fillMaxHeight(0.72f)
                .pixelBox(Color(0xFF120D0B), Px.coral, shadow = 6.dp, borderWidth = 2.dp)
                .clickable(interactionSource = noRippleInner, indication = null) {}
                .padding(2.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).drawBehind {
                    val y = size.height - 1.dp.toPx()
                    drawLine(Color(0xFF3A2419), Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(Modifier.size(11.dp).background(Px.coral))
                Box(Modifier.size(11.dp).background(Px.line))
                Box(Modifier.size(11.dp).background(Px.line))
                PxText("~/apps — ${shown.size} items", 23.sp, Px.dim, modifier = Modifier.weight(1f))
                Box(
                    Modifier.width(104.dp).height(32.dp)
                        .pixelBox(Px.panel, Px.line, shadow = 0.dp)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { PxText("[X] CLOSE", 10.sp, Px.cream, fontFamily = PixelFonts.display) }
            }

            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = Px.cream, fontSize = 28.sp, fontFamily = PixelFonts.body),
                    cursorBrush = SolidColor(Px.coral),
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    decorationBox = { inner ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PxText("> ", 28.sp, Px.coral)
                            PxText("ls apps/", 28.sp, Px.cream)
                            if (query.isNotEmpty()) Spacer(Modifier.width(8.dp))
                            inner()
                        }
                    },
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(4.dp),
                ) {
                    itemsIndexed(
                        items = shown,
                        key = { _, app -> app.pkg + "/" + app.activity },
                        contentType = { _, _ -> "app_tile" },
                    ) { _, app ->
                        AppTile(app) { onLaunch(app) }
                    }
                }
            }
        }
    }
}

@Composable
fun AppTile(app: AppInfo, onClick: () -> Unit) {
    val interaction = remember(app.pkg) { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val highlighted = hovered || focused || pressed
    Column(
        Modifier.fillMaxWidth().height(124.dp)
            .graphicsLayer {
                if (highlighted) translationX = -2.dp.toPx()
            }
            .pixelBox(
                fill = Color(0xFF120D0B),
                border = if (highlighted) Px.coral else Px.line,
                shadow = if (highlighted) 6.dp else 4.dp,
                shadowColor = if (highlighted) Px.coralDark else Color.Black,
            )
            .hoverable(interactionSource = interaction)
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Image(app.icon, contentDescription = app.label, modifier = Modifier.size(44.dp))
        PxText(
            app.label.uppercase(Locale.ENGLISH),
            22.sp,
            if (highlighted) Px.coral else Px.cream,
            maxLines = 1,
            align = TextAlign.Center
        )
    }
}
