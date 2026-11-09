package com.globalinstitute.akademos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.globalinstitute.akademos.R
import com.globalinstitute.akademos.data.ThemeMode

/*
 * Akademos palette — drawn from an Indian tuition classroom rather than an institution:
 *   Jamun     #2E1A47  deep purple ink: text, primary buttons
 *   Marigold  #F5A524  the one loud colour: what's happening now
 *   Paper     #F6F5FA  cool off-white page
 *   Leaf      #1E8E5A  present
 *   Sindoor   #D2462F  absent
 *   Haldi     #B7791F  late
 */
object Ak {
    val Jamun = Color(0xFF2E1A47)
    val JamunSoft = Color(0xFFEDE7F6)
    val Marigold = Color(0xFFF5A524)
    val MarigoldDeep = Color(0xFFE08E0B)
    val OnMarigold = Color(0xFF2A1700)
    val Paper = Color(0xFFF6F5FA)
    val Night = Color(0xFF130E1A)
}

private val LightColors = lightColorScheme(
    primary = Ak.Jamun, onPrimary = Color.White,
    primaryContainer = Ak.JamunSoft, onPrimaryContainer = Ak.Jamun,
    secondary = Ak.Marigold, onSecondary = Ak.OnMarigold,
    secondaryContainer = Color(0xFFFFEBC7), onSecondaryContainer = Color(0xFF3D2600),
    tertiary = Color(0xFF1E8E5A), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5F1E2), onTertiaryContainer = Color(0xFF0B3321),
    background = Ak.Paper, onBackground = Color(0xFF1C1724),
    surface = Color.White, onSurface = Color(0xFF1C1724),
    surfaceVariant = Color(0xFFEFECF5), onSurfaceVariant = Color(0xFF5B5468),
    surfaceContainer = Color(0xFFF0EEF6), surfaceContainerHigh = Color(0xFFE9E6F1),
    surfaceContainerLow = Color(0xFFFAF9FD),
    outline = Color(0xFFCFC9DA), outlineVariant = Color(0xFFE6E2EE),
    error = Color(0xFFC03A25), errorContainer = Color(0xFFFBE2DD), onErrorContainer = Color(0xFF5A140A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD9C8F5), onPrimary = Ak.Jamun,
    primaryContainer = Color(0xFF3A2858), onPrimaryContainer = Color(0xFFEDE3FF),
    secondary = Ak.Marigold, onSecondary = Ak.OnMarigold,
    secondaryContainer = Color(0xFF4A3410), onSecondaryContainer = Color(0xFFFFE0A8),
    tertiary = Color(0xFF6FD6A1), onTertiary = Color(0xFF07301C),
    tertiaryContainer = Color(0xFF15402B), onTertiaryContainer = Color(0xFFC9F2DC),
    background = Ak.Night, onBackground = Color(0xFFF0EBF7),
    surface = Color(0xFF1E1727), onSurface = Color(0xFFF0EBF7),
    surfaceVariant = Color(0xFF2A2234), onSurfaceVariant = Color(0xFFC1B8CE),
    surfaceContainer = Color(0xFF221B2C), surfaceContainerHigh = Color(0xFF2A2234),
    surfaceContainerLow = Color(0xFF19131F),
    outline = Color(0xFF4C4357), outlineVariant = Color(0xFF332B3D),
    error = Color(0xFFFF9E8A), errorContainer = Color(0xFF4D1A12), onErrorContainer = Color(0xFFFFDAD2),
)

@Immutable
data class StatusColors(
    val present: Color, val onPresent: Color, val presentSoft: Color,
    val absent: Color, val onAbsent: Color, val absentSoft: Color,
    val late: Color, val onLate: Color, val lateSoft: Color,
)

private val LightStatus = StatusColors(
    Color(0xFF1E8E5A), Color.White, Color(0xFFDDF3E7),
    Color(0xFFD2462F), Color.White, Color(0xFFFBE3DE),
    Color(0xFFB7791F), Color.White, Color(0xFFFCEFD6),
)
private val DarkStatus = StatusColors(
    Color(0xFF6FD6A1), Color(0xFF07301C), Color(0xFF173726),
    Color(0xFFFF8F78), Color(0xFF3D0C04), Color(0xFF45201A),
    Color(0xFFF5B94E), Color(0xFF2A1700), Color(0xFF433112),
)

val LocalStatus = staticCompositionLocalOf { LightStatus }
val LocalIsDark = staticCompositionLocalOf { false }

// ---------------------------------------------------------------- type
@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int, width: Float, opsz: Float) = Font(
    R.font.bricolage, FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight), FontVariation.width(width), FontVariation.Setting("opsz", opsz),
    ),
)

@OptIn(ExperimentalTextApi::class)
private fun atkinson(weight: Int, res: Int = R.font.atkinson) = Font(
    res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Big, slightly condensed display cut — used sparingly for titles and the "now" time. */
val Display = FontFamily(bricolage(700, 88f, 72f), bricolage(800, 82f, 96f))
private val Heading = FontFamily(bricolage(600, 95f, 24f), bricolage(700, 92f, 36f))
/** Atkinson Hyperlegible: designed for readers with low vision. */
val Body = FontFamily(atkinson(400), atkinson(500), atkinson(600), atkinson(700))
/** Times, roll numbers, marks and access keys. */
val Mono = FontFamily(atkinson(400, R.font.atkinson_mono), atkinson(600, R.font.atkinson_mono), atkinson(700, R.font.atkinson_mono))

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp, lineHeight = 56.sp, letterSpacing = (-0.02).em),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.015).em),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.01).em),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em),
    headlineSmall = TextStyle(fontFamily = Heading, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Heading, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.06.em),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun AkademosTheme(mode: ThemeMode, textScale: Float, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val colors: ColorScheme = if (dark) DarkColors else LightColors
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalStatus provides if (dark) DarkStatus else LightStatus,
        LocalIsDark provides dark,
        LocalDensity provides Density(d.density, d.fontScale * textScale),
    ) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

fun parseHex(hex: String?, fallback: Color = Ak.Jamun): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(fallback)
