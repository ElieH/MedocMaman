package com.medocmaman.ui

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medocmaman.alarm.AlarmReceiver
import com.medocmaman.alarm.MedicationAlarmScheduler
import com.medocmaman.alerts.EmergencyAlertManager
import com.medocmaman.data.local.AlertLevel
import com.medocmaman.data.local.AppDatabase
import com.medocmaman.data.local.AppPreferences
import com.medocmaman.data.local.CallLogEntity
import com.medocmaman.data.local.CallOutcome
import com.medocmaman.data.local.CallStatus
import com.medocmaman.data.local.MedicationEntity
import com.medocmaman.voice.FrenchKeywordMatcher
import com.medocmaman.voice.FrenchVoiceHelper
import com.medocmaman.voice.VoiceIntent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CallScreenStage {
    SCREEN_1_RINGING,
    SCREEN_2_ACTIVE_PRIMARY,
    SCREEN_3_FOLLOW_UP,
    SCREEN_4_VITE_SAFETY,
    SHOWING_MISSED_DOSE_INSTRUCTION,
    CLOSING
}

data class CallUiState(
    val stage: CallScreenStage = CallScreenStage.SCREEN_1_RINGING,
    val medication: MedicationEntity? = null,
    val timeLabel: String = "08:00",
    val periodLabelFr: String = "Matin",
    val callLogId: String = "",
    val attemptCount: Int = 1,
    val snoozeCount: Int = 0,
    val scheduledTimestamp: Long = System.currentTimeMillis(),
    val isListeningVoice: Boolean = false,
    val lastTranscript: String = "",
    val statusBannerFr: String = "",
    val motherFirstName: String = "Maman",
    val useVouvoiement: Boolean = true,
    val sonPhone: String = "",
    val daughterPhone: String = ""
)

class CallViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val db = AppDatabase.getInstance(context)
    private val prefs = AppPreferences(context)

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private var voiceHelper: FrenchVoiceHelper? = null
    private var keywordMatcher: FrenchKeywordMatcher? = null
    private var ringPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var ringTimeoutJob: Job? = null
    private var voiceTimeoutJob: Job? = null

    fun initializeCall(
        medId: String,
        timeLabel: String,
        callLogId: String,
        attemptCount: Int,
        snoozeCount: Int,
        scheduledTimestamp: Long,
        autoAnswer: Boolean = false,
        autoSnooze: Boolean = false
    ) {
        viewModelScope.launch {
            AppDatabase.seedDefaultsIfEmpty(db)
            val med = db.medicationDao().getMedicationById(medId)
                ?: db.medicationDao().getActiveMedications().firstOrNull()

            val hour = timeLabel.split(":").firstOrNull()?.toIntOrNull() ?: 8
            val periodFr = when {
                hour < 12 -> "Matin"
                hour < 18 -> "Midi / Après-midi"
                else -> "Soir"
            }

            _uiState.value = CallUiState(
                stage = CallScreenStage.SCREEN_1_RINGING,
                medication = med,
                timeLabel = timeLabel,
                periodLabelFr = periodFr,
                callLogId = callLogId,
                attemptCount = attemptCount,
                snoozeCount = snoozeCount,
                scheduledTimestamp = scheduledTimestamp,
                motherFirstName = prefs.motherFirstName,
                useVouvoiement = prefs.useVouvoiement,
                sonPhone = prefs.sonPhoneNumber,
                daughterPhone = prefs.daughterPhoneNumber
            )

            voiceHelper = FrenchVoiceHelper(context)

            if (autoSnooze) {
                onSnooze15MinutesClicked()
            } else if (autoAnswer) {
                onAnswerCallClicked()
            } else {
                startRinging60Seconds()
            }
        }
    }

    private fun startRinging60Seconds() {
        stopRinger()
        try {
            val ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringPlayer = MediaPlayer().apply {
                setDataSource(context, ringUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
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
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0))
        } catch (_: Exception) {
        }

        // Ring for 60 seconds. If unanswered, retry #2 (+10m), retry #3 (+20m), then NO_ANSWER_3_TRIES
        ringTimeoutJob?.cancel()
        ringTimeoutJob = viewModelScope.launch {
            delay(60_000L)
            handleUnansweredTimeout()
        }
    }

    private suspend fun handleUnansweredTimeout() {
        stopRinger()
        cancelCallNotification()
        val state = _uiState.value
        val med = state.medication

        if (state.attemptCount < 3) {
            val nextAttempt = state.attemptCount + 1
            MedicationAlarmScheduler.scheduleRetryOrSnooze(
                context = context,
                medId = med?.id ?: "med_morning_statin",
                timeLabel = state.timeLabel,
                callLogId = state.callLogId,
                attemptCount = nextAttempt,
                snoozeCount = state.snoozeCount,
                scheduledTimestamp = state.scheduledTimestamp,
                delayMinutes = 10
            )
            _uiState.value = state.copy(stage = CallScreenStage.CLOSING)
        } else {
            // 3 tries missed -> Log NO_ANSWER_3_TRIES and send IMMEDIATE alert
            recordFinalOutcome(
                outcome = CallOutcome.NO_ANSWER_3_TRIES,
                alertLevel = AlertLevel.IMMEDIATE,
                reasonSummaryFr = "Aucune réponse après 3 appels (${state.timeLabel})"
            )
            _uiState.value = state.copy(stage = CallScreenStage.CLOSING)
        }
    }

    /**
     * Screen 1 -> Screen 2: Stops ringer, activates speakerphone, speaks French greeting & starts listening.
     */
    fun onAnswerCallClicked() {
        ringTimeoutJob?.cancel()
        stopRinger()
        cancelCallNotification()

        val state = _uiState.value
        val med = state.medication
        _uiState.value = state.copy(stage = CallScreenStage.SCREEN_2_ACTIVE_PRIMARY)

        voiceHelper?.activateSpeakerphone()
        val greeting = FrenchVoiceHelper.buildGreetingScript(
            motherName = state.motherFirstName,
            medNameFr = med?.nameFr ?: "Atorvastatine",
            visualDescriptionFr = med?.visualDescriptionFr ?: "le petit comprimé rond et blanc",
            dosageFr = med?.dosageFr ?: "Un comprimé",
            mealTimingFr = med?.mealTimingFr ?: "après le repas",
            vouvoiement = state.useVouvoiement
        )

        voiceHelper?.speak(greeting) {
            viewModelScope.launch {
                startVoiceRecognitionWith8sFallback()
            }
        }
    }

    private fun startVoiceRecognitionWith8sFallback() {
        if (_uiState.value.stage != CallScreenStage.SCREEN_2_ACTIVE_PRIMARY) return

        _uiState.value = _uiState.value.copy(isListeningVoice = true)
        voiceTimeoutJob?.cancel()
        voiceTimeoutJob = viewModelScope.launch {
            delay(8_000L)
            if (_uiState.value.isListeningVoice && _uiState.value.stage == CallScreenStage.SCREEN_2_ACTIVE_PRIMARY) {
                keywordMatcher?.stopListening()
                _uiState.value = _uiState.value.copy(isListeningVoice = false)
                voiceHelper?.speak(FrenchVoiceHelper.buildTimeoutPrompt(_uiState.value.useVouvoiement))
            }
        }

        keywordMatcher = FrenchKeywordMatcher(
            context = context,
            onResult = { transcript, intent ->
                voiceTimeoutJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    isListeningVoice = false,
                    lastTranscript = transcript
                )
                when (intent) {
                    VoiceIntent.EMERGENCY_VITE -> onUnwellOrViteTriggered(transcript)
                    VoiceIntent.AFFIRMATIVE_TAKEN -> onTakenConfirmed()
                    VoiceIntent.NEGATIVE_OR_DELAY -> onNotYetOrProblemClicked()
                    VoiceIntent.UNCLEAR -> {
                        voiceHelper?.speak(FrenchVoiceHelper.buildTimeoutPrompt(_uiState.value.useVouvoiement))
                    }
                }
            },
            onErrorOrTimeout = {
                // Handled gently by the 8-second timer or user button tap
            }
        )
        keywordMatcher?.startListening()
    }

    /**
     * Screen 2 -> OUI, JE L'AI PRIS
     */
    fun onTakenConfirmed() {
        stopVoiceAndTimeout()
        viewModelScope.launch {
            recordFinalOutcome(
                outcome = CallOutcome.TAKEN,
                alertLevel = AlertLevel.WEEKLY,
                reasonSummaryFr = "Médicament pris (${_uiState.value.timeLabel})"
            )
            _uiState.value = _uiState.value.copy(
                statusBannerFr = "Merci ! Prise enregistrée. Bonne journée 🌸"
            )
            voiceHelper?.speak("Merci beaucoup ! C'est bien noté. Passez une très bonne journée !") {
                viewModelScope.launch {
                    delay(1200L)
                    _uiState.value = _uiState.value.copy(stage = CallScreenStage.CLOSING)
                }
            }
        }
    }

    /**
     * Screen 1 or Screen 2 or Screen 3 -> Snooze 15 minutes ("Rappeler dans 15 minutes" / "Je le prends maintenant")
     */
    fun onSnooze15MinutesClicked() {
        ringTimeoutJob?.cancel()
        stopRinger()
        stopVoiceAndTimeout()
        cancelCallNotification()

        viewModelScope.launch {
            val state = _uiState.value
            val nextSnooze = state.snoozeCount + 1
            if (nextSnooze >= 3) {
                // Unconfirmed after 3 snoozes -> TODAY alert per Section 3.2
                recordFinalOutcome(
                    outcome = CallOutcome.SNOOZED_15M,
                    alertLevel = AlertLevel.TODAY,
                    reasonSummaryFr = "Reporté 3 fois de 15 min sans confirmation finale"
                )
            } else {
                MedicationAlarmScheduler.scheduleRetryOrSnooze(
                    context = context,
                    medId = state.medication?.id ?: "med_morning_statin",
                    timeLabel = state.timeLabel,
                    callLogId = state.callLogId,
                    attemptCount = state.attemptCount,
                    snoozeCount = nextSnooze,
                    scheduledTimestamp = state.scheduledTimestamp,
                    delayMinutes = 15
                )
                recordFinalOutcome(
                    outcome = CallOutcome.SNOOZED_15M,
                    alertLevel = AlertLevel.NONE,
                    reasonSummaryFr = "Rappel programmé dans 15 minutes",
                    markCompleted = false
                )
            }
            voiceHelper?.speak("D'accord, je vous rappellerai dans quinze minutes.") {
                viewModelScope.launch {
                    delay(800L)
                    _uiState.value = _uiState.value.copy(stage = CallScreenStage.CLOSING)
                }
            }
        }
    }

    /**
     * Screen 2 -> Screen 3 ("Non, pas encore" or "J'ai un problème")
     */
    fun onNotYetOrProblemClicked() {
        stopVoiceAndTimeout()
        _uiState.value = _uiState.value.copy(stage = CallScreenStage.SCREEN_3_FOLLOW_UP)
        voiceHelper?.speak(FrenchVoiceHelper.buildFollowUpPrompt(_uiState.value.useVouvoiement))
    }

    /**
     * Screen 3 -> "J'ai oublié la prise précédente"
     * Strictly reads the doctor's instruction (`on_missed_instruction_fr`) and never modifies treatment.
     */
    fun onForgotPreviousDoseClicked() {
        stopVoiceAndTimeout()
        viewModelScope.launch {
            val state = _uiState.value
            val med = state.medication
            val doctorInstruction = med?.onMissedInstructionFr?.takeIf { it.isNotBlank() }
                ?: "Ne prenez pas de double dose. Vos enfants ont été prévenus."

            // Check if forgot >= 3 times in the past 7 days -> TODAY alert, else WEEKLY
            val sevenDaysAgo = System.currentTimeMillis() - 7L * 24L * 3600_000L
            val forgotCount = db.callLogDao().countForgotSince(med?.id ?: "", sevenDaysAgo) + 1
            val level = if (forgotCount >= 3) AlertLevel.TODAY else AlertLevel.WEEKLY

            recordFinalOutcome(
                outcome = CallOutcome.FORGOT,
                alertLevel = level,
                reasonSummaryFr = "Prise oubliée signalée ($forgotCount fois cette semaine)"
            )

            _uiState.value = state.copy(
                stage = CallScreenStage.SHOWING_MISSED_DOSE_INSTRUCTION,
                statusBannerFr = doctorInstruction
            )
            voiceHelper?.speak("Voici la consigne de votre médecin : $doctorInstruction")
        }
    }

    /**
     * Screen 3 -> "Je n'ai plus de médicament"
     */
    fun onOutOfStockClicked() {
        stopVoiceAndTimeout()
        viewModelScope.launch {
            recordFinalOutcome(
                outcome = CallOutcome.OUT_OF_STOCK,
                alertLevel = AlertLevel.TODAY,
                reasonSummaryFr = "Plus de boîtes en stock signalé par Maman"
            )
            _uiState.value = _uiState.value.copy(
                stage = CallScreenStage.SHOWING_MISSED_DOSE_INSTRUCTION,
                statusBannerFr = "Vos enfants viennent d'être prévenus pour renouveler vos boîtes."
            )
            voiceHelper?.speak("D'accord, je préviens immédiatement vos enfants pour qu'ils vous apportent une nouvelle boîte.")
        }
    }

    /**
     * Screen 3 ("Je ne me sens pas bien") or Voice VITE keyword -> Screen 4 (VITE Safety Check)
     * Immediately notifies the children (`IMMEDIATE` alert) and asks the VITE stroke check question.
     */
    fun onUnwellOrViteTriggered(transcript: String = "") {
        stopVoiceAndTimeout()
        val updatedState = _uiState.value.copy(
            stage = CallScreenStage.SCREEN_4_VITE_SAFETY,
            lastTranscript = transcript.ifBlank { _uiState.value.lastTranscript }
        )
        _uiState.value = updatedState

        viewModelScope.launch {
            recordFinalOutcome(
                outcome = CallOutcome.SIDE_EFFECTS_UNWELL,
                alertLevel = AlertLevel.IMMEDIATE,
                reasonSummaryFr = "Maman a signalé ne pas se sentir bien. Vérification VITE en cours."
            )
        }

        voiceHelper?.speak(FrenchVoiceHelper.buildVitePrompt(updatedState.useVouvoiement))
    }

    /**
     * Screen 4 -> "OUI — APPELER LE 101 (URGENCE)"
     */
    fun onEmergency101Confirmed(onDial101: () -> Unit) {
        stopVoiceAndTimeout()
        viewModelScope.launch {
            recordFinalOutcome(
                outcome = CallOutcome.EMERGENCY_VITE,
                alertLevel = AlertLevel.CRITICAL,
                reasonSummaryFr = "URGENCE VITE 101 déclenchée par Maman"
            )
            voiceHelper?.speak(FrenchVoiceHelper.buildDial101Instruction(_uiState.value.useVouvoiement))
            onDial101()
        }
    }

    /**
     * Screen 4 -> "NON, C'EST AUTRE CHOSE (Mes enfants vont me rappeler)"
     */
    fun onNonViteUnwellConfirmed() {
        stopVoiceAndTimeout()
        viewModelScope.launch {
            recordFinalOutcome(
                outcome = CallOutcome.SIDE_EFFECTS_UNWELL,
                alertLevel = AlertLevel.IMMEDIATE,
                reasonSummaryFr = "Ne se sent pas bien (hors signes VITE) — demande d'appel des enfants"
            )
            voiceHelper?.speak("Vos enfants ont été prévenus et vont vous appeler tout de suite. Reposez-vous.") {
                viewModelScope.launch {
                    delay(1200L)
                    _uiState.value = _uiState.value.copy(stage = CallScreenStage.CLOSING)
                }
            }
        }
    }

    fun closeScreenNow() {
        stopRinger()
        stopVoiceAndTimeout()
        cancelCallNotification()
        _uiState.value = _uiState.value.copy(stage = CallScreenStage.CLOSING)
    }

    private suspend fun recordFinalOutcome(
        outcome: CallOutcome,
        alertLevel: AlertLevel,
        reasonSummaryFr: String,
        markCompleted: Boolean = true
    ) {
        val state = _uiState.value
        val existing = db.callLogDao().getById(state.callLogId)
        val now = System.currentTimeMillis()
        val updatedLog = CallLogEntity(
            id = state.callLogId,
            medId = state.medication?.id ?: "med_morning_statin",
            scheduledTimestamp = state.scheduledTimestamp,
            status = if (markCompleted) CallStatus.COMPLETED.name else CallStatus.RINGED.name,
            ringedAt = existing?.ringedAt ?: now,
            completedAt = if (markCompleted) now else null,
            attemptCount = state.attemptCount,
            outcome = outcome.name,
            userTranscript = state.lastTranscript,
            alertLevel = alertLevel.name,
            acknowledgedBy = existing?.acknowledgedBy
        )
        EmergencyAlertManager.dispatchOutcomeAndAlert(
            context = context,
            callLog = updatedLog,
            medNameFr = state.medication?.nameFr ?: "Médicament",
            reasonSummaryFr = reasonSummaryFr
        )
    }

    private fun stopVoiceAndTimeout() {
        voiceTimeoutJob?.cancel()
        keywordMatcher?.stopListening()
        _uiState.value = _uiState.value.copy(isListeningVoice = false)
    }

    private fun stopRinger() {
        try {
            ringPlayer?.stop()
            ringPlayer?.release()
        } catch (_: Exception) {
        } finally {
            ringPlayer = null
        }

        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        } finally {
            vibrator = null
        }
    }

    private fun cancelCallNotification() {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(AlarmReceiver.INCOMING_CALL_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        ringTimeoutJob?.cancel()
        voiceTimeoutJob?.cancel()
        stopRinger()
        keywordMatcher?.stopListening()
        voiceHelper?.shutdown()
    }
}
