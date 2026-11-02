package com.example.pixellauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FMT = DateTimeFormatter.ofPattern("EEE dd MMM yyyy", Locale.ENGLISH)
private val Mint = Color(0xFF6FE39A)
private val Yellow = Color(0xFFF2D94E)

@Composable
fun HomeScreen(homeTick: Int) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var menuOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    val batteryPercent = remember(ctx) {
        val manager = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }
    val railShortcuts = remember(apps) { resolveRailShortcuts(apps) }

    // Reload the app list when something is installed, updated or removed.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { reload++ }
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
    LaunchedEffect(homeTick) { menuOpen = false }
    LaunchedEffect(menuOpen) { if (!menuOpen) query = "" }

    // On a launcher, Back never leaves home. It only closes the app menu.
    BackHandler(enabled = true) { menuOpen = false }

    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(1000) }
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
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 48.dp, top = 48.dp, bottom = 48.dp, end = 200.dp)
                .blur(if (menuOpen) 8.dp else 0.dp),
        ) {
            TopBar(batteryPercent)
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                LeftDashboard(now, Modifier.weight(0.42f).fillMaxHeight())
                RightDashboard(ctx, Modifier.weight(0.58f).fillMaxHeight())
            }

            Spacer(Modifier.height(14.dp))
            SearchWindow(appCount = apps.size, modifier = Modifier.fillMaxWidth()) { menuOpen = true }
        }

        EdgeShortcutRail(
            shortcuts = railShortcuts,
            onLaunch = { launchApp(ctx, it) },
            onMenu = { menuOpen = true },
            modifier = Modifier.align(Alignment.CenterEnd).blur(if (menuOpen) 8.dp else 0.dp),
        )

        // Keep the working Milestone 1 app list and search while the home layout is rebuilt.
        if (menuOpen) {
            AppMenu(
                apps = apps,
                query = query,
                onQuery = { query = it },
                onClose = { menuOpen = false },
                onLaunch = { launchApp(ctx, it); menuOpen = false },
            )
        }
    }
}

@Composable
private fun TopBar(batteryPercent: Int) {
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
        PxText("S7 FE // ONLINE", 24.sp, Px.dim)
        Spacer(Modifier.weight(1f))
        PxText("WIFI OK", 24.sp, Px.dim)
        Spacer(Modifier.width(22.dp))
        PxText("BAT $batteryPercent%", 24.sp, Px.dim)
        Spacer(Modifier.width(10.dp))
        SegmentedBar(batteryPercent, Px.coral, Modifier.width(72.dp))
    }
}

