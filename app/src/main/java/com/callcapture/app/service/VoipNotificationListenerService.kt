package com.callcapture.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.callcapture.app.OmniCallApplication
import com.callcapture.app.data.model.CallDirection
import com.callcapture.app.data.model.CallType

class VoipNotificationListenerService : NotificationListenerService() {

    private var activeCallNotificationKey: String? = null
    private var activeCallPackage: String? = null

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName
        val callType = CallType.fromPackageName(pkg)
        if (callType == CallType.OTHER_VOIP && !isVoipPackage(pkg)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val category = notification.category

        // Strict filter: Discard any regular messages or social/promotional notifications immediately!
        if (category == Notification.CATEGORY_MESSAGE ||
            category == Notification.CATEGORY_SOCIAL ||
            category == Notification.CATEGORY_PROMO ||
            category == Notification.CATEGORY_RECOMMENDATION) {
            return
        }

        // Check for genuine call action buttons (Answer, Decline, Hang up, End Call)
        val hasCallActions = notification.actions?.any { action ->
            val actTitle = action.title?.toString()?.lowercase() ?: ""
            actTitle.contains("decline") || actTitle.contains("hang up") ||
                    actTitle.contains("answer") || actTitle.contains("end call")
        } ?: false

        val isCallCategory = category == Notification.CATEGORY_CALL
        val isOngoingCall = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 && hasCallActions

        val isCallNotification = isCallCategory || isOngoingCall

        if (isCallNotification) {
            val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
            val audioMode = audioManager?.mode ?: android.media.AudioManager.MODE_NORMAL

            // For VoIP, the phone hardware must be ringing or communicating, or have active call actions
            val isAudioActiveForCall = audioMode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
                    audioMode == android.media.AudioManager.MODE_RINGTONE ||
                    hasCallActions

            if (!isAudioActiveForCall) {
                Log.d(TAG, "Ignoring notification for $pkg: audioMode is NORMAL and no call action found.")
                return
            }

            val prefs = OmniCallApplication.instance.preferenceManager
            val isAllowed = when (callType) {
                CallType.WHATSAPP, CallType.WHATSAPP_BUSINESS -> prefs.isWhatsAppRecordEnabled
                CallType.MESSENGER -> prefs.isMessengerRecordEnabled
                CallType.TELEGRAM -> prefs.isTelegramRecordEnabled
                else -> true
            }

            if (isAllowed) {
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

                // Better caller name extraction:
                // Often title is caller name; if title is "WhatsApp" or "Messenger", subText or text contains caller
                val caller = when {
                    !title.isNullOrBlank() && !title.equals("whatsapp", ignoreCase = true) &&
                            !title.equals("messenger", ignoreCase = true) &&
                            !title.contains("drawer", ignoreCase = true) &&
                            !title.contains("call", ignoreCase = true) -> title
                    !subText.isNullOrBlank() && !subText.contains("call", ignoreCase = true) -> subText
                    !text.isNullOrBlank() && !text.contains("call", ignoreCase = true) -> text
                    else -> callType.displayName
                }

                if (caller != callType.displayName) {
                    latestVoipCallerName = caller
                    latestVoipCallerTime = System.currentTimeMillis()
                }

                if (!CallRecordingService.isServiceRecording) {
                    activeCallNotificationKey = sbn.key
                    activeCallPackage = pkg
                    Log.d(TAG, "VoIP call verified via notification: $callType ($caller)")
                    CallRecordingService.startRecording(
                        this,
                        callType,
                        CallDirection.INCOMING,
                        caller
                    )
                } else if (caller != callType.displayName) {
                    // Update metadata if active recording has a generic name
                    CallRecordingService.startRecording(
                        this,
                        callType,
                        CallDirection.INCOMING,
                        caller
                    )
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        // Only stop if the EXACT active call notification itself was dismissed
        if (sbn.key == activeCallNotificationKey) {
            Log.d(TAG, "VoIP active call notification removed for ${sbn.packageName}. Stopping call recording.")
            activeCallNotificationKey = null
            activeCallPackage = null
            CallRecordingService.stopRecording(this)
        }
    }

    private fun isVoipPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("whatsapp") ||
                lower.contains("orca") ||
                lower.contains("mlite") ||
                (lower.contains("facebook") && !lower.contains("system")) ||
                lower.contains("telegram") ||
                lower.contains("viber") ||
                lower.contains("skype")
    }

    companion object {
        private const val TAG = "VoipNotificationListener"

        @Volatile
        var latestVoipCallerName: String? = null

        @Volatile
        var latestVoipCallerTime: Long = 0
    }
}
