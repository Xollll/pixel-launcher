package com.example.pixellauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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

@Composable
fun HomeScreen(homeTick: Int) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var menuOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }

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

    // On a launcher, Back never leaves home. It only closes the menu.
    BackHandler(enabled = true) { menuOpen = false }

    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(1000) }
    }

    Box(Modifier.fillMaxSize().background(Px.bg)) {
        Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp)) {
            Column(Modifier.align(Alignment.TopStart)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PxText("> PIXEL.OS", 18.sp, Px.coral, FontWeight.Bold, fontFamily = PixelFonts.display)
                    Spacer(Modifier.width(16.dp))
                    PxText("S7 FE // ONLINE", 16.sp, Px.dim)
                }
                Spacer(Modifier.height(48.dp))
                PxText("GOOD DAY_", 20.sp, Px.coral, fontFamily = PixelFonts.display)
                PxText(now.format(TIME_FMT), 88.sp, Px.cream, FontWeight.Bold, fontFamily = PixelFonts.display)
                PxText(now.format(DATE_FMT).uppercase(Locale.ENGLISH), 24.sp, Px.dim)
            }
            MenuButton(Modifier.align(Alignment.BottomCenter)) { menuOpen = true }
        }

        AnimatedVisibility(menuOpen, enter = fadeIn(), exit = fadeOut()) {
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
fun MenuButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .width(200.dp)
            .height(64.dp)
            .pixelBox(Px.coral, Px.coral, shadowColor = Px.coralDark)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { PxText("MENU", 18.sp, Px.bg, FontWeight.Bold, fontFamily = PixelFonts.display) }
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

    // Tapping the dark area closes the menu; tapping inside the window does not.
    Box(
        Modifier.fillMaxSize().background(Color(0xCC060403))
            .clickable(interactionSource = noRippleOuter, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.82f)
                .pixelBox(Px.panel, Px.coral, shadow = 6.dp)
                .clickable(interactionSource = noRippleInner, indication = null) {}
                .padding(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PxText("~/apps - ${shown.size} items", 20.sp, Px.dim, modifier = Modifier.weight(1f))
                Box(
                    Modifier.pixelBox(shadow = 0.dp).clickable(onClick = onClose)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) { PxText("[X] CLOSE", 14.sp, Px.coral, FontWeight.Bold) }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().pixelBox(Px.bg, Px.line, shadow = 0.dp).padding(14.dp)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = Px.cream, fontSize = 20.sp, fontFamily = PixelFonts.body),
                    cursorBrush = SolidColor(Px.coral),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Row {
                            PxText("> ", 20.sp, Px.coral)
                            Box {
                                if (query.isEmpty()) PxText("search apps", 20.sp, Px.dim)
                                inner()
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(6.dp),
            ) {
                items(shown, key = { it.pkg + "/" + it.activity }) { app ->
                    AppTile(app) { onLaunch(app) }
                }
            }
        }
    }
}

@Composable
fun AppTile(app: AppInfo, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().pixelBox().clickable(onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(app.icon, contentDescription = app.label, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(8.dp))
        PxText(app.label, 14.sp, Px.cream, maxLines = 2, align = TextAlign.Center)
    }
}
