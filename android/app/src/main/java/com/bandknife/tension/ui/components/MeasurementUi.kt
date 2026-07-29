package com.bandknife.tension.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bandknife.tension.data.AppMode
import com.bandknife.tension.data.EquipmentEntity
import com.bandknife.tension.domain.GateStatus
import com.bandknife.tension.domain.TapQuality
import com.bandknife.tension.domain.TapQualityChecker
import com.bandknife.tension.ui.theme.Dimens
import com.bandknife.tension.ui.theme.statusColors
import com.bandknife.tension.viewmodel.AdjustDirection
import com.bandknife.tension.viewmodel.AdjustmentHint
import com.bandknife.tension.viewmodel.MeasureMode
import com.bandknife.tension.viewmodel.SyncUiState

@Composable
fun MeasureStatusBar(
    isMeasuring: Boolean,
    measureMode: MeasureMode,
    currentCount: Int,
    targetCount: Int,
    tapMessage: String,
    amplitude: Double = 0.0,
    signalQuality: Double = 0.0,
    stackedTapCount: Int = 0,
    hasCompletedStats: Boolean = false,
    savedToHistory: Boolean = false,
    isArming: Boolean = false,
    armingSecondsLeft: Int = 0,
    modifier: Modifier = Modifier
) {
    val status = statusColors
    // 状態は色だけでなくアイコンの形でも区別できるようにする
    val textColor: Color
    val containerColor: Color
    val icon: ImageVector
    when {
        isArming -> {
            textColor = status.warnText
            containerColor = status.warnContainer
            icon = Icons.Default.HourglassTop
        }
        isMeasuring -> {
            textColor = status.okText
            containerColor = status.okContainer
            icon = Icons.Default.Mic
        }
        else -> {
            textColor = status.idleText
            containerColor = status.idleContainer
            icon = Icons.Default.PauseCircle
        }
    }

    val statusLabel = when {
        isArming -> "準備中"
        isMeasuring -> "測定中"
        else -> "停止中"
    }
    val guideText = when {
        savedToHistory -> "履歴に記録しました"
        isArming -> "まだ叩かないでください（あと ${armingSecondsLeft} 秒）"
        hasCompletedStats -> "測定完了 — 下の「記録する」で保存してください"
        !isMeasuring && (currentCount > 0) -> "測定停止 — 結果を確認して「記録する」か、再度「開始」してください"
        !isMeasuring -> "「開始」を押して測定してください"
        measureMode == MeasureMode.CONTINUOUS && currentCount < targetCount ->
            "刃を叩いてください（${currentCount} / ${targetCount} 回）"
        measureMode == MeasureMode.CONTINUOUS && currentCount >= targetCount ->
            "測定完了 — 「記録する」で保存してください"
        else -> "刃を叩いてください"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "$statusLabel。$guideText"
            },
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceMd)
                .clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
        ) {
            Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(statusLabel, style = MaterialTheme.typography.titleMedium, color = textColor)
                Text(guideText, style = MaterialTheme.typography.bodyMedium)
                if (isMeasuring) {
                    val pct = TapQualityChecker.formatPercent(amplitude)
                    val minPct = TapQualityChecker.formatPercent(TapQualityChecker.WEAK_RATIO)
                    val maxPct = TapQualityChecker.formatPercent(TapQualityChecker.STRONG_RATIO)
                    Text(
                        "打音の強さ $pct%（適正 $minPct〜$maxPct%）",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (signalQuality > 0) {
                        val stackNote = if (stackedTapCount > 1) " / ${stackedTapCount}回積分" else ""
                        Text(
                            "信号品質 ${signalQuality.toInt()}%$stackNote",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (isMeasuring && tapMessage.isNotEmpty()) {
                    Text(tapMessage, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * 直前の打撃が棄却された理由を、成功時と明確に区別できる見た目で示す。
 * 同じ失敗が続いたときは、その場で打てる対処ボタンも出す。
 */
@Composable
fun TapFeedbackCard(
    quality: TapQuality?,
    message: String,
    repeatedRejection: Pair<TapQuality, Int>?,
    onRaiseSensitivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (quality == null || message.isEmpty()) return
    val status = statusColors
    val rejected = quality != TapQuality.GOOD

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(
            containerColor = if (rejected) status.ngContainer else status.okContainer
        )
    ) {
        Column(Modifier.padding(Dimens.SpaceMd)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
            ) {
                Icon(
                    if (rejected) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = if (rejected) "打撃が無効" else "打撃が有効",
                    tint = if (rejected) status.ngText else status.okText
                )
                Text(
                    tapQualityHeadline(quality),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (rejected) status.ngText else status.okText
                )
            }
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (rejected) status.onNgContainer else status.onOkContainer,
                modifier = Modifier.padding(top = Dimens.SpaceXs)
            )
            repeatedRejection?.let { (repeatedQuality, count) ->
                Text(
                    "${tapQualityHeadline(repeatedQuality)} が ${count} 回続いています",
                    style = MaterialTheme.typography.bodySmall,
                    color = status.onNgContainer,
                    modifier = Modifier.padding(top = Dimens.SpaceSm)
                )
                if (repeatedQuality == TapQuality.TOO_WEAK) {
                    Button(
                        onClick = onRaiseSensitivity,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.MinTouch)
                            .padding(top = Dimens.SpaceSm)
                    ) { Text("感度を上げてやり直す") }
                }
            }
        }
    }
}

private fun tapQualityHeadline(quality: TapQuality): String = when (quality) {
    TapQuality.GOOD -> "適正な打撃"
    TapQuality.TOO_WEAK -> "打音が弱すぎます"
    TapQuality.TOO_STRONG -> "打音が強すぎます"
    TapQuality.DOUBLE_HIT -> "二度打ちしています"
    TapQuality.HARMONIC_DOMINANT -> "叩く位置・道具が合っていません"
}

/**
 * 規格帯のどこに測定値があるかを 1 本のバーで示す。
 * 「要調整」の文字だけでは、締めるのか緩めるのかが伝わらない。
 */
@Composable
fun SpecRangeBar(
    value: Double,
    lower: Double,
    upper: Double,
    lowerLabel: String,
    upperLabel: String,
    modifier: Modifier = Modifier
) {
    val status = statusColors
    val span = (upper - lower).takeIf { it > 0 } ?: return
    // 規格帯の前後に 1/3 ずつ余白を取り、範囲外がどれだけ外れているか見えるようにする
    val axisLower = lower - span / 3
    val axisSpan = span * 5 / 3
    val position = ((value - axisLower) / axisSpan).coerceIn(0.0, 1.0).toFloat()
    val inSpec = value in lower..upper
    val markerColor = if (inSpec) status.okText else status.ngText
    val trackColor = status.idleContainer
    val bandColor = status.okFill
    val outlineColor = MaterialTheme.colorScheme.outline

    Column(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "規格 $lowerLabel から $upperLabel。" +
                    if (inSpec) "測定値は範囲内です" else "測定値は範囲外です"
            }
    ) {
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val trackTop = size.height * 0.45f
            val trackHeight = size.height * 0.35f
            drawRect(
                color = trackColor,
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeight)
            )
            drawRect(
                color = bandColor,
                topLeft = Offset(size.width / 5f, trackTop),
                size = Size(size.width * 3f / 5f, trackHeight)
            )
            val x = size.width * position
            val markerHeight = size.height * 0.4f
            val markerHalfWidth = 8.dp.toPx()
            drawPath(
                Path().apply {
                    moveTo(x, trackTop)
                    lineTo(x - markerHalfWidth, trackTop - markerHeight)
                    lineTo(x + markerHalfWidth, trackTop - markerHeight)
                    close()
                },
                color = markerColor
            )
            drawLine(
                color = outlineColor,
                start = Offset(x, trackTop),
                end = Offset(x, trackTop + trackHeight),
                strokeWidth = 2.dp.toPx()
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Dimens.SpaceXs),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(lowerLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(upperLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 規格を外れたときに、どちら向きにどれだけ調整するかを示す。 */
@Composable
fun AdjustmentCard(hint: AdjustmentHint, modifier: Modifier = Modifier) {
    val status = statusColors
    val inSpec = hint.direction == AdjustDirection.NONE
    val arrow = when (hint.direction) {
        AdjustDirection.UP -> "↑"
        AdjustDirection.DOWN -> "↓"
        AdjustDirection.NONE -> "✓"
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (inSpec) status.okContainer else status.warnContainer
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Dimens.SpaceLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
        ) {
            Text(
                arrow,
                style = MaterialTheme.typography.headlineLarge,
                color = if (inSpec) status.okText else status.warnText
            )
            Column(Modifier.weight(1f)) {
                Text(
                    hint.headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (inSpec) status.onOkContainer else status.onWarnContainer
                )
                Text(
                    hint.action,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (inSpec) status.onOkContainer else status.onWarnContainer
                )
            }
        }
    }
}

/**
 * ドライブの設備マスターとの接続確認の状態。
 * 期限が切れると測定そのものを止めるため、期限内でも残りが少なければ先に知らせる。
 */
@Composable
fun SyncGateBanner(
    state: SyncUiState,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.status == GateStatus.VALID) return
    val status = statusColors
    val blocked = state.status == GateStatus.BLOCKED
    val headline = when {
        blocked && state.gate.neverSynced -> "測定できません — 設備マスターが未確認です"
        blocked -> "測定できません — 接続確認が ${state.gate.elapsedDays} 日途切れています"
        else -> "あと ${state.gate.remainingDays} 日で測定できなくなります"
    }
    val detail = if (blocked) {
        "電波の届く場所で「今すぐ確認」を押し、ドライブの設備マスターを取り込んでください。" +
            "圏外が続く場合は、詳細モードの設定でローカルモードに切り替えられます"
    } else {
        "最終確認 ${state.lastSyncText}。電波のあるうちに確認を済ませてください"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Assertive
                contentDescription = "$headline。$detail"
            },
        colors = CardDefaults.cardColors(
            containerColor = if (blocked) status.ngContainer else status.warnContainer
        )
    ) {
        Column(Modifier.padding(Dimens.SpaceLg)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
            ) {
                Icon(
                    if (blocked) Icons.Default.CloudOff else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (blocked) status.ngText else status.warnText
                )
                Text(
                    headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (blocked) status.onNgContainer else status.onWarnContainer
                )
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = if (blocked) status.onNgContainer else status.onWarnContainer,
                modifier = Modifier.padding(top = Dimens.SpaceXs)
            )
            Button(
                onClick = onSyncNow,
                enabled = !state.syncing,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.ButtonHeight)
                    .padding(top = Dimens.SpaceMd)
            ) {
                Text(
                    if (state.syncing) "確認中…" else "今すぐ確認",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/** ローカルモード中であることを示す。ドライブ連携は行わず記録は端末のみに残る。 */
@Composable
fun LocalModeBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "ローカルモード中。記録はこの端末のみに保存されます"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            Modifier.padding(Dimens.SpaceLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
        ) {
            Icon(
                Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Column {
                Text(
                    "ローカルモード — 記録はこの端末のみ",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "ドライブへの送信と設備マスターの確認は行いません",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

/** 履歴で、ローカルモード中に保存した記録を示す。 */
@Composable
fun LocalOnlyRecordBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            "端末のみ",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceXs)
        )
    }
}

/** 設備一覧で、ドライブ未登録のため測定に使えないことを示す。 */
@Composable
fun UnsyncedEquipmentBadge(modifier: Modifier = Modifier) {
    val status = statusColors
    Surface(
        modifier = modifier.semantics {
            contentDescription = "ドライブ未登録のため測定できません"
        },
        color = status.ngContainer,
        contentColor = status.onNgContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            Modifier.padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text("ドライブ未登録", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun AppModeSelector(
    current: AppMode,
    onSelect: (AppMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)
    ) {
        ModeButton(
            label = "シンプル",
            selected = current == AppMode.SIMPLE,
            onClick = { onSelect(AppMode.SIMPLE) },
            enabled = true,
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            label = "アドバンス",
            selected = current == AppMode.ADVANCE,
            onClick = { onSelect(AppMode.ADVANCE) },
            enabled = true,
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            label = "詳細",
            selected = current == AppMode.DETAIL,
            onClick = { onSelect(AppMode.DETAIL) },
            enabled = true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun FlowBottomActionBar(
    onEquipment: () -> Unit,
    onSpec: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceSm),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
        ) {
            OutlinedButton(
                onClick = onEquipment,
                modifier = Modifier.weight(1f).heightIn(min = Dimens.MinTouch)
            ) {
                Text("設備管理")
            }
            OutlinedButton(
                onClick = onSpec,
                modifier = Modifier.weight(1f).heightIn(min = Dimens.MinTouch)
            ) {
                Text("刃のスペック設定")
            }
        }
    }
}

@Composable
fun MeasureModeSelector(
    selected: MeasureMode,
    onSelect: (MeasureMode) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
    ) {
        ModeButton(
            label = "単発",
            selected = selected == MeasureMode.SINGLE,
            onClick = { onSelect(MeasureMode.SINGLE) },
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            label = "連続",
            selected = selected == MeasureMode.CONTINUOUS,
            onClick = { onSelect(MeasureMode.CONTINUOUS) },
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val sized = modifier.heightIn(min = Dimens.MinTouch)
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = sized) {
            Text(label, textAlign = TextAlign.Center)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = sized) {
            Text(label, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun MeasureStartStopButtons(
    isMeasuring: Boolean,
    isArming: Boolean = false,
    canStart: Boolean = true,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val busy = isMeasuring || isArming
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
    ) {
        Button(
            onClick = onStart,
            enabled = !busy && canStart,
            modifier = Modifier.weight(1f).height(Dimens.ButtonHeight)
        ) { Text("開始", style = MaterialTheme.typography.titleMedium) }
        Button(
            onClick = onStop,
            enabled = busy,
            modifier = Modifier.weight(1f).height(Dimens.ButtonHeight),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) { Text("停止", style = MaterialTheme.typography.titleMedium) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentComboBox(
    equipment: List<EquipmentEntity>,
    selected: EquipmentEntity?,
    onSelect: (EquipmentEntity) -> Unit,
    enabled: Boolean = true,
    label: String = "設備",
    placeholder: String = "設備を選んでください",
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            enabled = enabled && equipment.isNotEmpty()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            equipment.forEach { eq ->
                DropdownMenuItem(
                    text = { Text(eq.name) },
                    onClick = {
                        onSelect(eq)
                        expanded = false
                    },
                    modifier = Modifier.defaultMinSize(minHeight = Dimens.MinTouch)
                )
            }
        }
    }
}
