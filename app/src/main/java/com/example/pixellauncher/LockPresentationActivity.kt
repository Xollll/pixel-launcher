package com.example.pixellauncher

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LockTimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val LockDateFormat = DateTimeFormatter.ofPattern("EEE dd MMM yyyy", Locale.ENGLISH)

class LockPresentationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            LockPresentationScreen(
                onUnlock = ::requestSystemUnlock,
                onCamera = {
                    runCatching { startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) }
                },
            )
        }
        window.decorView.post {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun requestSystemUnlock() {
        val keyguard = getSystemService(KeyguardManager::class.java) ?: return finish()
        if (!keyguard.isKeyguardLocked) return finish()
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { finish() }
            override fun onDismissError() { }
            override fun onDismissCancelled() { }
        })
    }

}

@Composable
private fun LockPresentationScreen(
    onUnlock: () -> Unit,
    onCamera: () -> Unit,
) {
    val context = LocalContext.current
    val locked = remember { context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true }
    BackHandler(enabled = locked) { }

    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(1000) }
    }
    val battery by produceState(readLockBattery(context)) {
        while (true) { value = readLockBattery(context); delay(15_000) }
    }
    val wifiConnected = remember {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        manager?.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
    val notificationsAllowed = remember { hasNotificationAccess(context) }
    val alerts by LockNotifications.alerts.collectAsState()

    Box(
        Modifier.fillMaxSize()
            .background(Px.bg)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFF1C100B), Px.bg),
                        center = androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .55f),
                        radius = size.maxDimension * .72f,
                    ),
                )
            },
    ) {
        LockDust(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 22.dp)) {
            Row(
                Modifier.fillMaxWidth().height(40.dp)
                    .drawBehind {
                        drawLine(Px.line, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx())
                        drawLine(Px.coral, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(110.dp.toPx(), size.height), 2.dp.toPx())
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PxText("> PIXEL.OS", 12.sp, Px.coral, weight = FontWeight.Bold, fontFamily = PixelFonts.display)
                Spacer(Modifier.width(16.dp))
                PxText("LOCKED", 23.sp, Px.dim)
                Spacer(Modifier.weight(1f))
                PxText(if (wifiConnected) "WIFI OK" else "WIFI OFF", 21.sp, Px.dim)
                Spacer(Modifier.width(18.dp))
                PxText("BAT $battery%", 21.sp, Px.dim)
            }

            Row(
                Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.weight(.30f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LockPanel("NOTIFICATIONS", Modifier.then(lockWindowMotion(200))) {
                        when {
                            !notificationsAllowed -> PxText("ALLOW NOTIFICATION ACCESS TO SHOW ALERTS", 19.sp, Px.dim, maxLines = 2)
                            alerts.isEmpty() -> PxText("NO NEW NOTIFICATIONS", 22.sp, Px.dim)
                            else -> alerts.forEach { alert ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    PxText("> ${alert.app.uppercase(Locale.ENGLISH)}", 18.sp, Px.coral, maxLines = 1)
                                    PxText(alert.title, 21.sp, Px.cream, maxLines = 1)
                                    if (alert.detail.isNotBlank()) PxText(alert.detail, 18.sp, Px.dim, maxLines = 1)
                                }
                            }
                        }
                    }
                    LockPanel("~/lock", Modifier.then(lockWindowMotion(500))) {
                        PxText("> status --secure", 22.sp, Px.cream)
                        PxText("Waiting for system authentication_", 19.sp, Px.dim, maxLines = 1)
                    }
                }

                Column(Modifier.weight(.40f).fillMaxHeight().then(lockWindowMotion(100)), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(10.dp))
                    PxText("SYSTEM LOCKED${if (now.second % 2 == 0) "_" else " "}", 12.sp, Px.coral, fontFamily = PixelFonts.display)
                    Spacer(Modifier.height(14.dp))
                    PxText(now.format(LockTimeFormat).replace(":", if (now.second % 2 == 0) ":" else " "), 58.sp, Px.cream, weight = FontWeight.Bold, fontFamily = PixelFonts.display)
                    PxText(now.format(LockDateFormat).uppercase(Locale.ENGLISH), 22.sp, Px.dim)
                    Spacer(Modifier.weight(1f))
                    LockMascot(Modifier.width(140.dp).height(122.dp), animated = true)
                    Spacer(Modifier.height(10.dp))
                    Canvas(Modifier.width(210.dp).height(28.dp)) {
                        val path = Path().apply {
                            moveTo(0f, size.height * .3f); lineTo(size.width * .5f, size.height)
                            lineTo(size.width, size.height * .3f); lineTo(size.width * .5f, 0f); close()
                        }
                        drawPath(path, Color(0x22120D0B))
                        clipPath(path) {
                            val gridStep = 14.dp.toPx()
                            var x = 0f
                            while (x <= size.width) {
                                drawLine(Px.line.copy(alpha = .42f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1.dp.toPx())
                                x += gridStep
                            }
                            var y = 0f
                            while (y <= size.height) {
                                drawLine(Px.line.copy(alpha = .42f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
                                y += gridStep
                            }
                        }
                        drawPath(path, Px.line, style = Stroke(1.dp.toPx()))
                    }
                    Spacer(Modifier.weight(1f))
                }

                Column(Modifier.weight(.30f).fillMaxHeight()) {
                    LockPanel("STATUS", Modifier.fillMaxWidth().then(lockWindowMotion(400))) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            LockMascot(Modifier.width(46.dp).height(40.dp), animated = true)
                            Column {
                                PxText("UNIT-07", 23.sp, Px.cream)
                                PxText("Locked", 21.sp, Px.coral)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        LockSegmentedBar(battery, Modifier.fillMaxWidth())
                        PxText("BAT $battery%", 21.sp, Px.dim)
                    }
                }
            }

            Row(Modifier.fillMaxWidth().height(58.dp).then(lockWindowMotion(1000)), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(64.dp))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.width(300.dp).height(54.dp)
                        .pixelBox(Px.panel, Px.line, shadow = 3.dp)
                        .pointerInput(onUnlock) {
                            var total = 0f
                            detectVerticalDragGestures { change, amount ->
                                total += amount
                                if (total < -80f) { change.consume(); onUnlock() }
                            }
                        }
                        .clickable(onClick = onUnlock),
                    contentAlignment = Alignment.Center,
                ) { LockUnlockPrompt(now.second % 2 == 0) }
                Spacer(Modifier.weight(1f))
                LockCameraButton(Modifier.width(64.dp), onClick = onCamera)
            }
        }
    }
}

