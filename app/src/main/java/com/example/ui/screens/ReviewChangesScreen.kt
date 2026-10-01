package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.model.EditableTrackState
import com.example.ui.theme.StatTypography
import com.example.ui.theme.status
import com.example.viewmodel.AudioTaggerViewModel
import com.example.viewmodel.TaggerUiState
import com.example.viewmodel.TagsStep

data class FieldDiff(
    val fieldName: String,
    val oldValue: String,
    val newValue: String
)

@Composable
fun ReviewChangesScreen(
    uiState: TaggerUiState,
    viewModel: AudioTaggerViewModel,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

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
                onClick = { viewModel.setTagsStep(TagsStep.BATCH_EDITOR) },
                modifier = Modifier.testTag("btn_back_to_editor")
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
                    text = "Review Changes",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${uiState.modifiedSelectedTracks.size}",
                        style = StatTypography.metricBadge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " files to update • Inspect diffs before saving",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tracks Diff List
        if (uiState.modifiedSelectedTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No changes pending for the selected tracks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(uiState.modifiedSelectedTracks, key = { it.original.uri }) { track ->
                    ReviewDiffItemCard(
                        track = track,
                        onToggleSelect = { viewModel.toggleTrackSelection(track.original.uri) }
                    )
                }
            }
        }

        // Bottom Sticky Action Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.revertAllPending()
                            viewModel.setTagsStep(TagsStep.BATCH_EDITOR)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_discard_changes"),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Discard", style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = { showConfirmDialog = true },
                        enabled = uiState.modifiedSelectedTracks.isNotEmpty(),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                            .testTag("btn_confirm_write_tags"),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Write Tags to Files", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Write Tags to Files", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    text = "Lossless audio stream preserved. A rollback snapshot will be kept so you can undo this batch.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.applyPendingChangesToDisk()
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag("btn_modal_write_tags")
                ) {
                    Text("Write Tags", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}

@Composable
fun ReviewDiffItemCard(
    track: EditableTrackState,
    onToggleSelect: () -> Unit
) {
    val original = track.original
    val pending = track.pending

    val diffList = remember(track.newAlbumArtBytes, original, pending, track.removeAlbumArt, track.pendingNewFileName) {
        val list = mutableListOf<FieldDiff>()
        if (original.title != pending.title) {
            list.add(FieldDiff("Title", original.title, pending.title))
        }
        if (original.artist != pending.artist) {
            list.add(FieldDiff("Artist", original.artist, pending.artist))
        }
        if (original.album != pending.album) {
            list.add(FieldDiff("Album", original.album, pending.album))
        }
        if (original.albumArtist != pending.albumArtist) {
            list.add(FieldDiff("Album Artist", original.albumArtist, pending.albumArtist))
        }
        if (original.trackNumber != pending.trackNumber) {
            list.add(FieldDiff("Track #", original.trackNumber?.toString() ?: "-", pending.trackNumber?.toString() ?: "-"))
        }
        if (original.totalTracks != pending.totalTracks) {
            list.add(FieldDiff("Total Tracks", original.totalTracks?.toString() ?: "-", pending.totalTracks?.toString() ?: "-"))
        }
        if (original.discNumber != pending.discNumber) {
            list.add(FieldDiff("Disc #", original.discNumber?.toString() ?: "-", pending.discNumber?.toString() ?: "-"))
        }
        if (original.year != pending.year) {
            list.add(FieldDiff("Year", original.year, pending.year))
        }
        if (original.genre != pending.genre) {
            list.add(FieldDiff("Genre", original.genre, pending.genre))
        }
        if (track.pendingNewFileName != null && track.pendingNewFileName != original.fileName) {
            list.add(FieldDiff("File name", original.fileName, track.pendingNewFileName))
        }
        if (track.newAlbumArtBytes != null) {
            list.add(FieldDiff("Cover", if (original.hasEmbeddedArt) "Existing Art" else "No Art", "New Artwork Embedded"))
        } else if (track.removeAlbumArt) {
            list.add(FieldDiff("Cover", if (original.hasEmbeddedArt) "Existing Art" else "No Art", "Removed"))
        }
        list
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(
            1.dp,
            if (track.isSelectedForApply) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSelect() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = track.isSelectedForApply,
                    onCheckedChange = { onToggleSelect() }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = original.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${diffList.size} fields changed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                diffList.forEach { diff ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = diff.fieldName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(90.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            if (diff.oldValue.isNotEmpty() && diff.oldValue != "-") {
                                Box(
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = diff.oldValue,
                                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Text("→", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.status.successContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = diff.newValue.ifEmpty { "(cleared)" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.status.success
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
