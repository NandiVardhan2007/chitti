package com.owlcoders.chitti.ui.components

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlin.math.PI

/**
 * Motion vocabulary, from Apple's "Designing Fluid Interfaces". Springs are described the way
 * Apple describes them, by damping ratio and response (seconds to settle, roughly), and turned into
 * Compose stiffness here, so every value in the app is one of a handful of named behaviours:
 *
 *  - [standard]  damping 1.0, response 0.35  anything that moves, resizes or recolours;
 *  - [snappy]    damping 1.0, response 0.22  press feedback and small controls;
 *  - [momentum]  damping 0.8, response 0.30  only after a gesture carried velocity (a flick,
 *                                            a thrown sheet): the one place overshoot is earned;
 *  - [gentle]    damping 1.0, response 0.55  large surfaces and continuous signals (mic level).
 *
 * [fade] is the only tween, and it is for opacity alone: an alpha has no momentum to carry.
 */
object Motion {
    private fun stiffness(response: Float): Float {
        val w = 2f * PI.toFloat() / response
        return w * w
    }

    private val StandardK = stiffness(0.35f)
    private val SnappyK = stiffness(0.22f)
    private val MomentumK = stiffness(0.30f)
    private val GentleK = stiffness(0.55f)

    fun <T> standard(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = StandardK)
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = SnappyK)
    fun <T> momentum(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = MomentumK)
    fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = GentleK)

    /** Opacity only. Short in, a touch shorter out, so leaving never lingers. */
    fun <T> fade(durationMs: Int = 180, delayMs: Int = 0, easing: Easing = FastOutSlowInEasing): TweenSpec<T> =
        tween(durationMillis = durationMs, delayMillis = delayMs, easing = easing)
}

/** True when the system animator scale is 0 (Android's "Remove animations"). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Android has no "Reduce transparency" switch, so glass stands down whenever the user has asked
 * for less visual complexity: animations removed, or high-contrast text on. Blur also needs
 * API 31; below that every glass surface is opaque.
 */
@Composable
fun rememberReducedTransparency(): Boolean {
    val context = LocalContext.current
    val reducedMotion = rememberReducedMotion()
    return remember(reducedMotion) {
        val highContrast = try {
            Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1
        } catch (e: Exception) {
            false
        }
        reducedMotion || highContrast || Build.VERSION.SDK_INT < 31
    }
}

/**
 * Scales the element down the instant it is pressed and springs back on release.
 * Pass the same [interactionSource] you give to `clickable`.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressed: Float = 0.96f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduce = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reduce) pressed else 1f,
        animationSpec = Motion.snappy(),
        label = "pressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Apple's momentum projection (from the "Designing Fluid Interfaces" sample code): where a
 * flick at [velocityPxPerSec] would come to rest under scroll-style deceleration.
 */
fun projectMomentum(velocityPxPerSec: Float, decelerationRate: Float = 0.998f): Float =
    (velocityPxPerSec / 1000f) * decelerationRate / (1f - decelerationRate)

/** Progressive resistance past a boundary: the further past it, the less the element follows. */
fun rubberBand(overshoot: Float, dimension: Float, constant: Float = 0.55f): Float =
    (overshoot * dimension * constant) / (dimension + constant * overshoot)

/** [rubberBand] for a signed overshoot. */
fun rubberBandSigned(overshoot: Float, dimension: Float): Float =
    if (overshoot >= 0f) rubberBand(overshoot, dimension) else -rubberBand(-overshoot, dimension)

/** Smooth 0..1 ramp of [x] between [from] and [to]; used to map scroll progress onto opacity. */
fun smoothStep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

// ------------------------------------------------------------------------------------ Haptics

/**
 * Haptics for the moments that earn them (Apple's audio-haptic rules: causality, harmony, utility).
 * Fire on the same frame as the visual change that caused it, and only for commits, snaps,
 * threshold crossings and outcomes, never for every tap. Honours the system haptics setting.
 */
class ChittiHaptics internal constructor(private val view: View) {
    /** A detent: a segment or tab passing under the finger. */
    fun tick() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)

    /** The first words of a transcript arriving. */
    fun clockTick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    /** A drag crossed the point where releasing would commit. */
    fun threshold() = perform(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
        else HapticFeedbackConstants.CONTEXT_CLICK
    )

    /** Something started or finished successfully. */
    fun confirm() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)

    /** Something failed or was refused. */
    fun reject() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)

    /** A primary control was pressed down (mic, send). */
    fun press() = perform(HapticFeedbackConstants.KEYBOARD_TAP)

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): ChittiHaptics {
    val view = LocalView.current
    return remember(view) { ChittiHaptics(view) }
}
