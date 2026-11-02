package com.example.pixellauncher

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.IOException

object LockUiSetupState {
    const val PREFERENCES = "pixel_lock_ui_setup"
    const val AUTO_SETUP_ATTEMPTED = "auto_setup_attempted"
    const val WALLPAPER_APPLIED = "wallpaper_applied"
    const val NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"
}

private const val LOCK_WALLPAPER_ASSET_URL = "file:///android_asset/lock_wallpaper.html"

/** Renders the lock mockup as wallpaper; Samsung's own UI remains over it. */
class LockWallpaperActivity : ComponentActivity() {
    private var wallpaperApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            LockWallpaperPage { webView -> captureAndApply(webView) }
        }
        window.decorView.post {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun captureAndApply(webView: WebView) {
        if (wallpaperApplied || isFinishing || isDestroyed) return
        if (!webView.isLaidOut || webView.width <= 0 || webView.height <= 0) {
            webView.postDelayed({ captureAndApply(webView) }, 250)
            return
        }
        wallpaperApplied = true
        try {
            val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
            try {
                webView.draw(Canvas(bitmap))
                WallpaperManager.getInstance(this).setBitmap(
                    bitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_LOCK,
                )
            } finally {
                bitmap.recycle()
            }
            getSharedPreferences(LockUiSetupState.PREFERENCES, MODE_PRIVATE)
                .edit().putBoolean(LockUiSetupState.WALLPAPER_APPLIED, true).apply()
            Toast.makeText(this, "Pixel lock screen applied", Toast.LENGTH_SHORT).show()
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    ),
                )
            }
        } catch (_: IOException) {
            Toast.makeText(this, "Could not apply lock screen", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Wallpaper access unavailable", Toast.LENGTH_SHORT).show()
        } finally {
            finish()
        }
    }
}

@Composable
private fun LockWallpaperPage(onPageReady: (WebView) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                // Only this bundled asset is loaded; JavaScript is used for its local 16:10 scaling.
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.allowFileAccessFromFileURLs = false
                settings.allowUniversalAccessFromFileURLs = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setBackgroundColor(android.graphics.Color.BLACK)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.url.toString() != LOCK_WALLPAPER_ASSET_URL

                    override fun onPageFinished(view: WebView, url: String?) {
                        view.postDelayed({ onPageReady(view) }, 1200)
                    }
                }
                loadUrl(LOCK_WALLPAPER_ASSET_URL)
            }
        },
    )
}
