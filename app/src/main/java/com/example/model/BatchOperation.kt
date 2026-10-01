package com.example.model

enum class TargetField(val displayName: String) {
    TITLE("Track Title"),
    ARTIST("Artist Name"),
    ALBUM("Album Title"),
    ALBUM_ARTIST("Album Artist"),
    YEAR("Year / Date"),
    GENRE("Genre"),
    TRACK_NUMBER("Track Number")
}

sealed class BatchAction {
    data class FindAndReplace(
        val field: TargetField,
        val search: String,
        val replacement: String,
        val matchCase: Boolean = false,
        val useRegex: Boolean = false
    ) : BatchAction()

    data class ChangeCase(
        val field: TargetField,
        val caseOption: CaseOption
    ) : BatchAction()

    data class SetUniformField(
        val field: TargetField,
        val value: String
    ) : BatchAction()

    data class AutoNumber(
        val startNumber: Int = 1,
        val padDigits: Int = 2,
        val setTotalTracks: Boolean = true,
        val totalTracks: Int? = null
    ) : BatchAction()

    data class ExtractFromFilename(
        val pattern: String = "%n - %a - %t" // e.g. %n - %t, %a - %t
    ) : BatchAction()

    data class RenameFilesFromTags(
        val pattern: String = "%n - %a - %t"
    ) : BatchAction()

    data class SetAlbumArt(
        val imageBytes: ByteArray,
        val mimeType: String = "image/jpeg"
    ) : BatchAction() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as SetAlbumArt
            return mimeType == other.mimeType && imageBytes.contentEquals(other.imageBytes)
        }
        override fun hashCode(): Int {
            return 31 * mimeType.hashCode() + imageBytes.contentHashCode()
        }
    }

    object RemoveAlbumArt : BatchAction()

    object ClearPendingEdits : BatchAction()
}

data class FieldDiff(
    val fieldName: String,
    val before: String,
    val after: String
) {
    val isChanged: Boolean = before != after
}
