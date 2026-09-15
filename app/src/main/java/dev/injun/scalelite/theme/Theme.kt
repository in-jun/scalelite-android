package dev.injun.scalelite.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The app keeps its own colours rather than following the wallpaper.
 *
 * The icon is a mint dial on deep sea green, and the app opens in the same two
 * colours, so it looks like the thing that was tapped. A weight chart drawn in a
 * colour the wallpaper chose would be a different chart on every phone, and the
 * error colour stays the one red on screen, so a reading that failed to be recorded
 * is findable at a glance.
 */
private val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Mint90,
    onPrimaryContainer = Teal10,
    secondary = TealGrey30,
    onSecondary = Color.White,
    secondaryContainer = TealGrey90,
    onSecondaryContainer = Teal10,
    tertiary = Teal30,
    onTertiary = Color.White,
    tertiaryContainer = Mint90,
    onTertiaryContainer = Teal10,
    background = Neutral99,
    onBackground = Teal10,
    surface = Neutral99,
    onSurface = Teal10,
    surfaceVariant = TealGrey90,
    onSurfaceVariant = TealGrey30,
    outline = TealGrey60,
    outlineVariant = TealGrey80,
)

private val DarkColors = darkColorScheme(
    primary = Mint90,
    onPrimary = Teal10,
    primaryContainer = Teal30,
    onPrimaryContainer = Mint90,
    secondary = TealGrey80,
    onSecondary = Teal10,
    secondaryContainer = TealGrey30,
    onSecondaryContainer = TealGrey90,
    tertiary = Mint80,
    onTertiary = Teal10,
    tertiaryContainer = Teal30,
    onTertiaryContainer = Mint90,
    background = Teal10,
    onBackground = Neutral90,
    surface = Teal10,
    onSurface = Neutral90,
    surfaceVariant = TealGrey30,
    onSurfaceVariant = TealGrey80,
    outline = TealGrey60,
    outlineVariant = TealGrey30,
)

@Composable
fun ScaleLiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
