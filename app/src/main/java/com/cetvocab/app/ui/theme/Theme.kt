package com.cetvocab.app.ui.theme

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.cetvocab.app.data.settings.ThemeMode

/** Extra, project specific design tokens that Material 3 has no slot for. */
@Immutable
data class AppColors(
    val successGreen: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val mutedText: Color,
    val hairline: Color,
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        successGreen = SuccessGreen,
        successContainer = SuccessContainer,
        warning = ApricotWarning,
        warningContainer = ApricotContainer,
        mutedText = InkMuted,
        hairline = Hairline,
    )
}

/**
 * Motion tokens. When the user disables animations in settings — or the system asks for
 * reduced motion via `ValueAnimator.areAnimatorsEnabled()` — every animation duration
 * collapses to zero, so the UI stays usable without ever "animating" anything.
 */
@Immutable
data class AppMotion(val enabled: Boolean) {
    fun durationMillis(normal: Int): Int = if (enabled) normal else 0
}

val LocalAppMotion = staticCompositionLocalOf { AppMotion(enabled = true) }

/**
 * Whether the *resolved* theme is dark.
 *
 * The value is resolved once here from [ThemeMode] + the system setting and shared, so screens
 * (and the system-bar effect) never have to re-derive it and can never disagree with the
 * colours that are actually on screen.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** True when animations should play. */
@Composable
fun animationsEnabled(userPreference: Boolean): Boolean {
    val context = LocalContext.current
    val systemAllows = remember(context) { ValueAnimator.areAnimatorsEnabled() }
    return userPreference && systemAllows
}

/**
 * Application theme.
 *
 * @param themeMode user preference (light / dark / follow system)
 * @param dynamicColor opt-in Material You palette. Only offered on Android 12+ and only
 *   when the user turns it on; the mint brand palette is the default on every version so
 *   the app looks the same from Android 8.0 upwards.
 * @param animations user preference; combined with the system "remove animations" flag.
 */
@Composable
fun CetVocabTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    animations: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    val appColors = if (darkTheme) {
        AppColors(
            successGreen = DarkSuccessGreen,
            successContainer = DarkSuccessContainer,
            warning = DarkWarning,
            warningContainer = DarkWarningContainer,
            mutedText = DarkOnSurfaceMuted,
            hairline = DarkHairline,
        )
    } else {
        AppColors(
            successGreen = SuccessGreen,
            successContainer = SuccessContainer,
            warning = ApricotWarning,
            warningContainer = ApricotContainer,
            mutedText = InkMuted,
            hairline = Hairline,
        )
    }

    val motion = AppMotion(enabled = animationsEnabled(animations))

    // Status/navigation bar icons follow the *in-app* theme, not the system night-mode flag.
    // Without this, an in-app dark theme on a light-mode device draws dark icons on a dark bar.
    ApplySystemBarAppearance(darkTheme = darkTheme)

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalAppMotion provides motion,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Convenience accessor: `AppTheme.colors.successGreen`. */
object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current

    val motion: AppMotion
        @Composable get() = LocalAppMotion.current
}
