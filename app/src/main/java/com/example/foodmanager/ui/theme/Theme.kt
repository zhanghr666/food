package com.example.foodmanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BrandOrangeLight,
    onPrimary = Color(0xFF4A1B00),
    primaryContainer = BrandContainerDark,
    onPrimaryContainer = Color(0xFFFFDCC4),
    secondary = FreshGreenLight,
    onSecondary = Color(0xFF003824),
    secondaryContainer = FreshContainerDark,
    onSecondaryContainer = FreshContainerLight,
    tertiary = WarnAmberLight,
    onTertiary = Color(0xFF3F2A00),
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = Color(0xFF2C211C),
    onSurfaceVariant = Color(0xFFD8C2B8),
    error = DangerRedLight,
    onError = Color(0xFF690005),
    errorContainer = DangerContainerDark,
    onErrorContainer = DangerContainerLight,
)

private val LightColorScheme = lightColorScheme(
    primary = BrandOrange,
    onPrimary = Color.White,
    primaryContainer = BrandContainerLight,
    onPrimaryContainer = Color(0xFF3A1100),
    secondary = FreshGreen,
    onSecondary = Color.White,
    secondaryContainer = FreshContainerLight,
    onSecondaryContainer = Color(0xFF00200F),
    tertiary = WarnAmber,
    onTertiary = Color.White,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = Color(0xFFFFFBFF),
    onSurface = OnSurfaceLight,
    surfaceVariant = Color(0xFFF5EAE4),
    onSurfaceVariant = Color(0xFF53433C),
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
        content = content,
    )
}
