package com.bandknife.tension.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bandknife.tension.data.EquipmentEntity
import com.bandknife.tension.ui.components.AppModeSelector
import com.bandknife.tension.ui.components.FlowBottomActionBar
import com.bandknife.tension.ui.components.LocalModeBanner
import com.bandknife.tension.ui.components.SyncGateBanner
import com.bandknife.tension.ui.theme.Dimens
import com.bandknife.tension.viewmodel.AppViewModel
import com.bandknife.tension.viewmodel.SimpleStep
import kotlinx.coroutines.launch

/**
 * シンプル／アドバンス両モードの共通シェル。
 * 設備選択・測定・結果という 3 ステップの骨格と、中止・再測定・保存という
 * 出口の扱いをここに集約し、各モードは中身のスロットだけを差し替える。
 */
@Composable
fun MeasurementFlowScaffold(
    vm: AppViewModel,
    title: String,
    selectContent: @Composable ColumnScope.(List<EquipmentEntity>) -> Unit,
    measureContent: @Composable ColumnScope.(EquipmentEntity?) -> Unit,
    resultContent: @Composable ColumnScope.(EquipmentEntity?) -> Unit
) {
    var showEquipment by rememberSaveable { mutableStateOf(false) }
    var showSpec by rememberSaveable { mutableStateOf(false) }
    var confirmAbort by rememberSaveable { mutableStateOf(false) }
    val selected by vm.selectedEquipment.collectAsState()
    val appMode by vm.appMode.collectAsState()
    val step by vm.simpleStep.collectAsState()
    val equipment by vm.equipment.collectAsState()
    val continuous by vm.continuous.collectAsState()
    val saveMessage by vm.saveMessage.collectAsState()
    val syncMessage by vm.syncMessage.collectAsState()
    val syncState by vm.syncState.collectAsState()
    val localMode by vm.localMode.collectAsState()
    val pendingUploads by vm.pendingUploads.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 期限が近い端末は測定画面に来るたびに確認をやり直し、現場で突然止まらないようにする
    LaunchedEffect(Unit) { vm.ensureFreshSync() }

    if (showSpec) {
        BackHandler { showSpec = false }
        BandKnifeSpecScreen(vm = vm, onBack = { showSpec = false }, initialEquipment = selected)
        return
    }
    if (showEquipment) {
        BackHandler { showEquipment = false }
        EquipmentScreen(vm, onBack = { showEquipment = false })
        return
    }

    // 測定が完了したら結果へ。Composition ではなく副作用として遷移させ、
    // 再コンポーズのたびに録音停止が呼ばれるのを防ぐ。
    LaunchedEffect(continuous.stats) {
        if (continuous.stats != null && step == SimpleStep.MEASURE) {
            vm.stopMeasuring()
            vm.setSimpleStep(SimpleStep.RESULT)
        }
    }

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            val suffix = if (!localMode && pendingUploads > 0) "（未送信 ${pendingUploads} 件）" else ""
            snackbarHostState.showSnackbar(it + suffix)
            vm.clearSaveMessage()
        }
    }

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbarHostState.showSnackbar(it.text)
            vm.clearSyncMessage()
        }
    }

    if (step != SimpleStep.SELECT) {
        BackHandler { confirmAbort = true }
    }

    if (confirmAbort) {
        AlertDialog(
            onDismissRequest = { confirmAbort = false },
            title = { Text("測定を中止しますか？") },
            text = { Text("ここまでの測定値は保存されません。設備の選択に戻ります。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmAbort = false
                    vm.cancelMeasuring()
                }) { Text("中止する") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAbort = false }) { Text("測定を続ける") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            FlowBottomActionBar(
                onEquipment = { showEquipment = true },
                onSpec = { showSpec = true }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Dimens.SpaceLg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = Dimens.SpaceMd, bottom = Dimens.SpaceSm)
            )
            AppModeSelector(
                current = appMode,
                onSelect = { mode -> scope.launch { vm.setAppMode(mode) } }
            )
            if (!localMode && pendingUploads > 0) {
                Text(
                    "未送信 ${pendingUploads} 件 — 電波が入ると自動で送信します",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.SpaceXs)
                )
            }
            if (localMode) {
                LocalModeBanner(modifier = Modifier.padding(top = Dimens.SpaceSm))
            } else {
                SyncGateBanner(
                    state = syncState,
                    onSyncNow = { vm.syncEquipmentNow() },
                    modifier = Modifier.padding(top = Dimens.SpaceSm)
                )
            }
            Spacer(Modifier.height(Dimens.SpaceMd))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (step) {
                    SimpleStep.SELECT -> Column(Modifier.fillMaxSize()) {
                        selectContent(equipment)
                    }
                    SimpleStep.MEASURE -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        measureContent(selected)
                        Spacer(Modifier.height(Dimens.SpaceXl))
                        OutlinedButton(
                            onClick = { confirmAbort = true },
                            modifier = Modifier.fillMaxWidth().height(Dimens.ButtonHeight),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("測定を中止", style = MaterialTheme.typography.titleMedium) }
                        Spacer(Modifier.height(Dimens.SpaceLg))
                    }
                    SimpleStep.RESULT -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        resultContent(selected)
                        Spacer(Modifier.height(Dimens.SpaceXl))
                        ResultActions(
                            passed = selected?.let { eq ->
                                continuous.stats?.let {
                                    vm.repository.evaluate(eq, continuous.frequencies.average(), it.mean)
                                }
                            } ?: false,
                            onSave = { vm.saveAndResetToSelect() },
                            onRemeasure = { vm.discardAndRemeasure() }
                        )
                        Spacer(Modifier.height(Dimens.SpaceLg))
                    }
                }
            }
        }
    }
}

/**
 * 規格を外れているときは、記録より先に測り直せるほうが自然な業務手順になるため、
 * 判定に応じて主ボタンを入れ替える。
 */
@Composable
private fun ResultActions(
    passed: Boolean,
    onSave: () -> Unit,
    onRemeasure: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
    ) {
        if (passed) {
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(Dimens.ButtonHeightLarge)
            ) { Text("記録して終了", style = MaterialTheme.typography.titleLarge) }
            OutlinedButton(
                onClick = onRemeasure,
                modifier = Modifier.fillMaxWidth().height(Dimens.ButtonHeight)
            ) { Text("破棄してもう一度測る", style = MaterialTheme.typography.titleMedium) }
        } else {
            Button(
                onClick = onRemeasure,
                modifier = Modifier.fillMaxWidth().height(Dimens.ButtonHeightLarge)
            ) { Text("調整して測り直す", style = MaterialTheme.typography.titleLarge) }
            OutlinedButton(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(Dimens.ButtonHeight)
            ) { Text("この結果を記録して終了", style = MaterialTheme.typography.titleMedium) }
        }
    }
}