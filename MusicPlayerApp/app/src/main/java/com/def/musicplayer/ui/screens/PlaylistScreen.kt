package com.def.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.def.musicplayer.R
import com.def.musicplayer.data.Song
import com.def.musicplayer.data.db.PlaylistEntity
import java.util.concurrent.TimeUnit

@Composable
fun PlaylistScreen(
    playlists: List<PlaylistEntity>,
    favoriteSongs: List<Song>,
    onCreatePlaylist: (String) -> Unit,
    onFavoriteSongClick: (Int) -> Unit
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var showFavorites by rememberSaveable { mutableStateOf(false) }

    if (showFavorites) {
        FavoritesListScreen(
            songs = favoriteSongs,
            onBack = { showFavorites = false },
            onSongClick = onFavoriteSongClick
        )
        return
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Новий плейлист")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Автоматичний плейлист "Улюблені" — завжди зверху, наповнюється сам
            // із треків, позначених сердечком на екрані плеєра.
            ListItem(
                headlineContent = { Text("Улюблені") },
                supportingContent = { Text("${favoriteSongs.size} трек(ів)") },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier.clickable { showFavorites = true }
            )
            HorizontalDivider()

            if (playlists.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Власних плейлистів ще немає")
                }
            } else {
                LazyColumn {
                    items(playlists) { playlist ->
                        ListItem(headlineContent = { Text(playlist.name) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Новий плейлист") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Назва плейлиста") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) onCreatePlaylist(newName)
                    newName = ""
                    showDialog = false
                }) { Text("Створити") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Скасувати") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesListScreen(songs: List<Song>, onBack: () -> Unit, onSongClick: (Int) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Улюблені") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (songs.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ще немає улюблених треків.\nПознач серцем трек на екрані плеєра.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(songs, key = { it.id }) { song ->
                    val index = songs.indexOf(song)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSongClick(index) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(song.albumArtUri)
                                .error(R.drawable.ic_vinyl_placeholder)
                                .fallback(R.drawable.ic_vinyl_placeholder)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, maxLines = 1)
                            Text(song.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(formatDuration(song.durationMs), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(minutes, seconds)
}
