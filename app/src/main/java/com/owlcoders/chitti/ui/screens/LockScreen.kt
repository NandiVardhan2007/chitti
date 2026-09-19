package com.owlcoders.chitti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.owlcoders.chitti.security.AppLock
import com.owlcoders.chitti.security.SecureScreen
import com.owlcoders.chitti.security.findActivity
import com.owlcoders.chitti.ui.components.LiveChittiFace
import com.owlcoders.chitti.ui.components.PrimaryButton
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.theme.Chitti
import kotlinx.coroutines.launch

/** Covers the whole app while it is locked, and asks for the fingerprint straight away. */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    SecureScreen()
    val context = LocalContext.current
    val colors = Chitti.colors
    val scope = rememberCoroutineScope()
    val activity = context.findActivity() as? FragmentActivity

    suspend fun unlock() {
        val a = activity ?: return
        if (AppLock.authenticate(a, "Unlock Chitti", "Use your fingerprint, face or screen lock") != AppLock.Outcome.Cancelled) onUnlocked()
    }
    LaunchedEffect(Unit) { unlock() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            // Swallow touches so nothing underneath can be reached.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Chitti asleep while the app is locked; it opens its eyes when you unlock.
        LiveChittiFace(eyesShut = true, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(Space.l))
        Text("Chitti is locked", style = MaterialTheme.typography.headlineMedium, color = colors.textHigh, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text("Unlock with your fingerprint, face or screen lock.", style = MaterialTheme.typography.bodyMedium, color = colors.textMid, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xl))
        PrimaryButton(text = "Unlock", fill = false, onClick = { scope.launch { unlock() } })
    }
}
