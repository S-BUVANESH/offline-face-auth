package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = PremiumPrimary,
    onPrimary = PremiumOnPrimary,
    primaryContainer = PremiumSurfaceVariant,
    onPrimaryContainer = PremiumOnPrimary,
    secondary = PremiumPrimary,
    secondaryContainer = PremiumSurface,
    onSecondaryContainer = PremiumOnSurface,
    background = PremiumBackground,
    surface = PremiumSurface,
    onBackground = PremiumOnBackground,
    onSurface = PremiumOnSurface,
    surfaceVariant = PremiumSurfaceVariant,
    onSurfaceVariant = PremiumOnSurface,
    error = Color(0xFFFF1744),
    onError = Color.White,
    outline = PremiumOutline
  )

private val LightColorScheme = DarkColorScheme


@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic colors to enforce the Professional Polish theme consistently
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
