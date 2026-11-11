package com.medocmaman.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medocmaman.R
import com.medocmaman.ui.CallUiState
import com.medocmaman.ui.theme.AmberOrange
import com.medocmaman.ui.theme.CrimsonRed
import com.medocmaman.ui.theme.DarkText
import com.medocmaman.ui.theme.DeepSlate
import com.medocmaman.ui.theme.EmeraldGreen
import com.medocmaman.ui.theme.GiantAccessibleButton
import com.medocmaman.ui.theme.RoyalBlue
import com.medocmaman.ui.theme.SeniorBodyTextStyle
import com.medocmaman.ui.theme.SeniorSubtitleTextStyle
import com.medocmaman.ui.theme.SeniorTitleTextStyle
import com.medocmaman.ui.theme.SlateGrayButton
import com.medocmaman.ui.theme.SoftRedAlertBg
import com.medocmaman.ui.theme.WarmOffWhite

/**
 * Screen 1: Incoming Medication Call (Lock Screen Wake-up)
 * Dark Slate Background (#121824), 240dp pulsing pill photo, Giant 96dp Green Answer button & 80dp Snooze button.
 */
@Composable
fun Screen1IncomingRing(
    state: CallUiState,
    onAnswer: () -> Unit,
    onSnooze15Min: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val medName = state.medication?.nameFr ?: "Atorvastatine"
    val dosage = state.medication?.dosageFr ?: "1 comprimé"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSlate)
            .padding(horizontal = 24.dp, vertical = 28.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Animated Pulse Ring + 240dp x 240dp High-Res Pill Visual
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(240.dp)
                .scale(pulseScale)
                .border(BorderStroke(5.dp, EmeraldGreen), RoundedCornerShape(28.dp))
                .background(Color(0xFF1E293B), RoundedCornerShape(28.dp))
                .padding(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.pill_white_round),
                contentDescription = medName,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Rappel de Médicament",
                style = SeniorBodyTextStyle,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${state.timeLabel} — ${state.periodLabelFr}",
                style = SeniorBodyTextStyle,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "\"$medName ($dosage)\"",
                style = SeniorTitleTextStyle,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GiantAccessibleButton(
                text = "📞  RÉPONDRE (OUVRIR)",
                backgroundColor = EmeraldGreen,
                minHeight = 96.dp,
                onClick = onAnswer
            )
            GiantAccessibleButton(
                text = "🕒  RAPPELER DANS 15 MINUTES",
                backgroundColor = SlateGrayButton,
                minHeight = 80.dp,
                onClick = onSnooze15Min
            )
        }
    }
}

/**
 * Screen 2: Active Call — Pill Identification & Primary Question
 * Warm White Background (#FAF9F6), Speakerphone badge, Pill card, Voice waveform, and Giant Yes/No/Problem buttons.
 */
