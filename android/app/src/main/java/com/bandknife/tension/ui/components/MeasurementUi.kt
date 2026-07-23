package com.bandknife.tension.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandknife.tension.data.EquipmentEntity
import com.bandknife.tension.viewmodel.MeasureMode

@Composable
fun MeasureStatusBar(
    isMeasuring: Boolean,
    measureMode: MeasureMode,
    currentCount: Int,
    targetCount: Int,
    tapMessage: String,
    hasCompletedStats: Boolean = false,
    savedToHistory: Boolean = false,
    modifier: Modifier = Modifier
) {
    val stoppedColor = Color(0xFF757575)
    val activeColor = Color(0xFF2E7D32)
    val color = if (isMeasuring) activeColor else stoppedColor

    val statusLabel = if (isMeasuring) "測定中" else "停止中"
    val guideText = when {
        savedToHistory -> "履歴に記録済みです"
        hasCompletedStats -> "測定完了 — 下の「記録」ボタンで保存してください"
        !isMeasuring -> "「開始」を押して測定してください"
        measureMode == MeasureMode.CONTINUOUS && currentCount < targetCount ->
            "刃を叩いてください（${currentCount} / ${targetCount} 回）"
        measureMode == MeasureMode.CONTINUOUS && currentCount >= targetCount ->
            "測定完了 — 「記録」ボタンで保存してください"
        else -> "刃を叩いてください"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(color, CircleShape)
            )
            Column(Modifier.weight(1f)) {
                Text(statusLabel, fontWeight = FontWeight.Bold, color = color, fontSize = 18.sp)
                Text(guideText, style = MaterialTheme.typography.bodyMedium)
                if (isMeasuring && tapMessage.isNotEmpty()) {
                    Text(tapMessage, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun MeasureModeSelector(
    selected: MeasureMode,
    onSelect: (MeasureMode) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModeButton(
            label = "単発",
            selected = selected == MeasureMode.SINGLE,
            onClick = { onSelect(MeasureMode.SINGLE) },
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            label = "連続",
            selected = selected == MeasureMode.CONTINUOUS,
            onClick = { onSelect(MeasureMode.CONTINUOUS) },
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
    }
}

@Composable
fun MeasureStartStopButtons(
    isMeasuring: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onStart,
            enabled = !isMeasuring,
            modifier = Modifier.weight(1f)
        ) { Text("開始") }
        Button(
            onClick = onStop,
            enabled = isMeasuring,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) { Text("停止") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentComboBox(
    equipment: List<EquipmentEntity>,
    selected: EquipmentEntity?,
    onSelect: (EquipmentEntity) -> Unit,
    enabled: Boolean = true,
    label: String = "設備選択",
    placeholder: String = "設備を選んでください",
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            enabled = enabled && equipment.isNotEmpty()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            equipment.forEach { eq ->
                DropdownMenuItem(
                    text = { Text(eq.name) },
                    onClick = {
                        onSelect(eq)
                        expanded = false
                    }
                )
            }
        }
    }
}
