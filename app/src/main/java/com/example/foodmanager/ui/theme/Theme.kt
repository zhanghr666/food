package com.example.foodmanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = FreshGreenLight,
    onPrimary = Color(0xFF003824),
    primaryContainer = FreshContainerDark,
    onPrimaryContainer = FreshContainerLight,
    secondary = FreshGreenLight,
    onSecondary = Color(0xFF003824),
    secondaryContainer = FreshContainerDark,
    onSecondaryContainer = FreshContainerLight,
    tertiary = WarnAmberLight,
    onTertiary = Color(0xFF3F2A00),
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    surfaceTint = FreshGreenLight,
    surfaceContainerHigh = Color(0xFF28342D),
    onSurface = OnSurfaceDark,
    surfaceVariant = Color(0xFF28342D),
    onSurfaceVariant = Color(0xFFBCC8C0),
    outline = Color(0xFF83948A),
    outlineVariant = Color(0xFF3F5046),
    error = DangerRedLight,
    onError = Color(0xFF690005),
    errorContainer = DangerContainerDark,
    onErrorContainer = DangerContainerLight,
)

private val LightColorScheme = lightColorScheme(
    primary = FreshGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDECE1),
    onPrimaryContainer = Color(0xFF123B28),
    secondary = FreshGreen,
    onSecondary = Color.White,
    secondaryContainer = FreshContainerLight,
    onSecondaryContainer = Color(0xFF00200F),
    tertiary = Color(0xFFA65C00),
    onTertiary = Color.White,
    background = Color(0xFFF5F6F3),
    onBackground = OnSurfaceLight,
    surface = Color(0xFFFEFFFB),
    surfaceTint = FreshGreen,
    surfaceContainerHigh = Color(0xFFFEFFFB),
    onSurface = OnSurfaceLight,
    surfaceVariant = Color(0xFFEBEEEA),
    onSurfaceVariant = Color(0xFF68736C),
    outline = Color(0xFF8A968E),
    outlineVariant = Color(0xFFDFE5DF),
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerContainerLight,
    onErrorContainer = Color(0xFF410E0B),
)

@Composable
fun FoodManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 动态取色在 Android 12+ 可用；这里默认关闭，保证「食物」主题色一致
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(20.dp)),
        content = content,
    )
}