@Composable
private fun LeftDashboard(now: LocalDateTime, modifier: Modifier = Modifier) {
    Column(modifier) {
        Column {
            PxText("GOOD ${now.format(DateTimeFormatter.ofPattern("a", Locale.ENGLISH)).let { if (it == "AM") "MORNING" else "EVENING" }}", 28.sp, Px.coral)
            PxText(now.format(TIME_FMT), 76.sp, Px.cream, FontWeight.Bold, fontFamily = PixelFonts.display)
            PxText(now.format(DATE_FMT).uppercase(Locale.ENGLISH), 28.sp, Px.dim)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(112.dp), verticalAlignment = Alignment.CenterVertically) {
            PixelMascot(Modifier.width(108.dp).fillMaxHeight())
            Spacer(Modifier.width(10.dp))
            WindowPanel(modifier = Modifier.weight(1f).fillMaxHeight()) {
                PxText("> hello, player one_", 24.sp, Px.cream, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                PxText("3 tasks today", 20.sp, Px.dim)
            }
        }
        Spacer(Modifier.height(12.dp))
        WindowPanel(title = "TODO.md", modifier = Modifier.fillMaxWidth().weight(1f)) {
            TodoRow("x", "Build")
            TodoRow(" ", "Test")
            TodoRow(" ", "Refactor")
            TodoRow(" ", "Ship it")
            TodoRow(" ", "Take a break")
        }
    }
}

@Composable
private fun TodoRow(mark: String, label: String) {
    PxText("[$mark] $label", 22.sp, if (mark == "x") Px.cream else Px.dim)
}

@Composable
private fun PixelMascot(modifier: Modifier = Modifier) {
    Canvas(modifier) {
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
        pixel(4, 3, Px.bg); pixel(5, 4, Px.bg); pixel(4, 5, Px.bg)
        pixel(9, 3, Px.bg); pixel(8, 4, Px.bg); pixel(9, 5, Px.bg)
    }
}

@Composable
private fun RightDashboard(ctx: Context, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WindowPanel(title = "TODAY", modifier = Modifier.weight(1.05f)) {
            AgendaRow("09:00", "Standup")
            AgendaRow("12:30", "Lunch")
            AgendaRow("15:00", "Review")
            AgendaRow("18:00", "Gym")
        }

        Row(Modifier.fillMaxWidth().weight(1.45f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WindowPanel(title = "NOW.PLAYING", modifier = Modifier.weight(1f)) {
                Equalizer()
                PxText("[TRACK TITLE]", 22.sp, Px.cream)
                PxText("[ARTIST]", 20.sp, Px.dim)
                Spacer(Modifier.height(4.dp))
                PxText("|<     >     >|", 22.sp, Px.coral)
            }
            WindowPanel(title = "SYS.MONITOR", modifier = Modifier.weight(1f)) {
                MonitorRow("CPU", 38, Px.coral)
                MonitorRow("RAM", 61, Px.coral)
                MonitorRow("STORAGE", 72, Yellow)
            }
        }

        WindowPanel(title = "QUICK.TOGGLE", modifier = Modifier.height(92.dp)) {
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
private fun Equalizer() {
    val heights = listOf(.38f, .72f, .52f, .94f, .44f, .82f, .31f, .67f, .48f, .9f, .58f, .76f)
    Row(
        Modifier.fillMaxWidth().height(34.dp).padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEach { height ->
            Box(
                Modifier.weight(1f).fillMaxHeight(height).background(Px.coral),
            )
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
    Box(modifier.height(9.dp).background(Color(0xFF1C1411))) {
        Row(Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f).fillMaxHeight()) {
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
private fun PanelButton(ctx: Context, label: String, action: String, active: Boolean, modifier: Modifier) {
    val fill = if (active) Px.coral else Px.panel
    val textColor = if (active) Px.bg else Px.dim
    Box(
        modifier.height(46.dp)
            .pixelBox(fill, if (active) Px.coral else Px.line, shadow = 0.dp)
            .clickable {
                runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.drawBehind {
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
        PxText("> SEARCH APPS_", 24.sp, Px.cream)
        Spacer(Modifier.weight(1f))
        PxText("$appCount APPS", 20.sp, Px.dim)
    }
}

private data class RailShortcutSpec(
    val label: String,
    val percent: Int,
    val color: Color,
    val matches: List<String>,
)

private data class RailShortcut(val spec: RailShortcutSpec, val app: AppInfo?)

private val RailShortcutSpecs = listOf(
    RailShortcutSpec("BROWSER", 73, Px.coral, listOf("browser", "chrome", "internet")),
    RailShortcutSpec("CHAT", 21, Mint, listOf("chat", "message", "whatsapp", "telegram")),
    RailShortcutSpec("MUSIC", 52, Yellow, listOf("music", "spotify")),
    RailShortcutSpec("CAMERA", 38, Px.coral, listOf("camera")),
)

private fun resolveRailShortcuts(apps: List<AppInfo>): List<RailShortcut> {
    val used = mutableSetOf<AppInfo>()
    return RailShortcutSpecs.map { spec ->
        val match = apps.firstOrNull { app ->
            app !in used && spec.matches.any { token -> app.label.contains(token, ignoreCase = true) }
        } ?: apps.firstOrNull { it !in used }
        if (match != null) used += match
        RailShortcut(spec, match)
    }
}

@Composable
private fun EdgeShortcutRail(
    shortcuts: List<RailShortcut>,
    onLaunch: (AppInfo) -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.width(152.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(500.dp)) {
            PixelRailShape(Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxHeight().padding(start = 40.dp, top = 34.dp, bottom = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.fillMaxHeight().width(112.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    shortcuts.forEach { shortcut ->
                        RailShortcutButton(shortcut) {
                            shortcut.app?.let(onLaunch)
                        }
                    }
                }
            }
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 13.dp)
                    .width(30.dp).height(5.dp).background(Px.coral),
            )
        }
        Spacer(Modifier.height(16.dp))
        PixelMenuCircle(onClick = onMenu)
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

@Composable
private fun RailShortcutButton(shortcut: RailShortcut, onClick: () -> Unit) {
    val spec = shortcut.spec
    Column(
        Modifier.width(100.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
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
                    spec.color, -90f, 360f * spec.percent / 100f, false, topLeft, ringSize,
                    style = Stroke(stroke),
                )
            }
            shortcut.app?.let { app ->
                Image(app.icon, contentDescription = app.label, modifier = Modifier.size(34.dp))
            } ?: PxText("?", 26.sp, Px.cream, fontFamily = PixelFonts.display)
        }
        PxText("${spec.percent}%", 22.sp, Px.cream, align = TextAlign.Center)
    }
}

@Composable
private fun PixelMenuCircle(onClick: () -> Unit) {
    Column(
        Modifier.width(92.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val rowWidths = intArrayOf(24, 40, 56, 64, 72, 72, 80, 80, 88, 88, 88, 88, 88, 88, 88, 80, 80, 72, 72, 64, 56, 40, 24)
                rowWidths.forEachIndexed { row, width ->
                    val cell = size.height / rowWidths.size
                    val pixelWidth = size.width * width / 92f
                    drawRect(Px.coral, Offset((size.width - pixelWidth) / 2f, row * cell), Size(pixelWidth, cell))
                }
            }
            Canvas(Modifier.size(32.dp)) {
                val cell = size.minDimension / 6f
                for (row in 0..1) for (col in 0..1) {
                    drawRect(Px.bg, Offset((col * 3 + 0.5f) * cell, (row * 3 + 0.5f) * cell), Size(2 * cell, 2 * cell))
                }
            }
        }
        PxText("MENU", 10.sp, Px.cream, fontFamily = PixelFonts.display)
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
                    modifier = Modifier.height(400.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(4.dp),
                ) {
                    items(shown, key = { it.pkg + "/" + it.activity }) { app ->
                        AppTile(app, selected = shown.firstOrNull() == app) { onLaunch(app) }
                    }
                }
            }
        }
    }
}

@Composable
fun AppTile(app: AppInfo, selected: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().height(124.dp)
            .pixelBox(
                fill = Color(0xFF120D0B),
                border = if (selected) Px.coral else Px.line,
                shadow = 4.dp,
                shadowColor = if (selected) Px.coralDark else Color.Black,
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Image(app.icon, contentDescription = app.label, modifier = Modifier.size(44.dp))
        PxText(app.label.uppercase(Locale.ENGLISH), 22.sp, if (selected) Px.coral else Px.cream, maxLines = 1, align = TextAlign.Center)
    }
}
