package com.example.sentriai.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.sentriai.data.ProfileStore

data class SmsResult(
    val success: Boolean,
    val handlerNumber: String,
    val failureReason: String? = null,
)

object EmergencySmsSender {

    private const val TAG = "EmergencySmsSender"

    /**
     * Checks if the app has SEND_SMS permission.
     */
    fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Sends an emergency SMS to the registered handler number.
     *
     * @param context Application context.
     * @param emergencyType Category of emergency (e.g. "Fall", "Medical Distress").
     * @param triggerPhrase Phrase spoken by the user.
     * @param confidence Confidence score (0.0 - 1.0).
     */
    fun sendEmergencyAlert(
        context: Context,
        emergencyType: String,
        triggerPhrase: String,
        confidence: Float,
    ): SmsResult {
        val prefs = ProfileStore.preferences(context)
        val handlerMobile = prefs.getString(ProfileStore.KEY_HANDLER_MOBILE, "")?.trim() ?: ""
        val handlerName = prefs.getString(ProfileStore.KEY_HANDLER_NAME, "Caregiver") ?: "Caregiver"
        val userName = prefs.getString(ProfileStore.KEY_FULL_NAME, "User") ?: "User"

        if (handlerMobile.isBlank()) {
            Log.e(TAG, "No handler mobile number configured in ProfileStore.")
            return SmsResult(
                success = false,
                handlerNumber = "Unconfigured",
                failureReason = "Emergency handler number is missing in settings"
            )
        }

        if (!hasSmsPermission(context)) {
            Log.w(TAG, "SEND_SMS permission not granted.")
            return SmsResult(
                success = false,
                handlerNumber = handlerMobile,
                failureReason = "SMS permission not granted by user"
            )
        }

        val message = " SentriAI Alert for $userName!\n" +
                "Detected: $emergencyType\n" +
                "Phrase: \"$triggerPhrase\"\n" +
                "Confidence: ${(confidence * 100).toInt()}%\n" +
                "Please check on $userName immediately."

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(handlerMobile, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(handlerMobile, null, message, null, null)
            }

            Log.i(TAG, "Emergency SMS successfully sent to $handlerName ($handlerMobile)")
            SmsResult(
                success = true,
                handlerNumber = handlerMobile
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $handlerMobile: ${e.message}", e)
            SmsResult(
                success = false,
                handlerNumber = handlerMobile,
                failureReason = e.message ?: "Failed to transmit SMS"
            )
        }
    }
}
