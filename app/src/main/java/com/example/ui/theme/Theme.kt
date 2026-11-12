package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
  primary = LabTealPrimaryDark,
  onPrimary = LabOnTealPrimaryDark,
  primaryContainer = LabTealContainerDark,
  onPrimaryContainer = LabOnTealContainerDark,
  secondary = LabMintSecondaryDark,
  onSecondary = LabOnTealPrimaryDark,
  secondaryContainer = LabMintContainerDark,
  onSecondaryContainer = LabOnMintContainerDark,
  tertiary = LabAmberTertiaryDark,
  onTertiary = LabAmberContainerDark,
  tertiaryContainer = LabAmberContainerDark,
  onTertiaryContainer = LabOnAmberContainerDark,
  background = LabBackgroundDark,
  onBackground = LabOnBackgroundDark,
  surface = LabSurfaceDark,
  onSurface = LabOnBackgroundDark,
  surfaceVariant = LabSurfaceVariantDark,
  onSurfaceVariant = LabOnSurfaceVariantDark,
  outline = LabOutlineDark,
)

private val LightColorScheme = lightColorScheme(
  primary = LabTealPrimary,
  onPrimary = Color.White,
  primaryContainer = LabTealContainer,
  onPrimaryContainer = LabOnTealContainer,
  secondary = LabMintSecondary,
  onSecondary = Color.White,
  secondaryContainer = LabMintContainer,
  onSecondaryContainer = LabOnMintContainer,
  tertiary = LabAmberTertiary,
  onTertiary = Color.White,
  tertiaryContainer = LabAmberContainer,
  onTertiaryContainer = LabOnAmberContainer,
  background = LabAlabasterBackground,
  onBackground = LabOnBackgroundLight,
  surface = LabSurfaceLight,
  onSurface = LabOnBackgroundLight,
  surfaceVariant = LabSurfaceVariantLight,
  onSurfaceVariant = LabOnSurfaceVariantLight,
  outline = LabOutlineLight,
  error = LabErrorLight,
  errorContainer = LabErrorContainerLight,
  onErrorContainer = LabOnErrorContainerLight,
)

val LabShapes = Shapes(
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(18.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LabAttendanceTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
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
    typography = AppTypography,
    shapes = LabShapes,
    content = content,
  )
}
