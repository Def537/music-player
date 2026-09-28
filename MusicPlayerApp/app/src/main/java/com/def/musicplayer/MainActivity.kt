package com.def.musicplayer

import android.Manifest
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.def.musicplayer.data.CoverArtHelper
import com.def.musicplayer.data.DeleteHelper
import com.def.musicplayer.data.PrepareCoverResult
import com.def.musicplayer.data.Song
import com.def.musicplayer.data.WriteCoverResult
import com.def.musicplayer.ui.AppViewModel
import com.def.musicplayer.ui.screens.LibraryScreen
import com.def.musicplayer.ui.screens.PlayerScreen
import com.def.musicplayer.ui.screens.PlaylistScreen
import com.def.musicplayer.ui.theme.MusicPlayerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            val isDark by vm.isDarkTheme.collectAsState()
            val accentColor by vm.accentColor.collectAsState()

            val permissions = when {
                Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                else -> arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { result -> if (result.values.any { it }) vm.loadLibrary() }

            LaunchedEffect(Unit) { permissionLauncher.launch(permissions) }

            MusicPlayerTheme(isDarkTheme = isDark, accentColor = accentColor) {
                MainScreen(vm)
            }
        }
    }
}

// Зберігаємо як Int (ordinal), бо rememberSaveable з enum напряму не переживає поворот екрана
private enum class Tab { LIBRARY, PLAYER, PLAYLISTS }

