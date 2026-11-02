package com.example.pixellauncher

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : ComponentActivity() {
    // Goes up each time the Home button is pressed while we are already open.
    private val homeTick = mutableIntStateOf(0)
    private val resumeTick = mutableIntStateOf(0)
    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

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
    }
}
