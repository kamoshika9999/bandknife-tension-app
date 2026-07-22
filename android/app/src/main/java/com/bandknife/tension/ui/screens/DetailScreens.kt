package com.bandknife.tension.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Slider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DetailMeasureScreen(vm: AppViewModel) {
    val measure by vm.measureState.collectAsState()
    val continuous by vm.continuous.collectAsState()
    val selected by vm.selectedEquipment.collectAsState()
    val equipment by vm.equipment.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("設備選択", fontWeight = FontWeight.Bold)
        equipment.forEach { eq ->
            Button(onClick = { vm.selectEquipment(eq) }, modifier = Modifier.padding(2.dp)) {
                Text(if (selected?.id == eq.id) "✓ ${eq.name}" else eq.name)
            }
        }
        Text(selected?.name ?: "設備未選択", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text("マイク: ${measure.micName}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Text("${"%.1f".format(measure.frequencyHz)} Hz", fontSize = 36.sp)
        Text(vm.formatTension(measure.tensionN), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        com.bandknife.tension.ui.components.PassFailBadge(measure.passed)
        com.bandknife.tension.ui.components.SpectrumChart(measure.spectrum)
        Text(measure.tapMessage)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { vm.setMeasureMode(MeasureMode.SINGLE) }) { Text("単発") }
            Button(onClick = { vm.setMeasureMode(MeasureMode.CONTINUOUS) }) { Text("連続") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { vm.startMeasuring() }) { Text("開始") }
            Button(onClick = { vm.stopMeasuring() }) { Text("停止") }
        }
        continuous.stats?.let { stats ->
            Text("連続結果: ${stats.mean.toInt()} N (n=${stats.values.size})")
            Button(onClick = { vm.saveRecord() }) { Text("記録") }
        }
    }
}

@Composable
fun EquipmentScreen(vm: AppViewModel) {
    val equipment by vm.equipment.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EquipmentEntity?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { showForm = true; editing = null }) { Text("設備を追加") }
        LazyColumn {
            items(equipment) { eq ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(eq.name, fontWeight = FontWeight.Bold)
                        Text("m=${eq.massPerMeter} kg/m  S=${eq.spanMeters} m")
                        Text("標準 ${eq.standardTension.toInt()} N  規格 ${eq.specLower.toInt()}〜${eq.specUpper.toInt()} N")
                        Row {
                            Button(onClick = { editing = eq; showForm = true }) { Text("編集") }
                            Button(onClick = { scope.launch { vm.repository.deleteEquipment(eq) } }) { Text("削除") }
                        }
                    }
                }
            }
        }
    }
    if (showForm) EquipmentForm(editing, onDismiss = { showForm = false }) { entity ->
        scope.launch {
            if (entity.id == 0L) vm.repository.saveEquipment(entity) else vm.repository.updateEquipment(entity)
            showForm = false
        }
    }
}

@Composable
private fun EquipmentForm(initial: EquipmentEntity?, onDismiss: () -> Unit, onSave: (EquipmentEntity) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var mass by remember { mutableStateOf((initial?.massPerMeter ?: 0.314).toString()) }
    var span by remember { mutableStateOf((initial?.spanMeters ?: 0.85).toString()) }
    var standard by remember { mutableStateOf((initial?.standardTension ?: 160.0).toString()) }
    var lower by remember { mutableStateOf((initial?.specLower ?: 150.0).toString()) }
    var upper by remember { mutableStateOf((initial?.specUpper ?: 180.0).toString()) }
    var width by remember { mutableStateOf((initial?.widthMm ?: 50.0).toString()) }
    var thickness by remember { mutableStateOf((initial?.thicknessMm ?: 0.8).toString()) }
    var useHz by remember { mutableStateOf(initial?.useHzMode ?: false) }

    Column(Modifier.padding(16.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("設備名") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(width, { width = it; mass = TensionCalculator.massFromDimensions(width.toDoubleOrNull()?.div(1000) ?: 0.0, thickness.toDoubleOrNull()?.div(1000) ?: 0.0, 7850.0).toString() }, label = { Text("幅 (mm)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(thickness, { thickness = it }, label = { Text("厚み (mm)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(mass, { mass = it }, label = { Text("単位質量 m (kg/m)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(span, { span = it }, label = { Text("スパン長 S (m)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(standard, { standard = it }, label = { Text("標準値 (N)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lower, { lower = it }, label = { Text("規格下限 (N)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(upper, { upper = it }, label = { Text("規格上限 (N)") }, modifier = Modifier.fillMaxWidth())
        Row { Text("Hz管理"); Switch(useHz, { useHz = it }) }
        Row {
            Button(onClick = onDismiss) { Text("キャンセル") }
            Button(onClick = {
                onSave(EquipmentEntity(
                    id = initial?.id ?: 0,
                    name = name, massPerMeter = mass.toDoubleOrNull() ?: 0.314,
                    spanMeters = span.toDoubleOrNull() ?: 0.85,
                    standardTension = standard.toDoubleOrNull() ?: 160.0,
                    specLower = lower.toDoubleOrNull() ?: 150.0,
                    specUpper = upper.toDoubleOrNull() ?: 180.0,
                    useHzMode = useHz,
                    widthMm = width.toDoubleOrNull() ?: 50.0,
                    thicknessMm = thickness.toDoubleOrNull() ?: 0.8
                ))
            }) { Text("保存") }
        }
    }
}

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val records by vm.records.collectAsState()
    val fmt = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
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
    var pending by remember { mutableStateOf(0) }
    val devices = remember { vm.getInputDevices() }

    LaunchedEffect(Unit) {
        driveUrl = vm.repository.preferences.driveUrl.first()
        driveEnabled = vm.repository.preferences.driveEnabled.first()
        useKgf = vm.repository.preferences.useKgf.first()
        sensitivity = vm.repository.preferences.sensitivity.first().toFloat()
        pending = vm.repository.pendingUploadCount()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("設定", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row { Text("kgf表示"); Switch(useKgf, { useKgf = it; scope.launch { vm.repository.preferences.setUseKgf(it) } }) }
        Text("打撃検出感度")
        Slider(sensitivity, { sensitivity = it; scope.launch { vm.repository.preferences.setSensitivity(it.toDouble()) } }, valueRange = 0.02f..0.3f)
        Text("マイク選択")
        devices.forEach { (id, name) ->
            Button(onClick = { scope.launch { vm.repository.preferences.setMicDeviceId(id); vm.repository.preferences.setMicAuto(false) } }) {
                Text(name)
            }
        }
        Button(onClick = { scope.launch { vm.repository.preferences.setMicAuto(true) } }) { Text("マイク自動選択") }
        OutlinedTextField(driveUrl, { driveUrl = it }, label = { Text("Drive アップロードURL") }, modifier = Modifier.fillMaxWidth())
        Row { Text("自動アップロード"); Switch(driveEnabled, { driveEnabled = it; scope.launch { vm.repository.preferences.setDriveEnabled(it) } }) }
        Button(onClick = { scope.launch { vm.repository.preferences.setDriveUrl(driveUrl); vm.repository.testUpload() } }) { Text("テスト送信") }
        Text("未送信: $pending 件")
        Button(onClick = { scope.launch { vm.repository.processUploadQueue(); pending = vm.repository.pendingUploadCount() } }) { Text("再送") }
    }
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
