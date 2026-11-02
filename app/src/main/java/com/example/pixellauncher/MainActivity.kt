package com.example.pixellauncher

import android.app.role.RoleManager
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    // Goes up each time the Home button is pressed while we are already open.
    private val homeTick = mutableIntStateOf(0)
    private val resumeTick = mutableIntStateOf(0)
    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private val notificationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        syncLockPresentationService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        addOnNewIntentListener { homeTick.intValue++ }
        setContent { HomeScreen(homeTick.intValue, resumeTick.intValue) }
        window.decorView.post { requestHomeRoleIfNeeded() }
    }

    private fun requestHomeRoleIfNeeded() {
        val manager = getSystemService(RoleManager::class.java)
        if (manager?.isRoleHeld(RoleManager.ROLE_HOME) == true) return

        val requestIntent = if (manager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
            manager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }
        runCatching { homeRoleRequest.launch(requestIntent) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
        if (!isSelectedAsHome()) {
            stopService(Intent(this, LockPresentationService::class.java))
            return
        }

        val setup = getSharedPreferences(LockUiSetupState.PREFERENCES, MODE_PRIVATE)
        if (!setup.getBoolean(LockUiSetupState.AUTO_SETUP_ATTEMPTED, false)) {
            setup.edit().putBoolean(LockUiSetupState.AUTO_SETUP_ATTEMPTED, true).apply()
            runCatching { startActivity(Intent(this, LockWallpaperActivity::class.java)) }
                .onFailure { setup.edit().putBoolean(LockUiSetupState.AUTO_SETUP_ATTEMPTED, false).apply() }
            return
        }
        if (Settings.canDrawOverlays(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !setup.getBoolean(LockUiSetupState.NOTIFICATION_PERMISSION_ASKED, false)
        ) {
            setup.edit().putBoolean(LockUiSetupState.NOTIFICATION_PERMISSION_ASKED, true).apply()
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        syncLockPresentationService()
    }

    private fun isSelectedAsHome(): Boolean =
        getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true

    private fun syncLockPresentationService() {
        val service = Intent(this, LockPresentationService::class.java)
        if (Settings.canDrawOverlays(this)) {
            runCatching { ContextCompat.startForegroundService(this, service) }
        } else {
            stopService(service)
        }
    }
}
