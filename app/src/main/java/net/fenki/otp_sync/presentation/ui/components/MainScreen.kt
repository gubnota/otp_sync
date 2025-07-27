import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.clickable
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import net.fenki.otp_sync.component.MyHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URL
import androidx.compose.runtime.collectAsState
import net.fenki.otp_sync.DataStoreRepository
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import net.fenki.otp_sync.SmsReceiver
import net.fenki.otp_sync.ui.theme.Otp_syncTheme
import net.fenki.otp_sync.utils.getVersionInfo

@Composable
fun MainScreen(
    context: Context,
    modifier: Modifier = Modifier,
    onIdsChange: (String) -> Unit,
    onSecretChange: (String) -> Unit,
    onNotifyBackendChange: (Boolean) -> Unit,
    onBackendUrlChange: (String) -> Unit,
    onPermissionsClick: () -> Unit
) {
    val dataStore = remember { DataStoreRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    val backendUrlFromStore by dataStore.backendUrl.collectAsState(initial = "")
    val secretFromStore by dataStore.secret.collectAsState(initial = "")
    val notifyBackend by dataStore.notifyBackend.collectAsState(initial = false)
    val idsFromStore by dataStore.ids.collectAsState(initial = "")

    var backendUrlLocal by remember { mutableStateOf(backendUrlFromStore) }
    var secretLocal by remember { mutableStateOf(secretFromStore) }
    var idsLocal by remember { mutableStateOf(idsFromStore) }

    // Sync local with datastore if changed externally
    LaunchedEffect(backendUrlFromStore) {
        if (backendUrlLocal != backendUrlFromStore) backendUrlLocal = backendUrlFromStore
    }
    LaunchedEffect(secretFromStore) {
        if (secretLocal != secretFromStore) secretLocal = secretFromStore
    }
    LaunchedEffect(idsFromStore) {
        if (idsLocal != idsFromStore) idsLocal = idsFromStore
    }

    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val httpClient = remember(context) { MyHttpClient(context).create() }

    fun validateAndFixUrl(url: String): String {
        var fixedUrl = url.trim()
        if (!fixedUrl.startsWith("http://") && !fixedUrl.startsWith("https://")) {
            fixedUrl = "https://$fixedUrl"
        }
        if (!fixedUrl.endsWith("/")) {
            fixedUrl = "$fixedUrl/"
        }
        return fixedUrl
    }

    fun testBackend() {
        if (backendUrlLocal.isBlank()) {
            showError = true
            errorMessage = "Please enter a backend URL"
            return
        }

        coroutineScope.launch {
            try {
                val testReceiver = SmsReceiver()
                testReceiver.sendToBackend(
                    context = context,
                    type = "TEST",
                    data = "Connection test message"
                )
                showError = true
                errorMessage = "Test message sent successfully!\n" +
                    "URL: $backendUrlLocal\n" +
                    "IDs: $idsLocal\n" +
                    "Notifications enabled: $notifyBackend"
            } catch (e: Exception) {
                showError = true
                val baseError = when {
                    e is java.net.UnknownHostException ->
                        "Could not resolve host address.\nPlease check if the URL is correct."
                    e is java.net.ConnectException ->
                        "Could not connect to server.\nPlease check if:\n" +
                            "- The server is running\n" +
                            "- The port number is correct\n" +
                            "- Your device has internet access"
                    e is javax.net.ssl.SSLHandshakeException ->
                        "SSL Certificate error.\nPlease check if:\n" +
                            "- The URL uses the correct protocol (http/https)\n" +
                            "- The server's SSL certificate is valid"
                    e is java.net.SocketTimeoutException ->
                        "Connection timed out.\nThe server took too long to respond."
                    else -> "Unexpected error: ${e.message}"
                }
                errorMessage = "Connection test failed!\n\n" +
                    "$baseError\n\n" +
                    "Configuration:\n" +
                    "URL: $backendUrlLocal\n" +
                    "IDs: $idsLocal\n" +
                    "Notifications enabled: $notifyBackend"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .clickable { focusManager.clearFocus() },
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Button(
                onClick = onPermissionsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Permissions")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = idsLocal,
                onValueChange = {
                    idsLocal = it
                    coroutineScope.launch {
                        dataStore.saveIds(it)
                        onIdsChange(it)
                    }
                },
                label = { Text("IDs") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = secretLocal,
                onValueChange = {
                    secretLocal = it
                    coroutineScope.launch {
                        dataStore.saveSecret(it)
                        onSecretChange(it)
                    }
                },
                label = { Text("Secret") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = backendUrlLocal,
                onValueChange = {
                    backendUrlLocal = it
                    coroutineScope.launch {
                        dataStore.saveBackendUrl(it)
                        onBackendUrlChange(it)
                    }
                },
                label = { Text("Backend URL") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Notify Backend")
                Switch(
                    checked = notifyBackend,
                    onCheckedChange = { checked ->
                        coroutineScope.launch {
                            dataStore.saveNotifyBackend(checked)
                            onNotifyBackendChange(checked)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { testBackend() },
                enabled = backendUrlLocal.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test Connection")
            }
        }

        Text(
            text = context.getVersionInfo(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text("Test Result") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showError = false }) {
                    Text("OK")
                }
            }
        )
    }
}


@Preview(
    name = "MainScreen Preview",
    showBackground = true,
    showSystemUi = true
)
@Composable
fun MainScreenPreview() {
    val context = LocalContext.current
    Otp_syncTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainScreen(
                context = context,
                onIdsChange = {},
                onSecretChange = {},
                onNotifyBackendChange = {},
                onBackendUrlChange = {},
                onPermissionsClick = {}
            )
        }
    }
}

@Preview(
    name = "MainScreen Dark Preview",
    showBackground = true,
    showSystemUi = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun MainScreenDarkPreview() {
    val context = LocalContext.current
    Otp_syncTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainScreen(
                context = context,
                onIdsChange = {},
                onSecretChange = {},
                onNotifyBackendChange = {},
                onBackendUrlChange = {},
                onPermissionsClick = {}
            )
        }
    }
}
