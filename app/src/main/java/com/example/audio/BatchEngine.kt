package com.example.audio

import com.example.model.AudioMetadata
import com.example.model.CaseOption
import com.example.model.EditableTrackState
import com.example.model.SplitOutputConfig
import com.example.model.TargetField

object BatchEngine {

    fun findAndReplace(
        tracks: List<EditableTrackState>,
        field: TargetField,
        search: String,
        replacement: String,
        matchCase: Boolean,
        useRegex: Boolean
    ): List<EditableTrackState> {
        if (search.isEmpty()) return tracks

        return tracks.map { track ->
            if (!track.isSelectedForApply) return@map track

            val currentPending = track.pending
            val updatedPending = when (field) {
                TargetField.TITLE -> currentPending.copy(
                    title = replaceString(currentPending.title, search, replacement, matchCase, useRegex)
                )
                TargetField.ARTIST -> currentPending.copy(
                    artist = replaceString(currentPending.artist, search, replacement, matchCase, useRegex)
                )
                TargetField.ALBUM -> currentPending.copy(
                    album = replaceString(currentPending.album, search, replacement, matchCase, useRegex)
                )
                TargetField.ALBUM_ARTIST -> currentPending.copy(
                    albumArtist = replaceString(currentPending.albumArtist, search, replacement, matchCase, useRegex)
                )
                TargetField.YEAR -> currentPending.copy(
                    year = replaceString(currentPending.year, search, replacement, matchCase, useRegex)
                )
                TargetField.GENRE -> currentPending.copy(
                    genre = replaceString(currentPending.genre, search, replacement, matchCase, useRegex)
                )
                TargetField.TRACK_NUMBER -> currentPending
            }
            track.copy(pending = updatedPending)
        }
    }

    private fun replaceString(
        target: String,
        search: String,
        replacement: String,
        matchCase: Boolean,
        useRegex: Boolean
    ): String {
        return try {
            if (useRegex) {
                val regexOption = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(search, regexOption).replace(target, replacement)
            } else {
                target.replace(search, replacement, ignoreCase = !matchCase)
            }
        } catch (_: Exception) {
            target
        }
    }

    fun changeCase(
        tracks: List<EditableTrackState>,
        field: TargetField,
        caseOption: CaseOption
    ): List<EditableTrackState> {
        return tracks.map { track ->
            if (!track.isSelectedForApply) return@map track

            val currentPending = track.pending
            val transform = { s: String ->
                when (caseOption) {
                    CaseOption.ORIGINAL -> s
                    CaseOption.TITLE_CASE -> SplitOutputConfig.toTitleCase(s)
                    CaseOption.UPPERCASE -> s.uppercase()
                    CaseOption.LOWERCASE -> s.lowercase()
                    CaseOption.SENTENCE_CASE -> SplitOutputConfig.toSentenceCase(s)
                }
            }

            val updatedPending = when (field) {
                TargetField.TITLE -> currentPending.copy(title = transform(currentPending.title))
                TargetField.ARTIST -> currentPending.copy(artist = transform(currentPending.artist))
                TargetField.ALBUM -> currentPending.copy(album = transform(currentPending.album))
                TargetField.ALBUM_ARTIST -> currentPending.copy(albumArtist = transform(currentPending.albumArtist))
                TargetField.YEAR -> currentPending
                TargetField.GENRE -> currentPending.copy(genre = transform(currentPending.genre))
                TargetField.TRACK_NUMBER -> currentPending
            }
            track.copy(pending = updatedPending)
        }
    }

    fun setUniformValue(
        tracks: List<EditableTrackState>,
        field: TargetField,
        value: String
    ): List<EditableTrackState> {
        return tracks.map { track ->
            if (!track.isSelectedForApply) return@map track

            val currentPending = track.pending
            val updatedPending = when (field) {
                TargetField.TITLE -> currentPending.copy(title = value)
                TargetField.ARTIST -> currentPending.copy(artist = value)
                TargetField.ALBUM -> currentPending.copy(album = value)
                TargetField.ALBUM_ARTIST -> currentPending.copy(albumArtist = value)
                TargetField.YEAR -> currentPending.copy(year = value)
                TargetField.GENRE -> currentPending.copy(genre = value)
                TargetField.TRACK_NUMBER -> currentPending.copy(trackNumber = value.toIntOrNull())
            }
            track.copy(pending = updatedPending)
        }
    }

    fun autoNumber(
        tracks: List<EditableTrackState>,
        startNumber: Int = 1,
        setTotalTracks: Boolean = true
    ): List<EditableTrackState> {
        val selectedTracks = tracks.filter { it.isSelectedForApply }
        val totalCount = if (setTotalTracks) selectedTracks.size else null

        var counter = startNumber
        return tracks.map { track ->
            if (!track.isSelectedForApply) return@map track

            val updatedPending = track.pending.copy(
                trackNumber = counter,
                totalTracks = totalCount ?: track.pending.totalTracks
            )
            counter++
            track.copy(pending = updatedPending)
        }
    }

