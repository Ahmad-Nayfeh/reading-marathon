package com.readingmarathon.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

/**
 * The last seven days as bars, newest on the left (RTL), each tappable to edit that day.
 * A thin gold line marks the daily goal.
 */
@Composable
fun WeekBars(days: List<Pair<LocalDate, Int>>, goal: Int, today: LocalDate, onDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val reveal = rememberReveal(days)
    val max = maxOf(days.maxOfOrNull { it.second } ?: 0, goal, 1)
    val gold = Brand.colors.gold
    val ink = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val barH = 86.dp
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { (date, minutes) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onDay(date) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (minutes > 0) "$minutes" else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Canvas(Modifier.fillMaxWidth(0.62f).height(barH)) {
                    val w = size.width
                    drawRoundRect(track, size = Size(w, size.height), cornerRadius = CornerRadius(w / 2, w / 2))
                    val h = size.height * (minutes.toFloat() / max) * reveal
                    if (h > 0f) {
                        drawRoundRect(
                            if (minutes >= goal) gold else ink.copy(alpha = 0.8f),
                            topLeft = Offset(0f, size.height - h), size = Size(w, h), cornerRadius = CornerRadius(w / 2, w / 2),
                        )
                    }
                    val gy = size.height - size.height * goal / max
                    drawLine(gold.copy(alpha = 0.7f), Offset(-w * 0.25f, gy), Offset(w * 1.25f, gy), strokeWidth = 1.dp.toPx())
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    Dates.weekdayLetter(date),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                    color = if (date == today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
