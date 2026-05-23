package com.asuka.pocketpdf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = PurpleOnPrimary,
    primaryContainer = PurplePrimaryContainer,
    onPrimaryContainer = PurpleOnPrimaryContainer,
    secondary = PurpleSecondary,
    onSecondary = PurpleOnSecondary,
    secondaryContainer = PurpleSecondaryContainer,
    onSecondaryContainer = PurpleOnSecondaryContainer,
    tertiary = PurpleTertiary,
    onTertiary = PurpleOnTertiary,
    tertiaryContainer = PurpleTertiaryContainer,
    onTertiaryContainer = PurpleOnTertiaryContainer,
    error = PurpleError,
    onError = PurpleOnError,
    errorContainer = PurpleErrorContainer,
    onErrorContainer = PurpleOnErrorContainer,
    surface = PurpleSurface,
    onSurface = PurpleOnSurface,
    surfaceVariant = PurpleSurfaceVariant,
    onSurfaceVariant = PurpleOnSurfaceVariant,
    outline = PurpleOutline,
    outlineVariant = PurpleOutlineVariant,
    background = PurpleBackground,
    onBackground = PurpleOnBackground,
    inverseSurface = PurpleInverseSurface,
    inverseOnSurface = PurpleInverseOnSurface,
    inversePrimary = PurpleInversePrimary,
)

private val DarkColorScheme = darkColorScheme(
    primary = PurpleInversePrimary,
    onPrimary = PurpleOnPrimaryContainer,
    primaryContainer = PurplePrimary,
    onPrimaryContainer = PurpleOnPrimary,
    secondary = PurpleSecondaryContainer,
    onSecondary = PurpleOnSecondaryContainer,
    tertiary = PurpleTertiaryContainer,
    onTertiary = PurpleOnTertiary,
    error = PurpleError,
    onError = PurpleOnError,
    errorContainer = PurpleErrorContainer,
    onErrorContainer = PurpleOnErrorContainer,
    surface = Color(0xFF1C1B1F),
    onSurface = PurpleInverseOnSurface,
    surfaceVariant = PurpleSurfaceVariant,
    onSurfaceVariant = PurpleOnSurfaceVariant,
    outline = PurpleOutlineVariant,
    background = Color(0xFF141218),
    onBackground = PurpleInverseOnSurface,
    inverseSurface = PurpleSurface,
    inverseOnSurface = PurpleOnSurface,
    inversePrimary = PurplePrimary,
)

@Composable
fun PocketPDFTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PocketPDFTypography,
        content = content,
    )
}
