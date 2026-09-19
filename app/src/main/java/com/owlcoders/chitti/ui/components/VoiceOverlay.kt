package com.owlcoders.chitti.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.automation.AssistantResponse
import com.owlcoders.chitti.ui.theme.Chitti
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val VoiceExamples = listOf(
    "“Open WhatsApp”",
    "“Remind me to call mom in 30 minutes”",
    "“What's pending?”",
    "“Turn on the flashlight”"
)

enum class VoiceAssistantState {
    IDLE, LISTENING, THINKING, SPEAKING, RESULT
}

/**
 * The voice overlay: the one place glass covers content, because here the overlay is the control.
 *
 *  - It materialises: blur, tint and content arrive together on one spring, and leave the same way.
 *  - One shape in the middle breathes with the real microphone level (smoothed by a spring so it
 *    has weight); with no level to follow it pulses slowly.
 *  - The transcript streams in as it is recognised: partial words in secondary text with a soft
 *    caret, the final sentence in primary text. No spinner sits between speech and text.
 *  - Cancel is at the bottom, where the thumb already is, and cancels for real (MainActivity stops
 *    the recogniser, the running query and speech). Back and a downward throw do the same.
 *  - Haptics: confirm when listening starts, a tick on the first recognised words, confirm on a
 *    completed action, reject on a failure.
 */
@Composable
fun VoiceOverlay(
    state: VoiceAssistantState,
    transcript: String,
    rmsLevel: Float,
    assistantResponse: AssistantResponse?,
    onMicClick: () -> Unit,
    onDismiss: () -> Unit,
    onStopSpeech: () -> Unit
) {
    val visible = state != VoiceAssistantState.IDLE
    BackHandler(enabled = visible, onBack = onDismiss)

    val colors = Chitti.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val appear by animateFloatAsState(if (visible) 1f else 0f, Motion.standard(), label = "overlayAppear")
    if (appear <= 0.001f && !visible) return

    // ------------------------------------------------------------------ haptics
    LaunchedEffect(state) {
        if (state == VoiceAssistantState.LISTENING) haptics.confirm()
    }
    var heardSomething by remember { mutableStateOf(false) }
    LaunchedEffect(state, transcript.isNotBlank()) {
        if (state == VoiceAssistantState.LISTENING) {
            if (transcript.isNotBlank() && !heardSomething) haptics.clockTick()
            heardSomething = transcript.isNotBlank()
        }
    }
    LaunchedEffect(assistantResponse) {
        val r = assistantResponse ?: return@LaunchedEffect
        if (r.actionSuccess) haptics.confirm() else haptics.reject()
    }

    // ------------------------------------------------------------------ drag to dismiss
    val drag = remember { Animatable(0f) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    var pastDismiss by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) drag.snapTo(0f) }
    val pulled = (drag.value / (heightPx * 0.5f)).coerceIn(0f, 1f)
    val presence = appear * (1f - pulled)

    val dragState = rememberDraggableState { delta ->
        val next = drag.value + delta
        val bounded = if (next >= 0f) next else -rubberBand(-next, heightPx)
        scope.launch { drag.snapTo(bounded) }
        val past = bounded > heightPx * 0.22f
        if (past != pastDismiss) {
            pastDismiss = past
            haptics.threshold()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            // Swallow touches so nothing underneath reacts while the overlay is up.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .draggable(
                orientation = Orientation.Vertical,
                state = dragState,
                onDragStopped = { velocity ->
                    pastDismiss = false
                    val projected = drag.value + projectMomentum(velocity)
                    if (projected > heightPx * 0.22f && velocity > -300f) {
                        onDismiss()
                    } else {
                        scope.launch { drag.animateTo(0f, Motion.momentum(), initialVelocity = velocity) }
                    }
                }
            )
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxSize(),
            depth = GlassDepth.Thick,
            overlay = true,
            progress = presence
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (drag.value.coerceAtLeast(0f) + (1f - appear) * 48.dp.toPx()).roundToInt()) }
                .graphicsLayer { alpha = presence }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = Space.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Space.xl))
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(Motion.fade(160)) togetherWith fadeOut(Motion.fade(120)) },
                label = "voiceState"
            ) { s ->
                Text(
                    when (s) {
                        VoiceAssistantState.LISTENING -> "Listening"
                        VoiceAssistantState.THINKING -> "Working on it"
                        VoiceAssistantState.SPEAKING -> "Speaking"
                        else -> "Chitti"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.glassTextMid
                )
            }

            // Words: what you said, then what Chitti answered.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(Modifier.height(Space.xxxl))
                when {
                    state == VoiceAssistantState.LISTENING && transcript.isBlank() -> {
                        Text("Try saying", style = MaterialTheme.typography.bodyMedium, color = colors.glassTextMid)
                        Spacer(Modifier.height(Space.s))
                        RotatingText(
                            items = VoiceExamples,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.textHigh,
                            textAlign = TextAlign.Center
                        )
                    }
                    transcript.isNotBlank() -> {
                        Transcript(text = transcript, final = state != VoiceAssistantState.LISTENING)
                    }
                }
                val response = assistantResponse
                if (response != null && state != VoiceAssistantState.LISTENING && state != VoiceAssistantState.THINKING) {
                    Spacer(Modifier.height(Space.xl))
                    if (response.actionLabel != null) {
                        StatusPill(
                            text = response.actionLabel,
                            tint = if (response.actionSuccess) colors.success else colors.warning,
                            icon = if (response.actionSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Info
                        )
                        Spacer(Modifier.height(Space.m))
                    }
                    WordRevealText(
                        text = response.message,
                        animate = true,
                        style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                        color = colors.textHigh,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                Spacer(Modifier.height(Space.xl))
            }

            VoiceForm(
                state = state,
                level = rmsLevel,
                onClick = onMicClick,
                modifier = Modifier.size(168.dp)
            )

            Spacer(Modifier.height(Space.l))

            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                if (state == VoiceAssistantState.SPEAKING) {
                    SecondaryButton(text = "Stop speaking", onClick = onStopSpeech)
                }
                SecondaryButton(
                    text = if (state == VoiceAssistantState.LISTENING || state == VoiceAssistantState.THINKING) "Cancel" else "Done",
                    onClick = onDismiss
                )
            }
            Spacer(Modifier.height(Space.l))
        }
    }
}

