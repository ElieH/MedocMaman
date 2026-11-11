package com.medocmaman.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medocmaman.data.local.MedicationEntity
import com.medocmaman.ui.theme.AmberOrange
import com.medocmaman.ui.theme.DarkText
import com.medocmaman.ui.theme.EmeraldGreen
import com.medocmaman.ui.theme.GiantAccessibleButton
import com.medocmaman.ui.theme.RoyalBlue
import com.medocmaman.ui.theme.SeniorBodyTextStyle
import com.medocmaman.ui.theme.SeniorTitleTextStyle
import com.medocmaman.ui.theme.SlateGrayButton
import com.medocmaman.ui.theme.WarmOffWhite

/**
 * Phase 4: Friendly French Meuhedet Prescription Renewal Screen.
 * Offers 1-tap "Demander à mes enfants de renouveler", a button to launch Meuhedet ("org.meuhedet.android"),
 * and a button "C'est fait" (REQUEST_OPENED_MEUHEDET).
 */
@Composable
fun MeuhedetRenewalScreen(
    medication: MedicationEntity,
    onAskChildrenToRenew: () -> Unit,
    onOpenMeuhedetApp: () -> Unit,
    onMarkDone: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmOffWhite)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Renouvellement d'Ordonnance (Meuhedet)",
                style = SeniorTitleTextStyle,
                color = DarkText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(2.dp, AmberOrange)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "💊 ${medication.nameFr}",
                        style = SeniorTitleTextStyle,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Fin de l'ordonnance : ${medication.prescriptionExpiryDate}\n" +
                            "• Fin des boîtes : ${medication.stockEmptyDate}",
                        style = SeniorBodyTextStyle,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Comme l'application Meuhedet est en hébreu, vous pouvez demander à vos enfants de faire le renouvellement en un clic :",
                        style = SeniorBodyTextStyle,
                        color = DarkText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GiantAccessibleButton(
                text = "👨‍👩‍👧  DEMANDER À MES ENFANTS DE RENOUVELER",
                backgroundColor = RoyalBlue,
                minHeight = 96.dp,
                onClick = onAskChildrenToRenew
            )

            GiantAccessibleButton(
                text = "📱  OUVRIR L'APPLICATION MEUHEDET",
                backgroundColor = AmberOrange,
                minHeight = 88.dp,
                onClick = onOpenMeuhedetApp
            )

            GiantAccessibleButton(
                text = "✅  C'EST FAIT (DEMANDE ENVOYÉE)",
                backgroundColor = EmeraldGreen,
                minHeight = 88.dp,
                onClick = onMarkDone
            )

            GiantAccessibleButton(
                text = "⬅️  RETOUR",
                backgroundColor = SlateGrayButton,
                minHeight = 80.dp,
                onClick = onBack
            )
        }
    }
}
