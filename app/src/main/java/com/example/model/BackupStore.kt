package com.example.model

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Keeps the last tag backup on disk so "Undo last batch" still works after the app restarts.
 * Cover art is stored once per distinct image.
 */
object BackupStore {
    private fun dir(context: Context) = File(context.filesDir, "tag_backup")

    fun clear(context: Context) {
        runCatching { dir(context).deleteRecursively() }
    }

    fun save(context: Context, backup: TagBackup) {
        runCatching {
            val d = dir(context)
            d.deleteRecursively()
            d.mkdirs()
            val entries = JSONArray()
            for ((_, m) in backup.snapshot) {
                val o = JSONObject()
                o.put("uri", m.uri.toString())
                o.put("fileName", m.fileName)
                o.put("mimeType", m.mimeType)
                o.put("sizeBytes", m.sizeBytes)
                o.put("title", m.title)
                o.put("artist", m.artist)
                o.put("album", m.album)
                o.put("albumArtist", m.albumArtist)
                m.trackNumber?.let { o.put("trackNumber", it) }
                m.totalTracks?.let { o.put("totalTracks", it) }
                m.discNumber?.let { o.put("discNumber", it) }
                o.put("year", m.year)
                o.put("genre", m.genre)
                o.put("durationMs", m.durationMs)
                o.put("bitRate", m.bitRate)
                o.put("sampleRate", m.sampleRate)
                o.put("hasEmbeddedArt", m.hasEmbeddedArt)
                o.put("albumArtMime", m.albumArtMime)
                m.folderUri?.let { o.put("folderUri", it.toString()) }
                o.put("folderName", m.folderName)
                m.albumArtBytes?.let { bytes ->
                    val name = "art_" + sha1(bytes)
                    val f = File(d, name)
                    if (!f.exists()) f.writeBytes(bytes)
                    o.put("art", name)
                }
                entries.put(o)
            }
            val renames = JSONArray()
            for ((from, to) in backup.renames) {
                renames.put(JSONObject().put("from", from.toString()).put("to", to.toString()))
            }
            val root = JSONObject()
                .put("timestamp", backup.timestamp)
                .put("entries", entries)
                .put("renames", renames)
            // Write the index last: a half-written backup has no index and is ignored.
            File(d, "index.json").writeText(root.toString())
        }
    }

    fun load(context: Context): TagBackup? {
        return runCatching {
            val d = dir(context)
            val index = File(d, "index.json")
            if (!index.exists()) return null
            val root = JSONObject(index.readText())
            val entries = root.getJSONArray("entries")
            val snapshot = LinkedHashMap<Uri, AudioMetadata>()
            for (i in 0 until entries.length()) {
                val o = entries.getJSONObject(i)
                val uri = Uri.parse(o.getString("uri"))
                val art = if (o.has("art")) File(d, o.getString("art")).takeIf { it.exists() }?.readBytes() else null
                snapshot[uri] = AudioMetadata(
                    uri = uri,
                    fileName = o.getString("fileName"),
                    mimeType = o.getString("mimeType"),
                    sizeBytes = o.optLong("sizeBytes"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    album = o.optString("album"),
                    albumArtist = o.optString("albumArtist"),
                    trackNumber = if (o.has("trackNumber")) o.getInt("trackNumber") else null,
                    totalTracks = if (o.has("totalTracks")) o.getInt("totalTracks") else null,
                    discNumber = if (o.has("discNumber")) o.getInt("discNumber") else null,
                    year = o.optString("year"),
                    genre = o.optString("genre"),
                    durationMs = o.optLong("durationMs"),
                    bitRate = o.optInt("bitRate"),
                    sampleRate = o.optInt("sampleRate"),
                    hasEmbeddedArt = o.optBoolean("hasEmbeddedArt"),
                    albumArtBytes = art,
                    albumArtMime = o.optString("albumArtMime", "image/jpeg"),
                    folderUri = if (o.has("folderUri")) Uri.parse(o.getString("folderUri")) else null,
                    folderName = o.optString("folderName")
                )
            }
            val renames = LinkedHashMap<Uri, Uri>()
            val rn = root.optJSONArray("renames")
            if (rn != null) for (i in 0 until rn.length()) {
                val o = rn.getJSONObject(i)
                renames[Uri.parse(o.getString("from"))] = Uri.parse(o.getString("to"))
            }
            if (snapshot.isEmpty()) null else TagBackup(root.optLong("timestamp"), snapshot, renames)
        }.getOrNull()
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
}
