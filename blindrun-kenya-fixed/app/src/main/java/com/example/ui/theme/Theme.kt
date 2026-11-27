package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val BlindRunDarkScheme =
  darkColorScheme(
    primary = VoltLime,
    onPrimary = ObsidianNight,
    primaryContainer = SurfaceElevatedDark,
    onPrimaryContainer = VoltLime,
    secondary = SignalCyan,
    onSecondary = ObsidianNight,
    secondaryContainer = DeepSlate,
    onSecondaryContainer = SignalCyan,
    tertiary = SkyCobalt,
    onTertiary = ObsidianNight,
    background = ObsidianNight,
    onBackground = CrispWhite,
    surface = DeepSlate,
    onSurface = CrispWhite,
    surfaceVariant = SurfaceCardDark,
    onSurfaceVariant = MutedSlateText,
    outline = BorderSubtleDark,
    error = CoralAlert,
    onError = CrispWhite,
  )

private val BlindRunLightScheme =
  lightColorScheme(
    primary = DeepCobaltPrimary,
    onPrimary = DaylightSurface,
    primaryContainer = DaylightCard,
    onPrimaryContainer = DeepCobaltPrimary,
    secondary = VoltLimeDark,
    onSecondary = InkDark,
    secondaryContainer = DaylightCard,
    onSecondaryContainer = InkDark,
    tertiary = SignalCyan,
    onTertiary = InkDark,
    background = DaylightCanvas,
    onBackground = InkDark,
    surface = DaylightSurface,
    onSurface = InkDark,
    surfaceVariant = DaylightCard,
    onSurfaceVariant = InkMuted,
    outline = DaylightBorder,
    error = CoralAlert,
    onError = DaylightSurface,
  )

val BlindRunShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
  )

@Composable
fun BlindRunTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) BlindRunDarkScheme else BlindRunLightScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = BlindRunShapes,
    content = content,
  )
}
