package com.bandknife.tension.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.bandknife.tension.data.EquipmentEntity
import com.bandknife.tension.data.MeasurementRecordEntity
import com.bandknife.tension.domain.GateStatus
import com.bandknife.tension.domain.MeasurementGate
import com.bandknife.tension.domain.BladeMaterialPreset
import com.bandknife.tension.domain.TensionCalculator
import com.bandknife.tension.ui.theme.Dimens
import com.bandknife.tension.ui.theme.statusColors
import com.bandknife.tension.viewmodel.AppViewModel
import com.bandknife.tension.viewmodel.MeasureMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DetailMeasureScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val measure by vm.measureState.collectAsState()
    val continuous by vm.continuous.collectAsState()
    val measureMode by vm.measureMode.collectAsState()
    val selected by vm.selectedEquipment.collectAsState()
    val equipment by vm.equipment.collectAsState()
    val saveMessage by vm.saveMessage.collectAsState()
    val syncState by vm.syncState.collectAsState()
    val localMode by vm.localMode.collectAsState()
    var showSpec by remember { mutableStateOf(false) }
    val hasCompletedStats = continuous.stats != null
    val blockReason = vm.measureBlockReason(selected)

    LaunchedEffect(Unit) { vm.ensureFreshSync() }
    val showLiveResults = !hasCompletedStats && (
        measure.isMeasuring ||
        measure.isArming ||
        measure.frequencyHz > 0 ||
        continuous.currentCount > 0
    )

    if (showSpec) {
        BandKnifeSpecScreen(
            vm = vm,
            onBack = { showSpec = false },
            initialEquipment = selected
        )
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (localMode) {
            com.bandknife.tension.ui.components.LocalModeBanner(
                modifier = Modifier.padding(bottom = Dimens.SpaceMd)
            )
        } else {
            com.bandknife.tension.ui.components.SyncGateBanner(
                state = syncState,
                onSyncNow = { vm.syncEquipmentNow() },
                modifier = Modifier.padding(bottom = Dimens.SpaceMd)
            )
        }
        com.bandknife.tension.ui.components.EquipmentComboBox(
            equipment = equipment,
            selected = selected,
            onSelect = { vm.selectEquipment(it) },
            enabled = !measure.isMeasuring && !measure.isArming
        )
        if (!localMode && selected?.isSynced == false) {
            com.bandknife.tension.ui.components.UnsyncedEquipmentBadge(
                modifier = Modifier.padding(top = Dimens.SpaceSm)
            )
        }
        TextButton(
            onClick = { showSpec = true },
            enabled = selected != null && !measure.isMeasuring && !measure.isArming
        ) { Text("スペック設定") }
        Text("マイク: ${measure.micName}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))

        com.bandknife.tension.ui.components.MeasureStatusBar(
            isMeasuring = measure.isMeasuring,
            measureMode = measureMode,
            currentCount = continuous.currentCount,
            targetCount = continuous.targetCount,
            tapMessage = measure.tapMessage,
            amplitude = measure.amplitude,
            signalQuality = measure.signalQuality,
            stackedTapCount = measure.stackedTapCount,
            hasCompletedStats = continuous.stats != null,
            savedToHistory = continuous.savedRecordId != null,
            isArming = measure.isArming,
            armingSecondsLeft = measure.armingSecondsLeft
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

        if (showLiveResults) {
            if (measure.isArming) {
                Text(
                    "まだ叩かないでください（あと ${measure.armingSecondsLeft} 秒）",
                    style = MaterialTheme.typography.headlineSmall,
                    color = statusColors.warnText,
                    modifier = Modifier.padding(vertical = Dimens.SpaceLg)
                )
            }
            Text("${"%.1f".format(measure.frequencyHz)} Hz", style = MaterialTheme.typography.displaySmall)
            Text(
                vm.formatTension(measure.tensionN),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (measure.frequencyHz > 0) {
                com.bandknife.tension.ui.components.PassFailBadge(measure.passed)
            }
            if (measureMode == MeasureMode.CONTINUOUS) {
                Text(
                    "${continuous.currentCount} / ${continuous.targetCount} 回",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = Dimens.SpaceXs)
                )
            }
            if (measure.isMeasuring) {
                com.bandknife.tension.ui.components.TapLevelMeter(
                    measure.amplitude,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                com.bandknife.tension.ui.components.SpectrumChart(measure.spectrum)
            }
        } else if (!hasCompletedStats) {
            // 「—」だけだと欠損やレイアウト崩れに見えるため、状態を言葉で示す
            Text(
                "周波数 未測定",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "張力 未測定",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "「開始」を押すと、ここに周波数と張力が表示されます",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Dimens.SpaceSm)
            )
        }

        measure.noiseWarning?.let {
            Text(
                it,
                color = statusColors.warnText,
                modifier = Modifier.padding(top = Dimens.SpaceXs)
            )
        }

        Spacer(Modifier.height(8.dp))
        com.bandknife.tension.ui.components.MeasureModeSelector(
            selected = measureMode,
            onSelect = { vm.setMeasureMode(it) },
            enabled = !measure.isMeasuring && !measure.isArming
        )
        com.bandknife.tension.ui.components.MeasureStartStopButtons(
            isMeasuring = measure.isMeasuring,
            isArming = measure.isArming,
            canStart = blockReason == null,
            onStart = { vm.startMeasuring() },
            onStop = { vm.stopMeasuring() }
        )
        if (blockReason != null && !measure.isMeasuring && !measure.isArming) {
            Text(
                blockReason,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColors.ngText,
                modifier = Modifier.padding(top = Dimens.SpaceSm)
            )
        }

        saveMessage?.let {
            val saved = continuous.savedRecordId != null
            Text(
                it,
                color = if (saved) statusColors.okText else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Dimens.SpaceSm)
            )
        }

        Spacer(Modifier.height(16.dp))
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
                .padding(Dimens.CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("測定結果", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimens.SpaceSm))
            Text(
                formatTension(stats.mean),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text("測定 ${stats.values.size} 回", style = MaterialTheme.typography.bodyMedium)
            com.bandknife.tension.ui.components.PassFailBadge(passed)
            Spacer(Modifier.height(Dimens.SpaceMd))
            if (saved) {
                Text(
                    "履歴に記録しました",
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColors.okText
                )
            } else {
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().height(Dimens.ButtonHeight)
                ) {
                    Text("記録する", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EquipmentScreen(
    vm: AppViewModel,
    onBack: (() -> Unit)? = null,
    initialShowDeleted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val equipment by vm.equipment.collectAsState()
    val deletedEquipment by vm.deletedEquipment.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var showDeleted by remember { mutableStateOf(initialShowDeleted) }
    var showSpec by remember { mutableStateOf(false) }
    var specTarget by remember { mutableStateOf<EquipmentEntity?>(null) }
    var editing by remember { mutableStateOf<EquipmentEntity?>(null) }
    var deletingEquipment by remember { mutableStateOf<EquipmentEntity?>(null) }
    val currentUser by vm.currentUser.collectAsState()
    val localMode by vm.localMode.collectAsState()
    // 未設定のまま操作されたら、設定を挟んでから元の操作を続ける
    var pendingEdit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()

    fun requireUser(action: () -> Unit) {
        if (currentUser == null) pendingEdit = action else action()
    }

    pendingEdit?.let { action ->
        SignInDialog(
            vm = vm,
            onDismiss = { pendingEdit = null },
            onSuccess = { pendingEdit = null; action() }
        )
    }

    if (showSpec && specTarget != null) {
        BandKnifeSpecScreen(
            vm = vm,
            onBack = { showSpec = false; specTarget = null },
            initialEquipment = specTarget
        )
        return
    }

    deletingEquipment?.let { eq ->
        AlertDialog(
            onDismissRequest = { deletingEquipment = null },
            title = { Text("設備の削除") },
            text = {
                Text(
                    if (eq.isSynced) {
                        "「${eq.name}」を設備一覧から外します。測定履歴は残ります。\n\n" +
                            "ドライブの設備マスターにはまだ残るため、次の「今すぐ確認」で元に戻ります。" +
                            "全端末から消すには、削除したあとに「ドライブへ登録」を実行してください。"
                    } else {
                        "「${eq.name}」を設備一覧から外します。測定履歴は残ります。\n\n" +
                            "あとで「設備管理」→「削除済み設備」から元に戻せます。"
                    }
                )
            },
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
            onDismiss = { showForm = false },
            modifier = modifier
        ) { entity ->
            scope.launch {
                if (entity.id == 0L) vm.repository.saveEquipment(entity) else vm.repository.updateEquipment(entity)
                showForm = false
            }
        }
        showDeleted -> DeletedEquipmentScreen(
            deletedEquipment = deletedEquipment,
            onBack = { showDeleted = false },
            onRestore = { eq -> requireUser { vm.restoreEquipment(eq) } },
            onPermanentDelete = { eq -> requireUser { vm.permanentlyDeleteEquipment(eq) } },
            modifier = modifier
        )
        else -> Column(modifier.fillMaxSize().padding(Dimens.SpaceLg)) {
            onBack?.let { back ->
                TextButton(onClick = back) { Text("← 戻る") }
            }
            Text(
                "設備管理",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = Dimens.SpaceMd)
            )
            if (currentUser == null) {
                EquipmentEditLockedBanner(onSetUp = { pendingEdit = {} })
                Spacer(Modifier.height(Dimens.SpaceSm))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { requireUser { editing = null; showForm = true } },
                    modifier = Modifier.weight(1f)
                ) { Text("設備を追加") }
                OutlinedButton(
                    onClick = { showDeleted = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (deletedEquipment.isEmpty()) "削除済み設備"
                        else "削除済み（${deletedEquipment.size}）"
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
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
                items(equipment, key = { it.id }) { eq ->
                    Card(Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceXs)) {
                        Column(Modifier.padding(Dimens.SpaceMd)) {
                            Text(eq.name, style = MaterialTheme.typography.titleMedium)
                            Text("単位質量 ${eq.massPerMeter} kg/m / スパン長 ${eq.spanMeters} m")
                            Text("標準張力 ${vm.formatStandard(eq)} / 規格 ${vm.formatSpecRange(eq)}")
                            if (!localMode && !eq.isSynced) {
                                com.bandknife.tension.ui.components.UnsyncedEquipmentBadge(
                                    modifier = Modifier.padding(top = Dimens.SpaceSm)
                                )
                            }
                            // 4 個を 1 行に詰めると手袋では押し分けられないため折り返す
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpaceSm),
                                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
                            ) {
                                Button(
                                    onClick = { requireUser { editing = eq; showForm = true } },
                                    modifier = Modifier.heightIn(min = Dimens.MinTouch)
                                ) { Text("編集") }
                                OutlinedButton(
                                    onClick = { requireUser { specTarget = eq; showSpec = true } },
                                    modifier = Modifier.heightIn(min = Dimens.MinTouch)
                                ) { Text("刃のスペック") }
                                OutlinedButton(
                                    onClick = { requireUser { editing = eq.asNewProfile(); showForm = true } },
                                    modifier = Modifier.heightIn(min = Dimens.MinTouch)
                                ) { Text("複製") }
                                // 破壊的操作は主要操作と同じ塗りにしない
                                OutlinedButton(
                                    onClick = { requireUser { deletingEquipment = eq } },
                                    modifier = Modifier.heightIn(min = Dimens.MinTouch),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(Modifier.width(Dimens.SpaceXs))
                                    Text("削除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 設備を変更できない理由と、その場で解消する手段を同じ場所に置く。
 * 測定は続けられるので、合否の赤緑ではなく注意の色で出す。
 */
@Composable
private fun EquipmentEditLockedBanner(onSetUp: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Column(Modifier.padding(Dimens.CardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.width(Dimens.SpaceSm))
                Text("設備の変更には使用者の設定が必要です", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "誰が規格値を変更したかを記録するため、名前とパスワードを設定してください。" +
                    "設定は一度だけで、次回から入力は不要です。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Dimens.SpaceXs)
            )
            Button(
                onClick = onSetUp,
                modifier = Modifier
                    .padding(top = Dimens.SpaceSm)
                    .heightIn(min = Dimens.MinTouch)
            ) { Text("使用者を設定する") }
        }
    }
}

@Composable
private fun DeletedEquipmentScreen(
    deletedEquipment: List<EquipmentEntity>,
    onBack: () -> Unit,
    onRestore: (EquipmentEntity) -> Unit,
    onPermanentDelete: (EquipmentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var permanentDeleteTarget by remember { mutableStateOf<EquipmentEntity?>(null) }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN) }

    permanentDeleteTarget?.let { eq ->
        AlertDialog(
            onDismissRequest = { permanentDeleteTarget = null },
            title = { Text("完全削除") },
            text = {
                Text(
                    "「${eq.name}」を完全に削除しますか？\n\n" +
                        "この操作は取り消せません。設備一覧・削除済み一覧の両方から消えます。\n" +
                        "測定履歴の記録は残ります。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onPermanentDelete(eq)
                    permanentDeleteTarget = null
                }) { Text("完全削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { permanentDeleteTarget = null }) { Text("キャンセル") }
            }
        )
    }

    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().padding(Dimens.SpaceLg)) {
        TextButton(onClick = onBack) { Text("← 設備一覧に戻る") }
        Text("削除済み設備", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = Dimens.SpaceSm))
        Text(
            "復元すると設備一覧に戻ります。完全削除すると一覧から消え、元に戻せません。",
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
                        Text(eq.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            eq.deletedAt?.let { "削除日時: ${dateFormat.format(Date(it))}" }
                                ?: "削除日時: 不明（更新前に削除された設備）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text("m=${eq.massPerMeter} kg/m  S=${eq.spanMeters} m")
                        Text("標準 ${eq.standardTension.toInt()} N  規格 ${eq.specLower.toInt()}〜${eq.specUpper.toInt()} N")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onRestore(eq) }) { Text("復元") }
                            OutlinedButton(
                                onClick = { permanentDeleteTarget = eq },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) { Text("完全削除") }
                        }
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
    modifier: Modifier = Modifier,
    onSave: (EquipmentEntity) -> Unit
) {
    val isNew = initial?.id == 0L
    var selectedTemplate by remember(initial) { mutableStateOf<EquipmentEntity?>(null) }
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var mass by remember(initial) { mutableStateOf((initial?.massPerMeter ?: 0.844).toString()) }
    var span by remember(initial) { mutableStateOf((initial?.spanMeters ?: TensionCalculator.DEFAULT_SPAN_METERS).toString()) }
    var standard by remember(initial) { mutableStateOf((initial?.standardTension ?: 160.0).toString()) }
    var lower by remember(initial) { mutableStateOf((initial?.specLower ?: 150.0).toString()) }
    var upper by remember(initial) { mutableStateOf((initial?.specUpper ?: 180.0).toString()) }
    var width by remember(initial) { mutableStateOf((initial?.widthMm ?: 86.0).toString()) }
    var thickness by remember(initial) { mutableStateOf((initial?.thicknessMm ?: 1.25).toString()) }
    var density by remember(initial) { mutableStateOf(initial?.density ?: BladeMaterialPreset.CARBON_STEEL.densityKgM3) }
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

    val specOrderValid = specRangeValid(lower, upper)
    val canSave = name.isNotBlank() &&
        positiveNumbers(width, thickness, mass, span, standard) &&
        lower.toDoubleOrNull() != null && upper.toDoubleOrNull() != null &&
        specOrderValid

    BackHandler(onBack = onDismiss)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.SpaceLg)) {
        Text(
            if (isNew) "設備を追加" else "設備を編集",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = Dimens.SpaceMd)
        )
        if (isNew && templates.isNotEmpty()) {
            com.bandknife.tension.ui.components.EquipmentComboBox(
                equipment = templates,
                selected = selectedTemplate,
                onSelect = { selectedTemplate = it },
                label = "既存の設定をコピー（任意）"
            )
            Spacer(Modifier.height(Dimens.SpaceMd))
        }
        OutlinedTextField(
            name,
            { name = it },
            label = { Text("設備名") },
            isError = name.isBlank(),
            supportingText = { if (name.isBlank()) Text("設備名を入力してください") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        NumberField(width, "幅 (mm)", onValueChange = {
            width = it
            mass = TensionCalculator.massFromDimensions(
                it.toDoubleOrNull()?.div(1000) ?: 0.0,
                thickness.toDoubleOrNull()?.div(1000) ?: 0.0,
                density
            ).toString()
        })
        NumberField(thickness, "板厚 (mm)") {
            thickness = it
            mass = TensionCalculator.massFromDimensions(
                width.toDoubleOrNull()?.div(1000) ?: 0.0,
                it.toDoubleOrNull()?.div(1000) ?: 0.0,
                density
            ).toString()
        }
        NumberField(mass, "単位質量 (kg/m)") { mass = it }
        NumberField(span, "スパン長 (m)") { span = it }
        NumberField(standard, "標準張力 (N)") { standard = it }
        NumberField(
            lower,
            "規格下限 (N)",
            errorText = if (!specOrderValid) "規格下限は上限より小さい値にしてください" else null
        ) { lower = it }
        NumberField(
            upper,
            "規格上限 (N)",
            errorText = if (!specOrderValid) "規格上限は下限より大きい値にしてください" else null,
            imeAction = ImeAction.Done
        ) { upper = it }
        SpecModeSwitch(useHz) { useHz = it }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = Dimens.MinTouch)
            ) { Text("キャンセル") }
            Button(enabled = canSave, modifier = Modifier.heightIn(min = Dimens.MinTouch), onClick = {
                val source = baseEntity ?: EquipmentEntity(
                    name = name,
                    massPerMeter = 0.844,
                    spanMeters = TensionCalculator.DEFAULT_SPAN_METERS,
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
                    density = density,
                    deleted = false,
                    deletedAt = null
                ))
            }) { Text(if (isNew) "追加" else "保存") }
        }
    }
}

private fun EquipmentEntity.asNewProfile(): EquipmentEntity =
    copy(id = 0, uuid = "", name = "$name（コピー）", deleted = false, deletedAt = null)

/**
 * 数値入力の共通フィールド。
 * 数値専用キーボードを出し、不正値を黙って旧値に戻す代わりにその場で理由を示す。
 */
@Composable
internal fun NumberField(
    value: String,
    label: String,
    errorText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onValueChange: (String) -> Unit
) {
    val parsed = value.toDoubleOrNull()
    val invalid = parsed == null || parsed <= 0
    val message = errorText ?: if (invalid) "0 より大きい数値を入力してください" else null
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = message != null,
        supportingText = { message?.let { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SpecModeSwitch(useHz: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = useHz, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("周波数（Hz）で判定する", style = MaterialTheme.typography.bodyLarge)
            Text(
                "オフのときは張力（N）で判定します",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = useHz, onCheckedChange = null)
    }
}

internal fun positiveNumbers(vararg values: String): Boolean =
    values.all { (it.toDoubleOrNull() ?: 0.0) > 0 }

internal fun specRangeValid(lower: String, upper: String): Boolean {
    val l = lower.toDoubleOrNull() ?: return false
    val u = upper.toDoubleOrNull() ?: return false
    return l < u
}

@Composable
fun HistoryScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val records by vm.records.collectAsState()
    val fmt = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN) }
    var deletingRecord by remember { mutableStateOf<MeasurementRecordEntity?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var exportMessage by remember { mutableStateOf<String?>(null) }

    deletingRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { deletingRecord = null },
            title = { Text("記録の削除") },
            text = {
                Text(
                    "「${record.equipmentName}」の ${fmt.format(Date(record.timestamp))} の記録を削除します。\n\n" +
                        "この操作は取り消せません。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRecord(record)
                    deletingRecord = null
                }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingRecord = null }) { Text("キャンセル") }
            }
        )
    }

    LazyColumn(modifier.fillMaxSize().padding(Dimens.SpaceLg)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = Dimens.SpaceMd),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("履歴", style = MaterialTheme.typography.headlineMedium)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            if (records.isEmpty()) {
                                exportMessage = "書き出す記録がありません"
                            } else {
                                com.bandknife.tension.util.RecordsShare.shareCsv(
                                    context,
                                    vm.buildRecordsCsv()
                                )
                                exportMessage = "共有画面を開きました"
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = Dimens.MinTouch)
                ) { Text("CSV書き出し") }
            }
            exportMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (records.isEmpty()) {
            item {
                Text(
                    "記録された測定結果はありません",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Dimens.SpaceXl)
                )
            }
        }
        items(records, key = { it.id }) { record ->
            RecordCard(record, fmt, vm, onRequestDelete = { deletingRecord = record })
        }
    }
}

