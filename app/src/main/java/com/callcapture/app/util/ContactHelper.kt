package com.callcapture.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

object ContactHelper {

    private const val TAG = "ContactHelper"

    fun getContactName(context: Context, phoneNumber: String?): String? {
        if (!phoneNumber.isNullOrBlank()) {
            val resolved = queryContactByNumber(context, phoneNumber)
            if (!resolved.isNullOrBlank()) {
                return resolved
            }
        }

        // If phone number was null, blank, or not in contacts by number lookup,
        // try to find the latest call entry from CallLog
        return queryLatestCallLogEntry(context)
    }

    private fun queryContactByNumber(context: Context, phoneNumber: String): String? {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in queryContactByNumber", e)
        }

        return null
    }

    fun queryLatestCallLogEntry(context: Context): String? {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val projection = arrayOf(
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE
        )

        try {
            // Check calls within the last 30 seconds
            val thirtySecondsAgo = System.currentTimeMillis() - 30_000
            val selection = "${CallLog.Calls.DATE} >= ?"
            val selectionArgs = arrayOf(thirtySecondsAgo.toString())

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)

                    val cachedName = if (nameIdx != -1) cursor.getString(nameIdx) else null
                    if (!cachedName.isNullOrBlank()) {
                        return cachedName
                    }

                    val number = if (numIdx != -1) cursor.getString(numIdx) else null
                    if (!number.isNullOrBlank()) {
                        // Try resolving number to contact
                        val resolved = queryContactByNumber(context, number)
                        return resolved ?: number
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying CallLog", e)
        }

        return null
    }
}
