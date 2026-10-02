package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.model.FieldAction
import com.example.ui.theme.status

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
    var editValueText by remember(fieldAction, currentCommonValue) {
        mutableStateOf(
            if (fieldAction is FieldAction.SetValue) transformToString(fieldAction.value)
            else if (currentCommonValue != "‹mixed›") currentCommonValue
            else ""
        )
    }

    // 8-point grid padding: 16dp. Rounded corners: 12dp.
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
                    is FieldAction.Keep -> MaterialTheme.colorScheme.outline
                    is FieldAction.SetValue -> MaterialTheme.colorScheme.primary
                    is FieldAction.Clear -> MaterialTheme.colorScheme.error
                },
                shape = MaterialTheme.shapes.medium
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Label: 14sp SemiBold (labelLarge)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Tri-state selector pills: Keep | Set | Clear (Height: 32dp, font: 12sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { onActionChange(FieldAction.Keep) },
                    label = { Text("Keep", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (fieldAction is FieldAction.Keep) MaterialTheme.colorScheme.surface else Color.Transparent,
                        labelColor = if (fieldAction is FieldAction.Keep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = null,
                    modifier = Modifier.height(32.dp)
                )

                AssistChip(
                    onClick = {
                        val v = transformFromString(editValueText)
                        onActionChange(FieldAction.SetValue(v))
                    },
                    label = { Text("Set", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (fieldAction is FieldAction.SetValue) MaterialTheme.status.selectedStrong else Color.Transparent,
                        labelColor = if (fieldAction is FieldAction.SetValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = null,
                    modifier = Modifier.height(32.dp)
                )

                AssistChip(
                    onClick = { onActionChange(FieldAction.Clear) },
                    label = { Text("Clear", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (fieldAction is FieldAction.Clear) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else Color.Transparent,
                        labelColor = if (fieldAction is FieldAction.Clear) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = null,
                    modifier = Modifier.height(32.dp)
                )
            }
        }

        when (fieldAction) {
            is FieldAction.Keep -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (currentCommonValue == "‹mixed›") {
                        Text(
                            text = "‹mixed› (different across selected files)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (currentCommonValue.isBlank()) {
                        Text(
                            text = "(empty across all selected files)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = currentCommonValue,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
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
                        .testTag("input_${label.lowercase().replace(" ", "_")}")
                )
            }

            is FieldAction.Clear -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Field will be cleared / erased across all selected tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
