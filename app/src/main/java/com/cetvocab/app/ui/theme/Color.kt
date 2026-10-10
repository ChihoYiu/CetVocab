package com.cetvocab.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Brand palette — 清新薄荷绿 + 暖白.
 *
 * The brand colours are plain [Color] constants (no `@Composable` access, no dynamic
 * colour dependency) so that Android 8.0 through the newest release render identically.
 */

// --- Core brand -------------------------------------------------------------------------
/** 主色：薄荷绿 */
val Mint = Color(0xFF63C7A5)

/** 主色深色变体：按钮按下、重点文字、高对比场景。 */
val MintDark = Color(0xFF2F9E7C)

/** 更深的薄荷绿，用于深色模式下的主色。 */
val MintLight = Color(0xFF8FDCC0)

/** 辅助色：浅薄荷绿。 */
val MintSoft = Color(0xFFE7F7F0)

/** 页面背景：暖白色。 */
val WarmWhite = Color(0xFFF8FAF8)

/** 卡片背景。 */
val CardWhite = Color(0xFFFFFFFF)

/** 主文字：深灰黑。 */
val Ink = Color(0xFF26332F)

/** 次级文字：柔和灰色。 */
val InkMuted = Color(0xFF89948F)

/** 分隔线与细边框。 */
val Hairline = Color(0xFFE4EBE6)

// --- Semantic ---------------------------------------------------------------------------
/** 成功状态：清新绿色。 */
val SuccessGreen = Color(0xFF3FAE7F)
val SuccessContainer = Color(0xFFE4F6EC)

/** 错题提示：低饱和珊瑚红。 */
val CoralError = Color(0xFFD9705C)
val CoralContainer = Color(0xFFFBE9E5)

/** 重点提醒：柔和杏黄色。 */
val ApricotWarning = Color(0xFFE0A34E)
val ApricotContainer = Color(0xFFFDF2DF)

// --- Dark scheme ------------------------------------------------------------------------
val DarkBackground = Color(0xFF121815)
val DarkSurface = Color(0xFF1A231F)
val DarkSurfaceVariant = Color(0xFF243029)
val DarkOnSurface = Color(0xFFE6EFE9)
val DarkOnSurfaceMuted = Color(0xFF9BAAA3)
val DarkHairline = Color(0xFF2E3A34)

/** Semantic colours tuned for the dark surface. */
val DarkSuccessGreen = Color(0xFF7ED3AC)
val DarkSuccessContainer = Color(0xFF21402F)
val DarkWarning = Color(0xFFF0C079)
val DarkWarningContainer = Color(0xFF4A3A17)

/** Light scheme used on every Android version unless the user opts into dynamic colour. */
val LightColors: ColorScheme = lightColorScheme(
    primary = MintDark,
    onPrimary = Color.White,
    primaryContainer = MintSoft,
    onPrimaryContainer = Ink,
    secondary = SuccessGreen,
    onSecondary = Color.White,
    secondaryContainer = SuccessContainer,
    onSecondaryContainer = Ink,
    tertiary = ApricotWarning,
    onTertiary = Color.White,
    tertiaryContainer = ApricotContainer,
    onTertiaryContainer = Ink,
    error = CoralError,
    onError = Color.White,
    errorContainer = CoralContainer,
    onErrorContainer = Ink,
    background = WarmWhite,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = MintSoft,
    onSurfaceVariant = InkMuted,
    outline = Hairline,
    outlineVariant = Hairline,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = WarmWhite,
    surfaceContainer = Color(0xFFF2F6F3),
    surfaceContainerHigh = Color(0xFFEDF3EF),
    surfaceContainerHighest = Color(0xFFE7EFEA),
    inverseSurface = Ink,
    inverseOnSurface = WarmWhite,
)

/** Dark scheme — the structure is complete so the app stays readable in dark mode. */
val DarkColors: ColorScheme = darkColorScheme(
    primary = MintLight,
    onPrimary = Color(0xFF05372A),
    primaryContainer = Color(0xFF1E4A3C),
    onPrimaryContainer = Color(0xFFD3F5E7),
    secondary = Color(0xFF7ED3AC),
    onSecondary = Color(0xFF04301F),
    secondaryContainer = Color(0xFF21402F),
    onSecondaryContainer = Color(0xFFD3F5E7),
    tertiary = Color(0xFFF0C079),
    onTertiary = Color(0xFF3B2A05),
    tertiaryContainer = Color(0xFF4A3A17),
    onTertiaryContainer = Color(0xFFFBE7C2),
    error = Color(0xFFE9A091),
    onError = Color(0xFF3D150D),
    errorContainer = Color(0xFF4E2A22),
    onErrorContainer = Color(0xFFF8DAD2),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceMuted,
    outline = DarkHairline,
    outlineVariant = DarkHairline,
    surfaceContainerLowest = Color(0xFF0E1310),
    surfaceContainerLow = Color(0xFF161D1A),
    surfaceContainer = Color(0xFF1C2521),
    surfaceContainerHigh = Color(0xFF222C27),
    surfaceContainerHighest = Color(0xFF28332D),
    inverseSurface = DarkOnSurface,
    inverseOnSurface = DarkBackground,
)