/** The transcript: partial in secondary text with a soft caret, final in primary text. */
@Composable
private fun Transcript(text: String, final: Boolean) {
    val colors = Chitti.colors
    val reduce = rememberReducedMotion()
    val caretAlpha = if (final || reduce) {
        if (final) 0f else 0.6f
    } else {
        val t = rememberInfiniteTransition(label = "caret")
        t.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(Motion.fade(650), RepeatMode.Reverse),
            label = "caretAlpha"
        ).value
    }
    val annotated = buildAnnotatedString {
        append(text)
        if (!final) {
            withStyle(SpanStyle(color = colors.accent.copy(alpha = caretAlpha))) { append(" |") }
        }
    }
    Text(
        annotated,
        style = if (text.length > 40) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
        color = if (final) colors.textHigh else colors.glassTextMid,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .heightIn(min = 34.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/**
 * One continuous form that breathes with the voice. Its outline is a circle displaced by a few
 * slow sine waves; the microphone level (smoothed by a spring, so it has weight) sets how far
 * the outline swells and moves. Thinking turns the waves faster and quieter; with no level at all
 * the form pulses slowly. Under reduced motion it is a still circle whose size follows the level.
 * Tapping it listens again.
 */
@Composable
private fun VoiceForm(
    state: VoiceAssistantState,
    level: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = Chitti.colors
    val reduce = rememberReducedMotion()
    val listening = state == VoiceAssistantState.LISTENING
    val voice by animateFloatAsState(
        targetValue = if (listening) level.coerceIn(0f, 1f) else 0f,
        animationSpec = Motion.standard(),
        label = "voiceLevel"
    )
    val scale by animateFloatAsState(
        targetValue = when (state) {
            VoiceAssistantState.LISTENING -> 1f
            VoiceAssistantState.THINKING -> 0.82f
            else -> 0.9f
        },
        animationSpec = Motion.gentle(),
        label = "formScale"
    )

    // A clock for the waves, read only inside draw so it costs a redraw, not a recomposition.
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now -> clock.floatValue = (now - start) / 1_000_000_000f }
        }
    }

    val interaction = remember { MutableInteractionSource() }
    Canvas(
        modifier = modifier
            .pressScale(interaction, 0.94f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (listening) "Listening. Tap to start again" else "Tap to speak" }
    ) {
        val t = clock.floatValue
        val speed = if (state == VoiceAssistantState.THINKING) 2.2f else 1f
        // Slow idle pulse (~2.6s cycle) when there is no level to follow.
        val idle = if (listening) 0f else 0.035f * (0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 2.6f))
        val amp = if (reduce) 0f else 0.05f + 0.20f * voice + idle
        val base = size.minDimension / 2f * 0.72f * scale * (1f + if (reduce) 0.25f * voice else 0f)
        val c = Offset(size.width / 2f, size.height / 2f)
        val path = Path()
        val steps = 96
        for (i in 0..steps) {
            val a = i / steps.toFloat() * 2f * PI.toFloat()
            val wave = 0.5f * sin(3f * a + t * 1.3f * speed) +
                0.3f * sin(5f * a - t * 1.7f * speed) +
                0.2f * sin(2f * a + t * 0.9f * speed)
            val r = base * (1f + amp * wave)
            val x = c.x + r * cos(a)
            val y = c.y + r * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(
            path = path,
            brush = Brush.radialGradient(
                colors = listOf(colors.accentFill, colors.accentDeep),
                center = Offset(c.x - base * 0.3f, c.y - base * 0.35f),
                radius = base * 1.6f
            )
        )
    }
}
