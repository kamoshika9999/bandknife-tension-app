package com.bandknife.tension.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bandknife.tension.data.EquipmentEntity
import com.bandknife.tension.domain.BladeMaterialPreset
import com.bandknife.tension.domain.TensionCalculator
import com.bandknife.tension.ui.components.EquipmentComboBox
import com.bandknife.tension.ui.theme.Dimens
import com.bandknife.tension.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun BandKnifeSpecScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    initialEquipment: EquipmentEntity? = null
) {
    val equipment by vm.equipment.collectAsState()
    val selected by vm.selectedEquipment.collectAsState()
    val currentUser by vm.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    var requestSignIn by remember { mutableStateOf(false) }

    var target by remember(initialEquipment, selected, equipment) {
        mutableStateOf(initialEquipment ?: selected ?: equipment.firstOrNull())
    }

    // 測定画面からも開ける画面なので、ここでも使用者を確かめる。
    // 未設定のまま保存されると、その設備は未登録扱いになって測定できなくなる。
    if (requestSignIn) {
        SignInDialog(
            vm = vm,
            onDismiss = { requestSignIn = false },
            onSuccess = { requestSignIn = false }
        )
    }

    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.SpaceLg)
    ) {
        TextButton(onClick = onBack) { Text("← 戻る") }
        Text(
            "刃のスペック設定",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = Dimens.SpaceMd)
        )
        Text(
            "ここで設定した規格が、以後すべての測定の合否判定に使われます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.SpaceMd)
        )

        if (equipment.isEmpty()) {
            Text(
                "設備が登録されていません。設備管理から先に設備を追加してください。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        if (currentUser == null) {
            SpecEditLockedCard(onSetUp = { requestSignIn = true })
            Spacer(Modifier.height(Dimens.SpaceMd))
        }

        EquipmentComboBox(
            equipment = equipment,
            selected = target,
            onSelect = { target = it },
            label = "設備"
        )
        Spacer(Modifier.height(Dimens.SpaceLg))

        target?.let { eq ->
            BandKnifeSpecForm(
                equipment = eq,
                canSave = currentUser != null,
                onCancel = onBack,
                onSave = { updated ->
                    scope.launch {
                        vm.repository.updateEquipment(updated)
                        if (selected?.id == updated.id) {
                            vm.selectEquipment(updated)
                        }
                        onBack()
                    }
                }
            )
        }
    }
}

