package com.owlcoders.chitti.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import com.owlcoders.chitti.ui.theme.metricNumber
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/*
 * Jetpack Compose ports of React Bits pieces (reactbits.dev), kept to the ones that carry meaning:
 *  - RollingNumber  <- Components/Counter           (a live count changing, digit by digit)
 *  - WordRevealText <- TextAnimations/SplitText     (a fresh reply reading in like a stream)
 *  - RotatingText   <- TextAnimations/RotatingText  (example voice commands, one at a time)
 *  - GlassSurface   <- Components/GlassSurface      (in Glass.kt)
 * None of them plays on entrance: a number appears settled and only rolls when it changes, which
 * is the moment it carries information. Every one honours reduced motion.
 */

/**
 * Counter: each digit is a column that rolls to its new value on a spring, so a count going from
 * 9 to 10 visibly carries. Tabular figures keep every column the same width. First composition
 * shows the value as it is; only later changes roll.
 */
@Composable
fun RollingNumber(
    value: Int,
    style: TextStyle,
    color: Color = LocalContentColor.current,
    modifier: Modifier = Modifier
) {
    val reduce = rememberReducedMotion()
    val digits = value.coerceAtLeast(0).toString()
    val numberStyle = style.metricNumber()
    Row(modifier = modifier.clearAndSetSemantics { contentDescription = digits }) {
        // Keyed from the right, so the ones column stays the ones column when a digit is added.
        digits.forEachIndexed { index, ch ->
            val place = digits.length - 1 - index
            androidx.compose.runtime.key(place) {
                RollingDigit(ch.digitToInt(), numberStyle, color, reduce)
            }
        }
    }
}

@Composable
private fun RollingDigit(digit: Int, style: TextStyle, color: Color, reduce: Boolean) {
    val position by animateFloatAsState(
        targetValue = digit.toFloat(),
        animationSpec = if (reduce) snap() else Motion.standard(),
        label = "digit"
    )
    // Measure one glyph, then draw the 0-9 strip offset by the animated position.
    Box(modifier = Modifier.clipToBounds()) {
        Text("0", style = style, color = Color.Transparent)
        Layout(
            content = {
                Column {
                    for (d in 0..9) Text(d.toString(), style = style, color = color)
                }
            }
        ) { measurables, constraints ->
            val strip = measurables.first().measure(constraints.copy(minHeight = 0, maxHeight = Int.MAX_VALUE))
            val cell = strip.height / 10f
            layout(strip.width, cell.toInt()) {
                strip.placeRelative(0, -(position * cell).toInt())
            }
        }
    }
}

/**
 * Word-by-word reveal for text that just arrived, the way a streamed answer reads. A single Text
 * with per-word alpha, so wrapping and selection behave like plain text. With [animate] false, or
 * under reduced motion, the text is shown at once.
 */
@Composable
fun WordRevealText(
    text: String,
    animate: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = LocalContentColor.current,
    perWordMs: Int = 28,
    maxMs: Int = 900,
    onRevealed: () -> Unit = {}
) {
    val reduce = rememberReducedMotion()
    val words = remember(text) { text.split(' ') }
    // Fully revealed is words + 2: each word takes two steps to reach full strength.
    val revealed = words.size + 2f
    val progress = remember(text) { Animatable(if (animate && !reduce) 0f else revealed) }
    LaunchedEffect(text, animate) {
        if (!animate || reduce || progress.value >= revealed) {
            if (animate) onRevealed()
            return@LaunchedEffect
        }
        val duration = (words.size * perWordMs).coerceAtMost(maxMs)
        // An opacity sweep across words: linear time, so the reading pace is steady.
        progress.animateTo(revealed, Motion.fade(duration, easing = LinearEasing))
        onRevealed()
    }
    val p = progress.value
    val annotated = if (p >= revealed) {
        AnnotatedString(text)
    } else {
        buildAnnotatedString {
            words.forEachIndexed { i, word ->
                val a = ((p - i) / 2f).coerceIn(0f, 1f)
                pushStyle(SpanStyle(color = color.copy(alpha = color.alpha * a)))
                append(word)
                if (i < words.lastIndex) append(' ')
                pop()
            }
        }
    }
    Text(text = annotated, modifier = modifier, style = style, color = color)
}

/**
 * RotatingText: cycles through [items], each leaving upward as the next rises from below, so the
 * direction of travel says "next". Holds the first item under reduced motion.
 */
@Composable
fun RotatingText(
    items: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = LocalContentColor.current,
    intervalMs: Long = 2800,
    textAlign: TextAlign? = null
) {
    val reduce = rememberReducedMotion()
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(items, reduce) {
        if (reduce || items.size < 2) return@LaunchedEffect
        while (isActive) {
            delay(intervalMs)
            index = (index + 1) % items.size
        }
    }
    AnimatedContent(
        targetState = items.getOrElse(index) { "" },
        modifier = modifier,
        transitionSpec = {
            (slideInVertically(Motion.standard()) { it / 2 } + fadeIn(Motion.fade(220))) togetherWith
                (slideOutVertically(Motion.standard()) { -it / 2 } + fadeOut(Motion.fade(160)))
        },
        contentAlignment = Alignment.Center,
        label = "rotatingText"
    ) { value ->
        Text(value, style = style, color = color, textAlign = textAlign)
    }
}
