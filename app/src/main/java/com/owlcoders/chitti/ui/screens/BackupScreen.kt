package com.owlcoders.chitti.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.owlcoders.chitti.account.Backend
import com.owlcoders.chitti.account.BackendException
import com.owlcoders.chitti.account.BackupMeta
import com.owlcoders.chitti.backup.BackupCrypto
import com.owlcoders.chitti.backup.BackupManager
import com.owlcoders.chitti.security.SecureScreen
import com.owlcoders.chitti.ui.components.ChittiTextField
import com.owlcoders.chitti.ui.components.Inset
import com.owlcoders.chitti.ui.components.InsetRow
import com.owlcoders.chitti.ui.components.LargeTitleScaffold
import com.owlcoders.chitti.ui.components.LargeTitleSubtitle
import com.owlcoders.chitti.ui.components.LatticeLoader
import com.owlcoders.chitti.ui.components.LatticeStatus
import com.owlcoders.chitti.ui.components.LinkButton
import com.owlcoders.chitti.ui.components.PrimaryButton
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.insetSection
import com.owlcoders.chitti.ui.components.rememberHaptics
import com.owlcoders.chitti.ui.theme.Chitti
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

private fun stageLabel(stage: BackupManager.Stage, progress: Float): String = when (stage) {
    BackupManager.Stage.Preparing -> "Preparing"
    BackupManager.Stage.Encrypting -> "Encrypting on this phone"
    BackupManager.Stage.Uploading -> "Uploading ${(progress * 100).toInt()}%"
    BackupManager.Stage.Downloading -> "Downloading ${(progress * 100).toInt()}%"
    BackupManager.Stage.Decrypting -> "Decrypting"
    BackupManager.Stage.Restoring -> "Restoring"
}

/**
 * Backup, laid out like WhatsApp's: when the last backup ran, back up now, the backup password,
 * automatic backups, and restore. Everything leaves the phone encrypted with a key derived from
 * the password; the footer says so plainly, including that a forgotten password cannot be reset.
 */
