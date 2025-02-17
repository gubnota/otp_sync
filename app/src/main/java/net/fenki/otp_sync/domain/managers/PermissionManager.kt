package net.fenki.otp_sync.domain.managers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionManager {
    fun getRequiredPermissions(): List<String> {
        return mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.FOREGROUND_SERVICE,
            // Manifest.permission.INSTALL_PACKAGES
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // add(Manifest.permission.MANAGE_OWN_CALLS)
                // add(Manifest.permission.FOREGROUND_SERVICE_PHONE_CALL)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun checkPermissions(context: Context): List<String> {
        return getRequiredPermissions()
            .filter {
                ContextCompat.checkSelfPermission(context, it) != 
                    PackageManager.PERMISSION_GRANTED
            }
    }

    fun areAllPermissionsGranted(context: Context): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == 
                PackageManager.PERMISSION_GRANTED
        }
    }
}