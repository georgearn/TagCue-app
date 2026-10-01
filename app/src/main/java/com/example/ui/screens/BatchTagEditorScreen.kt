package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.example.model.CoverArtAction
import com.example.ui.components.AutoNumberDialog
import com.example.ui.components.ChangeCaseDialog
import com.example.ui.components.CoverArtPicker
import com.example.ui.components.FilenameToTagDialog
import com.example.ui.components.FindReplaceDialog
import com.example.ui.components.RenameFilesDialog
import com.example.ui.components.TriStateFieldInput
import com.example.ui.theme.StatTypography
import com.example.viewmodel.AudioTaggerViewModel
import com.example.viewmodel.TaggerUiState
import com.example.viewmodel.TagsStep

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchTagEditorScreen(
    uiState: TaggerUiState,
    viewModel: AudioTaggerViewModel,
    modifier: Modifier = Modifier
) {
    var showAutoNumberDialog by remember { mutableStateOf(false) }
    var showFilenameToTagDialog by remember { mutableStateOf(false) }
    var showRenameFilesDialog by remember { mutableStateOf(false) }
    var showFindReplaceDialog by remember { mutableStateOf(false) }
    var showChangeCaseDialog by remember { mutableStateOf(false) }

    val batchForm = uiState.batchForm

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.setTagsStep(TagsStep.LIBRARY) },
                modifier = Modifier.testTag("btn_back_to_library")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column {
                Text(
                    text = "Batch Tag Editor",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${uiState.selectedCount}",
                        style = StatTypography.metricBadge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " tracks selected for batch update",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Batch Operations Toolbar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "QUICK TOOLS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showAutoNumberDialog = true },
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("btn_tool_auto_number")
                    ) {
                        Icon(Icons.Default.Numbers, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto-Number", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = { showFilenameToTagDialog = true },
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("btn_tool_filename_to_tag")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tags from Filename", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = { showRenameFilesDialog = true },
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("btn_tool_rename_files")
                    ) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rename Files", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = { showFindReplaceDialog = true },
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("btn_tool_find_replace")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Find & Replace", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = { showChangeCaseDialog = true },
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("btn_tool_change_case")
                    ) {
                        Icon(Icons.Default.FormatSize, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Case Fixes", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = { viewModel.applyTrimSpaces() },
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("btn_tool_trim_spaces")
                    ) {
                        Icon(Icons.Default.SpaceBar, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Trim Spaces", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Form Fields (Scrollable)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cover Art
            CoverArtPicker(
                currentArtBytes = batchForm.newCoverArtBytes ?: uiState.selectedTracks.firstOrNull()?.pending?.albumArtBytes,
                isMixed = uiState.selectedTracks.map { it.pending.hasEmbeddedArt }.distinct().size > 1,
                isMarkedForRemoval = batchForm.coverArtAction == CoverArtAction.REMOVE,
                onArtSelected = { bytes ->
                    viewModel.updateBatchForm { it.copy(coverArtAction = CoverArtAction.SET_NEW, newCoverArtBytes = bytes) }
                },
                onRemoveArt = {
                    viewModel.updateBatchForm { it.copy(coverArtAction = CoverArtAction.REMOVE, newCoverArtBytes = null) }
                }
            )

            // Title
            TriStateFieldInput(
                label = "Title",
                currentCommonValue = uiState.computeFieldValue { it.title },
                fieldAction = batchForm.titleAction,
                onActionChange = { act -> viewModel.updateBatchForm { it.copy(titleAction = act) } },
                transformToString = { it },
                transformFromString = { it },
                placeholder = "Track Title"
            )

            // Artist
            TriStateFieldInput(
                label = "Artist",
                currentCommonValue = uiState.computeFieldValue { it.artist },
                fieldAction = batchForm.artistAction,
                onActionChange = { act -> viewModel.updateBatchForm { it.copy(artistAction = act) } },
                transformToString = { it },
                transformFromString = { it },
                placeholder = "Artist Name"
            )

            // Album Artist
            TriStateFieldInput(
                label = "Album Artist",
                currentCommonValue = uiState.computeFieldValue { it.albumArtist },
                fieldAction = batchForm.albumArtistAction,
                onActionChange = { act -> viewModel.updateBatchForm { it.copy(albumArtistAction = act) } },
                transformToString = { it },
                transformFromString = { it },
                placeholder = "Album Artist"
            )

            // Album
            TriStateFieldInput(
                label = "Album",
                currentCommonValue = uiState.computeFieldValue { it.album },
                fieldAction = batchForm.albumAction,
                onActionChange = { act -> viewModel.updateBatchForm { it.copy(albumAction = act) } },
                transformToString = { it },
                transformFromString = { it },
                placeholder = "Album Title"
            )

            // Year
            TriStateFieldInput(
                label = "Year",
                currentCommonValue = uiState.computeFieldValue { it.year },
                fieldAction = batchForm.yearAction,
                onActionChange = { act -> viewModel.updateBatchForm { it.copy(yearAction = act) } },
                transformToString = { it },
                transformFromString = { it },
                placeholder = "Release Year (e.g. 2024)",
                isNumeric = true
            )

            // Genre
            TriStateFieldInput(
                label = "Genre",
                currentCommonValue = uiState.computeFieldValue { it.genre },
                fieldAction = batchForm.genreAction,
                onActionChange = { act -> viewModel.updateBatchForm { it.copy(genreAction = act) } },
                transformToString = { it },
                transformFromString = { it },
                placeholder = "Genre"
            )

            // Track Number
            TriStateFieldInput(
                label = "Track #",
                currentCommonValue = uiState.computeFieldInt { it.trackNumber },
                fieldAction = batchForm.trackNumAction,
                onActionChange = { act -> viewModel.updateBatchForm { it.copy(trackNumAction = act) } },
                transformToString = { it?.toString() ?: "" },
                transformFromString = { it.toIntOrNull() },
                placeholder = "Track Number",
                isNumeric = true
            )

            // Total Tracks
            TriStateFieldInput(
                label = "Total Tracks",
                currentCommonValue = uiState.computeFieldInt { it.totalTracks },
                fieldAction = batchForm.totalTracksAction,
                onActionChange = { act -> viewModel.updateBatchForm { it.copy(totalTracksAction = act) } },
                transformToString = { it?.toString() ?: "" },
                transformFromString = { it.toIntOrNull() },
                placeholder = "Total Tracks in Album",
                isNumeric = true
            )

            // Disc Number
            TriStateFieldInput(
                label = "Disc #",
                currentCommonValue = uiState.computeFieldInt { it.discNumber },
                fieldAction = batchForm.discNumAction,
                onActionChange = { act -> viewModel.updateBatchForm { it.copy(discNumAction = act) } },
                transformToString = { it?.toString() ?: "" },
                transformFromString = { it.toIntOrNull() },
                placeholder = "Disc Number",
                isNumeric = true
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bottom Sticky Action Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        viewModel.commitBatchFormToPending()
                        viewModel.setTagsStep(TagsStep.REVIEW_CHANGES)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_review_changes"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Review Changes", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    // Dialogs
    if (showAutoNumberDialog) {
        AutoNumberDialog(
            selectedCount = uiState.selectedCount,
            onDismiss = { showAutoNumberDialog = false },
            onApply = { start, total -> viewModel.applyAutoNumber(start, total) }
        )
    }

    if (showFilenameToTagDialog) {
        FilenameToTagDialog(
            onDismiss = { showFilenameToTagDialog = false },
            onApply = { pattern -> viewModel.applyFilenameToTags(pattern) }
        )
    }

    if (showRenameFilesDialog) {
        RenameFilesDialog(
            onDismiss = { showRenameFilesDialog = false },
            onApply = { pattern -> viewModel.applyRenameFromTags(pattern) }
        )
    }

    if (showFindReplaceDialog) {
        FindReplaceDialog(
            onDismiss = { showFindReplaceDialog = false },
            onApply = { field, search, replace, matchCase, useRegex ->
                viewModel.applyFindAndReplace(field, search, replace, matchCase, useRegex)
            }
        )
    }

    if (showChangeCaseDialog) {
        ChangeCaseDialog(
            onDismiss = { showChangeCaseDialog = false },
            onApply = { field, caseOpt -> viewModel.applyCaseConversion(field, caseOpt) }
        )
    }
}
