package com.def.musicplayer.data

/**
 * Трек, зчитаний із MediaStore пристрою.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,   // content://media/external/audio/media/{id}
    val albumArtUri: String?  // content://media/external/audio/albumart/{albumId}, може бути null
)

/**
 * Перетворює трек на MediaItem з повними метаданими (назва/виконавець/обкладинка).
 * Це критично для екрана черги — без метаданих у MediaItem чергу неможливо було б
 * коректно відобразити, коли вона відхиляється від порядку в бібліотеці.
 */
fun Song.toMediaItem(): androidx.media3.common.MediaItem {
    val metadata = androidx.media3.common.MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .apply {
            albumArtUri?.let { setArtworkUri(android.net.Uri.parse(it)) }
        }
        .build()
    return androidx.media3.common.MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(contentUri)
        .setMediaMetadata(metadata)
        .build()
}
