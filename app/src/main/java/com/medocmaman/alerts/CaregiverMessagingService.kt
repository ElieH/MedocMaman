package com.medocmaman.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.medocmaman.MainActivity
import com.medocmaman.R
import com.medocmaman.alarm.MedicationAlarmScheduler
import com.medocmaman.data.local.AppPreferences
import com.medocmaman.data.local.AppRole
import com.medocmaman.data.remote.FirebaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CaregiverMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val type = data["type"] ?: ""

        // 1. Silent schedule refresh from Google Sheet -> Apps Script -> FCM
        if (type == "SYNC_SCHEDULE") {
            CoroutineScope(Dispatchers.IO).launch {
                FirebaseRepository.pullFromFirestoreToRoom(applicationContext)
                MedicationAlarmScheduler.scheduleAllActiveMedications(applicationContext)
            }
            return
        }

        // 2. Caregiver Alert Siren (IMMEDIATE / CRITICAL)
        val prefs = AppPreferences(applicationContext)
        if (prefs.appRole == AppRole.CAREGIVER || type == "CAREGIVER_ALERT") {
            val callLogId = data["call_log_id"] ?: "alert_${System.currentTimeMillis()}"
            val title = data["title"] ?: message.notification?.title ?: "⚠️ Alerte Médicament Maman"
            val body = data["body"] ?: message.notification?.body ?: "Intervention requise."
            val level = data["alert_level"] ?: "IMMEDIATE"

            if (level == "IMMEDIATE" || level == "CRITICAL") {
                CaregiverSirenController.startSiren(
                    applicationContext,
                    ActiveCaregiverAlert(
                        callLogId = callLogId,
                        title = title,
                        message = body,
                        alertLevel = level
                    )
                )
            }
            postCaregiverNotification(title, body)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val prefs = AppPreferences(applicationContext)
        FirebaseRepository.syncTopicsForRole(applicationContext, prefs.appRole == AppRole.CAREGIVER)
    }

    private fun postCaregiverNotification(title: String, body: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "caregiver_siren_channel"
        val channel = NotificationChannel(
            channelId,
            "Alertes Enfants (Urgences Maman)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Sirène d'alerte pour le suivi médical de Maman"
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            9001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify(9001, notification)
    }
}
