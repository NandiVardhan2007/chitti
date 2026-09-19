package com.owlcoders.chitti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.BuildConfig
import com.owlcoders.chitti.R
import com.owlcoders.chitti.account.Auth
import com.owlcoders.chitti.account.AuthError
import com.owlcoders.chitti.ui.components.BarIconButton
import com.owlcoders.chitti.ui.components.ChittiTextField
import com.owlcoders.chitti.ui.components.LinkButton
import com.owlcoders.chitti.ui.components.PrimaryButton
import com.owlcoders.chitti.ui.components.SecondaryButton
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.rememberHaptics
import com.owlcoders.chitti.ui.theme.Chitti
import kotlinx.coroutines.launch

/**
 * Sign-in, required before first use. Google in one tap, or email and password.
 * [onSkip] exists only in debug builds when Firebase isn't configured, so the app can still be
 * developed and demoed before the Firebase project is connected.
 */
@Composable
fun LoginScreen(onSignedIn: () -> Unit, onSkip: (() -> Unit)?) {
    val context = LocalContext.current
    val colors = Chitti.colors
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    var mode by remember { mutableIntStateOf(0) } // email: 0 sign in, 1 create account
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    val emailOk = email.trim().matches(Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
    val canSubmit = !busy && emailOk && password.length >= (if (mode == 1) 8 else 1) && (mode == 0 || name.isNotBlank())

    fun run(block: suspend () -> Unit) {
        message = null
        info = null
        busy = true
        scope.launch {
            try {
                block()
            } catch (e: AuthError) {
                haptics.reject()
                message = e.message
            } finally {
                busy = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Space.xxxl * 2))
            // The app icon itself, so the first screen introduces the face people will see on
            // their home screen.
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.chitti_logo),
                contentDescription = "Chitti",
                modifier = Modifier.size(96.dp)
            )
            Spacer(Modifier.height(Space.l))
            Text("Welcome to Chitti", style = MaterialTheme.typography.displayLarge, color = colors.textHigh, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.xs))
            Text(
                "Your account keeps your encrypted backup. Your messages and documents stay on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMid,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Space.xxl))

            if (!Auth.isConfigured) {
                Text(
                    "Sign-in isn't set up in this build yet.",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.warning,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Add the Firebase keys to local.properties and rebuild.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMid,
                    textAlign = TextAlign.Center
                )
                if (onSkip != null) {
                    Spacer(Modifier.height(Space.l))
                    SecondaryButton(text = "Continue without an account (debug)", onClick = onSkip)
                }
                return@Column
            }

            if (Auth.googleConfigured) {
                SecondaryButton(
                    text = "Continue with Google",
                    fill = true,
                    enabled = !busy,
                    onClick = {
                        run {
                            Auth.signInWithGoogle(context)
                            haptics.confirm()
                            onSignedIn()
                        }
                    }
                )
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = Space.l), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(0.5.dp).background(colors.separator))
                    Text("  or  ", style = MaterialTheme.typography.labelMedium, color = colors.textMid)
                    Box(Modifier.weight(1f).height(0.5.dp).background(colors.separator))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                if (mode == 1) {
                    ChittiTextField(name, { name = it }, "Your name", leadingIcon = Icons.Rounded.Person, imeAction = ImeAction.Next)
                }
                ChittiTextField(
                    email, { email = it.trim() }, "Email",
                    leadingIcon = Icons.Rounded.Email,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
                ChittiTextField(
                    password, { password = it }, if (mode == 1) "Password (8+ characters)" else "Password",
                    leadingIcon = Icons.Rounded.Lock,
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        BarIconButton(
                            icon = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            tint = colors.textMid,
                            onClick = { showPassword = !showPassword }
                        )
                    }
                )
            }

            message?.let {
                Spacer(Modifier.height(Space.s))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.danger, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            info?.let {
                Spacer(Modifier.height(Space.s))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.success, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(Space.l))
            PrimaryButton(
                text = if (busy) "Please wait…" else if (mode == 0) "Sign in" else "Create account",
                enabled = canSubmit,
                onClick = {
                    run {
                        if (mode == 0) Auth.signInWithEmail(email, password) else Auth.createAccount(name, email, password)
                        haptics.confirm()
                        onSignedIn()
                    }
                }
            )
            LinkButton(
                text = if (mode == 0) "New here? Create an account" else "Have an account? Sign in",
                enabled = !busy,
                onClick = { mode = 1 - mode; message = null }
            )
            if (mode == 0) {
                LinkButton(text = "Forgot password?", enabled = !busy, color = colors.textMid, onClick = {
                    if (!emailOk) {
                        message = "Type your email above first."
                    } else run {
                        Auth.sendPasswordReset(email)
                        info = "We sent a reset link to $email."
                    }
                })
            }
            if (onSkip != null && BuildConfig.DEBUG) {
                LinkButton(text = "Skip for now (debug)", color = colors.textMid, onClick = onSkip)
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}
