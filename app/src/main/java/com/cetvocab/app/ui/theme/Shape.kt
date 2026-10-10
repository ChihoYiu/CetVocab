package com.cetvocab.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Rounded, calm geometry. Cards use 16-24dp corners; interactive controls use pill or
 * 12-16dp corners so the touch target shape stays obvious at any font scale.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Corner radius used by the app's own card container. */
val CardCorner = 20.dp

/** Corner radius of primary actions. */
val ButtonCorner = 16.dp

/** Minimum touch target enforced by Material 3; kept as a named constant for tests. */
val MinTouchTarget = 48.dp

/** Shared spacing scale — 4dp grid, generous whitespace. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}