@Composable
private fun MainScreen(vm: AppViewModel) {
    // rememberSaveable — виправляє баг "поворот екрана скидає на першу сторінку":
    // раніше тут був звичайний remember, який не переживає перестворення Activity.
    var tabOrdinal by rememberSaveable { mutableStateOf(Tab.LIBRARY.ordinal) }
    val tab = Tab.entries[tabOrdinal]

    val songs by vm.songs.collectAsState()
    val playerState by vm.playerState.collectAsState()
    val currentTrack by vm.currentTrack.collectAsState()
    val queue by vm.queue.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val favoriteIds by vm.favoriteIds.collectAsState()
    val favoriteSongs by vm.favoriteSongs.collectAsState()
    val equalizerState by vm.equalizerState.collectAsState()
    val sleepTimerRemainingMs by vm.sleepTimerRemainingMs.collectAsState()
    val accentColor by vm.accentColor.collectAsState()
    val context = LocalContext.current

    var lyricsText by remember { mutableStateOf("") }
    LaunchedEffect(currentTrack?.id) {
        lyricsText = currentTrack?.let { vm.getLyrics(it.id) } ?: ""
    }

    // Для видалення на Android 10+ система сама показує діалог підтвердження
    val deleteConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { vm.loadLibrary() }

    fun deleteSong(song: Song) {
        DeleteHelper.requestDelete(
            context = context,
            songId = song.id,
            onDeletedImmediately = { vm.loadLibrary() },
            onNeedsConsent = { sender: IntentSender ->
                deleteConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
            },
            onFailed = {
                Toast.makeText(context, "Не вдалося видалити трек", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // --- Редагування обкладинки: вибір фото -> запис у ID3-тег MP3 ---
    val scope = rememberCoroutineScope()
    var pendingCoverSongId by remember { mutableStateOf<Long?>(null) }
    var pendingCoverWriteBack by remember { mutableStateOf<Pair<android.net.Uri, java.io.File>?>(null) }

    val coverConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        val pending = pendingCoverWriteBack
        pendingCoverWriteBack = null
        if (pending != null) {
            val (uri, file) = pending
            val result = CoverArtHelper.writeBack(context, uri, file)
            CoverArtHelper.cleanup(file)
            if (result is WriteCoverResult.Success) {
                vm.loadLibrary()
                Toast.makeText(context, "Обкладинку оновлено", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Не вдалося зберегти обкладинку", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pickCoverImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val songId = pendingCoverSongId
        pendingCoverSongId = null
        if (uri != null && songId != null) {
            scope.launch {
                when (val prepared = CoverArtHelper.prepareCoverEdit(context, songId, uri)) {
                    is PrepareCoverResult.Ready -> {
                        when (val result = CoverArtHelper.writeBack(context, prepared.targetUri, prepared.preparedFile)) {
                            is WriteCoverResult.Success -> {
                                CoverArtHelper.cleanup(prepared.preparedFile)
                                vm.loadLibrary()
                                Toast.makeText(context, "Обкладинку оновлено", Toast.LENGTH_SHORT).show()
                            }
                            is WriteCoverResult.NeedsConsent -> {
                                pendingCoverWriteBack = prepared.targetUri to prepared.preparedFile
                                coverConsentLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build())
                            }
                            is WriteCoverResult.Failed -> {
                                CoverArtHelper.cleanup(prepared.preparedFile)
                                Toast.makeText(context, result.reason, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    is PrepareCoverResult.Failed -> {
                        Toast.makeText(context, prepared.reason, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun editCover(song: Song) {
        pendingCoverSongId = song.id
        pickCoverImageLauncher.launch(
            PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                .build()
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.LIBRARY,
                    onClick = { tabOrdinal = Tab.LIBRARY.ordinal },
                    icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                    label = { Text("Бібліотека") }
                )
                NavigationBarItem(
                    selected = tab == Tab.PLAYER,
                    onClick = { tabOrdinal = Tab.PLAYER.ordinal },
                    icon = { Icon(Icons.Filled.PlayCircle, contentDescription = null) },
                    label = { Text("Плеєр") }
                )
                NavigationBarItem(
                    selected = tab == Tab.PLAYLISTS,
                    onClick = { tabOrdinal = Tab.PLAYLISTS.ordinal },
                    icon = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                    label = { Text("Плейлисти") }
                )
            }
        }
    ) { padding ->
        Surface(modifier = androidx.compose.ui.Modifier.padding(padding)) {
            when (tab) {
                Tab.LIBRARY -> LibraryScreen(
                    songs = songs,
                    playlists = playlists,
                    onSongClick = { index ->
                        vm.playSongAt(index)
                        tabOrdinal = Tab.PLAYER.ordinal
                    },
                    onToggleTheme = { vm.toggleTheme() },
                    accentColor = accentColor,
                    onAccentColorSelected = { vm.setAccentColor(it) },
                    onPlayNext = { vm.playNext(it) },
                    onAddToQueue = { vm.addToQueue(it) },
                    onAddToPlaylist = { song, playlistId -> vm.addSongToPlaylist(playlistId, song.id) },
                    onDeleteSong = { deleteSong(it) },
                    onEditCover = { editCover(it) }
                )
                Tab.PLAYER -> PlayerScreen(
                    track = currentTrack,
                    playerState = playerState,
                    queue = queue,
                    isFavorite = currentTrack?.id?.let { it in favoriteIds } ?: false,
                    lyricsText = lyricsText,
                    onLyricsChange = { lyricsText = it },
                    onSaveLyrics = { currentTrack?.let { vm.saveLyrics(it.id, lyricsText) } },
                    onToggleFavorite = { currentTrack?.let { vm.toggleFavorite(it.id) } },
                    onPlayPause = { vm.playPause() },
                    onNext = { vm.next() },
                    onPrevious = { vm.previous() },
                    onSeek = { vm.seekTo(it) },
                    onToggleShuffle = { vm.toggleShuffle() },
                    onCycleRepeat = { vm.cycleRepeatMode() },
                    onSeekToQueueIndex = { vm.seekToQueueIndex(it) },
                    equalizerState = equalizerState,
                    onToggleEqEnabled = { vm.setEqualizerEnabled(it) },
                    onEqBandChange = { band, level -> vm.setEqualizerBand(band, level) },
                    onEqPresetSelected = { vm.useEqualizerPreset(it) },
                    sleepTimerRemainingMs = sleepTimerRemainingMs,
                    onStartSleepTimer = { vm.startSleepTimer(it) },
                    onCancelSleepTimer = { vm.cancelSleepTimer() }
                )
                Tab.PLAYLISTS -> PlaylistScreen(
                    playlists = playlists,
                    favoriteSongs = favoriteSongs,
                    onCreatePlaylist = { name -> vm.createPlaylist(name) },
                    onFavoriteSongClick = { index ->
                        vm.playQueue(favoriteSongs, index)
                        tabOrdinal = Tab.PLAYER.ordinal
                    }
                )
            }
        }
    }
}
