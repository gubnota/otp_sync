package net.fenki.otp_sync

import MainScreen
import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import net.fenki.otp_sync.ui.components.PermissionRequestScreen
import net.fenki.otp_sync.ui.theme.Otp_syncTheme
import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import net.fenki.otp_sync.domain.managers.PermissionManager
import net.fenki.otp_sync.utils.getVersionInfo

class MainActivity : ComponentActivity() {
    private var allPermissionsGranted by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private val PERMISSION_REQUEST_CODE = 1001
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            allPermissionsGranted = permissions.all { it.value }

            if (allPermissionsGranted) {
                if (!areNotificationsEnabled()) {
                    openNotificationSettings()
                } else {
                    startSmsCallService()
                }
            } else {
                Toast.makeText(this, "Some permissions were not granted. Please enable them in settings.", Toast.LENGTH_LONG).show()
                openAppSettings()
            }
        }

    fun areAllPermissionsGranted(context: Context): Boolean {
        val required = PermissionManager.getRequiredPermissions()
        return required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        allPermissionsGranted = areAllPermissionsGranted(this)
        if (!allPermissionsGranted) {
            checkAndRequestPermissions()
        } else {
            startSmsCallService()
            showSettings = true
        }

        val requiredPermissions = PermissionManager.getRequiredPermissions()
        allPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!allPermissionsGranted) checkAndRequestPermissions()

        if (allPermissionsGranted) {
            startSmsCallService()
            showSettings = true
        }

        setContent {
            Otp_syncTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(
                        allPermissionsGranted = allPermissionsGranted,
                        onRequestPermissions = { checkAndRequestPermissions() },
                        modifier = Modifier.padding(innerPadding),
                        context = this,
                        showSettings = showSettings,
                        onSettingsChange = { showSettings = it }
                    )
                }
            }
        }
    }

    fun isNotificationChannelEnabled(context: Context, channelId: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(channelId)
            channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = PermissionManager.getRequiredPermissions()

        val permissionsToRequest = requiredPermissions
            .filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
            return
        }

        val channelEnabled = isNotificationChannelEnabled(this, "SmsCallForegroundServiceChannel")
        if (!channelEnabled) {
            Toast.makeText(this, "Notifications are disabled for service channel", Toast.LENGTH_LONG).show()
            openNotificationSettings()
            return
        }

        allPermissionsGranted = true
        showSettings = true
        startSmsCallService()
    }

    private fun startSmsCallService() {
        val serviceIntent = Intent(this, SmsCallForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        if (areAllPermissionsGranted(this) && areNotificationsEnabled()) {
            startSmsCallService()
            showSettings = true
        }
    }
}

@Composable
fun MainContent(
    allPermissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    context: Context,
    showSettings: Boolean,
    onSettingsChange: (Boolean) -> Unit
) {
    if (allPermissionsGranted || showSettings) {
        MainScreen(
            context = context,
            onIdsChange = {},
            onSecretChange = {},
            onNotifyBackendChange = {},
            onBackendUrlChange = {},
            modifier = modifier,
            onPermissionsClick = { onSettingsChange(false) }
        )
    } else {
        PermissionRequestScreen(
            onRequestPermissions = onRequestPermissions,
            modifier = modifier,
            onSettingsClick = { onSettingsChange(true) },
            context = context
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Otp_syncTheme {
        MainScreen(
            onIdsChange = {},
            onSecretChange = {},
            onNotifyBackendChange = {},
            onBackendUrlChange = {},
            context = LocalContext.current,
            modifier = Modifier,
            onPermissionsClick = {}
        )
    }
}
