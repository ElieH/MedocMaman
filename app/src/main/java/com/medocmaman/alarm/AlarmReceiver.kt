package com.medocmaman.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.medocmaman.R
import com.medocmaman.data.local.AlertLevel
import com.medocmaman.data.local.AppDatabase
import com.medocmaman.data.local.CallLogEntity
import com.medocmaman.data.local.CallStatus
import com.medocmaman.data.remote.FirebaseRepository
import com.medocmaman.ui.IncomingCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MedocMaman::AlarmWakeLock"
        )
        wakeLock.acquire(90_000L)

        val medId = intent.getStringExtra(MedicationAlarmScheduler.EXTRA_MED_ID) ?: "med_morning_statin"
        val timeLabel = intent.getStringExtra(MedicationAlarmScheduler.EXTRA_TIME_LABEL) ?: "08:00"
        val callLogId = intent.getStringExtra(MedicationAlarmScheduler.EXTRA_CALL_LOG_ID)
            ?: "call_${System.currentTimeMillis()}"
        val attemptCount = intent.getIntExtra(MedicationAlarmScheduler.EXTRA_ATTEMPT_COUNT, 1)
        val snoozeCount = intent.getIntExtra(MedicationAlarmScheduler.EXTRA_SNOOZE_COUNT, 0)
        val scheduledTimestamp = intent.getLongExtra(
            MedicationAlarmScheduler.EXTRA_SCHEDULED_TIMESTAMP,
            System.currentTimeMillis()
        )

        // 1. Check if the mother is currently on an active phone call; if so, snooze 5 minutes automatically
        if (isUserOnActivePhoneCall(context)) {
            Log.i(TAG, "User is on an active phone call; snoozing medication reminder by 5 minutes")
            MedicationAlarmScheduler.scheduleRetryOrSnooze(
                context = context,
                medId = medId,
                timeLabel = timeLabel,
                callLogId = callLogId,
                attemptCount = attemptCount,
                snoozeCount = snoozeCount,
                scheduledTimestamp = scheduledTimestamp,
                delayMinutes = 5
            )
            if (wakeLock.isHeld) wakeLock.release()
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                AppDatabase.seedDefaultsIfEmpty(db)
                val med = db.medicationDao().getMedicationById(medId)

                // Reschedule tomorrow's occurrence if this is the initial attempt
                if (med != null && attemptCount == 1 && snoozeCount == 0 && !callLogId.contains("_test_")) {
                    MedicationAlarmScheduler.scheduleNextDailyOccurrence(context, med, timeLabel)
                }

                // 2. Write RINGED heartbeat to Room & Firestore immediately
                val existing = db.callLogDao().getById(callLogId)
                val ringedLog = (existing ?: CallLogEntity(
                    id = callLogId,
                    medId = medId,
                    scheduledTimestamp = scheduledTimestamp
                )).copy(
                    status = CallStatus.RINGED.name,
                    ringedAt = System.currentTimeMillis(),
                    attemptCount = attemptCount,
                    alertLevel = existing?.alertLevel ?: AlertLevel.NONE.name
                )
                FirebaseRepository.pushCallLog(context, ringedLog)

                // 3. Post Full-Screen CallStyle Notification & launch IncomingCallActivity
                val medName = med?.nameFr ?: "Atorvastatine"
                val dosage = med?.dosageFr ?: "1 comprimé"
                launchIncomingCallUi(
                    context = context,
                    medId = medId,
                    medName = medName,
                    dosage = dosage,
                    timeLabel = timeLabel,
                    callLogId = callLogId,
                    attemptCount = attemptCount,
                    snoozeCount = snoozeCount,
                    scheduledTimestamp = scheduledTimestamp
                )
            } finally {
                pendingResult.finish()
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun isUserOnActivePhoneCall(context: Context): Boolean {
        return try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                tm.callState == TelephonyManager.CALL_STATE_OFFHOOK ||
                    tm.callState == TelephonyManager.CALL_STATE_RINGING
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun launchIncomingCallUi(
        context: Context,
        medId: String,
        medName: String,
        dosage: String,
        timeLabel: String,
        callLogId: String,
        attemptCount: Int,
        snoozeCount: Int,
        scheduledTimestamp: Long
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = INCOMING_CALL_CHANNEL_ID

        val channel = NotificationChannel(
            channelId,
            "Appels Médicaments (Plein Écran)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Sonnerie plein écran pour les rappels de médicaments de Maman"
            setBypassDnd(true)
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            setSound(
                ringtoneUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(channel)

        val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MedicationAlarmScheduler.EXTRA_MED_ID, medId)
            putExtra(MedicationAlarmScheduler.EXTRA_TIME_LABEL, timeLabel)
            putExtra(MedicationAlarmScheduler.EXTRA_CALL_LOG_ID, callLogId)
            putExtra(MedicationAlarmScheduler.EXTRA_ATTEMPT_COUNT, attemptCount)
            putExtra(MedicationAlarmScheduler.EXTRA_SNOOZE_COUNT, snoozeCount)
            putExtra(MedicationAlarmScheduler.EXTRA_SCHEDULED_TIMESTAMP, scheduledTimestamp)
        }

        val fullScreenPi = PendingIntent.getActivity(
            context,
            callLogId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(fullScreenIntent).apply {
            putExtra(EXTRA_AUTO_ANSWER, true)
        }
        val answerPi = PendingIntent.getActivity(
            context,
            (callLogId + "_answer").hashCode(),
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(fullScreenIntent).apply {
            putExtra(EXTRA_AUTO_SNOOZE, true)
        }
        val snoozePi = PendingIntent.getActivity(
            context,
            (callLogId + "_snooze").hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caller = Person.Builder()
            .setName("Rappel de Médicament ($timeLabel)")
            .setImportant(true)
            .build()

        val callStyle = NotificationCompat.CallStyle.forIncomingCall(
            caller,
            snoozePi,
            answerPi
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Rappel de Médicament — $timeLabel")
            .setContentText("$medName ($dosage)")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPi, true)
            .setStyle(callStyle)
            .build()

        nm.notify(INCOMING_CALL_NOTIFICATION_ID, notification)

        try {
            context.startActivity(fullScreenIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Direct startActivity relied on FullScreenIntent notification: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        const val INCOMING_CALL_CHANNEL_ID = "medication_incoming_call_channel"
        const val INCOMING_CALL_NOTIFICATION_ID = 1001
        const val EXTRA_AUTO_ANSWER = "extra_auto_answer"
        const val EXTRA_AUTO_SNOOZE = "extra_auto_snooze"
    }
}
