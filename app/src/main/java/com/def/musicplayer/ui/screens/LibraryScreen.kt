package com.def.musicplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.def.musicplayer.R
import com.def.musicplayer.data.SettingsStore
import com.def.musicplayer.data.Song
import com.def.musicplayer.data.db.PlaylistEntity
import java.util.concurrent.TimeUnit

private enum class SortOption(val label: String) {
    TITLE("Назва"),
    ARTIST("Виконавець"),
    ALBUM("Альбом"),
    DURATION("Тривалість")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    songs: List<Song>,
    playlists: List<PlaylistEntity>,
    onSongClick: (Int) -> Unit,
    onToggleTheme: () -> Unit,
    accentColor: Color,
    onAccentColorSelected: (Color) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song, Long) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onEditCover: (Song) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    // rememberSaveable — щоб пошуковий запит не губився при повороті екрана
    var query by rememberSaveable { mutableStateOf("") }
    var menuSongId by rememberSaveable { mutableStateOf<Long?>(null) }
    var propertiesSong by remember { mutableStateOf<Song?>(null) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }
    var sortOptionOrdinal by rememberSaveable { mutableStateOf(SortOption.TITLE.ordinal) }
    var sortAscending by rememberSaveable { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    val sortOption = SortOption.entries[sortOptionOrdinal]

    val filtered = remember(songs, query) {
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
        }
    }

    val sortedFiltered = remember(filtered, sortOption, sortAscending) {
        val comparator = when (sortOption) {
            SortOption.TITLE -> compareBy<Song> { it.title.lowercase() }
            SortOption.ARTIST -> compareBy<Song> { it.artist.lowercase() }
            SortOption.ALBUM -> compareBy<Song> { it.album.lowercase() }
            SortOption.DURATION -> compareBy<Song> { it.durationMs }
        }
        val sorted = filtered.sortedWith(comparator)
        if (sortAscending) sorted else sorted.reversed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Бібліотека") },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Filled.Sort, contentDescription = "Сортування")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    trailingIcon = {
                                        if (option == sortOption) {
                                            Icon(
                                                if (sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        sortAscending = if (option == sortOption) !sortAscending else true
                                        sortOptionOrdinal = option.ordinal
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = onToggleTheme) {
                        Icon(Icons.Filled.Palette, contentDescription = "Темна/світла тема")
                    }
                    IconButton(onClick = { showColorPicker = true }) {
                        Icon(Icons.Filled.Circle, contentDescription = "Колір теми", tint = accentColor)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                placeholder = { Text("Пошук за назвою або виконавцем") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )

            if (sortedFiltered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (songs.isEmpty()) "Бібліотека порожня" else "Треків не знайдено")
                }
            } else {
                LazyColumn {
                    items(sortedFiltered, key = { it.id }) { song ->
                        val realIndex = songs.indexOf(song)
                        SongRow(
                            song = song,
                            onClick = { onSongClick(realIndex) },
                            onLongClick = { menuSongId = song.id }
                        )
                    }
                }
            }
        }
    }

    // Контекстне меню довгого натискання — базовий набір як у більшості плеєрів
    val menuSong = songs.find { it.id == menuSongId }
    if (menuSong != null) {
        ModalBottomSheet(onDismissRequest = { menuSongId = null }) {
            Column {
                Text(
                    menuSong.title,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                MenuAction(Icons.Filled.PlayArrow, "Відтворити зараз") {
                    onSongClick(songs.indexOf(menuSong)); menuSongId = null
                }
                MenuAction(Icons.Filled.SkipNext, "Відтворити наступним") {
                    onPlayNext(menuSong); menuSongId = null
                }
                MenuAction(Icons.Filled.QueueMusic, "Додати в чергу") {
                    onAddToQueue(menuSong); menuSongId = null
                }
                MenuAction(Icons.Filled.PlaylistAdd, "Додати до плейлиста") {
                    playlistPickerSong = menuSong; menuSongId = null
                }
                MenuAction(Icons.Filled.Image, "Редагувати обкладинку") {
                    onEditCover(menuSong); menuSongId = null
                }
                MenuAction(Icons.Filled.Info, "Властивості") {
                    propertiesSong = menuSong; menuSongId = null
                }
                MenuAction(Icons.Filled.Delete, "Видалити з пристрою") {
                    onDeleteSong(menuSong); menuSongId = null
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    // Діалог властивостей треку
    propertiesSong?.let { song ->
        AlertDialog(
            onDismissRequest = { propertiesSong = null },
            title = { Text("Властивості") },
            text = {
                Column {
                    PropertyRow("Назва", song.title)
                    PropertyRow("Виконавець", song.artist)
                    PropertyRow("Альбом", song.album)
                    PropertyRow("Тривалість", formatDuration(song.durationMs))
                }
            },
            confirmButton = {
                TextButton(onClick = { propertiesSong = null }) { Text("Закрити") }
            }
        )
    }

    // Вибір плейлиста для додавання треку
    playlistPickerSong?.let { song ->
        AlertDialog(
            onDismissRequest = { playlistPickerSong = null },
            title = { Text("Додати до плейлиста") },
            text = {
                if (playlists.isEmpty()) {
                    Text("Плейлистів ще немає. Створи один на вкладці «Плейлисти».")
                } else {
                    Column {
                        playlists.forEach { playlist ->
                            Text(
                                playlist.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            onAddToPlaylist(song, playlist.playlistId)
                                            playlistPickerSong = null
                                        },
                                        onLongClick = {}
                                    )
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { playlistPickerSong = null }) { Text("Закрити") }
            }
        )
    }

    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("Колір теми") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsStore.PRESET_ACCENTS.chunked(4).forEach { rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowColors.forEach { argb ->
                                val color = Color(argb)
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (color == accentColor) 3.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onAccentColorSelected(color)
                                            showColorPicker = false
                                        }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorPicker = false }) { Text("Закрити") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = {})
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(value.ifBlank { "—" }, modifier = Modifier.weight(2f))
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(song: Song, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(song.albumArtUri)
                .error(R.drawable.ic_vinyl_placeholder)
                .fallback(R.drawable.ic_vinyl_placeholder)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Text(
            formatDuration(song.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
