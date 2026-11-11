package com.medocmaman.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PrescriptionStatus {
    ACTIVE,
    REMINDER_SENT_14D,
    REQUEST_OPENED_MEUHEDET,
    ESCALATED_7D
}

enum class CallStatus {
    PENDING,
    RINGED,
    COMPLETED
}

enum class CallOutcome {
    TAKEN,
    SNOOZED_15M,
    FORGOT,
    OUT_OF_STOCK,
    REFUSED_OR_CONCERNED,
    SIDE_EFFECTS_UNWELL,
    EMERGENCY_VITE,
    CONFUSED_OR_UNCLEAR,
    NO_ANSWER_3_TRIES,
    WATCHDOG_SILENT_FAILURE
}

enum class AlertLevel {
    NONE,
    WEEKLY,
    TODAY,
    IMMEDIATE,
    CRITICAL
}

enum class MedicalExamState {
    NEEDS_REFERRAL,
    HAS_REFERRAL,
    APPOINTMENT_BOOKED,
    EXAM_COMPLETED,
    RESULTS_AT_DOCTOR,
    NEXT_DATE_SET
}

enum class AppRole {
    PATIENT,
    CAREGIVER
}

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name_fr") val nameFr: String,
    @ColumnInfo(name = "dosage_fr") val dosageFr: String,
    @ColumnInfo(name = "meal_timing_fr") val mealTimingFr: String,
    @ColumnInfo(name = "visual_description_fr") val visualDescriptionFr: String,
    @ColumnInfo(name = "image_url") val imageUrl: String = "",
    @ColumnInfo(name = "local_image_res") val localImageRes: String = "pill_white_round",
    @ColumnInfo(name = "scheduled_times_csv") val scheduledTimesCsv: String, // e.g., "08:00,20:00"
    @ColumnInfo(name = "on_missed_instruction_fr") val onMissedInstructionFr: String,
    @ColumnInfo(name = "prescription_expiry_date") val prescriptionExpiryDate: String, // YYYY-MM-DD
    @ColumnInfo(name = "stock_empty_date") val stockEmptyDate: String, // YYYY-MM-DD
    @ColumnInfo(name = "prescription_status") val prescriptionStatus: String = PrescriptionStatus.ACTIVE.name,
    @ColumnInfo(name = "active") val active: Boolean = true,
    @ColumnInfo(name = "updated_at") val updatedAt: String = ""
) {
    fun scheduledTimesList(): List<String> =
        scheduledTimesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey val id: String, // Format: YYYY-MM-DD_HH-mm_medId
    @ColumnInfo(name = "med_id") val medId: String,
    @ColumnInfo(name = "scheduled_timestamp") val scheduledTimestamp: Long,
    @ColumnInfo(name = "status") val status: String = CallStatus.PENDING.name,
    @ColumnInfo(name = "ringed_at") val ringedAt: Long? = null,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "outcome") val outcome: String? = null,
    @ColumnInfo(name = "user_transcript") val userTranscript: String = "",
    @ColumnInfo(name = "alert_level") val alertLevel: String = AlertLevel.NONE.name,
    @ColumnInfo(name = "acknowledged_by") val acknowledgedBy: String? = null
)

@Entity(tableName = "medical_exams")
data class MedicalExamEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name_fr") val nameFr: String,
    @ColumnInfo(name = "name_he") val nameHe: String,
    @ColumnInfo(name = "state") val state: String = MedicalExamState.NEEDS_REFERRAL.name,
    @ColumnInfo(name = "last_exam_date") val lastExamDate: String, // YYYY-MM-DD
    @ColumnInfo(name = "next_target_date") val nextTargetDate: String, // YYYY-MM-DD
    @ColumnInfo(name = "state_updated_at") val stateUpdatedAt: String
)
