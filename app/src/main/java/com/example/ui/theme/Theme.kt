package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = MoonGold,
    secondary = MoonGold,
    tertiary = MoonGold,
    background = MoonBlack,
    surface = MoonDarkGray,
    onPrimary = MoonBlack,
    onSecondary = MoonBlack,
    onTertiary = MoonBlack,
    onBackground = MoonWhite,
    onSurface = MoonWhite,
    surfaceVariant = MoonLightGray,
    onSurfaceVariant = MoonTextDim,
    error = MoonRedError
  )

private val LightColorScheme = DarkColorScheme // Force dark theme for the movie theater ambient requested

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
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
