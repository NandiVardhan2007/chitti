package com.owlcoders.chitti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.ui.theme.Chitti
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/*
 * Glass: a control-layer material, never a content material.
 *
 * It exists on exactly three surfaces in the app: the navigation bar (once content has scrolled
 * under it), the floating tab bar, and the voice overlay. Content (rows, groups, bubbles, chips)
 * is always opaque. Glass never sits on glass.
 *
 * Each glass surface is: the content behind it blurred (<= 32dp), a translucent tint, a trace of
 * grain (a clean blur reads as plastic), and a 0.5dp specular edge weighted to the top, which is
 * what makes it read as a material catching light instead of a blurred rectangle.
 *
 * When blur is unavailable or unwanted (API < 31, animations removed, high-contrast text) the
 * surface becomes genuinely opaque and gains a shadow to carry the depth the blur carried.
 *
 * The shape of the port follows React Bits' GlassSurface (reactbits.dev), rebuilt on Haze.
 */

enum class GlassDepth(val blur: Dp) {
    /** Bars: nav bar, tab bar. */
    Regular(24.dp),
    /** Full-screen overlay: heavier, so what is behind reads as context, not content. */
    Thick(32.dp)
}

/** Where the specular edge is drawn. A full-width bar only has a bottom edge that meets content. */
enum class GlassEdge { Outline, Bottom }

/**
 * The shared backdrop. Screens mark their scrolling content with [glassBackdrop]; every glass
 * surface samples from it. [suspended] turns bar glass off while the voice overlay is up, so no
 * more than two glass surfaces ever composite at once.
 */
@Stable
class GlassEnvironment(val state: HazeState) {
    var suspended by mutableStateOf(false)
}

val LocalGlass = staticCompositionLocalOf<GlassEnvironment?> { null }

/** Marks content that glass surfaces may blur. Content only: never put this on a control. */
fun Modifier.glassBackdrop(): Modifier = composed {
    val env = LocalGlass.current
    if (env != null) hazeSource(env.state) else this
}

/**
 * Nested corners share a centre: a control inset [inset] inside a container with corner
 * [outer] gets `outer - inset`. Every nested radius in the app goes through this.
 */
fun concentricRadius(outer: Dp, inset: Dp): Dp = (outer - inset).coerceAtLeast(0.dp)

/**
 * A glass surface.
 *
 * @param progress how much of the material is present, 0..1. The nav bar drives it from scroll
 * offset and the overlay from its enter/drag progress; blur, tint, grain and edge all scale with
 * it together, so the material arrives rather than fades.
 * @param overlay true for the voice overlay: glass is never suspended for it.
 * @param elevation shadow for floating surfaces. In the opaque fallback every surface gets at
 * least a small shadow in its place.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    depth: GlassDepth = GlassDepth.Regular,
    edge: GlassEdge = GlassEdge.Outline,
    progress: Float = 1f,
    overlay: Boolean = false,
    elevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val colors = Chitti.colors
    val env = LocalGlass.current
    val reduced = rememberReducedTransparency()
    val p = progress.coerceIn(0f, 1f)
    val useBlur = env != null && !reduced && (overlay || !env.suspended)

    val fallbackElevation = if (elevation > 0.dp) elevation else 6.dp
    val shadowElevation = (if (useBlur) elevation else fallbackElevation) * p

    val surface = Modifier
        .then(
            if (shadowElevation > 0.dp) Modifier.shadow(
                elevation = shadowElevation,
                shape = shape,
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow
            ) else Modifier
        )
        .clip(shape)
        .then(
            if (useBlur) {
                Modifier.hazeEffect(
                    state = env.state,
                    style = HazeStyle(
                        backgroundColor = colors.background,
                        tints = listOf(HazeTint(colors.glassTint)),
                        blurRadius = depth.blur,
                        noiseFactor = 0.06f
                    )
                ) {
                    alpha = p
                    blurRadius = depth.blur * p
                }
            } else {
                Modifier.background(colors.glassFallback.copy(alpha = p), shape)
            }
        )
        .specularEdge(shape, edge, p, colors.specular, colors.separator, colors.isDark)

    Box(modifier = modifier.then(surface), content = content)
}

/**
 * The light-catching edge. On an outlined surface: brightest along the top, fading down the sides.
 * On a bar: a hairline along the bottom where it meets content, brightest in the middle.
 */
private fun Modifier.specularEdge(
    shape: Shape,
    edge: GlassEdge,
    progress: Float,
    specular: Color,
    separator: Color,
    dark: Boolean
): Modifier {
    if (progress <= 0f) return this
    // White light reads on dark glass; on light glass the edge needs a faint darker rule under it.
    val top = specular.copy(alpha = (if (dark) 0.34f else 0.9f) * progress)
    val low = (if (dark) specular.copy(alpha = 0.06f) else separator.copy(alpha = 0.5f)).let { it.copy(alpha = it.alpha * progress) }
    return when (edge) {
        GlassEdge.Outline -> border(
            width = 0.5.dp,
            brush = Brush.verticalGradient(0f to top, 0.45f to low, 1f to low),
            shape = shape
        )
        GlassEdge.Bottom -> drawWithContent {
            drawContent()
            val y = size.height - 0.5.dp.toPx() / 2f
            // Dark: white light, brightest mid-bar. Light: white would vanish, so a separator rule.
            val rule = if (dark) listOf(low, top, low) else List(3) { separator.copy(alpha = progress) }
            drawLine(
                brush = Brush.horizontalGradient(rule),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.5.dp.toPx()
            )
        }
    }
}