@Composable
fun BackupScreen() {
    SecureScreen()
    val context = LocalContext.current
    val colors = Chitti.colors
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    var status by remember { mutableStateOf(BackupManager.status(context)) }
    var hasPassword by remember { mutableStateOf(BackupManager.hasPassword(context)) }
    var serverMeta by remember { mutableStateOf<BackupMeta?>(null) }
    var serverChecked by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var settingPassword by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var pickingFrequency by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        serverMeta = runCatching { Backend.backupMeta() }.getOrNull()
        serverChecked = true
    }

    fun backUp() {
        error = null
        working = "Preparing"
        scope.launch {
            try {
                serverMeta = BackupManager.backUpNow(context) { stage, p -> working = stageLabel(stage, p) }
                status = BackupManager.status(context)
                haptics.confirm()
            } catch (e: BackendException) {
                haptics.reject(); error = e.message
            } catch (e: Exception) {
                haptics.reject(); error = e.message ?: "Backup failed."
            } finally {
                working = null
            }
        }
    }

    LargeTitleScaffold(
        title = "Backup",
        subtitle = { LargeTitleSubtitle("End-to-end encrypted") }
    ) {
        insetSection(
            key = "last",
            header = "Last backup",
            footer = "Back up your conversation, what Chitti knows, your personal details and documents. " +
                "Everything is encrypted on this phone before it is uploaded."
        ) {
            row("when", Inset.iconInset) {
                InsetRow(
                    title = "This phone",
                    icon = Icons.Rounded.CloudUpload,
                    iconTint = colors.accent,
                    value = if (status.lastBackupAt == 0L) "Never" else SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(status.lastBackupAt)),
                    subtitle = if (status.lastBackupSize > 0) Formatter.formatShortFileSize(context, status.lastBackupSize) else null
                )
            }
            row("server", Inset.iconInset) {
                val m = serverMeta
                InsetRow(
                    title = "On the server",
                    icon = Icons.Rounded.CloudDownload,
                    iconTint = colors.info,
                    value = when {
                        !Backend.isConfigured -> "Not set up"
                        !serverChecked -> "Checking…"
                        m == null -> "None"
                        else -> runCatching { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date.from(Instant.parse(m.createdAt))) }.getOrDefault("Saved")
                    },
                    subtitle = m?.let { "${Formatter.formatShortFileSize(context, it.size)} · ${it.device}" }
                )
            }
            row("now") {
                Column(Modifier.padding(Space.m)) {
                    val w = working
                    if (w != null) {
                        LatticeLoader(status = LatticeStatus.WORKING, label = w, color = colors.accent, fontSize = 15)
                    } else {
                        PrimaryButton(
                            text = "Back up now",
                            enabled = Backend.isConfigured,
                            onClick = { if (hasPassword) backUp() else settingPassword = true }
                        )
                    }
                    error?.let {
                        Spacer(Modifier.height(Space.s))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.danger)
                    }
                }
            }
        }

        insetSection(
            key = "encryption",
            header = "Encryption",
            footer = "Your backup is locked with this password before it leaves your phone. Chitti and its servers never " +
                "see the password or your data, so nobody can reset it for you. If you forget it, the backup can't be restored."
        ) {
            row("password", Inset.iconInset) {
                InsetRow(
                    title = "Backup password",
                    icon = Icons.Rounded.Key,
                    iconTint = colors.warning,
                    value = if (hasPassword) "On" else "Not set",
                    chevron = true,
                    onClick = { settingPassword = true }
                )
            }
        }

        insetSection(key = "auto", header = "Automatic backups") {
            row("frequency", Inset.iconInset) {
                InsetRow(
                    title = "Back up",
                    icon = Icons.Rounded.Schedule,
                    iconTint = colors.accent,
                    value = status.frequency.name,
                    chevron = true,
                    enabled = hasPassword,
                    onClick = { pickingFrequency = true }
                )
            }
            row("wifi", Inset.iconInset) {
                InsetRow(
                    title = "Only on Wi-Fi",
                    icon = Icons.Rounded.Wifi,
                    iconTint = colors.info,
                    trailing = {
                        Switch(checked = status.wifiOnly, onCheckedChange = {
                            BackupManager.setSchedule(context, status.frequency, it)
                            status = BackupManager.status(context)
                        })
                    }
                )
            }
        }

        insetSection(key = "restore", header = "Restore", footer = "Replaces what's on this phone with your backup. Chitti restarts afterwards.") {
            row("restore", Inset.iconInset) {
                InsetRow(
                    title = "Restore from backup",
                    icon = Icons.Rounded.CloudDownload,
                    iconTint = colors.success,
                    enabled = serverMeta != null,
                    onClick = if (serverMeta != null) ({ restoring = true }) else null
                )
            }
            row("delete") {
                InsetRow(
                    title = "Delete backup from server",
                    destructive = true,
                    enabled = serverMeta != null,
                    onClick = if (serverMeta != null) ({ confirmDelete = true }) else null
                )
            }
        }
    }

    if (settingPassword) {
        PasswordSetupDialog(
            changing = hasPassword,
            onDismiss = { settingPassword = false },
            onSet = { pw ->
                settingPassword = false
                working = "Securing your password"
                scope.launch {
                    BackupManager.setPassword(context, pw.toCharArray())
                    hasPassword = true
                    working = null
                    haptics.confirm()
                    backUp()
                }
            }
        )
    }

    if (restoring) {
        RestoreDialog(
            onDismiss = { restoring = false },
            onRestore = { pw, done ->
                scope.launch {
                    try {
                        BackupManager.restore(context, pw.toCharArray()) { stage, p -> working = stageLabel(stage, p) }
                        haptics.confirm()
                        BackupManager.restartApp(context)
                    } catch (e: BackupCrypto.WrongPasswordException) {
                        haptics.reject(); done("That password isn't right.")
                    } catch (e: Exception) {
                        haptics.reject(); done(e.message ?: "Restore failed.")
                    } finally {
                        working = null
                    }
                }
            }
        )
    }

    if (pickingFrequency) {
        AlertDialog(
            onDismissRequest = { pickingFrequency = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = colors.surface,
            title = { Text("Back up automatically", style = MaterialTheme.typography.titleLarge, color = colors.textHigh) },
            text = {
                Column {
                    BackupManager.Frequency.entries.forEach { f ->
                        InsetRow(
                            title = f.name,
                            onClick = {
                                BackupManager.setSchedule(context, f, status.wifiOnly)
                                status = BackupManager.status(context)
                                pickingFrequency = false
                            },
                            value = if (f == status.frequency) "✓" else null,
                            valueColor = colors.accent
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = colors.surface,
            title = { Text("Delete your backup?", style = MaterialTheme.typography.titleLarge, color = colors.textHigh) },
            text = { Text("The encrypted backup is erased from the server. What's on this phone stays.", style = MaterialTheme.typography.bodyMedium, color = colors.textMid) },
            confirmButton = {
                LinkButton(text = "Delete", color = colors.danger, style = MaterialTheme.typography.titleLarge, onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { Backend.deleteBackup() }
                            .onSuccess { serverMeta = null; haptics.confirm() }
                            .onFailure { error = it.message }
                    }
                })
            },
            dismissButton = { LinkButton(text = "Cancel", onClick = { confirmDelete = false }) }
        )
    }
}

/**
 * Shown once after signing in on a phone when a backup exists, like WhatsApp's "Restore backup"
 * step. Restoring needs the backup password; skipping keeps this phone as it is.
 */
@Composable
fun RestorePromptScreen(meta: BackupMeta, onDone: () -> Unit) {
    SecureScreen()
    val context = LocalContext.current
    val colors = Chitti.colors
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    val date = runCatching { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date.from(Instant.parse(meta.createdAt))) }.getOrDefault("")

    LargeTitleScaffold(
        title = "Restore backup",
        subtitle = { LargeTitleSubtitle("We found a backup for your account") }
    ) {
        insetSection(
            key = "found",
            footer = "Your backup is end-to-end encrypted. You'll need the backup password you set on your old phone."
        ) {
            row("meta", Inset.iconInset) {
                InsetRow(
                    title = "Backup from ${meta.device.ifBlank { "your phone" }}",
                    subtitle = "$date · ${Formatter.formatShortFileSize(context, meta.size)}",
                    icon = Icons.Rounded.CloudDownload,
                    iconTint = colors.success
                )
            }
        }
        item(key = "actions") {
            Column(Modifier.padding(horizontal = Space.gutter).padding(top = Space.xl)) {
                PrimaryButton(text = "Restore", onClick = { asking = true })
                LinkButton(text = "Skip, start fresh", color = colors.textMid, onClick = onDone, modifier = Modifier.padding(top = Space.s))
            }
        }
    }

    if (asking) {
        RestoreDialog(
            onDismiss = { asking = false },
            onRestore = { pw, done ->
                scope.launch {
                    try {
                        BackupManager.restore(context, pw.toCharArray())
                        BackupManager.restartApp(context)
                    } catch (e: BackupCrypto.WrongPasswordException) {
                        done("That password isn't right.")
                    } catch (e: Exception) {
                        done(e.message ?: "Restore failed.")
                    }
                }
            }
        )
    }
}

@Composable
private fun PasswordSetupDialog(changing: Boolean, onDismiss: () -> Unit, onSet: (String) -> Unit) {
    val colors = Chitti.colors
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val ok = pw.length >= 8 && pw == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = colors.surface,
        icon = { androidx.compose.material3.Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.accent) },
        title = { Text(if (changing) "New backup password" else "Create a backup password", style = MaterialTheme.typography.titleLarge, color = colors.textHigh) },
        text = {
            Column {
                Text(
                    "Nobody can reset this password, including Chitti. If you forget it, you won't be able to restore your backup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMid
                )
                Spacer(Modifier.height(Space.m))
                ChittiTextField(pw, { pw = it }, "At least 8 characters", keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation())
                Spacer(Modifier.height(Space.s))
                ChittiTextField(confirm, { confirm = it }, "Type it again", keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation())
                if (confirm.isNotEmpty() && pw != confirm) {
                    Spacer(Modifier.height(Space.xs))
                    Text("The passwords don't match.", style = MaterialTheme.typography.bodySmall, color = colors.danger)
                }
            }
        },
        confirmButton = { LinkButton(text = "Save", enabled = ok, style = MaterialTheme.typography.titleLarge, onClick = { onSet(pw) }) },
        dismissButton = { LinkButton(text = "Cancel", onClick = onDismiss) }
    )
}

