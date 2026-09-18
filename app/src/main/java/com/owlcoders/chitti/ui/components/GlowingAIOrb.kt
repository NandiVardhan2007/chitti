package com.owlcoders.chitti.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.ui.theme.ChittiYellow
import com.owlcoders.chitti.ui.theme.ChittiYellowSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GlowingAIOrb(
    modifier: Modifier = Modifier,
    isInitializing: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbTransition")

    // Pulsing core scale
    val coreScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CoreScale"
    )

    // Rotating outer ring
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isInitializing) 3000 else 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RingRotation"
    )
    
    // Rotating inner ring (opposite direction)
    val innerRingRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isInitializing) 4000 else 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "InnerRingRotation"
    )

    // Subtle glow opacity pulse
    val glowOpacity by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowOpacity"
    )

    Box(
        modifier = modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val radius = size.minDimension / 2

            // 1. Ambient Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ChittiYellow.copy(alpha = glowOpacity),
                        ChittiYellow.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = radius * 1.5f
                ),
                radius = radius * 1.5f
            )

            // 2. Core Orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ChittiYellowSecondary,
                        ChittiYellow
                    ),
                    center = center,
                    radius = radius * 0.4f
                ),
                radius = (radius * 0.4f) * coreScale
            )

            // 3. Outer Ring (Dashed/Segmented)
            val outerRingRadius = radius * 0.8f
            val startAngleOuter = ringRotation
            drawArc(
                color = ChittiYellow.copy(alpha = 0.7f),
                startAngle = startAngleOuter,
                sweepAngle = 140f,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx())
            )
            drawArc(
                color = ChittiYellow.copy(alpha = 0.7f),
                startAngle = startAngleOuter + 180f,
                sweepAngle = 140f,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx())
            )

            // 4. Inner Ring (Thin)
            val innerRingRadius = radius * 0.6f
            val startAngleInner = innerRingRotation
            drawArc(
                color = ChittiYellowSecondary.copy(alpha = 0.5f),
                startAngle = startAngleInner,
                sweepAngle = 200f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx())
            )

            // 5. Particles / Dots rotating around
            if (isInitializing) {
                val particleRadius = radius * 0.95f
                val dotAngle1 = (ringRotation * 1.5f) * (PI / 180f)
                val dotAngle2 = (ringRotation * 1.5f + 180f) * (PI / 180f)
                
                val x1 = center.x + particleRadius * cos(dotAngle1).toFloat()
                val y1 = center.y + particleRadius * sin(dotAngle1).toFloat()
                
                val x2 = center.x + particleRadius * cos(dotAngle2).toFloat()
                val y2 = center.y + particleRadius * sin(dotAngle2).toFloat()

                drawCircle(
                    color = ChittiYellow,
                    radius = 3.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(x1, y1)
                )
                drawCircle(
                    color = ChittiYellow,
                    radius = 3.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(x2, y2)
                )
            }
        }
    }
}
