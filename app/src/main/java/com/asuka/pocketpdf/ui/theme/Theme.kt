package com.asuka.pocketpdf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    surface = Color(0xFF1B1720),
    onSurface = PurpleInverseOnSurface,
    surfaceVariant = Color(0xFF302739),
    onSurfaceVariant = Color(0xFFD0C6D5),
    outline = PurpleOutlineVariant,
    background = Color(0xFF100D14),
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
    val pocketColors = if (darkTheme) {
        PocketColors(
            workspace = Color(0xFF100D14),
            paper = Color(0xFF1B1720),
            crystal = Color(0xCC302739),
            crystalBorder = Color(0x33FFFFFF),
            ink = Color(0xFFF8F5F9),
            mutedInk = Color(0xFFD0C6D5),
            success = Color(0xFF78B895),
            warning = Color(0xFFD5A85F),
        )
    } else {
        PocketColors(
            workspace = PurpleBackground,
            paper = PurpleSurface,
            crystal = Color(0xD9302739),
            crystalBorder = Color(0x2EFFFFFF),
            ink = PurpleOnSurface,
            mutedInk = PurpleOnSurfaceVariant,
            success = Color(0xFF4F8A69),
            warning = Color(0xFFA8732D),
        )
    }
    CompositionLocalProvider(
        LocalPocketColors provides pocketColors,
        LocalPocketSpacing provides PocketSpacing,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PocketPDFTypography,
            content = content,
        )
    }
}
