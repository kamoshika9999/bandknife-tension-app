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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.bandknife.tension.data.EquipmentEntity
import com.bandknife.tension.ui.components.AdjustmentCard
import com.bandknife.tension.ui.components.HistogramChart
import com.bandknife.tension.ui.components.PassFailBadge
import com.bandknife.tension.ui.components.SpecRangeBar
import com.bandknife.tension.ui.components.SpectrumChart
import com.bandknife.tension.ui.components.TapFeedbackCard
import com.bandknife.tension.ui.components.TapLevelMeter
import com.bandknife.tension.ui.components.UnsyncedEquipmentBadge
import com.bandknife.tension.ui.theme.Dimens
import com.bandknife.tension.ui.theme.statusColors
import com.bandknife.tension.viewmodel.AppViewModel
import com.bandknife.tension.viewmodel.SimpleStep

@Composable
fun AdvanceFlowScreen(vm: AppViewModel) {
    MeasurementFlowScaffold(
        vm = vm,
        title = "アドバンスモード",
        selectContent = { equipment -> AdvanceSelectStep(vm, equipment) },
        measureContent = { selected -> AdvanceMeasureStep(vm, selected) },
        resultContent = { selected -> AdvanceResultStep(vm, selected) }
    )
}

@Composable
private fun AdvanceSelectStep(vm: AppViewModel, equipment: List<EquipmentEntity>) {
    val localMode by vm.localMode.collectAsState()
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
                val blockReason = vm.measureBlockReason(eq)
                val selectable = blockReason == null
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = selectable) {
                        vm.selectEquipment(eq)
                        vm.setSimpleStep(SimpleStep.MEASURE)
                        vm.startMeasuring()
                    },
                    colors = if (selectable) CardDefaults.cardColors()
                    else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(Dimens.SpaceLg)) {
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
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvanceMeasureStep(vm: AppViewModel, equipment: EquipmentEntity?) {
    val continuous by vm.continuous.collectAsState()
    val measure by vm.measureState.collectAsState()
    val busy = measure.isMeasuring || measure.isArming

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(equipment?.name ?: "", style = MaterialTheme.typography.titleMedium)
        if (measure.isArming) {
            Text(
                "まだ叩かないでください（あと ${measure.armingSecondsLeft} 秒）",
                style = MaterialTheme.typography.headlineSmall,
                color = statusColors.warnText,
                modifier = Modifier
                    .padding(vertical = Dimens.SpaceLg)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Text("測定 ${continuous.targetCount} 回", modifier = Modifier.padding(Dimens.SpaceSm))
        // 測定中に目標回数を現在値より下げると完了条件を満たせなくなるため操作を止める
        Slider(
            value = continuous.targetCount.toFloat(),
            onValueChange = { vm.setContinuousTarget(it.toInt().coerceIn(3, 15)) },
            valueRange = 3f..15f,
            steps = 11,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "${continuous.currentCount} / ${continuous.targetCount}",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "${continuous.targetCount}回中${continuous.currentCount}回"
            }
        )
        if (measure.isMeasuring) {
            TapLevelMeter(measure.amplitude)
            SpectrumChart(measure.spectrum)
        }
        TapFeedbackCard(
            quality = continuous.lastTapQuality,
            message = continuous.lastTapMessage,
            repeatedRejection = continuous.dominantRejection,
            onRaiseSensitivity = { vm.raiseSensitivityAndRestart() },
            modifier = Modifier.padding(top = Dimens.SpaceSm)
        )
        continuous.values.forEachIndexed { i, v ->
            Text("${i + 1}回目 ${vm.formatTension(v)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AdvanceResultStep(vm: AppViewModel, equipment: EquipmentEntity?) {
    val continuous by vm.continuous.collectAsState()
    val stats = continuous.stats ?: return
    val frequency = continuous.frequencies.average()
    val passed = equipment?.let { vm.repository.evaluate(it, frequency, stats.mean) } ?: false
    val unit = vm.tensionUnit()

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            vm.formatPrimaryValue(equipment, frequency, stats.mean),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            vm.formatSecondaryValue(equipment, frequency, stats.mean),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PassFailBadge(passed)
        equipment?.let { eq ->
            SpecRangeBar(
                value = if (eq.useHzMode) frequency else stats.mean,
                lower = if (eq.useHzMode) eq.specHzLower else eq.specLower,
                upper = if (eq.useHzMode) eq.specHzUpper else eq.specUpper,
                lowerLabel = if (eq.useHzMode) "${eq.specHzLower} Hz" else vm.formatTension(eq.specLower),
                upperLabel = if (eq.useHzMode) "${eq.specHzUpper} Hz" else vm.formatTension(eq.specUpper)
            )
            AdjustmentCard(vm.adjustmentHint(eq, frequency, stats.mean))
        }
        HistogramChart(stats.values.map { vm.toDisplayTension(it) }, unitLabel = unit)
        Column(Modifier.fillMaxWidth()) {
            Text(
                "平均 ${vm.formatTension(stats.mean)}" +
                    "（ばらつき ±${"%.1f".format(vm.toDisplayTension(stats.stdDev))} $unit" +
                    "・変動係数 ${"%.1f".format(stats.cvPercent)}%）",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "測定値の 95% が入る範囲 ${vm.formatTensionRange(stats.ci95Lower, stats.ci95Upper)}",
                style = MaterialTheme.typography.bodyMedium
            )
            equipment?.let {
                val margin = minOf(stats.ci95Lower - it.specLower, it.specUpper - stats.ci95Upper)
                Text(
                    if (margin > 0) "規格までの余裕 ${vm.formatTension(margin)}"
                    else "規格を ${vm.formatTension(-margin)} 外れています",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (margin > 0) statusColors.okText else statusColors.ngText
                )
            }
            Text(
                "測定 ${stats.values.size} 回: ${vm.formatTensionValues(stats.values)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (stats.outliers.isNotEmpty()) {
                Text(
                    "外れ値として除外: ${vm.formatTensionValues(stats.outliers)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColors.warnText
                )
            }
        }
    }
}