@Composable
fun Screen2ActiveCall(
    state: CallUiState,
    onTakenYes: () -> Unit,
    onNotYetNo: () -> Unit,
    onProblem: () -> Unit
) {
    val med = state.medication
    val visualDesc = med?.visualDescriptionFr ?: "Petit comprimé rond et blanc"
    val dosage = med?.dosageFr ?: "1 comprimé"
    val timing = med?.mealTimingFr ?: "Après le petit-déjeuner"
    val questionText = if (state.useVouvoiement) {
        "🎤 \"L'avez-vous déjà pris ?\""
    } else {
        "🎤 \"L'as-tu déjà pris ?\""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmOffWhite)
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Speakerphone Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = Color(0xFFE2E8F0),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "🔊 Haut-parleur actif",
                    style = SeniorSubtitleTextStyle,
                    color = DarkText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pill Identification Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(2.dp, Color(0xFFCBD5E1))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.pill_white_round),
                    contentDescription = med?.nameFr ?: "Médicament",
                    modifier = Modifier.size(150.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = med?.nameFr ?: "Atorvastatine",
                    style = SeniorTitleTextStyle,
                    color = DarkText,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"$visualDesc\"",
                    style = SeniorBodyTextStyle,
                    color = Color(0xFF334155),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$dosage — $timing",
                    style = SeniorSubtitleTextStyle,
                    color = EmeraldGreen,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Primary Question & Voice Indicator
        Text(
            text = questionText,
            style = SeniorTitleTextStyle,
            color = DarkText,
            textAlign = TextAlign.Center
        )

        if (state.isListeningVoice) {
            Text(
                text = "((( Écoute en cours : dites \"Oui\" ou \"Non\" )))",
                style = SeniorSubtitleTextStyle,
                color = RoyalBlue,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (state.statusBannerFr.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = state.statusBannerFr,
                    style = SeniorBodyTextStyle,
                    color = EmeraldGreen,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Giant Accessible Action Buttons (Vertical spacing 16.dp)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GiantAccessibleButton(
                text = "✅  OUI, JE L'AI PRIS",
                backgroundColor = EmeraldGreen,
                minHeight = 92.dp,
                onClick = onTakenYes
            )
            GiantAccessibleButton(
                text = "⏳  NON, PAS ENCORE",
                backgroundColor = RoyalBlue,
                minHeight = 88.dp,
                onClick = onNotYetNo
            )
            GiantAccessibleButton(
                text = "⚠️  J'AI UN PROBLÈME / UNE QUESTION",
                backgroundColor = AmberOrange,
                minHeight = 88.dp,
                onClick = onProblem
            )
        }
    }
}

/**
 * Screen 3: Follow-up / Reason Selection ("Pas encore" or "Problème")
 */
@Composable
fun Screen3FollowUp(
    state: CallUiState,
    onTakeNowSnooze15m: () -> Unit,
    onForgotPreviousDose: () -> Unit,
    onOutOfStock: () -> Unit,
    onUnwellViteCheck: () -> Unit
) {
    val question = if (state.useVouvoiement) {
        "\"Pouvez-vous le prendre maintenant,\nou y a-t-il un problème ?\""
    } else {
        "\"Peux-tu le prendre maintenant,\nou y a-t-il un problème ?\""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmOffWhite)
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = question,
            style = SeniorTitleTextStyle,
            color = DarkText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GiantAccessibleButton(
                text = "💊  JE LE PRENDS MAINTENANT",
                subtitle = "(Rappel de vérification dans 15 min)",
                backgroundColor = EmeraldGreen,
                minHeight = 92.dp,
                onClick = onTakeNowSnooze15m
            )
            GiantAccessibleButton(
                text = "❓  J'AI OUBLIÉ LA PRISE PRÉCÉDENTE",
                subtitle = "(Lire la consigne du médecin)",
                backgroundColor = RoyalBlue,
                minHeight = 92.dp,
                onClick = onForgotPreviousDose
            )
            GiantAccessibleButton(
                text = "📦  JE N'AI PLUS DE MÉDICAMENT",
                subtitle = "(Prévenir mes enfants)",
                backgroundColor = AmberOrange,
                minHeight = 92.dp,
                onClick = onOutOfStock
            )
            GiantAccessibleButton(
                text = "🩺  JE NE ME SENS PAS BIEN",
                subtitle = "(Ouvre la vérification de sécurité)",
                backgroundColor = CrimsonRed,
                minHeight = 92.dp,
                onClick = onUnwellViteCheck
            )
        }
    }
}

/**
 * Screen 3B: Doctor's Missed-Dose Instruction / Out-of-Stock Confirmation Card
 */
@Composable
fun ScreenMissedDoseOrInfo(
    state: CallUiState,
    onDoneClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmOffWhite)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Consigne Importante",
            style = SeniorTitleTextStyle,
            color = DarkText,
            textAlign = TextAlign.Center
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(3.dp, RoyalBlue)
        ) {
            Text(
                text = state.statusBannerFr,
                style = SeniorTitleTextStyle,
                color = DarkText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }

        GiantAccessibleButton(
            text = "✅  J'AI COMPRIS (FERMER)",
            backgroundColor = EmeraldGreen,
            minHeight = 96.dp,
            onClick = onDoneClose
        )
    }
}

/**
 * Screen 4: VITE Safety Check & Emergency 101 Screen
 * Soft Red/Cream Alert Background (#FEF2F2), pulsing 110dp Crimson Red 101 button, Call Son/Daughter, Non-VITE button.
 */
@Composable
fun Screen4ViteSafety(
    state: CallUiState,
    onCall101Emergency: () -> Unit,
    onCallChild: () -> Unit,
    onNonViteOtherIssue: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "emergencyPulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderAlpha"
    )

    val viteQuestion = if (state.useVouvoiement) {
        "\"Avez-vous soudain une faiblesse d'un côté,\ndu mal à parler, ou un problème de vue ?\""
    } else {
        "\"As-tu soudain une faiblesse d'un côté,\ndu mal à parler, ou un problème de vue ?\""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftRedAlertBg)
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
            border = BorderStroke(2.dp, CrimsonRed)
        ) {
            Text(
                text = "ℹ️ Vos enfants viennent d'être prévenus.",
                style = SeniorBodyTextStyle,
                color = CrimsonRed,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = viteQuestion,
            style = SeniorTitleTextStyle,
            color = DarkText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        BorderStroke(5.dp, CrimsonRed.copy(alpha = borderAlpha)),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(3.dp)
            ) {
                GiantAccessibleButton(
                    text = "🚨  OUI — APPELER LE 101 (URGENCE)",
                    backgroundColor = CrimsonRed,
                    minHeight = 110.dp,
                    onClick = onCall101Emergency
                )
            }

            GiantAccessibleButton(
                text = "📞  APPELER MA FILLE / MON FILS",
                backgroundColor = RoyalBlue,
                minHeight = 90.dp,
                onClick = onCallChild
            )

            GiantAccessibleButton(
                text = "💬  NON, C'EST AUTRE CHOSE",
                subtitle = "(Mes enfants vont me rappeler)",
                backgroundColor = AmberOrange,
                minHeight = 90.dp,
                onClick = onNonViteOtherIssue
            )
        }
    }
}
