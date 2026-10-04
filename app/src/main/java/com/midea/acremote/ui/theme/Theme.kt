package com.midea.acremote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MideaBlue = Color(0xFF0B5CAD)
val MideaLightBlue = Color(0xFF4CC2FF)
val MideaDeep = Color(0xFF0B1220)
val MideaPanel = Color(0xFF131C2E)
val MideaOn = Color(0xFF2ECC71)
val MideaOff = Color(0xFF5A6478)
val MideaHot = Color(0xFFF2994A)
val MideaCold = Color(0xFF56CCF2)

private val DarkColors = darkColorScheme(
    primary = MideaLightBlue,
    onPrimary = Color(0xFF00131F),
    secondary = MideaBlue,
    onSecondary = Color.White,
    background = MideaDeep,
    onBackground = Color(0xFFE8EEF7),
    surface = MideaPanel,
    onSurface = Color(0xFFE8EEF7),
    surfaceVariant = Color(0xFF1D2739),
    onSurfaceVariant = Color(0xFFA9B4C6),
    outline = Color(0xFF33405A),
    error = Color(0xFFFF6B6B)
)

private val LightColors = lightColorScheme(
    primary = MideaBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF0A84FF),
    onSecondary = Color.White,
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF101725),
    surface = Color.White,
    onSurface = Color(0xFF101725),
    surfaceVariant = Color(0xFFE7ECF4),
    onSurfaceVariant = Color(0xFF4A5568),
    outline = Color(0xFFC8D0DC)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp)
)

@Composable
fun MideaAcRemoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
