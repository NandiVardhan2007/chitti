package com.owlcoders.chitti.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.ui.theme.Chitti

/*
 * Skeleton loading: while a list's data is on its way, the screen shows the shape of what is
 * coming (grouped rows with an icon well and two lines) instead of an empty state that would
 * flash "Nothing here" and then be replaced. A soft band of light sweeps across, so it reads as
 * loading rather than broken; under reduced motion it stays still.
 */

/** A band of light moving across the element, left to right. */
fun Modifier.shimmer(): Modifier = composed {
    val reduce = rememberReducedMotion()
    val highlight = Chitti.colors.specular.copy(alpha = if (Chitti.colors.isDark) 0.07f else 0.45f)
    if (reduce) return@composed this
    val t by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(Motion.fade(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    drawWithContent {
        drawContent()
        val band = size.width * 0.6f
        val x = t * size.width
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent, 0.5f to highlight, 1f to Color.Transparent,
                start = Offset(x - band, 0f),
                end = Offset(x, size.height * 0.3f)
            )
        )
    }
}

/** A rounded placeholder block. */
@Composable
fun SkeletonBlock(width: Dp, height: Dp, modifier: Modifier = Modifier, fraction: Float? = null) {
    Box(
        modifier = modifier
            .then(if (fraction != null) Modifier.fillMaxWidth(fraction) else Modifier.width(width))
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Chitti.colors.fill)
    )
}

/** A placeholder row with the same metrics as an [InsetRow] with an icon and a subtitle. */
@Composable
fun SkeletonRow(titleFraction: Float = 0.6f, withIcon: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = Inset.textInset, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (withIcon) {
            Box(Modifier.size(Inset.iconWell).clip(RoundedCornerShape(Inset.iconWell * 0.24f)).background(Chitti.colors.fill))
            Spacer(Modifier.width(Space.m))
        }
        Column(Modifier.weight(1f)) {
            SkeletonBlock(0.dp, 14.dp, fraction = titleFraction)
            Spacer(Modifier.height(8.dp))
            SkeletonBlock(0.dp, 11.dp, fraction = (titleFraction + 0.25f).coerceAtMost(0.95f))
        }
    }
}

/** A whole grouped section in skeleton form: a header bar and [rows] placeholder rows. */
fun LazyListScope.skeletonSection(key: String, rows: Int = 3, withIcon: Boolean = true, header: Boolean = true) {
    item(key = "$key:skeleton-header") {
        Box(
            Modifier
                .padding(horizontal = Space.gutter)
                .padding(top = if (header) Space.xxl else Space.l, bottom = Space.s)
                .semantics { contentDescription = "Loading" }
        ) {
            if (header) SkeletonBlock(140.dp, 18.dp, Modifier.shimmer())
        }
    }
    insetSection(key = "$key:skeleton") {
        val widths = listOf(0.62f, 0.45f, 0.7f, 0.52f, 0.66f)
        repeat(rows) { i ->
            row("s$i", if (withIcon) Inset.iconInset else Inset.textInset) {
                Box(Modifier.shimmer()) { SkeletonRow(widths[i % widths.size], withIcon) }
            }
        }
    }
}
