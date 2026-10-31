package com.callcapture.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.callcapture.app.OmniCallApplication
import com.callcapture.app.data.model.CallDirection
import com.callcapture.app.data.model.CallType

private data class NodeInfoCandidate(
    val text: String,
    val isExplicitText: Boolean,
    val isClickable: Boolean,
    val className: String?,
    val viewId: String?
)

class VoipAccessibilityService : AccessibilityService() {

    private var activeCallPackage: String? = null
    private var detectedCallerName: String? = null
    private var isCallInProgress = false
    private val handler = Handler(Looper.getMainLooper())
    private var callDismissDebounceRunnable: Runnable? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        val callType = CallType.fromPackageName(packageName)
        val isVoip = isVoipPackage(packageName) || callType != CallType.OTHER_VOIP

        // If a call is in progress and the user leaves the VoIP app (home screen, lock screen, proximity off)
        if (isCallInProgress && !isVoip) {
            val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
            val audioMode = audioManager?.mode ?: android.media.AudioManager.MODE_NORMAL
            val isHardwareInCall = audioMode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
                    audioMode == android.media.AudioManager.MODE_IN_CALL

            if (isHardwareInCall) {
                // Call is active in background or screen is off — keep recording!
                cancelDismissCheck()
                return
            } else {
                scheduleDismissCheck()
                return
            }
        }

        if (!isVoip) return

        // Verify if user enabled recording for this specific app
        val prefs = OmniCallApplication.instance.preferenceManager
        val isAllowed = when (callType) {
            CallType.WHATSAPP, CallType.WHATSAPP_BUSINESS -> prefs.isWhatsAppRecordEnabled
            CallType.MESSENGER -> prefs.isMessengerRecordEnabled
            CallType.TELEGRAM -> prefs.isTelegramRecordEnabled
            else -> true
        }
        if (!isAllowed) return

