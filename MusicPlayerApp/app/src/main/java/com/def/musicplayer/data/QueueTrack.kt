package com.def.musicplayer.data

/**
 * Трек у поточній черзі відтворення. На відміну від Song (який приходить із
 * бібліотеки), цей об'єкт реконструюється напряму з метаданих MediaItem у плеєрі —
 * тому коректно відображає чергу навіть після "Відтворити наступним"/"Додати в чергу",
 * коли порядок вже не збігається зі списком бібліотеки.
 */
data class QueueTrack(
    val id: Long,
    val contentUri: String,
    val title: String,
    val artist: String,
    val albumArtUri: String?
)
