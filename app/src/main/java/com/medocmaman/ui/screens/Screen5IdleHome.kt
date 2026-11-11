package com.medocmaman.ui.screens

import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medocmaman.alarm.DailyCheckScheduler
import com.medocmaman.alarm.MedicationAlarmScheduler
import com.medocmaman.data.local.MedicationEntity
import com.medocmaman.ui.theme.AmberOrange
import com.medocmaman.ui.theme.CrimsonRed
import com.medocmaman.ui.theme.DarkText
import com.medocmaman.ui.theme.EmeraldGreen
import com.medocmaman.ui.theme.GiantAccessibleButton
import com.medocmaman.ui.theme.RoyalBlue
import com.medocmaman.ui.theme.SeniorBodyTextStyle
import com.medocmaman.ui.theme.SeniorSubtitleTextStyle
import com.medocmaman.ui.theme.SeniorTitleTextStyle
import com.medocmaman.ui.theme.WarmOffWhite
import kotlinx.coroutines.delay

/**
 * Screen 5: Idle Home Screen (When Opened Manually)
 * Includes the 3-second hidden long-press on the top-right ⚙️ icon to open Xiaomi Health & Admin Setup.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Screen5IdleHome(
    motherFirstName: String,
    medications: List<MedicationEntity>,
    onCallSon: () -> Unit,
    onCallDaughter: () -> Unit,
    onCall101: () -> Unit,
    onOpenMeuhedetRenewal: (MedicationEntity) -> Unit,
    onOpenHiddenAdmin: () -> Unit
) {
    var isPressingGear by remember { mutableStateOf(false) }

    // 3-second hold timer on ⚙️ to unlock Admin / Xiaomi Checklist Screen
    LaunchedEffect(isPressingGear) {
        if (isPressingGear) {
            delay(3_000L)
            if (isPressingGear) {
                isPressingGear = false
                onOpenHiddenAdmin()
            }
        }
    }

    val nextSlot = MedicationAlarmScheduler.findNextMedicationSlot(medications)
    val renewalMed = medications.firstOrNull { DailyCheckScheduler.isRenewalDueWithin14Days(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmOffWhite)
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top bar with Greeting and Hidden 3-second Long-Press ⚙️
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bonjour $motherFirstName 🌸",
                    style = SeniorTitleTextStyle,
                    color = DarkText,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    color = if (isPressingGear) Color(0xFFCBD5E1) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.pointerInteropFilter { event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                isPressingGear = true
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                isPressingGear = false
                                true
                            }
                            else -> false
                        }
                    }
                ) {
                    Text(
                        text = "⚙️",
                        style = SeniorBodyTextStyle,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (isPressingGear) {
                Text(
                    text = "Maintenez appuyé 3 secondes pour le menu famille...",
                    style = SeniorSubtitleTextStyle,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Next Medication Card
            Text(
                text = "Prochain médicament :",
                style = SeniorBodyTextStyle,
                color = DarkText
            )
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(3.dp, EmeraldGreen)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (nextSlot != null) {
                        val (med, timeStr) = nextSlot
                        val hour = timeStr.split(":").firstOrNull()?.toIntOrNull() ?: 8
                        val labelPrefix = if (hour < 12) "Ce matin à" else if (hour < 18) "Cet après-midi à" else "Ce soir à"
                        Text(
                            text = "🕗 $labelPrefix ${timeStr.replace(":", "h")}",
                            style = SeniorBodyTextStyle,
                            color = EmeraldGreen
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${med.nameFr} (${med.dosageFr})",
                            style = SeniorTitleTextStyle,
                            color = DarkText
                        )
                    } else {
                        Text(
                            text = "🕗 Aucun médicament programmé",
                            style = SeniorBodyTextStyle,
                            color = DarkText
                        )
                    }
                }
            }

            // Friendly Non-Urgent Meuhedet Renewal Prompt if within 14 days
            if (renewalMed != null) {
                Spacer(modifier = Modifier.height(18.dp))
                GiantAccessibleButton(
                    text = "🏥  RENOUVELER L'ORDONNANCE MEUHEDET",
                    subtitle = "(${renewalMed.nameFr})",
                    backgroundColor = AmberOrange,
                    minHeight = 88.dp,
                    onClick = { onOpenMeuhedetRenewal(renewalMed) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Need Help Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Besoin d'aide ?",
                style = SeniorBodyTextStyle,
                color = DarkText,
                textAlign = TextAlign.Start
            )

            GiantAccessibleButton(
                text = "📞  APPELER MON FILS",
                backgroundColor = RoyalBlue,
                minHeight = 88.dp,
                onClick = onCallSon
            )

            GiantAccessibleButton(
                text = "📞  APPELER MA FILLE",
                backgroundColor = RoyalBlue,
                minHeight = 88.dp,
                onClick = onCallDaughter
            )

            GiantAccessibleButton(
                text = "🚨  URGENCE 101",
                backgroundColor = CrimsonRed,
                minHeight = 92.dp,
                onClick = onCall101
            )
        }
    }
}
