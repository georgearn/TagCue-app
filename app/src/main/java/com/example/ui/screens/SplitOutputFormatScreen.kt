package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.material3.TextButton
import androidx.documentfile.provider.DocumentFile
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import com.example.ui.theme.Dimens
import com.example.ui.components.StickyActionBar
import com.example.ui.components.ScreenHeader
import com.example.ui.components.AppCard
import com.example.model.TrackNumberStyle
import com.example.ui.theme.StatTypography
import com.example.viewmodel.AudioTaggerViewModel
import com.example.viewmodel.SplitStep
import com.example.viewmodel.TaggerUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitOutputFormatScreen(
    uiState: TaggerUiState,
    viewModel: AudioTaggerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val outputFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.rememberSplitOutputFolder(treeUri)
            viewModel.executeFlacSplit(treeUri)
        }
    }

    // Reuse the last output folder while its write grant is still valid.
    val rememberedFolder = remember(uiState.settings.splitOutputUri) {
        uiState.settings.splitOutputUri?.let { Uri.parse(it) }?.takeIf { saved ->
            context.contentResolver.persistedUriPermissions.any { it.uri == saved && it.isWritePermission } &&
                DocumentFile.fromTreeUri(context, saved)?.canWrite() == true
        }
    }
    val rememberedName = remember(rememberedFolder) {
        rememberedFolder?.let { DocumentFile.fromTreeUri(context, it)?.name }
    }

    val config = uiState.splitConfig

    Column(modifier = modifier.fillMaxSize()) {
    ScreenHeader(
        title = "Output format",
        subtitle = "Set names, then check the preview",
        onBack = { viewModel.setSplitStep(SplitStep.SPLIT_HOME) }
    )
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = Dimens.ItemSpacing, bottom = Dimens.ListBottomInset),
            verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacing)
        ) {
            // Configuration Form Card
            item {
                AppCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Filename & Tag Settings",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Filename Pattern
                        OutlinedTextField(
                            value = config.filenameTemplate,
                            onValueChange = { newPattern ->
                                viewModel.updateSplitConfig { it.copy(filenameTemplate = newPattern) }
                            },
                            label = { Text("Filename Template") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_filename_template"),
                            singleLine = true
                        )

                        // Quick token chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("{track}", "{title}", "{artist}", "{album}", "{year}").forEach { token ->
                                AssistChip(
                                    onClick = {
                                        viewModel.updateSplitConfig {
                                            it.copy(filenameTemplate = "${it.filenameTemplate} $token".trim())
                                        }
                                    },
                                    label = { Text(token, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("chip_token_$token")
                                )
                            }
                        }

                        // Track Number Format
                        Text(
                            text = "Track Number Format:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TrackNumberStyle.values().forEach { style ->
                                FilterChip(
                                    selected = config.trackNumberStyle == style,
                                    onClick = {
                                        viewModel.updateSplitConfig { it.copy(trackNumberStyle = style) }
                                    },
                                    label = { Text(style.displayName, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("chip_track_style_${style.name}")
                                )
                            }
                        }

                        // Folder Structure Pattern
                        OutlinedTextField(
                            value = config.folderTemplate,
                            onValueChange = { newFolder ->
                                viewModel.updateSplitConfig { it.copy(folderTemplate = newFolder) }
                            },
                            label = { Text("Folder Sub-Directory Structure") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_folder_template"),
                            singleLine = true
                        )

                        // Switches
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Use Album Artist instead of Track Artist",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = config.useAlbumArtist,
                                onCheckedChange = { checked ->
                                    viewModel.updateSplitConfig { it.copy(useAlbumArtist = checked) }
                                },
                                modifier = Modifier.testTag("switch_use_album_artist")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Move 'feat. X' into track title",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = config.moveFeatIntoTitle,
                                onCheckedChange = { checked ->
                                    viewModel.updateSplitConfig { it.copy(moveFeatIntoTitle = checked) }
                                },
                                modifier = Modifier.testTag("switch_move_feat")
                            )
                        }
                    }
                }
            }

            // Live Preview Card
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Output Preview (${uiState.resolvedSplitPreviews.size} tracks):",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Updates in real-time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(uiState.resolvedSplitPreviews, key = { it.trackNumber }) { preview ->
                AppCard(shape = MaterialTheme.shapes.small) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "%02d".format(preview.trackNumber),
                            style = StatTypography.metricBadge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preview.relativeSubPath,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${preview.artist} • ${preview.title} • ${preview.album}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        StickyActionBar(modifier = Modifier.align(Alignment.BottomCenter)) {
            if (rememberedFolder != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { viewModel.executeFlacSplit(rememberedFolder) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.ButtonHeight)
                            .testTag("btn_split_to_remembered"),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Split to ${rememberedName ?: "last folder"}",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = { outputFolderPicker.launch(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_choose_output_and_split")
                    ) {
                        Text("Choose another folder", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Button(
                    onClick = { outputFolderPicker.launch(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.ButtonHeight)
                        .testTag("btn_choose_output_and_split"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Output Folder & Split FLAC", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    }
}
