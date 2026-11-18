package dev.xdripyou.app.ui.theme

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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.xdripyou.app.domain.ThemeModeOption

@Immutable
data class GlucoseColors(
    val low: Color,
    val inRange: Color,
    val high: Color,
    val urgentHigh: Color,
    val stale: Color,
) {
    fun colorForMgdl(
        mgdl: Double,
        targetLow: Double = 70.0,
        targetHigh: Double = 180.0,
        urgentHighThreshold: Double = 250.0,
    ): Color = when {
        mgdl < targetLow -> low
        mgdl > urgentHighThreshold -> urgentHigh
        mgdl > targetHigh -> high
        else -> inRange
    }
}

val LocalGlucoseColors = staticCompositionLocalOf {
    GlucoseColors(
        low = Color(0xFFD32F2F),
        inRange = Color(0xFF0E8A57),
        high = Color(0xFFE07A00),
        urgentHigh = Color(0xFF9C27B0),
        stale = Color(0xFF757575),
    )
}

private val LightFallbackScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF006A6A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF1F0),
    onPrimaryContainer = Color(0xFF002020),
    secondary = Color(0xFF4A6363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E7),
    onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF4B607C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF),
    onTertiaryContainer = Color(0xFF041C35),
)

private val DarkFallbackScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF80D5D4),
    onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF004F4F),
    onPrimaryContainer = Color(0xFF9CF1F0),
    secondary = Color(0xFFB0CCCB),
    onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFB3C8E8),
    onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863),
    onTertiaryContainer = Color(0xFFD3E4FF),
)

@Composable
fun XdripYouTheme(
    themeMode: ThemeModeOption,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeModeOption.SYSTEM -> isSystemInDarkTheme()
        ThemeModeOption.LIGHT -> false
        ThemeModeOption.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkFallbackScheme
        else -> LightFallbackScheme
    }

    val glucoseColors = if (darkTheme) {
        GlucoseColors(
            low = Color(0xFFFF6B6B),
            inRange = Color(0xFF4CD995),
            high = Color(0xFFFFB74D),
            urgentHigh = Color(0xFFCF6679),
            stale = Color(0xFF9E9E9E),
        )
    } else {
        GlucoseColors(
            low = Color(0xFFD32F2F),
            inRange = Color(0xFF0E8A57),
            high = Color(0xFFE07A00),
            urgentHigh = Color(0xFF9C27B0),
            stale = Color(0xFF757575),
        )
    }

    CompositionLocalProvider(LocalGlucoseColors provides glucoseColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
