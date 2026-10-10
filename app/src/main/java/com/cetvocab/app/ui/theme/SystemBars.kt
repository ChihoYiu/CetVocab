package com.cetvocab.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * How the system bars (status bar + gesture/three-button bar) should be tinted.
 *
 * The value describes the **icons**, not the background: with edge-to-edge enabled the bar
 * background is transparent and the app's own background colour shows through, so only the
 * icon contrast has to be driven by the theme.
 */
@Immutable
enum class SystemBarIconAppearance {
    /** Dark icons for a light app background. */
    DARK_ICONS,

    /** Light icons for a dark app background. */
    LIGHT_ICONS,
    ;

    companion object {
        fun forDarkTheme(darkTheme: Boolean): SystemBarIconAppearance =
            if (darkTheme) LIGHT_ICONS else DARK_ICONS
    }
}

/**
 * Applies the icon appearance to a window. Abstracted so the mapping from theme to system bars
 * is unit testable without a real window.
 */
fun interface SystemBarStyler {
    fun apply(appearance: SystemBarIconAppearance)
}

/** Composition-local styler; tests replace it with a recorder, the app uses the window one. */
val LocalSystemBarStyler = staticCompositionLocalOf<SystemBarStyler?> { null }

/**
 * Android implementation.
 *
 * Why this exists at all: `enableEdgeToEdge()` is called once in `Activity.onCreate` and its
 * default `SystemBarStyle.auto` derives the icon tint from the **system** night-mode flag. The
 * in-app theme (浅色 / 深色 / 跟随系统) is a separate, DataStore-backed choice, so the two can
 * disagree — and the platform never re-evaluates the bars when the in-app theme changes. This
 * effect re-applies the correct appearance on every theme change (and after every process
 * restart), which is the only reliable way to keep the bars readable on all four combinations.
 *
 * `WindowInsetsControllerCompat` is used instead of the platform `WindowInsetsController` so the
 * exact same code path works from API 26 (our minSdk) upwards: below API 30 it maps onto the
 * light-status-bar/light-navigation-bar system UI flags.
 */
class WindowSystemBarStyler(private val activity: Activity) : SystemBarStyler {

    override fun apply(appearance: SystemBarIconAppearance) {
        val window = activity.window ?: return
        val controller = WindowCompat.getInsetsController(window, window.decorView) ?: return
        val lightIcons = appearance == SystemBarIconAppearance.DARK_ICONS
        controller.isAppearanceLightStatusBars = lightIcons
        controller.isAppearanceLightNavigationBars = lightIcons
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // From Android 10 the system may add its own contrast scrim; keep it off so the bar
            // background is exactly the app background colour.
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }
}

/**
 * Keeps the system bars in sync with the current theme.
 *
 * Call it from inside the theme so that the app shell, previews and tests all get the same
 * behaviour; it is a no-op in edit mode (previews) and when there is no Activity (for example
 * inside a plain `createComposeRule()` test), where an injected [SystemBarStyler] is used
 * instead.
 */
@Composable
fun ApplySystemBarAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    val injected = LocalSystemBarStyler.current
    val appearance = SystemBarIconAppearance.forDarkTheme(darkTheme)

    if (view.isInEditMode) return

    SideEffect {
        val styler = injected ?: view.context.findActivity()?.let(::WindowSystemBarStyler)
        styler?.apply(appearance)
    }
}

/** Walks the context chain to the hosting Activity (Compose wraps it in ContextWrappers). */
internal fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
