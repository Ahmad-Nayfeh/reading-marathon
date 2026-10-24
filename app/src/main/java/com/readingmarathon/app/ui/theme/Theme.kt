package com.readingmarathon.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.readingmarathon.app.R
import com.readingmarathon.app.data.ThemeMode

val Plex = FontFamily(
    Font(R.font.plex_regular, FontWeight.Normal),
    Font(R.font.plex_medium, FontWeight.Medium),
    Font(R.font.plex_semibold, FontWeight.SemiBold),
    Font(R.font.plex_bold, FontWeight.Bold),
)

/** Amiri: screen titles, group names and book titles, like a printed page. */
val Amiri = FontFamily(Font(R.font.amiri_regular, FontWeight.Normal))

/**
 * Calm and literary, after Shadhra: paper and ink, one ink primary button, gold only for hairlines,
 * dots and small marks. The navy ink and the gold are the marathon's own identity.
 */
@Immutable
data class MarathonColors(
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val paper: Color,
    val card: Color,
    val deep: Color,
    val line: Color,
    val gold: Color,
    val goldSoft: Color,
    val success: Color,
    val heat: List<Color>,
)

private val LightBrand = MarathonColors(
    ink = Color(0xFF14213D),
    inkSoft = Color(0xFF3B4459),
    muted = Color(0xFF6B6F7B),
    paper = Color(0xFFF7F1E6),
    card = Color(0xFFFFFCF6),
    deep = Color(0xFFEFE6D6),
    line = Color(0xFFE3D8C4),
    gold = Color(0xFFB08322),
    goldSoft = Color(0xFFF3E6C8),
    success = Color(0xFF3F6B4F),
    heat = listOf(Color(0xFFEAE1D0), Color(0xFFE9D3A0), Color(0xFFD7AE55), Color(0xFFB08322), Color(0xFF6E4F12)),
)

private val DarkBrand = MarathonColors(
    ink = Color(0xFFEDE6D7),
    inkSoft = Color(0xFFC9C3B6),
    muted = Color(0xFF959BAA),
    paper = Color(0xFF0E1424),
    card = Color(0xFF151C2F),
    deep = Color(0xFF1B2338),
    line = Color(0xFF283149),
    gold = Color(0xFFD4A84A),
    goldSoft = Color(0xFF332A18),
    success = Color(0xFF7FB394),
    heat = listOf(Color(0xFF1C2439), Color(0xFF4A3D1E), Color(0xFF7D6227), Color(0xFFB4892F), Color(0xFFE2B95A)),
)

private val LightScheme = lightColorScheme(
    primary = LightBrand.ink,
    onPrimary = Color(0xFFFFFCF6),
    primaryContainer = LightBrand.deep,
    onPrimaryContainer = LightBrand.ink,
    secondary = LightBrand.gold,
    onSecondary = Color.White,
    secondaryContainer = LightBrand.goldSoft,
    onSecondaryContainer = Color(0xFF4A3505),
    tertiary = LightBrand.success,
    background = LightBrand.paper,
    onBackground = LightBrand.ink,
    surface = LightBrand.card,
    onSurface = LightBrand.ink,
    surfaceVariant = LightBrand.deep,
    onSurfaceVariant = LightBrand.muted,
    surfaceContainer = LightBrand.deep,
    surfaceContainerHigh = Color(0xFFE8DECB),
    surfaceContainerHighest = Color(0xFFE2D7C2),
    surfaceContainerLow = LightBrand.card,
    surfaceContainerLowest = Color.White,
    inverseSurface = LightBrand.ink,
    inverseOnSurface = Color(0xFFF7F1E6),
    inversePrimary = LightBrand.goldSoft,
    outline = Color(0xFFCDBFA6),
    outlineVariant = LightBrand.line,
    error = Color(0xFF8C2F2F),
)

private val DarkScheme = darkColorScheme(
    primary = DarkBrand.ink,
    onPrimary = DarkBrand.paper,
    primaryContainer = DarkBrand.deep,
    onPrimaryContainer = DarkBrand.ink,
    secondary = DarkBrand.gold,
    onSecondary = DarkBrand.paper,
    secondaryContainer = DarkBrand.goldSoft,
    onSecondaryContainer = Color(0xFFF3E6C8),
    tertiary = DarkBrand.success,
    background = DarkBrand.paper,
    onBackground = DarkBrand.ink,
    surface = DarkBrand.card,
    onSurface = DarkBrand.ink,
    surfaceVariant = DarkBrand.deep,
    onSurfaceVariant = DarkBrand.muted,
    surfaceContainer = DarkBrand.deep,
    surfaceContainerHigh = Color(0xFF222B42),
    surfaceContainerHighest = Color(0xFF29334C),
    surfaceContainerLow = DarkBrand.card,
    surfaceContainerLowest = Color(0xFF0A0F1C),
    inverseSurface = DarkBrand.ink,
    inverseOnSurface = DarkBrand.paper,
    inversePrimary = DarkBrand.gold,
    outline = Color(0xFF3A4560),
    outlineVariant = DarkBrand.line,
    error = Color(0xFFE6A3A3),
)

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.plex() = copy(fontFamily = Plex)
    fun TextStyle.amiri() = copy(fontFamily = Amiri, fontWeight = FontWeight.Normal)
    return Typography(
        displayLarge = base.displayLarge.amiri(),
        displayMedium = base.displayMedium.amiri(),
        displaySmall = base.displaySmall.amiri().copy(fontSize = 34.sp, lineHeight = 52.sp),
        headlineLarge = base.headlineLarge.amiri().copy(fontSize = 32.sp, lineHeight = 48.sp),
        headlineMedium = base.headlineMedium.amiri().copy(fontSize = 28.sp, lineHeight = 42.sp),
        headlineSmall = base.headlineSmall.amiri().copy(fontSize = 23.sp, lineHeight = 36.sp),
        titleLarge = base.titleLarge.plex().copy(fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
        titleMedium = base.titleMedium.plex().copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        titleSmall = base.titleSmall.plex().copy(fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.plex().copy(fontSize = 16.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.plex().copy(fontSize = 14.sp, lineHeight = 22.sp),
        bodySmall = base.bodySmall.plex().copy(fontSize = 12.5.sp, lineHeight = 19.sp),
        labelLarge = base.labelLarge.plex().copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        labelMedium = base.labelMedium.plex().copy(fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.plex(),
    )
}

val LocalBrand = staticCompositionLocalOf { LightBrand }

object Brand {
    val colors: MarathonColors @Composable get() = LocalBrand.current
}

@Composable
fun MarathonTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalBrand provides if (dark) DarkBrand else LightBrand) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = typography(), content = content)
    }
}
