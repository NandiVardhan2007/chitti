package com.owlcoders.chitti.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.owlcoders.chitti.ui.theme.Chitti
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/*
 * Chitti at rest, alive: the same face as the logo, with a pulse. It floats, breathes, blinks
 * (now and then twice), glances around and holds the look, and smiles every so often.
 *
 * Every motion is a pure function of one process-wide clock, so two screens that show Chitti at
 * the same time (a push transition) show it in the same pose instead of two out-of-step copies.
 * The clock is read only in draw and layer blocks: it costs a redraw per frame, never a
 * recomposition. Under reduced motion the clock stops and Chitti holds still.
 */

private val ChittiEpoch = System.nanoTime()
private const val TAU = (2.0 * PI).toFloat()

/** Seconds on the shared clock, advancing every frame while [running]. */
@Composable
fun rememberChittiClock(running: Boolean = true): FloatState {
    val clock = remember { mutableFloatStateOf(chittiNow()) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (isActive) withFrameNanos { clock.floatValue = chittiNow() }
    }
    return clock
}

private fun chittiNow(): Float = (System.nanoTime() - ChittiEpoch) / 1_000_000_000f

/** Where the eyes rest between glances: centre, then somewhere else, then back. */
private val Glances = listOf(
    Offset(0f, 0f), Offset(0.6f, -0.2f), Offset(0f, 0f), Offset(-0.55f, 0.15f),
    Offset(0.25f, 0.4f), Offset(0f, 0f), Offset(-0.4f, -0.3f), Offset(0.5f, 0.2f)
)

private fun hump(p: Float, start: Float, length: Float): Float =
    if (p < start || p > start + length) 0f else sin((p - start) / length * PI.toFloat())

/** How an idle Chitti's eyes look at time [t]. */
fun idleExpression(t: Float): FaceExpression {
    // A blink every 4.8s; every third one is a double blink.
    val blinkCycle = 4.8f
    val n = floor(t / blinkCycle).toInt()
    val p = t - n * blinkCycle
    val closed = max(hump(p, 0f, 0.16f), if (n % 3 == 2) hump(p, 0.28f, 0.16f) else 0f)

    // Glances are saccades: a quick move (0.28s) to the next point, then a hold.
    val glanceCycle = 3.4f
    val g = floor(t / glanceCycle).toInt()
    val move = smoothStep(0f, 0.28f, t - g * glanceCycle)
    val from = Glances[Math.floorMod(g - 1, Glances.size)]
    val to = Glances[Math.floorMod(g, Glances.size)]

    // A smile every 15s, for about a second and a half.
    val smile = hump(t % 15f, 10f, 1.6f)

    return FaceExpression(
        eyeOpen = 1f - 0.94f * closed,
        lookX = from.x + (to.x - from.x) * move,
        lookY = from.y + (to.y - from.y) * move,
        squint = 0.9f * smile
    )
}

/** Vertical float, as a fraction of the face radius. */
fun idleBob(t: Float): Float = sin(t * TAU / 5.2f) * 0.06f

/** Side-to-side drift, as a fraction of the face radius. */
fun idleSway(t: Float): Float = sin(t * TAU / 11f) * 0.1f

/** Tilt in degrees. */
fun idleTilt(t: Float): Float = sin(t * TAU / 7.4f) * 3f

/** Breathing scale. */
fun idleBreath(t: Float): Float = 1f + 0.015f * sin(t * TAU / 3.6f)

/**
 * Chitti as a quiet presence behind a screen's content, in the space rows don't fill. Low
 * opacity, no semantics, no touch. [lift] (0..1) raises it a little as the page scrolls, so
 * it moves at its own depth instead of sticking to the glass.
 */
@Composable
fun ChittiAmbient(modifier: Modifier = Modifier, lift: () -> Float = { 0f }) {
    val colors = Chitti.colors
    val reduce = rememberReducedMotion()
    val clock = rememberChittiClock(running = !reduce)
    val opacity = if (colors.isDark) 0.11f else 0.2f
    BoxWithConstraints(modifier.fillMaxSize().clearAndSetSemantics { }) {
        val diameter = minOf(maxWidth * 0.62f, maxHeight * 0.34f)
        Canvas(
            Modifier
                .align(BiasAlignment(0f, 0.42f))
                .size(diameter)
                .graphicsLayer {
                    val t = clock.floatValue
                    val r = size.minDimension / 2f
                    translationX = idleSway(t) * r
                    translationY = idleBob(t) * r - lift().coerceIn(0f, 1f) * r * 0.3f
                    rotationZ = idleTilt(t)
                    val s = idleBreath(t)
                    scaleX = s
                    scaleY = s
                    alpha = opacity
                    // Eyes are drawn over the body: composite first, then fade, so they stay dark.
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawChittiBody(c, r, colors)
            drawChittiEyes(c, r, idleExpression(clock.floatValue), colors.onAccent)
        }
    }
}

/**
 * A full-strength living face for hero spots (sign-in). Idle life runs underneath; the caller can
 * take over the gaze ([look], null = idle), close the eyes ([eyesShut], e.g. while a password is
 * typed) or make it smile ([happy]). Overrides blend in on springs, never jump.
 */
@Composable
fun LiveChittiFace(
    modifier: Modifier = Modifier,
    look: Offset? = null,
    eyesShut: Boolean = false,
    happy: Boolean = false
) {
    val colors = Chitti.colors
    val reduce = rememberReducedMotion()
    val clock = rememberChittiClock(running = !reduce)
    val lookWeight by animateFloatAsState(if (look != null) 1f else 0f, Motion.standard(), label = "lookWeight")
    val lookX by animateFloatAsState(look?.x ?: 0f, Motion.standard(), label = "lookX")
    val lookY by animateFloatAsState(look?.y ?: 0f, Motion.standard(), label = "lookY")
    val shut by animateFloatAsState(if (eyesShut) 1f else 0f, Motion.snappy(), label = "shut")
    val smile by animateFloatAsState(if (happy) 1f else 0f, Motion.standard(), label = "smile")

    Canvas(
        modifier.graphicsLayer {
            val t = clock.floatValue
            val r = size.minDimension / 2f
            translationY = idleBob(t) * r * 0.7f
            rotationZ = idleTilt(t) * 0.6f
        }
    ) {
        val r = size.minDimension / 2f * 0.94f
        val c = Offset(size.width / 2f, size.height / 2f)
        val idle = idleExpression(clock.floatValue)
        drawChittiBody(c, r, colors)
        drawChittiEyes(
            c, r,
            FaceExpression(
                eyeOpen = idle.eyeOpen * (1f - 0.94f * shut),
                lookX = idle.lookX + (lookX - idle.lookX) * lookWeight,
                lookY = idle.lookY + (lookY - idle.lookY) * lookWeight,
                squint = maxOf(idle.squint * (1f - lookWeight), smile)
            ),
            colors.onAccent
        )
    }
}
