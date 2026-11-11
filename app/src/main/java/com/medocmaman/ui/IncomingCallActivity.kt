package com.medocmaman.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.medocmaman.alarm.AlarmReceiver
import com.medocmaman.alarm.MedicationAlarmScheduler
import com.medocmaman.ui.screens.Screen1IncomingRing
import com.medocmaman.ui.screens.Screen2ActiveCall
import com.medocmaman.ui.screens.Screen3FollowUp
import com.medocmaman.ui.screens.Screen4ViteSafety
import com.medocmaman.ui.screens.ScreenMissedDoseOrInfo
import com.medocmaman.ui.theme.MedocMamanTheme

/**
 * Full-Screen Lock-Screen Wake-up Activity for Medication Calls.
 * Configured with setShowWhenLocked(true) and setTurnScreenOn(true) so the mother never has to unlock her Redmi phone.
 */
class IncomingCallActivity : ComponentActivity() {

    private val viewModel: CallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableLockScreenWakeUp()
        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)

        setContent {
            MedocMamanTheme {
                val state by viewModel.uiState.collectAsState()

                LaunchedEffect(state.stage) {
                    if (state.stage == CallScreenStage.CLOSING) {
                        finishAndRemoveTask()
                    }
                }

                when (state.stage) {
                    CallScreenStage.SCREEN_1_RINGING -> {
                        Screen1IncomingRing(
                            state = state,
                            onAnswer = { viewModel.onAnswerCallClicked() },
                            onSnooze15Min = { viewModel.onSnooze15MinutesClicked() }
                        )
                    }

                    CallScreenStage.SCREEN_2_ACTIVE_PRIMARY -> {
                        Screen2ActiveCall(
                            state = state,
                            onTakenYes = { viewModel.onTakenConfirmed() },
                            onNotYetNo = { viewModel.onNotYetOrProblemClicked() },
                            onProblem = { viewModel.onNotYetOrProblemClicked() }
                        )
                    }

                    CallScreenStage.SCREEN_3_FOLLOW_UP -> {
                        Screen3FollowUp(
                            state = state,
                            onTakeNowSnooze15m = { viewModel.onSnooze15MinutesClicked() },
                            onForgotPreviousDose = { viewModel.onForgotPreviousDoseClicked() },
                            onOutOfStock = { viewModel.onOutOfStockClicked() },
                            onUnwellViteCheck = { viewModel.onUnwellOrViteTriggered() }
                        )
                    }

                    CallScreenStage.SHOWING_MISSED_DOSE_INSTRUCTION -> {
                        ScreenMissedDoseOrInfo(
                            state = state,
                            onDoneClose = { viewModel.closeScreenNow() }
                        )
                    }

                    CallScreenStage.SCREEN_4_VITE_SAFETY -> {
                        Screen4ViteSafety(
                            state = state,
                            onCall101Emergency = {
                                viewModel.onEmergency101Confirmed {
                                    dialPhoneNumber("101")
                                }
                            },
                            onCallChild = {
                                val target = state.daughterPhone.ifBlank { state.sonPhone }
                                dialPhoneNumber(target)
                            },
                            onNonViteOtherIssue = {
                                viewModel.onNonViteUnwellConfirmed()
                            }
                        )
                    }

                    CallScreenStage.CLOSING -> {
                        // Closing activity
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val medId = intent?.getStringExtra(MedicationAlarmScheduler.EXTRA_MED_ID) ?: "med_morning_statin"
        val timeLabel = intent?.getStringExtra(MedicationAlarmScheduler.EXTRA_TIME_LABEL) ?: "08:00"
        val callLogId = intent?.getStringExtra(MedicationAlarmScheduler.EXTRA_CALL_LOG_ID)
            ?: "call_${System.currentTimeMillis()}"
        val attemptCount = intent?.getIntExtra(MedicationAlarmScheduler.EXTRA_ATTEMPT_COUNT, 1) ?: 1
        val snoozeCount = intent?.getIntExtra(MedicationAlarmScheduler.EXTRA_SNOOZE_COUNT, 0) ?: 0
        val scheduledTimestamp = intent?.getLongExtra(
            MedicationAlarmScheduler.EXTRA_SCHEDULED_TIMESTAMP,
            System.currentTimeMillis()
        ) ?: System.currentTimeMillis()
        val autoAnswer = intent?.getBooleanExtra(AlarmReceiver.EXTRA_AUTO_ANSWER, false) ?: false
        val autoSnooze = intent?.getBooleanExtra(AlarmReceiver.EXTRA_AUTO_SNOOZE, false) ?: false

        viewModel.initializeCall(
            medId = medId,
            timeLabel = timeLabel,
            callLogId = callLogId,
            attemptCount = attemptCount,
            snoozeCount = snoozeCount,
            scheduledTimestamp = scheduledTimestamp,
            autoAnswer = autoAnswer,
            autoSnooze = autoSnooze
        )
    }

    private fun dialPhoneNumber(phone: String) {
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(dialIntent)
    }

    private fun enableLockScreenWakeUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
