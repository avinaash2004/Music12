package com.offlinemusicpro.player.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

data class AccentOption(val name: String, val primary: Color, val secondary: Color)

val AccentOptions = listOf(
    AccentOption("Purple", AccentPurple, AccentLavender),
    AccentOption("Blue", Color(0xFF3B82F6), Color(0xFF93C5FD)),
    AccentOption("Green", Color(0xFF22C55E), Color(0xFF86EFAC)),
    AccentOption("Pink", Color(0xFFEC4899), Color(0xFFF9A8D4)),
    AccentOption("Orange", Color(0xFFF97316), Color(0xFFFDBA74))
)

@Composable
fun OfflineMusicProTheme(
    accent: AccentOption = AccentOptions[0],
    content: @Composable () -> Unit
) {
    val scheme = remember(accent) {
        darkColorScheme(
            primary = accent.primary,
            onPrimary = TextPrimary,
            secondary = accent.secondary,
            onSecondary = AmoledBlack,
            background = AmoledBlack,
            onBackground = TextPrimary,
            surface = SurfaceDark,
            onSurface = TextPrimary,
            surfaceVariant = CardDark,
            onSurfaceVariant = TextSecondary,
            surfaceContainerLowest = AmoledBlack,
            surfaceContainerLow = SurfaceDark,
            surfaceContainer = SurfaceDark,
            surfaceContainerHigh = CardDark,
            surfaceContainerHighest = CardDark,
            outlineVariant = CardDark
        )
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
