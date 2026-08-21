package com.k1.gastracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (values.isEmpty()) return@Canvas
            val maxY = (values.maxOrNull() ?: 0.0).coerceAtLeast(1e-9)
            val bottom = size.height - 16.dp.toPx()
            val top = 10.dp.toPx()
            val chartHeight = bottom - top

            drawLine(axisColor, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 2f)

            val n = values.size
            val slot = size.width / n
            val barWidth = slot * 0.6f
            values.forEachIndexed { i, v ->
                val h = (v / maxY * chartHeight).toFloat()
                val x = slot * i + (slot - barWidth) / 2
                drawRect(
                    color = barColor,
                    topLeft = Offset(x, bottom - h),
                    size = Size(barWidth, h),
                )
            }
            drawAxisText(formatAxis(maxY), 0f, top + 8.dp.toPx(), labelColor)
        }
        AxisLabels(labels, labelColor)
    }
}

@Composable
fun LineChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.secondary,
    padStart: Dp = 16.dp,
    emptyLabel: String = "No data",
) {
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(Modifier.fillMaxWidth().matchParentSize()) {
                when {
                    values.isEmpty() -> {
                        drawNoDataText(emptyLabel, labelColor)
                        return@Canvas
                    }
                    values.size == 1 -> {
                        drawSinglePoint(values[0], padStart.toPx(), lineColor, axisColor, labelColor)
                    }
                    else -> {
                        drawMultiLine(values, padStart.toPx(), lineColor, axisColor, labelColor)
                    }
                }
            }
        }
        AxisLabels(labels, labelColor)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNoDataText(label: String, color: Color) {
    val paint = android.graphics.Paint().apply {
        this.color = color.toArgb()
        textSize = 14.sp.toPx()
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(label, size.width / 2, size.height / 2, paint)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSinglePoint(
    value: Double,
    padStart: Float,
    lineColor: Color,
    axisColor: Color,
    labelColor: Color,
) {
    val bottom = size.height - 16.dp.toPx()
    val top = 10.dp.toPx()
    val y = if (value > 0) (top + bottom) / 2 else bottom
    val centerX = size.width / 2

    drawLine(axisColor, Offset(padStart, bottom), Offset(size.width - 12.dp.toPx(), bottom), 2f)
    drawLine(axisColor, Offset(padStart, top), Offset(padStart, bottom), 2f)
    drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(centerX, y))
    drawAxisText(formatAxis(value), 0f, top + 8.dp.toPx(), labelColor)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMultiLine(
    values: List<Double>,
    padStart: Float,
    lineColor: Color,
    axisColor: Color,
    labelColor: Color,
) {
    val minV = values.min()
    val maxV = values.max()
    val span = (maxV - minV).takeIf { it > 1e-9 } ?: 1.0
    val padEnd = 12.dp.toPx()
    val top = 10.dp.toPx()
    val bottom = size.height - 16.dp.toPx()
    val chartHeight = bottom - top
    val chartWidth = size.width - padStart - padEnd

    fun point(i: Int, v: Double): Offset {
        val x = padStart + chartWidth * i / (values.size - 1)
        val y = (bottom - ((v - minV) / span * chartHeight)).toFloat()
        return Offset(x.toFloat(), y)
    }

    drawLine(axisColor, Offset(padStart, bottom), Offset(size.width - padEnd, bottom), 2f)
    drawLine(axisColor, Offset(padStart, top), Offset(padStart, bottom), 2f)

    val path = Path()
    values.forEachIndexed { i, v ->
        val p = point(i, v)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
    values.indices.forEach { i ->
        drawCircle(lineColor, radius = 3.dp.toPx(), center = point(i, values[i]))
    }
    drawAxisText(formatAxis(maxV), 0f, top + 8.dp.toPx(), labelColor)
    drawAxisText(formatAxis(minV), 0f, bottom, labelColor)
    drawAxisText(formatAxis((maxV + minV) / 2), 0f, (top + bottom) / 2, labelColor)
}

@Composable
private fun AxisLabels(labels: List<String>, color: Color) {
    if (labels.isEmpty()) return
    Row(Modifier.fillMaxWidth()) {
        labels.forEach { l ->
            Text(
                l,
                fontSize = 9.sp,
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAxisText(
    text: String,
    x: Float,
    y: Float,
    color: Color,
) {
    val paint = android.graphics.Paint().apply {
        this.color = color.toArgb()
        textSize = 9.sp.toPx()
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

private fun formatAxis(v: Double): String = String.format(Locale.US, "%.1f", v)
