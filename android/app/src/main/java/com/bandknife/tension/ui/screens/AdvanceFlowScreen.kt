package com.bandknife.tension.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandknife.tension.ui.components.HistogramChart
import com.bandknife.tension.ui.components.PassFailBadge
import com.bandknife.tension.ui.components.SpectrumChart
import com.bandknife.tension.ui.components.TapLevelMeter
import com.bandknife.tension.viewmodel.AppViewModel
import com.bandknife.tension.viewmodel.SimpleStep

@Composable
fun AdvanceFlowScreen(vm: AppViewModel, onModeMenu: () -> Unit) {
    val step by vm.simpleStep.collectAsState()
    val equipment by vm.equipment.collectAsState()
    val selected by vm.selectedEquipment.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("アドバンスモード", style = MaterialTheme.typography.titleLarge)
        when (step) {
            SimpleStep.SELECT -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(equipment) { eq ->
                        Card(Modifier.fillMaxWidth().clickable {
                            vm.selectEquipment(eq)
                            vm.setSimpleStep(SimpleStep.MEASURE)
                            vm.startMeasuring()
                        }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(eq.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("標準 ${eq.standardTension.toInt()} N")
                            }
                        }
                    }
                }
            }
            SimpleStep.MEASURE -> AdvanceMeasureStep(vm, selected)
            SimpleStep.RESULT -> AdvanceResultStep(vm, selected)
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onModeMenu, Modifier.fillMaxWidth()) { Text("モード切替") }
    }
}

@Composable
private fun AdvanceMeasureStep(vm: AppViewModel, equipment: com.bandknife.tension.data.EquipmentEntity?) {
    val continuous by vm.continuous.collectAsState()
    val measure by vm.measureState.collectAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(equipment?.name ?: "")
        Text("測定回数: ${continuous.targetCount}", modifier = Modifier.padding(8.dp))
        Slider(
            value = continuous.targetCount.toFloat(),
            onValueChange = { vm.setContinuousTarget(it.toInt().coerceIn(3, 15)) },
            valueRange = 3f..15f,
            steps = 11,
            modifier = Modifier.fillMaxWidth()
        )
        Text("${continuous.currentCount} / ${continuous.targetCount}", fontSize = 48.sp, fontWeight = FontWeight.Bold)
        TapLevelMeter(measure.amplitude)
        SpectrumChart(measure.spectrum)
        continuous.values.forEachIndexed { i, v ->
            Text("${i + 1}回目: ${vm.formatTension(v)}")
        }
        if (continuous.stats != null) {
            vm.stopMeasuring()
            vm.setSimpleStep(SimpleStep.RESULT)
        }
    }
}

@Composable
private fun AdvanceResultStep(vm: AppViewModel, equipment: com.bandknife.tension.data.EquipmentEntity?) {
    val continuous by vm.continuous.collectAsState()
    val stats = continuous.stats ?: return
    val passed = equipment?.let { vm.repository.evaluate(it, continuous.frequencies.average(), stats.mean) } ?: false

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
        item {
            Text(vm.formatTension(stats.mean), fontSize = 56.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            PassFailBadge(passed)
        }
        item {
            HistogramChart(stats.values)
        }
        item {
            Text("平均: ${stats.mean.toInt()} N  SD: ${"%.1f".format(stats.stdDev)}  CV: ${"%.1f".format(stats.cvPercent)}%")
            Text("95%CI: ${stats.ci95Lower.toInt()} 〜 ${stats.ci95Upper.toInt()} N")
            equipment?.let {
                val margin = minOf(stats.ci95Lower - it.specLower, it.specUpper - stats.ci95Upper)
                Text("余裕度: ${margin.toInt()} N", color = if (margin > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                continuous.values.forEach { v ->
                    Text("•", fontSize = 24.sp)
                }
            }
            Text("個別値: ${stats.values.joinToString { it.toInt().toString() }} N")
            if (stats.outliers.isNotEmpty()) Text("除外: ${stats.outliers.joinToString { it.toInt().toString() }} N", color = MaterialTheme.colorScheme.error)
        }
        item {
            Button(onClick = { vm.saveAndResetToSelect() }, Modifier.fillMaxWidth().height(56.dp)) {
                Text("記録して終了", fontSize = 18.sp)
            }
        }
    }
}
