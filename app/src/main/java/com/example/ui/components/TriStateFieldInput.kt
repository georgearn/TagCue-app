package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.model.FieldAction

@Composable
fun <T> TriStateFieldInput(
    label: String,
    currentCommonValue: String, // Value or "‹mixed›"
    fieldAction: FieldAction<T>,
    onActionChange: (FieldAction<T>) -> Unit,
    transformToString: (T) -> String,
    transformFromString: (String) -> T,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isNumeric: Boolean = false
) {
    val isMixed = currentCommonValue == "‹mixed›"
    var editValueText by remember(fieldAction, currentCommonValue) {
        mutableStateOf(
            if (fieldAction is FieldAction.SetValue) transformToString(fieldAction.value)
            else if (!isMixed) currentCommonValue
            else ""
        )
    }
    val focusRequester = remember { FocusRequester() }
    val editing = fieldAction is FieldAction.SetValue
    LaunchedEffect(editing) {
        if (editing) focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                when (fieldAction) {
                    is FieldAction.SetValue -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    is FieldAction.Clear -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    is FieldAction.Keep -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .border(
                width = if (fieldAction !is FieldAction.Keep) 1.5.dp else 1.dp,
                color = when (fieldAction) {
                    is FieldAction.Keep -> MaterialTheme.colorScheme.outlineVariant
                    is FieldAction.SetValue -> MaterialTheme.colorScheme.primary
                    is FieldAction.Clear -> MaterialTheme.colorScheme.error
                },
                shape = MaterialTheme.shapes.medium
            )
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row {
                when (fieldAction) {
                    is FieldAction.Keep -> IconButton(
                        onClick = { onActionChange(FieldAction.SetValue(transformFromString(editValueText))) },
                        modifier = Modifier.testTag("edit_${label.lowercase().replace(" ", "_")}")
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit $label",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    is FieldAction.SetValue -> {
                        IconButton(onClick = { onActionChange(FieldAction.Clear) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear $label on all selected tracks",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { onActionChange(FieldAction.Keep) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel editing $label",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    is FieldAction.Clear -> IconButton(onClick = { onActionChange(FieldAction.Keep) }) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = "Undo clearing $label",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.padding(end = 12.dp)) {
            when (fieldAction) {
                is FieldAction.Keep -> {
                    when {
                        isMixed -> Text(
                            text = "‹mixed› (different across selected files)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        currentCommonValue.isBlank() -> Text(
                            text = "(empty across all selected files)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> Text(
                            text = currentCommonValue,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                is FieldAction.SetValue -> {
                    OutlinedTextField(
                        value = editValueText,
                        onValueChange = {
                            val sanitized = if (isNumeric) it.filter { ch -> ch.isDigit() } else it
                            editValueText = sanitized
                            onActionChange(FieldAction.SetValue(transformFromString(sanitized)))
                        },
                        placeholder = {
                            Text(
                                text = placeholder.ifBlank { "Enter new $label" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("input_${label.lowercase().replace(" ", "_")}")
                    )
                }

                is FieldAction.Clear -> Text(
                    text = "Will be cleared on all selected tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
