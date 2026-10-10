package com.cetvocab.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.cetvocab.app.ui.theme.MinTouchTarget
import com.cetvocab.app.ui.theme.Spacing

/**
 * The two input affordances shared by the search and library screens.
 */

/**
 * Rounded single-line search field with a leading magnifier and a clear button.
 *
 * @param onClear invoked after the query is cleared; defaults to clearing through
 *   [onQueryChange] so the caller can keep state in one place.
 */
@Composable
fun WordSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索单词或释义",
    onClear: () -> Unit = { onQueryChange("") },
) {
    val keyboard = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.mutedText,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                // Decorative: the placeholder already says what the field is for.
                contentDescription = null,
                tint = AppTheme.colors.mutedText,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onClear()
                        keyboard?.hide()
                    },
                    // An explicit square keeps the clear affordance at the 48dp minimum
                    // touch target regardless of how small the icon glyph is.
                    modifier = Modifier
                        .size(MinTouchTarget)
                        .semantics { contentDescription = "清除搜索" },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = AppTheme.colors.mutedText,
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = AppTheme.colors.hairline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/**
 * Single-selection chip row, horizontally scrollable by default.
 *
 * Every chip exposes [Role.RadioButton], the `Selected` state and a "已选择"/"未选择"
 * state description, so a screen reader announces it as a radio option and tests can
 * assert on semantics instead of on colours.
 */
@Composable
fun <T> FilterChipGroup(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    Row(
        modifier = if (scrollable) {
            modifier.horizontalScroll(rememberScrollState())
        } else {
            modifier
        },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                modifier = Modifier.semantics {
                    role = Role.RadioButton
                    // `selected`/`stateDescription` cannot both be set here: `selected` is a
                    // helper *function* on the semantics receiver, and FilterChip already
                    // publishes its own selection state through the `selected` parameter.
                    stateDescription = if (isSelected) "已选择" else "未选择"
                },
                shape = MaterialTheme.shapes.extraLarge,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        AppTheme.colors.hairline
                    },
                ),
            )
        }
    }
}

private enum class FakeFilter { ALL, NEW, MASTERED }

@Preview(showBackground = true)
@Composable
private fun WordSearchBarFilledPreview() {
    CetVocabTheme {
        Surface {
            WordSearchBar(
                query = "apple",
                onQueryChange = {},
                modifier = Modifier.padding(Spacing.lg),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WordSearchBarEmptyPreview() {
    CetVocabTheme {
        Surface {
            WordSearchBar(
                query = "",
                onQueryChange = {},
                modifier = Modifier.padding(Spacing.lg),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FilterChipGroupPreview() {
    CetVocabTheme {
        Surface {
            FilterChipGroup(
                options = FakeFilter.entries.toList(),
                selected = FakeFilter.NEW,
                onSelect = {},
                label = { option ->
                    when (option) {
                        FakeFilter.ALL -> "全部"
                        FakeFilter.NEW -> "未掌握"
                        FakeFilter.MASTERED -> "已掌握"
                    }
                },
                modifier = Modifier.padding(Spacing.lg),
            )
        }
    }
}
