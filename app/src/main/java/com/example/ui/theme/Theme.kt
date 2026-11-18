package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CockpitDarkColorScheme = darkColorScheme(
    primary = SignalEmerald,
    onPrimary = Color(0xFF022C22),
    primaryContainer = SignalEmeraldDark,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = SignalAmber,
    onSecondary = Color(0xFF271700),
    secondaryContainer = SignalAmberDark,
    onSecondaryContainer = Color(0xFFFDE68A),
    error = AlertCrimson,
    onError = Color.White,
    errorContainer = AlertCrimsonDark,
    onErrorContainer = Color(0xFFFECACA),
    background = AsphaltBlack,
    onBackground = InstrumentWhite,
    surface = CockpitSurface,
    onSurface = InstrumentWhite,
    surfaceVariant = CockpitSurfaceElevated,
    onSurfaceVariant = InstrumentMuted,
    outline = CockpitBorder
)

private val DaylightLightColorScheme = lightColorScheme(
    primary = HighwayBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF0A2E6E),
    secondary = SignalEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    error = AlertCrimson,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = DaylightCanvas,
    onBackground = DaylightInk,
    surface = DaylightSurface,
    onSurface = DaylightInk,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = DaylightMuted,
    outline = Color(0xFFCBD5E1)
)

@Composable
fun SpeedSignTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CockpitDarkColorScheme else DaylightLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SpeedSignTypography,
        content = content
    )
}
