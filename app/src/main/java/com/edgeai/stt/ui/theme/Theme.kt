package com.edgeai.stt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkGreen = Color(0xFF00C853)
val BrightCyan = Color(0xFF00E5FF)
val ElectricBlue = Color(0xFF2979FF)
val AlertRed = Color(0xFFFF1744)
val DarkBackground = Color(0xFF0A0E17)
val DarkSurface = Color(0xFF131B2E)
val LightSurface = Color(0xFF1E293B)

private val DarkColorScheme = darkColorScheme(
    primary = DarkGreen,
    secondary = BrightCyan,
    tertiary = ElectricBlue,
    error = AlertRed,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFF8FAFC)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF059669),
    secondary = Color(0xFF0284C7),
    tertiary = Color(0xFF2563EB),
    error = AlertRed,
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF1E293B)
)

@Composable
fun EdgeAISTTTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
