package com.zerorizz.habits

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val HabitColors = listOf(
    Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFFE91E63),
    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFFC107), Color(0xFFF44336),
    Color(0xFF3F51B5), Color(0xFF8BC34A), Color(0xFF795548), Color(0xFF607D8B),
)

fun habitColor(i: Int): Color = HabitColors[((i % HabitColors.size) + HabitColors.size) % HabitColors.size]

fun onColorFor(c: Color): Color = if (c.luminance() > 0.5f) Color.Black else Color.White

private fun seededScheme(c: Color, dark: Boolean): ColorScheme =
    if (dark) {
        val p = lerp(c, Color.White, 0.25f)
        darkColorScheme(
            primary = p, onPrimary = onColorFor(p),
            primaryContainer = lerp(c, Color.Black, 0.55f), onPrimaryContainer = lerp(c, Color.White, 0.85f),
            secondary = lerp(c, Color.White, 0.45f), onSecondary = Color.Black,
            secondaryContainer = lerp(c, Color.Black, 0.7f), onSecondaryContainer = lerp(c, Color.White, 0.85f),
            tertiary = lerp(c, Color(0xFFFFC107), 0.5f),
        )
    } else {
        val p = lerp(c, Color.Black, 0.15f)
        lightColorScheme(
            primary = p, onPrimary = onColorFor(p),
            primaryContainer = lerp(c, Color.White, 0.75f), onPrimaryContainer = lerp(c, Color.Black, 0.7f),
            secondary = lerp(c, Color.Black, 0.3f), onSecondary = Color.White,
            secondaryContainer = lerp(c, Color.White, 0.85f), onSecondaryContainer = lerp(c, Color.Black, 0.7f),
            tertiary = lerp(c, Color(0xFFFF6F00), 0.5f),
        )
    }

@Composable
fun HabitifyTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val ctx = LocalContext.current
    var scheme = when {
        settings.customAccent != null -> seededScheme(Color(settings.customAccent), dark)
        settings.accent < 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> seededScheme(if (settings.accent < 0) HabitColors[0] else habitColor(settings.accent), dark)
    }
    if (settings.theme == ThemeMode.AMOLED) {
        scheme = scheme.copy(
            background = Color.Black, surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0B0B0B),
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF222222),
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val ctl = WindowCompat.getInsetsController(window, view)
            ctl.isAppearanceLightStatusBars = !dark
            ctl.isAppearanceLightNavigationBars = !dark
        }
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * settings.textScale)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/* Small icons not in the core icon set */

val PauseIcon: ImageVector = ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(6f, 5f); horizontalLineTo(10f); verticalLineTo(19f); horizontalLineTo(6f); close()
        moveTo(14f, 5f); horizontalLineTo(18f); verticalLineTo(19f); horizontalLineTo(14f); close()
    }
}.build()

val BarsIcon: ImageVector = ImageVector.Builder("Bars", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(4f, 11f); horizontalLineTo(8f); verticalLineTo(20f); horizontalLineTo(4f); close()
        moveTo(10f, 4f); horizontalLineTo(14f); verticalLineTo(20f); horizontalLineTo(10f); close()
        moveTo(16f, 14f); horizontalLineTo(20f); verticalLineTo(20f); horizontalLineTo(16f); close()
    }
}.build()

val ArchiveIcon: ImageVector = ImageVector.Builder("Archive", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 4f); horizontalLineTo(21f); verticalLineTo(8f); horizontalLineTo(3f); close()
        moveTo(4f, 9f); horizontalLineTo(20f); verticalLineTo(20f); horizontalLineTo(4f); close()
    }
    path(fill = SolidColor(Color.White)) {
        moveTo(9f, 11f); horizontalLineTo(15f); verticalLineTo(13f); horizontalLineTo(9f); close()
    }
}.build()
