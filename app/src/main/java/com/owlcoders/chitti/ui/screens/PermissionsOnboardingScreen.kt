package com.owlcoders.chitti.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.owlcoders.chitti.security.BrowserRouter
import com.owlcoders.chitti.services.ChittiAccessibilityService
import com.owlcoders.chitti.ui.components.Inset
import com.owlcoders.chitti.ui.components.InsetRow
import com.owlcoders.chitti.ui.components.LargeTitleScaffold
import com.owlcoders.chitti.ui.components.LargeTitleSubtitle
import com.owlcoders.chitti.ui.components.LinkButton
import com.owlcoders.chitti.ui.components.Motion
import com.owlcoders.chitti.ui.components.PrimaryButton
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.insetSection
import com.owlcoders.chitti.ui.components.rememberHaptics
import com.owlcoders.chitti.ui.theme.Chitti

/**
 * First run. One grouped list of what Chitti needs and why, each row turning from "Allow" to "On"
 * in place as the grant lands (with a confirm haptic on that frame). Continue unlocks once the
 * three required grants are in; "Not now" lets someone look around first.
 */
@Composable
fun PermissionsOnboardingScreen(
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current

    var hasMic by remember { mutableStateOf(checkMicPermission(context)) }
    var hasNotification by remember { mutableStateOf(checkNotificationPermission(context)) }
    var hasAccessibility by remember { mutableStateOf(checkAccessibilityPermission(context)) }
    // Optional here, but required on Android 13+ for reminders and LinkGuard alerts.
    var hasPostNotifications by remember { mutableStateOf(checkPostNotificationsPermission(context)) }
    var checksLinks by remember { mutableStateOf(BrowserRouter.isLinkOpener(context)) }

    fun refresh() {
        hasMic = checkMicPermission(context)
        hasNotification = checkNotificationPermission(context)
        hasAccessibility = checkAccessibilityPermission(context)
        hasPostNotifications = checkPostNotificationsPermission(context)
        checksLinks = BrowserRouter.isLinkOpener(context)
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMic = it }
    val postLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPostNotifications = it }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    // Listener and accessibility grants happen in system Settings; re-check on return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !hasPostNotifications) {
            postLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val ready = hasMic && hasNotification && hasAccessibility
    LaunchedEffect(ready) { if (ready) onAllPermissionsGranted() }

    LargeTitleScaffold(
        title = "Set up Chitti",
        subtitle = {
            LargeTitleSubtitle("An assistant that runs on your phone. It needs a few things to listen, read what arrives and act for you.")
        }
    ) {
        insetSection(
            key = "required",
            header = "Needed",
            footer = "Everything Chitti reads stays on this phone."
        ) {
            row("mic", Inset.iconInset) {
                GrantRow("Microphone", "So you can talk to it", Icons.Rounded.Mic, hasMic) {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            row("listener", Inset.iconInset) {
                GrantRow("Notification access", "So it can turn messages into things to do", Icons.Rounded.Notifications, hasNotification) {
                    openSettingsSafely(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                }
            }
            row("a11y", Inset.iconInset) {
                GrantRow("Accessibility", "So it can tap and type for you", Icons.Rounded.Accessibility, hasAccessibility) {
                    openSettingsSafely(context, Settings.ACTION_ACCESSIBILITY_SETTINGS)
                }
            }
        }
        insetSection(
            key = "optional",
            header = "Optional",
            footer = "Notifications carry reminders and link warnings. Link checking makes every link you tap open in Chitti first, so unsafe ones are stopped before they load."
        ) {
            if (Build.VERSION.SDK_INT >= 33) {
                row("post", Inset.iconInset) {
                    GrantRow("Show notifications", null, Icons.Rounded.NotificationsActive, hasPostNotifications) {
                        postLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            row("links", Inset.iconInset) {
                GrantRow("Check links before they open", "Stops scam and phishing links", Icons.Rounded.GppGood, checksLinks) {
                    roleLauncher.launch(BrowserRouter.becomeLinkOpenerIntent(context))
                }
            }
        }
        item(key = "actions") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Space.gutter)
                    .padding(top = Space.xxl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PrimaryButton(
                    text = "Continue",
                    enabled = ready,
                    onClick = {
                        refresh()
                        if (hasMic && hasNotification && hasAccessibility) onAllPermissionsGranted()
                    }
                )
                // Kept for testing, and for anyone who wants to look around first.
                LinkButton(text = "Not now", onClick = onAllPermissionsGranted)
            }
        }
    }
}

private fun openSettingsSafely(context: Context, action: String) {
    try {
        context.startActivity(Intent(action))
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
        }
    }
}

/** A grant: what it is, why, and "Allow" that becomes "On" where it was tapped. */
@Composable
private fun GrantRow(title: String, reason: String?, icon: ImageVector, granted: Boolean, onAllow: () -> Unit) {
    val colors = Chitti.colors
    val haptics = rememberHaptics()
    var wasGranted by remember { mutableStateOf(granted) }
    LaunchedEffect(granted) {
        if (granted && !wasGranted) haptics.confirm()
        wasGranted = granted
    }
    InsetRow(
        title = title,
        subtitle = reason,
        icon = icon,
        iconTint = if (granted) colors.success else colors.accent,
        onClick = if (granted) null else onAllow,
        trailing = {
            AnimatedContent(
                targetState = granted,
                transitionSpec = { fadeIn(Motion.fade()) togetherWith fadeOut(Motion.fade(120)) },
                label = "grant"
            ) { on ->
                if (on) {
                    Text("On", style = MaterialTheme.typography.bodyLarge, color = colors.success)
                } else {
                    LinkButton(text = "Allow", onClick = onAllow)
                }
            }
        }
    )
}

fun checkMicPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

fun checkNotificationPermission(context: Context): Boolean {
    val packageNames = NotificationManagerCompat.getEnabledListenerPackages(context)
    return packageNames.contains(context.packageName)
}

fun checkPostNotificationsPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

/**
 * Reads the system setting instead of the service singleton: the singleton is null until the
 * system (re)binds the service, which can lag app start by seconds (and ~11 s after a crash
 * restart), which made onboarding reappear on every cold start.
 */
fun checkAccessibilityPermission(context: Context): Boolean {
    val expected = ComponentName(context, ChittiAccessibilityService::class.java)
    val enabled = try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    } catch (e: Exception) {
        null
    }.orEmpty()
    val listed = enabled.split(':').any { entry ->
        val parsed = ComponentName.unflattenFromString(entry)
        (parsed != null && parsed == expected) ||
            entry.equals(expected.flattenToString(), ignoreCase = true) ||
            entry.equals(expected.flattenToShortString(), ignoreCase = true)
    }
    return listed || ChittiAccessibilityService.instance != null
}
