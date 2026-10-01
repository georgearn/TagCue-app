package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.CaseOption
import com.example.model.TargetField
import com.example.ui.theme.status
import com.example.ui.theme.StatTypography

@Composable
fun FindReplaceDialog(
    onDismiss: () -> Unit,
    onApply: (field: TargetField, search: String, replace: String, matchCase: Boolean, useRegex: Boolean) -> Unit
) {
    var selectedField by remember { mutableStateOf(TargetField.TITLE) }
    var fieldMenuOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var useRegex by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find & Replace in Tags", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Field Selector Dropdown
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { fieldMenuOpen = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Target Field:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selectedField.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }

                    DropdownMenu(
                        expanded = fieldMenuOpen,
                        onDismissRequest = { fieldMenuOpen = false }
                    ) {
                        TargetField.values().filter { it != TargetField.TRACK_NUMBER }.forEach { field ->
                            DropdownMenuItem(
                                text = { Text(field.displayName, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    selectedField = field
                                    fieldMenuOpen = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Search text / pattern", style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_search_text")
                )

                OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    label = { Text("Replacement text", style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_replace_text")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Case Sensitive", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = matchCase, onCheckedChange = { matchCase = it })
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use Regular Expression", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = useRegex, onCheckedChange = { useRegex = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(selectedField, searchText, replaceText, matchCase, useRegex)
                    onDismiss()
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.testTag("btn_confirm_find_replace")
            ) {
                Text("Apply to Selected", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
        }
    )
}

@Composable
fun ChangeCaseDialog(
    onDismiss: () -> Unit,
    onApply: (field: TargetField, caseOption: CaseOption) -> Unit
) {
    var selectedField by remember { mutableStateOf(TargetField.TITLE) }
    var selectedCase by remember { mutableStateOf(CaseOption.TITLE_CASE) }
    var fieldMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Letter Case", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Field selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { fieldMenuOpen = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Target Tag Field:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selectedField.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }

                    DropdownMenu(
                        expanded = fieldMenuOpen,
                        onDismissRequest = { fieldMenuOpen = false }
                    ) {
                        TargetField.values().filter { it != TargetField.TRACK_NUMBER && it != TargetField.YEAR }.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.displayName, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    selectedField = f
                                    fieldMenuOpen = false
                                }
                            )
                        }
                    }
                }

                Text("Select Target Case:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                CaseOption.values().filter { it != CaseOption.ORIGINAL }.forEach { opt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCase = opt }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedCase == opt,
                            onClick = { selectedCase = opt }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(opt.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(selectedField, selectedCase)
                    onDismiss()
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.testTag("btn_confirm_change_case")
            ) {
                Text("Convert", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
        }
    )
}

@Composable
fun AutoNumberDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onApply: (startNumber: Int, setTotalTracks: Boolean) -> Unit
) {
    var startNumberStr by remember { mutableStateOf("1") }
    var setTotalTracks by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-Number Tracks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Numbering ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$selectedCount",
                        style = StatTypography.metricBadge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " selected tracks in current playlist order.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = startNumberStr,
                    onValueChange = { startNumberStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Start Number", style = MaterialTheme.typography.bodySmall) },
                    textStyle = StatTypography.metricBody,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_start_track_number")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Set total tracks to $selectedCount",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Switch(checked = setTotalTracks, onCheckedChange = { setTotalTracks = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val start = startNumberStr.toIntOrNull() ?: 1
                    onApply(start, setTotalTracks)
                    onDismiss()
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.testTag("btn_confirm_auto_number")
            ) {
                Text("Apply Numbering", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
        }
    )
}

@Composable
fun FilenameToTagDialog(
    onDismiss: () -> Unit,
    onApply: (pattern: String) -> Unit
) {
    var pattern by remember { mutableStateOf("%n - %t") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extract Tags from Filename", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Parse track title, number, and artist from filename using format tokens:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Extraction Pattern", style = MaterialTheme.typography.bodySmall) },
                    textStyle = StatTypography.metricBody,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_filename_pattern")
                )

                // Quick presets
                Text("Common Presets:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val presets = listOf(
                    "%n - %t" to "01 - Track Title",
                    "%a - %t" to "Artist - Title",
                    "%n. %t" to "01. Title",
                    "%a - %b - %n - %t" to "Full Hierarchy"
                )
                presets.forEach { (p, label) ->
                    AssistChip(
                        onClick = { pattern = p },
                        label = { Text("$p  ($label)", style = StatTypography.metricBadge) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.status.selected)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Tokens: %n = Track #, %t = Title, %a = Artist, %b = Album, %y = Year",
                        style = StatTypography.metricBadge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(pattern)
                    onDismiss()
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.testTag("btn_confirm_filename_to_tag")
            ) {
                Text("Extract & Apply", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
        }
    )
}

@Composable
fun RenameFilesDialog(
    onDismiss: () -> Unit,
    onApply: (pattern: String) -> Unit
) {
    var pattern by remember { mutableStateOf("%n - %t") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Files from Tags", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Build new file names from each file's tags. The extension is kept. Names are checked in the review step and written with the tags.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("File Name Pattern", style = MaterialTheme.typography.bodySmall) },
                    textStyle = StatTypography.metricBody,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_rename_pattern")
                )

                Text("Common Presets:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val presets = listOf(
                    "%n - %t" to "01 - Title",
                    "%n - %a - %t" to "01 - Artist - Title",
                    "%a - %t" to "Artist - Title",
                    "%y - %b - %n - %t" to "Year - Album - 01 - Title"
                )
                presets.forEach { (p, label) ->
                    AssistChip(
                        onClick = { pattern = p },
                        label = { Text("$p  ($label)", style = StatTypography.metricBadge) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.status.selected)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Tokens: %n = Track #, %t = Title, %a = Artist, %b = Album, %y = Year",
                        style = StatTypography.metricBadge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(pattern) },
                enabled = pattern.isNotBlank(),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.testTag("btn_confirm_rename")
            ) {
                Text("Rename & Apply", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
        }
    )
}
