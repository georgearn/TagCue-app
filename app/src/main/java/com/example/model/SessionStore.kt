package com.example.model

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** One track's unsaved work, keyed to the file it was made against. */
data class SavedEdit(
    val uri: Uri,
    val fileName: String,
    val sizeBytes: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val trackNumber: Int?,
    val totalTracks: Int?,
    val discNumber: Int?,
    val year: String,
    val genre: String,
    val selected: Boolean,
    val newFileName: String?,
    val newArt: ByteArray?,
    val removeArt: Boolean
)

data class SavedSession(val folderUri: Uri, val folderName: String, val edits: List<SavedEdit>)

/**
 * Remembers which folder is open and the pending (not yet written) edits, so a process restart
 * brings the library back as it was. The folder is re-scanned on restore; edits are laid over the
 * fresh scan and dropped for any file whose name or size has changed in the meantime.
 */
object SessionStore {
    private fun dir(context: Context) = File(context.filesDir, "session")

    fun clear(context: Context) {
        runCatching { dir(context).deleteRecursively() }
    }

    fun save(context: Context, folderUri: Uri, folderName: String, tracks: List<EditableTrackState>) {
        runCatching {
            val d = dir(context)
            d.mkdirs()
            val keepArt = HashSet<String>()
            val arr = JSONArray()
            for (t in tracks) {
                val dirty = t.isModified || t.pendingNewFileName != null || t.newAlbumArtBytes != null ||
                    t.removeAlbumArt || t.isSelectedForApply
                if (!dirty) continue
                val p = t.pending
                val o = JSONObject()
                    .put("uri", t.original.uri.toString())
                    .put("fileName", t.original.fileName)
                    .put("sizeBytes", t.original.sizeBytes)
                    .put("title", p.title).put("artist", p.artist).put("album", p.album)
                    .put("albumArtist", p.albumArtist).put("year", p.year).put("genre", p.genre)
                    .put("selected", t.isSelectedForApply)
                    .put("removeArt", t.removeAlbumArt)
                p.trackNumber?.let { o.put("trackNumber", it) }
                p.totalTracks?.let { o.put("totalTracks", it) }
                p.discNumber?.let { o.put("discNumber", it) }
                t.pendingNewFileName?.let { o.put("newFileName", it) }
                t.newAlbumArtBytes?.let { bytes ->
                    val name = "art_" + sha1(bytes)
                    val f = File(d, name)
                    if (!f.exists()) f.writeBytes(bytes)
                    keepArt.add(name)
                    o.put("art", name)
                }
                arr.put(o)
            }
            val root = JSONObject()
                .put("folderUri", folderUri.toString())
                .put("folderName", folderName)
                .put("edits", arr)
            val tmp = File(d, "index.json.tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(File(d, "index.json"))
            d.listFiles()?.forEach { if (it.name.startsWith("art_") && it.name !in keepArt) it.delete() }
        }
    }

    fun load(context: Context): SavedSession? = runCatching {
        val d = dir(context)
        val index = File(d, "index.json")
        if (!index.exists()) return null
        val root = JSONObject(index.readText())
        val arr = root.getJSONArray("edits")
        val edits = ArrayList<SavedEdit>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            edits.add(
                SavedEdit(
                    uri = Uri.parse(o.getString("uri")),
                    fileName = o.getString("fileName"),
                    sizeBytes = o.optLong("sizeBytes"),
                    title = o.optString("title"), artist = o.optString("artist"), album = o.optString("album"),
                    albumArtist = o.optString("albumArtist"),
                    trackNumber = if (o.has("trackNumber")) o.getInt("trackNumber") else null,
                    totalTracks = if (o.has("totalTracks")) o.getInt("totalTracks") else null,
                    discNumber = if (o.has("discNumber")) o.getInt("discNumber") else null,
                    year = o.optString("year"), genre = o.optString("genre"),
                    selected = o.optBoolean("selected"),
                    newFileName = if (o.has("newFileName")) o.getString("newFileName") else null,
                    newArt = if (o.has("art")) File(d, o.getString("art")).takeIf { it.exists() }?.readBytes() else null,
                    removeArt = o.optBoolean("removeArt")
                )
            )
        }
        SavedSession(Uri.parse(root.getString("folderUri")), root.optString("folderName", "Music Folder"), edits)
    }.getOrNull()

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
}
