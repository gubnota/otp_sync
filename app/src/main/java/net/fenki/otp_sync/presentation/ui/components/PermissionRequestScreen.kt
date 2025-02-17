package net.fenki.otp_sync.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import net.fenki.otp_sync.utils.getVersionInfo
import net.fenki.otp_sync.ui.theme.Otp_syncTheme

@Preview(
    name = "Permission Screen Light",
    showBackground = true,
    showSystemUi = true
)
@Composable
fun PreviewPermissionRequestScreen() {
    val context = LocalContext.current
    Otp_syncTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            PermissionRequestScreen(
                onRequestPermissions = {},
                onSettingsClick = {},
                modifier = Modifier,
                context = context
            )
        }
    }
}

@Preview(
    name = "Permission Screen Dark",
    showBackground = true,
    showSystemUi = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewPermissionRequestScreenDark() {
    val context = LocalContext.current
    Otp_syncTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            PermissionRequestScreen(
                onRequestPermissions = {},
                onSettingsClick = {},
                modifier = Modifier,
                context = context
            )
        }
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit,
    context: Context
) {
//     Card(
//         modifier = modifier.fillMaxWidth().background(Color(0xFF000000)),
// //        colors = CardDefaults.cardColors(containerColor = Color(0xFFf0f0f0))
//     ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Permissions Required",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFFFA000)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "To provide OTP sync functionality, please grant the following permissions:",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onRequestPermissions,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Grant Permissions")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onSettingsClick,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Open Settings")
                }
            }
            
            Text(
                text = context.getVersionInfo(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    // }
}