@Composable
private fun RecordCard(
    record: MeasurementRecordEntity,
    fmt: SimpleDateFormat,
    vm: AppViewModel,
    onRequestDelete: () -> Unit
) {
    var comment by remember(record.id) { mutableStateOf(record.comment) }
    val status = statusColors
    Card(Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceXs)) {
        Column(Modifier.padding(Dimens.SpaceMd)) {
            Text(
                "${record.equipmentName} / ${fmt.format(Date(record.timestamp))}",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
            ) {
                Text(vm.formatTension(record.tensionN), style = MaterialTheme.typography.titleMedium)
                if (record.localOnly) {
                    com.bandknife.tension.ui.components.LocalOnlyRecordBadge()
                }
                Icon(
                    if (record.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (record.passed) status.okText else status.ngText
                )
                Text(
                    if (record.passed) "OK" else "要調整",
                    color = if (record.passed) status.okText else status.ngText
                )
                Text("測定 ${record.sampleCount} 回", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!record.isLocked) {
                OutlinedTextField(
                    comment,
                    { comment = it },
                    label = { Text("コメント") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { vm.updateRecordComment(record, comment) },
                    modifier = Modifier.heightIn(min = Dimens.MinTouch)
                ) { Text("コメント保存") }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = status.okText)
                    Text("ロック中（編集・削除できません）", color = status.okText)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = Dimens.SpaceSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { vm.toggleRecordLock(record) },
                    modifier = Modifier.heightIn(min = Dimens.MinTouch)
                ) {
                    Icon(
                        if (record.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(Dimens.SpaceXs))
                    Text(if (record.isLocked) "ロックを解除" else "記録をロック")
                }
                // 誤タップを避けるため、削除は反対の端に離して置く
                Spacer(Modifier.weight(1f))
                if (!record.isLocked) {
                    TextButton(
                        onClick = onRequestDelete,
                        modifier = Modifier.heightIn(min = Dimens.MinTouch),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(Dimens.SpaceXs))
                        Text("削除")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val localMode by vm.localMode.collectAsState()
    var driveUrl by remember { mutableStateOf("") }
    var driveEnabled by remember { mutableStateOf(false) }
    var useKgf by remember { mutableStateOf(false) }
    var vibrationEnabled by remember { mutableStateOf(true) }
    var sensitivity by remember { mutableStateOf(0.08f) }
    var builtinMicSpecialMode by remember { mutableStateOf(false) }
    var selectedMicDeviceId by remember { mutableStateOf(-1) }
    val devices = remember(builtinMicSpecialMode) { vm.getInputDevices() }
    val pending by vm.pendingUploads.collectAsState()
    var uploadMessage by remember { mutableStateOf<String?>(null) }
    var uploadSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.repository.preferences.ensureDriveUrlNormalized()
        driveUrl = vm.repository.preferences.driveUrl.first()
        driveEnabled = vm.repository.preferences.driveEnabled.first()
        useKgf = vm.repository.preferences.useKgf.first()
        vibrationEnabled = vm.repository.preferences.vibrationEnabled.first()
        sensitivity = vm.repository.preferences.sensitivity.first().toFloat()
        builtinMicSpecialMode = vm.repository.preferences.builtinMicSpecialMode.first()
        vm.applyBuiltinMicSpecialMode(builtinMicSpecialMode)
        selectedMicDeviceId = vm.repository.preferences.micDeviceId.first()
        vm.refreshPendingUploads()
    }

    // スクロールは呼び出し元に 1 箇所だけ持たせる
    Column(modifier.fillMaxWidth().padding(Dimens.SpaceLg)) {
        Text("設定", style = MaterialTheme.typography.headlineMedium)
        SettingSwitchRow(
            label = "張力を kgf で表示する",
            description = "オフのときは N（ニュートン）で表示します",
            checked = useKgf,
            onCheckedChange = { useKgf = it; scope.launch { vm.repository.preferences.setUseKgf(it) } }
        )
        SettingSwitchRow(
            label = "打撃を振動で知らせる",
            description = "測定中は画面を見られないため、成功と失敗を振動パターンで区別します",
            checked = vibrationEnabled,
            onCheckedChange = {
                vibrationEnabled = it
                scope.launch { vm.repository.preferences.setVibration(it) }
            }
        )
        Spacer(Modifier.height(Dimens.SpaceLg))
        StrikeSensitivitySection(
            sensitivity = sensitivity,
            onSensitivityChange = { value ->
                sensitivity = value
                scope.launch { vm.repository.preferences.setSensitivity(value.toDouble()) }
            }
        )
        Spacer(Modifier.height(16.dp))
        SettingSwitchRow(
            label = "特別モード（内蔵マイクを許可）",
            description = "USBマイクがないときやテスト時のみ有効にしてください。通常はオフのままにしてください",
            checked = builtinMicSpecialMode,
            onCheckedChange = {
                builtinMicSpecialMode = it
                vm.setBuiltinMicSpecialMode(it)
            }
        )
        Spacer(Modifier.height(Dimens.SpaceMd))
        MicSelectionSection(
            devices = devices,
            builtinMicSpecialMode = builtinMicSpecialMode,
            selectedMicDeviceId = selectedMicDeviceId,
            onSelectDevice = { id ->
                selectedMicDeviceId = id
                scope.launch { vm.repository.preferences.setMicDeviceId(id) }
            }
        )
        Spacer(Modifier.height(Dimens.SpaceLg))
        LocalModeSection(vm)
        Spacer(Modifier.height(Dimens.SpaceXl))
        if (!localMode) {
        Text("アップロード", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            driveUrl,
            { driveUrl = it },
            label = { Text("アップロード先 URL") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        SettingSwitchRow(
            label = "測定後に自動アップロード",
            description = "圏外のときは端末に貯めておき、つながったときに送信します",
            checked = driveEnabled,
            onCheckedChange = {
                driveEnabled = it
                scope.launch { vm.repository.preferences.setDriveEnabled(it) }
            }
        )
        Text(
            if (pending > 0) "未送信 $pending 件" else "未送信のデータはありません",
            color = if (pending > 0) statusColors.warnText else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = Dimens.SpaceSm)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            UploadButton(
                label = "接続テスト",
                isUploading = isUploading,
                onClick = {
                    scope.launch {
                        isUploading = true
                        uploadMessage = null
                        vm.repository.preferences.setDriveUrl(driveUrl)
                        val result = vm.repository.testUpload()
                        uploadSuccess = result.success
                        uploadMessage = result.message
                        isUploading = false
                    }
                }
            )
            UploadButton(
                label = "未送信データを送る",
                isUploading = isUploading,
                onClick = {
                    scope.launch {
                        isUploading = true
                        uploadMessage = null
                        val result = vm.repository.processUploadQueue()
                        vm.refreshPendingUploads()
                        uploadSuccess = result.sentCount > 0 || result.remainingCount == 0
                        uploadMessage = result.message
                        isUploading = false
                    }
                }
            )
        }
        uploadMessage?.let { message ->
            Spacer(Modifier.height(Dimens.SpaceSm))
            Text(
                message,
                color = when (uploadSuccess) {
                    true -> statusColors.okText
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
        }
        Spacer(Modifier.height(Dimens.SpaceXl))
        if (!localMode) {
        UserAccountSection(vm)
        Spacer(Modifier.height(Dimens.SpaceXl))
        EquipmentMasterSection(vm)
        Spacer(Modifier.height(Dimens.SpaceXl))
        EquipmentHistorySection(vm)
        }
    }
}

/**
 * 設備マスターはドライブが正。端末側は写しに過ぎず、確認が途切れると測定できなくなるため、
 * 期限と最終確認をここで常に見えるようにしておく。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquipmentMasterSection(vm: AppViewModel, modifier: Modifier = Modifier) {
    val syncState by vm.syncState.collectAsState()
    val equipment by vm.equipment.collectAsState()
    val syncMessage by vm.syncMessage.collectAsState()
    val currentUser by vm.currentUser.collectAsState()
    val removal by vm.removalConfirmation.collectAsState()
    val unsynced = equipment.count { !it.isSynced }
    var confirmPush by remember { mutableStateOf(false) }
    var requestSignIn by remember { mutableStateOf(false) }

    val freshnessText = when (syncState.status) {
        GateStatus.BLOCKED -> if (syncState.gate.neverSynced) {
            "未確認のため測定できません"
        } else {
            "接続確認が ${syncState.gate.elapsedDays} 日途切れているため測定できません"
        }
        GateStatus.EXPIRING -> "あと ${syncState.gate.remainingDays} 日で測定できなくなります"
        GateStatus.VALID -> "あと ${syncState.gate.remainingDays} 日有効です"
    }
    val freshnessColor = when (syncState.status) {
        GateStatus.BLOCKED -> statusColors.ngText
        GateStatus.EXPIRING -> statusColors.warnText
        GateStatus.VALID -> statusColors.okText
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            "設備マスターの共有",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            "設備の設定はドライブで共有します。ドライブに無い設備では測定できません。" +
                "また ${MeasurementGate.VALID_DAYS} 日以上ドライブを確認できないと測定を停止します。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.SpaceXs)
        )
        Text(
            "最終確認 ${syncState.lastSyncText}（第 ${syncState.revision} 版）",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Dimens.SpaceSm)
        )
        Text(freshnessText, style = MaterialTheme.typography.bodyMedium, color = freshnessColor)
        if (unsynced > 0) {
            Text(
                "ドライブ未登録のため使えない設備が $unsynced 件あります",
                style = MaterialTheme.typography.bodyMedium,
                color = statusColors.warnText,
                modifier = Modifier.padding(top = Dimens.SpaceXs)
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SpaceSm)
        ) {
            UploadButton(
                label = "今すぐ確認",
                busyLabel = "確認中",
                isBusy = syncState.syncing,
                enabled = !syncState.busy,
                onClick = { vm.syncEquipmentNow() }
            )
            UploadButton(
                label = "ドライブへ登録",
                busyLabel = "登録中",
                isBusy = syncState.pushing,
                enabled = !syncState.busy,
                // 未設定のまま押させて失敗させるより、その場で設定に誘導する
                onClick = { if (currentUser == null) requestSignIn = true else confirmPush = true }
            )
        }
        if (currentUser == null) {
            Text(
                "「ドライブへ登録」には使用者の設定が必要です",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.SpaceXs)
            )
        }
        // 詳細モードにはスナックバーが無いため、通信結果はこの場に残す
        syncMessage?.let { message ->
            Text(
                message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isError) MaterialTheme.colorScheme.error else statusColors.okText,
                modifier = Modifier
                    .padding(top = Dimens.SpaceSm)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }

    if (confirmPush) {
        PushConfirmDialog(
            title = "この端末の設備をドライブに登録しますか？",
            body = "ドライブの設備マスターは、この端末の設備 ${equipment.size} 件で置き換わります。" +
                "他の端末は次回の確認時にこの内容を取り込みます。" +
                "更新者として「${currentUser?.name.orEmpty()}」が記録されます。",
            confirmLabel = "置き換える",
            onConfirm = { confirmPush = false; vm.registerEquipmentToDrive() },
            onDismiss = { confirmPush = false }
        )
    }
    // 消える設備があるときは、内容を見せてから改めて実行させる
    removal?.let { pending ->
        PushConfirmDialog(
            title = "${pending.names.size} 件の設備が全端末から消えます",
            body = "消えるのは ${pending.names.joinToString("、")} です。" +
                "これらの設備では、どの端末でも測定できなくなります。よろしいですか？",
            confirmLabel = "消して登録する",
            onConfirm = {
                vm.dismissRemovalConfirmation()
                vm.registerEquipmentToDrive(confirmRemoval = true)
            },
            onDismiss = { vm.dismissRemovalConfirmation() }
        )
    }
    if (requestSignIn) {
        SignInDialog(
            vm = vm,
            onDismiss = { requestSignIn = false },
            onSuccess = { requestSignIn = false }
        )
    }
}

/** 全端末に伝わる操作なので、確定側は破壊的操作と同じ色で出す。 */
@Composable
private fun PushConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = Dimens.MinTouch),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = Dimens.MinTouch)
            ) { Text("やめる") }
        }
    )
}

@Composable
private fun UploadButton(
    label: String,
    isUploading: Boolean,
    onClick: () -> Unit
) = UploadButton(label, "送信中", isUploading, !isUploading, onClick)

@Composable
private fun UploadButton(
    label: String,
    busyLabel: String,
    isBusy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.heightIn(min = Dimens.MinTouch)
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(Dimens.SpaceSm))
            Text(busyLabel)
        } else {
            Text(label)
        }
    }
}

