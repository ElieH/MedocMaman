package com.medocmaman.alerts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.medocmaman.data.local.AlertLevel
import com.medocmaman.data.local.AppPreferences
import com.medocmaman.data.local.CallLogEntity
import com.medocmaman.data.remote.FirebaseRepository

object EmergencyAlertManager {
    private const val TAG = "EmergencyAlertManager"

    /**
     * Records the call outcome in Room + Firestore and triggers SMS fallback
     * whenever the alert is CRITICAL / IMMEDIATE or when offline.
     */
    suspend fun dispatchOutcomeAndAlert(
        context: Context,
        callLog: CallLogEntity,
        medNameFr: String,
        reasonSummaryFr: String
    ) {
        val syncedToCloud = FirebaseRepository.pushCallLog(context, callLog)
        val level = runCatching { AlertLevel.valueOf(callLog.alertLevel) }.getOrDefault(AlertLevel.NONE)

        // Always send direct SMS on CRITICAL (VITE 101), or on IMMEDIATE/TODAY when offline
        if (level == AlertLevel.CRITICAL || level == AlertLevel.IMMEDIATE || (!syncedToCloud && level != AlertLevel.NONE)) {
            sendSmsToCaregivers(context, level, medNameFr, reasonSummaryFr)
        }
    }

    fun sendSmsToCaregivers(
        context: Context,
        level: AlertLevel,
        medNameFr: String,
        reasonSummaryFr: String
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "SEND_SMS permission not granted; cannot send SMS fallback")
            return
        }

        val prefs = AppPreferences(context)
        val motherName = prefs.motherFirstName
        val address = prefs.motherAddress
        val prefix = when (level) {
            AlertLevel.CRITICAL -> "🚨 URGENCE VITE 101 ($motherName)"
            AlertLevel.IMMEDIATE -> "⚠️ ALERTE MÉDICAMENT ($motherName)"
            else -> "ℹ️ Info Médicament ($motherName)"
        }
        val addressPart = if (level == AlertLevel.CRITICAL && address.isNotBlank()) {
            " | Adresse: $address"
        } else {
            ""
        }
        val message = "$prefix : $medNameFr — $reasonSummaryFr$addressPart. Merci de l'appeler immédiatement."

        val smsManager: SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        for (phone in prefs.caregiverNumbers()) {
            try {
                val parts = smsManager?.divideMessage(message)
                if (parts != null && parts.size > 1) {
                    smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                } else {
                    smsManager?.sendTextMessage(phone, null, message, null, null)
                }
                Log.i(TAG, "Sent alert SMS ($level) to $phone")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS to $phone: ${e.message}")
            }
        }
    }
}
