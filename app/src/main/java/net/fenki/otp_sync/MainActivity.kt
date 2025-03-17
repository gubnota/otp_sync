package net.fenki.otp_sync

import MainScreen
import android.Manifest
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
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import net.fenki.otp_sync.domain.managers.PermissionManager
import net.fenki.otp_sync.utils.getVersionInfo
//import net.fenki.otp_sync.domain.managers.EnvironmentManager

class MainActivity : ComponentActivity() {
    private var allPermissionsGranted by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private val PERMISSION_REQUEST_CODE = 1001
    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                allPermissionsGranted = permissions.all { it.value }
                if (allPermissionsGranted) {
                    startSmsCallService()
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize environment variables
//        EnvironmentManager.init(this)

        // Check permissions before starting service
        val requiredPermissions = PermissionManager.getRequiredPermissions()
        allPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!allPermissionsGranted) checkAndRequestPermissions();

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

    private fun checkAndRequestPermissions() {
        val requiredPermissions = PermissionManager.getRequiredPermissions()
        val permissionsToRequest = requiredPermissions
            .filter {
                ContextCompat.checkSelfPermission(this, it) !=
                    PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            startSmsCallService()
        }
        Log.d("checkAndRequestP","${permissionsToRequest.joinToString(",")}, $allPermissionsGranted, $showSettings");

        if(permissionsToRequest.isEmpty()) {
            allPermissionsGranted = true; showSettings = true;
            Log.d("checkAndRequestP","$allPermissionsGranted, $showSettings");
        }
    }

    private fun startSmsCallService() {
        val serviceIntent = Intent(this, SmsCallForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
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