        val rootNode = rootInActiveWindow ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                inspectCallWindow(callType, packageName, rootNode)
            }
        }
    }

    private fun inspectCallWindow(callType: CallType, packageName: String, rootNode: AccessibilityNodeInfo) {
        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        val audioMode = audioManager?.mode ?: android.media.AudioManager.MODE_NORMAL

        // CRITICAL CHECK: In Android, WhatsApp / Messenger / Telegram VoIP calls ALWAYS switch
        // audio hardware to MODE_IN_COMMUNICATION or MODE_RINGTONE / MODE_IN_CALL.
        val isHardwareInCall = audioMode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
                audioMode == android.media.AudioManager.MODE_RINGTONE ||
                audioMode == android.media.AudioManager.MODE_IN_CALL

        if (!isHardwareInCall) {
            if (isCallInProgress) {
                scheduleDismissCheck()
            }
            return
        }

        val candidates = mutableListOf<NodeInfoCandidate>()
        val flatTexts = mutableListOf<String>()
        extractAllCandidates(rootNode, candidates, flatTexts)
        val lowerTexts = flatTexts.map { it.lowercase() }

        // Check if explicit call-termination text appears
        val isCallTerminated = lowerTexts.any {
            it == "call ended" || it == "call declined" ||
                    it == "call busy" || it == "no answer" ||
                    it == "couldn't connect" || it == "disconnected"
        }

        if (isCallTerminated) {
            if (isCallInProgress) {
                Log.d(TAG, "VoIP call explicit termination detected in UI ($callType). Stopping recording.")
                isCallInProgress = false
                activeCallPackage = null
                detectedCallerName = null
                CallRecordingService.stopRecording(this)
            }
            return
        }

        val isCallActive = isVoipCallScreen(packageName, flatTexts)

        if (isCallActive) {
            cancelDismissCheck()

            if (!isCallInProgress) {
                if (!com.callcapture.app.util.PermissionHelper.hasRecordAudioPermission(this)) {
                    Log.w(TAG, "VoIP call detected, but RECORD_AUDIO permission is not granted.")
                    return
                }

                isCallInProgress = true
                activeCallPackage = packageName
                detectedCallerName = extractCallerName(packageName, candidates)

                Log.d(TAG, "VoIP call verified: $callType with $detectedCallerName")
                try {
                    CallRecordingService.startRecording(
                        this,
                        callType,
                        CallDirection.UNKNOWN,
                        detectedCallerName ?: callType.displayName
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start CallRecordingService from accessibility", e)
                }
            } else if (detectedCallerName == null || detectedCallerName == callType.displayName || !isCleanCallerName(detectedCallerName ?: "")) {
                // If call is already recording but name wasn't extracted yet (e.g. connecting screen transition),
                // try to capture the caller name as soon as it renders on screen!
                val betterName = extractCallerName(packageName, candidates)
                if (!betterName.isNullOrBlank() && betterName != callType.displayName && isCleanCallerName(betterName)) {
                    detectedCallerName = betterName
                    Log.d(TAG, "Refined VoIP caller name during active call: $betterName")
                    CallRecordingService.startRecording(
                        this,
                        callType,
                        CallDirection.UNKNOWN,
                        betterName
                    )
                }
            }
        } else {
            if (isCallInProgress) {
                scheduleDismissCheck()
            }
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

    private fun isVoipCallScreen(packageName: String, texts: List<String>): Boolean {
        val lowerTexts = texts.map { it.lowercase() }

        // Strict filter: must NOT be an active chat / messaging / browsing screen
        val isChatOrBrowsing = lowerTexts.any {
            it.contains("type a message") || it.contains("ask meta") ||
                    it == "chats" || it == "status" || it == "calls" || it == "communities" ||
                    it.contains("search…") || it.contains("search...")
        }
        if (isChatOrBrowsing) {
            return false
        }

        // Must have an explicit call termination control ("End call", "Leave call", "Hang up", "Decline", "End", "Leave")
        val hasEndCallButton = lowerTexts.any {
            it.contains("end call") || it.contains("leave call") ||
                    it.contains("hang up") || it == "end" || it == "leave" || it == "decline"
        }

        // Must have explicit calling status
        val hasCallStatus = lowerTexts.any {
            it.contains("calling") || it.contains("ringing") ||
                    it.contains("connecting") || it.contains("whatsapp call") ||
                    it.contains("messenger audio call") || it.contains("messenger video call") ||
                    it.contains("in a call") || it.contains("ongoing call") ||
                    it.contains("telegram call")
        }

        return hasEndCallButton || hasCallStatus
    }

    private fun isCleanCallerName(rawText: String): Boolean {
        val trimmed = rawText.trim()
        val lower = trimmed.lowercase()

        if (trimmed.length < 2 || trimmed.length > 50) return false

        // Discard timestamps (e.g. 0:05, 12:34, 01:23:45)
        if (lower.matches(".*\\b\\d{1,2}:\\d{2}(:\\d{2})?\\b.*".toRegex())) return false

        // Block UI drawer, buttons, control action names
        val blockedSubstrings = listOf(
            "drawer", "minimize", "maximize", "collapse", "expand", "floating",
            "camera", "flip", "switch", "video", "audio", "mic", "microphone",
            "mute", "unmute", "speaker", "earpiece", "headset", "bluetooth",
            "effect", "filter", "activity", "activities", "avatar", "reaction",
            "share", "screen share", "watch together", "add people", "invite",
            "chat", "message", "profile", "option", "setting", "more",
            "calling", "ringing", "connecting", "reconnecting", "ended", "busy",
            "decline", "hang up", "answer", "leave", "end call", "end",
            "tap to", "press", "hold", "back", "navigate", "close", "cancel",
            "encrypted", "end-to-end", "meta", "facebook", "messenger", "whatsapp",
            "telegram", "viber", "skype", "incoming", "outgoing", "missed",
            "duration", "network", "connection", "waiting for", "return to call",
            "ongoing call", "view profile", "open chat", "pip", "picture-in-picture",
            "details", "info", "member", "done", "edit", "swap"
        )

        for (blocked in blockedSubstrings) {
            if (lower.contains(blocked)) {
                return false
            }
        }

        return true
    }

    private fun extractCallerName(packageName: String, candidates: List<NodeInfoCandidate>): String? {
        // Priority 1: TextViews with viewId explicitly containing name, title, caller, user, contact
        for (c in candidates) {
            if (c.isExplicitText && c.viewId != null) {
                val vid = c.viewId
                if (vid.contains("name") || vid.contains("title") || vid.contains("user") ||
                    vid.contains("caller") || vid.contains("contact") || vid.contains("participant")) {
                    if (isCleanCallerName(c.text)) {
                        return c.text
                    }
                }
            }
        }

        // Priority 2: Non-clickable TextViews with explicit node.text
        for (c in candidates) {
            if (c.isExplicitText && !c.isClickable) {
                if (isCleanCallerName(c.text)) {
                    return c.text
                }
            }
        }

        // Priority 3: Check recent high-confidence name from NotificationListener
        val notifName = VoipNotificationListenerService.latestVoipCallerName
        val notifTime = VoipNotificationListenerService.latestVoipCallerTime
        if (!notifName.isNullOrBlank() && (System.currentTimeMillis() - notifTime) < 60_000) {
            if (isCleanCallerName(notifName)) {
                return notifName
            }
        }

        // Priority 4: Any other explicit node.text
        for (c in candidates) {
            if (c.isExplicitText) {
                if (isCleanCallerName(c.text)) {
                    return c.text
                }
            }
        }

        // Priority 5: Non-clickable contentDescription as last resort
        for (c in candidates) {
            if (!c.isExplicitText && !c.isClickable) {
                if (isCleanCallerName(c.text)) {
                    return c.text
                }
            }
        }

        return null
    }

    private fun extractAllCandidates(
        node: AccessibilityNodeInfo?,
        result: MutableList<NodeInfoCandidate>,
        flatTexts: MutableList<String>
    ) {
        if (node == null) return

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val isClickable = node.isClickable
        val className = node.className?.toString()
        val viewId = node.viewIdResourceName?.lowercase()

        if (!text.isNullOrBlank()) {
            flatTexts.add(text)
            result.add(
                NodeInfoCandidate(
                    text = text,
                    isExplicitText = true,
                    isClickable = isClickable,
                    className = className,
                    viewId = viewId
                )
            )
        }

        if (!desc.isNullOrBlank()) {
            flatTexts.add(desc)
            if (desc != text) {
                result.add(
                    NodeInfoCandidate(
                        text = desc,
                        isExplicitText = false,
                        isClickable = isClickable,
                        className = className,
                        viewId = viewId
                    )
                )
            }
        }

        for (i in 0 until node.childCount) {
            extractAllCandidates(node.getChild(i), result, flatTexts)
        }
    }

    private fun scheduleDismissCheck() {
        if (callDismissDebounceRunnable != null) return

        callDismissDebounceRunnable = Runnable {
            val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
            val audioMode = audioManager?.mode ?: android.media.AudioManager.MODE_NORMAL
            val isHardwareInCall = audioMode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
                    audioMode == android.media.AudioManager.MODE_IN_CALL

            if (isCallInProgress && !isHardwareInCall) {
                Log.d(TAG, "VoIP call ended (hardware audio is normal). Stopping recording.")
                isCallInProgress = false
                activeCallPackage = null
                detectedCallerName = null
                CallRecordingService.stopRecording(this)
            }
            callDismissDebounceRunnable = null
        }
        // 3.5 seconds debounce: ensures screen off / proximity sensor / app switches don't kill active recording
        handler.postDelayed(callDismissDebounceRunnable!!, 3500)
    }

    private fun cancelDismissCheck() {
        callDismissDebounceRunnable?.let { handler.removeCallbacks(it) }
        callDismissDebounceRunnable = null
    }

    override fun onInterrupt() {
        Log.w(TAG, "VoipAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelDismissCheck()
        if (isCallInProgress) {
            CallRecordingService.stopRecording(this)
        }
    }

    companion object {
        private const val TAG = "VoipAccessibilitySvc"
    }
}