@Composable
private fun LockUnlockPrompt(showCursor: Boolean) {
    val motion = androidx.compose.animation.core.rememberInfiniteTransition(label = "unlock prompt")
    val arrowY by motion.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "unlock arrow bob",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        PxText("↑", 22.sp, Px.coral, modifier = Modifier.graphicsLayer { translationY = arrowY * density }, fontFamily = PixelFonts.display)
        PxText("SWIPE UP TO UNLOCK", 10.sp, Px.cream, fontFamily = PixelFonts.display)
        PxText(if (showCursor) "_" else " ", 10.sp, Px.coral, fontFamily = PixelFonts.display)
    }
}

@Composable
private fun LockPanel(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .background(Color(0xD1120C0A))
            .border(1.dp, Px.line)
            .drawBehind {
                val t = 3.dp.toPx()
                drawLine(Px.coral, androidx.compose.ui.geometry.Offset(0f, t / 2), androidx.compose.ui.geometry.Offset(14.dp.toPx(), t / 2), t)
                drawLine(Px.coral, androidx.compose.ui.geometry.Offset(t / 2, 0f), androidx.compose.ui.geometry.Offset(t / 2, 14.dp.toPx()), t)
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PxText(title, 11.sp, Px.coral, fontFamily = PixelFonts.display)
        content()
    }
}

@Composable
private fun lockWindowMotion(delayMillis: Int): Modifier {
    var entered by remember(delayMillis) { mutableStateOf(false) }
    LaunchedEffect(delayMillis) {
        delay(delayMillis.toLong())
        entered = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(800),
        label = "lock window fade in",
    )
    val floatMotion = androidx.compose.animation.core.rememberInfiniteTransition(label = "lock window float $delayMillis")
    val offsetY by floatMotion.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "lock window drift",
    )
    return Modifier.graphicsLayer {
        this.alpha = alpha
        translationY = offsetY * density
    }
}

