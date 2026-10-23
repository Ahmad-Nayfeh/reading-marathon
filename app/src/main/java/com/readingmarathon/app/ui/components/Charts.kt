package com.readingmarathon.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.readingmarathon.app.ui.theme.Brand
import com.readingmarathon.core.Dates
import com.readingmarathon.core.HeatCell
import java.time.LocalDate

/**
 * Hand-drawn charts. Time always runs right-to-left (the reading direction) by drawing from the
 * end, which avoids the legacy app's reversed axes. Every chart animates in once.
 */
@Composable
private fun rememberReveal(key: Any?): Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    return a.value
}

/** Area chart (e.g. cumulative hours). Values ordered oldest → newest; drawn newest on the left. */
@Composable
fun AreaChart(values: List<Double>, modifier: Modifier = Modifier, height: Dp = 150.dp, color: Color = Brand.colors.gold) {
    val reveal = rememberReveal(values)
    val line = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.size < 2) return@Canvas
        val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1e-6)
        for (g in 1..3) {
            val y = size.height * g / 4f
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        val n = values.size
        fun x(i: Int) = size.width - size.width * i / (n - 1f)
        fun y(v: Double) = size.height - (size.height * (v / max) * reveal).toFloat()
        val p = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until n) lineTo(x(i), y(values[i]))
        }
        val fill = Path().apply {
            addPath(p)
            lineTo(x(n - 1), size.height)
            lineTo(x(0), size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f))))
        drawPath(p, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 4.dp.toPx(), Offset(x(n - 1), y(values[n - 1])))
    }
}

/** Vertical bars with labels under them. Order given is right-to-left on screen. */
@Composable
fun BarChart(bars: List<Pair<String, Double>>, modifier: Modifier = Modifier, height: Dp = 150.dp, color: Color = MaterialTheme.colorScheme.primary, valueSuffix: String = "") {
    val reveal = rememberReveal(bars)
    val max = (bars.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1e-6)
    Row(modifier.fillMaxWidth().height(height + 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEach { (label, v) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (v == 0.0) "" else "${Math.round(v)}$valueSuffix",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.72f)
                        .height((height.value * (v / max) * reveal).toFloat().coerceAtLeast(2f).dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(if (v == max) Brand.colors.gold else color.copy(alpha = 0.85f)),
                )
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Thin daily columns (rhythm). Newest on the left. */
@Composable
fun RhythmChart(values: List<Int>, modifier: Modifier = Modifier, height: Dp = 110.dp) {
    val reveal = rememberReveal(values)
    val gold = Brand.colors.gold
    val base = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val n = values.size
        val slot = size.width / n
        val w = (slot * 0.62f).coerceAtLeast(1.5f)
        values.forEachIndexed { i, v ->
            val h = size.height * (v.toFloat() / max) * reveal
            val x = size.width - slot * (i + 1) + (slot - w) / 2
            drawRoundRect(
                if (v == max) gold else base.copy(alpha = 0.75f),
                topLeft = Offset(x, size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2, w / 2),
            )
        }
    }
}

/** Donut with a legend. */
@Composable
fun DonutChart(slices: List<Pair<String, Double>>, modifier: Modifier = Modifier, centerTop: String = "", centerBottom: String = "") {
    val colors = Brand.colors.chart
    val reveal = rememberReveal(slices)
    val total = slices.sumOf { it.second }.coerceAtLeast(1e-6)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(132.dp)) {
                val stroke = 18.dp.toPx()
                val d = size.minDimension - stroke
                val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
                var start = -90f
                slices.forEachIndexed { i, (_, v) ->
                    val sweep = (360f * (v / total).toFloat()) * reveal
                    drawArc(colors[i % colors.size], start, (sweep - 2f).coerceAtLeast(0f), false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Butt))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerTop, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (centerBottom.isNotEmpty()) Text(centerBottom, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEachIndexed { i, (label, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i % colors.size]))
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${Math.round(v / total * 100)}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Horizontal ranking bars (name, value). */
@Composable
fun RankBars(rows: List<Pair<String, Double>>, format: (Double) -> String, modifier: Modifier = Modifier) {
    val reveal = rememberReveal(rows)
    val max = (rows.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1e-6)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { i, (name, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(78.dp), maxLines = 1)
                Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Box(
                        Modifier.fillMaxWidth((v / max * reveal).toFloat().coerceIn(0.02f, 1f)).height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (i == 0) Brand.colors.gold else MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(format(v), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
            }
        }
    }
}

/** Commitment map: weeks as columns (newest on the left), Saturday at the top. */
@Composable
fun Heatmap(weeks: List<List<HeatCell>>, modifier: Modifier = Modifier) {
    val heat = Brand.colors.heat
    val max = weeks.flatten().maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
    fun colorFor(m: Int): Color = when {
        m == 0 -> heat[0]
        m < max * 0.25 -> heat[1]
        m < max * 0.5 -> heat[2]
        m < max * 0.8 -> heat[3]
        else -> heat[4]
    }
    val today = LocalDate.now()
    Column(modifier) {
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Short ranges get big cells that fill the card; long ones shrink to 14dp and scroll.
        val gap = 3.dp
        val labels = 20.dp
        val fit = if (weeks.isEmpty()) 14.dp else (maxWidth - labels - gap * (weeks.size + 1) - 4.dp) / weeks.size
        val cellSize = fit.coerceIn(14.dp, 30.dp)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(labels), verticalArrangement = Arrangement.spacedBy(gap)) {
                listOf("س", "ح", "ن", "ث", "ر", "خ", "ج").forEach {
                    Box(Modifier.size(labels, cellSize), contentAlignment = Alignment.Center) {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.width(gap))
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        week.forEach { cell ->
                            Box(
                                Modifier.size(cellSize).clip(RoundedCornerShape(if (cellSize > 20.dp) 5.dp else 3.dp))
                                    .background(
                                        when {
                                            !cell.inRange -> Color.Transparent
                                            cell.date.isAfter(today) -> heat[0].copy(alpha = 0.35f) // days still to come
                                            else -> colorFor(cell.minutes)
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
        }
      }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("أقل", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            heat.forEach { Box(Modifier.padding(horizontal = 1.5.dp).size(11.dp).clip(RoundedCornerShape(2.dp)).background(it)) }
            Spacer(Modifier.width(6.dp))
            Text("أكثر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (weeks.isNotEmpty()) {
                Text(
                    "${Dates.short(weeks.first().first().date)} – ${Dates.short(weeks.last().last().date)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Circular progress with a label in the middle. */
@Composable
fun ProgressRing(fraction: Float, modifier: Modifier = Modifier, size: Dp = 92.dp, stroke: Dp = 9.dp, content: @Composable () -> Unit) {
    val reveal = rememberReveal(fraction)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val gold = Brand.colors.gold
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val d = this.size.minDimension - s
            val tl = Offset((this.size.width - d) / 2, (this.size.height - d) / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(s))
            drawArc(gold, -90f, -360f * fraction.coerceIn(0f, 1f) * reveal, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}
