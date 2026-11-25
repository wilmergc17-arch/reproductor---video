package com.wilmer.reproductor.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wilmer.reproductor.data.ThemeChoice

// Colores de las etiquetas de tipo de video (iguales en todos los temas)
val MovieColor = Color(0xFFFFB547)
val EpisodeColor = Color(0xFF5CC8FF)
val ReelColor = Color(0xFFFF6FB1)

/** Los tres colores del degradado de la marca para un tema. */
@Immutable
data class BrandColors(val start: Color, val middle: Color, val end: Color) {
    val diagonal: Brush get() = Brush.linearGradient(listOf(start, middle, end))
    val horizontal: Brush get() = Brush.horizontalGradient(listOf(start, middle, end))
}

/** Una paleta: degradado, color principal y fondo. */
private data class Palette(
    val brand: BrandColors,
    val primary: Color,
    val background: Color,
    val light: Boolean = false,
)

private val Power = Palette(
    BrandColors(Color(0xFFFF7A18), Color(0xFFFF2D78), Color(0xFF7B2FF7)),
    primary = Color(0xFFFF2D78),
    background = Color(0xFF0B0B10),
)

private fun palette(choice: ThemeChoice): Palette = when (choice) {
    ThemeChoice.POWER, ThemeChoice.FONDO -> Power
    ThemeChoice.OCEANO -> Palette(
        BrandColors(Color(0xFF00C6FF), Color(0xFF0072FF), Color(0xFF6A11CB)),
        primary = Color(0xFF3D8BFF),
        background = Color(0xFF080D18),
    )
    ThemeChoice.ESMERALDA -> Palette(
        BrandColors(Color(0xFFB4F06C), Color(0xFF2BD99F), Color(0xFF0B8F8A)),
        primary = Color(0xFF22C997),
        background = Color(0xFF07110E),
    )
    ThemeChoice.ATARDECER -> Palette(
        BrandColors(Color(0xFFFFD200), Color(0xFFFF8C00), Color(0xFFFF3D3D)),
        primary = Color(0xFFFF8C1A),
        background = Color(0xFF130D08),
    )
    ThemeChoice.AMOLED -> Power.copy(background = Color.Black)
    ThemeChoice.CLARO -> Power.copy(primary = Color(0xFFE0115F), background = Color(0xFFF6F6FA), light = true)
}

/** Colores del degradado de cada tema, para mostrar las muestras en Ajustes. */
fun brandColorsFor(choice: ThemeChoice): BrandColors = palette(choice).brand

/** Color de fondo de cada tema, para las muestras en Ajustes. */
fun backgroundFor(choice: ThemeChoice): Color = palette(choice).background

private fun darkScheme(p: Palette): ColorScheme {
    val bg = p.background
    fun up(amount: Float) = lerp(bg, Color.White, amount)
    return darkColorScheme(
        primary = p.primary,
        onPrimary = Color.White,
        primaryContainer = lerp(bg, p.primary, 0.3f),
        onPrimaryContainer = Color.White,
        secondary = p.brand.start,
        onSecondary = Color.Black,
        secondaryContainer = up(0.12f),
        onSecondaryContainer = Color(0xFFF1F1F6),
        tertiary = p.brand.end,
        background = bg,
        onBackground = Color(0xFFF1F1F6),
        surface = bg,
        onSurface = Color(0xFFF1F1F6),
        surfaceVariant = up(0.10f),
        onSurfaceVariant = Color(0xFFA3A3B5),
        surfaceContainerLowest = bg,
        surfaceContainerLow = up(0.04f),
        surfaceContainer = up(0.06f),
        surfaceContainerHigh = up(0.09f),
        surfaceContainerHighest = up(0.13f),
        outline = up(0.22f),
        outlineVariant = up(0.14f),
        error = Color(0xFFFF5A5F),
    )
}

private fun lightScheme(p: Palette): ColorScheme {
    val bg = p.background
    fun down(amount: Float) = lerp(bg, Color(0xFF1B1B24), amount)
    return lightColorScheme(
        primary = p.primary,
        onPrimary = Color.White,
        primaryContainer = lerp(Color.White, p.primary, 0.15f),
        onPrimaryContainer = Color(0xFF3A0A1E),
        secondary = p.brand.start,
        tertiary = p.brand.end,
        background = bg,
        onBackground = Color(0xFF16161E),
        surface = bg,
        onSurface = Color(0xFF16161E),
        surfaceVariant = down(0.06f),
        onSurfaceVariant = Color(0xFF5A5A6A),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = down(0.02f),
        surfaceContainer = down(0.04f),
        surfaceContainerHigh = down(0.07f),
        surfaceContainerHighest = down(0.10f),
        outline = down(0.25f),
        outlineVariant = down(0.14f),
        error = Color(0xFFD32F2F),
    )
}

private val LocalBrand = staticCompositionLocalOf { Power.brand }

/** Degradado del tema actual (diagonal, para logos y botones redondos). */
val BrandGradient: Brush
    @Composable @ReadOnlyComposable get() = LocalBrand.current.diagonal

/** Degradado del tema actual de izquierda a derecha (botones y píldoras). */
val BrandGradientHorizontal: Brush
    @Composable @ReadOnlyComposable get() = LocalBrand.current.horizontal

private val BaseType = Typography()

private val Type = Typography(
    headlineMedium = BaseType.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = BaseType.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = BaseType.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = BaseType.bodyLarge.copy(fontWeight = FontWeight.Medium),
    labelLarge = BaseType.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** El tema claro necesita íconos oscuros en las barras del sistema. */
fun ThemeChoice.isLight(): Boolean = this == ThemeChoice.CLARO

/** Tema de Repropower con la paleta elegida en Ajustes. */
@Composable
fun RepropowerTheme(choice: ThemeChoice = ThemeChoice.POWER, content: @Composable () -> Unit) {
    val p = palette(choice)
    val context = LocalContext.current
    val dynamic = choice == ThemeChoice.FONDO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = when {
        dynamic -> dynamicDarkColorScheme(context)
        p.light -> lightScheme(p)
        else -> darkScheme(p)
    }
    // Con los colores del fondo de pantalla, el degradado sale de esos mismos colores
    val brand = if (dynamic) BrandColors(colors.tertiary, colors.primary, colors.secondary) else p.brand
    CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(colorScheme = colors, typography = Type, shapes = AppShapes, content = content)
    }
}