/** Asks for the backup password and runs [onRestore]; shows its error message on failure. */
@Composable
fun RestoreDialog(onDismiss: () -> Unit, onRestore: (String, (String) -> Unit) -> Unit) {
    val colors = Chitti.colors
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = colors.surface,
        title = { Text("Restore your backup", style = MaterialTheme.typography.titleLarge, color = colors.textHigh) },
        text = {
            Column {
                Text("Enter the password you set when you backed up. What's on this phone will be replaced.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMid)
                Spacer(Modifier.height(Space.m))
                ChittiTextField(pw, { pw = it; error = null }, "Backup password", keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation())
                if (busy) {
                    Spacer(Modifier.height(Space.s))
                    LatticeLoader(status = LatticeStatus.WORKING, label = "Restoring", color = colors.accent, fontSize = 14)
                }
                error?.let {
                    Spacer(Modifier.height(Space.s))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger)
                }
            }
        },
        confirmButton = {
            LinkButton(text = "Restore", enabled = pw.isNotEmpty() && !busy, style = MaterialTheme.typography.titleLarge, onClick = {
                busy = true
                onRestore(pw) { message ->
                    busy = false
                    error = message
                }
            })
        },
        dismissButton = { LinkButton(text = "Cancel", enabled = !busy, onClick = onDismiss) }
    )
}
