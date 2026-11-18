package com.zerorizz.habits

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.random.Random

private data class Particle(
    val x0: Float, val y0: Float, val vx: Float, val vy: Float,
    val color: Color, val size: Float, val spin: Float, val circle: Boolean,
)

/** Full-screen confetti burst whenever [trigger] changes. Draws only; never blocks taps. */
@Composable
fun ConfettiOverlay(trigger: Int, big: Boolean, enabled: Boolean) {
    var particles by remember { mutableStateOf(emptyList<Particle>()) }
    var t by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val palette = HabitColors + MaterialTheme.colorScheme.primary

    LaunchedEffect(trigger) {
        if (trigger == 0 || !enabled) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val n = if (big) 170 else 45
        particles = List(n) {
            if (big) {
                Particle(
                    x0 = Random.nextFloat(), y0 = -0.05f,
                    vx = (Random.nextFloat() - 0.5f) * 0.3f, vy = Random.nextFloat() * 0.25f + 0.05f,
                    color = palette.random(), size = Random.nextFloat() * 8f + 6f,
                    spin = (Random.nextFloat() - 0.5f) * 720f, circle = Random.nextBoolean(),
                )
            } else {
                val angle = Random.nextFloat() * Math.PI.toFloat() + Math.PI.toFloat()
                val speed = Random.nextFloat() * 0.5f + 0.35f
                Particle(
                    x0 = 0.5f, y0 = 0.45f,
                    vx = kotlin.math.cos(angle) * speed * 0.7f, vy = kotlin.math.sin(angle) * speed,
                    color = palette.random(), size = Random.nextFloat() * 7f + 5f,
                    spin = (Random.nextFloat() - 0.5f) * 900f, circle = Random.nextBoolean(),
                )
            }
        }
        val start = withFrameNanos { it }
        val duration = if (big) 3.2f else 1.6f
        while (true) {
            val now = withFrameNanos { it }
            t = (now - start) / 1_000_000_000f
            if (t > duration) break
        }
        particles = emptyList()
    }

    if (particles.isEmpty()) return
    val duration = if (big) 3.2f else 1.6f
    Canvas(Modifier.fillMaxSize()) {
        val g = if (big) 0.35f else 1.1f
        val fade = (1f - ((t - duration * 0.7f) / (duration * 0.3f))).coerceIn(0f, 1f)
        particles.forEach { p ->
            val x = (p.x0 + p.vx * t) * size.width
            val y = (p.y0 + p.vy * t + 0.5f * g * t * t) * size.height
            val s = p.size.dp.toPx()
            val c = p.color.copy(alpha = fade)
            if (p.circle) {
                drawCircle(c, s / 2, Offset(x, y))
            } else {
                rotate(p.spin * t, Offset(x, y)) {
                    drawRect(c, Offset(x - s / 2, y - s / 4), Size(s, s / 2))
                }
            }
        }
    }
}

/** Squish-and-bounce effect for tap targets. */
class Bounce {
    val scale = Animatable(1f)
    suspend fun play() {
        scale.animateTo(0.78f, tween(70))
        scale.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow))
    }
}

@Composable
fun rememberBounce(): Pair<Bounce, () -> Unit> {
    val b = remember { Bounce() }
    val scope = rememberCoroutineScope()
    return b to { scope.launch { b.play() } ; Unit }
}

fun Modifier.bounce(b: Bounce): Modifier = graphicsLayer { scaleX = b.scale.value; scaleY = b.scale.value }

/** Card colours + border for the chosen card style. */
@Composable
fun habitCardColors(style: CardStyle, color: Color, done: Boolean): Pair<CardColors, BorderStroke?> {
    val cs = MaterialTheme.colorScheme
    val tint = if (done) 0.16f else 0f
    return when (style) {
        CardStyle.FILLED -> CardDefaults.cardColors(containerColor = lerp(cs.surfaceContainerLow, color, tint)) to null
        CardStyle.OUTLINED -> CardDefaults.cardColors(containerColor = lerp(cs.surface, color, tint * 0.6f)) to
            BorderStroke(1.5.dp, color.copy(alpha = if (done) 0.9f else 0.45f))
        CardStyle.GRADIENT -> CardDefaults.cardColors(containerColor = Color.Transparent) to null
        CardStyle.MINIMAL -> CardDefaults.cardColors(containerColor = Color.Transparent) to
            BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun gradientBrush(color: Color, done: Boolean): Brush {
    val cs = MaterialTheme.colorScheme
    return Brush.horizontalGradient(
        listOf(color.copy(alpha = if (done) 0.42f else 0.22f), lerp(cs.surfaceContainerLow, color, 0.04f))
    )
}
