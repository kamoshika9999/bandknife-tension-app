package com.bandknife.tension.ui.manual

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bandknife.tension.data.AppMode
import com.bandknife.tension.data.EquipmentEntity
import com.bandknife.tension.domain.TensionCalculator
import com.bandknife.tension.ui.components.AdjustmentCard
import com.bandknife.tension.ui.components.AppModeSelector
import com.bandknife.tension.ui.components.FlowBottomActionBar
import com.bandknife.tension.ui.components.HistogramChart
import com.bandknife.tension.ui.components.MeasureModeSelector
import com.bandknife.tension.ui.components.MeasureStartStopButtons
import com.bandknife.tension.ui.components.MeasureStatusBar
import com.bandknife.tension.ui.components.PassFailBadge
import com.bandknife.tension.ui.components.SpecRangeBar
import com.bandknife.tension.ui.components.SpectrumChart
import com.bandknife.tension.ui.components.TapLevelMeter
import com.bandknife.tension.ui.theme.Dimens
import com.bandknife.tension.ui.theme.statusColors
import com.bandknife.tension.viewmodel.AdjustDirection
import com.bandknife.tension.viewmodel.AdjustmentHint
import com.bandknife.tension.viewmodel.MeasureMode

private val sampleEquipment = EquipmentEntity(
    id = 1,
    name = "ペフ用スライサー1号",
    massPerMeter = 0.844,
    spanMeters = TensionCalculator.DEFAULT_SPAN_METERS,
    standardTension = 160.0,
    specLower = 150.0,
    specUpper = 180.0,
    widthMm = 86.0,
    thicknessMm = 1.25
)

@Composable
fun ManualSimpleEquipmentSelectPreview() {
    FlowScaffold(mode = AppMode.SIMPLE) {
        Column(Modifier.fillMaxSize().padding(horizontal = Dimens.SpaceLg)) {
            Text("設備を選んでください", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Dimens.SpaceLg))
            EquipmentCard(sampleEquipment)
        }
    }
}

@Composable
fun ManualSimpleMeasuringPreview() {
    FlowScaffold(mode = AppMode.SIMPLE) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(sampleEquipment.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimens.SpaceXl))
            Text("刃の中央を5回軽く叩いてください", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(Dimens.SpaceXl))
            Text("3 / 5", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(Dimens.SpaceLg))
            TapLevelMeter(0.42, modifier = Modifier.padding(horizontal = Dimens.SpaceLg))
            Spacer(Modifier.height(Dimens.SpaceSm))
            Card(
                colors = CardDefaults.cardColors(containerColor = statusColors.okContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(Dimens.SpaceMd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = statusColors.okText)
                    Text("適正な打撃 (3/5)", color = statusColors.okText)
                }
            }
            Spacer(Modifier.height(Dimens.SpaceXl))
            OutlinedButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(Dimens.ButtonHeight)
            ) { Text("測定を中止") }
        }
    }
}

@Composable
fun ManualSimpleResultPreview() {
    FlowScaffold(mode = AppMode.SIMPLE) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(sampleEquipment.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimens.SpaceLg))
            Text("162 N", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
            Text("周波数 85.4 Hz", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Dimens.SpaceLg))
            PassFailBadge(passed = true)
            Spacer(Modifier.height(Dimens.SpaceLg))
            SpecRangeBar(
                value = 162.0,
                lower = 150.0,
                upper = 180.0,
                lowerLabel = "150 N",
                upperLabel = "180 N"
            )
            Spacer(Modifier.height(Dimens.SpaceLg))
            AdjustmentCard(
                AdjustmentHint(
                    direction = AdjustDirection.NONE,
                    headline = "規格 150〜180 N の範囲内です",
                    action = "このまま使用できます"
                )
            )
            Spacer(Modifier.height(Dimens.SpaceXl))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(Dimens.ButtonHeightLarge)) {
                Text("記録して終了", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(Dimens.SpaceMd))
            OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().height(Dimens.ButtonHeight)) {
                Text("破棄してもう一度測る", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun ManualAdvanceResultPreview() {
    FlowScaffold(mode = AppMode.ADVANCE) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("162 N", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            Text("周波数 85.4 Hz", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PassFailBadge(passed = true)
            Spacer(Modifier.height(Dimens.SpaceMd))
            SpecRangeBar(
                value = 162.0,
                lower = 150.0,
                upper = 180.0,
                lowerLabel = "150 N",
                upperLabel = "180 N"
            )
            Spacer(Modifier.height(Dimens.SpaceMd))
            HistogramChart(listOf(158.0, 161.0, 162.0, 163.0, 164.0))
            Spacer(Modifier.height(Dimens.SpaceSm))
            Column(Modifier.fillMaxWidth()) {
                Text("平均 162 N（ばらつき ±2.1 N・変動係数 1.3%）")
                Text("測定値の 95% が入る範囲 159〜165 N")
                Text("規格までの余裕 9 N", color = statusColors.okText)
            }
            SpectrumChart(FloatArray(64) { i -> if (i == 18) 0.9f else 0.05f })
        }
    }
}

@Composable
fun ManualDetailMeasurePreview() {
    DetailScaffold(selectedTab = 0) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = sampleEquipment.name,
                onValueChange = {},
                label = { Text("設備") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true
            )
            TextButton(onClick = {}) { Text("刃のスペック設定") }
            Text("使用中のマイク: 内蔵マイク", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Dimens.SpaceMd))
            MeasureStatusBar(
                isMeasuring = false,
                measureMode = MeasureMode.CONTINUOUS,
                currentCount = 0,
                targetCount = 5,
                tapMessage = ""
            )
            Spacer(Modifier.height(Dimens.SpaceMd))
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
                modifier = Modifier.padding(vertical = Dimens.SpaceSm)
            )
            MeasureModeSelector(MeasureMode.CONTINUOUS, {}, enabled = true)
            MeasureStartStopButtons(isMeasuring = false, isArming = false, onStart = {}, onStop = {})
        }
    }
}

