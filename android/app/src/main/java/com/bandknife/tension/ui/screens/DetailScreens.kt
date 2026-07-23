package com.bandknife.tension.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Slider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandknife.tension.data.EquipmentEntity
import com.bandknife.tension.data.MeasurementRecordEntity
import com.bandknife.tension.domain.TensionCalculator
import com.bandknife.tension.viewmodel.AppViewModel
import com.bandknife.tension.viewmodel.MeasureMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DetailMeasureScreen(vm: AppViewModel) {
    val measure by vm.measureState.collectAsState()
    val continuous by vm.continuous.collectAsState()
    val measureMode by vm.measureMode.collectAsState()
    val selected by vm.selectedEquipment.collectAsState()
    val equipment by vm.equipment.collectAsState()
    val saveMessage by vm.saveMessage.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        com.bandknife.tension.ui.components.EquipmentComboBox(
            equipment = equipment,
            selected = selected,
            onSelect = { vm.selectEquipment(it) },
            enabled = !measure.isMeasuring
        )
        Text("マイク: ${measure.micName}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))

        com.bandknife.tension.ui.components.MeasureStatusBar(
            isMeasuring = measure.isMeasuring,
            measureMode = measureMode,
            currentCount = continuous.currentCount,
            targetCount = continuous.targetCount,
            tapMessage = measure.tapMessage,
            hasCompletedStats = continuous.stats != null,
            savedToHistory = continuous.savedRecordId != null
        )

        Spacer(Modifier.height(12.dp))

        continuous.stats?.let { stats ->
            RecordResultCard(
                stats = stats,
                formatTension = vm::formatTension,
                passed = selected?.let { eq ->
                    vm.repository.evaluate(eq, continuous.frequencies.average(), stats.mean)
                } ?: false,
                saved = continuous.savedRecordId != null,
                onSave = { vm.saveRecord() }
            )
            Spacer(Modifier.height(12.dp))
        }

        if (measure.isMeasuring) {
            Text("${"%.1f".format(measure.frequencyHz)} Hz", fontSize = 36.sp)
            Text(
                vm.formatTension(measure.tensionN),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (measure.frequencyHz > 0) {
                com.bandknife.tension.ui.components.PassFailBadge(measure.passed)
            }
            if (measureMode == MeasureMode.CONTINUOUS) {
                Text(
                    "${continuous.currentCount} / ${continuous.targetCount} 回",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            com.bandknife.tension.ui.components.TapLevelMeter(measure.amplitude)
            com.bandknife.tension.ui.components.SpectrumChart(measure.spectrum)
        } else {
            Text("— Hz", fontSize = 36.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            Text(
                "—",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                "測定を開始すると結果が表示されます",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        measure.noiseWarning?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(8.dp))
        com.bandknife.tension.ui.components.MeasureModeSelector(
            selected = measureMode,
            onSelect = { vm.setMeasureMode(it) },
            enabled = !measure.isMeasuring
        )
        com.bandknife.tension.ui.components.MeasureStartStopButtons(
            isMeasuring = measure.isMeasuring,
            onStart = { vm.startMeasuring() },
            onStop = { vm.stopMeasuring() }
        )

        saveMessage?.let {
            Text(
                it,
                color = if (it.contains("記録しました")) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun RecordResultCard(
    stats: com.bandknife.tension.domain.MeasurementStats,
    formatTension: (Double) -> String,
    passed: Boolean,
    saved: Boolean,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("測定結果", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                formatTension(stats.mean),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("n=${stats.values.size}", style = MaterialTheme.typography.bodyMedium)
            com.bandknife.tension.ui.components.PassFailBadge(passed)
            Spacer(Modifier.height(12.dp))
            if (saved) {
                Text(
                    "履歴に記録済み",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("記録", fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
fun EquipmentScreen(vm: AppViewModel) {
    val equipment by vm.equipment.collectAsState()
    val deletedEquipment by vm.deletedEquipment.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var showDeleted by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EquipmentEntity?>(null) }
    var deletingEquipment by remember { mutableStateOf<EquipmentEntity?>(null) }
    val scope = rememberCoroutineScope()

    deletingEquipment?.let { eq ->
        AlertDialog(
            onDismissRequest = { deletingEquipment = null },
            title = { Text("設備の削除") },
            text = { Text("「${eq.name}」を削除しますか？\n削除後も復元できます。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteEquipment(eq)
                    deletingEquipment = null
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingEquipment = null }) { Text("キャンセル") }
            }
        )
    }

    when {
        showForm -> EquipmentForm(
            initial = editing,
            templates = equipment,
            onDismiss = { showForm = false }
        ) { entity ->
            scope.launch {
                if (entity.id == 0L) vm.repository.saveEquipment(entity) else vm.repository.updateEquipment(entity)
                showForm = false
            }
        }
        showDeleted -> DeletedEquipmentScreen(
            deletedEquipment = deletedEquipment,
            onBack = { showDeleted = false },
            onRestore = { vm.restoreEquipment(it) }
        )
        else -> Column(Modifier.fillMaxSize().padding(16.dp)) {
            Button(onClick = { showForm = true; editing = null }) { Text("設備を追加") }
            LazyColumn(Modifier.weight(1f)) {
                if (equipment.isEmpty()) {
                    item {
                        Text(
                            "登録されている設備がありません",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
                items(equipment) { eq ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(eq.name, fontWeight = FontWeight.Bold)
                            Text("m=${eq.massPerMeter} kg/m  S=${eq.spanMeters} m")
                            Text("標準 ${eq.standardTension.toInt()} N  規格 ${eq.specLower.toInt()}〜${eq.specUpper.toInt()} N")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { editing = eq; showForm = true }) { Text("編集") }
                                OutlinedButton(onClick = {
                                    editing = eq.asNewProfile()
                                    showForm = true
                                }) { Text("複製") }
                                Button(onClick = { deletingEquipment = eq }) { Text("削除") }
                            }
                        }
                    }
                }
            }
            if (deletedEquipment.isNotEmpty()) {
                TextButton(
                    onClick = { showDeleted = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("削除済み設備（${deletedEquipment.size}件）")
                }
            }
        }
    }
}

@Composable
private fun DeletedEquipmentScreen(
    deletedEquipment: List<EquipmentEntity>,
    onBack: () -> Unit,
    onRestore: (EquipmentEntity) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← 設備一覧に戻る") }
        Text("削除済み設備", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        Text(
            "復元すると設備一覧に戻ります",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyColumn {
            if (deletedEquipment.isEmpty()) {
                item {
                    Text(
                        "削除済みの設備はありません",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
            items(deletedEquipment) { eq ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(eq.name, fontWeight = FontWeight.Bold)
                        Text("m=${eq.massPerMeter} kg/m  S=${eq.spanMeters} m")
                        Text("標準 ${eq.standardTension.toInt()} N  規格 ${eq.specLower.toInt()}〜${eq.specUpper.toInt()} N")
                        Button(onClick = { onRestore(eq) }) { Text("復元") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EquipmentForm(
    initial: EquipmentEntity?,
    templates: List<EquipmentEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (EquipmentEntity) -> Unit
) {
    val isNew = initial?.id == 0L
    var selectedTemplate by remember(initial) { mutableStateOf<EquipmentEntity?>(null) }
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var mass by remember(initial) { mutableStateOf((initial?.massPerMeter ?: 0.844).toString()) }
    var span by remember(initial) { mutableStateOf((initial?.spanMeters ?: 1.0).toString()) }
    var standard by remember(initial) { mutableStateOf((initial?.standardTension ?: 160.0).toString()) }
    var lower by remember(initial) { mutableStateOf((initial?.specLower ?: 150.0).toString()) }
    var upper by remember(initial) { mutableStateOf((initial?.specUpper ?: 180.0).toString()) }
    var width by remember(initial) { mutableStateOf((initial?.widthMm ?: 86.0).toString()) }
    var thickness by remember(initial) { mutableStateOf((initial?.thicknessMm ?: 1.25).toString()) }
    var useHz by remember(initial) { mutableStateOf(initial?.useHzMode ?: false) }
    var baseEntity by remember(initial) { mutableStateOf(initial) }

    LaunchedEffect(selectedTemplate) {
        selectedTemplate?.let { template ->
            baseEntity = template
            name = template.asNewProfile().name
            mass = template.massPerMeter.toString()
            span = template.spanMeters.toString()
            standard = template.standardTension.toString()
            lower = template.specLower.toString()
            upper = template.specUpper.toString()
            width = template.widthMm.toString()
            thickness = template.thicknessMm.toString()
            useHz = template.useHzMode
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            if (isNew) "設備を追加" else "設備を編集",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (isNew && templates.isNotEmpty()) {
            com.bandknife.tension.ui.components.EquipmentComboBox(
                equipment = templates,
                selected = selectedTemplate,
                onSelect = { selectedTemplate = it },
                label = "既存の設定をコピー（任意）"
            )
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(name, { name = it }, label = { Text("設備名") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(width, { width = it; mass = TensionCalculator.massFromDimensions(width.toDoubleOrNull()?.div(1000) ?: 0.0, thickness.toDoubleOrNull()?.div(1000) ?: 0.0, 7850.0).toString() }, label = { Text("幅 (mm)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(thickness, { thickness = it }, label = { Text("厚み (mm)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(mass, { mass = it }, label = { Text("単位質量 m (kg/m)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(span, { span = it }, label = { Text("スパン長 S (m)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(standard, { standard = it }, label = { Text("標準値 (N)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lower, { lower = it }, label = { Text("規格下限 (N)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(upper, { upper = it }, label = { Text("規格上限 (N)") }, modifier = Modifier.fillMaxWidth())
        Row { Text("Hz管理"); Switch(useHz, { useHz = it }) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss) { Text("キャンセル") }
            Button(onClick = {
                val source = baseEntity ?: EquipmentEntity(
                    name = name,
                    massPerMeter = 0.844,
                    spanMeters = 1.0,
                    standardTension = 160.0,
                    specLower = 150.0,
                    specUpper = 180.0
                )
                onSave(source.copy(
                    id = initial?.id ?: 0,
                    name = name,
                    massPerMeter = mass.toDoubleOrNull() ?: source.massPerMeter,
                    spanMeters = span.toDoubleOrNull() ?: source.spanMeters,
                    standardTension = standard.toDoubleOrNull() ?: source.standardTension,
                    specLower = lower.toDoubleOrNull() ?: source.specLower,
                    specUpper = upper.toDoubleOrNull() ?: source.specUpper,
                    useHzMode = useHz,
                    widthMm = width.toDoubleOrNull() ?: source.widthMm,
                    thicknessMm = thickness.toDoubleOrNull() ?: source.thicknessMm,
                    deleted = false
                ))
            }) { Text(if (isNew) "追加" else "保存") }
        }
    }
}

private fun EquipmentEntity.asNewProfile(): EquipmentEntity =
    copy(id = 0, name = "$name（コピー）", deleted = false)

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val records by vm.records.collectAsState()
    val fmt = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        if (records.isEmpty()) {
            item {
                Text(
                    "記録された測定結果はありません",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }
        items(records) { record ->
            RecordCard(record, fmt, vm)
        }
    }
}

@Composable
private fun RecordCard(record: MeasurementRecordEntity, fmt: SimpleDateFormat, vm: AppViewModel) {
    var comment by remember(record.id) { mutableStateOf(record.comment) }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("${record.equipmentName}  ${fmt.format(Date(record.timestamp))}", fontWeight = FontWeight.Bold)
            Text("${record.tensionN.toInt()} N  ${if (record.passed) "OK" else "要調整"}  n=${record.sampleCount}")
            if (!record.isLocked) {
                OutlinedTextField(comment, { comment = it }, label = { Text("コメント") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.updateRecordComment(record, comment) }) { Text("コメント保存") }
            } else Text("🔒 ロック中", color = MaterialTheme.colorScheme.secondary)
            Row {
                IconButton(onClick = { vm.toggleRecordLock(record) }) {
                    Icon(if (record.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, null)
                }
                if (!record.isLocked) IconButton(onClick = { vm.deleteRecord(record) }) {
                    Icon(Icons.Default.Delete, null)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    var driveUrl by remember { mutableStateOf("") }
    var driveEnabled by remember { mutableStateOf(false) }
    var useKgf by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableStateOf(0.08f) }
    var micAuto by remember { mutableStateOf(true) }
    var selectedMicDeviceId by remember { mutableStateOf(-1) }
    var pending by remember { mutableStateOf(0) }
    val devices = remember { vm.getInputDevices() }

    LaunchedEffect(Unit) {
        driveUrl = vm.repository.preferences.driveUrl.first()
        driveEnabled = vm.repository.preferences.driveEnabled.first()
        useKgf = vm.repository.preferences.useKgf.first()
        sensitivity = vm.repository.preferences.sensitivity.first().toFloat()
        micAuto = vm.repository.preferences.micAuto.first()
        selectedMicDeviceId = vm.repository.preferences.micDeviceId.first()
        pending = vm.repository.pendingUploadCount()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("設定", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row { Text("kgf表示"); Switch(useKgf, { useKgf = it; scope.launch { vm.repository.preferences.setUseKgf(it) } }) }
        Spacer(Modifier.height(16.dp))
        StrikeSensitivitySection(
            sensitivity = sensitivity,
            onSensitivityChange = { value ->
                sensitivity = value
                scope.launch { vm.repository.preferences.setSensitivity(value.toDouble()) }
            }
        )
        Spacer(Modifier.height(16.dp))
        MicSelectionSection(
            devices = devices,
            micAuto = micAuto,
            selectedMicDeviceId = selectedMicDeviceId,
            onSelectDevice = { id ->
                micAuto = false
                selectedMicDeviceId = id
                scope.launch {
                    vm.repository.preferences.setMicDeviceId(id)
                    vm.repository.preferences.setMicAuto(false)
                }
            },
            onSelectAuto = {
                micAuto = true
                scope.launch { vm.repository.preferences.setMicAuto(true) }
            }
        )
        OutlinedTextField(driveUrl, { driveUrl = it }, label = { Text("Drive アップロードURL") }, modifier = Modifier.fillMaxWidth())
        Row { Text("自動アップロード"); Switch(driveEnabled, { driveEnabled = it; scope.launch { vm.repository.preferences.setDriveEnabled(it) } }) }
        Button(onClick = { scope.launch { vm.repository.preferences.setDriveUrl(driveUrl); vm.repository.testUpload() } }) { Text("テスト送信") }
        Text("未送信: $pending 件")
        Button(onClick = { scope.launch { vm.repository.processUploadQueue(); pending = vm.repository.pendingUploadCount() } }) { Text("再送") }
    }
}

@Composable
private fun MicSelectionSection(
    devices: List<Pair<Int, String>>,
    micAuto: Boolean,
    selectedMicDeviceId: Int,
    onSelectDevice: (Int) -> Unit,
    onSelectAuto: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text("マイク選択", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            devices.forEach { (id, name) ->
                FilterChip(
                    selected = !micAuto && selectedMicDeviceId == id,
                    onClick = { onSelectDevice(id) },
                    label = { Text(name) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            FilterChip(
                selected = micAuto,
                onClick = onSelectAuto,
                label = { Text("マイク自動選択") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StrikeSensitivitySection(
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit
) {
    val presets = listOf(
        "高" to 0.04f,
        "標準" to 0.08f,
        "低" to 0.18f
    )

    Column(Modifier.fillMaxWidth()) {
        Text("打撃検出感度", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "叩いた音の大きさがこの値以上のとき測定を開始します",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (label, value) ->
                FilterChip(
                    selected = abs(sensitivity - value) < 0.02f,
                    onClick = { onSensitivityChange(value) },
                    label = { Text(label) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "弱い打音も",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                sensitivityLevelLabel(sensitivity),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "強い打音のみ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = sensitivityThresholdToSlider(sensitivity),
            onValueChange = { onSensitivityChange(sliderToSensitivityThreshold(it)) },
            valueRange = SENSITIVITY_MIN..SENSITIVITY_MAX,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            sensitivityDescription(sensitivity),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val SENSITIVITY_MIN = 0.02f
private const val SENSITIVITY_MAX = 0.3f

private fun sensitivityThresholdToSlider(threshold: Float) = SENSITIVITY_MAX + SENSITIVITY_MIN - threshold

private fun sliderToSensitivityThreshold(slider: Float) = SENSITIVITY_MAX + SENSITIVITY_MIN - slider

private fun sensitivityLevelLabel(threshold: Float): String = when {
    threshold <= 0.05f -> "高"
    threshold <= 0.10f -> "標準"
    threshold <= 0.16f -> "やや低"
    else -> "低"
}

private fun sensitivityDescription(threshold: Float): String = when {
    threshold <= 0.05f -> "弱い打音でも反応します。静かな場所向けです。"
    threshold <= 0.10f -> "通常の打音に反応します。"
    threshold <= 0.16f -> "やや強い打音が必要です。周囲に雑音がある場合に調整してください。"
    else -> "強い打音のみ反応します。工場など騒がしい環境向けです。"
}

@Composable
fun HelpScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ヘルプ", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("原理: T = 4 × m × S² × f²")
        Text("• 樹脂の柄や指の腹でスパン中央を軽く叩く")
        Text("• 金属で叩くと高調波ノイズが発生します")
        Text("• マイクは刃から1〜2cm離して保持")
        Text("• 測定前に必ず機械を停止してください")
        Text("• 有線ピンマイクが狭い場所で最適です")
    }
}
