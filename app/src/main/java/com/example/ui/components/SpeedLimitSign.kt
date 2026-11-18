package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SignStyle
import com.example.model.SpeedUnit
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.InstrumentMuted
import com.example.ui.theme.InstrumentWhite
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OswaldFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SignInkBlack
import com.example.ui.theme.SignInternationalRed
import com.example.ui.theme.SignPlateWhite

@Composable
fun SpeedLimitSign(
    speedLimit: Int?,
    unit: SpeedUnit,
    signStyle: SignStyle,
    isSpeeding: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    currentSpeed: Int? = null,
    showCurrentSpeedPill: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "speeding_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val alertGlowColor by animateColorAsState(
        targetValue = if (isSpeeding) AlertCrimson.copy(alpha = pulseAlpha) else Color.Transparent,
        animationSpec = tween(220),
        label = "alert_glow"
    )

    val limitText = speedLimit?.toString() ?: "--"
    val accessibilityLabel = if (speedLimit != null) {
        "Speed limit $speedLimit ${unit.label}"
    } else {
        "Speed limit unavailable"
    }

    Column(
        modifier = modifier.semantics { contentDescription = accessibilityLabel },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (signStyle) {
            SignStyle.US_MUTCD -> {
                UsMutcdSign(
                    limitText = limitText,
                    unitLabel = unit.label,
                    isSpeeding = isSpeeding,
                    alertGlowColor = alertGlowColor,
                    scale = scale
                )
            }
            SignStyle.INTERNATIONAL -> {
                InternationalSign(
                    limitText = limitText,
                    unitLabel = unit.label,
                    isSpeeding = isSpeeding,
                    alertGlowColor = alertGlowColor,
                    scale = scale
                )
            }
        }

        if (showCurrentSpeedPill && currentSpeed != null) {
            Spacer(modifier = Modifier.height((6 * scale).dp))
            CurrentSpeedPill(
                currentSpeed = currentSpeed,
                unit = unit,
                isSpeeding = isSpeeding,
                scale = scale
            )
        }
    }
}

@Composable
private fun UsMutcdSign(
    limitText: String,
    unitLabel: String,
    isSpeeding: Boolean,
    alertGlowColor: Color,
    scale: Float
) {
    val width: Dp = (78 * scale).dp
    val height: Dp = (98 * scale).dp
    val corner: Dp = (10 * scale).dp
    val numberFontSize = if (limitText.length >= 3) (31 * scale).sp else (38 * scale).sp

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .shadow(elevation = (8 * scale).dp, shape = RoundedCornerShape(corner))
            .clip(RoundedCornerShape(corner))
            .background(SignPlateWhite)
            .border(
                width = if (isSpeeding) (3.5f * scale).dp else (1.5f * scale).dp,
                color = if (isSpeeding) alertGlowColor else Color(0xFFCBD5E1),
                shape = RoundedCornerShape(corner)
            )
            .padding((4 * scale).dp)
            .border(
                width = (2 * scale).dp,
                color = if (isSpeeding) AlertCrimson else SignInkBlack,
                shape = RoundedCornerShape((7 * scale).dp)
            )
            .padding(horizontal = (4 * scale).dp, vertical = (4 * scale).dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SPEED\nLIMIT",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (9.5f * scale).sp,
                lineHeight = (10.5f * scale).sp,
                color = SignInkBlack,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height((1 * scale).dp))
            Text(
                text = limitText,
                fontFamily = OswaldFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = numberFontSize,
                lineHeight = numberFontSize,
                color = if (isSpeeding) AlertCrimson else SignInkBlack,
                textAlign = TextAlign.Center
            )
            Text(
                text = unitLabel,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (7.5f * scale).sp,
                lineHeight = (9 * scale).sp,
                color = Color(0xFF475569)
            )
        }
    }
}

@Composable
private fun InternationalSign(
    limitText: String,
    unitLabel: String,
    isSpeeding: Boolean,
    alertGlowColor: Color,
    scale: Float
) {
    val diameter: Dp = (84 * scale).dp
    val ringThickness: Dp = (8.5f * scale).dp
    val numberFontSize = if (limitText.length >= 3) (28 * scale).sp else (35 * scale).sp

    Box(
        modifier = Modifier
            .size(diameter)
            .shadow(elevation = (8 * scale).dp, shape = CircleShape)
            .clip(CircleShape)
            .background(SignPlateWhite)
            .border(
                width = if (isSpeeding) (4 * scale).dp else (1.5f * scale).dp,
                color = if (isSpeeding) alertGlowColor else Color(0xFFE2E8F0),
                shape = CircleShape
            )
            .padding((2 * scale).dp)
            .border(
                width = ringThickness,
                color = SignInternationalRed,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = limitText,
                fontFamily = OswaldFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = numberFontSize,
                lineHeight = numberFontSize,
                color = if (isSpeeding) AlertCrimson else SignInkBlack,
                textAlign = TextAlign.Center
            )
            Text(
                text = unitLabel,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (7 * scale).sp,
                lineHeight = (8 * scale).sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun CurrentSpeedPill(
    currentSpeed: Int,
    unit: SpeedUnit,
    isSpeeding: Boolean,
    scale: Float
) {
    val pillBg = if (isSpeeding) Color(0xEB991B1B) else Color(0xE60B0E14)
    val pillBorder = if (isSpeeding) AlertCrimson else Color(0x4D94A3B8)

    Row(
        modifier = Modifier
            .shadow((4 * scale).dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(pillBg)
            .border((1.2f * scale).dp, pillBorder, RoundedCornerShape(50))
            .padding(horizontal = (10 * scale).dp, vertical = (4 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = currentSpeed.toString(),
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (14 * scale).sp,
            color = InstrumentWhite
        )
        Spacer(modifier = Modifier.width((4 * scale).dp))
        Text(
            text = unit.label.lowercase(),
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (10 * scale).sp,
            color = if (isSpeeding) Color(0xFFFECACA) else InstrumentMuted
        )
    }
}
