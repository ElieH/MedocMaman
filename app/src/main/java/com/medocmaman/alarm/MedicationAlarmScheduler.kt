package com.medocmaman.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.medocmaman.MainActivity
import com.medocmaman.data.local.AppDatabase
import com.medocmaman.data.local.MedicationEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MedicationAlarmScheduler {
    private const val TAG = "MedicationAlarmScheduler"

    const val EXTRA_MED_ID = "extra_med_id"
    const val EXTRA_TIME_LABEL = "extra_time_label"
    const val EXTRA_CALL_LOG_ID = "extra_call_log_id"
    const val EXTRA_ATTEMPT_COUNT = "extra_attempt_count"
    const val EXTRA_SNOOZE_COUNT = "extra_snooze_count"
    const val EXTRA_SCHEDULED_TIMESTAMP = "extra_scheduled_timestamp"

    suspend fun scheduleAllActiveMedications(context: Context) {
        val db = AppDatabase.getInstance(context)
        AppDatabase.seedDefaultsIfEmpty(db)
        val meds = db.medicationDao().getActiveMedications()
        for (med in meds) {
            for (timeStr in med.scheduledTimesList()) {
                scheduleNextDailyOccurrence(context, med, timeStr)
            }
        }
    }

    fun scheduleNextDailyOccurrence(
        context: Context,
        med: MedicationEntity,
        timeHHmm: String
    ) {
        val parts = timeHHmm.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateStr = dateFormat.format(Date(target.timeInMillis))
        val safeTime = timeHHmm.replace(":", "-")
        val callLogId = "${dateStr}_${safeTime}_${med.id}"

        scheduleExactAlarmClock(
            context = context,
            triggerAtMillis = target.timeInMillis,
            medId = med.id,
            timeLabel = timeHHmm,
            callLogId = callLogId,
            attemptCount = 1,
            snoozeCount = 0,
            scheduledTimestamp = target.timeInMillis,
            requestCode = (med.id + "_" + timeHHmm).hashCode()
        )
    }

    fun scheduleRetryOrSnooze(
        context: Context,
        medId: String,
        timeLabel: String,
        callLogId: String,
        attemptCount: Int,
        snoozeCount: Int,
        scheduledTimestamp: Long,
        delayMinutes: Int
    ) {
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        scheduleExactAlarmClock(
            context = context,
            triggerAtMillis = triggerAt,
            medId = medId,
            timeLabel = timeLabel,
            callLogId = callLogId,
            attemptCount = attemptCount,
            snoozeCount = snoozeCount,
            scheduledTimestamp = scheduledTimestamp,
            requestCode = (callLogId + "_retry_${attemptCount}_$snoozeCount").hashCode()
        )
    }

    fun scheduleTestCall(context: Context, delaySeconds: Int, medId: String = "med_morning_statin") {
        val triggerAt = System.currentTimeMillis() + delaySeconds * 1000L
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(triggerAt))
        val callLogId = "${dateStr}_test_$medId"
        scheduleExactAlarmClock(
            context = context,
            triggerAtMillis = triggerAt,
            medId = medId,
            timeLabel = "08:00",
            callLogId = callLogId,
            attemptCount = 1,
            snoozeCount = 0,
            scheduledTimestamp = triggerAt,
            requestCode = callLogId.hashCode()
        )
    }

    private fun scheduleExactAlarmClock(
        context: Context,
        triggerAtMillis: Long,
        medId: String,
        timeLabel: String,
        callLogId: String,
        attemptCount: Int,
        snoozeCount: Int,
        scheduledTimestamp: Long,
        requestCode: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val receiverIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_MED_ID, medId)
            putExtra(EXTRA_TIME_LABEL, timeLabel)
            putExtra(EXTRA_CALL_LOG_ID, callLogId)
            putExtra(EXTRA_ATTEMPT_COUNT, attemptCount)
            putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
            putExtra(EXTRA_SCHEDULED_TIMESTAMP, scheduledTimestamp)
        }

        val operationPi = PendingIntent.getBroadcast(
            context,
            requestCode,
            receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPi = PendingIntent.getActivity(
            context,
            0,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Exact alarm permission not granted; falling back to setAndAllowWhileIdle")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operationPi)
            } else {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPi)
                alarmManager.setAlarmClock(alarmClockInfo, operationPi)
            }
            Log.i(TAG, "Scheduled setAlarmClock for $callLogId at $triggerAtMillis (attempt=$attemptCount)")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling exact alarm: ${e.message}")
        }
    }

    /**
     * Computes the next upcoming medication and time string for Screen 5 (Idle Home Screen).
     */
    fun findNextMedicationSlot(medications: List<MedicationEntity>): Pair<MedicationEntity, String>? {
        if (medications.isEmpty()) return null
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        var bestCandidate: Pair<MedicationEntity, String>? = null
        var bestDeltaMinutes = Int.MAX_VALUE

        for (med in medications) {
            for (timeStr in med.scheduledTimesList()) {
                val parts = timeStr.split(":")
                val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
                val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val slotMinutes = h * 60 + m
                val delta = if (slotMinutes >= nowMinutes) {
                    slotMinutes - nowMinutes
                } else {
                    (24 * 60 - nowMinutes) + slotMinutes
                }
                if (delta < bestDeltaMinutes) {
                    bestDeltaMinutes = delta
                    bestCandidate = med to timeStr
                }
            }
        }
        return bestCandidate
    }
}
