package com.example.model

import android.content.Context

/** Persists [AppSettings] so theme, templates and behaviour flags survive restarts. */
object SettingsStore {
    private const val PREFS = "tagcue_settings"

    fun load(context: Context): AppSettings {
        val d = AppSettings()
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppSettings(
            defaultFilenameTemplate = p.getString("filename", d.defaultFilenameTemplate) ?: d.defaultFilenameTemplate,
            defaultFolderTemplate = p.getString("folder", d.defaultFolderTemplate) ?: d.defaultFolderTemplate,
            defaultTrackNumberStyle = runCatching {
                TrackNumberStyle.valueOf(p.getString("style", d.defaultTrackNumberStyle.name) ?: d.defaultTrackNumberStyle.name)
            }.getOrDefault(d.defaultTrackNumberStyle),
            deleteSourceAfterSplit = p.getBoolean("deleteSource", d.deleteSourceAfterSplit),
            backupTagsBeforeWrite = p.getBoolean("backup", d.backupTagsBeforeWrite),
            autoTrimSpaces = p.getBoolean("trim", d.autoTrimSpaces),
            useMaterial3Dynamic = p.getBoolean("dynamic", d.useMaterial3Dynamic),
            darkTheme = p.getBoolean("dark", d.darkTheme),
            musicRootUri = p.getString("musicRootUri", null)
        )
    }

    fun save(context: Context, s: AppSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("filename", s.defaultFilenameTemplate)
            .putString("folder", s.defaultFolderTemplate)
            .putString("style", s.defaultTrackNumberStyle.name)
            .putBoolean("deleteSource", s.deleteSourceAfterSplit)
            .putBoolean("backup", s.backupTagsBeforeWrite)
            .putBoolean("trim", s.autoTrimSpaces)
            .putBoolean("dynamic", s.useMaterial3Dynamic)
            .putBoolean("dark", s.darkTheme)
            .putString("musicRootUri", s.musicRootUri)
            .apply()
    }
}
