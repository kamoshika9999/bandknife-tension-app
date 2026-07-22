package com.bandknife.tension.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bandknife.tension.domain.StatisticsEngine
import kotlin.math.max

@Composable
fun SpectrumChart(spectrum: FloatArray, modifier: Modifier = Modifier) {
    if (spectrum.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.fillMaxWidth().height(80.dp)) {
        val maxVal = spectrum.maxOrNull() ?: 1f
        val step = size.width / (spectrum.size - 1).coerceAtLeast(1)
        val path = Path()
        spectrum.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v / maxVal) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2f))
    }
}

@Composable
fun HistogramChart(values: List<Double>, modifier: Modifier = Modifier) {
    if (values.isEmpty()) return
    val bins = StatisticsEngine.histogram(values)
    val maxCount = bins.maxOfOrNull { it.second } ?: 1
    val mean = values.average()
    val std = kotlin.math.sqrt(values.map { (it - mean) * (it - mean) }.average())
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)

    Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
        val barWidth = size.width / bins.size
        bins.forEachIndexed { i, (center, count) ->
            val h = (count.toFloat() / maxCount) * size.height * 0.8f
            drawRect(
                color = primary,
                topLeft = Offset(i * barWidth + 2f, size.height - h),
                size = androidx.compose.ui.geometry.Size(barWidth - 4f, h)
            )
        }
        if (std > 0) {
            val min = values.min()
            val max = values.max()
            val range = max(max - min, 0.001)
            val path = Path()
            for (i in 0..50) {
                val xVal = min + range * i / 50.0
                val pdf = StatisticsEngine.normalPdf(xVal, mean, std)
                val x = ((xVal - min) / range * size.width).toFloat()
                val y = size.height - (pdf * size.height * 0.8f / (1.0 / (std * 2.5))).toFloat()
                if (i == 0) path.moveTo(x, y.coerceIn(0f, size.height)) else path.lineTo(x, y.coerceIn(0f, size.height))
            }
            drawPath(path, secondary, style = Stroke(2f))
        }
    }
}

@Composable
fun TapLevelMeter(amplitude: Double, modifier: Modifier = Modifier) {
    val goodColor = Color(0xFF4CAF50)
    val warnColor = Color(0xFFFFC107)
    val badColor = Color(0xFFF44336)
    Canvas(modifier = modifier.fillMaxWidth().height(24.dp)) {
        val w = size.width
        val h = size.height
        drawRect(Color.Gray.copy(alpha = 0.3f), topLeft = Offset.Zero, size = size)
        drawRect(goodColor.copy(alpha = 0.4f), topLeft = Offset(w * 0.15f, 0f), size = androidx.compose.ui.geometry.Size(w * 0.7f, h))
        val x = (amplitude.coerceIn(0.0, 1.0) * w).toFloat()
        val color = when {
            amplitude < 0.15 -> warnColor
            amplitude > 0.95 -> badColor
            else -> goodColor
        }
        drawCircle(color, radius = 8f, center = Offset(x, h / 2))
    }
}

@Composable
fun PassFailBadge(passed: Boolean, modifier: Modifier = Modifier) {
    val text = if (passed) "OK" else "要調整"
    val color = if (passed) Color(0xFF2E7D32) else Color(0xFFC62828)
    androidx.compose.material3.Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.displayMedium,
        modifier = modifier
    )
}