@Composable
private fun SpecEditLockedCard(onSetUp: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Column(Modifier.padding(Dimens.CardPadding)) {
            Text("規格の変更には使用者の設定が必要です", style = MaterialTheme.typography.titleSmall)
            Text(
                "誰が規格値を変更したかを記録するため、名前とパスワードを設定してください。",
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
fun BandKnifeSpecForm(
    equipment: EquipmentEntity,
    onCancel: () -> Unit,
    onSave: (EquipmentEntity) -> Unit,
    modifier: Modifier = Modifier,
    /** 使用者が未設定のときは保存させない。誰の変更か残せないため。 */
    canSave: Boolean = true
) {
    var width by remember(equipment.id) { mutableStateOf(equipment.widthMm.toString()) }
    var thickness by remember(equipment.id) { mutableStateOf(equipment.thicknessMm.toString()) }
    var mass by remember(equipment.id) { mutableStateOf(equipment.massPerMeter.toString()) }
    var span by remember(equipment.id) { mutableStateOf(equipment.spanMeters.toString()) }
    var standard by remember(equipment.id) { mutableStateOf(equipment.standardTension.toString()) }
    var lower by remember(equipment.id) { mutableStateOf(equipment.specLower.toString()) }
    var upper by remember(equipment.id) { mutableStateOf(equipment.specUpper.toString()) }
    var useHz by remember(equipment.id) { mutableStateOf(equipment.useHzMode) }
    var standardHz by remember(equipment.id) { mutableStateOf(equipment.standardHz.toString()) }
    var hzLower by remember(equipment.id) { mutableStateOf(equipment.specHzLower.toString()) }
    var hzUpper by remember(equipment.id) { mutableStateOf(equipment.specHzUpper.toString()) }
    var materialId by remember(equipment.id) { mutableStateOf(equipment.materialId) }
    var density by remember(equipment.id) { mutableStateOf(equipment.density) }
    var youngGpa by remember(equipment.id) { mutableStateOf(equipment.youngModulusGpa) }
    var vibrationMode by remember(equipment.id) { mutableStateOf(equipment.vibrationMode.toString()) }
    var edgewiseBending by remember(equipment.id) { mutableStateOf(equipment.edgewiseBending) }

    LaunchedEffect(equipment) {
        width = equipment.widthMm.toString()
        thickness = equipment.thicknessMm.toString()
        mass = equipment.massPerMeter.toString()
        span = equipment.spanMeters.toString()
        standard = equipment.standardTension.toString()
        lower = equipment.specLower.toString()
        upper = equipment.specUpper.toString()
        useHz = equipment.useHzMode
        standardHz = equipment.standardHz.toString()
        hzLower = equipment.specHzLower.toString()
        hzUpper = equipment.specHzUpper.toString()
        materialId = equipment.materialId
        density = equipment.density
        youngGpa = equipment.youngModulusGpa
        vibrationMode = equipment.vibrationMode.toString()
        edgewiseBending = equipment.edgewiseBending
    }

    fun recalcMass() {
        val w = width.toDoubleOrNull()?.div(1000) ?: 0.0
        val t = thickness.toDoubleOrNull()?.div(1000) ?: 0.0
        mass = TensionCalculator.massFromDimensions(w, t, density).toString()
    }

    fun applyMaterial(preset: BladeMaterialPreset) {
        materialId = preset.id
        density = preset.densityKgM3
        youngGpa = preset.youngModulusGpa
        recalcMass()
    }

    fun draftEquipment(): EquipmentEntity {
        val mode = vibrationMode.toIntOrNull()?.coerceAtLeast(1) ?: 1
        return equipment.copy(
            widthMm = width.toDoubleOrNull() ?: equipment.widthMm,
            thicknessMm = thickness.toDoubleOrNull() ?: equipment.thicknessMm,
            massPerMeter = mass.toDoubleOrNull() ?: equipment.massPerMeter,
            spanMeters = span.toDoubleOrNull() ?: equipment.spanMeters,
            standardTension = standard.toDoubleOrNull() ?: equipment.standardTension,
            specLower = lower.toDoubleOrNull() ?: equipment.specLower,
            specUpper = upper.toDoubleOrNull() ?: equipment.specUpper,
            density = density,
            youngModulusGpa = youngGpa,
            materialId = materialId,
            vibrationMode = mode,
            edgewiseBending = edgewiseBending
        )
    }

    val predictedHz = TensionCalculator.frequencyFromEquipment(
        draftEquipment(),
        standard.toDoubleOrNull() ?: equipment.standardTension
    )

    val tensionRangeValid = specRangeValid(lower, upper)
    val hzRangeValid = !useHz || specRangeValid(hzLower, hzUpper)
    val inputValid = positiveNumbers(width, thickness, mass, span, standard) &&
        tensionRangeValid && hzRangeValid &&
        (vibrationMode.toIntOrNull() ?: 0) >= 1 &&
        (!useHz || positiveNumbers(standardHz))
    val saveEnabled = inputValid && canSave

    Column(modifier) {
        Text(
            equipment.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Dimens.SpaceSm)
        )
        Text(
            "刃の寸法・張力規格を設定します。各項目の初期値は現在の登録値です。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.SpaceMd)
        )

        NumberField(width, "幅 (mm)") { width = it; recalcMass() }
        NumberField(thickness, "板厚 (mm)") { thickness = it; recalcMass() }
        Text("材種", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Dimens.SpaceSm))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            BladeMaterialPreset.ALL.forEach { preset ->
                OutlinedButton(
                    onClick = { applyMaterial(preset) },
                    enabled = materialId != preset.id,
                    modifier = Modifier.heightIn(min = Dimens.MinTouch)
                ) { Text(preset.label) }
            }
        }
        Text(
            "密度 ${density.toInt()} kg/m³ / ヤング率 ${youngGpa.toInt()} GPa",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        NumberField(mass, "単位質量 (kg/m)") { mass = it }
        NumberField(span, "スパン長 (m)") { span = it }
        NumberField(vibrationMode, "振動モード n") { vibrationMode = it }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = edgewiseBending, role = Role.Switch) { edgewiseBending = it },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("面内曲げ（幅方向）", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "ON: 面内曲げ（高周波・張力感度低） / OFF: 面外曲げ（推奨・張力測定向け）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = edgewiseBending, onCheckedChange = null)
        }
        Text(
            "標準張力での予測周波数: ${"%.1f".format(predictedHz)} Hz",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = Dimens.SpaceSm)
        )
        NumberField(standard, "標準張力 (N)") { standard = it }
        NumberField(
            lower,
            "規格下限 (N)",
            errorText = if (!tensionRangeValid) "規格下限は上限より小さい値にしてください" else null
        ) { lower = it }
        NumberField(
            upper,
            "規格上限 (N)",
            errorText = if (!tensionRangeValid) "規格上限は下限より大きい値にしてください" else null,
            imeAction = if (useHz) ImeAction.Next else ImeAction.Done
        ) { upper = it }

        Spacer(Modifier.height(Dimens.SpaceSm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = useHz, role = Role.Switch, onValueChange = { useHz = it }),
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
        if (useHz) {
            NumberField(standardHz, "標準周波数 (Hz)") { standardHz = it }
            NumberField(
                hzLower,
                "規格下限 (Hz)",
                errorText = if (!hzRangeValid) "規格下限は上限より小さい値にしてください" else null
            ) { hzLower = it }
            NumberField(
                hzUpper,
                "規格上限 (Hz)",
                errorText = if (!hzRangeValid) "規格上限は下限より大きい値にしてください" else null,
                imeAction = ImeAction.Done
            ) { hzUpper = it }
        }

        Spacer(Modifier.height(Dimens.SpaceLg))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = Dimens.MinTouch)
            ) { Text("キャンセル") }
            Button(enabled = saveEnabled, modifier = Modifier.heightIn(min = Dimens.MinTouch), onClick = {
                onSave(
                    draftEquipment().copy(
                        useHzMode = useHz,
                        standardHz = standardHz.toDoubleOrNull() ?: equipment.standardHz,
                        specHzLower = hzLower.toDoubleOrNull() ?: equipment.specHzLower,
                        specHzUpper = hzUpper.toDoubleOrNull() ?: equipment.specHzUpper
                    )
                )
            }) { Text("保存") }
        }
    }
}
