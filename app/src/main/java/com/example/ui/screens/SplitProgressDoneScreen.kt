package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Dimens
import com.example.ui.components.StickyActionBar
import com.example.ui.components.ScreenHeader
import com.example.ui.components.AppCard
import com.example.ui.theme.StatTypography
import com.example.viewmodel.AudioTaggerViewModel
import com.example.viewmodel.SplitStep
import com.example.viewmodel.TaggerUiState

@Composable
fun SplitProgressDoneScreen(
    uiState: TaggerUiState,
    viewModel: AudioTaggerViewModel,
    modifier: Modifier = Modifier
) {
    val progress = uiState.splitProgress
    val isRunning = progress.isRunning

    Column(modifier = modifier.fillMaxSize()) {
    ScreenHeader(
        title = if (isRunning) "Splitting" else "Split complete",
        subtitle = if (isRunning) "Keep the app open until it finishes" else "${uiState.splitGeneratedFiles.size} tracks created"
    )
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        if (isRunning) {
            // Running Progress View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 5.dp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Lossless FLAC Splitting in Progress",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = progress.message.ifBlank { "Processing audio blocks..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                val fraction = if (progress.total > 0) {
                    progress.current.toFloat() / progress.total.toFloat()
                } else 0f

                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 440.dp)
                        .height(6.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${progress.current} / ${progress.total} tracks",
                    style = StatTypography.metricBadge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            // Completed View
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = Dimens.ItemSpacing, bottom = Dimens.ListBottomInset),
                verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing)
            ) {
                // Generated Files list header
                item {
                    Text(
                        text = "Generated Track Files:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(uiState.splitGeneratedFiles) { fileName ->
                    AppCard(shape = MaterialTheme.shapes.small) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fileName,
                                    style = StatTypography.metricBody,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Lossless FLAC track",
                                    style = StatTypography.metricBadge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Sticky Bar
            StickyActionBar(modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.setSplitStep(SplitStep.SPLIT_HOME) },
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.ButtonHeight)
                        .testTag("btn_split_another_cue"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Split Another", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = { viewModel.openSplitFilesIntoTagEditor() },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(Dimens.ButtonHeight)
                        .testTag("btn_open_split_in_tag_editor"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open in Tag Editor", style = MaterialTheme.typography.labelLarge)
                }
            }
            }
        }
    }
    }
}
