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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandknife.tension.data.EquipmentEntity
import com.bandknife.tension.viewmodel.AppViewModel
import com.bandknife.tension.viewmodel.SimpleStep

@Composable
fun SimpleFlowScreen(vm: AppViewModel, onModeMenu: () -> Unit) {
    val step by vm.simpleStep.collectAsState()
    val equipment by vm.equipment.collectAsState()
    val selected by vm.selectedEquipment.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("バンドナイフ張力計", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        when (step) {
            SimpleStep.SELECT -> {
                Text("設備を選んでください", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(equipment) { eq ->
                        EquipmentSelectCard(eq) {
                            vm.selectEquipment(eq)
                            vm.setSimpleStep(SimpleStep.MEASURE)
                            vm.startMeasuring()
                        }
                    }
                }
            }
            SimpleStep.MEASURE -> SimpleMeasureStep(vm, selected)
            SimpleStep.RESULT -> SimpleResultStep(vm, selected)
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onModeMenu, modifier = Modifier.fillMaxWidth()) { Text("モード切替") }
    }
}

@Composable
private fun EquipmentSelectCard(eq: EquipmentEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(eq.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("標準: ${eq.standardTension.toInt()} N  規格: ${eq.specLower.toInt()}〜${eq.specUpper.toInt()} N")
        }
    }
}

@Composable
private fun SimpleMeasureStep(vm: AppViewModel, equipment: EquipmentEntity?) {
    val continuous by vm.continuous.collectAsState()
    val measure by vm.measureState.collectAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(equipment?.name ?: "", fontSize = 20.sp)
        Spacer(Modifier.height(24.dp))
        Text("刃の中央を${continuous.targetCount}回軽く叩いてください", fontSize = 18.sp)
        Spacer(Modifier.height(32.dp))
        Text("${continuous.currentCount} / ${continuous.targetCount}", fontSize = 64.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(measure.tapMessage, color = MaterialTheme.colorScheme.secondary)
        measure.noiseWarning?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        if (continuous.stats != null) {
            vm.setSimpleStep(SimpleStep.RESULT)
            vm.stopMeasuring()
        }
    }
}

@Composable
private fun SimpleResultStep(vm: AppViewModel, equipment: EquipmentEntity?) {
    val continuous by vm.continuous.collectAsState()
    val stats = continuous.stats
    val passed = equipment?.let { eq ->
        stats?.let { vm.repository.evaluate(eq, continuous.frequencies.average(), it.mean) }
    } ?: false

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(equipment?.name ?: "", fontSize = 20.sp)
        Spacer(Modifier.height(24.dp))
        stats?.let {
            Text(vm.formatTension(it.mean), fontSize = 72.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            com.bandknife.tension.ui.components.PassFailBadge(passed)
            equipment?.let { eq ->
                val dev = it.mean - eq.standardTension
                Text("標準 ${eq.standardTension.toInt()} N に対して ${if (dev >= 0) "+" else ""}${dev.toInt()} N", modifier = Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = { vm.saveAndResetToSelect() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("記録して終了", fontSize = 20.sp)
        }
    }
}
