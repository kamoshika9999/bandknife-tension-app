package com.bandknife.tension.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bandknife.tension.domain.StatisticsEngine
import com.bandknife.tension.domain.TapQualityChecker
import com.bandknife.tension.ui.theme.Dimens
import com.bandknife.tension.ui.theme.statusColors
import kotlin.math.max

@Composable
fun SpectrumChart(spectrum: FloatArray, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.outlineVariant
    // 空でも高さを保持する。測定開始のたびにレイアウトが跳ねると読み取りにくい。
    Canvas(modifier = modifier.fillMaxWidth().height(80.dp)) {
        val stroke = 2.dp.toPx()
        if (spectrum.isEmpty()) {
            drawLine(
                color = baseline,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = stroke
            )
            return@Canvas
        }
        val maxVal = spectrum.maxOrNull() ?: 1f
        val step = size.width / (spectrum.size - 1).coerceAtLeast(1)
        val path = Path()
        spectrum.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v / maxVal) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun HistogramChart(
    values: List<Double>,
    unitLabel: String = "N",
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return
    val bins = StatisticsEngine.histogram(values)
    val maxCount = bins.maxOfOrNull { it.second } ?: 1
    val mean = values.average()
    val std = kotlin.math.sqrt(values.map { (it - mean) * (it - mean) }.average())
    val primary = MaterialTheme.colorScheme.primary
    val curve = MaterialTheme.colorScheme.tertiary
    val axis = MaterialTheme.colorScheme.outline
    val minValue = values.min()
    val maxValue = values.max()

    Column(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "測定値の分布。${minValue.toInt()} から ${maxValue.toInt()} $unitLabel " +
                    "の範囲に ${values.size} 回分が入っています"
            }
    ) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val barWidth = size.width / bins.size
            val gap = 2.dp.toPx()
            bins.forEachIndexed { i, (_, count) ->
                val h = (count.toFloat() / maxCount) * size.height * 0.8f
                drawRect(
                    color = primary,
                    topLeft = Offset(i * barWidth + gap, size.height - h),
                    size = Size((barWidth - gap * 2).coerceAtLeast(1f), h)
                )
            }
            if (std > 0) {
                val range = max(maxValue - minValue, 0.001)
                val path = Path()
                for (i in 0..50) {
                    val xVal = minValue + range * i / 50.0
                    val pdf = StatisticsEngine.normalPdf(xVal, mean, std)
                    val x = ((xVal - minValue) / range * size.width).toFloat()
                    val y = size.height - (pdf * size.height * 0.8f / (1.0 / (std * 2.5))).toFloat()
                    if (i == 0) path.moveTo(x, y.coerceIn(0f, size.height)) else path.lineTo(x, y.coerceIn(0f, size.height))
                }
                drawPath(path, curve, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
            drawLine(
                color = axis,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Dimens.SpaceXs),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AxisLabel("${minValue.toInt()} $unitLabel")
            AxisLabel("平均 ${mean.toInt()} $unitLabel")
            AxisLabel("${maxValue.toInt()} $unitLabel")
        }
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun TapLevelMeter(amplitude: Double, modifier: Modifier = Modifier) {
    val status = statusColors
    val pct = TapQualityChecker.formatPercent(amplitude)
    val minPct = TapQualityChecker.formatPercent(TapQualityChecker.WEAK_RATIO)
    val maxPct = TapQualityChecker.formatPercent(TapQualityChecker.STRONG_RATIO)
    // 強さの判定は色だけに任せず、必ず言葉でも示す
    val levelLabel: String
    val levelTextColor: Color
    val levelFillColor: Color
    when {
        amplitude <= 0.0 -> {
            levelLabel = "待機"
            levelTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            levelFillColor = status.idleFill
        }
        amplitude < TapQualityChecker.WEAK_RATIO -> {
            levelLabel = "弱い"
            levelTextColor = status.warnText
            levelFillColor = status.warnFill
        }
        amplitude > TapQualityChecker.STRONG_RATIO -> {
            levelLabel = "強すぎ"
            levelTextColor = status.ngText
            levelFillColor = status.ngFill
        }
        else -> {
            levelLabel = "適正"
            levelTextColor = status.okText
            levelFillColor = status.okFill
        }
    }
    val trackColor = status.idleContainer
    val bandColor = status.okFill.copy(alpha = 0.35f)
    val outline = MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "打音の強さ $levelLabel、$pct パーセント。適正は $minPct から $maxPct パーセント" }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.SpaceXs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("打音の強さ", style = MaterialTheme.typography.bodySmall)
            Text(
                "$levelLabel $pct%",
                style = MaterialTheme.typography.titleMedium,
                color = levelTextColor
            )
            Text(
                "適正 $minPct〜$maxPct%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
            val w = size.width
            val h = size.height
            drawRect(trackColor, topLeft = Offset.Zero, size = size)
            drawRect(
                bandColor,
                topLeft = Offset(w * TapQualityChecker.WEAK_RATIO.toFloat(), 0f),
                size = Size(
                    w * (TapQualityChecker.STRONG_RATIO - TapQualityChecker.WEAK_RATIO).toFloat(),
                    h
                )
            )
            val x = (amplitude.coerceIn(0.0, 1.0) * w).toFloat()
            drawCircle(levelFillColor, radius = 7.dp.toPx(), center = Offset(x, h / 2))
            drawCircle(
                outline,
                radius = 7.dp.toPx(),
                center = Offset(x, h / 2),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

/**
 * 合否は色だけに依存させず、アイコン・塗り・文字の 3 つで示す。
 * 逆光や粉塵で曇った画面、色覚特性のある作業者でも一目で区別できるようにするため。
 */
@Composable
fun PassFailBadge(passed: Boolean, modifier: Modifier = Modifier) {
    val status = statusColors
    val text = if (passed) "OK" else "要調整"
    Surface(
        modifier = modifier.semantics { contentDescription = if (passed) "判定 OK" else "判定 要調整" },
        color = if (passed) status.okFill else status.ngFill,
        contentColor = Color.White,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
        ) {
            Icon(
                if (passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.height(32.dp)
            )
            Text(text, style = MaterialTheme.typography.headlineMedium)
        }
    }
}
