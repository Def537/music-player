package com.def.musicplayer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Раніше тема перевизначала лише 5 слотів ColorScheme (primary/secondary/background/
 * surface/onSurface), тож усе решта (кнопки, діалоги, чипи, outline-рамки, і т.д.)
 * лишалось на дефолтних фіолетуватих кольорах Material3 — звідси й баг "змінюється
 * лише частина кольорів". Тепер уся палітра узгоджено виводиться з ОДНОГО обраного
 * акценту через [buildDarkScheme]/[buildLightScheme], а не задається частково вручну.
 *
 * Золотий і білий/чорний лишаються "постійними" акцентами (secondary/tertiary/outline
 * прив'язані до AccentSecondary, а не до обраного кольору) — це і є "нейтральні
 * акценти", які не змінюються залежно від теми.
 */
@Composable
fun MusicPlayerTheme(
    isDarkTheme: Boolean,
    accentColor: Color = AccentPrimary,
    content: @Composable () -> Unit
) {
    val colors = if (isDarkTheme) buildDarkScheme(accentColor) else buildLightScheme(accentColor)
    MaterialTheme(colorScheme = colors, content = content)
}

/** Лінійне змішування двох кольорів: 0f = чистий base, 1f = чистий accent. */
private fun blend(base: Color, accent: Color, accentWeight: Float): Color {
    val w = accentWeight.coerceIn(0f, 1f)
    return Color(
        red = base.red * (1 - w) + accent.red * w,
        green = base.green * (1 - w) + accent.green * w,
        blue = base.blue * (1 - w) + accent.blue * w,
        alpha = 1f
    )
}

private fun buildDarkScheme(accent: Color): ColorScheme {
    val nearBlack = Color(0xFF120D0B)
    val background = blend(nearBlack, accent, 0.10f)
    val surface = blend(nearBlack, accent, 0.14f)
    val surfaceVariant = blend(nearBlack, accent, 0.24f)
    val primaryContainer = blend(Color.Black, accent, 0.50f)
    val secondaryContainer = blend(Color.Black, AccentSecondary, 0.35f)
    // Тонкі рамки/outline — нейтрально-сірий із легким золотим відтінком (постійний акцент)
    val outline = blend(Color(0xFF7A6F63), AccentSecondary, 0.30f)

    return darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = primaryContainer,
        onPrimaryContainer = Color.White,
        // Золотий — фіксований, не залежить від обраної теми (постійний акцент)
        secondary = AccentSecondary,
        onSecondary = Color.Black,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = Color.White,
        tertiary = AccentSecondary,
        onTertiary = Color.Black,
        background = background,
        onBackground = Color.White,
        surface = surface,
        onSurface = Color.White,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = Color(0xFFD8CFC5),
        outline = outline,
        outlineVariant = blend(nearBlack, accent, 0.30f),
        inverseSurface = Color(0xFFEDE3D8),
        inverseOnSurface = nearBlack,
        // Помилка лишається впізнаваною незалежно від того, чи сам акцент — червоний
        error = Color(0xFFCF6679),
        onError = Color.Black
    )
}

private fun buildLightScheme(accent: Color): ColorScheme {
    val cream = LightBackground
    val background = blend(cream, accent, 0.06f)
    val surface = blend(Color.White, accent, 0.04f)
    val surfaceVariant = blend(cream, accent, 0.14f)
    val primaryContainer = blend(Color.White, accent, 0.22f)
    val secondaryContainer = blend(Color.White, AccentSecondary, 0.25f)
    val outline = blend(Color(0xFFBFB6AC), AccentSecondary, 0.30f)
    val darkText = Color(0xFF2A1C18)

    return lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = primaryContainer,
        onPrimaryContainer = darkText,
        secondary = AccentSecondary,
        onSecondary = Color.Black,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = darkText,
        tertiary = AccentSecondary,
        onTertiary = Color.Black,
        background = background,
        onBackground = darkText,
        surface = surface,
        onSurface = darkText,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = Color(0xFF5B4F45),
        outline = outline,
        outlineVariant = blend(cream, accent, 0.20f),
        inverseSurface = darkText,
        inverseOnSurface = cream,
        error = Color(0xFFB3261E),
        onError = Color.White
    )
}
