package com.def.musicplayer.ui

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.def.musicplayer.data.MusicLibrary
import com.def.musicplayer.data.QueueTrack
import com.def.musicplayer.data.SettingsStore
import com.def.musicplayer.data.Song
import com.def.musicplayer.data.db.AppDatabase
import com.def.musicplayer.data.db.FavoriteEntity
import com.def.musicplayer.data.db.LyricsEntity
import com.def.musicplayer.data.db.PlaylistEntity
import com.def.musicplayer.data.db.PlaylistSongCrossRef
import com.def.musicplayer.playback.EqualizerUiState
import com.def.musicplayer.playback.PlaybackService
import com.def.musicplayer.playback.PlayerController
import com.def.musicplayer.playback.PlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.get(application)
    private val settings = SettingsStore(application)
    val playerController = PlayerController(application, viewModelScope)

    val playerState: StateFlow<PlayerUiState> = playerController.uiState
    val queue: StateFlow<List<QueueTrack>> = playerController.queue
    val sleepTimerRemainingMs: StateFlow<Long?> = playerController.sleepTimerRemainingMs

    // Еквалайзер живе у PlaybackService (там, де реальний ExoPlayer і audioSessionId) —
    // цей StateFlow просто прокидує його стан в UI, оскільки сервіс працює в тому ж процесі.
    val equalizerState: StateFlow<EqualizerUiState?> = PlaybackService.eqState

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(settings.isDarkTheme)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _accentColor = MutableStateFlow(Color(settings.accentColorArgb))
    val accentColor: StateFlow<Color> = _accentColor.asStateFlow()

    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> = _playlists.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    init {
        playerController.connect {}
        viewModelScope.launch {
            db.playlistDao().observeAll().collect { _playlists.value = it }
        }
        viewModelScope.launch {
            db.favoriteDao().observeAllIds().collect { _favoriteIds.value = it.toSet() }
        }
    }

    // Поточний трек тепер береться з РЕАЛЬНОЇ черги плеєра (queue), а не зі списку
    // бібліотеки за індексом — раніше це могло розсинхронитись після "Відтворити наступним".
    val currentTrack: StateFlow<QueueTrack?> = combine(
        queue, playerState
    ) { q, state ->
        q.getOrNull(state.currentMediaItemIndex)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Автоматичний "плейлист" улюблених — просто похідний фільтр списку бібліотеки
    // за favoriteIds, завжди актуальний, нічого окремо зберігати не треба.
    val favoriteSongs: StateFlow<List<Song>> = combine(
        songs, favoriteIds
    ) { list, ids ->
        list.filter { it.id in ids }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
        settings.isDarkTheme = _isDarkTheme.value
    }

    fun setAccentColor(color: Color) {
        _accentColor.value = color
        settings.accentColorArgb = color.toArgb()
    }

    fun loadLibrary() {
        viewModelScope.launch {
            _songs.value = MusicLibrary.scan(getApplication())
        }
    }

    fun playSongAt(index: Int) {
        val list = _songs.value
        if (index !in list.indices) return
        playerController.setQueue(list, index)
    }

    /** Відтворення довільного списку (наприклад, улюблених) як окремої черги. */
    fun playQueue(list: List<Song>, startIndex: Int) {
        if (startIndex in list.indices) playerController.setQueue(list, startIndex)
    }

    fun playPause() = playerController.playPause()
    fun next() = playerController.next()
    fun previous() = playerController.previous()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun seekToQueueIndex(index: Int) = playerController.seekToQueueIndex(index)
    fun playNext(song: Song) = playerController.playNext(song)
    fun addToQueue(song: Song) = playerController.addToQueue(song)
    fun toggleShuffle() = playerController.toggleShuffle()
    fun cycleRepeatMode() = playerController.cycleRepeatMode()
    fun startSleepTimer(minutes: Int) = playerController.startSleepTimer(minutes)
    fun cancelSleepTimer() = playerController.cancelSleepTimer()

    // --- Еквалайзер ---
    fun setEqualizerEnabled(enabled: Boolean) = PlaybackService.setEqualizerEnabled(enabled)
    fun setEqualizerBand(band: Short, level: Short) = PlaybackService.setEqualizerBandLevel(band, level)
    fun useEqualizerPreset(preset: Short) = PlaybackService.useEqualizerPreset(preset)

    // --- Улюблені ---
    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            if (trackId in _favoriteIds.value) {
                db.favoriteDao().remove(trackId)
            } else {
                db.favoriteDao().add(FavoriteEntity(trackId = trackId))
            }
        }
    }

    // --- Лірики (ручне додавання/редагування) ---
    suspend fun getLyrics(trackId: Long): String =
        db.lyricsDao().get(trackId)?.text ?: ""

    fun saveLyrics(trackId: Long, text: String) {
        viewModelScope.launch {
            db.lyricsDao().upsert(LyricsEntity(trackId = trackId, text = text))
        }
    }

    // --- Плейлисти ---
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            db.playlistDao().createPlaylist(PlaylistEntity(name = name))
        }
    }

    fun addSongToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            val existing = db.playlistDao().getTrackIds(playlistId)
            db.playlistDao().addSong(
                PlaylistSongCrossRef(playlistId = playlistId, trackId = trackId, position = existing.size)
            )
        }
    }

    override fun onCleared() {
        playerController.release()
        super.onCleared()
    }
}
