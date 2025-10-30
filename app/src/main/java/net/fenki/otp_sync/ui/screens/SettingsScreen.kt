package net.fenki.otp_sync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import net.fenki.otp_sync.DataStoreRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dataStore = remember { DataStoreRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    val backendUrl by dataStore.backendUrl.collectAsState(initial = "")
    val secret by dataStore.secret.collectAsState(initial = "")
    val ids by dataStore.ids.collectAsState(initial = "")
    var notifyBackend by remember { mutableStateOf(false) }

    var sim1Name by remember { mutableStateOf("SIM 1") }
    var sim2Name by remember { mutableStateOf("SIM 2") }
    var deviceName by remember { mutableStateOf("My Device") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = backendUrl,
            onValueChange = { newUrl ->
                coroutineScope.launch {
                    dataStore.saveBackendUrl(newUrl)
                }
            },
            label = { Text("Backend URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = secret,
            onValueChange = { newSecret ->
                coroutineScope.launch {
                    dataStore.saveSecret(newSecret)
                }
            },
            label = { Text("Auth Key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = ids,
            onValueChange = { newIds ->
                coroutineScope.launch {
                    dataStore.saveIds(newIds)
                }
            },
            label = { Text("User IDs (comma-separated)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = sim1Name,
            onValueChange = { sim1Name = it },
            label = { Text("SIM 1 Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = sim2Name,
            onValueChange = { sim2Name = it },
            label = { Text("SIM 2 Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("Device Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Notify Backend")
            Switch(
                checked = notifyBackend,
                onCheckedChange = { checked ->
                    notifyBackend = checked
                    coroutineScope.launch {
                        dataStore.saveNotifyBackend(checked)
                    }
                }
            )
        }
    }
}
