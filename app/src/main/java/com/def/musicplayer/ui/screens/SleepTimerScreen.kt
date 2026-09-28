package com.def.musicplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit

@Composable
fun SleepTimerContent(
    remainingMs: Long?,
    onStart: (minutes: Int) -> Unit,
    onCancel: () -> Unit
) {
    Column(Modifier.padding(20.dp)) {
        Text("Таймер сну", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (remainingMs != null) {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60
            Text("Відтворення зупиниться через %d:%02d".format(minutes, seconds))
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onCancel) { Text("Скасувати таймер") }
        } else {
            Text("Автоматично поставити на паузу через:")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    OutlinedButton(onClick = { onStart(minutes) }) { Text("${minutes} хв") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
