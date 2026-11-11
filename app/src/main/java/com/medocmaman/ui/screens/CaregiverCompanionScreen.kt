package com.medocmaman.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medocmaman.alerts.ActiveCaregiverAlert
import com.medocmaman.data.local.CallLogEntity
import com.medocmaman.data.local.MedicalExamEntity
import com.medocmaman.data.local.MedicalExamState
import com.medocmaman.data.local.MedicationEntity
import com.medocmaman.ui.theme.AmberOrange
import com.medocmaman.ui.theme.CrimsonRed
import com.medocmaman.ui.theme.DarkText
import com.medocmaman.ui.theme.EmeraldGreen
import com.medocmaman.ui.theme.GiantAccessibleButton
import com.medocmaman.ui.theme.RoyalBlue
import com.medocmaman.ui.theme.WarmOffWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Caregiver Companion Mode (Son & Daughter's phones).
 * - Continuous Siren Banner with 1-tap "Acknowledge" (synchronized via Firestore `acknowledged_by`).
 * - Live Call Logs & Heartbeats (`RINGED`, `COMPLETED`, `outcome`).
 * - Medical Exams Tracker (`Échographie-Doppler des carotides`) state machine progression.
 */
@Composable
fun CaregiverCompanionScreen(
    motherFirstName: String,
    caregiverName: String,
    activeSirenAlert: ActiveCaregiverAlert?,
    recentLogs: List<CallLogEntity>,
    medications: List<MedicationEntity>,
    exams: List<MedicalExamEntity>,
    onAcknowledgeSiren: (String) -> Unit,
    onAdvanceExamState: (MedicalExamEntity) -> Unit,
    onSyncNow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmOffWhite)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "👨‍👩‍👧 Suivi Aidant — $motherFirstName",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DarkText,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
            ) {
                Text("⚙️ Config")
            }
        }

        // 1. Active Siren Alert Card (Pushover-style Group Acknowledge)
        if (activeSirenAlert != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                border = BorderStroke(3.dp, CrimsonRed)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "🚨 SIRÈNE ACTIVE : ${activeSirenAlert.title}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CrimsonRed
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = activeSirenAlert.message,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    GiantAccessibleButton(
                        text = "🔕 PRENDRE EN CHARGE ($caregiverName)",
                        subtitle = "(Arrête la sirène et informe mon frère / ma sœur)",
                        backgroundColor = CrimsonRed,
                        minHeight = 88.dp,
                        onClick = { onAcknowledgeSiren(activeSirenAlert.callLogId) }
                    )
                }
            }
        }

        // 2. Sync & Status Bar
        Button(
            onClick = onSyncNow,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
        ) {
            Text("🔄 Actualiser depuis Firebase Firestore", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // 3. Medical Exams State Machine (Carotid Duplex & Blood Tests)
        Text(
            text = "🩺 Examens Médicaux & Suivi Carotides",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = DarkText
        )
        exams.forEach { exam ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(exam.nameFr, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    if (exam.nameHe.isNotBlank()) {
                        Text(exam.nameHe, fontSize = 16.sp, color = Color(0xFF475569))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Prochaine échéance : ${exam.nextTargetDate} | Statut : ${exam.state}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RoyalBlue
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onAdvanceExamState(exam) },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                    ) {
                        val nextState = nextExamState(exam.state)
                        Text("Passer à l'étape suivante : $nextState")
                    }
                }
            }
        }

        // 4. Medications & Meuhedet Prescription Status
        Text(
            text = "💊 Médicaments & Ordonnances Meuhedet",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = DarkText
        )
        medications.forEach { med ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${med.nameFr} (${med.dosageFr}) — Heures: ${med.scheduledTimesCsv}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Text(
                        text = "Ordonnance: ${med.prescriptionExpiryDate} | Stock: ${med.stockEmptyDate} | Statut: ${med.prescriptionStatus}",
                        fontSize = 15.sp,
                        color = Color(0xFF334155)
                    )
                }
            }
        }

        // 5. Recent Call Logs & Heartbeats
        Text(
            text = "📋 Historique des Appels & Heartbeats",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = DarkText
        )
        if (recentLogs.isEmpty()) {
            Text("Aucun appel enregistré pour le moment.", fontSize = 16.sp, color = Color(0xFF64748B))
        } else {
            val sdf = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE)
            recentLogs.forEach { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(
                        1.dp,
                        if (log.alertLevel == "CRITICAL" || log.alertLevel == "IMMEDIATE") CrimsonRed else Color(0xFFCBD5E1)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ID: ${log.id}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        val ringedStr = log.ringedAt?.let { sdf.format(Date(it)) } ?: "Non sonné"
                        val outcomeStr = log.outcome ?: "En attente"
                        Text(
                            text = "Statut: ${log.status} | Sonné: $ringedStr | Résultat: $outcomeStr",
                            fontSize = 15.sp,
                            color = Color(0xFF334155)
                        )
                        if (log.userTranscript.isNotBlank()) {
                            Text(
                                text = "Voix reconnue : \"${log.userTranscript}\"",
                                fontSize = 14.sp,
                                color = RoyalBlue
                            )
                        }
                        if (!log.acknowledgedBy.isNullOrBlank()) {
                            Text(
                                text = "✅ Pris en charge par : ${log.acknowledgedBy}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        } else if (log.alertLevel == "IMMEDIATE" || log.alertLevel == "CRITICAL") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { onAcknowledgeSiren(log.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberOrange)
                            ) {
                                Text("Prendre en charge ($caregiverName)")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun nextExamState(current: String): String {
    val states = MedicalExamState.values().map { it.name }
    val idx = states.indexOf(current)
    return if (idx in 0 until states.lastIndex) states[idx + 1] else states.first()
}
