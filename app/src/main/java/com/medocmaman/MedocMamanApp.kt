package com.medocmaman

import android.app.Application
import com.medocmaman.alarm.DailyCheckScheduler
import com.medocmaman.alarm.MedicationAlarmScheduler
import com.medocmaman.data.local.AppDatabase
import com.medocmaman.data.local.AppPreferences
import com.medocmaman.data.local.AppRole
import com.medocmaman.data.remote.FirebaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MedocMamanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = AppPreferences(this)
        FirebaseRepository.syncTopicsForRole(this, prefs.appRole == AppRole.CAREGIVER)

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(this@MedocMamanApp)
            AppDatabase.seedDefaultsIfEmpty(db)
            FirebaseRepository.pullFromFirestoreToRoom(this@MedocMamanApp)
            MedicationAlarmScheduler.scheduleAllActiveMedications(this@MedocMamanApp)
            DailyCheckScheduler.scheduleDailyCheck(this@MedocMamanApp)
        }
    }
}
