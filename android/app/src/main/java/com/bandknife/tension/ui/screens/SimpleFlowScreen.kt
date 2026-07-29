package com.bandknife.tension.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bandknife.tension.data.EquipmentEntity
import com.bandknife.tension.ui.components.AdjustmentCard
import com.bandknife.tension.ui.components.PassFailBadge
import com.bandknife.tension.ui.components.SpecRangeBar
import com.bandknife.tension.ui.components.TapFeedbackCard
import com.bandknife.tension.ui.components.TapLevelMeter
import com.bandknife.tension.ui.components.UnsyncedEquipmentBadge
import com.bandknife.tension.ui.theme.Dimens
import com.bandknife.tension.ui.theme.statusColors
import com.bandknife.tension.viewmodel.AppViewModel

@Composable
fun SimpleFlowScreen(vm: AppViewModel) {
    MeasurementFlowScaffold(
        vm = vm,
        title = "バンドナイフ張力計",
        selectContent = { equipment -> SimpleSelectStep(vm, equipment) },
        measureContent = { selected -> SimpleMeasureStep(vm, selected) },
        resultContent = { selected -> SimpleResultStep(vm, selected) }
    )
}

@Composable
private fun SimpleSelectStep(vm: AppViewModel, equipment: List<EquipmentEntity>) {
    Column(Modifier.fillMaxSize()) {
        Text("設備を選んでください", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Dimens.SpaceLg))
        if (equipment.isEmpty()) {
            Text(
                EMPTY_EQUIPMENT_MESSAGE,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Dimens.SpaceLg)
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            contentPadding = PaddingValues(bottom = Dimens.SpaceLg)
        ) {
            items(equipment, key = { it.id }) { eq ->
                EquipmentSelectCard(vm, eq) {
                    vm.selectEquipment(eq)
                    vm.setSimpleStep(com.bandknife.tension.viewmodel.SimpleStep.MEASURE)
                    vm.startMeasuring()
                }
            }
        }
    }
}

@Composable
private fun EquipmentSelectCard(vm: AppViewModel, eq: EquipmentEntity, onClick: () -> Unit) {
    val records by vm.records.collectAsState()
    val localMode by vm.localMode.collectAsState()
    val last = records.filter { it.equipmentId == eq.id }.maxByOrNull { it.timestamp }
    val blockReason = vm.measureBlockReason(eq)
    val selectable = blockReason == null
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = selectable, onClick = onClick),
        elevation = CardDefaults.cardElevation(if (selectable) 4.dp else 0.dp),
        colors = if (selectable) CardDefaults.cardColors()
        else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(Dimens.SpaceXl)) {
            Text(
                eq.name,
                style = MaterialTheme.typography.headlineSmall,
                color = if (selectable) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "標準張力 ${vm.formatStandard(eq)} / 規格 ${vm.formatSpecRange(eq)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (selectable) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!localMode && !eq.isSynced) {
                UnsyncedEquipmentBadge(modifier = Modifier.padding(top = Dimens.SpaceSm))
            }
            blockReason?.let { reason ->
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Dimens.SpaceSm)
                )
            }
            last?.let { record ->
                Text(
                    "前回 ${vm.formatTension(record.tensionN)}（${if (record.passed) "OK" else "要調整"}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (record.passed) statusColors.okText else statusColors.ngText,
                    modifier = Modifier.padding(top = Dimens.SpaceXs)
                )
            }
        }
    }
}

@Composable
private fun SimpleMeasureStep(vm: AppViewModel, equipment: EquipmentEntity?) {
    val continuous by vm.continuous.collectAsState()
    val measure by vm.measureState.collectAsState()

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(equipment?.name ?: "", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.SpaceXl))
        if (measure.isArming) {
            Text(
                "まだ叩かないでください",
                style = MaterialTheme.typography.headlineSmall,
                color = statusColors.warnText
            )
            Text(
                "測定開始まで ${measure.armingSecondsLeft} 秒",
                style = MaterialTheme.typography.headlineMedium,
                color = statusColors.warnText,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        } else {
            Text(
                "刃の中央を${continuous.targetCount}回軽く叩いてください",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Spacer(Modifier.height(Dimens.SpaceXl))
        Text(
            "${continuous.currentCount} / ${continuous.targetCount}",
            style = MaterialTheme.typography.displayLarge,
            color = if (measure.isArming) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "${continuous.targetCount}回中${continuous.currentCount}回"
            }
        )
        Spacer(Modifier.height(Dimens.SpaceLg))
        if (measure.isMeasuring) {
            TapLevelMeter(measure.amplitude, modifier = Modifier.padding(horizontal = Dimens.SpaceLg))
            Spacer(Modifier.height(Dimens.SpaceSm))
        }
        TapFeedbackCard(
            quality = continuous.lastTapQuality,
            message = continuous.lastTapMessage,
            repeatedRejection = continuous.dominantRejection,
            onRaiseSensitivity = { vm.raiseSensitivityAndRestart() }
        )
        measure.noiseWarning?.let {
            Text(
                it,
                color = statusColors.warnText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Dimens.SpaceSm)
            )
        }
    }
}

@Composable
private fun SimpleResultStep(vm: AppViewModel, equipment: EquipmentEntity?) {
    val continuous by vm.continuous.collectAsState()
    val stats = continuous.stats ?: return
    val frequency = continuous.frequencies.average()
    val passed = equipment?.let { vm.repository.evaluate(it, frequency, stats.mean) } ?: false

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(equipment?.name ?: "", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.SpaceXl))
        Text(
            vm.formatPrimaryValue(equipment, frequency, stats.mean),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            vm.formatSecondaryValue(equipment, frequency, stats.mean),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.SpaceLg))
        PassFailBadge(passed)
        equipment?.let { eq ->
            Spacer(Modifier.height(Dimens.SpaceLg))
            SpecRangeBar(
                value = if (eq.useHzMode) frequency else stats.mean,
                lower = if (eq.useHzMode) eq.specHzLower else eq.specLower,
                upper = if (eq.useHzMode) eq.specHzUpper else eq.specUpper,
                lowerLabel = if (eq.useHzMode) "${eq.specHzLower} Hz" else vm.formatTension(eq.specLower),
                upperLabel = if (eq.useHzMode) "${eq.specHzUpper} Hz" else vm.formatTension(eq.specUpper)
            )
            Spacer(Modifier.height(Dimens.SpaceLg))
            AdjustmentCard(vm.adjustmentHint(eq, frequency, stats.mean))
            vm.lastRecordFor(eq.id)?.let { previous ->
                Spacer(Modifier.height(Dimens.SpaceMd))
                Text(
                    "前回 ${vm.formatTension(previous.tensionN)} → 今回 ${vm.formatTension(stats.mean)}" +
                        "（${vm.formatTensionDelta(stats.mean - previous.tensionN)}）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal const val EMPTY_EQUIPMENT_MESSAGE =
    "表示できる設備がありません。下の「設備管理」から追加するか、削除済み設備を復元してください。"
