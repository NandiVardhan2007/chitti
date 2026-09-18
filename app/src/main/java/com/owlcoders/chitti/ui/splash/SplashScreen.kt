package com.owlcoders.chitti.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.ui.components.ChittiBranding
import com.owlcoders.chitti.ui.components.GlowingAIOrb
import com.owlcoders.chitti.ui.theme.AppBlack
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animation States
    var showBackground by remember { mutableStateOf(false) }
    var showOrb by remember { mutableStateOf(false) }
    var showBrand by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }

    // Animated Values based on state
    val bgAlpha by animateFloatAsState(
        targetValue = if (showBackground) 1f else 0f,
        animationSpec = tween(400),
        label = "BgAlpha"
    )

    val orbScale by animateFloatAsState(
        targetValue = if (showOrb) 1f else 0.8f,
        animationSpec = tween(800, delayMillis = 100),
        label = "OrbScale"
    )

    val orbAlpha by animateFloatAsState(
        targetValue = if (showOrb) 1f else 0f,
        animationSpec = tween(600),
        label = "OrbAlpha"
    )

    LaunchedEffect(Unit) {
        // Phase 1 — Background
        showBackground = true
        delay(300)

        // Phase 2 — AI Orb
        showOrb = true
        delay(600)

        // Phase 3 — Brand Reveal
        showBrand = true
        delay(400)

        // Phase 4 — Subtitle Reveal
        showSubtitle = true
        delay(800) // Keep it on screen for a moment

        // Phase 5 — Transition to App
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBlack)
            .alpha(bgAlpha),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .scale(orbScale)
                    .alpha(orbAlpha)
            ) {
                GlowingAIOrb(isInitializing = true)
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            ChittiBranding(
                showBrand = showBrand,
                showSubtitle = showSubtitle
            )
        }
    }
}