@Composable
fun ManualDetailEquipmentPreview() {
    DetailScaffold(selectedTab = 1) {
        Column {
            Text(
                "設備管理",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = Dimens.SpaceMd)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                Button(onClick = {}, modifier = Modifier.weight(1f).heightIn(min = Dimens.MinTouch)) {
                    Text("設備を追加")
                }
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).heightIn(min = Dimens.MinTouch)) {
                    Text("削除済み設備")
                }
            }
            Spacer(Modifier.height(Dimens.SpaceSm))
            EquipmentCard(sampleEquipment)
        }
    }
}

@Composable
fun ManualDetailHistoryPreview() {
    DetailScaffold(selectedTab = 2) {
        Card(Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceXs)) {
            Column(Modifier.padding(Dimens.SpaceMd)) {
                Text(
                    "ペフ用スライサー1号 / 2026/07/23 14:30",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
                ) {
                    Text("162 N", style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = statusColors.okText)
                    Text("OK", color = statusColors.okText)
                    Text("測定 5 回", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ManualDetailSettingsPreview() {
    DetailScaffold(selectedTab = 3) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            AppModeSelector(current = AppMode.DETAIL, onSelect = {})
            Spacer(Modifier.height(Dimens.SpaceSm))
            Text("設定", style = MaterialTheme.typography.headlineMedium)
            SettingRowPreview("張力を kgf で表示する", "オフのときは N（ニュートン）で表示します", false)
            SettingRowPreview("打撃を振動で知らせる", "測定中は画面を見られないため、成功と失敗を振動で区別します", true)
            Spacer(Modifier.height(Dimens.SpaceSm))
            Text("打音の検出感度", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                FilterChip(selected = false, onClick = {}, label = { Text("高") }, modifier = Modifier.heightIn(min = Dimens.MinTouch))
                FilterChip(selected = true, onClick = {}, label = { Text("標準") }, modifier = Modifier.heightIn(min = Dimens.MinTouch))
                FilterChip(selected = false, onClick = {}, label = { Text("低") }, modifier = Modifier.heightIn(min = Dimens.MinTouch))
            }
            OutlinedTextField(
                "",
                {},
                label = { Text("アップロード先 URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            SettingRowPreview("測定後に自動アップロード", "圏外のときは端末に貯めておき、つながったときに送信します", true)
            Spacer(Modifier.height(Dimens.SpaceLg))
            Text("ヘルプ", style = MaterialTheme.typography.headlineMedium)
            Text("原理: T = 4 × m × S² × f²")
            Text("• 樹脂の柄や指の腹でスパン中央を軽く叩く")
            Text("• マイクは刃から1〜2cm離して保持")
        }
    }
}

@Composable
private fun SettingRowPreview(label: String, description: String, checked: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
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
        Switch(checked = checked, onCheckedChange = {})
    }
}

@Composable
fun ManualSpecSettingsPreview() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.SpaceLg)) {
        TextButton(onClick = {}) { Text("← 戻る") }
        Text("刃のスペック設定", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Dimens.SpaceMd))
        OutlinedTextField(
            sampleEquipment.name,
            {},
            label = { Text("設備") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true
        )
        Spacer(Modifier.height(Dimens.SpaceMd))
        OutlinedTextField("86", {}, label = { Text("幅 (mm)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField("1.25", {}, label = { Text("板厚 (mm)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField("1.0", {}, label = { Text("スパン長 (m)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField("160", {}, label = { Text("標準張力 (N)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField("150", {}, label = { Text("規格下限 (N)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField("180", {}, label = { Text("規格上限 (N)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Dimens.SpaceMd))
        Button(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouch)) { Text("保存") }
    }
}

@Composable
private fun FlowScaffold(mode: AppMode, content: @Composable () -> Unit) {
    Scaffold(
        bottomBar = {
            FlowBottomActionBar(onEquipment = {}, onSpec = {})
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(top = Dimens.SpaceMd),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "バンドナイフ張力計",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = Dimens.SpaceSm)
            )
            AppModeSelector(current = mode, onSelect = {}, modifier = Modifier.padding(horizontal = Dimens.SpaceLg))
            Spacer(Modifier.height(Dimens.SpaceMd))
            BoxContent(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dimens.SpaceLg), content)
        }
    }
}

@Composable
private fun DetailScaffold(selectedTab: Int, content: @Composable () -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("測定", "設備", "履歴", "その他").forEachIndexed { index, label ->
                    val icon = when (index) {
                        0 -> Icons.Default.Home
                        1 -> Icons.Default.Settings
                        2 -> Icons.Default.History
                        else -> Icons.Default.Info
                    }
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {},
                        icon = { Icon(icon, null) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Dimens.SpaceLg)) {
            content()
        }
    }
}

@Composable
private fun BoxContent(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(modifier) { content() }
}

@Composable
private fun EquipmentCard(eq: EquipmentEntity) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = {}),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(Dimens.SpaceXl)) {
            Text(eq.name, style = MaterialTheme.typography.headlineSmall)
            Text("標準張力 ${eq.standardTension.toInt()} N / 規格 ${eq.specLower.toInt()}〜${eq.specUpper.toInt()} N")
        }
    }
}
