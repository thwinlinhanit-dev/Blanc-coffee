package com.example.ui.theme

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
    primary = CoffeePrimaryDark,
    onPrimary = Color(0xFF442012),
    primaryContainer = CoffeePrimaryContainerDark,
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = MatchaSecondaryDark,
    onSecondary = Color(0xFF0F3810),
    secondaryContainer = MatchaSecondaryContainerDark,
    onSecondaryContainer = Color(0xFFBCEFB8),
    tertiary = MacadamiaTertiaryDark,
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = MacadamiaTertiaryContainerDark,
    onTertiaryContainer = Color(0xFFFFDDB1),
    background = ShopBackgroundDark,
    onBackground = ShopTextPrimaryDark,
    surface = ShopSurfaceDark,
    onSurface = ShopTextPrimaryDark,
    surfaceVariant = ShopSurfaceVariantDark,
    onSurfaceVariant = ShopTextSecondaryDark,
    outline = ShopOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = CoffeePrimary,
    onPrimary = Color.White,
    primaryContainer = CoffeePrimaryContainer,
    onPrimaryContainer = CoffeeOnPrimaryContainer,
    secondary = MatchaSecondary,
    onSecondary = Color.White,
    secondaryContainer = MatchaSecondaryContainer,
    onSecondaryContainer = MatchaOnSecondaryContainer,
    tertiary = MacadamiaTertiary,
    onTertiary = Color.White,
    tertiaryContainer = MacadamiaTertiaryContainer,
    onTertiaryContainer = MacadamiaOnTertiaryContainer,
    background = ShopBackgroundLight,
    onBackground = ShopTextPrimaryLight,
    surface = ShopSurfaceLight,
    onSurface = ShopTextPrimaryLight,
    surfaceVariant = ShopSurfaceVariantLight,
    onSurfaceVariant = ShopTextSecondaryLight,
    outline = ShopOutlineLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep artisan theme consistent by default
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
        content = content
    )
}
