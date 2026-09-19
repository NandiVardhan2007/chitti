package com.owlcoders.chitti.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.owlcoders.chitti.ui.components.Avatar
import com.owlcoders.chitti.ui.components.Inset
import com.owlcoders.chitti.ui.components.InsetRow
import com.owlcoders.chitti.ui.components.LargeTitleScaffold
import com.owlcoders.chitti.ui.components.LinkButton
import com.owlcoders.chitti.ui.components.insetSection
import com.owlcoders.chitti.ui.components.rememberHaptics
import com.owlcoders.chitti.ui.theme.Chitti

/** How much of each kind of thing is stored, and how to clear it. */
class StoredCounts(
    val commitments: Int,
    val tasks: Int,
    val found: Int,
    val known: Int,
    val messages: Int,
    val did: Int
)

class ClearActions(
    val found: () -> Unit,
    val known: () -> Unit,
    val messages: () -> Unit,
    val did: () -> Unit,
    val everything: () -> Unit
)

private const val DeveloperTaps = 7

/**
 * Settings: grouped rows in the iOS manner. Each group says in its footer what it is for; every
 * row reads label -> current answer. Clearing data is one tap on the row that shows how much there
 * is, then a confirmation. The version row is also the way into the developer lab (7 taps).
 */
@Composable
fun SettingsScreen(
    profileName: String?,
    counts: StoredCounts,
    clear: ClearActions,
    onOpenProfile: () -> Unit,
    onOpenLab: () -> Unit
) {
    val context = LocalContext.current
    val colors = Chitti.colors
    val haptics = rememberHaptics()
    val prefs = remember { context.getSharedPreferences("chitti_prefs", Context.MODE_PRIVATE) }
    var developer by remember { mutableStateOf(prefs.getBoolean("developer_unlocked", false)) }
    var versionTaps by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    // Permission state is re-read whenever the screen resumes (e.g. back from system Settings).
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val listener = remember(refresh) { NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName) }
    val mic = remember(refresh) { context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
    val calendar = remember(refresh) { context.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED }

    fun openAppInfo() {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open app settings", Toast.LENGTH_SHORT).show()
        }
    }

    // If the system will no longer show the dialog ("don't ask again"), go to App info instead.
    var requested by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        if (!granted) {
            val activity = context.findActivity()
            val permission = requested
            if (activity != null && permission != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                openAppInfo()
            }
        }
    }
    fun request(permission: String) {
        requested = permission
        launcher.launch(permission)
    }

    LargeTitleScaffold(title = "Settings") {
        insetSection(key = "profile") {
            row("profile", separatorInset = Inset.iconInset) {
                InsetRow(
                    title = profileName?.takeIf { it.isNotBlank() } ?: "Your details",
                    subtitle = "Name, contact and address for filling forms",
                    leading = { Avatar(initial = profileName, size = 44.dp, fallback = Icons.Rounded.Person) },
                    chevron = true,
                    onClick = onOpenProfile
                )
            }
        }

        insetSection(
            key = "access",
            header = "Access",
            footer = "Chitti only reads what these allow. You can turn any of them off in Android settings."
        ) {
            row("listener", Inset.iconInset) {
                PermissionRow("Notifications", "Read what arrives, to find things you need to do", Icons.Rounded.Notifications, listener) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } catch (e: Exception) {
                        openAppInfo()
                    }
                }
            }
            row("mic", Inset.iconInset) {
                PermissionRow("Microphone", "Hear you when you tap the mic", Icons.Rounded.Mic, mic) { request(android.Manifest.permission.RECORD_AUDIO) }
            }
            row("calendar", Inset.iconInset) {
                PermissionRow("Calendar", "Add what it finds to your calendar", Icons.Rounded.CalendarMonth, calendar) { request(android.Manifest.permission.WRITE_CALENDAR) }
            }
        }

        insetSection(
            key = "models",
            header = "On this phone",
            footer = "Every model runs on this device. Nothing you capture, say or store leaves it."
        ) {
            row("llm", Inset.iconInset) { InsetRow(title = "Language", icon = Icons.Rounded.Memory, iconTint = colors.purple, value = "Gemma 2B") }
            row("stt", Inset.iconInset) { InsetRow(title = "Listening", icon = Icons.Rounded.GraphicEq, iconTint = colors.info, value = "Android") }
            row("tts", Inset.iconInset) { InsetRow(title = "Speaking", icon = Icons.Rounded.RecordVoiceOver, iconTint = colors.success, value = "Android") }
        }

        insetSection(
            key = "stored",
            header = "Stored on this phone",
            footer = "Tap a kind of data to clear just that."
        ) {
            row("commitments") { InsetRow(title = "Things that need you", value = "${counts.commitments}") }
            row("tasks") { InsetRow(title = "Tasks and reminders", value = "${counts.tasks}") }
            row("found") { StoredRow("What Chitti found", counts.found) { confirm = "Clear what Chitti found?" to clear.found } }
            row("known") { StoredRow("What Chitti knows", counts.known) { confirm = "Clear what Chitti knows?" to clear.known } }
            row("did") { StoredRow("What Chitti did", counts.did) { confirm = "Clear what Chitti did?" to clear.did } }
            row("messages") { StoredRow("Conversation", counts.messages) { confirm = "Clear the conversation?" to clear.messages } }
        }

        insetSection(key = "erase", footer = "Deletes everything Chitti has stored on this phone. This can't be undone.") {
            row("erase") {
                InsetRow(title = "Erase all data", destructive = true, onClick = { confirm = "Erase all data?" to clear.everything })
            }
        }

        insetSection(key = "about", header = "About") {
            row("version") {
                InsetRow(
                    title = "Version",
                    value = "1.0",
                    onClick = {
                        if (developer) return@InsetRow
                        versionTaps++
                        val left = DeveloperTaps - versionTaps
                        when {
                            left <= 0 -> {
                                developer = true
                                prefs.edit().putBoolean("developer_unlocked", true).apply()
                                haptics.confirm()
                                Toast.makeText(context, "Developer lab unlocked", Toast.LENGTH_SHORT).show()
                            }
                            left <= 3 -> {
                                haptics.tick()
                                Toast.makeText(context, "$left more", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }

        if (developer) {
            insetSection(key = "developer", header = "Developer", footer = "Run a notification through the extractor and see what it pulls out.") {
                row("lab", Inset.iconInset) {
                    InsetRow(title = "Extraction lab", icon = Icons.Rounded.Science, iconTint = colors.warning, chevron = true, onClick = onOpenLab)
                }
            }
        }
    }

    val pending = confirm
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = colors.surface,
            title = { Text(pending.first, style = MaterialTheme.typography.titleLarge, color = colors.textHigh) },
            text = { Text("This can't be undone.", style = MaterialTheme.typography.bodyMedium, color = colors.textMid) },
            confirmButton = {
                LinkButton(text = "Clear", color = colors.danger, style = MaterialTheme.typography.titleLarge, onClick = {
                    pending.second()
                    haptics.confirm()
                    confirm = null
                })
            },
            dismissButton = { LinkButton(text = "Cancel", onClick = { confirm = null }) }
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    reason: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    granted: Boolean,
    onAllow: () -> Unit
) {
    val colors = Chitti.colors
    InsetRow(
        title = title,
        subtitle = reason,
        icon = icon,
        iconTint = if (granted) colors.accent else colors.textMid,
        value = if (granted) "On" else null,
        onClick = if (granted) null else onAllow,
        trailing = if (granted) null else { { LinkButton(text = "Allow", onClick = onAllow) } }
    )
}

@Composable
private fun StoredRow(title: String, count: Int, onClear: () -> Unit) {
    InsetRow(
        title = title,
        value = "$count",
        enabled = count > 0,
        onClick = if (count > 0) onClear else null
    )
}

/** Walks ContextWrappers (Compose gives a ContextThemeWrapper) up to the hosting Activity. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
