package com.medocmaman.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("BootCompletedReceiver", "Action=${intent.action}: Rescheduling all medication alarms")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MedicationAlarmScheduler.scheduleAllActiveMedications(context)
                DailyCheckScheduler.scheduleDailyCheck(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