/** ラベル全体を操作対象にして、手袋でもスイッチのつまみを狙わずに切り替えられるようにする。 */
@Composable
private fun SettingSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun MicSelectionSection(
    devices: List<Pair<Int, String>>,
    builtinMicSpecialMode: Boolean,
    selectedMicDeviceId: Int,
    onSelectDevice: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text("マイク", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.SpaceXs))
        Text(
            if (builtinMicSpecialMode) {
                "通常は USB マイクを優先します。特別モードでは内蔵マイクも選べます。"
            } else {
                "USB マイクを自動で優先します。内蔵マイクは特別モードでのみ使用できます。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.SpaceMd))
        if (devices.isEmpty()) {
            Text(
                if (builtinMicSpecialMode) "使用できるマイクがありません"
                else "USBマイクが接続されていません",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                devices.forEach { (id, name) ->
                    FilterChip(
                        selected = selectedMicDeviceId == id || (selectedMicDeviceId < 0 && id == devices.first().first),
                        onClick = { onSelectDevice(id) },
                        label = { Text(name) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouch)
                    )
                }
            }
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
        Text("打音の検出感度", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.SpaceXs))
        Text(
            "打音の大きさがこの値以上のとき測定を開始します",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.SpaceMd))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            presets.forEach { (label, value) ->
                FilterChip(
                    selected = abs(sensitivity - value) < 0.02f,
                    onClick = { onSensitivityChange(value) },
                    label = { Text(label) },
                    modifier = Modifier.heightIn(min = Dimens.MinTouch)
                )
            }
        }
        Spacer(Modifier.height(Dimens.SpaceMd))
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
fun HelpScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(Dimens.SpaceLg)) {
        Text("ヘルプ", style = MaterialTheme.typography.headlineMedium)
        Text("原理: T = 4 × m × S² × f²")
        Text("• 樹脂の柄や指の腹でスパン中央を軽く叩く")
        Text("• 金属で叩くと高調波ノイズが発生します")
        Text("• USB-C マイクを接続して測定してください（内蔵マイクは設定の特別モードでのみ使用可）")
        Text("• 測定前に必ず機械を停止してください")
        Spacer(Modifier.height(Dimens.SpaceMd))
        Text("設備の復元", style = MaterialTheme.typography.titleMedium)
        Text("削除した設備は「設備管理」→「削除済み設備」から復元できます")
        Text("削除日時が表示されます。不要な設備は「完全削除」で一覧から消せます（測定履歴は残ります）")
        Text("シンプル／アドバンスモードでは画面下の「設備管理」ボタンから開けます")
        Text("詳細モードでは下部メニューの「設備」タブから開けます")
    }
}
