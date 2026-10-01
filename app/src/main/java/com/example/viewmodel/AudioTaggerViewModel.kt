package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WorkService
import com.example.audio.AudioTagManager
import com.example.audio.BatchEngine
import com.example.audio.CueParser
import com.example.audio.FlacEngine
import com.example.audio.SourceFile
import com.example.model.AlbumGroup
import com.example.model.AppSettings
import com.example.model.AudioMetadata
import com.example.model.BackupStore
import com.example.model.CaseOption
import com.example.model.CoverArtAction
import com.example.model.CueEncoding
import com.example.model.CueSheet
import com.example.model.CueTrack
import com.example.model.EditableTrackState
import com.example.model.FieldAction
import com.example.model.ProblemFilter
import com.example.model.ResolvedTrackMetadata
import com.example.model.SessionStore
import com.example.model.SettingsStore
import com.example.model.SplitOutputConfig
import com.example.model.TagBackup
import com.example.model.TargetField
import com.example.model.TrackNumberStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab {
    TAGS,
    SPLIT
}

enum class TagsStep {
    LIBRARY,
    BATCH_EDITOR,
    REVIEW_CHANGES
}

enum class SplitStep {
    SPLIT_HOME,
    OUTPUT_FORMAT,
    SPLIT_PROGRESS,
    SPLIT_DONE
}

data class BatchFormState(
    val titleAction: FieldAction<String> = FieldAction.Keep,
    val artistAction: FieldAction<String> = FieldAction.Keep,
    val albumArtistAction: FieldAction<String> = FieldAction.Keep,
    val albumAction: FieldAction<String> = FieldAction.Keep,
    val yearAction: FieldAction<String> = FieldAction.Keep,
    val genreAction: FieldAction<String> = FieldAction.Keep,
    val trackNumAction: FieldAction<Int?> = FieldAction.Keep,
    val totalTracksAction: FieldAction<Int?> = FieldAction.Keep,
    val discNumAction: FieldAction<Int?> = FieldAction.Keep,
    val coverArtAction: CoverArtAction = CoverArtAction.KEEP,
    val newCoverArtBytes: ByteArray? = null
)

data class BatchProgress(
    val current: Int = 0,
    val total: Int = 0,
    val message: String = "",
    val isRunning: Boolean = false
)

data class TaggerUiState(
    val activeTab: MainTab = MainTab.TAGS,
    val tagsStep: TagsStep = TagsStep.LIBRARY,
    val splitStep: SplitStep = SplitStep.SPLIT_HOME,
    // Settings
    val settings: AppSettings = AppSettings(),
    val showSettingsDialog: Boolean = false,
    // Folders & Library
    val loadedFolders: List<Uri> = emptyList(),
    val allTracks: List<EditableTrackState> = emptyList(),
    val selectedFilter: ProblemFilter = ProblemFilter.ALL,
    val lastBackup: TagBackup? = null,
    // Batch Editor Form
    val batchForm: BatchFormState = BatchFormState(),
    // Loading & Progress
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val progress: BatchProgress = BatchProgress(),
    val lastSummaryMessage: String? = null,
    // Split Feature
    val cueSheet: CueSheet? = null,
    val cueSheetUri: Uri? = null,
    val cueEncoding: CueEncoding = CueEncoding.AUTO,
    val pairedFlacUri: Uri? = null,
    val pairedFlacMetadata: AudioMetadata? = null,
    val splitConfig: SplitOutputConfig = SplitOutputConfig(),
    val splitProgress: BatchProgress = BatchProgress(),
    val splitGeneratedFiles: List<String> = emptyList(),
    val splitOutputFolderUri: Uri? = null
) {
    val selectedTracks: List<EditableTrackState>
        get() = allTracks.filter { it.isSelectedForApply }

    val selectedCount: Int
        get() = selectedTracks.size

    val isAllSelected: Boolean
        get() = allTracks.isNotEmpty() && allTracks.all { it.isSelectedForApply }

    val modifiedTracks: List<EditableTrackState>
        get() = allTracks.filter { it.isModified }

    val modifiedSelectedTracks: List<EditableTrackState>
        get() = allTracks.filter { it.isModified && it.isSelectedForApply }

    // Grouping by Album
    val albumGroups: List<AlbumGroup>
        get() {
            val filtered = allTracks.filter { track ->
                track.hasProblem(selectedFilter, allTracks.filter { it.pending.effectiveAlbum == track.pending.effectiveAlbum })
            }
            return filtered.groupBy { "${it.pending.effectiveAlbum}:::${it.pending.albumArtist.ifBlank { it.pending.artist }}" }
                .map { (key, groupTracks) ->
                    val first = groupTracks.first()
                    AlbumGroup(
                        albumKey = key,
                        albumTitle = first.pending.effectiveAlbum,
                        albumArtist = first.pending.albumArtist.ifBlank { first.pending.artist }.ifBlank { "Unknown Artist" },
                        coverArtBytes = groupTracks.firstOrNull { it.newAlbumArtBytes != null }?.newAlbumArtBytes
                            ?: groupTracks.firstOrNull { it.original.albumArtBytes != null }?.original?.albumArtBytes,
                        tracks = groupTracks.sortedBy { it.pending.trackNumber ?: 999 }
                    )
                }.sortedBy { it.albumTitle.lowercase() }
        }

    // Mixed values computation for batch editor form
    fun computeFieldValue(selector: (AudioMetadata) -> String): String {
        val sel = selectedTracks
        if (sel.isEmpty()) return ""
        val values = sel.map { selector(it.pending) }.distinct()
        return if (values.size == 1) values.first() else "‹mixed›"
    }

    fun computeFieldInt(selector: (AudioMetadata) -> Int?): String {
        val sel = selectedTracks
        if (sel.isEmpty()) return ""
        val values = sel.map { selector(it.pending) }.distinct()
        return if (values.size == 1) values.first()?.toString() ?: "" else "‹mixed›"
    }

    val resolvedSplitPreviews: List<ResolvedTrackMetadata>
        get() {
            val cue = cueSheet ?: return emptyList()
            return cue.tracks.map { track ->
                splitConfig.resolveMetadata(
                    track = track,
                    albumTitle = cue.title,
                    albumPerformer = cue.performer,
                    albumDate = cue.date,
                    totalTracks = cue.tracks.size
                )
            }
        }
}

class AudioTaggerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TaggerUiState())
    val uiState: StateFlow<TaggerUiState> = _uiState.asStateFlow()

    init {
        val context = getApplication<Application>()
        val settings = SettingsStore.load(context)
        _uiState.update {
            it.copy(
                settings = settings,
                splitConfig = it.splitConfig.copy(
                    filenameTemplate = settings.defaultFilenameTemplate,
                    folderTemplate = settings.defaultFolderTemplate,
                    trackNumberStyle = settings.defaultTrackNumberStyle
                )
            )
        }
        // Restore the last backup so undo survives an app restart.
        viewModelScope.launch {
            val backup = withContext(Dispatchers.IO) { BackupStore.load(context) }
            if (backup != null) _uiState.update { it.copy(lastBackup = it.lastBackup ?: backup) }
        }
        restoreSession()
        persistSessionOnChange()
        forwardProgressToNotification()
    }

    @OptIn(FlowPreview::class)
    private fun forwardProgressToNotification() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState
                .map { st -> listOf(st.progress, st.splitProgress).firstOrNull { it.isRunning } }
                .distinctUntilChanged()
                .sample(500)
                .collect { p -> if (p != null) WorkService.update(context, p.message, p.current, p.total) }
        }
    }

    /** Runs [block] under the foreground service so a long job survives the app going to the background. */
    private fun launchWithService(label: String, block: suspend () -> Unit) = viewModelScope.launch {
        val context = getApplication<Application>()
        WorkService.begin(context, label)
        try {
            block()
        } finally {
            WorkService.end(context)
        }
    }

    // ----------------------------------------------------
    // SESSION PERSISTENCE (open folder + pending edits)
    // ----------------------------------------------------
    private fun restoreSession() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { SessionStore.load(context) } ?: return@launch
            val stillGranted = context.contentResolver.persistedUriPermissions.any {
                it.isReadPermission && it.uri == saved.folderUri
            }
            if (!stillGranted) { withContext(Dispatchers.IO) { SessionStore.clear(context) }; return@launch }
            if (_uiState.value.allTracks.isNotEmpty() || _uiState.value.isLoading) return@launch // user already started something

            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = "Restoring ${saved.folderName}...",
                    progress = BatchProgress(0, 0, "Restoring your library...", true)
                )
            }
            try {
                val scanned = AudioTagManager.readFolderAudioFiles(context, saved.folderUri) { loaded, total, name ->
                    _uiState.update { it.copy(progress = BatchProgress(loaded, total, "Reading tags: $name", true)) }
                }
                val editsByUri = saved.edits.associateBy { it.uri }
                var restored = 0
                val tracks = scanned.map { meta ->
                    val base = meta.copy(folderUri = saved.folderUri, folderName = saved.folderName)
                    val e = editsByUri[meta.uri]
                    if (e == null || e.fileName != meta.fileName || (e.sizeBytes != 0L && e.sizeBytes != meta.sizeBytes)) {
                        EditableTrackState(original = base, pending = base, isSelectedForApply = false)
                    } else {
                        restored++
                        EditableTrackState(
                            original = base,
                            pending = base.copy(
                                title = e.title, artist = e.artist, album = e.album, albumArtist = e.albumArtist,
                                trackNumber = e.trackNumber, totalTracks = e.totalTracks, discNumber = e.discNumber,
                                year = e.year, genre = e.genre
                            ),
                            isSelectedForApply = e.selected,
                            newAlbumArtBytes = e.newArt,
                            removeAlbumArt = e.removeArt,
                            pendingNewFileName = e.newFileName
                        )
                    }
                }
                // A new folder may have been opened while this scan ran.
                if (_uiState.value.allTracks.isNotEmpty()) {
                    _uiState.update { it.copy(isLoading = false, progress = BatchProgress(isRunning = false)) }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        loadedFolders = listOf(saved.folderUri),
                        allTracks = tracks,
                        isLoading = false,
                        progress = BatchProgress(isRunning = false),
                        lastSummaryMessage = if (restored > 0) "Restored $restored unsaved edits" else null
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, progress = BatchProgress(isRunning = false)) }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun persistSessionOnChange() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState
                .map { it.allTracks to it.loadedFolders }
                .distinctUntilChanged { a, b -> a.first === b.first && a.second == b.second }
                .debounce(1000)
                .filter { (tracks, folders) -> folders.isNotEmpty() && tracks.isNotEmpty() }
                .collect { (tracks, folders) ->
                    if (_uiState.value.isLoading) return@collect
                    val folder = folders.first()
                    val name = tracks.firstOrNull()?.original?.folderName ?: "Music Folder"
                    withContext(Dispatchers.IO) { SessionStore.save(context, folder, name, tracks) }
                }
        }
    }

    // ----------------------------------------------------
    // TAB & STEP NAVIGATION
    // ----------------------------------------------------
    fun switchTab(tab: MainTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setTagsStep(step: TagsStep) {
        _uiState.update { state ->
            // Batch form belongs to one selection. Drop it when the editor closes or opens fresh.
            val resetForm = step == TagsStep.LIBRARY ||
                (step == TagsStep.BATCH_EDITOR && state.tagsStep == TagsStep.LIBRARY)
            state.copy(
                tagsStep = step,
                batchForm = if (resetForm) BatchFormState() else state.batchForm
            )
        }
    }

    fun setSplitStep(step: SplitStep) {
        _uiState.update { it.copy(splitStep = step) }
    }

    fun toggleSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun updateSettings(settings: AppSettings) {
        SettingsStore.save(getApplication<Application>(), settings)
        _uiState.update { state ->
            val prev = state.settings
            var cfg = state.splitConfig
            // Changed defaults flow into the split screen right away.
            if (settings.defaultFilenameTemplate != prev.defaultFilenameTemplate) cfg = cfg.copy(filenameTemplate = settings.defaultFilenameTemplate)
            if (settings.defaultFolderTemplate != prev.defaultFolderTemplate) cfg = cfg.copy(folderTemplate = settings.defaultFolderTemplate)
            if (settings.defaultTrackNumberStyle != prev.defaultTrackNumberStyle) cfg = cfg.copy(trackNumberStyle = settings.defaultTrackNumberStyle)
            state.copy(settings = settings, splitConfig = cfg)
        }
    }

    fun dismissSummary() {
        _uiState.update { it.copy(lastSummaryMessage = null) }
    }

    // ----------------------------------------------------
    // TAGS FLOW: 1. LIBRARY & FOLDER SCANNING
    // ----------------------------------------------------
    fun setProblemFilter(filter: ProblemFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun addMusicFolder(treeUri: Uri, folderName: String = "Music Folder") {
        launchWithService("Scanning music folder") {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    batchForm = BatchFormState(),
                    loadingMessage = "Scanning $folderName (including sub-folders)...",
                    progress = BatchProgress(0, 0, "Scanning files in sub-folders...", true)
                )
            }

            try {
                val context = getApplication<Application>()
                val newAudioList = AudioTagManager.readFolderAudioFiles(context, treeUri) { loaded, total, currentName ->
                    _uiState.update {
                        it.copy(progress = BatchProgress(loaded, total, "Reading tags: $currentName", true))
                    }
                }

                // Discard previous edits and load fresh for the new folder
                val newEditable = newAudioList.map { meta ->
                    EditableTrackState(
                        original = meta.copy(folderUri = treeUri, folderName = folderName),
                        pending = meta.copy(folderUri = treeUri, folderName = folderName),
                        isSelectedForApply = false
                    )
                }

                _uiState.update {
                    it.copy(
                        loadedFolders = listOf(treeUri),
                        allTracks = newEditable,
                        batchForm = BatchFormState(),
                        lastBackup = null,
                        selectedFilter = ProblemFilter.ALL,
                        tagsStep = TagsStep.LIBRARY,
                        isLoading = false,
                        progress = BatchProgress(isRunning = false),
                        lastSummaryMessage = if (newEditable.isEmpty()) {
                            "No supported audio files found in $folderName or its sub-folders."
                        } else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        progress = BatchProgress(isRunning = false),
                        lastSummaryMessage = "Scan Error: ${e.localizedMessage ?: "Failed to read folder"}"
                    )
                }
            }
        }
    }

    fun toggleAlbumSelection(albumKey: String) {
        _uiState.update { state ->
            val albumTracks = state.albumGroups.find { it.albumKey == albumKey }?.tracks ?: return@update state
            val allSelected = albumTracks.all { it.isSelectedForApply }
            val targetUris = albumTracks.map { it.original.uri }.toSet()

            val updated = state.allTracks.map { track ->
                if (targetUris.contains(track.original.uri)) {
                    track.copy(isSelectedForApply = !allSelected)
                } else track
            }
            state.copy(allTracks = updated, batchForm = BatchFormState())
        }
    }

    fun toggleTrackSelection(uri: Uri) {
        _uiState.update { state ->
            val updated = state.allTracks.map { track ->
                if (track.original.uri == uri) track.copy(isSelectedForApply = !track.isSelectedForApply) else track
            }
            state.copy(allTracks = updated, batchForm = BatchFormState())
        }
    }

    fun selectAll(select: Boolean) {
        _uiState.update { state ->
            val updated = state.allTracks.map { it.copy(isSelectedForApply = select) }
            state.copy(allTracks = updated, batchForm = BatchFormState())
        }
    }

    // ----------------------------------------------------
    // TAGS FLOW: 2. BATCH TAG EDITOR FORM
    // ----------------------------------------------------
    fun updateBatchForm(updater: (BatchFormState) -> BatchFormState) {
        _uiState.update { it.copy(batchForm = updater(it.batchForm)) }
    }

    fun commitBatchFormToPending() {
        val form = _uiState.value.batchForm
        _uiState.update { state ->
            val updated = state.allTracks.map { track ->
                if (!track.isSelectedForApply) return@map track

                var p = track.pending

                if (form.titleAction is FieldAction.SetValue) p = p.copy(title = form.titleAction.value)
                else if (form.titleAction is FieldAction.Clear) p = p.copy(title = "")

                if (form.artistAction is FieldAction.SetValue) p = p.copy(artist = form.artistAction.value)
                else if (form.artistAction is FieldAction.Clear) p = p.copy(artist = "")

                if (form.albumArtistAction is FieldAction.SetValue) p = p.copy(albumArtist = form.albumArtistAction.value)
                else if (form.albumArtistAction is FieldAction.Clear) p = p.copy(albumArtist = "")

                if (form.albumAction is FieldAction.SetValue) p = p.copy(album = form.albumAction.value)
                else if (form.albumAction is FieldAction.Clear) p = p.copy(album = "")

                if (form.yearAction is FieldAction.SetValue) p = p.copy(year = form.yearAction.value)
                else if (form.yearAction is FieldAction.Clear) p = p.copy(year = "")

                if (form.genreAction is FieldAction.SetValue) p = p.copy(genre = form.genreAction.value)
                else if (form.genreAction is FieldAction.Clear) p = p.copy(genre = "")

                if (form.trackNumAction is FieldAction.SetValue) p = p.copy(trackNumber = form.trackNumAction.value)
                else if (form.trackNumAction is FieldAction.Clear) p = p.copy(trackNumber = null)

                if (form.totalTracksAction is FieldAction.SetValue) p = p.copy(totalTracks = form.totalTracksAction.value)
                else if (form.totalTracksAction is FieldAction.Clear) p = p.copy(totalTracks = null)

                if (form.discNumAction is FieldAction.SetValue) p = p.copy(discNumber = form.discNumAction.value)
                else if (form.discNumAction is FieldAction.Clear) p = p.copy(discNumber = null)

                var newArt = track.newAlbumArtBytes
                var removeArt = track.removeAlbumArt
                when (form.coverArtAction) {
                    CoverArtAction.KEEP -> {}
                    CoverArtAction.SET_NEW -> {
                        newArt = form.newCoverArtBytes
                        removeArt = false
                    }
                    CoverArtAction.REMOVE -> {
                        newArt = null
                        removeArt = true
                    }
                }

                track.copy(
                    pending = if (state.settings.autoTrimSpaces) p.withCleanSpaces() else p,
                    newAlbumArtBytes = newArt,
                    removeAlbumArt = removeArt
                )
            }
            state.copy(allTracks = updated, tagsStep = TagsStep.REVIEW_CHANGES, batchForm = BatchFormState())
        }
    }

    // Tools row operations
    fun applyAutoNumber(startNumber: Int = 1, setTotalTracks: Boolean = true) {
        _uiState.update { state ->
            val updated = BatchEngine.autoNumber(state.allTracks, startNumber, setTotalTracks)
            state.copy(allTracks = updated)
        }
    }

    fun applyFilenameToTags(pattern: String = "%n - %t") {
        _uiState.update { state ->
            val updated = BatchEngine.extractFromFilename(state.allTracks, pattern)
            state.copy(allTracks = updated)
        }
    }

    fun applyFindAndReplace(field: TargetField, search: String, replacement: String, matchCase: Boolean, useRegex: Boolean) {
        _uiState.update { state ->
            val updated = BatchEngine.findAndReplace(state.allTracks, field, search, replacement, matchCase, useRegex)
            state.copy(allTracks = updated)
        }
    }

    fun applyCaseConversion(field: TargetField, caseOption: CaseOption) {
        _uiState.update { state ->
            val updated = BatchEngine.changeCase(state.allTracks, field, caseOption)
            state.copy(allTracks = updated)
        }
    }

    fun applyTrimSpaces() {
        _uiState.update { state ->
            val updated = state.allTracks.map { track ->
                if (!track.isSelectedForApply) track else track.copy(pending = track.pending.withCleanSpaces())
            }
            state.copy(allTracks = updated)
        }
    }

    private fun AudioMetadata.withCleanSpaces(): AudioMetadata {
        fun clean(v: String) = v.trim().replace(Regex("\\s+"), " ")
        return copy(
            title = clean(title),
            artist = clean(artist),
            album = clean(album),
            albumArtist = clean(albumArtist),
            genre = clean(genre)
        )
    }

    fun applyRenameFromTags(pattern: String) {
        _uiState.update { state ->
            state.copy(allTracks = BatchEngine.renameFilesFromTags(state.allTracks, pattern))
        }
    }

    fun revertSingleTrack(uri: Uri) {
        _uiState.update { state ->
            val updated = state.allTracks.map {
                if (it.original.uri == uri) {
                    EditableTrackState(original = it.original, pending = it.original.copy(), isSelectedForApply = it.isSelectedForApply)
                } else it
            }
            state.copy(allTracks = updated)
        }
    }

    fun revertAllPending() {
        _uiState.update { state ->
            val updated = state.allTracks.map {
                EditableTrackState(original = it.original, pending = it.original.copy(), isSelectedForApply = it.isSelectedForApply)
            }
            state.copy(allTracks = updated, tagsStep = TagsStep.LIBRARY, batchForm = BatchFormState())
        }
    }

    // ----------------------------------------------------
    // TAGS FLOW: 3. REVIEW CHANGES & WRITE (WITH UNDO BACKUP)
    // ----------------------------------------------------
    fun applyPendingChangesToDisk() {
        val startState = _uiState.value
        val tracksToApply = startState.allTracks.filter { it.isSelectedForApply && it.isModified }
        if (tracksToApply.isEmpty()) return

        val context = getApplication<Application>()

        // 1. Snapshot the original tags before anything is written (skipped when the setting is off).
        if (startState.settings.backupTagsBeforeWrite) {
            val backup = TagBackup(snapshot = tracksToApply.associate { it.original.uri to it.original })
            _uiState.update { it.copy(lastBackup = backup) }
            viewModelScope.launch(Dispatchers.IO) { BackupStore.save(context, backup) }
        } else {
            _uiState.update { it.copy(lastBackup = null) }
            BackupStore.clear(context)
        }

        launchWithService("Writing tags") {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    progress = BatchProgress(0, tracksToApply.size, "Writing tags to audio files...", true)
                )
            }

            var successCount = 0
            var errorCount = 0
            var renamedCount = 0
            var renameFailures = 0
            val renames = LinkedHashMap<Uri, Uri>()
            val updatedTracks = _uiState.value.allTracks.toMutableList()

            for (i in tracksToApply.indices) {
                val track = tracksToApply[i]
                _uiState.update {
                    it.copy(
                        progress = BatchProgress(i + 1, tracksToApply.size, "Saving: ${track.original.fileName}", true)
                    )
                }

                val saveResult = AudioTagManager.saveTrackMetadata(context, track)
                if (saveResult.isSuccess) {
                    successCount++
                    var currentUri = track.original.uri
                    val newName = track.pendingNewFileName
                    if (newName != null && newName != track.original.fileName) {
                        val renamedUri = renameDocument(currentUri, newName)
                        if (renamedUri != null) {
                            renames[currentUri] = renamedUri
                            currentUri = renamedUri
                            renamedCount++
                        } else {
                            renameFailures++
                        }
                    }
                    val freshMeta = AudioTagManager.readMetadata(context, currentUri)
                    val idx = updatedTracks.indexOfFirst { it.original.uri == track.original.uri }
                    if (idx != -1 && freshMeta != null) {
                        val withFolder = freshMeta.copy(folderUri = track.original.folderUri, folderName = track.original.folderName)
                        updatedTracks[idx] = EditableTrackState(
                            original = withFolder,
                            pending = withFolder,
                            isSelectedForApply = false
                        )
                    }
                } else {
                    errorCount++
                }
            }

            // Undo needs to know where renamed files went.
            val backupWithRenames = _uiState.value.lastBackup?.takeIf { renames.isNotEmpty() }?.copy(renames = renames)
            if (backupWithRenames != null) {
                withContext(Dispatchers.IO) { BackupStore.save(context, backupWithRenames) }
            }

            _uiState.update {
                it.copy(
                    allTracks = updatedTracks,
                    lastBackup = backupWithRenames ?: it.lastBackup,
                    isLoading = false,
                    progress = BatchProgress(isRunning = false),
                    tagsStep = TagsStep.LIBRARY,
                    lastSummaryMessage = buildString {
                        append(if (errorCount == 0) "Successfully wrote tags to $successCount files" else "Wrote $successCount files. $errorCount errors occurred.")
                        if (renamedCount > 0) append(" Renamed $renamedCount files.")
                        if (renameFailures > 0) append(" $renameFailures files could not be renamed.")
                    }
                )
            }
        }
    }

    /** Renames a SAF document. Returns the new uri, or null if the provider refused. */
    private suspend fun renameDocument(uri: Uri, newName: String): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            DocumentsContract.renameDocument(getApplication<Application>().contentResolver, uri, newName)
        }.getOrNull()
    }

    fun undoLastBatch() {
        val backup = _uiState.value.lastBackup ?: return
        launchWithService("Restoring previous tags") {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    progress = BatchProgress(0, backup.snapshot.size, "Restoring previous tags...", true)
                )
            }

            val context = getApplication<Application>()
            val updatedTracks = _uiState.value.allTracks.toMutableList()
            var restoredCount = 0

            for ((oldUri, originalMeta) in backup.snapshot) {
                // A renamed file now lives at a different uri.
                val currentUri = backup.renames[oldUri] ?: oldUri
                val idx = updatedTracks.indexOfFirst { it.original.uri == currentUri }
                if (idx == -1) continue
                val currentTrack = updatedTracks[idx]
                val restoreState = EditableTrackState(
                    original = currentTrack.original,
                    pending = originalMeta,
                    isSelectedForApply = true,
                    newAlbumArtBytes = originalMeta.albumArtBytes,
                    removeAlbumArt = !originalMeta.hasEmbeddedArt
                )
                val res = AudioTagManager.saveTrackMetadata(context, restoreState)
                if (!res.isSuccess) continue
                restoredCount++

                var finalUri = currentUri
                if (currentUri != oldUri && currentTrack.original.fileName != originalMeta.fileName) {
                    renameDocument(currentUri, originalMeta.fileName)?.let { finalUri = it }
                }
                val restored = originalMeta.copy(uri = finalUri)
                updatedTracks[idx] = EditableTrackState(
                    original = restored,
                    pending = restored.copy(),
                    isSelectedForApply = false
                )
            }

            withContext(Dispatchers.IO) { BackupStore.clear(context) }
            _uiState.update {
                it.copy(
                    allTracks = updatedTracks,
                    lastBackup = null,
                    isLoading = false,
                    progress = BatchProgress(isRunning = false),
                    lastSummaryMessage = "Undone last batch: $restoredCount files restored"
                )
            }
        }
    }

    // ----------------------------------------------------
    // SPLIT FLOW: CUE PARSING, ENCODING & SPLITTING
    // ----------------------------------------------------
    fun loadCueSheet(uri: Uri, encoding: CueEncoding = CueEncoding.AUTO) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot open CUE file stream")
                val sheet = CueParser.parse(stream, encoding, uri)

                _uiState.update {
                    it.copy(
                        cueSheet = sheet,
                        cueSheetUri = uri,
                        cueEncoding = encoding,
                        splitStep = SplitStep.SPLIT_HOME
                    )
                }

                // Pair the FLAC automatically: CUE's own folder first, then the library.
                findPairedFlac(uri, sheet)?.let { pairFlacFile(it) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(lastSummaryMessage = "Error Parsing CUE: ${e.localizedMessage}")
                }
            }
        }
    }

    fun reParseCueWithEncoding(encoding: CueEncoding) {
        val state = _uiState.value
        val currentSheet = state.cueSheet ?: return
        if (currentSheet.rawBytes.isEmpty()) return

        // Edits are whatever differs from a clean parse with the encoding in use until now.
        // Re-apply them, and the split selection, on top of the new parse.
        val baseline = CueParser.parseBytes(currentSheet.rawBytes, state.cueEncoding, currentSheet.uri)
            .tracks.associateBy { it.number }
        val current = currentSheet.tracks.associateBy { it.number }
        val reParsed = CueParser.parseBytes(currentSheet.rawBytes, encoding, currentSheet.uri)
        val merged = reParsed.tracks.map { fresh ->
            val cur = current[fresh.number] ?: return@map fresh
            val base = baseline[fresh.number]
            fresh.copy(
                title = if (base != null && cur.title != base.title) cur.title else fresh.title,
                performer = if (base != null && cur.performer != base.performer) cur.performer else fresh.performer,
                isSelectedForSplit = cur.isSelectedForSplit
            )
        }
        _uiState.update {
            it.copy(
                cueSheet = reParsed.copy(tracks = merged),
                cueEncoding = encoding
            )
        }
    }

    fun pairFlacFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val meta = AudioTagManager.readMetadata(context, uri)
                _uiState.update {
                    it.copy(
                        pairedFlacUri = uri,
                        pairedFlacMetadata = meta
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun updateCueTrackInline(trackNumber: Int, title: String, performer: String) {
        val currentSheet = _uiState.value.cueSheet ?: return
        val updatedTracks = currentSheet.tracks.map {
            if (it.number == trackNumber) it.copy(title = title, performer = performer) else it
        }
        _uiState.update { it.copy(cueSheet = currentSheet.copy(tracks = updatedTracks)) }
    }

    fun toggleCueTrackSplitSelection(trackNumber: Int) {
        val currentSheet = _uiState.value.cueSheet ?: return
        val updatedTracks = currentSheet.tracks.map {
            if (it.number == trackNumber) it.copy(isSelectedForSplit = !it.isSelectedForSplit) else it
        }
        _uiState.update { it.copy(cueSheet = currentSheet.copy(tracks = updatedTracks)) }
    }

    fun updateSplitConfig(updater: (SplitOutputConfig) -> SplitOutputConfig) {
        _uiState.update { it.copy(splitConfig = updater(it.splitConfig)) }
    }

    fun executeFlacSplit(outputFolderUri: Uri) {
        val cue = _uiState.value.cueSheet ?: return
        val flacUri = _uiState.value.pairedFlacUri ?: return
        val cueUri = _uiState.value.cueSheetUri
        val config = _uiState.value.splitConfig
        val deleteSource = _uiState.value.settings.deleteSourceAfterSplit
        val selectedCount = cue.tracks.count { it.isSelectedForSplit }

        launchWithService("Splitting audio") {
            _uiState.update {
                it.copy(
                    splitStep = SplitStep.SPLIT_PROGRESS,
                    splitProgress = BatchProgress(0, selectedCount, "Starting lossless split...", true),
                    splitGeneratedFiles = emptyList(),
                    splitOutputFolderUri = outputFolderUri
                )
            }

            try {
                val context = getApplication<Application>()
                val generated = mutableListOf<String>()

                withContext(Dispatchers.IO) {
                    val rootDoc = DocumentFile.fromTreeUri(context, outputFolderUri)
                        ?: throw IllegalStateException("Cannot access output folder")

                    SourceFile.open(context, flacUri).use { source ->
                        FlacEngine.splitFlacByCue(
                            flac = source.buffer,
                            cueSheet = cue,
                            config = config,
                            onProgress = { current, total, trackTitle ->
                                _uiState.update {
                                    it.copy(splitProgress = BatchProgress(current, total, "Splitting: $trackTitle", true))
                                }
                            },
                            onTrackReady = { resolved, _, write ->
                                // Honour the folder template: {artist}/{year} - {album}
                                var dir: DocumentFile = rootDoc
                                for (part in resolved.subFolderPath.split('/').filter { it.isNotBlank() }) {
                                    dir = dir.findFile(part)?.takeIf { it.isDirectory }
                                        ?: dir.createDirectory(part)
                                        ?: throw IllegalStateException("Cannot create folder $part")
                                }
                                val existing = dir.findFile(resolved.fileName)?.takeIf { it.isFile }
                                val target = existing
                                    ?: dir.createFile("audio/flac", resolved.fileName)
                                    ?: throw IllegalStateException("Cannot create file ${resolved.fileName}")
                                try {
                                    val stream = context.contentResolver.openOutputStream(target.uri, "wt")
                                        ?: throw IllegalStateException("Cannot write ${resolved.fileName}")
                                    stream.buffered(256 * 1024).use { out -> write(out) }
                                } catch (e: Exception) {
                                    // Do not leave a half-written track behind.
                                    if (existing == null) runCatching { target.delete() }
                                    throw e
                                }
                                val label = if (resolved.subFolderPath.isBlank()) resolved.fileName
                                else "${resolved.subFolderPath}/${resolved.fileName}"
                                generated.add(label)
                            }
                        )
                    }
                }

                // Only delete the source when the whole album was split and every file was written.
                var deleteNote = ""
                if (deleteSource && selectedCount == cue.tracks.size && generated.size == selectedCount) {
                    val flacGone = deleteDocument(flacUri)
                    val cueGone = cueUri?.let { deleteDocument(it) } ?: true
                    deleteNote = if (flacGone && cueGone) "Source FLAC and CUE deleted."
                    else "Split finished, but the source files could not be deleted."
                } else if (deleteSource) {
                    deleteNote = "Source kept: not every track was split."
                }

                _uiState.update {
                    it.copy(
                        splitStep = SplitStep.SPLIT_DONE,
                        splitProgress = BatchProgress(isRunning = false),
                        splitGeneratedFiles = generated,
                        pairedFlacUri = if (deleteNote.startsWith("Source FLAC")) null else it.pairedFlacUri,
                        pairedFlacMetadata = if (deleteNote.startsWith("Source FLAC")) null else it.pairedFlacMetadata,
                        lastSummaryMessage = deleteNote.ifBlank { null }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        splitStep = SplitStep.SPLIT_HOME,
                        splitProgress = BatchProgress(isRunning = false),
                        lastSummaryMessage = "Split Error: ${e.localizedMessage ?: "Execution failed"}"
                    )
                }
            }
        }
    }

    private suspend fun deleteDocument(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri)
        }.getOrDefault(false)
    }

    /** Looks for the FLAC a CUE sheet refers to, in the CUE's own folder first, then the library. */
    private fun findPairedFlac(cueUri: Uri, sheet: CueSheet): Uri? {
        val flacs = _uiState.value.allTracks.map { it.original }.filter { it.format == "FLAC" }
        if (flacs.isEmpty()) return null
        fun base(name: String) = name.substringBeforeLast('.').lowercase()

        val cueParent = parentKey(cueUri)
        val sameFolder = if (cueParent != null) flacs.filter { parentKey(it.uri) == cueParent } else emptyList()
        val wanted = sheet.audioFileName
        for (pool in listOf(sameFolder, flacs)) {
            if (wanted.isNotBlank()) {
                pool.firstOrNull { it.fileName.equals(wanted, ignoreCase = true) }?.let { return it.uri }
                // CUE often says album.wav or album.ape while the file on disk is album.flac
                pool.firstOrNull { base(it.fileName) == base(wanted) }?.let { return it.uri }
            }
        }
        val cueName = DocumentFile.fromSingleUri(getApplication<Application>(), cueUri)?.name
        if (cueName != null) {
            sameFolder.firstOrNull { base(it.fileName) == base(cueName) }?.let { return it.uri }
        }
        if (sameFolder.size == 1) return sameFolder[0].uri
        return null
    }

    /** authority + parent path of a document uri, comparable across tree and single-document uris. */
    private fun parentKey(uri: Uri): String? = try {
        val id = DocumentsContract.getDocumentId(uri)
        val parent = id.substringBeforeLast('/', id.substringBefore(':') + ":")
        (uri.authority ?: "") + "|" + parent
    } catch (_: Exception) {
        null
    }

    // Opens newly split tracks straight in the Batch Tag Editor!
    fun openSplitFilesIntoTagEditor() {
        val folderUri = _uiState.value.splitOutputFolderUri ?: return
        addMusicFolder(folderUri, "Split Output Folder")
        _uiState.update {
            it.copy(
                activeTab = MainTab.TAGS,
                tagsStep = TagsStep.LIBRARY,
                splitStep = SplitStep.SPLIT_HOME
            )
        }
    }
}
