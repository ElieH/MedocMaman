package com.medocmaman.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// WCAG AAA Senior-Accessible Palette from ui_ux_design_specification.md
val WarmOffWhite = Color(0xFFFAF9F6)
val DeepSlate = Color(0xFF121824)
val SoftRedAlertBg = Color(0xFFFEF2F2)

val EmeraldGreen = Color(0xFF15803D)
val RoyalBlue = Color(0xFF1D4ED8)
val AmberOrange = Color(0xFFD97706)
val CrimsonRed = Color(0xFFDC2626)
val SlateGrayButton = Color(0xFF334155)
val DarkText = Color(0xFF0F172A)

val SeniorTitleTextStyle = TextStyle(
    fontSize = 32.sp,
    fontWeight = FontWeight.ExtraBold,
    lineHeight = 40.sp
)

val SeniorBodyTextStyle = TextStyle(
    fontSize = 26.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = 34.sp
)

val SeniorSubtitleTextStyle = TextStyle(
    fontSize = 22.sp,
    fontWeight = FontWeight.SemiBold,
    lineHeight = 28.sp
)

private val MedocColorScheme = lightColorScheme(
    primary = EmeraldGreen,
    onPrimary = Color.White,
    secondary = RoyalBlue,
    onSecondary = Color.White,
    tertiary = AmberOrange,
    onTertiary = Color.White,
    error = CrimsonRed,
    onError = Color.White,
    background = WarmOffWhite,
    onBackground = DarkText,
    surface = Color.White,
    onSurface = DarkText
)

@Composable
fun MedocMamanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MedocColorScheme,
        content = content
    )
}

@Composable
fun GiantAccessibleButton(
    text: String,
    subtitle: String? = null,
    backgroundColor: Color,
    contentColor: Color = Color.White,
    minHeight: Dp = 88.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
    ) {
        Text(
            text = if (subtitle.isNullOrBlank()) text else "$text\n$subtitle",
            style = SeniorBodyTextStyle,
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}
