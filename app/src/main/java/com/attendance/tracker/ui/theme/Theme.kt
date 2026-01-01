package com.attendance.tracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.attendance.tracker.data.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    secondary = SecondaryDark,
    tertiary = InfoBlue,
    background = BackgroundDark,
    surface = SurfaceDark,
    error = ErrorDark,
    onPrimary = OnPrimaryDark,
    onSecondary = OnSecondaryDark,
    onBackground = OnBackgroundDark,
    onSurface = OnSurfaceDark,
    onError = OnErrorDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    secondary = SecondaryLight,
    tertiary = InfoBlue,
    background = BackgroundLight,
    surface = SurfaceLight,
    error = ErrorLight,
    onPrimary = OnPrimaryLight,
    onSecondary = OnSecondaryLight,
    onBackground = OnBackgroundLight,
    onSurface = OnSurfaceLight,
    onError = OnErrorLight
)

private val AmoledColorScheme = darkColorScheme(
    primary = AmoledPrimary,
    secondary = AmoledSecondary,
    tertiary = AmoledTertiary,
    background = AmoledBackground,
    surface = AmoledSurface,
    error = AmoledError,
    onPrimary = AmoledOnPrimary,
    onSecondary = AmoledOnSecondary,
    onBackground = AmoledOnBackground,
    onSurface = AmoledOnSurface,
    onError = AmoledOnError
)

@Composable
fun AttendanceTrackerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    customPrimaryColor: Color? = null,
    customSecondaryColor: Color? = null,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> darkTheme
    }

    val colorScheme = when {
        themeMode == ThemeMode.AMOLED -> {
            // Apply custom colors if provided
            if (customPrimaryColor != null || customSecondaryColor != null) {
                AmoledColorScheme.copy(
                    primary = customPrimaryColor ?: AmoledColorScheme.primary,
                    secondary = customSecondaryColor ?: AmoledColorScheme.secondary
                )
            } else {
                AmoledColorScheme
            }
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDarkTheme -> {
            // Apply custom colors if provided
            if (customPrimaryColor != null || customSecondaryColor != null) {
                DarkColorScheme.copy(
                    primary = customPrimaryColor ?: DarkColorScheme.primary,
                    secondary = customSecondaryColor ?: DarkColorScheme.secondary
                )
            } else {
                DarkColorScheme
            }
        }
        else -> {
            // Apply custom colors if provided
            if (customPrimaryColor != null || customSecondaryColor != null) {
                LightColorScheme.copy(
                    primary = customPrimaryColor ?: LightColorScheme.primary,
                    secondary = customSecondaryColor ?: LightColorScheme.secondary
                )
            } else {
                LightColorScheme
            }
        }
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
