package com.medocmaman.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY id ASC")
    fun observeActiveMedications(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY id ASC")
    suspend fun getActiveMedications(): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE id = :id LIMIT 1")
    suspend fun getMedicationById(id: String): MedicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(medications: List<MedicationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(medication: MedicationEntity)
}

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY scheduled_timestamp DESC LIMIT 50")
    fun observeRecentLogs(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CallLogEntity?

    @Query("SELECT COUNT(*) FROM call_logs WHERE med_id = :medId AND outcome = 'FORGOT' AND scheduled_timestamp >= :sinceTimestamp")
    suspend fun countForgotSince(medId: String, sinceTimestamp: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(callLog: CallLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(callLogs: List<CallLogEntity>)
}

@Dao
interface MedicalExamDao {
    @Query("SELECT * FROM medical_exams ORDER BY next_target_date ASC")
    fun observeExams(): Flow<List<MedicalExamEntity>>

    @Query("SELECT * FROM medical_exams ORDER BY next_target_date ASC")
    suspend fun getAllExams(): List<MedicalExamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(exams: List<MedicalExamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exam: MedicalExamEntity)
}

@Database(
    entities = [MedicationEntity::class, CallLogEntity::class, MedicalExamEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun callLogDao(): CallLogDao
    abstract fun medicalExamDao(): MedicalExamDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medoc_maman.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun seedDefaultsIfEmpty(db: AppDatabase) {
            if (db.medicationDao().getActiveMedications().isEmpty()) {
                db.medicationDao().upsert(
                    MedicationEntity(
                        id = "med_morning_statin",
                        nameFr = "Atorvastatine",
                        dosageFr = "Un comprimé",
                        mealTimingFr = "après le petit-déjeuner",
                        visualDescriptionFr = "le petit comprimé rond et blanc",
                        imageUrl = "",
                        localImageRes = "pill_white_round",
                        scheduledTimesCsv = "08:00,20:00",
                        onMissedInstructionFr = "Ne prenez pas de double dose. Attendez la prochaine prise prévue.",
                        prescriptionExpiryDate = "2026-12-15",
                        stockEmptyDate = "2026-12-10",
                        prescriptionStatus = PrescriptionStatus.ACTIVE.name,
                        active = true,
                        updatedAt = "2026-11-11T08:00:00Z"
                    )
                )
            }
            if (db.medicalExamDao().getAllExams().isEmpty()) {
                db.medicalExamDao().upsert(
                    MedicalExamEntity(
                        id = "exam_carotid_duplex",
                        nameFr = "Échographie-Doppler des carotides",
                        nameHe = "אולטרסאונד דופלקס של עורקי הצוואר",
                        state = MedicalExamState.NEEDS_REFERRAL.name,
                        lastExamDate = "2026-01-15",
                        nextTargetDate = "2027-01-15",
                        stateUpdatedAt = "2026-11-01"
                    )
                )
            }
        }
    }
}
