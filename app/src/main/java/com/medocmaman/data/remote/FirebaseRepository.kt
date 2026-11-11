package com.medocmaman.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.medocmaman.data.local.AppDatabase
import com.medocmaman.data.local.CallLogEntity
import com.medocmaman.data.local.MedicalExamEntity
import com.medocmaman.data.local.MedicationEntity
import kotlinx.coroutines.tasks.await

/**
 * Local-first wrapper around Firebase Firestore & FCM.
 * Gracefully degrades to 100% offline Room storage when using the placeholder google-services.json
 * or when internet is unavailable.
 */
object FirebaseRepository {
    private const val TAG = "FirebaseRepository"
    private const val PLACEHOLDER_PROJECT_ID = "medocmaman-placeholder"

    fun isFirebaseConfigured(context: Context): Boolean {
        return try {
            val apps = FirebaseApp.getApps(context)
            val app = if (apps.isEmpty()) FirebaseApp.initializeApp(context) else FirebaseApp.getInstance()
            val projectId = app?.options?.projectId
            !projectId.isNullOrBlank() && projectId != PLACEHOLDER_PROJECT_ID
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not configured, using offline local Room mode: ${e.message}")
            false
        }
    }

    fun syncTopicsForRole(context: Context, isCaregiver: Boolean) {
        if (!isFirebaseConfigured(context)) return
        try {
            val messaging = FirebaseMessaging.getInstance()
            if (isCaregiver) {
                messaging.subscribeToTopic("caregiver_alerts")
            } else {
                messaging.subscribeToTopic("patient_schedule_sync")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to subscribe to FCM topic: ${e.message}")
        }
    }

    suspend fun pushCallLog(context: Context, callLog: CallLogEntity): Boolean {
        val db = AppDatabase.getInstance(context)
        db.callLogDao().upsert(callLog)
        if (!isFirebaseConfigured(context)) return false

        return try {
            val firestore = FirebaseFirestore.getInstance()
            val data = mapOf(
                "id" to callLog.id,
                "med_id" to callLog.medId,
                "scheduled_timestamp" to callLog.scheduledTimestamp,
                "status" to callLog.status,
                "ringed_at" to callLog.ringedAt,
                "completed_at" to callLog.completedAt,
                "attempt_count" to callLog.attemptCount,
                "outcome" to callLog.outcome,
                "user_transcript" to callLog.userTranscript,
                "alert_level" to callLog.alertLevel,
                "acknowledged_by" to callLog.acknowledgedBy
            )
            firestore.collection("call_logs")
                .document(callLog.id)
                .set(data, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Offline or Firestore write failed for ${callLog.id}: ${e.message}")
            false
        }
    }

    suspend fun acknowledgeAlert(context: Context, callLogId: String, caregiverName: String) {
        val db = AppDatabase.getInstance(context)
        val existing = db.callLogDao().getById(callLogId)
        if (existing != null) {
            db.callLogDao().upsert(existing.copy(acknowledgedBy = caregiverName))
        }
        if (!isFirebaseConfigured(context)) return
        try {
            FirebaseFirestore.getInstance()
                .collection("call_logs")
                .document(callLogId)
                .update("acknowledged_by", caregiverName)
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update acknowledged_by in Firestore: ${e.message}")
        }
    }

    suspend fun pushMedicationStatus(context: Context, med: MedicationEntity) {
        val db = AppDatabase.getInstance(context)
        db.medicationDao().upsert(med)
        if (!isFirebaseConfigured(context)) return
        try {
            FirebaseFirestore.getInstance()
                .collection("medications")
                .document(med.id)
                .update("prescription_status", med.prescriptionStatus)
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to push medication status to Firestore: ${e.message}")
        }
    }

    suspend fun pushExamState(context: Context, exam: MedicalExamEntity) {
        val db = AppDatabase.getInstance(context)
        db.medicalExamDao().upsert(exam)
        if (!isFirebaseConfigured(context)) return
        try {
            FirebaseFirestore.getInstance()
                .collection("medical_exams")
                .document(exam.id)
                .set(
                    mapOf(
                        "id" to exam.id,
                        "name_fr" to exam.nameFr,
                        "name_he" to exam.nameHe,
                        "state" to exam.state,
                        "last_exam_date" to exam.lastExamDate,
                        "next_target_date" to exam.nextTargetDate,
                        "state_updated_at" to exam.stateUpdatedAt
                    ),
                    SetOptions.merge()
                )
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to push exam state to Firestore: ${e.message}")
        }
    }

    suspend fun pullFromFirestoreToRoom(context: Context) {
        if (!isFirebaseConfigured(context)) return
        val db = AppDatabase.getInstance(context)
        try {
            val firestore = FirebaseFirestore.getInstance()
            val medsSnap = firestore.collection("medications").get().await()
            val meds = medsSnap.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val nameFr = doc.getString("name_fr") ?: return@mapNotNull null
                val timesList = (doc.get("scheduled_times") as? List<*>)?.mapNotNull { it?.toString() }
                    ?: listOf("08:00")
                MedicationEntity(
                    id = id,
                    nameFr = nameFr,
                    dosageFr = doc.getString("dosage_fr") ?: "Un comprimé",
                    mealTimingFr = doc.getString("meal_timing_fr") ?: "",
                    visualDescriptionFr = doc.getString("visual_description_fr") ?: "",
                    imageUrl = doc.getString("image_url") ?: "",
                    localImageRes = doc.getString("local_image_res") ?: "pill_white_round",
                    scheduledTimesCsv = timesList.joinToString(","),
                    onMissedInstructionFr = doc.getString("on_missed_instruction_fr")
                        ?: "Ne prenez pas de double dose. Demandez conseil à vos enfants ou au médecin.",
                    prescriptionExpiryDate = doc.getString("prescription_expiry_date") ?: "2026-12-31",
                    stockEmptyDate = doc.getString("stock_empty_date") ?: "2026-12-31",
                    prescriptionStatus = doc.getString("prescription_status") ?: "ACTIVE",
                    active = doc.getBoolean("active") ?: true,
                    updatedAt = doc.getString("updated_at") ?: ""
                )
            }
            if (meds.isNotEmpty()) {
                db.medicationDao().upsertAll(meds)
            }

            val examsSnap = firestore.collection("medical_exams").get().await()
            val exams = examsSnap.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val nameFr = doc.getString("name_fr") ?: return@mapNotNull null
                MedicalExamEntity(
                    id = id,
                    nameFr = nameFr,
                    nameHe = doc.getString("name_he") ?: "",
                    state = doc.getString("state") ?: "NEEDS_REFERRAL",
                    lastExamDate = doc.getString("last_exam_date") ?: "",
                    nextTargetDate = doc.getString("next_target_date") ?: "",
                    stateUpdatedAt = doc.getString("state_updated_at") ?: ""
                )
            }
            if (exams.isNotEmpty()) {
                db.medicalExamDao().upsertAll(exams)
            }

            val logsSnap = firestore.collection("call_logs")
                .limit(50)
                .get()
                .await()
            val logs = logsSnap.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val medId = doc.getString("med_id") ?: return@mapNotNull null
                CallLogEntity(
                    id = id,
                    medId = medId,
                    scheduledTimestamp = doc.getLong("scheduled_timestamp") ?: System.currentTimeMillis(),
                    status = doc.getString("status") ?: "PENDING",
                    ringedAt = doc.getLong("ringed_at"),
                    completedAt = doc.getLong("completed_at"),
                    attemptCount = (doc.getLong("attempt_count") ?: 0L).toInt(),
                    outcome = doc.getString("outcome"),
                    userTranscript = doc.getString("user_transcript") ?: "",
                    alertLevel = doc.getString("alert_level") ?: "NONE",
                    acknowledgedBy = doc.getString("acknowledged_by")
                )
            }
            if (logs.isNotEmpty()) {
                db.callLogDao().upsertAll(logs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "pullFromFirestoreToRoom skipped or offline: ${e.message}")
        }
    }
}
