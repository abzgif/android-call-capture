package com.callcapture.app.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.callcapture.app.service.VoipAccessibilityService
import com.callcapture.app.service.VoipNotificationListenerService

object PermissionHelper {

    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPhoneStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCallLogPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, VoipAccessibilityService::class.java)
        val expectedFlattened = expectedComponent.flattenToString()
        val expectedShortFlattened = expectedComponent.flattenToShortString()

        // 1. Check via Settings.Secure (most reliable across Android versions & manufacturer skins)
        try {
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (!enabledServicesSetting.isNullOrBlank()) {
                val colonSplitter = enabledServicesSetting.split(":")
                if (colonSplitter.any { item ->
                        item.equals(expectedFlattened, ignoreCase = true) ||
                        item.equals(expectedShortFlattened, ignoreCase = true) ||
                        (item.contains(expectedComponent.packageName) && item.contains("VoipAccessibilityService"))
                    }) {
                    return true
                }
            }
        } catch (_: Exception) {}

        // 2. Fallback to AccessibilityManager list
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

            if (enabledServices.any {
                    val serviceInfo = it.resolveInfo.serviceInfo
                    serviceInfo.packageName == expectedComponent.packageName &&
                            (serviceInfo.name == expectedComponent.className || serviceInfo.name.endsWith("VoipAccessibilityService"))
                }) {
                return true
            }
        } catch (_: Exception) {}

        return false
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        val expectedComponent = ComponentName(context, VoipNotificationListenerService::class.java)
        return enabledListeners.contains(expectedComponent.flattenToString())
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (_: Exception) {
            false
        }
    }
}
