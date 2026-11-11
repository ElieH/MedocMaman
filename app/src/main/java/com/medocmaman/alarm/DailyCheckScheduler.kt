package com.medocmaman.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.medocmaman.alerts.EmergencyAlertManager
import com.medocmaman.data.local.AlertLevel
import com.medocmaman.data.local.AppDatabase
import com.medocmaman.data.local.MedicalExamEntity
import com.medocmaman.data.local.MedicalExamState
import com.medocmaman.data.local.MedicationEntity
import com.medocmaman.data.local.PrescriptionStatus
import com.medocmaman.data.remote.FirebaseRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Phase 4: Daily 10:00 AM check for Meuhedet prescription renewals (<= 14 days / <= 7 days escalation)
 * and Medical Exams (Carotid Duplex & blood tests <= 30 days in NEEDS_REFERRAL / HAS_REFERRAL).
 */
class DailyCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        AppDatabase.seedDefaultsIfEmpty(db)
        FirebaseRepository.pullFromFirestoreToRoom(applicationContext)

        val meds = db.medicationDao().getActiveMedications()
        for (med in meds) {
            val daysToExpiry = DailyCheckScheduler.daysUntilDate(med.prescriptionExpiryDate)
            val daysToEmpty = DailyCheckScheduler.daysUntilDate(med.stockEmptyDate)
            val minDays = minOf(daysToExpiry, daysToEmpty)

            if (minDays <= 7 && med.prescriptionStatus != PrescriptionStatus.REQUEST_OPENED_MEUHEDET.name) {
                val updated = med.copy(prescriptionStatus = PrescriptionStatus.ESCALATED_7D.name)
                FirebaseRepository.pushMedicationStatus(applicationContext, updated)
                EmergencyAlertManager.sendSmsToCaregivers(
                    context = applicationContext,
                    level = AlertLevel.TODAY,
                    medNameFr = med.nameFr,
                    reasonSummaryFr = "Ordonnance Meuhedet ou stock expire dans $minDays jours"
                )
            } else if (minDays <= 14 && med.prescriptionStatus == PrescriptionStatus.ACTIVE.name) {
                val updated = med.copy(prescriptionStatus = PrescriptionStatus.REMINDER_SENT_14D.name)
                FirebaseRepository.pushMedicationStatus(applicationContext, updated)
            }
        }

        val exams = db.medicalExamDao().getAllExams()
        for (exam in exams) {
            if (DailyCheckScheduler.isExamActionRequired(exam)) {
                val daysLeft = DailyCheckScheduler.daysUntilDate(exam.nextTargetDate)
                EmergencyAlertManager.sendSmsToCaregivers(
                    context = applicationContext,
                    level = AlertLevel.TODAY,
                    medNameFr = exam.nameFr,
                    reasonSummaryFr = "Examen prévu dans $daysLeft jours (statut: ${exam.state})"
                )
            }
        }

        return Result.success()
    }
}

object DailyCheckScheduler {
    private const val WORK_NAME = "medoc_maman_daily_10am_check"

    fun scheduleDailyCheck(context: Context) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val initialDelayMs = (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)

        val workRequest = PeriodicWorkRequestBuilder<DailyCheckWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun daysUntilDate(dateYyyyMmDd: String, referenceTimeMillis: Long = System.currentTimeMillis()): Int {
        if (dateYyyyMmDd.isBlank()) return 999
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsed: Date = sdf.parse(dateYyyyMmDd) ?: return 999
            val diffMillis = parsed.time - referenceTimeMillis
            TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
        } catch (_: Exception) {
            999
        }
    }

    fun isRenewalDueWithin14Days(med: MedicationEntity, referenceTimeMillis: Long = System.currentTimeMillis()): Boolean {
        if (med.prescriptionStatus == PrescriptionStatus.REQUEST_OPENED_MEUHEDET.name) return false
        val daysToExpiry = daysUntilDate(med.prescriptionExpiryDate, referenceTimeMillis)
        val daysToEmpty = daysUntilDate(med.stockEmptyDate, referenceTimeMillis)
        return minOf(daysToExpiry, daysToEmpty) <= 14
    }

    fun isExamActionRequired(exam: MedicalExamEntity, referenceTimeMillis: Long = System.currentTimeMillis()): Boolean {
        val daysToTarget = daysUntilDate(exam.nextTargetDate, referenceTimeMillis)
        val needsActionState = exam.state == MedicalExamState.NEEDS_REFERRAL.name ||
            exam.state == MedicalExamState.HAS_REFERRAL.name
        return daysToTarget <= 30 && needsActionState
    }
}
