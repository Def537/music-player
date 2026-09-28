package com.def.musicplayer.data

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

/**
 * Мінімальний знімок черги, достатній для повного відновлення відтворення
 * (пісня, позиція, shuffle/repeat) після того, як Android вбив процес сервісу —
 * а це стається доволі часто, щойно відтворення на паузі й сервіс перестає
 * бути foreground.
 */
data class QueueSnapshot(
    val tracks: List<QueueTrack>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
    val wasPlaying: Boolean
)

fun QueueTrack.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .apply {
            albumArtUri?.let { setArtworkUri(android.net.Uri.parse(it)) }
        }
        .build()
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(contentUri)
        .setMediaMetadata(metadata)
        .build()
}

object QueuePersistence {

    fun encode(snapshot: QueueSnapshot): String {
        val root = JSONObject()
        root.put("index", snapshot.currentIndex)
        root.put("position", snapshot.positionMs)
        root.put("shuffle", snapshot.shuffleEnabled)
        root.put("repeat", snapshot.repeatMode)
        root.put("wasPlaying", snapshot.wasPlaying)
        val arr = JSONArray()
        snapshot.tracks.forEach { t ->
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("uri", t.contentUri)
            obj.put("title", t.title)
            obj.put("artist", t.artist)
            obj.put("art", t.albumArtUri ?: "")
            arr.put(obj)
        }
        root.put("tracks", arr)
        return root.toString()
    }

    fun decode(json: String?): QueueSnapshot? {
        if (json.isNullOrBlank()) return null
        return try {
            val root = JSONObject(json)
            val arr = root.getJSONArray("tracks")
            val tracks = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                QueueTrack(
                    id = obj.getLong("id"),
                    contentUri = obj.getString("uri"),
                    title = obj.getString("title"),
                    artist = obj.getString("artist"),
                    albumArtUri = obj.getString("art").ifBlank { null }
                )
            }
            if (tracks.isEmpty()) return null
            QueueSnapshot(
                tracks = tracks,
                currentIndex = root.optInt("index", 0).coerceIn(0, tracks.lastIndex),
                positionMs = root.optLong("position", 0L),
                shuffleEnabled = root.optBoolean("shuffle", false),
                repeatMode = root.optInt("repeat", 0),
                wasPlaying = root.optBoolean("wasPlaying", false)
            )
        } catch (e: Exception) {
            null
        }
    }
}
