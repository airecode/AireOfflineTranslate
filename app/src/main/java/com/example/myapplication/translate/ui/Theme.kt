package com.example.myapplication.translate.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Aire's brand colour. Deliberately neither the reference design's green nor Google Translate's
 * azure — a same-category translation app that also copies the palette is what draws an
 * impersonation flag at Play review.
 */
val BrandPrimary = Color(0xFF4A2E8C)

/** Lifted for dark mode; the deep violet has too little contrast against a dark surface. */
val BrandPrimaryDark = Color(0xFFB6A0F5)

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF1B1B1B),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFECECEC),
    onSurfaceVariant = Color(0xFF5F5F5F),
    outlineVariant = Color(0xFFDADADA),
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = Color(0xFF1F1040),
    background = Color(0xFF121016),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF121016),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF262232),
    onSurfaceVariant = Color(0xFFB6B6B6),
    outlineVariant = Color(0xFF363144),
)

@Composable
fun TranslateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
