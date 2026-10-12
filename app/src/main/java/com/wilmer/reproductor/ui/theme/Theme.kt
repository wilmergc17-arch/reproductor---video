package com.wilmer.reproductor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Colores de la marca Repropower: degradado naranja → rosa → violeta sobre fondo casi negro
val PowerOrange = Color(0xFFFF7A18)
val PowerPink = Color(0xFFFF2D78)
val PowerViolet = Color(0xFF7B2FF7)

val BrandGradient = Brush.linearGradient(listOf(PowerOrange, PowerPink, PowerViolet))
val BrandGradientHorizontal = Brush.horizontalGradient(listOf(PowerOrange, PowerPink, PowerViolet))

// Colores de las etiquetas de tipo de video
val MovieColor = Color(0xFFFFB547)
val EpisodeColor = Color(0xFF5CC8FF)
val ReelColor = Color(0xFFFF6FB1)

private val Colors = darkColorScheme(
    primary = PowerPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1230),
    onPrimaryContainer = Color(0xFFFFD9E4),
    secondary = PowerOrange,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2A2A36),
    onSecondaryContainer = Color(0xFFF1F1F6),
    tertiary = Color(0xFFA77BFF),
    background = Color(0xFF0B0B10),
    onBackground = Color(0xFFF1F1F6),
    surface = Color(0xFF0B0B10),
    onSurface = Color(0xFFF1F1F6),
    surfaceVariant = Color(0xFF22222C),
    onSurfaceVariant = Color(0xFFA3A3B5),
    surfaceContainerLowest = Color(0xFF08080C),
    surfaceContainerLow = Color(0xFF121218),
    surfaceContainer = Color(0xFF16161E),
    surfaceContainerHigh = Color(0xFF1E1E28),
    surfaceContainerHighest = Color(0xFF282834),
    outline = Color(0xFF3A3A48),
    outlineVariant = Color(0xFF2A2A36),
    error = Color(0xFFFF5A5F),
)

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

/** Tema de Repropower: siempre oscuro, como las apps de video modernas. */
@Composable
fun RepropowerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, shapes = AppShapes, content = content)
}
