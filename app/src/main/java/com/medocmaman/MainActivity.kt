package com.medocmaman

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.medocmaman.alerts.CaregiverSirenController
import com.medocmaman.alerts.EmergencyAlertManager
import com.medocmaman.data.local.AlertLevel
import com.medocmaman.data.local.AppDatabase
import com.medocmaman.data.local.AppPreferences
import com.medocmaman.data.local.AppRole
import com.medocmaman.data.local.MedicationEntity
import com.medocmaman.data.local.PrescriptionStatus
import com.medocmaman.data.remote.FirebaseRepository
import com.medocmaman.ui.screens.AdminSetupScreen
import com.medocmaman.ui.screens.CaregiverCompanionScreen
import com.medocmaman.ui.screens.MeuhedetRenewalScreen
import com.medocmaman.ui.screens.Screen5IdleHome
import com.medocmaman.ui.screens.nextExamState
import com.medocmaman.ui.theme.MedocMamanTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        Toast.makeText(this, "Permissions mises à jour", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getInstance(this)
        val prefs = AppPreferences(this)

        setContent {
            MedocMamanTheme {
                val scope = rememberCoroutineScope()
                val medications by db.medicationDao().observeActiveMedications().collectAsState(initial = emptyList())
                val recentLogs by db.callLogDao().observeRecentLogs().collectAsState(initial = emptyList())
                val exams by db.medicalExamDao().observeExams().collectAsState(initial = emptyList())
                val activeSiren by CaregiverSirenController.activeAlert.collectAsState()

                var showAdminScreen by remember { mutableStateOf(false) }
                var selectedRenewalMed by remember { mutableStateOf<MedicationEntity?>(null) }
                var currentRole by remember { mutableStateOf(prefs.appRole) }
                var motherName by remember { mutableStateOf(prefs.motherFirstName) }

                when {
                    showAdminScreen -> {
                        AdminSetupScreen(
                            prefs = prefs,
                            onRequestRuntimePermissions = { requestNecessaryPermissions() },
                            onOpenMeuhedetTest = {
                                showAdminScreen = false
                                selectedRenewalMed = medications.firstOrNull()
                            },
                            onCloseAdmin = {
                                currentRole = prefs.appRole
                                motherName = prefs.motherFirstName
                                showAdminScreen = false
                            }
                        )
                    }

                    selectedRenewalMed != null -> {
                        val med = selectedRenewalMed!!
                        MeuhedetRenewalScreen(
                            medication = med,
                            onAskChildrenToRenew = {
                                scope.launch {
                                    EmergencyAlertManager.sendSmsToCaregivers(
                                        context = this@MainActivity,
                                        level = AlertLevel.TODAY,
                                        medNameFr = med.nameFr,
                                        reasonSummaryFr = "Maman demande de renouveler l'ordonnance sur Meuhedet"
                                    )
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Vos enfants ont été prévenus !",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    selectedRenewalMed = null
                                }
                            },
                            onOpenMeuhedetApp = {
                                openMeuhedetOrPlayStore()
                            },
                            onMarkDone = {
                                scope.launch {
                                    val updated = med.copy(
                                        prescriptionStatus = PrescriptionStatus.REQUEST_OPENED_MEUHEDET.name
                                    )
                                    FirebaseRepository.pushMedicationStatus(this@MainActivity, updated)
                                    selectedRenewalMed = null
                                }
                            },
                            onBack = { selectedRenewalMed = null }
                        )
                    }

                    currentRole == AppRole.CAREGIVER -> {
                        CaregiverCompanionScreen(
                            motherFirstName = motherName,
                            caregiverName = prefs.caregiverName,
                            activeSirenAlert = activeSiren,
                            recentLogs = recentLogs,
                            medications = medications,
                            exams = exams,
                            onAcknowledgeSiren = { callLogId ->
                                CaregiverSirenController.stopSiren()
                                scope.launch {
                                    FirebaseRepository.acknowledgeAlert(
                                        context = this@MainActivity,
                                        callLogId = callLogId,
                                        caregiverName = prefs.caregiverName
                                    )
                                }
                            },
                            onAdvanceExamState = { exam ->
                                scope.launch {
                                    val next = nextExamState(exam.state)
                                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                    val updated = exam.copy(state = next, stateUpdatedAt = today)
                                    FirebaseRepository.pushExamState(this@MainActivity, updated)
                                }
                            },
                            onSyncNow = {
                                scope.launch {
                                    FirebaseRepository.pullFromFirestoreToRoom(this@MainActivity)
                                    Toast.makeText(this@MainActivity, "Synchronisé", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onOpenSettings = { showAdminScreen = true }
                        )
                    }

                    else -> {
                        Screen5IdleHome(
                            motherFirstName = motherName,
                            medications = medications,
                            onCallSon = { dialPhoneNumber(prefs.sonPhoneNumber) },
                            onCallDaughter = { dialPhoneNumber(prefs.daughterPhoneNumber) },
                            onCall101 = { dialPhoneNumber("101") },
                            onOpenMeuhedetRenewal = { med -> selectedRenewalMed = med },
                            onOpenHiddenAdmin = { showAdminScreen = true }
                        )
                    }
                }
            }
        }
    }

    private fun requestNecessaryPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun openMeuhedetOrPlayStore() {
        val launchIntent = packageManager.getLaunchIntentForPackage("org.meuhedet.android")
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=org.meuhedet.android")
            )
            startActivity(marketIntent)
        }
    }

    private fun dialPhoneNumber(phone: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        startActivity(intent)
    }
}
