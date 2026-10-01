package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ui.components.SettingsSheet
import com.example.ui.theme.StatTypography
import com.example.viewmodel.AudioTaggerViewModel
import com.example.viewmodel.MainTab
import com.example.viewmodel.SplitStep
import com.example.viewmodel.TagsStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: AudioTaggerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler {
        if (uiState.activeTab == MainTab.TAGS) {
            when (uiState.tagsStep) {
                TagsStep.REVIEW_CHANGES -> viewModel.setTagsStep(TagsStep.BATCH_EDITOR)
                TagsStep.BATCH_EDITOR -> viewModel.setTagsStep(TagsStep.LIBRARY)
                TagsStep.LIBRARY -> {}
            }
        } else {
            when (uiState.splitStep) {
                SplitStep.SPLIT_DONE,
                SplitStep.SPLIT_PROGRESS,
                SplitStep.OUTPUT_FORMAT -> viewModel.setSplitStep(SplitStep.SPLIT_HOME)
                SplitStep.SPLIT_HOME -> viewModel.switchTab(MainTab.TAGS)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "TagCue",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val subtitle = when (uiState.activeTab) {
                            MainTab.TAGS -> when (uiState.tagsStep) {
                                TagsStep.LIBRARY -> "Music Library"
                                TagsStep.BATCH_EDITOR -> "Batch Tag Editor"
                                TagsStep.REVIEW_CHANGES -> "Review Changes"
                            }
                            MainTab.SPLIT -> when (uiState.splitStep) {
                                SplitStep.SPLIT_HOME -> "CUE Splitter"
                                SplitStep.OUTPUT_FORMAT -> "Output Format & Preview"
                                SplitStep.SPLIT_PROGRESS,
                                SplitStep.SPLIT_DONE -> "Split Status"
                            }
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleSettingsDialog(true) },
                        modifier = Modifier.testTag("btn_top_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                val navItemColors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                NavigationBarItem(
                    selected = uiState.activeTab == MainTab.TAGS,
                    onClick = { viewModel.switchTab(MainTab.TAGS) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uiState.selectedCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = "${uiState.selectedCount}",
                                            style = StatTypography.metricBadge
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Style, contentDescription = "Tags")
                        }
                    },
                    label = { Text("Tags") },
                    colors = navItemColors,
                    modifier = Modifier.testTag("nav_tab_tags")
                )

                NavigationBarItem(
                    selected = uiState.activeTab == MainTab.SPLIT,
                    onClick = { viewModel.switchTab(MainTab.SPLIT) },
                    icon = {
                        Icon(Icons.Default.CallSplit, contentDescription = "Split")
                    },
                    label = { Text("Split") },
                    colors = navItemColors,
                    modifier = Modifier.testTag("nav_tab_split")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.activeTab) {
                MainTab.TAGS -> when (uiState.tagsStep) {
                    TagsStep.LIBRARY -> LibraryScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    TagsStep.BATCH_EDITOR -> BatchTagEditorScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    TagsStep.REVIEW_CHANGES -> ReviewChangesScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                MainTab.SPLIT -> when (uiState.splitStep) {
                    SplitStep.SPLIT_HOME -> SplitHomeScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    SplitStep.OUTPUT_FORMAT -> SplitOutputFormatScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    SplitStep.SPLIT_PROGRESS,
                    SplitStep.SPLIT_DONE -> SplitProgressDoneScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (uiState.isLoading) {
            Dialog(onDismissRequest = {}) {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = uiState.loadingMessage.ifBlank { "Processing..." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        uiState.lastSummaryMessage?.let { summary ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissSummary() },
                title = { Text("Notice", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) },
                text = { Text(summary, style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissSummary() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("OK")
                    }
                }
            )
        }

        if (uiState.showSettingsDialog) {
            SettingsSheet(
                settings = uiState.settings,
                onDismiss = { viewModel.toggleSettingsDialog(false) },
                onSave = { updatedSettings ->
                    viewModel.updateSettings(updatedSettings)
                    viewModel.toggleSettingsDialog(false)
                }
            )
        }
    }
}
