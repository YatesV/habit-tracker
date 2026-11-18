package com.zerorizz.habits

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

fun pct(f: Float?): String = f?.let { "${(it * 100).roundToInt()}%" } ?: "–"

@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 8.dp,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val ring = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        ring.animateTo(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
    }
    val anim = ring.value
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val s = stroke.toPx()
            val tl = Offset(s / 2, s / 2)
            val sz = Size(size.width - s, size.height - s)
            drawArc(track, 0f, 360f, false, topLeft = tl, size = sz, style = Stroke(s))
            drawArc(color, -90f, 360f * anim, false, topLeft = tl, size = sz, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/** GitHub-style year grid. Values: null = no data, -1 = not scheduled, 0..1 = progress. */
@Composable
fun Heatmap(
    values: Map<LocalDate, Float>,
    color: Color,
    today: LocalDate,
    weekStartsMonday: Boolean,
    modifier: Modifier = Modifier,
    weeks: Int = 53,
) {
    val cell = 13.dp
    val gap = 3.dp
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val off = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tm = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)
    val firstDow = if (weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    val todayIdx = (today.dayOfWeek.value - firstDow.value + 7) % 7
    val gridStart = today.minusDays(todayIdx.toLong()).minusWeeks((weeks - 1).toLong())
    val scroll = rememberScrollState()
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    val labelH = 14.dp

    Column(modifier) {
        Row {
            Column(Modifier.padding(top = labelH).width(16.dp)) {
                for (r in 0 until 7) {
                    Box(Modifier.height(cell + gap)) {
                        if (r % 2 == 1) {
                            Text(
                                firstDow.plus(r.toLong()).getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                                fontSize = 9.sp, color = labelColor,
                            )
                        }
                    }
                }
            }
            Box(Modifier.horizontalScroll(scroll)) {
                Canvas(Modifier.size(width = (cell + gap) * weeks, height = labelH + (cell + gap) * 7)) {
                    val c = cell.toPx()
                    val g = gap.toPx()
                    val top = labelH.toPx()
                    for (w in 0 until weeks) {
                        val weekStart = gridStart.plusWeeks(w.toLong())
                        if (weekStart.dayOfMonth <= 7 && w < weeks - 1) {
                            drawText(
                                tm,
                                weekStart.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()),
                                topLeft = Offset(w * (c + g), 0f),
                                style = labelStyle,
                            )
                        }
                        for (r in 0 until 7) {
                            val d = weekStart.plusDays(r.toLong())
                            if (d.isAfter(today)) continue
                            val v = values[d]
                            val col = when {
                                v == null -> empty
                                v < 0f -> off
                                v == 0f -> empty
                                else -> color.copy(alpha = 0.3f + 0.7f * v)
                            }
                            drawRoundRect(
                                col,
                                topLeft = Offset(w * (c + g), top + r * (c + g)),
                                size = Size(c, c),
                                cornerRadius = CornerRadius(c * 0.25f),
                            )
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Less ", fontSize = 10.sp, color = labelColor)
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { v ->
                Box(
                    Modifier.padding(horizontal = 1.5.dp).size(10.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (v == 0f) empty else color.copy(alpha = 0.3f + 0.7f * v))
                )
            }
            Text(" More", fontSize = 10.sp, color = labelColor)
        }
    }
}

@Composable
fun LineChart(
    points: List<Float?>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    maxValue: Float = 1f,
    percent: Boolean = true,
    refLine: Float? = null,
    height: Dp = 170.dp,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val tm = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(points) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = reveal.value
        val leftPad = 36.dp.toPx()
        val topPad = 8.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val w = size.width - leftPad - 6.dp.toPx()
        val h = size.height - topPad - bottomPad
        val maxV = if (maxValue <= 0f) 1f else maxValue
        for (i in 0..2) {
            val frac = i / 2f
            val y = topPad + h * (1 - frac)
            drawLine(
                grid, Offset(leftPad, y), Offset(size.width, y), 1.dp.toPx(),
                pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            val txt = if (percent) "${(frac * 100).roundToInt()}%" else trimNum((maxV * frac).toDouble())
            drawText(tm, txt, Offset(0f, y - 7.dp.toPx()), labelStyle)
        }
        refLine?.let {
            val y = topPad + h * (1 - (it / maxV).coerceIn(0f, 1f))
            drawLine(
                color.copy(alpha = 0.6f), Offset(leftPad, y), Offset(size.width, y), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
            )
        }
        if (points.isEmpty()) return@Canvas
        val step = if (points.size > 1) w / (points.size - 1) else 0f
        val pts = points.mapIndexedNotNull { i, v ->
            v?.let { Offset(leftPad + i * step, topPad + h * (1 - (it / maxV).coerceIn(0f, 1f))) }
        }
        if (pts.size >= 2) {
            val line = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(pts.last().x, topPad + h)
                lineTo(pts.first().x, topPad + h)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f * r), Color.Transparent), topPad, topPad + h))
            val pm = PathMeasure()
            pm.setPath(line, false)
            val seg = Path()
            pm.getSegment(0f, pm.length * r, seg, true)
            drawPath(seg, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val revealX = leftPad + w * r + 1f
        pts.forEach {
            if (it.x <= revealX) {
                drawCircle(color, 4.5.dp.toPx(), it)
                drawCircle(surface, 2.dp.toPx(), it)
            }
        }
        val every = ceil(labels.size / 6.0).toInt().coerceAtLeast(1)
        labels.forEachIndexed { i, l ->
            if ((labels.size - 1 - i) % every != 0) return@forEachIndexed
            val lay = tm.measure(l, labelStyle)
            val x = (leftPad + i * step - lay.size.width / 2f).coerceIn(leftPad - 8f, size.width - lay.size.width)
            drawText(lay, topLeft = Offset(x, size.height - lay.size.height))
        }
    }
}

@Composable
fun BarChart(
    values: List<Float?>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    maxValue: Float = 1f,
    valueText: (Float) -> String = { pct(it) },
) {
    val best = values.filterNotNull().maxOrNull()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { i, v ->
            val frac = ((v ?: 0f) / if (maxValue <= 0f) 1f else maxValue).coerceIn(0f, 1f)
            val bar = remember { Animatable(0f) }
            LaunchedEffect(frac) {
                bar.animateTo(frac, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
            }
            val anim = bar.value
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(v?.let(valueText) ?: "–", fontSize = 10.sp, color = muted, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth()
                            .fillMaxHeight(if (v == null) 0.03f else anim.coerceAtLeast(0.03f))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                when {
                                    v == null -> MaterialTheme.colorScheme.surfaceVariant
                                    v == best -> color
                                    else -> color.copy(alpha = 0.5f)
                                }
                            )
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(labels.getOrElse(i) { "" }, fontSize = 11.sp, color = muted, maxLines = 1)
            }
        }
    }
}

@Composable
fun RadarChart(axes: List<Pair<String, Float>>, color: Color, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val style = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(axes) { grow.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow)) }
    Canvas(modifier) {
        val gv = grow.value
        val n = axes.size
        if (n < 3) return@Canvas
        val center = Offset(size.width / 2, size.height / 2)
        val radius = min(size.width, size.height) / 2 - 30.dp.toPx()
        fun pt(i: Int, r: Float): Offset {
            val a = -PI / 2 + 2 * PI * i / n
            return Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat())
        }
        for (ring in 1..4) {
            val r = radius * ring / 4
            val p = Path()
            for (i in 0 until n) {
                val q = pt(i, r)
                if (i == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y)
            }
            p.close()
            drawPath(p, grid, style = Stroke(1.dp.toPx()))
        }
        for (i in 0 until n) drawLine(grid, center, pt(i, radius), 1.dp.toPx())
        val vp = Path()
        axes.forEachIndexed { i, (_, v) ->
            val q = pt(i, radius * v.coerceIn(0.04f, 1f) * gv)
            if (i == 0) vp.moveTo(q.x, q.y) else vp.lineTo(q.x, q.y)
        }
        vp.close()
        drawPath(vp, color.copy(alpha = 0.3f))
        drawPath(vp, color, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
        axes.forEachIndexed { i, (label, v) ->
            drawCircle(color, 4.dp.toPx(), pt(i, radius * v.coerceIn(0.04f, 1f) * gv))
            val lay = tm.measure(label, style)
            val lp = pt(i, radius + 17.dp.toPx())
            drawText(lay, topLeft = Offset(lp.x - lay.size.width / 2f, lp.y - lay.size.height / 2f))
        }
    }
}
