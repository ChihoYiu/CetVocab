// Root build file. Plugins are declared here (without applying them) so that the
// version catalog stays the single source of truth; modules opt in with `alias(...)`.
//
// Note: since AGP 9.0 Kotlin support is built into the Android plugin, so
// `org.jetbrains.kotlin.android` is intentionally NOT applied anywhere in this build.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