@Composable
private fun LockCameraButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(54.dp)
            .pixelBox(fill = Px.panel, border = Px.line, shadow = 3.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(30.dp)) {
            val unit = size.minDimension / 24f
            val stroke = 2.dp.toPx()
            drawRect(Px.cream, androidx.compose.ui.geometry.Offset(3f * unit, 8f * unit), androidx.compose.ui.geometry.Size(18f * unit, 13f * unit), style = Stroke(stroke))
            drawRect(Px.cream, androidx.compose.ui.geometry.Offset(8f * unit, 5f * unit), androidx.compose.ui.geometry.Size(8f * unit, 3f * unit))
            drawRect(Px.cream, androidx.compose.ui.geometry.Offset(10f * unit, 11f * unit), androidx.compose.ui.geometry.Size(8f * unit, 8f * unit), style = Stroke(stroke))
            drawRect(Px.coral, androidx.compose.ui.geometry.Offset(13f * unit, 14f * unit), androidx.compose.ui.geometry.Size(2f * unit, 2f * unit))
        }
    }
}

@Composable
private fun LockSegmentedBar(percent: Int, modifier: Modifier = Modifier) {
    Row(modifier.height(10.dp).background(Color(0xFF1C1411))) {
        repeat(20) { index ->
            Box(
                Modifier.weight(1f).fillMaxHeight().padding(end = 2.dp)
                    .background(if (index < percent.coerceIn(0, 100) / 5) Px.coral else Color.Transparent),
            )
        }
    }
}

@Composable
private fun LockMascot(modifier: Modifier = Modifier, animated: Boolean = false) {
    val motion = androidx.compose.animation.core.rememberInfiniteTransition(label = "lock mascot")
    val bobY by motion.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2400
                0f at 0
                -2f at 600
                -4f at 1200
                -6f at 1800
                0f at 2400
            },
            RepeatMode.Restart,
        ),
        label = "lock mascot bob",
    )
    val eyeOpen by motion.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 4000
                1f at 0
                1f at 3680
                .15f at 3800
                1f at 3920
                1f at 4000
            },
            RepeatMode.Restart,
        ),
        label = "lock mascot blink",
    )
    Canvas(if (animated) modifier.graphicsLayer { translationY = bobY * density } else modifier) {
        val cellWidth = size.width / 14f
        val cellHeight = size.height / 12f
        fun pixel(x: Int, y: Int, color: Color) {
            drawRect(color, androidx.compose.ui.geometry.Offset(x * cellWidth, y * cellHeight), androidx.compose.ui.geometry.Size(cellWidth, cellHeight))
        }
        for (x in 3..10) pixel(x, 0, Color(0xFFF4A384))
        for (y in 1..3) for (x in 2..11) pixel(x, y, Px.coral)
        for (y in 4..5) for (x in 0..13) pixel(x, y, Px.coral)
        for (y in 6..7) for (x in 2..11) pixel(x, y, Px.coral)
        for (x in 2..11) pixel(x, 8, Px.coralDark)
        for (y in 9..11) {
            for (x in 2..3) pixel(x, y, Px.coralDark)
            pixel(5, y, Px.coralDark); pixel(8, y, Px.coralDark)
            for (x in 10..11) pixel(x, y, Px.coralDark)
        }
        if (eyeOpen > .5f) {
            pixel(4, 3, Px.bg); pixel(5, 4, Px.bg); pixel(4, 5, Px.bg)
            pixel(9, 3, Px.bg); pixel(8, 4, Px.bg); pixel(9, 5, Px.bg)
        } else {
            pixel(4, 4, Px.bg); pixel(9, 4, Px.bg)
        }
    }
}

@Composable
private fun LockDust(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "lock dust")
    val alpha by transition.animateFloat(
        .15f, .8f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1700),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "lock dust alpha",
    )
    Canvas(modifier) {
        val dots = listOf(.12f to .22f, .26f to .68f, .72f to .17f, .84f to .55f, .51f to .82f, .93f to .83f)
        dots.forEachIndexed { index, (x, y) ->
            drawRect(Px.coral.copy(alpha = if (index % 2 == 0) alpha else 1f - alpha),
                androidx.compose.ui.geometry.Offset(size.width * x, size.height * y), androidx.compose.ui.geometry.Size(5.dp.toPx(), 5.dp.toPx()))
        }
    }
}

private fun readLockBattery(context: Context): Int {
    val manager = context.getSystemService(BatteryManager::class.java)
    val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    return level.takeIf { it in 0..100 } ?: 0
}