    fun extractFromFilename(
        tracks: List<EditableTrackState>,
        pattern: String = "%n - %a - %t"
    ): List<EditableTrackState> {
        return tracks.map { track ->
            if (!track.isSelectedForApply) return@map track

            val baseName = track.original.fileName.substringBeforeLast(".")
            val parsed = parseFilenameWithPattern(baseName, pattern)

            val updatedPending = track.pending.copy(
                title = parsed.title ?: track.pending.title,
                artist = parsed.artist ?: track.pending.artist,
                album = parsed.album ?: track.pending.album,
                trackNumber = parsed.trackNumber ?: track.pending.trackNumber,
                year = parsed.year ?: track.pending.year
            )
            track.copy(pending = updatedPending)
        }
    }

    data class ParsedFilename(
        val trackNumber: Int? = null,
        val artist: String? = null,
        val album: String? = null,
        val title: String? = null,
        val year: String? = null
    )

    fun parseFilenameWithPattern(filename: String, pattern: String): ParsedFilename {
        try {
            val normalizedPattern = pattern
                .replace("{track}", "%n")
                .replace("{title}", "%t")
                .replace("{artist}", "%a")
                .replace("{album}", "%l")
                .replace("{year}", "%y")

            val tokenPattern = Regex("%(?:0[23])?[natAlby]")
            val tokens = mutableListOf<String>()
            val regexBuilder = StringBuilder("^")
            var lastIdx = 0

            for (match in tokenPattern.findAll(normalizedPattern)) {
                val literal = normalizedPattern.substring(lastIdx, match.range.first)
                regexBuilder.append(Regex.escape(literal))
                val token = match.value
                tokens.add(token)
                if (token == "%t") {
                    regexBuilder.append("(.+)")
                } else if (token.contains("n")) {
                    regexBuilder.append("(\\d+)")
                } else if (token == "%y") {
                    regexBuilder.append("(\\d{4})")
                } else {
                    regexBuilder.append("(.+?)")
                }
                lastIdx = match.range.last + 1
            }
            if (lastIdx < normalizedPattern.length) {
                regexBuilder.append(Regex.escape(normalizedPattern.substring(lastIdx)))
            }
            regexBuilder.append("$")

            val regex = Regex(regexBuilder.toString(), RegexOption.IGNORE_CASE)
            val match = regex.find(filename.trim()) ?: return ParsedFilename()

            var trackNum: Int? = null
            var artist: String? = null
            var album: String? = null
            var title: String? = null
            var year: String? = null

            for (i in tokens.indices) {
                val token = tokens[i]
                val groupVal = match.groupValues.getOrNull(i + 1)?.trim() ?: continue
                when {
                    token.contains("n") -> trackNum = groupVal.toIntOrNull()
                    token == "%a" || token == "%A" -> artist = groupVal
                    token == "%l" || token == "%b" -> album = groupVal
                    token == "%t" -> title = groupVal
                    token == "%y" -> year = groupVal
                }
            }

            return ParsedFilename(trackNumber = trackNum, artist = artist, album = album, title = title, year = year)
        } catch (_: Exception) {
            return ParsedFilename()
        }
    }

    /**
     * Builds new file names from the pending tags of the selected tracks.
     * Tokens: %n track number (2 digits), %t title, %a artist, %b/%l album, %y year.
     * Extension is kept. Names are sanitised and made unique within each folder.
     */
    fun renameFilesFromTags(
        tracks: List<EditableTrackState>,
        pattern: String
    ): List<EditableTrackState> {
        val taken = mutableMapOf<String, MutableSet<String>>()
        // Files that are not renamed keep their names, so those names are off limits.
        for (t in tracks) {
            if (!t.isSelectedForApply) {
                taken.getOrPut(t.original.folderUri?.toString() ?: "") { mutableSetOf() }
                    .add((t.pendingNewFileName ?: t.original.fileName).lowercase())
            }
        }
        val illegal = Regex("[\\\\/:*?\"<>|]")
        val spaces = Regex("\\s+")
        return tracks.map { track ->
            if (!track.isSelectedForApply) return@map track
            val p = track.pending
            val ext = track.original.fileName.substringAfterLast('.', "")
            val number = (p.trackNumber ?: 0).let { if (it > 0) it.toString().padStart(2, '0') else "" }
            var base = pattern
                .replace("%n", number)
                .replace("%t", p.title)
                .replace("%a", p.artist.ifBlank { p.albumArtist })
                .replace("%b", p.album)
                .replace("%l", p.album)
                .replace("%y", p.year)
            base = base.replace(illegal, "_").replace(spaces, " ").trim().trim('.', '-', ' ')
            if (base.isBlank()) base = track.original.fileName.substringBeforeLast('.')
            val used = taken.getOrPut(track.original.folderUri?.toString() ?: "") { mutableSetOf() }
            var candidate = if (ext.isNotEmpty()) "$base.$ext" else base
            var n = 2
            while (!used.add(candidate.lowercase())) {
                candidate = if (ext.isNotEmpty()) "$base ($n).$ext" else "$base ($n)"
                n++
            }
            track.copy(pendingNewFileName = if (candidate == track.original.fileName) null else candidate)
        }
    }

    fun applyAlbumArt(
        tracks: List<EditableTrackState>,
        artBytes: ByteArray?
    ): List<EditableTrackState> {
        return tracks.map { track ->
            if (!track.isSelectedForApply) return@map track
            if (artBytes == null) {
                track.copy(removeAlbumArt = true, newAlbumArtBytes = null)
            } else {
                track.copy(removeAlbumArt = false, newAlbumArtBytes = artBytes)
            }
        }
    }
}
