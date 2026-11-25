package tv.cleanyour.ui.design.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// DESIGN-SYSTEM 2 and 11. Dark is the default and the brand; light mirrors the roles one to one.
@Immutable
data class CytColors(
    val bg: Color, val surface1: Color, val surface2: Color, val surface3: Color,
    val rim: Color, val outline: Color, val outlineStrong: Color,
    val textPrimary: Color, val textSecondary: Color, val textTertiary: Color,
    val primary: Color, val onPrimary: Color, val primaryPressed: Color, val accent: Color,
    val glowBrand: Color, val glowTv: Color,
    val success: Color, val successContainer: Color, val onSuccessContainer: Color,
    val warning: Color, val warningContainer: Color, val onWarningContainer: Color,
    val danger: Color, val dangerFill: Color, val onDanger: Color,
    val dangerContainer: Color, val onDangerContainer: Color,
    val scrim: Color,
    // Glass recipe (DESIGN-SYSTEM 5.1): fill top and bottom, specular top, rim.
    val glassFillTop: Color, val glassFillBottom: Color, val glassSpecular: Color,
    // The springy highlight: brighter fill, an 80% rim and three outer rings.
    val highlightFillTop: Color, val highlightFillBottom: Color, val highlightSpecular: Color,
    val highlightRim: Color, val highlightRing: Color,
    // Ambient glow core alpha (24% dark, 14% light) and its mid stop.
    val glowCoreAlpha: Float, val glowMidAlpha: Float,
    val isDark: Boolean,
)

val DarkCytColors = CytColors(
    // Bloatless: the near-black page, the #18181A cards with a #262628 hairline, and the teal accent.
    bg = Color(0xFF0F0F10), surface1 = Color(0xFF18181A), surface2 = Color(0xFF1C1C1E),
    surface3 = Color(0xFF2A2A2C), rim = Color(0xFF2A2A2C), outline = Color(0xFF262628),
    outlineStrong = Color(0x5CFFFFFF), textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFFA1A1A6), textTertiary = Color(0xFF98989E),
    primary = Color(0xFF5DCAA5), onPrimary = Color(0xFF04342C), primaryPressed = Color(0xFF4AB892),
    accent = Color(0xFF5DCAA5),
    glowBrand = Color(0xFF1D9E75), glowTv = Color(0xFF1D9E75),
    success = Color(0xFF30D158), successContainer = Color(0xFF0D2A16),
    onSuccessContainer = Color(0xFF5CE07E), warning = Color(0xFFFFB340),
    warningContainer = Color(0xFF33240A), onWarningContainer = Color(0xFFFFC46B),
    danger = Color(0xFFFF6961), dangerFill = Color(0xFFFF453A), onDanger = Color(0xFF000000),
    dangerContainer = Color(0xFF3A1512), onDangerContainer = Color(0xFFFF8A80),
    scrim = Color(0xA3000000),
    glassFillTop = Color.White.copy(alpha = 0.09f), glassFillBottom = Color.White.copy(alpha = 0.04f),
    glassSpecular = Color.White.copy(alpha = 0.08f),
    // Bloatless: the chosen row is tinted teal with a teal rim, as in the mockups.
    highlightFillTop = Color(0x2E5DCAA5), highlightFillBottom = Color(0x145DCAA5),
    highlightSpecular = Color.White.copy(alpha = 0.10f), highlightRim = Color(0xCC5DCAA5),
    highlightRing = Color(0xFF5DCAA5),
    glowCoreAlpha = 0.10f, glowMidAlpha = 0.04f,   // Bloatless: a quieter glow, the page reads flat
    isDark = true,
)

val LightCytColors = CytColors(
    bg = Color(0xFFF2F2F4), surface1 = Color(0xFFFFFFFF), surface2 = Color(0xFFF7F7F9),
    surface3 = Color(0xFFE6E6EA), rim = Color(0x1A000000), outline = Color(0x14000000),
    outlineStrong = Color(0x52000000), textPrimary = Color(0xFF0B0B0C),
    textSecondary = Color(0xFF4A4A50), textTertiary = Color(0xFF5A5A61),
    primary = Color(0xFF0F6E56), onPrimary = Color(0xFFFFFFFF), primaryPressed = Color(0xFF085041),
    accent = Color(0xFF0F6E56),
    glowBrand = Color(0xFF1D9E75), glowTv = Color(0xFF1D9E75),
    success = Color(0xFF157030), successContainer = Color(0xFFE3F4E8),
    onSuccessContainer = Color(0xFF0F5A26), warning = Color(0xFF8F5300),
    warningContainer = Color(0xFFFFF1DB), onWarningContainer = Color(0xFF7A4600),
    danger = Color(0xFFC01B2C), dangerFill = Color(0xFFC01B2C), onDanger = Color(0xFFFFFFFF),
    dangerContainer = Color(0xFFFDE7E9), onDangerContainer = Color(0xFFA3121F),
    scrim = Color(0x66000000),
    glassFillTop = Color.White.copy(alpha = 0.72f), glassFillBottom = Color.White.copy(alpha = 0.56f),
    glassSpecular = Color.White.copy(alpha = 0.50f),
    highlightFillTop = Color.White.copy(alpha = 0.95f), highlightFillBottom = Color.White.copy(alpha = 0.80f),
    highlightSpecular = Color.White.copy(alpha = 0.60f), highlightRim = Color(0xCC0F6E56),
    highlightRing = Color(0xFF0F6E56),
    glowCoreAlpha = 0.14f, glowMidAlpha = 0.06f,
    isDark = false,
)

// Stock Material 3 components inherit the brand without wrappers (DESIGN-SYSTEM 11).
fun CytColors.toM3(): ColorScheme = (if (isDark) darkColorScheme() else lightColorScheme()).copy(
    primary = primary, onPrimary = onPrimary, background = bg, onBackground = textPrimary,
    surface = bg, onSurface = textPrimary, onSurfaceVariant = textSecondary,
    surfaceContainerLowest = bg, surfaceContainerLow = surface1, surfaceContainer = surface2,
    surfaceContainerHigh = surface3, surfaceContainerHighest = surface3,
    surfaceVariant = surface3, inverseSurface = surface3, inverseOnSurface = textPrimary,
    outline = outlineStrong, outlineVariant = outline, error = danger,
    errorContainer = dangerContainer, onErrorContainer = onDangerContainer,
    secondary = accent, onSecondary = onPrimary, tertiary = accent, scrim = scrim,
    primaryContainer = surface3, onPrimaryContainer = textPrimary,
    secondaryContainer = surface3, onSecondaryContainer = textPrimary,
)
