package com.asuka.pocketpdf.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object PocketSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Xxxl = 32.dp
}

object PocketRadii {
    val Compact = 10.dp
    val Control = 14.dp
    val Card = 16.dp
    val Floating = 20.dp
}

object PocketMotion {
    const val Micro = 150
    const val Content = 200
    const val Panel = 220
}

data class PocketColors(
    val workspace: Color,
    val paper: Color,
    val crystal: Color,
    val crystalBorder: Color,
    val ink: Color,
    val mutedInk: Color,
    val success: Color,
    val warning: Color,
    val shadowAmbient: Color,
    val shadowSpot: Color,
)

val LocalPocketColors = staticCompositionLocalOf<PocketColors> {
    error("PocketColors not provided")
}

val LocalPocketSpacing = staticCompositionLocalOf { PocketSpacing }
