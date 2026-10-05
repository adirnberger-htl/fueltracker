package com.fueltracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val PrimaryColor = Color(0xFF006C4C)
val OnPrimaryColor = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFF89F8C7)
val SecondaryColor = Color(0xFF4C6356)
val BackgroundLight = Color(0xFFFBFDFA)
val SurfaceLight = Color(0xFFFBFDFA)

val PrimaryDark = Color(0xFF6CDBAC)
val OnPrimaryDark = Color(0xFF003825)
val PrimaryContainerDark = Color(0xFF005138)
val BackgroundDark = Color(0xFF191C1B)
val SurfaceDark = Color(0xFF191C1B)

val EcoGreen = Color(0xFF2E7D32)
val ModerateOrange = Color(0xFFF57C00)
val AggressiveRed = Color(0xFFD32F2F)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    secondary = SecondaryColor,
    background = BackgroundDark,
    surface = SurfaceDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryColor,
    onPrimary = OnPrimaryColor,
    primaryContainer = PrimaryContainer,
    secondary = SecondaryColor,
    background = BackgroundLight,
    surface = SurfaceLight
)

@Composable
fun FuelTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
