package com.medocmaman.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveCaregiverAlert(
    val callLogId: String,
    val title: String,
    val message: String,
    val alertLevel: String
)

/**
 * Manages the continuous siren on the Son's and Daughter's phones in Caregiver Mode
 * until one child taps "Acknowledge" (replicating Pushover's group-acknowledge feature for free).
 */
object CaregiverSirenController {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private val _activeAlert = MutableStateFlow<ActiveCaregiverAlert?>(null)
    val activeAlert: StateFlow<ActiveCaregiverAlert?> = _activeAlert.asStateFlow()

    fun startSiren(context: Context, alert: ActiveCaregiverAlert) {
        stopSiren()
        _activeAlert.value = alert

        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context.applicationContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 800, 400, 800, 400)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (_: Exception) {
        }
    }

    fun stopSiren() {
        _activeAlert.value = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
        }

        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        } finally {
            vibrator = null
        }
    }
}
