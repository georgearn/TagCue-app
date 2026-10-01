package com.example.model

data class AppSettings(
    val defaultFilenameTemplate: String = "{track} - {title}",
    val defaultFolderTemplate: String = "{artist}/{year} - {album}",
    val defaultTrackNumberStyle: TrackNumberStyle = TrackNumberStyle.STYLE_01,
    val deleteSourceAfterSplit: Boolean = false,
    val backupTagsBeforeWrite: Boolean = true,
    val autoTrimSpaces: Boolean = true,
    val useMaterial3Dynamic: Boolean = false,
    val darkTheme: Boolean = true,
    val musicRootUri: String? = null
)
