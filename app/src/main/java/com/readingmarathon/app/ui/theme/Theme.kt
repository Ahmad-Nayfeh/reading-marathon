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

/** Amiri, for book titles and quotes. */
val Amiri = FontFamily(Font(R.font.amiri_regular, FontWeight.Normal))

/** Brand colours outside Material's roles. */
@Immutable
data class MarathonColors(
    val gold: Color,
    val goldSoft: Color,
    val ink: Color,
    val paper: Color,
    val muted: Color,
    val line: Color,
    val silver: Color,
    val bronze: Color,
    val success: Color,
    val chart: List<Color>,
    val heat: List<Color>,
)

private val LightBrand = MarathonColors(
    gold = Color(0xFFB7791F),
    goldSoft = Color(0xFFF7E7C4),
    ink = Color(0xFF14213D),
    paper = Color(0xFFFBF7EF),
    muted = Color(0xFF5B6478),
    line = Color(0xFFE6DFD0),
    silver = Color(0xFF8A94A6),
    bronze = Color(0xFFA0643A),
    success = Color(0xFF2E7D4F),
    chart = listOf(Color(0xFF14213D), Color(0xFFB7791F), Color(0xFF3D6FB6), Color(0xFF2E7D4F), Color(0xFF9C4A6B)),
    heat = listOf(Color(0xFFEEE8DB), Color(0xFFF3D99E), Color(0xFFE7B24F), Color(0xFFC07F1C), Color(0xFF7A4D0E)),
)

private val DarkBrand = MarathonColors(
    gold = Color(0xFFF2B84B),
    goldSoft = Color(0xFF3A3020),
    ink = Color(0xFFEEF1F7),
    paper = Color(0xFF0B1222),
    muted = Color(0xFF9AA6BD),
    line = Color(0xFF243049),
    silver = Color(0xFFB9C2D3),
    bronze = Color(0xFFD08B5B),
    success = Color(0xFF6CCB92),
    chart = listOf(Color(0xFFF2B84B), Color(0xFF8FB3FF), Color(0xFF6CCB92), Color(0xFFE59AB7), Color(0xFFEEF1F7)),
    heat = listOf(Color(0xFF18223A), Color(0xFF5A4A22), Color(0xFF8E6C27), Color(0xFFC9902F), Color(0xFFF2B84B)),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF14213D),
    onPrimary = Color(0xFFFBF7EF),
    primaryContainer = Color(0xFFDCE3F2),
    onPrimaryContainer = Color(0xFF14213D),
    secondary = Color(0xFFB7791F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7E7C4),
    onSecondaryContainer = Color(0xFF4A3005),
    tertiary = Color(0xFF3D6FB6),
    background = Color(0xFFFBF7EF),
    onBackground = Color(0xFF14213D),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF14213D),
    surfaceVariant = Color(0xFFF2ECDF),
    onSurfaceVariant = Color(0xFF5B6478),
    surfaceContainer = Color(0xFFF6F1E6),
    surfaceContainerHigh = Color(0xFFF1EBDD),
    surfaceContainerLow = Color(0xFFFFFDF8),
    outline = Color(0xFFCFC6B3),
    outlineVariant = Color(0xFFE6DFD0),
    error = Color(0xFFB3261E),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF2B84B),
    onPrimary = Color(0xFF1E1404),
    primaryContainer = Color(0xFF3A3020),
    onPrimaryContainer = Color(0xFFF7E7C4),
    secondary = Color(0xFF8FB3FF),
    onSecondary = Color(0xFF0B1222),
    secondaryContainer = Color(0xFF1F2C4A),
    onSecondaryContainer = Color(0xFFDCE3F2),
    tertiary = Color(0xFF6CCB92),
    background = Color(0xFF0B1222),
    onBackground = Color(0xFFEEF1F7),
    surface = Color(0xFF111A2E),
    onSurface = Color(0xFFEEF1F7),
    surfaceVariant = Color(0xFF1A2440),
    onSurfaceVariant = Color(0xFF9AA6BD),
    surfaceContainer = Color(0xFF141E34),
    surfaceContainerHigh = Color(0xFF1A2440),
    surfaceContainerLow = Color(0xFF0F182B),
    outline = Color(0xFF34415E),
    outlineVariant = Color(0xFF243049),
    error = Color(0xFFF2B8B5),
)

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.plex() = copy(fontFamily = Plex)
    return Typography(
        displayLarge = base.displayLarge.plex().copy(fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.plex().copy(fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.plex().copy(fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.plex().copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.plex().copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
        headlineSmall = base.headlineSmall.plex().copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleLarge = base.titleLarge.plex().copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = base.titleMedium.plex().copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        titleSmall = base.titleSmall.plex().copy(fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.plex().copy(fontSize = 16.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.plex().copy(fontSize = 14.sp, lineHeight = 22.sp),
        bodySmall = base.bodySmall.plex().copy(lineHeight = 18.sp),
        labelLarge = base.labelLarge.plex().copy(fontWeight = FontWeight.SemiBold),
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
