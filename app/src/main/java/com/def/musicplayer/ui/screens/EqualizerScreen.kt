package com.def.musicplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.def.musicplayer.playback.EqualizerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerContent(
    state: EqualizerUiState?,
    onToggleEnabled: (Boolean) -> Unit,
    onBandChange: (Short, Short) -> Unit,
    onPresetSelected: (Short) -> Unit
) {
    Column(Modifier.padding(20.dp)) {
        Text("Еквалайзер", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (state == null) {
            Text("Еквалайзер недоступний на цьому пристрої або ще не готовий — почни відтворення й спробуй ще раз.")
            Spacer(Modifier.height(20.dp))
            return@Column
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Увімкнено")
            Switch(checked = state.enabled, onCheckedChange = onToggleEnabled)
        }

        if (state.presets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            var expanded by remember { mutableStateOf(false) }
            val currentLabel = state.presets.getOrNull(state.currentPreset.toInt())
                ?: "Власний"
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = currentLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Пресет") },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.presets.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onPresetSelected(index.toShort())
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        state.bands.forEach { band ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(band.frequencyLabel, style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = band.levelMillibels.toFloat(),
                    onValueChange = { onBandChange(band.index, it.toInt().toShort()) },
                    valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat(),
                    enabled = state.enabled
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
