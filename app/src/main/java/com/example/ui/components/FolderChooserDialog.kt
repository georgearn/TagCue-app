package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.example.audio.AudioTagManager

/**
 * Lets the user pick a folder inside the music folder that was granted once in Settings,
 * so the system picker is not needed for every album.
 */
@Composable
fun FolderChooserDialog(
    rootUri: Uri,
    rootName: String,
    onPick: (uri: Uri, name: String) -> Unit,
    onPickOther: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // Folders entered below the root, oldest first.
    var trail by remember(rootUri) { mutableStateOf<List<Pair<Uri, String>>>(emptyList()) }
    val currentUri = trail.lastOrNull()?.first ?: rootUri
    val currentName = trail.lastOrNull()?.second ?: rootName

    val folders by produceState<List<Pair<Uri, String>>?>(initialValue = null, key1 = currentUri) {
        value = null
        value = runCatching { AudioTagManager.listSubFolders(context, currentUri) }.getOrDefault(emptyList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("folder_chooser"),
        title = { Text("Choose a folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = (listOf(rootName) + trail.map { it.second }).joinToString(" / "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp)) {
                    val list = folders
                    if (list == null) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(28.dp))
                    } else {
                        LazyColumn {
                            if (trail.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable { trail = trail.dropLast(1) }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Text("Up one level", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            if (list.isEmpty()) {
                                item {
                                    Text(
                                        "No sub-folders here.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                            }
                            items(list, key = { it.first.toString() }) { (uri, name) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { trail = trail + (uri to name) }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Folder, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onPickOther) { Text("Other location…") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPick(currentUri, currentName) },
                modifier = Modifier.testTag("btn_scan_this_folder")
            ) { Text("Scan \"$currentName\"", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Display name of a tree uri, or a generic fallback. */
@Composable
fun rememberFolderName(uri: Uri?): String {
    val context = LocalContext.current
    return remember(uri) {
        uri?.let { runCatching { DocumentFile.fromTreeUri(context, it)?.name }.getOrNull() } ?: "Music folder"
    }
}
