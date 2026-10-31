package com.callcapture.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.callcapture.app.OmniCallApplication
import com.callcapture.app.data.model.CallDirection
import com.callcapture.app.data.model.CallType
import com.callcapture.app.service.CallRecordingService

class PhoneCallReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val prefs = OmniCallApplication.instance.preferenceManager
        if (!prefs.isCellularRecordEnabled) return

        val action = intent.action

        if (action == Intent.ACTION_NEW_OUTGOING_CALL) {
            val outgoingNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            savedNumber = outgoingNumber
            isIncoming = false
            Log.d(TAG, "Outgoing call initiated to: $outgoingNumber")
            return
        }

        if (action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            if (!incomingNumber.isNullOrBlank()) {
                savedNumber = incomingNumber
            }

            when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    isIncoming = true
                    lastState = TelephonyManager.CALL_STATE_RINGING
                    Log.d(TAG, "Phone ringing from: $savedNumber")
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    if (lastState != TelephonyManager.CALL_STATE_OFFHOOK) {
                        lastState = TelephonyManager.CALL_STATE_OFFHOOK

                        if (!com.callcapture.app.util.PermissionHelper.hasRecordAudioPermission(context)) {
                            Log.w(TAG, "Phone call connected, but RECORD_AUDIO permission is not granted. Skipping recording.")
                            return
                        }

                        // Check if this OFFHOOK state is triggered by a self-managed VoIP call (Messenger / WhatsApp)
                        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                        val isManagedCarrierCall = try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                telecomManager?.isInManagedCall == true
                            } else {
                                true
                            }
                        } catch (_: Exception) {
                            true
                        }

                        if (!isManagedCarrierCall && savedNumber.isNullOrBlank()) {
                            Log.d(TAG, "Telephony OFFHOOK received but TelecomManager.isInManagedCall is false. This is a VoIP call (Messenger/WhatsApp), skipping CELLULAR recording.")
                            return
                        }

                        // If number is still unknown, try checking latest call log
                        var numberToUse = savedNumber
                        if (numberToUse.isNullOrBlank()) {
                            numberToUse = com.callcapture.app.util.ContactHelper.queryLatestCallLogEntry(context)
                        }

                        val direction = if (isIncoming) CallDirection.INCOMING else CallDirection.OUTGOING
                        Log.d(TAG, "Phone call connected ($direction) with $numberToUse. Starting recording.")
                        try {
                            CallRecordingService.startRecording(
                                context,
                                CallType.CELLULAR,
                                direction,
                                numberToUse
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error requesting call recording service start", e)
                        }
                    }
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    Log.d(TAG, "Phone call state IDLE received. Stopping recording.")
                    CallRecordingService.stopRecording(context)
                    lastState = TelephonyManager.CALL_STATE_IDLE
                    isIncoming = false
                    savedNumber = null
                }
            }
        }
    }

    companion object {
        private const val TAG = "PhoneCallReceiver"
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var isIncoming = false
        private var savedNumber: String? = null
    }
}
