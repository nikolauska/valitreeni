package app.fittracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.fittracker.R

/** The "Pine" palette: pine green for structure, cloudberry amber only for "do this now". */
private object Pine {
    val pine = Color(0xFF1F3A2E)
    val amber = Color(0xFFF2A33A)
    val moss = Color(0xFF4F7F4B)
    val paper = Color(0xFFF4F1E8)
    val paper2 = Color(0xFFEAE5D7)
    val ink = Color(0xFF14231C)
    val ink2 = Color(0xFF4A5A51)
    val line = Color(0xFFD6D0BF)

    val night = Color(0xFF0F1A15)
    val nightCard = Color(0xFF17261F)
    val nightHigh = Color(0xFF22362C)
    val mint = Color(0xFF9FD3B0)
    val nightInk = Color(0xFFE6EFE8)
    val nightInk2 = Color(0xFF9DB2A5)
}

/**
 * Brand roles Material's scheme has no slot for. Kept separate so the hero card, the one amber
 * action and the "done" state look the same everywhere without bending Material roles.
 */
@Immutable
data class BrandColors(
    val hero: Color,
    val onHero: Color,
    val accent: Color,
    val onAccent: Color,
    val done: Color,
    val doneContainer: Color,
    val warnContainer: Color,
    val onWarn: Color,
    val card: Color,
    val track: Color,
    val figure: Color,
)

private val LightBrand = BrandColors(
    hero = Pine.pine, onHero = Pine.paper, accent = Pine.amber, onAccent = Pine.ink,
    done = Pine.moss, doneContainer = Color(0xFFE1ECDD), warnContainer = Color(0xFFFDF0D8), onWarn = Color(0xFF6B4508),
    card = Color(0xFFFFFDF7), track = Pine.paper2, figure = Pine.pine,
)

private val DarkBrand = BrandColors(
    hero = Color(0xFF244536), onHero = Pine.nightInk, accent = Pine.amber, onAccent = Pine.ink,
    done = Color(0xFF6E9B6A), doneContainer = Color(0xFF1C2F25), warnContainer = Color(0xFF3A2C12), onWarn = Color(0xFFF5CF8E),
    card = Pine.nightCard, track = Pine.nightHigh, figure = Pine.mint,
)

private val LightColors = lightColorScheme(
    primary = Pine.pine, onPrimary = Pine.paper,
    primaryContainer = Color(0xFFD5E4D6), onPrimaryContainer = Pine.ink,
    secondary = Pine.moss, onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1ECDD), onSecondaryContainer = Pine.ink,
    tertiary = Pine.amber, onTertiary = Pine.ink,
    tertiaryContainer = Color(0xFFFDF0D8), onTertiaryContainer = Color(0xFF6B4508),
    background = Pine.paper, onBackground = Pine.ink,
    surface = Pine.paper, onSurface = Pine.ink,
    surfaceVariant = Pine.paper2, onSurfaceVariant = Pine.ink2,
    surfaceContainerLowest = Color(0xFFFFFDF7), surfaceContainerLow = Color(0xFFF9F6EE),
    surfaceContainer = Color(0xFFEFEBDF), surfaceContainerHigh = Pine.paper2, surfaceContainerHighest = Color(0xFFE4DECE),
    outline = Color(0xFF7D877F), outlineVariant = Pine.line,
    error = Color(0xFFB8343F), onError = Color.White,
    errorContainer = Color(0xFFF7D9D6), onErrorContainer = Color(0xFF5C1016),
)

private val DarkColors = darkColorScheme(
    primary = Pine.mint, onPrimary = Color(0xFF0F2A1D),
    primaryContainer = Color(0xFF244536), onPrimaryContainer = Color(0xFFCDEBD6),
    secondary = Color(0xFF8DBB88), onSecondary = Color(0xFF0F2A1D),
    secondaryContainer = Color(0xFF1C2F25), onSecondaryContainer = Pine.nightInk,
    tertiary = Pine.amber, onTertiary = Pine.ink,
    tertiaryContainer = Color(0xFF3A2C12), onTertiaryContainer = Color(0xFFF5CF8E),
    background = Pine.night, onBackground = Pine.nightInk,
    surface = Pine.night, onSurface = Pine.nightInk,
    surfaceVariant = Pine.nightHigh, onSurfaceVariant = Pine.nightInk2,
    surfaceContainerLowest = Color(0xFF0B140F), surfaceContainerLow = Color(0xFF14211B),
    surfaceContainer = Pine.nightCard, surfaceContainerHigh = Color(0xFF1D2E25), surfaceContainerHighest = Pine.nightHigh,
    outline = Color(0xFF5E7166), outlineVariant = Color(0xFF2A3F34),
    error = Color(0xFFFFB4AB), onError = Color(0xFF5C1016),
    errorContainer = Color(0xFF7A1F25), onErrorContainer = Color(0xFFFFDAD6),
)

// One variable font file covers every weight; bundling it avoids depending on Play Services font downloads.
private fun bricolage(weight: Int) =
    Font(R.font.bricolage_grotesque, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

private val Display = FontFamily(bricolage(500), bricolage(700), bricolage(800))

private val Base = Typography()

private fun TextStyle.display(weight: FontWeight) =
    // Tabular digits keep counts like 20 / 40 from jumping sideways as they change.
    copy(fontFamily = Display, fontWeight = weight, fontFeatureSettings = "tnum", letterSpacing = (-0.2).sp)

private val BrandTypography = Base.copy(
    displayLarge = Base.displayLarge.display(FontWeight.ExtraBold),
    displayMedium = Base.displayMedium.display(FontWeight.ExtraBold),
    displaySmall = Base.displaySmall.display(FontWeight.ExtraBold),
    headlineLarge = Base.headlineLarge.display(FontWeight.ExtraBold),
    headlineMedium = Base.headlineMedium.display(FontWeight.ExtraBold),
    headlineSmall = Base.headlineSmall.display(FontWeight.Bold),
    titleLarge = Base.titleLarge.display(FontWeight.Bold),
    titleMedium = Base.titleMedium.display(FontWeight.Bold),
)

private val LocalBrandColors = staticCompositionLocalOf { LightBrand }

/** Brand colors for the current light/dark mode; see [BrandColors]. */
val MaterialTheme.brand: BrandColors
    @Composable @ReadOnlyComposable get() = LocalBrandColors.current

@Composable
fun FitTrackerTheme(content: @Composable () -> Unit) {
    // Fixed brand colors instead of wallpaper colors: the app should look like itself on every phone.
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalBrandColors provides if (dark) DarkBrand else LightBrand) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = BrandTypography, content = content)
    }
}
