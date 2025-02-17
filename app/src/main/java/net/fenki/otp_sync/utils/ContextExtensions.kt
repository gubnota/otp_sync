package net.fenki.otp_sync.utils

import android.content.Context
import net.fenki.otp_sync.BuildConfig

fun Context.getVersionInfo(): String {
    return "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
} 