package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CueEncoding
import com.example.model.CueSheet
import com.example.model.CueTrack
import com.example.ui.components.EmptyState
import com.example.ui.theme.StatTypography
import com.example.viewmodel.AudioTaggerViewModel
import com.example.viewmodel.SplitStep
import com.example.viewmodel.TaggerUiState

@Composable
fun SplitHomeScreen(
    uiState: TaggerUiState,
    viewModel: AudioTaggerViewModel,
    modifier: Modifier = Modifier
) {
    val cuePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadCueSheet(uri)
        }
    }

    val flacPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.pairFlacFile(uri)
        }
    }

    var encodingMenuOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        val cueSheet = uiState.cueSheet

        if (cueSheet == null) {
            // Empty State
            EmptyState(
                icon = Icons.Default.CallSplit,
                title = "Split FLAC by CUE Sheet",
                description = "Select a .cue sheet from local storage. The app parses tracks immediately, fixes non-Unicode/Cyrillic encodings, and splits lossless FLAC tracks.",
                actionLabel = "Select .cue File",
                actionIcon = Icons.Default.FileOpen,
                actionTestTag = "btn_empty_pick_cue",
                onAction = { cuePicker.launch(arrayOf("*/*")) }
            )
        } else {
            // CUE sheet loaded
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                item {
                    Column {
                        Text(
                            text = "FLAC CUE Splitter",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Pick .cue sheet + FLAC image • Lossless audio split",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // CUE Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = cueSheet.title.ifBlank { "CUE Sheet" },
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                OutlinedButton(
                                    onClick = { cuePicker.launch(arrayOf("*/*")) },
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("btn_pick_cue_sheet"),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Text("Change CUE", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            if (cueSheet.performer.isNotBlank() || cueSheet.date.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = listOf(cueSheet.performer, cueSheet.date).filter { it.isNotBlank() }.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Encoding Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Encoding: ${uiState.cueEncoding.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Box {
                                    OutlinedButton(
                                        onClick = { encodingMenuOpen = true },
                                        modifier = Modifier.height(40.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text("Change", style = MaterialTheme.typography.labelSmall)
                                    }

                                    DropdownMenu(
                                        expanded = encodingMenuOpen,
                                        onDismissRequest = { encodingMenuOpen = false }
                                    ) {
                                        CueEncoding.values().forEach { enc ->
                                            DropdownMenuItem(
                                                text = { Text(enc.displayName) },
                                                onClick = {
                                                    encodingMenuOpen = false
                                                    viewModel.reParseCueWithEncoding(enc)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Paired FLAC Card
                item {
                    val pairedFlac = uiState.pairedFlacMetadata
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    tint = if (pairedFlac != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = pairedFlac?.fileName ?: "No FLAC image paired",
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (pairedFlac != null) "${pairedFlac.format} • ${pairedFlac.formattedDuration}" else "Select FLAC audio image",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = { flacPicker.launch(arrayOf("audio/*", "application/octet-stream", "*/*")) },
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("btn_select_flac_image"),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (pairedFlac != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(if (pairedFlac != null) "Change" else "Select FLAC", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Track list header
                item {
                    val selectedCount = cueSheet.tracks.count { it.isSelectedForSplit }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tracks ($selectedCount/${cueSheet.tracks.size}):",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap fields to edit inline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Track items
                items(cueSheet.tracks, key = { it.number }) { track ->
                    InlineCueTrackCard(
                        track = track,
                        onToggle = { viewModel.toggleCueTrackSplitSelection(track.number) },
                        onUpdate = { title, performer ->
                            viewModel.updateCueTrackInline(track.number, title, performer)
                        }
                    )
                }
            }

            // Bottom Sticky Bar
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    val canContinue = uiState.pairedFlacUri != null && cueSheet.tracks.any { it.isSelectedForSplit }
                    Button(
                        onClick = { viewModel.setSplitStep(SplitStep.OUTPUT_FORMAT) },
                        enabled = canContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_continue_to_output_format"),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Configure Output Format", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun InlineCueTrackCard(
    track: CueTrack,
    onToggle: () -> Unit,
    onUpdate: (title: String, performer: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var titleText by remember(track.title) { mutableStateOf(track.title) }
    var performerText by remember(track.performer) { mutableStateOf(track.performer) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = track.isSelectedForSplit,
                onCheckedChange = { onToggle() },
                modifier = Modifier
                    .size(24.dp)
                    .testTag("cb_cue_track_${track.number}")
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Track %02d".format(track.number),
                        style = StatTypography.metricBadge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = track.index01,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = titleText,
                    onValueChange = {
                        titleText = it
                        onUpdate(it, performerText)
                    },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_cue_title_${track.number}")
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = performerText,
                    onValueChange = {
                        performerText = it
                        onUpdate(titleText, it)
                    },
                    label = { Text("Performer / Artist") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_cue_performer_${track.number}")
                )
            }
        }
    }
}
