package com.def.musicplayer.playback

import android.content.Intent
import android.media.audiofx.Equalizer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.def.musicplayer.R
import com.def.musicplayer.data.QueuePersistence
import com.def.musicplayer.data.QueueSnapshot
import com.def.musicplayer.data.QueueTrack
import com.def.musicplayer.data.SettingsStore
import com.def.musicplayer.data.toMediaItem
import com.def.musicplayer.widget.MusicWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Одна смуга еквалайзера: частота-мітка + поточний і допустимий рівень (у мілібелах). */
data class EqBand(
    val index: Short,
    val frequencyLabel: String,
    val levelMillibels: Short,
    val minLevel: Short,
    val maxLevel: Short
)

data class EqualizerUiState(
    val enabled: Boolean,
    val bands: List<EqBand>,
    val presets: List<String>,
    val currentPreset: Short
)

/**
 * Сервіс, що керує ExoPlayer і показує керування у шторці/на екрані блокування.
 * Ніякого стороннього SDK для реклами тут немає і не буде.
 *
 * Еквалайзер живе саме тут (а не в PlayerController), бо для android.media.audiofx.Equalizer
 * потрібен реальний audioSessionId ExoPlayer — MediaController (клієнтська сторона)
 * прямого доступу до нього не дає. Оскільки сервіс працює в тому ж процесі, що й
 * Activity (немає android:process у маніфесті), UI читає стан напряму через
 * companion object — це набагато простіше за повноцінні SessionCommand для одного застосунку.
 *
 * ВАЖЛИВО: Android регулярно вбиває процес фонового сервісу, щойно відтворення стає на
 * паузу (сервіс перестає бути foreground) — це нормальна поведінка ОС, а не баг. Тому
 * чергу/позицію/shuffle/repeat зберігаємо на диск при КОЖНІЙ значущій зміні й відновлюємо
 * при наступному старті сервісу, інакше застосунок після повернення виглядав би так,
 * ніби "забув" усе.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var settings: SettingsStore
    // ВАЖЛИВО: саме тут раніше був критичний баг. ExoPlayer/MediaSession дозволяють
    // звертатись до себе ЛИШЕ з головного потоку застосунку — Dispatchers.Default
    // (фоновий пул потоків) призводив до IllegalStateException і краху щоразу,
    // коли періодичне збереження стану намагалось прочитати стан плеєра під час
    // відтворення. Main.immediate тут абсолютно безпечний: Service і так живе
    // на головному потоці, а saveState() не робить нічого важкого/блокуючого.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionSaveJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)

        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()

        restoreQueueIfPossible(player)

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupEqualizer(audioSessionId)
            }
        })
        // На випадок якщо сесія вже готова на момент підписки
        if (player.audioSessionId != 0) setupEqualizer(player.audioSessionId)

        // Тримаємо віджет на головному екрані в актуальному стані
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = updateWidget()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateWidget()
        })

        // Зберігаємо стан при кожній значущій зміні — плюс періодично поки грає,
        // щоб позиція не "застрягала" надто застарілою, якщо процес вб'ють раптово.
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                saveState()
                if (isPlaying) startPeriodicSave() else stopPeriodicSave()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = saveState()
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = saveState()
            override fun onRepeatModeChanged(repeatMode: Int) = saveState()
        })
    }

    private fun restoreQueueIfPossible(player: ExoPlayer) {
        val snapshot = QueuePersistence.decode(settings.lastQueueJson) ?: return
        try {
            val items = snapshot.tracks.map { it.toMediaItem() }
            player.setMediaItems(items, snapshot.currentIndex, snapshot.positionMs)
            player.shuffleModeEnabled = snapshot.shuffleEnabled
            player.repeatMode = snapshot.repeatMode
            // Якщо сервіс загинув просто під час відтворення (система вбила процес,
            // а не користувач поставив паузу) — відновлюємо саме відтворення, інакше
            // музика мовчки "помре" й застрягне на паузі, хоча мала б грати далі.
            player.playWhenReady = snapshot.wasPlaying
            player.prepare()
        } catch (e: Exception) {
            // Пошкоджений або застарілий знімок — просто ігноруємо, почнемо з чистого стану
        }
    }

    private fun saveState() {
        val player = mediaSession?.player ?: return
        try {
            if (player.mediaItemCount == 0) return
            val tracks = (0 until player.mediaItemCount).map { i ->
                val item = player.getMediaItemAt(i)
                val metadata = item.mediaMetadata
                QueueTrack(
                    id = item.mediaId.toLongOrNull() ?: i.toLong(),
                    contentUri = item.localConfiguration?.uri?.toString() ?: "",
                    title = metadata.title?.toString() ?: "Невідомий трек",
                    artist = metadata.artist?.toString() ?: "",
                    albumArtUri = metadata.artworkUri?.toString()
                )
            }
            val snapshot = QueueSnapshot(
                tracks = tracks,
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                positionMs = player.currentPosition.coerceAtLeast(0L),
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                wasPlaying = player.playWhenReady
            )
            settings.lastQueueJson = QueuePersistence.encode(snapshot)
        } catch (e: Exception) {
            // Не даємо збереженню стану впасти і потягти за собою весь застосунок
        }
    }

    private fun startPeriodicSave() {
        positionSaveJob?.cancel()
        positionSaveJob = serviceScope.launch {
            while (true) {
                // Раз на 2 секунди, а не 5 — щоб позиція не застрягала на старому
                // значенні, якщо процес вб'ють раптово буквально за кілька секунд
                // після початку відтворення.
                delay(2000)
                saveState()
            }
        }
    }

    private fun stopPeriodicSave() {
        positionSaveJob?.cancel()
        positionSaveJob = null
    }

    private fun updateWidget() {
        val player = mediaSession?.player ?: return
        val metadata = player.currentMediaItem?.mediaMetadata
        val title = metadata?.title?.toString() ?: "Немає відтворення"
        val artist = metadata?.artist?.toString() ?: ""
        MusicWidgetProvider.updateWidgets(this, title, artist, player.isPlaying)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = mediaSession?.player

        // КОРІННИЙ ФІКС: нас підняли через startForegroundService() (з боку віджета) —
        // система ЗАВЖДИ вимагає startForeground() протягом кількох секунд, інакше
        // або кине ForegroundServiceDidNotStartInTimeException, або (як у цьому
        // конкретному репорті) просто вб'є процес як недостатньо захищений фоновий,
        // щойно екран блокується. Раніше явний startForeground() викликався лише
        // в гілці "черга порожня" — у гілці РЕАЛЬНОГО відтворення покладались на
        // автоматичне сповіщення MediaSessionService, яке, судячи з усього, не
        // встигає/не завжди спрацьовує, коли сервіс піднятий напряму через Intent
        // від віджета (без жодного прив'язаного MediaController — саме цей шлях
        // не проходить через звичайний controller-binding сценарій, під який
        // заточений автоматичний механізм бібліотеки). Тепер викликаємо це
        // ЗАВЖДИ, одразу, для будь-якої команди від віджета — а MediaSessionService,
        // якщо й коли розпізнає активне відтворення, просто замінить це власним
        // повноцінним сповіщенням (це безпечний, стандартний патерн).
        if (intent?.action == MusicWidgetProvider.ACTION_PLAY_PAUSE ||
            intent?.action == MusicWidgetProvider.ACTION_NEXT ||
            intent?.action == MusicWidgetProvider.ACTION_PREVIOUS
        ) {
            ensureForegroundNotification(player)
        }

        when (intent?.action) {
            MusicWidgetProvider.ACTION_PLAY_PAUSE -> {
                if (player == null || player.mediaItemCount == 0) {
                    // Ще нічого не відтворювалось — у черзі порожньо, тож просто
                    // вмикати "play" нема чого. Відкриваємо застосунок, щоб можна
                    // було обрати трек, замість того, щоб кнопка виглядала мертвою.
                    startActivity(
                        Intent(this, com.def.musicplayer.MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    // Захисний prepare() — якщо плеєр з якоїсь причини опинився в
                    // стані IDLE (не підготовлений), play() сам по собі нічого не
                    // зробить. prepare() на вже підготовленому плеєрі — безпечний no-op.
                    player.prepare()
                    if (player.isPlaying) player.pause() else player.play()
                }
            }
            MusicWidgetProvider.ACTION_NEXT -> player?.seekToNextMediaItem()
            MusicWidgetProvider.ACTION_PREVIOUS -> player?.seekToPreviousMediaItem()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Гарантує, що сервіс формально й негайно стає foreground — незалежно від того,
     * чи встигне/зможе MediaSessionService зробити це сам через свій внутрішній
     * механізм сповіщень. Якщо є поточний трек — показуємо його назву, інакше
     * нейтральний плейсхолдер. MediaSessionService може замінити це власним
     * сповіщенням пізніше — це нормально й безпечно.
     */
    private fun ensureForegroundNotification(player: Player?) {
        try {
            val channelId = "playback_fallback"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val manager = getSystemService(android.app.NotificationManager::class.java)
                if (manager.getNotificationChannel(channelId) == null) {
                    manager.createNotificationChannel(
                        android.app.NotificationChannel(
                            channelId,
                            "Плеєр",
                            android.app.NotificationManager.IMPORTANCE_LOW
                        )
                    )
                }
            }
            val title = player?.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Music Player"
            val artist = player?.currentMediaItem?.mediaMetadata?.artist?.toString()
            val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .apply { if (!artist.isNullOrBlank()) setContentText(artist) }
                .setSmallIcon(R.drawable.ic_vinyl_placeholder)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    FALLBACK_NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(FALLBACK_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Найгірший сценарій — сервіс просто зупиниться на onTaskRemoved/системою,
            // але принаймні не впаде з необробленим винятком
        }
    }

    private fun setupEqualizer(sessionId: Int) {
        if (sessionId == 0) return
        try {
            equalizer?.release()
            val eq = Equalizer(0, sessionId)
            eq.setEnabled(true)
            equalizer = eq
            refreshEqState()
        } catch (e: Exception) {
            // Деякі пристрої/емулятори не підтримують аудіоефекти — просто лишаємо еквалайзер недоступним
            equalizer = null
            _eqState.value = null
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        saveState()
        stopPeriodicSave()
        serviceScope.cancel()
        equalizer?.release()
        equalizer = null
        _eqState.value = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    // Якщо застосунок вбито з активним відтворенням — зупиняємо сервіс коректно.
    // Стан уже збережений (onIsPlayingChanged/onMediaItemTransition ловлять це заздалегідь),
    // тому наступний запуск відновить чергу навіть якщо стопнемось тут.
    override fun onTaskRemoved(rootIntent: Intent?) {
        saveState()
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    companion object {
        private const val FALLBACK_NOTIFICATION_ID = 4242
        private var equalizer: Equalizer? = null

        private val _eqState = MutableStateFlow<EqualizerUiState?>(null)
        val eqState: StateFlow<EqualizerUiState?> = _eqState.asStateFlow()

        fun setEqualizerEnabled(enabled: Boolean) {
            try {
                equalizer?.setEnabled(enabled)
                refreshEqState()
            } catch (e: Exception) { /* ефект вже звільнено або недоступний */ }
        }

        fun setEqualizerBandLevel(band: Short, level: Short) {
            try {
                equalizer?.setBandLevel(band, level)
                refreshEqState()
            } catch (e: Exception) { /* ефект вже звільнено або недоступний */ }
        }

        fun useEqualizerPreset(preset: Short) {
            try {
                equalizer?.usePreset(preset)
                refreshEqState()
            } catch (e: Exception) { /* ефект вже звільнено або недоступний */ }
        }

        private fun refreshEqState() {
            val eq = equalizer
            if (eq == null) {
                _eqState.value = null
                return
            }
            try {
                val range = eq.bandLevelRange
                val bands = (0 until eq.numberOfBands).map { i ->
                    val idx = i.toShort()
                    val freqHz = eq.getCenterFreq(idx) / 1000
                    val label = if (freqHz >= 1000) "${freqHz / 1000}кГц" else "${freqHz}Гц"
                    EqBand(
                        index = idx,
                        frequencyLabel = label,
                        levelMillibels = eq.getBandLevel(idx),
                        minLevel = range[0],
                        maxLevel = range[1]
                    )
                }
                val presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
                val currentPreset = try {
                    eq.currentPreset
                } catch (e: Exception) {
                    (-1).toShort()
                }
                _eqState.value = EqualizerUiState(
                    enabled = eq.enabled,
                    bands = bands,
                    presets = presets,
                    currentPreset = currentPreset
                )
            } catch (e: Exception) {
                _eqState.value = null
            }
        }
    }
}
