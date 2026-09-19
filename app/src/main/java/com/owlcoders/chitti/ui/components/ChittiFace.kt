package com.owlcoders.chitti.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.owlcoders.chitti.ui.theme.ChittiColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/*
 * Chitti's face, drawn from the logo's geometry (branding/chitti-logo): a yellow sphere shaded
 * from a lighter top-left to a deeper lower edge, a highlight arc, and two capsule eyes slanted
 * -8 degrees. All proportions are fractions of the face radius, from the logo's r = 31 face with
 * 7.5 x 17 eyes at +/-7.75 from the centre, 3.5 above it.
 *
 * The eyes carry all the expression, so the mascot stays as simple as the logo.
 */

/** How the eyes look. Every value is continuous, so expressions can be animated between. */
@Immutable
data class FaceExpression(
    /** 1 = open, 0 = shut. */
    val eyeOpen: Float = 1f,
    /** -1 = looking fully left, 1 = right. */
    val lookX: Float = 0f,
    /** -1 = up, 1 = down. */
    val lookY: Float = 0f,
    /** 0 = neutral, 1 = a happy squint (shorter eyes pushed up by the cheeks). */
    val squint: Float = 0f
)

private val SKEW = tan(Math.toRadians(-8.0)).toFloat()

/** The shaded yellow body, as a circle or as any outline (the voice screen wobbles it). */
fun DrawScope.drawChittiBody(center: Offset, radius: Float, colors: ChittiColors, outline: Path? = null) {
    val brush = Brush.radialGradient(
        0f to lerp(colors.accentFill, colors.specular, 0.35f),
        0.55f to colors.accentFill,
        1f to colors.accentDeep,
        center = Offset(center.x - radius * 0.28f, center.y - radius * 0.4f),
        radius = radius * 1.64f
    )
    if (outline != null) drawPath(outline, brush) else drawCircle(brush, radius, center)
    // The light catching the top-left edge.
    drawArc(
        color = colors.specular.copy(alpha = 0.4f),
        startAngle = 190f,
        sweepAngle = 70f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.77f, center.y - radius * 0.77f),
        size = Size(radius * 1.54f, radius * 1.54f),
        style = Stroke(width = radius * 0.065f, cap = StrokeCap.Round)
    )
}

fun DrawScope.drawChittiEyes(center: Offset, radius: Float, expression: FaceExpression, color: Color) {
    val e = expression
    val w = radius * 0.242f
    val h = (radius * 0.548f * e.eyeOpen.coerceIn(0.06f, 1.2f) * (1f - 0.42f * e.squint)).coerceAtLeast(w * 0.35f)
    val y = center.y - radius * 0.113f + e.lookY * radius * 0.08f - e.squint * radius * 0.05f
    for (side in listOf(-1f, 1f)) {
        val x = center.x + side * radius * 0.25f + e.lookX * radius * 0.13f
        drawPath(eyePath(x, y, w, h, center.y), color)
    }
}

/** A capsule of [w] x [h] centred at ([cx], [cy]), slanted like the logo's eyes. */
private fun eyePath(cx: Float, cy: Float, w: Float, h: Float, pivotY: Float): Path {
    val r = w / 2f
    val top = minOf(cy - h / 2f + r, cy)
    val bottom = maxOf(cy + h / 2f - r, cy)
    val p = Path()
    val steps = 10
    fun add(x: Float, y: Float, first: Boolean) {
        val sx = x + SKEW * (y - pivotY)
        if (first) p.moveTo(sx, y) else p.lineTo(sx, y)
    }
    for (i in 0..steps) {
        val a = PI.toFloat() + PI.toFloat() * i / steps
        add(cx + r * cos(a), top + r * sin(a), i == 0)
    }
    for (i in 0..steps) {
        val a = PI.toFloat() * i / steps
        add(cx + r * cos(a), bottom + r * sin(a), false)
    }
    p.close()
    return p
}

/** Chitti's face at rest or mid-expression. The eyes are always the logo's ink colour. */
@Composable
fun ChittiFace(expression: FaceExpression, colors: ChittiColors, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawChittiBody(c, r, colors)
        drawChittiEyes(c, r, expression, colors.onAccent)
    }
}
