package com.def.musicplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.def.musicplayer.R
import com.def.musicplayer.data.QueueTrack
import com.def.musicplayer.playback.EqualizerUiState
import com.def.musicplayer.playback.PlayerUiState
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin

private val VinylBlack = Color(0xFF161616)
private val VinylGroove = Color(0xFF2E2E2E)
private val TonearmCream = Color(0xFFF0E6DA)
private val AccentGold = Color(0xFFC79A4B)
private val MechanismGrey = Color(0xFF2A2A2A)

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    track: QueueTrack?,
    playerState: PlayerUiState,
    queue: List<QueueTrack>,
    isFavorite: Boolean,
    lyricsText: String,
    onLyricsChange: (String) -> Unit,
    onSaveLyrics: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeekToQueueIndex: (Int) -> Unit,
    equalizerState: EqualizerUiState?,
    onToggleEqEnabled: (Boolean) -> Unit,
    onEqBandChange: (Short, Short) -> Unit,
    onEqPresetSelected: (Short) -> Unit,
    sleepTimerRemainingMs: Long?,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit
) {
    if (track == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Оберіть трек у бібліотеці")
        }
        return
    }

    var showLyricsEditor by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showEqualizer by rememberSaveable { mutableStateOf(false) }
    var showSleepTimer by rememberSaveable { mutableStateOf(false) }
    var draggingPositionMs by remember { mutableStateOf<Float?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Диск займає всю вільну висоту, що лишається після керування знизу —
        // це і прибирає порожнє місце, і дає диску природно збільшуватись/зменшуватись
        // залежно від розміру екрана.
        Box(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            VinylWithTonearm(isPlaying = playerState.isPlaying, albumArtUri = track.albumArtUri)
        }

        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Start,
                    maxLines = 2
                )
                Text(track.artist, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { showSleepTimer = true }) {
                Icon(
                    Icons.Filled.Bedtime,
                    contentDescription = "Таймер сну",
                    tint = if (sleepTimerRemainingMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { showEqualizer = true }) {
                Icon(Icons.Filled.Tune, contentDescription = "Еквалайзер")
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Улюблене",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        val duration = playerState.durationMs.coerceAtLeast(1L)
        val displayedPosition = draggingPositionMs ?: playerState.positionMs.toFloat()
        Slider(
            value = displayedPosition.coerceIn(0f, duration.toFloat()),
            onValueChange = { draggingPositionMs = it },
            onValueChangeFinished = {
                draggingPositionMs?.let { onSeek(it.toLong()) }
                draggingPositionMs = null
            },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(displayedPosition.toLong()), style = MaterialTheme.typography.labelSmall)
            Text(formatTime(playerState.durationMs), style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(10.dp))

        // Ряд керування: shuffle — prev — play/pause — next — repeat
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Перемішати",
                    tint = if (playerState.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Попередній", modifier = Modifier.size(36.dp))
            }
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(68.dp)) {
                if (playerState.isBuffering) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                } else {
                    AnimatedContent(
                        targetState = playerState.isPlaying,
                        transitionSpec = {
                            (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith
                                (scaleOut(targetScale = 0.6f) + fadeOut())
                        },
                        label = "play-pause-icon"
                    ) { isPlaying ->
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Наступний", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    if (playerState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Повтор",
                    tint = if (playerState.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Пігулки-кнопки "Черга" і "Текст пісні"
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(icon = Icons.Filled.QueueMusic, label = "Черга", onClick = { showQueue = true })
            PillButton(
                icon = Icons.Filled.Lyrics,
                label = "Текст пісні",
                onClick = { showLyricsEditor = !showLyricsEditor }
            )
        }

        if (showLyricsEditor) {
            OutlinedTextField(
                value = lyricsText,
                onValueChange = onLyricsChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(top = 12.dp),
                placeholder = { Text("Встав або введи текст пісні тут...") },
                minLines = 4
            )
            Button(
                onClick = onSaveLyrics,
                modifier = Modifier.align(Alignment.End).padding(top = 8.dp, bottom = 12.dp)
            ) {
                Text("Зберегти")
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            QueueList(
                queue = queue,
                currentIndex = playerState.currentMediaItemIndex,
                onTrackClick = { index ->
                    onSeekToQueueIndex(index)
                    showQueue = false
                }
            )
        }
    }

    if (showEqualizer) {
        ModalBottomSheet(onDismissRequest = { showEqualizer = false }) {
            EqualizerContent(
                state = equalizerState,
                onToggleEnabled = onToggleEqEnabled,
                onBandChange = onEqBandChange,
                onPresetSelected = onEqPresetSelected
            )
        }
    }

    if (showSleepTimer) {
        ModalBottomSheet(onDismissRequest = { showSleepTimer = false }) {
            SleepTimerContent(
                remainingMs = sleepTimerRemainingMs,
                onStart = { minutes ->
                    onStartSleepTimer(minutes)
                    showSleepTimer = false
                },
                onCancel = {
                    onCancelSleepTimer()
                    showSleepTimer = false
                }
            )
        }
    }
}

@Composable
private fun PillButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(50)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun QueueList(queue: List<QueueTrack>, currentIndex: Int, onTrackClick: (Int) -> Unit) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            "Черга відтворення",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (queue.isEmpty()) {
            Text(
                "Черга порожня",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(queue.size) { index ->
                    val item = queue[index]
                    val isCurrent = index == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onTrackClick(index) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.albumArtUri)
                                .error(R.drawable.ic_vinyl_placeholder)
                                .fallback(R.drawable.ic_vinyl_placeholder)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                maxLines = 1,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(item.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        }
                        if (isCurrent) {
                            Icon(
                                Icons.Filled.Equalizer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Вініл у пласкому стилі + анімований тонарм на власному "корпусі" механізму.
 *
 * Кут голки виправлено: раніше при +32° вона "заїжджала" аж у ліву/центральну
 * частину диска, що виглядало нелогічно для тонарма, закріпленого справа зверху.
 * Тепер +18° — голка лягає на правий сектор диска, ближче до краю, як і має бути.
 *
 * Диск малюється через Canvas з кількома асиметричними рисками (не просто рівні
 * кола) — ідеально симетричне коло при обертанні виглядає нерухомим для ока,
 * тому саме асиметрія й дає відчуття "живого" обертання, навіть коли немає
 * обкладинки і показується заглушка.
 */
@Composable
private fun VinylWithTonearm(isPlaying: Boolean, albumArtUri: String?) {
    val discRotation = rememberInfiniteRotation()
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 18f else -24f,
        animationSpec = tween(durationMillis = 550),
        label = "tonearm-angle"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(1f)
    ) {
        val side = maxWidth
        val discSize = side * 0.90f
        val pivotSize = side * 0.16f
        val armLength = side * 0.46f
        val armWidth = side * 0.03f
        val pivotCenterX = side * 0.88f
        val pivotCenterY = side * 0.12f

        // Диск — по центру
        Box(
            modifier = Modifier
                .size(discSize)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            // Обертається як одне ціле: текстура диска (Canvas) + обкладинка зверху
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = if (isPlaying) discRotation else 0f },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    drawCircle(color = VinylBlack, radius = radius)

                    // Концентричні борозни — базова текстура
                    listOf(0.92f, 0.80f, 0.68f, 0.56f).forEach { frac ->
                        drawCircle(
                            color = VinylGroove,
                            radius = radius * frac,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // Зовнішнє червоне кільце
                    drawCircle(
                        color = primaryColor,
                        radius = radius - 1.dp.toPx(),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Асиметричні риски — ключове для "живого" вигляду: без них
                    // ідеально кругла текстура при обертанні здається нерухомою
                    val markAngles = listOf(20f, 95f, 160f, 225f, 290f)
                    markAngles.forEach { deg ->
                        val rad = Math.toRadians(deg.toDouble())
                        val rInner = radius * 0.5f
                        val rOuter = radius * 0.62f
                        val start = Offset(
                            center.x + (rInner * cos(rad)).toFloat(),
                            center.y + (rInner * sin(rad)).toFloat()
                        )
                        val end = Offset(
                            center.x + (rOuter * cos(rad)).toFloat(),
                            center.y + (rOuter * sin(rad)).toFloat()
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.14f),
                            start = start,
                            end = end,
                            strokeWidth = 1.6.dp.toPx()
                        )
                    }
                }

                // Етикетка з обкладинкою (або заглушка) — теж крутиться разом з диском
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(albumArtUri)
                        .error(R.drawable.ic_vinyl_placeholder)
                        .fallback(R.drawable.ic_vinyl_placeholder)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize(0.4f)
                        .clip(CircleShape)
                        .border(1.dp, AccentGold, CircleShape)
                )
            }
            // Отвір по центру — не обертається (символічна вісь програвача)
            Box(
                modifier = Modifier
                    .fillMaxSize(0.035f)
                    .clip(CircleShape)
                    .background(backgroundColor)
            )
        }

        // Корпус механізму тонарма (статичний) — "заземлює" голку візуально,
        // щоб було зрозуміло, що вона кріпиться до реального вузла, а не висить у повітрі
        Box(
            modifier = Modifier
                .offset(x = pivotCenterX - pivotSize / 2, y = pivotCenterY - pivotSize / 2)
                .size(pivotSize)
                .clip(CircleShape)
                .background(MechanismGrey)
                .border(1.5.dp, primaryColor, CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .size(pivotSize * 0.4f)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(VinylBlack)
                    .border(1.dp, AccentGold, CircleShape)
            )
        }

        // Рухома планка тонарма + голова звукознімача — обертаються як єдине ціле
        // навколо тієї самої точки, де стоїть корпус механізму вище.
        Box(
            modifier = Modifier
                .offset(x = pivotCenterX - armWidth / 2, y = pivotCenterY)
                .size(width = armWidth, height = armLength)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    rotationZ = tonearmAngle
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50))
                    .background(TonearmCream)
            )
            // Голка — акцентна крапка на самому кінчику планки
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(armWidth * 2.4f)
                    .clip(CircleShape)
                    .background(primaryColor)
                    .border(1.dp, TonearmCream, CircleShape)
            )
        }
    }
}

@Composable
private fun rememberInfiniteRotation(): Float {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "vinyl-rotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "rotation"
    )
    return rotation
}

private fun formatTime(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms.coerceAtLeast(0))
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0)) % 60
    return "%d:%02d".format(minutes, seconds)
}
