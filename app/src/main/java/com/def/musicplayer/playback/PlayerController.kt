package com.def.musicplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.def.musicplayer.data.QueueTrack
import com.def.musicplayer.data.Song
import com.def.musicplayer.data.toMediaItem
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Реальний стан плеєра, яким живиться весь UI: isPlaying, позиція, індекс у черзі,
 * режими shuffle/repeat. Оновлюється через Player.Listener, а не читається "наосліп".
 */
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val currentMediaItemIndex: Int = -1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
)

/**
 * Обгортка над MediaController для виклику з Compose UI.
 */
class PlayerController(private val context: Context, private val scope: CoroutineScope) {

    var controller: MediaController? = null
        private set

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // Реальна черга відтворення — реконструйована з метаданих MediaItem,
    // тому коректна навіть після ручних змін через "Відтворити наступним"/"Додати в чергу".
    private val _queue = MutableStateFlow<List<QueueTrack>>(emptyList())
    val queue: StateFlow<List<QueueTrack>> = _queue.asStateFlow()

    // Sleep timer — скільки мілісекунд лишилось до автопаузи, null = таймер вимкнено
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()
    private var sleepTimerJob: Job? = null

    private var positionTickerJob: Job? = null

    fun connect(onConnected: () -> Unit) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            controller = future.get()
            attachListener()
            onConnected()
        }, MoreExecutors.directExecutor())
    }

    private fun attachListener() {
        val c = controller ?: return
        c.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                if (isPlaying) startPositionTicker() else stopPositionTicker()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _uiState.value = _uiState.value.copy(
                    currentMediaItemIndex = c.currentMediaItemIndex,
                    positionMs = 0L,
                    durationMs = c.duration.coerceAtLeast(0L)
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _uiState.value = _uiState.value.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    durationMs = c.duration.coerceAtLeast(0L)
                )
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _uiState.value = _uiState.value.copy(shuffleEnabled = shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _uiState.value = _uiState.value.copy(repeatMode = repeatMode)
            }

            // Черга змінилась (додали/прибрали/переставили трек) — перебудовуємо список
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                rebuildQueue()
            }
        })
        rebuildQueue()

        // Одразу зчитуємо поточний стан сесії (isPlaying/позиція/shuffle/repeat) —
        // якщо сервіс відновив чергу з диска ДО підключення контролера, без цього
        // UI просто не дізнався б про це, бо чекав би на майбутні зміни, а стан
        // на момент підключення вже "усталений" і жодних подій не викликає.
        _uiState.value = _uiState.value.copy(
            isPlaying = c.isPlaying,
            currentMediaItemIndex = c.currentMediaItemIndex,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.coerceAtLeast(0L),
            shuffleEnabled = c.shuffleModeEnabled,
            repeatMode = c.repeatMode
        )
    }

    private fun rebuildQueue() {
        val c = controller ?: return
        val items = mutableListOf<QueueTrack>()
        for (i in 0 until c.mediaItemCount) {
            val mediaItem = c.getMediaItemAt(i)
            val metadata = mediaItem.mediaMetadata
            items += QueueTrack(
                id = mediaItem.mediaId.toLongOrNull() ?: i.toLong(),
                contentUri = mediaItem.localConfiguration?.uri?.toString() ?: "",
                title = metadata.title?.toString() ?: "Невідомий трек",
                artist = metadata.artist?.toString() ?: "",
                albumArtUri = metadata.artworkUri?.toString()
            )
        }
        _queue.value = items
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (true) {
                val c = controller
                if (c != null) {
                    _uiState.value = _uiState.value.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0L),
                        durationMs = c.duration.coerceAtLeast(0L)
                    )
                }
                delay(300)
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    fun setQueue(songs: List<Song>, startIndex: Int) {
        val items = songs.map { it.toMediaItem() }
        controller?.apply {
            setMediaItems(items, startIndex, 0L)
            prepare()
            play()
        }
        _uiState.value = _uiState.value.copy(currentMediaItemIndex = startIndex, positionMs = 0L)
    }

    fun playPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()

    /** Перехід одразу на конкретний трек у черзі (використовується екраном черги). */
    fun seekToQueueIndex(index: Int) {
        controller?.seekTo(index, 0L)
        controller?.play()
    }

    /** Додає трек одразу після поточного — пункт меню "Відтворити наступним". */
    fun playNext(song: Song) {
        val c = controller ?: return
        val insertIndex = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
        c.addMediaItem(insertIndex, song.toMediaItem())
    }

    /** Додає трек у кінець черги — пункт меню "Додати в чергу". */
    fun addToQueue(song: Song) {
        controller?.addMediaItem(song.toMediaItem())
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    /** Циклічно перемикає режим повтору: вимкнено → всі → один трек → вимкнено. */
    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Перетягування повзунка прогресу — миттєво оновлює UI, не чекаючи наступного тіку. */
    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(positionMs = positionMs)
    }

    /** Запускає таймер сну: через durationMinutes хвилин плеєр сам поставить паузу. */
    fun startSleepTimer(durationMinutes: Int) {
        sleepTimerJob?.cancel()
        val totalMs = durationMinutes * 60_000L
        _sleepTimerRemainingMs.value = totalMs
        sleepTimerJob = scope.launch {
            var remaining = totalMs
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                _sleepTimerRemainingMs.value = remaining.coerceAtLeast(0L)
            }
            controller?.pause()
            _sleepTimerRemainingMs.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = null
    }

    fun release() {
        stopPositionTicker()
        sleepTimerJob?.cancel()
        controller?.release()
        controller = null
    }
}
