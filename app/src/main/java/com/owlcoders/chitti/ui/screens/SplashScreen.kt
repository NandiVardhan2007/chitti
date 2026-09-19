package com.owlcoders.chitti.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owlcoders.chitti.ui.components.ChittiFace
import com.owlcoders.chitti.ui.components.FaceExpression
import com.owlcoders.chitti.ui.components.Motion
import com.owlcoders.chitti.ui.components.rememberHaptics
import com.owlcoders.chitti.ui.components.rememberReducedMotion
import com.owlcoders.chitti.ui.theme.DarkChittiColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val NAME = "Chitti"

/**
 * The opening: Chitti wakes up.
 *
 *  1. The face arrives with a little bounce, eyes shut;
 *  2. it opens its eyes, blinks, looks left and right as if taking in the room;
 *  3. it squints happily as its name sharpens into view letter by letter, the tagline under it;
 *  4. it fades into the app.
 *
 * The full version plays on the first launch after install; after that a shorter one (a blink
 * and the name), so opening the app every day isn't slowed down. A tap skips it; with animations
 * turned off it is a short, still fade. The splash is always dark (the brand tile), in both
 * appearances, and continues the system's dark starting window without a seam.
 */
@Composable
fun ChittiSplash(full: Boolean, onReadyForContent: () -> Unit, onFinished: () -> Unit) {
    val colors = DarkChittiColors
    val reduce = rememberReducedMotion()
    val haptics = rememberHaptics()

    val faceScale = remember { Animatable(if (reduce) 1f else 0.55f) }
    val faceAlpha = remember { Animatable(if (reduce) 1f else 0f) }
    val eyeOpen = remember { Animatable(if (reduce || !full) 1f else 0.06f) }
    val lookX = remember { Animatable(0f) }
    val squint = remember { Animatable(0f) }
    val letters = remember { Animatable(if (reduce) NAME.length + 1f else 0f) }
    val tagline = remember { Animatable(if (reduce) 1f else 0f) }
    val exit = remember { Animatable(1f) }
    var skipped by remember { mutableStateOf(false) }

    suspend fun blink() {
        eyeOpen.animateTo(0.08f, Motion.fade(80))
        eyeOpen.animateTo(1f, Motion.snappy())
    }

    // The app behind is built while the face holds still (eyes shut), so the heavy first frame
    // of the real UI never lands in the middle of a movement.
    suspend fun letContentBuild() {
        onReadyForContent()
        withFrameNanos { }
        withFrameNanos { }
    }

    LaunchedEffect(skipped) {
        if (skipped) {
            onReadyForContent()
            exit.animateTo(0f, Motion.fade(160))
            onFinished()
            return@LaunchedEffect
        }
        if (reduce) {
            letContentBuild()
            delay(450)
        } else if (full) {
            // 1. Arrives, asleep.
            coroutineScope {
                launch { faceAlpha.animateTo(1f, Motion.fade(200)) }
                faceScale.animateTo(1f, Motion.momentum())
            }
            letContentBuild()
            // 2. Wakes up, blinks, takes in the room.
            eyeOpen.animateTo(1f, Motion.snappy())
            haptics.tick()
            delay(160)
            blink()
            lookX.animateTo(-1f, Motion.snappy())
            delay(140)
            coroutineScope {
                launch { lookX.animateTo(1f, Motion.standard()) }
                // 3. Its name arrives while it looks across at it.
                launch { letters.animateTo(NAME.length + 1f, Motion.fade(NAME.length * 90, easing = LinearEasing)) }
                launch {
                    delay(NAME.length * 60L)
                    tagline.animateTo(1f, Motion.fade(320))
                }
                launch {
                    delay(360)
                    lookX.animateTo(0f, Motion.standard())
                    squint.animateTo(1f, Motion.standard())
                }
            }
            delay(420)
        } else {
            coroutineScope {
                launch { faceAlpha.animateTo(1f, Motion.fade(160)) }
                faceScale.animateTo(1f, Motion.standard())
            }
            letContentBuild()
            coroutineScope {
                launch { letters.animateTo(NAME.length + 1f, Motion.fade(NAME.length * 55, easing = LinearEasing)) }
                launch { delay(160); tagline.animateTo(1f, Motion.fade(220)) }
                launch { blink() }
            }
            delay(220)
        }
        exit.animateTo(0f, Motion.fade(if (reduce) 200 else 300))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exit.value }
            .background(colors.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skipped = true }
            .semantics { contentDescription = "Chitti. Tap to skip" },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ChittiFace(
                expression = FaceExpression(eyeOpen = eyeOpen.value, lookX = lookX.value, squint = squint.value),
                colors = colors,
                modifier = Modifier
                    .size(128.dp)
                    .graphicsLayer {
                        scaleX = faceScale.value
                        scaleY = faceScale.value
                        alpha = faceAlpha.value
                    }
            )
            Spacer(Modifier.height(28.dp))
            // Each letter rises out of a soft blur in turn: the name is read as it arrives.
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.clearAndSetSemantics { }
            ) {
                NAME.forEachIndexed { i, ch ->
                    val p = (letters.value - i).coerceIn(0f, 1f)
                    Text(
                        ch.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 46.sp, letterSpacing = (-1).sp),
                        color = colors.textHigh,
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = p
                                translationY = (1f - p) * 14.dp.toPx()
                            }
                            .then(if (p < 1f) Modifier.blur((10f * (1f - p)).dp) else Modifier)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Your deadlines, caught.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textMid,
                modifier = Modifier.graphicsLayer {
                    alpha = tagline.value
                    translationY = (1f - tagline.value) * 8.dp.toPx()
                }
            )
        }
    }
}
