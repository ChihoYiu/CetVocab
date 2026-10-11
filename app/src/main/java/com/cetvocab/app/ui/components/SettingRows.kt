package com.cetvocab.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.Spacing

/**
 * One switch row inside a settings card.
 *
 * Why this exists instead of several hand-written `Row`s: the settings page had two different
 * structures — some rows used `SpaceBetween` with an unweighted `Text`, others a weighted `Column`
 * — so a long label (or a 200% font scale) squeezed the switch on some rows but not others, and the
 * switch column did not sit on the same vertical axis. Here the label column always takes
 * `weight(1f)` and the switch always keeps its intrinsic width, so **every switch in every card
 * lands on exactly the same right-aligned axis**, and a long label wraps inside its own column
 * instead of pushing the control.
 *
 * @param label the setting's name
 * @param checked current value
 * @param onCheckedChange called with the new value
 * @param description optional second line explaining what the setting does
 * @param testTag optional tag for UI tests
 */
@Composable
fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    testTag: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        // A fixed gap instead of SpaceBetween: the two ends are then defined by the layout rather
        // than by how wide the text happens to be.
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.mutedText,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
        )
    }
}

/**
 * A row of tiles that all end up the same height.
 *
 * `Row` sizes each child to its own content, so two tiles where only one has a caption end up with
 * different heights — exactly the "色块不齐" the profile page showed. Sizing the row to its minimum
 * intrinsic height and letting each tile `fillMaxHeight()` makes them match, without inventing
 * filler content just to pad the shorter one.
 *
 * Usage: `EqualHeightTileRow { StatCard(..., modifier = Modifier.weight(1f).fillMaxHeight()) }`
 */
@Composable
fun EqualHeightTileRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Spacing.md),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.Top,
        content = content,
    )
}
